package com.openrsc.server.event.rsc;

import java.util.Objects;

/**
 * Dormant scheduler-local contract for consuming one restored one-shot event.
 *
 * <p>A refused Region commit retains the exact pending registration unchanged.
 * An applied commit or desired-state no-op requires terminal consumption of
 * that exact registration after Region work, without invoking the callback.
 * This class evaluates detached declarations only; it owns no event, Store,
 * Region, callback, monitor, or mutation authority.</p>
 */
public final class GameTickEventRestorationOneShotConsumptionContract {
	private GameTickEventRestorationOneShotConsumptionContract() { }

	public static Decision assess(final Precondition precondition) {
		Precondition checked = Objects.requireNonNull(
			precondition, "precondition");
		if (!checked.isSchedulerInstanceExact()
			|| !checked.isRegistrationExact()
			|| !checked.isAuthoredGenerationExact()) {
			return Decision.refused(checked, Reason.IDENTITY_FENCE_REFUSED);
		}
		if (!checked.isEventExecutionBoundaryHeld()) {
			return Decision.refused(
				checked, Reason.EVENT_EXECUTION_BOUNDARY_MISSING);
		}
		if (checked.isSchedulerStoreBoundaryHeldDuringRegionCommit()) {
			return Decision.refused(
				checked, Reason.SCHEDULER_STORE_HELD_DURING_REGION_COMMIT);
		}
		if (!checked.isLifecycleBoundaryHeld()) {
			return Decision.refused(
				checked, Reason.EVENT_LIFECYCLE_BOUNDARY_MISSING);
		}
		if (!checked.isOneShotExecution()
			|| !checked.isContinuingServerTickProgression()) {
			return Decision.refused(
				checked, Reason.EXECUTION_SEMANTICS_REFUSED);
		}
		if (!checked.isRunning() || checked.getTimesRan() != 0) {
			return Decision.refused(
				checked, Reason.EVENT_NOT_PENDING_ZERO_RUN);
		}
		if (checked.isCallbackInvoked()) {
			return Decision.refused(
				checked, Reason.CALLBACK_ALREADY_INVOKED);
		}
		if (!checked.isRegionOutcomeConsistent()) {
			return Decision.refused(
				checked, Reason.REGION_COMMIT_OUTCOME_INCONSISTENT);
		}
		return checked.getRegionCommitOutcome() == RegionCommitOutcome.REFUSED
			? Decision.required(
				checked, RequiredAction.RETAIN_SCHEDULED,
				Reason.REFUSAL_RETAINS_SCHEDULED_EVENT)
			: Decision.required(
				checked, RequiredAction.TERMINALLY_CONSUME,
				Reason.DESIRED_STATE_REQUIRES_TERMINAL_CONSUMPTION);
	}

	public static Verification verifyPostcondition(
		final Decision decision,
		final Postcondition postcondition) {
		Decision checkedDecision = Objects.requireNonNull(decision, "decision");
		Postcondition checkedPost = Objects.requireNonNull(
			postcondition, "postcondition");
		if (checkedDecision.isRefused()) {
			return Verification.refused(
				VerificationReason.DECISION_REFUSED);
		}
		if (checkedPost.isCallbackInvoked()) {
			return Verification.refused(
				VerificationReason.CALLBACK_WAS_INVOKED);
		}
		if (checkedPost.isEventRescheduled()) {
			return Verification.refused(
				VerificationReason.EVENT_WAS_RESCHEDULED);
		}
		Precondition before = checkedDecision.getPrecondition();
		if (checkedPost.getTimesRan() != before.getTimesRan()) {
			return Verification.refused(
				VerificationReason.EXECUTION_COUNT_CHANGED);
		}
		if (checkedDecision.getRequiredAction()
				== RequiredAction.RETAIN_SCHEDULED) {
			if (!checkedPost.isRegistrationPresent()
				|| !checkedPost.isSameRegistrationPresent()) {
				return Verification.refused(
					VerificationReason.EXACT_REGISTRATION_NOT_RETAINED);
			}
			if (!checkedPost.isRunning()
				|| checkedPost.getLifecycleVersion()
					!= before.getLifecycleVersion()) {
				return Verification.refused(
					VerificationReason.RETAINED_LIFECYCLE_CHANGED);
			}
			if (checkedPost
				.isSchedulerStoreBoundaryAcquiredAfterRegionCommit()) {
				return Verification.refused(
					VerificationReason.UNNECESSARY_STORE_MUTATION);
			}
			return Verification.satisfied(
				VerificationReason.REFUSAL_RETAINED_UNCHANGED);
		}
		if (checkedPost.isRegistrationPresent()
			|| !checkedPost.isExactRegistrationRemoved()) {
			return Verification.refused(
				VerificationReason.EXACT_REGISTRATION_NOT_REMOVED);
		}
		if (checkedPost.isRunning()
			|| checkedPost.getLifecycleVersion()
				<= before.getLifecycleVersion()) {
			return Verification.refused(
				VerificationReason.EVENT_NOT_TERMINALLY_STOPPED);
		}
		if (!checkedPost
			.isSchedulerStoreBoundaryAcquiredAfterRegionCommit()) {
			return Verification.refused(
				VerificationReason.STORE_REMOVAL_ORDER_MISSING);
		}
		return Verification.satisfied(
			VerificationReason.EVENT_TERMINALLY_CONSUMED);
	}

