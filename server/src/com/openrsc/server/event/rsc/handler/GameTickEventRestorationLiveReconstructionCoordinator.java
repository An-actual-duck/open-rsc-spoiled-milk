package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.handler.GameTickEventRestorationReconstructionLifecycleCoordinator.LifecycleExecution;
import com.openrsc.server.event.rsc.handler.GameTickEventRestorationReconstructionLifecycleCoordinator.ReconstructionOperation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.region.RegionManager;

import java.util.Objects;

/**
 * Exact package-local composition of live recovery preparation and the
 * single-use reconstruction lifecycle. It adds no reconstruction operation of
 * its own and exposes no arrival or visibility path.
 */
final class GameTickEventRestorationLiveReconstructionCoordinator {
	private final GameTickEventRestorationLivePreparationCoordinator preparation;
	private final GameTickEventRestorationReconstructionLifecycleCoordinator
		lifecycle;

	GameTickEventRestorationLiveReconstructionCoordinator(
		final GameTickEventStore store,
		final RegionManager regionManager) {
		GameTickEventStore checkedStore = Objects.requireNonNull(store, "store");
		RegionManager checkedRegions = Objects.requireNonNull(
			regionManager, "regionManager");
		this.preparation =
			new GameTickEventRestorationLivePreparationCoordinator(
				checkedStore, checkedRegions);
		this.lifecycle =
			new GameTickEventRestorationReconstructionLifecycleCoordinator(
				checkedStore, checkedRegions);
	}

	LiveCapturedRecovery captureBeforeReconstruction(
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final int maximumCandidates) {
		GameTickEventRestorationLivePreparationCoordinator.PreparationCapture
			live = preparation.capture(
				Objects.requireNonNull(inventory, "inventory"),
				maximumCandidates);
		if (!live.isReady()) {
			return LiveCapturedRecovery.refused(live);
		}
		GameTickEventRestorationReconstructionLifecycleCoordinator.CapturedRecovery
			captured = lifecycle.captureBeforeReconstruction(
				live.getPreparation(), live.getFutureSnapshots(),
				live.getProposalGeneration(), live.getMaximumCandidates());
		if (!captured.isCaptured()) {
			return LiveCapturedRecovery.lifecycleRefused(live, captured);
		}
		return LiveCapturedRecovery.captured(live, captured);
	}

	LiveLifecycleExecution reconstructThenRecover(
		final LiveCapturedRecovery captured,
		final ReconstructionOperation reconstructionOperation) {
		return reconstructThenRecover(
			captured, reconstructionOperation, true);
	}

	LiveLifecycleExecution verifyAfterNoOpReconstruction(
		final LiveCapturedRecovery captured,
		final ReconstructionOperation reconstructionOperation) {
		return reconstructThenRecover(
			captured, reconstructionOperation, false);
	}

	private LiveLifecycleExecution reconstructThenRecover(
		final LiveCapturedRecovery captured,
		final ReconstructionOperation reconstructionOperation,
		final boolean mutationAllowed) {
		LiveCapturedRecovery checked = Objects.requireNonNull(
			captured, "captured");
		ReconstructionOperation operation = Objects.requireNonNull(
			reconstructionOperation, "reconstructionOperation");
		if (!checked.isCaptured()) {
			return LiveLifecycleExecution.captureRefused(checked);
		}
		LifecycleExecution execution = mutationAllowed
			? lifecycle.reconstructThenRecover(
				checked.getCapturedRecovery(), operation)
			: lifecycle.verifyAfterNoOpReconstruction(
				checked.getCapturedRecovery(), operation);
		return LiveLifecycleExecution.executed(checked, execution);
	}

	enum CaptureReason {
		LIVE_PREPARATION_REFUSED,
		LIFECYCLE_CAPTURE_REFUSED,
		CAPTURED_BEFORE_RECONSTRUCTION
	}

	enum ExecutionReason {
		LIVE_CAPTURE_REFUSED,
		RECONSTRUCTION_LIFECYCLE_EXECUTED
	}

