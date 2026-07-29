package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, detached results from composed scheduler/Region target checks.
 *
 * <p>Each record corresponds to one restoration-capable entry in an earlier
 * event inventory. Runtime handles never enter this value. A successful record
 * describes one stable read-only observation window only and is stale as soon
 * as its event and Region boundaries are released.</p>
 */
public final class LayeredPackedRegionEventAtomicTargetRevalidation {
	public static final int MAXIMUM_RECORDS = 65536;

	private final long proposalGeneration;
	private final long eventInventoryObservedAtTick;
	private final long revalidationObservedAtTick;
	private final String schedulerInstanceIdentity;
	private final List<Record> records;
	private final int outerFenceAcceptedCount;
	private final int outerFenceRefusedCount;
	private final int lifecycleChangeDetectedCount;
	private final int runtimeTargetLookupPerformedCount;
	private final int runtimeRevalidationPerformedCount;
	private final int contractRefusedCount;
	private final int noOpContractSatisfiedCount;
	private final int mutationPreconditionContractSatisfiedCount;

	private LayeredPackedRegionEventAtomicTargetRevalidation(
		final long proposalGeneration,
		final long eventInventoryObservedAtTick,
		final long revalidationObservedAtTick,
		final String schedulerInstanceIdentity,
		final List<Record> records,
		final int maximumRecords) {
		if (proposalGeneration <= 0L || eventInventoryObservedAtTick < 0L
			|| revalidationObservedAtTick < eventInventoryObservedAtTick
			|| maximumRecords < 0 || maximumRecords > MAXIMUM_RECORDS) {
			throw new IllegalArgumentException(
				"Atomic target revalidation metadata is invalid");
		}
		if (schedulerInstanceIdentity == null
			|| schedulerInstanceIdentity.isEmpty()) {
			throw new IllegalArgumentException(
				"Scheduler-instance identity is required");
		}
		Objects.requireNonNull(records, "records");
		if (records.size() > maximumRecords) {
			throw new IllegalArgumentException(
				"Atomic target revalidation exceeds its record budget");
		}
		List<Record> copied = new ArrayList<Record>(records.size());
		int previousOrdinal = -1;
		long previousRegistration = 0L;
		int accepted = 0;
		int refused = 0;
		int lifecycleChanged = 0;
		int targetLookup = 0;
		int runtimeRevalidation = 0;
		int contractRefused = 0;
		int noOp = 0;
		int mutationPrecondition = 0;
		for (int index = 0; index < records.size(); index++) {
			Record record = Objects.requireNonNull(
				records.get(index), "records[" + index + "]");
			if (record.getSnapshotOrdinal() <= previousOrdinal
				|| record.getRegistrationSequence()
					<= previousRegistration) {
				throw new IllegalArgumentException(
					"Atomic target records must preserve inventory order");
			}
			previousOrdinal = record.getSnapshotOrdinal();
			previousRegistration = record.getRegistrationSequence();
			accepted += record.isOuterFenceAccepted() ? 1 : 0;
			refused += record.isOuterFenceAccepted() ? 0 : 1;
			lifecycleChanged += record.isLifecycleChangeDetected() ? 1 : 0;
			targetLookup += record.isRuntimeTargetLookupPerformed() ? 1 : 0;
			runtimeRevalidation +=
				record.isRuntimeRevalidationPerformed() ? 1 : 0;
			if (record.getTarget() != null) {
				switch (record.getTarget().getContractOutcome()) {
					case REFUSED:
						contractRefused++;
						break;
					case NO_OP_CONTRACT_SATISFIED:
						noOp++;
						break;
					case MUTATION_PRECONDITION_CONTRACT_SATISFIED:
						mutationPrecondition++;
						break;
					default:
						throw new IllegalStateException(
							"Unhandled atomic target contract outcome");
				}
			}
			copied.add(record);
		}
		this.proposalGeneration = proposalGeneration;
		this.eventInventoryObservedAtTick = eventInventoryObservedAtTick;
		this.revalidationObservedAtTick = revalidationObservedAtTick;
		this.schedulerInstanceIdentity = schedulerInstanceIdentity;
		this.records = Collections.unmodifiableList(copied);
		this.outerFenceAcceptedCount = accepted;
		this.outerFenceRefusedCount = refused;
		this.lifecycleChangeDetectedCount = lifecycleChanged;
		this.runtimeTargetLookupPerformedCount = targetLookup;
		this.runtimeRevalidationPerformedCount = runtimeRevalidation;
		this.contractRefusedCount = contractRefused;
		this.noOpContractSatisfiedCount = noOp;
		this.mutationPreconditionContractSatisfiedCount = mutationPrecondition;
	}