	public enum RegionCommitOutcome { REFUSED, NO_OP, APPLIED }
	public enum RequiredAction { NONE, RETAIN_SCHEDULED, TERMINALLY_CONSUME }

	public enum Reason {
		IDENTITY_FENCE_REFUSED,
		EVENT_EXECUTION_BOUNDARY_MISSING,
		SCHEDULER_STORE_HELD_DURING_REGION_COMMIT,
		EVENT_LIFECYCLE_BOUNDARY_MISSING,
		EXECUTION_SEMANTICS_REFUSED,
		EVENT_NOT_PENDING_ZERO_RUN,
		CALLBACK_ALREADY_INVOKED,
		REGION_COMMIT_OUTCOME_INCONSISTENT,
		REFUSAL_RETAINS_SCHEDULED_EVENT,
		DESIRED_STATE_REQUIRES_TERMINAL_CONSUMPTION
	}

	public enum VerificationReason {
		DECISION_REFUSED,
		CALLBACK_WAS_INVOKED,
		EVENT_WAS_RESCHEDULED,
		EXECUTION_COUNT_CHANGED,
		EXACT_REGISTRATION_NOT_RETAINED,
		RETAINED_LIFECYCLE_CHANGED,
		UNNECESSARY_STORE_MUTATION,
		EXACT_REGISTRATION_NOT_REMOVED,
		EVENT_NOT_TERMINALLY_STOPPED,
		STORE_REMOVAL_ORDER_MISSING,
		REFUSAL_RETAINED_UNCHANGED,
		EVENT_TERMINALLY_CONSUMED
	}

	public static final class Precondition {
		private final RegionCommitOutcome regionCommitOutcome;
		private final boolean schedulerInstanceExact;
		private final boolean registrationExact;
		private final boolean authoredGenerationExact;
		private final boolean eventExecutionBoundaryHeld;
		private final boolean schedulerStoreBoundaryHeldDuringRegionCommit;
		private final boolean lifecycleBoundaryHeld;
		private final boolean oneShotExecution;
		private final boolean continuingServerTickProgression;
		private final boolean running;
		private final int timesRan;
		private final long lifecycleVersion;
		private final boolean regionMutationPerformed;
		private final boolean desiredStateSatisfied;
		private final boolean callbackInvoked;

		private Precondition(
			final RegionCommitOutcome regionCommitOutcome,
			final boolean schedulerInstanceExact,
			final boolean registrationExact,
			final boolean authoredGenerationExact,
			final boolean eventExecutionBoundaryHeld,
			final boolean schedulerStoreBoundaryHeldDuringRegionCommit,
			final boolean lifecycleBoundaryHeld,
			final boolean oneShotExecution,
			final boolean continuingServerTickProgression,
			final boolean running,
			final int timesRan,
			final long lifecycleVersion,
			final boolean regionMutationPerformed,
			final boolean desiredStateSatisfied,
			final boolean callbackInvoked) {
			this.regionCommitOutcome = Objects.requireNonNull(
				regionCommitOutcome, "regionCommitOutcome");
			if (timesRan < 0 || lifecycleVersion <= 0L) {
				throw new IllegalArgumentException(
					"One-shot consumption precondition is invalid");
			}
			this.schedulerInstanceExact = schedulerInstanceExact;
			this.registrationExact = registrationExact;
			this.authoredGenerationExact = authoredGenerationExact;
			this.eventExecutionBoundaryHeld = eventExecutionBoundaryHeld;
			this.schedulerStoreBoundaryHeldDuringRegionCommit =
				schedulerStoreBoundaryHeldDuringRegionCommit;
			this.lifecycleBoundaryHeld = lifecycleBoundaryHeld;
			this.oneShotExecution = oneShotExecution;
			this.continuingServerTickProgression =
				continuingServerTickProgression;
			this.running = running;
			this.timesRan = timesRan;
			this.lifecycleVersion = lifecycleVersion;
			this.regionMutationPerformed = regionMutationPerformed;
			this.desiredStateSatisfied = desiredStateSatisfied;
			this.callbackInvoked = callbackInvoked;
		}

