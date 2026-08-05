#!/usr/bin/env python3
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
PRODUCTION_SESSION = (
    ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "production" / "ProductionSession.java"
)
ACTION = (
    ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "states" / "Action.java"
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
SMELTING = (
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
    / "Smelting.java"
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
INTERFACE_OPTION_HANDLER = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "net"
    / "rsc"
    / "handlers"
    / "InterfaceOptionHandler.java"
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
DO_SKILL_INTERFACE = (
    ROOT
    / "Client_Base"
    / "src"
    / "com"
    / "openrsc"
    / "interfaces"
    / "misc"
    / "DoSkillInterface.java"
)


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, message: str) -> None:
    if snippet not in text:
        fail(message)


def main() -> None:
    session_text = PRODUCTION_SESSION.read_text(encoding="utf-8")
    action_text = ACTION.read_text(encoding="utf-8")
    smithing_text = SMITHING.read_text(encoding="utf-8")
    smelting_text = SMELTING.read_text(encoding="utf-8")
    crafting_text = CRAFTING.read_text(encoding="utf-8")
    fletching_text = FLETCHING.read_text(encoding="utf-8")
    do_skill_interface_text = DO_SKILL_INTERFACE.read_text(encoding="utf-8")
    interface_handler_text = INTERFACE_OPTION_HANDLER.read_text(encoding="utf-8")

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
        smithing_text,
        "new int[]{barId}, new int[]{-1}, new int[]{def.getRequiredBars()}",
        "Smithing production recipes should expose bar cost details for icon-based material display",
    )
    require(
        smithing_text,
        "new int[]{barId, ItemId.HAMMER.id()}, new int[]{-1, -1}, new int[]{1, 1}",
        "Smithing metal picker recipes should expose bar and hammer icon cost details",
    )
    require(
        smithing_text,
        "stopbatch();\n\t\t\t\tbreak;",
        "Smithing production should close the batch window when resources run out mid-batch",
    )
    require(
        smelting_text,
        "if (!recipe.hasMaterials(player)) {\n\t\t\t\tstopbatch();\n\t\t\t\tbreak;",
        "Smelting production should close the batch window when resources run out mid-batch",
    )
    require(
        smelting_text,
        'player.message("You have no steel bars left");\n\t\t\tstopbatch();',
        "Cannonball production should close the batch window when steel runs out",
    )
    require(
        do_skill_interface_text,
        "boolean showQuantityControls = !isPickerInterface();",
        "Production picker pages should hide unused quantity controls",
    )
    require(
        crafting_text,
        "private static final int THREAD_USES_PER_LEATHER_MATERIAL = 2;",
        "Leather armor crafting should cost two thread uses per material",
    )
    require(
        crafting_text,
        "int threadCost = getLeatherThreadCost(piece.materialCost);",
        "Leather armor production recipes should derive thread cost from material cost",
    )
    require(
        crafting_text,
        "materialCount >= piece.materialCost && threadUses >= threadCost",
        "Leather armor production recipes should reflect scaled thread requirements",
    )
    require(
        crafting_text,
        "new int[]{-1, -1}, new int[]{piece.materialCost, threadCost}",
        "Leather armor production recipes should expose scaled thread icon costs",
    )
    require(
        crafting_text,
        "int availableByThread = availableThreadUses / threadCost;",
        "Leather armor production batches should be capped by scaled thread cost",
    )
    require(
        crafting_text,
        "consumeThreadUses(player, threadCost);",
        "Leather armor crafting should consume the scaled thread cost",
    )
    require(
        crafting_text,
        "player.getConfig().WANT_FATIGUE",
        "Crafting interface production should use player config outside plugin callback context",
    )
    require(
        smelting_text,
        "player.getConfig().WANT_FATIGUE",
        "Smelting interface production should use player config outside plugin callback context",
    )
    require(
        smithing_text,
        "player.getConfig().WANT_FATIGUE",
        "Smithing interface production should use player config outside plugin callback context",
    )
    require(
        smelting_text,
        "private static final int SMELTING_ACTION_DELAY_TICKS = 3;",
        "Smelting should expose its per-action delay instead of using an inline magic number",
    )
    require(
        smelting_text,
        "ActionSender.sendActionProgressBar(player, recipe.progressItemId(player), SMELTING_ACTION_DELAY_TICKS);",
        "Smelting should show the same per-action progress cadence as gathering skills",
    )
    require(
        smelting_text,
        "if (!ifinterrupted() && !isbatchcomplete()) {\n\t\t\t\tdelay();\n\t\t\t}",
        "Smelting should pause between repeated production actions like fishing",
    )
    require(
        smithing_text,
        "private static final int SMITHING_ACTION_DELAY_TICKS = 3;",
        "Smithing should expose its per-action delay instead of using an inline magic number",
    )
    require(
        smithing_text,
        "ActionSender.sendActionProgressBar(player, ItemId.HAMMER.id(), SMITHING_ACTION_DELAY_TICKS);",
        "Smithing should show hammer progress for each production action",
    )
    require(
        smithing_text,
        "if (!ifinterrupted() && !isbatchcomplete()) {\n\t\t\t\tdelay();\n\t\t\t}",
        "Smithing should pause between repeated production actions like fishing",
    )
    for label, text in (
        ("crafting", crafting_text),
        ("smelting", smelting_text),
        ("smithing", smithing_text),
    ):
        if "config()" in text:
            fail(f"{label} production-heavy plugin should not depend on plugin-thread config()")
    require(
        interface_handler_text,
        "catch (RuntimeException e)",
        "Production starts should fail gracefully instead of bubbling packet handler exceptions",
    )
    require(
        action_text,
        'production(EntityType.NONE, "production"),',
        "Production interface starts should have a dedicated plugin action context",
    )
    require(
        interface_handler_text,
        'private static final String PRODUCTION_PLUGIN_NAME = "ProductionInterface.start";',
        "Production interface starts should use one shared plugin event name",
    )
    require(
        interface_handler_text,
        'final PluginTask task = new PluginTask(player.getWorld(), player, "production", new Object[0])',
        "Production starts should run through a ticked PluginTask so delay() works",
    )
    require(
        interface_handler_text,
        'if (activeSession != session || activeStarter != starter) {',
        "Production starts should abort stale interface submissions",
    )
    require(
        interface_handler_text,
        "started = starter.start(player, session, itemId, quantity);",
        "Production tasks should call the existing production starter inside the plugin context",
    )
    require(
        interface_handler_text,
        "catch (PluginInterruptedException e)",
        "Production tasks should treat normal plugin interruption as a clean stop",
    )
    require(
        interface_handler_text,
        "new PluginTickEvent(player.getWorld(), player, PRODUCTION_PLUGIN_NAME, null, task);",
        "Production starts should be scheduled on the player event loop",
    )
    require(
        interface_handler_text,
        "player.getWorld().getServer().getGameEventHandler().add(event)",
        "Production start events should be registered with the game event handler",
    )
    require(
        crafting_text,
        "barCount >= 1 && hasMould && hasGem",
        "Gold jewelry production recipes should reflect bar, mould, and gem requirements",
    )
    require(
        crafting_text,
        "goldJewelryProductionRecipe(outputId, def, mouldId",
        "Gold jewelry production recipes should route through ingredient-aware recipe construction",
    )
    require(
        crafting_text,
        "addProductionIngredient(ingredientIds, fallbackIds, amounts, ItemId.GOLD_BAR.id(), 1);",
        "Gold jewelry production recipes should expose gold bar icon cost details",
    )
    require(
        crafting_text,
        "addProductionIngredient(ingredientIds, fallbackIds, amounts, mouldId, 1);",
        "Gold jewelry production recipes should expose mould icon cost details",
    )
    require(
        crafting_text,
        "addProductionIngredient(ingredientIds, fallbackIds, amounts, def.getReqGem(), 1);",
        "Gold jewelry production recipes should expose gem icon cost details",
    )
    require(
        crafting_text,
        "barCount >= 1 && hasMould",
        "Silver jewelry production recipes should reflect bar and mould requirements",
    )
    require(
        crafting_text,
        "new int[]{ItemId.SILVER_BAR.id(), silver_moulds[type]}",
        "Silver jewelry production recipes should expose silver bar and mould icon costs",
    )
    for snippet, label in (
        ("new int[]{leather.getCatalogId(), ItemId.THREAD.id()}", "leather crafting"),
        ("new int[]{ItemId.COW_HIDE.id(), ItemId.THREAD.id()}", "brown apron crafting"),
        ("new int[]{ItemId.BALL_OF_WOOL.id()}", "wool crafting"),
        ("new int[]{ItemId.SOFT_CLAY.id()}", "pottery crafting"),
        ("new int[]{ItemId.MOLTEN_GLASS.id()}", "glassblowing crafting"),
        ("new int[]{barId, recipe.mouldId}", "ranged mould casting"),
        ("new int[]{barId}, new int[]{-1}, new int[]{1}", "furnace metal selection"),
    ):
        require(
            crafting_text,
            snippet,
            f"{label} production recipes should expose ingredient icon costs",
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
    require(
        fletching_text,
        "new int[]{log.getCatalogId()}, new int[]{-1}, new int[]{1}",
        "Fletching production recipes should expose log icon cost details",
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
