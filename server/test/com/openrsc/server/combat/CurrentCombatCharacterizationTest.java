package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.cleric.ClericSpellId;
import com.openrsc.server.content.cleric.ClericSupportTargeting;
import com.openrsc.server.content.cleric.effect.ClericEffectCatalog;
import com.openrsc.server.content.cleric.effect.ClericEffectClock;
import com.openrsc.server.content.cleric.effect.ClericEffectOrigin;
import com.openrsc.server.content.cleric.effect.ClericEffectOriginValidator;
import com.openrsc.server.content.cleric.effect.ClericEffectOrigins;
import com.openrsc.server.content.cleric.effect.ClericEffectRankDefinition;
import com.openrsc.server.content.cleric.effect.ClericEffectRegistry;
import com.openrsc.server.content.cleric.runtime.ClericDirectCombatRuntime;
import com.openrsc.server.content.DropTable;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.content.party.PartyPlayer;
import com.openrsc.server.content.party.PartyRank;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.PluginTickEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeUtils;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.combat.CombatTick;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcAttackStyleProfile;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.states.HostileState;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.AttackHandler;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;
import com.openrsc.server.runtime.ProductionGameRandom;
import com.openrsc.server.runtime.SystemGameClock;
import com.openrsc.server.util.rsc.DataConversions;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable current-state combat specifications for the authoritative Ant build. */
public final class CurrentCombatCharacterizationTest {
	private static final String SUMMARY_FILE =
		System.getProperty("combat.summary.file", "");
	private static final boolean FORCE_ZERO_SCENARIOS =
		Boolean.getBoolean("combat.forceZeroScenarios");
	private static final int DRAGONSTONE_BANGLE_OF_SIPHONING = 1733;
	private static int passed;

	private CurrentCombatCharacterizationTest() {
	}

	public static void main(final String[] arguments) throws Exception {
		if (FORCE_ZERO_SCENARIOS) {
			writeSummary();
			return;
		}

		try (CurrentCombatHarness harness = new CurrentCombatHarness()) {
			run(harness, "deterministic_runtime_contracts_preserve_production_sources",
				CurrentCombatCharacterizationTest::deterministicRuntimeContracts);
			run(harness, "current_scheduler_advances_one_whole_combat_tick",
				CurrentCombatCharacterizationTest::currentSchedulerTickDriver);
			run(harness, "melee_ranged_magic_formula_replay_is_byte_identical",
				CurrentCombatCharacterizationTest::deterministicFormulaReplay);
			run(harness, "npc_projectile_style_and_element_rolls_are_replayable",
				CurrentCombatCharacterizationTest::deterministicNpcProjectileProfile);
			run(harness, "ranged_and_throwing_cooldowns_remain_whole_tick_boundaries",
				CurrentCombatCharacterizationTest::rangedCooldownBoundaries);
			run(harness, "ranged_and_magic_projectiles_settle_on_current_tick_boundary",
				CurrentCombatCharacterizationTest::projectileImpactBoundary);
			run(harness, "selected_drop_outcomes_replay_from_server_random_source",
				CurrentCombatCharacterizationTest::deterministicDropReplay);
			run(harness, "melee_ranged_magic_damage_share_xp",
				CurrentCombatCharacterizationTest::damageShareExperience);
			run(harness, "attack_eligibility_precedes_plugin_callback",
				CurrentCombatCharacterizationTest::attackEligibilityAndPluginOrder);
			run(harness, "layered_domain_and_line_of_effect_reject_cross_level",
				CurrentCombatCharacterizationTest::layeredDomainAndLineOfEffect);
			run(harness, "ordinary_npc_retreat_then_hostile_reengagement",
				CurrentCombatCharacterizationTest::ordinaryNpcRetreatAndReengagement);
			run(harness, "npc_poison_clears_on_death_and_respawn",
				CurrentCombatCharacterizationTest::poisonDeathAndRespawn);
			run(harness, "cleric_ward_aegis_rally_thorns_order",
				CurrentCombatCharacterizationTest::clericDirectEffectOrder);
			run(harness, "summon_damage_contributes_to_owner_credit_not_style_xp",
				CurrentCombatCharacterizationTest::summonOwnerContribution);
			run(harness, "scythe_cleave_selects_adjacent_secondary_npcs",
				CurrentCombatCharacterizationTest::scytheCleaveSelection);
			run(harness, "scythe_damage_and_lifesteal_settle_in_current_order",
				CurrentCombatCharacterizationTest::scytheDamageAndLifesteal);
			run(harness, "shuriken_selects_three_unique_valid_targets",
				CurrentCombatCharacterizationTest::shurikenSelection);
			run(harness, "shuriken_damage_and_lifesteal_wait_for_projectile_impact",
				CurrentCombatCharacterizationTest::shurikenDamageAndLifesteal);
			run(harness, "support_aoe_excludes_caster_cross_level_and_blocked",
				CurrentCombatCharacterizationTest::supportAreaSelection);
			run(harness, "npc_death_listener_is_exactly_once",
				CurrentCombatCharacterizationTest::exactlyOnceDeathCallback);
		}
		writeSummary();
		System.out.println("Combat characterization scenarios passed: " + passed);
	}

