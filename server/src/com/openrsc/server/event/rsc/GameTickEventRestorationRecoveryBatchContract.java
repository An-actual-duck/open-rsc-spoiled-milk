package com.openrsc.server.event.rsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure policy for ordering one bounded recovery batch before first visibility.
 * It owns no scheduler, event, Region, callback, loading, or arrival handle.
 */
public final class GameTickEventRestorationRecoveryBatchContract {
	public static final int MAXIMUM_CANDIDATES = 4096;

	private GameTickEventRestorationRecoveryBatchContract() { }

	public static Plan plan(
		final String schedulerInstanceIdentity,
		final long observedAtTick,
		final List<Candidate> candidates,
		final int maximumCandidates,
		final boolean firstVisibilityWithheld,
		final boolean currentStateRecoveryAvailable) {
		if (schedulerInstanceIdentity == null
			|| schedulerInstanceIdentity.isEmpty()
			|| observedAtTick < 0L
			|| maximumCandidates <= 0
			|| maximumCandidates > MAXIMUM_CANDIDATES) {
			throw new IllegalArgumentException(
				"Recovery batch declaration is invalid");
		}
		List<Candidate> checked = Objects.requireNonNull(
			candidates, "candidates");
		if (!firstVisibilityWithheld) {
			return Plan.refused(
				schedulerInstanceIdentity, observedAtTick,
				Reason.FIRST_VISIBILITY_NOT_WITHHELD);
		}
		if (checked.size() > maximumCandidates) {
			return Plan.refused(
				schedulerInstanceIdentity, observedAtTick,
				Reason.CANDIDATE_BOUND_EXCEEDED);
		}
		List<Candidate> ordered = new ArrayList<>(checked.size());
		Set<Long> registrations = new HashSet<>();
		for (Candidate candidate : checked) {
			Candidate value = Objects.requireNonNull(candidate, "candidate");
			if (!schedulerInstanceIdentity.equals(
					value.getSchedulerInstanceIdentity())) {
				return Plan.refused(
					schedulerInstanceIdentity, observedAtTick,
					Reason.SCHEDULER_INSTANCE_MISMATCH);
			}
			if (!registrations.add(
					Long.valueOf(value.getRegistrationSequence()))) {
				return Plan.refused(
					schedulerInstanceIdentity, observedAtTick,
					Reason.DUPLICATE_REGISTRATION);
			}
			if (!value.isRunning() || value.getTimesRan() != 0
				|| !value.isOneShotExecution()
				|| !value.isContinuingServerTickProgression()) {
				return Plan.refused(
					schedulerInstanceIdentity, observedAtTick,
					Reason.EVENT_SEMANTICS_REFUSED);
			}
			ordered.add(value);
		}
		Collections.sort(ordered, Comparator
			.comparingLong(Candidate::getRegistrationSequence));
		List<Step> steps = new ArrayList<>(ordered.size());
		for (int index = 0; index < ordered.size(); index++) {
			Candidate candidate = ordered.get(index);
			StepAction action = candidate.getTicksBeforeRun() <= 0L
				? StepAction.COMMIT_DESIRED_STATE_AND_CONSUME
				: StepAction.RESTORE_CURRENT_STATE_AND_RETAIN_SCHEDULED;
			if (action
					== StepAction.RESTORE_CURRENT_STATE_AND_RETAIN_SCHEDULED
				&& !currentStateRecoveryAvailable) {
				return Plan.refused(
					schedulerInstanceIdentity, observedAtTick,
					Reason.FUTURE_CURRENT_STATE_RECOVERY_UNAVAILABLE);
			}
			steps.add(new Step(index, candidate, action));
		}
		return Plan.accepted(
			schedulerInstanceIdentity, observedAtTick, steps,
			currentStateRecoveryAvailable);
	}

