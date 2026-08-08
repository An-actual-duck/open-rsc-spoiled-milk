package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.combat.InfernalFireProc;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.runtime.GameRandom;

/** Executable contract evidence for the bounded A07.5H Infernal Fire core. */
final class CurrentCombatInfernalFireProcCharacterization {
	private CurrentCombatInfernalFireProcCharacterization() {
	}

	static void infernalFirePolicies(final CurrentCombatHarness harness)
			throws Exception {
		assertSuccessfulOrderingAndExpiry(harness);
		assertZeroPayloadStillAppliesState(harness);
		assertFailedAndUnconfiguredDrawPolicies(harness);
	}

	private static void assertSuccessfulOrderingAndExpiry(
			final CurrentCombatHarness harness) throws Exception {
		final Player source = demonPlayer(harness, "Infernal success", 900, 700);
		final Npc target = target(harness, 901, 700);
		final int[] callbacks = {0, -1, -1};
		final ScriptedRandom random = new ScriptedRandom(0.10D, 7);
		final InfernalFireProc.Result result = InfernalFireProc.tryApply(source,
			target, random, damage -> {
				callbacks[0] = damage;
				callbacks[1] = target.getFireDefenseDebuffPercent();
				return 5;
			}, damageDealt -> {
				callbacks[2] = target.getFireDefenseDebuffPercent();
				assertEquals(5, damageDealt, "post-damage callback receives settled damage");
			});
		assertTrue(result.isTriggered(), "successful Infernal Fire trigger");
		assertEquals(8, result.getMaxHit(), "Demon Infernal max hit");
		assertEquals(7, result.getRolledDamage(), "Infernal inclusive payload");
		assertEquals(5, result.getDamageDealt(), "Infernal settled callback result");
		assertEquals(7, callbacks[0], "damage callback receives payload");
		assertEquals(0, callbacks[1], "damage callback precedes defense debuff");
		assertEquals(6, callbacks[2], "followup observes defense debuff");
		assertEquals(2, random.draws, "Infernal chance then payload draws");
		assertEquals(CombatEffect.HELLS_FIRE,
			target.getUpdateFlags().getCombatEffect().get().getEffectType(),
			"Demon Infernal presentation");
		for (int attack = 0; attack < 5; attack++) {
			target.consumeAttackBasedDebuffs();
		}
		assertEquals(0, target.getFireDefenseDebuffPercent(),
			"Infernal defense debuff expires after five target attacks");
	}

	private static void assertZeroPayloadStillAppliesState(
			final CurrentCombatHarness harness) throws Exception {
		final Player source = demonPlayer(harness, "Infernal zero", 910, 700);
		final Npc target = target(harness, 911, 700);
		final int[] calls = {0};
		final InfernalFireProc.Result result = InfernalFireProc.tryApply(source,
			target, new ScriptedRandom(0.10D, 0), damage -> {
				calls[0]++;
				return 0;
			}, damageDealt -> calls[0]++);
		assertTrue(result.isTriggered(), "zero Infernal payload still triggers");
		assertEquals(0, result.getRolledDamage(), "zero Infernal payload");
		assertEquals(2, calls[0], "zero payload retains damage and followup callbacks");
		assertEquals(6, target.getFireDefenseDebuffPercent(),
			"zero payload applies Infernal defense debuff");
	}

	private static void assertFailedAndUnconfiguredDrawPolicies(
			final CurrentCombatHarness harness) throws Exception {
		final Player source = demonPlayer(harness, "Infernal failed", 920, 700);
		final Npc target = target(harness, 921, 700);
		final ScriptedRandom failed = new ScriptedRandom(0.50D, 0);
		InfernalFireProc.Result result = InfernalFireProc.tryApply(source,
			target, failed, damage -> { throw new AssertionError("failed damage callback"); },
			damage -> { throw new AssertionError("failed followup callback"); });
		assertFalse(result.isTriggered(), "failed Infernal chance");
		assertEquals(1, failed.draws, "failed Infernal consumes one chance draw");

		final Player unconfigured = harness.player("Infernal none", 930, 700);
		final ScriptedRandom noDraw = new ScriptedRandom(0.10D, 7);
		result = InfernalFireProc.tryApply(unconfigured, target, noDraw,
			damage -> 0, damage -> { });
		assertFalse(result.isConfigured(), "no Infernal armor is unconfigured");
		assertEquals(0, noDraw.draws, "unconfigured Infernal consumes no draw");
	}

	private static Player demonPlayer(final CurrentCombatHarness harness,
			final String name, final int x, final int y) throws Exception {
		final Player player = harness.player(name, x, y);
		final int[] ids = {ItemId.DEMON_COIF.id(), ItemId.DEMON_GLOVES.id(),
			ItemId.DEMON_BOOTS.id(), ItemId.DEMON_CHAPS.id(), ItemId.DEMON_CUIRASS.id()};
		for (final int id : ids) {
			harness.equip(player, id, 1);
		}
		return player;
	}

	private static Npc target(final CurrentCombatHarness harness, final int x,
			final int y) {
		final Npc target = harness.npc(NpcId.GREATER_DEMON.id(), x, y);
		target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 20, 20, false);
		return target;
	}

	private static void assertTrue(final boolean value, final String label) {
		if (!value) { throw new AssertionError(label); }
	}

	private static void assertFalse(final boolean value, final String label) {
		assertTrue(!value, label);
	}

	private static void assertEquals(final int expected, final int actual,
			final String label) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	private static final class ScriptedRandom implements GameRandom {
		private final double chance;
		private final int payload;
		private int draws;
		private boolean chanceDrawn;

		private ScriptedRandom(final double chance, final int payload) {
			this.chance = chance;
			this.payload = payload;
		}

		@Override
		public int nextInt(final int bound) {
			if (!chanceDrawn || payload < 0 || payload >= bound) {
				throw new AssertionError("unexpected Infernal payload bound " + bound);
			}
			draws++;
			return payload;
		}

		@Override
		public double nextDouble() {
			if (chanceDrawn) { throw new AssertionError("duplicate Infernal chance draw"); }
			chanceDrawn = true;
			draws++;
			return chance;
		}

		@Override
		public String describeState() { return "draws=" + draws; }
	}
}
