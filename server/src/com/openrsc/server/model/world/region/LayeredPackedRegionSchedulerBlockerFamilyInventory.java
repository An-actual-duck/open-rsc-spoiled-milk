package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.EventRecord;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.EventTypeIdentity;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionEventOwnershipInventory.RestorationKind;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation.EventCorrelation;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation.EventOutcome;

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
 * Bounded detached reduction of scheduler blockers by implementation family.
 *
 * <p>One family is an exact tuple of blocker outcome, detached type identity,
 * owner kind, attribution kind, and restoration kind. Families retain only
 * primitive counts, timing ranges, stable snapshot/registration boundaries,
 * and copied names. They do not reinterpret an unattributed callback as safe:
 * every input blocker remains a blocker until its implementation explicitly
 * establishes the required spatial or preservation contract.</p>
 *
 * <p>No event, callback, owner, scheduler, {@code Class}, class loader,
 * entity, Region, registry, lifecycle boundary, or runtime handle is retained.
 * This inventory cannot cancel, reschedule, preserve, detach, reconstruct, or
 * mutate runtime state and grants no lifecycle authority.</p>
 */
public final class LayeredPackedRegionSchedulerBlockerFamilyInventory {
	public static final int MAXIMUM_FAMILIES =
		LayeredPackedRegionEventOwnershipInventory.MAXIMUM_EVENTS;

	private final long generation;
	private final long eventObservedAtTick;
	private final String schedulerInstanceIdentity;
	private final String sourceCorrelationFingerprintSha256;
	private final List<BlockerFamily> families;
	private final int blockerEventCount;
	private final int candidateNpcOwnerUncorrelatedEventCount;
	private final int candidateNonNpcOwnerEventCount;
	private final int candidateExactRestorationIncompleteEventCount;
	private final int unattributedEventCount;
	private final int runningEventCount;
	private final int candidateRelatedEventCount;
	private final int selectedSourceReferenceCount;
	private final String fingerprintSha256;

	private LayeredPackedRegionSchedulerBlockerFamilyInventory(
		final
			LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation
				correlation,
		final List<BlockerFamily> families,
		final int candidateNpcOwnerUncorrelatedEventCount,
		final int candidateNonNpcOwnerEventCount,
		final int candidateExactRestorationIncompleteEventCount,
		final int unattributedEventCount,
		final int runningEventCount,
		final int candidateRelatedEventCount,
		final int selectedSourceReferenceCount) {
		this.generation = correlation.getGeneration();
		this.eventObservedAtTick = correlation.getEventObservedAtTick();
		this.schedulerInstanceIdentity =
			correlation.getSchedulerInstanceIdentity();
		this.sourceCorrelationFingerprintSha256 =
			correlation.getFingerprintSha256();
		this.families = Collections.unmodifiableList(families);
		this.blockerEventCount = correlation.getBlockerEventCount();
		this.candidateNpcOwnerUncorrelatedEventCount =
			candidateNpcOwnerUncorrelatedEventCount;
		this.candidateNonNpcOwnerEventCount =
			candidateNonNpcOwnerEventCount;
		this.candidateExactRestorationIncompleteEventCount =
			candidateExactRestorationIncompleteEventCount;
		this.unattributedEventCount = unattributedEventCount;
		this.runningEventCount = runningEventCount;
		this.candidateRelatedEventCount = candidateRelatedEventCount;
		this.selectedSourceReferenceCount = selectedSourceReferenceCount;
		this.fingerprintSha256 = fingerprint(
			sourceCorrelationFingerprintSha256, families);
	}

