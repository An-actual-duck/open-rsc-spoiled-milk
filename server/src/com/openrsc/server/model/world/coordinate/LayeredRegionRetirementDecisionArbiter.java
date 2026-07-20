package com.openrsc.server.model.world.coordinate;

import java.util.Objects;

/**
 * Pure, dormant arbiter for one logical Region retirement candidate.
 *
 * <p>The earlier candidate and a fresh snapshot must come from the same
 * retirement projection. Eligibility is returned only when the Region remains
 * unreferenced, resident, supported, past cooldown, on the same release record,
 * and at the same residency-mirror version. Even an eligible decision is
 * immutable evidence, not an unload, unregister, release, or eviction order.</p>
 */
public final class LayeredRegionRetirementDecisionArbiter {
	/** Evaluates one candidate without retaining or consuming it. */
	public Decision evaluate(
		final LayeredRegionRetirementEligibilityLedger.Snapshot candidateSnapshot,
		final LayeredRegionRetirementEligibilityLedger.Snapshot currentSnapshot) {
		LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
			Objects.requireNonNull(candidateSnapshot, "candidateSnapshot");
		LayeredRegionRetirementEligibilityLedger.Snapshot current =
			Objects.requireNonNull(currentSnapshot, "currentSnapshot");
		if (!candidate.getLogicalRegionKey().equals(
			current.getLogicalRegionKey())) {
			throw new IllegalArgumentException(
				"Retirement candidate and current snapshot identify different Regions");
		}

		DecisionState state;
		if (!candidate.sharesProjectionWith(current)) {
			state = DecisionState.FOREIGN_PROJECTION;
		} else if (!candidate.isRetirementEligible()) {
			state = DecisionState.CANDIDATE_NOT_ELIGIBLE;
		} else {
			state = decideCurrentState(candidate, current);
		}
		return new Decision(candidate, current, state);
	}

	private static DecisionState decideCurrentState(
		final LayeredRegionRetirementEligibilityLedger.Snapshot candidate,
		final LayeredRegionRetirementEligibilityLedger.Snapshot current) {
		switch (current.getRetirementState()) {
		case PINNED:
			return DecisionState.PINNED;
		case COOLING_DOWN:
			return DecisionState.COOLING_DOWN;
		case NOT_RESIDENT:
			return DecisionState.NOT_RESIDENT;
		case UNSUPPORTED:
			return DecisionState.UNSUPPORTED;
		case UNTRACKED:
			return DecisionState.UNTRACKED;
		case RETIREMENT_ELIGIBLE:
			break;
		default:
			throw new IllegalStateException(
				"Unhandled retirement state " + current.getRetirementState());
		}
		if (!sameRelease(candidate, current)) {
			return DecisionState.RELEASE_CHANGED;
		}
		if (candidate.getResidencyMirrorVersion()
			!= current.getResidencyMirrorVersion()) {
			return DecisionState.RESIDENCY_CHANGED;
		}
		return DecisionState.ELIGIBLE;
	}

	private static boolean sameRelease(
		final LayeredRegionRetirementEligibilityLedger.Snapshot candidate,
		final LayeredRegionRetirementEligibilityLedger.Snapshot current) {
		return candidate.getReleasedAtOwnershipVersion().equals(
				current.getReleasedAtOwnershipVersion())
			&& candidate.getReleasedAtTick().equals(current.getReleasedAtTick())
			&& candidate.getEligibleAtTick().equals(current.getEligibleAtTick());
	}

	public enum DecisionState {
		ELIGIBLE,
		FOREIGN_PROJECTION,
		CANDIDATE_NOT_ELIGIBLE,
		PINNED,
		COOLING_DOWN,
		NOT_RESIDENT,
		UNSUPPORTED,
		UNTRACKED,
		RELEASE_CHANGED,
		RESIDENCY_CHANGED
	}

	/** Immutable result; it deliberately exposes no mutable Region handle. */
	public static final class Decision {
		private final LayeredRegionRetirementEligibilityLedger.Snapshot candidate;
		private final LayeredRegionRetirementEligibilityLedger.Snapshot current;
		private final DecisionState decisionState;

		private Decision(
			final LayeredRegionRetirementEligibilityLedger.Snapshot candidate,
			final LayeredRegionRetirementEligibilityLedger.Snapshot current,
			final DecisionState decisionState) {
			this.candidate = candidate;
			this.current = current;
			this.decisionState = Objects.requireNonNull(
				decisionState, "decisionState");
		}

		public WorldRegionKey getLogicalRegionKey() {
			return current.getLogicalRegionKey();
		}

		public long getCandidateOwnershipVersion() {
			return candidate.getOwnershipVersion();
		}

		public long getCurrentOwnershipVersion() {
			return current.getOwnershipVersion();
		}

		public long getCandidateResidencyMirrorVersion() {
			return candidate.getResidencyMirrorVersion();
		}

		public long getCurrentResidencyMirrorVersion() {
			return current.getResidencyMirrorVersion();
		}

		public long getObservedAtTick() {
			return current.getObservedAtTick();
		}

		public DecisionState getDecisionState() {
			return decisionState;
		}

		public boolean isEligible() {
			return decisionState == DecisionState.ELIGIBLE;
		}
	}
}
