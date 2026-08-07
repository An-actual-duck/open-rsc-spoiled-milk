package com.openrsc.server.combat;

import com.openrsc.server.model.combat.SecondaryEffectDescriptor;
import com.openrsc.server.model.combat.SecondaryEffectPolicy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Executable A07.2 coverage for the descriptive semantic-effect inventory. */
final class CurrentCombatSecondaryEffectDescriptorCharacterization {
	private static final String[] EXPECTED_STABLE_KEYS = {
		"semantic.compatibility-player-poison-script",
		"semantic.npc-poison-script",
		"semantic.body-robe-weapon-power-charge",
		"semantic.summon-damage-absorption",
		"semantic.frostbite-reflection",
		"semantic.cleric-ward-aegis-protection",
		"semantic.cleric-zeal-damage",
		"semantic.summon-owner-assist",
		"semantic.divine-grace",
		"semantic.giant-bat-lifesteal",
		"semantic.blood-amulet-lifesteal",
		"semantic.corrosive-aura",
		"semantic.divine-retribution",
		"semantic.blood-robe-splash",
		"semantic.cleric-rally-lifesteal",
		"semantic.cleric-thorns-reflection",
		"semantic.death-ring-charged-hit",
		"semantic.balrog-magic-splash",
		"semantic.summon-trait-on-hit",
		"semantic.poison-current-hit-reset",
		"semantic.weapon-poison",
		"semantic.style-armor-poison",
		"semantic.black-dragon-armor-poison",
		"semantic.king-black-dragon-armor-poison",
		"semantic.elemental-giant-might",
		"semantic.ogre-stagger",
		"semantic.baby-dragon-smoke",
		"semantic.infernal-fire",
		"semantic.hells-inferno-splash",
		"semantic.blue-dragon-water",
		"semantic.earth-dragon-slow",
		"semantic.red-dragon-fire",
		"semantic.black-dragon-breath-followup",
		"semantic.king-black-dragon-breath-followup",
		"semantic.elder-green-dragon-armor-breath",
		"semantic.elder-green-dragon-armor-burn-application",
		"semantic.projectile-startle",
		"semantic.projectile-acid",
		"semantic.projectile-frostbite",
		"semantic.projectile-wind",
		"semantic.projectile-water",
		"semantic.projectile-earth",
		"semantic.projectile-fire",
		"semantic.dragon-ranged-breath",
		"semantic.projectile-splinter",
		"semantic.elder-green-dragon-projectile-aoe",
		"semantic.elder-green-dragon-fireshot",
		"semantic.elder-green-dragon-burn-application",
		"semantic.bear-maul-second-hit",
		"semantic.dragon-melee-breath",
		"semantic.elemental-sword",
		"semantic.demon-pitchfork-hell-blaze",
		"semantic.kolodion-fire-claw",
		"semantic.chaos-chain-lightning",
		"semantic.scythe-cleave",
		"semantic.jewelry-recoil",
		"semantic.elder-green-dragon-melee-sweep",
		"semantic.death-robe-overkill",
		"semantic.death-ring-charge-acquisition",
		"semantic.death-amulet-burst",
		"semantic.soul-amulet-burst",
		"semantic.ring-of-life",
		"semantic.compatibility-tutorial-rat-safety",
		"semantic.god-spell-area-damage",
		"semantic.guthix-god-spell-poison",
		"semantic.zamorak-god-spell-withering",
		"semantic.saradomin-god-spell-lifesteal",
		"semantic.iban-blast-area-damage",
		"semantic.salarin-second-strike",
		"semantic.elder-green-dragon-armor-burn-pulse",
		"semantic.elder-green-dragon-burn-pulse"
	};

	private CurrentCombatSecondaryEffectDescriptorCharacterization() {
	}

