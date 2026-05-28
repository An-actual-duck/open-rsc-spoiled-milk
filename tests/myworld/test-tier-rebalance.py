#!/usr/bin/env python3
"""Validate current MyWorld crafting/smelting tier rebalance data."""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
CRAFTING_DEFS = ROOT / "server/conf/server/defs/extras/ItemCraftingDef.xml"
GEM_DEFS = ROOT / "server/conf/server/defs/extras/ItemGemDef.xml"
SMITHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smithing.java"
SMELTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java"
SMELTING_DEFS = ROOT / "server/conf/server/defs/extras/ItemSmeltingDef.xml"


STANDARD_JEWELRY_LEVELS = {
    283: 1,   # gold ring
    288: 3,   # gold necklace
    296: 5,   # gold amulet
    284: 8,   # sapphire ring
    289: 10,  # sapphire necklace
    297: 13,  # sapphire amulet
    285: 18,  # emerald ring
    290: 22,  # emerald necklace
    298: 26,  # emerald amulet
    286: 32,  # ruby ring
    291: 38,  # ruby necklace
    299: 44,  # ruby amulet
    287: 48,  # diamond ring
    292: 54,  # diamond necklace
    300: 60,  # diamond amulet
    543: 58,  # dragonstone ring
    544: 64,  # dragonstone necklace
    524: 70,  # dragonstone amulet
}

STANDARD_GEM_CUTTING_LEVELS = {
    160: 8,   # uncut sapphire
    159: 18,  # uncut emerald
    158: 32,  # uncut ruby
    157: 48,  # uncut diamond
    542: 58,  # uncut dragonstone
}

SIDE_GEM_CUTTING_LEVELS = {
    891: 10,  # uncut opal
    890: 13,  # uncut jade
    889: 16,  # uncut red topaz
}

SMELT_LEVEL_SNIPPETS = {
    "TIN_BAR": 1,
    "COPPER_BAR": 8,
    "BRONZE_BAR": 15,
    "SILVER_BAR": 20,
    "IRON_BAR": 22,
    "STEEL_BAR": 30,
    "MITHRIL_BAR": 38,
    "GOLD_BAR": 40,
    "TITAN_STEEL_BAR": 46,
    "ADAMANTITE_BAR": 54,
    "ORICHALCUM_BAR": 62,
    "RUNITE_BAR": 70,
}


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_required_levels(path: Path, def_name: str, id_field: str) -> dict[int, int]:
    tree = ET.parse(path)
    result: dict[int, int] = {}
    for entry in tree.getroot().findall("entry"):
        def_node = entry.find(def_name)
        if def_node is None:
            continue
        item_id = int(def_node.findtext(id_field, default="-1"))
        result[item_id] = int(def_node.findtext("requiredLvl", default="-1"))
    return result


def require_levels(label: str, actual: dict[int, int], expected: dict[int, int]) -> None:
    for item_id, required_level in expected.items():
        if actual.get(item_id) != required_level:
            fail(f"{label} id {item_id} expected level {required_level}, found {actual.get(item_id)}")


def require_smelting_enum_levels() -> None:
    text = SMELTING.read_text(encoding="utf-8")
    for bar_name, required_level in SMELT_LEVEL_SNIPPETS.items():
        pattern = rf"new SmeltRecipe\(ItemId\.{bar_name}\.id\(\),\s*{required_level},"
        if re.search(pattern, text) is None:
            fail(f"Smelting recipe {bar_name} should require level {required_level}")


def require_legacy_smelting_def_levels() -> None:
    # The live Smelting plugin uses its enum, but keep XML metadata aligned so
    # future audits do not report stale legacy values.
    expected = {
        150: 15,  # copper + tin = bronze
        202: 15,  # tin + copper = bronze
        383: 20,  # silver
        151: 30,  # iron + coal = steel
        152: 40,  # gold
        153: 38,  # mithril
        154: 54,  # adamantite
        155: 30,  # coal + iron = steel
        409: 70,  # rune
        690: 40,  # quest gold
        9999: 22, # iron fallback compatibility entry
        2753: 30, # pig iron + coal = steel
    }
    levels = {}
    tree = ET.parse(SMELTING_DEFS)
    for entry in tree.getroot().findall("entry"):
        key = int(entry.findtext("int", default="-1"))
        def_node = entry.find("ItemSmeltingDef")
        if def_node is not None:
            levels[key] = int(def_node.findtext("requiredLvl", default="-1"))
    require_levels("legacy smelting def", levels, expected)


def require_smithing_min_levels() -> None:
    text = SMITHING.read_text(encoding="utf-8")
    expected = "int[] levels = {1, 8, 15, 22, 30, 38, 46, 54, 62, 70};"
    if expected not in text:
        fail("Smithing minSmithingLevel should follow the 1-70 metal bar ladder")


def main() -> None:
    crafting_levels = load_required_levels(CRAFTING_DEFS, "ItemCraftingDef", "itemID")
    gem_levels = load_required_levels(GEM_DEFS, "ItemGemDef", "gemID")
    # ItemGemDef stores output gem ids, but the XML entry keys are the uncut ids.
    uncut_gem_levels = {}
    tree = ET.parse(GEM_DEFS)
    for entry in tree.getroot().findall("entry"):
        key = int(entry.findtext("int", default="-1"))
        def_node = entry.find("ItemGemDef")
        if def_node is not None:
            uncut_gem_levels[key] = int(def_node.findtext("requiredLvl", default="-1"))

    require_levels("standard jewelry", crafting_levels, STANDARD_JEWELRY_LEVELS)
    require_levels("standard gem cutting", uncut_gem_levels, STANDARD_GEM_CUTTING_LEVELS)
    require_levels("side gem cutting", uncut_gem_levels, SIDE_GEM_CUTTING_LEVELS)
    require_smelting_enum_levels()
    require_legacy_smelting_def_levels()
    require_smithing_min_levels()
    print("PASS: tier rebalance data validated")


if __name__ == "__main__":
    main()
