#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EFFECTS = ROOT / "server/src/com/openrsc/server/content/EnchantingItemEffects.java"
RESTRICTIONS = ROOT / "server/plugins/com/openrsc/server/plugins/shared/EntranaRestrictions.java"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    effects = EFFECTS.read_text(encoding="utf-8")
    restrictions = RESTRICTIONS.read_text(encoding="utf-8")

    require(
        "import com.openrsc.server.content.EnchantingItemEffects;" in restrictions,
        "Entrana restrictions should use the enchanting helper",
    )
    require(
        "EnchantingItemEffects.isLawAltarAllowedItem(item.getCatalogId())" in restrictions,
        "Entrana restrictions should allow law altar items",
    )
    law_exception = restrictions.index("EnchantingItemEffects.isLawAltarAllowedItem(item.getCatalogId())")
    require(
        law_exception < restrictions.index("DataConversions.inArray(blockedCustomEquipment"),
        "Law altar exception should run before custom equipment blocks",
    )
    require(
        law_exception < restrictions.index("item.getCatalogId() <= ItemId.SCYTHE.id()"),
        "Law altar exception should run before authentic block list checks",
    )

    required_effects = (
        "public static boolean isLawAltarAllowedItem(final int itemId)",
        "return isLawAltarEnchantingInput(itemId) || isLawAltarProduct(itemId);",
        "public static boolean isLawAltarEnchantingInput(final int itemId)",
        "isBangleBase(itemId)",
        "isNecklaceBase(itemId)",
        "isRingBase(itemId)",
        "isBaseStaff(itemId)",
        "isBaseWoolRobePiece(itemId)",
        "public static boolean isLawAltarProduct(final int itemId)",
        "getLawItemMaxCharges(itemId) > 0",
        "contains(LAW_STAFFS, itemId)",
        "getAltarIdForWoolRobeItem(itemId) == LAW_ALTAR",
    )
    for snippet in required_effects:
        require(snippet in effects, f"EnchantingItemEffects should define law altar allowance: {snippet}")

    required_base_item_constants = (
        "ItemId.STAFF.id()",
        "ItemId.PINE_STAFF.id()",
        "ItemId.BLOOD_STAFF.id()",
        "ItemId.WOOL_WIZARD_HAT.id()",
        "ItemId.WOOL_ROBE_TOP.id()",
        "ItemId.WOOL_ROBE_SKIRT.id()",
        "ItemId.WOOL_GLOVES.id()",
        "ItemId.WOOL_BOOTS.id()",
    )
    for snippet in required_base_item_constants:
        require(snippet in effects, f"Law altar allowance should include base item: {snippet}")

    print("PASS: Entrana allows law altar enchanting and improvement items")


if __name__ == "__main__":
    main()
