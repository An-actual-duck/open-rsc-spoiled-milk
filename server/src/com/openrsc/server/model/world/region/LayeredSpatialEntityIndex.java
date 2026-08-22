package com.openrsc.server.model.world.region;

import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Authoritative world-space/level-qualified runtime entity membership.
 *
 * <p>This index deliberately owns no terrain or collision state. Packed
 * {@link Region} containers remain the compatibility terrain backend until
 * native layered terrain storage is separately enabled.</p>
 */
public final class LayeredSpatialEntityIndex {
	private final Object lock = new Object();
	private final Map<WorldRegionKey, LinkedHashSet<Entity>> regions =
		new java.util.HashMap<WorldRegionKey, LinkedHashSet<Entity>>();
	private final Map<WorldRegionKey, LinkedHashSet<Player>> playerRegions =
		new java.util.HashMap<WorldRegionKey, LinkedHashSet<Player>>();
	private final Map<WorldRegionKey, List<GameObject>>
		gameObjectRegions =
			new java.util.HashMap<WorldRegionKey, List<GameObject>>();
	private final IdentityHashMap<Entity, WorldLocation> memberships =
		new IdentityHashMap<Entity, WorldLocation>();
	private long version;
	private long objectVersion;

	public void synchronize(
		final Entity entity,
		final WorldLocation expectedPrevious,
		final WorldLocation target) {
		Entity checkedEntity = Objects.requireNonNull(entity, "entity");
		WorldLocation checkedTarget = Objects.requireNonNull(target, "target");
		synchronized (lock) {
			WorldLocation current = memberships.get(checkedEntity);
			if (current == null) {
				if (expectedPrevious != null) {
					throw new IllegalStateException(
						"Layered entity membership is missing before movement");
				}
				add(checkedEntity, checkedTarget);
				return;
			}
			if (expectedPrevious != null && !current.equals(expectedPrevious)) {
				throw new IllegalStateException(
					"Layered entity membership differs from expected movement origin");
			}
			if (current.equals(checkedTarget)) {
				return;
			}
			removeFromRegion(checkedEntity, current);
			addToRegion(checkedEntity, checkedTarget);
			memberships.put(checkedEntity, checkedTarget);
			advanceVersion(checkedEntity);
		}
	}

	public void remove(
		final Entity entity,
		final WorldLocation expectedLocation) {
		Entity checkedEntity = Objects.requireNonNull(entity, "entity");
		WorldLocation checkedLocation = Objects.requireNonNull(
			expectedLocation, "expectedLocation");
		synchronized (lock) {
			WorldLocation current = memberships.get(checkedEntity);
			if (!checkedLocation.equals(current)) {
				throw new IllegalStateException(
					"Layered entity removal differs from indexed membership");
			}
			removeFromRegion(checkedEntity, current);
			memberships.remove(checkedEntity);
			advanceVersion(checkedEntity);
		}
	}

	public void replace(
		final Entity expected,
		final Entity replacement,
		final WorldLocation expectedLocation) {
		Entity checkedExpected = Objects.requireNonNull(expected, "expected");
		Entity checkedReplacement = Objects.requireNonNull(
			replacement, "replacement");
		WorldLocation checkedLocation = Objects.requireNonNull(
			expectedLocation, "expectedLocation");
		if (checkedExpected == checkedReplacement) {
			throw new IllegalArgumentException(
				"Layered entity replacement must use distinct instances");
		}
		synchronized (lock) {
			if (!checkedLocation.equals(memberships.get(checkedExpected))
				|| memberships.containsKey(checkedReplacement)) {
				throw new IllegalStateException(
					"Layered entity replacement membership differs");
			}
			WorldRegionKey key = WorldRegionKey.from(checkedLocation);
			LinkedHashSet<Entity> members = regions.get(key);
			if (members == null || !members.contains(checkedExpected)) {
				throw new IllegalStateException(
					"Layered entity replacement source is absent");
			}
			requirePlayerReplacementMembership(
				key, checkedExpected, checkedReplacement);
			requireGameObjectReplacementMembership(
				key, checkedExpected, checkedReplacement);
			members.remove(checkedExpected);
			if (!members.add(checkedReplacement)) {
				members.add(checkedExpected);
				throw new IllegalStateException(
					"Layered entity replacement target is already present");
			}
			replacePlayerMembership(
				key, checkedExpected, checkedReplacement);
			replaceGameObjectMembership(
				key, checkedExpected, checkedReplacement);
			memberships.remove(checkedExpected);
			memberships.put(checkedReplacement, checkedLocation);
			version++;
			if (checkedExpected instanceof GameObject
				|| checkedReplacement instanceof GameObject) {
				objectVersion++;
			}
		}
	}

