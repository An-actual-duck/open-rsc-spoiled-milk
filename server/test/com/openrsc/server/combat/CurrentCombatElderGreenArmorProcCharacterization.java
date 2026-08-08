package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.combat.ElderGreenDragonArmorProc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.runtime.GameRandom;

/** Executable trigger-level parity evidence for bounded A07.5I. */
final class CurrentCombatElderGreenArmorProcCharacterization {
	private CurrentCombatElderGreenArmorProcCharacterization() {
	}

	static void elderGreenArmorTriggerPolicies(
			final CurrentCombatHarness harness) throws Exception {
		final Player source = elderPlayer(harness, "Elder trigger", 900, 700);
		final int[] payload = {-1};
		final ScriptedRandom success = new ScriptedRandom(0.10D, 10);
		assertTrue(ElderGreenDragonArmorProc.tryApply(source, 1, success,
			damage -> payload[0] = damage), "Elder positive primary succeeds");
		assertEquals(10, payload[0], "Elder inclusive maximum payload");
		assertEquals(2, success.draws, "Elder chance then payload draws");

		final ScriptedRandom failed = new ScriptedRandom(0.70D, 0);
		assertFalse(ElderGreenDragonArmorProc.tryApply(source, 1, failed,
			damage -> { throw new AssertionError("failed Elder payload"); }),
			"Elder failed chance");
		assertEquals(1, failed.draws, "Elder failed chance consumes one draw");

		final ScriptedRandom zeroPrimary = new ScriptedRandom(0.10D, 5);
		assertFalse(ElderGreenDragonArmorProc.tryApply(source, 0, zeroPrimary,
			damage -> { throw new AssertionError("zero-primary Elder payload"); }),
			"Elder zero primary rejected");
		assertEquals(0, zeroPrimary.draws, "Elder zero primary consumes no draw");

		final Player unconfigured = harness.player("Elder none", 910, 700);
		final ScriptedRandom noSet = new ScriptedRandom(0.10D, 5);
		assertFalse(ElderGreenDragonArmorProc.tryApply(unconfigured, 1, noSet,
			damage -> { throw new AssertionError("unconfigured Elder payload"); }),
			"Elder missing set rejected");
		assertEquals(0, noSet.draws, "Elder missing set consumes no draw");
	}

	private static Player elderPlayer(final CurrentCombatHarness harness,
			final String name, final int x, final int y) throws Exception {
		final Player player = harness.player(name, x, y);
		final int[] ids = {ItemId.ELDER_GREEN_DRAGON_COIF.id(),
			ItemId.ELDER_GREEN_DRAGON_GLOVES.id(),
			ItemId.ELDER_GREEN_DRAGON_BOOTS.id(),
			ItemId.ELDER_GREEN_DRAGON_CHAPS.id(),
			ItemId.ELDER_GREEN_DRAGON_CUIRASS.id()};
		for (final int id : ids) { harness.equip(player, id, 1); }
		return player;
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
				throw new AssertionError("unexpected Elder payload bound " + bound);
			}
			draws++;
			return payload;
		}

		@Override
		public double nextDouble() {
			if (chanceDrawn) { throw new AssertionError("duplicate Elder chance draw"); }
			chanceDrawn = true;
			draws++;
			return chance;
		}

		@Override
		public String describeState() { return "draws=" + draws; }
	}
}
