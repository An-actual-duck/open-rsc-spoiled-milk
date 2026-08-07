package com.openrsc.server.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.projectile.BallProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.BenignProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.CustomProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.combat.ProjectileImpactDecision;
import com.openrsc.server.model.combat.ProjectileImpactLedger;
import com.openrsc.server.model.combat.ProjectileLaunchSnapshot;
import com.openrsc.server.model.combat.ProjectileLaunchSpecification;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import com.openrsc.server.util.rsc.CollisionFlag;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
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

	static void policyDecisionEvidence(final CurrentCombatHarness harness)
			throws Exception {
		participantTerminationEvidence(harness);
		movementAndDomainEvidence(harness);
		collisionRetargetAndProtectionEvidence(harness);
		familyAndSiblingEvidence(harness);
	}

	static void typedProducerLaunches(final CurrentCombatHarness harness) {
		final Player source = harness.player("pj typed source", 990, 740);
		final Npc target = harness.npc(
			NpcId.GREATER_DEMON.id(), 991, 740);
		for (ProjectileLaunchSpecification.Producer producer
				: ProjectileLaunchSpecification.Producer.values()) {
			final int attackType = attackTypeFor(producer);
			final ProjectileLaunchSpecification specification =
				ProjectileLaunchSpecification.builder(producer, 0, attackType)
					.presentation(Projectile.GNOMEBALL, 0, true)
					.duplicationStrategy(DuplicationStrategy.ALLOW_MULTIPLE)
					.build();
			final int sourceVisualsBefore =
				source.getUpdateFlags().getProjectiles().size();
			final int targetVisualsBefore =
				target.getUpdateFlags().getProjectiles().size();
			final ProjectileLaunchSnapshot snapshot;
			if (producer.getKind()
					== ProjectileLaunchSnapshot.Kind.SCRIPTED_EFFECT) {
				final RecordingCustomProjectile event =
					new RecordingCustomProjectile(
						harness.world(), source, target,
						new AtomicInteger(), false, specification);
				snapshot = event.getLaunchSnapshot();
				event.setCanceled(true);
				event.action();
			} else if (producer.getKind()
					== ProjectileLaunchSnapshot.Kind.BENIGN_EFFECT) {
				final RecordingBallProjectile event =
					new RecordingBallProjectile(
						harness.world(), source, target,
						new AtomicInteger(), specification);
				snapshot = event.getLaunchSnapshot();
				event.setCanceled(true);
				event.action();
			} else {
				final ProjectileEvent event = new ProjectileEvent(
					harness.world(), source, target, specification);
				snapshot = event.getLaunchSnapshot();
				event.setCanceled(true);
				event.action();
			}
			assertEquals(producer.getKey(), snapshot.getProducerKey(),
				producer + " producer identity");
			assertEquals(producer.getFamilyKey(), snapshot.getFamilyKey(),
				producer + " family identity");
			assertEquals(producer.getKind(), snapshot.getKind(),
				producer + " launch kind");
			if (producer.getKind()
					== ProjectileLaunchSnapshot.Kind.BENIGN_EFFECT) {
				assertEquals(sourceVisualsBefore + 1,
					source.getUpdateFlags().getProjectiles().size(),
					producer + " benign launch visual owner");
				assertEquals(targetVisualsBefore,
					target.getUpdateFlags().getProjectiles().size(),
					producer + " benign target visual remains unchanged");
				assertEquals(Projectile.GNOMEBALL,
					source.getUpdateFlags().getProjectile().get().getType(),
					producer + " benign launch visual type");
			} else {
				assertEquals(sourceVisualsBefore,
					source.getUpdateFlags().getProjectiles().size(),
					producer + " damaging/scripted source visual unchanged");
				assertEquals(targetVisualsBefore + 1,
					target.getUpdateFlags().getProjectiles().size(),
					producer + " damaging/scripted launch visual owner");
				assertEquals(Projectile.GNOMEBALL,
					target.getUpdateFlags().getProjectile().get().getType(),
					producer + " launch visual type");
			}
		}
	}

	static void specificationAndFacadeParity(
			final CurrentCombatHarness harness) {
		final Player source = harness.player("pj facade source", 995, 750);
		final Npc positionalTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 996, 750);
		final Npc typedTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 997, 750);
		final ProjectileEvent positional = new ProjectileEvent(
			harness.world(), source, positionalTarget, 7, 1, false,
			11, 12, 13, 14, Projectile.FIREBALL, 21, true,
			NpcMagicElement.FIRE, 15, 16, 17, 18);

		final ProjectileLaunchSpecification.Builder builder =
			ProjectileLaunchSpecification.builder(
				ProjectileLaunchSpecification.Producer.PLAYER_MAGIC, 7, 1)
				.chase(false)
				.elementalDebuffs(11, 12, 13, 14)
				.presentation(Projectile.FIREBALL, 21, true)
				.magicElement(NpcMagicElement.FIRE)
				.dualElementProcs(15, 16, 17, 18);
		final ProjectileLaunchSpecification frozen = builder.build();
		builder.chase(true)
			.elementalDebuffs(1, 2, 3, 4)
			.presentation(Projectile.ARROW, 2, false)
			.magicElement(NpcMagicElement.WATER)
			.dualElementProcs(5, 6, 7, 8)
			.bloodSpell(true)
			.dragonBreathDamage(9);
		final ProjectileEvent typed = new ProjectileEvent(
			harness.world(), source, typedTarget, frozen);

		assertSpecificationParity(
			positional.getLaunchSnapshot().getSpecification(),
			typed.getLaunchSnapshot().getSpecification(),
			"rich positional constructor facade");
		assertFalse(frozen.shouldChase(),
			"built specification remains immutable after builder reuse");
		assertEquals(Projectile.FIREBALL, frozen.getProjectileType(),
			"frozen projectile visual after builder reuse");
		assertEquals(NpcMagicElement.FIRE, frozen.getMagicElement(),
			"frozen magic element after builder reuse");
		assertEquals(18, frozen.getSplinterProcChancePercent(),
			"frozen dual-element proc after builder reuse");
		assertFalse(frozen.isBloodSpell(),
			"frozen blood-spell flag after builder reuse");
		assertEquals(0, frozen.getDragonBreathDamage(),
			"frozen dragon-breath damage after builder reuse");
		assertEquals(1, typedTarget.getUpdateFlags().getProjectiles().size(),
			"typed launch publishes one visual before impact");
		assertEquals(0, typedTarget.getUpdateFlags().getHitSplats().size(),
			"typed launch publishes no impact presentation during construction");
		typed.action();
		assertEquals(1, typedTarget.getUpdateFlags().getProjectiles().size(),
			"impact does not replay the launch visual");
		assertEquals(1, typedTarget.getUpdateFlags().getHitSplats().size(),
			"impact presentation follows the launch visual exactly once");

		final Npc bloodPositionalTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 998, 750);
		final Npc bloodTypedTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 999, 750);
		final ProjectileEvent bloodPositional = new ProjectileEvent(
			harness.world(), source, bloodPositionalTarget, 8, 1, true,
			1, 2, 3, 4, Projectile.WATER_BALL, 5, false,
			6, 7, 8, 9, true);
		final ProjectileEvent bloodTyped = new ProjectileEvent(
			harness.world(), source, bloodTypedTarget,
			ProjectileLaunchSpecification.builder(
				ProjectileLaunchSpecification.Producer.PLAYER_MAGIC, 8, 1)
				.chase(true)
				.elementalDebuffs(1, 2, 3, 4)
				.presentation(Projectile.WATER_BALL, 5, false)
				.dualElementProcs(6, 7, 8, 9)
				.bloodSpell(true)
				.build());
		assertSpecificationParity(
			bloodPositional.getLaunchSnapshot().getSpecification(),
			bloodTyped.getLaunchSnapshot().getSpecification(),
			"blood-spell positional constructor facade");

		final Npc dragonPositionalTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 1000, 750);
		final Npc dragonTypedTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 1001, 750);
		final ProjectileEvent dragonPositional = new ProjectileEvent(
			harness.world(), source, dragonPositionalTarget, 9, 2, true,
			ItemId.BRONZE_ARROWS.id(), 1, 2, 3, 4,
			DuplicationStrategy.ALLOW_MULTIPLE, Projectile.ARROW, 5, false, 6);
		final ProjectileEvent dragonTyped = new ProjectileEvent(
			harness.world(), source, dragonTypedTarget,
			ProjectileLaunchSpecification.builder(
				ProjectileLaunchSpecification.Producer.PLAYER_BOW, 9, 2)
				.chase(true)
				.poisonWeaponId(ItemId.BRONZE_ARROWS.id())
				.elementalDebuffs(1, 2, 3, 4)
				.presentation(Projectile.ARROW, 5, false)
				.dragonBreathDamage(6)
				.duplicationStrategy(DuplicationStrategy.ALLOW_MULTIPLE)
				.build());
		assertSpecificationParity(
			dragonPositional.getLaunchSnapshot().getSpecification(),
			dragonTyped.getLaunchSnapshot().getSpecification(),
			"dragon-breath positional constructor facade");

		final RecordingCustomProjectile customPositional =
			new RecordingCustomProjectile(harness.world(), source,
				positionalTarget, new AtomicInteger(), false);
		final RecordingCustomProjectile customTyped =
			new RecordingCustomProjectile(harness.world(), source, typedTarget,
				new AtomicInteger(), false,
				ProjectileLaunchSpecification.builder(
					ProjectileLaunchSpecification.Producer.MAGIC_SCRIPTED_EFFECT,
					0, 1)
					.chase(false)
					.build());
		assertSpecificationParity(
			customPositional.getLaunchSnapshot().getSpecification(),
			customTyped.getLaunchSnapshot().getSpecification(),
			"scripted positional constructor facade");

		final RecordingBallProjectile ballPositional =
			new RecordingBallProjectile(harness.world(), source,
				positionalTarget, new AtomicInteger());
		final RecordingBallProjectile ballTyped =
			new RecordingBallProjectile(harness.world(), source, typedTarget,
				new AtomicInteger(), ProjectileLaunchSpecification.builder(
					ProjectileLaunchSpecification.Producer.GNOME_BALL, 0, -1)
					.presentation(Projectile.GNOMEBALL, 0, true)
					.build());
		assertSpecificationParity(
			ballPositional.getLaunchSnapshot().getSpecification(),
			ballTyped.getLaunchSnapshot().getSpecification(),
			"ball positional constructor facade");
	}

	private static int attackTypeFor(
			final ProjectileLaunchSpecification.Producer producer) {
		switch (producer) {
			case PLAYER_IBAN_MAGIC:
				return 4;
			case CANNON:
				return 5;
			case PLAYER_MAGIC:
			case NPC_MAGIC:
			case SUMMON_MAGIC:
			case MAGIC_SCRIPTED_EFFECT:
			case LEGENDS_HOLY_WATER:
				return 1;
			case COMPATIBILITY:
			case NPC_COMPATIBILITY:
			case SUMMON_COMPATIBILITY:
				return 3;
			case GNOME_BALL:
			case BENIGN_COMPATIBILITY:
				return -1;
			default:
				return 2;
		}
	}

	private static void assertSpecificationParity(
			final ProjectileLaunchSpecification expected,
			final ProjectileLaunchSpecification actual,
			final String message) {
		assertEquals(expected.getProducer(), actual.getProducer(),
			message + " producer");
		assertEquals(expected.getProposedDamage(), actual.getProposedDamage(),
			message + " damage");
		assertEquals(expected.getAttackType(), actual.getAttackType(),
			message + " attack type");
		assertEquals(expected.shouldChase(), actual.shouldChase(),
			message + " chase");
		assertEquals(expected.getPoisonWeaponId(), actual.getPoisonWeaponId(),
			message + " poison weapon");
		assertEquals(expected.getWindAccuracyDebuffPercent(),
			actual.getWindAccuracyDebuffPercent(), message + " wind debuff");
		assertEquals(expected.getWaterMaxHitDebuffPercent(),
			actual.getWaterMaxHitDebuffPercent(), message + " water debuff");
		assertEquals(expected.getEarthAttackSpeedDebuffPercent(),
			actual.getEarthAttackSpeedDebuffPercent(), message + " earth debuff");
		assertEquals(expected.getFireDefenseDebuffPercent(),
			actual.getFireDefenseDebuffPercent(), message + " fire debuff");
		assertEquals(expected.getProjectileType(), actual.getProjectileType(),
			message + " projectile type");
		assertEquals(expected.getImpactEffectType(),
			actual.getImpactEffectType(), message + " impact type");
		assertEquals(expected.shouldShowProjectile(),
			actual.shouldShowProjectile(), message + " visibility");
		assertEquals(expected.getMagicElement(), actual.getMagicElement(),
			message + " magic element");
		assertEquals(expected.getStartleProcChancePercent(),
			actual.getStartleProcChancePercent(), message + " startle proc");
		assertEquals(expected.getAcidPoisonPower(), actual.getAcidPoisonPower(),
			message + " acid proc");
		assertEquals(expected.getFrostbiteProcChancePercent(),
			actual.getFrostbiteProcChancePercent(), message + " frostbite proc");
		assertEquals(expected.getSplinterProcChancePercent(),
			actual.getSplinterProcChancePercent(), message + " splinter proc");
		assertEquals(expected.isBloodSpell(), actual.isBloodSpell(),
			message + " blood spell");
		assertEquals(expected.getDragonBreathDamage(),
			actual.getDragonBreathDamage(), message + " dragon breath");
		assertEquals(expected.getDuplicationStrategy(),
			actual.getDuplicationStrategy(), message + " duplication strategy");
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

	private static void participantTerminationEvidence(
			final CurrentCombatHarness harness) {
		final Player loggedOutSource = harness.player(
			"pj logout source", 810, 780);
		final Npc logoutTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 811, 780);
		final int logoutTargetHits = logoutTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent sourceSessionChanged = projectile(
			harness, loggedOutSource, logoutTarget, 3);
		loggedOutSource.setLoggedIn(false);
		loggedOutSource.sessionId++;
		loggedOutSource.setLoggedIn(true);
		sourceSessionChanged.action();
		assertEquals(logoutTargetHits - 3,
			logoutTarget.getLevel(Skill.HITS.id()),
			"source logout and reconnect currently retain a launched hit");
		assertFalse(sourceSessionChanged.getLaunchSnapshot()
			.getSourceSnapshot().matches(loggedOutSource),
			"source snapshot detects the replacement login session");

		final Npc npcSource = harness.npc(
			NpcId.GREATER_DEMON.id(), 820, 780);
		final Player reconnectedTarget = harness.player(
			"pj reconnect target", 821, 780);
		final int reconnectedHits = reconnectedTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent targetSessionChanged = typedProjectile(
			harness, npcSource, reconnectedTarget, 3,
			ProjectileLaunchSpecification.Producer.NPC_RANGED, 2);
		reconnectedTarget.setLoggedIn(false);
		reconnectedTarget.sessionId++;
		reconnectedTarget.setLoggedIn(true);
		targetSessionChanged.action();
		assertEquals(reconnectedHits - 3,
			reconnectedTarget.getLevel(Skill.HITS.id()),
			"target logout and reconnect currently retain a launched hit");
		assertFalse(targetSessionChanged.getLaunchSnapshot()
			.getTargetSnapshot().matches(reconnectedTarget),
			"target snapshot detects the replacement login session");

		final Player deadTargetSource = harness.player(
			"pj dead target source", 830, 780);
		final Npc deadTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 831, 780);
		final ProjectileEvent targetAlreadyTerminal = projectile(
			harness, deadTargetSource, deadTarget, 3);
		deadTarget.getSkills().setLevel(Skill.HITS.id(), 0);
		deadTarget.advanceCombatLifecycle();
		targetAlreadyTerminal.action();
		assertEquals(ProjectileImpactLedger.State.SETTLED,
			targetAlreadyTerminal.getProjectileImpactState(),
			"a target already at zero Hits currently reaches impact settlement");

		final Player respawnSource = harness.player(
			"pj respawn source", 840, 780);
		final Npc respawnedTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 841, 780);
		final ProjectileEvent staleLifetimeImpact = projectile(
			harness, respawnSource, respawnedTarget, 3);
		final List<GameTickEvent> eventsBeforeRemoval =
			new ArrayList<GameTickEvent>(
				harness.server().getGameEventHandler().getEvents());
		respawnedTarget.remove();
		GameTickEvent respawnEvent = null;
		for (GameTickEvent candidate
				: harness.server().getGameEventHandler().getEvents()) {
			if ("Respawn NPC".equals(candidate.getDescriptor())
					&& !eventsBeforeRemoval.contains(candidate)) {
				respawnEvent = candidate;
			}
		}
		assertTrue(respawnEvent != null,
			"removed NPC schedules its real same-object respawn event");
		respawnEvent.run();
		assertFalse(respawnedTarget.isRemoved(),
			"respawn fixture restores the same NPC object");
		final int respawnedHits = respawnedTarget.getLevel(Skill.HITS.id());
		staleLifetimeImpact.action();
		assertEquals(respawnedHits - 3,
			respawnedTarget.getLevel(Skill.HITS.id()),
			"a stale projectile currently damages the reused NPC lifetime");
		assertFalse(staleLifetimeImpact.getLaunchSnapshot()
			.getTargetSnapshot().matches(respawnedTarget),
			"target snapshot distinguishes the reused NPC lifetime");
	}

	private static void movementAndDomainEvidence(
			final CurrentCombatHarness harness) {
		final Player movedSource = harness.player(
			"pj moved source", 850, 780);
		final Npc stationaryTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 851, 780);
		final int stationaryHits = stationaryTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent sourceMovedOut = projectile(
			harness, movedSource, stationaryTarget, 3);
		movedSource.setLocation(Point.location(870, 780), true);
		sourceMovedOut.action();
		assertEquals(stationaryHits,
			stationaryTarget.getLevel(Skill.HITS.id()),
			"moving only the source outside current range invalidates impact");

		final Player travelingSource = harness.player(
			"pj traveling source", 880, 780);
		final Npc travelingTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 881, 780);
		final int travelingHits = travelingTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent currentPairRange = projectile(
			harness, travelingSource, travelingTarget, 3);
		travelingSource.teleport(910, 780, false);
		travelingTarget.teleport(911, 780);
		currentPairRange.action();
		assertEquals(travelingHits - 3,
			travelingTarget.getLevel(Skill.HITS.id()),
			"participants teleporting together retain current-source range");
		assertEquals(880, currentPairRange.getLaunchSnapshot()
			.getSourceLaunchLocation().getCoordinate().getX(),
			"teleport preserves immutable source launch geography");
		assertEquals(910, currentPairRange.getInitialProjectileImpactDecision()
			.getSourceImpactLocation().getCoordinate().getX(),
			"settlement records the source's current post-teleport geography");

		final boolean layeredBefore = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Player layerSource = harness.player(
				"pj paired layer source", 920, 780);
			final Npc layerTarget = harness.npc(
				NpcId.GREATER_DEMON.id(), 921, 780);
			final int layerHits = layerTarget.getLevel(Skill.HITS.id());
			final ProjectileEvent pairedLayerChange = projectile(
				harness, layerSource, layerTarget, 3);
			harness.openTile(920,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 780);
			harness.openTile(921,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 780);
			layerSource.setLocation(Point.location(920,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 780), true);
			layerTarget.setLocation(Point.location(921,
				LegacyPackedPointAdapter.LEVEL_STRIDE + 780), true);
			pairedLayerChange.action();
			assertEquals(layerHits - 3,
				layerTarget.getLevel(Skill.HITS.id()),
				"participants changing signed level together currently settle");

			final Player unsupportedSpaceSource = harness.player(
				"pj unsupported space", 930, 780);
			boolean rejectedUnsupportedSpace = false;
			try {
				unsupportedSpaceSource.setWorldLocation(new WorldLocation(
					new WorldSpaceId("a06-unconfigured-space"),
					new WorldCoordinate(930, 780, 0)), true);
			} catch (final IllegalArgumentException expected) {
				rejectedUnsupportedSpace = true;
			}
			assertTrue(rejectedUnsupportedSpace,
				"the current global-only package rejects an unconfigured world "
					+ "space before impact policy can observe it");
			assertEquals(WorldSpaceId.GLOBAL,
				unsupportedSpaceSource.getWorldLocation().getWorldSpace(),
				"failed world-space transition preserves the current domain");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				layeredBefore;
		}
	}

	private static void collisionRetargetAndProtectionEvidence(
			final CurrentCombatHarness harness) throws Exception {
		harness.openRectangle(950, 954, 780, 780);
		final Player collisionSource = harness.player(
			"pj collision source", 950, 780);
		final Npc collisionTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 954, 780);
		assertTrue(PathValidation.checkPath(harness.world(),
			collisionSource.getWorldLocation(), collisionTarget.getWorldLocation(),
			false), "collision fixture begins with a clear projectile path");
		final int collisionHits = collisionTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent collisionChanged = projectile(
			harness, collisionSource, collisionTarget, 3);
		harness.world().getTile(952, 780).projectileAllowed = false;
		harness.world().getTile(952, 780).traversalMask =
			(byte) CollisionFlag.FULL_BLOCK;
		assertFalse(PathValidation.checkPath(harness.world(),
			collisionSource.getWorldLocation(), collisionTarget.getWorldLocation(),
			false), "a barrier appearing during flight blocks the path authority");
		collisionChanged.action();
		assertEquals(collisionHits - 3,
			collisionTarget.getLevel(Skill.HITS.id()),
			"current impact ignores a barrier appearing during flight");

		final Player retargetedSource = harness.player(
			"pj retarget source", 960, 780);
		final Npc originalTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 961, 780);
		final Npc replacementTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 962, 780);
		final int originalHits = originalTarget.getLevel(Skill.HITS.id());
		final int replacementHits = replacementTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent originalTargetImpact = projectile(
			harness, retargetedSource, originalTarget, 3);
		retargetedSource.setOpponent(replacementTarget);
		originalTargetImpact.action();
		assertEquals(originalHits - 3,
			originalTarget.getLevel(Skill.HITS.id()),
			"retargeting preserves the target captured by the launched event");
		assertEquals(replacementHits,
			replacementTarget.getLevel(Skill.HITS.id()),
			"retargeting does not redirect an in-flight projectile");

		final boolean pvpBefore = harness.server().getConfig().WANT_PVP;
		final boolean myWorldBefore = harness.server().getConfig().WANT_MYWORLD;
		harness.server().getConfig().WANT_PVP = true;
		harness.server().getConfig().WANT_MYWORLD = false;
		try {
			final Player protectedSource = harness.player(
				"pj protected source", 300, 300);
			final Player protectedTarget = harness.player(
				"pj protected target", 301, 300);
			harness.equip(protectedSource, ItemId.SHORTBOW.id(), 1);
			protectedTarget.getPrayers().setPrayer(
				Prayers.PROTECT_FROM_MISSILES, true, false);
			final int projectilesBefore = countProjectileEvents(harness);
			final RangeEvent protectedLaunch = new RangeEvent(
				harness.world(), protectedSource, 1L, protectedTarget);
			protectedSource.setRangeEvent(protectedLaunch);
			protectedLaunch.run();
			assertEquals(projectilesBefore, countProjectileEvents(harness),
				"classic protection prayer rejects ranged fire before launch");

			protectedTarget.getPrayers().setPrayer(
				Prayers.PROTECT_FROM_MISSILES, false, false);
			final ProjectileEvent alreadyLaunched = typedProjectile(
				harness, protectedSource, protectedTarget, 3,
				ProjectileLaunchSpecification.Producer.PLAYER_BOW, 2);
			final int protectedHits = protectedTarget.getLevel(Skill.HITS.id());
			protectedTarget.getPrayers().setPrayer(
				Prayers.PROTECT_FROM_MISSILES, true, false);
			alreadyLaunched.action();
			assertEquals(protectedHits - 3,
				protectedTarget.getLevel(Skill.HITS.id()),
				"protection activated after launch does not reroll impact");
		} finally {
			harness.server().getConfig().WANT_PVP = pvpBefore;
			harness.server().getConfig().WANT_MYWORLD = myWorldBefore;
		}
	}

	private static void familyAndSiblingEvidence(
			final CurrentCombatHarness harness) throws Exception {
		final Player scriptedSource = harness.player(
			"pj scripted lifecycle", 970, 780);
		final Npc scriptedTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 971, 780);
		final AtomicInteger scriptedCalls = new AtomicInteger();
		final RecordingCustomProjectile scripted = new RecordingCustomProjectile(
			harness.world(), scriptedSource, scriptedTarget, scriptedCalls, false);
		scriptedSource.setLoggedIn(false);
		scriptedTarget.setLocation(Point.location(995, 780), true);
		scripted.action();
		assertEquals(1, scriptedCalls.get(),
			"scripted effects currently ignore logout and long movement");

		final Player ballSource = harness.player(
			"pj ball lifecycle source", 980, 780);
		final Player ballTarget = harness.player(
			"pj ball lifecycle target", 981, 780);
		final AtomicInteger ballCalls = new AtomicInteger();
		final RecordingBallProjectile ball = new RecordingBallProjectile(
			harness.world(), ballSource, ballTarget, ballCalls);
		ballTarget.setLoggedIn(false);
		ballTarget.setLocation(Point.location(1005, 780), true);
		ball.action();
		assertEquals(1, ballCalls.get(),
			"ball effects currently ignore logout and long movement");

		final Constructor<BenignProjectileEvent> benignConstructor =
			BenignProjectileEvent.class.getDeclaredConstructor(
				World.class, Mob.class, Mob.class, int.class, int.class);
		benignConstructor.setAccessible(true);
		final BenignProjectileEvent benign = benignConstructor.newInstance(
			harness.world(), ballSource, ballTarget,
			Integer.valueOf(0), Integer.valueOf(Projectile.GNOMEBALL));
		benign.setCanceled(true);
		benign.action();
		assertEquals(ProjectileImpactLedger.State.SETTLED,
			benign.getProjectileImpactState(),
			"base benign compatibility cleanup ignores its dormant cancel flag");

		final Player siblingSource = harness.player(
			"pj sibling source", 800, 800);
		final Npc invalidSiblingTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 801, 800);
		final Npc settledSiblingTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 802, 800);
		final int invalidSiblingHits =
			invalidSiblingTarget.getLevel(Skill.HITS.id());
		final int settledSiblingHits =
			settledSiblingTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent invalidSibling = typedProjectile(
			harness, siblingSource, invalidSiblingTarget, 3,
			ProjectileLaunchSpecification.Producer.PLAYER_SHURIKEN, 2);
		final ProjectileEvent settledSibling = typedProjectile(
			harness, siblingSource, settledSiblingTarget, 3,
			ProjectileLaunchSpecification.Producer.PLAYER_SHURIKEN, 2);
		invalidSiblingTarget.setLocation(Point.location(825, 800), true);
		invalidSibling.action();
		settledSibling.action();
		assertEquals(invalidSiblingHits,
			invalidSiblingTarget.getLevel(Skill.HITS.id()),
			"one invalid sibling has no impact");
		assertEquals(settledSiblingHits - 3,
			settledSiblingTarget.getLevel(Skill.HITS.id()),
			"one invalid sibling cannot consume another sibling ledger");
		assertEquals(ProjectileImpactLedger.State.INVALIDATED,
			invalidSibling.getProjectileImpactState(),
			"invalid sibling owns its terminal state");
		assertEquals(ProjectileImpactLedger.State.SETTLED,
			settledSibling.getProjectileImpactState(),
			"settled sibling owns its independent terminal state");
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
			final CurrentCombatHarness harness, final Mob source,
			final Mob target, final int damage) {
		return new ProjectileEvent(
			harness.world(), source, target, damage, 2, true,
			DuplicationStrategy.ALLOW_MULTIPLE);
	}

	private static ProjectileEvent typedProjectile(
			final CurrentCombatHarness harness, final Mob source,
			final Mob target, final int damage,
			final ProjectileLaunchSpecification.Producer producer,
			final int attackType) {
		return new ProjectileEvent(harness.world(), source, target,
			ProjectileLaunchSpecification.builder(producer, damage, attackType)
				.chase(true)
				.presentation(attackType, 0, true)
				.duplicationStrategy(DuplicationStrategy.ALLOW_MULTIPLE)
				.build());
	}

	private static int countProjectileEvents(
			final CurrentCombatHarness harness) {
		int count = 0;
		for (GameTickEvent event
				: harness.server().getGameEventHandler().getEvents()) {
			if (event instanceof ProjectileEvent) {
				count++;
			}
		}
		return count;
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

		private RecordingCustomProjectile(final World world,
				final Mob source, final Mob target, final AtomicInteger calls,
				final boolean fail,
				final ProjectileLaunchSpecification specification) {
			super(world, source, target, specification);
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

		private RecordingBallProjectile(final World world,
				final Mob source, final Mob target,
				final AtomicInteger calls,
				final ProjectileLaunchSpecification specification) {
			super(world, source, target, specification);
			this.calls = calls;
		}

		@Override
		public void doSpell() {
			calls.incrementAndGet();
		}
	}
}
