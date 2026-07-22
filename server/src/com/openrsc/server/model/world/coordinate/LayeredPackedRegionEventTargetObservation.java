package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, immutable target-slot evidence for detached restoration events.
 *
 * <p>Records contain scalar counts, comparison outcomes, and scheduler-local
 * correlation only. They retain no Region, entity, authored-identity object,
 * event, callback, scheduler, registry, packet, or Player handle.</p>
 */
public final class LayeredPackedRegionEventTargetObservation {
	public static final int MAXIMUM_TARGET_RECORDS = 65536;

	private final long proposalGeneration;
	private final long eventInventoryObservedAtTick;
	private final long targetObservedAtTick;
	private final String schedulerInstanceIdentity;
	private final List<TargetRecord> targets;
	private final int availableTargetCount;
	private final int noOpSuccessCount;
	private final int mutationPreconditionSatisfiedCount;
	private final int refusedTargetCount;

	private LayeredPackedRegionEventTargetObservation(
		final long proposalGeneration,
		final long eventInventoryObservedAtTick,
		final long targetObservedAtTick,
		final String schedulerInstanceIdentity,
		final List<TargetRecord> targets,
		final int maximumTargetRecords) {
		if (proposalGeneration <= 0L || eventInventoryObservedAtTick < 0L
			|| targetObservedAtTick < eventInventoryObservedAtTick
			|| maximumTargetRecords < 0
			|| maximumTargetRecords > MAXIMUM_TARGET_RECORDS) {
			throw new IllegalArgumentException(
				"Event target observation metadata is invalid");
		}
		if (schedulerInstanceIdentity == null
			|| schedulerInstanceIdentity.isEmpty()) {
			throw new IllegalArgumentException(
				"Scheduler-instance identity is required");
		}
		Objects.requireNonNull(targets, "targets");
		if (targets.size() > maximumTargetRecords) {
			throw new IllegalArgumentException(
				"Event target observation exceeds its record budget");
		}

		List<TargetRecord> copied =
			new ArrayList<TargetRecord>(targets.size());
		int previousSnapshotOrdinal = -1;
		long previousRegistrationSequence = 0L;
		int available = 0;
		int noOp = 0;
		int mutationPrecondition = 0;
		int refused = 0;
		for (int index = 0; index < targets.size(); index++) {
			TargetRecord target = Objects.requireNonNull(
				targets.get(index), "targets[" + index + "]");
			if (target.getSnapshotOrdinal() <= previousSnapshotOrdinal
				|| target.getRegistrationSequence()
					<= previousRegistrationSequence) {
				throw new IllegalArgumentException(
					"Target records must preserve event snapshot order");
			}
			previousSnapshotOrdinal = target.getSnapshotOrdinal();
			previousRegistrationSequence = target.getRegistrationSequence();
			available += target.isRegionAvailable() ? 1 : 0;
			switch (target.getDecisionOutcome()) {
				case NO_OP_SUCCESS:
					noOp++;
					break;
				case MUTATION_PRECONDITION_SATISFIED:
					mutationPrecondition++;
					break;
				case REFUSED:
					refused++;
					break;
				default:
					throw new IllegalStateException(
						"Unhandled target-decision outcome");
			}
			copied.add(target);
		}
		this.proposalGeneration = proposalGeneration;
		this.eventInventoryObservedAtTick = eventInventoryObservedAtTick;
		this.targetObservedAtTick = targetObservedAtTick;
		this.schedulerInstanceIdentity = schedulerInstanceIdentity;
		this.targets = Collections.unmodifiableList(copied);
		this.availableTargetCount = available;
		this.noOpSuccessCount = noOp;
		this.mutationPreconditionSatisfiedCount = mutationPrecondition;
		this.refusedTargetCount = refused;
	}

	public static LayeredPackedRegionEventTargetObservation observation(
		final long proposalGeneration,
		final long eventInventoryObservedAtTick,
		final long targetObservedAtTick,
		final String schedulerInstanceIdentity,
		final List<TargetRecord> targets,
		final int maximumTargetRecords) {
		return new LayeredPackedRegionEventTargetObservation(
			proposalGeneration, eventInventoryObservedAtTick,
			targetObservedAtTick, schedulerInstanceIdentity, targets,
			maximumTargetRecords);
	}

	public long getProposalGeneration() { return proposalGeneration; }
	public long getEventInventoryObservedAtTick() {
		return eventInventoryObservedAtTick;
	}
	public long getTargetObservedAtTick() { return targetObservedAtTick; }
	public String getSchedulerInstanceIdentity() {
		return schedulerInstanceIdentity;
	}
	public List<TargetRecord> getTargets() { return targets; }
	public int getTargetCount() { return targets.size(); }
	public int getAvailableTargetCount() { return availableTargetCount; }
	public int getUnavailableTargetCount() {
		return targets.size() - availableTargetCount;
	}
	public int getNoOpSuccessCount() { return noOpSuccessCount; }
	public int getMutationPreconditionSatisfiedCount() {
		return mutationPreconditionSatisfiedCount;
	}
	public int getRefusedTargetCount() { return refusedTargetCount; }
	public boolean isOutcomeCountComplete() {
		return noOpSuccessCount + mutationPreconditionSatisfiedCount
			+ refusedTargetCount == targets.size();
	}

