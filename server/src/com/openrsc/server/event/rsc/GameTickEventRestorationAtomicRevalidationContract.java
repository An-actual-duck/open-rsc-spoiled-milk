package com.openrsc.server.event.rsc;

import java.util.Objects;

/**
 * Dormant, pure specification for a future restoration mutation boundary.
 *
 * <p>This class evaluates explicitly supplied scalar declarations. It neither
 * acquires nor proves a lock, looks up an event or target, retains a runtime
 * handle, or authorizes mutation. A future runtime seam must validate scheduler
 * identity and registration before entering the Region object boundary, keep
 * the event execution boundary held across that transition, never carry the
 * scheduler-store lock into the Region boundary, and classify the exact target
 * again inside that Region boundary.</p>
 */
public final class GameTickEventRestorationAtomicRevalidationContract {
	private final Outcome outcome;
	private final Reason reason;
	private final GameTickEventRestorationTargetDecision.Outcome targetOutcome;
	private final GameTickEventRestorationTargetDecision.Reason targetReason;

	private GameTickEventRestorationAtomicRevalidationContract(
		final Outcome outcome,
		final Reason reason,
		final GameTickEventRestorationTargetDecision.Outcome targetOutcome,
		final GameTickEventRestorationTargetDecision.Reason targetReason) {
		this.outcome = Objects.requireNonNull(outcome, "outcome");
		this.reason = Objects.requireNonNull(reason, "reason");
		this.targetOutcome = Objects.requireNonNull(
			targetOutcome, "targetOutcome");
		this.targetReason = Objects.requireNonNull(targetReason, "targetReason");
		if ((outcome == Outcome.REFUSED) != reason.isRefusal()) {
			throw new IllegalArgumentException(
				"Atomic-revalidation contract outcome and reason disagree");
		}
	}

	/**
	 * Applies the required boundary order to detached declarations only.
	 * Refusals are deliberately ordered so an unsafe lock relationship cannot
	 * be hidden by otherwise matching identity or target evidence.
	 */
	public static GameTickEventRestorationAtomicRevalidationContract evaluate(
		final BoundaryDeclaration boundaryDeclaration,
		final GameTickEventRestorationTargetDecision targetDecision) {
		BoundaryDeclaration boundary = Objects.requireNonNull(
			boundaryDeclaration, "boundaryDeclaration");
		GameTickEventRestorationTargetDecision target =
			Objects.requireNonNull(targetDecision, "targetDecision");
		if (!boundary.isEventExecutionBoundaryHeld()) {
			return refused(Reason.EVENT_EXECUTION_BOUNDARY_MISSING, target);
		}
		if (boundary.isSchedulerStoreBoundaryHeld()) {
			return refused(Reason.SCHEDULER_STORE_BOUNDARY_HELD, target);
		}
		if (!boundary.isRegistrationValidatedBeforeRegionBoundary()) {
			return refused(
				Reason.REGISTRATION_NOT_VALIDATED_BEFORE_REGION_BOUNDARY,
				target);
		}
		if (!boundary.getExpectedSchedulerInstanceIdentity().equals(
			boundary.getObservedSchedulerInstanceIdentity())) {
			return refused(Reason.SCHEDULER_INSTANCE_MISMATCH, target);
		}
		if (boundary.getExpectedRegistrationSequence()
			!= boundary.getObservedRegistrationSequence()) {
			return refused(Reason.REGISTRATION_SEQUENCE_MISMATCH, target);
		}
		if (boundary.getExpectedProposalGeneration()
			!= boundary.getObservedProposalGeneration()) {
			return refused(Reason.PROPOSAL_GENERATION_MISMATCH, target);
		}
		if (!boundary.isRegionObjectBoundaryHeld()) {
			return refused(Reason.REGION_OBJECT_BOUNDARY_MISSING, target);
		}
		if (!boundary.isTargetObservedInsideRegionBoundary()) {
			return refused(
				Reason.TARGET_NOT_OBSERVED_INSIDE_REGION_BOUNDARY, target);
		}
		if (target.isRefused()) {
			return refused(Reason.TARGET_DECISION_REFUSED, target);
		}
		if (target.isNoOpSuccess()) {
			return satisfied(
				Outcome.NO_OP_CONTRACT_SATISFIED,
				Reason.DESIRED_STATE_ALREADY_SATISFIED, target);
		}
		if (target.isMutationPreconditionSatisfied()) {
			return satisfied(
				Outcome.MUTATION_PRECONDITION_CONTRACT_SATISFIED,
				Reason.MUTATION_PRECONDITION_REVALIDATED, target);
		}
		throw new IllegalStateException("Unhandled target-decision outcome");
	}

