package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.Directive;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.OperationKind;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.OperationOutcome;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.OperationResult;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.Preparation;
import com.openrsc.server.model.world.region.RegionManager;

import java.util.Objects;

/** Executes one already-prepared recovery directive; it is not a batch loop. */
final class GameTickEventRestorationRecoveryDirectiveExecutor {
	private final GameTickEventStore store;
	private final RegionManager regionManager;
	private final GameTickEventRestorationFutureStateApplicationCoordinator
		futureApplication;

	GameTickEventRestorationRecoveryDirectiveExecutor(
		final GameTickEventStore store,
		final RegionManager regionManager) {
		this.store = Objects.requireNonNull(store, "store");
		this.regionManager = Objects.requireNonNull(
			regionManager, "regionManager");
		this.futureApplication =
			new GameTickEventRestorationFutureStateApplicationCoordinator(
				this.store, this.regionManager);
	}

	DirectiveExecution execute(
		final Preparation preparation,
		final int directiveIndex,
		final GameTickEventRestorationCurrentStateRecoverySnapshot
			futureSnapshot) {
		Preparation checked = Objects.requireNonNull(
			preparation, "preparation");
		if (!checked.isReady()) {
			return DirectiveExecution.invalid(Reason.PREPARATION_REFUSED);
		}
		if (directiveIndex < 0
			|| directiveIndex >= checked.getDirectives().size()) {
			return DirectiveExecution.invalid(Reason.DIRECTIVE_INDEX_INVALID);
		}
		Directive directive = checked.getDirectives().get(directiveIndex);
		if (directive.getIndex() != directiveIndex) {
			return DirectiveExecution.invalid(Reason.DIRECTIVE_ORDER_MISMATCH);
		}
		switch (directive.getOperationKind()) {
			case DESIRED_STATE_COMMIT_AND_EVENT_CONSUME:
				if (futureSnapshot != null
					|| directive.isFutureSnapshotCorrelated()) {
					return DirectiveExecution.refused(
						directive, Reason.DIRECTIVE_INPUT_MISMATCH);
				}
				return executeOverdue(checked, directive);
			case CURRENT_STATE_RESTORE_AND_EVENT_RETAIN:
				if (futureSnapshot == null
					|| !directive.isFutureSnapshotCorrelated()
					|| !futureSnapshotMatches(
						checked, directive, futureSnapshot)) {
					return DirectiveExecution.refused(
						directive, Reason.DIRECTIVE_INPUT_MISMATCH);
				}
				return executeFuture(directive, futureSnapshot);
			default:
				return DirectiveExecution.refused(
					directive, Reason.DIRECTIVE_INPUT_MISMATCH);
		}
	}

	private DirectiveExecution executeOverdue(
		final Preparation preparation,
		final Directive directive) {
		GameTickEventStore.RestorationRegionCommitConsumptionExecution result =
			store.withValidatedRestorationRegionCommitConsumption(
				regionManager, preparation.getSchedulerInstanceIdentity(),
				directive.getRegistrationSequence(),
				directive.getProposalGeneration(),
				directive.getLifecycleVersion(),
				directive.getTicksBeforeRun());
		OperationOutcome outcome = result.getRegionOutcome()
			== RegionManager.RestorationCommitOutcome.APPLIED
				? OperationOutcome.DESIRED_STATE_APPLIED_AND_EVENT_CONSUMED
				: result.getRegionOutcome()
					== RegionManager.RestorationCommitOutcome.NO_OP
						? OperationOutcome
							.DESIRED_STATE_NO_OP_AND_EVENT_CONSUMED
						: OperationOutcome.REFUSED;
		return DirectiveExecution.overdue(directive, outcome, result);
	}

	private DirectiveExecution executeFuture(
		final Directive directive,
		final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
		GameTickEventRestorationFutureStateApplicationCoordinator
			.ApplicationExecution result = futureApplication.apply(snapshot);
		OperationOutcome outcome = result.isCurrentStateRestored()
			|| result.isCurrentStateAlreadySatisfied()
				? OperationOutcome.CURRENT_STATE_RESTORED_AND_EVENT_RETAINED
				: OperationOutcome.REFUSED;
		return DirectiveExecution.future(directive, outcome, result);
	}

	private static boolean futureSnapshotMatches(
		final Preparation preparation,
		final Directive directive,
		final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
		return preparation.getSchedulerInstanceIdentity().equals(
				snapshot.getSchedulerInstanceIdentity())
			&& directive.getRegistrationSequence()
				== snapshot.getRegistrationSequence()
			&& directive.getProposalGeneration()
				== snapshot.getProposalGeneration()
			&& directive.getLifecycleVersion() == snapshot.getLifecycleVersion()
			&& directive.getTicksBeforeRun() == snapshot.getTicksBeforeRun()
			&& snapshot.getTicksBeforeRun() > 0L
			&& snapshot.isFutureCallback()
			&& snapshot.isCallbackRetainedScheduled();
	}

