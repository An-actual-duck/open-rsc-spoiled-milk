package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.world.coordinate.WorldLocation;

import java.util.UUID;

/**
 * Immutable identity and presentation facts for one current delayed projectile.
 *
 * <p>This A06 foundation records launch state only. It deliberately owns no
 * ammunition, rune, recovery, experience, impact-eligibility, or damage
 * authority.</p>
 */
public final class ProjectileLaunchSnapshot {
	public enum Kind {
		DAMAGING,
		SCRIPTED_EFFECT,
		BENIGN_EFFECT
	}

	private final UUID eventId;
	private final long launchTick;
	private final long expectedImpactTick;
	private final CombatParticipantSnapshot sourceSnapshot;
	private final CombatParticipantSnapshot targetSnapshot;
	private final WorldLocation sourceLaunchLocation;
	private final WorldLocation targetLaunchLocation;
	private final Kind kind;
	private final String familyKey;
	private final int attackType;
	private final int projectileType;
	private final int impactEffectType;
	private final int proposedDamage;
	private final boolean visualRequested;

	private ProjectileLaunchSnapshot(final UUID eventId,
			final long launchTick, final long expectedImpactTick,
			final CombatParticipantSnapshot sourceSnapshot,
			final CombatParticipantSnapshot targetSnapshot,
			final WorldLocation sourceLaunchLocation,
			final WorldLocation targetLaunchLocation, final Kind kind,
			final String familyKey, final int attackType,
			final int projectileType, final int impactEffectType,
			final int proposedDamage, final boolean visualRequested) {
		this.eventId = eventId;
		this.launchTick = launchTick;
		this.expectedImpactTick = expectedImpactTick;
		this.sourceSnapshot = sourceSnapshot;
		this.targetSnapshot = targetSnapshot;
		this.sourceLaunchLocation = sourceLaunchLocation;
		this.targetLaunchLocation = targetLaunchLocation;
		this.kind = kind;
		this.familyKey = familyKey;
		this.attackType = attackType;
		this.projectileType = projectileType;
		this.impactEffectType = impactEffectType;
		this.proposedDamage = proposedDamage;
		this.visualRequested = visualRequested;
	}

	public static ProjectileLaunchSnapshot capture(final UUID eventId,
			final long launchTick, final long expectedImpactTick,
			final Mob source, final Mob target, final Kind kind,
			final String familyKey, final int attackType,
			final int projectileType, final int impactEffectType,
			final int proposedDamage, final boolean visualRequested) {
		if (eventId == null) {
			throw new IllegalArgumentException("eventId cannot be null");
		}
		if (source == null || target == null) {
			throw new IllegalArgumentException(
				"projectile participants cannot be null");
		}
		if (kind == null) {
			throw new IllegalArgumentException("kind cannot be null");
		}
		if (familyKey == null || familyKey.trim().isEmpty()) {
			throw new IllegalArgumentException("familyKey cannot be blank");
		}
		return new ProjectileLaunchSnapshot(
			eventId, launchTick, expectedImpactTick,
			CombatParticipantSnapshot.capture(source),
			CombatParticipantSnapshot.capture(target),
			source.getWorldLocation(), target.getWorldLocation(), kind,
			familyKey, attackType, projectileType, impactEffectType,
			proposedDamage, visualRequested);
	}

	public UUID getEventId() {
		return eventId;
	}

	public long getLaunchTick() {
		return launchTick;
	}

	public long getExpectedImpactTick() {
		return expectedImpactTick;
	}

	public CombatParticipantSnapshot getSourceSnapshot() {
		return sourceSnapshot;
	}

	public CombatParticipantSnapshot getTargetSnapshot() {
		return targetSnapshot;
	}

	public WorldLocation getSourceLaunchLocation() {
		return sourceLaunchLocation;
	}

	public WorldLocation getTargetLaunchLocation() {
		return targetLaunchLocation;
	}

	public Kind getKind() {
		return kind;
	}

	public String getFamilyKey() {
		return familyKey;
	}

	public int getAttackType() {
		return attackType;
	}

	public int getProjectileType() {
		return projectileType;
	}

	public int getImpactEffectType() {
		return impactEffectType;
	}

	public int getProposedDamage() {
		return proposedDamage;
	}

	public boolean isVisualRequested() {
		return visualRequested;
	}
}
