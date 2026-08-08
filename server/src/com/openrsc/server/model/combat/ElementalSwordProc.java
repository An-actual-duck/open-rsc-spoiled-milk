package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.Projectile;

/** Shared post-root presentation and callback ordering for Elemental Sword. */
public final class ElementalSwordProc {
	private ElementalSwordProc() { }
	@FunctionalInterface public interface IntSupplier { int get(); }
	@FunctionalInterface public interface BooleanSupplier { boolean get(); }
	@FunctionalInterface public interface EffectConsumer { void apply(int effect); }
	@FunctionalInterface public interface DamageConsumer { void apply(int damage); }

	public static boolean tryApply(final Mob source, final Mob target,
			final IntSupplier effectSupplier, final BooleanSupplier chanceRoll,
			final EffectConsumer debuffApplier, final IntSupplier damageRoll,
			final DamageConsumer auxiliaryTrueDamage) {
		if (target.getSkills().getLevel(Skill.HITS.id()) <= 0) return false;
		final int effect = effectSupplier.get();
		if (effect == CombatEffect.NONE || !chanceRoll.get()) return false;
		if (effect == CombatEffect.ICE_SWORD) {
			target.getUpdateFlags().setProjectile(new Projectile(source, target, Projectile.ICE_SWORD_STAB));
		} else target.getUpdateFlags().setCombatEffect(new CombatEffect(target, effect));
		debuffApplier.apply(effect);
		final int damage = damageRoll.get();
		if (damage > 0) auxiliaryTrueDamage.apply(damage);
		return true;
	}
}
