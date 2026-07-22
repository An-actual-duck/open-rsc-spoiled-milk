package com.openrsc.server.model.world.region;

import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionApplicationContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionApplicationContract.CurrentTileState;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionApplicationContract.Evaluation;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionApplicationContract.ProjectedTileState;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionApplicationContract.Request;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionTransactionContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationTransientRollbackSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Package-local exact collision executor, disconnected from World callers. */
final class RegionCollisionFootprintMutationExecutor {
	private RegionCollisionFootprintMutationExecutor() { }

	static Result execute(
		final List<RegionObjectCollisionMutationBoundary> boundaries,
		final GameTickEventRestorationCollisionFootprintPlanner.Result footprint,
		final MutableTileAccess tileAccess) {
		List<RegionObjectCollisionMutationBoundary> checkedBoundaries =
			Objects.requireNonNull(boundaries, "boundaries");
		GameTickEventRestorationCollisionFootprintPlanner.Result checkedFootprint =
			Objects.requireNonNull(footprint, "footprint");
		MutableTileAccess checkedTileAccess = Objects.requireNonNull(
			tileAccess, "tileAccess");
		if (!checkedFootprint.isFootprintAvailable()) {
			return Result.refused(Reason.FOOTPRINT_UNAVAILABLE, null);
		}
		if (!matchesBoundaryCoverage(
				checkedBoundaries, checkedFootprint.getRequiredRegions())) {
			throw new IllegalArgumentException(
				"Collision mutation boundary coverage is not canonical and exact");
		}
		final Result[] result = new Result[1];
		RegionObjectCollisionMutationBoundary.MutationExecution execution =
			RegionObjectCollisionMutationBoundary.executeMutation(
				checkedBoundaries,
				new RegionObjectCollisionMutationBoundary.MutationOperation() {
					@Override
					public void run(
						final RegionObjectCollisionMutationBoundary
							.HeldMutationBoundarySet heldBoundaries) {
						if (!heldBoundaries.areAllBoundariesHeld()
							|| !heldBoundaries.isMutationAuthorized()) {
							throw new IllegalStateException(
								"Collision mutation escaped its ordered boundary");
						}
						result[0] = applyInsideBoundary(
							checkedFootprint, checkedTileAccess);
					}
				});
		if (!execution.wereAllBoundariesHeldDuringOperation()
			|| !execution.isMutationAuthorized()
			|| !execution.isMutationOperationInvoked()
			|| result[0] == null) {
			throw new IllegalStateException(
				"Collision mutation execution did not complete");
		}
		return result[0].withBoundaryCount(
			execution.getDeclaredBoundaryCount());
	}

	static Result refuseRequiredRegionUnavailable() {
		return Result.refused(Reason.REQUIRED_REGION_UNAVAILABLE, null);
	}

	private static Result applyInsideBoundary(
		final GameTickEventRestorationCollisionFootprintPlanner.Result footprint,
		final MutableTileAccess tileAccess) {
		List<TileMutation> mutations = new ArrayList<TileMutation>();
		List<CurrentTileState> currentTiles =
			new ArrayList<CurrentTileState>();
		for (GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution contribution
					: footprint.getContributions()) {
			TileValue tile = tileAccess.getMutableTile(
				contribution.getX(), contribution.getY());
			if (tile == null) {
				return Result.refused(Reason.REQUIRED_TILE_UNAVAILABLE, null);
			}
			mutations.add(new TileMutation(tile, contribution));
			currentTiles.add(CurrentTileState.of(
				contribution.getX(), contribution.getY(),
				tile.getBlockingSceneryCount(),
				tile.getDynamicCollisionCounts(),
				tile.getDynamicProjectileCount()));
		}
		Evaluation evaluation =
			GameTickEventRestorationCollisionApplicationContract.evaluate(
				footprint,
				Request.declare(
					footprint.getOperation(), true, true,
					footprint.getRequiredRegions(), currentTiles));
		if (!evaluation.isProjectedPostStateAvailable()) {
			return Result.refused(
				Reason.APPLICATION_PRECONDITION_REFUSED, evaluation);
		}
		for (TileMutation mutation : mutations) {
			applyContribution(footprint.getOperation(), mutation);
		}
		if (!matchesProjectedState(mutations, evaluation.getProjectedTiles())) {
			for (int index = mutations.size() - 1; index >= 0; index--) {
				applyContribution(opposite(footprint.getOperation()),
					mutations.get(index));
			}
			return Result.rolledBack(evaluation);
		}
		return Result.applied(evaluation);
	}

	private static boolean matchesBoundaryCoverage(
		final List<RegionObjectCollisionMutationBoundary> boundaries,
		final List<GameTickEventRestorationCollisionTransactionContract
			.PackedRegionCoordinate> regions) {
		if (boundaries.size() != regions.size() || boundaries.isEmpty()) {
			return false;
		}
		for (int index = 0; index < boundaries.size(); index++) {
			RegionObjectCollisionMutationBoundary boundary = boundaries.get(index);
			GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate region = regions.get(index);
			if (boundary == null
				|| boundary.getRegionX() != region.getRegionX()
				|| boundary.getRegionY() != region.getRegionY()) {
				return false;
			}
		}
		return true;
	}

