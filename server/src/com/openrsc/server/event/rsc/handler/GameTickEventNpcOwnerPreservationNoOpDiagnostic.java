package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.util.EntityList;

import java.util.Objects;

/**
 * Verification-only production adapter for the ephemeral NPC-owner
 * preservation lifecycle.
 *
 * <p>The real event/registration/World/NPC scope is entered, but the source
 * lifecycle always returns a typed unavailable refusal. No absence,
 * reconstruction, preserved consumer, Region mutation, or visibility action
 * can occur through this diagnostic.</p>
 */
final class GameTickEventNpcOwnerPreservationNoOpDiagnostic {
	private GameTickEventNpcOwnerPreservationNoOpDiagnostic() {
	}

	static Result capture(
		final GameTickEventStore eventStore,
		final EntityList<Npc> worldNpcs,
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final long boundaryObservedAtTick,
		final int maximumOwners,
		final boolean regionLifecycleBoundaryHeld) {
		GameTickEventStore checkedStore =
			Objects.requireNonNull(eventStore, "eventStore");
		EntityList<Npc> checkedWorldNpcs =
			Objects.requireNonNull(worldNpcs, "worldNpcs");
		LayeredPackedRegionNpcOwnerPreservationRequirements checked =
			Objects.requireNonNull(requirements, "requirements");
		final GameTickEventNpcOwnerPreservationLifecycle.Result[]
			lifecycleResult =
				new GameTickEventNpcOwnerPreservationLifecycle.Result[1];
		final boolean[] lifecycleInvoked = new boolean[1];
		final boolean[] preservedConsumerInvoked = new boolean[1];

		boolean scopeEntered =
			GameTickEventNpcOwnerPreservationBoundary.withinPreservationScope(
				checkedStore, checkedWorldNpcs, checked,
				boundaryObservedAtTick, maximumOwners,
				regionLifecycleBoundaryHeld, scope -> {
					lifecycleResult[0] =
						GameTickEventNpcOwnerPreservationLifecycle.execute(
							scope,
							request -> {
								lifecycleInvoked[0] = true;
								return request.refused(
									GameTickEventNpcOwnerPreservationLifecycle
										.LifecycleRefusalReason
										.SOURCE_SET_UNAVAILABLE);
							},
							evidence -> preservedConsumerInvoked[0] = true);
				});

		if (!scopeEntered) {
			if (lifecycleInvoked[0] || lifecycleResult[0] != null
				|| preservedConsumerInvoked[0]) {
				throw new IllegalStateException(
					"Refused owner scope invoked preservation work");
			}
			return Result.scopeRefused(checked);
		}
		GameTickEventNpcOwnerPreservationLifecycle.Result result =
			Objects.requireNonNull(
				lifecycleResult[0], "preservation lifecycle result");
		if (!lifecycleInvoked[0] || preservedConsumerInvoked[0]
			|| result.getReason()
				!= GameTickEventNpcOwnerPreservationLifecycle.Reason
					.SOURCE_LIFECYCLE_REFUSED
			|| result.getLifecycleRefusalReason()
				!= GameTickEventNpcOwnerPreservationLifecycle
					.LifecycleRefusalReason.SOURCE_SET_UNAVAILABLE
			|| result.getAbsentSourceCount() != 0
			|| result.getReconstructedSourceCount() != 0
			|| result.isPreservedConsumerInvoked()
			|| result.isPreservationEstablishedForConsumedWork()) {
			throw new IllegalStateException(
				"Verification-only owner lifecycle crossed its refusal");
		}
		return Result.lifecycleUnavailable(checked, result);
	}

	enum Reason {
		OWNER_SCOPE_REFUSED,
		SOURCE_LIFECYCLE_UNAVAILABLE
	}

	/** Detached diagnostic result with no authority after capture. */
	static final class Result {
		private final long generation;
		private final long requirementsObservedAtTick;
		private final int selectedSourceCount;
		private final int requiredEventLinkCount;
		private final int requiredOwnerCount;
		private final boolean ownerScopeEntered;
		private final boolean sourceLifecycleInvoked;
		private final int absentSourceCount;
		private final int reconstructedSourceCount;
		private final boolean preservedConsumerInvoked;
		private final Reason reason;

		private Result(
			final LayeredPackedRegionNpcOwnerPreservationRequirements
				requirements,
			final boolean ownerScopeEntered,
			final boolean sourceLifecycleInvoked,
			final int absentSourceCount,
			final int reconstructedSourceCount,
			final boolean preservedConsumerInvoked,
			final Reason reason) {
			this.generation = requirements.getGeneration();
			this.requirementsObservedAtTick =
				requirements.getEventObservedAtTick();
			this.selectedSourceCount =
				requirements.getSelectedSourceCount();
			this.requiredEventLinkCount =
				requirements.getEventLinkCount();
			this.requiredOwnerCount =
				requirements.getUniqueNpcOwnerCount();
			this.ownerScopeEntered = ownerScopeEntered;
			this.sourceLifecycleInvoked = sourceLifecycleInvoked;
			this.absentSourceCount = absentSourceCount;
			this.reconstructedSourceCount = reconstructedSourceCount;
			this.preservedConsumerInvoked = preservedConsumerInvoked;
			this.reason = Objects.requireNonNull(reason, "reason");
			if (absentSourceCount != 0 || reconstructedSourceCount != 0
				|| preservedConsumerInvoked
				|| (ownerScopeEntered != sourceLifecycleInvoked)
				|| (reason == Reason.OWNER_SCOPE_REFUSED
					&& ownerScopeEntered)
				|| (reason == Reason.SOURCE_LIFECYCLE_UNAVAILABLE
					&& !ownerScopeEntered)) {
				throw new IllegalArgumentException(
					"Owner preservation no-op diagnostic is inconsistent");
			}
		}

		private static Result scopeRefused(
			final LayeredPackedRegionNpcOwnerPreservationRequirements
				requirements) {
			return new Result(
				requirements, false, false, 0, 0, false,
				Reason.OWNER_SCOPE_REFUSED);
		}

		private static Result lifecycleUnavailable(
			final LayeredPackedRegionNpcOwnerPreservationRequirements
				requirements,
			final GameTickEventNpcOwnerPreservationLifecycle.Result result) {
			return new Result(
				requirements, true, true,
				result.getAbsentSourceCount(),
				result.getReconstructedSourceCount(),
				result.isPreservedConsumerInvoked(),
				Reason.SOURCE_LIFECYCLE_UNAVAILABLE);
		}

		long getGeneration() { return generation; }
		long getRequirementsObservedAtTick() {
			return requirementsObservedAtTick;
		}
		int getSelectedSourceCount() { return selectedSourceCount; }
		int getRequiredEventLinkCount() { return requiredEventLinkCount; }
		int getRequiredOwnerCount() { return requiredOwnerCount; }
		boolean isOwnerScopeEntered() { return ownerScopeEntered; }
		boolean isSourceLifecycleInvoked() {
			return sourceLifecycleInvoked;
		}
		int getAbsentSourceCount() { return absentSourceCount; }
		int getReconstructedSourceCount() {
			return reconstructedSourceCount;
		}
		boolean isPreservedConsumerInvoked() {
			return preservedConsumerInvoked;
		}
		Reason getReason() { return reason; }
		boolean isPreservationEstablishedForConsumedWork() { return false; }
		boolean isPreservationPerformed() { return false; }
		boolean isSourceAbsencePerformed() { return false; }
		boolean isSourceReconstructionPerformed() { return false; }
		boolean isRegionMutationPerformed() { return false; }
		boolean isRuntimeHandleRetained() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isVisibilityReleased() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}
}