	public boolean isPointInTimeOnly() { return true; }
	public boolean isAtomicWithEventInventory() { return false; }
	public boolean isReadOnlyTargetLookupPerformed() { return true; }
	public boolean isEntityHandleRetained() { return false; }
	public boolean isAchievedStateClaimed() { return false; }
	public boolean isCommitToken() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isExecutableRestoration() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** One restoration event's detached exact-slot observation and decision. */
	public static final class TargetRecord {
		private final int snapshotOrdinal;
		private final long registrationSequence;
		private final int x;
		private final int y;
		private final boolean regionAvailable;
		private final int slotObjectCount;
		private final int exactRestorationSceneryCount;
		private final int exactAuthoredIdentityCount;
		private final ObservedTargetState observedTargetState;
		private final Outcome decisionOutcome;
		private final Reason decisionReason;

		private TargetRecord(
			final int snapshotOrdinal,
			final long registrationSequence,
			final int x,
			final int y,
			final boolean regionAvailable,
			final int slotObjectCount,
			final int exactRestorationSceneryCount,
			final int exactAuthoredIdentityCount,
			final boolean targetBindingComplete,
			final Outcome decisionOutcome,
			final Reason decisionReason) {
			if (snapshotOrdinal < 0 || registrationSequence <= 0L
				|| x < 0 || y < 0 || slotObjectCount < 0
				|| exactRestorationSceneryCount < 0
				|| exactRestorationSceneryCount > slotObjectCount
				|| exactAuthoredIdentityCount < 0
				|| exactAuthoredIdentityCount > slotObjectCount) {
				throw new IllegalArgumentException(
					"Event target record is invalid");
			}
			if (!regionAvailable && (slotObjectCount != 0
				|| exactRestorationSceneryCount != 0
				|| exactAuthoredIdentityCount != 0)) {
				throw new IllegalArgumentException(
					"Unavailable Region cannot report target contents");
			}
			ObservedTargetState observation = classifyObservedTargetState(
				regionAvailable, slotObjectCount,
				exactRestorationSceneryCount, exactAuthoredIdentityCount,
				targetBindingComplete);
			Outcome outcome = Objects.requireNonNull(
				decisionOutcome, "decisionOutcome");
			Reason reason = Objects.requireNonNull(
				decisionReason, "decisionReason");
			if ((outcome == Outcome.REFUSED) != reason.isRefusal()) {
				throw new IllegalArgumentException(
					"Target observation outcome and reason disagree");
			}
			this.snapshotOrdinal = snapshotOrdinal;
			this.registrationSequence = registrationSequence;
			this.x = x;
			this.y = y;
			this.regionAvailable = regionAvailable;
			this.slotObjectCount = slotObjectCount;
			this.exactRestorationSceneryCount =
				exactRestorationSceneryCount;
			this.exactAuthoredIdentityCount = exactAuthoredIdentityCount;
			this.observedTargetState = observation;
			this.decisionOutcome = outcome;
			this.decisionReason = reason;
		}

		public static TargetRecord observe(
			final int snapshotOrdinal,
			final long registrationSequence,
			final int x,
			final int y,
			final boolean regionAvailable,
			final int slotObjectCount,
			final int exactRestorationSceneryCount,
			final int exactAuthoredIdentityCount,
			final boolean targetBindingComplete,
			final Outcome decisionOutcome,
			final Reason decisionReason) {
			return new TargetRecord(
				snapshotOrdinal, registrationSequence, x, y, regionAvailable,
				slotObjectCount, exactRestorationSceneryCount,
				exactAuthoredIdentityCount,
				targetBindingComplete, decisionOutcome, decisionReason);
		}

		public static ObservedTargetState classifyObservedTargetState(
			final boolean regionAvailable,
			final int slotObjectCount,
			final int exactRestorationSceneryCount,
			final int exactAuthoredIdentityCount,
			final boolean targetBindingComplete) {
			if (!regionAvailable) {
				return ObservedTargetState.UNAVAILABLE;
			}
			if (slotObjectCount == 0) {
				return ObservedTargetState.EMPTY;
			}
			if (slotObjectCount > 1) {
				return ObservedTargetState.AMBIGUOUS_OCCUPANCY;
			}
			if (targetBindingComplete
				&& exactRestorationSceneryCount == 1
				&& exactAuthoredIdentityCount == 1) {
				return ObservedTargetState
					.EXACT_RESTORATION_SCENERY_PRESENT;
			}
			if (targetBindingComplete && exactAuthoredIdentityCount == 1) {
				return ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT;
			}
			return ObservedTargetState
				.MISMATCHED_OR_IDENTITYLESS_OCCUPANT;
		}

		public int getSnapshotOrdinal() { return snapshotOrdinal; }
		public long getRegistrationSequence() {
			return registrationSequence;
		}
		public int getX() { return x; }
		public int getY() { return y; }
		public boolean isRegionAvailable() { return regionAvailable; }
		public int getSlotObjectCount() { return slotObjectCount; }
		public int getExactRestorationSceneryCount() {
			return exactRestorationSceneryCount;
		}
		public int getExactAuthoredIdentityCount() {
			return exactAuthoredIdentityCount;
		}
		public ObservedTargetState getObservedTargetState() {
			return observedTargetState;
		}
		public Outcome getDecisionOutcome() {
			return decisionOutcome;
		}
		public Reason getDecisionReason() {
			return decisionReason;
		}
	}

	public enum Outcome {
		REFUSED,
		NO_OP_SUCCESS,
		MUTATION_PRECONDITION_SATISFIED
	}

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

		Reason(final boolean refusal) { this.refusal = refusal; }

		private boolean isRefusal() { return refusal; }
	}
}
