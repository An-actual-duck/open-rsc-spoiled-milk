#!/usr/bin/env python3
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EQUIPMENT_PATH = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
PLAYER_PATH = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
SKILLS_PATH = ROOT / "server/src/com/openrsc/server/model/Skills.java"
ACTION_SENDER_PATH = ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
GAME_DATABASE_PATH = ROOT / "server/src/com/openrsc/server/database/GameDatabase.java"
MOB_PATH = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
STAT_RESTORATION_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/StatRestorationEvent.java"
COMBAT_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PVM_MELEE_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
PROJECTILE_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
OGRE_STAGGER_PROC_PATH = ROOT / "server/src/com/openrsc/server/model/combat/OgreStaggeringBlowProc.java"
BABY_DRAGON_SMOKE_PROC_PATH = ROOT / "server/src/com/openrsc/server/model/combat/BabyDragonSmokeProc.java"
BLUE_DRAGON_WATER_PROC_PATH = ROOT / "server/src/com/openrsc/server/model/combat/BlueDragonWaterProc.java"
EARTH_DRAGON_SLOW_PROC_PATH = ROOT / "server/src/com/openrsc/server/model/combat/EarthDragonSlowProc.java"
RED_DRAGON_FIRE_PROC_PATH = ROOT / "server/src/com/openrsc/server/model/combat/RedDragonFireProc.java"
ELDER_ARMOR_EFFECT_PATH = ROOT / "server/src/com/openrsc/server/content/ElderGreenDragonArmorEffect.java"
COMBAT_FORMULA_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"
OSRS_COMBAT_FORMULA_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/OSRSCombatFormula.java"
LEATHER_DEBUFF_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/LeatherSetDebuffEvent.java"
DEVOTION_PATH = ROOT / "server/src/com/openrsc/server/content/Devotion.java"
BONES_PATH = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/Bones.java"
SUMMONING_PATH = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
CLIENT_ENTITY_HANDLER_PATH = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
ITEM_DEFS_MYWORLD_PATH = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def expect_contains(path: Path, needle: str, label: str) -> None:
    text = path.read_text()
    if needle not in text:
        fail(f"{label} missing `{needle}` in {path}")


def expect_not_contains(path: Path, needle: str, label: str) -> None:
    text = path.read_text()
    if needle in text:
        fail(f"{label} should not contain `{needle}` in {path}")


def expect_before(path: Path, first: str, second: str, label: str) -> None:
    text = path.read_text()
    if first not in text or second not in text or text.index(first) >= text.index(second):
        fail(f"{label} has incorrect ordering in {path}")


def expect_item_descriptions(item_ids: range, description: str, label: str) -> None:
    items = json.loads(ITEM_DEFS_MYWORLD_PATH.read_text(encoding="utf-8"))["items"]
    descriptions = {item["id"]: item.get("description") for item in items}
    for item_id in item_ids:
        if descriptions.get(item_id) != description:
            fail(f"{label} item {item_id} description is {descriptions.get(item_id)!r}, expected {description!r}")


