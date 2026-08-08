package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Random;

/** Executable parity evidence for the bounded A07.5F Black Dragon follow-up. */
final class CurrentCombatBlackDragonBreathProcCharacterization {
	private static final double PROC_CHANCE = 0.20D;
	private static final int MAX_PROC_DAMAGE = 10;
	private static final int APPLIED_POISON_POWER = 15;
	private static final int MAX_POISON_POWER = 30;
	private static final String MARKER_KEY = "dragon_breath_armor_proc";
	private static final String BLACK_MARKER = "black";

	private CurrentCombatBlackDragonBreathProcCharacterization() {
	}

	static void blackDragonBreathPolicies(
			final CurrentCombatHarness harness) throws Exception {
		for (ProcPath path : ProcPath.values()) {
			assertSuccessfulIntegratedFlow(harness, path, 7, 710);
			assertSuccessfulIntegratedFlow(harness, path, 0, 720);
			assertFailedChanceConsumesOneDraw(harness, path);
			assertZeroPrimaryClearsStaleMarker(harness, path);
			assertIneligibleFollowupsConsumeNoDraw(harness, path);
		}
	}

	private static void assertSuccessfulIntegratedFlow(
			final CurrentCombatHarness harness, final ProcPath path,
			final int rolledDamage, final int y) throws Exception {
		final Player source = harness.player(
			"Black Success " + path.ordinal() + " " + rolledDamage,
			950 + path.ordinal() * 8, y);
		final Npc target = npcWithHits(
			harness, 951 + path.ordinal() * 8, y, 20);
		equipBlackDragonSet(harness, source, 5);
		assertTrue(source.hasFullBlackDragonSet(),
			path + " full Black Dragon set fixture");

		final Object event = event(path, harness, source, target, 7);
		final RandomExpectation random = primeSuccessfulRandom(
			harness, path, rolledDamage,
			rolledDamage > 0 ? 0xA075F1L : 0xA075F2L);
		invokeWeaponPoison(path, event, source, target, 7);
		assertMarker(source, BLACK_MARKER,
			path + " successful poison marker");
		assertEquals(Integer.valueOf(APPLIED_POISON_POWER),
			Integer.valueOf(target.getCurrentPoisonPower()),
			path + " breath poison power");
		assertEquals(Integer.valueOf(MAX_POISON_POWER),
			Integer.valueOf(target.getPoisonMaxPower()),
			path + " breath poison ceiling");

		invokeLeatherEffects(path, event, source, target, 7);
		assertBreathEffect(source, path + " breath presentation");
		if (rolledDamage > 0) {
			assertDamage(target, source, rolledDamage,
				path + " Black Dragon follow-up damage");
		} else {
			assertNoDamage(target, source, 20, 0,
				path + " zero-roll Black Dragon follow-up");
		}
		assertMarker(source, BLACK_MARKER,
			path + " marker retained through follow-up");
		assertRandomTranscript(harness, path, random,
			path + " chance-then-payload draw order");
	}

	private static void assertFailedChanceConsumesOneDraw(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player source = harness.player(
			"Black Failure " + path.ordinal(), 950 + path.ordinal() * 8, 730);
		final Npc target = npcWithHits(
			harness, 951 + path.ordinal() * 8, 730, 20);
		equipBlackDragonSet(harness, source, 5);
		final Object event = event(path, harness, source, target, 7);
		final RandomExpectation random = primeFailedRandom(harness, path);

		invokeWeaponPoison(path, event, source, target, 7);
		invokeLeatherEffects(path, event, source, target, 7);

		assertMarker(source, "", path + " failed chance marker");
		assertEquals(Integer.valueOf(0),
			Integer.valueOf(target.getCurrentPoisonPower()),
			path + " failed chance poison power");
		assertNoBreathEffect(source, path + " failed chance presentation");
		assertNoDamage(target, source, 20, 0,
			path + " failed chance follow-up damage");
		assertRandomTranscript(harness, path, random,
			path + " failed chance draw cardinality");
	}

