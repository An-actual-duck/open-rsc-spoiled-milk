package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.GameTickEventRestorationCurrentStateRecoverySnapshot;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryBatchContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.Completion;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.Directive;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.OperationOutcome;
import com.openrsc.server.event.rsc.GameTickEventRestorationRecoveryCoordinatorContract.OperationResult;
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

/** Bounded in-order execution of one already-prepared recovery batch. */
final class GameTickEventRestorationRecoveryBatchExecutor {
	private final GameTickEventRestorationRecoveryDirectiveExecutor executor;

	GameTickEventRestorationRecoveryBatchExecutor(
		final GameTickEventStore store,
		final RegionManager regionManager) {
		this.executor = new GameTickEventRestorationRecoveryDirectiveExecutor(
			Objects.requireNonNull(store, "store"),
			Objects.requireNonNull(regionManager, "regionManager"));
	}

	BatchExecution execute(
		final Preparation preparation,
		final List<GameTickEventRestorationCurrentStateRecoverySnapshot>
			futureSnapshots,
		final int maximumCandidates) {
		Preparation checked = Objects.requireNonNull(
			preparation, "preparation");
		List<GameTickEventRestorationCurrentStateRecoverySnapshot> snapshots =
			Objects.requireNonNull(futureSnapshots, "futureSnapshots");
		if (maximumCandidates <= 0
			|| maximumCandidates
				> GameTickEventRestorationRecoveryBatchContract
					.MAXIMUM_CANDIDATES) {
			throw new IllegalArgumentException(
				"Recovery execution bound is invalid");
		}
		if (!checked.isReady()) {
			return BatchExecution.invalid(Reason.PREPARATION_REFUSED);
		}
		if (checked.getDirectives().size() > maximumCandidates
			|| snapshots.size() > maximumCandidates) {
			return BatchExecution.invalid(Reason.CANDIDATE_BOUND_EXCEEDED);
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
				return BatchExecution.invalid(
					Reason.DUPLICATE_FUTURE_SNAPSHOT);
			}
		}
		Set<Long> matchedSnapshots = new HashSet<Long>();
		for (Directive directive : checked.getDirectives()) {
			GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
				futureByRegistration.get(
					Long.valueOf(directive.getRegistrationSequence()));
			if (directive.isFutureSnapshotCorrelated()) {
				if (snapshot == null) {
					return BatchExecution.invalid(
						Reason.FUTURE_SNAPSHOT_SET_MISMATCH);
				}
				matchedSnapshots.add(
					Long.valueOf(directive.getRegistrationSequence()));
			} else if (snapshot != null) {
				return BatchExecution.invalid(
					Reason.FUTURE_SNAPSHOT_SET_MISMATCH);
			}
		}
		if (matchedSnapshots.size() != futureByRegistration.size()) {
			return BatchExecution.invalid(
				Reason.FUTURE_SNAPSHOT_SET_MISMATCH);
		}

