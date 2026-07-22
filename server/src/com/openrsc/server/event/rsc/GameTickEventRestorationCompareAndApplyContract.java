package com.openrsc.server.event.rsc;

import java.util.Objects;

/**
 * Dormant specification for a future in-Region compare-and-apply boundary.
 *
 * <p>This pure contract compares detached declarations and a fresh closed
 * target observation. It does not acquire or prove a lock, retain its intent,
 * inspect a runtime target, or mutate anything. Even an apply-precondition
 * result is not reusable after this method returns.</p>
 */
public final class GameTickEventRestorationCompareAndApplyContract {
	private final Outcome outcome;
	private final Reason reason;
	private final ApplyOperation applyOperation;
	private final RollbackStrategy rollbackStrategy;

	private GameTickEventRestorationCompareAndApplyContract(
		final Outcome outcome,
		final Reason reason,
		final ApplyOperation applyOperation,
		final RollbackStrategy rollbackStrategy) {
		this.outcome = Objects.requireNonNull(outcome, "outcome");
		this.reason = Objects.requireNonNull(reason, "reason");
		this.applyOperation = Objects.requireNonNull(
			applyOperation, "applyOperation");
		this.rollbackStrategy = Objects.requireNonNull(
			rollbackStrategy, "rollbackStrategy");
		boolean apply = outcome == Outcome.APPLY_PRECONDITION_SATISFIED;
		if (apply != (reason == Reason.APPLY_PRECONDITION_REVALIDATED)
			|| apply != (applyOperation != ApplyOperation.NONE)
			|| apply != (rollbackStrategy != RollbackStrategy.NONE)
			|| (outcome == Outcome.REFUSED) != reason.isRefusal()) {
			throw new IllegalArgumentException(
				"Compare-and-apply contract result is inconsistent");
		}
	}

