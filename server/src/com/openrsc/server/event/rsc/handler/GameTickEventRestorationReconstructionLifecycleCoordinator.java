package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryBatchContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.Directive;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.Preparation;
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
 * Single-use ordering seam for capture, caller-supplied reconstruction, and
 * bounded event recovery. It neither implements reconstruction nor releases
 * first visibility.
 */
final class GameTickEventRestorationReconstructionLifecycleCoordinator {
	private final GameTickEventRestorationRecoveryBatchExecutor batchExecutor;

	GameTickEventRestorationReconstructionLifecycleCoordinator(
		final GameTickEventStore store,
		final RegionManager regionManager) {
		this.batchExecutor = new GameTickEventRestorationRecoveryBatchExecutor(
			Objects.requireNonNull(store, "store"),
			Objects.requireNonNull(regionManager, "regionManager"));
	}

	CapturedRecovery captureBeforeReconstruction(
		final Preparation preparation,
		final List<GameTickEventRestorationCurrentStateRecoverySnapshot>
			futureSnapshots,
		final long proposalGeneration,
		final int maximumCandidates) {
		Preparation checked = Objects.requireNonNull(
			preparation, "preparation");
		List<GameTickEventRestorationCurrentStateRecoverySnapshot> snapshots =
			Objects.requireNonNull(futureSnapshots, "futureSnapshots");
		if (proposalGeneration <= 0L
			|| maximumCandidates <= 0
			|| maximumCandidates
				> GameTickEventRestorationRecoveryBatchContract
					.MAXIMUM_CANDIDATES) {
			throw new IllegalArgumentException(
				"Reconstruction lifecycle capture bound is invalid");
		}
		if (!checked.isReady()) {
			return CapturedRecovery.refused(
				CaptureReason.PREPARATION_REFUSED);
		}
		if (checked.getDirectives().size() > maximumCandidates
			|| snapshots.size() > maximumCandidates) {
			return CapturedRecovery.refused(
				CaptureReason.CANDIDATE_BOUND_EXCEEDED);
		}

		Map<Long, GameTickEventRestorationCurrentStateRecoverySnapshot>
			futureByRegistration = new HashMap<Long,
				GameTickEventRestorationCurrentStateRecoverySnapshot>();
		for (GameTickEventRestorationCurrentStateRecoverySnapshot snapshot
			: snapshots) {
			GameTickEventRestorationCurrentStateRecoverySnapshot value =
				Objects.requireNonNull(snapshot, "futureSnapshot");
			if (futureByRegistration.put(
				Long.valueOf(value.getRegistrationSequence()), value) != null) {
				return CapturedRecovery.refused(
					CaptureReason.DUPLICATE_FUTURE_SNAPSHOT);
			}
		}
		Set<Long> matched = new HashSet<Long>();
		for (Directive directive : checked.getDirectives()) {
			if (directive.getProposalGeneration() != proposalGeneration) {
				return CapturedRecovery.refused(
					CaptureReason.PROPOSAL_GENERATION_MISMATCH);
			}
			GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
				futureByRegistration.get(
					Long.valueOf(directive.getRegistrationSequence()));
			if (directive.isFutureSnapshotCorrelated()) {
				if (snapshot == null
					|| !snapshotMatches(
						checked, directive, snapshot, proposalGeneration)) {
					return CapturedRecovery.refused(
						CaptureReason.FUTURE_SNAPSHOT_SET_MISMATCH);
				}
				matched.add(Long.valueOf(
					directive.getRegistrationSequence()));
			} else if (snapshot != null) {
				return CapturedRecovery.refused(
					CaptureReason.FUTURE_SNAPSHOT_SET_MISMATCH);
			}
		}
		if (matched.size() != futureByRegistration.size()) {
			return CapturedRecovery.refused(
				CaptureReason.FUTURE_SNAPSHOT_SET_MISMATCH);
		}
		return CapturedRecovery.captured(
			checked, snapshots, proposalGeneration, maximumCandidates);
	}

