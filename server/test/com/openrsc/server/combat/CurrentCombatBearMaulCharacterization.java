package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.combat.BearMaulSecondHit;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

/** Executable eligibility and callback evidence for bounded A07.5J. */
final class CurrentCombatBearMaulCharacterization {
	private CurrentCombatBearMaulCharacterization() {
	}

	static void bearMaulPolicies(final CurrentCombatHarness harness) throws Exception {
		final Player full = bearPlayer(harness, "Bear full", 900, 700, 5);
		final Npc target = target(harness, 901, 700);
		final int[] payload = {-1};
		assertTrue(BearMaulSecondHit.tryApply(full, target, 6,
			damage -> payload[0] = damage), "full Bear set second hit");
		assertEquals(6, payload[0], "Bear second-hit callback damage");

		final Player partial = bearPlayer(harness, "Bear partial", 910, 700, 4);
		assertFalse(BearMaulSecondHit.tryApply(partial, target, 6,
			damage -> { throw new AssertionError("partial Bear callback"); }),
			"partial Bear set rejected");
		assertFalse(BearMaulSecondHit.tryApply(full, target, 0,
			damage -> { throw new AssertionError("zero Bear callback"); }),
			"zero Bear primary rejected");
		target.getSkills().setLevel(Skill.HITS.id(), 0);
		assertFalse(BearMaulSecondHit.tryApply(full, target, 6,
			damage -> { throw new AssertionError("dead-target Bear callback"); }),
			"dead Bear target rejected");
		final Npc npcSource = harness.npc(NpcId.CHICKEN.id(), 920, 700);
		assertFalse(BearMaulSecondHit.tryApply(npcSource, target, 6,
			damage -> { throw new AssertionError("NPC Bear callback"); }),
			"NPC Bear source rejected");
	}

	private static Player bearPlayer(final CurrentCombatHarness harness,
			final String name, final int x, final int y, final int pieces) throws Exception {
		final Player player = harness.player(name, x, y);
		final int[] ids = {ItemId.BEAR_HIDE_COIF.id(), ItemId.BEAR_HIDE_GLOVES.id(),
			ItemId.BEAR_HIDE_BOOTS.id(), ItemId.BEAR_HIDE_CHAPS.id(), ItemId.BEAR_HIDE_CUIRASS.id()};
		for (int index = 0; index < pieces; index++) { harness.equip(player, ids[index], 1); }
		return player;
	}

	private static Npc target(final CurrentCombatHarness harness, final int x, final int y) {
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

	private static void assertEquals(final int expected, final int actual, final String label) {
		if (expected != actual) { throw new AssertionError(label); }
	}
}
