package com.openrsc.server.event.rsc;

import java.util.Objects;

/**
 * Immutable, dormant description of callback inputs that a future event
 * preservation design would have to retain.
 *
 * <p>This value is deliberately not executable. It contains no event, entity,
 * World, Region, scheduler, registry, callback, UUID, or collection handle.
 * Scheduler identity/countdown capture, target lookup, cancellation,
 * rescheduling, replay, and lifecycle authority are all outside this
 * contract.</p>
 */
public final class GameTickEventRestorationState {
	public static final int MAXIMUM_AUTHORED_SOURCE_ORDINAL = 262144;

	private static final GameTickEventRestorationState UNAVAILABLE =
		new GameTickEventRestorationState(
			Kind.UNAVAILABLE, null, false,
			TargetBindingEvidence.UNAVAILABLE, false);

	private final Kind kind;
	private final SceneryState scenery;
	private final boolean forceFullBlock;
	private final TargetBindingEvidence targetBindingEvidence;
	private final boolean detachedCallbackPayloadComplete;

	private GameTickEventRestorationState(
		final Kind kind,
		final SceneryState scenery,
		final boolean forceFullBlock,
		final TargetBindingEvidence targetBindingEvidence,
		final boolean detachedCallbackPayloadComplete) {
		this.kind = Objects.requireNonNull(kind, "kind");
		this.scenery = scenery;
		this.forceFullBlock = forceFullBlock;
		this.targetBindingEvidence = Objects.requireNonNull(
			targetBindingEvidence, "targetBindingEvidence");
		this.detachedCallbackPayloadComplete =
			detachedCallbackPayloadComplete;
		if (kind == Kind.UNAVAILABLE) {
			if (scenery != null || forceFullBlock
				|| targetBindingEvidence
					!= TargetBindingEvidence.UNAVAILABLE
				|| detachedCallbackPayloadComplete) {
				throw new IllegalArgumentException(
					"Unavailable restoration state cannot contain callback data");
			}
		} else if (scenery == null) {
			throw new IllegalArgumentException(
				"Scenery restoration state requires constructor data");
		}
	}

	/** Default for every callback without an explicit detached contract. */
	public static GameTickEventRestorationState unavailable() {
		return UNAVAILABLE;
	}

	/**
	 * Records every input used by the known delayed scenery-spawn callback.
	 * No target binding is needed because the callback constructs a new object.
	 */
	public static GameTickEventRestorationState scenerySpawn(
		final SceneryState scenery,
		final boolean forceFullBlock) {
		return new GameTickEventRestorationState(
			Kind.SCENERY_SPAWN, Objects.requireNonNull(scenery, "scenery"),
			forceFullBlock, TargetBindingEvidence.NOT_REQUIRED, true);
	}

	/**
	 * Records the current constructor state of the delayed-removal target. A
	 * detached binding is available only when authored placement identity is
	 * present; identity-less objects still depend on the live entity reference
	 * held by the existing callback.
	 */
	public static GameTickEventRestorationState sceneryRemove(
		final SceneryState scenery) {
		SceneryState checked = Objects.requireNonNull(scenery, "scenery");
		boolean authoredBinding = checked.getAuthoredPlacement() != null;
		return new GameTickEventRestorationState(
			Kind.SCENERY_REMOVE, checked, false,
			authoredBinding
				? TargetBindingEvidence.AUTHORED_PLACEMENT_IDENTITY
				: TargetBindingEvidence.LIVE_ENTITY_REFERENCE_ONLY,
			authoredBinding);
	}

	public Kind getKind() { return kind; }
	public SceneryState getScenery() { return scenery; }
	public boolean isForceFullBlock() { return forceFullBlock; }
	public TargetBindingEvidence getTargetBindingEvidence() {
		return targetBindingEvidence;
	}
	public boolean isDetachedCallbackPayloadComplete() {
		return detachedCallbackPayloadComplete;
	}

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedPrimitiveCopy() { return true; }
	public boolean isRuntimeAttributesCaptured() { return false; }
	public boolean isSchedulerStateCaptured() { return false; }
	public boolean isSchedulerIdentityCaptured() { return false; }
	public boolean isTargetBindingLookupPerformed() { return false; }
	public boolean isStandaloneRestorationComplete() { return false; }
	public boolean isPreservationPerformed() { return false; }
	public boolean isReloadRequest() { return false; }
	public boolean isEventCancellation() { return false; }
	public boolean isEventReschedule() { return false; }
	public boolean isEntityRegistry() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isTeardownTransaction() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum Kind {
		UNAVAILABLE,
		SCENERY_SPAWN,
		SCENERY_REMOVE
	}

