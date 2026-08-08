package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;

/** Shared payload policy for the current Black Dragon breath follow-up. */
public final class BlackDragonBreathFollowup {
	private static final String BLACK_MARKER = "black";
	private static final int MAX_PROC_DAMAGE = 10;

	private BlackDragonBreathFollowup() {
	}

	/** Event-owned adapter for the existing auxiliary true-damage policy. */
	@FunctionalInterface
	public interface AuxiliaryTrueDamage {
		void apply(int rolledDamage);
	}

	/**
	 * Applies the Black Dragon payload for an event-owned poison marker.
	 *
	 * <p>The event owner retains primary-hit eligibility, poison chance and
	 * application, marker creation/clearing, shared Black/KBD presentation, and
	 * the surviving-target phase. The callback retains event-specific
	 * mitigation, contribution, presentation, and death handling.</p>
	 *
	 * @return whether the Black Dragon payload activated, including a zero roll
	 */
	public static boolean tryApply(final Player source, final String marker,
			final GameRandom random,
			final AuxiliaryTrueDamage auxiliaryTrueDamage) {
		if (!source.hasFullBlackDragonSet() || !BLACK_MARKER.equals(marker)) {
			return false;
		}
		final int rolledDamage =
			random.nextIntInclusive(0, MAX_PROC_DAMAGE);
		if (rolledDamage > 0) {
			auxiliaryTrueDamage.apply(rolledDamage);
		}
		return true;
	}
}
