package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.handler.GameTickEventRestorationLiveReconstructionCoordinator.LiveCapturedRecovery;
import com.openrsc.server.event.rsc.handler.GameTickEventRestorationLiveReconstructionCoordinator.LiveLifecycleExecution;
import com.openrsc.server.event.rsc.handler.GameTickEventRestorationLivePreparationCoordinator.RecoveryPreflight;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.region.RegionManager;

import java.util.Objects;

/**
 * Private-diagnostic value for an unchanged-world reconstruction verification.
 * It never permits Region mutation or overdue event consumption.
 */
public final class GameTickEventRestorationNoOpDiagnostic {
	private final Reason reason;
	private final String preparationReason;
	private final String lifecycleReason;
	private final long proposalGeneration;
	private final int inventoryEventCount;
	private final int recoveryCandidateCount;
	private final int proposalRelatedEventCount;
	private final int recoveryCompleteEventCount;
	private final int recoveryIncompleteEventCount;
	private final int incompleteOwnerPositionHintEventCount;
	private final int incompleteExactSpatialEventCount;
	private final Long firstIncompleteRegistrationSequence;
	private final String firstIncompleteOwnerKind;
	private final String firstIncompleteAttributionKind;
	private final String firstIncompleteRecoveryRequirement;
	private final boolean preflightComplete;
	private final int futureSnapshotCount;
	private final int runtimeVerificationCount;
	private final int mutationOperationCount;
	private final int terminalEventConsumptionCount;
	private final boolean reconstructionInvoked;
	private final boolean recoveryInvoked;
	private final boolean contractualReadiness;
	private final boolean freshInventoryRetryRequired;

	private GameTickEventRestorationNoOpDiagnostic(
		final Reason reason,
		final String preparationReason,
		final String lifecycleReason,
		final long proposalGeneration,
		final int inventoryEventCount,
		final int recoveryCandidateCount,
		final RecoveryPreflight preflight,
		final int futureSnapshotCount,
		final int runtimeVerificationCount,
		final int mutationOperationCount,
		final int terminalEventConsumptionCount,
		final boolean reconstructionInvoked,
		final boolean recoveryInvoked,
		final boolean contractualReadiness,
		final boolean freshInventoryRetryRequired) {
		this.reason = Objects.requireNonNull(reason, "reason");
		this.preparationReason = Objects.requireNonNull(
			preparationReason, "preparationReason");
		this.lifecycleReason = lifecycleReason;
		this.proposalGeneration = proposalGeneration;
		this.inventoryEventCount = inventoryEventCount;
		this.recoveryCandidateCount = recoveryCandidateCount;
		RecoveryPreflight checkedPreflight = Objects.requireNonNull(
			preflight, "preflight");
		this.proposalRelatedEventCount =
			checkedPreflight.getProposalRelatedEventCount();
		this.recoveryCompleteEventCount =
			checkedPreflight.getRecoveryCompleteEventCount();
		this.recoveryIncompleteEventCount =
			checkedPreflight.getRecoveryIncompleteEventCount();
		this.incompleteOwnerPositionHintEventCount =
			checkedPreflight.getIncompleteOwnerPositionHintEventCount();
		this.incompleteExactSpatialEventCount =
			checkedPreflight.getIncompleteExactSpatialEventCount();
		this.firstIncompleteRegistrationSequence =
			checkedPreflight.getFirstIncompleteRegistrationSequence();
		this.firstIncompleteOwnerKind =
			checkedPreflight.getFirstIncompleteOwnerKind() == null ? null
				: checkedPreflight.getFirstIncompleteOwnerKind().name();
		this.firstIncompleteAttributionKind =
			checkedPreflight.getFirstIncompleteAttributionKind() == null ? null
				: checkedPreflight.getFirstIncompleteAttributionKind().name();
		this.firstIncompleteRecoveryRequirement =
			checkedPreflight.getFirstIncompleteRequirement() == null ? null
				: checkedPreflight.getFirstIncompleteRequirement().name();
		this.preflightComplete = checkedPreflight.isComplete();
		this.futureSnapshotCount = futureSnapshotCount;
		this.runtimeVerificationCount = runtimeVerificationCount;
		this.mutationOperationCount = mutationOperationCount;
		this.terminalEventConsumptionCount = terminalEventConsumptionCount;
		this.reconstructionInvoked = reconstructionInvoked;
		this.recoveryInvoked = recoveryInvoked;
		this.contractualReadiness = contractualReadiness;
		this.freshInventoryRetryRequired = freshInventoryRetryRequired;
		if (recoveryCandidateCount < 0 || futureSnapshotCount < 0
			|| runtimeVerificationCount < 0 || mutationOperationCount != 0
			|| terminalEventConsumptionCount != 0
			|| proposalRelatedEventCount < 0
			|| recoveryIncompleteEventCount < 0
			|| recoveryCandidateCount != recoveryCompleteEventCount
			|| proposalRelatedEventCount
				!= recoveryCompleteEventCount + recoveryIncompleteEventCount
			|| preflightComplete != (recoveryIncompleteEventCount == 0)
			|| preflightComplete
				!= (firstIncompleteRegistrationSequence == null)
			|| preflightComplete != (firstIncompleteOwnerKind == null)
			|| preflightComplete != (firstIncompleteAttributionKind == null)
			|| preflightComplete
				!= (firstIncompleteRecoveryRequirement == null)
			|| firstIncompleteRegistrationSequence != null
				&& firstIncompleteRegistrationSequence.longValue() <= 0L
			|| recoveryInvoked && !reconstructionInvoked
			|| contractualReadiness && !recoveryInvoked
			|| (reason == Reason.NO_OP_VERIFICATION_READY)
				!= contractualReadiness) {
			throw new IllegalArgumentException(
				"No-op recovery diagnostic is inconsistent");
		}
	}

