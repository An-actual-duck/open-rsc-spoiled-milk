package com.openrsc.server.event.rsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure pre-application arithmetic for one planned collision footprint.
 *
 * <p>This contract compares a Slice 131 footprint with detached current
 * per-tile counters and returns an exact projected post-state. It neither
 * observes nor mutates a TileValue and does not acquire Slice 130's boundary.</p>
 */
public final class GameTickEventRestorationCollisionApplicationContract {
	private GameTickEventRestorationCollisionApplicationContract() { }

	/** Applies the fail-closed comparison and arithmetic order. */
	public static Evaluation evaluate(
		final GameTickEventRestorationCollisionFootprintPlanner.Result footprint,
		final Request request) {
		GameTickEventRestorationCollisionFootprintPlanner.Result checkedFootprint =
			Objects.requireNonNull(footprint, "footprint");
		Request checkedRequest = Objects.requireNonNull(request, "request");
		if (!checkedFootprint.isFootprintAvailable()) {
			return Evaluation.refused(Reason.FOOTPRINT_UNAVAILABLE);
		}
		if (checkedRequest.getOperation() != checkedFootprint.getOperation()) {
			return Evaluation.refused(Reason.OPERATION_MISMATCH);
		}
		if (!checkedRequest.isOrderedCollisionBoundaryHeld()) {
			return Evaluation.refused(Reason.ORDERED_BOUNDARY_MISSING);
		}
		if (!checkedRequest.isCurrentStateComparisonFresh()) {
			return Evaluation.refused(Reason.CURRENT_STATE_COMPARISON_STALE);
		}
		if (!isCanonicalRegions(checkedRequest.getDeclaredRegions())) {
			return Evaluation.refused(Reason.REGION_COVERAGE_NOT_CANONICAL);
		}
		if (!checkedRequest.getDeclaredRegions().equals(
				checkedFootprint.getRequiredRegions())) {
			return Evaluation.refused(Reason.REGION_COVERAGE_MISMATCH);
		}
		if (hasDuplicateTiles(checkedRequest.getCurrentTiles())) {
			return Evaluation.refused(Reason.DUPLICATE_CURRENT_TILE);
		}
		if (!isCanonicalTiles(checkedRequest.getCurrentTiles())) {
			return Evaluation.refused(Reason.CURRENT_TILE_ORDER_NOT_CANONICAL);
		}
		List<GameTickEventRestorationTransientRollbackSnapshot
			.CollisionContribution> contributions =
			checkedFootprint.getContributions();
		if (!hasExactCoordinates(
				contributions, checkedRequest.getCurrentTiles())) {
			return Evaluation.refused(Reason.CURRENT_TILE_COVERAGE_MISMATCH);
		}

		List<ProjectedTileState> projected =
			new ArrayList<ProjectedTileState>(contributions.size());
		for (int index = 0; index < contributions.size(); index++) {
			GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution contribution = contributions.get(index);
			CurrentTileState current = checkedRequest.getCurrentTiles().get(index);
			ProjectedTileState next;
			try {
				next = project(
					checkedRequest.getOperation(), contribution, current,
					checkedFootprint.isLegacySaturatingUnregister());
			} catch (CounterUnderflow underflow) {
				return Evaluation.refused(Reason.COUNTER_UNDERFLOW);
			} catch (ArithmeticException overflow) {
				return Evaluation.refused(Reason.COUNTER_OVERFLOW);
			}
			projected.add(next);
		}
		return Evaluation.available(projected);
	}

