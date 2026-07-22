package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc.GameTickEventRestorationState.AuthoredConstructionKind;
import com.openrsc.server.event.rsc.GameTickEventRestorationState.AuthoredPlacementState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState.SceneryState;

import java.util.Objects;

/**
 * Immutable, dormant prerequisites for applying a detached event-restoration
 * state to a reconstructed source.
 *
 * <p>This value deliberately has no entity, Region, World, event, scheduler,
 * registry, callback, login, packet, or arrival-gate handle. It classifies the
 * target a future restoration path would have to bind, the evidence available
 * for that binding, and the point by which callback state must be reconciled.
 * It cannot perform any of those operations.</p>
 */
public final class GameTickEventRestorationRequirement {
	private static final GameTickEventRestorationRequirement UNAVAILABLE =
		new GameTickEventRestorationRequirement(
			TargetSubject.UNAVAILABLE, BindingEvidence.UNAVAILABLE, null,
			TargetConflictPolicy.UNAVAILABLE,
			ArrivalOrderingRequirement.UNAVAILABLE,
			GenerationBindingRequirement.UNAVAILABLE,
			DesiredState.UNAVAILABLE, IdempotencyPolicy.UNAVAILABLE,
			MutationPrecondition.UNAVAILABLE);

	private final TargetSubject targetSubject;
	private final BindingEvidence bindingEvidence;
	private final AuthoredTarget authoredTarget;
	private final TargetConflictPolicy targetConflictPolicy;
	private final ArrivalOrderingRequirement arrivalOrderingRequirement;
	private final GenerationBindingRequirement generationBindingRequirement;
	private final DesiredState desiredState;
	private final IdempotencyPolicy idempotencyPolicy;
	private final MutationPrecondition mutationPrecondition;

	private GameTickEventRestorationRequirement(
		final TargetSubject targetSubject,
		final BindingEvidence bindingEvidence,
		final AuthoredTarget authoredTarget,
		final TargetConflictPolicy targetConflictPolicy,
		final ArrivalOrderingRequirement arrivalOrderingRequirement,
		final GenerationBindingRequirement generationBindingRequirement,
		final DesiredState desiredState,
		final IdempotencyPolicy idempotencyPolicy,
		final MutationPrecondition mutationPrecondition) {
		this.targetSubject = Objects.requireNonNull(
			targetSubject, "targetSubject");
		this.bindingEvidence = Objects.requireNonNull(
			bindingEvidence, "bindingEvidence");
		this.authoredTarget = authoredTarget;
		this.targetConflictPolicy = Objects.requireNonNull(
			targetConflictPolicy, "targetConflictPolicy");
		this.arrivalOrderingRequirement = Objects.requireNonNull(
			arrivalOrderingRequirement, "arrivalOrderingRequirement");
		this.generationBindingRequirement = Objects.requireNonNull(
			generationBindingRequirement, "generationBindingRequirement");
		this.desiredState = Objects.requireNonNull(
			desiredState, "desiredState");
		this.idempotencyPolicy = Objects.requireNonNull(
			idempotencyPolicy, "idempotencyPolicy");
		this.mutationPrecondition = Objects.requireNonNull(
			mutationPrecondition, "mutationPrecondition");

		if (targetSubject == TargetSubject.UNAVAILABLE) {
			if (bindingEvidence != BindingEvidence.UNAVAILABLE
				|| authoredTarget != null
				|| targetConflictPolicy != TargetConflictPolicy.UNAVAILABLE
				|| arrivalOrderingRequirement
					!= ArrivalOrderingRequirement.UNAVAILABLE
				|| generationBindingRequirement
					!= GenerationBindingRequirement.UNAVAILABLE
				|| desiredState != DesiredState.UNAVAILABLE
				|| idempotencyPolicy != IdempotencyPolicy.UNAVAILABLE
				|| mutationPrecondition != MutationPrecondition.UNAVAILABLE) {
				throw new IllegalArgumentException(
					"Unavailable restoration requirement cannot contain data");
			}
		} else {
			if (bindingEvidence == BindingEvidence.UNAVAILABLE
				|| targetConflictPolicy
					!= TargetConflictPolicy.REFUSE_MISMATCH_OR_AMBIGUITY
				|| arrivalOrderingRequirement
					!= ArrivalOrderingRequirement
						.RECONCILE_BEFORE_FIRST_VISIBILITY
				|| generationBindingRequirement
					!= GenerationBindingRequirement
						.MATCH_RECONSTRUCTION_GENERATION
				|| idempotencyPolicy
					!= IdempotencyPolicy
						.ALREADY_SATISFIED_IS_NO_OP_SUCCESS) {
				throw new IllegalArgumentException(
					"Known restoration requirement must fail closed and be idempotent");
			}
			if ((bindingEvidence
					== BindingEvidence.AUTHORED_PLACEMENT_IDENTITY)
				!= (authoredTarget != null)) {
				throw new IllegalArgumentException(
					"Authored binding evidence and target must agree");
			}
			DesiredState requiredState = targetSubject
				== TargetSubject.AUTHORED_DESTINATION_SLOT
					? DesiredState.AUTHORED_SCENERY_PRESENT
					: DesiredState.AUTHORED_SCENERY_ABSENT;
			MutationPrecondition requiredPrecondition = targetSubject
				== TargetSubject.AUTHORED_DESTINATION_SLOT
					? MutationPrecondition.DESTINATION_SLOT_EMPTY
					: MutationPrecondition.EXACT_AUTHORED_ENTITY_PRESENT;
			if (desiredState != requiredState
				|| mutationPrecondition != requiredPrecondition) {
				throw new IllegalArgumentException(
					"Restoration desired state does not match its target subject");
			}
		}
	}

