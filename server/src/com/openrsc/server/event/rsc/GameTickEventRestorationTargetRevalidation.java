package com.openrsc.server.event.rsc;

import java.util.Objects;

/**
 * Detached result of one real, read-only Region-boundary target observation.
 *
 * <p>The target counts and closed state are produced while the Region object
 * boundary is held. They are stale immediately after that boundary is
 * released. The value contains no runtime handle and cannot authorize or
 * perform restoration.</p>
 */
public final class GameTickEventRestorationTargetRevalidation {
	private final boolean regionAvailable;
	private final int slotObjectCount;
	private final int exactRestorationSceneryCount;
	private final int exactAuthoredIdentityCount;
	private final GameTickEventRestorationTargetDecision.ObservedTargetState
		observedTargetState;
	private final boolean objectBoundaryHeldDuringClassification;
	private final GameTickEventRestorationTargetDecision targetDecision;
	private final GameTickEventRestorationAtomicRevalidationContract contract;

	private GameTickEventRestorationTargetRevalidation(
		final boolean regionAvailable,
		final int slotObjectCount,
		final int exactRestorationSceneryCount,
		final int exactAuthoredIdentityCount,
		final GameTickEventRestorationTargetDecision.ObservedTargetState
			observedTargetState,
		final boolean objectBoundaryHeldDuringClassification,
		final GameTickEventRestorationTargetDecision targetDecision,
		final GameTickEventRestorationAtomicRevalidationContract contract) {
		if (slotObjectCount < 0
			|| exactRestorationSceneryCount < 0
			|| exactRestorationSceneryCount > slotObjectCount
			|| exactAuthoredIdentityCount < 0
			|| exactAuthoredIdentityCount > slotObjectCount
			|| (regionAvailable
				!= objectBoundaryHeldDuringClassification)) {
			throw new IllegalArgumentException(
				"Restoration target revalidation counts are invalid");
		}
		this.observedTargetState = Objects.requireNonNull(
			observedTargetState, "observedTargetState");
		this.targetDecision = Objects.requireNonNull(
			targetDecision, "targetDecision");
		this.contract = Objects.requireNonNull(contract, "contract");
		if (targetDecision.getObservedTargetState() != observedTargetState
			|| contract.getTargetOutcome() != targetDecision.getOutcome()
			|| contract.getTargetReason() != targetDecision.getReason()
			|| (!regionAvailable
				&& (slotObjectCount != 0
					|| exactRestorationSceneryCount != 0
					|| exactAuthoredIdentityCount != 0
					|| observedTargetState
						!= GameTickEventRestorationTargetDecision
							.ObservedTargetState.UNAVAILABLE))) {
			throw new IllegalArgumentException(
				"Restoration target revalidation evidence disagrees");
		}
		this.regionAvailable = regionAvailable;
		this.slotObjectCount = slotObjectCount;
		this.exactRestorationSceneryCount =
			exactRestorationSceneryCount;
		this.exactAuthoredIdentityCount = exactAuthoredIdentityCount;
		this.objectBoundaryHeldDuringClassification =
			objectBoundaryHeldDuringClassification;
	}

	public static GameTickEventRestorationTargetRevalidation observe(
		final boolean regionAvailable,
		final int slotObjectCount,
		final int exactRestorationSceneryCount,
		final int exactAuthoredIdentityCount,
		final GameTickEventRestorationTargetDecision.ObservedTargetState
			observedTargetState,
		final boolean objectBoundaryHeldDuringClassification,
		final GameTickEventRestorationTargetDecision targetDecision,
		final GameTickEventRestorationAtomicRevalidationContract contract) {
		return new GameTickEventRestorationTargetRevalidation(
			regionAvailable, slotObjectCount,
			exactRestorationSceneryCount, exactAuthoredIdentityCount,
			observedTargetState, objectBoundaryHeldDuringClassification,
			targetDecision, contract);
	}

	public boolean isRegionAvailable() { return regionAvailable; }
	public int getSlotObjectCount() { return slotObjectCount; }
	public int getExactRestorationSceneryCount() {
		return exactRestorationSceneryCount;
	}
	public int getExactAuthoredIdentityCount() {
		return exactAuthoredIdentityCount;
	}
	public GameTickEventRestorationTargetDecision.ObservedTargetState
		getObservedTargetState() {
		return observedTargetState;
	}
	public boolean isObjectBoundaryHeldDuringClassification() {
		return objectBoundaryHeldDuringClassification;
	}
	public GameTickEventRestorationTargetDecision getTargetDecision() {
		return targetDecision;
	}
	public GameTickEventRestorationAtomicRevalidationContract getContract() {
		return contract;
	}

	public boolean isRuntimeTargetLookupPerformed() { return true; }
	public boolean isRuntimeTargetClassificationPerformed() {
		return objectBoundaryHeldDuringClassification;
	}
	public boolean isRuntimeRevalidationPerformed() {
		return objectBoundaryHeldDuringClassification;
	}
	public boolean isStaleAfterBoundaryRelease() { return true; }
	public boolean isEntityHandleRetained() { return false; }
	public boolean isMutationAuthorized() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isExecutableRestoration() { return false; }
	public boolean isCommitToken() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }
}