	private static ProjectedTileState project(
		final GameTickEventRestorationCollisionFootprintPlanner.Operation operation,
		final GameTickEventRestorationTransientRollbackSnapshot
			.CollisionContribution contribution,
		final CurrentTileState current,
		final boolean legacySaturatingUnregister) {
		int blockingSceneryCount = combine(
			operation, current.getBlockingSceneryCount(),
			contribution.getBlockingSceneryCount(), legacySaturatingUnregister);
		int dynamicProjectileCount = combine(
			operation, current.getDynamicProjectileCount(),
			contribution.getDynamicProjectileCount(),
			legacySaturatingUnregister);
		int[] dynamicCollisionCounts = current.getDynamicCollisionCounts();
		for (int bit = 0; bit < dynamicCollisionCounts.length; bit++) {
			dynamicCollisionCounts[bit] = combine(
				operation, dynamicCollisionCounts[bit],
				contribution.getDynamicCollisionCount(bit),
				legacySaturatingUnregister);
		}
		return ProjectedTileState.of(
			current.getX(), current.getY(), blockingSceneryCount,
			dynamicCollisionCounts, dynamicProjectileCount);
	}

	private static int combine(
		final GameTickEventRestorationCollisionFootprintPlanner.Operation operation,
		final int current,
		final int contribution,
		final boolean legacySaturatingUnregister) {
		if (operation
			== GameTickEventRestorationCollisionFootprintPlanner.Operation.REGISTER) {
			return Math.addExact(current, contribution);
		}
		if (current < contribution) {
			if (legacySaturatingUnregister) {
				return 0;
			}
			throw new CounterUnderflow();
		}
		return current - contribution;
	}

	private static boolean isCanonicalRegions(
		final List<GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate> regions) {
		GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate previous = null;
		for (GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate current : regions) {
			if (previous != null && compareRegions(previous, current) >= 0) {
				return false;
			}
			previous = current;
		}
		return !regions.isEmpty();
	}

	private static int compareRegions(
		final GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate left,
		final GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate right) {
		int compared = Integer.compare(
			left.getRegionX(), right.getRegionX());
		return compared != 0 ? compared : Integer.compare(
			left.getRegionY(), right.getRegionY());
	}

