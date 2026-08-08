package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;

/** Shared eligibility policy for the primary-melee Bear Maul second hit. */
public final class BearMaulSecondHit {
	private BearMaulSecondHit() {
	}

	@FunctionalInterface
	public interface AuxiliaryTrueDamage {
		void apply(int damage);
	}

	/**
	 * Invokes the owner-local second-hit settlement only for an eligible Bear set.
	 *
	 * <p>The event owner retains the primary-hit ordering, Scythe relationship,
	 * mitigation, contribution, presentation, lifesteal, packets, and death
	 * adapter through the callback.</p>
	 */
	public static boolean tryApply(final Mob source, final Mob target,
			final int damage, final AuxiliaryTrueDamage auxiliaryTrueDamage) {
		if (!source.isPlayer() || !((Player) source).hasFullBearHideSet()
				|| damage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return false;
		}
		auxiliaryTrueDamage.apply(damage);
		return true;
	}
}