	/** Applies the fail-closed boundary, freshness, and rollback table. */
	public static GameTickEventRestorationCompareAndApplyContract evaluate(
		final GameTickEventRestorationMutationIntent intent,
		final BoundaryDeclaration boundaryDeclaration,
		final FreshTargetObservation freshTargetObservation) {
		GameTickEventRestorationMutationIntent checkedIntent =
			Objects.requireNonNull(intent, "intent");
		BoundaryDeclaration boundary = Objects.requireNonNull(
			boundaryDeclaration, "boundaryDeclaration");
		FreshTargetObservation target = Objects.requireNonNull(
			freshTargetObservation, "freshTargetObservation");
		if (!boundary.isEventExecutionBoundaryHeld()) {
			return refused(Reason.EVENT_EXECUTION_BOUNDARY_MISSING);
		}
		if (boundary.isSchedulerStoreBoundaryHeld()) {
			return refused(Reason.SCHEDULER_STORE_BOUNDARY_HELD);
		}
		if (!boundary.isRegistrationRevalidated()) {
			return refused(Reason.REGISTRATION_NOT_REVALIDATED);
		}
		if (!boundary.getExpectedSchedulerInstanceIdentity().equals(
			boundary.getObservedSchedulerInstanceIdentity())) {
			return refused(Reason.SCHEDULER_INSTANCE_MISMATCH);
		}
		if (boundary.getExpectedRegistrationSequence()
			!= boundary.getObservedRegistrationSequence()) {
			return refused(Reason.REGISTRATION_SEQUENCE_MISMATCH);
		}
		if (boundary.getExpectedProposalGeneration()
			!= boundary.getObservedProposalGeneration()) {
			return refused(Reason.PROPOSAL_GENERATION_MISMATCH);
		}
		if (!boundary.isLifecycleVersionRevalidated()) {
			return refused(Reason.LIFECYCLE_VERSION_NOT_REVALIDATED);
		}
		if (boundary.getExpectedLifecycleVersion()
			!= boundary.getObservedLifecycleVersion()) {
			return refused(Reason.EVENT_LIFECYCLE_VERSION_MISMATCH);
		}
		if (checkedIntent.getAuthoredGeneration()
			!= boundary.getExpectedProposalGeneration()) {
			return refused(Reason.INTENT_GENERATION_MISMATCH);
		}
		if (!boundary.isRegionObjectBoundaryHeld()) {
			return refused(Reason.REGION_OBJECT_BOUNDARY_MISSING);
		}
		if (!target.isObservedInsideRegionBoundary()) {
			return refused(
				Reason.TARGET_NOT_OBSERVED_INSIDE_REGION_BOUNDARY);
		}
		if (!boundary.isTargetComparedAgainstExactIntent()) {
			return refused(Reason.EXACT_INTENT_COMPARISON_MISSING);
		}
		if (!target.matchesClosedClassification()) {
			return refused(Reason.TARGET_EVIDENCE_INCONSISTENT);
		}

		ApplyOperation operation;
		RollbackStrategy rollback;
		switch (checkedIntent.getOperation()) {
			case SCENERY_SPAWN:
				if (target.getObservedTargetState()
					== GameTickEventRestorationTargetDecision
						.ObservedTargetState
						.EXACT_RESTORATION_SCENERY_PRESENT) {
					return noOp();
				}
				if (target.getObservedTargetState()
					!= checkedIntent.getExpectedTargetState()) {
					return refused(Reason.TARGET_CHANGED_SINCE_INTENT);
				}
				if (target.getObservedTargetState()
					== GameTickEventRestorationTargetDecision
						.ObservedTargetState.EMPTY) {
					operation = ApplyOperation.SPAWN_INTO_EMPTY;
					rollback = RollbackStrategy.REMOVE_INSERTED_SCENERY;
				} else if (target.getObservedTargetState()
					== GameTickEventRestorationTargetDecision
						.ObservedTargetState
						.EXACT_AUTHORED_TRANSIENT_PRESENT) {
					if (!boundary.isExactTransientRollbackStateCaptured()) {
						return refused(
							Reason.EXACT_TRANSIENT_ROLLBACK_STATE_MISSING);
					}
					operation = ApplyOperation
						.REPLACE_EXACT_AUTHORED_TRANSIENT;
					rollback = RollbackStrategy
						.RESTORE_EXACT_AUTHORED_TRANSIENT;
				} else {
					return refused(Reason.TARGET_CHANGED_SINCE_INTENT);
				}
				break;
			case SCENERY_REMOVE:
				if (target.getObservedTargetState()
					== GameTickEventRestorationTargetDecision
						.ObservedTargetState.EMPTY) {
					return noOp();
				}
				if (target.getObservedTargetState()
					!= GameTickEventRestorationTargetDecision
						.ObservedTargetState
						.EXACT_RESTORATION_SCENERY_PRESENT
					|| target.getObservedTargetState()
						!= checkedIntent.getExpectedTargetState()) {
					return refused(Reason.TARGET_CHANGED_SINCE_INTENT);
				}
				operation = ApplyOperation
					.REMOVE_EXACT_RESTORATION_SCENERY;
				rollback = RollbackStrategy.RESTORE_REMOVED_SCENERY;
				break;
			default:
				return refused(Reason.INTENT_OPERATION_UNSUPPORTED);
		}
		if (!boundary.isCollisionRollbackAvailable()) {
			return refused(Reason.COLLISION_ROLLBACK_UNAVAILABLE);
		}
		return apply(operation, rollback);
	}

	private static GameTickEventRestorationCompareAndApplyContract refused(
		final Reason reason) {
		if (!reason.isRefusal()) {
			throw new IllegalArgumentException(
				"Non-refusal reason cannot refuse compare-and-apply");
		}
		return new GameTickEventRestorationCompareAndApplyContract(
			Outcome.REFUSED, reason, ApplyOperation.NONE,
			RollbackStrategy.NONE);
	}

	private static GameTickEventRestorationCompareAndApplyContract noOp() {
		return new GameTickEventRestorationCompareAndApplyContract(
			Outcome.NO_OP_SATISFIED,
			Reason.DESIRED_STATE_ALREADY_SATISFIED,
			ApplyOperation.NONE, RollbackStrategy.NONE);
	}

	private static GameTickEventRestorationCompareAndApplyContract apply(
		final ApplyOperation operation,
		final RollbackStrategy rollback) {
		return new GameTickEventRestorationCompareAndApplyContract(
			Outcome.APPLY_PRECONDITION_SATISFIED,
			Reason.APPLY_PRECONDITION_REVALIDATED, operation, rollback);
	}

