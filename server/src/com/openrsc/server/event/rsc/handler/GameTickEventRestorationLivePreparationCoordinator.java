package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryBatchContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryBatchContract.Candidate;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.Preparation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.EventRecord;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.EventRestorationState;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory.RestorationKind;
import com.openrsc.server.model.world.region.RegionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds one exact recovery preparation from an already-detached proposal-
 * scoped event inventory. It performs capture only: no reconstruction, retry,
 * arrival, or visibility action is attached.
 */
final class GameTickEventRestorationLivePreparationCoordinator {
	private final GameTickEventStore store;
	private final GameTickEventRestorationCurrentStateCaptureCoordinator
		currentStateCapture;

	GameTickEventRestorationLivePreparationCoordinator(
		final GameTickEventStore store,
		final RegionManager regionManager) {
		this.store = Objects.requireNonNull(store, "store");
		this.currentStateCapture =
			new GameTickEventRestorationCurrentStateCaptureCoordinator(
				this.store,
				Objects.requireNonNull(regionManager, "regionManager"));
	}

	PreparationCapture capture(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final int maximumCandidates) {
		LayeredPackedRegionEventOwnershipInventory checked =
			Objects.requireNonNull(inventory, "inventory");
		if (maximumCandidates <= 0
			|| maximumCandidates
				> GameTickEventRestorationRecoveryBatchContract
					.MAXIMUM_CANDIDATES) {
			throw new IllegalArgumentException(
				"Live recovery preparation bound is invalid");
		}

		RecoveryPreflight preflight = assessRecovery(checked);
		List<EventRecord> related = preflight.getCompleteRecoveryRecords();
		if (preflight.getProposalRelatedEventCount() > maximumCandidates) {
			return PreparationCapture.refused(
				Reason.CANDIDATE_BOUND_EXCEEDED,
				preflight.getRecoveryCompleteEventCount());
		}
		if (!preflight.isComplete()) {
			return PreparationCapture.refused(
				Reason.RELATED_EVENT_RECOVERY_INCOMPLETE,
				preflight.getRecoveryCompleteEventCount());
		}

		GameTickEventStore.StoreAtomicTimingSnapshot before =
			store.getTrackedEventAtomicTimingSnapshot(
				checked.getObservedAtTick());
		if (!registrationSetMatches(checked, before)) {
			return PreparationCapture.refused(
				Reason.REGISTRATION_SET_DRIFT, related.size());
		}

		List<Candidate> candidates = new ArrayList<Candidate>(related.size());
		List<GameTickEventRestorationCurrentStateRecoverySnapshot> snapshots =
			new ArrayList<
				GameTickEventRestorationCurrentStateRecoverySnapshot>();
		for (EventRecord event : related) {
			final Candidate[] fencedCandidate = new Candidate[1];
			GameTickEventStore.RestorationStableLifecycleExecution lifecycle =
				store.withValidatedRestorationStableLifecycle(
					checked.getSchedulerInstanceIdentity(),
					event.getRegistrationSequence(),
					checked.getProposalGeneration(), fence ->
						fencedCandidate[0] = Candidate.declare(
							fence.getSchedulerInstanceIdentity(),
							fence.getRegistrationSequence(),
							fence.getExpectedProposalGeneration(),
							fence.getLifecycleVersion(),
							fence.getTicksBeforeRun(),
							fence.getTimesRan(), true,
							fence.isOneShotExecution(),
							fence.isContinuingServerTickProgression()));
			if (!lifecycle.isAccepted() || fencedCandidate[0] == null) {
				return PreparationCapture.refused(
					Reason.SCHEDULER_CAPTURE_REFUSED, related.size());
			}
			Candidate candidate = fencedCandidate[0];
			if (candidate.getTicksBeforeRun() > 0L) {
				GameTickEventRestorationCurrentStateCaptureCoordinator
					.CaptureExecution current = currentStateCapture.capture(
						checked.getSchedulerInstanceIdentity(),
						event.getRegistrationSequence(),
						checked.getProposalGeneration());
				if (!current.isSnapshotAvailable()) {
					return PreparationCapture.refused(
						Reason.FUTURE_CURRENT_STATE_CAPTURE_REFUSED,
						related.size());
				}
				GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
					current.getSnapshot();
				candidate = Candidate.declare(
					snapshot.getSchedulerInstanceIdentity(),
					snapshot.getRegistrationSequence(),
					snapshot.getProposalGeneration(),
					snapshot.getLifecycleVersion(),
					snapshot.getTicksBeforeRun(), 0,
					true, true, true);
				snapshots.add(snapshot);
			}
			candidates.add(candidate);
		}

		GameTickEventStore.StoreAtomicTimingSnapshot after =
			store.getTrackedEventAtomicTimingSnapshot(
				checked.getObservedAtTick());
		if (!registrationSetMatches(checked, after)
			|| !candidateTimingMatches(candidates, after)) {
			return PreparationCapture.refused(
				Reason.REGISTRATION_OR_TIMING_DRIFT, related.size());
		}

		GameTickEventRestorationRecoveryBatchContract.Plan plan =
			GameTickEventRestorationRecoveryBatchContract.plan(
				checked.getSchedulerInstanceIdentity(),
				checked.getObservedAtTick(), candidates, maximumCandidates,
				true, true);
		Preparation preparation =
			GameTickEventRestorationRecoveryCoordinatorContract.prepare(
				plan, snapshots, maximumCandidates);
		if (!preparation.isReady()) {
			return PreparationCapture.refused(
				Reason.PREPARATION_CONTRACT_REFUSED, related.size());
		}
		return PreparationCapture.ready(
			preparation, snapshots, checked.getProposalGeneration(),
			checked.getEventCount(), related.size(), maximumCandidates);
	}

