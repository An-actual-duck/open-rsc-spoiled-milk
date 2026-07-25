package com.openrsc.server.model.world;

import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc.GameTickEventRestorationTransientRollbackSnapshot.CollisionContribution;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.TileValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Level-qualified package-object identity and collision overlay.
 *
 * <p>This registry never mutates a packed Region. It retains exact object
 * identity and canonical legacy collision contributions, then composes those
 * contributions onto a newly decoded native terrain tile.</p>
 */
public final class NativeLayeredGameObjectRegistry<T> {
	private final Object lock = new Object();
	private final Map<String, Entry<T>> placements =
		new HashMap<String, Entry<T>>();
	private final Map<Slot, Entry<T>> slots =
		new HashMap<Slot, Entry<T>>();
	private final Map<WorldLocation, CollisionAggregate> collision =
		new HashMap<WorldLocation, CollisionAggregate>();

	public T register(
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
			|| direction < 0 || direction > 7
			|| !checkedFootprint.isFootprintAvailable()
			|| checkedFootprint.getOperation() != Operation.REGISTER
			|| checkedFootprint.isLegacySaturatingUnregister()) {
			throw new IllegalArgumentException(
				"Native layered object registration is invalid");
		}
		Slot slot = new Slot(checkedLocation, type, direction);
		synchronized (lock) {
			if (placements.containsKey(checkedId)) {
				throw new IllegalStateException(
					"Native layered placement ID is already active: "
						+ checkedId);
			}
			if (slots.containsKey(slot)) {
				throw new IllegalStateException(
					"Native layered object slot is already occupied: "
						+ checkedLocation);
			}
			Map<WorldLocation, CollisionAggregate> projected =
				projectCollision(checkedLocation, checkedFootprint);
			Entry<T> entry = new Entry<T>(
				checkedId, checkedLocation, type, direction,
				checkedInstance, checkedFootprint.getContributionTileCount());
			placements.put(checkedId, entry);
			slots.put(slot, entry);
			collision.putAll(projected);
			return checkedInstance;
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
		}
	}

	private Map<WorldLocation, CollisionAggregate> projectCollision(
		final WorldLocation origin,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			footprint) {
		Map<WorldLocation, CollisionAggregate> projected =
			new HashMap<WorldLocation, CollisionAggregate>(collision);
		for (CollisionContribution contribution
			: footprint.getContributions()) {
			WorldLocation location = new WorldLocation(
				origin.getWorldSpace(),
				new WorldCoordinate(
					contribution.getX(),
					contribution.getY(),
					origin.getCoordinate().getLevel()));
			CollisionAggregate aggregate = projected.get(location);
			if (aggregate == null) {
				aggregate = new CollisionAggregate();
			} else {
				aggregate = aggregate.copy();
			}
			aggregate.add(contribution);
			projected.put(location, aggregate);
		}
		return projected;
	}

	private static final class Entry<T> {
		private final String placementId;
		private final WorldLocation location;
		private final int type;
		private final int direction;
		private final T instance;
		private final int collisionTileCount;

		private Entry(
			final String placementId,
			final WorldLocation location,
			final int type,
			final int direction,
			final T instance,
			final int collisionTileCount) {
			this.placementId = placementId;
			this.location = location;
			this.type = type;
			this.direction = direction;
			this.instance = instance;
			this.collisionTileCount = collisionTileCount;
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
