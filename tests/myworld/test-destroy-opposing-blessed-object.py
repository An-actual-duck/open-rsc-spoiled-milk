#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "myworld" / "skills" / "prayer" / "DestroyOpposingBlessedObject.java"
DEVOTION = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "Devotion.java"
PLAN = ROOT / "docs" / "myworld" / "in-progress-work-plans" / "prayer-devotion-equipment-plan.md"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    plugin = PLUGIN.read_text(encoding="utf-8")
    devotion = DEVOTION.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    require("class DestroyOpposingBlessedObject implements UseLocTrigger" in plugin,
            "opposing blessed object destruction plugin should exist")
    require("itemGod != altarGod" in plugin,
            "only opposing blessed objects should be intercepted")
    require("altarGod != worshippedGod" in plugin,
            "player should have to worship the altar god")
    require("DEVOTION_CHANGE_PER_RESOURCE = 1" in plugin,
            "devotion reward/penalty should be resource-cost based")
    require("PRAYER_XP_MULTIPLIER = 5" in plugin,
            "destroying opposing gear should grant large Prayer XP")
    require("Devotion.adjustDevotionOfferings(player, worshippedGod, devotionOfferingChange)" in plugin,
            "destroying opposing gear should reward current god devotion")
    require("Devotion.adjustDevotionOfferings(player, itemGod, -devotionOfferingChange)" in plugin,
            "destroying opposing gear should reduce destroyed item god devotion")
    require("formatDevotionOfferingChange(actualGain)" in plugin,
            "destroying opposing gear should report actual clamped devotion gained")
    require("formatDevotionOfferingChange(actualLoss)" in plugin,
            "destroying opposing gear should report actual clamped devotion lost")
    require("player.getCarriedItems().getInventory().remove(item, true)" in plugin,
            "destroying opposing gear should consume the exact selected inventory object")

    for marker in [
        "isZamorakKnightEquipment",
        "isSaradominKnightEquipment",
        "isGuthixKnightEquipment",
        "isZamorakBlessedWool",
        "isSaradominBlessedWool",
        "isGuthixBlessedWool",
        "isZamorakBlessedStaff",
        "isSaradominBlessedStaff",
        "isGuthixBlessedStaff",
    ]:
        require(marker in plugin, f"missing destruction support for {marker}")

    require("removeDevotionLevels" in devotion and "adjustDevotionLevels" in devotion
            and "removeDevotionOfferings" in devotion and "adjustDevotionOfferings" in devotion,
            "devotion should support clamped negative adjustments")
    require("clampOfferings((long) previousOfferings + ((long) devotionLevels * OFFERINGS_PER_DEVOTION_LEVEL))" in devotion,
            "devotion adjustments should clamp between negative and positive caps without overflowing")
    require("SYMBOL_DEVOTION_OFFERING_CHANGE = 2" in plugin,
            "symbol destruction should transfer exactly two offering units")
    require("DEVOTION_OFFERINGS_PER_RESOURCE = Devotion.OFFERINGS_PER_DEVOTION_LEVEL * DEVOTION_CHANGE_PER_RESOURCE" in plugin,
            "resource-cost destruction should still translate full devotion levels to offering points")
    for item_id in (432, 2161, 3123, 3229, 3230, 3231, 3232, 3233, 3234):
        require(f"case {item_id}:" in plugin, f"missing destruction coverage for god item {item_id}")
    for artifact_id in (1213, 1214, 1215, 1216, 1217, 1218, 3252, 3253, 3254):
        require(f"case {artifact_id}:" not in plugin, f"god artifact {artifact_id} must remain indestructible")
    require("getBlessedStaffTier(itemId)" in plugin
            and "return getBlessedObjectDevotionResourceCost(itemId) * DEVOTION_OFFERINGS_PER_RESOURCE;" in plugin,
            "tiered blessed staves should transfer devotion according to tier")
    require("player.getPrayers().deactivateOverflowingPrayers();" in devotion,
            "Devotion reductions should deactivate prayers above the new allocation")
    require("`5x`" in plan and "`1` devotion per equivalent resource cost" in plan,
            "destroy opposing blessed object formula should be documented")

    print("PASS: opposing blessed object destruction is wired")


if __name__ == "__main__":
    main()
