package com.openrsc.server.event.rsc;

import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.LegacyObjectProjectileCollisionPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure legacy-parity planner for one object's collision contribution.
 *
 * <p>The caller supplies detached constructor and definition scalars, including
 * the result of the legacy projectile-clipping classification. The planner
 * reproduces the register/unregister footprint without reading a World,
 * Region, GameObject, definition table, or TileValue. It does not acquire
 * Slice 130's boundary or apply the returned contribution.</p>
 */
public final class GameTickEventRestorationCollisionFootprintPlanner {
	private static final int SPECIAL_COLLISIONLESS_REGISTER_OBJECT_ID = 1147;

	private GameTickEventRestorationCollisionFootprintPlanner() { }

	/** Plans one bounded collision contribution or returns a typed refusal. */
	public static Result plan(
		final Operation operation,
		final ConstructorState constructor,
		final Definition definition,
		final boolean forceFullBlock,
		final WorldBounds worldBounds) {
		Operation checkedOperation = Objects.requireNonNull(operation, "operation");
		ConstructorState checkedConstructor = Objects.requireNonNull(
			constructor, "constructor");
		WorldBounds checkedBounds = Objects.requireNonNull(
			worldBounds, "worldBounds");
		if (forceFullBlock && checkedOperation != Operation.REGISTER) {
			return Result.refused(
				checkedOperation,
				Reason.FORCE_FULL_BLOCK_REQUIRES_REGISTER_OPERATION);
		}

		Accumulator accumulator = new Accumulator(checkedBounds);
		if (!accumulator.requireInWorld(
				checkedConstructor.getX(), checkedConstructor.getY())) {
			return Result.refused(
				checkedOperation, Reason.OUT_OF_WORLD_EFFECT);
		}

		// Legacy registration returns before consulting a definition for 1147.
		if (checkedOperation == Operation.REGISTER
			&& checkedConstructor.getObjectId()
				== SPECIAL_COLLISIONLESS_REGISTER_OBJECT_ID) {
			if (forceFullBlock && !accumulator.add(
					checkedConstructor.getX(), checkedConstructor.getY(), 1, 0, 0)) {
				return Result.refused(
					checkedOperation, accumulator.getRefusalReason());
			}
			return accumulator.complete(checkedOperation, checkedConstructor);
		}

		if (definition == null) {
			return Result.refused(
				checkedOperation, Reason.DEFINITION_UNAVAILABLE);
		}
		if (definition.getObjectType() != checkedConstructor.getType()) {
			return Result.refused(
				checkedOperation, Reason.DEFINITION_KIND_MISMATCH);
		}

		boolean planned;
		if (checkedConstructor.getType() == ConstructorState.SCENERY) {
			planned = planScenery(
				checkedConstructor, definition, accumulator);
		} else {
			planned = planBoundary(
				checkedConstructor, definition, accumulator);
		}
		if (!planned) {
			return Result.refused(
				checkedOperation, accumulator.getRefusalReason());
		}
		if (forceFullBlock && !accumulator.add(
				checkedConstructor.getX(), checkedConstructor.getY(), 1, 0, 0)) {
			return Result.refused(
				checkedOperation, accumulator.getRefusalReason());
		}
		return accumulator.complete(checkedOperation, checkedConstructor);
	}