	LifecycleExecution reconstructThenRecover(
		final CapturedRecovery captured,
		final ReconstructionOperation reconstructionOperation) {
		CapturedRecovery checked = Objects.requireNonNull(
			captured, "captured");
		ReconstructionOperation operation = Objects.requireNonNull(
			reconstructionOperation, "reconstructionOperation");
		if (!checked.isCaptured()) {
			return LifecycleExecution.refused(
				LifecycleReason.CAPTURE_REFUSED, checked.getCaptureReason(),
				false, false, null);
		}
		if (!checked.claimExecution()) {
			return LifecycleExecution.refused(
				LifecycleReason.CAPTURE_ALREADY_CONSUMED,
				checked.getCaptureReason(), false, false, null);
		}
		ReconstructionExecution reconstruction = Objects.requireNonNull(
			operation.reconstruct(ReconstructionBoundary.open(
				checked.getSchedulerInstanceIdentity(),
				checked.getProposalGeneration())),
			"reconstructionExecution");
		if (!checked.getSchedulerInstanceIdentity().equals(
				reconstruction.getSchedulerInstanceIdentity())
			|| checked.getProposalGeneration()
				!= reconstruction.getProposalGeneration()
			|| !reconstruction.isFirstVisibilityWithheld()) {
			return LifecycleExecution.refused(
				LifecycleReason.RECONSTRUCTION_CORRELATION_REFUSED,
				checked.getCaptureReason(), true, false, reconstruction);
		}
		if (!reconstruction.isCompleted()) {
			return LifecycleExecution.refused(
				LifecycleReason.RECONSTRUCTION_REFUSED,
				checked.getCaptureReason(), true, false, reconstruction);
		}
		GameTickEventRestorationRecoveryBatchExecutor.BatchExecution recovery =
			batchExecutor.execute(
				checked.getPreparation(), checked.getFutureSnapshots(),
				checked.getMaximumCandidates());
		if (recovery.isContractuallyReadyForFirstVisibility()) {
			return LifecycleExecution.completed(
				LifecycleReason.CONTRACTUALLY_READY_FOR_FIRST_VISIBILITY,
				checked.getCaptureReason(), reconstruction, recovery);
		}
		if (recovery.requiresFreshInventoryRetry()) {
			return LifecycleExecution.completed(
				LifecycleReason.RECOVERY_REFUSED_REQUIRES_FRESH_INVENTORY,
				checked.getCaptureReason(), reconstruction, recovery);
		}
		return LifecycleExecution.completed(
			LifecycleReason.RECOVERY_EXECUTION_INVALID,
			checked.getCaptureReason(), reconstruction, recovery);
	}

	private static boolean snapshotMatches(
		final Preparation preparation,
		final Directive directive,
		final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot,
		final long proposalGeneration) {
		return preparation.getSchedulerInstanceIdentity().equals(
				snapshot.getSchedulerInstanceIdentity())
			&& directive.getRegistrationSequence()
				== snapshot.getRegistrationSequence()
			&& directive.getProposalGeneration() == proposalGeneration
			&& snapshot.getProposalGeneration() == proposalGeneration
			&& directive.getLifecycleVersion()
				== snapshot.getLifecycleVersion()
			&& directive.getTicksBeforeRun() == snapshot.getTicksBeforeRun()
			&& snapshot.getTicksBeforeRun() > 0L
			&& snapshot.isFutureCallback()
			&& snapshot.isCallbackRetainedScheduled();
	}

	enum CaptureReason {
		CAPTURED_BEFORE_RECONSTRUCTION,
		PREPARATION_REFUSED,
		CANDIDATE_BOUND_EXCEEDED,
		DUPLICATE_FUTURE_SNAPSHOT,
		PROPOSAL_GENERATION_MISMATCH,
		FUTURE_SNAPSHOT_SET_MISMATCH
	}

	enum LifecycleReason {
		CAPTURE_REFUSED,
		CAPTURE_ALREADY_CONSUMED,
		RECONSTRUCTION_CORRELATION_REFUSED,
		RECONSTRUCTION_REFUSED,
		RECOVERY_REFUSED_REQUIRES_FRESH_INVENTORY,
		RECOVERY_EXECUTION_INVALID,
		CONTRACTUALLY_READY_FOR_FIRST_VISIBILITY
	}

	@FunctionalInterface
	interface ReconstructionOperation {
		ReconstructionExecution reconstruct(ReconstructionBoundary boundary);
	}

	/** Detached, operation-local correlation facts; not a load permit. */
	static final class ReconstructionBoundary {
		private final String schedulerInstanceIdentity;
		private final long proposalGeneration;

		private ReconstructionBoundary(
			final String schedulerInstanceIdentity,
			final long proposalGeneration) {
			this.schedulerInstanceIdentity = schedulerInstanceIdentity;
			this.proposalGeneration = proposalGeneration;
		}

		private static ReconstructionBoundary open(
			final String schedulerInstanceIdentity,
			final long proposalGeneration) {
			return new ReconstructionBoundary(
				schedulerInstanceIdentity, proposalGeneration);
		}

