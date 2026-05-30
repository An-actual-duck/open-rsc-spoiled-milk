#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EQUIPMENT_PATH = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
PLAYER_PATH = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
ACTION_SENDER_PATH = ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
MOB_PATH = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
COMBAT_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PVM_MELEE_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
PROJECTILE_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
LEATHER_DEBUFF_EVENT_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/LeatherSetDebuffEvent.java"
CLIENT_ENTITY_HANDLER_PATH = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def expect_contains(path: Path, needle: str, label: str) -> None:
    text = path.read_text()
    if needle not in text:
        fail(f"{label} missing `{needle}` in {path}")


def main() -> None:
    expect_contains(EQUIPMENT_PATH, "getCowHideHitsBonus()", "equipment cow bonus")
    expect_contains(EQUIPMENT_PATH, "getGoblinEnragedProcChance()", "equipment goblin bonus")
    expect_contains(EQUIPMENT_PATH, "getUnicornHidePrayerBonus()", "equipment unicorn bonus")
    expect_contains(EQUIPMENT_PATH, "getBlackUnicornHidePrayerBonus()", "equipment black unicorn bonus")
    expect_contains(EQUIPMENT_PATH, "getBearHideIntimidatePercent()", "equipment bear bonus")
    expect_contains(EQUIPMENT_PATH, "getBearHideIntimidateProcChance()", "equipment bear proc")
    expect_contains(EQUIPMENT_PATH, "getGiantBruteForceProcChance()", "equipment giant bonus")
    expect_contains(EQUIPMENT_PATH, "getOgreStaggeringBlowProcChance()", "equipment ogre bonus")
    expect_contains(EQUIPMENT_PATH, "getMossGiantBruteForceProcChance()", "equipment moss giant bonus")
    expect_contains(EQUIPMENT_PATH, "getIceGiantBruteForceProcChance()", "equipment ice giant bonus")
    expect_contains(EQUIPMENT_PATH, "getFireGiantBruteForceProcChance()", "equipment fire giant bonus")
    expect_contains(EQUIPMENT_PATH, "getBabyDragonSmokeAccuracyDebuffPercent()", "equipment baby dragon smoke bonus")
    expect_contains(EQUIPMENT_PATH, "getInfernalFireProcMaxHit()", "equipment infernal fire bonus")
    expect_contains(EQUIPMENT_PATH, "hasFullBlueDragonSet()", "equipment blue dragon set")
    expect_contains(EQUIPMENT_PATH, "hasFullEarthDragonSet()", "equipment earth dragon set")
    expect_contains(EQUIPMENT_PATH, "hasFullRedDragonSet()", "equipment red dragon set")
    expect_contains(EQUIPMENT_PATH, "hasFullBlackDragonSet()", "equipment black dragon set")
    expect_contains(EQUIPMENT_PATH, "hasFullKingBlackDragonSet()", "equipment king black dragon set")
    expect_contains(EQUIPMENT_PATH, "hasFullCowHideSet()", "cow full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullGoblinHideSet()", "goblin full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullUnicornHideSet()", "unicorn full-set detection")
    expect_contains(EQUIPMENT_PATH, "hasFullBlackUnicornHideSet()", "black unicorn full-set detection")
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

    expect_contains(PLAYER_PATH, "syncHitsEquipmentBonuses()", "player hits sync")
    expect_contains(PLAYER_PATH, 'setAttribute("cow_hide_hits_bonus"', "cow hits tracking")
    expect_contains(PLAYER_PATH, "getBearHideIntimidatePercent()", "player bear access")
    expect_contains(PLAYER_PATH, "activateGoblinEnraged()", "player goblin buff")
    expect_contains(PLAYER_PATH, "activateGiantBruteForce()", "player giant buff")
    expect_contains(PLAYER_PATH, "activateOgreStaggeringBlowCooldown()", "player ogre cooldown")
    expect_contains(PLAYER_PATH, "consumeLeatherSetAttackBuffs()", "player leather-set buff consumption")
    expect_contains(PLAYER_PATH, "getBabyDragonSmokeAccuracyDebuffPercent()", "player baby dragon access")
    expect_contains(PLAYER_PATH, "getInfernalFireProcMaxHit()", "player infernal fire access")
    expect_contains(PLAYER_PATH, "hasFullGiantSet()", "player giant access")
    expect_contains(PLAYER_PATH, "hasFullOgreSet()", "player ogre access")
    expect_contains(PLAYER_PATH, "hasFullBlueDragonSet()", "player blue dragon access")
    expect_contains(PLAYER_PATH, "hasFullKingBlackDragonSet()", "player king black dragon access")
    expect_contains(ACTION_SENDER_PATH, "player.syncHitsEquipmentBonuses();", "equipment sync hook")

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
    expect_contains(PVM_MELEE_PATH, "new CombatEffect(player, CombatEffect.DRAGON_BREATH)", "pvm dragon breath visual")
    expect_contains(PROJECTILE_EVENT_PATH, "applyLeatherSetOnHitEffects", "projectile hook")
    expect_contains(PROJECTILE_EVENT_PATH, "inflictAuxiliaryMagicDamage", "projectile infernal proc damage hook")
    expect_contains(PROJECTILE_EVENT_PATH, "inflictAuxiliaryTrueDamage", "projectile dragon breath true damage hook")
    expect_contains(PROJECTILE_EVENT_PATH, "applyPlayerProjectileDamageBuff", "projectile magic buff hook")
    expect_contains(PROJECTILE_EVENT_PATH, "new CombatEffect(casterPlayer, CombatEffect.DRAGON_BREATH)", "projectile dragon breath visual")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "applyMyWorldLeatherArmorDescriptions();", "client leather examine descriptions hook")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Full cow-hide set: +5 Hits.", "cow leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Full black-dragon-hide set: 20% chance for dragon breath, max hit 30.", "black dragon leather examine description")
    expect_contains(CLIENT_ENTITY_HANDLER_PATH, "Full king-black-dragon-hide set: 60% chance for dragon breath, max hit 40.", "king black dragon leather examine description")

    print("PASS: leather set bonuses wired for passive, debuff, poison, brute-force, infernal, dragon-breath, and ogre families")


if __name__ == "__main__":
    main()
