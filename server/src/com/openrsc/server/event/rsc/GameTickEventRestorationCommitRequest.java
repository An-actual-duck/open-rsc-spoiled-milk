package com.openrsc.server.event.rsc;

import java.util.Objects;

/**
 * Ephemeral, closed input for one future authored-scenery commit operation.
 *
 * <p>The scheduler may construct this value only while the exact event
 * execution, registration, authored-generation, and event-lifecycle
 * boundaries are all held or revalidated. The scheduler-store monitor must be
 * absent before the request crosses toward a Region operation.</p>
 *
 * <p>This value is deliberately not a reusable permit. It retains no event,
 * callback, World, Region, entity, collection, monitor, owner, key, or UUID,
 * and no runtime consumer is connected in this slice.</p>
 */
public final class GameTickEventRestorationCommitRequest {
	private final String schedulerInstanceIdentity;
	private final long registrationSequence;
	private final long proposalGeneration;
	private final long authoredGeneration;
	private final long lifecycleVersion;
	private final boolean eventExecutionBoundaryHeld;
	private final boolean schedulerStoreBoundaryHeld;
	private final boolean registrationRevalidated;
	private final boolean lifecycleBoundaryHeld;
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

	private GameTickEventRestorationCommitRequest(
		final String schedulerInstanceIdentity,
		final long registrationSequence,
		final long proposalGeneration,
		final long authoredGeneration,
		final long lifecycleVersion,
		final boolean eventExecutionBoundaryHeld,
		final boolean schedulerStoreBoundaryHeld,
		final boolean registrationRevalidated,
		final boolean lifecycleBoundaryHeld,
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
			|| lifecycleVersion <= 0L
			|| !eventExecutionBoundaryHeld
			|| schedulerStoreBoundaryHeld
			|| !registrationRevalidated
			|| !lifecycleBoundaryHeld
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
				"Restoration commit request is invalid");
		}
		this.registrationSequence = registrationSequence;
		this.proposalGeneration = proposalGeneration;
		this.authoredGeneration = authoredGeneration;
		this.lifecycleVersion = lifecycleVersion;
		this.eventExecutionBoundaryHeld = eventExecutionBoundaryHeld;
		this.schedulerStoreBoundaryHeld = schedulerStoreBoundaryHeld;
		this.registrationRevalidated = registrationRevalidated;
		this.lifecycleBoundaryHeld = lifecycleBoundaryHeld;
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

	public static GameTickEventRestorationCommitRequest request(
		final String schedulerInstanceIdentity,
		final long registrationSequence,
		final long proposalGeneration,
		final long authoredGeneration,
		final long lifecycleVersion,
		final boolean eventExecutionBoundaryHeld,
		final boolean schedulerStoreBoundaryHeld,
		final boolean registrationRevalidated,
		final boolean lifecycleBoundaryHeld,
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
		return new GameTickEventRestorationCommitRequest(
			schedulerInstanceIdentity, registrationSequence,
			proposalGeneration, authoredGeneration, lifecycleVersion,
			eventExecutionBoundaryHeld, schedulerStoreBoundaryHeld,
			registrationRevalidated, lifecycleBoundaryHeld,
			targetOperation, objectId, permanentObjectId, x, y, direction,
			type, forceFullBlock, authoredPackedRegionX,
			authoredPackedRegionY, authoredSourceOrdinal,
			authoredConstructionKind);
	}

	public String getSchedulerInstanceIdentity() {
		return schedulerInstanceIdentity;
	}
	public long getRegistrationSequence() { return registrationSequence; }
	public long getProposalGeneration() { return proposalGeneration; }
	public long getAuthoredGeneration() { return authoredGeneration; }
	public long getLifecycleVersion() { return lifecycleVersion; }
	public boolean isEventExecutionBoundaryHeld() {
		return eventExecutionBoundaryHeld;
	}
	public boolean isSchedulerStoreBoundaryHeld() {
		return schedulerStoreBoundaryHeld;
	}
	public boolean isRegistrationRevalidated() {
		return registrationRevalidated;
	}
	public boolean isLifecycleBoundaryHeld() {
		return lifecycleBoundaryHeld;
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
	public boolean isEphemeralBoundaryValue() { return true; }
	public boolean isReusablePermit() { return false; }
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
