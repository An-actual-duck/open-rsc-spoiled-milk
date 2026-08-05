package com.openrsc.server.combat;

import com.google.common.collect.Multimap;
import com.openrsc.server.Server;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.plugins.handler.PluginHandler;
import com.openrsc.server.util.rsc.DataConversions;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Test-only fixture over the current production Server, World, Player, Npc,
 * scheduler, and plugin-handler implementations.
 *
 * <p>A02 supplies deterministic time and random-number sources through the
 * production Server constructor without installing alternate combat logic.</p>
 */
final class CurrentCombatHarness implements AutoCloseable {
	private static final long DEFAULT_CLOCK_MILLIS = 1_700_000_000_000L;
	private static final long DEFAULT_RANDOM_SEED = 0x5A17C0B4L;

	private final Server server;
	private final World world;
	private final MutableGameClock clock;
	private final SeededGameRandom random;
	private final List<Player> players = new ArrayList<Player>();
	private final List<Npc> npcs = new ArrayList<Npc>();
	private ThreadPoolExecutor pluginExecutor;
	private boolean closed;

	CurrentCombatHarness() throws IOException {
		clock = new MutableGameClock(DEFAULT_CLOCK_MILLIS);
		random = new SeededGameRandom(DEFAULT_RANDOM_SEED);
		server = new Server("myworld.conf", clock, random);
		server.getConfig().WANT_THREADING__BREAK_PID_PRIORITY = false;
		server.getConfig().WANT_PVP = false;
		server.getConfig().COMBAT_EXP_RATE = 1.0D;
		server.getConfig().SKILLING_EXP_RATE = 1.0D;
		server.getConfig().IS_DOUBLE_EXP = false;
		server.getEntityHandler().load();
		world = server.getWorld();
		world.getNpcDrops().load();
	}

	Server server() {
		return server;
	}

	World world() {
		return world;
	}

	MutableGameClock clock() {
		return clock;
	}

	SeededGameRandom random() {
		return random;
	}

	Player player(final String name, final int x, final int packedY) {
		ensureOpen();
		openTile(x, packedY);
		final Player player = new Player(
			world, DataConversions.usernameToHash(name));
		player.setClientVersion(server.getConfig().CLIENT_VERSION);
		player.setClientLimitations(ClientLimitations.forVersion(
			server.getConfig().CLIENT_VERSION));
		player.setInitialLocation(Point.location(x, packedY));
		setCombatSkills(player, 40, 40);
		world.getPlayers().add(player);
		player.updateRegion();
		player.setBusy(false);
		player.setLoggedIn(true);
		players.add(player);
		return player;
	}

	Npc npc(final int id, final int x, final int packedY) {
		ensureOpen();
		openTile(x, packedY);
		final Npc npc = new Npc(world, id, x, packedY, 5);
		world.registerNpc(npc);
		npcs.add(npc);
		return npc;
	}

	void openTile(final int x, final int packedY) {
		world.getTile(x, packedY).traversalMask = 0;
		world.getTile(x, packedY).projectileAllowed = true;
		world.getTile(x, packedY).originalProjectileAllowed = true;
	}

	void openRectangle(final int minX, final int maxX,
			final int minY, final int maxY) {
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				openTile(x, y);
			}
		}
	}

	@SuppressWarnings("unchecked")
	void installPlugin(final Class<?> triggerType, final Object plugin)
			throws ReflectiveOperationException {
		ensureOpen();
		final PluginHandler handler = server.getPluginHandler();
		final Field reloading = PluginHandler.class.getDeclaredField("reloading");
		reloading.setAccessible(true);
		reloading.setBoolean(handler, false);

		final Field instances = PluginHandler.class.getDeclaredField(
			"triggerTypeToInstance");
		instances.setAccessible(true);
		((Multimap<Class<?>, Object>) instances.get(handler)).put(triggerType, plugin);

		if (pluginExecutor == null) {
			pluginExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
			final Field executor = PluginHandler.class.getDeclaredField("executor");
			executor.setAccessible(true);
			executor.set(handler, pluginExecutor);
		}
	}

	GameTickEvent findEvent(final String descriptor) {
		for (GameTickEvent event : server.getGameEventHandler().getEvents()) {
			if (descriptor.equals(event.getDescriptor())) {
				return event;
			}
		}
		return null;
	}

	static Object invokePrivate(final Object target, final String methodName,
			final Class<?>[] parameterTypes, final Object... arguments)
			throws ReflectiveOperationException {
		final Method method = target.getClass().getDeclaredMethod(
			methodName, parameterTypes);
		method.setAccessible(true);
		return method.invoke(target, arguments);
	}

	private static void setCombatSkills(final Player player,
			final int combatLevel, final int hits) {
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.MELEE.id(), combatLevel, combatLevel, false);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.RANGED.id(), combatLevel, combatLevel, false);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.MAGIC.id(), combatLevel, combatLevel, false);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), hits, hits, false);
	}

	private void ensureOpen() {
		if (closed) {
			throw new IllegalStateException("Combat harness is closed");
		}
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		for (GameTickEvent event : server.getGameEventHandler().getEvents()) {
			event.stop();
		}
		server.getGameEventHandler().cleanupEvents();
		world.getPlayers().clear();
		world.getNpcs().clear();
		world.getNpcDrops().unload();
		server.getEntityHandler().unload();
		shutdown(pluginExecutor);
		shutdownServerExecutor("sqlLoggingThreadPool");
		shutdownServerExecutor("sqlThreadPool");
		shutdownServerExecutor("onlineMonitorThreadPool");
		Server.serversList.remove(server.getName(), server);
	}

	private void shutdownServerExecutor(final String fieldName) {
		try {
			final Field field = Server.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			shutdown((ExecutorService) field.get(server));
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(
				"Unable to close test-only Server executor " + fieldName, failure);
		}
	}

	private static void shutdown(final ExecutorService executor) {
		if (executor != null) {
			executor.shutdownNow();
		}
	}
}