	private static void applyContribution(
		final GameTickEventRestorationCollisionFootprintPlanner.Operation operation,
		final TileMutation mutation) {
		GameTickEventRestorationTransientRollbackSnapshot.CollisionContribution
			contribution = mutation.contribution;
		for (int count = 0;
				count < contribution.getBlockingSceneryCount(); count++) {
			if (operation
				== GameTickEventRestorationCollisionFootprintPlanner.Operation.REGISTER) {
				mutation.tile.addBlockingScenery();
			} else {
				mutation.tile.removeBlockingScenery();
			}
		}
		for (int bit = 0; bit < 6; bit++) {
			for (int count = 0;
					count < contribution.getDynamicCollisionCount(bit); count++) {
				if (operation
					== GameTickEventRestorationCollisionFootprintPlanner
						.Operation.REGISTER) {
					mutation.tile.addDynamicCollision(1 << bit);
				} else {
					mutation.tile.removeDynamicCollision(1 << bit);
				}
			}
		}
		for (int count = 0;
				count < contribution.getDynamicProjectileCount(); count++) {
			if (operation
				== GameTickEventRestorationCollisionFootprintPlanner.Operation.REGISTER) {
				mutation.tile.addDynamicProjectileBlock();
			} else {
				mutation.tile.removeDynamicProjectileBlock();
			}
		}
	}

	private static GameTickEventRestorationCollisionFootprintPlanner.Operation
		opposite(
			final GameTickEventRestorationCollisionFootprintPlanner.Operation
				operation) {
		return operation
			== GameTickEventRestorationCollisionFootprintPlanner.Operation.REGISTER
				? GameTickEventRestorationCollisionFootprintPlanner.Operation.UNREGISTER
				: GameTickEventRestorationCollisionFootprintPlanner.Operation.REGISTER;
	}

	private static boolean matchesProjectedState(
		final List<TileMutation> mutations,
		final List<ProjectedTileState> projectedTiles) {
		if (mutations.size() != projectedTiles.size()) {
			return false;
		}
		for (int index = 0; index < mutations.size(); index++) {
			TileValue tile = mutations.get(index).tile;
			ProjectedTileState projected = projectedTiles.get(index);
			if (tile.getBlockingSceneryCount()
					!= projected.getBlockingSceneryCount()
				|| tile.getDynamicProjectileCount()
					!= projected.getDynamicProjectileCount()) {
				return false;
			}
			int[] counts = tile.getDynamicCollisionCounts();
			for (int bit = 0; bit < counts.length; bit++) {
				if (counts[bit] != projected.getDynamicCollisionCount(bit)) {
					return false;
				}
			}
		}
		return true;
	}

	interface MutableTileAccess {
		TileValue getMutableTile(int x, int y);
	}

	enum Outcome {
		REFUSED,
		APPLIED,
		POST_STATE_FAILED_AND_ROLLED_BACK
	}

	enum Reason {
		FOOTPRINT_UNAVAILABLE,
		REQUIRED_REGION_UNAVAILABLE,
		REQUIRED_TILE_UNAVAILABLE,
		APPLICATION_PRECONDITION_REFUSED,
		COLLISION_APPLIED,
		POST_STATE_VERIFICATION_FAILED
	}

	static final class Result {
		private final Outcome outcome;
		private final Reason reason;
		private final Evaluation evaluation;
		private final int boundaryCount;

		private Result(
			final Outcome outcome,
			final Reason reason,
			final Evaluation evaluation,
			final int boundaryCount) {
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.evaluation = evaluation;
			this.boundaryCount = boundaryCount;
			boolean applied = outcome == Outcome.APPLIED;
			boolean rolledBack =
				outcome == Outcome.POST_STATE_FAILED_AND_ROLLED_BACK;
			if (boundaryCount < 0
				|| applied != (reason == Reason.COLLISION_APPLIED)
				|| rolledBack
					!= (reason == Reason.POST_STATE_VERIFICATION_FAILED)
				|| (applied || rolledBack)
					!= (evaluation != null
						&& evaluation.isProjectedPostStateAvailable())) {
				throw new IllegalArgumentException(
					"Collision mutation result is inconsistent");
			}
		}

		private static Result refused(
			final Reason reason,
			final Evaluation evaluation) {
			return new Result(Outcome.REFUSED, reason, evaluation, 0);
		}

		private static Result applied(final Evaluation evaluation) {
			return new Result(
				Outcome.APPLIED, Reason.COLLISION_APPLIED, evaluation, 0);
		}

		private static Result rolledBack(final Evaluation evaluation) {
			return new Result(
				Outcome.POST_STATE_FAILED_AND_ROLLED_BACK,
				Reason.POST_STATE_VERIFICATION_FAILED, evaluation, 0);
		}

		private Result withBoundaryCount(final int checkedBoundaryCount) {
			return new Result(
				outcome, reason, evaluation, checkedBoundaryCount);
		}

		Outcome getOutcome() { return outcome; }
		Reason getReason() { return reason; }
		Evaluation getEvaluation() { return evaluation; }
		int getBoundaryCount() { return boundaryCount; }
		boolean isRefused() { return outcome == Outcome.REFUSED; }
		boolean isApplied() { return outcome == Outcome.APPLIED; }
		boolean isPostStateFailedAndRolledBack() {
			return outcome == Outcome.POST_STATE_FAILED_AND_ROLLED_BACK;
		}
		boolean isMutationAuthorized() { return !isRefused(); }
		boolean isMutationPerformed() { return !isRefused(); }
		boolean isRollbackAuthorized() {
			return isPostStateFailedAndRolledBack();
		}
		boolean isRollbackPerformed() {
			return isPostStateFailedAndRolledBack();
		}
		boolean isExecutableRestoration() { return false; }
		boolean isCommitToken() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}

	private static final class TileMutation {
		private final TileValue tile;
		private final GameTickEventRestorationTransientRollbackSnapshot
			.CollisionContribution contribution;

		private TileMutation(
			final TileValue tile,
			final GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution contribution) {
			this.tile = tile;
			this.contribution = contribution;
		}
	}
}