	private static GameTickEventRestorationAtomicRevalidationContract refused(
		final Reason reason,
		final GameTickEventRestorationTargetDecision target) {
		return new GameTickEventRestorationAtomicRevalidationContract(
			Outcome.REFUSED, reason, target.getOutcome(), target.getReason());
	}

	private static GameTickEventRestorationAtomicRevalidationContract satisfied(
		final Outcome outcome,
		final Reason reason,
		final GameTickEventRestorationTargetDecision target) {
		return new GameTickEventRestorationAtomicRevalidationContract(
			outcome, reason, target.getOutcome(), target.getReason());
	}

	public Outcome getOutcome() { return outcome; }
	public Reason getReason() { return reason; }
	public GameTickEventRestorationTargetDecision.Outcome getTargetOutcome() {
		return targetOutcome;
	}
	public GameTickEventRestorationTargetDecision.Reason getTargetReason() {
		return targetReason;
	}
	public boolean isRefused() { return outcome == Outcome.REFUSED; }
	public boolean isNoOpContractSatisfied() {
		return outcome == Outcome.NO_OP_CONTRACT_SATISFIED;
	}
	public boolean isMutationPreconditionContractSatisfied() {
		return outcome == Outcome.MUTATION_PRECONDITION_CONTRACT_SATISFIED;
	}

	public boolean isDormantContract() { return true; }
	public boolean isEventExecutionBoundaryRequired() { return true; }
	public boolean isRegionObjectBoundaryRequired() { return true; }
	public boolean isSchedulerStoreBoundaryForbidden() { return true; }
	public boolean isTargetRevalidationRequired() { return true; }
	public boolean isRuntimeRevalidationPerformed() { return false; }
	public boolean isAtomicityClaimed() { return false; }
	public boolean isEntityHandleRetained() { return false; }
	public boolean isMutationAuthorized() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isExecutableRestoration() { return false; }
	public boolean isCommitToken() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum Outcome {
		REFUSED,
		NO_OP_CONTRACT_SATISFIED,
		MUTATION_PRECONDITION_CONTRACT_SATISFIED
	}

	public enum Reason {
		EVENT_EXECUTION_BOUNDARY_MISSING(true),
		SCHEDULER_STORE_BOUNDARY_HELD(true),
		REGISTRATION_NOT_VALIDATED_BEFORE_REGION_BOUNDARY(true),
		SCHEDULER_INSTANCE_MISMATCH(true),
		REGISTRATION_SEQUENCE_MISMATCH(true),
		PROPOSAL_GENERATION_MISMATCH(true),
		REGION_OBJECT_BOUNDARY_MISSING(true),
		TARGET_NOT_OBSERVED_INSIDE_REGION_BOUNDARY(true),
		TARGET_DECISION_REFUSED(true),
		DESIRED_STATE_ALREADY_SATISFIED(false),
		MUTATION_PRECONDITION_REVALIDATED(false);

		private final boolean refusal;

		Reason(final boolean refusal) { this.refusal = refusal; }
		private boolean isRefusal() { return refusal; }
	}

	/**
	 * Detached declarations consumed by the dormant contract. Tokens and
	 * sequences are compared but never retained by the result.
	 */
	public static final class BoundaryDeclaration {
		private final String expectedSchedulerInstanceIdentity;
		private final String observedSchedulerInstanceIdentity;
		private final long expectedRegistrationSequence;
		private final long observedRegistrationSequence;
		private final long expectedProposalGeneration;
		private final long observedProposalGeneration;
		private final boolean eventExecutionBoundaryHeld;
		private final boolean schedulerStoreBoundaryHeld;
		private final boolean registrationValidatedBeforeRegionBoundary;
		private final boolean regionObjectBoundaryHeld;
		private final boolean targetObservedInsideRegionBoundary;

