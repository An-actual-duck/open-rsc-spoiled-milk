package com.openrsc.server.event.rsc;

import java.util.Objects;

/**
 * Pure, dormant classification of one explicitly supplied target observation.
 *
 * <p>The classifier receives an already detached observation category. It does
 * not find a World, Region, tile, entity, event, callback, scheduler, registry,
 * packet, or Player. A mutation-precondition result is evidence for a later
 * design; it is not mutation authority and cannot execute restoration.</p>
 */
public final class GameTickEventRestorationTargetDecision {
	private final Outcome outcome;
	private final Reason reason;
	private final ObservedTargetState observedTargetState;

	private GameTickEventRestorationTargetDecision(
		final Outcome outcome,
		final Reason reason,
		final ObservedTargetState observedTargetState) {
		this.outcome = Objects.requireNonNull(outcome, "outcome");
		this.reason = Objects.requireNonNull(reason, "reason");
		this.observedTargetState = Objects.requireNonNull(
			observedTargetState, "observedTargetState");
		if ((outcome == Outcome.REFUSED)
			!= reason.isRefusal()) {
			throw new IllegalArgumentException(
				"Target-decision outcome and reason disagree");
		}
	}

	/**
	 * Classifies detached evidence after binding and generation checks. Binding
	 * or generation failure takes precedence over occupancy so stale evidence
	 * cannot be interpreted as a usable target.
	 */
	public static GameTickEventRestorationTargetDecision decide(
		final GameTickEventRestorationRequirement requirement,
		final long reconstructionGeneration,
		final ObservedTargetState observedTargetState) {
		GameTickEventRestorationRequirement checked =
			Objects.requireNonNull(requirement, "requirement");
		ObservedTargetState observation = Objects.requireNonNull(
			observedTargetState, "observedTargetState");
		if (reconstructionGeneration <= 0L) {
			throw new IllegalArgumentException(
				"Reconstruction generation must be positive");
		}
		if (checked.getTargetSubject()
			== GameTickEventRestorationRequirement.TargetSubject.UNAVAILABLE) {
			return refused(Reason.REQUIREMENT_UNAVAILABLE, observation);
		}
		if (!checked.isTargetBindingComplete()) {
			return refused(Reason.TARGET_BINDING_INCOMPLETE, observation);
		}
		if (checked.getAuthoredTarget().getGeneration()
			!= reconstructionGeneration) {
			return refused(Reason.GENERATION_MISMATCH, observation);
		}
		if (observation == ObservedTargetState.UNAVAILABLE) {
			return refused(Reason.TARGET_OBSERVATION_UNAVAILABLE, observation);
		}

		switch (checked.getTargetSubject()) {
			case AUTHORED_DESTINATION_SLOT:
				return decideSpawn(observation);
			case AUTHORED_EXISTING_ENTITY:
				return decideRemoval(observation);
			default:
				return refused(Reason.REQUIREMENT_UNAVAILABLE, observation);
		}
	}

	private static GameTickEventRestorationTargetDecision decideSpawn(
		final ObservedTargetState observation) {
		switch (observation) {
			case EMPTY:
				return mutationPrecondition(
					Reason.SPAWN_DESTINATION_EMPTY, observation);
			case EXACT_RESTORATION_SCENERY_PRESENT:
				return noOp(
					Reason.DESIRED_PRESENT_STATE_ALREADY_SATISFIED,
					observation);
			case EXACT_AUTHORED_TRANSIENT_PRESENT:
				return mutationPrecondition(
					Reason.EXACT_AUTHORED_TRANSIENT_PRESENT, observation);
			case MISMATCHED_OR_IDENTITYLESS_OCCUPANT:
				return refused(
					Reason.MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
					observation);
			case AMBIGUOUS_OCCUPANCY:
				return refused(Reason.AMBIGUOUS_OCCUPANCY, observation);
			default:
				return refused(
					Reason.TARGET_OBSERVATION_UNAVAILABLE, observation);
		}
	}