	private static boolean hasDuplicateTiles(
		final List<CurrentTileState> tiles) {
		Set<TileCoordinate> coordinates = new HashSet<TileCoordinate>();
		for (CurrentTileState tile : tiles) {
			if (!coordinates.add(new TileCoordinate(tile.getX(), tile.getY()))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCanonicalTiles(
		final List<CurrentTileState> tiles) {
		CurrentTileState previous = null;
		for (CurrentTileState current : tiles) {
			if (previous != null && compareTiles(previous, current) >= 0) {
				return false;
			}
			previous = current;
		}
		return true;
	}

	private static int compareTiles(
		final CurrentTileState left,
		final CurrentTileState right) {
		int compared = Integer.compare(left.getY(), right.getY());
		return compared != 0
			? compared : Integer.compare(left.getX(), right.getX());
	}

	private static boolean hasExactCoordinates(
		final List<GameTickEventRestorationTransientRollbackSnapshot
			.CollisionContribution> contributions,
		final List<CurrentTileState> currentTiles) {
		if (contributions.size() != currentTiles.size()) {
			return false;
		}
		for (int index = 0; index < contributions.size(); index++) {
			GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution contribution = contributions.get(index);
			CurrentTileState current = currentTiles.get(index);
			if (contribution.getX() != current.getX()
				|| contribution.getY() != current.getY()) {
				return false;
			}
		}
		return true;
	}

	public enum Outcome {
		REFUSED,
		PROJECTED_POST_STATE_AVAILABLE
	}

	public enum Reason {
		FOOTPRINT_UNAVAILABLE,
		OPERATION_MISMATCH,
		ORDERED_BOUNDARY_MISSING,
		CURRENT_STATE_COMPARISON_STALE,
		REGION_COVERAGE_NOT_CANONICAL,
		REGION_COVERAGE_MISMATCH,
		DUPLICATE_CURRENT_TILE,
		CURRENT_TILE_ORDER_NOT_CANONICAL,
		CURRENT_TILE_COVERAGE_MISMATCH,
		COUNTER_UNDERFLOW,
		COUNTER_OVERFLOW,
		PROJECTED_POST_STATE_AVAILABLE
	}

	/** Detached declaration sampled by a future in-boundary consumer. */
	public static final class Request {
		private final GameTickEventRestorationCollisionFootprintPlanner.Operation
			operation;
		private final boolean orderedCollisionBoundaryHeld;
		private final boolean currentStateComparisonFresh;
		private final List<GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate> declaredRegions;
		private final List<CurrentTileState> currentTiles;

		private Request(
			final GameTickEventRestorationCollisionFootprintPlanner.Operation operation,
			final boolean orderedCollisionBoundaryHeld,
			final boolean currentStateComparisonFresh,
			final List<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> declaredRegions,
			final List<CurrentTileState> currentTiles) {
			this.operation = Objects.requireNonNull(operation, "operation");
			List<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> checkedRegions = Objects.requireNonNull(
				declaredRegions, "declaredRegions");
			List<CurrentTileState> checkedTiles = Objects.requireNonNull(
				currentTiles, "currentTiles");
			if (checkedRegions.isEmpty() || checkedRegions.contains(null)
				|| checkedTiles.contains(null)
				|| checkedTiles.size()
					> GameTickEventRestorationTransientRollbackSnapshot
						.MAXIMUM_COLLISION_CONTRIBUTION_TILES) {
				throw new IllegalArgumentException(
					"Collision application request is invalid");
			}
			this.orderedCollisionBoundaryHeld = orderedCollisionBoundaryHeld;
			this.currentStateComparisonFresh = currentStateComparisonFresh;
			this.declaredRegions = Collections.unmodifiableList(
				new ArrayList<GameTickEventRestorationCollisionTransactionContract
					.PackedRegionCoordinate>(checkedRegions));
			this.currentTiles = Collections.unmodifiableList(
				new ArrayList<CurrentTileState>(checkedTiles));
		}

		public static Request declare(
			final GameTickEventRestorationCollisionFootprintPlanner.Operation operation,
			final boolean orderedCollisionBoundaryHeld,
			final boolean currentStateComparisonFresh,
			final List<GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate> declaredRegions,
			final List<CurrentTileState> currentTiles) {
			return new Request(
				operation, orderedCollisionBoundaryHeld,
				currentStateComparisonFresh, declaredRegions, currentTiles);
		}

		public GameTickEventRestorationCollisionFootprintPlanner.Operation
			getOperation() { return operation; }
		public boolean isOrderedCollisionBoundaryHeld() {
			return orderedCollisionBoundaryHeld;
		}
		public boolean isCurrentStateComparisonFresh() {
			return currentStateComparisonFresh;
		}
		public List<GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate> getDeclaredRegions() {
			return declaredRegions;
		}
		public List<CurrentTileState> getCurrentTiles() { return currentTiles; }
	}

	/** Exact current dynamic collision counters for one contribution tile. */
	public static final class CurrentTileState {
		private final int x;
		private final int y;
		private final int blockingSceneryCount;
		private final int[] dynamicCollisionCounts;
		private final int dynamicProjectileCount;

		private CurrentTileState(
			final int x,
			final int y,
			final int blockingSceneryCount,
			final int[] dynamicCollisionCounts,
			final int dynamicProjectileCount) {
			int[] checkedCounts = Objects.requireNonNull(
				dynamicCollisionCounts, "dynamicCollisionCounts");
			if (x < 0 || y < 0 || blockingSceneryCount < 0
				|| dynamicProjectileCount < 0 || checkedCounts.length != 6) {
				throw new IllegalArgumentException(
					"Current collision tile state is invalid");
			}
			for (int count : checkedCounts) {
				if (count < 0) {
					throw new IllegalArgumentException(
						"Current dynamic collision count is invalid");
				}
			}
			this.x = x;
			this.y = y;
			this.blockingSceneryCount = blockingSceneryCount;
			this.dynamicCollisionCounts = checkedCounts.clone();
			this.dynamicProjectileCount = dynamicProjectileCount;
		}

		public static CurrentTileState of(
			final int x,
			final int y,
			final int blockingSceneryCount,
			final int[] dynamicCollisionCounts,
			final int dynamicProjectileCount) {
			return new CurrentTileState(
				x, y, blockingSceneryCount, dynamicCollisionCounts,
				dynamicProjectileCount);
		}

		public int getX() { return x; }
		public int getY() { return y; }
		public int getBlockingSceneryCount() {
			return blockingSceneryCount;
		}
		public int[] getDynamicCollisionCounts() {
			return dynamicCollisionCounts.clone();
		}
		public int getDynamicCollisionCount(final int bit) {
			if (bit < 0 || bit >= dynamicCollisionCounts.length) {
				throw new IllegalArgumentException(
					"Dynamic collision bit is invalid");
			}
			return dynamicCollisionCounts[bit];
		}
		public int getDynamicProjectileCount() {
			return dynamicProjectileCount;
		}
	}

	/** Exact arithmetic output; it is not a write set or commit token. */
	public static final class ProjectedTileState {
		private final CurrentTileState state;

		private ProjectedTileState(final CurrentTileState state) {
			this.state = state;
		}

		private static ProjectedTileState of(
			final int x,
			final int y,
			final int blockingSceneryCount,
			final int[] dynamicCollisionCounts,
			final int dynamicProjectileCount) {
			return new ProjectedTileState(CurrentTileState.of(
				x, y, blockingSceneryCount, dynamicCollisionCounts,
				dynamicProjectileCount));
		}

		public int getX() { return state.getX(); }
		public int getY() { return state.getY(); }
		public int getBlockingSceneryCount() {
			return state.getBlockingSceneryCount();
		}
		public int[] getDynamicCollisionCounts() {
			return state.getDynamicCollisionCounts();
		}
		public int getDynamicCollisionCount(final int bit) {
			return state.getDynamicCollisionCount(bit);
		}
		public int getDynamicProjectileCount() {
			return state.getDynamicProjectileCount();
		}
	}

	/** Result wrapper; projected state carries no runtime authority. */
	public static final class Evaluation {
		private final Outcome outcome;
		private final Reason reason;
		private final List<ProjectedTileState> projectedTiles;

		private Evaluation(
			final Outcome outcome,
			final Reason reason,
			final List<ProjectedTileState> projectedTiles) {
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.projectedTiles = Collections.unmodifiableList(
				new ArrayList<ProjectedTileState>(
					Objects.requireNonNull(projectedTiles, "projectedTiles")));
			boolean available =
				outcome == Outcome.PROJECTED_POST_STATE_AVAILABLE;
			if (available
				!= (reason == Reason.PROJECTED_POST_STATE_AVAILABLE)
				|| !available && !projectedTiles.isEmpty()) {
				throw new IllegalArgumentException(
					"Collision application evaluation is inconsistent");
			}
		}

		private static Evaluation refused(final Reason reason) {
			return new Evaluation(
				Outcome.REFUSED, reason,
				Collections.<ProjectedTileState>emptyList());
		}

		private static Evaluation available(
			final List<ProjectedTileState> projectedTiles) {
			return new Evaluation(
				Outcome.PROJECTED_POST_STATE_AVAILABLE,
				Reason.PROJECTED_POST_STATE_AVAILABLE, projectedTiles);
		}

		public Outcome getOutcome() { return outcome; }
		public Reason getReason() { return reason; }
		public boolean isRefused() { return outcome == Outcome.REFUSED; }
		public boolean isProjectedPostStateAvailable() {
			return outcome == Outcome.PROJECTED_POST_STATE_AVAILABLE;
		}
		public List<ProjectedTileState> getProjectedTiles() {
			return projectedTiles;
		}
		public int getProjectedTileCount() { return projectedTiles.size(); }

		public boolean isRuntimeObservationPerformed() { return false; }
		public boolean isRuntimeBoundaryAcquired() { return false; }
		public boolean isRuntimeStateRetained() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isMutationPerformed() { return false; }
		public boolean isRollbackAuthorized() { return false; }
		public boolean isRollbackPerformed() { return false; }
		public boolean isExecutableRestoration() { return false; }
		public boolean isCommitToken() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
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

	private static final class CounterUnderflow extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}
