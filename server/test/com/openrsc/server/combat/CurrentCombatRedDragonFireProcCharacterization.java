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
import com.openrsc.server.model.entity.update.HitSplat;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.util.Random;

/** Executable parity evidence for the bounded A07.5E fire proc. */
final class CurrentCombatRedDragonFireProcCharacterization {
	private static final double PROC_CHANCE = 0.20D;
	private static final int MAX_PROC_DAMAGE = 10;
	private static final int DEBUFF_PERCENT = 6;
	private static final int DEBUFF_ATTACKS = 5;

	private CurrentCombatRedDragonFireProcCharacterization() {
	}

	static void redDragonFirePolicies(
			final CurrentCombatHarness harness) throws Exception {
		for (ProcPath path : ProcPath.values()) {
			assertSuccessfulZeroPrimaryDamageAndRefresh(harness, path);
			assertFailedRollConsumesOneDraw(harness, path);
			assertIneligibleCallsConsumeNoDraw(harness, path);
		}
	}

	private static void assertSuccessfulZeroPrimaryDamageAndRefresh(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player source = harness.player(
			"Fire Success " + path.ordinal(), 910 + path.ordinal() * 8, 710);
		final Npc target = npcWithHits(
			harness, 911 + path.ordinal() * 8, 710, 20);
		equipRedDragonSet(harness, source, 5);
		assertTrue(source.hasFullRedDragonSet(),
			path + " full Red Dragon set fixture");

		Object event = event(path, harness, source, target, 0);
		RandomExpectation random = primeSuccessfulRandom(
			harness, path, 7, 0xA075E1L);
		invokeLeatherEffects(path, event, source, target, 0);

		assertDamage(target, source, 7,
			path + " settled-zero primary fire damage");
		assertFireState(target, DEBUFF_PERCENT, DEBUFF_ATTACKS,
			path + " settled-zero primary fire debuff");
		assertRandomTranscript(harness, path, random,
			path + " successful fire draw order");

		target.consumeAttackBasedDebuffs();
		target.consumeAttackBasedDebuffs();
		assertFireState(target, DEBUFF_PERCENT, DEBUFF_ATTACKS - 2,
			path + " fire debuff before refresh");

		final Object damageUpdateBefore =
			target.getUpdateFlags().getDamage().get();
		final int hitSplatCountBefore =
			target.getUpdateFlags().getHitSplats().size();
		event = event(path, harness, source, target, 0);
		random = primeSuccessfulRandom(harness, path, 0, 0xA075E2L);
		invokeLeatherEffects(path, event, source, target, 0);
		assertDamageUnchanged(target, source, 13, 7,
			damageUpdateBefore, hitSplatCountBefore,
			path + " zero-roll fire damage");
		assertFireState(target, DEBUFF_PERCENT, DEBUFF_ATTACKS,
			path + " zero-roll successful refresh");
		assertRandomTranscript(harness, path, random,
			path + " zero-roll fire draw order");

		for (int attack = 0; attack < DEBUFF_ATTACKS; attack++) {
			target.consumeAttackBasedDebuffs();
		}
		assertFireState(target, 0, 0,
			path + " fire debuff expires after five target attacks");
	}

	private static void assertFailedRollConsumesOneDraw(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player source = harness.player(
			"Fire Failure " + path.ordinal(), 910 + path.ordinal() * 8, 720);
		final Npc target = npcWithHits(
			harness, 911 + path.ordinal() * 8, 720, 20);
		equipRedDragonSet(harness, source, 5);

		final Object event = event(path, harness, source, target, 7);
		final RandomExpectation random = primeFailedRandom(harness, path);
		invokeLeatherEffects(path, event, source, target, 7);

		assertNoDamage(target, source, 20, 0,
			path + " failed fire roll");
		assertFireState(target, 0, 0,
			path + " failed fire roll state");
		assertRandomTranscript(harness, path, random,
			path + " failed fire draw cardinality");
	}

