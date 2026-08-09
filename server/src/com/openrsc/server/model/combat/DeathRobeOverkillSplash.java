package com.openrsc.server.model.combat;

import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

/** Shared terminal Death Robe splash selection; event owners settle child damage. */
public final class DeathRobeOverkillSplash {
	private static final int RADIUS = 2;

	private DeathRobeOverkillSplash() { }

	@FunctionalInterface
	public interface ChildDamage {
		void apply(Npc target, int splashDamage);
	}

	public static void apply(final Player source, final Npc primaryTarget,
			final int overkillDamage, final ChildDamage childDamage) {
		final double splashPercent = source.getDeathRobeOverkillSplashPercent();
		if (Summoning.isPlayerAreaEffectSuppressed(source)
			|| overkillDamage <= 0 || splashPercent <= 0.0D) return;
		final int splashDamage = calculateDamage(overkillDamage, splashPercent);
		for (Npc target : PlayerOwnedNpcRadiusSelection.aroundPrimary(
			source, primaryTarget, RADIUS)) {
			childDamage.apply(target, splashDamage);
		}
	}

	/** Preserves the historical floor-with-minimum-one terminal payload. */
	public static int calculateDamage(final int overkillDamage,
			final double splashPercent) {
		return Math.max(1, (int) Math.floor(overkillDamage * splashPercent));
	}
}
