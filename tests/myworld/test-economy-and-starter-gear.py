#!/usr/bin/env python3
"""Validate MyWorld starter/economy gear policy."""

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEM_DEFS_MYWORLD = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"
ENTITY_HANDLER = ROOT / "server/src/com/openrsc/server/external/EntityHandler.java"
ITEM_DEFINITION = ROOT / "server/src/com/openrsc/server/external/ItemDefinition.java"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
IRONMAN_NPC = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/authentic/npcs/tutorial/IronMan.java"
)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def item_by_id() -> dict[int, dict]:
    data = json.loads(ITEM_DEFS_MYWORLD.read_text(encoding="utf-8"))
    return {entry["id"]: entry for entry in data["items"]}


def main() -> None:
    items = item_by_id()

    tin_shears = items.get(144)
    require(tin_shears is not None, "Tin shears override is missing")
    require(tin_shears.get("name") == "Tin shears", "Legacy shears should be renamed to Tin shears")
    require(tin_shears.get("isWearable") == 1, "Tin shears should be wearable from item data")
    require(tin_shears.get("wearableID") == 16, "Tin shears should use main-hand wearable data")
    require(tin_shears.get("wearSlot") == 4, "Tin shears should equip to main hand")
    require(tin_shears.get("requiredLevel") == 1, "Tin shears should require harvesting level 1")
    require(tin_shears.get("requiredSkillID") == 19, "Tin shears should use the harvesting skill gate")

    expected_tool_prices = {
        144: 40,
        1987: 40,
        2047: 80,
        156: 180,
        87: 180,
        1258: 420,
        12: 420,
        1259: 900,
        88: 900,
        1260: 1800,
        203: 1800,
        2048: 3200,
        2023: 3200,
        1261: 5500,
        204: 5500,
        2049: 9000,
        2034: 9000,
        1262: 15000,
        405: 15000,
    }
    for item_id, expected_price in expected_tool_prices.items():
        require(item_id in items, f"Missing tool price override for item {item_id}")
        actual = items[item_id].get("basePrice")
        require(
            actual == expected_price,
            f"Tool item {item_id} should have basePrice {expected_price}, found {actual}",
        )

    entity_handler = ENTITY_HANDLER.read_text(encoding="utf-8")
    for snippet in (
        'item.has("isWearable")',
        'item.has("wearableID")',
        'item.has("wearSlot")',
        'item.has("basePrice")',
        "existing.setDefaultPrice",
    ):
        require(snippet in entity_handler, f"EntityHandler should apply item override field: {snippet}")

    item_definition = ITEM_DEFINITION.read_text(encoding="utf-8")
    require("public void setDefaultPrice(int defaultPrice)" in item_definition, "ItemDefinition should expose price overrides")

    client_entity_handler = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    require("applyMyWorldEconomyOverrides();" in client_entity_handler, "Client should apply MyWorld shop price overrides")
    client_price_pairs = {
        (int(item_id), int(price))
        for item_id, price in re.findall(r"\{(\d+),\s*(\d+)\}", client_entity_handler)
    }
    for item_id, item in items.items():
        if "basePrice" not in item:
            continue
        expected_pair = (item_id, int(item["basePrice"]))
        require(
            expected_pair in client_price_pairs,
            f"Client shop display basePrice for item {item_id} should match server basePrice {item['basePrice']}",
        )

    ironman = IRONMAN_NPC.read_text(encoding="utf-8")
    require("Have you any armour for me, please?" in ironman, "Ironman NPC should keep the armour replacement option")
    require("missingArmour(player, ArmourPart.ANY)" in ironman, "Ironman NPC should only replace missing armour pieces")
    require("Ironman armour handouts are not used in Spoiled Milk." not in ironman, "Ironman NPC should not block cosmetic armour in MyWorld")

    ironman_armour_caps = {
        1290: 1,  # Ironman helm
        1291: 2,  # Ironman platebody
        1292: 1,  # Ironman platelegs
        1293: 1,  # Ultimate ironman helm
        1294: 2,  # Ultimate ironman platebody
        1295: 1,  # Ultimate ironman platelegs
        1296: 1,  # Hardcore ironman helm
        1297: 2,  # Hardcore ironman platebody
        1298: 1,  # Hardcore ironman platelegs
        1554: 2, # Ironman plate top
        1555: 2, # Ultimate ironman plate top
        1556: 2, # Hardcore ironman plate top
        1557: 1, # Ironman plated skirt
        1558: 1, # Ultimate ironman plated skirt
        1559: 1, # Hardcore ironman plated skirt
    }
    for item_id, max_melee_defense in ironman_armour_caps.items():
        require(item_id in items, f"Missing Ironman armour override for item {item_id}")
        require(
            items[item_id].get("meleeDefense", 0) <= max_melee_defense,
            f"Ironman armour item {item_id} should stay cosmetic-tier, found meleeDefense {items[item_id].get('meleeDefense')}",
        )
        require(items[item_id].get("requiredLevel") == 0, f"Ironman armour item {item_id} should remain wearable at level 1")

    print("PASS: MyWorld economy gear and ironman handout policy validated")


if __name__ == "__main__":
    main()
