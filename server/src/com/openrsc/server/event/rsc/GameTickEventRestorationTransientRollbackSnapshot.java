package com.openrsc.server.event.rsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant exact rollback state for one displaced authored transient.
 *
 * <p>The input is a detached declaration of state that a future atomic Region
 * seam would have to capture. This class never observes an entity or tile. It
 * accepts only one exact authored transient with no opaque runtime attributes,
 * plus a complete, bounded per-tile collision contribution captured under a
 * separately declared ordered collision boundary.</p>
 *
 * <p>Even an available snapshot is not executable rollback. It contains no
 * World, Region, entity, tile, collection owner, monitor, scheduler, callback,
 * or lifecycle handle, and no runtime path consumes it.</p>
 */
public final class GameTickEventRestorationTransientRollbackSnapshot {
	public static final int MAXIMUM_COLLISION_CONTRIBUTION_TILES = 4096;
	public static final int MAXIMUM_DYNAMIC_COLLISION_MASK = 0x3f;

	private final int objectId;
	private final int permanentObjectId;
	private final int x;
	private final int y;
	private final int direction;
	private final int type;
	private final String owner;
	private final long authoredGeneration;
	private final int authoredPackedRegionX;
	private final int authoredPackedRegionY;
	private final int authoredSourceOrdinal;
	private final GameTickEventRestorationMutationIntent.AuthoredConstructionKind
		authoredConstructionKind;
	private final List<CollisionContribution> collisionContributions;

	private GameTickEventRestorationTransientRollbackSnapshot(
		final Candidate candidate) {
		this.objectId = candidate.getObjectId();
		this.permanentObjectId = candidate.getPermanentObjectId();
		this.x = candidate.getX();
		this.y = candidate.getY();
		this.direction = candidate.getDirection();
		this.type = candidate.getType();
		this.owner = candidate.getOwner();
		this.authoredGeneration = candidate.getAuthoredGeneration();
		this.authoredPackedRegionX = candidate.getAuthoredPackedRegionX();
		this.authoredPackedRegionY = candidate.getAuthoredPackedRegionY();
		this.authoredSourceOrdinal = candidate.getAuthoredSourceOrdinal();
		this.authoredConstructionKind =
			candidate.getAuthoredConstructionKind();
		List<CollisionContribution> canonical =
			new ArrayList<CollisionContribution>(
				candidate.getCollisionContributions());
		Collections.sort(canonical, CollisionContribution.ORDER);
		this.collisionContributions = Collections.unmodifiableList(canonical);
	}

	/** Applies the closed snapshot-availability refusal table. */
	public static Creation assess(
		final GameTickEventRestorationMutationIntent intent,
		final Candidate candidate) {
		GameTickEventRestorationMutationIntent checkedIntent =
			Objects.requireNonNull(intent, "intent");
		Candidate checkedCandidate = Objects.requireNonNull(
			candidate, "candidate");
		if (checkedIntent.getOperation()
				!= GameTickEventRestorationMutationIntent.Operation.SCENERY_SPAWN
			|| checkedIntent.getExpectedTargetState()
				!= GameTickEventRestorationTargetDecision.ObservedTargetState
					.EXACT_AUTHORED_TRANSIENT_PRESENT) {
			return Creation.refused(Reason.INTENT_NOT_TRANSIENT_REPLACEMENT);
		}
		if (!checkedCandidate.isRegionObjectBoundaryHeldDuringCapture()) {
			return Creation.refused(Reason.REGION_OBJECT_BOUNDARY_MISSING);
		}
		if (checkedCandidate.getExactSlotObjectCount() != 1) {
			return Creation.refused(Reason.EXACT_SLOT_NOT_SINGLE_OBJECT);
		}
		if (checkedCandidate.getX() != checkedIntent.getX()
			|| checkedCandidate.getY() != checkedIntent.getY()
			|| checkedCandidate.getType() != checkedIntent.getType()
			|| (checkedIntent.getType() == 1
				&& checkedCandidate.getDirection()
					!= checkedIntent.getDirection())) {
			return Creation.refused(
				Reason.TRANSIENT_COORDINATE_OR_TYPE_MISMATCH);
		}
		if (checkedCandidate.getAuthoredGeneration()
				!= checkedIntent.getAuthoredGeneration()
			|| checkedCandidate.getAuthoredPackedRegionX()
				!= checkedIntent.getAuthoredPackedRegionX()
			|| checkedCandidate.getAuthoredPackedRegionY()
				!= checkedIntent.getAuthoredPackedRegionY()
			|| checkedCandidate.getAuthoredSourceOrdinal()
				!= checkedIntent.getAuthoredSourceOrdinal()
			|| checkedCandidate.getAuthoredConstructionKind()
				!= checkedIntent.getAuthoredConstructionKind()) {
			return Creation.refused(
				Reason.TRANSIENT_AUTHORED_IDENTITY_MISMATCH);
		}
		if (checkedCandidate.getRuntimeAttributeCount() != 0) {
			return Creation.refused(Reason.RUNTIME_ATTRIBUTES_NOT_RESTORABLE);
		}
		if (!checkedCandidate
			.isOrderedCollisionBoundaryHeldDuringCapture()) {
			return Creation.refused(Reason.COLLISION_BOUNDARY_MISSING);
		}
		if (!checkedCandidate.isCollisionContributionComplete()) {
			return Creation.refused(
				Reason.COLLISION_CONTRIBUTION_INCOMPLETE);
		}
		Set<CollisionCoordinate> coordinates =
			new HashSet<CollisionCoordinate>();
		for (CollisionContribution contribution
				: checkedCandidate.getCollisionContributions()) {
			if (!coordinates.add(
				new CollisionCoordinate(
					contribution.getX(), contribution.getY()))) {
				return Creation.refused(
					Reason.DUPLICATE_COLLISION_CONTRIBUTION_TILE);
			}
		}
		return Creation.available(
			new GameTickEventRestorationTransientRollbackSnapshot(
				checkedCandidate));
	}