		private BoundaryDeclaration(
			final String expectedSchedulerInstanceIdentity,
			final String observedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long observedRegistrationSequence,
			final long expectedProposalGeneration,
			final long observedProposalGeneration,
			final boolean eventExecutionBoundaryHeld,
			final boolean schedulerStoreBoundaryHeld,
			final boolean registrationValidatedBeforeRegionBoundary,
			final boolean regionObjectBoundaryHeld,
			final boolean targetObservedInsideRegionBoundary) {
			if (expectedSchedulerInstanceIdentity == null
				|| expectedSchedulerInstanceIdentity.isEmpty()
				|| observedSchedulerInstanceIdentity == null
				|| observedSchedulerInstanceIdentity.isEmpty()
				|| expectedRegistrationSequence <= 0L
				|| observedRegistrationSequence <= 0L
				|| expectedProposalGeneration <= 0L
				|| observedProposalGeneration <= 0L) {
				throw new IllegalArgumentException(
					"Atomic-revalidation boundary declarations are invalid");
			}
			this.expectedSchedulerInstanceIdentity =
				expectedSchedulerInstanceIdentity;
			this.observedSchedulerInstanceIdentity =
				observedSchedulerInstanceIdentity;
			this.expectedRegistrationSequence = expectedRegistrationSequence;
			this.observedRegistrationSequence = observedRegistrationSequence;
			this.expectedProposalGeneration = expectedProposalGeneration;
			this.observedProposalGeneration = observedProposalGeneration;
			this.eventExecutionBoundaryHeld = eventExecutionBoundaryHeld;
			this.schedulerStoreBoundaryHeld = schedulerStoreBoundaryHeld;
			this.registrationValidatedBeforeRegionBoundary =
				registrationValidatedBeforeRegionBoundary;
			this.regionObjectBoundaryHeld = regionObjectBoundaryHeld;
			this.targetObservedInsideRegionBoundary =
				targetObservedInsideRegionBoundary;
		}

		public static BoundaryDeclaration declare(
			final String expectedSchedulerInstanceIdentity,
			final String observedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long observedRegistrationSequence,
			final long expectedProposalGeneration,
			final long observedProposalGeneration,
			final boolean eventExecutionBoundaryHeld,
			final boolean schedulerStoreBoundaryHeld,
			final boolean registrationValidatedBeforeRegionBoundary,
			final boolean regionObjectBoundaryHeld,
			final boolean targetObservedInsideRegionBoundary) {
			return new BoundaryDeclaration(
				expectedSchedulerInstanceIdentity,
				observedSchedulerInstanceIdentity,
				expectedRegistrationSequence, observedRegistrationSequence,
				expectedProposalGeneration, observedProposalGeneration,
				eventExecutionBoundaryHeld, schedulerStoreBoundaryHeld,
				registrationValidatedBeforeRegionBoundary,
				regionObjectBoundaryHeld,
				targetObservedInsideRegionBoundary);
		}

		public String getExpectedSchedulerInstanceIdentity() {
			return expectedSchedulerInstanceIdentity;
		}
		public String getObservedSchedulerInstanceIdentity() {
			return observedSchedulerInstanceIdentity;
		}
		public long getExpectedRegistrationSequence() {
			return expectedRegistrationSequence;
		}
		public long getObservedRegistrationSequence() {
			return observedRegistrationSequence;
		}
		public long getExpectedProposalGeneration() {
			return expectedProposalGeneration;
		}
		public long getObservedProposalGeneration() {
			return observedProposalGeneration;
		}
		public boolean isEventExecutionBoundaryHeld() {
			return eventExecutionBoundaryHeld;
		}
		public boolean isSchedulerStoreBoundaryHeld() {
			return schedulerStoreBoundaryHeld;
		}
		public boolean isRegistrationValidatedBeforeRegionBoundary() {
			return registrationValidatedBeforeRegionBoundary;
		}
		public boolean isRegionObjectBoundaryHeld() {
			return regionObjectBoundaryHeld;
		}
		public boolean isTargetObservedInsideRegionBoundary() {
			return targetObservedInsideRegionBoundary;
		}
	}
}