	public void requireMembership(
		final Entity entity,
		final WorldLocation expectedLocation) {
		synchronized (lock) {
			if (!Objects.requireNonNull(expectedLocation, "expectedLocation")
				.equals(memberships.get(
					Objects.requireNonNull(entity, "entity")))) {
				throw new IllegalStateException(
					"Layered entity authority differs from spatial membership");
			}
		}
	}

	public Snapshot snapshot(final WorldRegionWindow window) {
		WorldRegionWindow checked = requireBoundedWindow(window);
		synchronized (lock) {
			List<Entity> entities = new ArrayList<Entity>();
			for (int regionX = checked.getMinRegionX();
				regionX <= checked.getMaxRegionX(); regionX++) {
				for (int regionY = checked.getMinRegionY();
					regionY <= checked.getMaxRegionY(); regionY++) {
					Collection<Entity> members = regions.get(
						new WorldRegionKey(
							checked.getWorldSpace(), checked.getLevel(),
							regionX, regionY));
					if (members != null) {
						entities.addAll(members);
					}
				}
			}
			return new Snapshot(
				checked, version, objectVersion, entities);
		}
	}

	/** Exact-tile NPC lookup without materializing a mixed-region snapshot. */
	public Npc findNpcAt(
		final WorldLocation target,
		final Entity observer) {
		WorldLocation checkedTarget = Objects.requireNonNull(target, "target");
		Entity checkedObserver = Objects.requireNonNull(observer, "observer");
		synchronized (lock) {
			Collection<Entity> members = regions.get(
				WorldRegionKey.from(checkedTarget));
			if (members == null) return null;
			for (Entity candidate : members) {
				if (candidate instanceof Npc
					&& checkedTarget.equals(memberships.get(candidate))
					&& !candidate.isInvisibleTo(checkedObserver)) {
					return (Npc)candidate;
				}
			}
			return null;
		}
	}

	/** Exact-tile player lookup over the existing player-only projection. */
	public Player findPlayerAt(
		final WorldLocation target,
		final Entity observer,
		final boolean includeSelf) {
		WorldLocation checkedTarget = Objects.requireNonNull(target, "target");
		Entity checkedObserver = Objects.requireNonNull(observer, "observer");
		synchronized (lock) {
			Collection<Player> members = playerRegions.get(
				WorldRegionKey.from(checkedTarget));
			if (members == null) return null;
			for (Player candidate : members) {
				if (checkedTarget.equals(memberships.get(candidate))
					&& !candidate.isInvisibleTo(checkedObserver)
					&& (!includeSelf || candidate == checkedObserver)) {
					return candidate;
				}
			}
			return null;
		}
	}

	/**
	 * Checks the player-only membership projection without copying every entity
	 * in the interest window.
	 *
	 * <p>NPC roam eligibility calls this for many owners on each game tick.
	 * Keeping the player projection separate prevents scenery, items, and the
	 * complete NPC population from becoming allocation and scan work for a
	 * boolean presence query.</p>
	 */
	public boolean hasPlayerWithinRange(
		final WorldRegionWindow window,
		final Entity observer) {
		WorldRegionWindow checked = requireBoundedWindow(window);
		Entity checkedObserver = Objects.requireNonNull(observer, "observer");
		synchronized (lock) {
			for (int regionX = checked.getMinRegionX();
				regionX <= checked.getMaxRegionX(); regionX++) {
				for (int regionY = checked.getMinRegionY();
					regionY <= checked.getMaxRegionY(); regionY++) {
					Collection<Player> players = playerRegions.get(
						new WorldRegionKey(
							checked.getWorldSpace(), checked.getLevel(),
							regionX, regionY));
					if (players == null) {
						continue;
					}
					for (Player player : players) {
						if (player.withinRange(checkedObserver)) {
							return true;
						}
					}
				}
			}
			return false;
		}
	}