	/** Closed exact composition; callers cannot substitute captured inputs. */
	static final class LiveCapturedRecovery {
		private final CaptureReason reason;
		private final GameTickEventRestorationLivePreparationCoordinator.Reason
			preparationReason;
		private final GameTickEventRestorationReconstructionLifecycleCoordinator
			.CaptureReason lifecycleCaptureReason;
		private final GameTickEventRestorationReconstructionLifecycleCoordinator
			.CapturedRecovery capturedRecovery;
		private final int inventoryEventCount;
		private final int recoveryCandidateCount;
		private final int futureSnapshotCount;
		private final long proposalGeneration;

		private LiveCapturedRecovery(
			final CaptureReason reason,
			final GameTickEventRestorationLivePreparationCoordinator.Reason
				preparationReason,
			final GameTickEventRestorationReconstructionLifecycleCoordinator
				.CaptureReason lifecycleCaptureReason,
			final GameTickEventRestorationReconstructionLifecycleCoordinator
				.CapturedRecovery capturedRecovery,
			final int inventoryEventCount,
			final int recoveryCandidateCount,
			final int futureSnapshotCount,
			final long proposalGeneration) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.preparationReason = Objects.requireNonNull(
				preparationReason, "preparationReason");
			this.lifecycleCaptureReason = lifecycleCaptureReason;
			this.capturedRecovery = capturedRecovery;
			this.inventoryEventCount = inventoryEventCount;
			this.recoveryCandidateCount = recoveryCandidateCount;
			this.futureSnapshotCount = futureSnapshotCount;
			this.proposalGeneration = proposalGeneration;
			boolean captured = reason == CaptureReason
				.CAPTURED_BEFORE_RECONSTRUCTION;
			if (captured != (capturedRecovery != null)
				|| captured != (proposalGeneration > 0L)
				|| captured != (inventoryEventCount >= 0)
				|| captured != (futureSnapshotCount >= 0)
				|| recoveryCandidateCount < 0
				|| ((reason != CaptureReason.LIVE_PREPARATION_REFUSED)
					!= (lifecycleCaptureReason != null))) {
				throw new IllegalArgumentException(
					"Live captured recovery is inconsistent");
			}
		}

		private static LiveCapturedRecovery refused(
			final GameTickEventRestorationLivePreparationCoordinator
				.PreparationCapture live) {
			return new LiveCapturedRecovery(
				CaptureReason.LIVE_PREPARATION_REFUSED, live.getReason(),
				null, null, -1, live.getRecoveryCandidateCount(), -1, -1L);
		}

		private static LiveCapturedRecovery lifecycleRefused(
			final GameTickEventRestorationLivePreparationCoordinator
				.PreparationCapture live,
			final GameTickEventRestorationReconstructionLifecycleCoordinator
				.CapturedRecovery captured) {
			return new LiveCapturedRecovery(
				CaptureReason.LIFECYCLE_CAPTURE_REFUSED, live.getReason(),
				captured.getCaptureReason(), null, -1,
				live.getRecoveryCandidateCount(), -1, -1L);
		}

		private static LiveCapturedRecovery captured(
			final GameTickEventRestorationLivePreparationCoordinator
				.PreparationCapture live,
			final GameTickEventRestorationReconstructionLifecycleCoordinator
				.CapturedRecovery captured) {
			return new LiveCapturedRecovery(
				CaptureReason.CAPTURED_BEFORE_RECONSTRUCTION, live.getReason(),
				captured.getCaptureReason(), captured,
				live.getInventoryEventCount(),
				live.getRecoveryCandidateCount(),
				live.getFutureSnapshots().size(),
				live.getProposalGeneration());
		}

