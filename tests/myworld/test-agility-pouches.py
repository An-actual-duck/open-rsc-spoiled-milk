#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
ITEM_ID_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "constants" / "ItemId.java"
ITEM_DEFS_CUSTOM_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
ENTITY_HANDLER_PATH = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "EntityHandler.java"
AGILITY_UTILS_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "agility" / "AgilityUtils.java"
GNOME_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "agility" / "GnomeAgilityCourse.java"
BARBARIAN_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "agility" / "BarbarianAgilityCourse.java"
WILDERNESS_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "agility" / "WildernessAgilityCourse.java"
POUCH_PLUGIN_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "myworld" / "skills" / "agility" / "AgilityRewardPouches.java"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(path: Path, snippet: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def main() -> None:
    require(ITEM_ID_PATH, "TIER_1_AGILITY_POUCH(2328)", "ItemId.java")
    require(ITEM_ID_PATH, "TIER_2_AGILITY_POUCH(2329)", "ItemId.java")
    require(ITEM_ID_PATH, "TIER_3_AGILITY_POUCH(2330)", "ItemId.java")
    item_id_text = ITEM_ID_PATH.read_text(encoding="utf-8")
    max_custom_match = re.search(r"public static final int maxCustom = (\d+);", item_id_text)
    if not max_custom_match or int(max_custom_match.group(1)) <= 2330:
        fail("ItemId.maxCustom must include the agility pouch IDs")

    defs = json.loads(ITEM_DEFS_CUSTOM_PATH.read_text(encoding="utf-8"))["items"]
    by_id = {item["id"]: item for item in defs}
    expected = {
        2328: ("Tier 1 Agility Pouch", 1),
        2329: ("Tier 2 Agility Pouch", 35),
        2330: ("Tier 3 Agility Pouch", 52),
    }
    for item_id, (name, req) in expected.items():
        item = by_id.get(item_id)
        if item is None:
            fail(f"Missing agility pouch item def {item_id}")
        if item["name"] != name:
            fail(f"Agility pouch {item_id} has unexpected name: {item['name']}")
        if item["isStackable"] != 1 or item["isUntradable"] != 0:
            fail(f"Agility pouch {item_id} should be stackable and tradable")
        if item["requiredLevel"] != req or item["requiredSkillID"] != 16:
            fail(f"Agility pouch {item_id} should record agility requirement {req}")

    require(ENTITY_HANDLER_PATH, 'setCustomItemDefinition(2328, new ItemDef("Tier 1 Agility Pouch"', "EntityHandler.java")
    require(ENTITY_HANDLER_PATH, 'setCustomItemDefinition(2329, new ItemDef("Tier 2 Agility Pouch"', "EntityHandler.java")
    require(ENTITY_HANDLER_PATH, 'setCustomItemDefinition(2330, new ItemDef("Tier 3 Agility Pouch"', "EntityHandler.java")
    require(ENTITY_HANDLER_PATH, '300, 187, "items:187"', "Tier 1 temporary built-in casket sprite")
    require(ENTITY_HANDLER_PATH, '900, 187, "items:187"', "Tier 2 temporary built-in casket sprite")
    require(ENTITY_HANDLER_PATH, '1800, 187, "items:187"', "Tier 3 temporary built-in casket sprite")

    require(AGILITY_UTILS_PATH, "new Item(completionRewardId, 1)", "AgilityUtils.java")
    require(AGILITY_UTILS_PATH, "You receive a \" + pouch.getDef(player.getWorld()).getName()", "AgilityUtils.java")
    require(GNOME_PATH, "ItemId.TIER_1_AGILITY_POUCH.id()", "GnomeAgilityCourse.java")
    require(BARBARIAN_PATH, "ItemId.TIER_2_AGILITY_POUCH.id()", "BarbarianAgilityCourse.java")
    require(WILDERNESS_PATH, "ItemId.TIER_3_AGILITY_POUCH.id()", "WildernessAgilityCourse.java")

    require(POUCH_PLUGIN_PATH, "getRareDropTable().rollItem(player)", "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, "final int desiredDrops = DataConversions.random(config.minDrops, config.maxDrops);", "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, "availableCategories.remove(category);", "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, 'new RewardCategory("Runes", 18,', "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, 'new RewardCategory("Arrows", 14,', "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, 'new RewardCategory("Herbs", 10,', "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, 'new RewardCategory("Ingredients", 8,', "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, 'new RewardCategory("Potions", 8,', "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, "new RewardOption(ItemId.TIN_ORE.id()", "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, "new RewardOption(ItemId.PINE_LOGS.id()", "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, "new RewardOption(ItemId.COSMIC_RUNE.id()", "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, "new RewardOption(ItemId.CHAOS_RUNE.id()", "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, "new RewardOption(ItemId.AIR_RUNE.id(), 5, 10", "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, "new RewardOption(ItemId.TIN_ARROWS.id(), 5, 10", "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, "new RewardOption(ItemId.LAW_RUNE.id(), 5, 10", "AgilityRewardPouches.java")
    require(POUCH_PLUGIN_PATH, "new RewardOption(ItemId.RUNE_ARROWS.id(), 5, 10", "AgilityRewardPouches.java")

    print("PASS: agility pouch setup validated")


if __name__ == "__main__":
    main()
