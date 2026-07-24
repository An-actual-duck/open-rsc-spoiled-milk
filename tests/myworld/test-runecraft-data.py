#!/usr/bin/env python3
import re
import sys
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
RUNES_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "skills"
    / "runecraft"
    / "Runecraft.java"
)
ALTARS_PATH = (
    ROOT / "server" / "conf" / "server" / "defs" / "extras" / "ObjectRunecraft.xml"
)
ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
RUNECRAFT_LOCS_PATH = (
    ROOT / "server" / "conf" / "server" / "defs" / "locs" / "SceneryLocsRunecraft.json"
)
SCENERY_OTHER_LOCS_PATH = (
    ROOT / "server" / "conf" / "server" / "defs" / "locs" / "SceneryLocsOther.json"
)

EXPECTED_ALTARS = {
    1191: ("air", 33, 1, 20),
    1321: ("life", 37, 1, 20),
    1193: ("mind", 35, 8, 21),
    1195: ("water", 32, 1, 20),
    1197: ("earth", 34, 1, 20),
    1199: ("fire", 31, 1, 20),
    1201: ("body", 36, 15, 27),
    1203: ("cosmic", 46, 30, 29),
    1205: ("chaos", 41, 22, 28),
    1207: ("nature", 40, 38, 30),
    1209: ("law", 42, 46, 94),
    1211: ("death", 38, 54, 168),
    1296: ("soul", 825, 62, 186),
    1213: ("blood", 619, 70, 256),
}

EXPECTED_MULTIPLIER_REQUIREMENTS = {
    "AIR_RUNE": 1,
    "WATER_RUNE": 1,
    "EARTH_RUNE": 1,
    "FIRE_RUNE": 1,
    "LIFE_RUNE": 1,
    "MIND_RUNE": 8,
    "BODY_RUNE": 15,
    "CHAOS_RUNE": 22,
    "COSMIC_RUNE": 30,
    "NATURE_RUNE": 38,
    "LAW_RUNE": 46,
    "DEATH_RUNE": 54,
    "SOUL_RUNE": 62,
    "BLOOD_RUNE": 70,
}


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_altars() -> dict[int, tuple[str, int, int, int]]:
    if not ALTARS_PATH.exists():
        fail(f"Missing file: {ALTARS_PATH}")
    tree = ET.parse(ALTARS_PATH)
    root = tree.getroot()
    result: dict[int, tuple[str, int, int, int]] = {}
    for entry in root.findall("entry"):
        altar_id = int(entry.findtext("int", default="-1"))
        def_node = entry.find("ObjectRunecraftDef")
        if def_node is None:
            fail(f"Missing ObjectRunecraftDef for altar id {altar_id}")
        assert def_node is not None
        result[altar_id] = (
            def_node.findtext("runeName", default=""),
            int(def_node.findtext("runeId", default="-1")),
            int(def_node.findtext("requiredLvl", default="-1")),
            int(def_node.findtext("exp", default="-1")),
        )
    return result


def ensure_expected_altars(altars: dict[int, tuple[str, int, int, int]]) -> None:
    if set(altars.keys()) != set(EXPECTED_ALTARS.keys()):
        fail(f"Unexpected altar ids: {sorted(altars.keys())}")
    for altar_id, (name, rune_id, req, xp) in EXPECTED_ALTARS.items():
        actual_name, actual_rune_id, actual_req, actual_xp = altars[altar_id]
        if (actual_name, actual_rune_id, actual_req, actual_xp) != (name, rune_id, req, xp):
            fail(
                f"Altar {altar_id} expected {(name, rune_id, req, xp)} "
                f"but found {(actual_name, actual_rune_id, actual_req, actual_xp)}"
            )


def ensure_runecraft_supports_all_runes() -> None:
    text = RUNES_PATH.read_text(encoding="utf-8")
    for rune_name in ("LIFE_RUNE", "LAW_RUNE", "DEATH_RUNE", "SOUL_RUNE", "BLOOD_RUNE"):
        if rune_name not in text:
            fail(f"Runecraft.java missing {rune_name} in direct crafting support")


def ensure_multiplier_requirements_follow_progression() -> None:
    text = RUNES_PATH.read_text(encoding="utf-8")

    helper_match = re.search(
        r"private int getRequiredLevelForRune\(int runeId\) \{(?P<body>.*?)\n\t\}",
        text,
        re.S,
    )
    if not helper_match:
        fail("Could not locate getRequiredLevelForRune helper in Runecraft.java")
    assert helper_match is not None

    helper_body = helper_match.group("body")
    for rune_name, level in EXPECTED_MULTIPLIER_REQUIREMENTS.items():
        pattern = rf"case {rune_name}:\s+return {level};"
        if not re.search(pattern, helper_body):
            fail(f"Multiplier progression missing {rune_name} -> {level}")

    if "retVal += (level - requiredLevel) / 10;" not in text:
        fail("Rune multipliers no longer advance every 10 levels after unlock")
    if "retVal >" in text:
        fail("Rune multiplier logic still appears to cap outputs before level 99")


