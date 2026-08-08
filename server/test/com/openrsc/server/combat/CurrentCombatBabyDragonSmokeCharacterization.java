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
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.util.rsc.DataConversions;

import java.lang.reflect.Field;
import java.util.Random;

/** Executable parity evidence for the bounded A07.5B smoke proc. */
final class CurrentCombatBabyDragonSmokeCharacterization {
	private static final double PROC_CHANCE = 0.20D;
	private static final int DEBUFF_PERCENT = 10;
	private static final int DEBUFF_ATTACKS = 5;

	private CurrentCombatBabyDragonSmokeCharacterization() {
	}

	static void babyDragonSmokePolicies(
			final CurrentCombatHarness harness) throws Exception {
		for (ProcPath path : ProcPath.values()) {
			assertSuccessfulZeroDamageProcAndRefresh(harness, path);
			assertFailedRollConsumesOneDraw(harness, path);
			assertIneligibleCallsConsumeNoDraw(harness, path);
		}
	}

	private static void assertSuccessfulZeroDamageProcAndRefresh(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player source = harness.player(
			"Smoke Success " + path.ordinal(), 850 + path.ordinal() * 8, 710);
		final Npc target = harness.npc(
			NpcId.CHICKEN.id(), 851 + path.ordinal() * 8, 710);
		equipBabyDragonSet(harness, source, 5);
		assertTrue(source.getCarriedItems().getEquipment().hasFullBabyDragonSet(),
			path + " full Baby Dragon set fixture");

		Object event = event(path, harness, source, target, 0, 1);
		RandomExpectation random = primeRandom(harness, path, true);
		invokeLeatherEffects(path, event, source, target, 0);

		assertSmokeProjectile(source, target,
			path + " settled-zero smoke presentation");
		assertSmokeState(target, DEBUFF_PERCENT, DEBUFF_ATTACKS,
			path + " settled-zero smoke state");
		assertOneDraw(harness, path, random,
			path + " successful smoke draw cardinality");

		target.consumeAttackBasedDebuffs();
		target.consumeAttackBasedDebuffs();
		assertSmokeState(target, DEBUFF_PERCENT, DEBUFF_ATTACKS - 2,
			path + " smoke state before refresh");

		event = event(path, harness, source, target, 0, 2);
		random = primeRandom(harness, path, true);
		invokeLeatherEffects(path, event, source, target, 0);
		assertSmokeProjectile(source, target,
			path + " refreshed smoke presentation");
		assertSmokeState(target, DEBUFF_PERCENT, DEBUFF_ATTACKS,
			path + " successful smoke refresh");
		assertOneDraw(harness, path, random,
			path + " refreshed smoke draw cardinality");

		for (int attack = 0; attack < DEBUFF_ATTACKS; attack++) {
			target.consumeAttackBasedDebuffs();
		}
		assertSmokeState(target, 0, 0,
			path + " smoke expires after five target attacks");
	}

	private static void assertFailedRollConsumesOneDraw(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player source = harness.player(
			"Smoke Failure " + path.ordinal(), 850 + path.ordinal() * 8, 720);
		final Npc target = harness.npc(
			NpcId.CHICKEN.id(), 851 + path.ordinal() * 8, 720);
		equipBabyDragonSet(harness, source, 5);

		final Object event = event(path, harness, source, target, 7, 2);
		final Projectile projectileBefore =
			target.getUpdateFlags().getProjectile().get();
		final RandomExpectation random = primeRandom(harness, path, false);
		invokeLeatherEffects(path, event, source, target, 7);

		assertProjectileUnchanged(target, projectileBefore,
			path + " failed smoke roll adds no projectile");
		assertSmokeState(target, 0, 0,
			path + " failed smoke roll leaves target unchanged");
		assertOneDraw(harness, path, random,
			path + " failed smoke draw cardinality");
	}

