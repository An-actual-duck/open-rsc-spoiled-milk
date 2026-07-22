package com.openrsc.server.event.rsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure coordinator contract for one already-planned restoration batch.
 *
 * <p>Overdue steps map only to desired-state commit plus terminal event
 * consumption. Future steps map only to current-state restoration plus event
 * retention and require one exactly correlated Slice 144 snapshot. Results are
 * supplied as detached typed declarations; this class invokes no operation.</p>
 */
public final class GameTickEventRestorationRecoveryCoordinatorContract {
	private GameTickEventRestorationRecoveryCoordinatorContract() { }

	/** Correlates one accepted bounded batch with all required future state. */
	public static Preparation prepare(
		final GameTickEventRestorationRecoveryBatchContract.Plan plan,
		final List<
			GameTickEventRestorationCurrentStateRecoverySnapshot>
			futureSnapshots,
		final int maximumCandidates) {
		GameTickEventRestorationRecoveryBatchContract.Plan checkedPlan =
			Objects.requireNonNull(plan, "plan");
		List<GameTickEventRestorationCurrentStateRecoverySnapshot> checkedSnapshots =
			Objects.requireNonNull(futureSnapshots, "futureSnapshots");
		if (maximumCandidates <= 0
			|| maximumCandidates
				> GameTickEventRestorationRecoveryBatchContract
					.MAXIMUM_CANDIDATES) {
			throw new IllegalArgumentException(
				"Recovery coordinator bound is invalid");
		}
		if (!checkedPlan.isAccepted()) {
			return Preparation.refused(Reason.BATCH_PLAN_REFUSED);
		}
		if (checkedPlan.getSteps().size() > maximumCandidates
			|| checkedSnapshots.size() > maximumCandidates) {
			return Preparation.refused(Reason.CANDIDATE_BOUND_EXCEEDED);
		}

		Map<Long, GameTickEventRestorationCurrentStateRecoverySnapshot>
			byRegistration = new HashMap<Long,
				GameTickEventRestorationCurrentStateRecoverySnapshot>();
		for (GameTickEventRestorationCurrentStateRecoverySnapshot snapshot
			: checkedSnapshots) {
			GameTickEventRestorationCurrentStateRecoverySnapshot checked =
				Objects.requireNonNull(snapshot, "futureSnapshot");
			if (byRegistration.put(
				Long.valueOf(checked.getRegistrationSequence()), checked) != null) {
				return Preparation.refused(
					Reason.DUPLICATE_FUTURE_SNAPSHOT_REGISTRATION);
			}
		}

		List<Directive> directives =
			new ArrayList<Directive>(checkedPlan.getSteps().size());
		Set<Long> consumedSnapshots = new HashSet<Long>();
		for (GameTickEventRestorationRecoveryBatchContract.Step step
			: checkedPlan.getSteps()) {
			GameTickEventRestorationRecoveryBatchContract.Candidate candidate =
				step.getCandidate();
			long registration = candidate.getRegistrationSequence();
			switch (step.getAction()) {
				case COMMIT_DESIRED_STATE_AND_CONSUME:
					if (byRegistration.containsKey(Long.valueOf(registration))) {
						return Preparation.refused(
							Reason.OVERDUE_STEP_HAS_FUTURE_SNAPSHOT);
					}
					directives.add(Directive.overdue(step.getIndex(), candidate));
					break;
				case RESTORE_CURRENT_STATE_AND_RETAIN_SCHEDULED:
					GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
						byRegistration.get(Long.valueOf(registration));
					if (snapshot == null) {
						return Preparation.refused(
							Reason.FUTURE_SNAPSHOT_MISSING);
					}
					if (!snapshotMatches(
						checkedPlan, candidate, snapshot)) {
						return Preparation.refused(
							Reason.FUTURE_SNAPSHOT_CORRELATION_MISMATCH);
					}
					consumedSnapshots.add(Long.valueOf(registration));
					directives.add(
						Directive.future(step.getIndex(), candidate, snapshot));
					break;
				default:
					return Preparation.refused(
						Reason.BATCH_ACTION_UNSUPPORTED);
			}
		}
		if (consumedSnapshots.size() != checkedSnapshots.size()) {
			return Preparation.refused(Reason.UNMATCHED_FUTURE_SNAPSHOT);
		}
		return Preparation.ready(
			checkedPlan.getSchedulerInstanceIdentity(),
			checkedPlan.getObservedAtTick(), directives);
	}

