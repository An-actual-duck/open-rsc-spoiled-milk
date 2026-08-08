package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.combat.KolodionFireClawProc;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.runtime.GameRandom;

/** Executable gate and RNG-boundary checks for Kolodion demon Fire Claw. */
final class CurrentCombatKolodionFireClawCharacterization {
	private CurrentCombatKolodionFireClawCharacterization() { }

	static void fireClawPolicies(final CurrentCombatHarness harness) {
		final Npc demon = harness.npc(NpcId.KOLODION_DEMON.id(), 900, 700);
		final Npc target = harness.npc(NpcId.GREATER_DEMON.id(), 901, 700);
		target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 20, 20, false);
		assertTrue(KolodionFireClawProc.tryApply(demon, target, 1, 0.10D,
			fixed(0.099D)), "below-threshold demon hit triggers");
		assertFalse(KolodionFireClawProc.tryApply(demon, target, 1, 0.10D,
			fixed(0.10D)), "threshold roll does not trigger");
		assertFalse(KolodionFireClawProc.tryApply(demon, target, 0, 0.10D,
			throwing()), "zero primary rejects before RNG");
		target.getSkills().setLevel(Skill.HITS.id(), 0);
		assertFalse(KolodionFireClawProc.tryApply(demon, target, 1, 0.10D,
			throwing()), "dead target rejects before RNG");
	}

	private static GameRandom fixed(final double value) {
		return new GameRandom() {
			@Override public int nextInt(final int bound) { return 0; }
			@Override public double nextDouble() { return value; }
			@Override public String describeState() { return "fixed"; }
		};
	}

	private static GameRandom throwing() {
		return new GameRandom() {
			@Override public int nextInt(final int bound) { return 0; }
			@Override public double nextDouble() {
				throw new AssertionError("unexpected Fire Claw RNG draw");
			}
			@Override public String describeState() { return "throwing"; }
		};
	}

	private static void assertTrue(final boolean value, final String label) {
		if (!value) throw new AssertionError(label);
	}

	private static void assertFalse(final boolean value, final String label) {
		assertTrue(!value, label);
	}
}
