package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot;
import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackExpectation;
import com.openrsc.server.model.world.region.RegionManager;

import java.util.Objects;

/**
 * Composes the scheduler's exact stable-lifecycle fence with RegionManager's
 * non-loading current-scenery capture. This adapter remains package-local and
 * is not connected to arrival or visibility paths.
 */
final class GameTickEventRestorationCurrentStateCaptureCoordinator {
	private final GameTickEventStore store;
	private final RegionManager regionManager;

	GameTickEventRestorationCurrentStateCaptureCoordinator(
		final GameTickEventStore store,
		final RegionManager regionManager) {
		this.store = Objects.requireNonNull(store, "store");
		this.regionManager = Objects.requireNonNull(
			regionManager, "regionManager");
	}

	CaptureExecution capture(
		final String expectedSchedulerInstanceIdentity,
		final long expectedRegistrationSequence,
		final long expectedProposalGeneration) {
		final RegionManager.CurrentStateRecoveryCaptureResult[] region =
			new RegionManager.CurrentStateRecoveryCaptureResult[1];
		GameTickEventStore.RestorationStableLifecycleExecution scheduler =
			store.withValidatedRestorationStableLifecycle(
				expectedSchedulerInstanceIdentity,
				expectedRegistrationSequence,
				expectedProposalGeneration, fence -> {
					CallbackExpectation callback = CallbackExpectation.declare(
						GameTickEventRestorationCurrentStateRecoverySnapshot
							.CallbackKind.valueOf(
								fence.getRestorationKind().name()),
						fence.getSchedulerInstanceIdentity(),
						fence.getRegistrationSequence(),
						fence.getExpectedProposalGeneration(),
						fence.getLifecycleVersion(),
						fence.getTicksBeforeRun(), fence.getTimesRan(), true,
						fence.isOneShotExecution(),
						fence.isContinuingServerTickProgression(),
						fence.getObjectId(), fence.getPermanentObjectId(),
						fence.getX(), fence.getY(), fence.getDirection(),
						fence.getType(), null, 0,
						fence.getObservedAuthoredGeneration(),
						fence.getAuthoredPackedRegionX(),
						fence.getAuthoredPackedRegionY(),
						fence.getAuthoredSourceOrdinal(),
						GameTickEventRestorationCurrentStateRecoverySnapshot
							.AuthoredConstructionKind.valueOf(
								fence.getAuthoredConstructionKind().name()));
					region[0] = Objects.requireNonNull(
						regionManager
							.captureGameTickEventCurrentStateRecoverySnapshot(
								callback,
								fence.isEventExecutionBoundaryHeld(), true),
						"current-state Region capture");
				});
		if (!scheduler.isAccepted()) {
			return CaptureExecution.refused(scheduler);
		}
		if (region[0] == null) {
			throw new IllegalStateException(
				"Accepted stable lifecycle produced no Region capture");
		}
		return CaptureExecution.observed(scheduler, region[0]);
	}

	/** Closed composition result; neither scheduler nor Region handles escape. */
	static final class CaptureExecution {
		private final GameTickEventStore.RestorationRegistrationFenceReason
			schedulerReason;
		private final RegionManager.CurrentStateRecoveryCaptureReason regionReason;
		private final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot;
		private final GameTickEventRestorationCurrentStateRecoverySnapshot.Reason
			snapshotReason;
		private final long lifecycleVersionBeforeOperation;
		private final long lifecycleVersionAfterOperation;
		private final boolean lifecycleBoundaryEntered;
		private final boolean regionCaptureInvoked;