	private static void deterministicRuntimeContracts(
			final CurrentCombatHarness harness) {
		assertTrue(harness.server().getGameClock() == harness.clock(),
			"Server must retain the injected gameplay clock");
		assertTrue(harness.server().getCombatRandom() == harness.random(),
			"Server must retain the injected combat random source");

		final long initialMillis = harness.clock().currentTimeMillis();
		final long initialNanos = harness.clock().nanoTime();
		harness.clock().advanceMillis(640L);
		assertEquals(Long.valueOf(initialMillis + 640L),
			Long.valueOf(harness.clock().currentTimeMillis()),
			"mutable clock milliseconds");
		assertEquals(Long.valueOf(initialNanos + 640_000_000L),
			Long.valueOf(harness.clock().nanoTime()),
			"mutable clock nanoseconds");

		final CombatTick first = CombatTick.of(7L);
		final CombatTick later = first.plus(5L);
		assertEquals(Long.valueOf(12L), Long.valueOf(later.value()),
			"typed combat tick addition");
		assertEquals(Long.valueOf(5L), Long.valueOf(later.elapsedSince(first)),
			"typed combat tick elapsed value");
		assertFalse(CombatTick.unset().isSet(), "unset combat tick sentinel");

		final long replaySeed = 0xA02C0B4L;
		final String firstReplay = deterministicDrawTranscript(
			harness.random(), replaySeed);
		final String secondReplay = deterministicDrawTranscript(
			harness.random(), replaySeed);
		assertEquals(firstReplay, secondReplay,
			"seeded random replay transcript must be byte-identical");

		DataConversions.getRandom().setSeed(replaySeed);
		final int legacyInt = DataConversions.getRandom().nextInt(37);
		final double legacyDouble = DataConversions.getRandom().nextDouble();
		DataConversions.getRandom().setSeed(replaySeed);
		assertEquals(Integer.valueOf(legacyInt),
			Integer.valueOf(ProductionGameRandom.INSTANCE.nextInt(37)),
			"production random integer adapter parity");
		assertEquals(Double.valueOf(legacyDouble),
			Double.valueOf(ProductionGameRandom.INSTANCE.nextDouble()),
			"production random double adapter parity");
		DataConversions.getRandom().setSeed(replaySeed);
		final int legacyInclusive = DataConversions.random(-3, 5);
		DataConversions.getRandom().setSeed(replaySeed);
		assertEquals(Integer.valueOf(legacyInclusive),
			Integer.valueOf(ProductionGameRandom.INSTANCE.nextIntInclusive(-3, 5)),
			"production random inclusive-bound adapter parity");

		final long systemMillis = System.currentTimeMillis();
		final long adaptedMillis = SystemGameClock.INSTANCE.currentTimeMillis();
		assertTrue(adaptedMillis >= systemMillis,
			"production clock adapter cannot precede its immediate system read");
		assertTrue(adaptedMillis - systemMillis < 1_000L,
			"production clock adapter must use the current system clock");
	}

	private static String deterministicDrawTranscript(
			final SeededGameRandom random, final long seed) {
		random.reset(seed);
		final int first = random.nextInt(17);
		final int inclusive = random.nextIntInclusive(-3, 3);
		final double fraction = random.nextDouble();
		return first + "|" + inclusive + "|" + fraction + "|"
			+ random.describeState();
	}

	private static void currentSchedulerTickDriver(
			final CurrentCombatHarness harness) throws Exception {
		final List<String> callbacks = new ArrayList<String>();
		final Player owner = harness.player("tick driver", 82, 82);
		harness.server().getGameEventHandler().add(new GameTickEvent(
				harness.world(), null, 1L, "A02 non-player tick fixture",
				DuplicationStrategy.ALLOW_MULTIPLE) {
			@Override
			public void run() {
				callbacks.add("non-player");
				stop();
			}
		});
		harness.server().getGameEventHandler().add(new GameTickEvent(
				harness.world(), owner, 1L, "A02 player tick fixture",
				DuplicationStrategy.ALLOW_MULTIPLE) {
			@Override
			public void run() {
				callbacks.add("player");
				stop();
			}
		});
		final long tickBefore = harness.server().getCurrentTick();
		final long millisBefore = harness.clock().currentTimeMillis();
		final CombatTick advanced = harness.advanceOneCombatTick();
		assertEquals(Arrays.asList("non-player", "player"), callbacks,
			"current production event-handler ordering");
		assertEquals(Long.valueOf(tickBefore + 1L),
			Long.valueOf(advanced.value()), "whole scheduler tick advance");
		assertEquals(Long.valueOf(millisBefore + harness.server().getConfig().GAME_TICK),
			Long.valueOf(harness.clock().currentTimeMillis()),
			"mutable clock advances by exactly one configured tick");
	}

	private static void deterministicFormulaReplay(
			final CurrentCombatHarness harness) {
		final Player player = harness.player("formula replay", 86, 86);
		final Npc victim = harness.npc(NpcId.GREATER_DEMON.id(), 87, 86);
		final String first = combatFormulaTranscript(harness, player, victim,
			0xA02F0A1L);
		final String second = combatFormulaTranscript(harness, player, victim,
			0xA02F0A1L);
		assertEquals(first, second,
			"melee/ranged/magic formula replay must be byte-identical");
		assertTrue(first.contains("int("),
			"formula replay must record bounded production-equivalent draws");

		harness.random().reset(0xA02A11L);
		harness.random().scriptInts(Integer.valueOf(0), Integer.valueOf(-1));
		assertEquals(0, CombatFormula.doMeleeDamage(player, victim),
			"scripted melee miss");
		harness.random().reset(0xA02A12L);
		harness.random().scriptInts(Integer.valueOf(-1), Integer.valueOf(0));
		assertTrue(CombatFormula.doMeleeDamage(player, victim) > 0,
			"scripted melee hit");

		harness.random().reset(0xA02A13L);
		harness.random().scriptInts(Integer.valueOf(0), Integer.valueOf(-1));
		assertEquals(0, CombatFormula.doRangedDamage(
			player, -1, -1, victim, false), "scripted ranged miss");
		harness.random().reset(0xA02A14L);
		harness.random().scriptInts(Integer.valueOf(-1), Integer.valueOf(0));
		assertTrue(CombatFormula.doRangedDamage(
			player, -1, -1, victim, false) > 0, "scripted ranged hit");

		harness.random().reset(0xA02A15L);
		harness.random().scriptInts(Integer.valueOf(0), Integer.valueOf(-1));
		assertEquals(0, CombatFormula.calculateMagicDamage(
			player, victim, 12.0D), "scripted magic miss");
		harness.random().reset(0xA02A16L);
		harness.random().scriptInts(Integer.valueOf(-1), Integer.valueOf(0));
		assertTrue(CombatFormula.calculateMagicDamage(
			player, victim, 12.0D) > 0, "scripted magic hit");
	}

