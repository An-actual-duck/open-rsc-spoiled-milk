package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.runtime.GameRandom;

/** Shared execution policy for the current Baby Dragon smoke proc. */
public final class BabyDragonSmokeProc {
	private BabyDragonSmokeProc() {
	}

	/**
	 * Attempts the equipment-gated proc and applies its presentation and debuff.
	 *
	 * <p>The event owner must establish the current surviving-target phase: a
	 * player source and a living target. Settled zero damage remains eligible.
	 * This method consumes no random value without a positive equipment effect
	 * and exactly one value otherwise.</p>
	 *
	 * @return whether smoke was applied
	 */
	public static boolean tryApply(final Player source, final Mob target,
			final GameRandom random) {
		final int smokePercent =
			source.getBabyDragonSmokeAccuracyDebuffPercent();
		if (smokePercent <= 0) {
			return false;
		}
		if (random.nextDouble() >= source.getBabyDragonSmokeProcChance()) {
			return false;
		}
		target.getUpdateFlags().setProjectile(
			new Projectile(source, target, Projectile.BLOW_SMOKE));
		target.applySmokeAccuracyDebuff(smokePercent);
		return true;
	}
}
