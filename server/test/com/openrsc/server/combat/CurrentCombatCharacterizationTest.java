package com.openrsc.server.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.content.PoisonPower;
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
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeUtils;
import com.openrsc.server.event.rsc.impl.projectile.MagicCombatEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.combat.CombatTick;
import com.openrsc.server.model.combat.AttackIntent;
import com.openrsc.server.model.combat.AttackTransactionResult;
import com.openrsc.server.model.combat.CombatEligibility;
import com.openrsc.server.model.combat.CombatEligibilityDecision;
import com.openrsc.server.model.combat.CombatEligibilityMessageAdapter;
import com.openrsc.server.model.combat.CombatEligibilityPhase;
import com.openrsc.server.model.combat.CombatEligibilityReason;
import com.openrsc.server.model.combat.CombatEligibilityRequest;
import com.openrsc.server.model.combat.CombatEngagementTerminalReason;
import com.openrsc.server.model.combat.CombatOwnershipAudit;
import com.openrsc.server.model.combat.CombatStyle;
import com.openrsc.server.model.combat.DamageRequest;
import com.openrsc.server.model.combat.DamageResult;
import com.openrsc.server.model.combat.PlayerAttackTransaction;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcAttackStyleProfile;
import com.openrsc.server.model.entity.npc.NpcMagicElement;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.states.HostileState;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.handlers.AttackHandler;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.net.rsc.struct.incoming.SpellStruct;
import com.openrsc.server.plugins.DefaultHandler;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;
import com.openrsc.server.runtime.ProductionGameRandom;
import com.openrsc.server.runtime.CombatDamageObserver;
import com.openrsc.server.runtime.SystemGameClock;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
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

		final RecordingDamageObserver damageObserver =
			new RecordingDamageObserver();
		try (CurrentCombatHarness harness = new CurrentCombatHarness(
				damageObserver)) {
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
			run(harness, "combat_eligibility_reasons_preserve_legacy_messages",
				CurrentCombatCharacterizationTest::combatEligibilityReasons);
			run(harness, "latest_melee_intent_wins_and_default_plugin_commits_once",
				CurrentCombatCharacterizationTest::meleeIntentOrdering);
			run(harness, "denied_retarget_preserves_current_melee_encounter",
				CurrentCombatCharacterizationTest::deniedRetargetPreservesEncounter);
			run(harness, "walk_logout_death_expiry_and_npc_lifecycle_cancel_intents",
				CurrentCombatCharacterizationTest::attackIntentLifecycle);
			run(harness, "ranged_throwing_and_magic_starts_commit_through_transactions",
				CurrentCombatCharacterizationTest::attackStyleTransactions);
			run(harness, "manual_intent_has_priority_over_autocast_retaliation",
				CurrentCombatCharacterizationTest::manualIntentPriority);
			run(harness, "directional_engagement_supports_many_incoming_attackers",
				CurrentCombatCharacterizationTest::directionalEngagementOwnership);
			run(harness, "stale_events_cannot_clear_retargeted_ownership",
				CurrentCombatCharacterizationTest::staleEventOwnership);
			run(harness, "teleport_logout_and_death_close_owned_events",
				CurrentCombatCharacterizationTest::combatOwnershipLifecycle);
			run(harness, "passive_retaliation_owns_no_outgoing_player_event",
				CurrentCombatCharacterizationTest::passiveRetaliationOwnership);
			run(harness, "combat_ownership_audit_repairs_only_explicit_anomalies",
				CurrentCombatCharacterizationTest::combatOwnershipAuditRepair);
			run(harness, "reciprocal_melee_teardown_closes_both_directions",
				CurrentCombatCharacterizationTest::reciprocalOwnershipTeardown);
			run(harness, "attack_eligibility_precedes_plugin_callback",
				CurrentCombatCharacterizationTest::attackEligibilityAndPluginOrder);
			run(harness, "layered_domain_and_line_of_effect_reject_cross_level",
				CurrentCombatCharacterizationTest::layeredDomainAndLineOfEffect);
			run(harness, "ordinary_npc_retreat_then_hostile_reengagement",
				CurrentCombatCharacterizationTest::ordinaryNpcRetreatAndReengagement);
			run(harness, "npc_poison_clears_on_death_and_respawn",
				CurrentCombatCharacterizationTest::poisonDeathAndRespawn);
			run(harness, "poisoned_exalted_rune_dagger_and_spear_apply_tier_twelve_poison",
				CurrentCombatCharacterizationTest::poisonedExaltedRuneWeaponPower);
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
			run(harness, "primary_projectile_variants_preserve_current_impact_settlement",
				CurrentCombatCharacterizationTest::primaryProjectileVariantSettlement);
			run(harness, "pvm_primary_melee_preserves_zero_nonlethal_and_lethal_settlement",
				CurrentCombatCharacterizationTest::pvmPrimaryMeleeSettlement);
			run(harness, "reciprocal_primary_melee_preserves_zero_nonlethal_and_lethal_settlement",
				CurrentCombatCharacterizationTest::reciprocalPrimaryMeleeSettlement);
			damageObserver.enabled = true;
			run(harness, "resolved_damage_contracts_are_immutable_and_lifecycle_aware",
				CurrentCombatCharacterizationTest::resolvedDamageContracts);
			run(harness, "both_primary_melee_transactions_report_applied_outcomes",
				CurrentCombatCharacterizationTest::primaryMeleeDamageTransactions);
			run(harness, "primary_projectile_transactions_preserve_variant_metadata",
				CurrentCombatCharacterizationTest::primaryProjectileDamageTransactions);
			run(harness, "primary_projectiles_preserve_shared_hits_mitigation",
				CurrentCombatCharacterizationTest::primaryProjectileSharedHitsMitigation);
			run(harness, "non_primary_projectile_damage_remains_outside_transaction",
				CurrentCombatCharacterizationTest::nonPrimaryProjectileExclusion);
			run(harness, "compatibility_helper_dot_and_death_order_remain_distinct",
				CurrentCombatSecondaryDamageCharacterization::compatibilityHelperAndDamageOverTime);
			run(harness, "projectile_secondary_damage_preserves_style_contribution",
				CurrentCombatSecondaryDamageCharacterization::projectileSecondaryContributionPolicies);
			run(harness, "all_auxiliary_helpers_preserve_nonlethal_and_terminal_settlement",
				CurrentCombatSecondaryDamageCharacterization::auxiliarySettlementAcrossEvents);
			run(harness, "all_auxiliary_helpers_preserve_magic_and_true_mitigation_asymmetry",
				CurrentCombatSecondaryDamageCharacterization::auxiliaryMitigationAcrossEvents);
			run(harness, "frostbite_reflection_preserves_pending_hit_and_magic_attribution",
				CurrentCombatReflectionCharacterization::frostbitePolicies);
			run(harness, "cleric_thorns_preserves_post_hit_and_simultaneous_death_order",
				CurrentCombatReflectionCharacterization::clericThornsPolicies);
			run(harness, "melee_jewelry_recoil_preserves_combat_attribution_and_death_adapters",
				CurrentCombatReflectionCharacterization::meleeJewelryRecoilPolicies);
			run(harness, "projectile_recoil_preserves_ring_of_life_and_ranged_reset",
				CurrentCombatReflectionCharacterization::projectileRecoilPolicies);
			run(harness, "divine_retribution_preserves_result_and_caller_owned_death",
				CurrentCombatReflectionCharacterization::divineRetributionPolicies);
			run(harness, "delayed_spell_secondary_preserves_helper_and_chase_policy",
				CurrentCombatSecondaryDamageCharacterization::delayedSpellSecondaryHelperPolicy);
			run(harness, "both_primary_melee_paths_preserve_shared_hits_mitigation",
				CurrentCombatCharacterizationTest::primaryMeleeSharedHitsMitigation);
			run(harness, "damage_observer_failure_cannot_change_current_settlement",
				CurrentCombatCharacterizationTest::damageObserverFailureIsolation);
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
		assertFalse(harness.server().getCombatDamageObserver().isEnabled(),
			"combat characterization starts with inert damage observation");
		assertFalse(CombatDamageObserver.NONE.isEnabled(),
			"ordinary production observer remains inert");
		assertNotNull(harness.server().getResolvedDamageTransaction(),
			"server owns the resolved damage transaction");

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

	private static void combatEligibilityReasons(
			final CurrentCombatHarness harness) throws Exception {
		final Player source = harness.player("elig source", 106, 106);
		final Player target = harness.player("elig target", 107, 106);
		CombatEligibilityDecision decision = CombatEligibility.evaluate(
			CombatEligibilityRequest.builder(source, target,
				CombatEligibilityPhase.COMMAND, CombatStyle.MELEE)
				.playerAttackRules(true).build());
		assertEquals(CombatEligibilityReason.PVP_DISABLED, decision.getReason(),
			"PvP-disabled reason");
		assertEquals(Collections.singletonList("This is a PvM-only world"),
			CombatEligibilityMessageAdapter.legacyAttackMessages(source, decision),
			"MyWorld PvP-disabled message");
		assertLegacyMessages(source,
			CombatEligibilityReason.PK_MODE_DISABLED, 0,
			"You are not allowed to attack that person");
		assertLegacyMessages(source,
			CombatEligibilityReason.TARGET_INVULNERABLE, 0,
			"You are not allowed to attack that person");
		assertLegacyMessages(source,
			CombatEligibilityReason.PK_LUMBRIDGE_RESTRICTED, 0,
			"You can't attack other players here. Move out of Lumbridge");
		assertLegacyMessages(source,
			CombatEligibilityReason.PK_BANKER_RESTRICTED, 0,
			"You cannot attack other players in the vicinity of a banker");
		assertLegacyMessages(source,
			CombatEligibilityReason.PK_LEVEL_MISMATCH, 0,
			"You can only attack players with combat close to your own");
		assertLegacyMessages(source,
			CombatEligibilityReason.SOURCE_OUTSIDE_WILDERNESS, 0,
			"You can't attack other players here. Move to the wilderness");
		assertLegacyMessages(source,
			CombatEligibilityReason.TARGET_OUTSIDE_WILDERNESS, 0,
			"You can't attack other players here. Move to the wilderness");
		assertLegacyMessages(source,
			CombatEligibilityReason.SOURCE_WILDERNESS_LEVEL_MISMATCH, 7,
			"You can only attack players within 7 levels of your own here",
			"Move further into the wilderness for less restrictions");
		assertLegacyMessages(source,
			CombatEligibilityReason.TARGET_WILDERNESS_LEVEL_MISMATCH, 9,
			"You can only attack players within 9 levels of your own here",
			"Move further into the wilderness for less restrictions");
		assertLegacyMessages(source,
			CombatEligibilityReason.TARGET_REATTACK_PROTECTED, 0);

		final boolean previousPvp = harness.server().getConfig().WANT_PVP;
		harness.server().getConfig().WANT_PVP = true;
		try {
			party(source, target);
			decision = CombatEligibility.evaluate(
				CombatEligibilityRequest.builder(source, target,
					CombatEligibilityPhase.COMMAND, CombatStyle.MELEE)
					.playerAttackRules(true).build());
			assertEquals(CombatEligibilityReason.PARTY_MEMBER, decision.getReason(),
				"party friendly-fire reason");
			assertEquals(Collections.singletonList(
				"You can't attack your party members"),
				CombatEligibilityMessageAdapter.legacyAttackMessages(source, decision),
				"party friendly-fire message");
		} finally {
			harness.server().getConfig().WANT_PVP = previousPvp;
		}

		final Npc banker = harness.npc(NpcId.BANKER.id(), 108, 106);
		decision = CombatEligibility.evaluate(
			CombatEligibilityRequest.builder(source, banker,
				CombatEligibilityPhase.COMMAND, CombatStyle.MELEE)
				.playerAttackRules(true).build());
		assertEquals(CombatEligibilityReason.TARGET_NOT_ATTACKABLE,
			decision.getReason(), "non-attackable NPC reason");

		final Npc summon = harness.npc(3, 109, 106);
		summon.setAttribute("myworld_summon_owner", source.getUsernameHash());
		decision = CombatEligibility.evaluate(
			CombatEligibilityRequest.builder(source, summon,
				CombatEligibilityPhase.COMMAND, CombatStyle.MELEE)
				.summonRules(true).build());
		assertEquals(CombatEligibilityReason.TARGET_IS_SUMMON,
			decision.getReason(), "summon friendly-fire reason");

		final boolean previousLayered = harness.server().getConfig()
			.WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY;
		harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY = true;
		try {
			final Npc otherLevel = harness.npc(
				3, 106, LegacyPackedPointAdapter.LEVEL_STRIDE + 106);
			decision = CombatEligibility.evaluate(
				CombatEligibilityRequest.builder(source, otherLevel,
					CombatEligibilityPhase.APPROACH, CombatStyle.MELEE)
					.sameSpatialDomain(true).build());
			assertEquals(CombatEligibilityReason.DIFFERENT_SPATIAL_DOMAIN,
				decision.getReason(), "layered-domain reason");
		} finally {
			harness.server().getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY =
				previousLayered;
		}
	}

	private static void meleeIntentOrdering(final CurrentCombatHarness harness)
			throws Exception {
		final TransactionDefaultPlugin plugin = new TransactionDefaultPlugin();
		harness.installDefaultPlugin(plugin);
		final Player player = harness.player("intent ordering", 112, 112);
		final Npc first = harness.npc(3, 113, 112);
		final Npc second = harness.npc(3, 112, 113);
		first.setHostile(player, HostileState.HostilityType.PROVOKED);
		second.setHostile(player, HostileState.HostilityType.PROVOKED);

		processNpcAttack(player, first);
		final WalkToAction stale = player.getWalkToAction();
		assertNotNull(stale, "first melee approach");
		processNpcAttack(player, second);
		final WalkToAction winner = player.getWalkToAction();
		assertNotNull(winner, "replacement melee approach");
		assertTrue(winner != stale, "newer command replaces old approach");
		assertEquals(second, player.getAttackTransaction().getPending().getTarget(),
			"newest intent target");

		stale.execute();
		assertNull(player.getPvmMeleeEvent(),
			"stale callback cannot start the old target");
		assertEquals(second, player.getAttackTransaction().getPending().getTarget(),
			"stale callback cannot clear the winner");
		winner.execute();
		final PluginTickEvent pluginEvent = findPluginEvent(
			harness, "TransactionDefaultPlugin.onAttackNpc");
		assertNotNull(pluginEvent, "default attack callback event");
		pluginEvent.run();
		assertEquals(Collections.singletonList("default"), plugin.events(),
			"default callback count");
		assertNotNull(player.getPvmMeleeEvent(), "committed melee event");
		assertEquals(second, player.getPvmMeleeEvent().getTarget(),
			"winning melee target");
		assertNull(player.getAttackTransaction().getPending(),
			"committed intent is cleared");

		final AttackIntent later = player.getAttackTransaction().issue(
			first, CombatStyle.MELEE, AttackIntent.Channel.MELEE,
			AttackIntent.Source.MANUAL, null);
		assertFalse(player.commitPendingMeleeAttack(second),
			"stale delayed plugin callback cannot use the compatibility bridge");
		assertTrue(player.getAttackTransaction().getPending() == later,
			"stale delayed plugin callback cannot clear the current intent");
		player.getAttackTransaction().cancel(later,
			AttackTransactionResult.Reason.SUPERSEDED);
	}

	private static void deniedRetargetPreservesEncounter(
			final CurrentCombatHarness harness) throws Exception {
		final Player player = harness.player("denied retarget", 116, 116);
		final Npc active = harness.npc(3, 117, 116);
		final Npc banker = harness.npc(NpcId.BANKER.id(), 116, 117);
		active.setHostile(player, HostileState.HostilityType.PROVOKED);
		player.startCombat(active);
		assertNotNull(player.getPvmMeleeEvent(), "existing melee encounter");

		processNpcAttack(player, banker);
		final WalkToAction denied = player.getWalkToAction();
		assertNotNull(denied, "non-attackable retarget reaches eligibility callback");
		denied.execute();
		assertTrue(player.getPvmMeleeEvent().isRunning(),
			"denial leaves current event running");
		assertEquals(active, player.getPvmMeleeEvent().getTarget(),
			"denial leaves current target unchanged");
		assertNull(player.getAttackTransaction().getPending(),
			"denied intent rolls back");
	}

	private static void attackIntentLifecycle(final CurrentCombatHarness harness)
			throws Exception {
		final Player player = harness.player("intent lifecycle", 120, 120);
		final Npc target = harness.npc(3, 121, 120);
		target.setHostile(player, HostileState.HostilityType.PROVOKED);
		processNpcAttack(player, target);
		assertNotNull(player.getAttackTransaction().getPending(),
			"walk cancellation fixture intent");
		player.setWalkToAction(null);
		assertNull(player.getAttackTransaction().getPending(),
			"replacing bound walk cancels intent");

		final Player loadout = harness.player("loadout tx", 122, 120);
		harness.equip(loadout, ItemId.SHORTBOW.id(), 1);
		final AttackIntent ranged = loadout.getAttackTransaction().issue(
			target, CombatStyle.RANGED, AttackIntent.Channel.RANGED,
			AttackIntent.Source.MANUAL, null);
		harness.equip(loadout, ItemId.BRONZE_THROWING_DART.id(), 10);
		final AttackTransactionResult changedLoadout =
			loadout.getAttackTransaction().prepare(ranged);
		assertEquals(AttackTransactionResult.Reason.LOADOUT_CHANGED,
			changedLoadout.getReason(), "equipment change rejects stale intent");
		assertNull(loadout.getAttackTransaction().getPending(),
			"loadout-rejected intent is cleared");

		AttackIntent intent = player.getAttackTransaction().issue(
			target, CombatStyle.MELEE, AttackIntent.Channel.MELEE,
			AttackIntent.Source.MANUAL, null);
		for (long tick = 0; tick <= PlayerAttackTransaction.MAX_PENDING_TICKS; tick++) {
			harness.advanceOneCombatTick();
		}
		AttackTransactionResult expired = player.getAttackTransaction().prepare(intent);
		assertEquals(AttackTransactionResult.Reason.EXPIRED, expired.getReason(),
			"bounded stale-intent expiry");
		assertNull(player.getAttackTransaction().getPending(),
			"expired intent cleared");
		intent = player.getAttackTransaction().issue(
			target, CombatStyle.MELEE, AttackIntent.Channel.MELEE,
			AttackIntent.Source.MANUAL, null);
		for (long tick = 0; tick <= PlayerAttackTransaction.MAX_PENDING_TICKS; tick++) {
			harness.advanceOneCombatTick();
		}
		assertNull(player.getAttackTransaction().getPending(),
			"expired intent is cleared without executing its stale callback");

		intent = player.getAttackTransaction().issue(
			target, CombatStyle.MELEE, AttackIntent.Channel.MELEE,
			AttackIntent.Source.MANUAL, null);
		target.advanceCombatLifecycle();
		AttackTransactionResult changed = player.getAttackTransaction().prepare(intent);
		assertEquals(AttackTransactionResult.Reason.PARTICIPANT_CHANGED,
			changed.getReason(), "NPC lifetime change rejects stale intent");

		player.getAttackTransaction().issue(target, CombatStyle.MELEE,
			AttackIntent.Channel.MELEE, AttackIntent.Source.MANUAL, null);
		player.setLoggedIn(false);
		assertNull(player.getAttackTransaction().getPending(),
			"logout clears pending attack");
		player.setLoggedIn(true);

		player.getAttackTransaction().issue(target, CombatStyle.MELEE,
			AttackIntent.Channel.MELEE, AttackIntent.Source.MANUAL, null);
		player.killedBy(target);
		assertNull(player.getAttackTransaction().getPending(),
			"death clears pending attack");
	}

	private static void attackStyleTransactions(
			final CurrentCombatHarness harness) throws Exception {
		final Player ranger = harness.player("range tx", 126, 126);
		final Npc rangeTarget = harness.npc(3, 127, 126);
		harness.equip(ranger, ItemId.SHORTBOW.id(), 1);
		processNpcAttack(ranger, rangeTarget);
		final WalkToAction rangedApproach = ranger.getWalkToAction();
		assertNotNull(rangedApproach, "ranged approach");
		rangedApproach.execute();
		assertNotNull(ranger.getRangeEvent(), "ranged event committed");
		assertEquals(rangeTarget, ranger.getRangeEvent().getTarget(),
			"ranged transaction target");

		final Player thrower = harness.player("throw tx", 130, 126);
		final Npc throwingTarget = harness.npc(3, 131, 126);
		harness.equip(thrower, ItemId.BRONZE_THROWING_DART.id(), 20);
		assertEquals(Integer.valueOf(ItemId.BRONZE_THROWING_DART.id()),
			Integer.valueOf(thrower.getThrowingEquip()), "throwing equipment fixture");
		processNpcAttack(thrower, throwingTarget);
		final WalkToAction throwingApproach = thrower.getWalkToAction();
		assertNotNull(throwingApproach, "throwing approach");
		assertTrue(throwingApproach.shouldExecute(),
			"adjacent throwing approach is executable");
		throwingApproach.execute();
		assertTrue(thrower.getThrowingEvent() != null,
			"throwing event committed; pending="
				+ (thrower.getAttackTransaction().getPending() == null ? "none"
					: thrower.getAttackTransaction().getPending().getChannel())
				+ " equipment=" + thrower.getThrowingEquip()
				+ " busy=" + thrower.isBusy() + " combat=" + thrower.inCombat());
		assertEquals(throwingTarget, thrower.getThrowingEvent().getTarget(),
			"throwing transaction target");

		final Player mage = harness.player("magic tx", 134, 126);
		final Npc magicTarget = harness.npc(3, 135, 126);
		mage.getClientLimitations().maxItemId = Integer.MAX_VALUE;
		for (final Map.Entry<Integer, Integer> rune : harness.server()
				.getEntityHandler().getSpellDef(Spells.WIND_STRIKE)
				.getRunesRequired()) {
			assertTrue(mage.getCarriedItems().getInventory().add(
				new Item(rune.getKey(), rune.getValue() + 10)),
				"magic transaction rune fixture accepts item " + rune.getKey());
			assertTrue(mage.getCarriedItems().getInventory().countId(rune.getKey())
				>= rune.getValue(), "magic transaction rune fixture counts item "
				+ rune.getKey());
		}
		final SpellStruct payload = new SpellStruct();
		payload.setOpcode(OpcodeIn.CAST_ON_NPC);
		payload.spell = Spells.WIND_STRIKE;
		payload.targetIndex = magicTarget.getIndex();
		final int projectilesBefore = countEventsOfType(harness,
			ProjectileEvent.class);
		new SpellHandler().process(payload, mage);
		final WalkToAction magicApproach = mage.getWalkToAction();
		assertNotNull(magicApproach, "manual magic approach");
		magicApproach.execute();
		assertNull(mage.getAttackTransaction().getPending(),
			"manual magic intent committed before projectile settlement");
		assertEquals(Integer.valueOf(projectilesBefore + 1),
			Integer.valueOf(countEventsOfType(harness, ProjectileEvent.class)),
			"manual magic keeps current projectile settlement path");

		final Player noRunes = harness.player("no rune tx", 138, 126);
		final Npc noRuneTarget = harness.npc(3, 139, 126);
		payload.targetIndex = noRuneTarget.getIndex();
		new SpellHandler().process(payload, noRunes);
		final WalkToAction noRuneApproach = noRunes.getWalkToAction();
		assertNotNull(noRuneApproach, "missing-rune magic approach");
		noRuneApproach.execute();
		assertNull(noRunes.getAttackTransaction().getPending(),
			"missing runes roll back the manual magic intent");
		assertEquals(Integer.valueOf(projectilesBefore + 1),
			Integer.valueOf(countEventsOfType(harness, ProjectileEvent.class)),
			"missing runes install no projectile settlement");
	}

	private static void manualIntentPriority(final CurrentCombatHarness harness) {
		final Player player = harness.player("manual priority", 140, 126);
		final Npc manualTarget = harness.npc(3, 141, 126);
		final Npc attacker = harness.npc(3, 140, 127);
		final AttackIntent manual = player.getAttackTransaction().issue(
			manualTarget, CombatStyle.MELEE, AttackIntent.Channel.MELEE,
			AttackIntent.Source.MANUAL, null);
		player.setAutoCastSpell(com.openrsc.server.constants.Spells.WIND_STRIKE);
		assertTrue(MagicCombatEvent.start(player, attacker,
			AttackIntent.Source.RETALIATION),
			"suppressed retaliation is handled without melee fallback");
		assertTrue(player.getAttackTransaction().getPending() == manual,
			"retaliation cannot replace pending manual intent");
		assertNull(player.getMagicCombatEvent(),
			"suppressed retaliation installs no autocast event");
	}

	private static void directionalEngagementOwnership(
			final CurrentCombatHarness harness) {
		final Player ranger = harness.player("direction ranger", 142, 126);
		final Npc firstTarget = harness.npc(3, 143, 126);
		final Npc nextTarget = harness.npc(3, 144, 126);
		final Player secondAttacker = harness.player("direction melee", 143, 127);

		final RangeEvent range = new RangeEvent(
			harness.world(), ranger, 1, firstTarget);
		ranger.setRangeEvent(range);
		assertEquals(firstTarget, ranger.getOutgoingCombatTarget(),
			"ranged event owns an outgoing direction");
		assertTrue(firstTarget.hasIncomingAttackFrom(ranger),
			"ranged target records its incoming direction");
		assertNull(ranger.getOpponent(),
			"ranged authority does not change the legacy melee projection");
		ranger.setSprite(8);
		assertFalse(ranger.inCombat(),
			"ranged authority preserves legacy in-combat restrictions");
		ranger.setSprite(4);

		secondAttacker.setOpponent(firstTarget);
		final PvmMeleeEvent secondEvent = new PvmMeleeEvent(
			harness.world(), secondAttacker, firstTarget);
		secondAttacker.setPvmMeleeEvent(secondEvent);
		assertEquals(Integer.valueOf(2),
			Integer.valueOf(firstTarget.getIncomingCombatAttackerCount()),
			"one target supports independent incoming attackers");
		firstTarget.setOpponent(ranger);
		assertTrue(firstTarget.isMutuallyEngagedWith(ranger),
			"counter direction shares the existing encounter");

		range.reTarget(nextTarget);
		assertEquals(nextTarget, ranger.getOutgoingCombatTarget(),
			"retarget moves only the source outgoing direction");
		assertFalse(firstTarget.hasIncomingAttackFrom(ranger),
			"old target drops the retargeted incoming direction");
		assertTrue(nextTarget.hasIncomingAttackFrom(ranger),
			"new target records the retargeted incoming direction");
		assertEquals(ranger, firstTarget.getOutgoingCombatTarget(),
			"peer counter direction remains independently owned");

		ranger.resetRange();
		secondAttacker.resetCombatEvent();
		firstTarget.setOpponent(null);

		final Npc incomingNpc = harness.npc(3, 142, 129);
		final Player rangedCounter = harness.player("ranged counter", 143, 129);
		incomingNpc.setOpponent(rangedCounter);
		final PvmMeleeEvent incomingMelee = new PvmMeleeEvent(
			harness.world(), incomingNpc, rangedCounter);
		incomingNpc.setPvmMeleeEvent(incomingMelee);
		rangedCounter.setOpponent(incomingNpc);
		final RangeEvent counterRange = new RangeEvent(
			harness.world(), rangedCounter, 1, incomingNpc);
		rangedCounter.setRangeEvent(counterRange);
		incomingMelee.terminate(CombatEngagementTerminalReason.EVENT_ENDED, false);
		assertEquals(counterRange, rangedCounter.getRangeEvent(),
			"ending one direction preserves a ranged counter event");
		assertEquals(incomingNpc, rangedCounter.getOutgoingCombatTarget(),
			"ending one direction preserves the peer counter direction");
		rangedCounter.resetRange();
	}

	private static void staleEventOwnership(
			final CurrentCombatHarness harness) {
		final Player attacker = harness.player("stale owner", 146, 126);
		final Npc firstTarget = harness.npc(3, 147, 126);
		final Npc currentTarget = harness.npc(3, 148, 126);

		final RangeEvent staleRange = new RangeEvent(
			harness.world(), attacker, 1, firstTarget);
		attacker.setRangeEvent(staleRange);
		final RangeEvent currentRange = new RangeEvent(
			harness.world(), attacker, 1, currentTarget);
		attacker.setRangeEvent(currentRange);
		staleRange.run();
		assertEquals(currentRange, attacker.getRangeEvent(),
			"stale ranged callback cannot clear its replacement");
		assertEquals(currentTarget, attacker.getOutgoingCombatTarget(),
			"stale ranged callback cannot close current ownership");
		attacker.resetRange();

		attacker.setOpponent(firstTarget);
		final PvmMeleeEvent staleMelee = new PvmMeleeEvent(
			harness.world(), attacker, firstTarget);
		attacker.setPvmMeleeEvent(staleMelee);
		attacker.setOpponent(currentTarget);
		final PvmMeleeEvent currentMelee = new PvmMeleeEvent(
			harness.world(), attacker, currentTarget);
		attacker.setPvmMeleeEvent(currentMelee);
		staleMelee.resetCombat(false);
		assertEquals(currentMelee, attacker.getPvmMeleeEvent(),
			"stale melee callback cannot clear its replacement");
		assertEquals(currentTarget, attacker.getOutgoingCombatTarget(),
			"stale melee callback cannot close current ownership");
		attacker.resetCombatEvent();
	}

	private static void combatOwnershipLifecycle(
			final CurrentCombatHarness harness) {
		final Player ranger = harness.player("teleport owner", 150, 126);
		final Npc rangeTarget = harness.npc(3, 151, 126);
		final RangeEvent range = new RangeEvent(
			harness.world(), ranger, 1, rangeTarget);
		ranger.setRangeEvent(range);
		ranger.teleport(152, 126);
		assertNull(ranger.getRangeEvent(), "teleport clears ranged event ownership");
		assertFalse(ranger.hasOutgoingAttack(),
			"teleport closes the outgoing direction");
		assertFalse(rangeTarget.hasIncomingAttackFrom(ranger),
			"teleport removes target incoming ownership");
		ranger.terminateCombatOwnership(CombatEngagementTerminalReason.TELEPORT);
		assertFalse(ranger.hasOutgoingAttack(),
			"repeated empty cleanup remains idempotent");

		final Npc incomingAttacker = harness.npc(3, 154, 126);
		final Player teleportedTarget = harness.player("teleport target", 155, 126);
		incomingAttacker.setOpponent(teleportedTarget);
		final PvmMeleeEvent incomingEvent = new PvmMeleeEvent(
			harness.world(), incomingAttacker, teleportedTarget);
		incomingAttacker.setPvmMeleeEvent(incomingEvent);
		teleportedTarget.teleport(156, 126);
		assertNull(incomingAttacker.getPvmMeleeEvent(),
			"target teleport stops an incoming attacker event");
		assertFalse(incomingAttacker.hasOutgoingAttack(),
			"target teleport closes incoming source ownership");

		final Player mage = harness.player("logout owner", 158, 126);
		final Npc magicTarget = harness.npc(3, 159, 126);
		final MagicCombatEvent magic = new MagicCombatEvent(
			harness.world(), mage, 1, magicTarget, Spells.WIND_STRIKE);
		mage.setMagicCombatEvent(magic);
		mage.setLoggedIn(false);
		assertNull(mage.getMagicCombatEvent(), "logout clears magic event ownership");
		assertFalse(magicTarget.hasIncomingAttackFrom(mage),
			"logout removes target incoming ownership");
		mage.setLoggedIn(false);
		mage.setLoggedIn(true);
		assertFalse(mage.hasOutgoingAttack(),
			"repeated logout and reconnect do not revive ownership");

		final Player victim = harness.player("death target", 162, 126);
		final Player attacker = harness.player("death owner", 163, 126);
		final RangeEvent deathRange = new RangeEvent(
			harness.world(), attacker, 1, victim);
		attacker.setRangeEvent(deathRange);
		victim.killedBy(attacker);
		assertNull(attacker.getRangeEvent(),
			"target death stops an incoming ranged event");
		assertFalse(attacker.hasOutgoingAttack(),
			"target death closes attacker ownership");
	}

	private static void passiveRetaliationOwnership(
			final CurrentCombatHarness harness) {
		final Npc attacker = harness.npc(3, 166, 126);
		final Player victim = harness.player("passive target", 167, 126);
		victim.getCache().store("setting_auto_retaliate", false);

		attacker.setOpponent(victim);
		final PvmMeleeEvent attackEvent = new PvmMeleeEvent(
			harness.world(), attacker, victim);
		attacker.setPvmMeleeEvent(attackEvent);
		victim.startPvmCounterCombat(attacker);
		assertTrue(victim.hasIncomingAttackFrom(attacker),
			"passive player records the NPC incoming direction");
		assertFalse(victim.hasOutgoingAttack(),
			"passive player owns no outgoing direction");
		assertNull(victim.getOpponent(),
			"passive player owns no legacy opponent projection");
		assertNull(victim.getPvmMeleeEvent(),
			"passive player owns no melee event");
		assertFalse(victim.inCombat(),
			"passive player preserves current walk and logout restrictions");

		victim.getCache().store("setting_auto_retaliate", true);
		victim.startPvmCounterCombat(attacker);
		assertTrue(victim.hasOutgoingAttack(),
			"enabled retaliation creates an outgoing direction");
		assertEquals(attacker, victim.getOutgoingCombatTarget(),
			"retaliation direction targets the incoming NPC");
		assertTrue(victim.isMutuallyEngagedWith(attacker),
			"retaliation joins the existing encounter");
		assertNotNull(victim.getPvmMeleeEvent(),
			"enabled retaliation owns its melee event");
		victim.resetCombatEvent();
		attacker.resetCombatEvent();
	}

	private static void combatOwnershipAuditRepair(
			final CurrentCombatHarness harness) {
		final Player owner = harness.player("audit owner", 170, 126);
		final Npc target = harness.npc(3, 171, 126);
		final RangeEvent abandoned = new RangeEvent(
			harness.world(), owner, 1, target);
		owner.setRangeEvent(abandoned);
		abandoned.stop();

		final CombatOwnershipAudit observed = owner.auditCombatOwnership(false);
		assertEquals(Integer.valueOf(1),
			Integer.valueOf(observed.getDiscrepancies().size()),
			"read-only audit reports the stale slot");
		assertEquals(Integer.valueOf(0), Integer.valueOf(observed.getRepairedCount()),
			"read-only audit does not mutate ownership");

		final CombatOwnershipAudit repaired = owner.auditCombatOwnership(true);
		assertEquals(Integer.valueOf(1), Integer.valueOf(repaired.getRepairedCount()),
			"explicit repair removes the stale slot");
		assertFalse(owner.hasOutgoingAttack(),
			"explicit repair closes its orphan outgoing direction");
		assertFalse(target.hasIncomingAttackFrom(owner),
			"explicit repair removes peer incoming ownership");
		assertTrue(owner.auditCombatOwnership(false).isConsistent(),
			"repaired authority subsequently audits cleanly");
	}

	private static void reciprocalOwnershipTeardown(
			final CurrentCombatHarness harness) {
		final Player attacker = harness.player("reciprocal one", 174, 126);
		final Player defender = harness.player("reciprocal two", 175, 126);
		attacker.setOpponent(defender);
		defender.setOpponent(attacker);
		final boolean shufflePid = harness.server().getConfig().SHUFFLE_PID_ORDER;
		harness.server().getConfig().SHUFFLE_PID_ORDER = false;
		final CombatEvent event;
		try {
			event = new CombatEvent(harness.world(), attacker, defender);
		} finally {
			harness.server().getConfig().SHUFFLE_PID_ORDER = shufflePid;
		}
		attacker.setCombatEvent(event);
		defender.setCombatEvent(event);
		assertTrue(attacker.isMutuallyEngagedWith(defender),
			"reciprocal melee starts with two owned directions");

		event.terminate(CombatEngagementTerminalReason.EVENT_ENDED);
		assertNull(attacker.getCombatEvent(),
			"reciprocal teardown clears attacker event slot");
		assertNull(defender.getCombatEvent(),
			"reciprocal teardown clears defender event slot");
		assertFalse(attacker.hasOutgoingAttack(),
			"reciprocal teardown closes attacker direction");
		assertFalse(defender.hasOutgoingAttack(),
			"reciprocal teardown closes defender direction");
		assertEquals(Integer.valueOf(0),
			Integer.valueOf(attacker.getIncomingCombatAttackerCount()),
			"reciprocal teardown clears attacker incoming ownership");
		assertEquals(Integer.valueOf(0),
			Integer.valueOf(defender.getIncomingCombatAttackerCount()),
			"reciprocal teardown clears defender incoming ownership");
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
		assertNull(player.getAttackTransaction().getPending(),
			"blocking plugin owns the action and clears the default attack intent");

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

	private static void poisonedExaltedRuneWeaponPower(
			final CurrentCombatHarness harness) {
		final int[] poisonedWeapons = {
			MyWorldItemId.POISONED_EXALTED_RUNE_DAGGER,
			MyWorldItemId.POISONED_EXALTED_RUNE_SPEAR
		};
		for (int weaponId : poisonedWeapons) {
			assertTrue(PoisonPower.isPoisonWeapon(weaponId),
				"Exalted Rune poisoned weapon classification " + weaponId);
			assertEquals(120, PoisonPower.getWeaponMaxPoisonPower(weaponId),
				"Exalted Rune poisoned weapon ceiling " + weaponId);
			assertEquals(48, PoisonPower.getWeaponAppliedPoisonPower(weaponId),
				"Exalted Rune poisoned weapon application " + weaponId);
		}
		assertFalse(PoisonPower.isPoisonWeapon(MyWorldItemId.EXALTED_RUNE_DAGGER),
			"unpoisoned Exalted Rune weapon must remain inert");
		assertFalse(PoisonPower.isPoisonWeapon(MyWorldItemId.EXALTED_RUNE_HATCHET),
			"Exalted Rune gathering tools must remain outside weapon poison");
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
		player.setOpponent(primary);
		final PvmMeleeEvent event =
			new PvmMeleeEvent(harness.world(), player, primary);
		player.setPvmMeleeEvent(event);
		event.run();

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
		player.setThrowingEvent(event);
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

	private static void primaryProjectileVariantSettlement(
			final CurrentCombatHarness harness) throws Exception {
		exercisePrimaryProjectileVariants(harness, 500, 500);

		final Player zeroCaster = harness.player("pj zero", 540, 500);
		final Npc zeroTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 541, 500);
		zeroTarget.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 20, false);
		final ProjectileEvent zeroEvent = projectileEvent(
			harness, zeroCaster, zeroTarget, 0, 2,
			ItemId.BRONZE_THROWING_KNIFE.id(), Projectile.THROWING_KNIFE,
			0);
		invokePrimaryProjectile(zeroEvent);
		assertEquals(20, zeroTarget.getLevel(Skill.HITS.id()),
			"zero thrown impact target Hits");
		assertEquals(0, zeroTarget.getUpdateFlags().getDamage().get().getDamage(),
			"zero thrown impact damage update");
		assertEquals(1, zeroTarget.getUpdateFlags().getHitSplats().size(),
			"zero thrown impact hitsplat cardinality");
		assertEquals(0,
			zeroTarget.getUpdateFlags().getHitSplats().get(0).getAmount(),
			"zero thrown impact hitsplat amount");
		assertEquals(0, projectileContribution(
			zeroTarget, zeroCaster, "getRangeDamageInfoBy"),
			"zero thrown impact contribution");
		assertFalse(zeroTarget.hasDamageFrom(zeroCaster),
			"zero thrown impact does not create damage ownership");

		final Player lethalCaster = harness.player("pj lethal", 544, 500);
		lethalCaster.setKillType(
			com.openrsc.server.model.entity.KillType.MAGIC);
		final Npc lethalTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 545, 500);
		lethalTarget.setShouldRespawn(false);
		lethalTarget.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 5, 5, false);
		final AtomicInteger deathCallbacks = new AtomicInteger();
		lethalTarget.addDeathListener(new NpcLootEvent(
			harness.world(), lethalTarget.getLocation(), lethalTarget.getID(),
			1, ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				deathCallbacks.incrementAndGet();
			}
		});
		final int magicExperienceBefore = lethalCaster.getSkills()
			.getExperience(Skill.MAGIC.id());
		final int hitsExperienceBefore = lethalCaster.getSkills()
			.getExperience(Skill.HITS.id());
		final ProjectileEvent lethalEvent = projectileEvent(
			harness, lethalCaster, lethalTarget, 7, 1, -1,
			Projectile.FIREBALL, 0);
		invokePrimaryProjectile(lethalEvent);
		assertEquals(0, lethalTarget.getLevel(Skill.HITS.id()),
			"lethal projectile target Hits");
		assertEquals(7,
			lethalTarget.getUpdateFlags().getDamage().get().getDamage(),
			"lethal projectile displayed overkill damage");
		assertEquals(1, lethalTarget.getUpdateFlags().getHitSplats().size(),
			"lethal projectile hitsplat cardinality");
		assertEquals(7,
			lethalTarget.getUpdateFlags().getHitSplats().get(0).getAmount(),
			"lethal projectile hitsplat amount");
		assertEquals(5, projectileContribution(
			lethalTarget, lethalCaster, "getMageDamageInfoBy"),
			"lethal projectile contribution caps to actual damage");
		assertEquals(1, deathCallbacks.get(),
			"lethal projectile death callback cardinality");
		assertTrue(lethalTarget.isUnregistering(),
			"lethal projectile terminal unregister state");
		assertEquals(com.openrsc.server.model.entity.KillType.MAGIC,
			lethalCaster.getKillType(), "lethal projectile kill type");
		assertEquals(28, lethalCaster.getSkills()
			.getExperience(Skill.MAGIC.id()) - magicExperienceBefore,
			"lethal projectile Magic XP settlement");
		assertEquals(9, lethalCaster.getSkills()
			.getExperience(Skill.HITS.id()) - hitsExperienceBefore,
			"lethal projectile Hits XP settlement");
	}

	private static void primaryProjectileDamageTransactions(
			final CurrentCombatHarness harness) throws Exception {
		final RecordingDamageObserver observer = damageObserver(harness);
		observer.reset();
		final List<PrimaryProjectileSettlement> settlements =
			exercisePrimaryProjectileVariants(harness, 560, 520);
		assertEquals(Integer.valueOf(settlements.size()),
			Integer.valueOf(observer.results.size()),
			"one transaction result per primary projectile variant");
		for (int index = 0; index < settlements.size(); index++) {
			final PrimaryProjectileSettlement settlement = settlements.get(index);
			final DamageResult result = observer.results.get(index);
			final String path = settlement.variant.label;
			assertEquals(DamageResult.Status.APPLIED_CURRENT_PATH,
				result.getStatus(), path + " transaction status");
			assertEquals(settlement.variant.effectKey,
				result.getRequest().getEffectKey(), path + " stable effect key");
			assertEquals(settlement.variant.style,
				result.getRequest().getStyle(), path + " combat style");
			assertEquals(DamageRequest.SourceCategory.ACTOR,
				result.getRequest().getSourceCategory(), path + " source category");
			assertEquals(settlement.event.getUUID(),
				result.getRequest().getEventId(), path + " event identity");
			assertEquals(settlement.source,
				result.getRequest().getSource(), path + " source identity");
			assertEquals(settlement.target,
				result.getRequest().getTarget(), path + " target identity");
			assertTrue(result.getRequest().getSourceSnapshot()
				.matches(settlement.source), path + " source snapshot");
			assertTrue(result.getRequest().getTargetSnapshot()
				.matches(settlement.target), path + " target snapshot");
			assertEquals(7, result.getRequest().getResolvedDamage(),
				path + " resolved damage");
			assertEquals(settlement.variant.expectedHitSplatType,
				result.getRequest().getHitSplatType(), path + " hitsplat type");
			assertEquals(20, result.getHitsBefore(), path + " Hits before");
			assertEquals(7, result.getActualDamage(), path + " actual damage");
			assertEquals(7, result.getLegacyDamageDealt(),
				path + " legacy downstream damage");
			assertEquals(13, result.getHitsAfter(), path + " Hits after");
			assertEquals(0, result.getOverkillDamage(), path + " overkill");
			assertFalse(result.isTargetTerminal(), path + " terminal state");
		}
	}

	private static void primaryProjectileSharedHitsMitigation(
			final CurrentCombatHarness harness) throws Exception {
		final RecordingDamageObserver observer = damageObserver(harness);
		observer.reset();
		for (int attackType = 1; attackType <= 2; attackType++) {
			final Npc attacker = harness.npc(3, 600 + attackType * 3, 540);
			final Player target = harness.player(
				"pj ten" + attackType, 601 + attackType * 3, 540);
			for (int itemId : new int[] {
				ItemId.GOBLIN_HIDE_COIF.id(),
				ItemId.GOBLIN_HIDE_GLOVES.id(),
				ItemId.GOBLIN_HIDE_BOOTS.id(),
				ItemId.GOBLIN_HIDE_CHAPS.id(),
				ItemId.GOBLIN_HIDE_CUIRASS.id()}) {
				harness.equip(target, itemId, 1);
			}
			target.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), 5, 40, false);
			forceNextLegacyRandomBelow(0.05D);
			final ProjectileEvent event = projectileEvent(
				harness, attacker, target, 7, attackType, -1,
				attackType == 1 ? Projectile.FIREBALL : Projectile.ROCK_THROW,
				0);
			invokePrimaryProjectile(event);
			final String path = attackType == 1
				? "magic projectile" : "ranged projectile";
			assertEquals(1, target.getLevel(Skill.HITS.id()),
				path + " Goblin Tenacity Hits settlement");
			assertEquals(7,
				target.getUpdateFlags().getDamage().get().getDamage(),
				path + " displayed pre-Tenacity damage");
			assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
				path + " Tenacity hitsplat cardinality");
			assertEquals(7,
				target.getUpdateFlags().getHitSplats().get(0).getAmount(),
				path + " pre-Tenacity hitsplat amount");
			final DamageResult result = observer.results.get(
				observer.results.size() - 1);
			assertEquals(4, result.getActualDamage(),
				path + " factual post-Tenacity HP damage");
			assertEquals(5, result.getLegacyDamageDealt(),
				path + " historical post-hit hook damage");
			assertFalse(result.isTargetTerminal(),
				path + " Tenacity prevents terminal settlement");
		}
		assertEquals(2, observer.results.size(),
			"one shared-Hits result per primary projectile style");
	}

	private static void nonPrimaryProjectileExclusion(
			final CurrentCombatHarness harness) throws Exception {
		final RecordingDamageObserver observer = damageObserver(harness);
		observer.reset();
		final Player source = harness.player("pj exclude", 612, 540);
		final Npc compatibilityTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 613, 540);
		compatibilityTarget.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 20, false);
		final ProjectileEvent compatibilityEvent = projectileEvent(
			harness, source, compatibilityTarget, 7, 3, -1,
			Projectile.GNOMEBALL, 0);
		invokePrimaryProjectile(compatibilityEvent);
		assertEquals(13, compatibilityTarget.getLevel(Skill.HITS.id()),
			"non-primary compatibility projectile retains legacy Hits mutation");
		assertEquals(1,
			compatibilityTarget.getUpdateFlags().getHitSplats().size(),
			"non-primary compatibility projectile retains one hitsplat");

		final Npc secondaryTarget = harness.npc(
			NpcId.GREATER_DEMON.id(), 614, 540);
		secondaryTarget.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 20, false);
		CurrentCombatHarness.invokePrivate(compatibilityEvent,
			"inflictChainLightningDamage",
			new Class<?>[] {Player.class, Mob.class, int.class},
			source, secondaryTarget, Integer.valueOf(7));
		assertEquals(13, secondaryTarget.getLevel(Skill.HITS.id()),
			"secondary projectile damage retains legacy Hits mutation");
		assertEquals(1, secondaryTarget.getUpdateFlags().getHitSplats().size(),
			"secondary projectile damage retains one hitsplat");
		assertEquals(0, observer.results.size(),
			"non-primary and secondary projectile damage stay outside A05.3");
	}

	private static List<PrimaryProjectileSettlement>
			exercisePrimaryProjectileVariants(
				final CurrentCombatHarness harness, final int baseX,
				final int packedY) throws Exception {
		final PrimaryProjectileVariant[] variants = {
			new PrimaryProjectileVariant("player magic", 0, 1, -1,
				Projectile.FIREBALL, 0, "getMageDamageInfoBy",
				"projectile-player-magic-primary", CombatStyle.MAGIC, 0),
			new PrimaryProjectileVariant("player ranged", 0, 2,
				ItemId.BRONZE_ARROWS.id(), Projectile.ARROW, 0,
				"getRangeDamageInfoBy", "projectile-player-ranged-primary",
				CombatStyle.RANGED, 0),
			new PrimaryProjectileVariant("player thrown", 0, 2,
				ItemId.BRONZE_THROWING_KNIFE.id(), Projectile.THROWING_KNIFE,
				0, "getRangeDamageInfoBy",
				"projectile-player-thrown-primary", CombatStyle.RANGED, 0),
			new PrimaryProjectileVariant("NPC magic", 1, 1, -1,
				Projectile.ENEMY_FIRE_BASIC, 0, null,
				"projectile-npc-magic-primary", CombatStyle.MAGIC, 0),
			new PrimaryProjectileVariant("NPC ranged", 1, 2, -1,
				Projectile.ROCK_THROW, 0, null,
				"projectile-npc-ranged-primary", CombatStyle.RANGED, 0),
			new PrimaryProjectileVariant("summon magic", 2, 1, -1,
				Projectile.HOLY_MAGIC, 0, "getSummonDamageInfoBy",
				"projectile-summon-magic-primary", CombatStyle.MAGIC, 2),
			new PrimaryProjectileVariant("summon ranged", 2, 2, -1,
				Projectile.ROCK_THROW, 0, "getSummonDamageInfoBy",
				"projectile-summon-ranged-primary", CombatStyle.RANGED, 2),
			new PrimaryProjectileVariant("Iban compatibility", 0, 4, -1,
				Projectile.SKULL,
				com.openrsc.server.model.entity.update.CombatEffect.IBAN_BLAST,
				"getMageDamageInfoBy", "projectile-iban-primary",
				CombatStyle.MAGIC, 0),
			new PrimaryProjectileVariant("cannon compatibility", 0, 5, -1,
				Projectile.SPIKED_BALL, 0, "getRangeDamageInfoBy",
				"projectile-cannon-primary", CombatStyle.RANGED, 0)
		};
		final List<PrimaryProjectileSettlement> settlements =
			new ArrayList<PrimaryProjectileSettlement>();
		for (int index = 0; index < variants.length; index++) {
			final PrimaryProjectileVariant variant = variants[index];
			final int x = baseX + index * 3;
			final Mob source;
			final Mob target;
			final Player contributionOwner;
			if (variant.sourceKind == 0) {
				contributionOwner = harness.player(
					"pj" + baseX + "a" + index, x, packedY);
				source = contributionOwner;
				target = harness.npc(
					NpcId.GREATER_DEMON.id(), x + 1, packedY);
			} else if (variant.sourceKind == 1) {
				contributionOwner = null;
				source = harness.npc(3, x, packedY);
				target = harness.player(
					"pj" + baseX + "n" + index, x + 1, packedY);
			} else {
				contributionOwner = harness.player(
					"pj" + baseX + "o" + index, x, packedY);
				final Npc summon = harness.npc(3, x + 1, packedY);
				summon.setAttribute("myworld_summon_owner",
					contributionOwner.getUsernameHash());
				source = summon;
				target = harness.npc(
					NpcId.GREATER_DEMON.id(), x + 2, packedY);
			}
			target.getSkills().setTemporaryLevelAndMaxStat(
				Skill.HITS.id(), 20, 20, false);
			final int magicExperienceBefore = contributionOwner == source
				? contributionOwner.getSkills().getExperience(Skill.MAGIC.id()) : -1;
			final int rangedExperienceBefore = contributionOwner == source
				? contributionOwner.getSkills().getExperience(Skill.RANGED.id()) : -1;
			final int hitsExperienceBefore = contributionOwner == source
				? contributionOwner.getSkills().getExperience(Skill.HITS.id()) : -1;
			final ProjectileEvent event = projectileEvent(
				harness, source, target, 7, variant.attackType,
				variant.poisonWeaponId, variant.projectileType,
				variant.impactEffectType);
			final Projectile launched = target.getUpdateFlags()
				.getProjectile().get();
			assertNotNull(launched, variant.label + " launch visual");
			assertEquals(variant.projectileType, launched.getType(),
				variant.label + " projectile type");
			invokePrimaryProjectile(event);
			assertEquals(13, target.getLevel(Skill.HITS.id()),
				variant.label + " target Hits");
			assertEquals(7,
				target.getUpdateFlags().getDamage().get().getDamage(),
				variant.label + " damage update");
			assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
				variant.label + " hitsplat cardinality");
			assertEquals(7,
				target.getUpdateFlags().getHitSplats().get(0).getAmount(),
				variant.label + " hitsplat amount");
			assertEquals(variant.expectedHitSplatType,
				target.getUpdateFlags().getHitSplats().get(0).getType(),
				variant.label + " hitsplat type");
			if (variant.impactEffectType > 0) {
				assertNotNull(target.getUpdateFlags().getCombatEffect().get(),
					variant.label + " impact effect");
				assertEquals(variant.impactEffectType, target.getUpdateFlags()
					.getCombatEffect().get().getEffectType(),
					variant.label + " impact effect type");
			}
			if (variant.contributionMethod != null) {
				assertEquals(7, projectileContribution(
					(Npc) target, contributionOwner,
					variant.contributionMethod),
					variant.label + " contribution");
				assertTrue(((Npc) target).hasDamageFrom(contributionOwner),
					variant.label + " damage ownership");
			}
			if (contributionOwner == source) {
				assertEquals(magicExperienceBefore, contributionOwner.getSkills()
					.getExperience(Skill.MAGIC.id()),
					variant.label + " impact does not duplicate Magic XP");
				assertEquals(rangedExperienceBefore, contributionOwner.getSkills()
					.getExperience(Skill.RANGED.id()),
					variant.label + " impact does not duplicate Ranged XP");
				assertEquals(hitsExperienceBefore, contributionOwner.getSkills()
					.getExperience(Skill.HITS.id()),
					variant.label + " impact does not duplicate Hits XP");
			}
			settlements.add(new PrimaryProjectileSettlement(
				variant, event, source, target));
		}
		return settlements;
	}

	private static ProjectileEvent projectileEvent(
			final CurrentCombatHarness harness, final Mob source,
			final Mob target, final int damage, final int attackType,
			final int poisonWeaponId, final int projectileType,
			final int impactEffectType) {
		return new ProjectileEvent(harness.world(), source, target, damage,
			attackType, true, poisonWeaponId, 0, 0, 0, 0,
			DuplicationStrategy.ALLOW_MULTIPLE, projectileType,
			impactEffectType, true);
	}

	private static void invokePrimaryProjectile(final ProjectileEvent event)
			throws Exception {
		CurrentCombatHarness.invokePrivate(event, "projectileDamage",
			new Class<?>[0]);
	}

	@SuppressWarnings("unchecked")
	private static int projectileContribution(final Npc target,
			final Player owner, final String methodName) throws Exception {
		final Pair<Integer, Long> contribution = (Pair<Integer, Long>)
			CurrentCombatHarness.invokePrivate(target, methodName,
				new Class<?>[] {UUID.class}, owner.getUUID());
		return contribution.getLeft().intValue();
	}

	private static void pvmPrimaryMeleeSettlement(
			final CurrentCombatHarness harness) throws Exception {
		final PrimaryMeleeSettlement settlement = exercisePrimaryMeleeSettlement(
			harness, true, 410);
		assertPrimaryMeleeSettlement(settlement, "PvM primary melee");
	}

	private static void reciprocalPrimaryMeleeSettlement(
			final CurrentCombatHarness harness) throws Exception {
		final PrimaryMeleeSettlement reciprocal = exercisePrimaryMeleeSettlement(
			harness, false, 430);
		assertPrimaryMeleeSettlement(reciprocal, "reciprocal primary melee");
		final PrimaryMeleeSettlement pvm = exercisePrimaryMeleeSettlement(
			harness, true, 450);
		assertEquals(pvm.meleeExperience, reciprocal.meleeExperience,
			"primary melee paths preserve identical melee XP settlement");
		assertEquals(pvm.hitsExperience, reciprocal.hitsExperience,
			"primary melee paths preserve identical Hits XP settlement");
	}

	private static PrimaryMeleeSettlement exercisePrimaryMeleeSettlement(
			final CurrentCombatHarness harness, final boolean pvm,
			final int baseX) throws Exception {
		final Player attacker = harness.player(
			"melee " + baseX + " a", baseX, 410);
		harness.equip(attacker, DRAGONSTONE_BANGLE_OF_SIPHONING, 1);
		attacker.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 40, false);

		final Npc nonlethal = harness.npc(
			NpcId.GREATER_DEMON.id(), baseX + 1, 410);
		nonlethal.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 20, false);
		invokePrimaryMelee(pvm, harness, attacker, nonlethal, 7);
		final int nonlethalContribution = combatContribution(
			nonlethal, attacker);

		final Npc zero = harness.npc(
			NpcId.GREATER_DEMON.id(), baseX, 411);
		zero.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 20, false);
		invokePrimaryMelee(pvm, harness, attacker, zero, 0);
		final int zeroContribution = combatContribution(zero, attacker);

		final Player lethalAttacker = harness.player(
			"melee " + baseX + " b", baseX, 413);
		final Npc lethal = harness.npc(
			NpcId.GREATER_DEMON.id(), baseX + 1, 413);
		lethal.setShouldRespawn(false);
		lethal.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 5, 5, false);
		final AtomicInteger deathCallbacks = new AtomicInteger();
		lethal.addDeathListener(new NpcLootEvent(
			harness.world(), lethal.getLocation(), lethal.getID(),
			1, ItemId.COINS.id()) {
			@Override
			public void onLootNpcDeath(final Player ignoredPlayer,
					final Npc ignoredNpc) {
				deathCallbacks.incrementAndGet();
			}
		});
		final int meleeExperienceBefore = lethalAttacker.getSkills()
			.getExperience(Skill.MELEE.id());
		final int hitsExperienceBefore = lethalAttacker.getSkills()
			.getExperience(Skill.HITS.id());
		invokePrimaryMelee(pvm, harness, lethalAttacker, lethal, 7);

		final PrimaryMeleeSettlement result = new PrimaryMeleeSettlement();
		result.hitsMade = attacker.getHitsMade();
		result.nonlethalHits = nonlethal.getLevel(Skill.HITS.id());
		result.nonlethalDamageUpdate = nonlethal.getUpdateFlags()
			.getDamage().get().getDamage();
		result.nonlethalHitSplatCount = nonlethal.getUpdateFlags()
			.getHitSplats().size();
		result.nonlethalHitSplatType = nonlethal.getUpdateFlags()
			.getHitSplats().get(0).getType();
		result.nonlethalHitSplatAmount = nonlethal.getUpdateFlags()
			.getHitSplats().get(0).getAmount();
		result.nonlethalContribution = nonlethalContribution;
		result.nonlethalDamageOwner = nonlethal.hasDamageFrom(attacker);
		result.attackerHitsAfterLifesteal = attacker.getLevel(Skill.HITS.id());
		result.zeroHits = zero.getLevel(Skill.HITS.id());
		result.zeroDamageUpdate = zero.getUpdateFlags().getDamage().get().getDamage();
		result.zeroHitSplatCount = zero.getUpdateFlags().getHitSplats().size();
		result.zeroHitSplatAmount = zero.getUpdateFlags()
			.getHitSplats().get(0).getAmount();
		result.zeroContribution = zeroContribution;
		result.zeroDamageOwner = zero.hasDamageFrom(attacker);
		result.lethalHits = lethal.getLevel(Skill.HITS.id());
		result.lethalDamageUpdate = lethal.getUpdateFlags()
			.getDamage().get().getDamage();
		result.lethalHitSplatCount = lethal.getUpdateFlags().getHitSplats().size();
		result.lethalHitSplatAmount = lethal.getUpdateFlags()
			.getHitSplats().get(0).getAmount();
		result.lethalContribution = combatContribution(lethal, lethalAttacker);
		result.deathCallbacks = deathCallbacks.get();
		result.unregistering = lethal.isUnregistering();
		result.attackerCombatState = lethalAttacker.getCombatState();
		result.targetCombatState = lethal.getCombatState();
		result.killType = lethalAttacker.getKillType();
		result.meleeExperience = lethalAttacker.getSkills()
			.getExperience(Skill.MELEE.id()) - meleeExperienceBefore;
		result.hitsExperience = lethalAttacker.getSkills()
			.getExperience(Skill.HITS.id()) - hitsExperienceBefore;
		return result;
	}

	private static void invokePrimaryMelee(final boolean pvm,
			final CurrentCombatHarness harness, final Mob hitter,
			final Mob target, final int damage) throws Exception {
		final Object event = pvm
			? new PvmMeleeEvent(harness.world(), hitter, target)
			: new CombatEvent(harness.world(), hitter, target);
		CurrentCombatHarness.invokePrivate(event, "inflictDamage",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			hitter, target, Integer.valueOf(damage));
	}

	@SuppressWarnings("unchecked")
	private static int combatContribution(final Npc target,
			final Player attacker) throws Exception {
		final Pair<Integer, Long> contribution = (Pair<Integer, Long>)
			CurrentCombatHarness.invokePrivate(target, "getCombatDamageInfoBy",
				new Class<?>[] {UUID.class}, attacker.getUUID());
		return contribution.getLeft().intValue();
	}

	private static void assertPrimaryMeleeSettlement(
			final PrimaryMeleeSettlement actual, final String path) {
		assertEquals(2, actual.hitsMade,
			path + " counts zero and nonlethal attack attempts");
		assertEquals(13, actual.nonlethalHits, path + " nonlethal Hits");
		assertEquals(7, actual.nonlethalDamageUpdate,
			path + " nonlethal damage update");
		assertEquals(1, actual.nonlethalHitSplatCount,
			path + " nonlethal hitsplat cardinality");
		assertEquals(0, actual.nonlethalHitSplatType,
			path + " nonlethal hitsplat type");
		assertEquals(7, actual.nonlethalHitSplatAmount,
			path + " nonlethal hitsplat amount");
		assertEquals(7, actual.nonlethalContribution,
			path + " nonlethal contribution");
		assertTrue(actual.nonlethalDamageOwner,
			path + " nonlethal damage ownership");
		assertEquals(21, actual.attackerHitsAfterLifesteal,
			path + " lifesteal follows nonlethal damage");
		assertEquals(20, actual.zeroHits, path + " zero-hit target Hits");
		assertEquals(0, actual.zeroDamageUpdate, path + " zero damage update");
		assertEquals(1, actual.zeroHitSplatCount,
			path + " zero hitsplat cardinality");
		assertEquals(0, actual.zeroHitSplatAmount,
			path + " zero hitsplat amount");
		assertEquals(0, actual.zeroContribution,
			path + " zero contribution amount");
		assertFalse(actual.zeroDamageOwner,
			path + " zero contribution is not damage ownership");
		assertEquals(0, actual.lethalHits, path + " lethal target Hits");
		assertEquals(7, actual.lethalDamageUpdate,
			path + " lethal displayed overkill damage");
		assertEquals(1, actual.lethalHitSplatCount,
			path + " lethal hitsplat cardinality");
		assertEquals(7, actual.lethalHitSplatAmount,
			path + " lethal hitsplat amount");
		assertEquals(5, actual.lethalContribution,
			path + " contribution caps to actual lethal damage");
		assertEquals(1, actual.deathCallbacks,
			path + " death listener cardinality");
		assertTrue(actual.unregistering,
			path + " terminal NPC unregister state");
		assertEquals(com.openrsc.server.model.states.CombatState.WON,
			actual.attackerCombatState, path + " attacker terminal state");
		assertEquals(com.openrsc.server.model.states.CombatState.LOST,
			actual.targetCombatState, path + " target terminal state");
		assertEquals(com.openrsc.server.model.entity.KillType.COMBAT,
			actual.killType, path + " kill type");
		assertEquals(27, actual.meleeExperience,
			path + " melee XP settlement");
		assertEquals(9, actual.hitsExperience,
			path + " Hits XP settlement");
	}

	private static void resolvedDamageContracts(
			final CurrentCombatHarness harness) {
		final Player source = harness.player("damage contract source", 380, 380);
		final Npc target = harness.npc(3, 381, 380);
		final DamageRequest request = DamageRequest.resolvedLegacy(
			source, target, DamageRequest.SourceCategory.ACTOR,
			"fixture-resolved-damage", 14)
			.style(CombatStyle.MELEE)
			.hitSplatType(0)
			.build();
		assertEquals(DamageRequest.InputStage.RESOLVED_LEGACY,
			request.getInputStage(), "damage input stage");
		assertEquals(DamageRequest.SourceCategory.ACTOR,
			request.getSourceCategory(), "damage source category");
		assertTrue(request.getSourceSnapshot().matches(source),
			"source snapshot starts current");
		assertTrue(request.getTargetSnapshot().matches(target),
			"target snapshot starts current");

		final DamageResult result = DamageResult.observedCurrentPath(
			request, 10, 0);
		assertEquals(DamageResult.Status.OBSERVED_CURRENT_PATH,
			result.getStatus(), "observation-only result status");
		assertEquals(10, result.getActualDamage(), "actual damage cap");
		assertEquals(4, result.getOverkillDamage(), "observed overkill");
		assertTrue(result.isTargetTerminal(), "terminal Hits outcome");
		final DamageResult compatibilityAdjusted = DamageResult.appliedCurrentPath(
			request, 10, 1);
		assertEquals(DamageResult.Status.APPLIED_CURRENT_PATH,
			compatibilityAdjusted.getStatus(), "applied result status");
		assertEquals(9, compatibilityAdjusted.getActualDamage(),
			"factual damage after shared Hits compatibility mitigation");
		assertEquals(10, compatibilityAdjusted.getLegacyDamageDealt(),
			"legacy post-hit value remains request-capped");

		source.advanceCombatLifecycle();
		assertFalse(request.getSourceSnapshot().matches(source),
			"captured source generation must not silently refresh");
		boolean rejectedNegative = false;
		try {
			DamageRequest.resolvedLegacy(source, target,
				DamageRequest.SourceCategory.ACTOR, "invalid", -1);
		} catch (final IllegalArgumentException expected) {
			rejectedNegative = true;
		}
		assertTrue(rejectedNegative,
			"negative resolved damage must be rejected by the contract");
	}

	private static void primaryMeleeDamageTransactions(
			final CurrentCombatHarness harness) throws Exception {
		final RecordingDamageObserver observer = damageObserver(harness);
		observer.reset();
		final Player attacker = harness.player("damage observer", 390, 390);
		final Npc target = harness.npc(3, 391, 390);
		target.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 20, false);
		final PvmMeleeEvent event = new PvmMeleeEvent(
			harness.world(), attacker, target);

		CurrentCombatHarness.invokePrivate(event, "inflictDamage",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			attacker, target, Integer.valueOf(7));

		assertEquals(13, target.getLevel(Skill.HITS.id()),
			"current melee Hits subtraction");
		assertEquals(7, target.getUpdateFlags().getDamage().get().getDamage(),
			"current damage update amount");
		assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
			"current hitsplat cardinality");
		assertEquals(7,
			target.getUpdateFlags().getHitSplats().get(0).getAmount(),
			"current hitsplat amount");
		assertEquals(1, observer.results.size(),
			"one factual observation per primary mutation");
		final DamageResult result = observer.results.get(0);
		assertEquals(DamageResult.Status.APPLIED_CURRENT_PATH,
			result.getStatus(), "PvM transaction result status");
		assertEquals("pvm-melee-primary",
			result.getRequest().getEffectKey(), "stable effect key");
		assertEquals(CombatStyle.MELEE, result.getRequest().getStyle(),
			"observed combat style");
		assertEquals(event.getUUID(), result.getRequest().getEventId(),
			"originating event identity");
		assertEquals(20, result.getHitsBefore(), "observed Hits before");
		assertEquals(7, result.getActualDamage(), "observed actual damage");
		assertEquals(13, result.getHitsAfter(), "observed Hits after");
		assertEquals(0, result.getOverkillDamage(), "non-overkill observation");
		assertFalse(result.isTargetTerminal(), "nonterminal observation");

		final Player reciprocalAttacker = harness.player(
			"damage reciprocal", 395, 390);
		final Npc reciprocalTarget = harness.npc(3, 396, 390);
		reciprocalTarget.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 20, false);
		final CombatEvent reciprocalEvent = new CombatEvent(
			harness.world(), reciprocalAttacker, reciprocalTarget);
		CurrentCombatHarness.invokePrivate(reciprocalEvent, "inflictDamage",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			reciprocalAttacker, reciprocalTarget, Integer.valueOf(7));

		assertEquals(13, reciprocalTarget.getLevel(Skill.HITS.id()),
			"reciprocal melee Hits subtraction");
		assertEquals(1, reciprocalTarget.getUpdateFlags().getHitSplats().size(),
			"reciprocal melee hitsplat cardinality");
		assertEquals(2, observer.results.size(),
			"one transaction result from each primary melee path");
		final DamageResult reciprocalResult = observer.results.get(1);
		assertEquals(DamageResult.Status.APPLIED_CURRENT_PATH,
			reciprocalResult.getStatus(), "reciprocal transaction result status");
		assertEquals("reciprocal-melee-primary",
			reciprocalResult.getRequest().getEffectKey(),
			"reciprocal stable effect key");
		assertEquals(CombatStyle.MELEE,
			reciprocalResult.getRequest().getStyle(),
			"reciprocal combat style");
		assertEquals(reciprocalEvent.getUUID(),
			reciprocalResult.getRequest().getEventId(),
			"reciprocal event identity");
		assertEquals(7, reciprocalResult.getActualDamage(),
			"reciprocal actual damage");
		assertEquals(13, reciprocalResult.getHitsAfter(),
			"reciprocal Hits after");
	}

	private static void damageObserverFailureIsolation(
			final CurrentCombatHarness harness) throws Exception {
		final RecordingDamageObserver observer = damageObserver(harness);
		observer.reset();
		observer.throwOnEnableCheck = true;
		final Player setupAttacker = harness.player(
			"failing observer setup", 400, 400);
		final Npc setupTarget = harness.npc(3, 401, 400);
		setupTarget.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 20, false);
		final PvmMeleeEvent setupEvent = new PvmMeleeEvent(
			harness.world(), setupAttacker, setupTarget);
		CurrentCombatHarness.invokePrivate(setupEvent, "inflictDamage",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			setupAttacker, setupTarget, Integer.valueOf(7));
		assertEquals(13, setupTarget.getLevel(Skill.HITS.id()),
			"observer enable-check failure cannot prevent current damage");
		assertEquals(1, setupTarget.getUpdateFlags().getHitSplats().size(),
			"observer setup failure cannot suppress the hitsplat");
		assertEquals(0, observer.calls,
			"failed observation setup cannot publish a partial result");

		observer.throwOnEnableCheck = false;
		observer.throwOnObservation = true;
		final Player attacker = harness.player("failing observer", 405, 400);
		final Npc target = harness.npc(3, 406, 400);
		target.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 20, 20, false);
		final PvmMeleeEvent event = new PvmMeleeEvent(
			harness.world(), attacker, target);

		CurrentCombatHarness.invokePrivate(event, "inflictDamage",
			new Class<?>[] {Mob.class, Mob.class, int.class},
			attacker, target, Integer.valueOf(7));

		assertEquals(13, target.getLevel(Skill.HITS.id()),
			"observer failure cannot roll back or repeat current damage");
		assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
			"observer failure cannot duplicate the hitsplat");
		assertEquals(1, observer.calls,
			"failing observer is invoked exactly once");
		observer.throwOnObservation = false;
	}

	private static void primaryMeleeSharedHitsMitigation(
			final CurrentCombatHarness harness) throws Exception {
		final RecordingDamageObserver observer = damageObserver(harness);
		observer.reset();
		assertSharedHitsMitigation(harness, observer, true, 460);
		assertSharedHitsMitigation(harness, observer, false, 470);
		assertEquals(2, observer.results.size(),
			"one shared-Hits result from each primary melee path");
	}

	private static void assertSharedHitsMitigation(
			final CurrentCombatHarness harness,
			final RecordingDamageObserver observer, final boolean pvm,
			final int baseX) throws Exception {
		final Npc attacker = harness.npc(3, baseX, 420);
		final Player target = harness.player("goblin " + baseX, baseX + 1, 420);
		for (int itemId : new int[] {
				ItemId.GOBLIN_HIDE_COIF.id(),
				ItemId.GOBLIN_HIDE_GLOVES.id(),
				ItemId.GOBLIN_HIDE_BOOTS.id(),
				ItemId.GOBLIN_HIDE_CHAPS.id(),
				ItemId.GOBLIN_HIDE_CUIRASS.id()}) {
			harness.equip(target, itemId, 1);
		}
		target.getSkills().setTemporaryLevelAndMaxStat(
			Skill.HITS.id(), 5, 40, false);
		forceNextLegacyRandomBelow(0.05D);
		invokePrimaryMelee(pvm, harness, attacker, target, 7);

		final String path = pvm ? "PvM" : "reciprocal";
		assertEquals(1, target.getLevel(Skill.HITS.id()),
			path + " Goblin Tenacity Hits settlement");
		assertEquals(7, target.getUpdateFlags().getDamage().get().getDamage(),
			path + " preserves displayed pre-Tenacity damage");
		assertEquals(1, target.getUpdateFlags().getHitSplats().size(),
			path + " Tenacity hitsplat cardinality");
		assertEquals(7, target.getUpdateFlags().getHitSplats().get(0).getAmount(),
			path + " preserves pre-Tenacity hitsplat amount");
		final DamageResult result = observer.results.get(observer.results.size() - 1);
		assertEquals(4, result.getActualDamage(),
			path + " factual post-Tenacity HP damage");
		assertEquals(5, result.getLegacyDamageDealt(),
			path + " historical post-hit hook damage");
		assertFalse(result.isTargetTerminal(),
			path + " Tenacity prevents terminal settlement");
	}

	private static void forceNextLegacyRandomBelow(final double threshold) {
		for (long seed = 0L; seed < 100_000L; seed++) {
			final java.util.Random candidate = new java.util.Random(seed);
			if (candidate.nextDouble() < threshold) {
				DataConversions.getRandom().setSeed(seed);
				return;
			}
		}
		throw new AssertionError("No deterministic legacy random seed found");
	}

	private static RecordingDamageObserver damageObserver(
			final CurrentCombatHarness harness) {
		return (RecordingDamageObserver) harness.server()
			.getCombatDamageObserver();
	}

	static void resetDamageObserver(final CurrentCombatHarness harness) {
		damageObserver(harness).reset();
	}

	static List<DamageResult> observedDamageResults(
			final CurrentCombatHarness harness) {
		return new ArrayList<DamageResult>(damageObserver(harness).results);
	}

	private static void processNpcAttack(final Player player, final Npc npc)
			throws Exception {
		final TargetMobStruct payload = new TargetMobStruct();
		payload.setOpcode(OpcodeIn.NPC_ATTACK);
		payload.serverIndex = npc.getIndex();
		new AttackHandler().process(payload, player);
	}

	private static PluginTickEvent findPluginEvent(
			final CurrentCombatHarness harness, final String descriptor) {
		for (GameTickEvent event : harness.server().getGameEventHandler().getEvents()) {
			if (event instanceof PluginTickEvent
				&& descriptor.equals(event.getDescriptor())) {
				return (PluginTickEvent) event;
			}
		}
		return null;
	}

	private static boolean hasEventType(final CurrentCombatHarness harness,
			final Class<?> eventType) {
		return countEventsOfType(harness, eventType) > 0;
	}

	private static int countEventsOfType(final CurrentCombatHarness harness,
			final Class<?> eventType) {
		int count = 0;
		for (GameTickEvent event : harness.server().getGameEventHandler().getEvents()) {
			if (eventType.isInstance(event)) count++;
		}
		return count;
	}

	private static void assertLegacyMessages(final Player player,
			final CombatEligibilityReason reason, final int detail,
			final String... expected) {
		assertEquals(Arrays.asList(expected),
			CombatEligibilityMessageAdapter.legacyAttackMessages(
				player, reason, detail),
			"legacy message mapping for " + reason);
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

	private static final class PrimaryProjectileVariant {
		private final String label;
		private final int sourceKind;
		private final int attackType;
		private final int poisonWeaponId;
		private final int projectileType;
		private final int impactEffectType;
		private final String contributionMethod;
		private final String effectKey;
		private final CombatStyle style;
		private final int expectedHitSplatType;

		private PrimaryProjectileVariant(final String label,
				final int sourceKind, final int attackType,
				final int poisonWeaponId, final int projectileType,
				final int impactEffectType, final String contributionMethod,
				final String effectKey, final CombatStyle style,
				final int expectedHitSplatType) {
			this.label = label;
			this.sourceKind = sourceKind;
			this.attackType = attackType;
			this.poisonWeaponId = poisonWeaponId;
			this.projectileType = projectileType;
			this.impactEffectType = impactEffectType;
			this.contributionMethod = contributionMethod;
			this.effectKey = effectKey;
			this.style = style;
			this.expectedHitSplatType = expectedHitSplatType;
		}
	}

	private static final class PrimaryProjectileSettlement {
		private final PrimaryProjectileVariant variant;
		private final ProjectileEvent event;
		private final Mob source;
		private final Mob target;

		private PrimaryProjectileSettlement(
				final PrimaryProjectileVariant variant,
				final ProjectileEvent event, final Mob source,
				final Mob target) {
			this.variant = variant;
			this.event = event;
			this.source = source;
			this.target = target;
		}
	}

	/** Closed fixture tuple for the two pre-transaction direct-melee paths. */
	private static final class PrimaryMeleeSettlement {
		private int hitsMade;
		private int nonlethalHits;
		private int nonlethalDamageUpdate;
		private int nonlethalHitSplatCount;
		private int nonlethalHitSplatType;
		private int nonlethalHitSplatAmount;
		private int nonlethalContribution;
		private boolean nonlethalDamageOwner;
		private int attackerHitsAfterLifesteal;
		private int zeroHits;
		private int zeroDamageUpdate;
		private int zeroHitSplatCount;
		private int zeroHitSplatAmount;
		private int zeroContribution;
		private boolean zeroDamageOwner;
		private int lethalHits;
		private int lethalDamageUpdate;
		private int lethalHitSplatCount;
		private int lethalHitSplatAmount;
		private int lethalContribution;
		private int deathCallbacks;
		private boolean unregistering;
		private com.openrsc.server.model.states.CombatState attackerCombatState;
		private com.openrsc.server.model.states.CombatState targetCombatState;
		private com.openrsc.server.model.entity.KillType killType;
		private int meleeExperience;
		private int hitsExperience;
	}

	private static final class RecordingDamageObserver
			implements CombatDamageObserver {
		private final List<DamageResult> results =
			new ArrayList<DamageResult>();
		private boolean throwOnObservation;
		private boolean throwOnEnableCheck;
		private boolean enabled;
		private int calls;

		@Override
		public boolean isEnabled() {
			if (throwOnEnableCheck) {
				throw new IllegalStateException(
					"deliberate observer enable-check failure");
			}
			return enabled;
		}

		@Override
		public void onDamageObserved(final DamageResult result) {
			calls++;
			if (throwOnObservation) {
				throw new IllegalStateException("deliberate observer failure");
			}
			results.add(result);
		}

		void reset() {
			results.clear();
			calls = 0;
			throwOnObservation = false;
			throwOnEnableCheck = false;
		}
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

	private static final class TransactionDefaultPlugin
			implements DefaultHandler, AttackNpcTrigger {
		private final List<String> events = Collections.synchronizedList(
			new ArrayList<String>());

		@Override
		public boolean blockAttackNpc(final Player player, final Npc npc) {
			return false;
		}

		@Override
		public void onAttackNpc(final Player player, final Npc npc) {
			events.add("default");
			player.commitPendingMeleeAttack(npc);
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
