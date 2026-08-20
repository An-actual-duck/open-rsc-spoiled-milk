package com.openrsc.server.model.world;

import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc.GameTickEventRestorationTransientRollbackSnapshot.CollisionContribution;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generation-fenced, level-qualified package-object identity and collision
 * overlay.
 *
 * <p>This registry never mutates a packed Region. Registration, replacement,
 * and removal stage every affected collision tile before committing identity,
 * slot, and collision state together.</p>
 */
public final class NativeLayeredGameObjectRegistry<T> {
	private final Object lock = new Object();
	private final Map<String, Entry<T>> placements =
		new HashMap<String, Entry<T>>();
	private final Map<Slot, Entry<T>> slots =
		new HashMap<Slot, Entry<T>>();
	private final Map<WorldLocation, CollisionAggregate> collision =
		new HashMap<WorldLocation, CollisionAggregate>();
	private final Map<WorldLocation, Integer> npcBlockingScenery =
		new HashMap<WorldLocation, Integer>();
	private long generation = 1L;

	public long getGeneration() {
		synchronized (lock) {
			return generation;
		}
	}

	public T register(
		final long expectedGeneration,
		final String placementId,
		final WorldLocation location,
		final int type,
		final int direction,
		final T instance,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint,
		final Collection<WorldLocation> npcBlockingSceneryTiles) {
		return register(expectedGeneration, placementId, location, type,
			direction, instance, footprint, npcBlockingSceneryTiles, false, false);
	}

	public T register(
		final long expectedGeneration,
		final String placementId,
		final WorldLocation location,
		final int type,
		final int direction,
		final T instance,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint,
		final Collection<WorldLocation> npcBlockingSceneryTiles,
		final boolean combatProjectileHardCover) {
		return register(
			expectedGeneration, placementId, location, type, direction,
			instance, footprint, npcBlockingSceneryTiles,
			combatProjectileHardCover, false);
	}

	public T register(
		final long expectedGeneration,
		final String placementId,
		final WorldLocation location,
		final int type,
		final int direction,
		final T instance,
		final GameTickEventRestorationCollisionFootprintPlanner.Result footprint,
		final Collection<WorldLocation> npcBlockingSceneryTiles,
		final boolean combatProjectileHardCover,
		final boolean enemyProjectileFenceCover) {
		Entry<T> proposed = entry(
			placementId, location, type, direction, instance, footprint,
			npcBlockingSceneryTiles, combatProjectileHardCover,
			enemyProjectileFenceCover);
		synchronized (lock) {
			if (expectedGeneration != generation) {
				return null;
			}
			if (placements.containsKey(proposed.placementId)) {
				throw new IllegalStateException(
					"Native layered placement ID is already active: "
						+ proposed.placementId);
			}
			if (slots.containsKey(proposed.slot)) {
				throw new IllegalStateException(
					"Native layered object slot is already occupied: "
						+ proposed.location);
			}
			Map<WorldLocation, CollisionAggregate> staged =
				stageCollision(null, proposed);
			Map<WorldLocation, Integer> stagedNpcBlockingScenery =
				stageNpcBlockingScenery(null, proposed);
			placements.put(proposed.placementId, proposed);
			slots.put(proposed.slot, proposed);
			commitCollision(staged);
			commitNpcBlockingScenery(stagedNpcBlockingScenery);
			return proposed.instance;
		}
	}

	public T replace(
		final long expectedGeneration,
		final String placementId,
		final T expectedInstance,
		final WorldLocation location,
		final int type,
		final int direction,
		final T replacement,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint,
		final Collection<WorldLocation> npcBlockingSceneryTiles) {
		return replace(expectedGeneration, placementId, expectedInstance,
			location, type, direction, replacement, footprint,
			npcBlockingSceneryTiles, false, false);
	}

	public T replace(
		final long expectedGeneration,
		final String placementId,
		final T expectedInstance,
		final WorldLocation location,
		final int type,
		final int direction,
		final T replacement,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint,
		final Collection<WorldLocation> npcBlockingSceneryTiles,
		final boolean combatProjectileHardCover) {
		return replace(expectedGeneration, placementId, expectedInstance, location,
			type, direction, replacement, footprint, npcBlockingSceneryTiles,
			combatProjectileHardCover, false);
	}

