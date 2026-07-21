#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CRAFTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java"
SMELTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def forbid(text: str, snippet: str, label: str) -> None:
    if snippet in text:
        fail(f"{label} should not contain old snippet: {snippet}")


def extract_method(text: str, method_name: str) -> str:
    match = re.search(rf"\n\t(?:public|private|protected) .* {method_name}\([^)]*\) \{{", text)
    if not match:
        fail(f"Could not find method {method_name}")
    brace_start = text.index("{", match.start())
    depth = 0
    for index in range(brace_start, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[match.start():index + 1]
    fail(f"Could not parse method {method_name}")


def main() -> None:
    crafting = CRAFTING.read_text(encoding="utf-8")
    smelting = SMELTING.read_text(encoding="utf-8")

    open_category = extract_method(crafting, "openFurnaceCategory")
    require(open_category, "session = createRangedMouldCategoryProductionSession(player, categoryId);",
            "furnace mold category handoff")
    forbid(open_category, "createFurnaceMetalSelectionSession", "furnace mold category handoff")
    forbid(open_category, "beginFurnaceMetalSelectionFromInterface", "furnace mold category handoff")
    forbid(open_category, "furnace_metal_category", "furnace mold category handoff")

    category_session = extract_method(crafting, "createRangedMouldCategoryProductionSession")
    require(category_session, "for (int barId : MODERN_CASTING_BARS)", "tiered mold recipe list")
    require(category_session, "RangedMouldRecipe recipe = getRangedMouldRecipeForCategory(barId, categoryId);",
            "tiered mold recipe lookup")
    require(category_session, "new ProductionRecipe(recipe.resultId, recipe.reqLvl, 1, recipe.amount",
            "tiered mold output recipe")
    require(category_session, "new int[]{barId}, new int[]{-1}, new int[]{1}",
            "tiered mold consumable ingredient details")
    forbid(category_session, "new int[]{barId, recipe.mouldId}",
           "reusable mould listed as a consumable ingredient")
    require(category_session,
            "new ProductionSession(ProductionSession.TYPE_CRAFTING, getFurnaceCategoryProductionTitle(categoryId), categoryId, recipes)",
            "tiered mold final production session")
    forbid(crafting, "TYPE_FURNACE_MATERIAL, \"Choose a metal to cast\"", "retired mold metal picker")
    forbid(crafting, "public static boolean beginFurnaceMetalSelectionFromInterface", "retired mold metal picker starter")

    begin_production = extract_method(crafting, "beginProductionFromInterface")
    require(begin_production, "if (crafting.isFurnaceMetalCategory(inputId))", "category final start handling")
    require(begin_production, "ProductionRecipe productionRecipe = session.getRecipeByItemId(itemId);",
            "category final recipe validation")
    require(begin_production, "int barId = ingredientIds.length > 0 ? ingredientIds[0] : -1;",
            "category final bar lookup")
    require(begin_production, "return crafting.beginRangedMouldProduction(player, barId, recipe, quantity);",
            "category final production start")

    for category in (
        "FURNACE_CATEGORY_BOLTS",
        "FURNACE_CATEGORY_ARROWHEADS",
        "FURNACE_CATEGORY_DARTS",
        "FURNACE_CATEGORY_THROWING_KNIVES",
        "FURNACE_CATEGORY_SHURIKEN",
    ):
        require(smelting, category, f"furnace top-level category {category}")
        require(crafting, f"Smelting.{category}", f"crafting category support {category}")

    print("PASS: furnace mold flow uses two production screens")


if __name__ == "__main__":
    main()