	/**
	 * Derives a detached prerequisite without retaining the supplied state.
	 * Exact coordinates alone never authorize restoration: both known scenery
	 * mutations require an authored placement identity, and missing identity is
	 * recorded as an explicit fail-closed gap.
	 */
	public static GameTickEventRestorationRequirement from(
		final GameTickEventRestorationState state) {
		GameTickEventRestorationState checked = Objects.requireNonNull(
			state, "state");
		if (checked.getKind()
			== GameTickEventRestorationState.Kind.UNAVAILABLE) {
			return UNAVAILABLE;
		}

		SceneryState scenery = Objects.requireNonNull(
			checked.getScenery(), "scenery");
		AuthoredPlacementState authored = scenery.getAuthoredPlacement();
		BindingEvidence evidence = authored == null
			? BindingEvidence.MISSING_AUTHORED_PLACEMENT_IDENTITY
			: BindingEvidence.AUTHORED_PLACEMENT_IDENTITY;
		AuthoredTarget target = authored == null
			? null : AuthoredTarget.copyOf(authored);
		TargetSubject subject;
		DesiredState desiredState;
		MutationPrecondition mutationPrecondition;
		switch (checked.getKind()) {
			case SCENERY_SPAWN:
				subject = TargetSubject.AUTHORED_DESTINATION_SLOT;
				desiredState = DesiredState.AUTHORED_SCENERY_PRESENT;
				mutationPrecondition = MutationPrecondition.DESTINATION_SLOT_EMPTY;
				break;
			case SCENERY_REMOVE:
				subject = TargetSubject.AUTHORED_EXISTING_ENTITY;
				desiredState = DesiredState.AUTHORED_SCENERY_ABSENT;
				mutationPrecondition =
					MutationPrecondition.EXACT_AUTHORED_ENTITY_PRESENT;
				break;
			default:
				throw new IllegalStateException(
					"Unhandled event restoration-state kind");
		}
		return new GameTickEventRestorationRequirement(
			subject, evidence, target,
			TargetConflictPolicy.REFUSE_MISMATCH_OR_AMBIGUITY,
			ArrivalOrderingRequirement.RECONCILE_BEFORE_FIRST_VISIBILITY,
			GenerationBindingRequirement.MATCH_RECONSTRUCTION_GENERATION,
			desiredState,
			IdempotencyPolicy.ALREADY_SATISFIED_IS_NO_OP_SUCCESS,
			mutationPrecondition);
	}

