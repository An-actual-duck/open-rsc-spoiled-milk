package com.openrsc.server.model.combat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Descriptive inventory of current combat effect semantics.
 *
 * <p>A descriptor is deliberately not executable. It cannot select a target,
 * draw random values, mutate charge state, apply an effect, or settle damage.
 * Existing event and content owners retain all of those responsibilities. The
 * stable semantic keys here are also intentionally distinct from the A05
 * {@link SecondaryEffectPolicy} damage-settlement keys: several effects may
 * continue to share one settlement helper without losing their own identity.</p>
 */
public enum SecondaryEffectDescriptor {
	COMPATIBILITY_PLAYER_POISON_SCRIPT("semantic.compatibility-player-poison-script",
		phases(Phase.PRE_PRIMARY_DAMAGE), styles(Style.MELEE),
		ParticipantGate.COMPATIBILITY_PLAYER_TO_PLAYER, ZeroDamageRule.ATTACK_CONTEXT,
		RandomTiming.DYNAMIC_SCRIPT_DRAW, StateOwner.COMPATIBILITY_SCRIPT,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.MESSAGE,
		"CombatScriptLoader/PlayerPoisonScript", "combat runtime and poison fixtures"),
	NPC_POISON_SCRIPT("semantic.npc-poison-script",
		phases(Phase.PRE_PRIMARY_DAMAGE), styles(Style.MELEE),
		ParticipantGate.NPC_TO_PLAYER, ZeroDamageRule.ATTACK_CONTEXT,
		RandomTiming.DYNAMIC_SCRIPT_DRAW, StateOwner.TARGET_DOT,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.NONE,
		"CombatScriptLoader/NpcPoisonPlayerScript", "combat runtime and poison fixtures"),
	BODY_ROBE_WEAPON_POWER_CHARGE("semantic.body-robe-weapon-power-charge",
		phases(Phase.PRE_PRIMARY_SETTLEMENT), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_DEFENDER, ZeroDamageRule.POSITIVE_POST_RESISTANCE,
		RandomTiming.NONE, StateOwner.DEFENDER_EQUIPMENT_CHARGE,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.NONE,
		"Player.applyRobeDamageMitigation", "enchanted robe fixtures"),
	SUMMON_DAMAGE_ABSORPTION("semantic.summon-damage-absorption",
		phases(Phase.PRE_PRIMARY_SETTLEMENT), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.NPC_TO_PLAYER, ZeroDamageRule.POSITIVE_PENDING_DAMAGE,
		RandomTiming.TRAIT_CONDITIONAL_DRAW, StateOwner.SUMMON_TRAIT,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.MIXED,
		"Summoning.applySummonDamageAbsorption", "summoning combat fixtures"),
	FROSTBITE_REFLECTION("semantic.frostbite-reflection",
		phases(Phase.PRE_PRIMARY_SETTLEMENT), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.CURRENT_ATTACK_PAIR, ZeroDamageRule.POSITIVE_PENDING_DAMAGE,
		RandomTiming.NONE, StateOwner.TARGET_DEBUFF,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"CombatEvent/PvmMeleeEvent/ProjectileEvent", "frostbite reflection characterization"),
	CLERIC_PROTECTION("semantic.cleric-ward-aegis-protection",
		phases(Phase.PRE_PRIMARY_SETTLEMENT), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_DEFENDER_PVE, ZeroDamageRule.POSITIVE_PENDING_DAMAGE,
		RandomTiming.NONE, StateOwner.CLERIC_RECIPIENT_CHARGES,
		RecursionPolicy.ROOT_ATTACK_ONLY, PresentationPolicy.STATUS_COUNTER,
		"ClericDirectCombatRuntime.beforeDirectDamage", "Cleric direct-effect ordering fixture"),
	CLERIC_ZEAL("semantic.cleric-zeal-damage",
		phases(Phase.PRE_PRIMARY_SETTLEMENT), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_PVE, ZeroDamageRule.POSITIVE_PENDING_DAMAGE,
		RandomTiming.NONE, StateOwner.CLERIC_RECIPIENT_FRACTION,
		RecursionPolicy.ROOT_ATTACK_ONLY, PresentationPolicy.STATUS_COUNTER,
		"ClericDirectCombatRuntime.beforeDirectDamage", "Cleric direct-effect ordering fixture"),

