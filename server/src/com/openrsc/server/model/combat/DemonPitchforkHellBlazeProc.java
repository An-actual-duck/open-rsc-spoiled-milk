package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.update.CombatEffect;

/** Shared post-root Hell's Blaze sequencing for Demon Pitchfork. */
public final class DemonPitchforkHellBlazeProc {
	private DemonPitchforkHellBlazeProc() { }
	@FunctionalInterface public interface BooleanSupplier { boolean get(); }
	@FunctionalInterface public interface IntSupplier { int get(); }
	@FunctionalInterface public interface DamageConsumer { void apply(int damage); }

	public static boolean tryApply(final Mob target, final int primaryDamage,
			final BooleanSupplier chanceRoll, final IntSupplier payloadRoll,
			final DamageConsumer auxiliaryMagicDamage) {
		if (primaryDamage <= 0 || target.getSkills().getLevel(Skill.HITS.id()) <= 0
				|| !chanceRoll.get()) return false;
		target.getUpdateFlags().setCombatEffect(new CombatEffect(target, CombatEffect.HELLS_BLAZE));
		final int damage = payloadRoll.get();
		if (damage > 0) auxiliaryMagicDamage.apply(damage);
		return true;
	}
}