	public Outcome getOutcome() { return outcome; }
	public Reason getReason() { return reason; }
	public ApplyOperation getApplyOperation() { return applyOperation; }
	public RollbackStrategy getRollbackStrategy() { return rollbackStrategy; }
	public boolean isRefused() { return outcome == Outcome.REFUSED; }
	public boolean isNoOpSatisfied() {
		return outcome == Outcome.NO_OP_SATISFIED;
	}
	public boolean isApplyPreconditionSatisfied() {
		return outcome == Outcome.APPLY_PRECONDITION_SATISFIED;
	}

	public boolean isDormantContract() { return true; }
	public boolean isFreshTargetComparisonRequired() { return true; }
	public boolean isRuntimeComparisonPerformed() { return false; }
	public boolean isIntentRetained() { return false; }
	public boolean isReusablePermit() { return false; }
	public boolean isAtomicityClaimed() { return false; }
	public boolean isMutationAuthorized() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isRollbackPerformed() { return false; }
	public boolean isExecutableRestoration() { return false; }
	public boolean isCommitToken() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum Outcome {
		REFUSED,
		NO_OP_SATISFIED,
		APPLY_PRECONDITION_SATISFIED
	}

	public enum ApplyOperation {
		NONE,
		SPAWN_INTO_EMPTY,
		REPLACE_EXACT_AUTHORED_TRANSIENT,
		REMOVE_EXACT_RESTORATION_SCENERY
	}

	public enum RollbackStrategy {
		NONE,
		REMOVE_INSERTED_SCENERY,
		RESTORE_EXACT_AUTHORED_TRANSIENT,
		RESTORE_REMOVED_SCENERY
	}

	public enum Reason {
		EVENT_EXECUTION_BOUNDARY_MISSING(true),
		SCHEDULER_STORE_BOUNDARY_HELD(true),
		REGISTRATION_NOT_REVALIDATED(true),
		SCHEDULER_INSTANCE_MISMATCH(true),
		REGISTRATION_SEQUENCE_MISMATCH(true),
		PROPOSAL_GENERATION_MISMATCH(true),
		LIFECYCLE_VERSION_NOT_REVALIDATED(true),
		EVENT_LIFECYCLE_VERSION_MISMATCH(true),
		INTENT_GENERATION_MISMATCH(true),
		REGION_OBJECT_BOUNDARY_MISSING(true),
		TARGET_NOT_OBSERVED_INSIDE_REGION_BOUNDARY(true),
		EXACT_INTENT_COMPARISON_MISSING(true),
		TARGET_EVIDENCE_INCONSISTENT(true),
		TARGET_CHANGED_SINCE_INTENT(true),
		EXACT_TRANSIENT_ROLLBACK_STATE_MISSING(true),
		COLLISION_ROLLBACK_UNAVAILABLE(true),
		INTENT_OPERATION_UNSUPPORTED(true),
		DESIRED_STATE_ALREADY_SATISFIED(false),
		APPLY_PRECONDITION_REVALIDATED(false);

		private final boolean refusal;
		Reason(final boolean refusal) { this.refusal = refusal; }
		private boolean isRefusal() { return refusal; }
	}

	/** Detached declarations; the contract compares but never retains them. */
	public static final class BoundaryDeclaration {
		private final String expectedSchedulerInstanceIdentity;
		private final String observedSchedulerInstanceIdentity;
		private final long expectedRegistrationSequence;
		private final long observedRegistrationSequence;
		private final long expectedProposalGeneration;
		private final long observedProposalGeneration;
		private final long expectedLifecycleVersion;
		private final long observedLifecycleVersion;
		private final boolean eventExecutionBoundaryHeld;
		private final boolean schedulerStoreBoundaryHeld;
		private final boolean registrationRevalidated;
		private final boolean lifecycleVersionRevalidated;
		private final boolean regionObjectBoundaryHeld;
		private final boolean targetComparedAgainstExactIntent;
		private final boolean exactTransientRollbackStateCaptured;
		private final boolean collisionRollbackAvailable;