	public static Progress assessProgress(
		final Plan plan,
		final List<StepOutcome> outcomes) {
		Plan checkedPlan = Objects.requireNonNull(plan, "plan");
		List<StepOutcome> checkedOutcomes = Objects.requireNonNull(
			outcomes, "outcomes");
		if (!checkedPlan.isAccepted()) {
			return Progress.invalid(ProgressReason.PLAN_REFUSED);
		}
		if (checkedOutcomes.size() > checkedPlan.getSteps().size()) {
			return Progress.invalid(ProgressReason.OUTCOME_COUNT_EXCEEDED);
		}
		for (int index = 0; index < checkedOutcomes.size(); index++) {
			Step step = checkedPlan.getSteps().get(index);
			StepOutcome outcome = Objects.requireNonNull(
				checkedOutcomes.get(index), "outcome");
			if (outcome.getRegistrationSequence()
					!= step.getCandidate().getRegistrationSequence()) {
				return Progress.invalid(ProgressReason.OUTCOME_ORDER_MISMATCH);
			}
			if (outcome.getOutcome() == Outcome.REFUSED) {
				if (index + 1 != checkedOutcomes.size()) {
					return Progress.invalid(
						ProgressReason.OUTCOME_AFTER_REFUSAL);
				}
				return Progress.refused(
					index, outcome.getRegistrationSequence());
			}
			boolean compatible = step.getAction()
					== StepAction.COMMIT_DESIRED_STATE_AND_CONSUME
				? outcome.getOutcome() == Outcome.APPLIED
					|| outcome.getOutcome() == Outcome.NO_OP
				: outcome.getOutcome() == Outcome.CURRENT_STATE_RESTORED;
			if (!compatible) {
				return Progress.invalid(ProgressReason.OUTCOME_ACTION_MISMATCH);
			}
		}
		return checkedOutcomes.size() == checkedPlan.getSteps().size()
			? Progress.ready(checkedOutcomes.size())
			: Progress.pending(checkedOutcomes.size());
	}

	public enum Reason {
		ACCEPTED,
		FIRST_VISIBILITY_NOT_WITHHELD,
		CANDIDATE_BOUND_EXCEEDED,
		SCHEDULER_INSTANCE_MISMATCH,
		DUPLICATE_REGISTRATION,
		EVENT_SEMANTICS_REFUSED,
		FUTURE_CURRENT_STATE_RECOVERY_UNAVAILABLE
	}

	public enum StepAction {
		COMMIT_DESIRED_STATE_AND_CONSUME,
		RESTORE_CURRENT_STATE_AND_RETAIN_SCHEDULED
	}

	public enum Outcome { REFUSED, NO_OP, APPLIED, CURRENT_STATE_RESTORED }

	public enum ProgressReason {
		PLAN_REFUSED,
		OUTCOME_COUNT_EXCEEDED,
		OUTCOME_ORDER_MISMATCH,
		OUTCOME_AFTER_REFUSAL,
		OUTCOME_ACTION_MISMATCH,
		BATCH_PENDING,
		REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY,
		READY_FOR_FIRST_VISIBILITY
	}

	public static final class Candidate {
		private final String schedulerInstanceIdentity;
		private final long registrationSequence;
		private final long proposalGeneration;
		private final long lifecycleVersion;
		private final long ticksBeforeRun;
		private final int timesRan;
		private final boolean running;
		private final boolean oneShotExecution;
		private final boolean continuingServerTickProgression;

		private Candidate(
			final String schedulerInstanceIdentity,
			final long registrationSequence,
			final long proposalGeneration,
			final long lifecycleVersion,
			final long ticksBeforeRun,
			final int timesRan,
			final boolean running,
			final boolean oneShotExecution,
			final boolean continuingServerTickProgression) {
			if (schedulerInstanceIdentity == null
				|| schedulerInstanceIdentity.isEmpty()
				|| registrationSequence <= 0L
				|| proposalGeneration <= 0L
				|| lifecycleVersion <= 0L || timesRan < 0) {
				throw new IllegalArgumentException(
					"Recovery candidate is invalid");
			}
			this.schedulerInstanceIdentity = schedulerInstanceIdentity;
			this.registrationSequence = registrationSequence;
			this.proposalGeneration = proposalGeneration;
			this.lifecycleVersion = lifecycleVersion;
			this.ticksBeforeRun = ticksBeforeRun;
			this.timesRan = timesRan;
			this.running = running;
			this.oneShotExecution = oneShotExecution;
			this.continuingServerTickProgression =
				continuingServerTickProgression;
		}

		public static Candidate declare(
			final String schedulerInstanceIdentity,
			final long registrationSequence,
			final long proposalGeneration,
			final long lifecycleVersion,
			final long ticksBeforeRun,
			final int timesRan,
			final boolean running,
			final boolean oneShotExecution,
			final boolean continuingServerTickProgression) {
			return new Candidate(
				schedulerInstanceIdentity, registrationSequence,
				proposalGeneration, lifecycleVersion, ticksBeforeRun,
				timesRan, running, oneShotExecution,
				continuingServerTickProgression);
		}

		public String getSchedulerInstanceIdentity() {
			return schedulerInstanceIdentity;
		}
		public long getRegistrationSequence() { return registrationSequence; }
		public long getProposalGeneration() { return proposalGeneration; }
		public long getLifecycleVersion() { return lifecycleVersion; }
		public long getTicksBeforeRun() { return ticksBeforeRun; }
		public int getTimesRan() { return timesRan; }
		public boolean isRunning() { return running; }
		public boolean isOneShotExecution() { return oneShotExecution; }
		public boolean isContinuingServerTickProgression() {
			return continuingServerTickProgression;
		}
	}