		String getSchedulerInstanceIdentity() {
			return schedulerInstanceIdentity;
		}
		long getProposalGeneration() { return proposalGeneration; }
		boolean isRecoveryCapturedBeforeOperation() { return true; }
		boolean isFirstVisibilityWithheld() { return true; }
		boolean isRegionLoadingPerformed() { return false; }
		boolean isLoadPermit() { return false; }
		boolean isVisibilityReleased() { return false; }
	}

	/** Closed caller result; only completion permits the recovery phase. */
	static final class ReconstructionExecution {
		private final String schedulerInstanceIdentity;
		private final long proposalGeneration;
		private final boolean completed;
		private final boolean firstVisibilityWithheld;

		private ReconstructionExecution(
			final String schedulerInstanceIdentity,
			final long proposalGeneration,
			final boolean completed,
			final boolean firstVisibilityWithheld) {
			if (schedulerInstanceIdentity == null
				|| schedulerInstanceIdentity.isEmpty()
				|| proposalGeneration <= 0L) {
				throw new IllegalArgumentException(
					"Reconstruction result correlation is invalid");
			}
			this.schedulerInstanceIdentity = schedulerInstanceIdentity;
			this.proposalGeneration = proposalGeneration;
			this.completed = completed;
			this.firstVisibilityWithheld = firstVisibilityWithheld;
		}

		static ReconstructionExecution completed(
			final ReconstructionBoundary boundary) {
			ReconstructionBoundary checked = Objects.requireNonNull(
				boundary, "boundary");
			return new ReconstructionExecution(
				checked.getSchedulerInstanceIdentity(),
				checked.getProposalGeneration(), true,
				checked.isFirstVisibilityWithheld());
		}

		static ReconstructionExecution refused(
			final ReconstructionBoundary boundary) {
			ReconstructionBoundary checked = Objects.requireNonNull(
				boundary, "boundary");
			return new ReconstructionExecution(
				checked.getSchedulerInstanceIdentity(),
				checked.getProposalGeneration(), false,
				checked.isFirstVisibilityWithheld());
		}

		static ReconstructionExecution report(
			final String schedulerInstanceIdentity,
			final long proposalGeneration,
			final boolean completed,
			final boolean firstVisibilityWithheld) {
			return new ReconstructionExecution(
				schedulerInstanceIdentity, proposalGeneration,
				completed, firstVisibilityWithheld);
		}

		String getSchedulerInstanceIdentity() {
			return schedulerInstanceIdentity;
		}
		long getProposalGeneration() { return proposalGeneration; }
		boolean isCompleted() { return completed; }
		boolean isFirstVisibilityWithheld() {
			return firstVisibilityWithheld;
		}
	}

	/** Single-use detached recovery inputs captured before reconstruction. */
	static final class CapturedRecovery {
		private final CaptureReason captureReason;
		private final Preparation preparation;
		private final List<
			GameTickEventRestorationCurrentStateRecoverySnapshot> futureSnapshots;
		private final long proposalGeneration;
		private final int maximumCandidates;
		private boolean executionClaimed;

		private CapturedRecovery(
			final CaptureReason captureReason,
			final Preparation preparation,
			final List<GameTickEventRestorationCurrentStateRecoverySnapshot>
				futureSnapshots,
			final long proposalGeneration,
			final int maximumCandidates) {
			this.captureReason = Objects.requireNonNull(
				captureReason, "captureReason");
			this.preparation = preparation;
			this.futureSnapshots = futureSnapshots;
			this.proposalGeneration = proposalGeneration;
			this.maximumCandidates = maximumCandidates;
			boolean captured = captureReason
				== CaptureReason.CAPTURED_BEFORE_RECONSTRUCTION;
			if (captured != (preparation != null)
				|| captured != (futureSnapshots != null)
				|| captured != (proposalGeneration > 0L)
				|| captured != (maximumCandidates > 0)) {
				throw new IllegalArgumentException(
					"Captured reconstruction recovery is inconsistent");
			}
		}

		private static CapturedRecovery refused(final CaptureReason reason) {
			if (reason == CaptureReason.CAPTURED_BEFORE_RECONSTRUCTION) {
				throw new IllegalArgumentException(
					"Captured reason cannot refuse recovery inputs");
			}
			return new CapturedRecovery(reason, null, null, -1L, -1);
		}

