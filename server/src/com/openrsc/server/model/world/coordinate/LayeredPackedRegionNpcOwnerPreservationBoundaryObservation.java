package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerPreservationRequirements.OwnerRequirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Detached result of one bounded, nested NPC-owner preservation-boundary
 * attempt.
 *
 * <p>Scheduler instance, exact registrations, event execution/timing, owner
 * object identity, and World-list membership must all agree in one operation.
 * NPC lifecycle exclusion and Region-absence quiescence remain independent
 * inner requirements.</p>
 *
 * <p>This value is stale after its operation returns. Even a scope-ready result
 * is not a reusable preservation fact, handle, permit, lease, commit token, or
 * lifecycle authority.</p>
 */
public final class
	LayeredPackedRegionNpcOwnerPreservationBoundaryObservation {
	public static final int MAXIMUM_OWNERS =
		LayeredPackedRegionNpcOwnerPreservationRequirements
			.MAXIMUM_OWNER_REQUIREMENTS;

	private final long generation;
	private final long requirementsObservedAtTick;
	private final long boundaryObservedAtTick;
	private final String schedulerInstanceIdentity;
	private final int requiredOwnerCount;
	private final int requiredEventLinkCount;
	private final int separateNonNpcOwnerEventCount;
	private final boolean schedulerInstanceMatched;
	private final boolean registrationSetComplete;
	private final int eventExecutionBoundaryCount;
	private final int eventTimingBoundaryCount;
	private final boolean worldRegistrationBoundaryHeld;
	private final int npcLifecycleBoundaryCount;
	private final boolean regionAbsenceQuiescenceHeld;
	private final int exactReferenceOwnerCount;
	private final Reason reason;
	private final List<OwnerEvidence> owners;

	private LayeredPackedRegionNpcOwnerPreservationBoundaryObservation(
		final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
		final long boundaryObservedAtTick,
		final boolean schedulerInstanceMatched,
		final boolean registrationSetComplete,
		final int eventExecutionBoundaryCount,
		final int eventTimingBoundaryCount,
		final boolean worldRegistrationBoundaryHeld,
		final int npcLifecycleBoundaryCount,
		final boolean regionAbsenceQuiescenceHeld,
		final List<OwnerEvidence> owners,
		final Reason reason) {
		this.generation = requirements.getGeneration();
		this.requirementsObservedAtTick = requirements.getEventObservedAtTick();
		this.boundaryObservedAtTick = boundaryObservedAtTick;
		this.schedulerInstanceIdentity =
			requirements.getSchedulerInstanceIdentity();
		this.requiredOwnerCount = requirements.getUniqueNpcOwnerCount();
		this.requiredEventLinkCount = requirements.getEventLinkCount();
		this.separateNonNpcOwnerEventCount =
			requirements.getSeparateNonNpcOwnerEventCount();
		this.schedulerInstanceMatched = schedulerInstanceMatched;
		this.registrationSetComplete = registrationSetComplete;
		this.eventExecutionBoundaryCount = eventExecutionBoundaryCount;
		this.eventTimingBoundaryCount = eventTimingBoundaryCount;
		this.worldRegistrationBoundaryHeld =
			worldRegistrationBoundaryHeld;
		this.npcLifecycleBoundaryCount = npcLifecycleBoundaryCount;
		this.regionAbsenceQuiescenceHeld =
			regionAbsenceQuiescenceHeld;
		this.owners = Collections.unmodifiableList(
			new ArrayList<OwnerEvidence>(owners));
		this.reason = Objects.requireNonNull(reason, "reason");

		int exactOwners = 0;
		for (OwnerEvidence owner : owners) {
			exactOwners += owner.getOutcome()
				== OwnerOutcome.EXACT_REFERENCE_BOUNDARY ? 1 : 0;
		}
		this.exactReferenceOwnerCount = exactOwners;
		if (requiredOwnerCount < 0 || requiredEventLinkCount < 0
			|| eventExecutionBoundaryCount < 0
			|| eventExecutionBoundaryCount > requiredEventLinkCount
			|| eventTimingBoundaryCount < 0
			|| eventTimingBoundaryCount > eventExecutionBoundaryCount
			|| npcLifecycleBoundaryCount < 0
			|| npcLifecycleBoundaryCount > requiredOwnerCount
			|| owners.size() > requiredOwnerCount
			|| exactReferenceOwnerCount > owners.size()
			|| (isReferenceBoundaryComplete()
				&& reason != Reason.NPC_LIFECYCLE_BOUNDARY_INCOMPLETE
				&& reason != Reason.REGION_ABSENCE_QUIESCENCE_UNPROVED
				&& reason != Reason.PRESERVATION_SCOPE_READY)
			|| (isPreservationScopeReadyAtBoundary()
				!= (reason == Reason.PRESERVATION_SCOPE_READY))) {
			throw new IllegalArgumentException(
				"NPC owner preservation boundary arithmetic is inconsistent");
		}
	}

	/**
	 * Classifies facts supplied by one runtime operation. Owner states must
	 * preserve the exact requirement order when they are available.
	 */
	public static
		LayeredPackedRegionNpcOwnerPreservationBoundaryObservation observe(
			final LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
			final long boundaryObservedAtTick,
			final boolean schedulerInstanceMatched,
			final boolean registrationSetComplete,
			final int eventExecutionBoundaryCount,
			final int eventTimingBoundaryCount,
			final boolean worldRegistrationBoundaryHeld,
			final int npcLifecycleBoundaryCount,
			final boolean regionAbsenceQuiescenceHeld,
			final List<OwnerBoundaryState> ownerStates,
			final int maximumOwners) {
		LayeredPackedRegionNpcOwnerPreservationRequirements checked =
			Objects.requireNonNull(requirements, "requirements");
		List<OwnerBoundaryState> checkedStates =
			Objects.requireNonNull(ownerStates, "ownerStates");
		if (boundaryObservedAtTick < checked.getEventObservedAtTick()
			|| maximumOwners < 0 || maximumOwners > MAXIMUM_OWNERS
			|| checked.getUniqueNpcOwnerCount() > maximumOwners
			|| checkedStates.size() > maximumOwners
			|| eventExecutionBoundaryCount < 0
			|| eventExecutionBoundaryCount > checked.getEventLinkCount()
			|| eventTimingBoundaryCount < 0
			|| eventTimingBoundaryCount > eventExecutionBoundaryCount
			|| npcLifecycleBoundaryCount < 0
			|| npcLifecycleBoundaryCount > checked.getUniqueNpcOwnerCount()) {
			throw new IllegalArgumentException(
				"NPC owner preservation boundary exceeds its bounds");
		}

		List<OwnerEvidence> owners =
			new ArrayList<OwnerEvidence>(checkedStates.size());
		for (int index = 0; index < checkedStates.size(); index++) {
			if (index >= checked.getOwners().size()) {
				throw new IllegalArgumentException(
					"Owner boundary evidence exceeds requirements");
			}
			owners.add(OwnerEvidence.assess(
				checked.getOwners().get(index),
				Objects.requireNonNull(
					checkedStates.get(index),
					"ownerStates[" + index + "]")));
		}

		Reason reason;
		if (!checked.isNpcRequirementSetComplete()) {
			reason = Reason.REQUIREMENTS_INCOMPLETE;
		} else if (!schedulerInstanceMatched) {
			reason = Reason.SCHEDULER_INSTANCE_MISMATCH;
		} else if (!registrationSetComplete) {
			reason = Reason.EVENT_REGISTRATION_INCOMPLETE;
		} else if (eventExecutionBoundaryCount
				!= checked.getEventLinkCount()) {
			reason = Reason.EVENT_EXECUTION_BOUNDARY_INCOMPLETE;
		} else if (eventTimingBoundaryCount
				!= checked.getEventLinkCount()) {
			reason = Reason.EVENT_TIMING_BOUNDARY_INCOMPLETE;
		} else if (!worldRegistrationBoundaryHeld) {
			reason = Reason.WORLD_REGISTRATION_BOUNDARY_MISSING;
		} else if (owners.size() != checked.getUniqueNpcOwnerCount()
			|| !allExact(owners)) {
			reason = Reason.OWNER_REFERENCE_CORRELATION_INCOMPLETE;
		} else if (npcLifecycleBoundaryCount
				!= checked.getUniqueNpcOwnerCount()) {
			reason = Reason.NPC_LIFECYCLE_BOUNDARY_INCOMPLETE;
		} else if (!regionAbsenceQuiescenceHeld) {
			reason = Reason.REGION_ABSENCE_QUIESCENCE_UNPROVED;
		} else {
			reason = Reason.PRESERVATION_SCOPE_READY;
		}
		return new
			LayeredPackedRegionNpcOwnerPreservationBoundaryObservation(
				checked, boundaryObservedAtTick, schedulerInstanceMatched,
				registrationSetComplete, eventExecutionBoundaryCount,
				eventTimingBoundaryCount, worldRegistrationBoundaryHeld,
				npcLifecycleBoundaryCount, regionAbsenceQuiescenceHeld,
				owners, reason);
	}

	private static boolean allExact(final List<OwnerEvidence> owners) {
		for (OwnerEvidence owner : owners) {
			if (owner.getOutcome()
					!= OwnerOutcome.EXACT_REFERENCE_BOUNDARY) {
				return false;
			}
		}
		return true;
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getBoundaryObservedAtTick() { return boundaryObservedAtTick; }
	public String getSchedulerInstanceIdentity() {
		return schedulerInstanceIdentity;
	}
	public int getRequiredOwnerCount() { return requiredOwnerCount; }
	public int getRequiredEventLinkCount() { return requiredEventLinkCount; }
	public int getSeparateNonNpcOwnerEventCount() {
		return separateNonNpcOwnerEventCount;
	}
	public boolean isSchedulerInstanceMatched() {
		return schedulerInstanceMatched;
	}
	public boolean isRegistrationSetComplete() {
		return registrationSetComplete;
	}
	public int getEventExecutionBoundaryCount() {
		return eventExecutionBoundaryCount;
	}
	public int getEventTimingBoundaryCount() {
		return eventTimingBoundaryCount;
	}
	public boolean isWorldRegistrationBoundaryHeld() {
		return worldRegistrationBoundaryHeld;
	}
	public int getNpcLifecycleBoundaryCount() {
		return npcLifecycleBoundaryCount;
	}
	public boolean isRegionAbsenceQuiescenceHeld() {
		return regionAbsenceQuiescenceHeld;
	}
	public int getExactReferenceOwnerCount() {
		return exactReferenceOwnerCount;
	}
	public Reason getReason() { return reason; }
	public List<OwnerEvidence> getOwners() { return owners; }
	public boolean isReferenceBoundaryComplete() {
		return schedulerInstanceMatched && registrationSetComplete
			&& eventExecutionBoundaryCount == requiredEventLinkCount
			&& eventTimingBoundaryCount == requiredEventLinkCount
			&& worldRegistrationBoundaryHeld
			&& owners.size() == requiredOwnerCount
			&& exactReferenceOwnerCount == requiredOwnerCount;
	}
	public boolean isPreservationScopeReadyAtBoundary() {
		return isReferenceBoundaryComplete()
			&& npcLifecycleBoundaryCount == requiredOwnerCount
			&& regionAbsenceQuiescenceHeld;
	}
	public boolean isPointInTimeOnly() { return true; }
	public boolean isPreservationFactEstablished() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isPreservationPerformed() { return false; }
	public boolean isEventReschedule() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum Reason {
		REQUIREMENTS_INCOMPLETE,
		SCHEDULER_INSTANCE_MISMATCH,
		EVENT_REGISTRATION_INCOMPLETE,
		EVENT_EXECUTION_BOUNDARY_INCOMPLETE,
		EVENT_TIMING_BOUNDARY_INCOMPLETE,
		WORLD_REGISTRATION_BOUNDARY_MISSING,
		OWNER_REFERENCE_CORRELATION_INCOMPLETE,
		NPC_LIFECYCLE_BOUNDARY_INCOMPLETE,
		REGION_ABSENCE_QUIESCENCE_UNPROVED,
		PRESERVATION_SCOPE_READY
	}

	public enum OwnerOutcome {
		EXACT_REFERENCE_BOUNDARY,
		EVENT_LINK_COUNT_MISMATCH,
		EVENT_OWNER_IDENTITY_MISMATCH,
		EVENT_OWNER_INSTANCE_MISMATCH,
		WORLD_OWNER_NOT_FOUND,
		WORLD_OWNER_AMBIGUOUS,
		WORLD_OWNER_INSTANCE_MISMATCH,
		OWNER_INACTIVE
	}

	/** Primitive facts captured while runtime handles remain locally fenced. */
	public static final class OwnerBoundaryState {
		private final long generation;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int sourceOrdinal;
		private final int runtimeNpcId;
		private final int validatedEventLinkCount;
		private final boolean eventOwnerIdentityMatched;
		private final boolean sameRuntimeInstanceAcrossEventLinks;
		private final int worldIdentityMatchCount;
		private final boolean sameInstanceRegisteredInWorld;
		private final boolean ownerActive;

		private OwnerBoundaryState(
			final OwnerRequirement requirement,
			final int validatedEventLinkCount,
			final boolean eventOwnerIdentityMatched,
			final boolean sameRuntimeInstanceAcrossEventLinks,
			final int worldIdentityMatchCount,
			final boolean sameInstanceRegisteredInWorld,
			final boolean ownerActive) {
			OwnerRequirement checked =
				Objects.requireNonNull(requirement, "requirement");
			this.generation = checked.getGeneration();
			this.packedRegionX = checked.getPackedRegionX();
			this.packedRegionY = checked.getPackedRegionY();
			this.sourceOrdinal = checked.getSourceOrdinal();
			this.runtimeNpcId = checked.getRuntimeNpcId();
			this.validatedEventLinkCount = validatedEventLinkCount;
			this.eventOwnerIdentityMatched = eventOwnerIdentityMatched;
			this.sameRuntimeInstanceAcrossEventLinks =
				sameRuntimeInstanceAcrossEventLinks;
			this.worldIdentityMatchCount = worldIdentityMatchCount;
			this.sameInstanceRegisteredInWorld =
				sameInstanceRegisteredInWorld;
			this.ownerActive = ownerActive;
			if (validatedEventLinkCount < 0
				|| validatedEventLinkCount
					> checked.getEventRegistrationSequences().size()
				|| worldIdentityMatchCount < 0
				|| (sameInstanceRegisteredInWorld
					&& worldIdentityMatchCount != 1)
				|| (ownerActive && !sameInstanceRegisteredInWorld)) {
				throw new IllegalArgumentException(
					"NPC owner boundary state is inconsistent");
			}
		}

		public static OwnerBoundaryState observe(
			final OwnerRequirement requirement,
			final int validatedEventLinkCount,
			final boolean eventOwnerIdentityMatched,
			final boolean sameRuntimeInstanceAcrossEventLinks,
			final int worldIdentityMatchCount,
			final boolean sameInstanceRegisteredInWorld,
			final boolean ownerActive) {
			return new OwnerBoundaryState(
				requirement, validatedEventLinkCount,
				eventOwnerIdentityMatched,
				sameRuntimeInstanceAcrossEventLinks,
				worldIdentityMatchCount, sameInstanceRegisteredInWorld,
				ownerActive);
		}
	}

	/** Stable owner-order assessment containing no runtime reference. */
	public static final class OwnerEvidence {
		private final long generation;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int sourceOrdinal;
		private final int runtimeNpcId;
		private final int requiredEventLinkCount;
		private final int validatedEventLinkCount;
		private final int worldIdentityMatchCount;
		private final OwnerOutcome outcome;

		private OwnerEvidence(
			final OwnerRequirement requirement,
			final OwnerBoundaryState state,
			final OwnerOutcome outcome) {
			this.generation = requirement.getGeneration();
			this.packedRegionX = requirement.getPackedRegionX();
			this.packedRegionY = requirement.getPackedRegionY();
			this.sourceOrdinal = requirement.getSourceOrdinal();
			this.runtimeNpcId = requirement.getRuntimeNpcId();
			this.requiredEventLinkCount =
				requirement.getEventRegistrationSequences().size();
			this.validatedEventLinkCount =
				state.validatedEventLinkCount;
			this.worldIdentityMatchCount = state.worldIdentityMatchCount;
			this.outcome = Objects.requireNonNull(outcome, "outcome");
		}

		private static OwnerEvidence assess(
			final OwnerRequirement requirement,
			final OwnerBoundaryState state) {
			if (requirement.getGeneration() != state.generation
				|| requirement.getPackedRegionX() != state.packedRegionX
				|| requirement.getPackedRegionY() != state.packedRegionY
				|| requirement.getSourceOrdinal() != state.sourceOrdinal
				|| requirement.getRuntimeNpcId() != state.runtimeNpcId) {
				throw new IllegalArgumentException(
					"Owner boundary state order or identity is inconsistent");
			}
			int requiredLinks =
				requirement.getEventRegistrationSequences().size();
			OwnerOutcome outcome =
				state.validatedEventLinkCount != requiredLinks
					? OwnerOutcome.EVENT_LINK_COUNT_MISMATCH
					: !state.eventOwnerIdentityMatched
						? OwnerOutcome.EVENT_OWNER_IDENTITY_MISMATCH
						: !state.sameRuntimeInstanceAcrossEventLinks
							? OwnerOutcome.EVENT_OWNER_INSTANCE_MISMATCH
							: state.worldIdentityMatchCount == 0
								? OwnerOutcome.WORLD_OWNER_NOT_FOUND
								: state.worldIdentityMatchCount != 1
									? OwnerOutcome.WORLD_OWNER_AMBIGUOUS
									: !state.sameInstanceRegisteredInWorld
										? OwnerOutcome
											.WORLD_OWNER_INSTANCE_MISMATCH
										: !state.ownerActive
											? OwnerOutcome.OWNER_INACTIVE
											: OwnerOutcome
												.EXACT_REFERENCE_BOUNDARY;
			return new OwnerEvidence(requirement, state, outcome);
		}

		public long getGeneration() { return generation; }
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getSourceOrdinal() { return sourceOrdinal; }
		public int getRuntimeNpcId() { return runtimeNpcId; }
		public int getRequiredEventLinkCount() {
			return requiredEventLinkCount;
		}
		public int getValidatedEventLinkCount() {
			return validatedEventLinkCount;
		}
		public int getWorldIdentityMatchCount() {
			return worldIdentityMatchCount;
		}
		public OwnerOutcome getOutcome() { return outcome; }
	}
}
