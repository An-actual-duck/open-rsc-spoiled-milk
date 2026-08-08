package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;

/** Shared payload and elemental-debuff policy for King Black Dragon breath. */
public final class KingBlackDragonBreathFollowup {
	private static final String KING_BLACK_MARKER = "king_black";
	private static final int MAX_PROC_DAMAGE = 10;
	private static final int WATER_MAX_HIT_DEBUFF_PERCENT = 10;
	private static final int EARTH_ATTACK_SPEED_DEBUFF_PERCENT = 6;
	private static final int FIRE_DEFENSE_DEBUFF_PERCENT = 6;

	private KingBlackDragonBreathFollowup() {
	}

	/** Event-owned adapter for the existing auxiliary true-damage policy. */
	@FunctionalInterface
	public interface AuxiliaryTrueDamage {
		void apply(int rolledDamage);
	}

	/**
	 * Applies the King Black Dragon payload for an event-owned poison marker.
	 *
	 * <p>The event owner retains primary-hit eligibility, poison chance and
	 * application, marker creation/clearing, shared Black/KBD presentation, and
	 * the surviving-target phase. The callback retains event-specific
	 * mitigation, contribution, presentation, and death handling. The elemental
	 * debuff intentionally follows the callback even when it kills the target,
	 * matching the prior owner-local order.</p>
	 *
	 * @return whether the KBD payload activated, including a zero roll
	 */
	public static boolean tryApply(final Player source, final Mob target,
			final String marker, final GameRandom random,
			final AuxiliaryTrueDamage auxiliaryTrueDamage) {
		if (!source.hasFullKingBlackDragonSet()
				|| !KING_BLACK_MARKER.equals(marker)) {
			return false;
		}
		final int rolledDamage = random.nextIntInclusive(0, MAX_PROC_DAMAGE);
		if (rolledDamage > 0) {
			auxiliaryTrueDamage.apply(rolledDamage);
		}
		switch (random.nextIntInclusive(0, 2)) {
			case 0:
				target.applyDragonWaterMaxHitDebuff(
					WATER_MAX_HIT_DEBUFF_PERCENT);
				break;
			case 1:
				target.applyDragonEarthAttackSpeedDebuff(
					EARTH_ATTACK_SPEED_DEBUFF_PERCENT);
				break;
			default:
				target.applyDragonFireDefenseDebuff(
					FIRE_DEFENSE_DEBUFF_PERCENT);
				break;
		}
		return true;
	}
}