	static void stableSemanticInventory(final CurrentCombatHarness harness) {
		final Map<String, SecondaryEffectDescriptor> descriptors =
			SecondaryEffectDescriptor.byStableKey();
		final Set<String> expected = new LinkedHashSet<String>(
			Arrays.asList(EXPECTED_STABLE_KEYS));

		assertEquals(71, SecondaryEffectDescriptor.values().length,
			"current semantic descriptor count");
		assertEquals(expected, descriptors.keySet(),
			"stable semantic descriptor inventory");
		assertEquals(expected.size(), descriptors.size(),
			"one descriptor per stable semantic key");
		assertTrue(descriptors.size() > 32,
			"registration capacity must not use the rejected total cap");
		assertEquals(0, SecondaryEffectDescriptor.APPROVED_PLANNED_EFFECTS,
			"active plans contain no approved unimplemented semantic effects");
		assertEquals(4, SecondaryEffectDescriptor.REVIEWED_HEADROOM_PER_PHASE,
			"reviewed per-phase planning headroom");

		for (final String key : expected) {
			final SecondaryEffectDescriptor descriptor =
				SecondaryEffectDescriptor.forStableKey(key);
			assertNotNull(descriptor, "stable semantic lookup for " + key);
			assertEquals(key, descriptor.getStableKey(),
				"stable semantic round trip for " + key);
			assertTrue(key.startsWith("semantic."),
				"semantic namespace for " + key);
			assertFalse(SecondaryEffectPolicy.byStableKey().containsKey(key),
				"semantic key must not reuse an A05 settlement identity");
			assertFalse(descriptor.getPhases().isEmpty(), "phase for " + key);
			assertFalse(descriptor.getStyles().isEmpty(), "styles for " + key);
			assertNotNull(descriptor.getParticipantGate(), "participant gate for " + key);
			assertNotNull(descriptor.getZeroDamageRule(), "zero rule for " + key);
			assertNotNull(descriptor.getRandomTiming(), "RNG timing for " + key);
			assertNotNull(descriptor.getStateOwner(), "state owner for " + key);
			assertNotNull(descriptor.getRecursionPolicy(), "recursion for " + key);
			assertNotNull(descriptor.getPresentationPolicy(), "presentation for " + key);
			assertFalse(descriptor.getExistingOwner().isEmpty(), "runtime owner for " + key);
			assertFalse(descriptor.getExecutableEvidence().isEmpty(), "evidence for " + key);
		}

		assertEquals(null, SecondaryEffectDescriptor.forStableKey(null),
			"null semantic key lookup");
		assertEquals(null, SecondaryEffectDescriptor.forStableKey("unknown"),
			"unknown semantic key lookup");
		assertImmutableMap(descriptors, "semantic descriptor map");
		assertImmutableSet(SecondaryEffectDescriptor.WEAPON_POISON.getPhases(),
			"descriptor phase set");
		assertImmutableSet(SecondaryEffectDescriptor.WEAPON_POISON.getStyles(),
			"descriptor style set");

		assertPhase(SecondaryEffectDescriptor.Phase.PRE_PRIMARY_DAMAGE, 8, 12);
		assertPhase(SecondaryEffectDescriptor.Phase.PRE_PRIMARY_SETTLEMENT, 5, 9);
		assertPhase(SecondaryEffectDescriptor.Phase.POST_PRIMARY_DAMAGE, 12, 16);
		assertPhase(SecondaryEffectDescriptor.Phase.SURVIVING_TARGET, 30, 34);
		assertPhase(SecondaryEffectDescriptor.Phase.AFTER_ROOT_ATTACK, 9, 13);
		assertPhase(SecondaryEffectDescriptor.Phase.TARGET_TERMINAL, 1, 5);
		assertPhase(SecondaryEffectDescriptor.Phase.KILL_SETTLEMENT, 3, 7);
		assertPhase(SecondaryEffectDescriptor.Phase.DELAYED_IMPACT, 8, 12);
		assertPhase(SecondaryEffectDescriptor.Phase.PERIODIC_TICK, 2, 6);
		assertImmutableMap(SecondaryEffectDescriptor.currentCountsByPhase(),
			"phase-count map");

		assertTrue(SecondaryEffectDescriptor.WEAPON_POISON.getPhases().contains(
			SecondaryEffectDescriptor.Phase.PRE_PRIMARY_DAMAGE),
			"melee weapon poison precedes primary settlement");
		assertTrue(SecondaryEffectDescriptor.WEAPON_POISON.getPhases().contains(
			SecondaryEffectDescriptor.Phase.SURVIVING_TARGET),
			"projectile weapon poison follows surviving impact");
		assertEquals(SecondaryEffectDescriptor.RandomTiming.SHARED_BRANCH_DRAW,
			SecondaryEffectDescriptor.ELDER_GREEN_DRAGON_PROJECTILE_AOE.getRandomTiming(),
			"Elder projectile branches share one roll");
		assertEquals(SecondaryEffectDescriptor.RecursionPolicy.ROOT_ATTACK_ONLY,
			SecondaryEffectDescriptor.SCYTHE_CLEAVE.getRecursionPolicy(),
			"Scythe does not recursively select more cleaves");
		assertEquals(SecondaryEffectDescriptor.RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS,
			SecondaryEffectDescriptor.BEAR_MAUL_SECOND_HIT.getRecursionPolicy(),
			"Bear Maul remains an allowed Scythe descendant");
		assertEquals(SecondaryEffectDescriptor.RecursionPolicy.ALLOW_KILL_DESCENDANTS,
			SecondaryEffectDescriptor.DEATH_AMULET_BURST.getRecursionPolicy(),
			"kill-triggered jewelry retains child-kill callbacks");

		for (final Method method : SecondaryEffectDescriptor.class.getDeclaredMethods()) {
			final String name = method.getName();
			assertFalse("execute".equals(name) || "apply".equals(name)
					|| "resolve".equals(name) || "register".equals(name),
				"descriptor catalog must not expose runtime authority");
		}
	}

	private static void assertPhase(final SecondaryEffectDescriptor.Phase phase,
			final int expectedCurrent, final int expectedBudget) {
		assertEquals(expectedCurrent,
			SecondaryEffectDescriptor.currentCountForPhase(phase),
			"current descriptor count for " + phase);
		assertEquals(expectedBudget,
			SecondaryEffectDescriptor.recommendedPlanningBudget(phase),
			"planning budget for " + phase);
	}

	private static void assertImmutableMap(final Map<?, ?> values, final String label) {
		boolean immutable = false;
		try {
			values.clear();
		} catch (final UnsupportedOperationException expected) {
			immutable = true;
		}
		assertTrue(immutable, label + " is immutable");
	}

	private static void assertImmutableSet(final Set<?> values, final String label) {
		boolean immutable = false;
		try {
			values.clear();
		} catch (final UnsupportedOperationException expected) {
			immutable = true;
		}
		assertTrue(immutable, label + " is immutable");
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

	private static void assertFalse(final boolean value, final String label) {
		assertTrue(!value, label);
	}

	private static void assertEquals(final Object expected, final Object actual,
			final String label) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(label + " expected=" + expected
				+ " actual=" + actual);
		}
	}
}