	/**
	 * Reduces every retained blocker from one exact correlation/inventory pair.
	 *
	 * <p>Truncated blocker details and uncaptured type identities refuse rather
	 * than manufacturing a partial family inventory.</p>
	 */
	public static LayeredPackedRegionSchedulerBlockerFamilyInventory reduce(
		final
			LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation
				sourceCorrelation,
		final LayeredPackedRegionEventOwnershipInventory eventInventory,
		final int maximumFamilies) {
		LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation correlation =
			Objects.requireNonNull(sourceCorrelation, "sourceCorrelation");
		LayeredPackedRegionEventOwnershipInventory inventory =
			Objects.requireNonNull(eventInventory, "eventInventory");
		if (maximumFamilies < 0 || maximumFamilies > MAXIMUM_FAMILIES
			|| correlation.getGeneration()
				!= inventory.getProposalGeneration()
			|| correlation.getEventObservedAtTick()
				!= inventory.getObservedAtTick()
			|| !correlation.getSchedulerInstanceIdentity().equals(
				inventory.getSchedulerInstanceIdentity())
			|| correlation.getEventCount() != inventory.getEventCount()
			|| !correlation.areAllSchedulerEventsClassified()) {
			throw new IllegalArgumentException(
				"Scheduler blocker family evidence is not aligned");
		}

		Map<FamilyKey, MutableFamily> grouped =
			new LinkedHashMap<FamilyKey, MutableFamily>();
		int retainedBlockers = 0;
		int candidateNpc = 0;
		int candidateNonNpc = 0;
		int candidateExactIncomplete = 0;
		int unattributed = 0;
		int running = 0;
		int candidateRelated = 0;
		int sourceReferences = 0;
		for (EventCorrelation blocked : correlation.getRetainedEvents()) {
			if (!blocked.isBlocker()) { continue; }
			retainedBlockers = Math.addExact(retainedBlockers, 1);
			EventRecord event = exactEvent(inventory, blocked);
			EventTypeIdentity type = event.getEventTypeIdentity();
			if (type == null || !type.isCaptured()
				|| type.isClassHandle() || type.isCallbackHandle()
				|| type.isSchedulerHandle()
				|| type.isLifecycleAuthority()) {
				throw new IllegalArgumentException(
					"Scheduler blocker type identity is incomplete");
			}
			EventOutcome outcome = blocked.getOutcome();
			switch (outcome) {
				case CANDIDATE_NPC_OWNER_UNCORRELATED:
					candidateNpc++;
					break;
				case CANDIDATE_NON_NPC_OWNER:
					candidateNonNpc++;
					break;
				case CANDIDATE_EXACT_RESTORATION_INCOMPLETE:
					candidateExactIncomplete++;
					break;
				case UNATTRIBUTED_BLOCKER:
					unattributed++;
					break;
				default:
					throw new IllegalArgumentException(
						"Non-blocker outcome entered blocker reduction");
			}
			running += event.isRunning() ? 1 : 0;
			candidateRelated += event.isCandidateRelated() ? 1 : 0;
			sourceReferences = Math.addExact(
				sourceReferences,
				event.getCandidateSourceOrdinals().size());

			FamilyKey key = new FamilyKey(event, outcome);
			MutableFamily family = grouped.get(key);
			if (family == null) {
				if (grouped.size() >= maximumFamilies) {
					throw new IllegalArgumentException(
						"Scheduler blocker families exceed their budget");
				}
				family = new MutableFamily(grouped.size(), event, outcome);
				grouped.put(key, family);
			}
			family.add(event);
		}

		if (retainedBlockers != correlation.getBlockerEventCount()
			|| candidateNpc
				!= correlation
					.getCandidateNpcOwnerUncorrelatedEventCount()
			|| candidateNonNpc
				!= correlation.getCandidateNonNpcOwnerEventCount()
			|| candidateExactIncomplete
				!= correlation
					.getCandidateExactRestorationIncompleteEventCount()
			|| unattributed != correlation.getUnattributedEventCount()) {
			throw new IllegalArgumentException(
				"Scheduler blocker details are incomplete");
		}

		List<BlockerFamily> families =
			new ArrayList<BlockerFamily>(grouped.size());
		int familyEvents = 0;
		int familyRunning = 0;
		int familyCandidateRelated = 0;
		int familySourceReferences = 0;
		for (MutableFamily mutable : grouped.values()) {
			BlockerFamily family = mutable.toImmutable();
			families.add(family);
			familyEvents = Math.addExact(
				familyEvents, family.getEventCount());
			familyRunning = Math.addExact(
				familyRunning, family.getRunningEventCount());
			familyCandidateRelated = Math.addExact(
				familyCandidateRelated,
				family.getCandidateRelatedEventCount());
			familySourceReferences = Math.addExact(
				familySourceReferences,
				family.getSelectedSourceReferenceCount());
		}
		if (familyEvents != retainedBlockers
			|| familyRunning != running
			|| familyCandidateRelated != candidateRelated
			|| familySourceReferences != sourceReferences) {
			throw new IllegalStateException(
				"Scheduler blocker family totals do not reconcile");
		}
		return new LayeredPackedRegionSchedulerBlockerFamilyInventory(
			correlation, families, candidateNpc, candidateNonNpc,
			candidateExactIncomplete, unattributed, running,
			candidateRelated, sourceReferences);
	}

