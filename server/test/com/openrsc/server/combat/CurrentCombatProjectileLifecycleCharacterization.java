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
import com.openrsc.server.event.rsc.impl.projectile.RangeEventNpc;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.CombatProjectileCollision;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.combat.ProjectileImpactDecision;
import com.openrsc.server.model.combat.ProjectileImpactLedger;
import com.openrsc.server.model.combat.ProjectileImpactPolicy;
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
		participantLifecycleInvalidatesImpact(harness);
		terminalParticipantPolicy(harness);
		duplicateCallbackSettlesExactlyOnce(harness);
		scriptedAndBenignCallbacksSettleExactlyOnce(harness);
		failedScriptedCallbackCannotReplay(harness);
	}

	static void policyDecisionEvidence(final CurrentCombatHarness harness)
			throws Exception {
		participantTerminationEvidence(harness);
		sourceFamilyLifetimeEvidence(harness);
		movementAndDomainEvidence(harness);
		authoredFenceEndToEndEvidence(harness);
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
			assertEquals(producer.getImpactPolicy(),
				snapshot.getSpecification().getImpactPolicy(),
				producer + " impact policy identity");
			assertEquals(expectedImpactPolicy(producer),
				producer.getImpactPolicy(),
				producer + " approved impact policy mapping");
			assertEquals(expectedCollision(producer),
				producer.getImpactPolicy().getCollision(),
				producer + " approved projectile-cover allegiance");
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
		openCombatProjectileRectangle(harness, 995, 1001, 750, 750);
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

	private static ProjectileImpactPolicy expectedImpactPolicy(
			final ProjectileLaunchSpecification.Producer producer) {
		switch (producer) {
			case PLAYER_BOW:
			case PLAYER_THROWN:
			case PLAYER_SHURIKEN:
			case PLAYER_MAGIC:
			case PLAYER_IBAN_MAGIC:
			case CANNON:
				return ProjectileImpactPolicy.PLAYER_DAMAGE;
			case NPC_RANGED:
			case NPC_MAGIC:
			case NPC_COMPATIBILITY:
			case LEGACY_NPC_RANGED:
				return ProjectileImpactPolicy.NPC_DAMAGE;
			case SUMMON_RANGED:
			case SUMMON_MAGIC:
			case SUMMON_COMPATIBILITY:
				return ProjectileImpactPolicy.SUMMON_DAMAGE;
			case ADMIN_DEBUG:
				return ProjectileImpactPolicy.ADMIN_DAMAGE;
			case COMPATIBILITY:
				return ProjectileImpactPolicy
					.POSITIONAL_COMPATIBILITY_DAMAGE;
			case MAGIC_SCRIPTED_EFFECT:
				return ProjectileImpactPolicy.SCRIPTED_MAGIC;
			case LEGENDS_HOLY_WATER:
				return ProjectileImpactPolicy.LEGENDS_HOLY_WATER;
			case GNOME_BALL:
				return ProjectileImpactPolicy.GNOME_BALL;
			case BENIGN_COMPATIBILITY:
				return ProjectileImpactPolicy.BENIGN_COMPATIBILITY_CLEANUP;
			default:
				throw new IllegalStateException(
					"Unhandled projectile producer: " + producer);
		}
	}

	private static ProjectileImpactPolicy.Collision expectedCollision(
			final ProjectileLaunchSpecification.Producer producer) {
		switch (producer) {
			case NPC_RANGED:
			case NPC_MAGIC:
			case NPC_COMPATIBILITY:
			case LEGACY_NPC_RANGED:
				return ProjectileImpactPolicy.Collision.ENEMY_PROJECTILE;
			case COMPATIBILITY:
			case GNOME_BALL:
			case BENIGN_COMPATIBILITY:
				return ProjectileImpactPolicy.Collision.NONE;
			default:
				return ProjectileImpactPolicy.Collision.PLAYER_ALLIED_PROJECTILE;
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
		assertEquals(expected.getImpactPolicy(), actual.getImpactPolicy(),
			message + " impact policy");
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
				.OUTSIDE_LAUNCH_ORIGIN_RANGE,
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
				"a signed-level change must suppress impact");
			assertEquals(ProjectileImpactDecision.Reason.LAUNCH_DOMAIN_DEPARTURE,
				crossLayer.getInitialProjectileImpactDecision().getReason(),
				"signed-level invalidation reason");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				layeredBefore;
		}
	}

	private static void participantLifecycleInvalidatesImpact(
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
		assertEquals(hitsBefore, target.getLevel(Skill.HITS.id()),
			"a replacement target lifetime must reject an in-flight impact");
		assertFalse(event.getLaunchSnapshot().getSourceSnapshot().matches(source),
			"source launch generation remains immutable");
		assertFalse(event.getLaunchSnapshot().getTargetSnapshot().matches(target),
			"target launch generation remains immutable");
		assertEquals(ProjectileImpactLedger.State.INVALIDATED,
			event.getProjectileImpactState(),
			"lifecycle change invalidates settlement");
		assertEquals(ProjectileImpactDecision.Reason
				.TARGET_IDENTITY_SESSION_OR_LIFETIME_CHANGED,
			event.getInitialProjectileImpactDecision().getReason(),
			"target replacement lifetime reason");
	}

	private static void terminalParticipantPolicy(
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
		assertEquals(removedTargetHits,
			removedTarget.getLevel(Skill.HITS.id()),
			"a launched impact cannot hit a removed target");
		assertEquals(ProjectileImpactDecision.Reason
				.TARGET_TERMINAL_OR_UNREGISTERED,
			targetRemovalImpact.getInitialProjectileImpactDecision().getReason(),
			"removed target invalidation reason");
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
		assertEquals(logoutTargetHits,
			logoutTarget.getLevel(Skill.HITS.id()),
			"source logout and reconnect invalidate a launched hit");
		assertFalse(sourceSessionChanged.getLaunchSnapshot()
			.getSourceSnapshot().matches(loggedOutSource),
			"source snapshot detects the replacement login session");
		assertEquals(ProjectileImpactDecision.Reason
				.SOURCE_IDENTITY_SESSION_OR_LIFETIME_CHANGED,
			sourceSessionChanged.getInitialProjectileImpactDecision().getReason(),
			"source replacement session reason");

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
		assertEquals(reconnectedHits,
			reconnectedTarget.getLevel(Skill.HITS.id()),
			"target logout and reconnect invalidate a launched hit");
		assertFalse(targetSessionChanged.getLaunchSnapshot()
			.getTargetSnapshot().matches(reconnectedTarget),
			"target snapshot detects the replacement login session");
		assertEquals(ProjectileImpactDecision.Reason
				.TARGET_IDENTITY_SESSION_OR_LIFETIME_CHANGED,
			targetSessionChanged.getInitialProjectileImpactDecision().getReason(),
			"target replacement session reason");

		final Player deadTargetSource = harness.player(
			"pj dead target source", 830, 780);
		final Npc deadTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 831, 780);
		final ProjectileEvent targetAlreadyTerminal = projectile(
			harness, deadTargetSource, deadTarget, 3);
		deadTarget.getSkills().setLevel(Skill.HITS.id(), 0);
		deadTarget.advanceCombatLifecycle();
		targetAlreadyTerminal.action();
		assertEquals(ProjectileImpactLedger.State.INVALIDATED,
			targetAlreadyTerminal.getProjectileImpactState(),
			"a target already at zero Hits cannot reach impact settlement");
		assertEquals(ProjectileImpactDecision.Reason
				.TARGET_TERMINAL_OR_UNREGISTERED,
			targetAlreadyTerminal.getInitialProjectileImpactDecision().getReason(),
			"zero-Hits target reason");

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
		assertEquals(respawnedHits,
			respawnedTarget.getLevel(Skill.HITS.id()),
			"a stale projectile cannot damage the reused NPC lifetime");
		assertFalse(staleLifetimeImpact.getLaunchSnapshot()
			.getTargetSnapshot().matches(respawnedTarget),
			"target snapshot distinguishes the reused NPC lifetime");
		assertEquals(ProjectileImpactDecision.Reason
				.TARGET_IDENTITY_SESSION_OR_LIFETIME_CHANGED,
			staleLifetimeImpact.getInitialProjectileImpactDecision().getReason(),
			"respawn-reused target reason");
	}

	private static void sourceFamilyLifetimeEvidence(
			final CurrentCombatHarness harness) {
		openCombatProjectileRectangle(harness, 740, 751, 780, 780);
		final Npc deadNpcSource = harness.npc(
			NpcId.GREATER_DEMON.id(), 740, 780);
		final Npc deadNpcTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 741, 780);
		final int deadNpcTargetHits = deadNpcTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent oldNpcMissile = typedProjectile(
			harness, deadNpcSource, deadNpcTarget, 3,
			ProjectileLaunchSpecification.Producer.NPC_RANGED, 2);
		deadNpcSource.remove();
		oldNpcMissile.action();
		assertEquals(ProjectileImpactLedger.State.SETTLED,
			oldNpcMissile.getProjectileImpactState(),
			"an emitted NPC projectile reaches settlement after source death ("
				+ oldNpcMissile.getInitialProjectileImpactDecision().getReason()
				+ ")");
		assertEquals(deadNpcTargetHits - 3,
			deadNpcTarget.getLevel(Skill.HITS.id()),
			"an emitted NPC projectile may survive the old source's death");

		final Npc reusedNpcSource = harness.npc(
			NpcId.GREATER_DEMON.id(), 750, 780);
		final Npc reusedNpcTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 751, 780);
		final int reusedNpcTargetHits =
			reusedNpcTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent reusedNpcMissile = typedProjectile(
			harness, reusedNpcSource, reusedNpcTarget, 3,
			ProjectileLaunchSpecification.Producer.NPC_MAGIC, 1);
		final List<GameTickEvent> eventsBeforeRemoval =
			new ArrayList<GameTickEvent>(
				harness.server().getGameEventHandler().getEvents());
		reusedNpcSource.remove();
		final GameTickEvent respawn = findNewRespawnEvent(
			harness, eventsBeforeRemoval);
		assertTrue(respawn != null,
			"source reuse fixture captures the real NPC respawn callback");
		respawn.run();
		reusedNpcMissile.action();
		assertEquals(reusedNpcTargetHits,
			reusedNpcTarget.getLevel(Skill.HITS.id()),
			"an NPC projectile cannot cross source respawn reuse");
		assertEquals(ProjectileImpactDecision.Reason
				.SOURCE_IDENTITY_SESSION_OR_LIFETIME_CHANGED,
			reusedNpcMissile.getInitialProjectileImpactDecision().getReason(),
			"respawn-reused source reason");

		final Player summonOwner = harness.player(
			"pj summon owner", 760, 780);
		final Npc removedSummon = harness.npc(
			NpcId.GREATER_DEMON.id(), 761, 780);
		removedSummon.relatedMob = summonOwner;
		final Npc summonTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 762, 780);
		final int summonTargetHits = summonTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent removedSummonMissile = typedProjectile(
			harness, removedSummon, summonTarget, 3,
			ProjectileLaunchSpecification.Producer.SUMMON_RANGED, 2);
		removedSummon.remove();
		removedSummonMissile.action();
		assertEquals(summonTargetHits,
			summonTarget.getLevel(Skill.HITS.id()),
			"a removed summon cannot settle its projectile");
		assertEquals(ProjectileImpactDecision.Reason
				.SOURCE_TERMINAL_OR_UNREGISTERED,
			removedSummonMissile.getInitialProjectileImpactDecision().getReason(),
			"removed summon source reason");

		final Player replacementOwner = harness.player(
			"pj replacement summon owner", 770, 780);
		final Npc ownedSummon = harness.npc(
			NpcId.GREATER_DEMON.id(), 771, 780);
		ownedSummon.relatedMob = replacementOwner;
		final Npc ownerTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 772, 780);
		final int ownerTargetHits = ownerTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent ownerSessionMissile = typedProjectile(
			harness, ownedSummon, ownerTarget, 3,
			ProjectileLaunchSpecification.Producer.SUMMON_MAGIC, 1);
		replacementOwner.setLoggedIn(false);
		replacementOwner.sessionId++;
		replacementOwner.setLoggedIn(true);
		ownerSessionMissile.action();
		assertEquals(ownerTargetHits, ownerTarget.getLevel(Skill.HITS.id()),
			"a summon projectile cannot cross its owner's login session");
		assertEquals(ProjectileImpactDecision.Reason
				.SOURCE_IDENTITY_SESSION_OR_LIFETIME_CHANGED,
			ownerSessionMissile.getInitialProjectileImpactDecision().getReason(),
			"replacement summon-owner session reason");

		final Player continuingOwner = harness.player(
			"pj continuing summon owner", 775, 790);
		final Npc continuingSummon = harness.npc(
			NpcId.GREATER_DEMON.id(), 776, 790);
		continuingSummon.relatedMob = continuingOwner;
		final Npc continuingTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 777, 790);
		final int continuingHits = continuingTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent continuingMissile = typedProjectile(
			harness, continuingSummon, continuingTarget, 3,
			ProjectileLaunchSpecification.Producer.SUMMON_MAGIC, 1);
		continuingOwner.advanceCombatLifecycle();
		continuingMissile.action();
		assertEquals(continuingHits - 3,
			continuingTarget.getLevel(Skill.HITS.id()),
			"summon ownership follows login session, not owner combat lifecycle");

		final Npc adminSource = harness.npc(
			NpcId.GREATER_DEMON.id(), 780, 780);
		final Player adminTarget = harness.player(
			"pj admin target", 781, 780);
		final int adminTargetHits = adminTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent adminMissile = typedProjectile(
			harness, adminSource, adminTarget, 3,
			ProjectileLaunchSpecification.Producer.ADMIN_DEBUG, 2);
		adminSource.remove();
		adminMissile.action();
		assertEquals(adminTargetHits, adminTarget.getLevel(Skill.HITS.id()),
			"an admin projectile requires its exact live source");
		assertEquals(ProjectileImpactDecision.Reason
				.SOURCE_TERMINAL_OR_UNREGISTERED,
			adminMissile.getInitialProjectileImpactDecision().getReason(),
			"terminal admin source reason");
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
		assertEquals(stationaryHits - 3,
			stationaryTarget.getLevel(Skill.HITS.id()),
			"source-only movement does not move the frozen range origin");

		openCombatProjectileRectangle(harness, 860, 866, 790, 790);
		final Player shortMoveSource = harness.player(
			"pj short move source", 860, 790);
		final Npc shortMoveTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 861, 790);
		final int shortMoveHits = shortMoveTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent shortMove = projectile(
			harness, shortMoveSource, shortMoveTarget, 3);
		shortMoveTarget.teleport(866, 790);
		shortMove.action();
		assertEquals(shortMoveHits - 3,
			shortMoveTarget.getLevel(Skill.HITS.id()),
			"same-domain movement inside the launch-origin ceiling remains valid");

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
		assertEquals(travelingHits,
			travelingTarget.getLevel(Skill.HITS.id()),
			"paired long movement cannot carry a projectile from launch");
		assertEquals(880, currentPairRange.getLaunchSnapshot()
			.getSourceLaunchLocation().getCoordinate().getX(),
			"teleport preserves immutable source launch geography");
		assertEquals(910, currentPairRange.getInitialProjectileImpactDecision()
			.getSourceImpactLocation().getCoordinate().getX(),
			"invalidation records the source's current post-teleport geography");
		assertEquals(ProjectileImpactDecision.Reason
				.OUTSIDE_LAUNCH_ORIGIN_RANGE,
			currentPairRange.getInitialProjectileImpactDecision().getReason(),
			"paired long-movement reason");

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
			assertEquals(layerHits,
				layerTarget.getLevel(Skill.HITS.id()),
				"participants changing signed level together invalidate");
			assertEquals(ProjectileImpactDecision.Reason
					.LAUNCH_DOMAIN_DEPARTURE,
				pairedLayerChange.getInitialProjectileImpactDecision().getReason(),
				"paired signed-level transition reason");

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
		harness.world().getTile(952, 780)
			.addCombatProjectileCollision(CollisionFlag.FULL_BLOCK);
		assertFalse(PathValidation.checkCombatProjectilePath(harness.world(),
			collisionSource.getWorldLocation(), collisionTarget.getWorldLocation()),
			"hard cover appearing during flight blocks the path authority");
		collisionChanged.action();
		assertEquals(collisionHits,
			collisionTarget.getLevel(Skill.HITS.id()),
			"player impact rechecks combat-projectile hard cover");
		assertEquals(ProjectileImpactDecision.Reason.IMPACT_PATH_BLOCKED,
			collisionChanged.getInitialProjectileImpactDecision().getReason(),
			"player combat-projectile collision reason");

		final Npc compatibilityTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 954, 780);
		final int compatibilityHits =
			compatibilityTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent positionalCompatibility = typedProjectile(
			harness, collisionSource, compatibilityTarget, 3,
			ProjectileLaunchSpecification.Producer.COMPATIBILITY, 3);
		positionalCompatibility.action();
		assertEquals(compatibilityHits - 3,
			compatibilityTarget.getLevel(Skill.HITS.id()),
			"positional compatibility retains its explicit no-recheck semantic");

		openCombatProjectileRectangle(harness, 700, 704, 780, 780);
		final Npc hostileSource = harness.npc(
			NpcId.GREATER_DEMON.id(), 700, 780);
		final Player hostileTarget = harness.player(
			"pj hostile collision target", 704, 780);
		assertTrue(PathValidation.checkCombatProjectilePath(harness.world(),
			hostileSource.getWorldLocation(), hostileTarget.getWorldLocation()),
			"hostile collision fixture begins with clear hard cover");
		final int hostileHits = hostileTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent hostileChanged = typedProjectile(
			harness, hostileSource, hostileTarget, 3,
			ProjectileLaunchSpecification.Producer.NPC_RANGED, 2);
		harness.world().getTile(702, 780)
			.addCombatProjectileCollision(CollisionFlag.FULL_BLOCK);
		assertFalse(PathValidation.checkCombatProjectilePath(harness.world(),
			hostileSource.getWorldLocation(), hostileTarget.getWorldLocation()),
			"new hard cover blocks the hostile-projectile semantic");
		hostileChanged.action();
		assertEquals(hostileHits, hostileTarget.getLevel(Skill.HITS.id()),
			"NPC impact rechecks hostile-projectile hard cover");
		assertEquals(ProjectileImpactDecision.Reason.IMPACT_PATH_BLOCKED,
			hostileChanged.getInitialProjectileImpactDecision().getReason(),
			"hostile-projectile collision reason");

		openCombatProjectileRectangle(harness, 710, 714, 780, 780);
		final Player alliedFenceSource = harness.player(
			"pj allied fence source", 710, 780);
		final Npc alliedFenceTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 714, 780);
		harness.world().getTile(712, 780)
			.addEnemyProjectileFenceCollision(CollisionFlag.FULL_BLOCK);
		assertTrue(PathValidation.checkCombatProjectilePath(harness.world(),
			alliedFenceSource.getWorldLocation(), alliedFenceTarget.getWorldLocation()),
			"player-allied launch passes through enemy-only fence cover");
		assertFalse(PathValidation.checkEnemyCombatProjectilePath(harness.world(),
			alliedFenceSource.getWorldLocation(), alliedFenceTarget.getWorldLocation()),
			"enemy launch is blocked by enemy-only fence cover");
		final int alliedFenceHits = alliedFenceTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent alliedFenceImpact = projectile(
			harness, alliedFenceSource, alliedFenceTarget, 3);
		alliedFenceImpact.action();
		assertEquals(alliedFenceHits - 3,
			alliedFenceTarget.getLevel(Skill.HITS.id()),
			"player-allied delayed impact passes through an existing fence");

		openCombatProjectileRectangle(harness, 710, 714, 790, 790);
		final Npc enemyFenceSource = harness.npc(
			NpcId.GREATER_DEMON.id(), 710, 790);
		final Player enemyFenceTarget = harness.player(
			"pj enemy fence target", 714, 790);
		final int enemyFenceHits = enemyFenceTarget.getLevel(Skill.HITS.id());
		final ProjectileEvent enemyFenceImpact = typedProjectile(
			harness, enemyFenceSource, enemyFenceTarget, 3,
			ProjectileLaunchSpecification.Producer.NPC_MAGIC, 1);
		assertTrue(PathValidation.checkEnemyCombatProjectilePath(harness.world(),
			enemyFenceSource.getWorldLocation(), enemyFenceTarget.getWorldLocation()),
			"enemy delayed-impact fence fixture launches clear");
		harness.world().getTile(712, 790)
			.addEnemyProjectileFenceCollision(CollisionFlag.FULL_BLOCK);
		assertFalse(PathValidation.checkEnemyCombatProjectilePath(harness.world(),
			enemyFenceSource.getWorldLocation(), enemyFenceTarget.getWorldLocation()),
			"fence appearing during flight blocks the enemy path authority");
		enemyFenceImpact.action();
		assertEquals(enemyFenceHits,
			enemyFenceTarget.getLevel(Skill.HITS.id()),
			"enemy delayed impact rechecks enemy-only fence cover");
		assertEquals(ProjectileImpactDecision.Reason.IMPACT_PATH_BLOCKED,
			enemyFenceImpact.getInitialProjectileImpactDecision().getReason(),
			"enemy fence impact collision reason");

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

	private static void authoredFenceEndToEndEvidence(
			final CurrentCombatHarness harness) throws Exception {
		final int westX = 367;
		final int fenceX = 369;
		final int eastX = 371;
		final int y = 3265;
		openCombatProjectileRectangle(
			harness, westX, eastX, y, y);
		assertEquals(CombatProjectileCollision.Cover.ENEMY_ONLY_FENCE,
			CombatProjectileCollision.boundaryCover(
				harness.server().getEntityHandler().getDoorDef(5)),
			"Heroes Guild raw wall ID 6 railings retain fence semantics");
		addAuthoredHeroesRailing(harness, fenceX, y);

		final Player player = harness.player(
			"heroes railing ranger", westX, y);
		final Npc npc = harness.npc(
			NpcId.GREATER_DEMON.id(), eastX, y);
		harness.equip(player, ItemId.SHORTBOW.id(), 1);
		harness.equip(player, ItemId.TIN_ARROWS.id(), 3);

		assertTrue(PathValidation.checkCombatProjectilePath(harness.world(),
			player.getWorldLocation(), npc.getWorldLocation()),
			"authored Heroes Guild railing permits player launch");
		assertFalse(PathValidation.checkEnemyCombatProjectilePath(harness.world(),
			npc.getWorldLocation(), player.getWorldLocation()),
			"authored Heroes Guild railing blocks enemy launch");

		final RangeEvent playerLaunch = new RangeEvent(
			harness.world(), player, 1L, npc);
		player.setRangeEvent(playerLaunch);
		playerLaunch.run();
		final ProjectileEvent playerProjectile = findProjectileEvent(
			harness, player,
			ProjectileLaunchSpecification.Producer.PLAYER_BOW);
		assertNotNull(playerProjectile,
			"real player range eligibility launches through authored railing");
		final int hitSplatsBefore = npc.getUpdateFlags().getHitSplats().size();
		playerProjectile.action();
		assertEquals(ProjectileImpactDecision.Reason.CURRENT_POLICY_ACCEPTED,
			playerProjectile.getInitialProjectileImpactDecision().getReason(),
			"player delayed impact remains valid through authored railing");
		assertEquals(ProjectileImpactLedger.State.SETTLED,
			playerProjectile.getProjectileImpactState(),
			"player projectile settles through authored railing");
		assertEquals(hitSplatsBefore + 1,
			npc.getUpdateFlags().getHitSplats().size(),
			"player damage settlement publishes its hit through authored railing");

		final int projectileCountBeforeNpc = countProjectileEvents(harness);
		final RangeEventNpc npcLaunch = new RangeEventNpc(
			harness.world(), npc, player);
		npc.setRangeEventNpc(npcLaunch);
		npcLaunch.run();
		assertEquals(projectileCountBeforeNpc, countProjectileEvents(harness),
			"real NPC range eligibility cannot launch through authored railing");

		removeAuthoredHeroesRailing(harness, fenceX, y);
		final ProjectileEvent npcInFlight = new ProjectileEvent(
			harness.world(), npc, player,
			ProjectileLaunchSpecification.builder(
				ProjectileLaunchSpecification.Producer.NPC_MAGIC, 3, 1)
				.build());
		assertTrue(PathValidation.checkEnemyCombatProjectilePath(harness.world(),
			npc.getWorldLocation(), player.getWorldLocation()),
			"NPC delayed-impact fixture launches while railing is absent");
		addAuthoredHeroesRailing(harness, fenceX, y);
		npcInFlight.action();
		assertEquals(ProjectileImpactDecision.Reason.IMPACT_PATH_BLOCKED,
			npcInFlight.getInitialProjectileImpactDecision().getReason(),
			"NPC delayed impact observes restored authored railing");
	}

	private static void addAuthoredHeroesRailing(
			final CurrentCombatHarness harness,
			final int fenceX, final int y) {
		harness.world().getTile(fenceX, y)
			.addTerrainCollision(CollisionFlag.WALL_EAST);
		harness.world().getTile(fenceX - 1, y)
			.addTerrainCollision(CollisionFlag.WALL_WEST);
		harness.world().getTile(fenceX, y)
			.addEnemyProjectileFenceCollision(CollisionFlag.WALL_EAST);
		harness.world().getTile(fenceX - 1, y)
			.addEnemyProjectileFenceCollision(CollisionFlag.WALL_WEST);
	}

	private static void removeAuthoredHeroesRailing(
			final CurrentCombatHarness harness,
			final int fenceX, final int y) {
		harness.world().getTile(fenceX, y)
			.removeEnemyProjectileFenceCollision(CollisionFlag.WALL_EAST);
		harness.world().getTile(fenceX - 1, y)
			.removeEnemyProjectileFenceCollision(CollisionFlag.WALL_WEST);
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
		assertEquals(0, scriptedCalls.get(),
			"scripted effects reject a logged-out source");
		assertEquals(ProjectileImpactDecision.Reason
				.SOURCE_TERMINAL_OR_UNREGISTERED,
			scripted.getInitialProjectileImpactDecision().getReason(),
			"scripted source terminal reason");

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
		assertEquals(0, ballCalls.get(),
			"ball effects reject a logged-out target");
		assertEquals(ProjectileImpactDecision.Reason
				.TARGET_TERMINAL_OR_UNREGISTERED,
			ball.getInitialProjectileImpactDecision().getReason(),
			"ball target terminal reason");

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
		assertTrue(ballSource.getAttribute("benignprojectile") == null,
			"base benign cleanup clears the source compatibility attribute");
		assertTrue(ballTarget.getAttribute("benignprojectile") == null,
			"base benign cleanup clears the target compatibility attribute");

		openCombatProjectileRectangle(harness, 720, 724, 810, 810);
		final Player validHolySource = harness.player(
			"pj valid holy source", 720, 810);
		final Npc validHolyTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 724, 810);
		final AtomicInteger validHolyCalls = new AtomicInteger();
		final RecordingCustomProjectile validHolyWater =
			new RecordingCustomProjectile(harness.world(), validHolySource,
				validHolyTarget, validHolyCalls, false,
				ProjectileLaunchSpecification.builder(
					ProjectileLaunchSpecification.Producer.LEGENDS_HOLY_WATER,
					0, 1).build());
		validHolyWater.action();
		assertEquals(1, validHolyCalls.get(),
			"Legends holy water settles at its inclusive four-tile ceiling");

		final Player holySource = harness.player(
			"pj holy source", 710, 810);
		final Npc holyTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 711, 810);
		final AtomicInteger holyCalls = new AtomicInteger();
		final RecordingCustomProjectile holyWater =
			new RecordingCustomProjectile(harness.world(), holySource, holyTarget,
				holyCalls, false, ProjectileLaunchSpecification.builder(
					ProjectileLaunchSpecification.Producer.LEGENDS_HOLY_WATER,
					0, 1).build());
		holyTarget.setLocation(Point.location(715, 810), true);
		holyWater.action();
		assertEquals(0, holyCalls.get(),
			"Legends holy water retains its four-tile launch ceiling");
		assertEquals(ProjectileImpactDecision.Reason
				.OUTSIDE_LAUNCH_ORIGIN_RANGE,
			holyWater.getInitialProjectileImpactDecision().getReason(),
			"holy-water range reason");

		final Player playerBallSource = harness.player(
			"pj distant ball source", 700, 810);
		final Npc npcBallTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 740, 810);
		final AtomicInteger playerToNpcCalls = new AtomicInteger();
		final RecordingBallProjectile playerToNpcBall =
			new RecordingBallProjectile(harness.world(), playerBallSource,
				npcBallTarget, playerToNpcCalls);
		playerToNpcBall.action();
		assertEquals(1, playerToNpcCalls.get(),
			"player-to-NPC gnome ball retains unlimited distance");

		final Npc npcBallSource = harness.npc(
			NpcId.GREATER_DEMON.id(), 750, 810);
		final Player playerBallTarget = harness.player(
			"pj distant ball target", 790, 810);
		final AtomicInteger npcToPlayerCalls = new AtomicInteger();
		final RecordingBallProjectile npcToPlayerBall =
			new RecordingBallProjectile(harness.world(), npcBallSource,
				playerBallTarget, npcToPlayerCalls);
		npcToPlayerBall.action();
		assertEquals(1, npcToPlayerCalls.get(),
			"NPC-to-player gnome ball retains unlimited distance");

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
		openCombatProjectilePath(harness, customSource, customTarget);
		final AtomicInteger customCalls = new AtomicInteger();
		final RecordingCustomProjectile custom =
			new RecordingCustomProjectile(
				harness.world(), customSource, customTarget, customCalls, false);
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
		openCombatProjectilePath(harness, source, target);
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
		openCombatProjectilePath(harness, source, target);
		return new ProjectileEvent(
			harness.world(), source, target, damage, 2, true,
			DuplicationStrategy.ALLOW_MULTIPLE);
	}

	private static ProjectileEvent typedProjectile(
			final CurrentCombatHarness harness, final Mob source,
			final Mob target, final int damage,
			final ProjectileLaunchSpecification.Producer producer,
			final int attackType) {
		openCombatProjectilePath(harness, source, target);
		return new ProjectileEvent(harness.world(), source, target,
			ProjectileLaunchSpecification.builder(producer, damage, attackType)
				.chase(true)
				.presentation(attackType, 0, true)
				.duplicationStrategy(DuplicationStrategy.ALLOW_MULTIPLE)
				.build());
	}

	private static GameTickEvent findNewRespawnEvent(
			final CurrentCombatHarness harness,
			final List<GameTickEvent> eventsBeforeRemoval) {
		for (GameTickEvent candidate
				: harness.server().getGameEventHandler().getEvents()) {
			if ("Respawn NPC".equals(candidate.getDescriptor())
					&& !eventsBeforeRemoval.contains(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static void openCombatProjectileRectangle(
			final CurrentCombatHarness harness, final int minX, final int maxX,
			final int minY, final int maxY) {
		harness.openRectangle(minX, maxX, minY, maxY);
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				harness.world().getTile(x, y).removeTerrainCollision(
					CollisionFlag.FULL_BLOCK
						| CollisionFlag.WALL_NORTH
						| CollisionFlag.WALL_EAST
						| CollisionFlag.WALL_SOUTH
						| CollisionFlag.WALL_WEST);
				harness.world().getTile(x, y).initializeTerrainCollision();
			}
		}
	}

	private static void openCombatProjectilePath(
			final CurrentCombatHarness harness, final Mob source,
			final Mob target) {
		if (!source.sharesSpatialDomain(target)) {
			return;
		}
		openCombatProjectileRectangle(
			harness,
			Math.min(source.getX(), target.getX()),
			Math.max(source.getX(), target.getX()),
			Math.min(source.getY(), target.getY()),
			Math.max(source.getY(), target.getY()));
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

	private static ProjectileEvent findProjectileEvent(
			final CurrentCombatHarness harness, final Mob source,
			final ProjectileLaunchSpecification.Producer producer) {
		for (GameTickEvent event
				: harness.server().getGameEventHandler().getEvents()) {
			if (event instanceof ProjectileEvent) {
				final ProjectileEvent projectile = (ProjectileEvent) event;
				if (projectile.getLaunchSnapshot().getSourceSnapshot()
						.matchesIdentityAndSession(source)
						&& projectile.getLaunchSnapshot().getSpecification()
							.getProducer() == producer) {
					return projectile;
				}
			}
		}
		return null;
	}

	private static void assertNotNull(
			final Object value, final String message) {
		assertTrue(value != null, message);
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
