package com.openrsc.server.model.world.region;

import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionTransactionContract.PackedRegionCoordinate;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Composes exact GameObject slot membership with collision counters under one
 * canonical Region boundary set. Scheduler restoration is deliberately absent.
 */
final class RegionObjectCollisionTransactionExecutor {
	private RegionObjectCollisionTransactionExecutor() { }

	static Result execute(
		final List<RegionObjectCollisionMutationBoundary> transactionBoundaries,
		final Region oldRegion,
		final GameObject oldObject,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			oldUnregisterFootprint,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			oldRollbackRegisterFootprint,
		final Region newRegion,
		final GameObject newObject,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			newRegisterFootprint,
		final RegionCollisionFootprintMutationExecutor.MutableTileAccess tileAccess,
		final CacheInvalidator cacheInvalidator) {
		List<RegionObjectCollisionMutationBoundary> checkedBoundaries =
			Objects.requireNonNull(transactionBoundaries, "transactionBoundaries");
		RegionCollisionFootprintMutationExecutor.MutableTileAccess
			checkedTileAccess = Objects.requireNonNull(tileAccess, "tileAccess");
		CacheInvalidator checkedInvalidator = Objects.requireNonNull(
			cacheInvalidator, "cacheInvalidator");
		Change oldChange = oldObject == null ? null : Change.oldObject(
			Objects.requireNonNull(oldRegion, "oldRegion"), oldObject,
			Objects.requireNonNull(
				oldUnregisterFootprint, "oldUnregisterFootprint"),
			Objects.requireNonNull(
				oldRollbackRegisterFootprint,
				"oldRollbackRegisterFootprint"));
		Change newChange = newObject == null ? null : Change.newObject(
			Objects.requireNonNull(newRegion, "newRegion"), newObject,
			Objects.requireNonNull(
				newRegisterFootprint, "newRegisterFootprint"));
		if (oldChange == null && newChange == null) {
			throw new IllegalArgumentException(
				"Object/collision transaction has no membership change");
		}
		List<PackedRegionCoordinate> union = requiredRegionUnion(
			oldChange, newChange);
		if (!matchesBoundaryCoverage(checkedBoundaries, union)) {
			throw new IllegalArgumentException(
				"Object/collision transaction boundary coverage is not exact");
		}
		List<Region> membershipRegions = membershipRegions(
			oldChange, newChange);
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
								"Object transaction escaped its Region boundaries");
						}
						withMembershipMonitors(
							membershipRegions, 0, new Runnable() {
								@Override
								public void run() {
									result[0] = executeInsideBoundaries(
										checkedBoundaries, oldChange, newChange,
										checkedTileAccess, checkedInvalidator);
								}
							});
					}
				});
		if (!execution.isMutationOperationInvoked()
			|| !execution.wereAllBoundariesHeldDuringOperation()
			|| result[0] == null) {
			throw new IllegalStateException(
				"Object/collision transaction did not complete");
		}
		return result[0].withBoundaryCount(
			execution.getDeclaredBoundaryCount());
	}

	private static Result executeInsideBoundaries(
		final List<RegionObjectCollisionMutationBoundary> transactionBoundaries,
		final Change oldChange,
		final Change newChange,
		final RegionCollisionFootprintMutationExecutor.MutableTileAccess tileAccess,
		final CacheInvalidator cacheInvalidator) {
		if (!membershipPreconditionSatisfied(oldChange, newChange)) {
			return Result.refused(Reason.MEMBERSHIP_PRECONDITION_REFUSED);
		}

		boolean oldRemoved = false;
		if (oldChange != null) {
			if (!oldChange.region.removeGameObjectUnderTransaction(
					oldChange.object)) {
				return Result.refused(Reason.MEMBERSHIP_PRECONDITION_REFUSED);
			}
			oldChange.object.removeOrderedTransactionState(oldChange.region);
			oldRemoved = true;
			RegionCollisionFootprintMutationExecutor.Result collision =
				RegionCollisionFootprintMutationExecutor
					.executeInsideHeldBoundaries(
						boundariesFor(
							transactionBoundaries,
							oldChange.forward.getRequiredRegions()),
						oldChange.forward, tileAccess);
			if (!collision.isApplied()) {
				oldChange.object.restoreOrderedTransactionState(oldChange.region);
				if (!oldChange.region.addGameObjectUnderTransaction(
						oldChange.object)) {
					throw new IllegalStateException(
						"Unable to restore refused object membership removal");
				}
				return Result.refused(Reason.OLD_COLLISION_REFUSED);
			}
		}

		if (newChange != null) {
			Point newLocation = newChange.location;
			newChange.object.attachOrderedTransactionState(
				newLocation, newChange.region);
			if (!newChange.region.addGameObjectUnderTransaction(
					newChange.object)) {
				newChange.object.detachOrderedTransactionState(
					newLocation, newChange.region);
				if (oldRemoved) {
					restoreOldChange(
						transactionBoundaries, oldChange, tileAccess);
				}
				return Result.refused(Reason.NEW_MEMBERSHIP_REFUSED);
			}
			RegionCollisionFootprintMutationExecutor.Result collision =
				RegionCollisionFootprintMutationExecutor
					.executeInsideHeldBoundaries(
						boundariesFor(
							transactionBoundaries,
							newChange.forward.getRequiredRegions()),
						newChange.forward, tileAccess);
			if (!collision.isApplied()) {
				if (!newChange.region.removeGameObjectUnderTransaction(
						newChange.object)) {
					throw new IllegalStateException(
						"Unable to roll back refused object membership add");
				}
				newChange.object.detachOrderedTransactionState(
					newLocation, newChange.region);
				if (oldRemoved) {
					restoreOldChange(
						transactionBoundaries, oldChange, tileAccess);
				}
				return Result.refused(Reason.NEW_COLLISION_REFUSED);
			}
		}

		if ((oldChange != null
				&& oldChange.region.containsGameObjectIdentityUnderTransaction(
					oldChange.object))
			|| (newChange != null
				&& !newChange.region.containsGameObjectIdentityUnderTransaction(
					newChange.object))) {
			throw new IllegalStateException(
				"Object membership post-state verification failed");
		}
		if (oldChange != null) {
			cacheInvalidator.invalidate(oldChange.region);
		}
		if (newChange != null
			&& (oldChange == null || newChange.region != oldChange.region)) {
			cacheInvalidator.invalidate(newChange.region);
		}
		return Result.applied(oldChange != null, newChange != null);
	}

	private static boolean membershipPreconditionSatisfied(
		final Change oldChange,
		final Change newChange) {
		if (oldChange != null
			&& (oldChange.object.isRemoved()
				|| oldChange.object.getRegion() != oldChange.region
				|| !oldChange.region.containsGameObjectIdentityUnderTransaction(
					oldChange.object))) {
			return false;
		}
		if (newChange != null) {
			if (newChange.object.isRemoved()
				|| newChange.object.getLocation() != null
				|| newChange.object.getRegion() != null) {
				return false;
			}
			GameObject occupant =
				newChange.region.getCollidingGameObjectUnderTransaction(
					newChange.location,
					newChange.object.getGameObjectType(),
					newChange.object.getDirection());
			if (occupant != null
				&& (oldChange == null || occupant != oldChange.object)) {
				return false;
			}
		}
		return true;
	}

	private static void restoreOldChange(
		final List<RegionObjectCollisionMutationBoundary> transactionBoundaries,
		final Change oldChange,
		final RegionCollisionFootprintMutationExecutor.MutableTileAccess tileAccess) {
		oldChange.object.restoreOrderedTransactionState(oldChange.region);
		if (!oldChange.region.addGameObjectUnderTransaction(oldChange.object)) {
			throw new IllegalStateException(
				"Unable to restore replaced object membership");
		}
		RegionCollisionFootprintMutationExecutor.Result restored =
			RegionCollisionFootprintMutationExecutor.executeInsideHeldBoundaries(
				boundariesFor(
					transactionBoundaries,
					oldChange.rollback.getRequiredRegions()),
				oldChange.rollback, tileAccess);
		if (!restored.isApplied()) {
			throw new IllegalStateException(
				"Unable to restore replaced object collision state");
		}
	}

	private static void withMembershipMonitors(
		final List<Region> regions,
		final int index,
		final Runnable operation) {
		if (index == regions.size()) {
			operation.run();
			return;
		}
		synchronized (regions.get(index).getGameObjectTransactionMonitor()) {
			withMembershipMonitors(regions, index + 1, operation);
		}
	}

	private static List<Region> membershipRegions(
		final Change oldChange,
		final Change newChange) {
		List<Region> result = new ArrayList<Region>();
		if (oldChange != null) { result.add(oldChange.region); }
		if (newChange != null
			&& (oldChange == null || newChange.region != oldChange.region)) {
			result.add(newChange.region);
		}
		Collections.sort(result, new Comparator<Region>() {
			@Override
			public int compare(final Region left, final Region right) {
				int compared = Integer.compare(
					left.getRegionX(), right.getRegionX());
				return compared != 0 ? compared : Integer.compare(
					left.getRegionY(), right.getRegionY());
			}
		});
		return result;
	}

	private static List<PackedRegionCoordinate> requiredRegionUnion(
		final Change oldChange,
		final Change newChange) {
		List<PackedRegionCoordinate> result =
			new ArrayList<PackedRegionCoordinate>();
		if (oldChange != null) {
			result.addAll(oldChange.forward.getRequiredRegions());
			result.addAll(oldChange.rollback.getRequiredRegions());
		}
		if (newChange != null) {
			result.addAll(newChange.forward.getRequiredRegions());
		}
		Collections.sort(result, PACKED_REGION_COMPARATOR);
		List<PackedRegionCoordinate> unique =
			new ArrayList<PackedRegionCoordinate>();
		for (PackedRegionCoordinate coordinate : result) {
			if (unique.isEmpty()
				|| PACKED_REGION_COMPARATOR.compare(
					unique.get(unique.size() - 1), coordinate) != 0) {
				unique.add(coordinate);
			}
		}
		return Collections.unmodifiableList(unique);
	}

	private static List<RegionObjectCollisionMutationBoundary> boundariesFor(
		final List<RegionObjectCollisionMutationBoundary> transactionBoundaries,
		final List<PackedRegionCoordinate> requiredRegions) {
		List<RegionObjectCollisionMutationBoundary> result =
			new ArrayList<RegionObjectCollisionMutationBoundary>();
		for (PackedRegionCoordinate required : requiredRegions) {
			RegionObjectCollisionMutationBoundary found = null;
			for (RegionObjectCollisionMutationBoundary candidate
					: transactionBoundaries) {
				if (candidate.getRegionX() == required.getRegionX()
					&& candidate.getRegionY() == required.getRegionY()) {
					found = candidate;
					break;
				}
			}
			if (found == null) {
				throw new IllegalStateException(
					"Required collision boundary left the transaction union");
			}
			result.add(found);
		}
		return result;
	}

	private static boolean matchesBoundaryCoverage(
		final List<RegionObjectCollisionMutationBoundary> boundaries,
		final List<PackedRegionCoordinate> regions) {
		if (boundaries.size() != regions.size() || boundaries.isEmpty()) {
			return false;
		}
		for (int index = 0; index < boundaries.size(); index++) {
			RegionObjectCollisionMutationBoundary boundary = boundaries.get(index);
			PackedRegionCoordinate region = regions.get(index);
			if (boundary == null
				|| boundary.getRegionX() != region.getRegionX()
				|| boundary.getRegionY() != region.getRegionY()) {
				return false;
			}
		}
		return true;
	}

	interface CacheInvalidator {
		void invalidate(Region region);
	}

	enum Outcome { REFUSED, APPLIED }

	enum Reason {
		MEMBERSHIP_PRECONDITION_REFUSED,
		OLD_COLLISION_REFUSED,
		NEW_MEMBERSHIP_REFUSED,
		NEW_COLLISION_REFUSED,
		TRANSACTION_APPLIED
	}

	static final class Result {
		private final Outcome outcome;
		private final Reason reason;
		private final boolean removed;
		private final boolean registered;
		private final int boundaryCount;

		private Result(
			final Outcome outcome,
			final Reason reason,
			final boolean removed,
			final boolean registered,
			final int boundaryCount) {
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.removed = removed;
			this.registered = registered;
			this.boundaryCount = boundaryCount;
			if (boundaryCount < 0
				|| (outcome == Outcome.APPLIED)
					!= (reason == Reason.TRANSACTION_APPLIED)
				|| (outcome == Outcome.REFUSED && (removed || registered))) {
				throw new IllegalArgumentException(
					"Object/collision transaction result is inconsistent");
			}
		}

		private static Result refused(final Reason reason) {
			return new Result(Outcome.REFUSED, reason, false, false, 0);
		}

		private static Result applied(
			final boolean removed,
			final boolean registered) {
			return new Result(
				Outcome.APPLIED, Reason.TRANSACTION_APPLIED,
				removed, registered, 0);
		}

		private Result withBoundaryCount(final int count) {
			return new Result(outcome, reason, removed, registered, count);
		}

		Outcome getOutcome() { return outcome; }
		Reason getReason() { return reason; }
		boolean isApplied() { return outcome == Outcome.APPLIED; }
		boolean isRefused() { return outcome == Outcome.REFUSED; }
		boolean isMembershipRemoved() { return removed; }
		boolean isMembershipRegistered() { return registered; }
		int getBoundaryCount() { return boundaryCount; }
		boolean isMutationAuthorized() { return isApplied(); }
		boolean isMutationPerformed() { return isApplied(); }
		boolean isRollbackAuthorized() { return false; }
		boolean isExecutableRestoration() { return false; }
		boolean isCommitToken() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}

	private static final class Change {
		private final Region region;
		private final GameObject object;
		private final Point location;
		private final GameTickEventRestorationCollisionFootprintPlanner.Result
			forward;
		private final GameTickEventRestorationCollisionFootprintPlanner.Result
			rollback;

		private Change(
			final Region region,
			final GameObject object,
			final Point location,
			final GameTickEventRestorationCollisionFootprintPlanner.Result forward,
			final GameTickEventRestorationCollisionFootprintPlanner.Result rollback) {
			this.region = region;
			this.object = object;
			this.location = location;
			this.forward = forward;
			this.rollback = rollback;
		}

		private static Change oldObject(
			final Region region,
			final GameObject object,
			final GameTickEventRestorationCollisionFootprintPlanner.Result forward,
			final GameTickEventRestorationCollisionFootprintPlanner.Result rollback) {
			if (!forward.isFootprintAvailable()
				|| !rollback.isFootprintAvailable()
				|| forward.getOperation() != Operation.UNREGISTER
				|| rollback.getOperation() != Operation.REGISTER
				|| object.getLocation() == null) {
				throw new IllegalArgumentException(
					"Old object transaction footprints are invalid");
			}
			return new Change(
				region, object, object.getLocation(), forward, rollback);
		}

		private static Change newObject(
			final Region region,
			final GameObject object,
			final GameTickEventRestorationCollisionFootprintPlanner.Result forward) {
			if (!forward.isFootprintAvailable()
				|| forward.getOperation() != Operation.REGISTER) {
				throw new IllegalArgumentException(
					"New object transaction footprint is invalid");
			}
			Point location = Point.location(
				object.getLoc().getX(), object.getLoc().getY());
			return new Change(region, object, location, forward, null);
		}
	}

	private static final Comparator<PackedRegionCoordinate>
		PACKED_REGION_COMPARATOR = new Comparator<PackedRegionCoordinate>() {
			@Override
			public int compare(
				final PackedRegionCoordinate left,
				final PackedRegionCoordinate right) {
				int compared = Integer.compare(
					left.getRegionX(), right.getRegionX());
				return compared != 0 ? compared : Integer.compare(
					left.getRegionY(), right.getRegionY());
			}
		};
}
