package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.EventRecord;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.NpcOwnerIdentity;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerEventContinuityAssessment.EventAssessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionNpcOwnerEventContinuityAssessment.Outcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deduplicates exact NPC-owned callback correlations into bounded preservation
 * requirements.
 *
 * <p>One authored NPC may own more than one scheduled callback. A future
 * preservation boundary must therefore preserve the same runtime NPC instance,
 * its World registration, and every correlated event-owner reference once per
 * exact authored identity rather than manufacturing one owner per callback.
 * Temporary Region absence also requires that the preserved owner cannot be
 * updated against absent terrain or membership.</p>
 *
 * <p>This class is a dormant detached plan. It does not retain an NPC, event,
 * Region, scheduler, registry, callback, permit, lease, transaction, or commit
 * token. In particular, an event previously classified as continuity-eligible
 * still does not make this value a runtime preservation proof.</p>
 */
public final class LayeredPackedRegionNpcOwnerPreservationRequirements {
	public static final int MAXIMUM_OWNER_REQUIREMENTS =
		LayeredPackedRegionEventOwnershipInventory.MAXIMUM_EVENTS;
	public static final int MAXIMUM_EVENT_LINKS =
		LayeredPackedRegionEventOwnershipInventory.MAXIMUM_EVENTS;

	private final long generation;
	private final long eventObservedAtTick;
	private final long censusObservedAtTick;
	private final int selectedSourceCount;
	private final int proposalRelatedEventCount;
	private final int relatedOwnerPositionHintEventCount;
	private final int npcOwnerEventCount;
	private final int separateNonNpcOwnerEventCount;
	private final int preservationRequiredEventCount;
	private final int previouslyEligibleEventCount;
	private final int npcHardBlockerEventCount;
	private final int eventLinkCount;
	private final List<OwnerRequirement> owners;

	private LayeredPackedRegionNpcOwnerPreservationRequirements(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final LayeredPackedRegionNpcOwnerEventContinuityAssessment continuity,
		final int npcOwnerEventCount,
		final int separateNonNpcOwnerEventCount,
		final int preservationRequiredEventCount,
		final int previouslyEligibleEventCount,
		final int npcHardBlockerEventCount,
		final int eventLinkCount,
		final List<OwnerRequirement> owners) {
		this.generation = inventory.getProposalGeneration();
		this.eventObservedAtTick = inventory.getObservedAtTick();
		this.censusObservedAtTick = continuity.getCensusObservedAtTick();
		this.selectedSourceCount = inventory.getSourceCount();
		this.proposalRelatedEventCount =
			inventory.getCandidateRelatedEventCount();
		this.relatedOwnerPositionHintEventCount =
			continuity.getRelatedOwnerPositionHintEventCount();
		this.npcOwnerEventCount = npcOwnerEventCount;
		this.separateNonNpcOwnerEventCount =
			separateNonNpcOwnerEventCount;
		this.preservationRequiredEventCount =
			preservationRequiredEventCount;
		this.previouslyEligibleEventCount = previouslyEligibleEventCount;
		this.npcHardBlockerEventCount = npcHardBlockerEventCount;
		this.eventLinkCount = eventLinkCount;
		this.owners = Collections.unmodifiableList(
			new ArrayList<OwnerRequirement>(owners));

		int ownerLinks = 0;
		int ownerPreservationRequired = 0;
		int ownerPreviouslyEligible = 0;
		for (OwnerRequirement owner : owners) {
			ownerLinks = Math.addExact(
				ownerLinks, owner.getEventRegistrationSequences().size());
			ownerPreservationRequired = Math.addExact(
				ownerPreservationRequired,
				owner.getPreservationRequiredEventCount());
			ownerPreviouslyEligible = Math.addExact(
				ownerPreviouslyEligible,
				owner.getPreviouslyEligibleEventCount());
		}
		if (relatedOwnerPositionHintEventCount
				!= npcOwnerEventCount + separateNonNpcOwnerEventCount
			|| npcOwnerEventCount
				!= preservationRequiredEventCount
					+ previouslyEligibleEventCount + npcHardBlockerEventCount
			|| eventLinkCount
				!= preservationRequiredEventCount
					+ previouslyEligibleEventCount
			|| ownerLinks != eventLinkCount
			|| ownerPreservationRequired != preservationRequiredEventCount
			|| ownerPreviouslyEligible != previouslyEligibleEventCount) {
			throw new IllegalArgumentException(
				"NPC owner preservation requirement arithmetic is inconsistent");
		}
	}

