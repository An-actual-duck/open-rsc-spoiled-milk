#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def main() -> None:
    equipment = (SERVER / "plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer/GodKnightEquipment.java").read_text(encoding="utf-8")
    constants = (SERVER / "src/com/openrsc/server/constants/ItemId.java").read_text(encoding="utf-8")
    custom_defs = json.loads((SERVER / "conf/server/defs/ItemDefsCustom.json").read_text(encoding="utf-8"))["items"]
    myworld_defs = json.loads((SERVER / "conf/server/defs/ItemDefsMyWorld.json").read_text(encoding="utf-8"))["items"]

    expected_items = {
        3131: ("Black gauntlets", 8),
        3132: ("Black greaves", 9),
        3133: ("White gauntlets", 8),
        3134: ("White greaves", 9),
        3135: ("Grey gauntlets", 8),
        3136: ("Grey greaves", 9),
    }
    custom_by_id = {item["id"]: item for item in custom_defs}
    myworld_by_id = {item["id"]: item for item in myworld_defs}

    for item_id, (name, slot) in expected_items.items():
        require(f"({item_id})" in constants, f"Missing ItemId constant for {name}")
        item = custom_by_id.get(item_id)
        require(item is not None, f"Missing custom item definition for {name}")
        require(item["name"] == name, f"Incorrect item name for {item_id}: {item['name']}")
        require(item["isWearable"] == 1 and item["wearSlot"] == slot, f"{name} should be wearable in slot {slot}")
        override = myworld_by_id.get(item_id)
        require(override is not None, f"Missing MyWorld combat override for {name}")
        require(override["meleeDefense"] == 8 and override["rangedDefense"] == 2, f"{name} should mirror steel hand/foot defense")
        require(override["prayerBonus"] == 1, f"{name} should have a small prayer bonus")

    for source, black, white, grey in (
        ("case 698: // STEEL_GAUNTLETS", "ItemId.BLACK_GAUNTLETS.id()", "ItemId.WHITE_GAUNTLETS.id()", "ItemId.GREY_GAUNTLETS.id()"),
        ("case 1988: // STEEL_GREAVES", "ItemId.BLACK_GREAVES.id()", "ItemId.WHITE_GREAVES.id()", "ItemId.GREY_GREAVES.id()"),
    ):
        require(source in equipment, f"Missing source mapping: {source}")
        require(black in equipment, f"Missing Zamorak product mapping for {source}")
        require(white in equipment, f"Missing Saradomin product mapping for {source}")
        require(grey in equipment, f"Missing Guthix product mapping for {source}")

    for source, resource_cost in (
        ("case 63: // STEEL_DAGGER", "return 1;"),
        ("case 72: // STEEL_LONG_SWORD", "return 2;"),
        ("case 78: // STEEL_2_HANDED_SWORD", "return 3;"),
        ("case 698: // STEEL_GAUNTLETS", "return 2;"),
        ("case 1988: // STEEL_GREAVES", "return 2;"),
        ("case 109: // LARGE_STEEL_HELMET", "return 2;"),
        ("case 129: // STEEL_KITE_SHIELD", "return 3;"),
        ("case 121: // STEEL_PLATE_MAIL_LEGS", "return 3;"),
        ("case 118: // STEEL_PLATE_MAIL_BODY", "return 4;"),
    ):
        require(source in equipment and resource_cost in equipment, f"Missing resource-cost rule for {source}")

    for retired_source in (
        "case 105: // MEDIUM_STEEL_HELMET",
        "case 114: // STEEL_CHAIN_MAIL_BODY",
        "case 1532: // STEEL_CHAIN_MAIL_TOP",
        "case 1420: // STEEL_CHAIN_MAIL_LEGS",
        "case 125: // STEEL_SQUARE_SHIELD",
        "case 309: // STEEL_PLATE_MAIL_TOP",
        "case 225: // STEEL_PLATED_SKIRT",
    ):
        require(retired_source not in equipment, f"Retired god knight source should not be convertible: {retired_source}")

    for active_product in (
        "ItemId.BLACK_2_HANDED_SWORD.id()",
        "ItemId.WHITE_2_HANDED_SWORD.id()",
        "ItemId.GREY_2_HANDED_SWORD.id()",
        "ItemId.BLACK_GAUNTLETS.id()",
        "ItemId.WHITE_GAUNTLETS.id()",
        "ItemId.GREY_GAUNTLETS.id()",
        "ItemId.BLACK_GREAVES.id()",
        "ItemId.WHITE_GREAVES.id()",
        "ItemId.GREY_GREAVES.id()",
    ):
        require(active_product in equipment, f"Active god knight product missing: {active_product}")

    for retired_product in (
        "MEDIUM_BLACK_HELMET", "MEDIUM_WHITE_HELMET", "MEDIUM_GREY_HELMET",
        "BLACK_CHAIN_MAIL", "WHITE_CHAIN_MAIL", "GREY_CHAIN_MAIL",
        "BLACK_SQUARE_SHIELD", "WHITE_SQUARE_SHIELD", "GREY_SQUARE_SHIELD",
        "BLACK_PLATE_MAIL_TOP", "WHITE_PLATE_MAIL_TOP", "GREY_PLATE_MAIL_TOP",
        "BLACK_PLATED_SKIRT", "WHITE_PLATED_SKIRT", "GREY_PLATED_SKIRT",
    ):
        require(retired_product not in equipment, f"Retired god knight product should not be altar-made: {retired_product}")

    require("Devotion.getDevotionLevel(player, godLine) < devotionRequirement" in equipment,
            "steel armour conversion should be gated by devotion")
    require("Devotion.getDevotionRequirementForResourceCost(getSteelResourceCost(itemId))" in equipment,
            "steel armour conversion should use resource cost * 50 devotion")
    require("Devotion.getBlessingPrayerXp(player, godLine, getSteelSmithingXp(item.getCatalogId()))" in equipment,
            "steel armour conversion Prayer XP should scale with devotion")
    require("player.incExp(Skill.PRAYER.id(), prayerXp, true)" in equipment,
            "steel armour conversion should grant Prayer XP")
    require("Devotion.addDevotionLevels(player, godLine, 1)" not in equipment,
            "steel armour conversion should not add devotion")

    require("public static final int maxCustom = 3172;" in constants, "ItemId.maxCustom should include blessed staff god variants")
    print("PASS: god knight armour conversion validated")


if __name__ == "__main__":
    main()
