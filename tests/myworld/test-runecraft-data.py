#!/usr/bin/env python3
import re
import sys
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

EXPECTED_ALTARS = {
    1191: ("air", 33, 1),
    1321: ("life", 37, 1),
    1193: ("mind", 35, 8),
    1195: ("water", 32, 1),
    1197: ("earth", 34, 1),
    1199: ("fire", 31, 1),
    1201: ("body", 36, 15),
    1203: ("cosmic", 46, 30),
    1205: ("chaos", 41, 22),
    1207: ("nature", 40, 38),
    1209: ("law", 42, 46),
    1211: ("death", 38, 54),
    1296: ("soul", 825, 62),
    1213: ("blood", 619, 70),
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
    previous_xp = -1
    for altar_id, (name, rune_id, req) in EXPECTED_ALTARS.items():
        actual_name, actual_rune_id, actual_req, actual_xp = altars[altar_id]
        if (actual_name, actual_rune_id, actual_req) != (name, rune_id, req):
            fail(
                f"Altar {altar_id} expected {(name, rune_id, req)} "
                f"but found {(actual_name, actual_rune_id, actual_req)}"
            )
        if actual_xp < previous_xp:
            fail(
                f"Altar XP must be non-decreasing by progression order, found {actual_xp} after {previous_xp}"
            )
        previous_xp = actual_xp


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


def main() -> None:
    ensure_expected_altars(load_altars())
    ensure_runecraft_supports_all_runes()
    ensure_multiplier_requirements_follow_progression()
    ensure_stone_and_talismans_are_retired()
    print("PASS: runecraft/enchanting migration data validated")
    print(f"Altars validated: {len(EXPECTED_ALTARS)}")


if __name__ == "__main__":
    main()