	public enum TargetBindingEvidence {
		UNAVAILABLE,
		NOT_REQUIRED,
		AUTHORED_PLACEMENT_IDENTITY,
		LIVE_ENTITY_REFERENCE_ONLY
	}

	/** Constructor and provenance values detached from scenery callback input. */
	public static final class SceneryState {
		private final int objectId;
		private final int permanentObjectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;
		private final String owner;
		private final int runtimeAttributeCount;
		private final AuthoredPlacementState authoredPlacement;

		private SceneryState(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount,
			final AuthoredPlacementState authoredPlacement) {
			if (objectId < 0 || permanentObjectId < 0 || x < 0 || y < 0
				|| direction < 0 || direction > 7
				|| (type != 0 && type != 1)
				|| runtimeAttributeCount < 0) {
				throw new IllegalArgumentException(
					"Scenery callback constructor state is invalid");
			}
			this.objectId = objectId;
			this.permanentObjectId = permanentObjectId;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
			this.owner = owner;
			this.runtimeAttributeCount = runtimeAttributeCount;
			this.authoredPlacement = authoredPlacement;
		}

		public static SceneryState of(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount,
			final AuthoredPlacementState authoredPlacement) {
			return new SceneryState(
				objectId, permanentObjectId, x, y, direction, type, owner,
				runtimeAttributeCount, authoredPlacement);
		}

		public int getObjectId() { return objectId; }
		public int getPermanentObjectId() { return permanentObjectId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getDirection() { return direction; }
		public int getType() { return type; }
		public String getOwner() { return owner; }
		public boolean hasOwner() { return owner != null; }
		public int getRuntimeAttributeCount() {
			return runtimeAttributeCount;
		}
		public AuthoredPlacementState getAuthoredPlacement() {
			return authoredPlacement;
		}
		public boolean hasAuthoredPlacement() {
			return authoredPlacement != null;
		}
		public boolean isConstructorStateComplete() { return true; }
	}

	/** Scalar copy of generation-fenced authored placement provenance. */
	public static final class AuthoredPlacementState {
		private final long generation;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int sourceOrdinal;
		private final AuthoredConstructionKind constructionKind;

		private AuthoredPlacementState(
			final long generation,
			final int packedRegionX,
			final int packedRegionY,
			final int sourceOrdinal,
			final AuthoredConstructionKind constructionKind) {
			if (generation <= 0L || packedRegionX < 0 || packedRegionY < 0
				|| sourceOrdinal <= 0
				|| sourceOrdinal > MAXIMUM_AUTHORED_SOURCE_ORDINAL) {
				throw new IllegalArgumentException(
					"Authored placement restoration state is invalid");
			}
			this.generation = generation;
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.sourceOrdinal = sourceOrdinal;
			this.constructionKind = Objects.requireNonNull(
				constructionKind, "constructionKind");
		}

		public static AuthoredPlacementState of(
			final long generation,
			final int packedRegionX,
			final int packedRegionY,
			final int sourceOrdinal,
			final AuthoredConstructionKind constructionKind) {
			return new AuthoredPlacementState(
				generation, packedRegionX, packedRegionY, sourceOrdinal,
				constructionKind);
		}

		public long getGeneration() { return generation; }
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getSourceOrdinal() { return sourceOrdinal; }
		public AuthoredConstructionKind getConstructionKind() {
			return constructionKind;
		}
	}

	public enum AuthoredConstructionKind {
		SCENERY,
		BOUNDARY,
		NPC_SPAWN,
		GROUND_ITEM_SPAWN,
		HARVESTING_SCENERY
	}
}