	private static boolean planScenery(
		final ConstructorState constructor,
		final Definition definition,
		final Accumulator accumulator) {
		if (definition.getCollisionType() != 1
			&& definition.getCollisionType() != 2) {
			return true;
		}
		int width;
		int height;
		if (constructor.getDirection() == 0
			|| constructor.getDirection() == 4) {
			width = definition.getWidth();
			height = definition.getHeight();
		} else {
			width = definition.getHeight();
			height = definition.getWidth();
		}
		long footprintSize = (long) width * (long) height;
		if (footprintSize
			> GameTickEventRestorationTransientRollbackSnapshot
				.MAXIMUM_COLLISION_CONTRIBUTION_TILES) {
			return accumulator.refuse(Reason.CONTRIBUTION_TILE_LIMIT_EXCEEDED);
		}
		for (int offsetX = 0; offsetX < width; offsetX++) {
			for (int offsetY = 0; offsetY < height; offsetY++) {
				int x;
				int y;
				try {
					x = Math.addExact(constructor.getX(), offsetX);
					y = Math.addExact(constructor.getY(), offsetY);
				} catch (ArithmeticException overflow) {
					return accumulator.refuse(Reason.OUT_OF_WORLD_EFFECT);
				}
				if (definition.isProjectileClipAllowed()
					&& !addProjectileContribution(
						x, y, constructor.getDirection(),
						definition.getCollisionType() == 1, accumulator)) {
					return false;
				}
				if (definition.getCollisionType() == 1) {
					if (!accumulator.add(x, y, 1, 0, 0)) {
						return false;
					}
				} else if (!addDirectionalSceneryCollision(
						x, y, constructor.getDirection(), accumulator)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean planBoundary(
		final ConstructorState constructor,
		final Definition definition,
		final Accumulator accumulator) {
		if (definition.getCollisionType() != 1) {
			return true;
		}
		int x = constructor.getX();
		int y = constructor.getY();
		int direction = constructor.getDirection();
		if (definition.isProjectileClipAllowed()
			&& !addProjectileContribution(
				x, y, direction, false, accumulator)) {
			return false;
		}
		if (direction == 0) {
			return accumulator.add(x, y, 0, CollisionFlag.WALL_NORTH, 0)
				&& accumulator.addOffset(
					x, y, 0, -1, 0, CollisionFlag.WALL_SOUTH, 0);
		}
		if (direction == 1) {
			return accumulator.add(x, y, 0, CollisionFlag.WALL_EAST, 0)
				&& accumulator.addOffset(
					x, y, -1, 0, 0, CollisionFlag.WALL_WEST, 0);
		}
		if (direction == 2) {
			return accumulator.add(
				x, y, 0, CollisionFlag.FULL_BLOCK_A, 0);
		}
		if (direction == 3) {
			return accumulator.add(
				x, y, 0, CollisionFlag.FULL_BLOCK_B, 0);
		}
		return true;
	}

	private static boolean addDirectionalSceneryCollision(
		final int x,
		final int y,
		final int direction,
		final Accumulator accumulator) {
		if (direction == 0) {
			return accumulator.add(x, y, 0, CollisionFlag.WALL_EAST, 0)
				&& accumulator.addOffset(
					x, y, -1, 0, 0, CollisionFlag.WALL_WEST, 0);
		}
		if (direction == 2) {
			return accumulator.add(x, y, 0, CollisionFlag.WALL_SOUTH, 0)
				&& accumulator.addOffset(
					x, y, 0, 1, 0, CollisionFlag.WALL_NORTH, 0);
		}
		if (direction == 4) {
			return accumulator.add(x, y, 0, CollisionFlag.WALL_WEST, 0)
				&& accumulator.addOffset(
					x, y, 1, 0, 0, CollisionFlag.WALL_EAST, 0);
		}
		if (direction == 6) {
			return accumulator.add(x, y, 0, CollisionFlag.WALL_NORTH, 0)
				&& accumulator.addOffset(
					x, y, 0, -1, 0, CollisionFlag.WALL_SOUTH, 0);
		}
		return true;
	}

	private static boolean addProjectileContribution(
		final int x,
		final int y,
		final int direction,
		final boolean fullBlockingScenery,
		final Accumulator accumulator) {
		if (!accumulator.add(x, y, 0, 0, 1)) {
			return false;
		}
		if (fullBlockingScenery) {
			return true;
		}
		if (direction == 0) {
			return accumulator.addOffset(x, y, -1, 0, 0, 0, 1);
		}
		if (direction == 2) {
			return accumulator.addOffset(x, y, 0, 1, 0, 0, 1);
		}
		if (direction == 4) {
			return accumulator.addOffset(x, y, 1, 0, 0, 0, 1);
		}
		if (direction == 6) {
			return accumulator.addOffset(x, y, 0, -1, 0, 0, 1);
		}
		return true;
	}

	public enum Operation {
		REGISTER,
		UNREGISTER
	}

	public enum Outcome {
		REFUSED,
		FOOTPRINT_AVAILABLE
	}

	public enum Reason {
		DEFINITION_UNAVAILABLE,
		DEFINITION_KIND_MISMATCH,
		FORCE_FULL_BLOCK_REQUIRES_REGISTER_OPERATION,
		OUT_OF_WORLD_EFFECT,
		CONTRIBUTION_TILE_LIMIT_EXCEEDED,
		FOOTPRINT_AVAILABLE
	}

	/** Detached GameObject constructor scalars. */
	public static final class ConstructorState {
		public static final int SCENERY = 0;
		public static final int BOUNDARY = 1;

		private final int objectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;

		private ConstructorState(
			final int objectId,
			final int x,
			final int y,
			final int direction,
			final int type) {
			if (objectId < 0 || x < 0 || y < 0
				|| direction < 0 || direction > 7
				|| (type != SCENERY && type != BOUNDARY)) {
				throw new IllegalArgumentException(
					"Collision constructor state is invalid");
			}
			this.objectId = objectId;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
		}

		public static ConstructorState of(
			final int objectId,
			final int x,
			final int y,
			final int direction,
			final int type) {
			return new ConstructorState(objectId, x, y, direction, type);
		}

		public int getObjectId() { return objectId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getDirection() { return direction; }
		public int getType() { return type; }
	}

	/** Detached definition values classified by the shared legacy policy. */
	public static final class Definition {
		private final int objectType;
		private final int collisionType;
		private final int width;
		private final int height;
		private final boolean projectileClipAllowed;

		private Definition(
			final int objectType,
			final int collisionType,
			final int width,
			final int height,
			final boolean projectileClipAllowed) {
			if ((objectType != ConstructorState.SCENERY
					&& objectType != ConstructorState.BOUNDARY)
				|| collisionType < 0 || width <= 0 || height <= 0) {
				throw new IllegalArgumentException(
					"Collision definition is invalid");
			}
			this.objectType = objectType;
			this.collisionType = collisionType;
			this.width = width;
			this.height = height;
			this.projectileClipAllowed = projectileClipAllowed;
		}

		public static Definition scenery(
			final int collisionType,
			final int width,
			final int height,
			final String name,
			final String[] projectileClipAllowedNames) {
			return new Definition(
				ConstructorState.SCENERY, collisionType, width, height,
				LegacyObjectProjectileCollisionPolicy
					.allowsSceneryProjectileClip(
						name, width, height, projectileClipAllowedNames));
		}

		public static Definition boundary(
			final int doorType,
			final String name,
			final String[] projectileClipAllowedNames) {
			return new Definition(
				ConstructorState.BOUNDARY, doorType, 1, 1,
				LegacyObjectProjectileCollisionPolicy
					.allowsBoundaryProjectileClip(
						name, projectileClipAllowedNames));
		}

		public int getObjectType() { return objectType; }
		public int getCollisionType() { return collisionType; }
		public int getWidth() { return width; }
		public int getHeight() { return height; }
		public boolean isProjectileClipAllowed() {
			return projectileClipAllowed;
		}
	}

	/** Explicit bounds keep the pure planner independent of one server config. */
	public static final class WorldBounds {
		private final int width;
		private final int height;

		private WorldBounds(final int width, final int height) {
			if (width <= 0 || height <= 0) {
				throw new IllegalArgumentException(
					"Collision world bounds are invalid");
			}
			this.width = width;
			this.height = height;
		}

		public static WorldBounds of(final int width, final int height) {
			return new WorldBounds(width, height);
		}

		boolean contains(final int x, final int y) {
			return x >= 0 && x < width && y >= 0 && y < height;
		}
	}

	/** Immutable, non-authoritative footprint result. */
	public static final class Result {
		private final Operation operation;
		private final Outcome outcome;
		private final Reason reason;
		private final List<GameTickEventRestorationTransientRollbackSnapshot
			.CollisionContribution> contributions;
		private final List<GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate> requiredRegions;

		private Result(
			final Operation operation,
			final Outcome outcome,
			final Reason reason,
			final List<GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution> contributions,
			final List<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> requiredRegions) {
			this.operation = Objects.requireNonNull(operation, "operation");
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.contributions = Collections.unmodifiableList(
				new ArrayList<GameTickEventRestorationTransientRollbackSnapshot
					.CollisionContribution>(contributions));
			this.requiredRegions = Collections.unmodifiableList(
				new ArrayList<GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate>(requiredRegions));
			boolean available = outcome == Outcome.FOOTPRINT_AVAILABLE;
			if (available != (reason == Reason.FOOTPRINT_AVAILABLE)
				|| !available && (!contributions.isEmpty()
					|| !requiredRegions.isEmpty())
				|| available && requiredRegions.isEmpty()) {
				throw new IllegalArgumentException(
					"Collision footprint result is inconsistent");
			}
		}

		private static Result refused(
			final Operation operation,
			final Reason reason) {
			return new Result(
				operation, Outcome.REFUSED, reason,
				Collections.<GameTickEventRestorationTransientRollbackSnapshot
					.CollisionContribution>emptyList(),
				Collections.<GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate>emptyList());
		}

		private static Result available(
			final Operation operation,
			final List<GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution> contributions,
			final List<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> regions) {
			return new Result(
				operation, Outcome.FOOTPRINT_AVAILABLE,
				Reason.FOOTPRINT_AVAILABLE, contributions, regions);
		}

		public Operation getOperation() { return operation; }
		public Outcome getOutcome() { return outcome; }
		public Reason getReason() { return reason; }
		public boolean isRefused() { return outcome == Outcome.REFUSED; }
		public boolean isFootprintAvailable() {
			return outcome == Outcome.FOOTPRINT_AVAILABLE;
		}
		public List<GameTickEventRestorationTransientRollbackSnapshot
			.CollisionContribution> getContributions() {
			return contributions;
		}
		public int getContributionTileCount() { return contributions.size(); }
		public List<GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate> getRequiredRegions() {
			return requiredRegions;
		}
		public int getRequiredRegionCount() { return requiredRegions.size(); }

		public boolean isRuntimeObservationPerformed() { return false; }
		public boolean isRuntimeBoundaryAcquired() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isMutationPerformed() { return false; }
		public boolean isRollbackAuthorized() { return false; }
		public boolean isRollbackPerformed() { return false; }
		public boolean isExecutableRestoration() { return false; }
		public boolean isCommitToken() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	private static final class Accumulator {
		private static final Comparator<TileCoordinate> TILE_ORDER =
			new Comparator<TileCoordinate>() {
				@Override
				public int compare(
					final TileCoordinate left,
					final TileCoordinate right) {
					int compared = Integer.compare(left.y, right.y);
					return compared != 0
						? compared : Integer.compare(left.x, right.x);
				}
			};

		private final WorldBounds bounds;
		private final Map<TileCoordinate, MutableContribution> contributions =
			new HashMap<TileCoordinate, MutableContribution>();
		private Reason refusalReason;

		private Accumulator(final WorldBounds bounds) {
			this.bounds = bounds;
		}

		private boolean requireInWorld(final int x, final int y) {
			return bounds.contains(x, y)
				|| refuse(Reason.OUT_OF_WORLD_EFFECT);
		}

		private boolean addOffset(
			final int x,
			final int y,
			final int offsetX,
			final int offsetY,
			final int blockingSceneryCount,
			final int dynamicCollisionMask,
			final int dynamicProjectileCount) {
			try {
				return add(
					Math.addExact(x, offsetX), Math.addExact(y, offsetY),
					blockingSceneryCount, dynamicCollisionMask,
					dynamicProjectileCount);
			} catch (ArithmeticException overflow) {
				return refuse(Reason.OUT_OF_WORLD_EFFECT);
			}
		}

		private boolean add(
			final int x,
			final int y,
			final int blockingSceneryCount,
			final int dynamicCollisionMask,
			final int dynamicProjectileCount) {
			if (!bounds.contains(x, y)) {
				return refuse(Reason.OUT_OF_WORLD_EFFECT);
			}
			TileCoordinate coordinate = new TileCoordinate(x, y);
			MutableContribution contribution = contributions.get(coordinate);
			if (contribution == null) {
				if (contributions.size()
					>= GameTickEventRestorationTransientRollbackSnapshot
						.MAXIMUM_COLLISION_CONTRIBUTION_TILES) {
					return refuse(Reason.CONTRIBUTION_TILE_LIMIT_EXCEEDED);
				}
				contribution = new MutableContribution();
				contributions.put(coordinate, contribution);
			}
			contribution.blockingSceneryCount = Math.addExact(
				contribution.blockingSceneryCount, blockingSceneryCount);
			contribution.dynamicCollisionMask |= dynamicCollisionMask;
			contribution.dynamicProjectileCount = Math.addExact(
				contribution.dynamicProjectileCount, dynamicProjectileCount);
			return true;
		}

		private boolean refuse(final Reason reason) {
			refusalReason = reason;
			return false;
		}

		private Reason getRefusalReason() {
			return refusalReason == null
				? Reason.CONTRIBUTION_TILE_LIMIT_EXCEEDED : refusalReason;
		}

		private Result complete(
			final Operation operation,
			final ConstructorState constructor) {
			List<TileCoordinate> coordinates =
				new ArrayList<TileCoordinate>(contributions.keySet());
			Collections.sort(coordinates, TILE_ORDER);
			List<GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution> immutableContributions =
				new ArrayList<GameTickEventRestorationTransientRollbackSnapshot
					.CollisionContribution>(coordinates.size());
			Set<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> uniqueRegions =
				new LinkedHashSet<GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate>();
			uniqueRegions.add(GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate.fromTile(
					constructor.getX(), constructor.getY()));
			for (TileCoordinate coordinate : coordinates) {
				MutableContribution contribution = contributions.get(coordinate);
				immutableContributions.add(
					GameTickEventRestorationTransientRollbackSnapshot
						.CollisionContribution.of(
							coordinate.x, coordinate.y,
							contribution.blockingSceneryCount,
							contribution.dynamicCollisionMask,
							contribution.dynamicProjectileCount));
				uniqueRegions.add(
					GameTickEventRestorationCollisionTransactionContract
						.PackedRegionCoordinate.fromTile(
							coordinate.x, coordinate.y));
			}
			List<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> regions =
				new ArrayList<GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate>(uniqueRegions);
			Collections.sort(
				regions,
				new Comparator<GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate>() {
					@Override
					public int compare(
						final GameTickEventRestorationCollisionTransactionContract
							.PackedRegionCoordinate left,
						final GameTickEventRestorationCollisionTransactionContract
							.PackedRegionCoordinate right) {
						int compared = Integer.compare(
							left.getRegionX(), right.getRegionX());
						return compared != 0 ? compared : Integer.compare(
							left.getRegionY(), right.getRegionY());
					}
				});
			return Result.available(
				operation, immutableContributions, regions);
		}
	}

	private static final class TileCoordinate {
		private final int x;
		private final int y;

		private TileCoordinate(final int x, final int y) {
			this.x = x;
			this.y = y;
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) { return true; }
			if (!(other instanceof TileCoordinate)) { return false; }
			TileCoordinate coordinate = (TileCoordinate) other;
			return x == coordinate.x && y == coordinate.y;
		}

		@Override
		public int hashCode() { return 31 * x + y; }
	}

	private static final class MutableContribution {
		private int blockingSceneryCount;
		private int dynamicCollisionMask;
		private int dynamicProjectileCount;
	}
}