	static RecoveryPreflight assessRecovery(
		final LayeredPackedRegionEventOwnershipInventory inventory) {
		LayeredPackedRegionEventOwnershipInventory checked =
			Objects.requireNonNull(inventory, "inventory");
		List<EventRecord> complete = new ArrayList<EventRecord>();
		int proposalRelatedEventCount = 0;
		int incompleteOwnerPositionHintEventCount = 0;
		int incompleteExactSpatialEventCount = 0;
		EventRecord firstIncomplete = null;
		RecoveryRequirement firstIncompleteRequirement = null;
		for (EventRecord event : checked.getEvents()) {
			if (!event.isCandidateRelated()) {
				continue;
			}
			proposalRelatedEventCount++;
			RecoveryRequirement requirement = recoveryRequirement(
				event, checked.getProposalGeneration());
			if (requirement == RecoveryRequirement.COMPLETE) {
				complete.add(event);
				continue;
			}
			if (event.getAttributionKind()
				== AttributionKind.OWNER_POSITION_HINT) {
				incompleteOwnerPositionHintEventCount++;
			}
			if (event.getAttributionKind() == AttributionKind.EXACT_SPATIAL) {
				incompleteExactSpatialEventCount++;
			}
			if (firstIncomplete == null) {
				firstIncomplete = event;
				firstIncompleteRequirement = requirement;
			}
		}
		return RecoveryPreflight.of(
			proposalRelatedEventCount, complete,
			incompleteOwnerPositionHintEventCount,
			incompleteExactSpatialEventCount, firstIncomplete,
			firstIncompleteRequirement);
	}