	private static EventRecord exactEvent(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final EventCorrelation blocked) {
		int ordinal = blocked.getSnapshotOrdinal();
		if (ordinal < 0 || ordinal >= inventory.getEvents().size()) {
			throw new IllegalArgumentException(
				"Scheduler blocker ordinal is outside the inventory");
		}
		EventRecord event = inventory.getEvents().get(ordinal);
		if (event.getSnapshotOrdinal() != ordinal
			|| event.getRegistrationSequence()
				!= blocked.getRegistrationSequence()
			|| event.getOwnerKind() != blocked.getOwnerKind()
			|| !event.getCandidateSourceOrdinals().equals(
				blocked.getCandidateSourceOrdinals())) {
			throw new IllegalArgumentException(
				"Scheduler blocker identity changed after correlation");
		}
		return event;
	}

	public long getGeneration() { return generation; }
	public long getEventObservedAtTick() { return eventObservedAtTick; }
	public String getSchedulerInstanceIdentity() {
		return schedulerInstanceIdentity;
	}
	public String getSourceCorrelationFingerprintSha256() {
		return sourceCorrelationFingerprintSha256;
	}
	public List<BlockerFamily> getFamilies() { return families; }
	public int getFamilyCount() { return families.size(); }
	public int getBlockerEventCount() { return blockerEventCount; }
	public int getCandidateNpcOwnerUncorrelatedEventCount() {
		return candidateNpcOwnerUncorrelatedEventCount;
	}
	public int getCandidateNonNpcOwnerEventCount() {
		return candidateNonNpcOwnerEventCount;
	}
	public int getCandidateExactRestorationIncompleteEventCount() {
		return candidateExactRestorationIncompleteEventCount;
	}
	public int getUnattributedEventCount() { return unattributedEventCount; }
	public int getRunningEventCount() { return runningEventCount; }
	public int getCandidateRelatedEventCount() {
		return candidateRelatedEventCount;
	}
	public int getSelectedSourceReferenceCount() {
		return selectedSourceReferenceCount;
	}
	public String getFingerprintSha256() { return fingerprintSha256; }

	public boolean areAllBlockersRetained() { return true; }
	public boolean isEventTypeIdentityComplete() { return true; }
	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isAttributionChanged() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isEventCancellation() { return false; }
	public boolean isEventReschedule() { return false; }
	public boolean isPreservationPerformed() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isRuntimeMutationAuthorized() { return false; }
	public boolean isRuntimeMutationPerformed() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** One first-observation-ordered blocker family. */
	public static final class BlockerFamily {
		private final int familyOrdinal;
		private final EventOutcome outcome;
		private final String runtimeTypeName;
		private final String familyTypeName;
		private final String directSupertypeName;
		private final boolean anonymousType;
		private final boolean localType;
		private final boolean syntheticType;
		private final OwnerKind ownerKind;
		private final AttributionKind attributionKind;
		private final RestorationKind restorationKind;
		private final int eventCount;
		private final int runningEventCount;
		private final int candidateRelatedEventCount;
		private final int selectedSourceReferenceCount;
		private final int firstSnapshotOrdinal;
		private final int lastSnapshotOrdinal;
		private final long firstRegistrationSequence;
		private final long lastRegistrationSequence;
		private final long minimumTicksBeforeRun;
		private final long maximumTicksBeforeRun;
		private final int minimumTimesRan;
		private final int maximumTimesRan;

		private BlockerFamily(final MutableFamily family) {
			this.familyOrdinal = family.familyOrdinal;
			this.outcome = family.key.outcome;
			this.runtimeTypeName = family.key.runtimeTypeName;
			this.familyTypeName = family.key.familyTypeName;
			this.directSupertypeName = family.key.directSupertypeName;
			this.anonymousType = family.key.anonymousType;
			this.localType = family.key.localType;
			this.syntheticType = family.key.syntheticType;
			this.ownerKind = family.key.ownerKind;
			this.attributionKind = family.key.attributionKind;
			this.restorationKind = family.key.restorationKind;
			this.eventCount = family.eventCount;
			this.runningEventCount = family.runningEventCount;
			this.candidateRelatedEventCount =
				family.candidateRelatedEventCount;
			this.selectedSourceReferenceCount =
				family.selectedSourceReferenceCount;
			this.firstSnapshotOrdinal = family.firstSnapshotOrdinal;
			this.lastSnapshotOrdinal = family.lastSnapshotOrdinal;
			this.firstRegistrationSequence =
				family.firstRegistrationSequence;
			this.lastRegistrationSequence =
				family.lastRegistrationSequence;
			this.minimumTicksBeforeRun = family.minimumTicksBeforeRun;
			this.maximumTicksBeforeRun = family.maximumTicksBeforeRun;
			this.minimumTimesRan = family.minimumTimesRan;
			this.maximumTimesRan = family.maximumTimesRan;
		}