	private static void assertIneligibleCallsConsumeNoDraw(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player incompleteSource = harness.player(
			"Fire Partial " + path.ordinal(), 910 + path.ordinal() * 8, 730);
		final Npc incompleteTarget = npcWithHits(
			harness, 911 + path.ordinal() * 8, 730, 20);
		equipRedDragonSet(harness, incompleteSource, 4);
		assertFalse(incompleteSource.hasFullRedDragonSet(),
			path + " partial Red Dragon set fixture");

		Object event = event(
			path, harness, incompleteSource, incompleteTarget, 7);
		RandomExpectation random = primeNoDrawRandom(harness, path, 0xA075E3L);
		invokeLeatherEffects(
			path, event, incompleteSource, incompleteTarget, 7);
		assertNoDamage(incompleteTarget, incompleteSource, 20, 0,
			path + " partial set fire damage");
		assertFireState(incompleteTarget, 0, 0,
			path + " partial set fire debuff");
		assertRandomTranscript(harness, path, random,
			path + " partial set draw cardinality");

		final Player deadTargetSource = harness.player(
			"Fire Dead " + path.ordinal(), 910 + path.ordinal() * 8, 740);
		final Npc deadTarget = npcWithHits(
			harness, 911 + path.ordinal() * 8, 740, 20);
		equipRedDragonSet(harness, deadTargetSource, 5);
		deadTarget.getSkills().setLevel(Skill.HITS.id(), 0);

		event = event(path, harness, deadTargetSource, deadTarget, 7);
		random = primeNoDrawRandom(harness, path, 0xA075E4L);
		invokeLeatherEffects(path, event, deadTargetSource, deadTarget, 7);
		assertFireState(deadTarget, 0, 0,
			path + " dead target fire debuff");
		assertRandomTranscript(harness, path, random,
			path + " dead target draw cardinality");

		final Npc npcSource = harness.npc(
			NpcId.CHICKEN.id(), 910 + path.ordinal() * 8, 750);
		final Npc npcTarget = npcWithHits(
			harness, 911 + path.ordinal() * 8, 750, 20);
		event = event(path, harness, npcSource, npcTarget, 7);
		random = primeNoDrawRandom(harness, path, 0xA075E5L);
		invokeLeatherEffects(path, event, npcSource, npcTarget, 7);
		assertFireState(npcTarget, 0, 0,
			path + " NPC source fire debuff");
		assertRandomTranscript(harness, path, random,
			path + " NPC source draw cardinality");
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
				throw new AssertionError("Unhandled fire path " + path);
		}
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

	private static void equipRedDragonSet(
			final CurrentCombatHarness harness, final Player player,
			final int pieces) throws Exception {
		final int[] itemIds = {
			ItemId.RED_DRAGON_COIF.id(),
			ItemId.RED_DRAGON_GLOVES.id(),
			ItemId.RED_DRAGON_BOOTS.id(),
			ItemId.RED_DRAGON_CHAPS.id(),
			ItemId.RED_DRAGON_CUIRASS.id()
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
			harness.random().reset(0xA075E6L);
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
		throw new AssertionError("Unable to find successful fire seed");
	}

	private static long findFailedLegacySeed() {
		for (long seed = 0L; seed < 100_000L; seed++) {
			if (new Random(seed).nextDouble() >= PROC_CHANCE) {
				return seed;
			}
		}
		throw new AssertionError("Unable to find failed fire seed");
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
		assertEquals(Integer.valueOf(expectedContribution),
			Integer.valueOf(combatContribution(target, source)),
			label + " combat contribution");
	}

	private static void assertDamageUnchanged(final Npc target,
			final Player source, final int expectedHits,
			final int expectedContribution, final Object expectedDamageUpdate,
			final int expectedHitSplatCount, final String label) throws Exception {
		assertEquals(Integer.valueOf(expectedHits),
			Integer.valueOf(target.getLevel(Skill.HITS.id())),
			label + " target Hits");
		assertTrue(target.getUpdateFlags().getDamage().get()
			== expectedDamageUpdate, label + " damage update identity");
		assertEquals(Integer.valueOf(expectedHitSplatCount), Integer.valueOf(
			target.getUpdateFlags().getHitSplats().size()),
			label + " hitsplat cardinality");
		assertEquals(Integer.valueOf(expectedContribution),
			Integer.valueOf(combatContribution(target, source)),
			label + " combat contribution");
	}

	private static int combatContribution(final Npc target,
			final Player source) throws Exception {
		@SuppressWarnings("unchecked")
		final Pair<Integer, Long> info = (Pair<Integer, Long>)
			CurrentCombatHarness.invokePrivate(target, "getCombatDamageInfoBy",
				new Class<?>[] {java.util.UUID.class}, source.getUUID());
		return info.getLeft().intValue();
	}

	private static void assertFireState(final Mob target,
			final int expectedPercent, final int expectedAttacks,
			final String label) throws Exception {
		assertEquals(Integer.valueOf(expectedPercent),
			Integer.valueOf(target.getFireDefenseDebuffPercent()),
			label + " defense reduction");
		assertEquals(Integer.valueOf(expectedPercent),
			readMobField(target, "dragonFireDefenseDebuffPercent"),
			label + " stored percent");
		assertEquals(Integer.valueOf(expectedAttacks),
			readMobField(target, "dragonFireDefenseDebuffAttacksRemaining"),
			label + " attacks remaining");
	}

	private static Object readMobField(final Mob target,
			final String fieldName) throws Exception {
		final Field field = Mob.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(target);
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