		public static Precondition declare(
			final RegionCommitOutcome regionCommitOutcome,
			final boolean schedulerInstanceExact,
			final boolean registrationExact,
			final boolean authoredGenerationExact,
			final boolean eventExecutionBoundaryHeld,
			final boolean schedulerStoreBoundaryHeldDuringRegionCommit,
			final boolean lifecycleBoundaryHeld,
			final boolean oneShotExecution,
			final boolean continuingServerTickProgression,
			final boolean running,
			final int timesRan,
			final long lifecycleVersion,
			final boolean regionMutationPerformed,
			final boolean desiredStateSatisfied,
			final boolean callbackInvoked) {
			return new Precondition(
				regionCommitOutcome, schedulerInstanceExact,
				registrationExact, authoredGenerationExact,
				eventExecutionBoundaryHeld,
				schedulerStoreBoundaryHeldDuringRegionCommit,
				lifecycleBoundaryHeld, oneShotExecution,
				continuingServerTickProgression, running, timesRan,
				lifecycleVersion, regionMutationPerformed,
				desiredStateSatisfied, callbackInvoked);
		}

		private boolean isRegionOutcomeConsistent() {
			switch (regionCommitOutcome) {
				case REFUSED:
					return !regionMutationPerformed && !desiredStateSatisfied;
				case NO_OP:
					return !regionMutationPerformed && desiredStateSatisfied;
				case APPLIED:
					return regionMutationPerformed && desiredStateSatisfied;
				default:
					return false;
			}
		}

		public RegionCommitOutcome getRegionCommitOutcome() {
			return regionCommitOutcome;
		}
		public boolean isSchedulerInstanceExact() {
			return schedulerInstanceExact;
		}
		public boolean isRegistrationExact() { return registrationExact; }
		public boolean isAuthoredGenerationExact() {
			return authoredGenerationExact;
		}
		public boolean isEventExecutionBoundaryHeld() {
			return eventExecutionBoundaryHeld;
		}
		public boolean isSchedulerStoreBoundaryHeldDuringRegionCommit() {
			return schedulerStoreBoundaryHeldDuringRegionCommit;
		}
		public boolean isLifecycleBoundaryHeld() {
			return lifecycleBoundaryHeld;
		}
		public boolean isOneShotExecution() { return oneShotExecution; }
		public boolean isContinuingServerTickProgression() {
			return continuingServerTickProgression;
		}
		public boolean isRunning() { return running; }
		public int getTimesRan() { return timesRan; }
		public long getLifecycleVersion() { return lifecycleVersion; }
		public boolean isRegionMutationPerformed() {
			return regionMutationPerformed;
		}
		public boolean isDesiredStateSatisfied() {
			return desiredStateSatisfied;
		}
		public boolean isCallbackInvoked() { return callbackInvoked; }
	}

	public static final class Decision {
		private final RequiredAction requiredAction;
		private final Reason reason;
		private final Precondition precondition;

		private Decision(
			final RequiredAction requiredAction,
			final Reason reason,
			final Precondition precondition) {
			this.requiredAction = Objects.requireNonNull(
				requiredAction, "requiredAction");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.precondition = Objects.requireNonNull(
				precondition, "precondition");
			boolean refusalReason =
				reason != Reason.REFUSAL_RETAINS_SCHEDULED_EVENT
					&& reason
						!= Reason
							.DESIRED_STATE_REQUIRES_TERMINAL_CONSUMPTION;
			if ((requiredAction == RequiredAction.NONE) != refusalReason
				|| (requiredAction == RequiredAction.RETAIN_SCHEDULED)
					!= (reason
						== Reason.REFUSAL_RETAINS_SCHEDULED_EVENT)
				|| (requiredAction == RequiredAction.TERMINALLY_CONSUME)
					!= (reason
						== Reason
							.DESIRED_STATE_REQUIRES_TERMINAL_CONSUMPTION)) {
				throw new IllegalArgumentException(
					"One-shot consumption decision is inconsistent");
			}
		}

		private static Decision refused(
			final Precondition precondition,
			final Reason reason) {
			return new Decision(RequiredAction.NONE, reason, precondition);
		}

		private static Decision required(
			final Precondition precondition,
			final RequiredAction action,
			final Reason reason) {
			return new Decision(action, reason, precondition);
		}