	private static RecoveryRequirement recoveryRequirement(
		final EventRecord event,
		final long proposalGeneration) {
		EventRestorationState restoration = event.getRestorationState();
		if (restoration.getKind() == RestorationKind.UNAVAILABLE) {
			return RecoveryRequirement.RESTORATION_STATE_UNAVAILABLE;
		}
		if (!event.isAtomicTimingCaptured()) {
			return RecoveryRequirement.ATOMIC_TIMING_UNAVAILABLE;
		}
		if (!event.isRunning()) {
			return RecoveryRequirement.EVENT_NOT_RUNNING;
		}
		if (event.getTimesRan() != 0) {
			return RecoveryRequirement.EVENT_ALREADY_EXECUTED;
		}
		if (!restoration.isDetachedCallbackPayloadComplete()) {
			return RecoveryRequirement.DETACHED_PAYLOAD_INCOMPLETE;
		}
		if (!restoration.isExecutionSemanticsCaptured()) {
			return RecoveryRequirement.EXECUTION_SEMANTICS_INCOMPLETE;
		}
		if (!restoration.isTargetBindingRequirementCaptured()) {
			return RecoveryRequirement
				.TARGET_BINDING_REQUIREMENT_INCOMPLETE;
		}
		if (!restoration.isTargetBindingComplete()) {
			return RecoveryRequirement.TARGET_BINDING_INCOMPLETE;
		}
		if (!restoration.isArrivalOrderingCaptured()) {
			return RecoveryRequirement.ARRIVAL_ORDERING_INCOMPLETE;
		}
		if (!restoration.isGenerationBindingRequirementCaptured()) {
			return RecoveryRequirement
				.GENERATION_BINDING_REQUIREMENT_INCOMPLETE;
		}
		if (!restoration.isGenerationBindingComplete(proposalGeneration)) {
			return RecoveryRequirement.GENERATION_BINDING_MISMATCH;
		}
		if (!restoration.isIdempotencyRequirementCaptured()) {
			return RecoveryRequirement.IDEMPOTENCY_REQUIREMENT_INCOMPLETE;
		}
		return RecoveryRequirement.COMPLETE;
	}

	private static boolean registrationSetMatches(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final GameTickEventStore.StoreAtomicTimingSnapshot timing) {
		if (!inventory.getSchedulerInstanceIdentity().equals(
				timing.getSchedulerInstanceIdentity())
			|| inventory.getEventCount() != timing.getRegistrations().size()) {
			return false;
		}
		Set<Long> expected = new HashSet<Long>();
		for (EventRecord event : inventory.getEvents()) {
			expected.add(Long.valueOf(event.getRegistrationSequence()));
		}
		Set<Long> observed = new HashSet<Long>();
		for (GameTickEventStore.AtomicTimedRegisteredEvent registration
			: timing.getRegistrations()) {
			observed.add(Long.valueOf(
				registration.getRegistrationSequence()));
		}
		return expected.size() == inventory.getEventCount()
			&& observed.size() == timing.getRegistrations().size()
			&& expected.equals(observed);
	}

	private static boolean candidateTimingMatches(
		final List<Candidate> candidates,
		final GameTickEventStore.StoreAtomicTimingSnapshot timing) {
		Map<Long, GameTickEvent.AtomicTimingSnapshot> byRegistration =
			new HashMap<Long, GameTickEvent.AtomicTimingSnapshot>();
		for (GameTickEventStore.AtomicTimedRegisteredEvent registration
			: timing.getRegistrations()) {
			if (byRegistration.put(
				Long.valueOf(registration.getRegistrationSequence()),
				registration.getTiming()) != null) {
				return false;
			}
		}
		for (Candidate candidate : candidates) {
			GameTickEvent.AtomicTimingSnapshot observed = byRegistration.get(
				Long.valueOf(candidate.getRegistrationSequence()));
			if (observed == null
				|| candidate.getLifecycleVersion()
					!= observed.getLifecycleVersion()
				|| candidate.getTicksBeforeRun()
					!= observed.getTicksBeforeRun()
				|| candidate.getTimesRan() != observed.getTimesRan()
				|| candidate.isRunning() != observed.isRunning()) {
				return false;
			}
		}
		return true;
	}

	enum Reason {
		READY_BEFORE_RECONSTRUCTION,
		CANDIDATE_BOUND_EXCEEDED,
		RELATED_EVENT_RECOVERY_INCOMPLETE,
		REGISTRATION_SET_DRIFT,
		SCHEDULER_CAPTURE_REFUSED,
		FUTURE_CURRENT_STATE_CAPTURE_REFUSED,
		REGISTRATION_OR_TIMING_DRIFT,
		PREPARATION_CONTRACT_REFUSED
	}

	enum RecoveryRequirement {
		COMPLETE,
		RESTORATION_STATE_UNAVAILABLE,
		ATOMIC_TIMING_UNAVAILABLE,
		EVENT_NOT_RUNNING,
		EVENT_ALREADY_EXECUTED,
		DETACHED_PAYLOAD_INCOMPLETE,
		EXECUTION_SEMANTICS_INCOMPLETE,
		TARGET_BINDING_REQUIREMENT_INCOMPLETE,
		TARGET_BINDING_INCOMPLETE,
		ARRIVAL_ORDERING_INCOMPLETE,
		GENERATION_BINDING_REQUIREMENT_INCOMPLETE,
		GENERATION_BINDING_MISMATCH,
		IDEMPOTENCY_REQUIREMENT_INCOMPLETE
	}