def ensure_law_robe_bonus_uses_fractional_carryover() -> None:
    text = RUNES_PATH.read_text(encoding="utf-8")
    for snippet in (
        "LAW_ROBE_RUNEPRODUCTION_POINTS_PER_RUNE = 10000",
        'LAW_ROBE_RUNEPRODUCTION_CACHE_PREFIX = "law_robe_runecraft_bonus_"',
        "final int earnedPoints = baseRuneCount * bonusPercent * LAW_ROBE_RUNEPRODUCTION_POINTS_PER_PERCENT;",
        "final int bonusRunes = totalPoints / LAW_ROBE_RUNEPRODUCTION_POINTS_PER_RUNE;",
        "final int remainingPoints = totalPoints % LAW_ROBE_RUNEPRODUCTION_POINTS_PER_RUNE;",
        "player.getCache().set(cacheKey, remainingPoints);",
        "player.getCache().remove(cacheKey);",
        "getLawRobeRunecraftBonusPercent(player)",
        "getLawRobeTierTotal() * 2",
    ):
        if snippet not in text:
            fail(f"Law robe runecraft bonus missing fixed-point carryover snippet: {snippet}")


def ensure_chaos_amulet_bonus_uses_fractional_carryover() -> None:
    text = RUNES_PATH.read_text(encoding="utf-8")
    for snippet in (
        "CHAOS_AMULET_YIELD_POINTS_PER_RUNE = 10000",
        "CHAOS_AMULET_YIELD_POINTS_PER_PERCENT = 100",
        'CHAOS_AMULET_YIELD_CACHE_KEY = "chaos_amulet_weighted_rune_bonus"',
        "addChaosAmuletBonusRunes(player, def.getRuneId(), baseRuneCount);",
        "runeId != ItemId.CHAOS_RUNE.id()",
        "getChaosAmuletYieldBonusPercent()",
        "getChaosAmuletBonusRuneWeights()",
        "final int earnedPoints = baseRuneCount * bonusPercent * CHAOS_AMULET_YIELD_POINTS_PER_PERCENT;",
        "final int bonusRunes = totalPoints / CHAOS_AMULET_YIELD_POINTS_PER_RUNE;",
        "final int remainingPoints = totalPoints % CHAOS_AMULET_YIELD_POINTS_PER_RUNE;",
        "ItemId.MIND_RUNE.id()",
        "ItemId.CHAOS_RUNE.id()",
        "ItemId.DEATH_RUNE.id()",
        "ItemId.BLOOD_RUNE.id()",
        "rollChaosAmuletBonusRuneIndex(weights)",
        "DataConversions.random(1, totalWeight)",
        "Your chaos Bangle weaves ",
    ):
        if snippet not in text:
            fail(f"Chaos Bangle weighted-rune bonus missing fixed-point carryover snippet: {snippet}")


def ensure_stone_and_talismans_are_retired() -> None:
    text = ITEMS_PATH.read_text(encoding="utf-8")
    if '"id": 1299' not in text or '"name": "Stone"' not in text:
        fail("Rune stone item was not renamed to Stone")
    if '"command": "Locate"' in text:
        fail("Found stale talisman Locate commands in ItemDefsCustom.json")

    loc_text = RUNECRAFT_LOCS_PATH.read_text(encoding="utf-8")
    for snippet in ('"id": 98', '"X": 114', '"Y": 700'):
        if snippet not in loc_text:
            fail(f"Runecraft stone source missing Varrock stone placement: {snippet}")

    other_locs = json.loads(SCENERY_OTHER_LOCS_PATH.read_text(encoding="utf-8"))
    stale_portals = [
        entry
        for entry in other_locs["sceneries"]
        if entry["id"] == 1236
        and entry["pos"] == {"X": 373, "Y": 3352}
    ]
    if stale_portals:
        fail("Legacy overworld rune-stone portal remains at 373,3352")


def main() -> None:
    ensure_expected_altars(load_altars())
    ensure_runecraft_supports_all_runes()
    ensure_multiplier_requirements_follow_progression()
    ensure_law_robe_bonus_uses_fractional_carryover()
    ensure_chaos_amulet_bonus_uses_fractional_carryover()
    ensure_stone_and_talismans_are_retired()
    print("PASS: runecraft/enchanting migration data validated")
    print(f"Altars validated: {len(EXPECTED_ALTARS)}")


if __name__ == "__main__":
    main()
