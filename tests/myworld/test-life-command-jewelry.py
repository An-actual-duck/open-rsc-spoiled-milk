#!/usr/bin/env python3
"""Regression coverage for the Life Command summon-jewelry categories."""

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEMS = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"
EFFECTS = ROOT / "server/src/com/openrsc/server/content/EnchantingItemEffects.java"
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
CLIENT = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/MyWorldItemOverrides.java"

TIERS = ("Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone")
RING_HEALTH = (10, 20, 30, 50, 100)
RING_DAMAGE = (3, 6, 9, 15, 25)
NECKLACE_DURATION = (10, 20, 30, 50, 100)
NECKLACE_UPKEEP_XP = (50, 100, 150, 250, 400)
BANGLE_CHARGES = (1, 2, 3, 5, 8)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def method_body(source: str, name: str) -> str:
    match = re.search(rf"(?:public|private)\s+(?:static\s+)?[^{{;]+\s+{name}\s*\([^)]*\)\s*\{{", source)
    if match is None:
        fail(f"Missing method {name}")
    depth = 0
    for index in range(match.end() - 1, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[match.start():index + 1]
    fail(f"Unclosed method {name}")
    return ""


def int_array(source: str, name: str) -> tuple[int, ...]:
    match = re.search(rf"{name}\s*=\s*\{{([^}}]+)\}}", source)
    if match is None:
        fail(f"Missing {name}")
    return tuple(int(value.strip()) for value in match.group(1).split(",") if value.strip())


def main() -> None:
    items = {entry["id"]: entry for entry in json.loads(ITEMS.read_text(encoding="utf-8"))["items"]}
    effects = EFFECTS.read_text(encoding="utf-8")
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    summoning = SUMMONING.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")

    for offset, tier in enumerate(TIERS):
        ring = items[3096 + offset]
        necklace = items[3101 + offset]
        bangle = items[3106 + offset]
        require(ring["name"] == f"{tier} Ring of Combat Command", f"incorrect Ring name for {tier}")
        require(ring["description"] == f"Raises combat summon health by {RING_HEALTH[offset]}% and damage by {RING_DAMAGE[offset]}%.", f"incorrect Ring description for {tier}")
        require(necklace["name"] == f"{tier} Necklace of Support Command", f"incorrect Necklace name for {tier}")
        require(necklace["description"] == f"Extends support upkeep by {NECKLACE_DURATION[offset]}%; command pulse grants +{NECKLACE_UPKEEP_XP[offset]}% Summoning XP.", f"incorrect Necklace description for {tier}")
        require(bangle["name"] == f"{tier} Bangle of Utility Command", f"incorrect Bangle name for {tier}")
        require(bangle["description"] == f"Adds +{BANGLE_CHARGES[offset]} utility summon charges.", f"incorrect Bangle description for {tier}")
        require(bangle["wearSlot"] == 14 and bangle["appearanceID"] == 0 and bangle["wearableID"] == 0, f"{tier} Utility Command Bangle must remain a zero-visual wrist item")
        require(f'new ItemOverride({3106 + offset}, "{tier} Bangle of Utility Command"' in client, f"client override missing {tier} Utility Command Bangle")

    require(int_array(effects, "LIFE_COMBAT_HEALTH_BONUS_PERCENTS") == RING_HEALTH, "combat health tier values changed")
    require(int_array(effects, "LIFE_COMBAT_DAMAGE_BONUS_PERCENTS") == RING_DAMAGE, "combat damage tier values changed")
    require(int_array(effects, "LIFE_SUPPORT_DURATION_BONUS_PERCENTS") == NECKLACE_DURATION, "support duration tier values changed")
    require(int_array(effects, "LIFE_SUPPORT_UPKEEP_XP_BONUS_PERCENTS") == NECKLACE_UPKEEP_XP, "support upkeep XP tier values changed")
    require(int_array(effects, "LIFE_UTILITY_CHARGE_BONUSES") == BANGLE_CHARGES, "utility charge tier values changed")

    for name in ("getLifeRingCombatSummonHealthPercent", "getLifeRingCombatSummonDamagePercent"):
        body = method_body(equipment, name)
        require("getEquippedRingItem()" in body, f"{name} must read the Ring slot")
    for name in ("getLifeNecklaceSupportDurationPercent", "getLifeNecklaceSupportUpkeepXpBonusPercent"):
        body = method_body(equipment, name)
        require("getEquippedNeckItem()" in body, f"{name} must read the Necklace slot")
    bangle_body = method_body(equipment, "getLifeBangleUtilityChargeBonus")
    require("getEquippedWristItem()" in bangle_body, "utility charges must read the Bangle wrist slot")

    require("getLifeRingCombatSummonHealthPercent()" in method_body(summoning, "getScaledHits"), "combat health must use the Ring")
    require("getLifeRingCombatSummonDamagePercent()" in method_body(summoning, "getScaledMaxHit"), "combat damage must use the Ring")
    require("Math.ceil(maxHit * (bonusPercent / 100.0D))" in method_body(summoning, "getScaledMaxHit"), "combat damage must be percentage-based")
    require("getLifeNecklaceSupportDurationPercent()" in method_body(summoning, "getDurationTicks"), "support duration must use the Necklace")
    consume = method_body(summoning, "consumeLifeRunes")
    require("awardDisplayedSummoningExperience" not in consume, "Life-rune consumption must not award Necklace command XP")
    pulse = method_body(summoning, "pulseSupportCommandExperience")
    require("getLifeNecklaceSupportUpkeepXpBonusPercent()" in pulse, "command XP must require the Necklace")
    require("awardDisplayedSummoningExperience(owner, getSupportCommandDisplayedExperience(owner));" in pulse, "command XP must award on its own pulse")
    command_xp = method_body(summoning, "getSupportCommandDisplayedExperience")
    require("getLifeNecklaceSupportUpkeepXpBonusPercent()" in command_xp, "command XP must use the Necklace")
    require("SUPPORT_COMMAND_XP_PULSE_MS = 60000" in summoning, "command XP must pulse once per minute")
    require("SUPPORT_COMMAND_XP_REMAINING_KEY" in summoning, "command XP must retain paused time across relog/equipment changes")
    require("PACK_RAT_UTILITY_USES = 1" in summoning and "DELIVERY_CAMEL_UTILITY_USES = 1" in summoning, "utility summons must have one base charge")
    spawn = method_body(summoning, "spawnManualSummon")
    require("profile.utilityUses + owner.getCarriedItems().getEquipment().getLifeBangleUtilityChargeBonus()" in spawn, "Utility Bangle charges must be assigned when the summon is created")

    print("PASS: Life Command jewelry categories, tier ladders, and summon activation paths are aligned")


if __name__ == "__main__":
    main()
