package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.combat.ElementalSwordProc;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.update.CombatEffect;

/** Executable ordering evidence for bounded A07.5L Elemental Sword. */
final class CurrentCombatElementalSwordProcCharacterization {
	private CurrentCombatElementalSwordProcCharacterization() { }
	static void elementalSwordPolicies(final CurrentCombatHarness harness) {
		final Npc source = harness.npc(NpcId.GREATER_DEMON.id(), 900, 700);
		final Npc target = harness.npc(NpcId.GREATER_DEMON.id(), 901, 700);
		target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 20, 20, false);
		final int[] order = {0};
		assertTrue(ElementalSwordProc.tryApply(source, target, () -> CombatEffect.FIRE_SWORD,
			() -> true, effect -> order[0] = 1, () -> 4, damage -> order[0] = 2), "fire proc");
		assertEquals(2, order[0], "damage follows debuff");
		assertEquals(CombatEffect.FIRE_SWORD, target.getUpdateFlags().getCombatEffect().get().getEffectType(), "fire visual");
		assertFalse(ElementalSwordProc.tryApply(source, target, () -> CombatEffect.NONE,
			() -> { throw new AssertionError(); }, effect -> { }, () -> 1, damage -> { }), "no weapon effect");
		assertFalse(ElementalSwordProc.tryApply(source, target, () -> CombatEffect.EARTH_SWORD,
			() -> false, effect -> { }, () -> 1, damage -> { }), "failed chance");
	}
	private static void assertTrue(boolean value, String label) { if (!value) throw new AssertionError(label); }
	private static void assertFalse(boolean value, String label) { assertTrue(!value, label); }
	private static void assertEquals(int expected, int actual, String label) { if (expected != actual) throw new AssertionError(label); }
}