	/**
	 * Bounded detached proposal preflight. It retains inventory records only
	 * package-locally for the immediately following capture pass.
	 */
	static final class RecoveryPreflight {
		private final int proposalRelatedEventCount;
		private final List<EventRecord> completeRecoveryRecords;
		private final int recoveryIncompleteEventCount;
		private final int incompleteOwnerPositionHintEventCount;
		private final int incompleteExactSpatialEventCount;
		private final Long firstIncompleteRegistrationSequence;
		private final OwnerKind firstIncompleteOwnerKind;
		private final AttributionKind firstIncompleteAttributionKind;
		private final RecoveryRequirement firstIncompleteRequirement;

		private RecoveryPreflight(
			final int proposalRelatedEventCount,
			final List<EventRecord> completeRecoveryRecords,
			final int incompleteOwnerPositionHintEventCount,
			final int incompleteExactSpatialEventCount,
			final EventRecord firstIncomplete,
			final RecoveryRequirement firstIncompleteRequirement) {
			this.proposalRelatedEventCount = proposalRelatedEventCount;
			this.completeRecoveryRecords = Collections.unmodifiableList(
				new ArrayList<EventRecord>(completeRecoveryRecords));
			this.recoveryIncompleteEventCount =
				proposalRelatedEventCount - completeRecoveryRecords.size();
			this.incompleteOwnerPositionHintEventCount =
				incompleteOwnerPositionHintEventCount;
			this.incompleteExactSpatialEventCount =
				incompleteExactSpatialEventCount;
			this.firstIncompleteRegistrationSequence =
				firstIncomplete == null ? null
					: Long.valueOf(firstIncomplete.getRegistrationSequence());
			this.firstIncompleteOwnerKind =
				firstIncomplete == null ? null : firstIncomplete.getOwnerKind();
			this.firstIncompleteAttributionKind = firstIncomplete == null
				? null : firstIncomplete.getAttributionKind();
			this.firstIncompleteRequirement = firstIncompleteRequirement;
			boolean incomplete = recoveryIncompleteEventCount > 0;
			if (proposalRelatedEventCount < 0
				|| completeRecoveryRecords.size() > proposalRelatedEventCount
				|| incompleteOwnerPositionHintEventCount < 0
				|| incompleteExactSpatialEventCount < 0
				|| incompleteOwnerPositionHintEventCount
					+ incompleteExactSpatialEventCount
					> recoveryIncompleteEventCount
				|| incomplete != (firstIncomplete != null)
				|| incomplete != (firstIncompleteRequirement != null)
				|| firstIncompleteRequirement == RecoveryRequirement.COMPLETE) {
				throw new IllegalArgumentException(
					"Recovery preflight is inconsistent");
			}
		}

		private static RecoveryPreflight of(
			final int proposalRelatedEventCount,
			final List<EventRecord> completeRecoveryRecords,
			final int incompleteOwnerPositionHintEventCount,
			final int incompleteExactSpatialEventCount,
			final EventRecord firstIncomplete,
			final RecoveryRequirement firstIncompleteRequirement) {
			return new RecoveryPreflight(
				proposalRelatedEventCount, completeRecoveryRecords,
				incompleteOwnerPositionHintEventCount,
				incompleteExactSpatialEventCount, firstIncomplete,
				firstIncompleteRequirement);
		}

