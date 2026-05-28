#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ENCHANTING = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/enchanting/Enchanting.java"
)
EFFECTS = ROOT / "server/src/com/openrsc/server/content/EnchantingItemEffects.java"
GOD_KNIGHTS = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer/GodKnightEquipment.java"
)
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
PLAN = ROOT / "docs/myworld/altar-enchantment-and-conversion-plan.md"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def require_regex(source: str, pattern: str, message: str) -> None:
    require(re.search(pattern, source, flags=re.S) is not None, message)


def main() -> None:
    enchanting = ENCHANTING.read_text(encoding="utf-8")
    effects = EFFECTS.read_text(encoding="utf-8")
    god_knights = GOD_KNIGHTS.read_text(encoding="utf-8")
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    require_regex(
        effects,
        r"public static int getWoolRobeRuneCost\(final int tier\) \{\s*return tier > 0 \? tier \* 100 : -1;\s*\}",
        "Cloth armor upgrades should cost 100 matching altar runes per target tier",
    )
    require_regex(
        effects,
        r"public static int getStaffRuneCost\(final int tier\) \{\s*return tier > 0 \? tier \* 200 : -1;\s*\}",
        "Staff attunement should cost 200 matching altar runes per staff tier",
    )
    require_regex(
        effects,
        r"public static int getRuneCostForTier\(final int tier\) \{\s*return tier > 0 \? tier \* 50 : -1;\s*\}",
        "Jewelry enchantment should cost 50 matching altar runes per gem tier",
    )
    for forbidden in (
        "WOOL_ROBE_TIER_BARS",
        "getWoolRobeTierBar",
        "getStaffCosmicCost",
        "ItemId.TIN_BAR",
    ):
        require(forbidden not in enchanting and forbidden not in effects, f"Retired altar cost dependency remains: {forbidden}")

    for gate in (
        "player.getSkills().getLevel(Skill.RUNECRAFT.id()) < altarLevelRequirement",
        "EnchantingItemEffects.getStaffEnchantingRequirementForTier(tier)",
        "EnchantingItemEffects.getJewelryEnchantingRequirementForTier(tier)",
        "EnchantingItemEffects.getTemporaryEnchantingRequirementForTier(nextTier)",
    ):
        require(gate in enchanting, f"Missing altar/item level gate: {gate}")

    require("class GodKnightEquipment implements UseLocTrigger" in god_knights, "God knight conversion plugin is missing")
    require("Only steel equipment can be blessed this way." in god_knights, "God knight conversion should be steel-only")
    require("PrayerCatalog.GodLine.ZAMORAK" in god_knights, "Zamorak conversion route is missing")
    require("PrayerCatalog.GodLine.SARADOMIN" in god_knights, "Saradomin conversion route is missing")
    require("PrayerCatalog.GodLine.GUTHIX" in god_knights, "Guthix conversion route is missing")
    require(
        "getZamorakProduct" in god_knights
        and "getSaradominProduct" in god_knights
        and "getGuthixProduct" in god_knights,
        "God conversion product maps are missing",
    )
    require("ItemId.BLACK_PLATE_MAIL_BODY.id()" in god_knights, "Steel plate body should convert to black knight plate")
    require("ItemId.WHITE_PLATE_MAIL_BODY.id()" in god_knights, "Steel plate body should convert to white knight plate")
    require("ItemId.GREY_PLATE_MAIL_BODY.id()" in god_knights, "Steel plate body should convert to grey knight plate")
    require("case 118:" in god_knights and "case 121:" in god_knights and "case 129:" in god_knights,
            "Core steel armor conversion cases are missing")
    require("BLACK_PLATE_MAIL_BODY" not in god_knights.split("getZamorakProduct", 1)[0],
            "God conversion should not accept already-blessed black equipment as input")
    require("WHITE_PLATE_MAIL_BODY" not in god_knights.split("getSaradominProduct", 1)[0],
            "God conversion should not accept already-blessed white equipment as input")

    for snippet in (
        "isWhiteKnightEquipment(itemId)",
        "isBlackKnightEquipment(itemId)",
        "isGreyKnightEquipment(itemId)",
        "return PrayerCatalog.GodLine.SARADOMIN;",
        "return PrayerCatalog.GodLine.ZAMORAK;",
        "return PrayerCatalog.GodLine.GUTHIX;",
        "getActivePrayerBonus",
    ):
        require(snippet in equipment, f"God knight equip/prayer gate missing: {snippet}")

    for snippet in (
        "target tier * 100 matching altar runes",
        "staff tier * 200 matching altar runes",
        "gem tier * 50 matching altar runes",
        "Only ordinary steel equipment can be blessed.",
    ):
        require(snippet in plan, f"Plan doc missing rule: {snippet}")

    print("PASS: altar enchantment costs and god knight conversion guards validated")


if __name__ == "__main__":
    main()