	private static void assertIneligibleCallsConsumeNoDraw(
			final CurrentCombatHarness harness, final ProcPath path)
			throws Exception {
		final Player incompleteSource = harness.player(
			"Smoke Partial " + path.ordinal(), 850 + path.ordinal() * 8, 730);
		final Npc incompleteTarget = harness.npc(
			NpcId.CHICKEN.id(), 851 + path.ordinal() * 8, 730);
		equipBabyDragonSet(harness, incompleteSource, 4);
		assertFalse(incompleteSource.getCarriedItems().getEquipment()
			.hasFullBabyDragonSet(),
			path + " partial Baby Dragon set fixture");

		Object event = event(path, harness, incompleteSource, incompleteTarget, 7, 1);
		Projectile projectileBefore =
			incompleteTarget.getUpdateFlags().getProjectile().get();
		RandomExpectation random = primeRandom(harness, path, true);
		invokeLeatherEffects(path, event, incompleteSource, incompleteTarget, 7);
		assertProjectileUnchanged(incompleteTarget, projectileBefore,
			path + " partial set adds no smoke projectile");
		assertSmokeState(incompleteTarget, 0, 0,
			path + " partial set has no smoke state");
		assertNoDraw(harness, path, random,
			path + " partial set draw cardinality");

		final Player deadTargetSource = harness.player(
			"Smoke Dead " + path.ordinal(), 850 + path.ordinal() * 8, 740);
		final Npc deadTarget = harness.npc(
			NpcId.CHICKEN.id(), 851 + path.ordinal() * 8, 740);
		equipBabyDragonSet(harness, deadTargetSource, 5);
		deadTarget.getSkills().setLevel(Skill.HITS.id(), 0);

		event = event(path, harness, deadTargetSource, deadTarget, 7, 1);
		projectileBefore = deadTarget.getUpdateFlags().getProjectile().get();
		random = primeRandom(harness, path, true);
		invokeLeatherEffects(path, event, deadTargetSource, deadTarget, 7);
		assertProjectileUnchanged(deadTarget, projectileBefore,
			path + " dead target gains no smoke projectile");
		assertSmokeState(deadTarget, 0, 0,
			path + " dead target has no smoke state");
		assertNoDraw(harness, path, random,
			path + " dead target draw cardinality");

		final Npc npcSource = harness.npc(
			NpcId.CHICKEN.id(), 850 + path.ordinal() * 8, 750);
		final Npc npcTarget = harness.npc(
			NpcId.CHICKEN.id(), 851 + path.ordinal() * 8, 750);
		event = event(path, harness, npcSource, npcTarget, 7, 1);
		projectileBefore = npcTarget.getUpdateFlags().getProjectile().get();
		random = primeRandom(harness, path, true);
		invokeLeatherEffects(path, event, npcSource, npcTarget, 7);
		assertProjectileUnchanged(npcTarget, projectileBefore,
			path + " NPC source adds no player smoke projectile");
		assertSmokeState(npcTarget, 0, 0,
			path + " NPC source has no player smoke state");
		assertNoDraw(harness, path, random,
			path + " NPC source draw cardinality");
	}

	private static Object event(final ProcPath path,
			final CurrentCombatHarness harness, final Mob source,
			final Mob target, final int damage, final int projectileType) {
		switch (path) {
			case RECIPROCAL_MELEE:
				return new CombatEvent(harness.world(), source, target);
			case PVM_MELEE:
				return new PvmMeleeEvent(harness.world(), source, target);
			case PROJECTILE:
				return new ProjectileEvent(harness.world(), source, target,
					damage, projectileType, false);
			default:
				throw new AssertionError("Unhandled smoke path " + path);
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

	private static void equipBabyDragonSet(
			final CurrentCombatHarness harness, final Player player,
			final int pieces) throws Exception {
		final int[] itemIds = {
			ItemId.BABY_DRAGON_COIF.id(),
			ItemId.BABY_DRAGON_GLOVES.id(),
			ItemId.BABY_DRAGON_BOOTS.id(),
			ItemId.BABY_DRAGON_CHAPS.id(),
			ItemId.BABY_DRAGON_CUIRASS.id()
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
			harness.random().reset(successfulRoll ? 0xA075B1L : 0xA075B2L);
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
			if ((roll < PROC_CHANCE) == successfulRoll) {
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

	private static void assertSmokeProjectile(final Mob source,
			final Mob target, final String label) {
		final Projectile projectile = target.getUpdateFlags().getProjectile().get();
		assertNotNull(projectile, label);
		assertTrue(projectile.getCaster() == source, label + " source");
		assertTrue(projectile.getVictim() == target, label + " target");
		assertEquals(Integer.valueOf(Projectile.BLOW_SMOKE),
			Integer.valueOf(projectile.getType()), label + " type");
	}

	private static void assertProjectileUnchanged(final Mob target,
			final Projectile expected, final String label) {
		assertTrue(target.getUpdateFlags().getProjectile().get() == expected,
			label);
	}

	private static void assertSmokeState(final Mob target,
			final int expectedPercent, final int expectedAttacks,
			final String label) throws Exception {
		assertEquals(Integer.valueOf(expectedPercent),
			Integer.valueOf(target.getWindLowRollBiasPercent()),
			label + " accuracy bias");
		assertEquals(Integer.valueOf(expectedPercent),
			readMobField(target, "smokeAccuracyDebuffPercent"),
			label + " stored percent");
		assertEquals(Integer.valueOf(expectedAttacks),
			readMobField(target, "smokeAccuracyDebuffAttacksRemaining"),
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
		private final double first;
		private final double second;

		private RandomExpectation(final double first, final double second) {
			this.first = first;
			this.second = second;
		}
	}
}
