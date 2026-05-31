#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BASE_ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefs.json"
CUSTOM_ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
GUIDE_PATH = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "interfaces" / "misc" / "SkillGuideInterface.java"


EXPECTED_REQUIREMENTS = {
    0: 22, 1: 22, 28: 22,
    62: 15, 63: 30, 64: 38, 65: 54, 66: 15, 67: 30, 68: 38, 69: 54,
    70: 15, 71: 22, 72: 30, 73: 38, 74: 54, 75: 70, 76: 15, 77: 22,
    78: 30, 79: 38, 80: 54, 81: 70, 82: 15, 83: 22, 84: 30, 85: 38,
    86: 54, 89: 22, 90: 30, 91: 38, 92: 54, 93: 70, 94: 15, 95: 30,
    96: 38, 97: 54, 98: 70, 205: 15, 396: 70, 397: 70, 398: 70,
    559: 22, 560: 15, 561: 30, 562: 38, 563: 70, 564: 54, 565: 30,
    827: 15, 1088: 22, 1089: 30, 1090: 38, 1091: 54, 1092: 70,
    1135: 15, 1136: 22, 1137: 30, 1138: 38, 1139: 54, 1140: 70,
    1995: 1, 1997: 1, 1998: 1, 1999: 1, 2000: 1, 2002: 1, 2003: 1,
    2006: 8, 2008: 8, 2009: 8, 2010: 8, 2011: 8, 2013: 8, 2014: 8,
    2017: 46, 2019: 46, 2020: 46, 2021: 46, 2022: 46, 2024: 46, 2025: 46,
    2028: 62, 2030: 62, 2031: 62, 2032: 62, 2033: 62, 2035: 62, 2036: 62,
    2207: 1, 2208: 8, 2209: 46, 2210: 62, 2211: 1, 2212: 8, 2213: 46, 2214: 62,
    593: 80, 1346: 80, 1447: 80, 2752: 80,
}

DRAGON_GUIDE_ROWS = (
    'addMeleeTierGuide("Dragon dagger", 80, 1447);',
    'addMeleeTierGuide("Dragon sword", 80, 593);',
    'addMeleeTierGuide("Dragon 2-handed sword", 80, 1346);',
    'addMeleeTierGuide("Dragon battle axe", 80, 2752);',
)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_items(path: Path, key: str) -> list[dict]:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    return data[key]


def main() -> None:
    items_by_id = {entry["id"]: entry for entry in load_items(BASE_ITEMS_PATH, "item")}
    items_by_id.update({entry["id"]: entry for entry in load_items(CUSTOM_ITEMS_PATH, "items")})

    for item_id, expected_level in sorted(EXPECTED_REQUIREMENTS.items()):
        entry = items_by_id.get(item_id)
        if entry is None:
            fail(f"missing melee weapon item id {item_id}")
        actual_level = entry.get("requiredLevel")
        if actual_level != expected_level:
            fail(f"{entry.get('name', item_id)} id {item_id} requires level {actual_level}, expected {expected_level}")
        if entry.get("requiredSkillID") != 0:
            fail(f"{entry.get('name', item_id)} id {item_id} should use legacy melee requirement skill 0")

    guide = GUIDE_PATH.read_text(encoding="utf-8")
    for row in DRAGON_GUIDE_ROWS:
        if row not in guide:
            fail(f"missing guide row: {row}")

    for guide_name, level, item_id in re.findall(r'addMeleeTierGuide\("([^"]+)", (\d+), (\d+)\);', guide):
        item_id_int = int(item_id)
        if item_id_int in EXPECTED_REQUIREMENTS and int(level) != EXPECTED_REQUIREMENTS[item_id_int]:
            fail(f"guide row for {guide_name} id {item_id} shows {level}, expected {EXPECTED_REQUIREMENTS[item_id_int]}")


if __name__ == "__main__":
    main()