	public TargetSubject getTargetSubject() { return targetSubject; }
	public BindingEvidence getBindingEvidence() { return bindingEvidence; }
	public AuthoredTarget getAuthoredTarget() { return authoredTarget; }
	public TargetConflictPolicy getTargetConflictPolicy() {
		return targetConflictPolicy;
	}
	public ArrivalOrderingRequirement getArrivalOrderingRequirement() {
		return arrivalOrderingRequirement;
	}
	public GenerationBindingRequirement getGenerationBindingRequirement() {
		return generationBindingRequirement;
	}
	public DesiredState getDesiredState() { return desiredState; }
	public IdempotencyPolicy getIdempotencyPolicy() {
		return idempotencyPolicy;
	}
	public MutationPrecondition getMutationPrecondition() {
		return mutationPrecondition;
	}
	public boolean isTargetBindingComplete() {
		return bindingEvidence
			== BindingEvidence.AUTHORED_PLACEMENT_IDENTITY
			&& authoredTarget != null;
	}
	public boolean isArrivalOrderingCaptured() {
		return arrivalOrderingRequirement
			!= ArrivalOrderingRequirement.UNAVAILABLE;
	}
	public boolean isGenerationBindingRequirementCaptured() {
		return generationBindingRequirement
			!= GenerationBindingRequirement.UNAVAILABLE;
	}
	public boolean isDesiredStateCaptured() {
		return desiredState != DesiredState.UNAVAILABLE;
	}
	public boolean isIdempotencyPolicyCaptured() {
		return idempotencyPolicy != IdempotencyPolicy.UNAVAILABLE;
	}
	public boolean isMutationPreconditionCaptured() {
		return mutationPrecondition != MutationPrecondition.UNAVAILABLE;
	}

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedPrimitiveCopy() { return true; }
	public boolean isTargetLookupPerformed() { return false; }
	public boolean isGenerationMatchPerformed() { return false; }
	public boolean isTargetStateInspected() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isExecutableRestoration() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** The mutation subject a future target resolver would have to bind. */
	public enum TargetSubject {
		UNAVAILABLE,
		AUTHORED_DESTINATION_SLOT,
		AUTHORED_EXISTING_ENTITY
	}

	/** Evidence present in this detached value, not a performed lookup. */
	public enum BindingEvidence {
		UNAVAILABLE,
		AUTHORED_PLACEMENT_IDENTITY,
		MISSING_AUTHORED_PLACEMENT_IDENTITY
	}

	/** A different or ambiguous occupant must never be replaced speculatively. */
	public enum TargetConflictPolicy {
		UNAVAILABLE,
		REFUSE_MISMATCH_OR_AMBIGUITY
	}

	/**
	 * The reconstructed transient state and its timer outcome must be reconciled
	 * before any login, teleport, or movement arrival can build its first
	 * visibility snapshot. If the timer is overdue, its one-shot mutation must
	 * be reflected first; if it is not due, the pending transient state and
	 * remaining timer must be reflected first.
	 */
	public enum ArrivalOrderingRequirement {
		UNAVAILABLE,
		RECONCILE_BEFORE_FIRST_VISIBILITY
	}

	/** A stale authored callback cannot bind into another population pass. */
	public enum GenerationBindingRequirement {
		UNAVAILABLE,
		MATCH_RECONSTRUCTION_GENERATION
	}

	/** Desired post-callback state, independent of whether mutation is needed. */
	public enum DesiredState {
		UNAVAILABLE,
		AUTHORED_SCENERY_PRESENT,
		AUTHORED_SCENERY_ABSENT
	}

	/** Repeating reconciliation after success must not repeat its side effect. */
	public enum IdempotencyPolicy {
		UNAVAILABLE,
		ALREADY_SATISFIED_IS_NO_OP_SUCCESS
	}

	/** State required before a future path may perform the one-shot mutation. */
	public enum MutationPrecondition {
		UNAVAILABLE,
		DESTINATION_SLOT_EMPTY,
		EXACT_AUTHORED_ENTITY_PRESENT
	}

	/** Scalar copy of the generation-fenced authored target address. */
	public static final class AuthoredTarget {
		private final long generation;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int sourceOrdinal;
		private final AuthoredConstructionKind constructionKind;

		private AuthoredTarget(
			final long generation,
			final int packedRegionX,
			final int packedRegionY,
			final int sourceOrdinal,
			final AuthoredConstructionKind constructionKind) {
			if (generation <= 0L || packedRegionX < 0 || packedRegionY < 0
				|| sourceOrdinal <= 0
				|| sourceOrdinal
					> GameTickEventRestorationState
						.MAXIMUM_AUTHORED_SOURCE_ORDINAL) {
				throw new IllegalArgumentException(
					"Authored restoration target is invalid");
			}
			this.generation = generation;
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.sourceOrdinal = sourceOrdinal;
			this.constructionKind = Objects.requireNonNull(
				constructionKind, "constructionKind");
		}

		private static AuthoredTarget copyOf(
			final AuthoredPlacementState authored) {
			return new AuthoredTarget(
				authored.getGeneration(), authored.getPackedRegionX(),
				authored.getPackedRegionY(), authored.getSourceOrdinal(),
				authored.getConstructionKind());
		}

		public long getGeneration() { return generation; }
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getSourceOrdinal() { return sourceOrdinal; }
		public AuthoredConstructionKind getConstructionKind() {
			return constructionKind;
		}
	}
}