		CaptureReason getReason() { return reason; }
		GameTickEventRestorationLivePreparationCoordinator.Reason
			getPreparationReason() { return preparationReason; }
		GameTickEventRestorationReconstructionLifecycleCoordinator.CaptureReason
			getLifecycleCaptureReason() { return lifecycleCaptureReason; }
		boolean isCaptured() {
			return reason == CaptureReason.CAPTURED_BEFORE_RECONSTRUCTION;
		}
		private GameTickEventRestorationReconstructionLifecycleCoordinator
			.CapturedRecovery getCapturedRecovery() {
			return capturedRecovery;
		}
		int getInventoryEventCount() { return inventoryEventCount; }
		int getRecoveryCandidateCount() { return recoveryCandidateCount; }
		int getFutureSnapshotCount() { return futureSnapshotCount; }
		long getProposalGeneration() { return proposalGeneration; }
		boolean isInputSubstitutionAvailable() { return false; }
		boolean isRuntimeHandleRetained() { return false; }
		boolean isReconstructionInvoked() { return false; }
		boolean isVisibilityReleased() { return false; }
	}

	/** Facade result retaining only the closed Slice 154 lifecycle result. */
	static final class LiveLifecycleExecution {
		private final ExecutionReason reason;
		private final CaptureReason captureReason;
		private final LifecycleExecution lifecycleExecution;

		private LiveLifecycleExecution(
			final ExecutionReason reason,
			final CaptureReason captureReason,
			final LifecycleExecution lifecycleExecution) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.captureReason = Objects.requireNonNull(
				captureReason, "captureReason");
			this.lifecycleExecution = lifecycleExecution;
			if ((reason == ExecutionReason.RECONSTRUCTION_LIFECYCLE_EXECUTED)
				!= (lifecycleExecution != null)) {
				throw new IllegalArgumentException(
					"Live reconstruction execution is inconsistent");
			}
		}

		private static LiveLifecycleExecution captureRefused(
			final LiveCapturedRecovery captured) {
			return new LiveLifecycleExecution(
				ExecutionReason.LIVE_CAPTURE_REFUSED,
				captured.getReason(), null);
		}

		private static LiveLifecycleExecution executed(
			final LiveCapturedRecovery captured,
			final LifecycleExecution execution) {
			return new LiveLifecycleExecution(
				ExecutionReason.RECONSTRUCTION_LIFECYCLE_EXECUTED,
				captured.getReason(),
				Objects.requireNonNull(execution, "lifecycleExecution"));
		}

		ExecutionReason getReason() { return reason; }
		CaptureReason getCaptureReason() { return captureReason; }
		GameTickEventRestorationReconstructionLifecycleCoordinator
			.LifecycleReason getLifecycleReason() {
			return lifecycleExecution == null
				? null : lifecycleExecution.getReason();
		}
		boolean isReconstructionInvoked() {
			return lifecycleExecution != null
				&& lifecycleExecution.isReconstructionInvoked();
		}
		boolean isRecoveryInvoked() {
			return lifecycleExecution != null
				&& lifecycleExecution.isRecoveryInvoked();
		}
		boolean isContractuallyReadyForFirstVisibility() {
			return lifecycleExecution != null
				&& lifecycleExecution
					.isContractuallyReadyForFirstVisibility();
		}
		boolean requiresFreshInventoryRetry() {
			return lifecycleExecution != null
				&& lifecycleExecution.requiresFreshInventoryRetry();
		}
		int getRuntimeOperationCount() {
			return lifecycleExecution == null
				? 0 : lifecycleExecution.getRuntimeOperationCount();
		}
		int getMutationOperationCount() {
			return lifecycleExecution == null
				? 0 : lifecycleExecution.getMutationOperationCount();
		}
		int getTerminalEventConsumptionCount() {
			return lifecycleExecution == null
				? 0 : lifecycleExecution.getTerminalEventConsumptionCount();
		}
		boolean isMutationAllowed() {
			return lifecycleExecution != null
				&& lifecycleExecution.isMutationAllowed();
		}
		boolean isRetryPerformed() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isVisibilityReleased() { return false; }
		boolean isRuntimeHandleRetained() { return false; }
	}
}