	/**
	 * Copies only players that are actually visible to the observer.
	 *
	 * <p>Aggressive NPC processing needs the complete ordered player set, not
	 * merely the presence check above. Reading the player projection directly
	 * avoids copying every NPC, object, and item in the same window before the
	 * caller discards them.</p>
	 */
	public List<Player> snapshotPlayersWithinRange(
		final WorldRegionWindow window,
		final Entity observer) {
		WorldRegionWindow checked = requireBoundedWindow(window);
		Entity checkedObserver = Objects.requireNonNull(observer, "observer");
		synchronized (lock) {
			List<Player> players = new ArrayList<Player>();
			for (int regionX = checked.getMinRegionX();
				regionX <= checked.getMaxRegionX(); regionX++) {
				for (int regionY = checked.getMinRegionY();
					regionY <= checked.getMaxRegionY(); regionY++) {
					Collection<Player> members = playerRegions.get(
						new WorldRegionKey(
							checked.getWorldSpace(), checked.getLevel(),
							regionX, regionY));
					if (members == null) {
						continue;
					}
					for (Player player : members) {
						if (player.withinRange(checkedObserver)) {
							players.add(player);
						}
					}
				}
			}
			return players;
		}
	}

	/** Copies only NPC membership while retaining region/insertion order. */
	public List<Npc> snapshotNpcs(final WorldRegionWindow window) {
		WorldRegionWindow checked = requireBoundedWindow(window);
		synchronized (lock) {
			List<Npc> npcs = new ArrayList<Npc>();
			for (int regionX = checked.getMinRegionX();
				regionX <= checked.getMaxRegionX(); regionX++) {
				for (int regionY = checked.getMinRegionY();
					regionY <= checked.getMaxRegionY(); regionY++) {
					Collection<Entity> members = regions.get(
						new WorldRegionKey(
							checked.getWorldSpace(), checked.getLevel(),
							regionX, regionY));
					if (members == null) {
						continue;
					}
					for (Entity member : members) {
						if (member instanceof Npc) {
							npcs.add((Npc) member);
						}
					}
				}
			}
			return npcs;
		}
	}

	/** Copies only ground-item membership while retaining region/insertion order. */
	public List<GroundItem> snapshotGroundItems(
		final WorldRegionWindow window) {
		WorldRegionWindow checked = requireBoundedWindow(window);
		synchronized (lock) {
			List<GroundItem> items = new ArrayList<GroundItem>();
			for (int regionX = checked.getMinRegionX();
				regionX <= checked.getMaxRegionX(); regionX++) {
				for (int regionY = checked.getMinRegionY();
					regionY <= checked.getMaxRegionY(); regionY++) {
					Collection<Entity> members = regions.get(
						new WorldRegionKey(
							checked.getWorldSpace(), checked.getLevel(),
							regionX, regionY));
					if (members == null) {
						continue;
					}
					for (Entity member : members) {
						if (member instanceof GroundItem) {
							items.add((GroundItem) member);
						}
					}
				}
			}
			return items;
		}
	}

	/**
	 * Copies only game-object membership from a bounded logical window.
	 *
	 * <p>NPC path validation performs several scenery checks for one movement
	 * step. It must not materialize and then discard every NPC, player, and
	 * ground item in the custom client's much larger interest window.</p>
	 */
	public GameObjectSnapshot snapshotGameObjects(
		final WorldRegionWindow window) {
		WorldRegionWindow checked = requireBoundedWindow(window);
		synchronized (lock) {
			List<GameObject> gameObjects = new ArrayList<GameObject>();
			for (int regionX = checked.getMinRegionX();
				regionX <= checked.getMaxRegionX(); regionX++) {
				for (int regionY = checked.getMinRegionY();
					regionY <= checked.getMaxRegionY(); regionY++) {
					Collection<GameObject> members = gameObjectRegions.get(
						new WorldRegionKey(
							checked.getWorldSpace(), checked.getLevel(),
							regionX, regionY));
					if (members != null) {
						gameObjects.addAll(members);
					}
				}
			}
			return new GameObjectSnapshot(
				checked, objectVersion, gameObjects);
		}
	}

