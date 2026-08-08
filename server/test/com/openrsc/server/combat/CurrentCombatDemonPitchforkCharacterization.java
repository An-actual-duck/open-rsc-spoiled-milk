package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.combat.DemonPitchforkHellBlazeProc;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.update.CombatEffect;

/** Executable callback-order evidence for bounded A07.5M Hell's Blaze. */
final class CurrentCombatDemonPitchforkCharacterization {
	private CurrentCombatDemonPitchforkCharacterization() { }

	static void hellBlazePolicies(final CurrentCombatHarness harness) {
		final Npc target = harness.npc(NpcId.GREATER_DEMON.id(), 902, 700);
		target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 20, 20, false);
		final int[] payload = {-1};
		assertTrue(DemonPitchforkHellBlazeProc.tryApply(target, 5, () -> true,
			() -> 4, damage -> payload[0] = damage), "positive Hell's Blaze proc");
		assertEquals(4, payload[0], "Hell's Blaze callback payload");
		assertEquals(CombatEffect.HELLS_BLAZE,
			target.getUpdateFlags().getCombatEffect().get().getEffectType(),
			"Hell's Blaze presentation precedes settlement");

		assertFalse(DemonPitchforkHellBlazeProc.tryApply(target, 0,
			() -> { throw new AssertionError("zero-primary chance draw"); },
			() -> { throw new AssertionError("zero-primary payload draw"); },
			damage -> { throw new AssertionError("zero-primary callback"); }),
			"zero primary rejected without draws");
		assertFalse(DemonPitchforkHellBlazeProc.tryApply(target, 5, () -> false,
			() -> { throw new AssertionError("failed-chance payload draw"); },
			damage -> { throw new AssertionError("failed-chance callback"); }),
			"failed chance rejects payload");

		payload[0] = -1;
		assertTrue(DemonPitchforkHellBlazeProc.tryApply(target, 5, () -> true,
			() -> 0, damage -> payload[0] = damage), "zero payload remains a proc");
		assertEquals(-1, payload[0], "zero payload invokes no damage callback");

		target.getSkills().setLevel(Skill.HITS.id(), 0);
		assertFalse(DemonPitchforkHellBlazeProc.tryApply(target, 5,
			() -> { throw new AssertionError("dead-target chance draw"); },
			() -> { throw new AssertionError("dead-target payload draw"); },
			damage -> { throw new AssertionError("dead-target callback"); }),
			"dead target rejected without draws");
	}

	private static void assertTrue(final boolean value, final String label) {
		if (!value) throw new AssertionError(label);
	}

	private static void assertFalse(final boolean value, final String label) {
		assertTrue(!value, label);
	}

	private static void assertEquals(final int expected, final int actual,
			final String label) {
		if (expected != actual) throw new AssertionError(label);
	}
}