	private static void projectileImpactBoundary(
			final CurrentCombatHarness harness) throws Exception {
		final Player rangedCaster = harness.player("ranged impact", 94, 94);
		final Player magicCaster = harness.player("magic impact", 94, 96);
		final Npc rangedTarget = harness.npc(3, 95, 94);
		final Npc magicTarget = harness.npc(3, 95, 96);
		final int rangedBefore = rangedTarget.getSkills().getLevel(Skill.HITS.id());
		final int magicBefore = magicTarget.getSkills().getLevel(Skill.HITS.id());
		harness.server().getGameEventHandler().add(new ProjectileEvent(
			harness.world(), rangedCaster, rangedTarget, 2, 2, true,
			DuplicationStrategy.ALLOW_MULTIPLE));
		harness.server().getGameEventHandler().add(new ProjectileEvent(
			harness.world(), magicCaster, magicTarget, 2, 1, true,
			DuplicationStrategy.ALLOW_MULTIPLE));
		assertEquals(rangedBefore,
			rangedTarget.getSkills().getLevel(Skill.HITS.id()),
			"ranged damage before projectile impact tick");
		assertEquals(magicBefore,
			magicTarget.getSkills().getLevel(Skill.HITS.id()),
			"magic damage before projectile impact tick");
		harness.advanceOneCombatTick();
		assertEquals(rangedBefore - 2,
			rangedTarget.getSkills().getLevel(Skill.HITS.id()),
			"ranged impact settlement");
		assertEquals(magicBefore - 2,
			magicTarget.getSkills().getLevel(Skill.HITS.id()),
			"magic impact settlement");
	}

	private static String combatFormulaTranscript(
			final CurrentCombatHarness harness, final Player player,
			final Npc victim, final long seed) {
		harness.random().reset(seed);
		final int melee = CombatFormula.doMeleeDamage(player, victim);
		final int ranged = CombatFormula.doRangedDamage(
			player, -1, -1, victim, false);
		final int magic = CombatFormula.calculateMagicDamage(
			player, victim, 12.0D);
		return "melee=" + melee + "|ranged=" + ranged + "|magic=" + magic
			+ "|" + harness.random().describeState();
	}

	private static void rangedCooldownBoundaries(
			final CurrentCombatHarness harness) throws Exception {
		final Player player = harness.player("cooldown owner", 92, 92);
		final Npc target = harness.npc(3, 93, 92);

		final RangeEvent range = new RangeEvent(
			harness.world(), player, 1L, target);
		player.setRangeEvent(range);
		player.setAttribute("can_range_again",
			Long.valueOf(harness.server().getCurrentTick() + 10L));
		range.reTarget(target);
		assertEquals(Long.valueOf(harness.server().getCurrentTick() + 1L),
			player.getAttribute("can_range_again", Long.valueOf(0L)),
			"range retarget truncates to the existing one-tick boundary");
		range.run();
		assertTrue(player.getRangeEvent() == range,
			"range remains cooldown-blocked before the next tick");
		harness.advanceOneCombatTick();
		range.run();
		assertTrue(player.getRangeEvent() == null,
			"range reaches its normal validation path on the boundary tick");

		final ThrowingEvent throwing = new ThrowingEvent(
			harness.world(), player, 1L, target);
		player.setThrowingEvent(throwing);
		player.setAttribute("can_range_again",
			Long.valueOf(harness.server().getCurrentTick() + 10L));
		throwing.reTarget(target);
		assertEquals(Long.valueOf(harness.server().getCurrentTick() + 1L),
			player.getAttribute("can_range_again", Long.valueOf(0L)),
			"throwing retarget truncates to the existing one-tick boundary");
		throwing.run();
		assertTrue(player.getThrowingEvent() == throwing,
			"throwing remains cooldown-blocked before the next tick");
		harness.advanceOneCombatTick();
		throwing.run();
		assertTrue(player.getThrowingEvent() == null,
			"throwing reaches its normal validation path on the boundary tick");
	}

