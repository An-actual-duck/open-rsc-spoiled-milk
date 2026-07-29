package com.openrsc.server.event.rsc;

import java.util.Objects;

/**
 * Dormant, immutable description of one exact authored scenery mutation.
 *
 * <p>An intent is created only when detached runtime evidence reports a stable
 * event lifecycle, a real Region object-boundary classification, and a
 * satisfied Slice 118 mutation-precondition contract. The value copies only
 * constructor, desired-state, and authored-identity scalars. It deliberately
 * retains no scheduler identity, registration sequence, lifecycle version,
 * event, callback, World, Region, entity, collection, monitor, owner, runtime
 * attribute, key, or UUID.</p>
 *
 * <p>This is not a permit. The evidence used to create it is stale after the
 * Region boundary releases, and no runtime path consumes this value.</p>
 */
public final class GameTickEventRestorationMutationIntent {
	private final Operation operation;
	private final DesiredState desiredState;
	private final GameTickEventRestorationTargetDecision.ObservedTargetState
		expectedTargetState;
	private final int objectId;
	private final int permanentObjectId;
	private final int x;
	private final int y;
	private final int direction;
	private final int type;
	private final boolean forceFullBlock;
	private final long authoredGeneration;
	private final int authoredPackedRegionX;
	private final int authoredPackedRegionY;
	private final int authoredSourceOrdinal;
	private final AuthoredConstructionKind authoredConstructionKind;

	private GameTickEventRestorationMutationIntent(
		final Operation operation,
		final DesiredState desiredState,
		final GameTickEventRestorationTargetDecision.ObservedTargetState
			expectedTargetState,
		final GameTickEventRestorationTargetRevalidationRequest request,
		final AuthoredConstructionKind authoredConstructionKind) {
		this.operation = Objects.requireNonNull(operation, "operation");
		this.desiredState = Objects.requireNonNull(
			desiredState, "desiredState");
		this.expectedTargetState = Objects.requireNonNull(
			expectedTargetState, "expectedTargetState");
		GameTickEventRestorationTargetRevalidationRequest checked =
			Objects.requireNonNull(request, "request");
		this.authoredConstructionKind = Objects.requireNonNull(
			authoredConstructionKind, "authoredConstructionKind");
		this.objectId = checked.getObjectId();
		this.permanentObjectId = checked.getPermanentObjectId();
		this.x = checked.getX();
		this.y = checked.getY();
		this.direction = checked.getDirection();
		this.type = checked.getType();
		this.forceFullBlock = checked.isForceFullBlock();
		this.authoredGeneration = checked.getAuthoredGeneration();
		this.authoredPackedRegionX = checked.getAuthoredPackedRegionX();
		this.authoredPackedRegionY = checked.getAuthoredPackedRegionY();
		this.authoredSourceOrdinal = checked.getAuthoredSourceOrdinal();
	}