	private static boolean snapshotMatches(
		final GameTickEventRestorationRecoveryBatchContract.Plan plan,
		final GameTickEventRestorationRecoveryBatchContract.Candidate candidate,
		final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
		return plan.getSchedulerInstanceIdentity().equals(
				snapshot.getSchedulerInstanceIdentity())
			&& candidate.getSchedulerInstanceIdentity().equals(
				snapshot.getSchedulerInstanceIdentity())
			&& candidate.getRegistrationSequence()
				== snapshot.getRegistrationSequence()
			&& candidate.getProposalGeneration()
				== snapshot.getProposalGeneration()
			&& candidate.getLifecycleVersion()
				== snapshot.getLifecycleVersion()
			&& candidate.getTicksBeforeRun() == snapshot.getTicksBeforeRun()
			&& snapshot.getTicksBeforeRun() > 0L
			&& snapshot.isFutureCallback()
			&& snapshot.isCallbackRetainedScheduled()
			&& snapshot.isCurrentStateKeptSeparateFromDesiredState()
			&& !snapshot.isRuntimeConsumerConnected()
			&& !snapshot.isMutationPerformed()
			&& !snapshot.isEventCancellation()
			&& !snapshot.isEventReschedule()
			&& !snapshot.isVisibilityReleased();
	}

	/**
	 * Validates an in-order prefix of fixture-supplied typed operation results.
	 * It delegates prefix/refusal/ready semantics to Slice 143.
	 */
	public static Completion assess(
		final Preparation preparation,
		final List<OperationResult> operationResults) {
		Preparation checkedPreparation = Objects.requireNonNull(
			preparation, "preparation");
		List<OperationResult> checkedResults = Objects.requireNonNull(
			operationResults, "operationResults");
		if (!checkedPreparation.isReady()) {
			return Completion.invalid(CompletionReason.PREPARATION_REFUSED);
		}
		if (checkedResults.size()
			> checkedPreparation.getDirectives().size()) {
			return Completion.invalid(CompletionReason.RESULT_COUNT_EXCEEDED);
		}
		List<GameTickEventRestorationRecoveryBatchContract.StepOutcome>
			batchOutcomes = new ArrayList<
				GameTickEventRestorationRecoveryBatchContract.StepOutcome>(
					checkedResults.size());
		for (int index = 0; index < checkedResults.size(); index++) {
			Directive directive = checkedPreparation.getDirectives().get(index);
			OperationResult result = Objects.requireNonNull(
				checkedResults.get(index), "operationResult");
			if (result.getRegistrationSequence()
					!= directive.getRegistrationSequence()
				|| result.getOperationKind() != directive.getOperationKind()) {
				return Completion.invalid(
					CompletionReason.RESULT_DIRECTIVE_MISMATCH);
			}
			GameTickEventRestorationRecoveryBatchContract.Outcome outcome =
				toBatchOutcome(directive, result);
			if (outcome == null) {
				return Completion.invalid(
					CompletionReason.RESULT_OUTCOME_MISMATCH);
			}
			batchOutcomes.add(
				GameTickEventRestorationRecoveryBatchContract.StepOutcome
					.report(result.getRegistrationSequence(), outcome));
		}

		GameTickEventRestorationRecoveryBatchContract.Plan batchPlan =
			checkedPreparation.toBatchPlan();
		GameTickEventRestorationRecoveryBatchContract.Progress progress =
			GameTickEventRestorationRecoveryBatchContract.assessProgress(
				batchPlan, batchOutcomes);
		return Completion.from(progress);
	}

	private static GameTickEventRestorationRecoveryBatchContract.Outcome
		toBatchOutcome(
			final Directive directive,
			final OperationResult result) {
		if (result.getOutcome() == OperationOutcome.REFUSED) {
			return GameTickEventRestorationRecoveryBatchContract.Outcome.REFUSED;
		}
		switch (directive.getOperationKind()) {
			case DESIRED_STATE_COMMIT_AND_EVENT_CONSUME:
				if (result.getOutcome()
					== OperationOutcome.DESIRED_STATE_APPLIED_AND_EVENT_CONSUMED) {
					return GameTickEventRestorationRecoveryBatchContract.Outcome
						.APPLIED;
				}
				if (result.getOutcome()
					== OperationOutcome.DESIRED_STATE_NO_OP_AND_EVENT_CONSUMED) {
					return GameTickEventRestorationRecoveryBatchContract.Outcome
						.NO_OP;
				}
				return null;
			case CURRENT_STATE_RESTORE_AND_EVENT_RETAIN:
				return result.getOutcome()
						== OperationOutcome
							.CURRENT_STATE_RESTORED_AND_EVENT_RETAINED
					? GameTickEventRestorationRecoveryBatchContract.Outcome
						.CURRENT_STATE_RESTORED
					: null;
			default:
				return null;
		}
	}