		private BoundaryDeclaration(
			final String expectedSchedulerInstanceIdentity,
			final String observedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long observedRegistrationSequence,
			final long expectedProposalGeneration,
			final long observedProposalGeneration,
			final long expectedLifecycleVersion,
			final long observedLifecycleVersion,
			final boolean eventExecutionBoundaryHeld,
			final boolean schedulerStoreBoundaryHeld,
			final boolean registrationRevalidated,
			final boolean lifecycleVersionRevalidated,
			final boolean regionObjectBoundaryHeld,
			final boolean targetComparedAgainstExactIntent,
			final boolean exactTransientRollbackStateCaptured,
			final boolean collisionRollbackAvailable) {
			if (expectedSchedulerInstanceIdentity == null
				|| expectedSchedulerInstanceIdentity.isEmpty()
				|| observedSchedulerInstanceIdentity == null
				|| observedSchedulerInstanceIdentity.isEmpty()
				|| expectedRegistrationSequence <= 0L
				|| observedRegistrationSequence <= 0L
				|| expectedProposalGeneration <= 0L
				|| observedProposalGeneration <= 0L
				|| expectedLifecycleVersion <= 0L
				|| observedLifecycleVersion <= 0L) {
				throw new IllegalArgumentException(
					"Compare-and-apply boundary declarations are invalid");
			}
			this.expectedSchedulerInstanceIdentity =
				expectedSchedulerInstanceIdentity;
			this.observedSchedulerInstanceIdentity =
				observedSchedulerInstanceIdentity;
			this.expectedRegistrationSequence = expectedRegistrationSequence;
			this.observedRegistrationSequence = observedRegistrationSequence;
			this.expectedProposalGeneration = expectedProposalGeneration;
			this.observedProposalGeneration = observedProposalGeneration;
			this.expectedLifecycleVersion = expectedLifecycleVersion;
			this.observedLifecycleVersion = observedLifecycleVersion;
			this.eventExecutionBoundaryHeld = eventExecutionBoundaryHeld;
			this.schedulerStoreBoundaryHeld = schedulerStoreBoundaryHeld;
			this.registrationRevalidated = registrationRevalidated;
			this.lifecycleVersionRevalidated = lifecycleVersionRevalidated;
			this.regionObjectBoundaryHeld = regionObjectBoundaryHeld;
			this.targetComparedAgainstExactIntent =
				targetComparedAgainstExactIntent;
			this.exactTransientRollbackStateCaptured =
				exactTransientRollbackStateCaptured;
			this.collisionRollbackAvailable = collisionRollbackAvailable;
		}

		public static BoundaryDeclaration declare(
			final String expectedSchedulerInstanceIdentity,
			final String observedSchedulerInstanceIdentity,
			final long expectedRegistrationSequence,
			final long observedRegistrationSequence,
			final long expectedProposalGeneration,
			final long observedProposalGeneration,
			final long expectedLifecycleVersion,
			final long observedLifecycleVersion,
			final boolean eventExecutionBoundaryHeld,
			final boolean schedulerStoreBoundaryHeld,
			final boolean registrationRevalidated,
			final boolean lifecycleVersionRevalidated,
			final boolean regionObjectBoundaryHeld,
			final boolean targetComparedAgainstExactIntent,
			final boolean exactTransientRollbackStateCaptured,
			final boolean collisionRollbackAvailable) {
			return new BoundaryDeclaration(
				expectedSchedulerInstanceIdentity,
				observedSchedulerInstanceIdentity,
				expectedRegistrationSequence, observedRegistrationSequence,
				expectedProposalGeneration, observedProposalGeneration,
				expectedLifecycleVersion, observedLifecycleVersion,
				eventExecutionBoundaryHeld, schedulerStoreBoundaryHeld,
				registrationRevalidated, lifecycleVersionRevalidated,
				regionObjectBoundaryHeld, targetComparedAgainstExactIntent,
				exactTransientRollbackStateCaptured,
				collisionRollbackAvailable);
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
		public long getExpectedLifecycleVersion() {
			return expectedLifecycleVersion;
		}
		public long getObservedLifecycleVersion() {
			return observedLifecycleVersion;
		}
		public boolean isEventExecutionBoundaryHeld() {
			return eventExecutionBoundaryHeld;
		}
		public boolean isSchedulerStoreBoundaryHeld() {
			return schedulerStoreBoundaryHeld;
		}
		public boolean isRegistrationRevalidated() {
			return registrationRevalidated;
		}
		public boolean isLifecycleVersionRevalidated() {
			return lifecycleVersionRevalidated;
		}
		public boolean isRegionObjectBoundaryHeld() {
			return regionObjectBoundaryHeld;
		}
		public boolean isTargetComparedAgainstExactIntent() {
			return targetComparedAgainstExactIntent;
		}
		public boolean isExactTransientRollbackStateCaptured() {
			return exactTransientRollbackStateCaptured;
		}
		public boolean isCollisionRollbackAvailable() {
			return collisionRollbackAvailable;
		}
	}