	/**
	 * Applies a closed refusal table to one detached stable revalidation.
	 * Refused and no-op outcomes never construct an intent.
	 */
	public static Creation assess(
		final GameTickEventRestorationTargetRevalidationRequest request,
		final GameTickEventRestorationTargetRevalidation revalidation,
		final long lifecycleVersionBeforeOperation,
		final long lifecycleVersionAfterOperation) {
		GameTickEventRestorationTargetRevalidationRequest checkedRequest =
			Objects.requireNonNull(request, "request");
		GameTickEventRestorationTargetRevalidation checkedRevalidation =
			Objects.requireNonNull(revalidation, "revalidation");
		if (lifecycleVersionBeforeOperation <= 0L
			|| lifecycleVersionAfterOperation <= 0L) {
			return Creation.refused(Reason.INVALID_LIFECYCLE_VERSION);
		}
		if (lifecycleVersionBeforeOperation
			!= lifecycleVersionAfterOperation) {
			return Creation.refused(Reason.EVENT_LIFECYCLE_CHANGED);
		}
		if (!checkedRevalidation.isRegionAvailable()) {
			return Creation.refused(Reason.REGION_UNAVAILABLE);
		}
		if (!checkedRevalidation
			.isObjectBoundaryHeldDuringClassification()) {
			return Creation.refused(Reason.REGION_OBJECT_BOUNDARY_MISSING);
		}
		GameTickEventRestorationAtomicRevalidationContract contract =
			checkedRevalidation.getContract();
		if (contract.isRefused()) {
			return Creation.refused(Reason.CONTRACT_REFUSED);
		}
		if (contract.isNoOpContractSatisfied()) {
			return Creation.refused(
				Reason.DESIRED_STATE_ALREADY_SATISFIED);
		}
		if (!contract.isMutationPreconditionContractSatisfied()) {
			return Creation.refused(Reason.CONTRACT_OUTCOME_UNSUPPORTED);
		}
		GameTickEventRestorationTargetDecision target =
			checkedRevalidation.getTargetDecision();
		if (!targetEvidenceMatchesCounts(checkedRevalidation)) {
			return Creation.refused(Reason.TARGET_EVIDENCE_INCONSISTENT);
		}

		Operation operation;
		DesiredState desiredState;
		switch (checkedRequest.getTargetOperation()) {
			case SCENERY_SPAWN:
				if (!matchesSpawnMutationPrecondition(target)) {
					return Creation.refused(
						Reason.TARGET_OPERATION_OUTCOME_MISMATCH);
				}
				operation = Operation.SCENERY_SPAWN;
				desiredState = DesiredState.PRESENT;
				break;
			case SCENERY_REMOVE:
				if (!matchesRemovalMutationPrecondition(target)) {
					return Creation.refused(
						Reason.TARGET_OPERATION_OUTCOME_MISMATCH);
				}
				operation = Operation.SCENERY_REMOVE;
				desiredState = DesiredState.ABSENT;
				break;
			default:
				return Creation.refused(
					Reason.TARGET_OPERATION_OUTCOME_MISMATCH);
		}

		AuthoredConstructionKind constructionKind;
		try {
			constructionKind = AuthoredConstructionKind.valueOf(
				checkedRequest.getAuthoredConstructionKind());
		} catch (IllegalArgumentException unsupported) {
			return Creation.refused(
				Reason.AUTHORED_CONSTRUCTION_KIND_UNSUPPORTED);
		}
		if (!matchesConstructionKind(
			constructionKind, checkedRequest.getType())) {
			return Creation.refused(
				Reason.AUTHORED_CONSTRUCTION_KIND_MISMATCH);
		}
		return Creation.available(
			new GameTickEventRestorationMutationIntent(
				operation, desiredState, target.getObservedTargetState(),
				checkedRequest, constructionKind));
	}

	private static boolean targetEvidenceMatchesCounts(
		final GameTickEventRestorationTargetRevalidation revalidation) {
		switch (revalidation.getObservedTargetState()) {
			case EMPTY:
				return revalidation.getSlotObjectCount() == 0
					&& revalidation.getExactRestorationSceneryCount() == 0
					&& revalidation.getExactAuthoredIdentityCount() == 0;
			case EXACT_RESTORATION_SCENERY_PRESENT:
				return revalidation.getSlotObjectCount() == 1
					&& revalidation.getExactRestorationSceneryCount() == 1
					&& revalidation.getExactAuthoredIdentityCount() == 1;
			case EXACT_AUTHORED_TRANSIENT_PRESENT:
				return revalidation.getSlotObjectCount() == 1
					&& revalidation.getExactRestorationSceneryCount() == 0
					&& revalidation.getExactAuthoredIdentityCount() == 1;
			default:
				return false;
		}
	}

	private static boolean matchesSpawnMutationPrecondition(
		final GameTickEventRestorationTargetDecision target) {
		if (!target.isMutationPreconditionSatisfied()) { return false; }
		switch (target.getReason()) {
			case SPAWN_DESTINATION_EMPTY:
				return target.getObservedTargetState()
					== GameTickEventRestorationTargetDecision
						.ObservedTargetState.EMPTY;
			case EXACT_AUTHORED_TRANSIENT_PRESENT:
				return target.getObservedTargetState()
					== GameTickEventRestorationTargetDecision
						.ObservedTargetState
						.EXACT_AUTHORED_TRANSIENT_PRESENT;
			default:
				return false;
		}
	}

	private static boolean matchesRemovalMutationPrecondition(
		final GameTickEventRestorationTargetDecision target) {
		return target.isMutationPreconditionSatisfied()
			&& target.getReason()
				== GameTickEventRestorationTargetDecision.Reason
					.EXACT_REMOVAL_TARGET_PRESENT
			&& target.getObservedTargetState()
				== GameTickEventRestorationTargetDecision.ObservedTargetState
					.EXACT_RESTORATION_SCENERY_PRESENT;
	}