		public RequiredAction getRequiredAction() { return requiredAction; }
		public Reason getReason() { return reason; }
		public boolean isRefused() {
			return requiredAction == RequiredAction.NONE;
		}
		public boolean isPostconditionRequired() { return !isRefused(); }
		private Precondition getPrecondition() { return precondition; }
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isMutationPerformed() { return false; }
		public boolean isCallbackInvoked() { return false; }
		public boolean isEventCancellation() { return false; }
		public boolean isEventReschedule() { return false; }
		public boolean isExecutableRestoration() { return false; }
		public boolean isCommitToken() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}

	public static final class Postcondition {
		private final boolean registrationPresent;
		private final boolean sameRegistrationPresent;
		private final boolean exactRegistrationRemoved;
		private final boolean running;
		private final int timesRan;
		private final long lifecycleVersion;
		private final boolean callbackInvoked;
		private final boolean eventRescheduled;
		private final boolean schedulerStoreBoundaryAcquiredAfterRegionCommit;

		private Postcondition(
			final boolean registrationPresent,
			final boolean sameRegistrationPresent,
			final boolean exactRegistrationRemoved,
			final boolean running,
			final int timesRan,
			final long lifecycleVersion,
			final boolean callbackInvoked,
			final boolean eventRescheduled,
			final boolean schedulerStoreBoundaryAcquiredAfterRegionCommit) {
			if (timesRan < 0 || lifecycleVersion <= 0L
				|| sameRegistrationPresent && !registrationPresent
				|| exactRegistrationRemoved && registrationPresent) {
				throw new IllegalArgumentException(
					"One-shot consumption postcondition is invalid");
			}
			this.registrationPresent = registrationPresent;
			this.sameRegistrationPresent = sameRegistrationPresent;
			this.exactRegistrationRemoved = exactRegistrationRemoved;
			this.running = running;
			this.timesRan = timesRan;
			this.lifecycleVersion = lifecycleVersion;
			this.callbackInvoked = callbackInvoked;
			this.eventRescheduled = eventRescheduled;
			this.schedulerStoreBoundaryAcquiredAfterRegionCommit =
				schedulerStoreBoundaryAcquiredAfterRegionCommit;
		}

		public static Postcondition declare(
			final boolean registrationPresent,
			final boolean sameRegistrationPresent,
			final boolean exactRegistrationRemoved,
			final boolean running,
			final int timesRan,
			final long lifecycleVersion,
			final boolean callbackInvoked,
			final boolean eventRescheduled,
			final boolean schedulerStoreBoundaryAcquiredAfterRegionCommit) {
			return new Postcondition(
				registrationPresent, sameRegistrationPresent,
				exactRegistrationRemoved, running, timesRan,
				lifecycleVersion, callbackInvoked, eventRescheduled,
				schedulerStoreBoundaryAcquiredAfterRegionCommit);
		}

		public boolean isRegistrationPresent() { return registrationPresent; }
		public boolean isSameRegistrationPresent() {
			return sameRegistrationPresent;
		}
		public boolean isExactRegistrationRemoved() {
			return exactRegistrationRemoved;
		}
		public boolean isRunning() { return running; }
		public int getTimesRan() { return timesRan; }
		public long getLifecycleVersion() { return lifecycleVersion; }
		public boolean isCallbackInvoked() { return callbackInvoked; }
		public boolean isEventRescheduled() { return eventRescheduled; }
		public boolean isSchedulerStoreBoundaryAcquiredAfterRegionCommit() {
			return schedulerStoreBoundaryAcquiredAfterRegionCommit;
		}
	}

	public static final class Verification {
		private final boolean satisfied;
		private final VerificationReason reason;

		private Verification(
			final boolean satisfied,
			final VerificationReason reason) {
			this.satisfied = satisfied;
			this.reason = Objects.requireNonNull(reason, "reason");
			if (satisfied != (reason
					== VerificationReason.REFUSAL_RETAINED_UNCHANGED
				|| reason == VerificationReason.EVENT_TERMINALLY_CONSUMED)) {
				throw new IllegalArgumentException(
					"One-shot postcondition verification is inconsistent");
			}
		}

		private static Verification refused(
			final VerificationReason reason) {
			return new Verification(false, reason);
		}
		private static Verification satisfied(
			final VerificationReason reason) {
			return new Verification(true, reason);
		}

		public boolean isSatisfied() { return satisfied; }
		public VerificationReason getReason() { return reason; }
		public boolean isRuntimeHandleRetained() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isMutationPerformed() { return false; }
		public boolean isCallbackInvoked() { return false; }
		public boolean isEventCancellation() { return false; }
		public boolean isEventReschedule() { return false; }
		public boolean isExecutableRestoration() { return false; }
		public boolean isCommitToken() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}
}