	/**
	 * Tests game-object membership in-place and stops at the first match.
	 *
	 * <p>This is the allocation-free query used by NPC path validation. The
	 * caller owns gameplay semantics; this index only constrains candidates to
	 * the exact world space, level, and bounded logical-region window.</p>
	 */
	public boolean hasGameObjectAt(
		final WorldRegionWindow window,
		final int tileX,
		final int tileY,
		final GameObjectTilePredicate predicate) {
		WorldRegionWindow checked = requireBoundedWindow(window);
		return hasGameObjectAt(
			checked,
			checked.getMinRegionX(),
			checked.getMinRegionY(),
			checked.getMaxRegionX(),
			checked.getMaxRegionY(),
			tileX,
			tileY,
			predicate);
	}

	/**
	 * Tests only origin regions whose objects can cover the requested tile.
	 *
	 * <p>The maximum footprint dimensions are an authoritative caller
	 * invariant. This keeps adjacent NPC collision checks from scanning the
	 * owner's entire visual interest window while retaining objects whose
	 * positive-axis footprint begins in a preceding logical region.</p>
	 */
	public boolean hasGameObjectAt(
		final WorldRegionWindow window,
		final int tileX,
		final int tileY,
		final int maximumFootprintWidth,
		final int maximumFootprintHeight,
		final GameObjectTilePredicate predicate) {
		WorldRegionWindow checked = requireBoundedWindow(window);
		if (maximumFootprintWidth < 1 || maximumFootprintHeight < 1) {
			throw new IllegalArgumentException(
				"Game-object footprint dimensions must be positive");
		}
		int minRegionX = Math.max(
			checked.getMinRegionX(),
			Math.floorDiv(
				Math.subtractExact(tileX, maximumFootprintWidth - 1),
				WorldRegionKey.REGION_SIZE));
		int minRegionY = Math.max(
			checked.getMinRegionY(),
			Math.floorDiv(
				Math.subtractExact(tileY, maximumFootprintHeight - 1),
				WorldRegionKey.REGION_SIZE));
		int maxRegionX = Math.min(
			checked.getMaxRegionX(),
			Math.floorDiv(tileX, WorldRegionKey.REGION_SIZE));
		int maxRegionY = Math.min(
			checked.getMaxRegionY(),
			Math.floorDiv(tileY, WorldRegionKey.REGION_SIZE));
		if (minRegionX > maxRegionX || minRegionY > maxRegionY) {
			return false;
		}
		return hasGameObjectAt(
			checked,
			minRegionX,
			minRegionY,
			maxRegionX,
			maxRegionY,
			tileX,
			tileY,
			predicate);
	}

	private boolean hasGameObjectAt(
		final WorldRegionWindow checked,
		final int minRegionX,
		final int minRegionY,
		final int maxRegionX,
		final int maxRegionY,
		final int tileX,
		final int tileY,
		final GameObjectTilePredicate predicate) {
		GameObjectTilePredicate checkedPredicate =
			Objects.requireNonNull(predicate, "predicate");
		synchronized (lock) {
			for (int regionX = minRegionX;
				regionX <= maxRegionX; regionX++) {
				for (int regionY = minRegionY;
					regionY <= maxRegionY; regionY++) {
					Collection<GameObject> members = gameObjectRegions.get(
						new WorldRegionKey(
							checked.getWorldSpace(), checked.getLevel(),
							regionX, regionY));
					if (members == null) {
						continue;
					}
					for (GameObject gameObject : members) {
						if (checkedPredicate.matches(
								gameObject, tileX, tileY)) {
							return true;
						}
					}
				}
			}
			return false;
		}
	}

	public int getMembershipCount() {
		synchronized (lock) {
			return memberships.size();
		}
	}