	private static void deterministicNpcProjectileProfile(
			final CurrentCombatHarness harness) {
		final Npc demon = harness.npc(NpcId.GREATER_DEMON.id(), 89, 89);
		final NpcAttackStyleProfile demonProfile =
			NpcAttackStyleProfile.forNpc(demon);
		assertEquals(NpcAttackStyleProfile.MELEE_MAGIC, demonProfile,
			"greater demon mixed attack profile");
		harness.random().reset(0xA02A771L);
		harness.random().scriptInts(Integer.valueOf(64), Integer.valueOf(65));
		assertTrue(demonProfile.prefersProjectileAtDistance(demon, 1),
			"mixed NPC projectile roll immediately below its threshold");
		assertFalse(demonProfile.prefersProjectileAtDistance(demon, 1),
			"mixed NPC melee roll at its threshold");

		final Npc battleMage = harness.npc(NpcId.BATTLE_MAGE_GUTHIX.id(), 90, 89);
		final NpcAttackStyleProfile mageProfile =
			NpcAttackStyleProfile.forNpc(battleMage);
		assertEquals(NpcAttackStyleProfile.PURE_MAGIC, mageProfile,
			"battle mage projectile profile");
		harness.random().scriptInts(Integer.valueOf(0), Integer.valueOf(3));
		assertEquals(NpcMagicElement.AIR,
			mageProfile.getMagicElement(battleMage),
			"scripted first battle-mage element");
		assertEquals(NpcMagicElement.FIRE,
			mageProfile.getMagicElement(battleMage),
			"scripted final battle-mage element");
		assertTrue(harness.random().describeState().contains("int(4)=3"),
			"NPC profile replay reports its bounded element draw");
	}

	private static void deterministicDropReplay(
			final CurrentCombatHarness harness) {
		final Player owner = harness.player("drop replay", 90, 90);
		final DropTable table = new DropTable("A02 deterministic drop fixture");
		table.addItemDrop(ItemId.COINS.id(), 7, 1);
		table.addItemDrop(ItemId.BONES.id(), 1, 1);

		harness.random().reset(0xA02D20FL);
		harness.random().scriptInts(Integer.valueOf(0));
		final List<Item> first = table.rollItem(owner);
		assertEquals(1, first.size(), "first selected drop count");
		assertEquals(ItemId.COINS.id(), first.get(0).getCatalogId(),
			"scripted first drop identity");
		final String firstState = harness.random().describeState();

		harness.random().reset(0xA02D20FL);
		harness.random().scriptInts(Integer.valueOf(0));
		final List<Item> replayed = table.rollItem(owner);
		assertEquals(first.get(0).getCatalogId(), replayed.get(0).getCatalogId(),
			"selected drop replay identity");
		assertEquals(firstState, harness.random().describeState(),
			"selected drop replay draw transcript");

		harness.random().reset(0xA02D20FL);
		harness.random().scriptInts(Integer.valueOf(1));
		final List<Item> alternate = table.rollItem(owner);
		assertEquals(ItemId.BONES.id(), alternate.get(0).getCatalogId(),
			"alternate bounded drop outcome");
	}

	private static void damageShareExperience(final CurrentCombatHarness harness)
			throws Exception {
		final Player player = harness.player("damage share", 100, 100);
		final Npc npc = harness.npc(3, 101, 100);
		player.setHitsXpFocus(com.openrsc.server.constants.Skills.AGGRESSIVE_MODE);

		final int meleeBefore = player.getSkills().getExperience(Skill.MELEE.id());
		final int rangedBefore = player.getSkills().getExperience(Skill.RANGED.id());
		final int magicBefore = player.getSkills().getExperience(Skill.MAGIC.id());
		final int hitsBefore = player.getSkills().getExperience(Skill.HITS.id());
		CurrentCombatHarness.invokePrivate(npc, "awardDamageShareXp",
			new Class<?>[] {Player.class, int.class, int.class, int.class, int.class},
			player, Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1),
			Integer.valueOf(12));

