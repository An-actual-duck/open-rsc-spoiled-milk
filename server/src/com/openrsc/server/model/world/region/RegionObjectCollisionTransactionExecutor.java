package com.openrsc.server.model.world.region;

import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionTransactionContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionTransactionContract.PackedRegionCoordinate;
import com.openrsc.server.event.rsc.GameTickEventRestorationCommitRequest;
import com.openrsc.server.event.rsc.GameTickEventRestorationMutationIntent.AuthoredConstructionKind;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetDecision.TargetOperation;
import com.openrsc.server.event.rsc.GameTickEventRestorationTransientRollbackSnapshot;
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

	/**
	 * Consumes one scheduler-fenced request inside the same ordered Region and
	 * membership boundaries as ordinary object/collision transactions. This
	 * disconnected seam supports only rollback-complete shapes, including an
	 * exact authored transient whose constructor and collision contribution can
	 * be captured and reversibly revalidated inside the boundary.
	 */
	static RestorationResult executeRestoration(
		final List<RegionObjectCollisionMutationBoundary> transactionBoundaries,
		final Region targetRegion,
		final GameTickEventRestorationCommitRequest request,
		final GameObject observedCandidate,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			candidateUnregisterFootprint,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			candidateRollbackRegisterFootprint,
		final GameObject desiredObject,
		final GameTickEventRestorationCollisionFootprintPlanner.Result
			desiredRegisterFootprint,
		final RegionCollisionFootprintMutationExecutor.MutableTileAccess tileAccess,
		final CacheInvalidator cacheInvalidator) {
		List<RegionObjectCollisionMutationBoundary> checkedBoundaries =
			Objects.requireNonNull(transactionBoundaries, "transactionBoundaries");
		Region checkedRegion = Objects.requireNonNull(targetRegion, "targetRegion");
		GameTickEventRestorationCommitRequest checkedRequest =
			Objects.requireNonNull(request, "request");
		RegionCollisionFootprintMutationExecutor.MutableTileAccess
			checkedTileAccess = Objects.requireNonNull(tileAccess, "tileAccess");
		CacheInvalidator checkedInvalidator = Objects.requireNonNull(
			cacheInvalidator, "cacheInvalidator");
		if (!checkedRequest.isEventExecutionBoundaryHeld()
			|| checkedRequest.isSchedulerStoreBoundaryHeld()
			|| !checkedRequest.isRegistrationRevalidated()
			|| !checkedRequest.isLifecycleBoundaryHeld()
			|| checkedRequest.getProposalGeneration()
				!= checkedRequest.getAuthoredGeneration()) {
			throw new IllegalArgumentException(
				"Restoration request escaped its scheduler boundaries");
		}
		if (observedCandidate == null
			!= (candidateUnregisterFootprint == null
				&& candidateRollbackRegisterFootprint == null)) {
			throw new IllegalArgumentException(
				"Restoration candidate footprint state is incomplete");
		}
		Change oldChange = observedCandidate == null ? null : Change.oldObject(
			checkedRegion, observedCandidate,
			Objects.requireNonNull(
				candidateUnregisterFootprint,
				"candidateUnregisterFootprint"),
			Objects.requireNonNull(
				candidateRollbackRegisterFootprint,
				"candidateRollbackRegisterFootprint"));
		Change newChange = desiredObject == null ? null : Change.newObject(
			checkedRegion, desiredObject,
			Objects.requireNonNull(
				desiredRegisterFootprint, "desiredRegisterFootprint"));
		if ((checkedRequest.getTargetOperation() == TargetOperation.SCENERY_SPAWN)
			!= (newChange != null)
			|| (checkedRequest.getTargetOperation()
				== TargetOperation.SCENERY_REMOVE && desiredRegisterFootprint != null)
			|| checkedRequest.getTargetOperation() == TargetOperation.UNAVAILABLE) {
			throw new IllegalArgumentException(
				"Restoration request and desired object disagree");
		}

		List<PackedRegionCoordinate> union = requiredRestorationRegionUnion(
			checkedRequest, oldChange, newChange);
		if (!matchesBoundaryCoverage(checkedBoundaries, union)) {
			throw new IllegalArgumentException(
				"Restoration transaction boundary coverage is not exact");
		}
		final RestorationResult[] result = new RestorationResult[1];
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
								"Restoration escaped its Region boundaries");
						}
						synchronized (
							checkedRegion.getGameObjectTransactionMonitor()) {
							result[0] = executeRestorationInsideBoundaries(
								checkedBoundaries, checkedRegion,
								checkedRequest, oldChange, newChange,
								checkedTileAccess, checkedInvalidator);
						}
					}
				});
		if (!execution.isMutationOperationInvoked()
			|| !execution.wereAllBoundariesHeldDuringOperation()
			|| result[0] == null) {
			throw new IllegalStateException(
				"Restoration transaction did not complete");
		}
		return result[0].withBoundaryCount(
			execution.getDeclaredBoundaryCount());
	}

	private static RestorationResult executeRestorationInsideBoundaries(
		final List<RegionObjectCollisionMutationBoundary> transactionBoundaries,
		final Region targetRegion,
		final GameTickEventRestorationCommitRequest request,
		final Change oldChange,
		final Change newChange,
		final RegionCollisionFootprintMutationExecutor.MutableTileAccess tileAccess,
		final CacheInvalidator cacheInvalidator) {
		Region.RestorationTargetMatchRequirement requirement =
			Region.RestorationTargetMatchRequirement.of(
				request.getObjectId(), request.getPermanentObjectId(),
				request.getX(), request.getY(), request.getDirection(),
				request.getType(), null, 0,
				request.getAuthoredGeneration(),
				request.getAuthoredPackedRegionX(),
				request.getAuthoredPackedRegionY(),
				request.getAuthoredSourceOrdinal(),
				request.getAuthoredConstructionKind());
		Region.RestorationTargetBoundarySnapshot target =
			targetRegion.captureRestorationTargetBoundarySnapshot(
				requirement, request.isTargetBindingComplete());
		if (oldChange == null) {
			if (target.getSlotObjectCount() != 0) {
				return RestorationResult.refused(
					RestorationReason.TARGET_CHANGED_BEFORE_COMMIT);
			}
		} else if (target.getSlotObjectCount() != 1
			|| !targetRegion.containsGameObjectIdentityUnderTransaction(
				oldChange.object)) {
			return RestorationResult.refused(
				RestorationReason.TARGET_CHANGED_BEFORE_COMMIT);
		}

		switch (request.getTargetOperation()) {
			case SCENERY_SPAWN:
				switch (target.getObservedTargetState()) {
					case EXACT_RESTORATION_SCENERY_PRESENT:
						return RestorationResult.noOp();
					case EMPTY:
						if (oldChange != null || newChange == null) {
							return RestorationResult.refused(
								RestorationReason.TARGET_CHANGED_BEFORE_COMMIT);
						}
						return applyRestorationTransaction(
							transactionBoundaries, null, newChange,
							tileAccess, cacheInvalidator);
					case EXACT_AUTHORED_TRANSIENT_PRESENT:
						return applyTransientReplacement(
							transactionBoundaries, request, target,
							oldChange, newChange, tileAccess,
							cacheInvalidator);
					default:
						return RestorationResult.refused(
							RestorationReason.TARGET_CLASSIFICATION_REFUSED);
				}
			case SCENERY_REMOVE:
				switch (target.getObservedTargetState()) {
					case EMPTY:
						return RestorationResult.noOp();
					case EXACT_RESTORATION_SCENERY_PRESENT:
						if (oldChange == null || newChange != null) {
							return RestorationResult.refused(
								RestorationReason.TARGET_CHANGED_BEFORE_COMMIT);
						}
						return applyRestorationTransaction(
							transactionBoundaries, oldChange, null,
							tileAccess, cacheInvalidator);
					default:
						return RestorationResult.refused(
							RestorationReason.TARGET_CLASSIFICATION_REFUSED);
				}
			default:
				return RestorationResult.refused(
					RestorationReason.TARGET_CLASSIFICATION_REFUSED);
		}
	}

	private static RestorationResult applyTransientReplacement(
		final List<RegionObjectCollisionMutationBoundary> transactionBoundaries,
		final GameTickEventRestorationCommitRequest request,
		final Region.RestorationTargetBoundarySnapshot target,
		final Change oldChange,
		final Change newChange,
		final RegionCollisionFootprintMutationExecutor.MutableTileAccess tileAccess,
		final CacheInvalidator cacheInvalidator) {
		if (oldChange == null || newChange == null) {
			return RestorationResult.refused(
				RestorationReason.TARGET_CHANGED_BEFORE_COMMIT);
		}
		if (!collisionRollbackIsExact(
				oldChange.forward, oldChange.rollback)) {
			return RestorationResult.refused(
				RestorationReason.TRANSIENT_COLLISION_ROLLBACK_MISMATCH);
		}
		if (oldChange.object.getAuthoredPlacementIdentity() == null) {
			return RestorationResult.refused(
				RestorationReason.TRANSIENT_ROLLBACK_SNAPSHOT_REFUSED);
		}
		AuthoredConstructionKind constructionKind;
		try {
			constructionKind = AuthoredConstructionKind.valueOf(
				oldChange.object.getAuthoredPlacementIdentity()
					.getConstructionKind().name());
		} catch (IllegalArgumentException unsupported) {
			return RestorationResult.refused(
				RestorationReason.TRANSIENT_ROLLBACK_SNAPSHOT_REFUSED);
		}
		GameTickEventRestorationTransientRollbackSnapshot.Candidate candidate =
			GameTickEventRestorationTransientRollbackSnapshot.Candidate.declare(
				oldChange.object.getID(),
				oldChange.object.getLoc().getPermId(),
				oldChange.object.getX(), oldChange.object.getY(),
				oldChange.object.getDirection(), oldChange.object.getType(),
				oldChange.object.getOwner(),
				oldChange.object.getRuntimeAttributeCount(),
				oldChange.object.getAuthoredPlacementIdentity().getGeneration(),
				oldChange.object.getAuthoredPlacementIdentity().getPackedRegionX(),
				oldChange.object.getAuthoredPlacementIdentity().getPackedRegionY(),
				oldChange.object.getAuthoredPlacementIdentity().getSourceOrdinal(),
				constructionKind, target.getSlotObjectCount(), true, true, true,
				oldChange.forward.getContributions());
		GameTickEventRestorationTransientRollbackSnapshot.Creation snapshot =
			GameTickEventRestorationTransientRollbackSnapshot.assess(
				request, candidate);
		if (!snapshot.isSnapshotAvailable()) {
			return RestorationResult.refused(
				RestorationReason.TRANSIENT_ROLLBACK_SNAPSHOT_REFUSED);
		}
		return applyRestorationTransaction(
			transactionBoundaries, oldChange, newChange,
			tileAccess, cacheInvalidator);
	}

	private static boolean collisionRollbackIsExact(
		final GameTickEventRestorationCollisionFootprintPlanner.Result forward,
		final GameTickEventRestorationCollisionFootprintPlanner.Result rollback) {
		if (!forward.isFootprintAvailable()
			|| !rollback.isFootprintAvailable()
			|| forward.getOperation() != Operation.UNREGISTER
			|| rollback.getOperation() != Operation.REGISTER
			|| forward.isLegacySaturatingUnregister()
			|| rollback.isLegacySaturatingUnregister()
			|| !forward.getRequiredRegions().equals(
				rollback.getRequiredRegions())
			|| forward.getContributionTileCount()
				!= rollback.getContributionTileCount()) {
			return false;
		}
		for (int index = 0;
				index < forward.getContributionTileCount(); index++) {
			GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution left =
					forward.getContributions().get(index);
			GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution right =
					rollback.getContributions().get(index);
			if (left.getX() != right.getX()
				|| left.getY() != right.getY()
				|| left.getBlockingSceneryCount()
					!= right.getBlockingSceneryCount()
				|| left.getDynamicProjectileCount()
					!= right.getDynamicProjectileCount()) {
				return false;
			}
			for (int bit = 0; bit < 6; bit++) {
				if (left.getDynamicCollisionCount(bit)
					!= right.getDynamicCollisionCount(bit)) {
					return false;
				}
			}
		}
		return true;
	}

	private static RestorationResult applyRestorationTransaction(
		final List<RegionObjectCollisionMutationBoundary> transactionBoundaries,
		final Change oldChange,
		final Change newChange,
		final RegionCollisionFootprintMutationExecutor.MutableTileAccess tileAccess,
		final CacheInvalidator cacheInvalidator) {
		Result applied = executeInsideBoundaries(
			transactionBoundaries, oldChange, newChange,
			tileAccess, cacheInvalidator);
		return applied.isApplied()
			? RestorationResult.applied(
				applied.isMembershipRemoved(),
				applied.isMembershipRegistered())
			: RestorationResult.refused(
				RestorationReason.OBJECT_TRANSACTION_REFUSED);
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

	private static List<PackedRegionCoordinate> requiredRestorationRegionUnion(
		final GameTickEventRestorationCommitRequest request,
		final Change oldChange,
		final Change newChange) {
		List<PackedRegionCoordinate> result =
			new ArrayList<PackedRegionCoordinate>();
		result.add(PackedRegionCoordinate.of(
			Math.floorDiv(
				request.getX(),
				GameTickEventRestorationCollisionTransactionContract
					.PACKED_REGION_SIZE),
			Math.floorDiv(
				request.getY(),
				GameTickEventRestorationCollisionTransactionContract
					.PACKED_REGION_SIZE)));
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

	enum RestorationOutcome { REFUSED, NO_OP, APPLIED }

	enum RestorationReason {
		TARGET_CHANGED_BEFORE_COMMIT,
		TARGET_CLASSIFICATION_REFUSED,
		TRANSIENT_ROLLBACK_STATE_NOT_CONNECTED,
		TRANSIENT_ROLLBACK_SNAPSHOT_REFUSED,
		TRANSIENT_COLLISION_ROLLBACK_MISMATCH,
		OBJECT_TRANSACTION_REFUSED,
		DESIRED_STATE_ALREADY_SATISFIED,
		RESTORATION_APPLIED
	}

	static final class RestorationResult {
		private final RestorationOutcome outcome;
		private final RestorationReason reason;
		private final boolean removed;
		private final boolean registered;
		private final int boundaryCount;

		private RestorationResult(
			final RestorationOutcome outcome,
			final RestorationReason reason,
			final boolean removed,
			final boolean registered,
			final int boundaryCount) {
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.removed = removed;
			this.registered = registered;
			this.boundaryCount = boundaryCount;
			if (boundaryCount < 0
				|| (outcome == RestorationOutcome.APPLIED)
					!= (reason == RestorationReason.RESTORATION_APPLIED)
				|| (outcome == RestorationOutcome.NO_OP)
					!= (reason
						== RestorationReason.DESIRED_STATE_ALREADY_SATISFIED)
				|| (outcome != RestorationOutcome.APPLIED
					&& (removed || registered))) {
				throw new IllegalArgumentException(
					"Restoration transaction result is inconsistent");
			}
		}

		private static RestorationResult refused(
			final RestorationReason reason) {
			return new RestorationResult(
				RestorationOutcome.REFUSED, reason, false, false, 0);
		}

		private static RestorationResult noOp() {
			return new RestorationResult(
				RestorationOutcome.NO_OP,
				RestorationReason.DESIRED_STATE_ALREADY_SATISFIED,
				false, false, 0);
		}

		private static RestorationResult applied(
			final boolean removed,
			final boolean registered) {
			return new RestorationResult(
				RestorationOutcome.APPLIED,
				RestorationReason.RESTORATION_APPLIED,
				removed, registered, 0);
		}

		private RestorationResult withBoundaryCount(final int count) {
			return new RestorationResult(
				outcome, reason, removed, registered, count);
		}

		RestorationOutcome getOutcome() { return outcome; }
		RestorationReason getReason() { return reason; }
		boolean isApplied() { return outcome == RestorationOutcome.APPLIED; }
		boolean isNoOp() { return outcome == RestorationOutcome.NO_OP; }
		boolean isRefused() { return outcome == RestorationOutcome.REFUSED; }
		boolean isMembershipRemoved() { return removed; }
		boolean isMembershipRegistered() { return registered; }
		int getBoundaryCount() { return boundaryCount; }
		boolean isRequestRetained() { return false; }
		boolean isCallbackInvoked() { return false; }
		boolean isEventCancellation() { return false; }
		boolean isEventReschedule() { return false; }
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
