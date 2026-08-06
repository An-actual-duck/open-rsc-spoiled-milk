#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import NoReturn

ROOT = Path(__file__).resolve().parents[2]
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
CUSTOM_ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
CLIENT_MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
HOOD_WORN = ROOT / "dev/myworld/assets/sprites/equipment/hood/numbered"
HOOD_ICON = ROOT / "dev/myworld/assets/sprites/items/inventory-ground/hood.png"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def main() -> None:
    item_id_text = ITEM_ID.read_text(encoding="utf-8")
    custom_items = json.loads(CUSTOM_ITEMS.read_text(encoding="utf-8"))["items"]
    client_defs = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    mudclient = CLIENT_MUDCLIENT.read_text(encoding="utf-8")

    require("HOOD(3191)" in item_id_text, "ItemId should define Hood")
    require("public static final int maxCustom = 3311;" in item_id_text, "maxCustom should include current custom items")

    hood = next((entry for entry in custom_items if entry["id"] == 3191), None)
    require(hood is not None, "ItemDefsCustom should define Hood")
    require(hood["name"] == "Hood", "Hood should have the expected name")
    require(hood["isWearable"] == 1, "Hood should be wearable")
    require(hood["appearanceID"] == 1034, "Hood should use its custom appearance")
    require(hood["wearableID"] == 32, "Hood should occupy the head equipment group")
    require(hood["wearSlot"] == 5, "Hood should use the head wear slot")
    require(hood["armourBonus"] == 0, "Hood should be aesthetic only")
    require(hood["weaponAimBonus"] == 0 and hood["weaponPowerBonus"] == 0, "Hood should not add combat stats")
    require(hood["magicBonus"] == 0 and hood["prayerBonus"] == 0, "Hood should not add magic or prayer stats")

    require('new ItemDef("Hood", "A simple hood.", ""' in client_defs, "Client should define Hood")
    require('"external-png:hood@35x27"' in client_defs, "Client should use the external hood icon")
    require('new AnimationDef("hood", "equipment", 0, 0, true, false, 0)' in client_defs,
            "Client should define the hood worn animation")
    require('loadExternalLayeredEquipmentSprite("hood", getExternalEquipmentNumberedFolder("hood"),' in mudclient,
            "Client should load external hood worn frames")
    require("Frame.LAYER.HEAD_NO_SKIN" in mudclient, "Hood should replace the head layer")

    require(HOOD_ICON.is_file(), "Hood inventory icon should exist")
    for index in range(18):
        require((HOOD_WORN / f"{index:02d}.png").is_file(), f"Hood worn frame {index:02d}.png should exist")

    print("PASS: Hood cosmetic item registered")


if __name__ == "__main__":
    main()