		public int getFamilyOrdinal() { return familyOrdinal; }
		public EventOutcome getOutcome() { return outcome; }
		public String getRuntimeTypeName() { return runtimeTypeName; }
		public String getFamilyTypeName() { return familyTypeName; }
		public String getDirectSupertypeName() {
			return directSupertypeName;
		}
		public boolean isAnonymousType() { return anonymousType; }
		public boolean isLocalType() { return localType; }
		public boolean isSyntheticType() { return syntheticType; }
		public OwnerKind getOwnerKind() { return ownerKind; }
		public AttributionKind getAttributionKind() {
			return attributionKind;
		}
		public RestorationKind getRestorationKind() {
			return restorationKind;
		}
		public int getEventCount() { return eventCount; }
		public int getRunningEventCount() { return runningEventCount; }
		public int getCandidateRelatedEventCount() {
			return candidateRelatedEventCount;
		}
		public int getSelectedSourceReferenceCount() {
			return selectedSourceReferenceCount;
		}
		public int getFirstSnapshotOrdinal() {
			return firstSnapshotOrdinal;
		}
		public int getLastSnapshotOrdinal() { return lastSnapshotOrdinal; }
		public long getFirstRegistrationSequence() {
			return firstRegistrationSequence;
		}
		public long getLastRegistrationSequence() {
			return lastRegistrationSequence;
		}
		public long getMinimumTicksBeforeRun() {
			return minimumTicksBeforeRun;
		}
		public long getMaximumTicksBeforeRun() {
			return maximumTicksBeforeRun;
		}
		public int getMinimumTimesRan() { return minimumTimesRan; }
		public int getMaximumTimesRan() { return maximumTimesRan; }
	}

	private static final class MutableFamily {
		private final int familyOrdinal;
		private final FamilyKey key;
		private int eventCount;
		private int runningEventCount;
		private int candidateRelatedEventCount;
		private int selectedSourceReferenceCount;
		private int firstSnapshotOrdinal = -1;
		private int lastSnapshotOrdinal = -1;
		private long firstRegistrationSequence = -1L;
		private long lastRegistrationSequence = -1L;
		private long minimumTicksBeforeRun = Long.MAX_VALUE;
		private long maximumTicksBeforeRun = Long.MIN_VALUE;
		private int minimumTimesRan = Integer.MAX_VALUE;
		private int maximumTimesRan = Integer.MIN_VALUE;

		private MutableFamily(
			final int familyOrdinal,
			final EventRecord first,
			final EventOutcome outcome) {
			this.familyOrdinal = familyOrdinal;
			this.key = new FamilyKey(first, outcome);
		}

		private void add(final EventRecord event) {
			if (!key.equals(new FamilyKey(event, key.outcome))) {
				throw new IllegalArgumentException(
					"Scheduler blocker family identity changed");
			}
			if (eventCount == 0) {
				firstSnapshotOrdinal = event.getSnapshotOrdinal();
				firstRegistrationSequence =
					event.getRegistrationSequence();
			}
			lastSnapshotOrdinal = event.getSnapshotOrdinal();
			lastRegistrationSequence = event.getRegistrationSequence();
			eventCount++;
			runningEventCount += event.isRunning() ? 1 : 0;
			candidateRelatedEventCount +=
				event.isCandidateRelated() ? 1 : 0;
			selectedSourceReferenceCount = Math.addExact(
				selectedSourceReferenceCount,
				event.getCandidateSourceOrdinals().size());
			minimumTicksBeforeRun = Math.min(
				minimumTicksBeforeRun, event.getTicksBeforeRun());
			maximumTicksBeforeRun = Math.max(
				maximumTicksBeforeRun, event.getTicksBeforeRun());
			minimumTimesRan = Math.min(
				minimumTimesRan, event.getTimesRan());
			maximumTimesRan = Math.max(
				maximumTimesRan, event.getTimesRan());
		}