	public static LayeredPackedRegionEventAtomicTargetRevalidation observation(
		final long proposalGeneration,
		final long eventInventoryObservedAtTick,
		final long revalidationObservedAtTick,
		final String schedulerInstanceIdentity,
		final List<Record> records,
		final int maximumRecords) {
		return new LayeredPackedRegionEventAtomicTargetRevalidation(
			proposalGeneration, eventInventoryObservedAtTick,
			revalidationObservedAtTick, schedulerInstanceIdentity,
			records, maximumRecords);
	}

	public long getProposalGeneration() { return proposalGeneration; }
	public long getEventInventoryObservedAtTick() {
		return eventInventoryObservedAtTick;
	}
	public long getRevalidationObservedAtTick() {
		return revalidationObservedAtTick;
	}
	public String getSchedulerInstanceIdentity() {
		return schedulerInstanceIdentity;
	}
	public List<Record> getRecords() { return records; }
	public int getRecordCount() { return records.size(); }
	public int getOuterFenceAcceptedCount() {
		return outerFenceAcceptedCount;
	}
	public int getOuterFenceRefusedCount() { return outerFenceRefusedCount; }
	public int getLifecycleChangeDetectedCount() {
		return lifecycleChangeDetectedCount;
	}
	public int getRuntimeTargetLookupPerformedCount() {
		return runtimeTargetLookupPerformedCount;
	}
	public int getRuntimeRevalidationPerformedCount() {
		return runtimeRevalidationPerformedCount;
	}
	public int getContractRefusedCount() { return contractRefusedCount; }
	public int getNoOpContractSatisfiedCount() {
		return noOpContractSatisfiedCount;
	}
	public int getMutationPreconditionContractSatisfiedCount() {
		return mutationPreconditionContractSatisfiedCount;
	}
	public boolean isOuterOutcomeCountComplete() {
		return outerFenceAcceptedCount + outerFenceRefusedCount == records.size();
	}
	public boolean isAcceptedContractOutcomeCountComplete() {
		return contractRefusedCount + noOpContractSatisfiedCount
			+ mutationPreconditionContractSatisfiedCount
			== outerFenceAcceptedCount;
	}

	public boolean isPointInTimeOnly() { return true; }
	public boolean isAtomicWithEventInventory() { return false; }
	public boolean isRuntimeTargetLookupPerformed() {
		return runtimeTargetLookupPerformedCount > 0;
	}
	public boolean isRuntimeRevalidationPerformed() {
		return runtimeRevalidationPerformedCount > 0;
	}
	public boolean isAtomicWithMutation() { return false; }
	public boolean isEntityHandleRetained() { return false; }
	public boolean isAchievedStateClaimed() { return false; }
	public boolean isCommitToken() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isExecutableRestoration() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** One inventory-correlated outer-fence outcome and optional target facts. */
	public static final class Record {
		private final int snapshotOrdinal;
		private final long registrationSequence;
		private final int x;
		private final int y;
		private final OuterFenceReason outerFenceReason;
		private final boolean operationInvoked;
		private final Long lifecycleVersionBeforeOperation;
		private final Long lifecycleVersionAfterOperation;
		private final boolean timingStableAcrossOperation;
		private final boolean runtimeTargetLookupPerformed;
		private final TargetEvidence target;

