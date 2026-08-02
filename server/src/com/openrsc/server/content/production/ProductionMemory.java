package com.openrsc.server.content.production;

import com.openrsc.server.model.Cache;
import com.openrsc.server.model.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Server-owned navigation and per-account recipe memory for production UIs.
 *
 * Production plugins remain authoritative: restoring a nested route invokes
 * the same {@link ProductionStarter} used by a normal click, and a recipe is
 * only persisted after that starter reports a successful production start.
 */
public final class ProductionMemory {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final int UI_FLAG_REMEMBER_SUPPORTED = 1;
	public static final int UI_FLAG_REMEMBER_ENABLED = 2;
	public static final int UI_FLAG_CAN_GO_BACK = 4;

	static final String PREFERENCE_CACHE_KEY = "prod_remember_v1";
	static final String ROUTE_CACHE_PREFIX = "prod_route_v1_";
	private static final int PLAYER_CACHE_KEY_LIMIT = 32;
	private static final String NAVIGATION_ATTRIBUTE = "production_navigation_v1";
	private static final int MAX_ROUTE_DEPTH = 8;

	private ProductionMemory() {
	}

	public static Display prepareDisplay(Player player, ProductionSession session) {
		return prepareDisplay(new PlayerContext(player), player, session);
	}

	static Display prepareDisplay(Context context, Player callbackPlayer, ProductionSession session) {
		if (!isRememberable(session)) {
			clearNavigation(context);
			return new Display(session, session.getDefaultRecipeId(), 0, false);
		}

		ProductionStarter starter = attribute(context, "production_starter");
		Navigation navigation = attribute(context, NAVIGATION_ATTRIBUTE);
		if (navigation != null && navigation.transitionRecipeId >= 0) {
			Frame parent = navigation.current();
			if (parent != null) {
				parent.selectedRecipeId = navigation.transitionRecipeId;
			}
			navigation.frames.add(new Frame(session, starter, session.getDefaultRecipeId()));
			navigation.transitionRecipeId = -1;
			return display(navigation, isEnabled(context.getCache()), navigation.restoring);
		}

		if (navigation != null && navigation.redisplaying) {
			navigation.redisplaying = false;
			return display(navigation, isEnabled(context.getCache()), false);
		}

		navigation = new Navigation(activityKey(session));
		navigation.frames.add(new Frame(session, starter, session.getDefaultRecipeId()));
		context.setAttribute(NAVIGATION_ATTRIBUTE, navigation);
		if (isEnabled(context.getCache())) {
			restoreRoute(context, callbackPlayer, navigation,
				loadRoute(context.getCache(), navigation.activityKey));
		}
		return display(navigation, isEnabled(context.getCache()), false);
	}

	public static void beginStart(Player player, ProductionSession session, int itemId) {
		beginStart(new PlayerContext(player), session, itemId);
	}

	static void beginStart(Context context, ProductionSession session, int itemId) {
		Navigation navigation = attribute(context, NAVIGATION_ATTRIBUTE);
		Frame current = navigation == null ? null : navigation.current();
		if (current == null || current.session != session || session.getRecipeByItemId(itemId) == null) {
			return;
		}
		current.selectedRecipeId = itemId;
		if (isPicker(session)) {
			navigation.transitionRecipeId = itemId;
		}
	}

	public static void finishStart(Player player, ProductionSession session, int itemId, boolean started) {
		finishStart(new PlayerContext(player), session, itemId, started);
	}

	static void finishStart(Context context, ProductionSession session, int itemId, boolean started) {
		Navigation navigation = attribute(context, NAVIGATION_ATTRIBUTE);
		Frame current = navigation == null ? null : navigation.current();
		if (current == null) {
			return;
		}
		navigation.transitionRecipeId = -1;
		if (current.session != session || isPicker(session) || !started) {
			return;
		}
		current.selectedRecipeId = itemId;
		if (isEnabled(context.getCache())) {
			storeRoute(context.getCache(), navigation.activityKey, navigation.route());
		}
	}

	public static ProductionSession back(Player player) {
		return back(new PlayerContext(player));
	}

	static ProductionSession back(Context context) {
		Navigation navigation = attribute(context, NAVIGATION_ATTRIBUTE);
		if (navigation == null || navigation.frames.size() < 2) {
			return null;
		}
		navigation.transitionRecipeId = -1;
		navigation.frames.remove(navigation.frames.size() - 1);
		Frame parent = navigation.current();
		context.setAttribute("production_session", parent.session);
		context.setAttribute("production_starter", parent.starter);
		navigation.redisplaying = true;
		return parent.session;
	}