	private static GameTickEventRestorationTargetDecision decideRemoval(
		final ObservedTargetState observation) {
		switch (observation) {
			case EMPTY:
				return noOp(
					Reason.DESIRED_ABSENT_STATE_ALREADY_SATISFIED,
					observation);
			case EXACT_RESTORATION_SCENERY_PRESENT:
				return mutationPrecondition(
					Reason.EXACT_REMOVAL_TARGET_PRESENT, observation);
			case EXACT_AUTHORED_TRANSIENT_PRESENT:
				return refused(
					Reason.REMOVAL_TARGET_CHANGED_TO_AUTHORED_TRANSIENT,
					observation);
			case MISMATCHED_OR_IDENTITYLESS_OCCUPANT:
				return refused(
					Reason.MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
					observation);
			case AMBIGUOUS_OCCUPANCY:
				return refused(Reason.AMBIGUOUS_OCCUPANCY, observation);
			default:
				return refused(
					Reason.TARGET_OBSERVATION_UNAVAILABLE, observation);
		}
	}

	private static GameTickEventRestorationTargetDecision noOp(
		final Reason reason,
		final ObservedTargetState observation) {
		return new GameTickEventRestorationTargetDecision(
			Outcome.NO_OP_SUCCESS, reason, observation);
	}

	private static GameTickEventRestorationTargetDecision mutationPrecondition(
		final Reason reason,
		final ObservedTargetState observation) {
		return new GameTickEventRestorationTargetDecision(
			Outcome.MUTATION_PRECONDITION_SATISFIED, reason, observation);
	}

	private static GameTickEventRestorationTargetDecision refused(
		final Reason reason,
		final ObservedTargetState observation) {
		return new GameTickEventRestorationTargetDecision(
			Outcome.REFUSED, reason, observation);
	}

	public Outcome getOutcome() { return outcome; }
	public Reason getReason() { return reason; }
	public ObservedTargetState getObservedTargetState() {
		return observedTargetState;
	}
	public boolean isNoOpSuccess() {
		return outcome == Outcome.NO_OP_SUCCESS;
	}
	public boolean isMutationPreconditionSatisfied() {
		return outcome == Outcome.MUTATION_PRECONDITION_SATISFIED;
	}
	public boolean isRefused() { return outcome == Outcome.REFUSED; }

	public boolean isDetachedObservationClassification() { return true; }
	public boolean isRuntimeTargetLookupPerformed() { return false; }
	public boolean isRuntimeTargetStateInspected() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isExecutableRestoration() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum Outcome {
		REFUSED,
		NO_OP_SUCCESS,
		MUTATION_PRECONDITION_SATISFIED
	}

	/** Detached category supplied by a later read-only observation seam. */
	public enum ObservedTargetState {
		UNAVAILABLE,
		EMPTY,
		EXACT_RESTORATION_SCENERY_PRESENT,
		EXACT_AUTHORED_TRANSIENT_PRESENT,
		MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
		AMBIGUOUS_OCCUPANCY
	}

	public enum Reason {
		REQUIREMENT_UNAVAILABLE(true),
		TARGET_BINDING_INCOMPLETE(true),
		GENERATION_MISMATCH(true),
		TARGET_OBSERVATION_UNAVAILABLE(true),
		DESIRED_PRESENT_STATE_ALREADY_SATISFIED(false),
		DESIRED_ABSENT_STATE_ALREADY_SATISFIED(false),
		SPAWN_DESTINATION_EMPTY(false),
		EXACT_AUTHORED_TRANSIENT_PRESENT(false),
		EXACT_REMOVAL_TARGET_PRESENT(false),
		REMOVAL_TARGET_CHANGED_TO_AUTHORED_TRANSIENT(true),
		MISMATCHED_OR_IDENTITYLESS_OCCUPANT(true),
		AMBIGUOUS_OCCUPANCY(true);

		private final boolean refusal;

		Reason(final boolean refusal) {
			this.refusal = refusal;
		}

		private boolean isRefusal() { return refusal; }
	}
}