	static GameTickEventRestorationNoOpDiagnostic capture(
		final GameTickEventStore store,
		final RegionManager regionManager,
		final LayeredPackedRegionEventOwnershipInventory inventory,
		final int maximumCandidates) {
		LayeredPackedRegionEventOwnershipInventory checked =
			Objects.requireNonNull(inventory, "inventory");
		RecoveryPreflight preflight =
			GameTickEventRestorationLivePreparationCoordinator
				.assessRecovery(checked);
		GameTickEventRestorationLiveReconstructionCoordinator coordinator =
			new GameTickEventRestorationLiveReconstructionCoordinator(
				Objects.requireNonNull(store, "store"),
				Objects.requireNonNull(regionManager, "regionManager"));
		LiveCapturedRecovery captured = coordinator.captureBeforeReconstruction(
			checked, maximumCandidates);
		if (!captured.isCaptured()) {
			return result(
				Reason.LIVE_CAPTURE_REFUSED,
				captured.getPreparationReason().name(), null,
				checked.getProposalGeneration(), checked.getEventCount(),
				captured.getRecoveryCandidateCount(), preflight, 0,
				0, false, false, false, false);
		}
		if (captured.getRecoveryCandidateCount() == 0) {
			return result(
				Reason.NO_RECOVERY_CANDIDATES,
				captured.getPreparationReason().name(), null,
				captured.getProposalGeneration(),
				captured.getInventoryEventCount(), 0, preflight, 0,
				0, false, false, false, false);
		}
		if (captured.getFutureSnapshotCount()
			!= captured.getRecoveryCandidateCount()) {
			return result(
				Reason.NON_FUTURE_CANDIDATE_REFUSED,
				captured.getPreparationReason().name(), null,
				captured.getProposalGeneration(),
				captured.getInventoryEventCount(),
				captured.getRecoveryCandidateCount(),
				preflight, captured.getFutureSnapshotCount(), 0,
				false, false, false, false);
		}

		LiveLifecycleExecution execution =
			coordinator.verifyAfterNoOpReconstruction(
				captured, boundary ->
					GameTickEventRestorationReconstructionLifecycleCoordinator
						.ReconstructionExecution.completed(boundary));
		if (execution.isMutationAllowed()
			|| execution.getMutationOperationCount() != 0
			|| execution.getTerminalEventConsumptionCount() != 0) {
			throw new IllegalStateException(
				"No-op diagnostic escaped its verification-only policy");
		}
		return result(
			execution.isContractuallyReadyForFirstVisibility()
				? Reason.NO_OP_VERIFICATION_READY
				: Reason.VERIFICATION_REFUSED,
			captured.getPreparationReason().name(),
			execution.getLifecycleReason() == null
				? null : execution.getLifecycleReason().name(),
			captured.getProposalGeneration(),
			captured.getInventoryEventCount(),
			captured.getRecoveryCandidateCount(),
			preflight, captured.getFutureSnapshotCount(),
			execution.getRuntimeOperationCount(),
			execution.isReconstructionInvoked(),
			execution.isRecoveryInvoked(),
			execution.isContractuallyReadyForFirstVisibility(),
			execution.requiresFreshInventoryRetry());
	}