	public static boolean isEnabled(Player player) {
		return isEnabled(player.getCache());
	}

	static boolean isEnabled(Cache cache) {
		if (!cache.hasKey(PREFERENCE_CACHE_KEY)) {
			return false;
		}
		try {
			return cache.getBoolean(PREFERENCE_CACHE_KEY);
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	public static void setEnabled(Player player, boolean enabled) {
		player.getCache().store(PREFERENCE_CACHE_KEY, enabled);
	}

	public static void clearNavigation(Player player) {
		clearNavigation(new PlayerContext(player));
	}

	static void clearNavigation(Context context) {
		context.removeAttribute(NAVIGATION_ATTRIBUTE);
	}

	public static boolean isRememberable(ProductionSession session) {
		return session != null && session.getType() >= ProductionSession.TYPE_SMITHING
			&& session.getType() <= ProductionSession.TYPE_FURNACE_MATERIAL;
	}

	static boolean isPicker(ProductionSession session) {
		return session != null && (session.isType(ProductionSession.TYPE_SMITHING_MATERIAL)
			|| session.isType(ProductionSession.TYPE_FURNACE_CATEGORY)
			|| session.isType(ProductionSession.TYPE_FURNACE_MATERIAL));
	}

	static String activityKey(ProductionSession session) {
		if (session.isType(ProductionSession.TYPE_SMITHING_MATERIAL)) {
			return "anvil";
		}
		if (session.isType(ProductionSession.TYPE_FURNACE_CATEGORY)) {
			return "furnace";
		}
		String title = session.getTitle().toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("^-|-$", "");
		if (title.length() > 48) {
			title = title.substring(0, 48);
		}
		return "type-" + session.getType() + "-input-" + session.getInputItemId() + "-" + title;
	}

	static void storeRoute(Cache cache, String activityKey, List<Integer> route) {
		if (route == null || route.isEmpty() || route.size() > MAX_ROUTE_DEPTH) {
			return;
		}
		StringBuilder encoded = new StringBuilder();
		for (Integer itemId : route) {
			if (itemId == null || itemId < 0 || itemId > 65535) {
				return;
			}
			if (encoded.length() > 0) {
				encoded.append(',');
			}
			encoded.append(itemId);
		}
		cache.store(routeCacheKey(activityKey), encoded.toString());
	}

	static List<Integer> loadRoute(Cache cache, String activityKey) {
		String key = routeCacheKey(activityKey);
		if (!cache.hasKey(key)) {
			return Collections.emptyList();
		}
		try {
			String encoded = cache.getString(key);
			if (encoded.isEmpty()) {
				return Collections.emptyList();
			}
			String[] parts = encoded.split(",", -1);
			if (parts.length > MAX_ROUTE_DEPTH) {
				return Collections.emptyList();
			}
			List<Integer> route = new ArrayList<>(parts.length);
			for (String part : parts) {
				int itemId = Integer.parseInt(part);
				if (itemId < 0 || itemId > 65535) {
					return Collections.emptyList();
				}
				route.add(itemId);
			}
			return Collections.unmodifiableList(route);
		} catch (RuntimeException ignored) {
			return Collections.emptyList();
		}
	}

	private static String routeCacheKey(String activityKey) {
		String readable = ROUTE_CACHE_PREFIX + activityKey;
		if (readable.length() <= PLAYER_CACHE_KEY_LIMIT) {
			return readable;
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(activityKey.getBytes(StandardCharsets.UTF_8));
			StringBuilder suffix = new StringBuilder(PLAYER_CACHE_KEY_LIMIT - ROUTE_CACHE_PREFIX.length());
			for (byte value : digest) {
				if (suffix.length() >= PLAYER_CACHE_KEY_LIMIT - ROUTE_CACHE_PREFIX.length()) {
					break;
				}
				suffix.append(String.format(Locale.ROOT, "%02x", value & 0xff));
			}
			return ROUTE_CACHE_PREFIX + suffix;
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static void restoreRoute(Context context, Player callbackPlayer,
		Navigation navigation, List<Integer> route) {
		if (route.isEmpty()) {
			return;
		}
		navigation.restoring = true;
		Frame pendingParent = null;
		int pendingDepth = 0;
		try {
			for (int depth = 0; depth < route.size(); depth++) {
				Frame current = navigation.current();
				if (current == null) {
					break;
				}
				int itemId = route.get(depth);
				ProductionRecipe recipe = current.session.getRecipeByItemId(itemId);
				if (recipe == null || !recipe.isLevelMet()) {
					break;
				}
				current.selectedRecipeId = itemId;
				if (depth == route.size() - 1 || !isPicker(current.session)) {
					break;
				}
				if (!recipe.isLevelMet() || !recipe.isMaterialsMet() || current.starter == null) {
					break;
				}
				int previousDepth = navigation.frames.size();
				pendingParent = current;
				pendingDepth = previousDepth;
				navigation.transitionRecipeId = itemId;
				boolean opened = current.starter.start(callbackPlayer, current.session, itemId, 1);
				if (!opened || navigation.frames.size() <= previousDepth) {
					rollbackRestoreStep(context, navigation, current, previousDepth);
					break;
				}
				pendingParent = null;
			}
		} catch (RuntimeException exception) {
			LOGGER.warn("Falling back after production route restore failed activity={}",
				navigation.activityKey, exception);
			if (pendingParent != null) {
				rollbackRestoreStep(context, navigation, pendingParent, pendingDepth);
			} else {
				Frame current = navigation.current();
				if (current != null) {
					context.setAttribute("production_session", current.session);
					context.setAttribute("production_starter", current.starter);
				}
				navigation.transitionRecipeId = -1;
			}
		} finally {
			navigation.restoring = false;
		}
	}

	private static void rollbackRestoreStep(Context context, Navigation navigation, Frame parent, int depth) {
		while (navigation.frames.size() > depth) {
			navigation.frames.remove(navigation.frames.size() - 1);
		}
		navigation.transitionRecipeId = -1;
		context.setAttribute("production_session", parent.session);
		context.setAttribute("production_starter", parent.starter);
	}

	@SuppressWarnings("unchecked")
	private static <T> T attribute(Context context, String key) {
		return (T) context.getAttribute(key);
	}

	interface Context {
		Cache getCache();
		Object getAttribute(String key);
		void setAttribute(String key, Object value);
		void removeAttribute(String key);
	}

	private static final class PlayerContext implements Context {
		private final Player player;

		private PlayerContext(Player player) {
			this.player = player;
		}

		@Override
		public Cache getCache() {
			return player.getCache();
		}

		@Override
		public Object getAttribute(String key) {
			return player.getAttribute(key);
		}

		@Override
		public void setAttribute(String key, Object value) {
			player.setAttribute(key, value);
		}

		@Override
		public void removeAttribute(String key) {
			player.removeAttribute(key);
		}
	}

	private static Display display(Navigation navigation, boolean enabled, boolean suppress) {
		Frame current = navigation.current();
		int flags = UI_FLAG_REMEMBER_SUPPORTED;
		if (enabled) {
			flags |= UI_FLAG_REMEMBER_ENABLED;
		}
		if (navigation.frames.size() > 1) {
			flags |= UI_FLAG_CAN_GO_BACK;
		}
		return new Display(current.session, current.selectedRecipeId, flags, suppress);
	}

	public static final class Display {
		private final ProductionSession session;
		private final int selectedRecipeId;
		private final int uiFlags;
		private final boolean suppress;

		private Display(ProductionSession session, int selectedRecipeId, int uiFlags, boolean suppress) {
			this.session = session;
			this.selectedRecipeId = selectedRecipeId;
			this.uiFlags = uiFlags;
			this.suppress = suppress;
		}

		public ProductionSession getSession() {
			return session;
		}

		public int getSelectedRecipeId() {
			return selectedRecipeId;
		}

		public int getUiFlags() {
			return uiFlags;
		}

		public boolean isSuppressed() {
			return suppress;
		}
	}

	private static final class Navigation {
		private final String activityKey;
		private final List<Frame> frames = new ArrayList<>();
		private int transitionRecipeId = -1;
		private boolean restoring;
		private boolean redisplaying;

		private Navigation(String activityKey) {
			this.activityKey = activityKey;
		}

		private Frame current() {
			return frames.isEmpty() ? null : frames.get(frames.size() - 1);
		}

		private List<Integer> route() {
			List<Integer> route = new ArrayList<>(frames.size());
			for (Frame frame : frames) {
				if (frame.selectedRecipeId < 0) {
					return Collections.emptyList();
				}
				route.add(frame.selectedRecipeId);
			}
			return route;
		}
	}

	private static final class Frame {
		private final ProductionSession session;
		private final ProductionStarter starter;
		private int selectedRecipeId;

		private Frame(ProductionSession session, ProductionStarter starter, int selectedRecipeId) {
			this.session = session;
			this.starter = starter;
			this.selectedRecipeId = selectedRecipeId;
		}
	}
}