	/**
	 * Builds a stable first-event-order owner plan from one exact inventory and
	 * its already validated continuity assessment.
	 */
	public static LayeredPackedRegionNpcOwnerPreservationRequirements derive(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final LayeredPackedRegionNpcOwnerEventContinuityAssessment continuity,
		final int maximumOwnerRequirements,
		final int maximumEventLinks) {
		LayeredPackedRegionEventOwnershipInventory checkedInventory =
			Objects.requireNonNull(inventory, "inventory");
		LayeredPackedRegionNpcOwnerEventContinuityAssessment checkedContinuity =
			Objects.requireNonNull(continuity, "continuity");
		if (maximumOwnerRequirements < 0
			|| maximumOwnerRequirements > MAXIMUM_OWNER_REQUIREMENTS
			|| maximumEventLinks < 0
			|| maximumEventLinks > MAXIMUM_EVENT_LINKS
			|| checkedInventory.getProposalGeneration()
				!= checkedContinuity.getGeneration()
			|| checkedInventory.getObservedAtTick()
				!= checkedContinuity.getEventObservedAtTick()
			|| checkedInventory.getSourceCount()
				!= checkedContinuity.getSelectedSourceCount()
			|| checkedInventory.getCandidateRelatedEventCount()
				!= checkedContinuity.getProposalRelatedEventCount()
			|| !checkedContinuity.isExactSelectionAligned()) {
			throw new IllegalArgumentException(
				"NPC owner preservation evidence is not aligned");
		}

		Map<OwnerKey, OwnerRequirementBuilder> ownerBuilders =
			new LinkedHashMap<OwnerKey, OwnerRequirementBuilder>();
		int npcEvents = 0;
		int nonNpcEvents = 0;
		int preservationRequired = 0;
		int previouslyEligible = 0;
		int npcHardBlockers = 0;
		int eventLinks = 0;
		for (EventAssessment assessment : checkedContinuity.getEvents()) {
			EventRecord event = exactEvent(checkedInventory, assessment);
			if (event.getAttributionKind()
					!= AttributionKind.OWNER_POSITION_HINT
				|| !event.isCandidateRelated()) {
				throw new IllegalArgumentException(
					"Continuity event is not a related owner-position hint");
			}
			if (event.getOwnerKind() != OwnerKind.NPC) {
				if (assessment.getOutcome() != Outcome.NON_NPC_OWNER) {
					throw new IllegalArgumentException(
						"Non-NPC continuity outcome is inconsistent");
				}
				nonNpcEvents++;
				continue;
			}

			npcEvents++;
			Outcome outcome = assessment.getOutcome();
			if (outcome != Outcome.OWNER_PRESERVATION_UNPROVED
				&& outcome != Outcome.OWNER_CONTINUITY_ELIGIBLE) {
				npcHardBlockers++;
				continue;
			}
			NpcOwnerIdentity identity = event.getNpcOwnerIdentity();
			if (identity == null || !assessment.isUniqueActiveOwnerMatch()) {
				throw new IllegalArgumentException(
					"Matched NPC owner lacks exact identity evidence");
			}
			eventLinks = Math.addExact(eventLinks, 1);
			if (eventLinks > maximumEventLinks) {
				throw new IllegalArgumentException(
					"NPC owner event links exceed their budget");
			}

			OwnerKey key = new OwnerKey(identity);
			OwnerRequirementBuilder owner = ownerBuilders.get(key);
			if (owner == null) {
				if (ownerBuilders.size() >= maximumOwnerRequirements) {
					throw new IllegalArgumentException(
						"NPC owner requirements exceed their budget");
				}
				owner = new OwnerRequirementBuilder(identity);
				ownerBuilders.put(key, owner);
			} else if (owner.getRuntimeNpcId()
					!= identity.getRuntimeNpcId()) {
				throw new IllegalArgumentException(
					"One authored NPC identity has conflicting definitions");
			}
			owner.add(event.getRegistrationSequence(), outcome);
			preservationRequired +=
				outcome == Outcome.OWNER_PRESERVATION_UNPROVED ? 1 : 0;
			previouslyEligible +=
				outcome == Outcome.OWNER_CONTINUITY_ELIGIBLE ? 1 : 0;
		}

		if (preservationRequired
				!= checkedContinuity.getPreservationUnprovedEventCount()
			|| previouslyEligible
				!= checkedContinuity.getContinuityEligibleEventCount()
			|| nonNpcEvents + npcHardBlockers
				!= checkedContinuity.getHardBlockerEventCount()) {
			throw new IllegalArgumentException(
				"Continuity outcome totals do not match the preservation plan");
		}

		List<OwnerRequirement> owners =
			new ArrayList<OwnerRequirement>(ownerBuilders.size());
		for (OwnerRequirementBuilder owner : ownerBuilders.values()) {
			owners.add(owner.build());
		}
		return new LayeredPackedRegionNpcOwnerPreservationRequirements(
			checkedInventory, checkedContinuity, npcEvents, nonNpcEvents,
			preservationRequired, previouslyEligible, npcHardBlockers,
			eventLinks, owners);
	}