	enum Reason {
		PREPARATION_REFUSED,
		DIRECTIVE_INDEX_INVALID,
		DIRECTIVE_ORDER_MISMATCH,
		DIRECTIVE_INPUT_MISMATCH,
		OVERDUE_OPERATION_COMPLETED,
		FUTURE_OPERATION_COMPLETED
	}

	/** Closed one-directive result; Slice 145 still performs batch reduction. */
	static final class DirectiveExecution {
		private final Reason reason;
		private final int directiveIndex;
		private final long registrationSequence;
		private final OperationKind operationKind;
		private final OperationResult operationResult;
		private final boolean runtimeOperationInvoked;
		private final boolean eventTerminallyConsumed;
		private final boolean eventRetainedScheduled;
		private final boolean regionMutationPerformed;

		private DirectiveExecution(
			final Reason reason,
			final int directiveIndex,
			final long registrationSequence,
			final OperationKind operationKind,
			final OperationResult operationResult,
			final boolean runtimeOperationInvoked,
			final boolean eventTerminallyConsumed,
			final boolean eventRetainedScheduled,
			final boolean regionMutationPerformed) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.directiveIndex = directiveIndex;
			this.registrationSequence = registrationSequence;
			this.operationKind = operationKind;
			this.operationResult = operationResult;
			this.runtimeOperationInvoked = runtimeOperationInvoked;
			this.eventTerminallyConsumed = eventTerminallyConsumed;
			this.eventRetainedScheduled = eventRetainedScheduled;
			this.regionMutationPerformed = regionMutationPerformed;
			boolean hasDirective = directiveIndex >= 0;
			boolean hasResult = operationResult != null;
			if (hasDirective != (registrationSequence > 0L)
				|| hasDirective != (operationKind != null)
				|| hasDirective != hasResult
				|| eventTerminallyConsumed && eventRetainedScheduled
				|| (!runtimeOperationInvoked
					&& (eventTerminallyConsumed
						|| regionMutationPerformed))) {
				throw new IllegalArgumentException(
					"Recovery directive execution is inconsistent");
			}
		}

		private static DirectiveExecution invalid(final Reason reason) {
			return new DirectiveExecution(
				reason, -1, -1L, null, null,
				false, false, false, false);
		}

		private static DirectiveExecution refused(
			final Directive directive,
			final Reason reason) {
			return completed(
				directive, reason, OperationOutcome.REFUSED,
				false, false, false, false);
		}

		private static DirectiveExecution overdue(
			final Directive directive,
			final OperationOutcome outcome,
			final GameTickEventStore
				.RestorationRegionCommitConsumptionExecution result) {
			return completed(
				directive, Reason.OVERDUE_OPERATION_COMPLETED, outcome,
				result.isRegionCommitInvoked(),
				result.isEventTerminallyConsumed(),
				result.isExactRegistrationRetained(),
				result.isMutationPerformed());
		}

		private static DirectiveExecution future(
			final Directive directive,
			final OperationOutcome outcome,
			final GameTickEventRestorationFutureStateApplicationCoordinator
				.ApplicationExecution result) {
			return completed(
				directive, Reason.FUTURE_OPERATION_COMPLETED, outcome,
				result.isRegionApplicationInvoked(), false,
				result.isExactRegistrationRetained(),
				result.isMutationPerformed());
		}

		private static DirectiveExecution completed(
			final Directive directive,
			final Reason reason,
			final OperationOutcome outcome,
			final boolean runtimeOperationInvoked,
			final boolean eventTerminallyConsumed,
			final boolean eventRetainedScheduled,
			final boolean regionMutationPerformed) {
			return new DirectiveExecution(
				reason, directive.getIndex(),
				directive.getRegistrationSequence(),
				directive.getOperationKind(),
				GameTickEventRestorationRecoveryCoordinatorContract
					.OperationResult.report(
						directive.getRegistrationSequence(),
						directive.getOperationKind(), outcome),
				runtimeOperationInvoked, eventTerminallyConsumed,
				eventRetainedScheduled, regionMutationPerformed);
		}

		Reason getReason() { return reason; }
		int getDirectiveIndex() { return directiveIndex; }
		long getRegistrationSequence() { return registrationSequence; }
		OperationKind getOperationKind() { return operationKind; }
		OperationResult getOperationResult() { return operationResult; }
		boolean isRuntimeOperationInvoked() { return runtimeOperationInvoked; }
		boolean isEventTerminallyConsumed() {
			return eventTerminallyConsumed;
		}
		boolean isEventRetainedScheduled() { return eventRetainedScheduled; }
		boolean isRegionMutationPerformed() { return regionMutationPerformed; }
		boolean isBatchLoop() { return false; }
		boolean isRetryPerformed() { return false; }
		boolean isRegionLoadingPerformed() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isVisibilityReleased() { return false; }
		boolean isRuntimeHandleRetained() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}
}
