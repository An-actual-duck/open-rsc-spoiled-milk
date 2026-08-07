package com.openrsc.server.combat;

import com.openrsc.server.model.combat.SecondaryEffectPolicy;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Executable A07.1 coverage for the descriptive secondary-effect catalog. */
final class CurrentCombatSecondaryEffectPolicyCharacterization {
	private static final String[] EXPECTED_STABLE_KEYS = {
		"reciprocal-melee-auxiliary-magic",
		"reciprocal-melee-auxiliary-true",
		"pvm-melee-auxiliary-magic",
		"pvm-melee-auxiliary-true",
		"projectile-auxiliary-magic",
		"projectile-auxiliary-true",
		"reciprocal-melee-frostbite-reflection",
		"pvm-melee-frostbite-reflection",
		"projectile-frostbite-reflection",
		"reciprocal-melee-cleric-thorns",
		"pvm-melee-cleric-thorns",
		"projectile-cleric-thorns",
		"reciprocal-melee-jewelry-recoil",
		"pvm-melee-jewelry-recoil",
		"projectile-jewelry-recoil",
		"divine-retribution",
		"reciprocal-melee-chain-lightning",
		"pvm-melee-chain-lightning",
		"projectile-chain-lightning",
		"projectile-splinter",
		"projectile-blood-robe-splash",
		"reciprocal-melee-death-robe-overkill",
		"pvm-melee-death-robe-overkill",
		"projectile-death-robe-overkill",
		"pvm-melee-scythe-cleave",
		"player-death-amulet-burst",
		"player-death-ring-charge-hit",
		"projectile-balrog-magic-splash",
		"elder-green-dragon-melee-sweep",
		"elder-green-dragon-ranged-fireshot",
		"elder-green-dragon-magic-secondary",
		"elder-green-dragon-burn-pulse",
		"elder-green-dragon-armor-true-damage",
		"elder-green-dragon-armor-burn",
		"summon-bonus-magic",
		"summon-bonus-melee",
		"delayed-god-spell-secondary",
		"delayed-iban-blast-secondary",
		"delayed-salarin-strike-secondary"
	};

	private CurrentCombatSecondaryEffectPolicyCharacterization() {
	}

	static void stablePolicyCatalog(final CurrentCombatHarness harness) {
		final Map<String, SecondaryEffectPolicy> policies =
			SecondaryEffectPolicy.byStableKey();
		final Set<String> expected = new LinkedHashSet<String>(
			Arrays.asList(EXPECTED_STABLE_KEYS));

		assertEquals(39, SecondaryEffectPolicy.currentPolicyCount(),
			"current secondary-effect policy count");
		assertTrue(SecondaryEffectPolicy.currentPolicyCount() > 32,
			"32 entries cannot be used as a total catalog capacity");
		assertEquals(expected, policies.keySet(),
			"stable secondary-effect key inventory");
		assertEquals(expected.size(), SecondaryEffectPolicy.values().length,
			"one enum policy per stable key");
		assertEquals(6, count(SecondaryEffectPolicy.Family.AUXILIARY),
			"auxiliary family count");
		assertEquals(10, count(SecondaryEffectPolicy.Family.REFLECTION),
			"reflection family count");
		assertEquals(11, count(SecondaryEffectPolicy.Family.PLAYER_CHILD),
			"player child family count");
		assertEquals(9, count(SecondaryEffectPolicy.Family.OWNED_CONTENT),
			"owned content family count");
		assertEquals(3, count(SecondaryEffectPolicy.Family.DELAYED_SPELL),
			"delayed spell family count");

		for (final String key : expected) {
			final SecondaryEffectPolicy policy =
				SecondaryEffectPolicy.forStableKey(key);
			assertNotNull(policy, "stable key lookup for " + key);
			assertEquals(key, policy.getStableKey(),
				"stable key round trip for " + key);
			assertNotNull(policy.getFamily(), "family for " + key);
		}
		assertEquals(null, SecondaryEffectPolicy.forStableKey(null),
			"null key lookup");
		assertEquals(null, SecondaryEffectPolicy.forStableKey("unknown"),
			"unknown key lookup");

		boolean immutable = false;
		try {
			policies.clear();
		} catch (final UnsupportedOperationException expectedFailure) {
			immutable = true;
		}
		assertTrue(immutable, "secondary-effect policy map is immutable");
	}

	private static int count(final SecondaryEffectPolicy.Family family) {
		int count = 0;
		for (final SecondaryEffectPolicy policy : SecondaryEffectPolicy.values()) {
			if (policy.getFamily() == family) {
				count++;
			}
		}
		return count;
	}

	private static void assertNotNull(final Object value, final String label) {
		if (value == null) {
			throw new AssertionError(label + " must not be null");
		}
	}

	private static void assertTrue(final boolean value, final String label) {
		if (!value) {
			throw new AssertionError(label);
		}
	}

	private static void assertEquals(final Object expected, final Object actual,
			final String label) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(label + " expected=" + expected
				+ " actual=" + actual);
		}
	}
}
