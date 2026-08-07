package com.openrsc.server.model.combat;

import com.openrsc.server.model.world.coordinate.WorldLocation;

/** Immutable result of entering the current projectile impact boundary. */
public final class ProjectileImpactDecision {
	public enum Outcome {
		AUTHORIZED,
		INVALIDATED,
		DUPLICATE
	}

	public enum Reason {
		CURRENT_POLICY_ACCEPTED,
		EXPLICIT_CANCELLATION,
		OUTSIDE_CURRENT_SPATIAL_GATE,
		TARGET_TERMINAL_OR_UNREGISTERED,
		TARGET_IDENTITY_SESSION_OR_LIFETIME_CHANGED,
		SOURCE_TERMINAL_OR_UNREGISTERED,
		SOURCE_IDENTITY_SESSION_OR_LIFETIME_CHANGED,
		LAUNCH_DOMAIN_DEPARTURE,
		OUTSIDE_LAUNCH_ORIGIN_RANGE,
		IMPACT_PATH_BLOCKED,
		DUPLICATE_CALLBACK
	}

	private final ProjectileLaunchSnapshot launchSnapshot;
	private final Outcome outcome;
	private final Reason reason;
	private final WorldLocation sourceImpactLocation;
	private final WorldLocation targetImpactLocation;

	ProjectileImpactDecision(final ProjectileLaunchSnapshot launchSnapshot,
			final Outcome outcome, final Reason reason,
			final WorldLocation sourceImpactLocation,
			final WorldLocation targetImpactLocation) {
		if (launchSnapshot == null || outcome == null || reason == null) {
			throw new IllegalArgumentException(
				"impact decision identity and outcome cannot be null");
		}
		this.launchSnapshot = launchSnapshot;
		this.outcome = outcome;
		this.reason = reason;
		this.sourceImpactLocation = sourceImpactLocation;
		this.targetImpactLocation = targetImpactLocation;
	}

	public ProjectileLaunchSnapshot getLaunchSnapshot() {
		return launchSnapshot;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public Reason getReason() {
		return reason;
	}

	public WorldLocation getSourceImpactLocation() {
		return sourceImpactLocation;
	}

	public WorldLocation getTargetImpactLocation() {
		return targetImpactLocation;
	}

	public boolean isAuthorized() {
		return outcome == Outcome.AUTHORIZED;
	}
}