	public T replace(
		final long expectedGeneration,
		final String placementId,
		final T expectedInstance,
		final WorldLocation location,
		final int type,
		final int direction,
		final T replacement,
		final GameTickEventRestorationCollisionFootprintPlanner.Result footprint,
		final Collection<WorldLocation> npcBlockingSceneryTiles,
		final boolean combatProjectileHardCover,
		final boolean enemyProjectileFenceCover) {
		Entry<T> proposed = entry(
			placementId, location, type, direction, replacement, footprint,
			npcBlockingSceneryTiles, combatProjectileHardCover,
			enemyProjectileFenceCover);
		T checkedExpected = Objects.requireNonNull(
			expectedInstance, "expectedInstance");
		synchronized (lock) {
			if (expectedGeneration != generation) {
				return null;
			}
			Entry<T> current = placements.get(proposed.placementId);
			if (current == null || current.instance != checkedExpected) {
				throw new IllegalStateException(
					"Native layered replacement source is not current: "
						+ proposed.placementId);
			}
			Entry<T> occupant = slots.get(proposed.slot);
			if (occupant != null && occupant != current) {
				throw new IllegalStateException(
					"Native layered replacement slot is occupied: "
						+ proposed.location);
			}
			Map<WorldLocation, CollisionAggregate> staged =
				stageCollision(current, proposed);
			Map<WorldLocation, Integer> stagedNpcBlockingScenery =
				stageNpcBlockingScenery(current, proposed);
			placements.put(proposed.placementId, proposed);
			slots.remove(current.slot);
			slots.put(proposed.slot, proposed);
			commitCollision(staged);
			commitNpcBlockingScenery(stagedNpcBlockingScenery);
			return proposed.instance;
		}
	}

	public T unregister(
		final long expectedGeneration,
		final String placementId,
		final T expectedInstance) {
		String checkedId = Objects.requireNonNull(
			placementId, "placementId");
		T checkedExpected = Objects.requireNonNull(
			expectedInstance, "expectedInstance");
		synchronized (lock) {
			if (expectedGeneration != generation) {
				return null;
			}
			Entry<T> current = placements.get(checkedId);
			if (current == null || current.instance != checkedExpected) {
				throw new IllegalStateException(
					"Native layered removal source is not current: "
						+ checkedId);
			}
			Map<WorldLocation, CollisionAggregate> staged =
				stageCollision(current, null);
			Map<WorldLocation, Integer> stagedNpcBlockingScenery =
				stageNpcBlockingScenery(current, null);
			placements.remove(checkedId);
			slots.remove(current.slot);
			commitCollision(staged);
			commitNpcBlockingScenery(stagedNpcBlockingScenery);
			return current.instance;
		}
	}

	public TileValue applyCollision(
		final WorldLocation location,
		final TileValue tile) {
		WorldLocation checkedLocation = Objects.requireNonNull(
			location, "location");
		TileValue checkedTile = Objects.requireNonNull(tile, "tile");
		synchronized (lock) {
			CollisionAggregate aggregate = collision.get(checkedLocation);
			if (aggregate != null) {
				aggregate.apply(checkedTile);
			}
			return checkedTile;
		}
	}

	/**
	 * Returns whether an NPC-blocking scenery footprint occupies one exact
	 * world-space/level-qualified tile.
	 *
	 * <p>The footprint is staged atomically with placement and collision state,
	 * so movement checks do not need to scan a broad region window or
	 * reconstruct object dimensions.</p>
	 */
	public boolean hasNpcBlockingSceneryAt(final WorldLocation location) {
		WorldLocation checkedLocation = Objects.requireNonNull(
			location, "location");
		synchronized (lock) {
			Integer count = npcBlockingScenery.get(checkedLocation);
			return count != null && count.intValue() > 0;
		}
	}

	public int size() {
		synchronized (lock) {
			return placements.size();
		}
	}

