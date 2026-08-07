package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.impl.projectile.BallProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.CustomProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.combat.ProjectileImpactDecision;
import com.openrsc.server.model.combat.ProjectileImpactLedger;
import com.openrsc.server.model.combat.ProjectileLaunchSnapshot;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;

import java.util.concurrent.atomic.AtomicInteger;

/** Executable A06 evidence for the current delayed-projectile boundary. */
final class CurrentCombatProjectileLifecycleCharacterization {
	private CurrentCombatProjectileLifecycleCharacterization() {
	}

	static void currentImpactPolicy(final CurrentCombatHarness harness) {
		explicitCancellationRetainsLaunchVisual(harness);
		currentSpatialGateInvalidatesImpact(harness);
		participantLifecycleDoesNotCurrentlyCancelImpact(harness);
		terminalParticipantsRetainCurrentImpactPolicy(harness);
		duplicateCallbackSettlesExactlyOnce(harness);
		scriptedAndBenignCallbacksSettleExactlyOnce(harness);
		failedScriptedCallbackCannotReplay(harness);
	}

	private static void explicitCancellationRetainsLaunchVisual(
			final CurrentCombatHarness harness) {
		final Player source = harness.player("pj cancel source", 860, 700);
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 861, 700);
		final int hitsBefore = target.getLevel(Skill.HITS.id());
		final ProjectileEvent event = projectile(
			harness, source, target, 3);
		assertEquals(event.getUUID(), event.getLaunchSnapshot().getEventId(),
			"launch snapshot event identity");
		assertEquals("player-bow-projectile",
			event.getLaunchSnapshot().getFamilyKey(),
			"launch snapshot family identity");
		assertEquals(ProjectileLaunchSnapshot.Kind.DAMAGING,
			event.getLaunchSnapshot().getKind(),
			"launch snapshot classification");
		assertEquals(3, event.getLaunchSnapshot().getProposedDamage(),
			"launch snapshot proposed damage");
		assertEquals(event.getLaunchSnapshot().getLaunchTick() + 1L,
			event.getLaunchSnapshot().getExpectedImpactTick(),
			"one-tick expected impact identity");
		assertTrue(target.getUpdateFlags().hasFiredProjectile(),
			"launch visual must be emitted before delayed cancellation");
		event.setCanceled(true);
		event.action();
		assertEquals(hitsBefore, target.getLevel(Skill.HITS.id()),
			"explicit cancellation must suppress impact");
		assertEquals(ProjectileImpactLedger.State.INVALIDATED,
			event.getProjectileImpactState(),
			"canceled impact terminal state");
		assertEquals(ProjectileImpactDecision.Reason.EXPLICIT_CANCELLATION,
			event.getInitialProjectileImpactDecision().getReason(),
			"canceled impact reason");
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
		assertEquals(871, distant.getLaunchSnapshot()
			.getTargetLaunchLocation().getCoordinate().getX(),
			"target launch location remains immutable after movement");
		assertEquals(890, distant.getInitialProjectileImpactDecision()
			.getTargetImpactLocation().getCoordinate().getX(),
			"impact decision records current target location");
		assertEquals(ProjectileImpactDecision.Reason
				.OUTSIDE_CURRENT_SPATIAL_GATE,
			distant.getInitialProjectileImpactDecision().getReason(),
			"out-of-range impact reason");

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
		assertFalse(event.getLaunchSnapshot().getSourceSnapshot().matches(source),
			"source launch generation remains immutable");
		assertFalse(event.getLaunchSnapshot().getTargetSnapshot().matches(target),
			"target launch generation remains immutable");
		assertEquals(ProjectileImpactLedger.State.SETTLED,
			event.getProjectileImpactState(),
			"lifecycle change retains current settlement behavior");
	}

	private static void terminalParticipantsRetainCurrentImpactPolicy(
			final CurrentCombatHarness harness) {
		final Player deadSource = harness.player(
			"pj dead source", 930, 700);
		final Npc liveTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 931, 700);
		final int liveTargetHits = liveTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent sourceDeathImpact = projectile(
			harness, deadSource, liveTarget, 3);
		deadSource.getSkills().setLevel(Skill.HITS.id(), 0);
		deadSource.advanceCombatLifecycle();
		sourceDeathImpact.action();
		assertEquals(liveTargetHits - 3, liveTarget.getLevel(Skill.HITS.id()),
			"current launched impact persists after source death");

		final Player liveSource = harness.player(
			"pj removed source", 940, 700);
		final Npc removedTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 941, 700);
		final int removedTargetHits = removedTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent targetRemovalImpact = projectile(
			harness, liveSource, removedTarget, 3);
		removedTarget.remove();
		targetRemovalImpact.action();
		assertEquals(removedTargetHits - 3,
			removedTarget.getLevel(Skill.HITS.id()),
			"current launched impact persists after target removal");
	}

	private static void duplicateCallbackSettlesExactlyOnce(
			final CurrentCombatHarness harness) {
		final Player source = harness.player("pj duplicate source", 950, 700);
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 951, 700);
		final int hitsBefore = target.getLevel(Skill.HITS.id());
		final ProjectileEvent event = projectile(
			harness, source, target, 3);
		event.action();
		event.action();
		assertEquals(hitsBefore - 3, target.getLevel(Skill.HITS.id()),
			"duplicate callbacks must settle impact exactly once");
		assertEquals(2, event.getProjectileImpactCallbackCount(),
			"ledger records both the owner and duplicate callback");
		assertEquals(ProjectileImpactLedger.State.SETTLED,
			event.getProjectileImpactState(),
			"duplicate callback cannot change terminal settlement");
		assertEquals(ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED,
			event.getInitialProjectileImpactDecision().getReason(),
			"duplicate callback cannot replace the initial decision");
	}

	private static void scriptedAndBenignCallbacksSettleExactlyOnce(
			final CurrentCombatHarness harness) {
		final Player customSource = harness.player(
			"pj custom source", 960, 700);
		final Npc customTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 961, 700);
		final AtomicInteger customCalls = new AtomicInteger();
		final RecordingCustomProjectile custom =
			new RecordingCustomProjectile(
				harness.world(), customSource, customTarget, customCalls, false);
		customTarget.setLocation(Point.location(980, 700), true);
		custom.action();
		custom.action();
		assertEquals(1, customCalls.get(),
			"scripted projectile callback cardinality");
		assertEquals(ProjectileLaunchSnapshot.Kind.SCRIPTED_EFFECT,
			custom.getLaunchSnapshot().getKind(),
			"scripted projectile classification");
		assertEquals("custom-projectile",
			custom.getLaunchSnapshot().getFamilyKey(),
			"scripted projectile family");
		assertEquals(ProjectileImpactLedger.State.SETTLED,
			custom.getProjectileImpactState(),
			"scripted projectile terminal state");

		final Player ballSource = harness.player(
			"pj ball source", 970, 700);
		final Player ballTarget = harness.player(
			"pj ball target", 971, 700);
		final AtomicInteger ballCalls = new AtomicInteger();
		final RecordingBallProjectile ball = new RecordingBallProjectile(
			harness.world(), ballSource, ballTarget, ballCalls);
		ball.action();
		ball.action();
		assertEquals(1, ballCalls.get(),
			"benign ball callback cardinality");
		assertEquals(ProjectileLaunchSnapshot.Kind.BENIGN_EFFECT,
			ball.getLaunchSnapshot().getKind(),
			"benign projectile classification");
		assertEquals("ball-projectile", ball.getLaunchSnapshot().getFamilyKey(),
			"benign projectile family");
	}

	private static void failedScriptedCallbackCannotReplay(
			final CurrentCombatHarness harness) {
		final Player source = harness.player("pj failed source", 980, 720);
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 981, 720);
		final AtomicInteger calls = new AtomicInteger();
		final RecordingCustomProjectile event = new RecordingCustomProjectile(
			harness.world(), source, target, calls, true);
		boolean failed = false;
		try {
			event.action();
		} catch (final IllegalStateException expected) {
			failed = true;
		}
		assertTrue(failed, "deliberate scripted callback failure must escape");
		event.action();
		assertEquals(1, calls.get(),
			"failed callback cannot replay partial scripted work");
		assertEquals(ProjectileImpactLedger.State.FAILED,
			event.getProjectileImpactState(),
			"failed callback terminal state");
		assertEquals(2, event.getProjectileImpactCallbackCount(),
			"failed callback replay is recorded as duplicate");
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

	private static void assertFalse(final boolean condition,
			final String message) {
		assertTrue(!condition, message);
	}

	private static void assertEquals(final Object expected,
			final Object actual, final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}

	private static void assertEquals(final int expected, final int actual,
			final String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}

	private static final class RecordingCustomProjectile
			extends CustomProjectileEvent {
		private final AtomicInteger calls;
		private final boolean fail;

		private RecordingCustomProjectile(final World world,
				final Mob source, final Mob target, final AtomicInteger calls,
				final boolean fail) {
			super(world, source, target, 1, false);
			this.calls = calls;
			this.fail = fail;
		}

		@Override
		public void doSpell() {
			calls.incrementAndGet();
			if (fail) {
				throw new IllegalStateException(
					"deliberate projectile lifecycle fixture failure");
			}
		}
	}

	private static final class RecordingBallProjectile
			extends BallProjectileEvent {
		private final AtomicInteger calls;

		private RecordingBallProjectile(final World world,
				final Mob source, final Mob target,
				final AtomicInteger calls) {
			super(world, source, target, 3);
			this.calls = calls;
		}

		@Override
		public void doSpell() {
			calls.incrementAndGet();
		}
	}
}
