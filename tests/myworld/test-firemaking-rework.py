#!/usr/bin/env python3
"""Validate MyWorld Firemaking log-tier runtime data."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FIREMAKING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/firemaking/Firemaking.java"
FIREMAKING_DEF = ROOT / "server/conf/server/defs/extras/FiremakingDef.xml"
SKILL_GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"

EXPECTED_LOGS = {
    14: ("LOGS", 1, 90),
    2111: ("PINE_LOGS", 8, 100),
    632: ("OAK_LOGS", 15, 110),
    633: ("WILLOW_LOGS", 22, 130),
    2112: ("PALM_LOGS", 30, 140),
    634: ("MAPLE_LOGS", 38, 150),
    635: ("YEW_LOGS", 46, 170),
    2113: ("EBONY_LOGS", 54, 180),
    636: ("MAGIC_LOGS", 62, 190),
    2114: ("BLOOD_LOGS", 70, 200),
}


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def parse_firemaking_defs() -> dict[int, dict[str, int]]:
    root = ET.parse(FIREMAKING_DEF).getroot()
    entries = {}
    for entry in root.findall("entry"):
        item_id = int(entry.findtext("int"))
        def_node = entry.find("FiremakingDef")
        entries[item_id] = {
            "level": int(def_node.findtext("level")),
            "exp": int(def_node.findtext("exp")),
            "length": int(def_node.findtext("length")),
        }
    return entries


def main() -> None:
    firemaking_text = FIREMAKING.read_text(encoding="utf-8")
    skill_guide_text = SKILL_GUIDE.read_text(encoding="utf-8")
    definitions = parse_firemaking_defs()

    if set(definitions) != set(EXPECTED_LOGS):
        missing = sorted(set(EXPECTED_LOGS) - set(definitions))
        extra = sorted(set(definitions) - set(EXPECTED_LOGS))
        fail(f"FiremakingDef log set mismatch; missing={missing}, extra={extra}")

    last_exp = 0
    for item_id, (constant, level, length) in EXPECTED_LOGS.items():
        if f"ItemId.{constant}.id()" not in firemaking_text:
            fail(f"Firemaking LOGS array missing ItemId.{constant}.id()")
        if f"case {constant}:" not in firemaking_text:
            fail(f"Custom Firemaking switch missing {constant}")
        if definitions[item_id]["level"] != level:
            fail(f"{constant} requires level {definitions[item_id]['level']}, expected {level}")
        if definitions[item_id]["length"] != length:
            fail(f"{constant} burns for {definitions[item_id]['length']}s, expected {length}s")
        if definitions[item_id]["exp"] <= last_exp:
            fail(f"{constant} Firemaking XP should increase by tier")
        last_exp = definitions[item_id]["exp"]
        if f'new SkillMenuItem({item_id}, "{level}",' not in skill_guide_text:
            fail(f"Firemaking guide missing {constant} level {level}")

    print("PASS: Firemaking log-tier runtime data validated")


if __name__ == "__main__":
    main()
