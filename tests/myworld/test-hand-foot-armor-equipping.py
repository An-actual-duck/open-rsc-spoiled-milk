#!/usr/bin/env python3
"""Validate hand/foot armour can be worn with body and leg armour."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
BASE_ITEMS = ROOT / "server/conf/server/defs/ItemDefs.json"
CUSTOM_ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
MYWORLD_ITEMS = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def main() -> None:
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    by_id = {}
    for item_file in (BASE_ITEMS, CUSTOM_ITEMS, MYWORLD_ITEMS):
        data = json.loads(item_file.read_text(encoding="utf-8"))
        for item in data.get("items", data.get("item", [])):
            existing = by_id.get(item["id"], {})
            existing.update(item)
            by_id[item["id"]] = existing

    require("allowsHandFootArmorOverlap(request.item, item)" in equipment,
            "equipment conflicts should exempt hand/foot armour versus body/leg armour")
    require("isHandFootArmorSlot" in equipment and "isBodyLegArmorSlot" in equipment,
            "hand/foot overlap exception should be slot-based")

    expected_slots = {
        698: 8,    # Steel gauntlets
        1988: 9,   # Steel greaves
        3131: 8,   # Black gauntlets
        3132: 9,   # Black greaves
        3133: 8,   # White gauntlets
        3134: 9,   # White greaves
        3135: 8,   # Grey gauntlets
        3136: 9,   # Grey greaves
    }
    for item_id, slot in expected_slots.items():
        item = by_id.get(item_id)
        require(item is not None, f"missing item {item_id}")
        require(item.get("isWearable") == 1, f"item {item_id} should be wearable")
        require(item.get("wearSlot") == slot, f"item {item_id} should equip to slot {slot}")

    print("PASS: hand and foot armour can coexist with body and leg armour")


if __name__ == "__main__":
    main()