		private static CapturedRecovery captured(
			final Preparation preparation,
			final List<GameTickEventRestorationCurrentStateRecoverySnapshot>
				futureSnapshots,
			final long proposalGeneration,
			final int maximumCandidates) {
			return new CapturedRecovery(
				CaptureReason.CAPTURED_BEFORE_RECONSTRUCTION, preparation,
				Collections.unmodifiableList(new ArrayList<
					GameTickEventRestorationCurrentStateRecoverySnapshot>(
						futureSnapshots)),
				proposalGeneration, maximumCandidates);
		}

		private synchronized boolean claimExecution() {
			if (executionClaimed) {
				return false;
			}
			executionClaimed = true;
			return true;
		}

		CaptureReason getCaptureReason() { return captureReason; }
		boolean isCaptured() {
			return captureReason
				== CaptureReason.CAPTURED_BEFORE_RECONSTRUCTION;
		}
		Preparation getPreparation() { return preparation; }
		List<GameTickEventRestorationCurrentStateRecoverySnapshot>
			getFutureSnapshots() {
			return futureSnapshots == null
				? Collections
					.<GameTickEventRestorationCurrentStateRecoverySnapshot>
						emptyList()
				: futureSnapshots;
		}
		String getSchedulerInstanceIdentity() {
			return preparation == null
				? null : preparation.getSchedulerInstanceIdentity();
		}
		long getProposalGeneration() { return proposalGeneration; }
		int getMaximumCandidates() { return maximumCandidates; }
		boolean isExecutionClaimed() { return executionClaimed; }
		boolean isReusable() { return false; }
		boolean isRuntimeHandleRetained() { return false; }
		boolean isVisibilityReleased() { return false; }
	}

	/** Phase result; contractual readiness remains distinct from visibility. */
	static final class LifecycleExecution {
		private final LifecycleReason reason;
		private final CaptureReason captureReason;
		private final boolean reconstructionInvoked;
		private final boolean recoveryInvoked;
		private final ReconstructionExecution reconstruction;
		private final GameTickEventRestorationRecoveryBatchExecutor.BatchExecution
			recovery;

		private LifecycleExecution(
			final LifecycleReason reason,
			final CaptureReason captureReason,
			final boolean reconstructionInvoked,
			final boolean recoveryInvoked,
			final ReconstructionExecution reconstruction,
			final GameTickEventRestorationRecoveryBatchExecutor.BatchExecution
				recovery) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.captureReason = Objects.requireNonNull(
				captureReason, "captureReason");
			this.reconstructionInvoked = reconstructionInvoked;
			this.recoveryInvoked = recoveryInvoked;
			this.reconstruction = reconstruction;
			this.recovery = recovery;
			if (reconstructionInvoked != (reconstruction != null)
				|| recoveryInvoked != (recovery != null)
				|| (recoveryInvoked && !reconstructionInvoked)) {
				throw new IllegalArgumentException(
					"Reconstruction lifecycle result is inconsistent");
			}
		}

		private static LifecycleExecution refused(
			final LifecycleReason reason,
			final CaptureReason captureReason,
			final boolean reconstructionInvoked,
			final boolean recoveryInvoked,
			final ReconstructionExecution reconstruction) {
			return new LifecycleExecution(
				reason, captureReason, reconstructionInvoked, recoveryInvoked,
				reconstruction, null);
		}

		private static LifecycleExecution completed(
			final LifecycleReason reason,
			final CaptureReason captureReason,
			final ReconstructionExecution reconstruction,
			final GameTickEventRestorationRecoveryBatchExecutor.BatchExecution
				recovery) {
			return new LifecycleExecution(
				reason, captureReason, true, true, reconstruction, recovery);
		}

		LifecycleReason getReason() { return reason; }
		CaptureReason getCaptureReason() { return captureReason; }
		boolean isReconstructionInvoked() { return reconstructionInvoked; }
		boolean isRecoveryInvoked() { return recoveryInvoked; }
		boolean isContractuallyReadyForFirstVisibility() {
			return reason
				== LifecycleReason.CONTRACTUALLY_READY_FOR_FIRST_VISIBILITY;
		}
		boolean requiresFreshInventoryRetry() {
			return reason
				== LifecycleReason
					.RECOVERY_REFUSED_REQUIRES_FRESH_INVENTORY;
		}
		int getCompletedRecoveryPrefixCount() {
			return recovery == null ? 0 : recovery.getCompletedPrefixCount();
		}
		boolean isRetryPerformed() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isVisibilityReleased() { return false; }
		boolean isRuntimeHandleRetained() { return false; }
	}
}
