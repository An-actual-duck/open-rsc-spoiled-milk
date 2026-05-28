#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
SMITHING = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "skills"
    / "smithing"
    / "Smithing.java"
)
CRAFTING = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "skills"
    / "crafting"
    / "Crafting.java"
)
FLETCHING = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "skills"
    / "fletching"
    / "Fletching.java"
)
ITEM_ARROW_HEAD_DEF = ROOT / "server" / "conf" / "server" / "defs" / "extras" / "ItemArrowHeadDef.xml"
ITEM_DART_TIP_DEF = ROOT / "server" / "conf" / "server" / "defs" / "extras" / "ItemDartTipDef.xml"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def main() -> None:
    smithing_text = SMITHING.read_text(encoding="utf-8")
    crafting_text = CRAFTING.read_text(encoding="utf-8")
    fletching_text = FLETCHING.read_text(encoding="utf-8")

    require(
        smithing_text,
        "int makeCount = Math.min(quantity, availableCount);",
        "Smithing production should clamp requested quantity to available bars",
    )
    require(
        smithing_text,
        "startbatch(makeCount);",
        "Smithing production should start the live batch with the clamped quantity",
    )
    require(
        smithing_text,
        "smithing.batchSmithing(player, bar, def);",
        "Smithing production should route into the existing smithing batch path",
    )
    if "PluginTask" in smithing_text or "PluginTickEvent" in smithing_text or '"Smithing.productionStart"' in smithing_text:
        fail("Smithing production should now run immediately instead of scheduling a delayed productionStart event")

    crafting_required_snippets = [
        "int makeCount = Math.min(quantity, availableBars);",
        "int makeCount = Math.min(quantity, Math.min(availableBars, availableGems));",
        "int makeCount = Math.min(quantity, available);",
        "int makeCount = Math.min(quantity, Math.min(availableMaterial, availableThreadUses));",
        "startbatch(makeCount);",
        "crafting.batchSilverJewelry(player, silverBar, results, type, reply);",
        "crafting.batchGoldJewelry(player, goldBar, def);",
        "crafting.batchWoolGarment(player, recipe);",
        "crafting.batchPotteryMoulding(player, softClay, recipe.reqLvl, result, msg, recipe.exp);",
        "crafting.batchGlassBlowing(player, glass, result, recipe.reqLvl, recipe.exp, recipe.resultGen);",
        "crafting.batchLeather(player, leather, result, piece.materialCost, piece.reqLvl, piece.exp);",
        "crafting.batchRangedMouldCasting(player, bar, recipe);",
        "ItemId.ARROWHEAD_MOULD.id()",
        "ItemId.DART_MOULD.id()",
        "ItemId.THROWING_KNIFE_MOULD.id()",
        "getDartTipsId(barId)",
    ]
    for snippet in crafting_required_snippets:
        require(
            crafting_text,
            snippet,
            f"Crafting production missing expected flow contract: {snippet}",
        )
    if "PluginTask" in crafting_text or "PluginTickEvent" in crafting_text or '"Crafting.productionStart"' in crafting_text:
        fail("Crafting production should now run immediately instead of scheduling a delayed productionStart event")

    smithing_removed_snippets = [
        "addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 18);",
        "addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 20);",
        "addModernWeaponOrMissileRecipe(recipes, player, item.getCatalogId(), 1);",
        "getModernArrowHeadsId",
        "getModernDartTipsId",
        "case 18:",
        "case 20:",
    ]
    for snippet in smithing_removed_snippets:
        if snippet in smithing_text:
            fail(f"Smithing should no longer offer ranged mould crafting output: {snippet}")

    require(
        fletching_text,
        "ItemId.TIN_DART_TIPS.id()",
        "Fletching should attach feathers to dart tips after tips are cast via moulds",
    )
    require(
        fletching_text,
        "ItemDartTipDef dartDef",
        "Fletching should use dart-tip definitions for feather attachment",
    )

    tourist_trap_text = (
        ROOT
        / "server"
        / "plugins"
        / "com"
        / "openrsc"
        / "server"
        / "plugins"
        / "authentic"
        / "quests"
        / "members"
        / "touristtrap"
        / "Tourist_Trap_Mechanism.java"
    ).read_text(encoding="utf-8")
    if "Skill.FLETCHING" in tourist_trap_text or "protoDartFletch" in tourist_trap_text:
        fail("Tourist Trap prototype dart completion should use Crafting, not Fletching")

    arrowhead_levels = [
        int(entry.find("ItemArrowHeadDef").findtext("requiredLvl"))
        for entry in ET.parse(ITEM_ARROW_HEAD_DEF).getroot().findall("entry")
    ]
    if arrowhead_levels != [1, 8, 15, 22, 30, 38, 46, 54, 62, 70]:
        fail(f"Arrowhead attachment levels must follow the 1-10 tier ladder, got {arrowhead_levels}")

    dart_tip_levels = [
        int(entry.find("ItemDartTipDef").findtext("requiredLvl"))
        for entry in ET.parse(ITEM_DART_TIP_DEF).getroot().findall("entry")
    ]
    if dart_tip_levels != [1, 8, 15, 22, 30, 38, 46, 54, 62, 70]:
        fail(f"Dart feathering levels must follow the 1-10 tier ladder, got {dart_tip_levels}")

    require(
        fletching_text,
        "int makeCount = Math.min(quantity, availableLogs);",
        "Fletching production should clamp requested quantity to available logs",
    )
    require(
        fletching_text,
        "startbatch(makeCount);",
        "Fletching production should start the live batch with the clamped quantity",
    )
    if "PluginTask" in fletching_text or "PluginTickEvent" in fletching_text or '"Crafting.logShapingStart"' in fletching_text:
        fail("Fletching production should now run immediately instead of scheduling a delayed logShapingStart event")
    if "TYPE_FLETCHING" in fletching_text or '"Fletching.productionStart"' in fletching_text:
        fail("Live log shaping should be integrated into Crafting, not Fletching")
    require(
        fletching_text,
        "fletching.batchLogCutting(player, log, recipe.resultId, recipe.requiredLevel, recipe.exp, recipe.message, recipe.craftingRecipe);",
        "Fletching production should route into the existing log-cutting batch path",
    )

    print("PASS: Production flow contracts validated")


if __name__ == "__main__":
    main()