		List<OperationResult> results = new ArrayList<OperationResult>(
			checked.getDirectives().size());
		int runtimeOperationCount = 0;
		Completion completion = GameTickEventRestorationRecoveryCoordinatorContract
			.assess(checked, results);
		for (int index = 0; index < checked.getDirectives().size(); index++) {
			Directive directive = checked.getDirectives().get(index);
			GameTickEventRestorationRecoveryDirectiveExecutor.DirectiveExecution
				step = executor.execute(
					checked, index, futureByRegistration.get(
						Long.valueOf(directive.getRegistrationSequence())));
			if (step.getOperationResult() == null) {
				return BatchExecution.invalid(
					Reason.DIRECTIVE_EXECUTION_INVALID);
			}
			results.add(step.getOperationResult());
			if (step.isRuntimeOperationInvoked()) {
				runtimeOperationCount++;
			}
			completion =
				GameTickEventRestorationRecoveryCoordinatorContract.assess(
					checked, results);
			if (step.getOperationResult().getOutcome()
				== OperationOutcome.REFUSED) {
				break;
			}
		}
		return BatchExecution.completed(
			completion, results, runtimeOperationCount,
			checked.getDirectives().size());
	}

	enum Reason {
		PREPARATION_REFUSED,
		CANDIDATE_BOUND_EXCEEDED,
		DUPLICATE_FUTURE_SNAPSHOT,
		FUTURE_SNAPSHOT_SET_MISMATCH,
		DIRECTIVE_EXECUTION_INVALID,
		BATCH_PENDING,
		REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY,
		CONTRACTUALLY_READY_FOR_FIRST_VISIBILITY
	}

	/** Closed batch result; contractual readiness does not release visibility. */
	static final class BatchExecution {
		private final Reason reason;
		private final List<OperationResult> operationResults;
		private final int completedPrefixCount;
		private final long refusedRegistrationSequence;
		private final int runtimeOperationCount;
		private final int directiveCount;

		private BatchExecution(
			final Reason reason,
			final List<OperationResult> operationResults,
			final int completedPrefixCount,
			final long refusedRegistrationSequence,
			final int runtimeOperationCount,
			final int directiveCount) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.operationResults = operationResults;
			this.completedPrefixCount = completedPrefixCount;
			this.refusedRegistrationSequence = refusedRegistrationSequence;
			this.runtimeOperationCount = runtimeOperationCount;
			this.directiveCount = directiveCount;
			boolean executed = operationResults != null;
			if (executed != (directiveCount >= 0)
				|| (!executed
					&& (completedPrefixCount != 0
						|| refusedRegistrationSequence != -1L
						|| runtimeOperationCount != 0))
				|| (executed
					&& (completedPrefixCount < 0
						|| completedPrefixCount > operationResults.size()
						|| operationResults.size() > directiveCount
						|| runtimeOperationCount < 0
						|| runtimeOperationCount > operationResults.size()))) {
				throw new IllegalArgumentException(
					"Recovery batch execution is inconsistent");
			}
		}

		private static BatchExecution invalid(final Reason reason) {
			return new BatchExecution(
				reason, null, 0, -1L, 0, -1);
		}

		private static BatchExecution completed(
			final Completion completion,
			final List<OperationResult> results,
			final int runtimeOperationCount,
			final int directiveCount) {
			Reason reason;
			switch (completion.getReason()) {
				case BATCH_PENDING:
					reason = Reason.BATCH_PENDING;
					break;
				case REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY:
					reason = Reason
						.REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY;
					break;
				case READY_FOR_FIRST_VISIBILITY_CONTRACT:
					reason = Reason
						.CONTRACTUALLY_READY_FOR_FIRST_VISIBILITY;
					break;
				default:
					return invalid(Reason.DIRECTIVE_EXECUTION_INVALID);
			}
			return new BatchExecution(
				reason,
				Collections.unmodifiableList(
					new ArrayList<OperationResult>(results)),
				completion.getCompletedPrefixCount(),
				completion.getRefusedRegistrationSequence(),
				runtimeOperationCount, directiveCount);
		}

		Reason getReason() { return reason; }
		List<OperationResult> getOperationResults() {
			return operationResults == null
				? Collections.<OperationResult>emptyList() : operationResults;
		}
		int getCompletedPrefixCount() { return completedPrefixCount; }
		long getRefusedRegistrationSequence() {
			return refusedRegistrationSequence;
		}
		int getRuntimeOperationCount() { return runtimeOperationCount; }
		int getDirectiveCount() { return directiveCount; }
		boolean isContractuallyReadyForFirstVisibility() {
			return reason == Reason.CONTRACTUALLY_READY_FOR_FIRST_VISIBILITY;
		}
		boolean requiresFreshInventoryRetry() {
			return reason
				== Reason.REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY;
		}
		boolean isRetryPerformed() { return false; }
		boolean isRegionLoadingPerformed() { return false; }
		boolean isArrivalGate() { return false; }
		boolean isVisibilityReleased() { return false; }
		boolean isRuntimeHandleRetained() { return false; }
		boolean isLifecycleAuthority() { return false; }
	}
}