		private Record(
			final int snapshotOrdinal,
			final long registrationSequence,
			final int x,
			final int y,
			final OuterFenceReason outerFenceReason,
			final boolean operationInvoked,
			final Long lifecycleVersionBeforeOperation,
			final Long lifecycleVersionAfterOperation,
			final boolean timingStableAcrossOperation,
			final boolean runtimeTargetLookupPerformed,
			final TargetEvidence target) {
			if (snapshotOrdinal < 0 || registrationSequence <= 0L
				|| x < 0 || y < 0) {
				throw new IllegalArgumentException(
					"Atomic target record identity is invalid");
			}
			this.outerFenceReason = Objects.requireNonNull(
				outerFenceReason, "outerFenceReason");
			boolean accepted = outerFenceReason
				== OuterFenceReason.OPERATION_COMPLETED;
			boolean versionsAvailable = lifecycleVersionBeforeOperation != null
				&& lifecycleVersionAfterOperation != null;
			if (accepted != (target != null)
				|| operationInvoked != versionsAvailable
				|| (versionsAvailable
					&& (lifecycleVersionBeforeOperation.longValue() <= 0L
						|| lifecycleVersionAfterOperation.longValue() <= 0L))
				|| (accepted
					&& (!operationInvoked || !timingStableAcrossOperation
						|| lifecycleVersionBeforeOperation.longValue()
							!= lifecycleVersionAfterOperation.longValue()))
				|| (outerFenceReason
					== OuterFenceReason
						.EVENT_LIFECYCLE_CHANGED_DURING_OPERATION
					&& (!operationInvoked || timingStableAcrossOperation
						|| lifecycleVersionBeforeOperation.longValue()
							== lifecycleVersionAfterOperation.longValue()))
				|| runtimeTargetLookupPerformed != operationInvoked) {
				throw new IllegalArgumentException(
					"Atomic target record evidence is inconsistent");
			}
			this.snapshotOrdinal = snapshotOrdinal;
			this.registrationSequence = registrationSequence;
			this.x = x;
			this.y = y;
			this.operationInvoked = operationInvoked;
			this.lifecycleVersionBeforeOperation =
				lifecycleVersionBeforeOperation;
			this.lifecycleVersionAfterOperation =
				lifecycleVersionAfterOperation;
			this.timingStableAcrossOperation = timingStableAcrossOperation;
			this.runtimeTargetLookupPerformed = runtimeTargetLookupPerformed;
			this.target = target;
		}

		public static Record record(
			final int snapshotOrdinal,
			final long registrationSequence,
			final int x,
			final int y,
			final OuterFenceReason outerFenceReason,
			final boolean operationInvoked,
			final Long lifecycleVersionBeforeOperation,
			final Long lifecycleVersionAfterOperation,
			final boolean timingStableAcrossOperation,
			final boolean runtimeTargetLookupPerformed,
			final TargetEvidence target) {
			return new Record(
				snapshotOrdinal, registrationSequence, x, y,
				outerFenceReason, operationInvoked,
				lifecycleVersionBeforeOperation,
				lifecycleVersionAfterOperation,
				timingStableAcrossOperation,
				runtimeTargetLookupPerformed, target);
		}

