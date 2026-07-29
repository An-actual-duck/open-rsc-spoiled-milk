package com.openrsc.server.event.rsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Detached exact current-state snapshot for one future scenery callback.
 *
 * <p>A future callback must not be executed early during source recovery. Its
 * presently visible scenery and exact collision contribution are preserved,
 * while its positive remaining countdown continues to belong to the existing
 * scheduler registration. Spawn and removal callbacks use the same snapshot;
 * their different target classifications are checked explicitly.</p>
 *
 * <p>This class accepts only detached declarations. It never finds a
 * scheduler registration, observes a Region, acquires a monitor, creates an
 * object, applies collision, changes a countdown, or exposes first visibility.
 * The boundary booleans are claims a later coordinator must prove while
 * capturing the inputs, not boundaries acquired by this value.</p>
 */
public final class GameTickEventRestorationCurrentStateRecoverySnapshot {
	public static final int MAXIMUM_COLLISION_CONTRIBUTION_TILES = 4096;
	public static final int MAXIMUM_DYNAMIC_COLLISION_MASK = 0x3f;

	private final CallbackExpectation callback;
	private final CurrentScenery currentScenery;
	private final List<CollisionContribution> collisionContributions;

	private GameTickEventRestorationCurrentStateRecoverySnapshot(
		final CallbackExpectation callback,
		final CurrentScenery currentScenery) {
		this.callback = CallbackExpectation.copyOf(callback);
		this.currentScenery = CurrentScenery.copyOf(currentScenery);
		List<CollisionContribution> canonical =
			new ArrayList<CollisionContribution>(
				currentScenery.getCollisionContributions());
		Collections.sort(canonical, CollisionContribution.ORDER);
		this.collisionContributions = Collections.unmodifiableList(canonical);
	}

