package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot;
import com.openrsc.server.model.world.region.RegionManager;

import java.util.Objects;

/**
 * Applies one previously captured future-callback current-state snapshot while
 * the exact callback registration and stable lifecycle remain held. The future
 * callback is retained and continues from its existing countdown.
 */
final class GameTickEventRestorationFutureStateApplicationCoordinator {
	private final GameTickEventStore store;
	private final RegionManager regionManager;

	GameTickEventRestorationFutureStateApplicationCoordinator(
		final GameTickEventStore store,
		final RegionManager regionManager) {
		this.store = Objects.requireNonNull(store, "store");
		this.regionManager = Objects.requireNonNull(
			regionManager, "regionManager");
	}

	ApplicationExecution apply(
		final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
		final GameTickEventRestorationCurrentStateRecoverySnapshot checked =
			Objects.requireNonNull(snapshot, "snapshot");
		final boolean[] correlationMatched = new boolean[1];
		final RegionManager.CurrentStateRecoveryApplicationResult[] application =
			new RegionManager.CurrentStateRecoveryApplicationResult[1];
		GameTickEventStore.RestorationStableLifecycleExecution scheduler =
			store.withValidatedRestorationStableLifecycle(
				checked.getSchedulerInstanceIdentity(),
				checked.getRegistrationSequence(),
				checked.getProposalGeneration(), fence -> {
					if (!snapshotMatchesFence(checked, fence)) {
						return;
					}
					correlationMatched[0] = true;
					application[0] = Objects.requireNonNull(
						regionManager
							.applyGameTickEventCurrentStateRecoverySnapshot(
								checked),
						"current-state application result");
				});
		if (!scheduler.isAccepted()) {
			return ApplicationExecution.schedulerRefused(scheduler);
		}
		if (!correlationMatched[0]) {
			return ApplicationExecution.correlationRefused(scheduler);
		}
		if (application[0] == null) {
			throw new IllegalStateException(
				"Correlated future snapshot produced no Region application");
		}
		return ApplicationExecution.completed(scheduler, application[0]);
	}

	private static boolean snapshotMatchesFence(
		final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot,
		final GameTickEventStore.RestorationRegistrationFence fence) {
		return snapshot.getSchedulerInstanceIdentity().equals(
				fence.getSchedulerInstanceIdentity())
			&& snapshot.getRegistrationSequence()
				== fence.getRegistrationSequence()
			&& snapshot.getProposalGeneration()
				== fence.getExpectedProposalGeneration()
			&& snapshot.getAuthoredGeneration()
				== fence.getObservedAuthoredGeneration()
			&& snapshot.getLifecycleVersion() == fence.getLifecycleVersion()
			&& snapshot.getTicksBeforeRun() == fence.getTicksBeforeRun()
			&& snapshot.getTicksBeforeRun() > 0L
			&& snapshot.getCallbackKind().name().equals(
				fence.getRestorationKind().name())
			&& snapshot.getCallbackObjectId() == fence.getObjectId()
			&& snapshot.getCallbackPermanentObjectId()
				== fence.getPermanentObjectId()
			&& snapshot.getX() == fence.getX()
			&& snapshot.getY() == fence.getY()
			&& snapshot.getDirection() == fence.getDirection()
			&& snapshot.getType() == fence.getType()
			&& snapshot.getAuthoredPackedRegionX()
				== fence.getAuthoredPackedRegionX()
			&& snapshot.getAuthoredPackedRegionY()
				== fence.getAuthoredPackedRegionY()
			&& snapshot.getAuthoredSourceOrdinal()
				== fence.getAuthoredSourceOrdinal()
			&& snapshot.getAuthoredConstructionKind().name().equals(
				fence.getAuthoredConstructionKind().name())
			&& fence.isOneShotExecution()
			&& fence.isContinuingServerTickProgression()
			&& snapshot.isFutureCallback()
			&& snapshot.isCallbackRetainedScheduled();
	}