	/** Fresh closed target counts declared for the dormant specification. */
	public static final class FreshTargetObservation {
		private final GameTickEventRestorationTargetDecision.ObservedTargetState
			observedTargetState;
		private final int slotObjectCount;
		private final int exactRestorationSceneryCount;
		private final int exactAuthoredIdentityCount;
		private final boolean observedInsideRegionBoundary;

		private FreshTargetObservation(
			final GameTickEventRestorationTargetDecision.ObservedTargetState
				observedTargetState,
			final int slotObjectCount,
			final int exactRestorationSceneryCount,
			final int exactAuthoredIdentityCount,
			final boolean observedInsideRegionBoundary) {
			if (slotObjectCount < 0
				|| exactRestorationSceneryCount < 0
				|| exactRestorationSceneryCount > slotObjectCount
				|| exactAuthoredIdentityCount < 0
				|| exactAuthoredIdentityCount > slotObjectCount) {
				throw new IllegalArgumentException(
					"Fresh target observation counts are invalid");
			}
			this.observedTargetState = Objects.requireNonNull(
				observedTargetState, "observedTargetState");
			this.slotObjectCount = slotObjectCount;
			this.exactRestorationSceneryCount =
				exactRestorationSceneryCount;
			this.exactAuthoredIdentityCount = exactAuthoredIdentityCount;
			this.observedInsideRegionBoundary =
				observedInsideRegionBoundary;
		}

		public static FreshTargetObservation observe(
			final GameTickEventRestorationTargetDecision.ObservedTargetState
				observedTargetState,
			final int slotObjectCount,
			final int exactRestorationSceneryCount,
			final int exactAuthoredIdentityCount,
			final boolean observedInsideRegionBoundary) {
			return new FreshTargetObservation(
				observedTargetState, slotObjectCount,
				exactRestorationSceneryCount, exactAuthoredIdentityCount,
				observedInsideRegionBoundary);
		}

		public GameTickEventRestorationTargetDecision.ObservedTargetState
			getObservedTargetState() { return observedTargetState; }
		public int getSlotObjectCount() { return slotObjectCount; }
		public int getExactRestorationSceneryCount() {
			return exactRestorationSceneryCount;
		}
		public int getExactAuthoredIdentityCount() {
			return exactAuthoredIdentityCount;
		}
		public boolean isObservedInsideRegionBoundary() {
			return observedInsideRegionBoundary;
		}

		private boolean matchesClosedClassification() {
			if (observedTargetState
				== GameTickEventRestorationTargetDecision.ObservedTargetState
					.UNAVAILABLE) {
				return slotObjectCount == 0
					&& exactRestorationSceneryCount == 0
					&& exactAuthoredIdentityCount == 0;
			}
			GameTickEventRestorationTargetDecision.ObservedTargetState derived;
			if (slotObjectCount == 0) {
				derived = GameTickEventRestorationTargetDecision
					.ObservedTargetState.EMPTY;
			} else if (slotObjectCount > 1) {
				derived = GameTickEventRestorationTargetDecision
					.ObservedTargetState.AMBIGUOUS_OCCUPANCY;
			} else if (exactRestorationSceneryCount == 1
				&& exactAuthoredIdentityCount == 1) {
				derived = GameTickEventRestorationTargetDecision
					.ObservedTargetState
					.EXACT_RESTORATION_SCENERY_PRESENT;
			} else if (exactAuthoredIdentityCount == 1) {
				derived = GameTickEventRestorationTargetDecision
					.ObservedTargetState
					.EXACT_AUTHORED_TRANSIENT_PRESENT;
			} else {
				derived = GameTickEventRestorationTargetDecision
					.ObservedTargetState
					.MISMATCHED_OR_IDENTITYLESS_OCCUPANT;
			}
			return derived == observedTargetState;
		}
	}
}