	/** Applies the closed future-current-state refusal table. */
	public static Creation assess(
		final CallbackExpectation callback,
		final CurrentScenery currentScenery) {
		CallbackExpectation checkedCallback = Objects.requireNonNull(
			callback, "callback");
		CurrentScenery checkedCurrent = Objects.requireNonNull(
			currentScenery, "currentScenery");

		if (!checkedCallback.isRunning()
			|| checkedCallback.getTimesRan() != 0
			|| !checkedCallback.isOneShotExecution()
			|| !checkedCallback.isContinuingServerTickProgression()) {
			return Creation.refused(Reason.EVENT_SEMANTICS_REFUSED);
		}
		if (checkedCallback.getTicksBeforeRun() <= 0L) {
			return Creation.refused(Reason.CALLBACK_NOT_FUTURE);
		}
		if (checkedCallback.getAuthoredGeneration()
				!= checkedCallback.getProposalGeneration()) {
			return Creation.refused(Reason.PROPOSAL_GENERATION_MISMATCH);
		}
		if (checkedCallback.hasOwner()) {
			return Creation.refused(Reason.CALLBACK_OWNER_BOUND_STATE_REFUSED);
		}
		if (checkedCallback.getRuntimeAttributeCount() != 0) {
			return Creation.refused(
				Reason.CALLBACK_RUNTIME_ATTRIBUTES_NOT_RESTORABLE);
		}
		if (!matchesConstructionKind(
			checkedCallback.getAuthoredConstructionKind(),
			checkedCallback.getType())) {
			return Creation.refused(
				Reason.AUTHORED_CONSTRUCTION_KIND_MISMATCH);
		}
		if (!checkedCurrent.isEventExecutionBoundaryHeldDuringCapture()
			|| !checkedCurrent.isStableLifecycleBoundaryHeldDuringCapture()) {
			return Creation.refused(Reason.EVENT_BOUNDARY_MISSING);
		}
		if (!checkedCurrent.isRegionObjectBoundaryHeldDuringCapture()) {
			return Creation.refused(Reason.REGION_OBJECT_BOUNDARY_MISSING);
		}
		if (!checkedCurrent
			.isOrderedCollisionBoundaryHeldDuringCapture()) {
			return Creation.refused(Reason.COLLISION_BOUNDARY_MISSING);
		}
		if (checkedCurrent.getExactSlotObjectCount() != 1) {
			return Creation.refused(Reason.EXACT_SLOT_NOT_SINGLE_OBJECT);
		}
		if (checkedCurrent.getX() != checkedCallback.getX()
			|| checkedCurrent.getY() != checkedCallback.getY()
			|| checkedCurrent.getType() != checkedCallback.getType()
			|| (checkedCallback.getType() == 1
				&& checkedCurrent.getDirection()
					!= checkedCallback.getDirection())) {
			return Creation.refused(
				Reason.CURRENT_COORDINATE_OR_TYPE_MISMATCH);
		}
		if (checkedCurrent.getAuthoredGeneration()
				!= checkedCallback.getAuthoredGeneration()
			|| checkedCurrent.getAuthoredPackedRegionX()
				!= checkedCallback.getAuthoredPackedRegionX()
			|| checkedCurrent.getAuthoredPackedRegionY()
				!= checkedCallback.getAuthoredPackedRegionY()
			|| checkedCurrent.getAuthoredSourceOrdinal()
				!= checkedCallback.getAuthoredSourceOrdinal()
			|| checkedCurrent.getAuthoredConstructionKind()
				!= checkedCallback.getAuthoredConstructionKind()) {
			return Creation.refused(
				Reason.CURRENT_AUTHORED_IDENTITY_MISMATCH);
		}
		if (checkedCurrent.hasOwner()) {
			return Creation.refused(Reason.CURRENT_OWNER_BOUND_STATE_REFUSED);
		}
		if (checkedCurrent.getRuntimeAttributeCount() != 0) {
			return Creation.refused(
				Reason.CURRENT_RUNTIME_ATTRIBUTES_NOT_RESTORABLE);
		}
		if (!currentClassificationMatchesCallback(
			checkedCallback, checkedCurrent)) {
			return Creation.refused(
				Reason.CURRENT_TARGET_CLASSIFICATION_MISMATCH);
		}
		if (checkedCallback.getKind() == CallbackKind.SCENERY_REMOVE
			&& (checkedCurrent.getObjectId()
					!= checkedCallback.getObjectId()
				|| checkedCurrent.getPermanentObjectId()
					!= checkedCallback.getPermanentObjectId())) {
			return Creation.refused(
				Reason.REMOVAL_CURRENT_CONSTRUCTOR_MISMATCH);
		}
		if (!checkedCurrent.isCollisionContributionComplete()) {
			return Creation.refused(
				Reason.COLLISION_CONTRIBUTION_INCOMPLETE);
		}
		Set<CollisionCoordinate> coordinates =
			new HashSet<CollisionCoordinate>();
		for (CollisionContribution contribution
			: checkedCurrent.getCollisionContributions()) {
			if (!coordinates.add(new CollisionCoordinate(
				contribution.getX(), contribution.getY()))) {
				return Creation.refused(
					Reason.DUPLICATE_COLLISION_CONTRIBUTION_TILE);
			}
		}
		return Creation.available(
			new GameTickEventRestorationCurrentStateRecoverySnapshot(
				checkedCallback, checkedCurrent));
	}