	public int getObjectId() { return objectId; }
	public int getPermanentObjectId() { return permanentObjectId; }
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDirection() { return direction; }
	public int getType() { return type; }
	public String getOwner() { return owner; }
	public boolean hasOwner() { return owner != null; }
	public long getAuthoredGeneration() { return authoredGeneration; }
	public int getAuthoredPackedRegionX() { return authoredPackedRegionX; }
	public int getAuthoredPackedRegionY() { return authoredPackedRegionY; }
	public int getAuthoredSourceOrdinal() { return authoredSourceOrdinal; }
	public GameTickEventRestorationMutationIntent.AuthoredConstructionKind
		getAuthoredConstructionKind() { return authoredConstructionKind; }
	public List<CollisionContribution> getCollisionContributions() {
		return collisionContributions;
	}
	public int getCollisionContributionTileCount() {
		return collisionContributions.size();
	}

	public boolean isDormantSnapshot() { return true; }
	public boolean isConstructorStateComplete() { return true; }
	public boolean isAuthoredIdentityComplete() { return true; }
	public boolean isOpaqueRuntimeAttributeStateCaptured() { return false; }
	public boolean isCollisionContributionComplete() { return true; }
	public boolean isRuntimeObservationPerformed() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isStandaloneRollbackComplete() { return false; }
	public boolean isRollbackAuthorized() { return false; }
	public boolean isRollbackPerformed() { return false; }
	public boolean isMutationAuthorized() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isExecutableRestoration() { return false; }
	public boolean isCommitToken() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum Outcome {
		REFUSED,
		SNAPSHOT_AVAILABLE
	}

	public enum Reason {
		INTENT_NOT_TRANSIENT_REPLACEMENT,
		REGION_OBJECT_BOUNDARY_MISSING,
		EXACT_SLOT_NOT_SINGLE_OBJECT,
		TRANSIENT_COORDINATE_OR_TYPE_MISMATCH,
		TRANSIENT_AUTHORED_IDENTITY_MISMATCH,
		RUNTIME_ATTRIBUTES_NOT_RESTORABLE,
		COLLISION_BOUNDARY_MISSING,
		COLLISION_CONTRIBUTION_INCOMPLETE,
		DUPLICATE_COLLISION_CONTRIBUTION_TILE,
		SNAPSHOT_AVAILABLE
	}

	/**
	 * Detached capture declaration. The booleans are claims checked by this
	 * dormant contract, not evidence that this class acquired either boundary.
	 */
	public static final class Candidate {
		private final int objectId;
		private final int permanentObjectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;
		private final String owner;
		private final int runtimeAttributeCount;
		private final long authoredGeneration;
		private final int authoredPackedRegionX;
		private final int authoredPackedRegionY;
		private final int authoredSourceOrdinal;
		private final GameTickEventRestorationMutationIntent
			.AuthoredConstructionKind authoredConstructionKind;
		private final int exactSlotObjectCount;
		private final boolean regionObjectBoundaryHeldDuringCapture;
		private final boolean orderedCollisionBoundaryHeldDuringCapture;
		private final boolean collisionContributionComplete;
		private final List<CollisionContribution> collisionContributions;

