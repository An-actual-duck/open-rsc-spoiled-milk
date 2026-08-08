package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.util.rsc.CombatEffectUtil;

/** Shared NPC-only Hell's Inferno splash selection and presentation. */
public final class HellsInfernoNpcSplash {
	private HellsInfernoNpcSplash() { }
	@FunctionalInterface public interface AuxiliaryMagicDamage { void apply(Npc target, int damage); }
	public static void apply(final Player source, final Npc primaryTarget,
			final int primaryDamageDealt, final AuxiliaryMagicDamage auxiliaryMagicDamage) {
		final int splashDamage = CombatEffectUtil.hellsInfernoSplashDamage(primaryDamageDealt);
		if (splashDamage <= 0) return;
		for (Npc target : CombatEffectUtil.findPlayerOwnedNpcSplashTargets(
			source, primaryTarget, CombatEffectUtil.HELLS_INFERNO_SPLASH_RADIUS)) {
			target.getUpdateFlags().setCombatEffect(new CombatEffect(target, CombatEffect.HELLS_INFERNO));
			auxiliaryMagicDamage.apply(target, splashDamage);
		}
	}
}
