#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CRAFTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java"
TANNING = ROOT / "server/plugins/com/openrsc/server/plugins/custom/skills/crafting/TanningRack.java"
RUNECRAFT = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/runecraft/Runecraft.java"
SMITHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smithing.java"
FLETCHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/fletching/Fletching.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def main() -> None:
    crafting_text = CRAFTING.read_text(encoding="utf-8")
    tanning_text = TANNING.read_text(encoding="utf-8")
    runecraft_text = RUNECRAFT.read_text(encoding="utf-8")
    smithing_text = SMITHING.read_text(encoding="utf-8")
    fletching_text = FLETCHING.read_text(encoding="utf-8")

    require(
        tanning_text,
        'player.playerServerMessage(MessageType.QUEST, "You stretch the material across the tanning rack.");',
        "Tanning rack should still announce the tanning action",
    )
    require(
        tanning_text,
        'player.playerServerMessage(MessageType.QUEST, "You finish preparing " + completed + " piece" + (completed == 1 ? "" : "s") + " into usable material.");',
        "Tanning rack should bulk-convert the selected hide stack",
    )
    if "batchTan(" in tanning_text or "startbatch(" in tanning_text or "delay(" in tanning_text:
        fail("Tanning rack should no longer use per-item batch recursion or delays")

    for snippet in (
        "while (!ifinterrupted() && !isbatchcomplete()) {",
        "consumeThreadUse(player)",
        "crafting.batchLeather(player, leather, result, piece.materialCost, piece.reqLvl, piece.exp);",
        "crafting.batchGoldJewelry(player, goldBar, def);",
        "crafting.batchSilverJewelry(player, silverBar, results, type, reply);",
        "crafting.batchPotteryMoulding(player, softClay, recipe.reqLvl, result, msg, recipe.exp);",
        "crafting.batchGlassBlowing(player, glass, result, recipe.reqLvl, recipe.exp, recipe.resultGen);",
        "crafting.batchRangedMouldCasting(player, bar, recipe);",
        "crafting.batchWoolGarment(player, recipe);",
    ):
        require(crafting_text, snippet, f"Crafting instant processing missing snippet: {snippet}")

    for forbidden in (
        "PluginTask",
        "PluginTickEvent",
        '"Crafting.productionStart"',
        "delay(2);\n\t\t\tbatchLeather",
        "delay();\n\t\t\tbatchGoldJewelry",
    ):
        if forbidden in crafting_text:
            fail(f"Crafting should not rely on delayed per-item processing anymore: {forbidden}")

    require(
        runecraft_text,
        "for (int loop = 0; loop < repeatTimes; ++loop)",
        "Runecrafting altar should still process the full rune stone inventory in one use",
    )
    require(
        runecraft_text,
        "player.incExp(Skill.RUNECRAFT.id(), def.getExp() * successCount, true);",
        "Runecrafting altar XP should still be granted for the full processed stack",
    )
    require(
        runecraft_text,
        "return getAltarDef(player, obj) != null && hasRuneStone(player);",
        "Runecrafting altar clicks should be intercepted when the player has stone",
    )
    require(
        runecraft_text,
        "craftRunesAtAltar(player, obj);",
        "Runecrafting direct altar clicks and item-on-altar use should share the same crafting logic",
    )

    for snippet in (
        "smithing.batchSmithing(player, bar, def);",
        "while (!ifinterrupted() && !isbatchcomplete()) {",
        "batchGoldSmithing(player);",
    ):
        require(smithing_text, snippet, f"Smithing instant processing missing snippet: {snippet}")
    if "PluginTask" in smithing_text or "PluginTickEvent" in smithing_text or '"Smithing.productionStart"' in smithing_text:
        fail("Smithing should not rely on delayed productionStart scheduling anymore")

    for snippet in (
        "fletching.batchLogCutting(player, log, recipe.resultId, recipe.requiredLevel, recipe.exp, recipe.message, recipe.craftingRecipe);",
        "batchFeathers(player, feathers, attachment, resultID, experience);",
        "batchArrowheads(player, headlessArrows, arrowHeads, headDef);",
        "batchStringing(player, bow, bowString, stringDef);",
        "batchPearlCutting(player, pearl, amount);",
        "batchBolts(player, bolts, tips);",
        "while (!ifinterrupted() && !isbatchcomplete()) {",
    ):
        require(fletching_text, snippet, f"Fletching instant processing missing snippet: {snippet}")
    if "PluginTask" in fletching_text or "PluginTickEvent" in fletching_text or '"Crafting.logShapingStart"' in fletching_text:
        fail("Fletching should not rely on delayed log shaping scheduling anymore")

    print("PASS: instant processing paths look correct")


if __name__ == "__main__":
    main()
