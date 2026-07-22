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
			ArrivalOrderingRequirement.UNAVAILABLE);

	private final TargetSubject targetSubject;
	private final BindingEvidence bindingEvidence;
	private final AuthoredTarget authoredTarget;
	private final TargetConflictPolicy targetConflictPolicy;
	private final ArrivalOrderingRequirement arrivalOrderingRequirement;

	private GameTickEventRestorationRequirement(
		final TargetSubject targetSubject,
		final BindingEvidence bindingEvidence,
		final AuthoredTarget authoredTarget,
		final TargetConflictPolicy targetConflictPolicy,
		final ArrivalOrderingRequirement arrivalOrderingRequirement) {
		this.targetSubject = Objects.requireNonNull(
			targetSubject, "targetSubject");
		this.bindingEvidence = Objects.requireNonNull(
			bindingEvidence, "bindingEvidence");
		this.authoredTarget = authoredTarget;
		this.targetConflictPolicy = Objects.requireNonNull(
			targetConflictPolicy, "targetConflictPolicy");
		this.arrivalOrderingRequirement = Objects.requireNonNull(
			arrivalOrderingRequirement, "arrivalOrderingRequirement");

		if (targetSubject == TargetSubject.UNAVAILABLE) {
			if (bindingEvidence != BindingEvidence.UNAVAILABLE
				|| authoredTarget != null
				|| targetConflictPolicy != TargetConflictPolicy.UNAVAILABLE
				|| arrivalOrderingRequirement
					!= ArrivalOrderingRequirement.UNAVAILABLE) {
				throw new IllegalArgumentException(
					"Unavailable restoration requirement cannot contain data");
			}
		} else {
			if (bindingEvidence == BindingEvidence.UNAVAILABLE
				|| targetConflictPolicy
					!= TargetConflictPolicy.REFUSE_MISMATCH_OR_AMBIGUITY
				|| arrivalOrderingRequirement
					!= ArrivalOrderingRequirement
						.RECONCILE_BEFORE_FIRST_VISIBILITY) {
				throw new IllegalArgumentException(
					"Known restoration requirement must fail closed before arrival");
			}
			if ((bindingEvidence
					== BindingEvidence.AUTHORED_PLACEMENT_IDENTITY)
				!= (authoredTarget != null)) {
				throw new IllegalArgumentException(
					"Authored binding evidence and target must agree");
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
		switch (checked.getKind()) {
			case SCENERY_SPAWN:
				subject = TargetSubject.AUTHORED_DESTINATION_SLOT;
				break;
			case SCENERY_REMOVE:
				subject = TargetSubject.AUTHORED_EXISTING_ENTITY;
				break;
			default:
				throw new IllegalStateException(
					"Unhandled event restoration-state kind");
		}
		return new GameTickEventRestorationRequirement(
			subject, evidence, target,
			TargetConflictPolicy.REFUSE_MISMATCH_OR_AMBIGUITY,
			ArrivalOrderingRequirement.RECONCILE_BEFORE_FIRST_VISIBILITY);
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
	public boolean isTargetBindingComplete() {
		return bindingEvidence
			== BindingEvidence.AUTHORED_PLACEMENT_IDENTITY
			&& authoredTarget != null;
	}
	public boolean isArrivalOrderingCaptured() {
		return arrivalOrderingRequirement
			!= ArrivalOrderingRequirement.UNAVAILABLE;
	}

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedPrimitiveCopy() { return true; }
	public boolean isTargetLookupPerformed() { return false; }
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