		public int getSnapshotOrdinal() { return snapshotOrdinal; }
		public long getRegistrationSequence() { return registrationSequence; }
		public int getX() { return x; }
		public int getY() { return y; }
		public OuterFenceReason getOuterFenceReason() {
			return outerFenceReason;
		}
		public boolean isOuterFenceAccepted() {
			return outerFenceReason == OuterFenceReason.OPERATION_COMPLETED;
		}
		public boolean isOperationInvoked() { return operationInvoked; }
		public Long getLifecycleVersionBeforeOperation() {
			return lifecycleVersionBeforeOperation;
		}
		public Long getLifecycleVersionAfterOperation() {
			return lifecycleVersionAfterOperation;
		}
		public boolean isTimingStableAcrossOperation() {
			return timingStableAcrossOperation;
		}
		public boolean isLifecycleChangeDetected() {
			return outerFenceReason
				== OuterFenceReason
					.EVENT_LIFECYCLE_CHANGED_DURING_OPERATION;
		}
		public boolean isRuntimeTargetLookupPerformed() {
			return runtimeTargetLookupPerformed;
		}
		public boolean isRuntimeRevalidationPerformed() {
			return target != null && target.isRuntimeRevalidationPerformed();
		}
		public TargetEvidence getTarget() { return target; }
	}

	/** Closed Region counts, classifications, and contract outcome. */
	public static final class TargetEvidence {
		private final boolean regionAvailable;
		private final int slotObjectCount;
		private final int exactRestorationSceneryCount;
		private final int exactAuthoredIdentityCount;
		private final boolean objectBoundaryHeldDuringClassification;
		private final ObservedTargetState observedTargetState;
		private final TargetOutcome targetOutcome;
		private final TargetReason targetReason;
		private final ContractOutcome contractOutcome;
		private final ContractReason contractReason;

		private TargetEvidence(
			final boolean regionAvailable,
			final int slotObjectCount,
			final int exactRestorationSceneryCount,
			final int exactAuthoredIdentityCount,
			final boolean objectBoundaryHeldDuringClassification,
			final ObservedTargetState observedTargetState,
			final TargetOutcome targetOutcome,
			final TargetReason targetReason,
			final ContractOutcome contractOutcome,
			final ContractReason contractReason) {
			if (slotObjectCount < 0
				|| exactRestorationSceneryCount < 0
				|| exactRestorationSceneryCount > slotObjectCount
				|| exactAuthoredIdentityCount < 0
				|| exactAuthoredIdentityCount > slotObjectCount
				|| regionAvailable
					!= objectBoundaryHeldDuringClassification) {
				throw new IllegalArgumentException(
					"Atomic target evidence counts are invalid");
			}
			this.regionAvailable = regionAvailable;
			this.slotObjectCount = slotObjectCount;
			this.exactRestorationSceneryCount =
				exactRestorationSceneryCount;
			this.exactAuthoredIdentityCount = exactAuthoredIdentityCount;
			this.objectBoundaryHeldDuringClassification =
				objectBoundaryHeldDuringClassification;
			this.observedTargetState = Objects.requireNonNull(
				observedTargetState, "observedTargetState");
			this.targetOutcome = Objects.requireNonNull(
				targetOutcome, "targetOutcome");
			this.targetReason = Objects.requireNonNull(
				targetReason, "targetReason");
			this.contractOutcome = Objects.requireNonNull(
				contractOutcome, "contractOutcome");
			this.contractReason = Objects.requireNonNull(
				contractReason, "contractReason");
		}

		public static TargetEvidence evidence(
			final boolean regionAvailable,
			final int slotObjectCount,
			final int exactRestorationSceneryCount,
			final int exactAuthoredIdentityCount,
			final boolean objectBoundaryHeldDuringClassification,
			final ObservedTargetState observedTargetState,
			final TargetOutcome targetOutcome,
			final TargetReason targetReason,
			final ContractOutcome contractOutcome,
			final ContractReason contractReason) {
			return new TargetEvidence(
				regionAvailable, slotObjectCount,
				exactRestorationSceneryCount, exactAuthoredIdentityCount,
				objectBoundaryHeldDuringClassification, observedTargetState,
				targetOutcome, targetReason, contractOutcome, contractReason);
		}