		assertEquals(12, player.getSkills().getExperience(Skill.MELEE.id()) - meleeBefore,
			"melee primary XP");
		assertEquals(12, player.getSkills().getExperience(Skill.RANGED.id()) - rangedBefore,
			"ranged primary XP");
		assertEquals(12, player.getSkills().getExperience(Skill.MAGIC.id()) - magicBefore,
			"magic primary XP");
		assertEquals(12, player.getSkills().getExperience(Skill.HITS.id()) - hitsBefore,
			"shared Hits-focus XP");
	}

	private static void attackEligibilityAndPluginOrder(
			final CurrentCombatHarness harness) throws Exception {
		final RecordingAttackPlugin plugin = new RecordingAttackPlugin();
		harness.installPlugin(AttackNpcTrigger.class, plugin);
		final Player player = harness.player("attack order", 130, 130);
		final Npc npc = harness.npc(3, 131, 130);
		npc.setHostile(player, HostileState.HostilityType.PROVOKED);

		processNpcAttack(player, npc);
		final WalkToAction action = player.getWalkToAction();
		assertNotNull(action, "eligible attack walk action");
		assertTrue(action.shouldExecute(), "eligible adjacent attack must execute");
		assertEquals(Collections.emptyList(), plugin.events(),
			"plugin callbacks must wait for attack eligibility and approach");
		action.execute();
		assertEquals(Collections.singletonList("block"), plugin.events(),
			"plugin block callback must precede its action callback");

		PluginTickEvent pluginEvent = null;
		for (GameTickEvent event : harness.server().getGameEventHandler().getEvents()) {
			if (event instanceof PluginTickEvent
					&& event.getDescriptor().contains("RecordingAttackPlugin")) {
				pluginEvent = (PluginTickEvent) event;
				break;
			}
		}
		assertNotNull(pluginEvent, "scheduled attack plugin action");
		pluginEvent.run();
		assertEquals(Arrays.asList("block", "action"), plugin.events(),
			"attack plugin callback order");
	}

	private static void layeredDomainAndLineOfEffect(
			final CurrentCombatHarness harness) throws Exception {
		final boolean previous = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Player player = harness.player("layer source", 160, 160);
			final Npc npc = harness.npc(
				3, 161, LegacyPackedPointAdapter.LEVEL_STRIDE + 160);
			assertFalse(player.sharesSpatialDomain(npc),
				"same packed geography on another signed level must be a different domain");
			assertFalse(PathValidation.checkHostileProjectilePath(
				harness.world(), player.getWorldLocation(), npc.getWorldLocation()),
				"hostile line of effect must reject cross-level targets");

			final RecordingAttackPlugin plugin = new RecordingAttackPlugin();
			harness.installPlugin(AttackNpcTrigger.class, plugin);
			npc.setHostile(player, HostileState.HostilityType.PROVOKED);
			processNpcAttack(player, npc);
			final WalkToAction action = player.getWalkToAction();
			assertNotNull(action, "cross-level request remains an approach attempt");
			assertFalse(action.shouldExecute(),
				"cross-level attack approach must fail before plugin dispatch");
			assertEquals(Collections.emptyList(), plugin.events(),
				"cross-level rejection must not invoke attack plugins");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = previous;
		}
	}

	private static void ordinaryNpcRetreatAndReengagement(
			final CurrentCombatHarness harness) throws Exception {
		final Player player = harness.player("retreat gate", 190, 190);
		final Npc npc = harness.npc(3, 191, 190);
		npc.clearHostility();
		npc.setRanAwayTimer();
		processNpcAttack(player, npc);
		assertNull(player.getWalkToAction(),
			"ordinary retreat window must reject a fresh attack request");
		harness.advanceOneCombatTick();
		processNpcAttack(player, npc);
		assertNotNull(player.getWalkToAction(),
			"ordinary retreat window expires at the current whole-tick boundary");

		npc.setRanAwayTimer();
		npc.setHostile(player, HostileState.HostilityType.PROVOKED);
		processNpcAttack(player, npc);
		assertNotNull(player.getWalkToAction(),
			"current hostile target may re-engage during the retreat tick");
	}

	private static void poisonDeathAndRespawn(final CurrentCombatHarness harness) {
		final Player source = harness.player("poison source", 220, 220);
		final Npc npc = harness.npc(3, 221, 220);
		npc.applyPoison(120, source);
		assertTrue(npc.getCurrentPoisonPower() > 0, "poison fixture must be active");
		npc.remove();
		assertEquals(0, npc.getCurrentPoisonPower(), "death removal poison power");
		assertEquals(0, npc.getPoisonMaxPower(), "death removal poison ceiling");
		npc.applyPoison(120, source);
		assertEquals(0, npc.getCurrentPoisonPower(),
			"respawning NPC must reject stale poison application");

		final GameTickEvent respawn = harness.findEvent("Respawn NPC");
		assertNotNull(respawn, "scheduled production respawn callback");
		respawn.run();
		assertFalse(npc.isRespawning(), "respawn callback must restore live state");
		assertEquals(0, npc.getCurrentPoisonPower(), "respawn poison power");
		assertTrue(npc.canReceivePoison(), "new NPC lifetime may receive new poison");
	}

	private static void clericDirectEffectOrder(final CurrentCombatHarness harness)
			throws Exception {
		final Player caster = harness.player("cleric caster", 250, 550);
		final Player defender = harness.player("cleric defender", 251, 550);
		final Player attacker = harness.player("cleric attacker", 250, 551);
		final Npc enemy = harness.npc(3, 251, 551);
		final Party party = party(caster, defender, attacker);
		assertTrue(caster.getParty() == party && defender.getParty() == party,
			"Cleric origin party fixture");

		final ClericEffectRegistry defenderEffects = registry(defender);
		final ClericEffectOrigin defenderOrigin = ClericEffectOrigins.current(
			caster, defender);
		final ClericEffectOriginValidator defenderValidator =
			ClericEffectOrigins.validatorFor(defender);
		apply(defenderEffects, ClericSpellId.WARD, 1,
			defenderOrigin, defenderValidator);
		ClericDirectCombatRuntime.BeforeDamage ward =
			ClericDirectCombatRuntime.beforeDirectDamage(enemy, defender, 20);
		assertEquals(15, ward.getDamage(), "Ward 25 percent protection");
		assertEquals(5, ward.getPreventedDamage(), "Ward prevented damage");

		apply(defenderEffects, ClericSpellId.AEGIS, 1,
			defenderOrigin, defenderValidator);
		ClericDirectCombatRuntime.BeforeDamage aegis =
			ClericDirectCombatRuntime.beforeDirectDamage(enemy, defender, 20);
		assertEquals(10, aegis.getDamage(), "Aegis replaces Ward before damage");
		assertEquals(10, aegis.getPreventedDamage(), "Aegis prevented damage");

		apply(defenderEffects, ClericSpellId.THORNS, 1,
			defenderOrigin, defenderValidator);
		ClericDirectCombatRuntime.AfterDamage thorns =
			ClericDirectCombatRuntime.afterExistingLifesteal(enemy, defender, 20);
		assertEquals(1, thorns.getThornsDamage(),
			"Thorns resolves after established direct damage");

		final ClericEffectRegistry attackerEffects = registry(attacker);
		apply(attackerEffects, ClericSpellId.RALLY, 1,
			ClericEffectOrigins.current(caster, attacker),
			ClericEffectOrigins.validatorFor(attacker));
		attacker.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 40, 100, false);
		ClericDirectCombatRuntime.AfterDamage rally =
			ClericDirectCombatRuntime.afterExistingLifesteal(attacker, enemy, 10);
		assertEquals(2, rally.getRallyHealing(),
			"Rally resolves after existing lifesteal");
		assertEquals(42, attacker.getSkills().getLevel(Skill.HITS.id()),
			"Rally applies its returned healing");

		final long thornsDurationMillis = ClericEffectCatalog
			.get(ClericSpellId.THORNS, 1).getDuration()
			.toMilliseconds(harness.server().getConfig().GAME_TICK);
		harness.clock().advanceMillis(thornsDurationMillis - 1L);
		assertTrue(defenderEffects.get(
			com.openrsc.server.content.cleric.effect.ClericEffectFamily.REFLECTION,
			defenderValidator).isPresent(),
			"Cleric effect remains active immediately before its existing deadline");
		harness.clock().advanceMillis(1L);
		assertFalse(defenderEffects.get(
			com.openrsc.server.content.cleric.effect.ClericEffectFamily.REFLECTION,
			defenderValidator).isPresent(),
			"Cleric effect expires at its existing monotonic deadline");
	}

	@SuppressWarnings("unchecked")
	private static void summonOwnerContribution(final CurrentCombatHarness harness)
			throws Exception {
		final Player owner = harness.player("summon owner", 280, 280);
		final Npc target = harness.npc(3, 281, 280);
		target.addSummonDamage(owner, 2);
		assertTrue(target.hasDamageBy(owner),
			"summon damage must count toward owner contribution");
		assertTrue(target.getPreferredThreatTarget() == owner,
			"summon owner contribution participates in current threat selection");
		final List<Object> styleContributors = (List<Object>)
			CurrentCombatHarness.invokePrivate(target, "getAllDamageDealerIds",
				new Class<?>[0]);
		assertTrue(styleContributors.isEmpty(),
			"summon-only contribution remains excluded from combat-style XP");
	}

	private static void scytheCleaveSelection(final CurrentCombatHarness harness)
			throws Exception {
		final Player player = harness.player("scythe user", 310, 310);
		final Npc primary = harness.npc(3, 311, 310);
		final Npc adjacent = harness.npc(3, 310, 311);
		final Npc distant = harness.npc(3, 313, 310);
		final PvmMeleeEvent event = new PvmMeleeEvent(
			harness.world(), player, primary);
		final Method selection = PvmMeleeEvent.class.getDeclaredMethod(
			"isValidScytheCleaveTarget", Player.class, Npc.class, Npc.class);
		selection.setAccessible(true);
		assertTrue(((Boolean) selection.invoke(event, player, primary, adjacent)).booleanValue(),
			"adjacent secondary NPC is scythe-cleave eligible");
		assertFalse(((Boolean) selection.invoke(event, player, primary, primary)).booleanValue(),
			"primary NPC cannot be selected twice by scythe cleave");
		assertFalse(((Boolean) selection.invoke(event, player, primary, distant)).booleanValue(),
			"distant NPC is outside scythe cleave");
	}

	private static void scytheDamageAndLifesteal(
			final CurrentCombatHarness harness) throws Exception {
		final Player player = harness.player("scythe settlement", 324, 324);
		final Npc primary = harness.npc(NpcId.GREATER_DEMON.id(), 325, 324);
		final Npc secondaryA = harness.npc(NpcId.GREATER_DEMON.id(), 324, 325);
		final Npc secondaryB = harness.npc(NpcId.GREATER_DEMON.id(), 323, 324);
		harness.equip(player, ItemId.TIN_SCYTHE.id(), 1);
		harness.equip(player, DRAGONSTONE_BANGLE_OF_SIPHONING, 1);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 40, false);
		final int primaryBefore = primary.getSkills().getLevel(Skill.HITS.id());
		final int secondaryABefore = secondaryA.getSkills().getLevel(Skill.HITS.id());
		final int secondaryBBefore = secondaryB.getSkills().getLevel(Skill.HITS.id());

		harness.random().reset(0xA02C1EAL);
		harness.random().scriptInts(
			Integer.valueOf(-1), Integer.valueOf(0),
			Integer.valueOf(-1), Integer.valueOf(0),
			Integer.valueOf(-1), Integer.valueOf(0));
		new PvmMeleeEvent(harness.world(), player, primary).run();

		assertTrue(primary.getSkills().getLevel(Skill.HITS.id()) < primaryBefore,
			"scythe primary damage settlement");
		assertTrue(secondaryA.getSkills().getLevel(Skill.HITS.id()) < secondaryABefore,
			"first scythe cleave settlement");
		assertTrue(secondaryB.getSkills().getLevel(Skill.HITS.id()) < secondaryBBefore,
			"second scythe cleave settlement");
		assertEquals(26, player.getSkills().getLevel(Skill.HITS.id()),
			"primary and two cleaves each apply existing lifesteal after damage");
	}

	@SuppressWarnings("unchecked")
	private static void shurikenSelection(final CurrentCombatHarness harness)
			throws Exception {
		harness.openRectangle(338, 344, 338, 344);
		final Player player = harness.player("shuriken user", 340, 340);
		final Npc primary = harness.npc(3, 341, 340);
		harness.npc(3, 340, 341);
		harness.npc(3, 341, 341);
		harness.npc(3, 339, 341);
		harness.npc(3, 339, 340);
		harness.npc(3, 340, 339);
		final ThrowingEvent event = new ThrowingEvent(
			harness.world(), player, 1, primary);
		final int itemId = ItemId.TIN_SHURIKEN.id();
		harness.random().reset(0xA025A17L);
		final List<Mob> targets = (List<Mob>) CurrentCombatHarness.invokePrivate(
			event, "selectThrowingTargets",
			new Class<?>[] {Player.class, int.class, int.class},
			player, Integer.valueOf(itemId),
			Integer.valueOf(RangeUtils.getThrowingAttackRadius(itemId)));
		assertEquals(3, targets.size(), "shuriken target cap");
		assertTrue(targets.contains(primary),
			"current primary target remains in capped shuriken selection");
		assertEquals(3, new java.util.HashSet<Mob>(targets).size(),
			"shuriken target identities must be unique");
		final List<Integer> firstOrder = mobIndices(targets);
		final String firstState = harness.random().describeState();
		harness.random().reset(0xA025A17L);
		final List<Mob> replayed = (List<Mob>) CurrentCombatHarness.invokePrivate(
			event, "selectThrowingTargets",
			new Class<?>[] {Player.class, int.class, int.class},
			player, Integer.valueOf(itemId),
			Integer.valueOf(RangeUtils.getThrowingAttackRadius(itemId)));
		assertEquals(firstOrder, mobIndices(replayed),
			"shuriken secondary ordering must replay for more candidates than the cap");
		assertEquals(firstState, harness.random().describeState(),
			"shuriken random draw transcript");
	}

	private static List<Integer> mobIndices(final List<Mob> mobs) {
		final List<Integer> indices = new ArrayList<Integer>(mobs.size());
		for (Mob mob : mobs) {
			indices.add(Integer.valueOf(mob.getIndex()));
		}
		return indices;
	}

	@SuppressWarnings("unchecked")
	private static void shurikenDamageAndLifesteal(
			final CurrentCombatHarness harness) throws Exception {
		harness.openRectangle(382, 388, 382, 388);
		final Player player = harness.player("shuriken settlement", 385, 385);
		final Npc primary = harness.npc(NpcId.GREATER_DEMON.id(), 386, 385);
		harness.npc(NpcId.GREATER_DEMON.id(), 385, 386);
		harness.npc(NpcId.GREATER_DEMON.id(), 386, 386);
		harness.npc(NpcId.GREATER_DEMON.id(), 384, 386);
		harness.npc(NpcId.GREATER_DEMON.id(), 384, 385);
		harness.npc(NpcId.GREATER_DEMON.id(), 385, 384);
		harness.equip(player, ItemId.TIN_SHURIKEN.id(), 4);
		harness.equip(player, DRAGONSTONE_BANGLE_OF_SIPHONING, 1);
		player.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 40, false);
		final ThrowingEvent event = new ThrowingEvent(
			harness.world(), player, 1, primary);
		harness.random().reset(0xA025E77L);
		harness.random().scriptInts(
			Integer.valueOf(0), Integer.valueOf(0),
			Integer.valueOf(-1), Integer.valueOf(0), Integer.valueOf(0),
			Integer.valueOf(-1), Integer.valueOf(0), Integer.valueOf(0),
			Integer.valueOf(-1), Integer.valueOf(0), Integer.valueOf(0));
		event.run();
		final List<Mob> selected = new ArrayList<Mob>((List<Mob>)
			CurrentCombatHarness.readPrivateField(event, "shurikenTargetLock"));
		assertEquals(3, selected.size(), "full-path shuriken target count");
		final List<Integer> hitsBefore = new ArrayList<Integer>(selected.size());
		for (Mob target : selected) {
			hitsBefore.add(Integer.valueOf(
				target.getSkills().getLevel(Skill.HITS.id())));
		}
		assertEquals(20, player.getSkills().getLevel(Skill.HITS.id()),
			"shuriken lifesteal cannot precede projectile impact");
		// Shuriken launch intentionally primes every NPC's independent counterattack.
		// Stop only those fresh target-owned events so this replay measures the
		// player-owned projectile impact/lifesteal boundary without retaliation
		// obscuring the healing assertion on the same production scheduler tick.
		for (GameTickEvent scheduled :
				harness.server().getGameEventHandler().getEvents()) {
			if (selected.contains(scheduled.getOwner())) {
				scheduled.stop();
			}
		}
		harness.server().getGameEventHandler().cleanupEvents();

		harness.advanceOneCombatTick();
		for (int index = 0; index < selected.size(); index++) {
			assertTrue(selected.get(index).getSkills().getLevel(Skill.HITS.id())
					< hitsBefore.get(index).intValue(),
				"selected shuriken target damage " + index);
		}
		assertTrue(player.getSkills().getLevel(Skill.HITS.id()) > 20,
			"three shuriken impacts apply lifesteal after their damage");
	}

	private static void supportAreaSelection(final CurrentCombatHarness ignored) {
		final AreaCandidate caster = new AreaCandidate("caster", "global", 0, 0, 0, true);
		final AreaCandidate valid = new AreaCandidate("valid", "global", 0, 2, 2, true);
		final AreaCandidate crossLevel = new AreaCandidate("cross", "global", 1, 1, 1, true);
		final AreaCandidate blocked = new AreaCandidate("blocked", "global", 0, 1, 0, false);
		final AreaCandidate distant = new AreaCandidate("distant", "global", 0, 3, 0, true);
		final List<AreaCandidate> resolved = ClericSupportTargeting.resolve(
			caster, Arrays.asList(caster, valid, valid, crossLevel, blocked, distant),
			2, AreaCandidate.VIEW);
		assertEquals(Collections.singletonList(valid), resolved,
			"shared support AoE eligibility");
	}

	private static void exactlyOnceDeathCallback(final CurrentCombatHarness harness) {
		final Player player = harness.player("death owner", 370, 370);
		final Npc npc = harness.npc(3, 371, 370);
		npc.setShouldRespawn(false);
		npc.addCombatDamage(player, npc.getDef().getHits());
		npc.getSkills().setLevel(Skill.HITS.id(), 0);
		final AtomicInteger callbacks = new AtomicInteger();
		npc.addDeathListener(new NpcLootEvent(
			harness.world(), npc.getLocation(), npc.getID(), 1, ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer, final Npc ignoredNpc) {
				callbacks.incrementAndGet();
			}
		});
		npc.killedBy(player);
		npc.killedBy(player);
		assertEquals(1, callbacks.get(), "NPC loot listener callback count");
		assertTrue(npc.isUnregistering(),
			"non-respawning NPC remains queued for terminal unregister");
	}

	private static void processNpcAttack(final Player player, final Npc npc)
			throws Exception {
		final TargetMobStruct payload = new TargetMobStruct();
		payload.setOpcode(OpcodeIn.NPC_ATTACK);
		payload.serverIndex = npc.getIndex();
		new AttackHandler().process(payload, player);
	}

	private static ClericEffectRegistry registry(final Player player) {
		final ClericEffectRegistry registry = new ClericEffectRegistry(
			ClericEffectClock.game(player.getWorld().getServer().getGameClock(),
				player.getConfig().GAME_TICK));
		player.installTransientEffectState(registry);
		return registry;
	}

	private static void apply(final ClericEffectRegistry registry,
			final ClericSpellId spell, final int rank,
			final ClericEffectOrigin origin,
			final ClericEffectOriginValidator validator) {
		final ClericEffectRankDefinition<?> definition =
			ClericEffectCatalog.get(spell, rank);
		assertTrue(registry.apply(definition, origin, validator).isUseful(),
			"Cleric effect application " + spell + "/" + rank);
	}

	private static Party party(final Player... players) throws Exception {
		final Party party = new Party(players[0].getWorld());
		final Constructor<PartyPlayer> constructor =
			PartyPlayer.class.getDeclaredConstructor(String.class);
		constructor.setAccessible(true);
		final Method setReference = PartyPlayer.class.getDeclaredMethod(
			"setPlayerReference", Player.class);
		setReference.setAccessible(true);
		for (Player player : players) {
			player.setParty(party);
			final PartyPlayer membership = constructor.newInstance(player.getUsername());
			setReference.invoke(membership, player);
			membership.setRank(party.getPlayers().isEmpty()
				? PartyRank.LEADER : PartyRank.NORMAL);
			party.getPlayers().add(membership);
			if (party.getLeader() == null) {
				party.setLeader(membership);
			}
		}
		return party;
	}

	private static void run(final CurrentCombatHarness harness,
			final String name, final Scenario scenario) throws Exception {
		try {
			scenario.run(harness);
			passed++;
			System.out.println("PASS " + name);
		} catch (Throwable failure) {
			System.err.println("FAIL " + name + ": " + failure.getMessage()
				+ " [" + harness.random().describeState() + "]");
			if (failure instanceof Exception) {
				throw (Exception) failure;
			}
			if (failure instanceof Error) {
				throw (Error) failure;
			}
			throw new AssertionError(failure);
		}
	}

	private static void writeSummary() throws Exception {
		if (SUMMARY_FILE.isEmpty()) {
			throw new IllegalStateException("combat.summary.file is required");
		}
		final File summary = new File(SUMMARY_FILE);
		final File parent = summary.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
			throw new IllegalStateException("Unable to create combat summary directory");
		}
		final Properties properties = new Properties();
		properties.setProperty("combat.summary.pass", Integer.toString(passed));
		properties.setProperty("combat.summary.scenarios", Integer.toString(passed));
		try (FileOutputStream output = new FileOutputStream(summary)) {
			properties.store(output, "current combat characterization receipt");
		}
	}

	private static void assertTrue(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(final boolean condition, final String message) {
		assertTrue(!condition, message);
	}

	private static void assertNull(final Object value, final String message) {
		assertTrue(value == null, message + ": expected null, got " + value);
	}

	private static void assertNotNull(final Object value, final String message) {
		assertTrue(value != null, message + ": expected a value");
	}

	private static void assertEquals(final Object expected, final Object actual,
			final String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected
				+ ", got " + actual);
		}
	}

	private interface Scenario {
		void run(CurrentCombatHarness harness) throws Exception;
	}

	private static final class RecordingAttackPlugin implements AttackNpcTrigger {
		private final List<String> events = Collections.synchronizedList(
			new ArrayList<String>());

		@Override
		public boolean blockAttackNpc(final Player player, final Npc npc) {
			events.add("block");
			return true;
		}

		@Override
		public void onAttackNpc(final Player player, final Npc npc) {
			events.add("action");
		}

		List<String> events() {
			synchronized (events) {
				return new ArrayList<String>(events);
			}
		}
	}

	private static final class AreaCandidate {
		private static final ClericSupportTargeting.CandidateView<AreaCandidate> VIEW =
			new ClericSupportTargeting.CandidateView<AreaCandidate>() {
				@Override
				public boolean isEligibleRecipient(final AreaCandidate candidate) {
					return true;
				}

				@Override
				public Object getWorldSpace(final AreaCandidate candidate) {
					return candidate.worldSpace;
				}

				@Override
				public int getSignedLevel(final AreaCandidate candidate) {
					return candidate.level;
				}

				@Override
				public int getX(final AreaCandidate candidate) {
					return candidate.x;
				}

				@Override
				public int getY(final AreaCandidate candidate) {
					return candidate.y;
				}

				@Override
				public boolean hasLineOfEffect(final AreaCandidate caster,
						final AreaCandidate candidate) {
					return candidate.lineOfEffect;
				}
			};

		private final String name;
		private final String worldSpace;
		private final int level;
		private final int x;
		private final int y;
		private final boolean lineOfEffect;

		private AreaCandidate(final String name, final String worldSpace,
				final int level, final int x, final int y,
				final boolean lineOfEffect) {
			this.name = name;
			this.worldSpace = worldSpace;
			this.level = level;
			this.x = x;
			this.y = y;
			this.lineOfEffect = lineOfEffect;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