	private static boolean currentClassificationMatchesCallback(
		final CallbackExpectation callback,
		final CurrentScenery current) {
		return callback.getKind() == CallbackKind.SCENERY_SPAWN
			? current.getObservedState()
				== ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT
			: callback.getKind() == CallbackKind.SCENERY_REMOVE
				&& current.getObservedState()
					== ObservedCurrentState
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

	public CallbackKind getCallbackKind() { return callback.getKind(); }
	public String getSchedulerInstanceIdentity() {
		return callback.getSchedulerInstanceIdentity();
	}
	public long getRegistrationSequence() {
		return callback.getRegistrationSequence();
	}
	public long getProposalGeneration() {
		return callback.getProposalGeneration();
	}
	public long getLifecycleVersion() {
		return callback.getLifecycleVersion();
	}
	public long getTicksBeforeRun() { return callback.getTicksBeforeRun(); }
	public int getCallbackObjectId() { return callback.getObjectId(); }
	public int getCallbackPermanentObjectId() {
		return callback.getPermanentObjectId();
	}
	public int getCurrentObjectId() { return currentScenery.getObjectId(); }
	public int getCurrentPermanentObjectId() {
		return currentScenery.getPermanentObjectId();
	}
	public int getX() { return currentScenery.getX(); }
	public int getY() { return currentScenery.getY(); }
	public int getDirection() { return currentScenery.getDirection(); }
	public int getType() { return currentScenery.getType(); }
	public long getAuthoredGeneration() {
		return currentScenery.getAuthoredGeneration();
	}
	public int getAuthoredPackedRegionX() {
		return currentScenery.getAuthoredPackedRegionX();
	}
	public int getAuthoredPackedRegionY() {
		return currentScenery.getAuthoredPackedRegionY();
	}
	public int getAuthoredSourceOrdinal() {
		return currentScenery.getAuthoredSourceOrdinal();
	}
	public AuthoredConstructionKind getAuthoredConstructionKind() {
		return currentScenery.getAuthoredConstructionKind();
	}
	public ObservedCurrentState getObservedCurrentState() {
		return currentScenery.getObservedState();
	}
	public List<CollisionContribution> getCollisionContributions() {
		return collisionContributions;
	}
	public int getCollisionContributionTileCount() {
		return collisionContributions.size();
	}

	public boolean isFutureCallback() { return true; }
	public boolean isCurrentStateKeptSeparateFromDesiredState() { return true; }
	public boolean isCallbackRetainedScheduled() { return true; }
	public boolean isConstructorStateComplete() { return true; }
	public boolean isAuthoredIdentityComplete() { return true; }
	public boolean isCollisionContributionComplete() { return true; }
	public boolean isOpaqueRuntimeAttributeStateCaptured() { return false; }
	public boolean isOwnerBoundStateCaptured() { return false; }
	public boolean isRuntimeConsumerConnected() { return true; }
	public boolean isRuntimeObservationPerformed() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRegionLoadingPerformed() { return false; }
	public boolean isMutationAuthorized() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isCallbackInvoked() { return false; }
	public boolean isEventCancellation() { return false; }
	public boolean isEventReschedule() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	public enum CallbackKind { SCENERY_SPAWN, SCENERY_REMOVE }

	public enum ObservedCurrentState {
		EXACT_AUTHORED_TRANSIENT_PRESENT,
		EXACT_RESTORATION_SCENERY_PRESENT
	}

	public enum AuthoredConstructionKind {
		SCENERY,
		BOUNDARY,
		NPC_SPAWN,
		GROUND_ITEM_SPAWN,
		HARVESTING_SCENERY
	}

	public enum Outcome { REFUSED, SNAPSHOT_AVAILABLE }

	public enum Reason {
		EVENT_SEMANTICS_REFUSED,
		CALLBACK_NOT_FUTURE,
		PROPOSAL_GENERATION_MISMATCH,
		CALLBACK_OWNER_BOUND_STATE_REFUSED,
		CALLBACK_RUNTIME_ATTRIBUTES_NOT_RESTORABLE,
		AUTHORED_CONSTRUCTION_KIND_MISMATCH,
		EVENT_BOUNDARY_MISSING,
		REGION_OBJECT_BOUNDARY_MISSING,
		COLLISION_BOUNDARY_MISSING,
		EXACT_SLOT_NOT_SINGLE_OBJECT,
		CURRENT_COORDINATE_OR_TYPE_MISMATCH,
		CURRENT_AUTHORED_IDENTITY_MISMATCH,
		CURRENT_OWNER_BOUND_STATE_REFUSED,
		CURRENT_RUNTIME_ATTRIBUTES_NOT_RESTORABLE,
		CURRENT_TARGET_CLASSIFICATION_MISMATCH,
		REMOVAL_CURRENT_CONSTRUCTOR_MISMATCH,
		COLLISION_CONTRIBUTION_INCOMPLETE,
		DUPLICATE_COLLISION_CONTRIBUTION_TILE,
		SNAPSHOT_AVAILABLE
	}

	/** Detached callback, scheduler, and desired-constructor expectation. */
	public static final class CallbackExpectation {
		private final CallbackKind kind;
		private final String schedulerInstanceIdentity;
		private final long registrationSequence;
		private final long proposalGeneration;
		private final long lifecycleVersion;
		private final long ticksBeforeRun;
		private final int timesRan;
		private final boolean running;
		private final boolean oneShotExecution;
		private final boolean continuingServerTickProgression;
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
		private final AuthoredConstructionKind authoredConstructionKind;

		private CallbackExpectation(
			final CallbackKind kind,
			final String schedulerInstanceIdentity,
			final long registrationSequence,
			final long proposalGeneration,
			final long lifecycleVersion,
			final long ticksBeforeRun,
			final int timesRan,
			final boolean running,
			final boolean oneShotExecution,
			final boolean continuingServerTickProgression,
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
			final AuthoredConstructionKind authoredConstructionKind) {
			if (schedulerInstanceIdentity == null
				|| schedulerInstanceIdentity.isEmpty()
				|| registrationSequence <= 0L
				|| proposalGeneration <= 0L
				|| lifecycleVersion <= 0L || timesRan < 0
				|| objectId < 0 || permanentObjectId < 0
				|| x < 0 || y < 0 || direction < 0 || direction > 7
				|| (type != 0 && type != 1)
				|| runtimeAttributeCount < 0
				|| authoredGeneration <= 0L
				|| authoredPackedRegionX < 0 || authoredPackedRegionY < 0
				|| authoredSourceOrdinal <= 0
				|| authoredSourceOrdinal
					> GameTickEventRestorationState
						.MAXIMUM_AUTHORED_SOURCE_ORDINAL) {
				throw new IllegalArgumentException(
					"Future callback expectation is invalid");
			}
			this.kind = Objects.requireNonNull(kind, "kind");
			this.schedulerInstanceIdentity = schedulerInstanceIdentity;
			this.registrationSequence = registrationSequence;
			this.proposalGeneration = proposalGeneration;
			this.lifecycleVersion = lifecycleVersion;
			this.ticksBeforeRun = ticksBeforeRun;
			this.timesRan = timesRan;
			this.running = running;
			this.oneShotExecution = oneShotExecution;
			this.continuingServerTickProgression =
				continuingServerTickProgression;
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
		}

		public static CallbackExpectation declare(
			final CallbackKind kind,
			final String schedulerInstanceIdentity,
			final long registrationSequence,
			final long proposalGeneration,
			final long lifecycleVersion,
			final long ticksBeforeRun,
			final int timesRan,
			final boolean running,
			final boolean oneShotExecution,
			final boolean continuingServerTickProgression,
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
			final AuthoredConstructionKind authoredConstructionKind) {
			return new CallbackExpectation(
				kind, schedulerInstanceIdentity, registrationSequence,
				proposalGeneration, lifecycleVersion, ticksBeforeRun,
				timesRan, running, oneShotExecution,
				continuingServerTickProgression, objectId,
				permanentObjectId, x, y, direction, type, owner,
				runtimeAttributeCount, authoredGeneration,
				authoredPackedRegionX, authoredPackedRegionY,
				authoredSourceOrdinal, authoredConstructionKind);
		}

		private static CallbackExpectation copyOf(
			final CallbackExpectation source) {
			return declare(
				source.kind, source.schedulerInstanceIdentity,
				source.registrationSequence, source.proposalGeneration,
				source.lifecycleVersion, source.ticksBeforeRun,
				source.timesRan, source.running, source.oneShotExecution,
				source.continuingServerTickProgression, source.objectId,
				source.permanentObjectId, source.x, source.y,
				source.direction, source.type, source.owner,
				source.runtimeAttributeCount, source.authoredGeneration,
				source.authoredPackedRegionX,
				source.authoredPackedRegionY,
				source.authoredSourceOrdinal,
				source.authoredConstructionKind);
		}

		public CallbackKind getKind() { return kind; }
		public String getSchedulerInstanceIdentity() {
			return schedulerInstanceIdentity;
		}
		public long getRegistrationSequence() {
			return registrationSequence;
		}
		public long getProposalGeneration() { return proposalGeneration; }
		public long getLifecycleVersion() { return lifecycleVersion; }
		public long getTicksBeforeRun() { return ticksBeforeRun; }
		public int getTimesRan() { return timesRan; }
		public boolean isRunning() { return running; }
		public boolean isOneShotExecution() { return oneShotExecution; }
		public boolean isContinuingServerTickProgression() {
			return continuingServerTickProgression;
		}
		public int getObjectId() { return objectId; }
		public int getPermanentObjectId() { return permanentObjectId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getDirection() { return direction; }
		public int getType() { return type; }
		public boolean hasOwner() { return owner != null; }
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
		public AuthoredConstructionKind getAuthoredConstructionKind() {
			return authoredConstructionKind;
		}
	}

	/** Detached exact current scenery and collision declaration. */
	public static final class CurrentScenery {
		private final ObservedCurrentState observedState;
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
		private final AuthoredConstructionKind authoredConstructionKind;
		private final int exactSlotObjectCount;
		private final boolean eventExecutionBoundaryHeldDuringCapture;
		private final boolean stableLifecycleBoundaryHeldDuringCapture;
		private final boolean regionObjectBoundaryHeldDuringCapture;
		private final boolean orderedCollisionBoundaryHeldDuringCapture;
		private final boolean collisionContributionComplete;
		private final List<CollisionContribution> collisionContributions;

		private CurrentScenery(
			final ObservedCurrentState observedState,
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
			final AuthoredConstructionKind authoredConstructionKind,
			final int exactSlotObjectCount,
			final boolean eventExecutionBoundaryHeldDuringCapture,
			final boolean stableLifecycleBoundaryHeldDuringCapture,
			final boolean regionObjectBoundaryHeldDuringCapture,
			final boolean orderedCollisionBoundaryHeldDuringCapture,
			final boolean collisionContributionComplete,
			final List<CollisionContribution> collisionContributions) {
			if (objectId < 0 || permanentObjectId < 0
				|| x < 0 || y < 0 || direction < 0 || direction > 7
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
					"Current scenery recovery declaration is invalid");
			}
			List<CollisionContribution> checked = Objects.requireNonNull(
				collisionContributions, "collisionContributions");
			if (checked.size() > MAXIMUM_COLLISION_CONTRIBUTION_TILES
				|| checked.contains(null)) {
				throw new IllegalArgumentException(
					"Current scenery collision contribution exceeds its bound");
			}
			this.observedState = Objects.requireNonNull(
				observedState, "observedState");
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
			this.eventExecutionBoundaryHeldDuringCapture =
				eventExecutionBoundaryHeldDuringCapture;
			this.stableLifecycleBoundaryHeldDuringCapture =
				stableLifecycleBoundaryHeldDuringCapture;
			this.regionObjectBoundaryHeldDuringCapture =
				regionObjectBoundaryHeldDuringCapture;
			this.orderedCollisionBoundaryHeldDuringCapture =
				orderedCollisionBoundaryHeldDuringCapture;
			this.collisionContributionComplete =
				collisionContributionComplete;
			this.collisionContributions = Collections.unmodifiableList(
				new ArrayList<CollisionContribution>(checked));
		}

		public static CurrentScenery declare(
			final ObservedCurrentState observedState,
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
			final AuthoredConstructionKind authoredConstructionKind,
			final int exactSlotObjectCount,
			final boolean eventExecutionBoundaryHeldDuringCapture,
			final boolean stableLifecycleBoundaryHeldDuringCapture,
			final boolean regionObjectBoundaryHeldDuringCapture,
			final boolean orderedCollisionBoundaryHeldDuringCapture,
			final boolean collisionContributionComplete,
			final List<CollisionContribution> collisionContributions) {
			return new CurrentScenery(
				observedState, objectId, permanentObjectId, x, y,
				direction, type, owner, runtimeAttributeCount,
				authoredGeneration, authoredPackedRegionX,
				authoredPackedRegionY, authoredSourceOrdinal,
				authoredConstructionKind, exactSlotObjectCount,
				eventExecutionBoundaryHeldDuringCapture,
				stableLifecycleBoundaryHeldDuringCapture,
				regionObjectBoundaryHeldDuringCapture,
				orderedCollisionBoundaryHeldDuringCapture,
				collisionContributionComplete, collisionContributions);
		}

		private static CurrentScenery copyOf(final CurrentScenery source) {
			return declare(
				source.observedState, source.objectId,
				source.permanentObjectId, source.x, source.y,
				source.direction, source.type, source.owner,
				source.runtimeAttributeCount, source.authoredGeneration,
				source.authoredPackedRegionX,
				source.authoredPackedRegionY,
				source.authoredSourceOrdinal,
				source.authoredConstructionKind,
				source.exactSlotObjectCount,
				source.eventExecutionBoundaryHeldDuringCapture,
				source.stableLifecycleBoundaryHeldDuringCapture,
				source.regionObjectBoundaryHeldDuringCapture,
				source.orderedCollisionBoundaryHeldDuringCapture,
				source.collisionContributionComplete,
				source.collisionContributions);
		}

		public ObservedCurrentState getObservedState() {
			return observedState;
		}
		public int getObjectId() { return objectId; }
		public int getPermanentObjectId() { return permanentObjectId; }
		public int getX() { return x; }
		public int getY() { return y; }
		public int getDirection() { return direction; }
		public int getType() { return type; }
		public boolean hasOwner() { return owner != null; }
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
		public AuthoredConstructionKind getAuthoredConstructionKind() {
			return authoredConstructionKind;
		}
		public int getExactSlotObjectCount() {
			return exactSlotObjectCount;
		}
		public boolean isEventExecutionBoundaryHeldDuringCapture() {
			return eventExecutionBoundaryHeldDuringCapture;
		}
		public boolean isStableLifecycleBoundaryHeldDuringCapture() {
			return stableLifecycleBoundaryHeldDuringCapture;
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

	/** Exact contribution made by the current scenery to one collision tile. */
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
		private final int[] dynamicCollisionCounts;
		private final int dynamicProjectileCount;

		private CollisionContribution(
			final int x,
			final int y,
			final int blockingSceneryCount,
			final int[] dynamicCollisionCounts,
			final int dynamicProjectileCount) {
			int[] checked = Objects.requireNonNull(
				dynamicCollisionCounts, "dynamicCollisionCounts");
			if (checked.length != 6 || x < 0 || y < 0
				|| blockingSceneryCount < 0
				|| dynamicProjectileCount < 0) {
				throw new IllegalArgumentException(
					"Current collision contribution is invalid");
			}
			int mask = 0;
			for (int bit = 0; bit < checked.length; bit++) {
				if (checked[bit] < 0) {
					throw new IllegalArgumentException(
						"Current collision contribution count is invalid");
				}
				if (checked[bit] > 0) { mask |= 1 << bit; }
			}
			if (blockingSceneryCount == 0 && mask == 0
				&& dynamicProjectileCount == 0) {
				throw new IllegalArgumentException(
					"Current collision contribution is empty");
			}
			this.x = x;
			this.y = y;
			this.blockingSceneryCount = blockingSceneryCount;
			this.dynamicCollisionMask = mask;
			this.dynamicCollisionCounts = checked.clone();
			this.dynamicProjectileCount = dynamicProjectileCount;
		}

		public static CollisionContribution of(
			final int x,
			final int y,
			final int blockingSceneryCount,
			final int dynamicCollisionMask,
			final int dynamicProjectileCount) {
			if (dynamicCollisionMask < 0
				|| dynamicCollisionMask > MAXIMUM_DYNAMIC_COLLISION_MASK) {
				throw new IllegalArgumentException(
					"Current collision contribution mask is invalid");
			}
			int[] counts = new int[6];
			for (int bit = 0; bit < counts.length; bit++) {
				counts[bit] = (dynamicCollisionMask & (1 << bit)) == 0
					? 0 : 1;
			}
			return new CollisionContribution(
				x, y, blockingSceneryCount, counts,
				dynamicProjectileCount);
		}

		public static CollisionContribution ofCounts(
			final int x,
			final int y,
			final int blockingSceneryCount,
			final int[] dynamicCollisionCounts,
			final int dynamicProjectileCount) {
			return new CollisionContribution(
				x, y, blockingSceneryCount, dynamicCollisionCounts,
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
		public int[] getDynamicCollisionCounts() {
			return dynamicCollisionCounts.clone();
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

	/** Result wrapper; availability grants no restoration authority. */
	public static final class Creation {
		private final Outcome outcome;
		private final Reason reason;
		private final GameTickEventRestorationCurrentStateRecoverySnapshot
			snapshot;

		private Creation(
			final Outcome outcome,
			final Reason reason,
			final GameTickEventRestorationCurrentStateRecoverySnapshot
				snapshot) {
			this.outcome = Objects.requireNonNull(outcome, "outcome");
			this.reason = Objects.requireNonNull(reason, "reason");
			this.snapshot = snapshot;
			boolean available = outcome == Outcome.SNAPSHOT_AVAILABLE;
			if (available != (reason == Reason.SNAPSHOT_AVAILABLE)
				|| available != (snapshot != null)) {
				throw new IllegalArgumentException(
					"Current-state recovery result is inconsistent");
			}
		}

		private static Creation refused(final Reason reason) {
			if (reason == Reason.SNAPSHOT_AVAILABLE) {
				throw new IllegalArgumentException(
					"Available reason cannot refuse current state");
			}
			return new Creation(Outcome.REFUSED, reason, null);
		}

		private static Creation available(
			final GameTickEventRestorationCurrentStateRecoverySnapshot
				snapshot) {
			return new Creation(
				Outcome.SNAPSHOT_AVAILABLE, Reason.SNAPSHOT_AVAILABLE,
				Objects.requireNonNull(snapshot, "snapshot"));
		}

		public Outcome getOutcome() { return outcome; }
		public Reason getReason() { return reason; }
		public GameTickEventRestorationCurrentStateRecoverySnapshot
			getSnapshot() { return snapshot; }
		public boolean isRefused() { return outcome == Outcome.REFUSED; }
		public boolean isSnapshotAvailable() {
			return outcome == Outcome.SNAPSHOT_AVAILABLE;
		}
		public boolean isReusablePermit() { return false; }
		public boolean isMutationAuthorized() { return false; }
		public boolean isMutationPerformed() { return false; }
		public boolean isCallbackInvoked() { return false; }
		public boolean isEventReschedule() { return false; }
		public boolean isArrivalGate() { return false; }
		public boolean isLifecycleAuthority() { return false; }
	}
}
