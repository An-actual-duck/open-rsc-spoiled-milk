#!/usr/bin/env python3
"""Guard Foundry Dragon robe/staff fuel preservation and full-cost gates."""

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRESERVATION = ROOT / "server/src/com/openrsc/server/content/RuneCostPreservation.java"
FOUNDRY_COST = ROOT / "server/src/com/openrsc/server/content/FoundryDragonSmeltingCost.java"
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
SMELTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java"
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def require(source: str, text: str, message: str) -> None:
    if text not in source:
        fail(f"{message}: {text}")


def between(source: str, start: str, end: str) -> str:
    try:
        return source[source.index(start):source.index(end, source.index(start) + len(start))]
    except ValueError:
        fail(f"Could not isolate {start}")
        return ""


def main() -> None:
    preservation = PRESERVATION.read_text(encoding="utf-8")
    foundry = FOUNDRY_COST.read_text(encoding="utf-8")
    summoning = SUMMONING.read_text(encoding="utf-8")
    smelting = SMELTING.read_text(encoding="utf-8")
    spell_handler = SPELL_HANDLER.read_text(encoding="utf-8")

    for text in (
        "getWoolRobeRunePreservationChance(runeId)",
        "EnchantingItemEffects.getStaffRunePreservationChance",
        "return Math.min(1.0D, chance);",
        "DataConversions.getRandom().nextDouble() < chance",
    ):
        require(preservation, text, "shared spell/Foundry preservation contract missing")
    require(summoning, "return RuneCostPreservation.shouldPreserve(owner, runeId);",
            "summoning must share the preservation roll")
    require(spell_handler, "return RuneCostPreservation.getChance(player, runeId);",
            "spell preservation must share the Foundry chance calculation")

    for text in (
        "ItemId.FIRE_RUNE.id()",
        "ItemId.NATURE_RUNE.id()",
        "Foundry Dragon only preserves Fire and Nature runes",
        "return RuneCostPreservation.shouldPreserve(player, runeId) ? 0 : amount;",
    ):
        require(foundry, text, "Foundry fuel preservation boundary missing")

    has_materials = between(smelting, "private boolean hasMaterials", "private int[] ingredientItemIds")
    consume = between(smelting, "private boolean consumeMaterials", "private String requirementMessage")
    require(has_materials, "resolvedCosts(player)",
            "furnace availability must require full pre-mitigation fuel")
    require(consume, "resolvedCosts(player)",
            "furnace consumption must recheck full pre-mitigation fuel")
    require(consume, "applyFoundryDragonRunePreservation(player, costs)",
            "furnace preservation must occur only after the full-cost check")

    superheat = between(spell_handler, "private void superheatItem", "private static void addSuperheatCost")
    require(superheat, "final Map<Integer, Integer> foundryFuelCost = new LinkedHashMap<>();",
            "Superheat must track Foundry fuel independently from spell runes")
    require(superheat, "hasSuperheatCosts(player, completeCost)",
            "Superheat must verify full combined costs before mitigation")
    require(superheat, "applyFoundryDragonSuperheatRunePreservation(player, completeCost, foundryFuelCost);",
            "Superheat must preserve only the Foundry fuel portion")
    if superheat.index("hasSuperheatCosts(player, completeCost)") > superheat.index(
            "applyFoundryDragonSuperheatRunePreservation(player, completeCost, foundryFuelCost);"):
        fail("Superheat preservation ran before full combined-cost availability")

    preserve = between(spell_handler, "private static void applyFoundryDragonSuperheatRunePreservation", "private static void addSuperheatCost")
    require(preserve, "completeCost.get(fuel.getKey()) - fuel.getValue()",
            "Superheat must retain overlapping spell-rune costs when Foundry fuel is waived")

    print("PASS: Foundry Dragon fuel uses shared Fire/Nature preservation with full-cost gates")


if __name__ == "__main__":
    main()