def main() -> None:
    expect_contains(EQUIPMENT_PATH, "getCowHideHitsBonus()", "equipment cow bonus")
    expect_contains(EQUIPMENT_PATH, "getGoblinTenacityProcChance()", "equipment goblin tenacity")
    expect_contains(EQUIPMENT_PATH, "return hasFullGoblinHideSet() ? 0.05D : 0.0D;", "goblin tenacity chance")
    expect_contains(EQUIPMENT_PATH, "getUnicornHidePrayerBonus()", "equipment unicorn bonus")
    expect_contains(EQUIPMENT_PATH, "return hasFullUnicornHideSet() ? 10 : 0;", "unicorn prayer bonus should be unconditional")
    expect_not_contains(EQUIPMENT_PATH, "getBlackUnicornHidePrayerBonus()", "retired black unicorn prayer bonus")
    expect_not_contains(EQUIPMENT_PATH, "total += getBlackUnicornHidePrayerBonus();", "retired black unicorn prayer total")
    expect_not_contains(EQUIPMENT_PATH, "hasFullUnicornHideSet() && player.getPrayerBook()", "unicorn prayer bonus god gate")
    expect_not_contains(EQUIPMENT_PATH, "hasFullBlackUnicornHideSet() && player.getPrayerBook()", "black unicorn prayer bonus god gate")
    expect_contains(EQUIPMENT_PATH, "getGiantMightSkillBonus(final int baseLevel)", "equipment giant might bonus")
    expect_contains(EQUIPMENT_PATH, "Math.floor(baseLevel * 0.10D)", "giant might literal skill bonus")
    expect_contains(EQUIPMENT_PATH, "getElementalGiantMightProcChance()", "equipment elemental giant proc")
    expect_contains(EQUIPMENT_PATH, "getOgreStaggeringBlowProcChance()", "equipment ogre bonus")
    expect_contains(EQUIPMENT_PATH, "getBabyDragonSmokeAccuracyDebuffPercent()", "equipment baby dragon smoke bonus")
    expect_contains(EQUIPMENT_PATH, "getInfernalFireProcMaxHit()", "equipment infernal fire bonus")
    expect_contains(EQUIPMENT_PATH, "hasFullBlueDragonSet()", "equipment blue dragon set")
    expect_contains(EQUIPMENT_PATH, "hasFullEarthDragonSet()", "equipment earth dragon set")
    expect_contains(EQUIPMENT_PATH, "hasFullRedDragonSet()", "equipment red dragon set")
    expect_contains(EQUIPMENT_PATH, "hasFullBlackDragonSet()", "equipment black dragon set")
    expect_contains(EQUIPMENT_PATH, "hasFullKingBlackDragonSet()", "equipment KBD set")
    expect_contains(EQUIPMENT_PATH, "hasFullElderGreenDragonSet()", "equipment elder green dragon set")
    expect_contains(EQUIPMENT_PATH, "getElderGreenDragonArmorProcChance()", "equipment elder green dragon proc")
    expect_contains(EQUIPMENT_PATH, "hasFullCowHideSet()", "cow full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullGoblinHideSet()", "goblin full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullUnicornHideSet()", "unicorn full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullBlackUnicornHideSet()", "black unicorn full-set detection")
    expect_contains(DEVOTION_PATH, "hasFullBlackUnicornSetEquipped(player)", "black unicorn full-set Devotion bonus")
    expect_contains(BONES_PATH, "BlackUnicornOfferingHealing.apply(player, item.getCatalogId(), 1);", "black unicorn manual-offering heal")
    expect_contains(SUMMONING_PATH, "BlackUnicornOfferingHealing.apply(owner, itemId, amount);", "black unicorn automatic-offering heal")
    expect_contains(EQUIPMENT_PATH, "hasFullBearHideSet()", "bear full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullGiantSet()", "giant full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullOgreSet()", "ogre full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullMossGiantSet()", "moss giant full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullIceGiantSet()", "ice giant full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullBabyDragonSet()", "baby dragon full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullDemonSet()", "demon full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullBlackDemonSet()", "black demon full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullBalrogSet()", "balrog full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullFireGiantSet()", "fire giant full-set detection")
    expect_contains(EQUIPMENT_PATH,
                    "if (combatStyle == PrayerCatalog.CombatStyle.MAGIC && hasFullMagicSpiderCarapaceSet()) {\n\t\t\treturn 0;\n\t\t}",
                    "magic spider leather magic-power penalty exemption")

    expect_contains(PLAYER_PATH, "syncHitsEquipmentBonuses()", "player hits sync")
    expect_contains(PLAYER_PATH, 'setAttribute("cow_hide_hits_bonus"', "cow hits tracking")
    expect_contains(PLAYER_PATH, "applyBearMaulDamage", "player bear maul damage")
    expect_contains(PLAYER_PATH, "applyGoblinTenacity", "player goblin tenacity")
    expect_contains(SKILLS_PATH, "applyGoblinTenacity(amount)", "shared lethal damage tenacity hook")
    expect_contains(PLAYER_PATH, "applyElementalGiantMightDebuff", "player elemental giant might")
    expect_contains(PLAYER_PATH, "syncGiantMightEquipmentBonuses()", "player giant skill synchronization")
    expect_contains(PLAYER_PATH, "getEquipmentAdjustedNormalLevel", "player equipment-adjusted normal level")
    expect_contains(PLAYER_PATH, "getPersistedSkillLevel", "player equipment-free persisted level")
    expect_contains(PLAYER_PATH, "target.applyEarthAttackSpeedDebuff(6)", "earth giant debuff")
    expect_contains(PLAYER_PATH, "target.applyWaterMaxHitDebuff(10)", "water giant debuff")
    expect_contains(PLAYER_PATH, "target.applyFireDefenseDebuff(6)", "fire giant debuff")
    expect_not_contains(PLAYER_PATH, "activateOgreStaggeringBlowCooldown()", "ogre cooldown")
    expect_contains(PLAYER_PATH, "consumeLeatherSetAttackBuffs()", "player leather-set buff consumption")
    expect_contains(PLAYER_PATH, "getBabyDragonSmokeAccuracyDebuffPercent()", "player baby dragon access")
    expect_contains(PLAYER_PATH, "getInfernalFireProcMaxHit()", "player infernal fire access")
    expect_contains(PLAYER_PATH, "hasFullGiantSet()", "player giant access")
    expect_contains(PLAYER_PATH, "hasFullOgreSet()", "player ogre access")
    expect_contains(PLAYER_PATH, "hasFullBlueDragonSet()", "player blue dragon access")
    expect_contains(PLAYER_PATH, "hasFullKingBlackDragonSet()", "player KBD access")
    expect_contains(PLAYER_PATH, "hasFullElderGreenDragonSet()", "player elder green dragon access")
    expect_contains(ACTION_SENDER_PATH, "player.syncHitsEquipmentBonuses();", "equipment sync hook")
    expect_contains(ACTION_SENDER_PATH, "player.syncGiantMightEquipmentBonuses();", "giant equipment sync hook")
    expect_contains(STAT_RESTORATION_PATH, "getEquipmentAdjustedNormalLevel", "giant restoration target")
    expect_contains(GAME_DATABASE_PATH, "player.getPersistedSkillLevel(i)", "giant logout persistence")

    expect_contains(MOB_PATH, "applyBearIntimidateDebuff", "mob bear debuff apply")
    expect_contains(MOB_PATH, "applyOgreStaggerDebuff", "mob ogre debuff apply")
    expect_contains(MOB_PATH, "consumeOgreStaggerDebuff", "mob ogre debuff consume")
    expect_contains(MOB_PATH, "applySmokeAccuracyDebuff", "mob smoke debuff apply")
    expect_contains(MOB_PATH, "applyInfernalFireDefenseDebuff", "mob infernal fire debuff apply")
    expect_contains(MOB_PATH, "applyDragonWaterMaxHitDebuff", "mob dragon water debuff apply")
    expect_contains(MOB_PATH, "applyDragonEarthAttackSpeedDebuff", "mob dragon earth debuff apply")
    expect_contains(MOB_PATH, "applyDragonFireDefenseDebuff", "mob dragon fire debuff apply")
    expect_contains(MOB_PATH, "clearBearIntimidateDebuff", "mob bear debuff clear")
    expect_contains(MOB_PATH, "bearIntimidatePercent", "mob bear debuff state")
    expect_contains(LEATHER_DEBUFF_EVENT_PATH, "BEAR_INTIMIDATE", "bear debuff event")

    expect_contains(COMBAT_EVENT_PATH, "applyLeatherSetOnHitEffects", "combat melee hook")
    expect_contains(COMBAT_EVENT_PATH, "inflictAuxiliaryMagicDamage", "combat infernal proc damage hook")
    expect_contains(COMBAT_EVENT_PATH, "inflictAuxiliaryTrueDamage", "combat dragon breath true damage hook")
    expect_contains(COMBAT_EVENT_PATH, "applyPlayerMeleeDamageBuff", "combat melee buff hook")
    expect_contains(COMBAT_EVENT_PATH, 'player.getAttribute("dragon_breath_armor_proc"', "combat dragon breath shared proc state")
    expect_contains(COMBAT_EVENT_PATH, "new CombatEffect(player, CombatEffect.DRAGON_BREATH)", "combat dragon breath visual")
    expect_contains(PVM_MELEE_PATH, "applyLeatherSetOnHitEffects", "pvm melee hook")
    expect_contains(PVM_MELEE_PATH, "inflictAuxiliaryMagicDamage", "pvm infernal proc damage hook")
    expect_contains(PVM_MELEE_PATH, "inflictAuxiliaryTrueDamage", "pvm dragon breath true damage hook")
    expect_contains(PVM_MELEE_PATH, "applyPlayerMeleeDamageBuff", "pvm melee buff hook")
    expect_contains(PVM_MELEE_PATH, "applyBearMaulSecondHit", "pvm bear maul second hit")
    expect_contains(PVM_MELEE_PATH, "new CombatEffect(player, CombatEffect.DRAGON_BREATH)", "pvm dragon breath visual")
    expect_contains(PROJECTILE_EVENT_PATH, "applyLeatherSetOnHitEffects", "projectile hook")
    expect_contains(PROJECTILE_EVENT_PATH, "inflictAuxiliaryMagicDamage", "projectile infernal proc damage hook")
    expect_contains(PROJECTILE_EVENT_PATH, "inflictAuxiliaryTrueDamage", "projectile dragon breath true damage hook")
    expect_contains(PROJECTILE_EVENT_PATH, "applyPlayerProjectileDamageBuff", "projectile magic buff hook")
    expect_contains(PROJECTILE_EVENT_PATH, "casterPlayer.applyElementalGiantMightDebuff(opponent)", "ranged elemental giant hook")
    expect_contains(PROJECTILE_EVENT_PATH, "new CombatEffect(casterPlayer, CombatEffect.DRAGON_BREATH)", "projectile dragon breath visual")
    for path, label in ((COMBAT_EVENT_PATH, "combat"),
                        (PVM_MELEE_PATH, "pvm"),
                        (PROJECTILE_EVENT_PATH, "projectile")):
        expect_contains(path, "OgreStaggeringBlowProc.tryApply", f"{label} shared Ogre stagger proc")
        expect_contains(path, "BabyDragonSmokeProc.tryApply", f"{label} shared Baby Dragon smoke proc")
        expect_contains(path, "BlueDragonWaterProc.tryApply", f"{label} shared Blue Dragon water proc")
        expect_contains(path, "EarthDragonSlowProc.tryApply", f"{label} shared Earth Dragon slow proc")
        expect_contains(path, "RedDragonFireProc.tryApply", f"{label} shared Red Dragon fire proc")
    expect_contains(OGRE_STAGGER_PROC_PATH, "if (!source.hasFullOgreSet())", "Ogre complete-set gate")
    expect_contains(OGRE_STAGGER_PROC_PATH, "random.nextDouble()", "Ogre single random draw")
    expect_contains(OGRE_STAGGER_PROC_PATH, "source.getOgreStaggeringBlowProcChance()", "Ogre configured chance")
    expect_contains(OGRE_STAGGER_PROC_PATH, "target.applyOgreStaggerDebuff();", "Ogre target debuff")
    expect_contains(BABY_DRAGON_SMOKE_PROC_PATH,
                    "source.getBabyDragonSmokeAccuracyDebuffPercent()",
                    "Baby Dragon equipment effect gate")
    expect_contains(BABY_DRAGON_SMOKE_PROC_PATH, "random.nextDouble()", "Baby Dragon single random draw")
    expect_contains(BABY_DRAGON_SMOKE_PROC_PATH,
                    "source.getBabyDragonSmokeProcChance()",
                    "Baby Dragon configured chance")
    expect_contains(BABY_DRAGON_SMOKE_PROC_PATH, "Projectile.BLOW_SMOKE", "Baby Dragon smoke presentation")
    expect_contains(BABY_DRAGON_SMOKE_PROC_PATH,
                    "target.applySmokeAccuracyDebuff(smokePercent);",
                    "Baby Dragon target debuff")
    expect_contains(BLUE_DRAGON_WATER_PROC_PATH,
                    "if (!source.hasFullBlueDragonSet())",
                    "Blue Dragon complete-set gate")
    expect_contains(BLUE_DRAGON_WATER_PROC_PATH, "random.nextDouble()", "Blue Dragon chance draw")
    expect_contains(BLUE_DRAGON_WATER_PROC_PATH,
                    "random.nextIntInclusive(0, MAX_PROC_DAMAGE)",
                    "Blue Dragon inclusive damage draw")
    expect_contains(BLUE_DRAGON_WATER_PROC_PATH,
                    "auxiliaryTrueDamage.apply(rolledDamage);",
                    "Blue Dragon event-owned true-damage callback")
    expect_contains(BLUE_DRAGON_WATER_PROC_PATH,
                    "target.applyDragonWaterMaxHitDebuff(MAX_HIT_DEBUFF_PERCENT);",
                    "Blue Dragon water debuff")
    expect_before(BLUE_DRAGON_WATER_PROC_PATH,
                  "auxiliaryTrueDamage.apply(rolledDamage);",
                  "target.applyDragonWaterMaxHitDebuff(MAX_HIT_DEBUFF_PERCENT);",
                  "Blue Dragon damage-before-debuff policy")
    expect_contains(EARTH_DRAGON_SLOW_PROC_PATH,
                    "if (!source.hasFullEarthDragonSet())",
                    "Earth Dragon complete-set gate")
    expect_contains(EARTH_DRAGON_SLOW_PROC_PATH, "random.nextDouble()", "Earth Dragon chance draw")
    expect_contains(EARTH_DRAGON_SLOW_PROC_PATH,
                    "random.nextIntInclusive(0, MAX_PROC_DAMAGE)",
                    "Earth Dragon inclusive damage draw")
    expect_contains(EARTH_DRAGON_SLOW_PROC_PATH,
                    "auxiliaryTrueDamage.apply(rolledDamage);",
                    "Earth Dragon event-owned true-damage callback")
    expect_contains(EARTH_DRAGON_SLOW_PROC_PATH,
                    "target.applyDragonEarthAttackSpeedDebuff(\n\t\t\tATTACK_SPEED_DEBUFF_PERCENT);",
                    "Earth Dragon slow debuff")
    expect_before(EARTH_DRAGON_SLOW_PROC_PATH,
                  "auxiliaryTrueDamage.apply(rolledDamage);",
                  "target.applyDragonEarthAttackSpeedDebuff(",
                  "Earth Dragon damage-before-debuff policy")
    expect_contains(RED_DRAGON_FIRE_PROC_PATH,
                    "if (!source.hasFullRedDragonSet())",
                    "Red Dragon complete-set gate")
    expect_contains(RED_DRAGON_FIRE_PROC_PATH, "random.nextDouble()", "Red Dragon chance draw")
    expect_contains(RED_DRAGON_FIRE_PROC_PATH,
                    "random.nextIntInclusive(0, MAX_PROC_DAMAGE)",
                    "Red Dragon inclusive damage draw")
    expect_contains(RED_DRAGON_FIRE_PROC_PATH,
                    "auxiliaryTrueDamage.apply(rolledDamage);",
                    "Red Dragon event-owned true-damage callback")
    expect_contains(RED_DRAGON_FIRE_PROC_PATH,
                    "target.applyDragonFireDefenseDebuff(DEFENSE_DEBUFF_PERCENT);",
                    "Red Dragon fire debuff")
    expect_before(RED_DRAGON_FIRE_PROC_PATH,
                  "auxiliaryTrueDamage.apply(rolledDamage);",
                  "target.applyDragonFireDefenseDebuff(DEFENSE_DEBUFF_PERCENT);",
                  "Red Dragon damage-before-debuff policy")
    for path, label in ((COMBAT_EVENT_PATH, "combat"),
                        (PVM_MELEE_PATH, "pvm"),
                        (PROJECTILE_EVENT_PATH, "projectile")):
        expect_contains(path, "ElderGreenDragonArmorEffect.applyProc", f"{label} elder armor proc")
    expect_contains(ELDER_ARMOR_EFFECT_PATH, "findPlayerOwnedNpcSplashTargets", "elder NPC AOE policy")
    expect_contains(ELDER_ARMOR_EFFECT_PATH, "findPlayerOwnedPvpSplashTargets", "elder PvP AOE policy")
    expect_contains(ELDER_ARMOR_EFFECT_PATH, "BURN_PULSES = 5", "elder reduced burn duration")
    expect_contains(ELDER_ARMOR_EFFECT_PATH, "BURN_DAMAGE = 1", "elder reduced burn damage")
    expect_contains(ELDER_ARMOR_EFFECT_PATH, "existing.refresh(source)", "elder burn refresh without stacking")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "applyMyWorldLeatherArmorDescriptions();", "client leather examine descriptions hook")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Full cow-hide set: +5 Hits.", "cow leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Full unicorn-hide set: +10 Prayer.", "unicorn leather examine description")
    expect_item_descriptions(range(1855, 1860), "Full set: +50% Devotion; bones/bat/big/demon/dragon offerings heal 1/2/2/3/4 Hits, including Mourning Unicorn offerings.", "black unicorn leather generated item examine")
    expect_not_contains(CLIENT_ENTITY_HANDLER_PATH, "Full black unicorn-hide set: +10 Prayer.", "retired black unicorn leather examine description")
    expect_not_contains(CLIENT_ENTITY_HANDLER_PATH, "Full unicorn-hide set: +10 Prayer while worshipping Saradomin.", "old unicorn leather examine description")
    expect_not_contains(CLIENT_ENTITY_HANDLER_PATH, "Full black unicorn-hide set: +10 Prayer while worshipping Zamorak.", "old black unicorn leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Full black-dragon-hide set: 20% chance for dragon breath, max hit 30.", "black dragon leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Full KBD-hide set: 40% chance for True Dragon's Breath", "KBD leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Full elder-green-dragon-hide set: 60% Elder Breath; up to 10 true damage, half in radius 2, then five 1-damage burn pulses.", "elder green dragon leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Goblin's Tenacity: 5% chance for lethal damage to leave you at 1 Hit.", "goblin leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Bear's Maul: melee hits become two hits for 60% damage each.", "bear leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Giant's Might: +10% of base Melee and Ranged levels.", "giant leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Earth Giant's Might: +10% base Melee/Ranged; 20% chance to slow attack speed by 6%.", "earth giant leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Water Giant's Might: +10% base Melee/Ranged; 20% chance to lower max hit by 10%.", "water giant leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Fire Giant's Might: +10% base Melee/Ranged; 20% chance to lower defense by 6%.", "fire giant leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Mystic Venom: 20% magic poison chance, up to 20 poison; removes leather magic-power penalty.", "magic spider leather examine description")
    expect_not_contains(CLIENT_ENTITY_HANDLER_PATH, "Giant's Might: raises current melee and ranged levels by 10%.", "old giant current-level examine description")
    expect_not_contains(CLIENT_ENTITY_HANDLER_PATH, "+10% current melee/ranged levels", "old elemental giant current-level examine description")
    expect_not_contains(CLIENT_ENTITY_HANDLER_PATH, "20% chance to apply Earth", "old earth giant vague examine description")
    expect_not_contains(CLIENT_ENTITY_HANDLER_PATH, "20% chance to apply Water", "old water giant vague examine description")
    expect_not_contains(CLIENT_ENTITY_HANDLER_PATH, "20% chance to apply Fire", "old fire giant vague examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Green dragon-hide", "green dragon armor name")
    expect_item_descriptions(range(1840, 1845), "Goblin's Tenacity: 5% chance for lethal damage to leave you at 1 Hit.", "goblin leather generated item examine")
    expect_item_descriptions(range(1850, 1855), "Bear's Maul: melee hits become two hits for 60% damage each.", "bear leather generated item examine")
    expect_item_descriptions(range(1875, 1880), "Giant's Might: +10% of base Melee and Ranged levels.", "giant leather generated item examine")
    expect_item_descriptions(range(1890, 1895), "Mystic Venom: 20% magic poison chance, up to 20 poison; removes leather magic-power penalty.", "magic spider leather generated item examine")
    expect_item_descriptions(range(1895, 1900), "Earth Giant's Might: +10% base Melee/Ranged; 20% chance to slow attack speed by 6%.", "earth giant leather generated item examine")
    expect_item_descriptions(range(1900, 1905), "Water Giant's Might: +10% base Melee/Ranged; 20% chance to lower max hit by 10%.", "water giant leather generated item examine")
    expect_item_descriptions(range(1915, 1920), "Fire Giant's Might: +10% base Melee/Ranged; 20% chance to lower defense by 6%.", "fire giant leather generated item examine")
    expect_not_contains(COMBAT_FORMULA_PATH, "getGiantMightSkillMultiplier()", "legacy formula-only giant multiplier")
    expect_not_contains(OSRS_COMBAT_FORMULA_PATH, "getGiantMightSkillMultiplier()", "osrs formula-only giant multiplier")

    print("PASS: leather set bonuses and bounded shared proc families are wired")


if __name__ == "__main__":
    main()