	public enum Reason {
		READY,
		BATCH_PLAN_REFUSED,
		CANDIDATE_BOUND_EXCEEDED,
		DUPLICATE_FUTURE_SNAPSHOT_REGISTRATION,
		OVERDUE_STEP_HAS_FUTURE_SNAPSHOT,
		FUTURE_SNAPSHOT_MISSING,
		FUTURE_SNAPSHOT_CORRELATION_MISMATCH,
		UNMATCHED_FUTURE_SNAPSHOT,
		BATCH_ACTION_UNSUPPORTED
	}

	public enum OperationKind {
		DESIRED_STATE_COMMIT_AND_EVENT_CONSUME,
		CURRENT_STATE_RESTORE_AND_EVENT_RETAIN
	}

	public enum OperationOutcome {
		REFUSED,
		DESIRED_STATE_APPLIED_AND_EVENT_CONSUMED,
		DESIRED_STATE_NO_OP_AND_EVENT_CONSUMED,
		CURRENT_STATE_RESTORED_AND_EVENT_RETAINED
	}

	public enum CompletionReason {
		PREPARATION_REFUSED,
		RESULT_COUNT_EXCEEDED,
		RESULT_DIRECTIVE_MISMATCH,
		RESULT_OUTCOME_MISMATCH,
		BATCH_PENDING,
		REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY,
		READY_FOR_FIRST_VISIBILITY_CONTRACT
	}

	/** One detached operation mapping; no operation or runtime handle. */
	public static final class Directive {
		private final int index;
		private final long registrationSequence;
		private final long proposalGeneration;
		private final long lifecycleVersion;
		private final long ticksBeforeRun;
		private final OperationKind operationKind;
		private final boolean futureSnapshotCorrelated;

		private Directive(
			final int index,
			final GameTickEventRestorationRecoveryBatchContract.Candidate candidate,
			final OperationKind operationKind,
			final boolean futureSnapshotCorrelated) {
			this.index = index;
			this.registrationSequence = candidate.getRegistrationSequence();
			this.proposalGeneration = candidate.getProposalGeneration();
			this.lifecycleVersion = candidate.getLifecycleVersion();
			this.ticksBeforeRun = candidate.getTicksBeforeRun();
			this.operationKind = Objects.requireNonNull(
				operationKind, "operationKind");
			this.futureSnapshotCorrelated = futureSnapshotCorrelated;
		}

		private static Directive overdue(
			final int index,
			final GameTickEventRestorationRecoveryBatchContract.Candidate
				candidate) {
			return new Directive(
				index, candidate,
				OperationKind.DESIRED_STATE_COMMIT_AND_EVENT_CONSUME,
				false);
		}

		private static Directive future(
			final int index,
			final GameTickEventRestorationRecoveryBatchContract.Candidate candidate,
			final GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
			Objects.requireNonNull(snapshot, "snapshot");
			return new Directive(
				index, candidate,
				OperationKind.CURRENT_STATE_RESTORE_AND_EVENT_RETAIN,
				true);
		}

		public int getIndex() { return index; }
		public long getRegistrationSequence() {
			return registrationSequence;
		}
		public long getProposalGeneration() { return proposalGeneration; }
		public long getLifecycleVersion() { return lifecycleVersion; }
		public long getTicksBeforeRun() { return ticksBeforeRun; }
		public OperationKind getOperationKind() { return operationKind; }
		public boolean isFutureSnapshotCorrelated() {
			return futureSnapshotCorrelated;
		}
	}

	/** Prepared immutable directive list; readiness grants no authority. */
	public static final class Preparation {
		private final Reason reason;
		private final String schedulerInstanceIdentity;
		private final long observedAtTick;
		private final List<Directive> directives;

		private Preparation(
			final Reason reason,
			final String schedulerInstanceIdentity,
			final long observedAtTick,
			final List<Directive> directives) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.schedulerInstanceIdentity = schedulerInstanceIdentity;
			this.observedAtTick = observedAtTick;
			this.directives = directives;
			boolean ready = reason == Reason.READY;
			if (ready != (schedulerInstanceIdentity != null)
				|| ready != (directives != null)) {
				throw new IllegalArgumentException(
					"Recovery coordinator preparation is inconsistent");
			}
		}

		private static Preparation refused(final Reason reason) {
			if (reason == Reason.READY) {
				throw new IllegalArgumentException(
					"Ready reason cannot refuse preparation");
			}
			return new Preparation(reason, null, -1L, null);
		}