		public boolean isRegionAvailable() { return regionAvailable; }
		public int getSlotObjectCount() { return slotObjectCount; }
		public int getExactRestorationSceneryCount() {
			return exactRestorationSceneryCount;
		}
		public int getExactAuthoredIdentityCount() {
			return exactAuthoredIdentityCount;
		}
		public boolean isObjectBoundaryHeldDuringClassification() {
			return objectBoundaryHeldDuringClassification;
		}
		public ObservedTargetState getObservedTargetState() {
			return observedTargetState;
		}
		public TargetOutcome getTargetOutcome() { return targetOutcome; }
		public TargetReason getTargetReason() { return targetReason; }
		public ContractOutcome getContractOutcome() { return contractOutcome; }
		public ContractReason getContractReason() { return contractReason; }
		public boolean isRuntimeRevalidationPerformed() {
			return objectBoundaryHeldDuringClassification;
		}
	}

	public enum OuterFenceReason {
		SCHEDULER_INSTANCE_MISMATCH,
		EVENT_NOT_REGISTERED,
		DUPLICATE_REGISTRATION_SEQUENCE,
		REGISTRATION_SEQUENCE_MISMATCH,
		RESTORATION_STATE_UNAVAILABLE,
		EVENT_NOT_RUNNING,
		EVENT_ALREADY_EXECUTED,
		RESTORATION_PAYLOAD_INCOMPLETE,
		AUTHORED_IDENTITY_MISSING,
		OWNER_BOUND_STATE_REFUSED,
		RUNTIME_ATTRIBUTE_STATE_INCOMPLETE,
		AUTHORED_CONSTRUCTION_KIND_MISMATCH,
		SPATIAL_AFFINITY_MISMATCH,
		PROPOSAL_GENERATION_MISMATCH,
		EVENT_LIFECYCLE_CHANGED_DURING_OPERATION,
		OPERATION_COMPLETED
	}

	public enum ObservedTargetState {
		UNAVAILABLE,
		EMPTY,
		EXACT_RESTORATION_SCENERY_PRESENT,
		EXACT_AUTHORED_TRANSIENT_PRESENT,
		MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
		AMBIGUOUS_OCCUPANCY
	}

	public enum TargetOutcome {
		REFUSED,
		NO_OP_SUCCESS,
		MUTATION_PRECONDITION_SATISFIED
	}

	public enum TargetReason {
		REQUIREMENT_UNAVAILABLE,
		TARGET_BINDING_INCOMPLETE,
		GENERATION_MISMATCH,
		TARGET_OBSERVATION_UNAVAILABLE,
		DESIRED_PRESENT_STATE_ALREADY_SATISFIED,
		DESIRED_ABSENT_STATE_ALREADY_SATISFIED,
		SPAWN_DESTINATION_EMPTY,
		EXACT_AUTHORED_TRANSIENT_PRESENT,
		EXACT_REMOVAL_TARGET_PRESENT,
		REMOVAL_TARGET_CHANGED_TO_AUTHORED_TRANSIENT,
		MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
		AMBIGUOUS_OCCUPANCY
	}

	public enum ContractOutcome {
		REFUSED,
		NO_OP_CONTRACT_SATISFIED,
		MUTATION_PRECONDITION_CONTRACT_SATISFIED
	}

	public enum ContractReason {
		EVENT_EXECUTION_BOUNDARY_MISSING,
		SCHEDULER_STORE_BOUNDARY_HELD,
		REGISTRATION_NOT_VALIDATED_BEFORE_REGION_BOUNDARY,
		SCHEDULER_INSTANCE_MISMATCH,
		REGISTRATION_SEQUENCE_MISMATCH,
		PROPOSAL_GENERATION_MISMATCH,
		REGION_OBJECT_BOUNDARY_MISSING,
		TARGET_NOT_OBSERVED_INSIDE_REGION_BOUNDARY,
		TARGET_DECISION_REFUSED,
		DESIRED_STATE_ALREADY_SATISFIED,
		MUTATION_PRECONDITION_REVALIDATED
	}
}
