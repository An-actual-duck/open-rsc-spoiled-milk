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
	private final ProjectileLaunchSpecification specification;

	private ProjectileLaunchSnapshot(final UUID eventId,
			final long launchTick, final long expectedImpactTick,
			final CombatParticipantSnapshot sourceSnapshot,
			final CombatParticipantSnapshot targetSnapshot,
			final WorldLocation sourceLaunchLocation,
			final WorldLocation targetLaunchLocation,
			final ProjectileLaunchSpecification specification) {
		this.eventId = eventId;
		this.launchTick = launchTick;
		this.expectedImpactTick = expectedImpactTick;
		this.sourceSnapshot = sourceSnapshot;
		this.targetSnapshot = targetSnapshot;
		this.sourceLaunchLocation = sourceLaunchLocation;
		this.targetLaunchLocation = targetLaunchLocation;
		this.specification = specification;
	}

	public static ProjectileLaunchSnapshot capture(final UUID eventId,
			final long launchTick, final long expectedImpactTick,
			final Mob source, final Mob target,
			final ProjectileLaunchSpecification specification) {
		if (eventId == null) {
			throw new IllegalArgumentException("eventId cannot be null");
		}
		if (source == null || target == null) {
			throw new IllegalArgumentException(
				"projectile participants cannot be null");
		}
		if (specification == null) {
			throw new IllegalArgumentException(
				"launch specification cannot be null");
		}
		return new ProjectileLaunchSnapshot(
			eventId, launchTick, expectedImpactTick,
			CombatParticipantSnapshot.capture(source),
			CombatParticipantSnapshot.capture(target),
			source.getWorldLocation(), target.getWorldLocation(), specification);
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
		return specification.getKind();
	}

	public String getFamilyKey() {
		return specification.getFamilyKey();
	}

	public String getProducerKey() {
		return specification.getProducerKey();
	}

	public int getAttackType() {
		return specification.getAttackType();
	}

	public int getProjectileType() {
		return specification.getProjectileType();
	}

	public int getImpactEffectType() {
		return specification.getImpactEffectType();
	}

	public int getProposedDamage() {
		return specification.getProposedDamage();
	}

	public boolean isVisualRequested() {
		return specification.shouldShowProjectile();
	}

	public ProjectileLaunchSpecification getSpecification() {
		return specification;
	}
}
