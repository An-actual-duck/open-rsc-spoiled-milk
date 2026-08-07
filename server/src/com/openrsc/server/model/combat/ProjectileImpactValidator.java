package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;

/** Side-effect-free validation for one delayed projectile impact. */
public final class ProjectileImpactValidator {
	private ProjectileImpactValidator() {
	}

	public static ProjectileImpactDecision.Reason validate(
			final ProjectileLaunchSnapshot launchSnapshot,
			final Mob source, final Mob target) {
		if (launchSnapshot == null || source == null || target == null) {
			throw new IllegalArgumentException(
				"projectile impact participants and snapshot cannot be null");
		}
		final ProjectileImpactPolicy policy = launchSnapshot
			.getSpecification().getImpactPolicy();
		if (policy.getSourceLifetime()
				== ProjectileImpactPolicy.SourceLifetime.TERMINAL_CLEANUP) {
			return ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED;
		}

		ProjectileImpactDecision.Reason rejection = validateTarget(
			launchSnapshot, target, policy);
		if (rejection != ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED) {
			return rejection;
		}
		rejection = validateSource(launchSnapshot, source, policy);
		if (rejection != ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED) {
			return rejection;
		}
		if (policy.requiresLaunchDomain()
				&& (!sameDomain(launchSnapshot.getSourceLaunchLocation(),
					source.getWorldLocation())
				|| !sameDomain(launchSnapshot.getTargetLaunchLocation(),
					target.getWorldLocation()))) {
			return ProjectileImpactDecision.Reason.LAUNCH_DOMAIN_DEPARTURE;
		}
		final int maximumRange = policy.getMaximumLaunchOriginRange();
		if (maximumRange >= 0 && !withinCompatibilityRange(
				launchSnapshot.getSourceLaunchLocation(),
				target.getWorldLocation(), maximumRange)) {
			return ProjectileImpactDecision.Reason.OUTSIDE_LAUNCH_ORIGIN_RANGE;
		}
		if (!hasImpactPath(policy.getCollision(), source,
				launchSnapshot.getSourceLaunchLocation(),
				target.getWorldLocation())) {
			return ProjectileImpactDecision.Reason.IMPACT_PATH_BLOCKED;
		}
		return ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED;
	}

	private static ProjectileImpactDecision.Reason validateTarget(
			final ProjectileLaunchSnapshot launchSnapshot,
			final Mob target, final ProjectileImpactPolicy policy) {
		if (!policy.requiresExactLiveTarget()) {
			return ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED;
		}
		if (!isLiveAndRegistered(target)) {
			return ProjectileImpactDecision.Reason
				.TARGET_TERMINAL_OR_UNREGISTERED;
		}
		if (!launchSnapshot.getTargetSnapshot().matches(target)) {
			return ProjectileImpactDecision.Reason
				.TARGET_IDENTITY_SESSION_OR_LIFETIME_CHANGED;
		}
		return ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED;
	}

