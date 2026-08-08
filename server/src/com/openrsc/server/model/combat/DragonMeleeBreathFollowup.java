package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.runtime.GameRandom;

/** Shared post-root Dragon melee-breath presentation and settlement gate. */
public final class DragonMeleeBreathFollowup {
	private DragonMeleeBreathFollowup() { }

	@FunctionalInterface public interface BreathDamageRoll { int roll(); }
	@FunctionalInterface public interface AuxiliaryTrueDamage { void apply(int damage); }

	public static boolean tryApply(final Mob source, final Mob target,
			final GameRandom random, final BreathDamageRoll breathDamageRoll,
			final AuxiliaryTrueDamage auxiliaryTrueDamage) {
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) return false;
		final int damage = breathDamageRoll.roll();
		if (damage <= 0) return false;
		final int effect = random.nextIntInclusive(0, 1) == 0
			? CombatEffect.DRAGON_WEAPON_BREATH : CombatEffect.DRAGON_WEAPON_SLASH_2;
		target.getUpdateFlags().setCombatEffect(new CombatEffect(target, effect));
		auxiliaryTrueDamage.apply(damage);
		return true;
	}
}