	SUMMON_OWNER_ASSIST("semantic.summon-owner-assist",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_NPC, ZeroDamageRule.POSITIVE_ACTUAL_DAMAGE,
		RandomTiming.NONE, StateOwner.SUMMON_OWNER_CREDIT,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.NONE,
		"Summoning.recordOwnerCombatSummonDamage", "summon contribution characterization"),
	DIVINE_GRACE("semantic.divine-grace",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.ELIGIBLE_PLAYER_SOURCE, ZeroDamageRule.POSITIVE_ACTUAL_DAMAGE,
		RandomTiming.SINGLE_EFFECT_DRAW, StateOwner.PRAYER_AND_EQUIPMENT,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.MIXED,
		"DivineGrace.apply", "god prayer and combat fixtures"),
	GIANT_BAT_LIFESTEAL("semantic.giant-bat-lifesteal",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.MELEE, Style.RANGED, Style.MAGIC),
		ParticipantGate.SUMMON_SOURCE, ZeroDamageRule.POSITIVE_ACTUAL_DAMAGE,
		RandomTiming.NONE, StateOwner.SUMMON_TRAIT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"Summoning.applySummonLifesteal", "summon lifesteal fixture"),
	BLOOD_AMULET_LIFESTEAL("semantic.blood-amulet-lifesteal",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.POSITIVE_ACTUAL_DAMAGE,
		RandomTiming.NONE, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.HIT_SPLAT,
		"Player.applyBloodAmuletLifesteal", "primary and Scythe lifesteal fixtures"),
	CORROSIVE_AURA("semantic.corrosive-aura",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_DEFENDER, ZeroDamageRule.POSITIVE_ACTUAL_DAMAGE,
		RandomTiming.NONE, StateOwner.PRAYER_AND_EQUIPMENT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.EFFECT,
		"CorrosiveAura.apply", "god prayer and combat fixtures"),
	DIVINE_RETRIBUTION("semantic.divine-retribution",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_DEFENDER, ZeroDamageRule.POSITIVE_ACTUAL_DAMAGE,
		RandomTiming.SINGLE_EFFECT_DRAW, StateOwner.PRAYER_AND_EQUIPMENT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.MIXED,
		"DivineRetribution.apply", "Divine Retribution characterization"),
	BLOOD_ROBE_SPLASH("semantic.blood-robe-splash",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_NPC, ZeroDamageRule.POSITIVE_ACTUAL_DAMAGE,
		RandomTiming.NONE, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"ProjectileEvent.applyBloodRobeSplash", "Blood robe splash characterization"),
	CLERIC_RALLY("semantic.cleric-rally-lifesteal",
		phases(Phase.POST_PRIMARY_DAMAGE, Phase.DELAYED_IMPACT), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_PVE, ZeroDamageRule.POSITIVE_ACTUAL_DAMAGE,
		RandomTiming.NONE, StateOwner.CLERIC_RECIPIENT_FRACTION,
		RecursionPolicy.ROOT_ATTACK_ONLY, PresentationPolicy.STATUS_COUNTER,
		"ClericDirectCombatRuntime.afterExistingLifesteal", "Cleric direct-effect ordering fixture"),
	CLERIC_THORNS("semantic.cleric-thorns-reflection",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_DEFENDER_PVE, ZeroDamageRule.POSITIVE_ACTUAL_DAMAGE,
		RandomTiming.NONE, StateOwner.CLERIC_RECIPIENT_CHARGES,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"ClericDirectCombatRuntime.afterExistingLifesteal", "Cleric Thorns characterization"),
	DEATH_RING_CHARGED_HIT("semantic.death-ring-charged-hit",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_SURVIVING_NPC, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.NONE, StateOwner.SOURCE_EQUIPMENT_CHARGE,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.HIT_SPLAT,
		"Player.applyDeathRingChargeHit", "Death Ring characterization"),
	BALROG_MAGIC_SPLASH("semantic.balrog-magic-splash",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.MAGIC),
		ParticipantGate.BALROG_TO_PLAYER_AREA, ZeroDamageRule.POSITIVE_ACTUAL_DAMAGE,
		RandomTiming.NONE, StateOwner.NPC_PROFILE,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.MIXED,
		"ProjectileEvent.applyBalrogMagicSplash", "Balrog splash characterization"),

	SUMMON_TRAIT_ON_HIT("semantic.summon-trait-on-hit",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.MAGIC),
		ParticipantGate.SUMMON_SOURCE, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.TRAIT_CONDITIONAL_DRAW, StateOwner.SUMMON_TRAIT,
		RecursionPolicy.ROOT_ATTACK_ONLY, PresentationPolicy.MIXED,
		"Summoning.applySummonOnHitEffects", "summon bonus characterization"),
	POISON_CURRENT_HIT_RESET("semantic.poison-current-hit-reset",
		phases(Phase.PRE_PRIMARY_DAMAGE, Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.ATTACK_CONTEXT,
		RandomTiming.NONE, StateOwner.SOURCE_TRANSIENT_MARKER,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.NONE,
		"central applyWeaponPoison methods", "poison model fixtures"),
	WEAPON_POISON("semantic.weapon-poison",
		phases(Phase.PRE_PRIMARY_DAMAGE, Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.ADAPTIVE_TRANSACTIONAL_DRAW, StateOwner.ADAPTIVE_POISON_CHANCE,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.MESSAGE,
		"central applyWeaponPoison methods", "poison model and weapon fixtures"),
	STYLE_ARMOR_POISON("semantic.style-armor-poison",
		phases(Phase.PRE_PRIMARY_DAMAGE, Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.ADAPTIVE_TRANSACTIONAL_DRAW, StateOwner.ADAPTIVE_POISON_CHANCE,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.PROJECTILE,
		"central applyWeaponPoison methods", "poison armor fixtures"),
	BLACK_DRAGON_ARMOR_POISON("semantic.black-dragon-armor-poison",
		phases(Phase.PRE_PRIMARY_DAMAGE, Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.SINGLE_EFFECT_DRAW, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.EFFECT,
		"central applyWeaponPoison methods", "dragon leather fixtures"),
	KING_BLACK_DRAGON_ARMOR_POISON("semantic.king-black-dragon-armor-poison",
		phases(Phase.PRE_PRIMARY_DAMAGE, Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.SINGLE_EFFECT_DRAW, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.EFFECT,
		"central applyWeaponPoison methods", "King Black Dragon set characterization"),
	ELEMENTAL_GIANT_MIGHT("semantic.elemental-giant-might",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.EQUIPMENT_CONDITIONAL_DRAW, StateOwner.TARGET_DEBUFF,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.STATUS,
		"central applyLeatherSetOnHitEffects methods", "leather set fixtures"),
	OGRE_STAGGER("semantic.ogre-stagger",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.EQUIPMENT_CONDITIONAL_DRAW, StateOwner.TARGET_DEBUFF,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.STATUS,
		"OgreStaggeringBlowProc via central on-hit methods", "A07.5 compiled Ogre parity fixture"),
	BABY_DRAGON_SMOKE("semantic.baby-dragon-smoke",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.EQUIPMENT_CONDITIONAL_DRAW, StateOwner.TARGET_DEBUFF,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.PROJECTILE,
		"BabyDragonSmokeProc via central on-hit methods", "A07.5B compiled smoke parity fixture"),
	INFERNAL_FIRE("semantic.infernal-fire",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.SINGLE_DRAW_THEN_PAYLOAD, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.MIXED,
		"InfernalFireProc via central on-hit methods", "A07.5H compiled payload/debuff parity fixture"),
	HELLS_INFERNO_SPLASH("semantic.hells-inferno-splash",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_AREA, ZeroDamageRule.POSITIVE_CHILD_SOURCE_DAMAGE,
		RandomTiming.NONE, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.MIXED,
		"central applyHellsInfernoSplash methods", "Hell's Inferno characterization"),
	BLUE_DRAGON_WATER("semantic.blue-dragon-water",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.SINGLE_DRAW_THEN_PAYLOAD, StateOwner.TARGET_DEBUFF,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"BlueDragonWaterProc via central on-hit methods", "A07.5C compiled water parity fixture"),
	EARTH_DRAGON_SLOW("semantic.earth-dragon-slow",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.SINGLE_DRAW_THEN_PAYLOAD, StateOwner.TARGET_DEBUFF,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"EarthDragonSlowProc via central on-hit methods", "A07.5D compiled slow parity fixture"),
	RED_DRAGON_FIRE("semantic.red-dragon-fire",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.SINGLE_DRAW_THEN_PAYLOAD, StateOwner.TARGET_DEBUFF,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"RedDragonFireProc via central on-hit methods", "A07.5E compiled fire parity fixture"),
	BLACK_DRAGON_BREATH_FOLLOWUP("semantic.black-dragon-breath-followup",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.SUCCESSFUL_POISON_MARKER,
		RandomTiming.PAYLOAD_ONLY, StateOwner.SOURCE_TRANSIENT_MARKER,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.MIXED,
		"BlackDragonBreathFollowup via central on-hit methods", "A07.5F compiled marker/payload parity fixture"),
	KING_BLACK_DRAGON_BREATH_FOLLOWUP("semantic.king-black-dragon-breath-followup",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.SUCCESSFUL_POISON_MARKER,
		RandomTiming.PAYLOAD_ONLY, StateOwner.SOURCE_TRANSIENT_MARKER,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.MIXED,
		"KingBlackDragonBreathFollowup via central on-hit methods", "A07.5G compiled payload/element parity fixture"),
	ELDER_GREEN_DRAGON_ARMOR_BREATH("semantic.elder-green-dragon-armor-breath",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.SINGLE_DRAW_THEN_PAYLOAD, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.MIXED,
		"ElderGreenDragonArmorProc via central on-hit methods", "A07.5I compiled trigger parity fixture"),
	ELDER_GREEN_DRAGON_ARMOR_BURN_APPLICATION("semantic.elder-green-dragon-armor-burn-application",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_AREA, ZeroDamageRule.POSITIVE_CHILD_SOURCE_DAMAGE,
		RandomTiming.NONE, StateOwner.TARGET_DOT,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.EFFECT,
		"ElderGreenDragonArmorEffect.applyBurn", "Elder armor characterization"),
	PROJECTILE_STARTLE("semantic.projectile-startle",
		phases(Phase.SURVIVING_TARGET), styles(Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.LAUNCH_SNAPSHOT_PAIR, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.IMPACT_SINGLE_DRAW, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.STATUS,
		"ProjectileEvent.applyDualElementOnHitEffects", "dual-element projectile fixtures"),
	PROJECTILE_ACID("semantic.projectile-acid",
		phases(Phase.SURVIVING_TARGET), styles(Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.LAUNCH_SNAPSHOT_PAIR, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.IMPACT_SINGLE_DRAW, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.MESSAGE,
		"ProjectileEvent.applyDualElementOnHitEffects", "dual-element projectile fixtures"),
	PROJECTILE_FROSTBITE("semantic.projectile-frostbite",
		phases(Phase.SURVIVING_TARGET), styles(Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.LAUNCH_SNAPSHOT_PAIR, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.IMPACT_SINGLE_DRAW, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.STATUS,
		"ProjectileEvent.applyDualElementOnHitEffects", "dual-element projectile fixtures"),
	PROJECTILE_WIND("semantic.projectile-wind",
		phases(Phase.SURVIVING_TARGET), styles(Style.MAGIC),
		ParticipantGate.LAUNCH_SNAPSHOT_PAIR, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.NONE, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.STATUS,
		"ProjectileEvent.projectileDamage", "elemental projectile fixtures"),
	PROJECTILE_WATER("semantic.projectile-water",
		phases(Phase.SURVIVING_TARGET), styles(Style.MAGIC),
		ParticipantGate.LAUNCH_SNAPSHOT_PAIR, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.NONE, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.STATUS,
		"ProjectileEvent.projectileDamage", "elemental projectile fixtures"),
	PROJECTILE_EARTH("semantic.projectile-earth",
		phases(Phase.SURVIVING_TARGET), styles(Style.MAGIC),
		ParticipantGate.LAUNCH_SNAPSHOT_PAIR, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.NONE, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.STATUS,
		"ProjectileEvent.projectileDamage", "elemental projectile fixtures"),
	PROJECTILE_FIRE("semantic.projectile-fire",
		phases(Phase.SURVIVING_TARGET), styles(Style.MAGIC),
		ParticipantGate.LAUNCH_SNAPSHOT_PAIR, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.NONE, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.STATUS,
		"ProjectileEvent.projectileDamage", "elemental projectile fixtures"),
	DRAGON_RANGED_BREATH("semantic.dragon-ranged-breath",
		phases(Phase.SURVIVING_TARGET), styles(Style.RANGED, Style.THROWING),
		ParticipantGate.LAUNCH_SNAPSHOT_PAIR, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.PAYLOAD_ONLY, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.MIXED,
		"ProjectileEvent.applyDragonWeaponBreathDamage", "primary projectile characterization"),
	PROJECTILE_SPLINTER("semantic.projectile-splinter",
		phases(Phase.POST_PRIMARY_DAMAGE), styles(Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_NPC_AREA, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.SINGLE_DRAW_THEN_TARGET_DRAW, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.ROOT_ATTACK_ONLY, PresentationPolicy.HIT_SPLAT,
		"ProjectileEvent.applySplinterOnHitEffect", "Splinter characterization"),
	ELDER_GREEN_DRAGON_PROJECTILE_AOE("semantic.elder-green-dragon-projectile-aoe",
		phases(Phase.SURVIVING_TARGET), styles(Style.RANGED, Style.MAGIC),
		ParticipantGate.ELDER_DRAGON_TO_PLAYER_AREA, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.SHARED_BRANCH_DRAW, StateOwner.NPC_PROFILE,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.PROJECTILE,
		"ElderGreenDragonSpecialAttacks.maybeApplyProjectileAoe", "Elder dragon characterization"),
	ELDER_GREEN_DRAGON_FIRESHOT("semantic.elder-green-dragon-fireshot",
		phases(Phase.DELAYED_IMPACT), styles(Style.RANGED),
		ParticipantGate.ELDER_DRAGON_TO_PLAYER_AREA, ZeroDamageRule.ATTACK_CONTEXT,
		RandomTiming.PER_TARGET_PAYLOAD, StateOwner.NPC_PROFILE,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.MIXED,
		"ElderGreenDragonSpecialAttacks.launchFireshotAoe", "Elder dragon characterization"),
	ELDER_GREEN_DRAGON_BURN_APPLICATION("semantic.elder-green-dragon-burn-application",
		phases(Phase.SURVIVING_TARGET), styles(Style.MAGIC),
		ParticipantGate.ELDER_DRAGON_TO_PLAYER_AREA, ZeroDamageRule.ATTACK_CONTEXT,
		RandomTiming.NONE, StateOwner.TARGET_DOT,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.MIXED,
		"ElderGreenDragonSpecialAttacks.launchBurnAoe", "Elder dragon characterization"),

	BEAR_MAUL_SECOND_HIT("semantic.bear-maul-second-hit",
		phases(Phase.AFTER_ROOT_ATTACK), styles(Style.MELEE),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.NONE, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.HIT_SPLAT,
		"BearMaulSecondHit via primary melee event methods", "A07.5J compiled eligibility parity fixture"),
	DRAGON_MELEE_BREATH("semantic.dragon-melee-breath",
		phases(Phase.AFTER_ROOT_ATTACK), styles(Style.MELEE),
		ParticipantGate.CURRENT_ATTACK_PAIR, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.PAYLOAD_ONLY, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.MIXED,
		"DragonMeleeBreathFollowup via primary melee event methods", "A07.5K compiled visual/callback parity fixture"),
	ELEMENTAL_SWORD("semantic.elemental-sword",
		phases(Phase.AFTER_ROOT_ATTACK), styles(Style.MELEE),
		ParticipantGate.CURRENT_ATTACK_PAIR, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.SINGLE_DRAW_THEN_PAYLOAD, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.MIXED,
		"ElementalSwordProc via primary melee event methods", "A07.5L compiled callback-order fixture"),
	DEMON_PITCHFORK_HELL_BLAZE("semantic.demon-pitchfork-hell-blaze",
		phases(Phase.AFTER_ROOT_ATTACK), styles(Style.MELEE),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.SINGLE_DRAW_THEN_PAYLOAD, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.EFFECT,
		"CombatEvent/PvmMeleeEvent.applyDemonPitchforkHellBlazeProc", "auxiliary damage characterization"),
	KOLODION_FIRE_CLAW("semantic.kolodion-fire-claw",
		phases(Phase.AFTER_ROOT_ATTACK), styles(Style.MELEE),
		ParticipantGate.KOLODION_TO_MOB, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.SINGLE_EFFECT_DRAW, StateOwner.NPC_PROFILE,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.EFFECT,
		"PvmMeleeEvent.applyNpcMeleeSpecialProc", "NPC melee proc fixtures"),
	CHAOS_CHAIN_LIGHTNING("semantic.chaos-chain-lightning",
		phases(Phase.AFTER_ROOT_ATTACK), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_NPC_AREA, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.PER_HOP_CHANCE_AND_TARGET, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.ROOT_ATTACK_ONLY, PresentationPolicy.PROJECTILE,
		"central applyChaosAmuletChainLightning methods", "chain lightning characterization"),
	SCYTHE_CLEAVE("semantic.scythe-cleave",
		phases(Phase.AFTER_ROOT_ATTACK), styles(Style.MELEE),
		ParticipantGate.PLAYER_SOURCE_TO_NPC_AREA, ZeroDamageRule.SETTLED_ZERO_ALLOWED,
		RandomTiming.PER_CHILD_PAYLOAD, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.ROOT_ATTACK_ONLY, PresentationPolicy.HIT_SPLAT,
		"PvmMeleeEvent.applyScytheNpcCleave", "Scythe characterization"),
	JEWELRY_RECOIL("semantic.jewelry-recoil",
		phases(Phase.AFTER_ROOT_ATTACK), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_DEFENDER, ZeroDamageRule.POSITIVE_ROLLED_DAMAGE,
		RandomTiming.SINGLE_EFFECT_DRAW, StateOwner.DEFENDER_EQUIPMENT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"central recoil callers", "melee and projectile recoil characterization"),
	ELDER_GREEN_DRAGON_MELEE_SWEEP("semantic.elder-green-dragon-melee-sweep",
		phases(Phase.PRE_PRIMARY_DAMAGE), styles(Style.MELEE),
		ParticipantGate.ELDER_DRAGON_TO_PLAYER_AREA, ZeroDamageRule.ATTACK_CONTEXT,
		RandomTiming.SINGLE_EFFECT_DRAW, StateOwner.NPC_PROFILE,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.HIT_SPLAT,
		"ElderGreenDragonSpecialAttacks.applyMeleeSweep", "Elder dragon characterization"),

	DEATH_ROBE_OVERKILL("semantic.death-robe-overkill",
		phases(Phase.TARGET_TERMINAL), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_NPC_AREA, ZeroDamageRule.TERMINAL_OVERKILL,
		RandomTiming.NONE, StateOwner.SOURCE_EQUIPMENT,
		RecursionPolicy.ALLOW_SCYTHE_DESCENDANTS, PresentationPolicy.HIT_SPLAT,
		"central applyDeathRobeOverkillSplash methods", "Death robe characterization"),
	DEATH_RING_CHARGE_ACQUISITION("semantic.death-ring-charge-acquisition",
		phases(Phase.KILL_SETTLEMENT), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC, Style.SPECIAL),
		ParticipantGate.PLAYER_KILL_OWNER, ZeroDamageRule.QUALIFYING_KILL,
		RandomTiming.NONE, StateOwner.SOURCE_EQUIPMENT_CHARGE,
		RecursionPolicy.ALLOW_KILL_DESCENDANTS, PresentationPolicy.NONE,
		"Npc.killedBy/Player.chargeDeathRingFromKill", "Death Ring characterization"),
	DEATH_AMULET_BURST("semantic.death-amulet-burst",
		phases(Phase.KILL_SETTLEMENT), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC, Style.SPECIAL),
		ParticipantGate.PLAYER_KILL_OWNER_AREA, ZeroDamageRule.QUALIFYING_KILL,
		RandomTiming.PER_CHILD_PAYLOAD, StateOwner.SOURCE_EQUIPMENT_CHARGE,
		RecursionPolicy.ALLOW_KILL_DESCENDANTS, PresentationPolicy.HIT_SPLAT,
		"Npc.killedBy/Player.applyDeathAmuletBurst", "Death Amulet characterization"),
	SOUL_AMULET_BURST("semantic.soul-amulet-burst",
		phases(Phase.KILL_SETTLEMENT), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC, Style.SPECIAL),
		ParticipantGate.PLAYER_KILL_OWNER_AREA, ZeroDamageRule.QUALIFYING_KILL,
		RandomTiming.PER_TARGET_PAYLOAD, StateOwner.SOURCE_EQUIPMENT_CHARGE,
		RecursionPolicy.ALLOW_KILL_DESCENDANTS, PresentationPolicy.HIT_SPLAT,
		"Npc.killedBy/Player.applySoulAmuletBurst", "jewelry runtime fixtures"),
	RING_OF_LIFE("semantic.ring-of-life",
		phases(Phase.SURVIVING_TARGET, Phase.AFTER_ROOT_ATTACK), styles(Style.MELEE, Style.RANGED, Style.THROWING, Style.MAGIC),
		ParticipantGate.PLAYER_DEFENDER, ZeroDamageRule.TARGET_SURVIVING,
		RandomTiming.NONE, StateOwner.DEFENDER_EQUIPMENT,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.MESSAGE,
		"central checkRingOfLife callers", "reflection and lifecycle characterization"),
	COMPATIBILITY_TUTORIAL_RAT_SAFETY("semantic.compatibility-tutorial-rat-safety",
		phases(Phase.SURVIVING_TARGET), styles(Style.MELEE),
		ParticipantGate.COMPATIBILITY_NPC_TO_PLAYER, ZeroDamageRule.TARGET_SURVIVING,
		RandomTiming.NONE, StateOwner.COMPATIBILITY_SCRIPT,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.NONE,
		"CombatScriptLoader/TutorialIslandRat", "death compatibility characterization"),

	GOD_SPELL_AREA_DAMAGE("semantic.god-spell-area-damage",
		phases(Phase.DELAYED_IMPACT), styles(Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_NPC_AREA, ZeroDamageRule.DELAYED_ATTACK_CONTEXT,
		RandomTiming.PER_CHILD_PAYLOAD, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"SpellHandler.applyGodSpellAreaEffects", "delayed god-spell characterization"),
	GUTHIX_GOD_SPELL_POISON("semantic.guthix-god-spell-poison",
		phases(Phase.DELAYED_IMPACT), styles(Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_NPC_AREA, ZeroDamageRule.POSITIVE_CHILD_SOURCE_DAMAGE,
		RandomTiming.SECONDARY_TARGET_DRAW, StateOwner.TARGET_DOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.MESSAGE,
		"SpellHandler.applyGuthixGodSpellPoison", "delayed god-spell characterization"),
	ZAMORAK_GOD_SPELL_WITHERING("semantic.zamorak-god-spell-withering",
		phases(Phase.DELAYED_IMPACT), styles(Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_NPC_AREA, ZeroDamageRule.POSITIVE_CHILD_SOURCE_DAMAGE,
		RandomTiming.NONE, StateOwner.TARGET_DEBUFF,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.STATUS,
		"SpellHandler.applyZamorakWithering", "delayed god-spell characterization"),
	SARADOMIN_GOD_SPELL_LIFESTEAL("semantic.saradomin-god-spell-lifesteal",
		phases(Phase.DELAYED_IMPACT), styles(Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE, ZeroDamageRule.POSITIVE_AGGREGATE_DAMAGE,
		RandomTiming.NONE, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"SpellHandler.applyGodSpellLifesteal", "delayed god-spell characterization"),
	IBAN_BLAST_AREA_DAMAGE("semantic.iban-blast-area-damage",
		phases(Phase.DELAYED_IMPACT), styles(Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_NPC_AREA, ZeroDamageRule.DELAYED_ATTACK_CONTEXT,
		RandomTiming.PER_CHILD_PAYLOAD, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.HIT_SPLAT,
		"SpellHandler.applyIbanBlastAreaEffects", "delayed Iban characterization"),
	SALARIN_SECOND_STRIKE("semantic.salarin-second-strike",
		phases(Phase.DELAYED_IMPACT), styles(Style.MAGIC),
		ParticipantGate.PLAYER_SOURCE_TO_NPC, ZeroDamageRule.DELAYED_ATTACK_CONTEXT,
		RandomTiming.PAYLOAD_ONLY, StateOwner.LAUNCH_SNAPSHOT,
		RecursionPolicy.NO_REENTRY, PresentationPolicy.SPARSE_DAMAGE,
		"SpellHandler Salarin MiniEvent", "delayed Salarin characterization"),

	ELDER_GREEN_DRAGON_ARMOR_BURN_PULSE("semantic.elder-green-dragon-armor-burn-pulse",
		phases(Phase.PERIODIC_TICK), styles(Style.SPECIAL),
		ParticipantGate.PLAYER_SOURCE_TO_MOB, ZeroDamageRule.TIMER_PULSE,
		RandomTiming.NONE, StateOwner.TARGET_DOT,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.HIT_SPLAT,
		"ElderGreenDragonArmorEffect.ElderArmorBurnEvent", "Elder armor characterization"),
	ELDER_GREEN_DRAGON_BURN_PULSE("semantic.elder-green-dragon-burn-pulse",
		phases(Phase.PERIODIC_TICK), styles(Style.SPECIAL),
		ParticipantGate.ELDER_DRAGON_TO_PLAYER, ZeroDamageRule.TIMER_PULSE,
		RandomTiming.PER_TICK_PAYLOAD, StateOwner.TARGET_DOT,
		RecursionPolicy.CONTENT_OWNED, PresentationPolicy.HIT_SPLAT,
		"ElderGreenDragonSpecialAttacks.ElderGreenDragonBurnEvent", "Elder dragon characterization");

	public enum Phase {
		PRE_PRIMARY_DAMAGE,
		PRE_PRIMARY_SETTLEMENT,
		POST_PRIMARY_DAMAGE,
		SURVIVING_TARGET,
		AFTER_ROOT_ATTACK,
		TARGET_TERMINAL,
		KILL_SETTLEMENT,
		DELAYED_IMPACT,
		PERIODIC_TICK
	}

	public enum Style {
		MELEE,
		RANGED,
		THROWING,
		MAGIC,
		SPECIAL
	}

	public enum ParticipantGate {
		CURRENT_ATTACK_PAIR,
		PLAYER_SOURCE,
		PLAYER_SOURCE_PVE,
		PLAYER_SOURCE_TO_NPC,
		PLAYER_SOURCE_TO_SURVIVING_NPC,
		PLAYER_SOURCE_TO_MOB,
		PLAYER_SOURCE_AREA,
		PLAYER_SOURCE_TO_NPC_AREA,
		PLAYER_DEFENDER,
		PLAYER_DEFENDER_PVE,
		NPC_TO_PLAYER,
		SUMMON_SOURCE,
		ELIGIBLE_PLAYER_SOURCE,
		LAUNCH_SNAPSHOT_PAIR,
		BALROG_TO_PLAYER_AREA,
		ELDER_DRAGON_TO_PLAYER,
		ELDER_DRAGON_TO_PLAYER_AREA,
		KOLODION_TO_MOB,
		PLAYER_KILL_OWNER,
		PLAYER_KILL_OWNER_AREA,
		COMPATIBILITY_PLAYER_TO_PLAYER,
		COMPATIBILITY_NPC_TO_PLAYER
	}

	public enum ZeroDamageRule {
		ATTACK_CONTEXT,
		DELAYED_ATTACK_CONTEXT,
		SETTLED_ZERO_ALLOWED,
		POSITIVE_PENDING_DAMAGE,
		POSITIVE_POST_RESISTANCE,
		POSITIVE_ROLLED_DAMAGE,
		POSITIVE_ACTUAL_DAMAGE,
		POSITIVE_CHILD_SOURCE_DAMAGE,
		POSITIVE_AGGREGATE_DAMAGE,
		SUCCESSFUL_POISON_MARKER,
		TARGET_SURVIVING,
		TERMINAL_OVERKILL,
		QUALIFYING_KILL,
		TIMER_PULSE
	}

	public enum RandomTiming {
		NONE,
		DYNAMIC_SCRIPT_DRAW,
		SINGLE_EFFECT_DRAW,
		EQUIPMENT_CONDITIONAL_DRAW,
		TRAIT_CONDITIONAL_DRAW,
		ADAPTIVE_TRANSACTIONAL_DRAW,
		SINGLE_DRAW_THEN_PAYLOAD,
		SINGLE_DRAW_THEN_TARGET_DRAW,
		IMPACT_SINGLE_DRAW,
		SHARED_BRANCH_DRAW,
		SECONDARY_TARGET_DRAW,
		PER_HOP_CHANCE_AND_TARGET,
		PER_CHILD_PAYLOAD,
		PER_TARGET_PAYLOAD,
		PER_TICK_PAYLOAD,
		PAYLOAD_ONLY
	}

	public enum StateOwner {
		SOURCE_EQUIPMENT,
		SOURCE_EQUIPMENT_CHARGE,
		DEFENDER_EQUIPMENT,
		DEFENDER_EQUIPMENT_CHARGE,
		PRAYER_AND_EQUIPMENT,
		TARGET_DEBUFF,
		TARGET_DOT,
		SOURCE_TRANSIENT_MARKER,
		ADAPTIVE_POISON_CHANCE,
		SUMMON_TRAIT,
		SUMMON_OWNER_CREDIT,
		CLERIC_RECIPIENT_CHARGES,
		CLERIC_RECIPIENT_FRACTION,
		LAUNCH_SNAPSHOT,
		NPC_PROFILE,
		COMPATIBILITY_SCRIPT
	}

	public enum RecursionPolicy {
		NO_REENTRY,
		ROOT_ATTACK_ONLY,
		ALLOW_SCYTHE_DESCENDANTS,
		ALLOW_KILL_DESCENDANTS,
		CONTENT_OWNED
	}

	public enum PresentationPolicy {
		NONE,
		STATUS,
		STATUS_COUNTER,
		HIT_SPLAT,
		EFFECT,
		PROJECTILE,
		MESSAGE,
		MIXED,
		SPARSE_DAMAGE
	}

	/** Planning evidence only; no runtime enforces these values. */
	public static final int REVIEWED_HEADROOM_PER_PHASE = 4;
	public static final int APPROVED_PLANNED_EFFECTS = 0;

	private static final Map<String, SecondaryEffectDescriptor> BY_STABLE_KEY;
	private static final Map<Phase, Integer> CURRENT_COUNTS_BY_PHASE;

	static {
		final Map<String, SecondaryEffectDescriptor> descriptors =
			new LinkedHashMap<String, SecondaryEffectDescriptor>();
		final Map<Phase, Integer> counts = new EnumMap<Phase, Integer>(Phase.class);
		for (final Phase phase : Phase.values()) {
			counts.put(phase, Integer.valueOf(0));
		}
		for (final SecondaryEffectDescriptor descriptor : values()) {
			if (descriptor.stableKey.isEmpty()) {
				throw new IllegalStateException("Secondary effect descriptor requires a stable key");
			}
			final SecondaryEffectDescriptor previous = descriptors.put(
				descriptor.stableKey, descriptor);
			if (previous != null) {
				throw new IllegalStateException(
					"Duplicate secondary effect descriptor key " + descriptor.stableKey);
			}
			for (final Phase phase : descriptor.phases) {
				counts.put(phase, Integer.valueOf(counts.get(phase).intValue() + 1));
			}
		}
		BY_STABLE_KEY = Collections.unmodifiableMap(descriptors);
		CURRENT_COUNTS_BY_PHASE = Collections.unmodifiableMap(counts);
	}

	private final String stableKey;
	private final Set<Phase> phases;
	private final Set<Style> styles;
	private final ParticipantGate participantGate;
	private final ZeroDamageRule zeroDamageRule;
	private final RandomTiming randomTiming;
	private final StateOwner stateOwner;
	private final RecursionPolicy recursionPolicy;
	private final PresentationPolicy presentationPolicy;
	private final String existingOwner;
	private final String executableEvidence;

	SecondaryEffectDescriptor(final String stableKey, final Set<Phase> phases,
			final Set<Style> styles, final ParticipantGate participantGate,
			final ZeroDamageRule zeroDamageRule, final RandomTiming randomTiming,
			final StateOwner stateOwner, final RecursionPolicy recursionPolicy,
			final PresentationPolicy presentationPolicy, final String existingOwner,
			final String executableEvidence) {
		this.stableKey = required(stableKey, "stable key");
		this.phases = immutableCopy(phases, "phase");
		this.styles = immutableCopy(styles, "style");
		if (participantGate == null || zeroDamageRule == null || randomTiming == null
				|| stateOwner == null || recursionPolicy == null
				|| presentationPolicy == null) {
			throw new IllegalArgumentException("Secondary effect descriptor metadata is incomplete");
		}
		this.participantGate = participantGate;
		this.zeroDamageRule = zeroDamageRule;
		this.randomTiming = randomTiming;
		this.stateOwner = stateOwner;
		this.recursionPolicy = recursionPolicy;
		this.presentationPolicy = presentationPolicy;
		this.existingOwner = required(existingOwner, "existing owner");
		this.executableEvidence = required(executableEvidence, "executable evidence");
	}

	private static Set<Phase> phases(final Phase first, final Phase... rest) {
		return EnumSet.of(first, rest);
	}

	private static Set<Style> styles(final Style first, final Style... rest) {
		return EnumSet.of(first, rest);
	}

	private static <T extends Enum<T>> Set<T> immutableCopy(final Set<T> values,
			final String label) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("Secondary effect descriptor requires a " + label);
		}
		return Collections.unmodifiableSet(EnumSet.copyOf(values));
	}

	private static String required(final String value, final String label) {
		final String normalized = value == null ? "" : value.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Secondary effect descriptor requires " + label);
		}
		return normalized;
	}

	public String getStableKey() {
		return stableKey;
	}

	public Set<Phase> getPhases() {
		return phases;
	}

	public Set<Style> getStyles() {
		return styles;
	}

	public ParticipantGate getParticipantGate() {
		return participantGate;
	}

	public ZeroDamageRule getZeroDamageRule() {
		return zeroDamageRule;
	}

	public RandomTiming getRandomTiming() {
		return randomTiming;
	}

	public StateOwner getStateOwner() {
		return stateOwner;
	}

	public RecursionPolicy getRecursionPolicy() {
		return recursionPolicy;
	}

	public PresentationPolicy getPresentationPolicy() {
		return presentationPolicy;
	}

	public String getExistingOwner() {
		return existingOwner;
	}

	public String getExecutableEvidence() {
		return executableEvidence;
	}

	public static SecondaryEffectDescriptor forStableKey(final String stableKey) {
		return stableKey == null ? null : BY_STABLE_KEY.get(stableKey);
	}

	public static Map<String, SecondaryEffectDescriptor> byStableKey() {
		return BY_STABLE_KEY;
	}

	public static Map<Phase, Integer> currentCountsByPhase() {
		return CURRENT_COUNTS_BY_PHASE;
	}

	public static int currentCountForPhase(final Phase phase) {
		if (phase == null) {
			return 0;
		}
		return CURRENT_COUNTS_BY_PHASE.get(phase).intValue();
	}

	/**
	 * Planning budget derived from current registrations plus reviewed headroom.
	 * This is not connected to combat execution and must not be treated as a
	 * runtime limit before a later branch defines an executor contract.
	 */
	public static int recommendedPlanningBudget(final Phase phase) {
		return currentCountForPhase(phase) + REVIEWED_HEADROOM_PER_PHASE;
	}
}