	private static GameTickEventRestorationNoOpDiagnostic result(
		final Reason reason,
		final String preparationReason,
		final String lifecycleReason,
		final long proposalGeneration,
		final int inventoryEventCount,
		final int recoveryCandidateCount,
		final RecoveryPreflight preflight,
		final int futureSnapshotCount,
		final int runtimeVerificationCount,
		final boolean reconstructionInvoked,
		final boolean recoveryInvoked,
		final boolean contractualReadiness,
		final boolean freshInventoryRetryRequired) {
		return new GameTickEventRestorationNoOpDiagnostic(
			reason, preparationReason, lifecycleReason, proposalGeneration,
			inventoryEventCount, recoveryCandidateCount, preflight,
			futureSnapshotCount,
			runtimeVerificationCount, 0, 0, reconstructionInvoked,
			recoveryInvoked, contractualReadiness,
			freshInventoryRetryRequired);
	}

	public enum Reason {
		LIVE_CAPTURE_REFUSED,
		NO_RECOVERY_CANDIDATES,
		NON_FUTURE_CANDIDATE_REFUSED,
		VERIFICATION_REFUSED,
		NO_OP_VERIFICATION_READY
	}

	public Reason getReason() { return reason; }
	public String getPreparationReason() { return preparationReason; }
	public String getLifecycleReason() { return lifecycleReason; }
	public long getProposalGeneration() { return proposalGeneration; }
	public int getInventoryEventCount() { return inventoryEventCount; }
	public int getRecoveryCandidateCount() { return recoveryCandidateCount; }
	public int getProposalRelatedEventCount() {
		return proposalRelatedEventCount;
	}
	public int getRecoveryCompleteEventCount() {
		return recoveryCompleteEventCount;
	}
	public int getRecoveryIncompleteEventCount() {
		return recoveryIncompleteEventCount;
	}
	public int getIncompleteOwnerPositionHintEventCount() {
		return incompleteOwnerPositionHintEventCount;
	}
	public int getIncompleteExactSpatialEventCount() {
		return incompleteExactSpatialEventCount;
	}
	public Long getFirstIncompleteRegistrationSequence() {
		return firstIncompleteRegistrationSequence;
	}
	public String getFirstIncompleteOwnerKind() {
		return firstIncompleteOwnerKind;
	}
	public String getFirstIncompleteAttributionKind() {
		return firstIncompleteAttributionKind;
	}
	public String getFirstIncompleteRecoveryRequirement() {
		return firstIncompleteRecoveryRequirement;
	}
	public boolean isPreflightComplete() { return preflightComplete; }
	public int getFutureSnapshotCount() { return futureSnapshotCount; }
	public int getRuntimeVerificationCount() {
		return runtimeVerificationCount;
	}
	public int getMutationOperationCount() { return mutationOperationCount; }
	public int getTerminalEventConsumptionCount() {
		return terminalEventConsumptionCount;
	}
	public boolean isReconstructionInvoked() { return reconstructionInvoked; }
	public boolean isRecoveryInvoked() { return recoveryInvoked; }
	public boolean isContractuallyReadyForFirstVisibility() {
		return contractualReadiness;
	}
	public boolean isFreshInventoryRetryRequired() {
		return freshInventoryRetryRequired;
	}
	public boolean isRegionMutationAllowed() { return false; }
	public boolean isOverdueConsumptionAllowed() { return false; }
	public boolean isRegionLoadingPerformed() { return false; }
	public boolean isRetryPerformed() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
}
