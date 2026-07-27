package com.openrsc.server.model.world;

import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc.GameTickEventRestorationTransientRollbackSnapshot.CollisionContribution;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.TileValue;

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
			footprint) {
		Entry<T> proposed = entry(
			placementId, location, type, direction, instance, footprint);
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
			placements.put(proposed.placementId, proposed);
			slots.put(proposed.slot, proposed);
			commitCollision(staged);
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
			footprint) {
		Entry<T> proposed = entry(
			placementId, location, type, direction, replacement, footprint);
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
			placements.put(proposed.placementId, proposed);
			slots.remove(current.slot);
			slots.put(proposed.slot, proposed);
			commitCollision(staged);
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
			placements.remove(checkedId);
			slots.remove(current.slot);
			commitCollision(staged);
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

	public void reset() {
		synchronized (lock) {
			placements.clear();
			slots.clear();
			collision.clear();
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
			footprint) {
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
		return new Entry<T>(
			checkedId, checkedLocation, type, direction,
			checkedInstance, checkedFootprint);
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
				removed.location, removed.footprint, staged, false);
		}
		if (added != null) {
			mutateCollision(
				added.location, added.footprint, staged, true);
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
		final boolean add) {
		for (CollisionContribution contribution
			: footprint.getContributions()) {
			WorldLocation location = collisionLocation(origin, contribution);
			CollisionAggregate aggregate = staged.get(location);
			if (aggregate == null) {
				throw new IllegalStateException(
					"Native layered collision staging is incomplete");
			}
			if (add) {
				aggregate.add(contribution);
			} else {
				aggregate.remove(contribution);
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

	private static final class Entry<T> {
		private final String placementId;
		private final WorldLocation location;
		private final int type;
		private final T instance;
		private final Slot slot;
		private final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint;

		private Entry(
			final String placementId,
			final WorldLocation location,
			final int type,
			final int direction,
			final T instance,
			final GameTickEventRestorationCollisionFootprintPlanner.Result
				footprint) {
			this.placementId = placementId;
			this.location = location;
			this.type = type;
			this.instance = instance;
			this.slot = new Slot(location, type, direction);
			this.footprint = footprint;
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

		private CollisionAggregate copy() {
			CollisionAggregate copy = new CollisionAggregate();
			copy.blockingSceneryCount = blockingSceneryCount;
			System.arraycopy(
				dynamicCollisionCounts, 0,
				copy.dynamicCollisionCounts, 0,
				dynamicCollisionCounts.length);
			copy.dynamicProjectileCount = dynamicProjectileCount;
			return copy;
		}

		private void add(final CollisionContribution contribution) {
			blockingSceneryCount = Math.addExact(
				blockingSceneryCount,
				contribution.getBlockingSceneryCount());
			for (int bit = 0; bit < dynamicCollisionCounts.length; bit++) {
				dynamicCollisionCounts[bit] = Math.addExact(
					dynamicCollisionCounts[bit],
					contribution.getDynamicCollisionCount(bit));
			}
			dynamicProjectileCount = Math.addExact(
				dynamicProjectileCount,
				contribution.getDynamicProjectileCount());
		}

		private void remove(final CollisionContribution contribution) {
			blockingSceneryCount = subtract(
				blockingSceneryCount,
				contribution.getBlockingSceneryCount());
			for (int bit = 0; bit < dynamicCollisionCounts.length; bit++) {
				dynamicCollisionCounts[bit] = subtract(
					dynamicCollisionCounts[bit],
					contribution.getDynamicCollisionCount(bit));
			}
			dynamicProjectileCount = subtract(
				dynamicProjectileCount,
				contribution.getDynamicProjectileCount());
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
			if (blockingSceneryCount != 0 || dynamicProjectileCount != 0) {
				return false;
			}
			for (int count : dynamicCollisionCounts) {
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
		}
	}
}
