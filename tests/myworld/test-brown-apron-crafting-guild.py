#!/usr/bin/env python3
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEM_DEFS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefs.json"
CLIENT_ITEM_DEFS_PATH = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "EntityHandler.java"
DOOR_ACTION_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "defaults" / "DoorAction.java"
CRAFTING_SHOP_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "CraftingEquipmentShops.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def main() -> None:
    with ITEM_DEFS_PATH.open("r", encoding="utf-8") as handle:
        items = json.load(handle)["item"]
    brown_apron = next((item for item in items if item.get("id") == 191), None)
    if brown_apron is None:
        fail("missing brown apron item id 191")
    if brown_apron.get("name") != "Brown apron":
        fail(f"brown apron item id 191 has name {brown_apron.get('name')!r}")

    client_defs = CLIENT_ITEM_DEFS_PATH.read_text(encoding="utf-8")
    if 'new ItemDef("Brown apron"' not in client_defs:
        fail("client item definitions do not label id 191 as Brown apron")

    door_action = DOOR_ACTION_PATH.read_text(encoding="utf-8")
    if "ItemId.BROWN_APRON.id()" not in door_action:
        fail("Crafting Guild door does not accept the brown apron")

    crafting_shop = CRAFTING_SHOP_PATH.read_text(encoding="utf-8")
    if "ItemId.BROWN_APRON.id()" not in crafting_shop:
        fail("crafting equipment shops do not stock the brown apron")


if __name__ == "__main__":
    main()
