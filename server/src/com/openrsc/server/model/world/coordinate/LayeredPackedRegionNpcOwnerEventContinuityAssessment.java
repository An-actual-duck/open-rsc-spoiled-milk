package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcResidencyObservation.ActiveResidencyClassification;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcResidencyObservation.IdentityStatus;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcResidencyObservation.InstanceEvidence;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.EventRecord;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.NpcOwnerIdentity;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.OwnerKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Correlates proposal-related owner-position callbacks with one bounded active
 * NPC census.
 *
 * <p>An exact authored identity can establish that one callback and one census
 * entry describe the same authored NPC. It does not establish that the NPC or
 * callback will survive a later source lifecycle. Consequently the production
 * diagnostic supplies no preservation proof and matched callbacks remain
 * {@link Outcome#OWNER_PRESERVATION_UNPROVED}.</p>
 *
 * <p>This immutable value consumes detached evidence only. It has no event,
 * entity, Region, scheduler, registry, cache, loading, retention, release,
 * teardown, reconstruction, arrival, transaction, permit, lease, commit, or
 * lifecycle authority.</p>
 */
public final class LayeredPackedRegionNpcOwnerEventContinuityAssessment {
	public static final int MAXIMUM_DETAILS =
		LayeredPackedRegionEventOwnershipInventory.MAXIMUM_EVENTS;

	private final long generation;
	private final long eventObservedAtTick;
	private final long censusObservedAtTick;
	private final int selectedSourceCount;
	private final int proposalRelatedEventCount;
	private final int relatedOwnerPositionHintEventCount;
	private final int npcOwnerPositionHintEventCount;
	private final int capturedNpcOwnerIdentityCount;
	private final int uniquelyMatchedActiveOwnerCount;
	private final int continuityEligibleEventCount;
	private final int preservationUnprovedEventCount;
	private final int hardBlockerEventCount;
	private final boolean exactSelectionAligned;
	private final boolean ownerPreservationProved;
	private final Long firstUnmetRegistrationSequence;
	private final Outcome firstUnmetOutcome;
	private final List<EventAssessment> events;

	private LayeredPackedRegionNpcOwnerEventContinuityAssessment(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final LayeredPackedRegionActiveNpcResidencyObservation observation,
		final boolean ownerPreservationProved,
		final List<EventAssessment> events) {
		this.generation = inventory.getProposalGeneration();
		this.eventObservedAtTick = inventory.getObservedAtTick();
		this.censusObservedAtTick = observation.getCensusObservedAtTick();
		this.selectedSourceCount = inventory.getSourceCount();
		this.proposalRelatedEventCount =
			inventory.getCandidateRelatedEventCount();
		this.events = Collections.unmodifiableList(
			new ArrayList<EventAssessment>(events));
		this.exactSelectionAligned = true;
		this.ownerPreservationProved = ownerPreservationProved;

		int npcHints = 0;
		int capturedIdentity = 0;
		int uniqueMatch = 0;
		int eligible = 0;
		int preservationUnproved = 0;
		int hardBlocker = 0;
		Long firstRegistration = null;
		Outcome firstOutcome = null;
		for (EventAssessment event : events) {
			npcHints += event.getOwnerKind() == OwnerKind.NPC ? 1 : 0;
			capturedIdentity += event.isNpcOwnerIdentityCaptured() ? 1 : 0;
			uniqueMatch += event.isUniqueActiveOwnerMatch() ? 1 : 0;
			eligible += event.getOutcome()
				== Outcome.OWNER_CONTINUITY_ELIGIBLE ? 1 : 0;
			preservationUnproved += event.getOutcome()
				== Outcome.OWNER_PRESERVATION_UNPROVED ? 1 : 0;
			hardBlocker += event.getOutcome().isHardBlocker() ? 1 : 0;
			if (firstRegistration == null
				&& event.getOutcome()
					!= Outcome.OWNER_CONTINUITY_ELIGIBLE) {
				firstRegistration = Long.valueOf(
					event.getRegistrationSequence());
				firstOutcome = event.getOutcome();
			}
		}
		this.relatedOwnerPositionHintEventCount = events.size();
		this.npcOwnerPositionHintEventCount = npcHints;
		this.capturedNpcOwnerIdentityCount = capturedIdentity;
		this.uniquelyMatchedActiveOwnerCount = uniqueMatch;
		this.continuityEligibleEventCount = eligible;
		this.preservationUnprovedEventCount = preservationUnproved;
		this.hardBlockerEventCount = hardBlocker;
		this.firstUnmetRegistrationSequence = firstRegistration;
		this.firstUnmetOutcome = firstOutcome;

		if (events.size() != eligible + preservationUnproved + hardBlocker
			|| capturedIdentity > npcHints || uniqueMatch > capturedIdentity
			|| eligible > uniqueMatch || preservationUnproved > uniqueMatch
			|| (firstRegistration == null) != (firstOutcome == null)
			|| (isAllRelatedOwnerContinuityReadyAtObservation()
				&& (!ownerPreservationProved || events.isEmpty()))) {
			throw new IllegalArgumentException(
				"NPC owner-event continuity arithmetic is inconsistent");
		}
	}

	/**
	 * Produces one stable event-order assessment. The caller must establish that
	 * the census selected the inventory's exact ordered packed source set;
	 * source-count coincidence alone is deliberately insufficient.
	 */
	public static LayeredPackedRegionNpcOwnerEventContinuityAssessment assess(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final LayeredPackedRegionActiveNpcResidencyObservation observation,
		final boolean exactSelectionAligned,
		final boolean ownerPreservationProved,
		final int maximumDetails) {
		LayeredPackedRegionEventOwnershipInventory checkedInventory =
			Objects.requireNonNull(inventory, "inventory");
		LayeredPackedRegionActiveNpcResidencyObservation checkedObservation =
			Objects.requireNonNull(observation, "observation");
		if (!exactSelectionAligned
			|| maximumDetails < 0 || maximumDetails > MAXIMUM_DETAILS
			|| checkedInventory.getProposalGeneration()
				!= checkedObservation.getGeneration()
			|| checkedInventory.getSourceCount()
				!= checkedObservation.getSelectedSourceCount()
			|| checkedObservation.getCensusObservedAtTick()
				< checkedInventory.getObservedAtTick()) {
			throw new IllegalArgumentException(
				"NPC owner-event evidence is not aligned");
		}

		List<EventAssessment> results = new ArrayList<EventAssessment>();
		for (EventRecord event : checkedInventory.getEvents()) {
			if (!event.isCandidateRelated()
				|| event.getAttributionKind()
					!= AttributionKind.OWNER_POSITION_HINT) {
				continue;
			}
			if (results.size() >= maximumDetails) {
				throw new IllegalArgumentException(
					"NPC owner-event details exceed their budget");
			}
			results.add(assessEvent(
				event, checkedObservation.getRelevantActiveInstances(),
				checkedObservation.getGeneration(),
				ownerPreservationProved));
		}
		return new LayeredPackedRegionNpcOwnerEventContinuityAssessment(
			checkedInventory, checkedObservation, ownerPreservationProved,
			results);
	}

	private static EventAssessment assessEvent(
		final EventRecord event,
		final List<InstanceEvidence> activeInstances,
		final long censusGeneration,
		final boolean ownerPreservationProved) {
		if (event.getOwnerKind() != OwnerKind.NPC) {
			return EventAssessment.refused(
				event, Outcome.NON_NPC_OWNER, null, null, 0);
		}
		NpcOwnerIdentity owner = event.getNpcOwnerIdentity();
		if (owner == null) {
			return EventAssessment.refused(
				event, Outcome.NPC_OWNER_IDENTITY_UNAVAILABLE,
				null, null, 0);
		}
		if (owner.getGeneration() != censusGeneration) {
			return EventAssessment.refused(
				event, Outcome.OWNER_GENERATION_MISMATCH,
				null, null, 0);
		}

		List<InstanceEvidence> identityMatches =
			new ArrayList<InstanceEvidence>();
		for (InstanceEvidence active : activeInstances) {
			if (matches(owner, active)) {
				identityMatches.add(active);
			}
		}
		if (identityMatches.isEmpty()) {
			return EventAssessment.refused(
				event, Outcome.ACTIVE_OWNER_NOT_FOUND, null, null, 0);
		}
		if (identityMatches.size() != 1) {
			return EventAssessment.refused(
				event, Outcome.ACTIVE_OWNER_AMBIGUOUS, null, null,
				identityMatches.size());
		}
		InstanceEvidence active = identityMatches.get(0);
		if (active.getIdentityStatus() != IdentityStatus.RECOGNIZED) {
			return EventAssessment.refused(
				event, Outcome.ACTIVE_OWNER_IDENTITY_UNRECOGNIZED,
				active.getIdentityStatus(), active.getClassification(), 1);
		}
		if (owner.getRuntimeNpcId() != active.getRuntimeNpcId()) {
			return EventAssessment.refused(
				event, Outcome.ACTIVE_OWNER_RUNTIME_ID_MISMATCH,
				active.getIdentityStatus(), active.getClassification(), 1);
		}
		if (active.getClassification()
				!= ActiveResidencyClassification.SELECTED_OWNER_INSIDE
			&& active.getClassification()
				!= ActiveResidencyClassification.EXTERNAL_OWNER_INSIDE) {
			return EventAssessment.refused(
				event, Outcome.OWNER_POSITION_CENSUS_DRIFT,
				active.getIdentityStatus(), active.getClassification(), 1);
		}
		return EventAssessment.matched(
			event, active,
			ownerPreservationProved
				? Outcome.OWNER_CONTINUITY_ELIGIBLE
				: Outcome.OWNER_PRESERVATION_UNPROVED);
	}

	private static boolean matches(
		final NpcOwnerIdentity owner,
		final InstanceEvidence active) {
		return active.hasAuthoredIdentity()
			&& owner.getGeneration() == active.getIdentityGeneration()
			&& owner.getPackedRegionX()
				== active.getIdentityPackedRegionX()
			&& owner.getPackedRegionY()
				== active.getIdentityPackedRegionY()
			&& owner.getSourceOrdinal()
				== active.getIdentitySourceOrdinal();
	}

	public long getGeneration() { return generation; }
	public long getEventObservedAtTick() { return eventObservedAtTick; }
	public long getCensusObservedAtTick() { return censusObservedAtTick; }
	public int getSelectedSourceCount() { return selectedSourceCount; }
	public int getProposalRelatedEventCount() {
		return proposalRelatedEventCount;
	}
	public int getRelatedOwnerPositionHintEventCount() {
		return relatedOwnerPositionHintEventCount;
	}
	public int getNpcOwnerPositionHintEventCount() {
		return npcOwnerPositionHintEventCount;
	}
	public int getCapturedNpcOwnerIdentityCount() {
		return capturedNpcOwnerIdentityCount;
	}
	public int getUniquelyMatchedActiveOwnerCount() {
		return uniquelyMatchedActiveOwnerCount;
	}
	public int getContinuityEligibleEventCount() {
		return continuityEligibleEventCount;
	}
	public int getPreservationUnprovedEventCount() {
		return preservationUnprovedEventCount;
	}
	public int getHardBlockerEventCount() { return hardBlockerEventCount; }
	public boolean isExactSelectionAligned() {
		return exactSelectionAligned;
	}
	public boolean isOwnerPreservationProved() {
		return ownerPreservationProved;
	}
	public Long getFirstUnmetRegistrationSequence() {
		return firstUnmetRegistrationSequence;
	}
	public Outcome getFirstUnmetOutcome() { return firstUnmetOutcome; }
	public List<EventAssessment> getEvents() { return events; }
	public boolean isAllRelatedOwnerContinuityReadyAtObservation() {
		return !events.isEmpty()
			&& continuityEligibleEventCount == events.size()
			&& preservationUnprovedEventCount == 0
			&& hardBlockerEventCount == 0;
	}
	public boolean isPointInTimeOnly() { return true; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isPreservationPerformed() { return false; }
	public boolean isEventReschedule() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum Outcome {
		OWNER_CONTINUITY_ELIGIBLE(false),
		OWNER_PRESERVATION_UNPROVED(false),
		NON_NPC_OWNER(true),
		NPC_OWNER_IDENTITY_UNAVAILABLE(true),
		OWNER_GENERATION_MISMATCH(true),
		ACTIVE_OWNER_NOT_FOUND(true),
		ACTIVE_OWNER_AMBIGUOUS(true),
		ACTIVE_OWNER_IDENTITY_UNRECOGNIZED(true),
		ACTIVE_OWNER_RUNTIME_ID_MISMATCH(true),
		OWNER_POSITION_CENSUS_DRIFT(true);

		private final boolean hardBlocker;

		Outcome(final boolean hardBlocker) {
			this.hardBlocker = hardBlocker;
		}

		public boolean isHardBlocker() { return hardBlocker; }
	}

	/** One stable inventory-order callback correlation result. */
	public static final class EventAssessment {
		private final int snapshotOrdinal;
		private final long registrationSequence;
		private final OwnerKind ownerKind;
		private final boolean npcOwnerIdentityCaptured;
		private final Outcome outcome;
		private final int activeIdentityMatchCount;
		private final IdentityStatus matchedIdentityStatus;
		private final ActiveResidencyClassification matchedClassification;

		private EventAssessment(
			final EventRecord event,
			final Outcome outcome,
			final int activeIdentityMatchCount,
			final IdentityStatus matchedIdentityStatus,
			final ActiveResidencyClassification matchedClassification) {
			this.snapshotOrdinal = event.getSnapshotOrdinal();
			this.registrationSequence = event.getRegistrationSequence();
			this.ownerKind = event.getOwnerKind();
			this.npcOwnerIdentityCaptured =
				event.getNpcOwnerIdentity() != null;
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.activeIdentityMatchCount = activeIdentityMatchCount;
			this.matchedIdentityStatus = matchedIdentityStatus;
			this.matchedClassification = matchedClassification;
		}

		private static EventAssessment refused(
			final EventRecord event,
			final Outcome outcome,
			final IdentityStatus matchedIdentityStatus,
			final ActiveResidencyClassification matchedClassification,
			final int activeIdentityMatchCount) {
			if (!outcome.isHardBlocker()) {
				throw new IllegalArgumentException(
					"Refused owner continuity requires a hard blocker");
			}
			return new EventAssessment(
				event, outcome, activeIdentityMatchCount,
				matchedIdentityStatus, matchedClassification);
		}

		private static EventAssessment matched(
			final EventRecord event,
			final InstanceEvidence active,
			final Outcome outcome) {
			if (outcome != Outcome.OWNER_CONTINUITY_ELIGIBLE
				&& outcome != Outcome.OWNER_PRESERVATION_UNPROVED) {
				throw new IllegalArgumentException(
					"Matched owner continuity outcome is invalid");
			}
			return new EventAssessment(
				event, outcome, 1, active.getIdentityStatus(),
				active.getClassification());
		}

		public int getSnapshotOrdinal() { return snapshotOrdinal; }
		public long getRegistrationSequence() {
			return registrationSequence;
		}
		public OwnerKind getOwnerKind() { return ownerKind; }
		public boolean isNpcOwnerIdentityCaptured() {
			return npcOwnerIdentityCaptured;
		}
		public Outcome getOutcome() { return outcome; }
		public int getActiveIdentityMatchCount() {
			return activeIdentityMatchCount;
		}
		public IdentityStatus getMatchedIdentityStatus() {
			return matchedIdentityStatus;
		}
		public ActiveResidencyClassification getMatchedClassification() {
			return matchedClassification;
		}
		public boolean isUniqueActiveOwnerMatch() {
			return activeIdentityMatchCount == 1
				&& matchedIdentityStatus == IdentityStatus.RECOGNIZED;
		}
	}
}