	public int countType(final int type) {
		if (type != 0 && type != 1) {
			throw new IllegalArgumentException(
				"Native layered object type must be 0 or 1");
		}
		synchronized (lock) {
			int count = 0;
			for (Entry<T> entry : placements.values()) {
				if (entry.type == type) {
					count++;
				}
			}
			return count;
		}
	}

	public int getCollisionTileCount() {
		synchronized (lock) {
			return collision.size();
		}
	}

	public T find(final String placementId) {
		synchronized (lock) {
			Entry<T> entry = placements.get(placementId);
			return entry == null ? null : entry.instance;
		}
	}

	public T find(
		final WorldLocation location,
		final int type,
		final int direction) {
		synchronized (lock) {
			Entry<T> entry = slots.get(new Slot(
				Objects.requireNonNull(location, "location"),
				type,
				direction));
			return entry == null ? null : entry.instance;
		}
	}

	public void reset() {
		synchronized (lock) {
			placements.clear();
			slots.clear();
			collision.clear();
			npcBlockingScenery.clear();
			generation = Math.addExact(generation, 1L);
		}
	}

	private Entry<T> entry(
		final String placementId,
		final WorldLocation location,
		final int type,
		final int direction,
		final T instance,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint,
		final Collection<WorldLocation> npcBlockingSceneryTiles,
		final boolean combatProjectileHardCover,
		final boolean enemyProjectileFenceCover) {
		String checkedId = Objects.requireNonNull(
			placementId, "placementId");
		WorldLocation checkedLocation = Objects.requireNonNull(
			location, "location");
		T checkedInstance = Objects.requireNonNull(instance, "instance");
		GameTickEventRestorationCollisionFootprintPlanner.Result
			checkedFootprint = Objects.requireNonNull(
				footprint, "footprint");
		if (checkedId.isEmpty() || (type != 0 && type != 1)
			|| direction < 0 || direction > (type == 0 ? 8 : 7)
			|| !checkedFootprint.isFootprintAvailable()
			|| checkedFootprint.getOperation() != Operation.REGISTER
			|| checkedFootprint.isLegacySaturatingUnregister()) {
			throw new IllegalArgumentException(
				"Native layered object registration is invalid");
		}
		Set<WorldLocation> checkedNpcBlockingSceneryTiles =
			new LinkedHashSet<WorldLocation>();
		for (WorldLocation tile : Objects.requireNonNull(
				npcBlockingSceneryTiles, "npcBlockingSceneryTiles")) {
			WorldLocation checkedTile = Objects.requireNonNull(
				tile, "npcBlockingSceneryTile");
			if (type != 0
				|| !checkedLocation.getWorldSpace().equals(
					checkedTile.getWorldSpace())
				|| checkedLocation.getCoordinate().getLevel()
					!= checkedTile.getCoordinate().getLevel()) {
				throw new IllegalArgumentException(
					"Native layered NPC-blocking scenery footprint is invalid");
			}
			checkedNpcBlockingSceneryTiles.add(checkedTile);
		}
		return new Entry<T>(
			checkedId, checkedLocation, type, direction,
			checkedInstance, checkedFootprint,
			checkedNpcBlockingSceneryTiles, combatProjectileHardCover,
			enemyProjectileFenceCover);
	}

	private Map<WorldLocation, CollisionAggregate> stageCollision(
		final Entry<T> removed,
		final Entry<T> added) {
		Set<WorldLocation> touched = new LinkedHashSet<WorldLocation>();
		if (removed != null) {
			collectCollisionLocations(
				removed.location, removed.footprint, touched);
		}
		if (added != null) {
			collectCollisionLocations(
				added.location, added.footprint, touched);
		}
		Map<WorldLocation, CollisionAggregate> staged =
			new HashMap<WorldLocation, CollisionAggregate>();
		for (WorldLocation location : touched) {
			CollisionAggregate aggregate = collision.get(location);
			staged.put(
				location,
				aggregate == null
					? new CollisionAggregate() : aggregate.copy());
		}
		if (removed != null) {
			mutateCollision(
				removed.location, removed.footprint, staged, false,
				removed.combatProjectileHardCover, removed.enemyProjectileFenceCover);
		}
		if (added != null) {
			mutateCollision(
				added.location, added.footprint, staged, true,
				added.combatProjectileHardCover, added.enemyProjectileFenceCover);
		}
		return staged;
	}