	private static void assertZeroPrimaryClearsStaleMarker(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player source = harness.player(
			"Black Zero " + path.ordinal(), 950 + path.ordinal() * 8, 740);
		final Npc target = npcWithHits(
			harness, 951 + path.ordinal() * 8, 740, 20);
		equipBlackDragonSet(harness, source, 5);
		source.setAttribute(MARKER_KEY, BLACK_MARKER);
		final Object event = event(path, harness, source, target, 0);
		final RandomExpectation random = primeNoDrawRandom(
			harness, path, 0xA075F4L);

		invokeWeaponPoison(path, event, source, target, 0);
		invokeLeatherEffects(path, event, source, target, 0);

		assertMarker(source, "", path + " zero primary clears stale marker");
		assertNoBreathEffect(source, path + " zero primary presentation");
		assertNoDamage(target, source, 20, 0,
			path + " zero primary follow-up damage");
		assertRandomTranscript(harness, path, random,
			path + " zero primary draw cardinality");
	}

	private static void assertIneligibleFollowupsConsumeNoDraw(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player incompleteSource = harness.player(
			"Black Partial " + path.ordinal(), 950 + path.ordinal() * 8, 750);
		final Npc incompleteTarget = npcWithHits(
			harness, 951 + path.ordinal() * 8, 750, 20);
		equipBlackDragonSet(harness, incompleteSource, 4);
		assertFalse(incompleteSource.hasFullBlackDragonSet(),
			path + " partial Black Dragon set fixture");
		Object event = event(
			path, harness, incompleteSource, incompleteTarget, 7);
		RandomExpectation random = primeNoDrawRandom(harness, path, 0xA075F5L);
		invokeWeaponPoison(
			path, event, incompleteSource, incompleteTarget, 7);
		invokeLeatherEffects(
			path, event, incompleteSource, incompleteTarget, 7);
		assertMarker(incompleteSource, "", path + " partial set marker");
		assertNoBreathEffect(
			incompleteSource, path + " partial set presentation");
		assertNoDamage(incompleteTarget, incompleteSource, 20, 0,
			path + " partial set damage");
		assertRandomTranscript(harness, path, random,
			path + " partial set draw cardinality");

		final Player wrongMarkerSource = harness.player(
			"Black Wrong " + path.ordinal(), 950 + path.ordinal() * 8, 760);
		final Npc wrongMarkerTarget = npcWithHits(
			harness, 951 + path.ordinal() * 8, 760, 20);
		equipBlackDragonSet(harness, wrongMarkerSource, 5);
		wrongMarkerSource.setAttribute(MARKER_KEY, "king_black");
		event = event(path, harness, wrongMarkerSource, wrongMarkerTarget, 7);
		random = primeNoDrawRandom(harness, path, 0xA075F6L);
		invokeLeatherEffects(
			path, event, wrongMarkerSource, wrongMarkerTarget, 7);
		assertBreathEffect(wrongMarkerSource,
			path + " shared KBD-marker presentation");
		assertNoDamage(wrongMarkerTarget, wrongMarkerSource, 20, 0,
			path + " wrong-marker Black damage");
		assertRandomTranscript(harness, path, random,
			path + " wrong-marker payload cardinality");

		final Player deadTargetSource = harness.player(
			"Black Dead " + path.ordinal(), 950 + path.ordinal() * 8, 770);
		final Npc deadTarget = npcWithHits(
			harness, 951 + path.ordinal() * 8, 770, 20);
		equipBlackDragonSet(harness, deadTargetSource, 5);
		deadTargetSource.setAttribute(MARKER_KEY, BLACK_MARKER);
		deadTarget.getSkills().setLevel(Skill.HITS.id(), 0);
		event = event(path, harness, deadTargetSource, deadTarget, 7);
		random = primeNoDrawRandom(harness, path, 0xA075F7L);
		invokeLeatherEffects(path, event, deadTargetSource, deadTarget, 7);
		assertNoBreathEffect(
			deadTargetSource, path + " dead target presentation");
		assertRandomTranscript(harness, path, random,
			path + " dead target payload cardinality");

		final Npc npcSource = harness.npc(
			NpcId.CHICKEN.id(), 950 + path.ordinal() * 8, 780);
		final Npc npcTarget = npcWithHits(
			harness, 951 + path.ordinal() * 8, 780, 20);
		event = event(path, harness, npcSource, npcTarget, 7);
		random = primeNoDrawRandom(harness, path, 0xA075F8L);
		invokeLeatherEffects(path, event, npcSource, npcTarget, 7);
		assertNoDamage(npcTarget, null, 20, 0,
			path + " NPC source follow-up damage");
		assertRandomTranscript(harness, path, random,
			path + " NPC source payload cardinality");
	}