		private BlockerFamily toImmutable() {
			if (eventCount <= 0) {
				throw new IllegalStateException(
					"Scheduler blocker family is empty");
			}
			return new BlockerFamily(this);
		}
	}

	private static final class FamilyKey {
		private final EventOutcome outcome;
		private final String runtimeTypeName;
		private final String familyTypeName;
		private final String directSupertypeName;
		private final boolean anonymousType;
		private final boolean localType;
		private final boolean syntheticType;
		private final OwnerKind ownerKind;
		private final AttributionKind attributionKind;
		private final RestorationKind restorationKind;

		private FamilyKey(
			final EventRecord event,
			final EventOutcome outcome) {
			EventTypeIdentity type = event.getEventTypeIdentity();
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.runtimeTypeName = type.getRuntimeTypeName();
			this.familyTypeName = type.getFamilyTypeName();
			this.directSupertypeName = type.getDirectSupertypeName();
			this.anonymousType = type.isAnonymousType();
			this.localType = type.isLocalType();
			this.syntheticType = type.isSyntheticType();
			this.ownerKind = event.getOwnerKind();
			this.attributionKind = event.getAttributionKind();
			this.restorationKind =
				event.getRestorationState().getKind();
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) { return true; }
			if (!(other instanceof FamilyKey)) { return false; }
			FamilyKey key = (FamilyKey) other;
			return anonymousType == key.anonymousType
				&& localType == key.localType
				&& syntheticType == key.syntheticType
				&& outcome == key.outcome
				&& runtimeTypeName.equals(key.runtimeTypeName)
				&& familyTypeName.equals(key.familyTypeName)
				&& directSupertypeName.equals(key.directSupertypeName)
				&& ownerKind == key.ownerKind
				&& attributionKind == key.attributionKind
				&& restorationKind == key.restorationKind;
		}

		@Override
		public int hashCode() {
			int result = outcome.hashCode();
			result = 31 * result + runtimeTypeName.hashCode();
			result = 31 * result + familyTypeName.hashCode();
			result = 31 * result + directSupertypeName.hashCode();
			result = 31 * result + (anonymousType ? 1 : 0);
			result = 31 * result + (localType ? 1 : 0);
			result = 31 * result + (syntheticType ? 1 : 0);
			result = 31 * result + ownerKind.hashCode();
			result = 31 * result + attributionKind.hashCode();
			result = 31 * result + restorationKind.hashCode();
			return result;
		}
	}

	private static String fingerprint(
		final String correlationFingerprint,
		final List<BlockerFamily> families) {
		MessageDigest digest = sha256();
		updateString(digest, correlationFingerprint);
		updateInt(digest, families.size());
		for (BlockerFamily family : families) {
			updateInt(digest, family.getFamilyOrdinal());
			updateInt(digest, family.getOutcome().ordinal());
			updateString(digest, family.getRuntimeTypeName());
			updateString(digest, family.getFamilyTypeName());
			updateString(digest, family.getDirectSupertypeName());
			updateBoolean(digest, family.isAnonymousType());
			updateBoolean(digest, family.isLocalType());
			updateBoolean(digest, family.isSyntheticType());
			updateInt(digest, family.getOwnerKind().ordinal());
			updateInt(digest, family.getAttributionKind().ordinal());
			updateInt(digest, family.getRestorationKind().ordinal());
			updateInt(digest, family.getEventCount());
			updateInt(digest, family.getRunningEventCount());
			updateInt(digest, family.getCandidateRelatedEventCount());
			updateInt(
				digest, family.getSelectedSourceReferenceCount());
			updateInt(digest, family.getFirstSnapshotOrdinal());
			updateInt(digest, family.getLastSnapshotOrdinal());
			updateLong(digest, family.getFirstRegistrationSequence());
			updateLong(digest, family.getLastRegistrationSequence());
			updateLong(digest, family.getMinimumTicksBeforeRun());
			updateLong(digest, family.getMaximumTicksBeforeRun());
			updateInt(digest, family.getMinimumTimesRan());
			updateInt(digest, family.getMaximumTimesRan());
		}
		return hex(digest.digest());
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static void updateBoolean(
		final MessageDigest digest,
		final boolean value) {
		digest.update((byte) (value ? 1 : 0));
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