	private static EventRecord exactEvent(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final EventAssessment assessment) {
		int ordinal = assessment.getSnapshotOrdinal();
		if (ordinal < 0 || ordinal >= inventory.getEvents().size()) {
			throw new IllegalArgumentException(
				"Continuity event ordinal is outside the inventory");
		}
		EventRecord event = inventory.getEvents().get(ordinal);
		if (event.getSnapshotOrdinal() != ordinal
			|| event.getRegistrationSequence()
				!= assessment.getRegistrationSequence()
			|| event.getOwnerKind() != assessment.getOwnerKind()) {
			throw new IllegalArgumentException(
				"Continuity event identity does not match the inventory");
		}
		return event;
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
	public int getNpcOwnerEventCount() { return npcOwnerEventCount; }
	public int getSeparateNonNpcOwnerEventCount() {
		return separateNonNpcOwnerEventCount;
	}
	public int getPreservationRequiredEventCount() {
		return preservationRequiredEventCount;
	}
	public int getPreviouslyEligibleEventCount() {
		return previouslyEligibleEventCount;
	}
	public int getNpcHardBlockerEventCount() {
		return npcHardBlockerEventCount;
	}
	public int getUniqueNpcOwnerCount() { return owners.size(); }
	public int getEventLinkCount() { return eventLinkCount; }
	public List<OwnerRequirement> getOwners() { return owners; }
	public boolean isNpcRequirementSetComplete() {
		return npcHardBlockerEventCount == 0
			&& npcOwnerEventCount
				== preservationRequiredEventCount
					+ previouslyEligibleEventCount;
	}
	public boolean hasSeparateNonNpcBlockers() {
		return separateNonNpcOwnerEventCount > 0;
	}

	public boolean isSameRuntimeInstanceRequired() { return true; }
	public boolean isWorldRegistrationContinuityRequired() { return true; }
	public boolean isEventOwnerReferenceContinuityRequired() { return true; }
	public boolean isRegionAbsenceQuiescenceRequired() { return true; }
	public boolean isPreservationFactEstablished() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isPreservationPerformed() { return false; }
	public boolean isEventReschedule() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** One exact authored NPC owner and every correlated callback identity. */
	public static final class OwnerRequirement {
		private final long generation;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int sourceOrdinal;
		private final int runtimeNpcId;
		private final int preservationRequiredEventCount;
		private final int previouslyEligibleEventCount;
		private final List<Long> eventRegistrationSequences;

		private OwnerRequirement(
			final NpcOwnerIdentity identity,
			final int preservationRequiredEventCount,
			final int previouslyEligibleEventCount,
			final List<Long> eventRegistrationSequences) {
			this.generation = identity.getGeneration();
			this.packedRegionX = identity.getPackedRegionX();
			this.packedRegionY = identity.getPackedRegionY();
			this.sourceOrdinal = identity.getSourceOrdinal();
			this.runtimeNpcId = identity.getRuntimeNpcId();
			this.preservationRequiredEventCount =
				preservationRequiredEventCount;
			this.previouslyEligibleEventCount = previouslyEligibleEventCount;
			this.eventRegistrationSequences = Collections.unmodifiableList(
				new ArrayList<Long>(eventRegistrationSequences));
		}

		public long getGeneration() { return generation; }
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getSourceOrdinal() { return sourceOrdinal; }
		public int getRuntimeNpcId() { return runtimeNpcId; }
		public int getPreservationRequiredEventCount() {
			return preservationRequiredEventCount;
		}
		public int getPreviouslyEligibleEventCount() {
			return previouslyEligibleEventCount;
		}
		public List<Long> getEventRegistrationSequences() {
			return eventRegistrationSequences;
		}
		public long getFirstRegistrationSequence() {
			return eventRegistrationSequences.get(0).longValue();
		}
		public boolean isSameRuntimeInstanceRequired() { return true; }
		public boolean isWorldRegistrationContinuityRequired() { return true; }
		public boolean isEventOwnerReferenceContinuityRequired() {
			return true;
		}
	}

	private static final class OwnerRequirementBuilder {
		private final NpcOwnerIdentity identity;
		private final List<Long> eventRegistrationSequences =
			new ArrayList<Long>();
		private int preservationRequiredEventCount;
		private int previouslyEligibleEventCount;

		private OwnerRequirementBuilder(final NpcOwnerIdentity identity) {
			this.identity = identity;
		}

		private int getRuntimeNpcId() {
			return identity.getRuntimeNpcId();
		}

		private void add(
			final long registrationSequence,
			final Outcome outcome) {
			if (!eventRegistrationSequences.isEmpty()
				&& eventRegistrationSequences
					.get(eventRegistrationSequences.size() - 1).longValue()
						>= registrationSequence) {
				throw new IllegalArgumentException(
					"Owner event registrations are not in stable order");
			}
			eventRegistrationSequences.add(
				Long.valueOf(registrationSequence));
			preservationRequiredEventCount +=
				outcome == Outcome.OWNER_PRESERVATION_UNPROVED ? 1 : 0;
			previouslyEligibleEventCount +=
				outcome == Outcome.OWNER_CONTINUITY_ELIGIBLE ? 1 : 0;
		}

		private OwnerRequirement build() {
			return new OwnerRequirement(
				identity, preservationRequiredEventCount,
				previouslyEligibleEventCount, eventRegistrationSequences);
		}
	}

	private static final class OwnerKey {
		private final long generation;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int sourceOrdinal;

		private OwnerKey(final NpcOwnerIdentity identity) {
			this.generation = identity.getGeneration();
			this.packedRegionX = identity.getPackedRegionX();
			this.packedRegionY = identity.getPackedRegionY();
			this.sourceOrdinal = identity.getSourceOrdinal();
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) { return true; }
			if (!(other instanceof OwnerKey)) { return false; }
			OwnerKey key = (OwnerKey) other;
			return generation == key.generation
				&& packedRegionX == key.packedRegionX
				&& packedRegionY == key.packedRegionY
				&& sourceOrdinal == key.sourceOrdinal;
		}

		@Override
		public int hashCode() {
			int result = (int) (generation ^ (generation >>> 32));
			result = 31 * result + packedRegionX;
			result = 31 * result + packedRegionY;
			result = 31 * result + sourceOrdinal;
			return result;
		}
	}
}
