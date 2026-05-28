#!/usr/bin/env python3
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
PRODUCTION_SESSION = (
    ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "production" / "ProductionSession.java"
)
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


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def main() -> None:
    session_text = PRODUCTION_SESSION.read_text(encoding="utf-8")
    smithing_text = SMITHING.read_text(encoding="utf-8")
    crafting_text = CRAFTING.read_text(encoding="utf-8")
    fletching_text = FLETCHING.read_text(encoding="utf-8")

    require(
        session_text,
        "if (recipe.isLevelMet() && recipe.isMaterialsMet()) {",
        "ProductionSession should prefer fully craftable recipes as the default selection",
    )
    require(
        session_text,
        "if (recipe.isLevelMet()) {",
        "ProductionSession should fall back to level-met recipes when materials are missing",
    )
    require(
        session_text,
        "return recipes.isEmpty() ? -1 : recipes.get(0).getItemId();",
        "ProductionSession should fall back to the first recipe only as a final default",
    )
    require(
        smithing_text,
        'player.message("You are not skilled enough for that yet");',
        "Smithing should refuse to open the production window when nothing is craftable",
    )
    require(
        smithing_text,
        "level >= def.getRequiredLevel(), materialCount >= def.getRequiredBars()",
        "Smithing production recipes should reflect live level and bar requirements in disabled states",
    )
    require(
        crafting_text,
        "materialCount >= piece.materialCost && threadUses >= 1",
        "Leather crafting production recipes should reflect both material and thread requirements",
    )
    require(
        crafting_text,
        "barCount >= 1 && hasMould && hasGem",
        "Gold jewelry production recipes should reflect bar, mould, and gem requirements",
    )
    require(
        crafting_text,
        "barCount >= 1 && hasMould",
        "Silver jewelry production recipes should reflect bar and mould requirements",
    )
    require(
        fletching_text,
        "int outputAmount = recipe.resultId == ItemId.ARROW_SHAFTS.id() ? getNumberOfShafts(player, log.getCatalogId()) : 1;",
        "Fletching production should preserve dynamic arrow-shaft output amounts",
    )
    require(
        fletching_text,
        "level >= recipe.requiredLevel, materialCount >= 1",
        "Fletching production recipes should reflect current level and log availability",
    )
    for label, text in (
        ("smithing", smithing_text),
        ("crafting", crafting_text),
        ("fletching", fletching_text),
    ):
        require(
            text,
            "if (!session.hasAnyCraftableRecipe()) {",
            f"{label.capitalize()} should gate the production window on at least one level-met recipe",
        )
        require(
            text,
            'player.setAttribute("production_session", session);',
            f"{label.capitalize()} should keep the active production session on the player",
        )
        require(
            text,
            "ActionSender.showProductionInterface(player, session);",
            f"{label.capitalize()} should route through the shared production window",
        )

    print("PASS: Production behavior invariants validated")


if __name__ == "__main__":
    main()
