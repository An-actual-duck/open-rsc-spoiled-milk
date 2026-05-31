#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEVOTION = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "Devotion.java"
EQUIPMENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "container" / "Equipment.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def main() -> None:
    devotion = DEVOTION.read_text()
    equipment = EQUIPMENT.read_text()

    require(devotion, "COMBAT_GROWTH_START_LEVEL = 500", "combat growth start")
    require(devotion, "PRAYER_BONUS_GROWTH_MAX = 10", "prayer bonus growth cap")
    require(devotion, "getDevotionGrowthBonus", "shared devotion growth helper")
    require(devotion, "getPrayerBonusGrowth", "shared prayer growth helper")

    require(equipment, "getScaledWeaponAimBonus(item)", "scaled weapon aim in equipment total")
    require(equipment, "getScaledWeaponPowerBonus(item)", "scaled weapon power in equipment total")
    require(equipment, "getScaledArmourBonus(item)", "scaled armour in equipment total")
    require(equipment, "getGodEquipmentPrayerBonus(item.getCatalogId())", "god equipment prayer override")
    if not ("isZamorakBlessedStaff(itemId)" in equipment
            and "isSaradominBlessedStaff(itemId)" in equipment
            and "isGuthixBlessedStaff(itemId)" in equipment):
        fail("missing blessed staff god-line assignment")
    require(equipment, "return PrayerCatalog.GodLine.ZAMORAK;", "legacy blessed staves should be Zamorak")
    require(equipment, "Devotion.getDevotionGrowthBonus(player, godLine, targetValue - baseValue)",
            "combat stats should grow from current value to target value")
    require(equipment, "Devotion.getPrayerBonusGrowth(player, godLine)",
            "prayer bonus should gain devotion growth")
    require(equipment, "isBlessedWoolArmor(item.getCatalogId())",
            "blessed wool armor should participate in magic-defense scaling")
    require(equipment, "getBlessedWoolMagicDefense(item)",
            "blessed wool armor should have derived magic defense")

    for item in (
        "case 3131: // BLACK_GAUNTLETS",
        "case 3132: // BLACK_GREAVES",
        "case 3133: // WHITE_GAUNTLETS",
        "case 3134: // WHITE_GREAVES",
        "case 3135: // GREY_GAUNTLETS",
        "case 3136: // GREY_GREAVES",
        "case 3138: // ZAMORAK_WOOL_ROBE_TOP",
        "case 3143: // SARADOMIN_WOOL_ROBE_TOP",
        "case 3148: // GUTHIX_WOOL_ROBE_TOP",
    ):
        require(equipment, item, f"{item} worship matching/scaling coverage")

    require(equipment, "case 196: // BLACK_PLATE_MAIL_BODY", "black plate body resource cost")
    require(equipment, "case 2163: // WHITE_PLATE_MAIL_BODY", "white plate body resource cost")
    require(equipment, "case 3125: // GREY_PLATE_MAIL_BODY", "grey plate body resource cost")
    require(equipment, "return 4;", "plate body resource cost should produce baseline +4 prayer")
    require(equipment, "return 80;", "plate body should scale to rune plate body armour")
    require(equipment, "return 38;", "mace aim and paladin shield armour targets should reach rune equivalents")
    require(equipment, "return 28;", "mace power target should reach rune mace power")
    require(equipment, "+ resourceCost", "baseline prayer should include resource equivalency")

    print("PASS: devotion equipment scaling hooks are wired")


if __name__ == "__main__":
    main()