	private static boolean matchesConstructionKind(
		final AuthoredConstructionKind kind,
		final int objectType) {
		return objectType == 0
			? kind == AuthoredConstructionKind.SCENERY
				|| kind == AuthoredConstructionKind.HARVESTING_SCENERY
			: objectType == 1
				&& kind == AuthoredConstructionKind.BOUNDARY;
	}

	public Operation getOperation() { return operation; }
	public DesiredState getDesiredState() { return desiredState; }
	public GameTickEventRestorationTargetDecision.ObservedTargetState
		getExpectedTargetState() { return expectedTargetState; }
	public int getObjectId() { return objectId; }
	public int getPermanentObjectId() { return permanentObjectId; }
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDirection() { return direction; }
	public int getType() { return type; }
	public boolean isForceFullBlock() { return forceFullBlock; }
	public long getAuthoredGeneration() { return authoredGeneration; }
	public int getAuthoredPackedRegionX() { return authoredPackedRegionX; }
	public int getAuthoredPackedRegionY() { return authoredPackedRegionY; }
	public int getAuthoredSourceOrdinal() { return authoredSourceOrdinal; }
	public AuthoredConstructionKind getAuthoredConstructionKind() {
		return authoredConstructionKind;
	}

	public boolean isDormantIntent() { return true; }
	public boolean isStaleAfterBoundaryRelease() { return true; }
	public boolean isReusablePermit() { return false; }
	public boolean isMutationAuthorized() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isExecutableRestoration() { return false; }
	public boolean isCommitToken() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum Operation {
		SCENERY_SPAWN,
		SCENERY_REMOVE
	}

	public enum DesiredState {
		PRESENT,
		ABSENT
	}

	public enum AuthoredConstructionKind {
		SCENERY,
		BOUNDARY,
		NPC_SPAWN,
		GROUND_ITEM_SPAWN,
		HARVESTING_SCENERY
	}

	public enum Outcome {
		REFUSED,
		INTENT_AVAILABLE
	}

	public enum Reason {
		INVALID_LIFECYCLE_VERSION,
		EVENT_LIFECYCLE_CHANGED,
		REGION_UNAVAILABLE,
		REGION_OBJECT_BOUNDARY_MISSING,
		CONTRACT_REFUSED,
		DESIRED_STATE_ALREADY_SATISFIED,
		CONTRACT_OUTCOME_UNSUPPORTED,
		TARGET_EVIDENCE_INCONSISTENT,
		TARGET_OPERATION_OUTCOME_MISMATCH,
		AUTHORED_CONSTRUCTION_KIND_UNSUPPORTED,
		AUTHORED_CONSTRUCTION_KIND_MISMATCH,
		INTENT_AVAILABLE
	}

	/** Result of the pure refusal table; never a commit or mutation token. */
	public static final class Creation {
		private final Outcome outcome;
		private final Reason reason;
		private final GameTickEventRestorationMutationIntent intent;

		private Creation(
			final Outcome outcome,
			final Reason reason,
			final GameTickEventRestorationMutationIntent intent) {
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.intent = intent;
			if ((outcome == Outcome.INTENT_AVAILABLE) != (intent != null)
				|| (outcome == Outcome.INTENT_AVAILABLE)
					!= (reason == Reason.INTENT_AVAILABLE)) {
				throw new IllegalArgumentException(
					"Mutation-intent creation result is inconsistent");
			}
		}

		private static Creation refused(final Reason reason) {
			if (reason == Reason.INTENT_AVAILABLE) {
				throw new IllegalArgumentException(
					"Available reason cannot refuse mutation intent");
			}
			return new Creation(Outcome.REFUSED, reason, null);
		}

		private static Creation available(
			final GameTickEventRestorationMutationIntent intent) {
			return new Creation(
				Outcome.INTENT_AVAILABLE, Reason.INTENT_AVAILABLE,
				Objects.requireNonNull(intent, "intent"));
		}

		public Outcome getOutcome() { return outcome; }
		public Reason getReason() { return reason; }
		public GameTickEventRestorationMutationIntent getIntent() {
			return intent;
		}
		public boolean isRefused() { return outcome == Outcome.REFUSED; }
		public boolean isIntentAvailable() {
			return outcome == Outcome.INTENT_AVAILABLE;
		}
		public boolean isReusablePermit() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isMutationPerformed() { return false; }
		public boolean isExecutableRestoration() { return false; }
		public boolean isCommitToken() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}
}
