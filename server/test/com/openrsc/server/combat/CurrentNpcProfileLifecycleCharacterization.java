package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcCombatProfile;
import com.openrsc.server.model.entity.npc.NpcMagicAttack;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;

/** Runtime parity specification for A10's resolved profile and lifecycle seams. */
final class CurrentNpcProfileLifecycleCharacterization {
	private CurrentNpcProfileLifecycleCharacterization() {
	}

	static void resolvedMagicPayloadUsesOneSelectedElement(
			final CurrentCombatHarness harness) {
		final Npc battleMage = harness.npc(NpcId.BATTLE_MAGE_GUTHIX.id(),
			430, 430);
		harness.random().reset(0xA10C0B4L);
		harness.random().scriptInts(Integer.valueOf(3));
		final NpcCombatProfile profile = NpcCombatProfile.resolve(battleMage);
		assertEquals(NpcMagicElement.FIRE,
			profile.selectMagicAttack().getElement(),
			"first selected element remains the existing scripted draw");

		harness.random().reset(0xA10C0B4L);
		harness.random().scriptInts(Integer.valueOf(3));
		final NpcMagicAttack attack = NpcCombatProfile.resolve(battleMage)
			.selectMagicAttack();
		assertEquals(NpcMagicElement.FIRE, attack.getElement(),
			"typed magic attack retains the selected element");
		assertTrue(attack.getProjectileVisual() > 0,
			"typed magic attack retains an existing projectile visual");
		assertTrue(attack.getImpactEffect() >= 0,
			"typed magic attack retains an existing impact policy");
		attack.getStartleProcChancePercent();
		attack.getAcidPoisonPower();
		attack.getFireDefenseDebuffPercent();
		attack.getSplinterProcChancePercent();
		assertTrue(harness.random().describeState().endsWith(
			"draws=[int(4)=3]"),
			"dependent payload reads must not reroll the selected element");
	}

	static void npcBehaviorLifecycleBoundaries(
			final CurrentCombatHarness harness) {
		final Player logoutTarget = harness.player("a10 logout", 440, 440);
		final Npc logoutNpc = harness.npc(NpcId.GREATER_DEMON.id(), 441, 440);
		logoutNpc.setChasing(logoutTarget);
		harness.logout(logoutTarget);
		logoutNpc.getBehavior().tick(true);
		assertNull(logoutNpc.getBehavior().getChaseTarget(),
			"removed target clears the current NPC chase lifecycle");

		final boolean priorLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Player upperTarget = harness.player("a10 upper", 444,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 440);
			final Npc surfaceNpc = harness.npc(NpcId.GREATER_DEMON.id(),
				445, 440);
			surfaceNpc.setChasing(upperTarget);
			surfaceNpc.getBehavior().tick(true);
			assertNull(surfaceNpc.getBehavior().getChaseTarget(),
				"cross-level target clears chase rather than crossing world domains");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				priorLayered;
		}

		final boolean priorPathfinding = harness.server().getConfig()
			.WANT_IMPROVED_PATHFINDING;
		harness.server().getConfig().WANT_IMPROVED_PATHFINDING = true;
		try {
			final Player distantTarget = harness.player("a10 distant", 470, 470);
			final Npc leashedNpc = harness.npc(NpcId.GREATER_DEMON.id(),
				450, 450);
			leashedNpc.getSkills().setLevel(Skill.ATTACK.id(), 1);
			leashedNpc.applyPoison(40, 40);
			leashedNpc.setChasing(distantTarget);
			leashedNpc.getBehavior().tick(true);
			assertNull(leashedNpc.getBehavior().getChaseTarget(),
				"out-of-bounds target ends chase at the existing leash boundary");
			assertEquals(leashedNpc.getDef().getAtt(),
				leashedNpc.getSkills().getLevel(Skill.ATTACK.id()),
				"improved-path leash retains existing skill normalization");
			assertEquals(0, leashedNpc.getCurrentPoisonPower(),
				"improved-path leash retains existing poison cleanup");
		} finally {
			harness.server().getConfig().WANT_IMPROVED_PATHFINDING =
				priorPathfinding;
		}
	}

	private static void assertTrue(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertNull(final Object value, final String message) {
		assertTrue(value == null, message + ": expected null, got " + value);
	}

	private static void assertEquals(final Object expected, final Object actual,
			final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}
}