	enum ApplicationReason {
		SCHEDULER_FENCE_REFUSED,
		SNAPSHOT_CORRELATION_MISMATCH,
		REGION_APPLICATION_REFUSED,
		CURRENT_STATE_ALREADY_SATISFIED_AND_EVENT_RETAINED,
		CURRENT_STATE_RESTORED_AND_EVENT_RETAINED
	}

	/** Closed application result with no reusable runtime authority. */
	static final class ApplicationExecution {
		private final ApplicationReason reason;
		private final GameTickEventStore.RestorationRegistrationFenceReason
			schedulerReason;
		private final RegionManager.CurrentStateRecoveryApplicationOutcome
			regionOutcome;
		private final RegionManager.CurrentStateRecoveryApplicationReason
			regionReason;
		private final long lifecycleVersionBeforeOperation;
		private final long lifecycleVersionAfterOperation;
		private final boolean lifecycleBoundaryEntered;
		private final boolean regionApplicationInvoked;
		private final boolean membershipRegistered;
		private final boolean forceFullBlockProjectionSelected;
		private final int boundaryCount;

		private ApplicationExecution(
			final ApplicationReason reason,
			final GameTickEventStore.RestorationRegistrationFenceReason
				schedulerReason,
			final RegionManager.CurrentStateRecoveryApplicationOutcome
				regionOutcome,
			final RegionManager.CurrentStateRecoveryApplicationReason
				regionReason,
			final long lifecycleVersionBeforeOperation,
			final long lifecycleVersionAfterOperation,
			final boolean lifecycleBoundaryEntered,
			final boolean regionApplicationInvoked,
			final boolean membershipRegistered,
			final boolean forceFullBlockProjectionSelected,
			final int boundaryCount) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.schedulerReason = Objects.requireNonNull(
				schedulerReason, "schedulerReason");
			this.regionOutcome = regionOutcome;
			this.regionReason = regionReason;
			this.lifecycleVersionBeforeOperation =
				lifecycleVersionBeforeOperation;
			this.lifecycleVersionAfterOperation =
				lifecycleVersionAfterOperation;
			this.lifecycleBoundaryEntered = lifecycleBoundaryEntered;
			this.regionApplicationInvoked = regionApplicationInvoked;
			this.membershipRegistered = membershipRegistered;
			this.forceFullBlockProjectionSelected =
				forceFullBlockProjectionSelected;
			this.boundaryCount = boundaryCount;
			boolean schedulerAccepted = schedulerReason
				== GameTickEventStore.RestorationRegistrationFenceReason
					.OPERATION_COMPLETED;
			boolean regionInvoked = regionOutcome != null;
			boolean applied = regionOutcome
				== RegionManager.CurrentStateRecoveryApplicationOutcome.APPLIED;
			boolean noOp = regionOutcome
				== RegionManager.CurrentStateRecoveryApplicationOutcome.NO_OP;
			if (regionInvoked != (regionReason != null)
				|| regionInvoked != regionApplicationInvoked
				|| (regionApplicationInvoked && !lifecycleBoundaryEntered)
				|| (regionInvoked && !schedulerAccepted)
				|| boundaryCount < 0
				|| membershipRegistered != applied
				|| (!regionInvoked
					&& (membershipRegistered
						|| forceFullBlockProjectionSelected
						|| boundaryCount != 0))
				|| (reason
					== ApplicationReason.CURRENT_STATE_RESTORED_AND_EVENT_RETAINED
					!= applied)
				|| (reason
					== ApplicationReason
						.CURRENT_STATE_ALREADY_SATISFIED_AND_EVENT_RETAINED
					!= noOp)
				|| ((lifecycleVersionBeforeOperation > 0L)
					!= (lifecycleVersionAfterOperation > 0L))) {
				throw new IllegalArgumentException(
					"Future-state application result is inconsistent");
			}
		}

		private static ApplicationExecution schedulerRefused(
			final GameTickEventStore.RestorationStableLifecycleExecution
				scheduler) {
			return empty(
				ApplicationReason.SCHEDULER_FENCE_REFUSED, scheduler);
		}

