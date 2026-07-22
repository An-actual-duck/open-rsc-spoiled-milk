package com.openrsc.server.event.rsc;

import java.util.Objects;

/**
 * Closed, detached input for one read-only restoration-target revalidation.
 *
 * <p>The scheduler creates this value only after validating an exact event
 * registration and authored generation while holding the event execution
 * boundary. It contains no event, callback, World, Region, entity, collection,
 * monitor, key, UUID, owner text, or mutable runtime attribute.</p>
 */
public final class GameTickEventRestorationTargetRevalidationRequest {
	private final String schedulerInstanceIdentity;
	private final long registrationSequence;
	private final long proposalGeneration;
	private final long authoredGeneration;
	private final boolean eventExecutionBoundaryHeld;
	private final boolean schedulerStoreBoundaryHeld;
	private final boolean registrationValidatedBeforeRegionBoundary;
	private final GameTickEventRestorationTargetDecision.TargetOperation
		targetOperation;
	private final int objectId;
	private final int permanentObjectId;
	private final int x;
	private final int y;
	private final int direction;
	private final int type;
	private final boolean forceFullBlock;
	private final int authoredPackedRegionX;
	private final int authoredPackedRegionY;
	private final int authoredSourceOrdinal;
	private final String authoredConstructionKind;

	private GameTickEventRestorationTargetRevalidationRequest(
		final String schedulerInstanceIdentity,
		final long registrationSequence,
		final long proposalGeneration,
		final long authoredGeneration,
		final boolean eventExecutionBoundaryHeld,
		final boolean schedulerStoreBoundaryHeld,
		final boolean registrationValidatedBeforeRegionBoundary,
		final GameTickEventRestorationTargetDecision.TargetOperation
			targetOperation,
		final int objectId,
		final int permanentObjectId,
		final int x,
		final int y,
		final int direction,
		final int type,
		final boolean forceFullBlock,
		final int authoredPackedRegionX,
		final int authoredPackedRegionY,
		final int authoredSourceOrdinal,
		final String authoredConstructionKind) {
		this.schedulerInstanceIdentity = Objects.requireNonNull(
			schedulerInstanceIdentity, "schedulerInstanceIdentity");
		this.targetOperation = Objects.requireNonNull(
			targetOperation, "targetOperation");
		this.authoredConstructionKind = Objects.requireNonNull(
			authoredConstructionKind, "authoredConstructionKind");
		if (schedulerInstanceIdentity.isEmpty()
			|| registrationSequence <= 0L
			|| proposalGeneration <= 0L
			|| authoredGeneration != proposalGeneration
			|| !eventExecutionBoundaryHeld
			|| schedulerStoreBoundaryHeld
			|| !registrationValidatedBeforeRegionBoundary
			|| targetOperation
				== GameTickEventRestorationTargetDecision.TargetOperation
					.UNAVAILABLE
			|| objectId < 0 || permanentObjectId < 0
			|| x < 0 || y < 0
			|| direction < 0 || direction > 7
			|| (type != 0 && type != 1)
			|| (targetOperation
				== GameTickEventRestorationTargetDecision.TargetOperation
					.SCENERY_REMOVE && forceFullBlock)
			|| authoredPackedRegionX < 0 || authoredPackedRegionY < 0
			|| authoredSourceOrdinal <= 0
			|| authoredSourceOrdinal
				> GameTickEventRestorationState
					.MAXIMUM_AUTHORED_SOURCE_ORDINAL
			|| authoredConstructionKind.isEmpty()) {
			throw new IllegalArgumentException(
				"Restoration target revalidation request is invalid");
		}
		this.registrationSequence = registrationSequence;
		this.proposalGeneration = proposalGeneration;
		this.authoredGeneration = authoredGeneration;
		this.eventExecutionBoundaryHeld = eventExecutionBoundaryHeld;
		this.schedulerStoreBoundaryHeld = schedulerStoreBoundaryHeld;
		this.registrationValidatedBeforeRegionBoundary =
			registrationValidatedBeforeRegionBoundary;
		this.objectId = objectId;
		this.permanentObjectId = permanentObjectId;
		this.x = x;
		this.y = y;
		this.direction = direction;
		this.type = type;
		this.forceFullBlock = forceFullBlock;
		this.authoredPackedRegionX = authoredPackedRegionX;
		this.authoredPackedRegionY = authoredPackedRegionY;
		this.authoredSourceOrdinal = authoredSourceOrdinal;
	}

	public static GameTickEventRestorationTargetRevalidationRequest request(
		final String schedulerInstanceIdentity,
		final long registrationSequence,
		final long proposalGeneration,
		final long authoredGeneration,
		final boolean eventExecutionBoundaryHeld,
		final boolean schedulerStoreBoundaryHeld,
		final boolean registrationValidatedBeforeRegionBoundary,
		final GameTickEventRestorationTargetDecision.TargetOperation
			targetOperation,
		final int objectId,
		final int permanentObjectId,
		final int x,
		final int y,
		final int direction,
		final int type,
		final boolean forceFullBlock,
		final int authoredPackedRegionX,
		final int authoredPackedRegionY,
		final int authoredSourceOrdinal,
		final String authoredConstructionKind) {
		return new GameTickEventRestorationTargetRevalidationRequest(
			schedulerInstanceIdentity, registrationSequence,
			proposalGeneration, authoredGeneration,
			eventExecutionBoundaryHeld, schedulerStoreBoundaryHeld,
			registrationValidatedBeforeRegionBoundary, targetOperation,
			objectId, permanentObjectId, x, y, direction, type, forceFullBlock,
			authoredPackedRegionX, authoredPackedRegionY,
			authoredSourceOrdinal, authoredConstructionKind);
	}

	public String getSchedulerInstanceIdentity() {
		return schedulerInstanceIdentity;
	}
	public long getRegistrationSequence() { return registrationSequence; }
	public long getProposalGeneration() { return proposalGeneration; }
	public long getAuthoredGeneration() { return authoredGeneration; }
	public boolean isEventExecutionBoundaryHeld() {
		return eventExecutionBoundaryHeld;
	}
	public boolean isSchedulerStoreBoundaryHeld() {
		return schedulerStoreBoundaryHeld;
	}
	public boolean isRegistrationValidatedBeforeRegionBoundary() {
		return registrationValidatedBeforeRegionBoundary;
	}
	public GameTickEventRestorationTargetDecision.TargetOperation
		getTargetOperation() {
		return targetOperation;
	}
	public int getObjectId() { return objectId; }
	public int getPermanentObjectId() { return permanentObjectId; }
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDirection() { return direction; }
	public int getType() { return type; }
	public boolean isForceFullBlock() { return forceFullBlock; }
	public int getAuthoredPackedRegionX() { return authoredPackedRegionX; }
	public int getAuthoredPackedRegionY() { return authoredPackedRegionY; }
	public int getAuthoredSourceOrdinal() { return authoredSourceOrdinal; }
	public String getAuthoredConstructionKind() {
		return authoredConstructionKind;
	}

	public boolean isTargetBindingComplete() { return true; }
	public boolean isOwnerStateRetained() { return false; }
	public boolean isRuntimeAttributeStateRetained() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isMutationAuthorized() { return false; }
}