		private static Preparation ready(
			final String schedulerInstanceIdentity,
			final long observedAtTick,
			final List<Directive> directives) {
			return new Preparation(
				Reason.READY,
				Objects.requireNonNull(
					schedulerInstanceIdentity, "schedulerInstanceIdentity"),
				observedAtTick,
				Collections.unmodifiableList(
					new ArrayList<Directive>(directives)));
		}

		private GameTickEventRestorationRecoveryBatchContract.Plan toBatchPlan() {
			List<GameTickEventRestorationRecoveryBatchContract.Candidate>
				candidates = new ArrayList<
					GameTickEventRestorationRecoveryBatchContract.Candidate>(
						directives.size());
			for (Directive directive : directives) {
				candidates.add(
					GameTickEventRestorationRecoveryBatchContract.Candidate.declare(
						schedulerInstanceIdentity,
						directive.getRegistrationSequence(),
						directive.getProposalGeneration(),
						directive.getLifecycleVersion(),
						directive.getTicksBeforeRun(), 0,
						true, true, true));
			}
			return GameTickEventRestorationRecoveryBatchContract.plan(
				schedulerInstanceIdentity, observedAtTick, candidates,
				Math.max(1, candidates.size()), true, true);
		}

		public Reason getReason() { return reason; }
		public boolean isReady() { return reason == Reason.READY; }
		public String getSchedulerInstanceIdentity() {
			return schedulerInstanceIdentity;
		}
		public long getObservedAtTick() { return observedAtTick; }
		public List<Directive> getDirectives() {
			return directives == null
				? Collections.<Directive>emptyList() : directives;
		}
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isOperationInvoked() { return false; }
		public boolean isRegionLoadingPerformed() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isVisibilityReleased() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	/** One fixture-supplied typed result; no callback or entity result. */
	public static final class OperationResult {
		private final long registrationSequence;
		private final OperationKind operationKind;
		private final OperationOutcome outcome;

		private OperationResult(
			final long registrationSequence,
			final OperationKind operationKind,
			final OperationOutcome outcome) {
			if (registrationSequence <= 0L) {
				throw new IllegalArgumentException(
					"Recovery operation registration is invalid");
			}
			this.registrationSequence = registrationSequence;
			this.operationKind = Objects.requireNonNull(
				operationKind, "operationKind");
			this.outcome = Objects.requireNonNull(outcome, "outcome");
		}

		public static OperationResult report(
			final long registrationSequence,
			final OperationKind operationKind,
			final OperationOutcome outcome) {
			return new OperationResult(
				registrationSequence, operationKind, outcome);
		}

		public long getRegistrationSequence() {
			return registrationSequence;
		}
		public OperationKind getOperationKind() { return operationKind; }
		public OperationOutcome getOutcome() { return outcome; }
	}

	/** Closed progress result; readiness is contractual, not visibility. */
	public static final class Completion {
		private final CompletionReason reason;
		private final int completedPrefixCount;
		private final long refusedRegistrationSequence;

		private Completion(
			final CompletionReason reason,
			final int completedPrefixCount,
			final long refusedRegistrationSequence) {
			this.reason = Objects.requireNonNull(reason, "reason");
			this.completedPrefixCount = completedPrefixCount;
			this.refusedRegistrationSequence = refusedRegistrationSequence;
		}

		private static Completion invalid(final CompletionReason reason) {
			return new Completion(reason, 0, -1L);
		}

		private static Completion from(
			final GameTickEventRestorationRecoveryBatchContract.Progress
				progress) {
			switch (progress.getReason()) {
				case BATCH_PENDING:
					return new Completion(
						CompletionReason.BATCH_PENDING,
						progress.getCompletedPrefixCount(), -1L);
				case REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY:
					return new Completion(
						CompletionReason
							.REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY,
						progress.getCompletedPrefixCount(),
						progress.getRefusedRegistrationSequence());
				case READY_FOR_FIRST_VISIBILITY:
					return new Completion(
						CompletionReason.READY_FOR_FIRST_VISIBILITY_CONTRACT,
						progress.getCompletedPrefixCount(), -1L);
				default:
					return invalid(CompletionReason.RESULT_OUTCOME_MISMATCH);
			}
		}

		public CompletionReason getReason() { return reason; }
		public int getCompletedPrefixCount() {
			return completedPrefixCount;
		}
		public long getRefusedRegistrationSequence() {
			return refusedRegistrationSequence;
		}
		public boolean isReadyForFirstVisibilityContract() {
			return reason
				== CompletionReason.READY_FOR_FIRST_VISIBILITY_CONTRACT;
		}
		public boolean requiresFreshInventoryRetry() {
			return reason
				== CompletionReason
					.REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY;
		}
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isOperationInvoked() { return false; }
		public boolean isRegionLoadingPerformed() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isVisibilityReleased() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}
}