	private static void collectCollisionLocations(
		final WorldLocation origin,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint,
		final Set<WorldLocation> locations) {
		for (CollisionContribution contribution
			: footprint.getContributions()) {
			locations.add(collisionLocation(origin, contribution));
		}
	}

	private static void mutateCollision(
		final WorldLocation origin,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint,
		final Map<WorldLocation, CollisionAggregate> staged,
		final boolean add,
		final boolean combatProjectileHardCover,
		final boolean enemyProjectileFenceCover) {
		for (CollisionContribution contribution
			: footprint.getContributions()) {
			WorldLocation location = collisionLocation(origin, contribution);
			CollisionAggregate aggregate = staged.get(location);
			if (aggregate == null) {
				throw new IllegalStateException(
					"Native layered collision staging is incomplete");
			}
			if (add) {
				aggregate.add(contribution, combatProjectileHardCover, enemyProjectileFenceCover);
			} else {
				aggregate.remove(contribution, combatProjectileHardCover, enemyProjectileFenceCover);
			}
		}
	}

	private static WorldLocation collisionLocation(
		final WorldLocation origin,
		final CollisionContribution contribution) {
		return new WorldLocation(
			origin.getWorldSpace(),
			new WorldCoordinate(
				contribution.getX(),
				contribution.getY(),
				origin.getCoordinate().getLevel()));
	}

	private void commitCollision(
		final Map<WorldLocation, CollisionAggregate> staged) {
		for (Map.Entry<WorldLocation, CollisionAggregate> value
			: staged.entrySet()) {
			if (value.getValue().isEmpty()) {
				collision.remove(value.getKey());
			} else {
				collision.put(value.getKey(), value.getValue());
			}
		}
	}

	private Map<WorldLocation, Integer> stageNpcBlockingScenery(
		final Entry<T> removed,
		final Entry<T> added) {
		Set<WorldLocation> touched = new LinkedHashSet<WorldLocation>();
		if (removed != null) {
			touched.addAll(removed.npcBlockingSceneryTiles);
		}
		if (added != null) {
			touched.addAll(added.npcBlockingSceneryTiles);
		}
		Map<WorldLocation, Integer> staged =
			new HashMap<WorldLocation, Integer>();
		for (WorldLocation location : touched) {
			Integer count = npcBlockingScenery.get(location);
			staged.put(
				location,
				Integer.valueOf(count == null ? 0 : count.intValue()));
		}
		if (removed != null) {
			mutateNpcBlockingScenery(
				removed.npcBlockingSceneryTiles, staged, false);
		}
		if (added != null) {
			mutateNpcBlockingScenery(
				added.npcBlockingSceneryTiles, staged, true);
		}
		return staged;
	}

	private static void mutateNpcBlockingScenery(
		final Set<WorldLocation> footprint,
		final Map<WorldLocation, Integer> staged,
		final boolean add) {
		for (WorldLocation location : footprint) {
			Integer current = staged.get(location);
			if (current == null) {
				throw new IllegalStateException(
					"Native layered NPC-blocking scenery staging is incomplete");
			}
			int count = add
				? Math.addExact(current.intValue(), 1)
				: Math.subtractExact(current.intValue(), 1);
			if (count < 0) {
				throw new IllegalStateException(
					"Native layered NPC-blocking scenery contribution underflow");
			}
			staged.put(location, Integer.valueOf(count));
		}
	}

	private void commitNpcBlockingScenery(
		final Map<WorldLocation, Integer> staged) {
		for (Map.Entry<WorldLocation, Integer> value : staged.entrySet()) {
			if (value.getValue().intValue() == 0) {
				npcBlockingScenery.remove(value.getKey());
			} else {
				npcBlockingScenery.put(value.getKey(), value.getValue());
			}
		}
	}

	private static final class Entry<T> {
		private final String placementId;
		private final WorldLocation location;
		private final int type;
		private final T instance;
		private final Slot slot;
		private final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint;
		private final Set<WorldLocation> npcBlockingSceneryTiles;
		private final boolean combatProjectileHardCover;
		private final boolean enemyProjectileFenceCover;

