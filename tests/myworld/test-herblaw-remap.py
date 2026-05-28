#!/usr/bin/env python3
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
HERB_SECOND = ROOT / "server" / "conf" / "server" / "defs" / "extras" / "ItemHerbSecond.xml"
ITEM_DEFS = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefs.json"
ITEM_DEFS_CUSTOM = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
HERBLAW = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "herblaw" / "Herblaw.java"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_item_names(path: Path) -> dict[int, tuple[str, str, str]]:
    text = path.read_text(encoding="utf-8")
    data = json.loads(text)
    if isinstance(data, dict):
        if "items" in data:
            items = data["items"]
        elif "item" in data:
            items = data["item"]
        else:
            fail(f"Unexpected item-def container shape in {path}")
    else:
        items = data
    return {item["id"]: (item["name"], item["description"], item["command"]) for item in items}


def main() -> None:
    xml_root = ET.parse(HERB_SECOND).getroot()
    recipes = {
        (int(node.findtext("unfinishedID")), int(node.findtext("secondID"))): int(node.findtext("potionID"))
        for node in xml_root.findall("ItemHerbSecond")
    }

    expected_recipes = {
        (454, 473): 566,
        (455, 270): 474,
        (456, 469): 477,
        (457, 472): 572,
        (458, 471): 480,
        (459, 219): 486,
        (460, 220): 489,
        (461, 936): 492,
        (462, 501): 483,
        (463, 270): 495,
    }
    for pair, potion_id in expected_recipes.items():
        if recipes.get(pair) != potion_id:
            fail(f"Expected herb recipe {pair} -> {potion_id}")

    item_defs = load_item_names(ITEM_DEFS)
    custom_defs = load_item_names(ITEM_DEFS_CUSTOM)

    expected_names = {
        474: "Potion of Insight",
        477: "Potion of Regeneration",
        480: "Potion of Speed",
        483: "Potion of Luck",
        486: "Potion of Magic Resistance",
        489: "Potion of Melee Resistance",
        492: "Potion of Ranged Resistance",
        495: "Potion of Notation",
        566: "Cure poison Potion",
        569: "Super Potion of Insight",
        963: "Super Potion of Regeneration",
        1468: "Super Potion of Magic Resistance",
        1471: "Super Potion of Melee Resistance",
        1474: "Super Potion of Ranged Resistance",
    }
    for item_id, expected_name in expected_names.items():
        defs = custom_defs if item_id >= 1400 else item_defs
        actual = defs.get(item_id)
        if actual is None or actual[0] != expected_name:
            fail(f"Expected item {item_id} to be named '{expected_name}'")

    retired = custom_defs.get(1477)
    if retired is None or retired[0] != "Retired potion" or retired[2] != "":
        fail("Super magic potion IDs should be retired and non-drinkable")

    herblaw_text = HERBLAW.read_text(encoding="utf-8")
    for snippet in (
        "secondaryId == ItemId.FISH_OIL.id()",
        "secondaryId == ItemId.HALF_COCONUT.id()",
        "secondaryId == ItemId.WINE_OF_SARADOMIN.id()",
        "secondaryId == ItemId.SLICED_DRAGONFRUIT.id()",
        "secondaryId == ItemId.GROUND_UNICORN_HORN.id()",
    ):
        if snippet not in herblaw_text:
            fail(f"Herblaw custom super recipe missing: {snippet}")

    print("PASS: herblaw recipe and item remap validated")


if __name__ == "__main__":
    main()
