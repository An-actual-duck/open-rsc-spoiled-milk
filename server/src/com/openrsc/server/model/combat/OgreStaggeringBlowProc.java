package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;

/** Shared execution policy for the current Ogre Staggering Blow proc. */
public final class OgreStaggeringBlowProc {
	private OgreStaggeringBlowProc() {
	}

	/**
	 * Attempts the equipment-gated proc and applies its one-attack debuff.
	 *
	 * <p>The event owner must establish the current surviving-target phase: a
	 * player source and a living target. Settled zero damage remains eligible.
	 * This method consumes no random value without the complete Ogre set and
	 * exactly one value otherwise.</p>
	 *
	 * @return whether the stagger was applied
	 */
	public static boolean tryApply(final Player source, final Mob target,
			final GameRandom random) {
		if (!source.hasFullOgreSet()) {
			return false;
		}
		if (random.nextDouble()
				>= source.getOgreStaggeringBlowProcChance()) {
			return false;
		}
		target.applyOgreStaggerDebuff();
		return true;
	}
}