		private static ApplicationExecution correlationRefused(
			final GameTickEventStore.RestorationStableLifecycleExecution
				scheduler) {
			return empty(
				ApplicationReason.SNAPSHOT_CORRELATION_MISMATCH, scheduler);
		}

		private static ApplicationExecution empty(
			final ApplicationReason reason,
			final GameTickEventStore.RestorationStableLifecycleExecution
				scheduler) {
			return new ApplicationExecution(
				reason, scheduler.getReason(), null, null,
				scheduler.getLifecycleVersionBeforeOperation(),
				scheduler.getLifecycleVersionAfterOperation(),
				scheduler.isLifecycleBoundaryEntered(), false,
				false, false, 0);
		}

		private static ApplicationExecution completed(
			final GameTickEventStore.RestorationStableLifecycleExecution scheduler,
			final RegionManager.CurrentStateRecoveryApplicationResult region) {
			ApplicationReason reason = region.isApplied()
				? ApplicationReason
					.CURRENT_STATE_RESTORED_AND_EVENT_RETAINED
				: region.isNoOp()
					? ApplicationReason
						.CURRENT_STATE_ALREADY_SATISFIED_AND_EVENT_RETAINED
					: ApplicationReason.REGION_APPLICATION_REFUSED;
			return new ApplicationExecution(
				reason, scheduler.getReason(), region.getOutcome(),
				region.getReason(),
				scheduler.getLifecycleVersionBeforeOperation(),
				scheduler.getLifecycleVersionAfterOperation(),
				scheduler.isLifecycleBoundaryEntered(), true,
				region.isMembershipRegistered(),
				region.isForceFullBlockProjectionSelected(),
				region.getBoundaryCount());
		}

		ApplicationReason getReason() { return reason; }
		GameTickEventStore.RestorationRegistrationFenceReason
			getSchedulerReason() { return schedulerReason; }
		RegionManager.CurrentStateRecoveryApplicationOutcome getRegionOutcome() {
			return regionOutcome;
		}
		RegionManager.CurrentStateRecoveryApplicationReason getRegionReason() {
			return regionReason;
		}
		long getLifecycleVersionBeforeOperation() {
			return lifecycleVersionBeforeOperation;
		}
		long getLifecycleVersionAfterOperation() {
			return lifecycleVersionAfterOperation;
		}
		boolean isLifecycleBoundaryEntered() {
			return lifecycleBoundaryEntered;
		}
		boolean isRegionApplicationInvoked() {
			return regionApplicationInvoked;
		}
		boolean isCurrentStateRestored() {
			return reason
				== ApplicationReason
					.CURRENT_STATE_RESTORED_AND_EVENT_RETAINED;
		}
		boolean isCurrentStateAlreadySatisfied() {
			return reason
				== ApplicationReason
					.CURRENT_STATE_ALREADY_SATISFIED_AND_EVENT_RETAINED;
		}
		boolean isRefused() {
			return !isCurrentStateRestored()
				&& !isCurrentStateAlreadySatisfied();
		}
		boolean isMembershipRegistered() { return membershipRegistered; }
		boolean isForceFullBlockProjectionSelected() {
			return forceFullBlockProjectionSelected;
		}
		int getBoundaryCount() { return boundaryCount; }
		boolean isExactRegistrationRetained() {
			return schedulerReason
				== GameTickEventStore.RestorationRegistrationFenceReason
					.OPERATION_COMPLETED;
		}
		boolean isCountdownRetained() { return isExactRegistrationRetained(); }
		boolean isMutationPerformed() { return isCurrentStateRestored(); }
		boolean isRuntimeHandleRetained() { return false; }
		boolean isSnapshotRetained() { return false; }
		boolean isRegionResultRetained() { return false; }
		boolean isCallbackInvoked() { return false; }
		boolean isEventCancellation() { return false; }
		boolean isEventReschedule() { return false; }
		boolean isRegionLoadingPerformed() { return false; }
		boolean isExecutableRestoration() {
			return regionApplicationInvoked;
		}
		boolean isCommitToken() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isVisibilityReleased() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}
}
