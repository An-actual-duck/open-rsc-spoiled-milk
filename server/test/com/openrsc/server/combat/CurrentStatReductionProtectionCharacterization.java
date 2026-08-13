package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.Functions;

import java.util.concurrent.TimeUnit;

/** Executable coverage for temporary stat-reduction protection boundaries. */
final class CurrentStatReductionProtectionCharacterization {
	private static final String EXPIRES_KEY = "stat_reduction_protection_expires_at";
	private static final long INTENDED_DURATION_MS = TimeUnit.MINUTES.toMillis(10);

	private CurrentStatReductionProtectionCharacterization() {
	}

	static void protectedDrainPathsAndExpiry(final CurrentCombatHarness harness) {
		final Player player = harness.player("restore_guard", 214, 620);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.RANGED.id(), 40, 40, false);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.PRAYER.id(), 40, 40, false);

		final long beforeActivation = System.currentTimeMillis();
		player.setStatReductionProtection(INTENDED_DURATION_MS);
		final long afterActivation = System.currentTimeMillis();
		final long expiresAt = player.getAttribute(EXPIRES_KEY, 0L);
		assertTrue(expiresAt >= beforeActivation + INTENDED_DURATION_MS,
			"protection must retain the intended ten-minute duration");
		assertTrue(expiresAt <= afterActivation + INTENDED_DURATION_MS,
			"protection duration must not exceed ten minutes without equipment bonuses");

		Functions.substat(player, Skill.RANGED.id(), 20, 0);
		assertEquals(40, player.getSkills().getLevel(Skill.RANGED.id()),
			"shared substat path must respect active protection");
		assertFalse(player.getSkills().subtractLevelFromStatReduction(
			Skill.PRAYER.id(), 10), "active protection must reject hostile Worship drains");
		assertEquals(40, player.getSkills().getLevel(Skill.PRAYER.id()),
			"protected Worship level");

		player.getSkills().setLevel(Skill.PRAYER.id(), 35, true);
		assertEquals(35, player.getSkills().getLevel(Skill.PRAYER.id()),
			"ordinary Worship consumption must bypass stat-reduction protection");
		player.getSkills().setLevel(Skill.RANGED.id(), 39, true, true);
		assertEquals(39, player.getSkills().getLevel(Skill.RANGED.id()),
			"restoration-event boost decay must bypass stat-reduction protection");

		final int hitsBefore = player.getSkills().getLevel(Skill.HITS.id());
		assertTrue(player.getSkills().subtractLevelFromStatReduction(
			Skill.HITS.id(), 1), "Hits damage must remain outside stat protection");
		assertEquals(hitsBefore - 1, player.getSkills().getLevel(Skill.HITS.id()),
			"unprotected Hits level");

		player.setAttribute(EXPIRES_KEY, System.currentTimeMillis() - 1L);
		assertFalse(player.hasStatReductionProtection(),
			"expired protection must become inactive");
		assertTrue(player.getSkills().setLevelFromStatReduction(
			Skill.RANGED.id(), 20), "stat drains must resume after expiry");
		assertEquals(20, player.getSkills().getLevel(Skill.RANGED.id()),
			"post-expiry ranged level");
		assertEquals(0L, player.getAttribute(EXPIRES_KEY, 0L),
			"expiry check must clear stale protection state");
	}

	private static void assertTrue(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(final boolean condition, final String message) {
		assertTrue(!condition, message);
	}

	private static void assertEquals(final int expected, final int actual,
			final String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}

	private static void assertEquals(final long expected, final long actual,
			final String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}
}