	private static Object event(final ProcPath path,
			final CurrentCombatHarness harness, final Mob source,
			final Mob target, final int damage) {
		switch (path) {
			case RECIPROCAL_MELEE:
				return new CombatEvent(harness.world(), source, target);
			case PVM_MELEE:
				return new PvmMeleeEvent(harness.world(), source, target);
			case PROJECTILE:
				return new ProjectileEvent(
					harness.world(), source, target, damage, 1, false);
			default:
				throw new AssertionError("Unhandled Black Dragon path " + path);
		}
	}

	private static void invokeWeaponPoison(final ProcPath path,
			final Object event, final Mob source, final Mob target,
			final int damage) throws Exception {
		if (path == ProcPath.PROJECTILE) {
			CurrentCombatHarness.invokePrivate(
				event, "applyWeaponPoison", new Class<?>[0]);
			return;
		}
		CurrentCombatHarness.invokePrivate(event, "applyWeaponPoison",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			source, target, Integer.valueOf(damage));
	}

	private static void invokeLeatherEffects(final ProcPath path,
			final Object event, final Mob source, final Mob target,
			final int damage) throws Exception {
		if (path == ProcPath.PROJECTILE) {
			CurrentCombatHarness.invokePrivate(event,
				"applyLeatherSetOnHitEffects", new Class<?>[0]);
			return;
		}
		CurrentCombatHarness.invokePrivate(event,
			"applyLeatherSetOnHitEffects",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			source, target, Integer.valueOf(damage));
	}

