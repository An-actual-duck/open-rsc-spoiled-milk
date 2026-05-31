#!/usr/bin/env python3
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ITEM_ID = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "constants" / "ItemId.java"
ITEMS = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
PLUGIN = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "myworld" / "skills" / "prayer" / "BlessedWoolArmor.java"
EQUIPMENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "container" / "Equipment.java"
CLIENT = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "EntityHandler.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def main() -> None:
    item_id = ITEM_ID.read_text()
    plugin = PLUGIN.read_text()
    equipment = EQUIPMENT.read_text()
    client = CLIENT.read_text()
    items = {item["id"]: item for item in json.loads(ITEMS.read_text())["items"]}

    max_custom = re.search(r"public static final int maxCustom = (\d+);", item_id)
    require(max_custom is not None and int(max_custom.group(1)) >= 3172,
            "ItemId.maxCustom must include blessed wool armor")

    expected = {
        3137: ("Wool hat blessed by Zamorak", 5, 1),
        3138: ("Wool robe top blessed by Zamorak", 6, 4),
        3139: ("Wool robe bottom blessed by Zamorak", 7, 3),
        3140: ("Wool gloves blessed by Zamorak", 8, 2),
        3141: ("Wool boots blessed by Zamorak", 9, 2),
        3142: ("Wool hat blessed by Saradomin", 5, 1),
        3143: ("Wool robe top blessed by Saradomin", 6, 4),
        3144: ("Wool robe bottom blessed by Saradomin", 7, 3),
        3145: ("Wool gloves blessed by Saradomin", 8, 2),
        3146: ("Wool boots blessed by Saradomin", 9, 2),
        3147: ("Wool hat blessed by Guthix", 5, 1),
        3148: ("Wool robe top blessed by Guthix", 6, 4),
        3149: ("Wool robe bottom blessed by Guthix", 7, 3),
        3150: ("Wool gloves blessed by Guthix", 8, 2),
        3151: ("Wool boots blessed by Guthix", 9, 2),
    }
    for item_id_value, (name, slot, prayer) in expected.items():
        item = items.get(item_id_value)
        require(item is not None, f"missing item {item_id_value}")
        require(item["name"] == name, f"{item_id_value} name mismatch")
        require(item["wearSlot"] == slot, f"{name} wear slot mismatch")
        require(item["prayerBonus"] == prayer, f"{name} baseline prayer mismatch")
    require("addBlessedWoolArmorDefinitions();" in client, "blessed wool client definitions should be registered")
    require('final String[] gods = {"Zamorak", "Saradomin", "Guthix"};' in client,
            "blessed wool client definitions should include all three gods")
    require('"Wool robe top blessed by " + god' in client,
            "blessed wool client definitions should generate robe tops")

    require("EnchantingItemEffects.isBaseWoolRobePiece(item.getCatalogId())" in plugin,
            "blessed wool conversion should start from base wool armor")
    require("Devotion.getDevotionRequirementForResourceCost(resourceCost)" in plugin,
            "blessed wool conversion should use resource-cost devotion requirements")
    require("Devotion.getBlessingPrayerXp(player, godLine, getWoolCraftingXp(item.getCatalogId()))" in plugin,
            "blessed wool conversion should grant scaled Prayer XP")
    require("return getWoolResourceCost(itemId) * 6;" in plugin,
            "blessed wool Prayer XP should mirror wool crafting XP")
    require("isBlessedWoolArmor(item.getCatalogId())" in equipment,
            "blessed wool armor should have runtime magic-defense scaling")
    require("getBlessedWoolTargetMagicDefense" in equipment,
            "blessed wool armor should scale toward robe tier targets")

    print("PASS: blessed wool armor conversion and definitions validated")


if __name__ == "__main__":
    main()
