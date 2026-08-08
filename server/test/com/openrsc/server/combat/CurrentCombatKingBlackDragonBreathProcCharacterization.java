package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.model.combat.KingBlackDragonBreathFollowup;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;

/** Executable contract evidence for the bounded A07.5G KBD follow-up. */
final class CurrentCombatKingBlackDragonBreathProcCharacterization {
	private static final String KBD_MARKER = "king_black";

	private CurrentCombatKingBlackDragonBreathProcCharacterization() {
	}

	static void kingBlackDragonBreathPolicies(
			final CurrentCombatHarness harness) throws Exception {
		for (int element = 0; element < 3; element++) {
			assertPayloadAndElement(harness, element);
		}
		assertZeroPayloadStillAppliesElement(harness);
		assertIneligibleCallsConsumeNoDraw(harness);
	}

	private static void assertPayloadAndElement(
			final CurrentCombatHarness harness, final int element)
			throws Exception {
		final Player source = kbdPlayer(harness, "KBD " + element,
			900 + element * 4, 700);
		final Npc target = target(harness, 901 + element * 4, 700);
		final int[] callback = {0};
		final ScriptedRandom random = new ScriptedRandom(7, element);

		assertTrue(KingBlackDragonBreathFollowup.tryApply(source, target,
			KBD_MARKER, random, damage -> callback[0] = damage),
			"full KBD set and KBD marker activate " + element);
		assertEquals(7, callback[0], "KBD payload " + element);
		assertEquals(2, random.draws, "KBD payload and element draws " + element);
		assertElement(target, element, "KBD element " + element);
		for (int attack = 0; attack < 5; attack++) {
			target.consumeAttackBasedDebuffs();
		}
		assertEquals(0, target.getWaterMaxHitDebuffPercent(),
			"KBD water expires");
		assertEquals(0, target.getEarthAttackSpeedDebuffPercent(),
			"KBD earth expires");
		assertEquals(0, target.getFireDefenseDebuffPercent(),
			"KBD fire expires");
	}

	private static void assertZeroPayloadStillAppliesElement(
			final CurrentCombatHarness harness) throws Exception {
		final Player source = kbdPlayer(harness, "KBD zero", 920, 700);
		final Npc target = target(harness, 921, 700);
		final int[] callbacks = {0};
		final ScriptedRandom random = new ScriptedRandom(0, 2);

		assertTrue(KingBlackDragonBreathFollowup.tryApply(source, target,
			KBD_MARKER, random, damage -> callbacks[0]++),
			"zero KBD payload still activates");
		assertEquals(0, callbacks[0], "zero KBD payload skips damage adapter");
		assertEquals(2, random.draws, "zero KBD payload still selects element");
		assertElement(target, 2, "zero KBD payload fire element");
	}

	private static void assertIneligibleCallsConsumeNoDraw(
			final CurrentCombatHarness harness) throws Exception {
		final Player partial = harness.player("KBD partial", 930, 700);
		equipKbdSet(harness, partial, 4);
		final Npc target = target(harness, 931, 700);
		ScriptedRandom random = new ScriptedRandom(7, 1);
		assertFalse(KingBlackDragonBreathFollowup.tryApply(partial, target,
			KBD_MARKER, random, damage -> { }), "partial KBD set rejected");
		assertEquals(0, random.draws, "partial KBD set consumes no draw");

		final Player full = kbdPlayer(harness, "KBD marker", 940, 700);
		random = new ScriptedRandom(7, 1);
		assertFalse(KingBlackDragonBreathFollowup.tryApply(full, target,
			"black", random, damage -> { }), "Black marker rejected by KBD");
		assertEquals(0, random.draws, "wrong KBD marker consumes no draw");
	}

	private static Player kbdPlayer(final CurrentCombatHarness harness,
			final String name, final int x, final int y) throws Exception {
		final Player player = harness.player(name, x, y);
		equipKbdSet(harness, player, 5);
		assertTrue(player.hasFullKingBlackDragonSet(), "full KBD fixture");
		return player;
	}

	private static void equipKbdSet(final CurrentCombatHarness harness,
			final Player player, final int pieces) throws Exception {
		final int[] ids = {MyWorldItemId.KING_BLACK_DRAGON_COIF,
			MyWorldItemId.KING_BLACK_DRAGON_GLOVES,
			MyWorldItemId.KING_BLACK_DRAGON_BOOTS,
			MyWorldItemId.KING_BLACK_DRAGON_CHAPS,
			MyWorldItemId.KING_BLACK_DRAGON_CUIRASS};
		for (int index = 0; index < pieces; index++) {
			harness.equip(player, ids[index], 1);
		}
	}

	private static Npc target(final CurrentCombatHarness harness,
			final int x, final int y) {
		final Npc target = harness.npc(NpcId.GREATER_DEMON.id(), x, y);
		target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 20, 20,
			false);
		return target;
	}

	private static void assertElement(final Npc target, final int element,
			final String label) {
		assertEquals(element == 0 ? 10 : 0,
			target.getWaterMaxHitDebuffPercent(), label + " water");
		assertEquals(element == 1 ? 6 : 0,
			target.getEarthAttackSpeedDebuffPercent(), label + " earth");
		assertEquals(element == 2 ? 6 : 0,
			target.getFireDefenseDebuffPercent(), label + " fire");
	}

	private static void assertTrue(final boolean value, final String label) {
		if (!value) {
			throw new AssertionError(label);
		}
	}

	private static void assertFalse(final boolean value, final String label) {
		assertTrue(!value, label);
	}

	private static void assertEquals(final int expected, final int actual,
			final String label) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected " + expected
				+ ", got " + actual);
		}
	}

	private static final class ScriptedRandom implements GameRandom {
		private final int[] values;
		private int position;
		private int draws;

		private ScriptedRandom(final int... values) {
			this.values = values;
		}

		@Override
		public int nextInt(final int bound) {
			if (position >= values.length || values[position] < 0
					|| values[position] >= bound) {
				throw new AssertionError("unexpected KBD random bound " + bound);
			}
			draws++;
			return values[position++];
		}

		@Override
		public double nextDouble() {
			throw new AssertionError("KBD follow-up must not draw chance");
		}

		@Override
		public String describeState() {
			return "draws=" + draws;
		}
	}
}