	private static Npc npcWithHits(final CurrentCombatHarness harness,
			final int x, final int y, final int hits) {
		final Npc npc = harness.npc(NpcId.GREATER_DEMON.id(), x, y);
		npc.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), hits, hits, false);
		return npc;
	}

	private static void equipBlackDragonSet(
			final CurrentCombatHarness harness, final Player player,
			final int pieces) throws Exception {
		final int[] itemIds = {
			ItemId.BLACK_DRAGON_COIF.id(),
			ItemId.BLACK_DRAGON_GLOVES.id(),
			ItemId.BLACK_DRAGON_BOOTS.id(),
			ItemId.BLACK_DRAGON_CHAPS.id(),
			ItemId.BLACK_DRAGON_CUIRASS.id()
		};
		for (int index = 0; index < pieces; index++) {
			harness.equip(player, itemIds[index], 1);
		}
	}

	private static RandomExpectation primeSuccessfulRandom(
			final CurrentCombatHarness harness, final ProcPath path,
			final int rolledDamage, final long fixtureSeed) {
		if (path == ProcPath.PVM_MELEE) {
			harness.random().reset(fixtureSeed);
			harness.random().scriptDoubles(Double.valueOf(0.10D));
			harness.random().scriptInts(Integer.valueOf(rolledDamage));
			return new RandomExpectation(0.0D,
				"draws=[double=0.1, int(11)=" + rolledDamage + "]");
		}

		final long seed = findSuccessfulLegacySeed(rolledDamage);
		final Random expected = new Random(seed);
		expected.nextDouble();
		expected.nextInt(MAX_PROC_DAMAGE + 1);
		final double next = expected.nextDouble();
		DataConversions.getRandom().setSeed(seed);
		return new RandomExpectation(next, null);
	}

	private static RandomExpectation primeFailedRandom(
			final CurrentCombatHarness harness, final ProcPath path) {
		if (path == ProcPath.PVM_MELEE) {
			harness.random().reset(0xA075F3L);
			harness.random().scriptDoubles(Double.valueOf(0.50D));
			return new RandomExpectation(0.0D, "draws=[double=0.5]");
		}
		final long seed = findFailedLegacySeed();
		final Random expected = new Random(seed);
		expected.nextDouble();
		final double next = expected.nextDouble();
		DataConversions.getRandom().setSeed(seed);
		return new RandomExpectation(next, null);
	}

	private static RandomExpectation primeNoDrawRandom(
			final CurrentCombatHarness harness, final ProcPath path,
			final long seed) {
		if (path == ProcPath.PVM_MELEE) {
			harness.random().reset(seed);
			return new RandomExpectation(0.0D, "draws=[]");
		}
		final double first = new Random(seed).nextDouble();
		DataConversions.getRandom().setSeed(seed);
		return new RandomExpectation(first, null);
	}

	private static long findSuccessfulLegacySeed(final int rolledDamage) {
		for (long seed = 0L; seed < 1_000_000L; seed++) {
			final Random random = new Random(seed);
			if (random.nextDouble() < PROC_CHANCE
					&& random.nextInt(MAX_PROC_DAMAGE + 1) == rolledDamage) {
				return seed;
			}
		}
		throw new AssertionError("Unable to find successful Black Dragon seed");
	}

	private static long findFailedLegacySeed() {
		for (long seed = 0L; seed < 100_000L; seed++) {
			if (new Random(seed).nextDouble() >= PROC_CHANCE) {
				return seed;
			}
		}
		throw new AssertionError("Unable to find failed Black Dragon seed");
	}

	private static void assertRandomTranscript(
			final CurrentCombatHarness harness, final ProcPath path,
			final RandomExpectation expected, final String label) {
		if (path == ProcPath.PVM_MELEE) {
			assertTrue(harness.random().describeState().contains(
				expected.pvmDraws), label);
			return;
		}
		assertEquals(Double.valueOf(expected.nextLegacyDouble),
			Double.valueOf(DataConversions.getRandom().nextDouble()), label);
	}

	private static void assertMarker(final Player source,
			final String expected, final String label) {
		assertEquals(expected, source.getAttribute(MARKER_KEY, ""), label);
	}

	private static void assertBreathEffect(final Player source,
			final String label) {
		assertNotNull(source.getUpdateFlags().getCombatEffect().get(), label);
		assertEquals(Integer.valueOf(CombatEffect.DRAGON_BREATH),
			Integer.valueOf(source.getUpdateFlags().getCombatEffect().get()
				.getEffectType()), label + " type");
	}

	private static void assertNoBreathEffect(final Player source,
			final String label) {
		assertTrue(source.getUpdateFlags().getCombatEffect().get() == null,
			label);
	}

	private static void assertDamage(final Npc target, final Player source,
			final int amount, final String label) throws Exception {
		assertEquals(Integer.valueOf(20 - amount),
			Integer.valueOf(target.getLevel(Skill.HITS.id())),
			label + " target Hits");
		assertNotNull(target.getUpdateFlags().getDamage().get(),
			label + " damage update");
		assertEquals(Integer.valueOf(amount), Integer.valueOf(
			target.getUpdateFlags().getDamage().get().getDamage()),
			label + " damage amount");
		assertEquals(Integer.valueOf(1), Integer.valueOf(
			target.getUpdateFlags().getHitSplats().size()),
			label + " hitsplat cardinality");
		assertEquals(Integer.valueOf(HitSplat.TYPE_ARMOR_PROC), Integer.valueOf(
			target.getUpdateFlags().getHitSplats().get(0).getType()),
			label + " hitsplat type");
		assertEquals(Integer.valueOf(amount),
			Integer.valueOf(combatContribution(target, source)),
			label + " combat contribution");
	}

	private static void assertNoDamage(final Npc target,
			final Player source, final int expectedHits,
			final int expectedContribution, final String label) throws Exception {
		assertEquals(Integer.valueOf(expectedHits),
			Integer.valueOf(target.getLevel(Skill.HITS.id())),
			label + " target Hits");
		assertTrue(target.getUpdateFlags().getDamage().get() == null,
			label + " damage update");
		assertEquals(Integer.valueOf(0), Integer.valueOf(
			target.getUpdateFlags().getHitSplats().size()),
			label + " hitsplat cardinality");
		if (source != null) {
			assertEquals(Integer.valueOf(expectedContribution),
				Integer.valueOf(combatContribution(target, source)),
				label + " combat contribution");
		}
	}

	private static int combatContribution(final Npc target,
			final Player source) throws Exception {
		@SuppressWarnings("unchecked")
		final Pair<Integer, Long> info = (Pair<Integer, Long>)
			CurrentCombatHarness.invokePrivate(target, "getCombatDamageInfoBy",
				new Class<?>[] {java.util.UUID.class}, source.getUUID());
		return info.getLeft().intValue();
	}

	private static void assertTrue(final boolean condition,
			final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(final boolean condition,
			final String message) {
		assertTrue(!condition, message);
	}

	private static void assertNotNull(final Object value,
			final String message) {
		assertTrue(value != null, message + ": expected a value");
	}

	private static void assertEquals(final Object expected,
			final Object actual, final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}

	private enum ProcPath {
		RECIPROCAL_MELEE,
		PVM_MELEE,
		PROJECTILE
	}

	private static final class RandomExpectation {
		private final double nextLegacyDouble;
		private final String pvmDraws;

		private RandomExpectation(final double nextLegacyDouble,
				final String pvmDraws) {
			this.nextLegacyDouble = nextLegacyDouble;
			this.pvmDraws = pvmDraws;
		}
	}
}
