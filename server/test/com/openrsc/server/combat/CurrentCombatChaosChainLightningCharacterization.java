package com.openrsc.server.combat;

import com.openrsc.server.model.combat.ChaosChainLightningProc;
import com.openrsc.server.model.entity.update.Projectile;

/** Executable payload and visual-cycle evidence for shared Chain Lightning. */
final class CurrentCombatChaosChainLightningCharacterization {
	private CurrentCombatChaosChainLightningCharacterization() { }

	static void chainProcPolicies(final CurrentCombatHarness harness) {
		assertEquals(1, ChaosChainLightningProc.initialDamage(1),
			"one damage keeps the historical minimum child payload");
		assertEquals(3, ChaosChainLightningProc.initialDamage(5),
			"initial child payload rounds upward");
		assertEquals(2, ChaosChainLightningProc.nextDamage(3),
			"each hop halves with upward rounding");
		assertEquals(Projectile.CHAIN_LIGHTNING_A,
			ChaosChainLightningProc.projectileForHop(0), "first visual");
		assertEquals(Projectile.CHAIN_LIGHTNING_B,
			ChaosChainLightningProc.projectileForHop(1), "second visual");
		assertEquals(Projectile.CHAIN_LIGHTNING_C,
			ChaosChainLightningProc.projectileForHop(2), "third visual");
		assertEquals(Projectile.CHAIN_LIGHTNING_A,
			ChaosChainLightningProc.projectileForHop(3), "visual cycle repeats");
	}

	private static void assertEquals(final int expected, final int actual,
			final String label) {
		if (expected != actual) throw new AssertionError(label);
	}
}
