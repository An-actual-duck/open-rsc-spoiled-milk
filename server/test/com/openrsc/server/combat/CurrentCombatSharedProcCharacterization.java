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
import com.openrsc.server.util.rsc.DataConversions;

import java.util.Random;

/** Executable parity evidence for the first bounded A07.5 proc family. */
final class CurrentCombatSharedProcCharacterization {
	private static final double OGRE_PROC_CHANCE = 0.20D;

	private CurrentCombatSharedProcCharacterization() {
	}

	static void ogreStaggeringBlowPolicies(
			final CurrentCombatHarness harness) throws Exception {
		for (ProcPath path : ProcPath.values()) {
			assertSuccessfulZeroDamageProc(harness, path);
			assertFailedRollConsumesOneDraw(harness, path);
			assertIneligibleCallsConsumeNoDraw(harness, path);
		}
	}

	private static void assertSuccessfulZeroDamageProc(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player source = harness.player(
			"Ogre Success " + path.ordinal(), 810 + path.ordinal() * 8, 710);
		final Npc target = harness.npc(
			NpcId.CHICKEN.id(), 811 + path.ordinal() * 8, 710);
		equipOgreSet(harness, source, 5);
		assertTrue(source.hasFullOgreSet(), path + " full Ogre set fixture");

		final Object event = event(path, harness, source, target, 0);
		final RandomExpectation random = primeRandom(
			harness, path, true);
		invokeLeatherEffects(path, event, source, target, 0);

		assertTrue(target.consumeOgreStaggerDebuff(),
			path + " applies Ogre stagger on a settled zero hit");
		assertFalse(target.consumeOgreStaggerDebuff(),
			path + " applies exactly one attack of Ogre stagger");
		assertOneDraw(harness, path, random,
			path + " successful proc draw cardinality");
	}

	private static void assertFailedRollConsumesOneDraw(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player source = harness.player(
			"Ogre Failure " + path.ordinal(), 810 + path.ordinal() * 8, 720);
		final Npc target = harness.npc(
			NpcId.CHICKEN.id(), 811 + path.ordinal() * 8, 720);
		equipOgreSet(harness, source, 5);

		final Object event = event(path, harness, source, target, 7);
		final RandomExpectation random = primeRandom(
			harness, path, false);
		invokeLeatherEffects(path, event, source, target, 7);

		assertFalse(target.consumeOgreStaggerDebuff(),
			path + " failed Ogre roll leaves target unchanged");
		assertOneDraw(harness, path, random,
			path + " failed proc draw cardinality");
	}

	private static void assertIneligibleCallsConsumeNoDraw(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player incompleteSource = harness.player(
			"Ogre Partial " + path.ordinal(), 810 + path.ordinal() * 8, 730);
		final Npc incompleteTarget = harness.npc(
			NpcId.CHICKEN.id(), 811 + path.ordinal() * 8, 730);
		equipOgreSet(harness, incompleteSource, 4);
		assertFalse(incompleteSource.hasFullOgreSet(),
			path + " partial Ogre set fixture");

		Object event = event(path, harness, incompleteSource, incompleteTarget, 7);
		RandomExpectation random = primeRandom(harness, path, true);
		invokeLeatherEffects(path, event, incompleteSource, incompleteTarget, 7);
		assertFalse(incompleteTarget.consumeOgreStaggerDebuff(),
			path + " partial set does not apply Ogre stagger");
		assertNoDraw(harness, path, random,
			path + " partial set draw cardinality");

		final Player deadTargetSource = harness.player(
			"Ogre Dead " + path.ordinal(), 810 + path.ordinal() * 8, 740);
		final Npc deadTarget = harness.npc(
			NpcId.CHICKEN.id(), 811 + path.ordinal() * 8, 740);
		equipOgreSet(harness, deadTargetSource, 5);
		deadTarget.getSkills().setLevel(Skill.HITS.id(), 0);

		event = event(path, harness, deadTargetSource, deadTarget, 7);
		random = primeRandom(harness, path, true);
		invokeLeatherEffects(path, event, deadTargetSource, deadTarget, 7);
		assertFalse(deadTarget.consumeOgreStaggerDebuff(),
			path + " dead target does not receive Ogre stagger");
		assertNoDraw(harness, path, random,
			path + " dead target draw cardinality");

		final Npc npcSource = harness.npc(
			NpcId.CHICKEN.id(), 810 + path.ordinal() * 8, 750);
		final Npc npcTarget = harness.npc(
			NpcId.CHICKEN.id(), 811 + path.ordinal() * 8, 750);
		event = event(path, harness, npcSource, npcTarget, 7);
		random = primeRandom(harness, path, true);
		invokeLeatherEffects(path, event, npcSource, npcTarget, 7);
		assertFalse(npcTarget.consumeOgreStaggerDebuff(),
			path + " NPC source does not receive a player equipment proc");
		assertNoDraw(harness, path, random,
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
				throw new AssertionError("Unhandled proc path " + path);
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

	private static void equipOgreSet(final CurrentCombatHarness harness,
			final Player player, final int pieces) throws Exception {
		final int[] itemIds = {
			ItemId.OGRE_COIF.id(),
			ItemId.OGRE_GLOVES.id(),
			ItemId.OGRE_BOOTS.id(),
			ItemId.OGRE_CHAPS.id(),
			ItemId.OGRE_CUIRASS.id()
		};
		for (int index = 0; index < pieces; index++) {
			harness.equip(player, itemIds[index], 1);
		}
	}

	private static RandomExpectation primeRandom(
			final CurrentCombatHarness harness, final ProcPath path,
			final boolean successfulRoll) {
		if (path == ProcPath.PVM_MELEE) {
			final double roll = successfulRoll ? 0.10D : 0.50D;
			harness.random().reset(successfulRoll ? 0xA07501L : 0xA07502L);
			harness.random().scriptDoubles(Double.valueOf(roll));
			return new RandomExpectation(roll, 0.0D);
		}

		final long seed = findLegacySeed(successfulRoll);
		final Random expected = new Random(seed);
		final double first = expected.nextDouble();
		final double second = expected.nextDouble();
		DataConversions.getRandom().setSeed(seed);
		return new RandomExpectation(first, second);
	}

	private static long findLegacySeed(final boolean successfulRoll) {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final double roll = new Random(seed).nextDouble();
			if ((roll < OGRE_PROC_CHANCE) == successfulRoll) {
				return seed;
			}
		}
		throw new AssertionError("Unable to find deterministic legacy seed");
	}

	private static void assertOneDraw(final CurrentCombatHarness harness,
			final ProcPath path, final RandomExpectation random,
			final String label) {
		if (path == ProcPath.PVM_MELEE) {
			assertTrue(harness.random().describeState().contains(
				"draws=[double=" + random.first + "]"), label);
			return;
		}
		assertEquals(Double.valueOf(random.second),
			Double.valueOf(DataConversions.getRandom().nextDouble()), label);
	}

	private static void assertNoDraw(final CurrentCombatHarness harness,
			final ProcPath path, final RandomExpectation random,
			final String label) {
		if (path == ProcPath.PVM_MELEE) {
			assertTrue(harness.random().describeState().contains("draws=[]"),
				label);
			return;
		}
		assertEquals(Double.valueOf(random.first),
			Double.valueOf(DataConversions.getRandom().nextDouble()), label);
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
		private final double first;
		private final double second;

		private RandomExpectation(final double first, final double second) {
			this.first = first;
			this.second = second;
		}
	}
}