		private Entry(
			final String placementId,
			final WorldLocation location,
			final int type,
			final int direction,
			final T instance,
			final GameTickEventRestorationCollisionFootprintPlanner.Result
				footprint,
			final Set<WorldLocation> npcBlockingSceneryTiles,
			final boolean combatProjectileHardCover,
			final boolean enemyProjectileFenceCover) {
			this.placementId = placementId;
			this.location = location;
			this.type = type;
			this.instance = instance;
			this.slot = new Slot(location, type, direction);
			this.footprint = footprint;
			this.npcBlockingSceneryTiles = Collections.unmodifiableSet(
				new LinkedHashSet<WorldLocation>(
					npcBlockingSceneryTiles));
			this.combatProjectileHardCover = combatProjectileHardCover;
			this.enemyProjectileFenceCover = enemyProjectileFenceCover;
		}
	}

	private static final class Slot {
		private final WorldLocation location;
		private final int type;
		private final int direction;

		private Slot(
			final WorldLocation location,
			final int type,
			final int direction) {
			this.location = location;
			this.type = type;
			this.direction = type == 0 ? 0 : direction;
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Slot)) {
				return false;
			}
			Slot slot = (Slot) other;
			return type == slot.type && direction == slot.direction
				&& location.equals(slot.location);
		}

		@Override
		public int hashCode() {
			int result = location.hashCode();
			result = 31 * result + type;
			result = 31 * result + direction;
			return result;
		}
	}

	private static final class CollisionAggregate {
		private int blockingSceneryCount;
		private final int[] dynamicCollisionCounts = new int[6];
		private int dynamicProjectileCount;
		private int blockingCombatProjectileCount;
		private final int[] combatProjectileCollisionCounts = new int[6];
		private int blockingEnemyProjectileFenceCount;
		private final int[] enemyProjectileFenceCollisionCounts = new int[6];

		private CollisionAggregate copy() {
			CollisionAggregate copy = new CollisionAggregate();
			copy.blockingSceneryCount = blockingSceneryCount;
			System.arraycopy(
				dynamicCollisionCounts, 0,
				copy.dynamicCollisionCounts, 0,
				dynamicCollisionCounts.length);
			copy.dynamicProjectileCount = dynamicProjectileCount;
			copy.blockingCombatProjectileCount = blockingCombatProjectileCount;
			System.arraycopy(
				combatProjectileCollisionCounts, 0,
				copy.combatProjectileCollisionCounts, 0,
				combatProjectileCollisionCounts.length);
			copy.blockingEnemyProjectileFenceCount =
				blockingEnemyProjectileFenceCount;
			System.arraycopy(enemyProjectileFenceCollisionCounts, 0,
				copy.enemyProjectileFenceCollisionCounts, 0,
				enemyProjectileFenceCollisionCounts.length);
			return copy;
		}

		private void add(
			final CollisionContribution contribution,
			final boolean combatProjectileHardCover,
			final boolean enemyProjectileFenceCover) {
			blockingSceneryCount = Math.addExact(
				blockingSceneryCount,
				contribution.getBlockingSceneryCount());
			for (int bit = 0; bit < dynamicCollisionCounts.length; bit++) {
				dynamicCollisionCounts[bit] = Math.addExact(
					dynamicCollisionCounts[bit],
					contribution.getDynamicCollisionCount(bit));
			}
			if (enemyProjectileFenceCover) {
				blockingEnemyProjectileFenceCount = Math.addExact(
					blockingEnemyProjectileFenceCount,
					contribution.getBlockingSceneryCount());
				for (int bit = 0;
						bit < enemyProjectileFenceCollisionCounts.length;
						bit++) {
					enemyProjectileFenceCollisionCounts[bit] = Math.addExact(
						enemyProjectileFenceCollisionCounts[bit],
						contribution.getDynamicCollisionCount(bit));
				}
			}
			dynamicProjectileCount = Math.addExact(
				dynamicProjectileCount,
				contribution.getDynamicProjectileCount());
			if (combatProjectileHardCover) {
				blockingCombatProjectileCount = Math.addExact(
					blockingCombatProjectileCount,
					contribution.getBlockingSceneryCount());
				for (int bit = 0;
						bit < combatProjectileCollisionCounts.length; bit++) {
					combatProjectileCollisionCounts[bit] = Math.addExact(
						combatProjectileCollisionCounts[bit],
						contribution.getDynamicCollisionCount(bit));
				}
			}
		}

		private void remove(
			final CollisionContribution contribution,
			final boolean combatProjectileHardCover,
			final boolean enemyProjectileFenceCover) {
			blockingSceneryCount = subtract(
				blockingSceneryCount,
				contribution.getBlockingSceneryCount());
			for (int bit = 0; bit < dynamicCollisionCounts.length; bit++) {
				dynamicCollisionCounts[bit] = subtract(
					dynamicCollisionCounts[bit],
					contribution.getDynamicCollisionCount(bit));
			}
			if (enemyProjectileFenceCover) {
				blockingEnemyProjectileFenceCount = subtract(
					blockingEnemyProjectileFenceCount,
					contribution.getBlockingSceneryCount());
				for (int bit = 0;
						bit < enemyProjectileFenceCollisionCounts.length;
						bit++) {
					enemyProjectileFenceCollisionCounts[bit] = subtract(
						enemyProjectileFenceCollisionCounts[bit],
						contribution.getDynamicCollisionCount(bit));
				}
			}
			dynamicProjectileCount = subtract(
				dynamicProjectileCount,
				contribution.getDynamicProjectileCount());
			if (combatProjectileHardCover) {
				blockingCombatProjectileCount = subtract(
					blockingCombatProjectileCount,
					contribution.getBlockingSceneryCount());
				for (int bit = 0;
						bit < combatProjectileCollisionCounts.length; bit++) {
					combatProjectileCollisionCounts[bit] = subtract(
						combatProjectileCollisionCounts[bit],
						contribution.getDynamicCollisionCount(bit));
				}
			}
		}

		private static int subtract(final int current, final int removed) {
			int result = Math.subtractExact(current, removed);
			if (result < 0) {
				throw new IllegalStateException(
					"Native layered collision contribution underflow");
			}
			return result;
		}

		private boolean isEmpty() {
			if (blockingSceneryCount != 0 || dynamicProjectileCount != 0
					|| blockingCombatProjectileCount != 0
					|| blockingEnemyProjectileFenceCount != 0) {
				return false;
			}
			for (int count : dynamicCollisionCounts) {
				if (count != 0) {
					return false;
				}
			}
			for (int count : combatProjectileCollisionCounts) {
				if (count != 0) {
					return false;
				}
			}
			for (int count : enemyProjectileFenceCollisionCounts) {
				if (count != 0) {
					return false;
				}
			}
			return true;
		}

		private void apply(final TileValue tile) {
			for (int count = 0; count < blockingSceneryCount; count++) {
				tile.addBlockingScenery();
			}
			for (int bit = 0; bit < dynamicCollisionCounts.length; bit++) {
				for (int count = 0;
					count < dynamicCollisionCounts[bit]; count++) {
					tile.addDynamicCollision(1 << bit);
				}
			}
			for (int count = 0; count < dynamicProjectileCount; count++) {
				tile.addDynamicProjectileBlock();
			}
			for (int count = 0; count < blockingCombatProjectileCount; count++) {
				tile.addCombatProjectileCollision(CollisionFlag.FULL_BLOCK_C);
			}
			for (int bit = 0;
					bit < combatProjectileCollisionCounts.length; bit++) {
				for (int count = 0;
						count < combatProjectileCollisionCounts[bit]; count++) {
					tile.addCombatProjectileCollision(1 << bit);
				}
			}
			for (int count = 0;
					count < blockingEnemyProjectileFenceCount;
					count++) {
				tile.addEnemyProjectileFenceCollision(CollisionFlag.FULL_BLOCK_C);
			}
			for (int bit = 0;
					bit < enemyProjectileFenceCollisionCounts.length;
					bit++) {
				for (int count = 0;
						count < enemyProjectileFenceCollisionCounts[bit];
						count++) {
					tile.addEnemyProjectileFenceCollision(1 << bit);
				}
			}
		}
	}
}