		int getProposalRelatedEventCount() {
			return proposalRelatedEventCount;
		}
		int getRecoveryCompleteEventCount() {
			return completeRecoveryRecords.size();
		}
		int getRecoveryIncompleteEventCount() {
			return recoveryIncompleteEventCount;
		}
		int getIncompleteOwnerPositionHintEventCount() {
			return incompleteOwnerPositionHintEventCount;
		}
		int getIncompleteExactSpatialEventCount() {
			return incompleteExactSpatialEventCount;
		}
		Long getFirstIncompleteRegistrationSequence() {
			return firstIncompleteRegistrationSequence;
		}
		OwnerKind getFirstIncompleteOwnerKind() {
			return firstIncompleteOwnerKind;
		}
		AttributionKind getFirstIncompleteAttributionKind() {
			return firstIncompleteAttributionKind;
		}
		RecoveryRequirement getFirstIncompleteRequirement() {
			return firstIncompleteRequirement;
		}
		boolean isComplete() { return recoveryIncompleteEventCount == 0; }
		List<EventRecord> getCompleteRecoveryRecords() {
			return completeRecoveryRecords;
		}
		boolean isRuntimeHandleRetained() { return false; }
	}

	/** Closed detached preparation; no scheduler, event, or Region handle. */
	static final class PreparationCapture {
		private final Reason reason;
		private final Preparation preparation;
		private final List<GameTickEventRestorationCurrentStateRecoverySnapshot>
			futureSnapshots;
		private final long proposalGeneration;
		private final int inventoryEventCount;
		private final int recoveryCandidateCount;
		private final int maximumCandidates;

		private PreparationCapture(
			final Reason reason,
			final Preparation preparation,
			final List<GameTickEventRestorationCurrentStateRecoverySnapshot>
				futureSnapshots,
			final long proposalGeneration,
			final int inventoryEventCount,
			final int recoveryCandidateCount,
			final int maximumCandidates) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.preparation = preparation;
			this.futureSnapshots = futureSnapshots;
			this.proposalGeneration = proposalGeneration;
			this.inventoryEventCount = inventoryEventCount;
			this.recoveryCandidateCount = recoveryCandidateCount;
			this.maximumCandidates = maximumCandidates;
			boolean ready = reason == Reason.READY_BEFORE_RECONSTRUCTION;
			if (ready != (preparation != null)
				|| ready != (futureSnapshots != null)
				|| ready != (proposalGeneration > 0L)
				|| ready != (inventoryEventCount >= 0)
				|| recoveryCandidateCount < 0
				|| ready != (maximumCandidates > 0)) {
				throw new IllegalArgumentException(
					"Live recovery preparation result is inconsistent");
			}
		}

		private static PreparationCapture refused(
			final Reason reason,
			final int recoveryCandidateCount) {
			if (reason == Reason.READY_BEFORE_RECONSTRUCTION) {
				throw new IllegalArgumentException(
					"Ready reason cannot refuse live preparation");
			}
			return new PreparationCapture(
				reason, null, null, -1L, -1,
				recoveryCandidateCount, -1);
		}

		private static PreparationCapture ready(
			final Preparation preparation,
			final List<GameTickEventRestorationCurrentStateRecoverySnapshot>
				futureSnapshots,
			final long proposalGeneration,
			final int inventoryEventCount,
			final int recoveryCandidateCount,
			final int maximumCandidates) {
			return new PreparationCapture(
				Reason.READY_BEFORE_RECONSTRUCTION, preparation,
				Collections.unmodifiableList(new ArrayList<
					GameTickEventRestorationCurrentStateRecoverySnapshot>(
						futureSnapshots)),
				proposalGeneration, inventoryEventCount,
				recoveryCandidateCount, maximumCandidates);
		}

		Reason getReason() { return reason; }
		boolean isReady() { return reason == Reason.READY_BEFORE_RECONSTRUCTION; }
		Preparation getPreparation() { return preparation; }
		List<GameTickEventRestorationCurrentStateRecoverySnapshot>
			getFutureSnapshots() {
			return futureSnapshots == null
				? Collections
					.<GameTickEventRestorationCurrentStateRecoverySnapshot>
						emptyList()
				: futureSnapshots;
		}
		long getProposalGeneration() { return proposalGeneration; }
		int getInventoryEventCount() { return inventoryEventCount; }
		int getRecoveryCandidateCount() { return recoveryCandidateCount; }
		int getMaximumCandidates() { return maximumCandidates; }
		boolean isRegistrationSetStable() { return isReady(); }
		boolean isCandidateTimingStable() { return isReady(); }
		boolean isRuntimeHandleRetained() { return false; }
		boolean isReconstructionInvoked() { return false; }
		boolean isRetryPerformed() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isVisibilityReleased() { return false; }
	}
}