	public static final class Step {
		private final int index;
		private final Candidate candidate;
		private final StepAction action;

		private Step(
			final int index,
			final Candidate candidate,
			final StepAction action) {
			this.index = index;
			this.candidate = candidate;
			this.action = action;
		}

		public int getIndex() { return index; }
		public Candidate getCandidate() { return candidate; }
		public StepAction getAction() { return action; }
	}

	public static final class StepOutcome {
		private final long registrationSequence;
		private final Outcome outcome;

		private StepOutcome(
			final long registrationSequence,
			final Outcome outcome) {
			if (registrationSequence <= 0L) {
				throw new IllegalArgumentException(
					"Recovery outcome registration is invalid");
			}
			this.registrationSequence = registrationSequence;
			this.outcome = Objects.requireNonNull(outcome, "outcome");
		}

		public static StepOutcome report(
			final long registrationSequence,
			final Outcome outcome) {
			return new StepOutcome(registrationSequence, outcome);
		}
		public long getRegistrationSequence() { return registrationSequence; }
		public Outcome getOutcome() { return outcome; }
	}

	public static final class Plan {
		private final String schedulerInstanceIdentity;
		private final long observedAtTick;
		private final Reason reason;
		private final List<Step> steps;
		private final boolean currentStateRecoveryAvailable;

		private Plan(
			final String schedulerInstanceIdentity,
			final long observedAtTick,
			final Reason reason,
			final List<Step> steps,
			final boolean currentStateRecoveryAvailable) {
			this.schedulerInstanceIdentity = schedulerInstanceIdentity;
			this.observedAtTick = observedAtTick;
			this.reason = Objects.requireNonNull(reason, "reason");
			this.steps = steps;
			this.currentStateRecoveryAvailable =
				currentStateRecoveryAvailable;
			if ((reason == Reason.ACCEPTED) != (steps != null)) {
				throw new IllegalArgumentException(
					"Recovery plan is inconsistent");
			}
		}

		private static Plan refused(
			final String identity, final long tick, final Reason reason) {
			return new Plan(identity, tick, reason, null, false);
		}
		private static Plan accepted(
			final String identity, final long tick,
			final List<Step> steps,
			final boolean currentStateRecoveryAvailable) {
			return new Plan(
				identity, tick, Reason.ACCEPTED,
				Collections.unmodifiableList(new ArrayList<>(steps)),
				currentStateRecoveryAvailable);
		}

		public String getSchedulerInstanceIdentity() {
			return schedulerInstanceIdentity;
		}
		public long getObservedAtTick() { return observedAtTick; }
		public Reason getReason() { return reason; }
		public boolean isAccepted() { return reason == Reason.ACCEPTED; }
		public List<Step> getSteps() {
			return steps == null ? Collections.emptyList() : steps;
		}
		public boolean isCurrentStateRecoveryAvailable() {
			return currentStateRecoveryAvailable;
		}
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isRegionInvocation() { return false; }
		public boolean isCallbackInvoked() { return false; }
		public boolean isEventCancellation() { return false; }
		public boolean isEventReschedule() { return false; }
		public boolean isLoadingAuthority() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	public static final class Progress {
		private final ProgressReason reason;
		private final int completedPrefixCount;
		private final long refusedRegistrationSequence;

		private Progress(
			final ProgressReason reason,
			final int completedPrefixCount,
			final long refusedRegistrationSequence) {
			this.reason = reason;
			this.completedPrefixCount = completedPrefixCount;
			this.refusedRegistrationSequence = refusedRegistrationSequence;
		}

		private static Progress invalid(final ProgressReason reason) {
			return new Progress(reason, 0, -1L);
		}
		private static Progress pending(final int completed) {
			return new Progress(ProgressReason.BATCH_PENDING, completed, -1L);
		}
		private static Progress refused(
			final int completed, final long registration) {
			return new Progress(
				ProgressReason.REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY,
				completed, registration);
		}
		private static Progress ready(final int completed) {
			return new Progress(
				ProgressReason.READY_FOR_FIRST_VISIBILITY, completed, -1L);
		}

		public ProgressReason getReason() { return reason; }
		public int getCompletedPrefixCount() { return completedPrefixCount; }
		public long getRefusedRegistrationSequence() {
			return refusedRegistrationSequence;
		}
		public boolean isReadyForFirstVisibility() {
			return reason == ProgressReason.READY_FOR_FIRST_VISIBILITY;
		}
		public boolean requiresFreshInventoryRetry() {
			return reason
				== ProgressReason.REFUSAL_REQUIRES_FRESH_INVENTORY_RETRY;
		}
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isRegionInvocation() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}
}
