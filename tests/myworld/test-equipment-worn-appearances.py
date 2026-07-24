#!/usr/bin/env python3
"""Validate worn appearances for enchanted jewelry and poisoned knives."""

import json
import re
import sys
from pathlib import Path
from typing import Any, NoReturn


ROOT = Path(__file__).resolve().parents[2]
BASE_ITEMS_PATH = ROOT / "server/conf/server/defs/ItemDefs.json"
CUSTOM_ITEMS_PATH = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
MYWORLD_ITEMS_PATH = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"
APPEARANCE_IDS_PATH = (
    ROOT / "server/src/com/openrsc/server/constants/AppearanceId.java"
)
EQUIPMENT_PATH = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
PLAYER_PATH = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"

TIERS = ("Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone")
JEWELRY_FAMILIES = {
    "Bangel of Teleportation": range(1709, 1714),
    "Necklace of Preservation": range(1759, 1764),
    "Bangel of Command": range(3106, 3111),
}
POISONED_KNIFE_PAIRS = {
    2200: 1996,
    2201: 2007,
    2202: 2018,
    2203: 2029,
}


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_items(path: Path) -> dict[int, dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict):
        entries = next(
            (
                value
                for value in data.values()
                if isinstance(value, list)
                and (not value or isinstance(value[0], dict))
            ),
            None,
        )
        if entries is None:
            fail(f"{path} does not contain an item-definition list")
    else:
        entries = data
    return {int(entry["id"]): entry for entry in entries}


def load_appearance_categories() -> dict[int, tuple[str, str]]:
    source = APPEARANCE_IDS_PATH.read_text(encoding="utf-8")
    entries = re.findall(
        r"^\s*([A-Z][A-Z0-9_]*)\((\d+),\s*([A-Z]+)\)",
        source,
        re.MULTILINE,
    )
    return {
        int(appearance_id): (name, category)
        for name, appearance_id, category in entries
    }


def require_item(
    items: dict[int, dict[str, Any]], item_id: int, expected_name: str
) -> dict[str, Any]:
    item = items.get(item_id)
    if item is None:
        fail(f"Missing item {item_id}: {expected_name}")
    if item.get("name") != expected_name:
        fail(
            f"Item {item_id} expected name {expected_name!r}, "
            f"found {item.get('name')!r}"
        )
    return item


def ensure_jewelry_appearances(
    custom_items: dict[int, dict[str, Any]],
    appearances: dict[int, tuple[str, str]],
) -> None:
    gold_necklace = appearances.get(81)
    if gold_necklace != ("GOLD_NECKLACE", "AMULET"):
        fail(f"Appearance 81 should be GOLD_NECKLACE/AMULET, found {gold_necklace}")

    for family, item_ids in JEWELRY_FAMILIES.items():
        for tier, item_id in zip(TIERS, item_ids):
            item = require_item(custom_items, item_id, f"{tier} {family}")
            is_bangel = "Bangel" in family
            expected_appearance = 0 if is_bangel else 81
            expected_slot = 14 if is_bangel else 10
            expected_wearable = 0 if is_bangel else 1024
            if item.get("appearanceID") != expected_appearance:
                fail(
                    f"{item['name']} ({item_id}) should use appearance "
                    f"{expected_appearance}, found {item.get('appearanceID')}"
                )
            if (
                item.get("wearSlot") != expected_slot
                or item.get("wearableID") != expected_wearable
            ):
                fail(
                    f"{item['name']} ({item_id}) should use equipment slot "
                    f"{expected_slot} and wearable ID {expected_wearable}"
                )

    neck_name = re.compile(r"\b(?:amulet|necklace|pendant|symbol)\b", re.IGNORECASE)
    for item in custom_items.values():
        if not item.get("isWearable") or not neck_name.search(item.get("name", "")):
            continue
        appearance_id = int(item.get("appearanceID", 0))
        appearance = appearances.get(appearance_id)
        if appearance is not None and appearance[1] != "AMULET":
            fail(
                f"Neck jewelry {item['name']} ({item['id']}) resolves to "
                f"{appearance[0]}/{appearance[1]} appearance {appearance_id}"
            )


def ensure_poisoned_knife_appearances(
    base_items: dict[int, dict[str, Any]],
    custom_items: dict[int, dict[str, Any]],
) -> None:
    for poisoned_id, plain_id in POISONED_KNIFE_PAIRS.items():
        poisoned = custom_items.get(poisoned_id)
        plain = custom_items.get(plain_id) or base_items.get(plain_id)
        if poisoned is None or plain is None:
            fail(f"Missing poisoned/plain throwing knife pair {poisoned_id}/{plain_id}")
        if "Poisoned " not in poisoned.get("name", ""):
            fail(f"Item {poisoned_id} should remain a poisoned throwing knife")
        if poisoned.get("appearanceID") != plain.get("appearanceID"):
            fail(
                f"{poisoned['name']} ({poisoned_id}) appearance "
                f"{poisoned.get('appearanceID')} should match {plain['name']} "
                f"({plain_id}) appearance {plain.get('appearanceID')}"
            )
        if poisoned.get("wearSlot") != 4 or poisoned.get("wearableID") != 16:
            fail(
                f"{poisoned['name']} ({poisoned_id}) should remain a mainhand weapon"
            )


def ensure_direct_worn_appearance_path() -> None:
    equipment = EQUIPMENT_PATH.read_text(encoding="utf-8")
    player = PLAYER_PATH.read_text(encoding="utf-8")
    if (
        "player.updateWornItems(itemDef.getWieldPosition(), itemDef.getAppearanceId(), "
        "itemDef.getWearableId(), true);"
        not in equipment
    ):
        fail("Equipment should send the item definition appearance without an ID offset")
    if "wornItems[indexPosition] = resolvedAppearanceId;" not in player:
        fail("Player worn-item state should retain the resolved definition appearance")


def main() -> None:
    base_items = load_items(BASE_ITEMS_PATH)
    custom_items = load_items(CUSTOM_ITEMS_PATH)
    for item_id, override in load_items(MYWORLD_ITEMS_PATH).items():
        if item_id in custom_items:
            custom_items[item_id].update(override)
    appearances = load_appearance_categories()

    ensure_jewelry_appearances(custom_items, appearances)
    ensure_poisoned_knife_appearances(base_items, custom_items)
    ensure_direct_worn_appearance_path()

    print("PASS: equipment worn appearances validated")


if __name__ == "__main__":
    main()
