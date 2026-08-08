package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;

/** Shared execution policy for the current Blue Dragon water proc. */
public final class BlueDragonWaterProc {
	private static final double PROC_CHANCE = 0.20D;
	private static final int MAX_PROC_DAMAGE = 10;
	private static final int MAX_HIT_DEBUFF_PERCENT = 10;

	private BlueDragonWaterProc() {
	}

	/** Event-owned adapter for the existing auxiliary true-damage policy. */
	@FunctionalInterface
	public interface AuxiliaryTrueDamage {
		void apply(int rolledDamage);
	}

	/**
	 * Attempts the equipment-gated proc, damage callback, and target debuff.
	 *
	 * <p>The event owner must establish the current surviving-target phase: a
	 * player source and a living target. Settled zero primary damage remains
	 * eligible. The callback retains event-specific mitigation, contribution,
	 * presentation, and death handling and runs before the debuff, including on
	 * a terminal auxiliary hit.</p>
	 *
	 * @return whether the water proc activated, including a zero damage roll
	 */
	public static boolean tryApply(final Player source, final Mob target,
			final GameRandom random,
			final AuxiliaryTrueDamage auxiliaryTrueDamage) {
		if (!source.hasFullBlueDragonSet()) {
			return false;
		}
		if (random.nextDouble() >= PROC_CHANCE) {
			return false;
		}
		final int rolledDamage =
			random.nextIntInclusive(0, MAX_PROC_DAMAGE);
		if (rolledDamage > 0) {
			auxiliaryTrueDamage.apply(rolledDamage);
		}
		target.applyDragonWaterMaxHitDebuff(MAX_HIT_DEBUFF_PERCENT);
		return true;
	}
}
