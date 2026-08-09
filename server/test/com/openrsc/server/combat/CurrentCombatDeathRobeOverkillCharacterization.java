package com.openrsc.server.combat;

import com.openrsc.server.model.combat.DeathRobeOverkillSplash;

/** Executable terminal-payload arithmetic evidence for Death Robe splash. */
final class CurrentCombatDeathRobeOverkillCharacterization {
	private CurrentCombatDeathRobeOverkillCharacterization() { }

	static void terminalPayloadPolicies(final CurrentCombatHarness harness) {
		assertEquals(1, DeathRobeOverkillSplash.calculateDamage(1, 0.10D),
			"positive fractional payload keeps the historical minimum");
		assertEquals(2, DeathRobeOverkillSplash.calculateDamage(10, 0.25D),
			"terminal payload floors fractional damage");
		assertEquals(5, DeathRobeOverkillSplash.calculateDamage(20, 0.25D),
			"exact terminal payload remains exact");
	}

	private static void assertEquals(final int expected, final int actual,
			final String label) {
		if (expected != actual) throw new AssertionError(label);
	}
}
