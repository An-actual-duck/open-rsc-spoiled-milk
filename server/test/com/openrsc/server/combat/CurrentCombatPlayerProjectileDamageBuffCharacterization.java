package com.openrsc.server.combat;

import com.openrsc.server.model.combat.PlayerProjectileDamageBuff;

/** Executable precedence and rounding evidence for projectile magic buffs. */
final class CurrentCombatPlayerProjectileDamageBuffCharacterization {
	private CurrentCombatPlayerProjectileDamageBuffCharacterization() { }

	static void projectileBuffPolicies(final CurrentCombatHarness harness) {
		assertEquals(1.0D, select(0, 0, 0), "no debuff leaves projectile damage unchanged");
		assertEquals(1.10D, select(1, 0, 0), "Earth selects its multiplier");
		assertEquals(1.20D, select(0, 1, 0), "Water selects its multiplier");
		assertEquals(1.30D, select(0, 0, 1), "Fire selects its multiplier");
		assertEquals(1.10D, select(1, 1, 1), "Earth remains first when effects overlap");
	}

	private static double select(final int earth, final int water, final int fire) {
		return PlayerProjectileDamageBuff.selectMultiplier(earth, water, fire,
			1.10D, 1.20D, 1.30D);
	}

	private static void assertEquals(final double expected, final double actual,
			final String label) {
		if (Double.compare(expected, actual) != 0) throw new AssertionError(label);
	}
}