		private Candidate(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount,
			final long authoredGeneration,
			final int authoredPackedRegionX,
			final int authoredPackedRegionY,
			final int authoredSourceOrdinal,
			final GameTickEventRestorationMutationIntent
				.AuthoredConstructionKind authoredConstructionKind,
			final int exactSlotObjectCount,
			final boolean regionObjectBoundaryHeldDuringCapture,
			final boolean orderedCollisionBoundaryHeldDuringCapture,
			final boolean collisionContributionComplete,
			final List<CollisionContribution> collisionContributions) {
			if (objectId < 0 || permanentObjectId < 0 || x < 0 || y < 0
				|| direction < 0 || direction > 7
				|| (type != 0 && type != 1)
				|| runtimeAttributeCount < 0
				|| authoredGeneration <= 0L
				|| authoredPackedRegionX < 0 || authoredPackedRegionY < 0
				|| authoredSourceOrdinal <= 0
				|| authoredSourceOrdinal
					> GameTickEventRestorationState
						.MAXIMUM_AUTHORED_SOURCE_ORDINAL
				|| exactSlotObjectCount < 0) {
				throw new IllegalArgumentException(
					"Transient rollback candidate is invalid");
			}
			List<CollisionContribution> checked = Objects.requireNonNull(
				collisionContributions, "collisionContributions");
			if (checked.size() > MAXIMUM_COLLISION_CONTRIBUTION_TILES
				|| checked.contains(null)) {
				throw new IllegalArgumentException(
					"Transient collision contribution exceeds its bound");
			}
			this.objectId = objectId;
			this.permanentObjectId = permanentObjectId;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
			this.owner = owner;
			this.runtimeAttributeCount = runtimeAttributeCount;
			this.authoredGeneration = authoredGeneration;
			this.authoredPackedRegionX = authoredPackedRegionX;
			this.authoredPackedRegionY = authoredPackedRegionY;
			this.authoredSourceOrdinal = authoredSourceOrdinal;
			this.authoredConstructionKind = Objects.requireNonNull(
				authoredConstructionKind, "authoredConstructionKind");
			this.exactSlotObjectCount = exactSlotObjectCount;
			this.regionObjectBoundaryHeldDuringCapture =
				regionObjectBoundaryHeldDuringCapture;
			this.orderedCollisionBoundaryHeldDuringCapture =
				orderedCollisionBoundaryHeldDuringCapture;
			this.collisionContributionComplete =
				collisionContributionComplete;
			this.collisionContributions = Collections.unmodifiableList(
				new ArrayList<CollisionContribution>(checked));
		}

		public static Candidate declare(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount,
			final long authoredGeneration,
			final int authoredPackedRegionX,
			final int authoredPackedRegionY,
			final int authoredSourceOrdinal,
			final GameTickEventRestorationMutationIntent
				.AuthoredConstructionKind authoredConstructionKind,
			final int exactSlotObjectCount,
			final boolean regionObjectBoundaryHeldDuringCapture,
			final boolean orderedCollisionBoundaryHeldDuringCapture,
			final boolean collisionContributionComplete,
			final List<CollisionContribution> collisionContributions) {
			return new Candidate(
				objectId, permanentObjectId, x, y, direction, type, owner,
				runtimeAttributeCount, authoredGeneration,
				authoredPackedRegionX, authoredPackedRegionY,
				authoredSourceOrdinal, authoredConstructionKind,
				exactSlotObjectCount,
				regionObjectBoundaryHeldDuringCapture,
				orderedCollisionBoundaryHeldDuringCapture,
				collisionContributionComplete, collisionContributions);
		}

		public int getObjectId() { return objectId; }
		public int getPermanentObjectId() { return permanentObjectId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getDirection() { return direction; }
		public int getType() { return type; }
		public String getOwner() { return owner; }
		public int getRuntimeAttributeCount() {
			return runtimeAttributeCount;
		}
		public long getAuthoredGeneration() { return authoredGeneration; }
		public int getAuthoredPackedRegionX() {
			return authoredPackedRegionX;
		}
		public int getAuthoredPackedRegionY() {
			return authoredPackedRegionY;
		}
		public int getAuthoredSourceOrdinal() {
			return authoredSourceOrdinal;
		}
		public GameTickEventRestorationMutationIntent.AuthoredConstructionKind
			getAuthoredConstructionKind() {
			return authoredConstructionKind;
		}
		public int getExactSlotObjectCount() {
			return exactSlotObjectCount;
		}
		public boolean isRegionObjectBoundaryHeldDuringCapture() {
			return regionObjectBoundaryHeldDuringCapture;
		}
		public boolean isOrderedCollisionBoundaryHeldDuringCapture() {
			return orderedCollisionBoundaryHeldDuringCapture;
		}
		public boolean isCollisionContributionComplete() {
			return collisionContributionComplete;
		}
		public List<CollisionContribution> getCollisionContributions() {
			return collisionContributions;
		}
	}

