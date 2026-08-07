package com.openrsc.server.model.combat;

import com.openrsc.server.model.world.coordinate.WorldLocation;

/**
 * Per-event exactly-once ledger for a delayed projectile callback.
 *
 * <p>This is intentionally not an economy settlement ledger. It has bounded
 * event lifetime and records only callback ownership and terminal state.</p>
 */
public final class ProjectileImpactLedger {
	public enum State {
		LAUNCHED,
		VALIDATING,
		SETTLING,
		SETTLED,
		INVALIDATED,
		FAILED
	}

	private final ProjectileLaunchSnapshot launchSnapshot;
	private State state = State.LAUNCHED;
	private ProjectileImpactDecision initialDecision;
	private int callbackCount;

	public ProjectileImpactLedger(
			final ProjectileLaunchSnapshot launchSnapshot) {
		if (launchSnapshot == null) {
			throw new IllegalArgumentException(
				"launchSnapshot cannot be null");
		}
		this.launchSnapshot = launchSnapshot;
	}

	/** Claims the only callback permitted to evaluate and settle this impact. */
	public synchronized boolean claimImpact() {
		callbackCount++;
		if (state != State.LAUNCHED) {
			return false;
		}
		state = State.VALIDATING;
		return true;
	}

	public synchronized ProjectileImpactDecision authorize(
			final WorldLocation sourceImpactLocation,
			final WorldLocation targetImpactLocation) {
		requireState(State.VALIDATING);
		initialDecision = new ProjectileImpactDecision(
			launchSnapshot, ProjectileImpactDecision.Outcome.AUTHORIZED,
			ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED,
			sourceImpactLocation, targetImpactLocation);
		state = State.SETTLING;
		return initialDecision;
	}

	public synchronized ProjectileImpactDecision invalidate(
			final ProjectileImpactDecision.Reason reason,
			final WorldLocation sourceImpactLocation,
			final WorldLocation targetImpactLocation) {
		requireState(State.VALIDATING);
		if (reason != ProjectileImpactDecision.Reason.EXPLICIT_CANCELLATION
				&& reason != ProjectileImpactDecision.Reason
					.OUTSIDE_CURRENT_SPATIAL_GATE) {
			throw new IllegalArgumentException(
				"reason is not a current invalidation: " + reason);
		}
		initialDecision = new ProjectileImpactDecision(
			launchSnapshot, ProjectileImpactDecision.Outcome.INVALIDATED,
			reason, sourceImpactLocation, targetImpactLocation);
		state = State.INVALIDATED;
		return initialDecision;
	}

	public synchronized ProjectileImpactDecision duplicate() {
		if (state == State.LAUNCHED) {
			throw new IllegalStateException(
				"duplicate decision requires a claimed callback");
		}
		return new ProjectileImpactDecision(
			launchSnapshot, ProjectileImpactDecision.Outcome.DUPLICATE,
			ProjectileImpactDecision.Reason.DUPLICATE_CALLBACK,
			null, null);
	}

	public synchronized void complete(
			final ProjectileImpactDecision decision) {
		requireInitialAuthorizedDecision(decision);
		requireState(State.SETTLING);
		state = State.SETTLED;
	}

	public synchronized void fail() {
		if (state != State.VALIDATING && state != State.SETTLING) {
			throw new IllegalStateException(
				"impact cannot fail from state " + state);
		}
		state = State.FAILED;
	}

	public synchronized State getState() {
		return state;
	}

	public synchronized ProjectileImpactDecision getInitialDecision() {
		return initialDecision;
	}

	public synchronized int getCallbackCount() {
		return callbackCount;
	}

	private void requireInitialAuthorizedDecision(
			final ProjectileImpactDecision decision) {
		if (decision == null || decision != initialDecision
				|| !decision.isAuthorized()) {
			throw new IllegalArgumentException(
				"decision does not own this impact settlement");
		}
	}

	private void requireState(final State expected) {
		if (state != expected) {
			throw new IllegalStateException(
				"expected impact state " + expected + ", got " + state);
		}
	}
}