	public void clear() {
		synchronized (lock) {
			regions.clear();
			playerRegions.clear();
			gameObjectRegions.clear();
			memberships.clear();
			version++;
			objectVersion++;
		}
	}

	private void add(
		final Entity entity,
		final WorldLocation location) {
		addToRegion(entity, location);
		memberships.put(entity, location);
		advanceVersion(entity);
	}

	private void advanceVersion(final Entity entity) {
		version++;
		if (entity instanceof GameObject) {
			objectVersion++;
		}
	}

	private void addToRegion(
		final Entity entity,
		final WorldLocation location) {
		WorldRegionKey key = WorldRegionKey.from(location);
		LinkedHashSet<Entity> members = regions.get(key);
		if (members == null) {
			members = new LinkedHashSet<Entity>();
			regions.put(key, members);
		}
		if (!members.add(entity)) {
			throw new IllegalStateException(
				"Layered entity is already present in its target region");
		}
		if (entity instanceof Player) {
			LinkedHashSet<Player> players = playerRegions.get(key);
			if (players == null) {
				players = new LinkedHashSet<Player>();
				playerRegions.put(key, players);
			}
			if (!players.add((Player) entity)) {
				members.remove(entity);
				if (members.isEmpty()) {
					regions.remove(key);
				}
				throw new IllegalStateException(
					"Layered player is already present in its target region");
			}
		}
		if (entity instanceof GameObject) {
			List<GameObject> gameObjects = gameObjectRegions.get(key);
			if (gameObjects == null) {
				gameObjects = new ArrayList<GameObject>();
				gameObjectRegions.put(key, gameObjects);
			}
			if (containsIdentity(gameObjects, (GameObject) entity)) {
				members.remove(entity);
				if (members.isEmpty()) {
					regions.remove(key);
				}
				throw new IllegalStateException(
					"Layered game object is already present in its target region");
			}
			gameObjects.add((GameObject) entity);
		}
	}

	private void removeFromRegion(
		final Entity entity,
		final WorldLocation location) {
		WorldRegionKey key = WorldRegionKey.from(location);
		LinkedHashSet<Entity> members = regions.get(key);
		if (members == null || !members.contains(entity)) {
			throw new IllegalStateException(
				"Layered entity is absent from its indexed region");
		}
		LinkedHashSet<Player> players = null;
		if (entity instanceof Player) {
			players = playerRegions.get(key);
			if (players == null || !players.contains(entity)) {
				throw new IllegalStateException(
					"Layered player is absent from its indexed region");
			}
		}
		List<GameObject> gameObjects = null;
		if (entity instanceof GameObject) {
			gameObjects = gameObjectRegions.get(key);
			if (gameObjects == null
				|| !containsIdentity(gameObjects, (GameObject) entity)) {
				throw new IllegalStateException(
					"Layered game object is absent from its indexed region");
			}
		}
		members.remove(entity);
		if (players != null) {
			players.remove(entity);
			if (players.isEmpty()) {
				playerRegions.remove(key);
			}
		}
		if (gameObjects != null) {
			removeIdentity(gameObjects, (GameObject) entity);
			if (gameObjects.isEmpty()) {
				gameObjectRegions.remove(key);
			}
		}
		if (members.isEmpty()) {
			regions.remove(key);
		}
	}

	private void replacePlayerMembership(
		final WorldRegionKey key,
		final Entity expected,
		final Entity replacement) {
		LinkedHashSet<Player> players = playerRegions.get(key);
		if (expected instanceof Player) {
			players.remove(expected);
		}
		if (replacement instanceof Player) {
			if (players == null) {
				players = new LinkedHashSet<Player>();
				playerRegions.put(key, players);
			}
			players.add((Player) replacement);
		}
		if (players != null && players.isEmpty()) {
			playerRegions.remove(key);
		}
	}

	private void requirePlayerReplacementMembership(
		final WorldRegionKey key,
		final Entity expected,
		final Entity replacement) {
		LinkedHashSet<Player> players = playerRegions.get(key);
		if (expected instanceof Player
			&& (players == null || !players.contains(expected))) {
			throw new IllegalStateException(
				"Layered player replacement source is absent");
		}
		if (replacement instanceof Player
			&& players != null && players.contains(replacement)) {
			throw new IllegalStateException(
				"Layered player replacement target is already present");
		}
	}