		private CaptureExecution(
			final GameTickEventStore.RestorationRegistrationFenceReason
				schedulerReason,
			final RegionManager.CurrentStateRecoveryCaptureReason regionReason,
			final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot,
			final GameTickEventRestorationCurrentStateRecoverySnapshot.Reason
				snapshotReason,
			final long lifecycleVersionBeforeOperation,
			final long lifecycleVersionAfterOperation,
			final boolean lifecycleBoundaryEntered,
			final boolean regionCaptureInvoked) {
			this.schedulerReason = Objects.requireNonNull(
				schedulerReason, "schedulerReason");
			this.regionReason = regionReason;
			this.snapshot = snapshot;
			this.snapshotReason = snapshotReason;
			this.lifecycleVersionBeforeOperation =
				lifecycleVersionBeforeOperation;
			this.lifecycleVersionAfterOperation =
				lifecycleVersionAfterOperation;
			this.lifecycleBoundaryEntered = lifecycleBoundaryEntered;
			this.regionCaptureInvoked = regionCaptureInvoked;
			boolean accepted = schedulerReason
				== GameTickEventStore.RestorationRegistrationFenceReason
					.OPERATION_COMPLETED;
			if (accepted != (regionReason != null)
				|| (accepted
					&& (!lifecycleBoundaryEntered || !regionCaptureInvoked))
				|| (regionCaptureInvoked && !lifecycleBoundaryEntered)
				|| (snapshot != null) != (regionReason
					== RegionManager.CurrentStateRecoveryCaptureReason
						.SNAPSHOT_AVAILABLE)
				|| (snapshot != null && snapshotReason
					!= GameTickEventRestorationCurrentStateRecoverySnapshot
						.Reason.SNAPSHOT_AVAILABLE)
				|| (!accepted && (snapshot != null || snapshotReason != null))
				|| ((lifecycleVersionBeforeOperation > 0L)
					!= (lifecycleVersionAfterOperation > 0L))
				|| (accepted
					&& (lifecycleVersionBeforeOperation <= 0L
						|| lifecycleVersionBeforeOperation
							!= lifecycleVersionAfterOperation))) {
				throw new IllegalArgumentException(
					"Current-state capture composition is inconsistent");
			}
		}

		private static CaptureExecution refused(
			final GameTickEventStore.RestorationStableLifecycleExecution
				scheduler) {
			return new CaptureExecution(
				scheduler.getReason(), null, null, null,
				scheduler.getLifecycleVersionBeforeOperation(),
				scheduler.getLifecycleVersionAfterOperation(),
				scheduler.isLifecycleBoundaryEntered(),
				scheduler.isOperationInvoked());
		}

		private static CaptureExecution observed(
			final GameTickEventStore.RestorationStableLifecycleExecution scheduler,
			final RegionManager.CurrentStateRecoveryCaptureResult region) {
			return new CaptureExecution(
				scheduler.getReason(), region.getReason(), region.getSnapshot(),
				region.getSnapshotReason(),
				scheduler.getLifecycleVersionBeforeOperation(),
				scheduler.getLifecycleVersionAfterOperation(),
				scheduler.isLifecycleBoundaryEntered(), true);
		}

		GameTickEventStore.RestorationRegistrationFenceReason
			getSchedulerReason() { return schedulerReason; }
		RegionManager.CurrentStateRecoveryCaptureReason getRegionReason() {
			return regionReason;
		}
		GameTickEventRestorationCurrentStateRecoverySnapshot getSnapshot() {
			return snapshot;
		}
		GameTickEventRestorationCurrentStateRecoverySnapshot.Reason
			getSnapshotReason() { return snapshotReason; }
		long getLifecycleVersionBeforeOperation() {
			return lifecycleVersionBeforeOperation;
		}
		long getLifecycleVersionAfterOperation() {
			return lifecycleVersionAfterOperation;
		}
		boolean isSchedulerFenceAccepted() { return regionReason != null; }
		boolean isLifecycleBoundaryEntered() {
			return lifecycleBoundaryEntered;
		}
		boolean isRegionCaptureInvoked() { return regionCaptureInvoked; }
		boolean isSnapshotAvailable() { return snapshot != null; }
		boolean isExactRegistrationRetained() {
			return isSchedulerFenceAccepted();
		}
		boolean isCountdownRetained() { return isSchedulerFenceAccepted(); }
		boolean isRegionManagerHandleRetained() { return false; }
		boolean isEventHandleRetained() { return false; }
		boolean isRegistrationHandleRetained() { return false; }
		boolean isMutationPerformed() { return false; }
		boolean isCallbackInvoked() { return false; }
		boolean isEventCancellation() { return false; }
		boolean isEventReschedule() { return false; }
		boolean isRegionLoadingPerformed() { return false; }
		boolean isExecutableRestoration() { return false; }
		boolean isCommitToken() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isVisibilityReleased() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}
}