	private static ProjectileImpactDecision.Reason validateSource(
			final ProjectileLaunchSnapshot launchSnapshot,
			final Mob source, final ProjectileImpactPolicy policy) {
		if (policy.getSourceLifetime()
				== ProjectileImpactPolicy.SourceLifetime.REQUIRE_EXACT_LIVE) {
			if (!isLiveAndRegistered(source)) {
				return ProjectileImpactDecision.Reason
					.SOURCE_TERMINAL_OR_UNREGISTERED;
			}
			if (!launchSnapshot.getSourceSnapshot().matches(source)) {
				return ProjectileImpactDecision.Reason
					.SOURCE_IDENTITY_SESSION_OR_LIFETIME_CHANGED;
			}
		} else {
			final CombatParticipantSnapshot sourceSnapshot =
				launchSnapshot.getSourceSnapshot();
			if (!sourceSnapshot.matchesIdentityAndSession(source)) {
				return ProjectileImpactDecision.Reason
					.SOURCE_IDENTITY_SESSION_OR_LIFETIME_CHANGED;
			}
			if (!sourceSnapshot.matches(source)
					&& !isPermittedTerminalDamageSource(source)) {
				return isLiveAndRegistered(source)
					? ProjectileImpactDecision.Reason
						.SOURCE_IDENTITY_SESSION_OR_LIFETIME_CHANGED
					: ProjectileImpactDecision.Reason
						.SOURCE_TERMINAL_OR_UNREGISTERED;
			}
			if (sourceSnapshot.matches(source)
					&& !isLiveAndRegistered(source)
					&& !isPermittedTerminalDamageSource(source)) {
				return ProjectileImpactDecision.Reason
					.SOURCE_TERMINAL_OR_UNREGISTERED;
			}
		}

		if (policy.requiresExactLiveSourceOwner()) {
			final CombatParticipantSnapshot ownerSnapshot =
				launchSnapshot.getSourceOwnerSnapshot();
			if (source.relatedMob == null
					|| !isLiveAndRegistered(source.relatedMob)) {
				return ProjectileImpactDecision.Reason
					.SOURCE_TERMINAL_OR_UNREGISTERED;
			}
			if (ownerSnapshot == null
					|| !ownerSnapshot.matchesIdentityAndSession(
						source.relatedMob)) {
				return ProjectileImpactDecision.Reason
					.SOURCE_IDENTITY_SESSION_OR_LIFETIME_CHANGED;
			}
		}
		return ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED;
	}

	private static boolean isLiveAndRegistered(final Mob participant) {
		if (participant.getSkills().getLevel(Skill.HITS.id()) <= 0
				|| participant.isRemoved()) {
			return false;
		}
		if (participant.isPlayer()) {
			final Player player = (Player) participant;
			return player.isLoggedIn()
				&& player.getWorld().hasPlayer(player);
		}
		if (participant.isNpc()) {
			final Npc npc = (Npc) participant;
			return !npc.killed && !npc.isRespawning()
				&& npc.getWorld().hasNpc(npc);
		}
		return false;
	}

	private static boolean isPermittedTerminalDamageSource(
			final Mob source) {
		if (source.isPlayer()) {
			final Player player = (Player) source;
			return player.isLoggedIn() && player.getWorld().hasPlayer(player)
				&& player.getSkills().getLevel(Skill.HITS.id()) <= 0;
		}
		if (source.isNpc()) {
			final Npc npc = (Npc) source;
			return npc.getWorld().hasNpc(npc)
				&& (npc.killed || npc.isRespawning()
					|| npc.getSkills().getLevel(Skill.HITS.id()) <= 0);
		}
		return false;
	}

	private static boolean sameDomain(final WorldLocation launch,
			final WorldLocation current) {
		return launch.getWorldSpace().equals(current.getWorldSpace())
			&& launch.getCoordinate().getLevel()
				== current.getCoordinate().getLevel();
	}

	private static boolean withinCompatibilityRange(
			final WorldLocation sourceLaunch, final WorldLocation targetCurrent,
			final int maximumRange) {
		final WorldCoordinate source = sourceLaunch.getCoordinate();
		final WorldCoordinate target = targetCurrent.getCoordinate();
		return Math.abs((long) source.getX() - target.getX()) <= maximumRange
			&& Math.abs((long) source.getY() - target.getY()) <= maximumRange;
	}

	private static boolean hasImpactPath(
			final ProjectileImpactPolicy.Collision collision,
			final Mob source, final WorldLocation sourceLaunch,
			final WorldLocation targetCurrent) {
		switch (collision) {
			case GENERAL_PROJECTILE:
				return PathValidation.checkPath(source.getWorld(), sourceLaunch,
					targetCurrent, false);
			case HOSTILE_PROJECTILE:
				return PathValidation.checkHostileProjectilePath(
					source.getWorld(), sourceLaunch, targetCurrent);
			case NONE:
				return true;
			default:
				throw new IllegalStateException(
					"Unhandled projectile collision policy: " + collision);
		}
	}
}