	private void replaceGameObjectMembership(
		final WorldRegionKey key,
		final Entity expected,
		final Entity replacement) {
		List<GameObject> gameObjects = gameObjectRegions.get(key);
		if (expected instanceof GameObject) {
			removeIdentity(gameObjects, (GameObject) expected);
		}
		if (replacement instanceof GameObject) {
			if (gameObjects == null) {
				gameObjects = new ArrayList<GameObject>();
				gameObjectRegions.put(key, gameObjects);
			}
			if (!containsIdentity(gameObjects, (GameObject) replacement)) {
				gameObjects.add((GameObject) replacement);
			}
		}
		if (gameObjects != null && gameObjects.isEmpty()) {
			gameObjectRegions.remove(key);
		}
	}

	private void requireGameObjectReplacementMembership(
		final WorldRegionKey key,
		final Entity expected,
		final Entity replacement) {
		List<GameObject> gameObjects = gameObjectRegions.get(key);
		if (expected instanceof GameObject
			&& (gameObjects == null
				|| !containsIdentity(gameObjects, (GameObject) expected))) {
			throw new IllegalStateException(
				"Layered game object replacement source is absent");
		}
		if (replacement instanceof GameObject
			&& gameObjects != null
			&& containsIdentity(gameObjects, (GameObject) replacement)) {
			throw new IllegalStateException(
				"Layered game object replacement target is already present");
		}
	}

	private static boolean containsIdentity(
		final List<GameObject> gameObjects,
		final GameObject target) {
		for (GameObject gameObject : gameObjects) {
			if (gameObject == target) {
				return true;
			}
		}
		return false;
	}

	private static boolean removeIdentity(
		final List<GameObject> gameObjects,
		final GameObject target) {
		if (gameObjects == null) {
			return false;
		}
		for (int index = 0; index < gameObjects.size(); index++) {
			if (gameObjects.get(index) == target) {
				gameObjects.remove(index);
				return true;
			}
		}
		return false;
	}

	private static WorldRegionWindow requireBoundedWindow(
		final WorldRegionWindow window) {
		WorldRegionWindow checked = Objects.requireNonNull(window, "window");
		if (checked.getRegionCount()
			> RegionManager.MAX_LAYERED_REGIONS_PER_INTEREST_OWNER) {
			throw new IllegalArgumentException(
				"Layered spatial window exceeds its bounded region count");
		}
		return checked;
	}

	public static final class Snapshot {
		private final WorldRegionWindow window;
		private final long version;
		private final long objectVersion;
		private final List<Entity> entities;

		private Snapshot(
			final WorldRegionWindow window,
			final long version,
			final long objectVersion,
			final List<Entity> entities) {
			this.window = window;
			this.version = version;
			this.objectVersion = objectVersion;
			this.entities = Collections.unmodifiableList(entities);
		}

		public WorldRegionWindow getWindow() {
			return window;
		}

		public long getVersion() {
			return version;
		}

		public long getObjectVersion() {
			return objectVersion;
		}

		public List<Entity> getEntities() {
			return entities;
		}
	}

	public static final class GameObjectSnapshot {
		private final WorldRegionWindow window;
		private final long objectVersion;
		private final List<GameObject> gameObjects;

		private GameObjectSnapshot(
			final WorldRegionWindow window,
			final long objectVersion,
			final List<GameObject> gameObjects) {
			this.window = window;
			this.objectVersion = objectVersion;
			this.gameObjects = Collections.unmodifiableList(gameObjects);
		}

		public WorldRegionWindow getWindow() {
			return window;
		}

		public long getObjectVersion() {
			return objectVersion;
		}

		public List<GameObject> getGameObjects() {
			return gameObjects;
		}
	}

	@FunctionalInterface
	public interface GameObjectTilePredicate {
		boolean matches(GameObject gameObject, int tileX, int tileY);
	}
}
