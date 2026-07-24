package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.AuthoredPlacementRestorationState;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.EventRecord;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.EventRestorationState;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.NpcOwnerIdentity;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.RestorationKind;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.SceneryRestorationState;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionNpcOwnerPreservationRequirements.OwnerRequirement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable correlation of one authored-object detachment definition with one
 * exact detached scheduler inventory.
 *
 * <p>The correlation distinguishes callbacks that can join an exact NPC-owner
 * preservation fence, authored scenery callbacks whose detached target
 * identity matches the detachment plan, candidate-related callbacks that
 * remain blockers, and callbacks whose current detached evidence does not
 * associate them with the selected sources. Unattributed callbacks remain
 * global blockers because absence of a spatial reference is not proof that a
 * callback cannot affect a selected source.</p>
 *
 * <p>This value consumes and retains detached primitives only. It does not
 * retain a scheduler, event, callback, entity, Region, object, collision
 * registration, lifecycle boundary, or runtime handle. A complete detached
 * classification is not permission to detach runtime state: exact boundary
 * revalidation, active-family preservation, rollback, arrival, and visibility
 * gates remain separate prerequisites.</p>
 */
public final class
	LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation {
	public static final int MAXIMUM_RETAINED_EVENTS =
		LayeredPackedRegionEventOwnershipInventory.MAXIMUM_EVENTS;

	private final long generation;
	private final long eventObservedAtTick;
	private final long detachmentRuntimeObservedAtTick;
	private final String schedulerInstanceIdentity;
	private final String detachmentPlanFingerprintSha256;
	private final List<SourceCorrelation> sources;
	private final List<EventCorrelation> retainedEvents;
	private final int eventCount;
	private final int npcOwnerFenceEventCount;
	private final int relatedNpcOwnerFenceEventCount;
	private final int supportingNpcOwnerFenceEventCount;
	private final int exactAuthoredRestorationEventCount;
	private final int candidateNpcOwnerUncorrelatedEventCount;
	private final int candidateNonNpcOwnerEventCount;
	private final int candidateExactRestorationIncompleteEventCount;
	private final int unattributedEventCount;
	private final int outsideSelectionOwnerHintEventCount;
	private final int outsideSelectionExactSpatialEventCount;
	private final int nonSpatialGlobalEventCount;
	private final int blockerEventCount;
	private final String fingerprintSha256;

	private
		LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation(
			final LayeredPackedRegionAuthoredObjectDetachmentPlan detachment,
			final LayeredPackedRegionEventOwnershipInventory inventory,
			final List<SourceCorrelation> sources,
			final List<EventCorrelation> retainedEvents,
			final int npcOwnerFenceEventCount,
			final int relatedNpcOwnerFenceEventCount,
			final int supportingNpcOwnerFenceEventCount,
			final int exactAuthoredRestorationEventCount,
			final int candidateNpcOwnerUncorrelatedEventCount,
			final int candidateNonNpcOwnerEventCount,
			final int candidateExactRestorationIncompleteEventCount,
			final int unattributedEventCount,
			final int outsideSelectionOwnerHintEventCount,
			final int outsideSelectionExactSpatialEventCount,
			final int nonSpatialGlobalEventCount) {
		this.generation = detachment.getGeneration();
		this.eventObservedAtTick = inventory.getObservedAtTick();
		this.detachmentRuntimeObservedAtTick =
			detachment.getRuntimeObservedAtTick();
		this.schedulerInstanceIdentity =
			inventory.getSchedulerInstanceIdentity();
		this.detachmentPlanFingerprintSha256 =
			detachment.getFingerprintSha256();
		this.sources = Collections.unmodifiableList(sources);
		this.retainedEvents = Collections.unmodifiableList(retainedEvents);
		this.eventCount = inventory.getEventCount();
		this.npcOwnerFenceEventCount = npcOwnerFenceEventCount;
		this.relatedNpcOwnerFenceEventCount =
			relatedNpcOwnerFenceEventCount;
		this.supportingNpcOwnerFenceEventCount =
			supportingNpcOwnerFenceEventCount;
		this.exactAuthoredRestorationEventCount =
			exactAuthoredRestorationEventCount;
		this.candidateNpcOwnerUncorrelatedEventCount =
			candidateNpcOwnerUncorrelatedEventCount;
		this.candidateNonNpcOwnerEventCount =
			candidateNonNpcOwnerEventCount;
		this.candidateExactRestorationIncompleteEventCount =
			candidateExactRestorationIncompleteEventCount;
		this.unattributedEventCount = unattributedEventCount;
		this.outsideSelectionOwnerHintEventCount =
			outsideSelectionOwnerHintEventCount;
		this.outsideSelectionExactSpatialEventCount =
			outsideSelectionExactSpatialEventCount;
		this.nonSpatialGlobalEventCount = nonSpatialGlobalEventCount;
		this.blockerEventCount = Math.addExact(
			Math.addExact(
				candidateNpcOwnerUncorrelatedEventCount,
				candidateNonNpcOwnerEventCount),
			Math.addExact(
				candidateExactRestorationIncompleteEventCount,
				unattributedEventCount));
		this.fingerprintSha256 = fingerprint(
			detachmentPlanFingerprintSha256, schedulerInstanceIdentity,
			eventObservedAtTick, eventCount, sources, retainedEvents,
			outsideSelectionOwnerHintEventCount,
			outsideSelectionExactSpatialEventCount,
			nonSpatialGlobalEventCount);
	}

	/**
	 * Correlates a stable detached event snapshot with the exact source and
	 * object identities in one inert detachment plan.
	 */
	public static
		LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation correlate(
			final LayeredPackedRegionAuthoredObjectDetachmentPlan
				detachmentPlan,
			final LayeredPackedRegionEventOwnershipInventory eventInventory,
			final LayeredPackedRegionNpcOwnerPreservationRequirements
				npcRequirements,
			final int maximumRetainedEvents) {
		LayeredPackedRegionAuthoredObjectDetachmentPlan detachment =
			Objects.requireNonNull(detachmentPlan, "detachmentPlan");
		LayeredPackedRegionEventOwnershipInventory inventory =
			Objects.requireNonNull(eventInventory, "eventInventory");
		LayeredPackedRegionNpcOwnerPreservationRequirements requirements =
			Objects.requireNonNull(npcRequirements, "npcRequirements");
		if (maximumRetainedEvents < 0
			|| maximumRetainedEvents > MAXIMUM_RETAINED_EVENTS
			|| detachment.getGeneration() != inventory.getProposalGeneration()
			|| detachment.getGeneration() != requirements.getGeneration()
			|| inventory.getObservedAtTick()
				!= requirements.getEventObservedAtTick()
			|| !inventory.getSchedulerInstanceIdentity().equals(
				requirements.getSchedulerInstanceIdentity())
			|| detachment.getSourceCount() != inventory.getSourceCount()
			|| detachment.getSourceCount()
				!= requirements.getSelectedSourceCount()
			|| inventory.getCandidateRelatedEventCount()
				!= requirements.getProposalRelatedEventCount()) {
			throw new IllegalArgumentException(
				"Detachment scheduler evidence is not one aligned snapshot");
		}

		Map<Long, Integer> selectedSourceOrdinals =
			new LinkedHashMap<Long, Integer>();
		Map<AuthoredObjectKey,
			LayeredPackedRegionAuthoredObjectDetachmentPlan.ObjectDetachment>
				authoredObjects =
					new LinkedHashMap<AuthoredObjectKey,
						LayeredPackedRegionAuthoredObjectDetachmentPlan
							.ObjectDetachment>();
		MutableSource[] sourceTotals =
			new MutableSource[detachment.getSourceCount()];
		for (int sourceOrdinal = 0;
			sourceOrdinal < detachment.getSourceCount(); sourceOrdinal++) {
			LayeredPackedRegionAuthoredObjectDetachmentPlan.SourcePlan source =
				detachment.getSources().get(sourceOrdinal);
			LayeredPackedRegionEventOwnershipInventory.SourceRecord
				inventorySource = inventory.getSources().get(sourceOrdinal);
			LayeredPackedRegionNpcOwnerPreservationRequirements.SelectedSource
				requirementSource =
					requirements.getSelectedSources().get(sourceOrdinal);
			if (source.getSelectedSourceOrdinal() != sourceOrdinal
				|| source.getPackedRegionX()
					!= inventorySource.getPackedRegionX()
				|| source.getPackedRegionY()
					!= inventorySource.getPackedRegionY()
				|| source.getPackedRegionX()
					!= requirementSource.getPackedRegionX()
				|| source.getPackedRegionY()
					!= requirementSource.getPackedRegionY()) {
				throw new IllegalArgumentException(
					"Detachment scheduler source order is not aligned");
			}
			long sourceKey = sourceKey(
				source.getPackedRegionX(), source.getPackedRegionY());
			if (selectedSourceOrdinals.put(
					Long.valueOf(sourceKey),
					Integer.valueOf(sourceOrdinal)) != null) {
				throw new IllegalArgumentException(
					"Detachment scheduler sources are duplicated");
			}
			sourceTotals[sourceOrdinal] = new MutableSource(
				sourceOrdinal, source.getPackedRegionX(),
				source.getPackedRegionY());
			for (LayeredPackedRegionAuthoredObjectDetachmentPlan
					.ObjectDetachment object : source.getObjects()) {
				AuthoredObjectKey objectKey = new AuthoredObjectKey(
					object.getAuthoredGeneration(),
					object.getSourcePackedRegionX(),
					object.getSourcePackedRegionY(),
					object.getAuthoredSourceOrdinal());
				if (authoredObjects.put(objectKey, object) != null) {
					throw new IllegalArgumentException(
						"Detachment authored identities are duplicated");
				}
			}
		}

		Map<Long, OwnerRequirement> ownerByRegistration =
			indexOwnerRequirements(requirements);
		List<EventCorrelation> retained =
			new ArrayList<EventCorrelation>();
		int npcFence = 0;
		int relatedNpcFence = 0;
		int supportingNpcFence = 0;
		int exactRestoration = 0;
		int candidateNpcUncorrelated = 0;
		int candidateNonNpc = 0;
		int candidateExactIncomplete = 0;
		int unattributed = 0;
		int outsideHint = 0;
		int outsideExact = 0;
		int unlinkedNonSpatialGlobal = 0;

		for (EventRecord event : inventory.getEvents()) {
			OwnerRequirement owner = ownerByRegistration.get(
				Long.valueOf(event.getRegistrationSequence()));
			if (owner != null) {
				validateOwnerFenceEvent(event, owner);
				EventOutcome outcome = EventOutcome.NPC_OWNER_FENCE;
				retain(
					retained, event, outcome, -1, -1,
					maximumRetainedEvents);
				npcFence++;
				if (event.isCandidateRelated()) {
					relatedNpcFence++;
					incrementSources(
						sourceTotals, event, outcome);
				} else {
					supportingNpcFence++;
				}
				continue;
			}

			AttributionKind attribution = event.getAttributionKind();
			if (attribution == AttributionKind.UNATTRIBUTED) {
				retain(
					retained, event, EventOutcome.UNATTRIBUTED_BLOCKER,
					-1, -1, maximumRetainedEvents);
				unattributed++;
			} else if (attribution
					== AttributionKind.NON_SPATIAL_GLOBAL) {
				unlinkedNonSpatialGlobal++;
			} else if (!event.isCandidateRelated()) {
				if (attribution == AttributionKind.OWNER_POSITION_HINT) {
					outsideHint++;
				} else if (attribution == AttributionKind.EXACT_SPATIAL) {
					outsideExact++;
				} else {
					throw new IllegalStateException(
						"Unhandled outside-selection event attribution");
				}
			} else if (attribution
					== AttributionKind.OWNER_POSITION_HINT) {
				EventOutcome outcome;
				if (event.getOwnerKind() == OwnerKind.NPC) {
					outcome =
						EventOutcome.CANDIDATE_NPC_OWNER_UNCORRELATED;
					candidateNpcUncorrelated++;
				} else {
					outcome = EventOutcome.CANDIDATE_NON_NPC_OWNER;
					candidateNonNpc++;
				}
				retain(
					retained, event, outcome, -1, -1,
					maximumRetainedEvents);
				incrementSources(sourceTotals, event, outcome);
			} else if (attribution == AttributionKind.EXACT_SPATIAL) {
				AuthoredMatch match = exactAuthoredMatch(
					event, detachment, selectedSourceOrdinals,
					authoredObjects);
				if (match == null) {
					retain(
						retained, event,
						EventOutcome
							.CANDIDATE_EXACT_RESTORATION_INCOMPLETE,
						-1, -1, maximumRetainedEvents);
					incrementSources(
						sourceTotals, event,
						EventOutcome
							.CANDIDATE_EXACT_RESTORATION_INCOMPLETE);
					candidateExactIncomplete++;
				} else {
					retain(
						retained, event,
						EventOutcome.EXACT_AUTHORED_RESTORATION,
						match.selectedSourceOrdinal,
						match.authoredSourceOrdinal,
						maximumRetainedEvents);
					incrementSources(
						sourceTotals, event,
						EventOutcome.EXACT_AUTHORED_RESTORATION);
					exactRestoration++;
				}
			} else {
				throw new IllegalStateException(
					"Unhandled candidate event attribution");
			}
		}

		int classified = Math.addExact(
			Math.addExact(
				Math.addExact(npcFence, exactRestoration),
				Math.addExact(
					candidateNpcUncorrelated, candidateNonNpc)),
			Math.addExact(
				Math.addExact(
					candidateExactIncomplete, unattributed),
					Math.addExact(
						Math.addExact(outsideHint, outsideExact),
						unlinkedNonSpatialGlobal)));
		if (classified != inventory.getEventCount()
			|| npcFence != requirements.getEventLinkCount()
			|| relatedNpcFence != requirements.getRelatedEventLinkCount()
			|| supportingNpcFence
				!= requirements.getSupportingEventLinkCount()
			|| candidateNpcUncorrelated
				!= requirements.getNpcHardBlockerEventCount()
			|| candidateNonNpc
				!= requirements.getSeparateNonNpcOwnerEventCount()
			|| relatedNpcFence + candidateNpcUncorrelated
				!= requirements.getNpcOwnerEventCount()
			|| unattributed != inventory.getUnattributedEventCount()
			|| unlinkedNonSpatialGlobal
				> inventory.getNonSpatialGlobalEventCount()) {
			throw new IllegalArgumentException(
				"Detachment scheduler classifications do not reconcile");
		}

		List<SourceCorrelation> sourceCorrelations =
			new ArrayList<SourceCorrelation>(sourceTotals.length);
		for (MutableSource source : sourceTotals) {
			sourceCorrelations.add(source.toImmutable());
		}
		return new
			LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation(
				detachment, inventory, sourceCorrelations, retained,
				npcFence, relatedNpcFence, supportingNpcFence,
				exactRestoration, candidateNpcUncorrelated,
				candidateNonNpc, candidateExactIncomplete, unattributed,
				outsideHint, outsideExact,
				inventory.getNonSpatialGlobalEventCount());
	}

	private static Map<Long, OwnerRequirement> indexOwnerRequirements(
		final LayeredPackedRegionNpcOwnerPreservationRequirements
			requirements) {
		Map<Long, OwnerRequirement> indexed =
			new LinkedHashMap<Long, OwnerRequirement>();
		for (OwnerRequirement owner : requirements.getOwners()) {
			if (owner.getGeneration() != requirements.getGeneration()) {
				throw new IllegalArgumentException(
					"NPC owner requirement generation is not aligned");
			}
			for (Long registration : owner.getEventRegistrationSequences()) {
				if (indexed.put(registration, owner) != null) {
					throw new IllegalArgumentException(
						"NPC owner registration is claimed more than once");
				}
			}
		}
		return indexed;
	}

	private static void validateOwnerFenceEvent(
		final EventRecord event,
		final OwnerRequirement owner) {
		NpcOwnerIdentity identity = event.getNpcOwnerIdentity();
		if (event.getOwnerKind() != OwnerKind.NPC || identity == null
			|| identity.getGeneration() != owner.getGeneration()
			|| identity.getPackedRegionX() != owner.getPackedRegionX()
			|| identity.getPackedRegionY() != owner.getPackedRegionY()
			|| identity.getSourceOrdinal() != owner.getSourceOrdinal()
			|| identity.getRuntimeNpcId() != owner.getRuntimeNpcId()) {
			throw new IllegalArgumentException(
				"NPC owner fence registration changed identity");
		}
	}

	private static AuthoredMatch exactAuthoredMatch(
		final EventRecord event,
		final LayeredPackedRegionAuthoredObjectDetachmentPlan detachment,
		final Map<Long, Integer> selectedSourceOrdinals,
		final Map<AuthoredObjectKey,
			LayeredPackedRegionAuthoredObjectDetachmentPlan.ObjectDetachment>
				authoredObjects) {
		EventRestorationState restoration = event.getRestorationState();
		if (restoration.getKind() == RestorationKind.UNAVAILABLE
			|| !restoration.isDetachedCallbackPayloadComplete()
			|| !restoration.isExecutionSemanticsCaptured()
			|| !event.isAtomicTimingCaptured()
			|| !restoration.isTargetBindingRequirementCaptured()
			|| !restoration.isTargetBindingComplete()
			|| !restoration.isArrivalOrderingCaptured()
			|| !restoration.isGenerationBindingRequirementCaptured()
			|| !restoration.isGenerationBindingComplete(
				detachment.getAuthoredGeneration())
			|| !restoration.isIdempotencyRequirementCaptured()) {
			return null;
		}
		SceneryRestorationState scenery = restoration.getScenery();
		AuthoredPlacementRestorationState authored =
			scenery == null ? null : scenery.getAuthoredPlacement();
		if (authored == null) { return null; }
		Integer selectedOrdinal = selectedSourceOrdinals.get(
			Long.valueOf(sourceKey(
				authored.getPackedRegionX(),
				authored.getPackedRegionY())));
		if (selectedOrdinal == null
			|| !event.getCandidateSourceOrdinals().contains(
				selectedOrdinal)
			|| event.getCandidateSourceOrdinals().size() != 1) {
			return null;
		}
		LayeredPackedRegionAuthoredObjectDetachmentPlan.ObjectDetachment
			object = authoredObjects.get(new AuthoredObjectKey(
				authored.getGeneration(), authored.getPackedRegionX(),
				authored.getPackedRegionY(), authored.getSourceOrdinal()));
		if (object == null
			|| !object.getConstructionKind().name().equals(
				authored.getConstructionKind().name())
			|| object.getObjectId() != scenery.getObjectId()
			|| object.getPermanentObjectId()
				!= scenery.getPermanentObjectId()
			|| object.getPackedX() != scenery.getX()
			|| object.getPackedY() != scenery.getY()
			|| object.getDirection() != scenery.getDirection()
			|| object.getObjectType() != scenery.getType()
			|| !Objects.equals(
				object.getObjectOwner(), scenery.getOwner())) {
			return null;
		}
		return new AuthoredMatch(
			selectedOrdinal.intValue(), object.getAuthoredSourceOrdinal());
	}

	private static void retain(
		final List<EventCorrelation> retained,
		final EventRecord event,
		final EventOutcome outcome,
		final int matchedSelectedSourceOrdinal,
		final int matchedAuthoredSourceOrdinal,
		final int maximumRetainedEvents) {
		if (retained.size() >= maximumRetainedEvents) {
			throw new IllegalArgumentException(
				"Detachment scheduler correlation exceeds its event budget");
		}
		retained.add(new EventCorrelation(
			event, outcome, matchedSelectedSourceOrdinal,
			matchedAuthoredSourceOrdinal));
	}

	private static void incrementSources(
		final MutableSource[] sources,
		final EventRecord event,
		final EventOutcome outcome) {
		for (Integer sourceOrdinal : event.getCandidateSourceOrdinals()) {
			int ordinal = sourceOrdinal.intValue();
			if (ordinal < 0 || ordinal >= sources.length) {
				throw new IllegalArgumentException(
					"Event refers to an unknown detachment source");
			}
			sources[ordinal].increment(outcome);
		}
	}

	private static long sourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xffffffffL);
	}

	public long getGeneration() { return generation; }
	public long getEventObservedAtTick() { return eventObservedAtTick; }
	public long getDetachmentRuntimeObservedAtTick() {
		return detachmentRuntimeObservedAtTick;
	}
	public String getSchedulerInstanceIdentity() {
		return schedulerInstanceIdentity;
	}
	public String getDetachmentPlanFingerprintSha256() {
		return detachmentPlanFingerprintSha256;
	}
	public List<SourceCorrelation> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public List<EventCorrelation> getRetainedEvents() {
		return retainedEvents;
	}
	public int getRetainedEventCount() { return retainedEvents.size(); }
	public int getEventCount() { return eventCount; }
	public int getNpcOwnerFenceEventCount() {
		return npcOwnerFenceEventCount;
	}
	public int getRelatedNpcOwnerFenceEventCount() {
		return relatedNpcOwnerFenceEventCount;
	}
	public int getSupportingNpcOwnerFenceEventCount() {
		return supportingNpcOwnerFenceEventCount;
	}
	public int getExactAuthoredRestorationEventCount() {
		return exactAuthoredRestorationEventCount;
	}
	public int getCandidateNpcOwnerUncorrelatedEventCount() {
		return candidateNpcOwnerUncorrelatedEventCount;
	}
	public int getCandidateNonNpcOwnerEventCount() {
		return candidateNonNpcOwnerEventCount;
	}
	public int getCandidateExactRestorationIncompleteEventCount() {
		return candidateExactRestorationIncompleteEventCount;
	}
	public int getUnattributedEventCount() {
		return unattributedEventCount;
	}
	public int getOutsideSelectionOwnerHintEventCount() {
		return outsideSelectionOwnerHintEventCount;
	}
	public int getOutsideSelectionExactSpatialEventCount() {
		return outsideSelectionExactSpatialEventCount;
	}
	public int getNonSpatialGlobalEventCount() {
		return nonSpatialGlobalEventCount;
	}
	public int getBlockerEventCount() { return blockerEventCount; }
	public String getFingerprintSha256() { return fingerprintSha256; }

	public boolean areAllSchedulerEventsClassified() { return true; }
	public boolean isDetachedSchedulerCorrelationComplete() {
		return blockerEventCount == 0;
	}
	public boolean isSchedulerCorrelationPerformed() { return true; }
	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isRuntimeDetachmentReady() { return false; }
	public boolean isSchedulerBoundaryEntered() { return false; }
	public boolean isSchedulerIdentityRetained() { return false; }
	public boolean isCallbackRetained() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isEventCancellation() { return false; }
	public boolean isEventReschedule() { return false; }
	public boolean isPreservationPerformed() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isRuntimeMutationAuthorized() { return false; }
	public boolean isRuntimeMutationPerformed() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum EventOutcome {
		NPC_OWNER_FENCE(false),
		EXACT_AUTHORED_RESTORATION(false),
		CANDIDATE_NPC_OWNER_UNCORRELATED(true),
		CANDIDATE_NON_NPC_OWNER(true),
		CANDIDATE_EXACT_RESTORATION_INCOMPLETE(true),
		UNATTRIBUTED_BLOCKER(true);

		private final boolean blocker;

		EventOutcome(final boolean blocker) {
			this.blocker = blocker;
		}

		public boolean isBlocker() { return blocker; }
	}

	/** Retained primitive identity for one fence, restoration, or blocker. */
	public static final class EventCorrelation {
		private final int snapshotOrdinal;
		private final long registrationSequence;
		private final OwnerKind ownerKind;
		private final EventOutcome outcome;
		private final List<Integer> candidateSourceOrdinals;
		private final int matchedSelectedSourceOrdinal;
		private final int matchedAuthoredSourceOrdinal;

		private EventCorrelation(
			final EventRecord event,
			final EventOutcome outcome,
			final int matchedSelectedSourceOrdinal,
			final int matchedAuthoredSourceOrdinal) {
			this.snapshotOrdinal = event.getSnapshotOrdinal();
			this.registrationSequence = event.getRegistrationSequence();
			this.ownerKind = event.getOwnerKind();
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.candidateSourceOrdinals = Collections.unmodifiableList(
				new ArrayList<Integer>(
					event.getCandidateSourceOrdinals()));
			this.matchedSelectedSourceOrdinal =
				matchedSelectedSourceOrdinal;
			this.matchedAuthoredSourceOrdinal =
				matchedAuthoredSourceOrdinal;
			boolean exact = outcome
				== EventOutcome.EXACT_AUTHORED_RESTORATION;
			if (exact != (matchedSelectedSourceOrdinal >= 0
					&& matchedAuthoredSourceOrdinal > 0)) {
				throw new IllegalArgumentException(
					"Authored event match identity is inconsistent");
			}
		}

		public int getSnapshotOrdinal() { return snapshotOrdinal; }
		public long getRegistrationSequence() {
			return registrationSequence;
		}
		public OwnerKind getOwnerKind() { return ownerKind; }
		public EventOutcome getOutcome() { return outcome; }
		public List<Integer> getCandidateSourceOrdinals() {
			return candidateSourceOrdinals;
		}
		public int getMatchedSelectedSourceOrdinal() {
			return matchedSelectedSourceOrdinal;
		}
		public int getMatchedAuthoredSourceOrdinal() {
			return matchedAuthoredSourceOrdinal;
		}
		public boolean isBlocker() { return outcome.isBlocker(); }
	}

	/** Candidate-ordered event totals for one selected packed source. */
	public static final class SourceCorrelation {
		private final int selectedSourceOrdinal;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int npcOwnerFenceEventCount;
		private final int exactAuthoredRestorationEventCount;
		private final int npcOwnerUncorrelatedEventCount;
		private final int nonNpcOwnerEventCount;
		private final int exactRestorationIncompleteEventCount;

		private SourceCorrelation(final MutableSource source) {
			this.selectedSourceOrdinal = source.selectedSourceOrdinal;
			this.packedRegionX = source.packedRegionX;
			this.packedRegionY = source.packedRegionY;
			this.npcOwnerFenceEventCount =
				source.npcOwnerFenceEventCount;
			this.exactAuthoredRestorationEventCount =
				source.exactAuthoredRestorationEventCount;
			this.npcOwnerUncorrelatedEventCount =
				source.npcOwnerUncorrelatedEventCount;
			this.nonNpcOwnerEventCount = source.nonNpcOwnerEventCount;
			this.exactRestorationIncompleteEventCount =
				source.exactRestorationIncompleteEventCount;
		}

		public int getSelectedSourceOrdinal() {
			return selectedSourceOrdinal;
		}
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getNpcOwnerFenceEventCount() {
			return npcOwnerFenceEventCount;
		}
		public int getExactAuthoredRestorationEventCount() {
			return exactAuthoredRestorationEventCount;
		}
		public int getNpcOwnerUncorrelatedEventCount() {
			return npcOwnerUncorrelatedEventCount;
		}
		public int getNonNpcOwnerEventCount() {
			return nonNpcOwnerEventCount;
		}
		public int getExactRestorationIncompleteEventCount() {
			return exactRestorationIncompleteEventCount;
		}
		public int getBlockerEventReferenceCount() {
			return npcOwnerUncorrelatedEventCount
				+ nonNpcOwnerEventCount
				+ exactRestorationIncompleteEventCount;
		}
	}

	private static final class MutableSource {
		private final int selectedSourceOrdinal;
		private final int packedRegionX;
		private final int packedRegionY;
		private int npcOwnerFenceEventCount;
		private int exactAuthoredRestorationEventCount;
		private int npcOwnerUncorrelatedEventCount;
		private int nonNpcOwnerEventCount;
		private int exactRestorationIncompleteEventCount;

		private MutableSource(
			final int selectedSourceOrdinal,
			final int packedRegionX,
			final int packedRegionY) {
			this.selectedSourceOrdinal = selectedSourceOrdinal;
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}

		private void increment(final EventOutcome outcome) {
			switch (outcome) {
				case NPC_OWNER_FENCE:
					npcOwnerFenceEventCount++;
					break;
				case EXACT_AUTHORED_RESTORATION:
					exactAuthoredRestorationEventCount++;
					break;
				case CANDIDATE_NPC_OWNER_UNCORRELATED:
					npcOwnerUncorrelatedEventCount++;
					break;
				case CANDIDATE_NON_NPC_OWNER:
					nonNpcOwnerEventCount++;
					break;
				case CANDIDATE_EXACT_RESTORATION_INCOMPLETE:
					exactRestorationIncompleteEventCount++;
					break;
				default:
					throw new IllegalArgumentException(
						"Outcome cannot be assigned to one source");
			}
		}

		private SourceCorrelation toImmutable() {
			return new SourceCorrelation(this);
		}
	}

	private static final class AuthoredMatch {
		private final int selectedSourceOrdinal;
		private final int authoredSourceOrdinal;

		private AuthoredMatch(
			final int selectedSourceOrdinal,
			final int authoredSourceOrdinal) {
			this.selectedSourceOrdinal = selectedSourceOrdinal;
			this.authoredSourceOrdinal = authoredSourceOrdinal;
		}
	}

	private static final class AuthoredObjectKey {
		private final long generation;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int authoredSourceOrdinal;

		private AuthoredObjectKey(
			final long generation,
			final int packedRegionX,
			final int packedRegionY,
			final int authoredSourceOrdinal) {
			this.generation = generation;
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.authoredSourceOrdinal = authoredSourceOrdinal;
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) { return true; }
			if (!(other instanceof AuthoredObjectKey)) { return false; }
			AuthoredObjectKey key = (AuthoredObjectKey) other;
			return generation == key.generation
				&& packedRegionX == key.packedRegionX
				&& packedRegionY == key.packedRegionY
				&& authoredSourceOrdinal == key.authoredSourceOrdinal;
		}

		@Override
		public int hashCode() {
			int result = (int) (generation ^ (generation >>> 32));
			result = 31 * result + packedRegionX;
			result = 31 * result + packedRegionY;
			result = 31 * result + authoredSourceOrdinal;
			return result;
		}
	}

	private static String fingerprint(
		final String detachmentFingerprint,
		final String schedulerIdentity,
		final long eventObservedAtTick,
		final int eventCount,
		final List<SourceCorrelation> sources,
		final List<EventCorrelation> retainedEvents,
		final int outsideSelectionOwnerHintEventCount,
		final int outsideSelectionExactSpatialEventCount,
		final int nonSpatialGlobalEventCount) {
		MessageDigest digest = sha256();
		updateString(digest, detachmentFingerprint);
		updateString(digest, schedulerIdentity);
		updateLong(digest, eventObservedAtTick);
		updateInt(digest, eventCount);
		updateInt(digest, sources.size());
		for (SourceCorrelation source : sources) {
			updateInt(digest, source.getSelectedSourceOrdinal());
			updateInt(digest, source.getPackedRegionX());
			updateInt(digest, source.getPackedRegionY());
			updateInt(digest, source.getNpcOwnerFenceEventCount());
			updateInt(
				digest, source.getExactAuthoredRestorationEventCount());
			updateInt(digest, source.getNpcOwnerUncorrelatedEventCount());
			updateInt(digest, source.getNonNpcOwnerEventCount());
			updateInt(
				digest, source.getExactRestorationIncompleteEventCount());
		}
		updateInt(digest, retainedEvents.size());
		for (EventCorrelation event : retainedEvents) {
			updateInt(digest, event.getSnapshotOrdinal());
			updateLong(digest, event.getRegistrationSequence());
			updateInt(digest, event.getOwnerKind().ordinal());
			updateInt(digest, event.getOutcome().ordinal());
			updateInt(digest, event.getCandidateSourceOrdinals().size());
			for (Integer sourceOrdinal
					: event.getCandidateSourceOrdinals()) {
				updateInt(digest, sourceOrdinal.intValue());
			}
			updateInt(
				digest, event.getMatchedSelectedSourceOrdinal());
			updateInt(digest, event.getMatchedAuthoredSourceOrdinal());
		}
		updateInt(digest, outsideSelectionOwnerHintEventCount);
		updateInt(digest, outsideSelectionExactSpatialEventCount);
		updateInt(digest, nonSpatialGlobalEventCount);
		return hex(digest.digest());
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static void updateInt(
		final MessageDigest digest,
		final int value) {
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	private static void updateLong(
		final MessageDigest digest,
		final long value) {
		digest.update((byte) (value >>> 56));
		digest.update((byte) (value >>> 48));
		digest.update((byte) (value >>> 40));
		digest.update((byte) (value >>> 32));
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	private static void updateString(
		final MessageDigest digest,
		final String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		updateInt(digest, bytes.length);
		digest.update(bytes);
	}

	private static String hex(final byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}
}