	/** Exact contribution made by the displaced object to one collision tile. */
	public static final class CollisionContribution {
		private static final Comparator<CollisionContribution> ORDER =
			new Comparator<CollisionContribution>() {
				@Override
				public int compare(
					final CollisionContribution left,
					final CollisionContribution right) {
					int compared = Integer.compare(left.y, right.y);
					return compared != 0
						? compared : Integer.compare(left.x, right.x);
				}
			};

		private final int x;
		private final int y;
		private final int blockingSceneryCount;
		private final int dynamicCollisionMask;
		private final int dynamicProjectileCount;

		private CollisionContribution(
			final int x,
			final int y,
			final int blockingSceneryCount,
			final int dynamicCollisionMask,
			final int dynamicProjectileCount) {
			if (x < 0 || y < 0 || blockingSceneryCount < 0
				|| dynamicCollisionMask < 0
				|| dynamicCollisionMask > MAXIMUM_DYNAMIC_COLLISION_MASK
				|| dynamicProjectileCount < 0
				|| (blockingSceneryCount == 0
					&& dynamicCollisionMask == 0
					&& dynamicProjectileCount == 0)) {
				throw new IllegalArgumentException(
					"Collision contribution is invalid");
			}
			this.x = x;
			this.y = y;
			this.blockingSceneryCount = blockingSceneryCount;
			this.dynamicCollisionMask = dynamicCollisionMask;
			this.dynamicProjectileCount = dynamicProjectileCount;
		}

		public static CollisionContribution of(
			final int x,
			final int y,
			final int blockingSceneryCount,
			final int dynamicCollisionMask,
			final int dynamicProjectileCount) {
			return new CollisionContribution(
				x, y, blockingSceneryCount, dynamicCollisionMask,
				dynamicProjectileCount);
		}

		public int getX() { return x; }
		public int getY() { return y; }
		public int getBlockingSceneryCount() {
			return blockingSceneryCount;
		}
		public int getDynamicCollisionMask() {
			return dynamicCollisionMask;
		}
		public int getDynamicProjectileCount() {
			return dynamicProjectileCount;
		}
	}

	private static final class CollisionCoordinate {
		private final int x;
		private final int y;

		private CollisionCoordinate(final int x, final int y) {
			this.x = x;
			this.y = y;
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) { return true; }
			if (!(other instanceof CollisionCoordinate)) { return false; }
			CollisionCoordinate coordinate = (CollisionCoordinate) other;
			return x == coordinate.x && y == coordinate.y;
		}

		@Override
		public int hashCode() { return 31 * x + y; }
	}

	/** Result wrapper; availability never grants rollback authority. */
	public static final class Creation {
		private final Outcome outcome;
		private final Reason reason;
		private final GameTickEventRestorationTransientRollbackSnapshot snapshot;

		private Creation(
			final Outcome outcome,
			final Reason reason,
			final GameTickEventRestorationTransientRollbackSnapshot snapshot) {
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.snapshot = snapshot;
			boolean available = outcome == Outcome.SNAPSHOT_AVAILABLE;
			if (available != (reason == Reason.SNAPSHOT_AVAILABLE)
				|| available != (snapshot != null)) {
				throw new IllegalArgumentException(
					"Transient rollback snapshot result is inconsistent");
			}
		}

		private static Creation refused(final Reason reason) {
			if (reason == Reason.SNAPSHOT_AVAILABLE) {
				throw new IllegalArgumentException(
					"Available reason cannot refuse a snapshot");
			}
			return new Creation(Outcome.REFUSED, reason, null);
		}

		private static Creation available(
			final GameTickEventRestorationTransientRollbackSnapshot snapshot) {
			return new Creation(
				Outcome.SNAPSHOT_AVAILABLE, Reason.SNAPSHOT_AVAILABLE,
				Objects.requireNonNull(snapshot, "snapshot"));
		}

		public Outcome getOutcome() { return outcome; }
		public Reason getReason() { return reason; }
		public GameTickEventRestorationTransientRollbackSnapshot getSnapshot() {
			return snapshot;
		}
		public boolean isRefused() { return outcome == Outcome.REFUSED; }
		public boolean isSnapshotAvailable() {
			return outcome == Outcome.SNAPSHOT_AVAILABLE;
		}
		public boolean isReusablePermit() { return false; }
		public boolean isRollbackAuthorized() { return false; }
		public boolean isRollbackPerformed() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isMutationPerformed() { return false; }
		public boolean isExecutableRestoration() { return false; }
		public boolean isCommitToken() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}
}
