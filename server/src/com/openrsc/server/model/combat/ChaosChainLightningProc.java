package com.openrsc.server.model.combat;

import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.runtime.GameRandom;

/** Shared Chaos Necklace chain traversal and presentation; owners settle hits. */
public final class ChaosChainLightningProc {
	private ChaosChainLightningProc() { }

	@FunctionalInterface
	public interface ChildDamage {
		void apply(Mob target, int damage);
	}

	@FunctionalInterface
	public interface TargetSelector {
		Mob select(Mob anchor);
	}

	public static void tryApply(final Player source, final Mob primaryTarget,
			final int primaryDamage, final GameRandom random,
			final TargetSelector targetSelector, final ChildDamage childDamage) {
		if (primaryDamage <= 0 || !primaryTarget.isNpc()
			|| Summoning.isPlayerAreaEffectSuppressed(source)) return;
		final double chance = source.getCarriedItems().getEquipment()
			.getChaosNecklaceChainLightningChance();
		if (chance <= 0.0D) return;
		Mob anchor = primaryTarget;
		int damage = initialDamage(primaryDamage);
		for (int hop = 0; hop < ChainLightningTraversalPolicy.MAX_HOPS; hop++) {
			if (random.nextDouble() >= chance) break;
			final Mob target = targetSelector.select(anchor);
			if (target == null) break;
			target.getUpdateFlags().setProjectile(new Projectile(anchor, target,
				projectileForHop(hop)));
			childDamage.apply(target, damage);
			anchor = target;
			damage = nextDamage(damage);
		}
	}

	public static int initialDamage(final int primaryDamage) {
		return Math.max(1, (int) Math.ceil(primaryDamage / 2.0D));
	}

	public static int nextDamage(final int priorDamage) {
		return Math.max(1, (int) Math.ceil(priorDamage / 2.0D));
	}

	public static int projectileForHop(final int hop) {
		switch (hop % 3) {
			case 0: return Projectile.CHAIN_LIGHTNING_A;
			case 1: return Projectile.CHAIN_LIGHTNING_B;
			default: return Projectile.CHAIN_LIGHTNING_C;
		}
	}
}
