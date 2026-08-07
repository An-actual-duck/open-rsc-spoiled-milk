package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;

/** Executable A06 evidence for the current delayed-projectile boundary. */
final class CurrentCombatProjectileLifecycleCharacterization {
	private CurrentCombatProjectileLifecycleCharacterization() {
	}

	static void currentImpactPolicy(final CurrentCombatHarness harness) {
		explicitCancellationRetainsLaunchVisual(harness);
		currentSpatialGateInvalidatesImpact(harness);
		participantLifecycleDoesNotCurrentlyCancelImpact(harness);
		duplicateCallbackCurrentlyReplaysImpact(harness);
	}

	private static void explicitCancellationRetainsLaunchVisual(
			final CurrentCombatHarness harness) {
		final Player source = harness.player("pj cancel source", 860, 700);
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 861, 700);
		final int hitsBefore = target.getLevel(Skill.HITS.id());
		final ProjectileEvent event = projectile(
			harness, source, target, 3);
		assertTrue(target.getUpdateFlags().hasFiredProjectile(),
			"launch visual must be emitted before delayed cancellation");
		event.setCanceled(true);
		event.action();
		assertEquals(hitsBefore, target.getLevel(Skill.HITS.id()),
			"explicit cancellation must suppress impact");
	}

	private static void currentSpatialGateInvalidatesImpact(
			final CurrentCombatHarness harness) {
		final Player distantSource = harness.player(
			"pj range source", 870, 700);
		final Npc distantTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 871, 700);
		final int distantHits = distantTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent distant = projectile(
			harness, distantSource, distantTarget, 3);
		distantTarget.setLocation(Point.location(890, 700), true);
		distant.action();
		assertEquals(distantHits, distantTarget.getLevel(Skill.HITS.id()),
			"movement outside the current 15-tile gate must suppress impact");

		final boolean layeredBefore = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Player layerSource = harness.player(
				"pj layer source", 900, 700);
			final Npc layerTarget = harness.npc(
				NpcId.GREATER_DEMON.id(), 901, 700);
			final int layerHits = layerTarget.getLevel(Skill.HITS.id());
			final ProjectileEvent crossLayer = projectile(
				harness, layerSource, layerTarget, 3);
			layerTarget.setLocation(Point.location(
				901, LegacyPackedPointAdapter.LEVEL_STRIDE + 700), true);
			crossLayer.action();
			assertEquals(layerHits, layerTarget.getLevel(Skill.HITS.id()),
				"a signed-level change must suppress impact through withinRange");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				layeredBefore;
		}
	}

	private static void participantLifecycleDoesNotCurrentlyCancelImpact(
			final CurrentCombatHarness harness) {
		final Player source = harness.player("pj lifecycle source", 910, 700);
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 911, 700);
		final int hitsBefore = target.getLevel(Skill.HITS.id());
		final ProjectileEvent event = projectile(
			harness, source, target, 3);
		source.advanceCombatLifecycle();
		target.advanceCombatLifecycle();
		event.action();
		assertEquals(hitsBefore - 3, target.getLevel(Skill.HITS.id()),
			"current in-flight impacts ignore participant lifecycle changes");
	}

	private static void duplicateCallbackCurrentlyReplaysImpact(
			final CurrentCombatHarness harness) {
		final Player source = harness.player("pj duplicate source", 920, 700);
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 921, 700);
		final int hitsBefore = target.getLevel(Skill.HITS.id());
		final ProjectileEvent event = projectile(
			harness, source, target, 3);
		event.action();
		event.action();
		assertEquals(hitsBefore - 6, target.getLevel(Skill.HITS.id()),
			"pre-A06 duplicate callbacks replay the complete impact");
	}

	private static ProjectileEvent projectile(
			final CurrentCombatHarness harness, final Player source,
			final Npc target, final int damage) {
		return new ProjectileEvent(
			harness.world(), source, target, damage, 2, true,
			DuplicationStrategy.ALLOW_MULTIPLE);
	}

	private static void assertTrue(final boolean condition,
			final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertEquals(final int expected, final int actual,
			final String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}
}
