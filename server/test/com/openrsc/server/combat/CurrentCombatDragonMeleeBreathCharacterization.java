package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.combat.DragonMeleeBreathFollowup;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.update.CombatEffect;
import com.openrsc.server.runtime.GameRandom;

/** Executable contract for bounded A07.5K Dragon melee breath. */
final class CurrentCombatDragonMeleeBreathCharacterization {
	private CurrentCombatDragonMeleeBreathCharacterization() { }

	static void dragonMeleeBreathPolicies(final CurrentCombatHarness harness) {
		final Npc source = harness.npc(NpcId.GREATER_DEMON.id(), 900, 700);
		final Npc target = harness.npc(NpcId.GREATER_DEMON.id(), 901, 700);
		target.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(), 20, 20, false);
		final int[] payload = {-1};
		assertTrue(DragonMeleeBreathFollowup.tryApply(source, target,
			new FixedRandom(0), () -> 6, damage -> payload[0] = damage), "positive breath");
		assertEquals(6, payload[0], "breath callback payload");
		assertEquals(CombatEffect.DRAGON_WEAPON_BREATH,
			target.getUpdateFlags().getCombatEffect().get().getEffectType(), "first visual");
		assertFalse(DragonMeleeBreathFollowup.tryApply(source, target,
			new FixedRandom(1), () -> 0, damage -> { throw new AssertionError(); }), "zero roll");
		target.getSkills().setLevel(Skill.HITS.id(), 0);
		assertFalse(DragonMeleeBreathFollowup.tryApply(source, target,
			new FixedRandom(1), () -> { throw new AssertionError(); }, damage -> { }), "dead target");
	}

	private static void assertTrue(boolean value, String label) { if (!value) throw new AssertionError(label); }
	private static void assertFalse(boolean value, String label) { assertTrue(!value, label); }
	private static void assertEquals(int expected, int actual, String label) { if (expected != actual) throw new AssertionError(label); }

	private static final class FixedRandom implements GameRandom {
		private final int value;
		private FixedRandom(int value) { this.value = value; }
		public int nextInt(int bound) { return value; }
		public double nextDouble() { throw new AssertionError(); }
		public String describeState() { return "fixed"; }
	}
}
