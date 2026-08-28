#!/usr/bin/env python3
import json
import re
import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
SMELTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java"
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
CLIENT_DEFS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
CLIENT_ASSET_LOADER = ROOT / "Client_Base/src/orsc/ClientExternalAssetLoader.java"
CLIENT_DIRECTION_MAPPING = ROOT / "Client_Base/src/orsc/NpcDirectionalAnimationMapping.java"
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefsCustom.json"
SMELTING_DEFS = ROOT / "server/conf/server/defs/extras/ItemSmeltingDef.xml"
FOUNDRY_DRAGON_SHEET = (
    ROOT
    / "dev/myworld/assets/sprites/npcs/foundry-dragon/foundry-dragon-sprite-sheet.png"
)


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(source: str, text: str, message: str) -> None:
    if text not in source:
        fail(message)


def method_body(source: str, signature: str) -> str:
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 0
    for index in range(brace, len(source)):
        depth += (source[index] == "{") - (source[index] == "}")
        if depth == 0:
            return source[brace:index + 1]
    fail(f"unterminated method: {signature}")


def parse_int_matrix(source: str, declaration: str) -> list[list[int]]:
    start = source.index(declaration)
    brace = source.index("{", start)
    depth = 0
    for index in range(brace, len(source)):
        depth += (source[index] == "{") - (source[index] == "}")
        if depth == 0:
            block = source[brace + 1:index]
            return [
                [int(value.strip()) for value in row.split(",") if value.strip()]
                for row in re.findall(r"\{([^{}]*)\}", block)
            ]
    fail(f"unterminated matrix: {declaration}")


def main() -> int:
    summoning = SUMMONING.read_text(encoding="utf-8")
    smelting = SMELTING.read_text(encoding="utf-8")
    spell_handler = SPELL_HANDLER.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    client_defs = CLIENT_DEFS.read_text(encoding="utf-8")
    client_asset_loader = CLIENT_ASSET_LOADER.read_text(encoding="utf-8")
    client_direction_mapping = CLIENT_DIRECTION_MAPPING.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")
    smelting_defs = SMELTING_DEFS.read_text(encoding="utf-8")

    profile_match = re.search(
        r"FOUNDRY_DRAGON_PROFILE = supportProfile\((?P<body>.*?)\n\t\);",
        summoning,
        re.S,
    )
    if not profile_match:
        fail("Foundry Dragon must be a support summon")
    profile = profile_match.group("body")
    for expected in (
        '"Foundry Dragon", 61, 365',
        "NpcId.FOUNDRY_DRAGON.id()",
        "cost(ItemId.LIFE_RUNE.id(), 2)",
        "cost(ItemId.FIRE_RUNE.id(), 5)",
        "cost(ItemId.NATURE_RUNE.id(), 1)",
        "cost(ItemId.SOUL_RUNE.id(), 1)",
        "cost(ItemId.DRAGON_BONES.id(), 1)",
    ):
        require(profile, expected, f"Foundry Dragon profile missing {expected}")
    require(
        summoning,
        "CAMEL_PROFILE, FOUNDRY_DRAGON_PROFILE,\n\t\tOTHERWORLDLY_BEING_PROFILE",
        "Foundry Dragon must sit between levels 58 and 64 in the summon catalog",
    )
    active = method_body(summoning, "private static Npc getActiveFoundryDragon")
    for check in (
        "MANUAL_SUMMON_KEY",
        "!summon.isRemoved()",
        "isOwnedSummon(player, summon)",
        "SOURCE_MANUAL.equals",
        "KIND_FOUNDRY_DRAGON.equals",
    ):
        require(active, check, f"active Foundry Dragon lifecycle check missing {check}")

    npc_defs = json.loads(NPC_DEFS.read_text(encoding="utf-8"))["npcs"]
    foundry_npc = next((npc for npc in npc_defs if npc["id"] == 845), None)
    if foundry_npc is None:
        fail("server NPC 845 is missing")
    expected_npc = {
        "name": "Foundry Dragon",
        "sprites1": 165,
        "camera1": 271,
        "camera2": 196,
        "attackable": 0,
        "aggressive": 0,
    }
    for key, value in expected_npc.items():
        if foundry_npc.get(key) != value:
            fail(f"server Foundry Dragon {key} should be {value!r}")
    for text in (
        "setCustomNpcDefinition(845, new NPCDef(",
        '"Foundry Dragon", "A miniature black dragon radiating furnace heat"',
        "new int[]{165, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}",
        "0, 0, 0, 0, 271, 196, 10, 7, 30, 845",
    ):
        require(client_defs, text, f"client Foundry Dragon definition missing {text}")

    if not FOUNDRY_DRAGON_SHEET.is_file():
        fail("Foundry Dragon sprite sheet is missing")
    sheet_bytes = FOUNDRY_DRAGON_SHEET.read_bytes()
    if sheet_bytes[:8] != b"\x89PNG\r\n\x1a\n":
        fail("Foundry Dragon sprite sheet is not a PNG")
    width, height = struct.unpack(">II", sheet_bytes[16:24])
    if (width, height) != (1465, 489):
        fail(f"Foundry Dragon sheet must remain native 1465x489, got {width}x{height}")
    if b"tRNS" not in sheet_bytes:
        fail("Foundry Dragon sheet lost its indexed transparency")
    for text in (
        'animations.add(new AnimationDef("foundrydragon", "npc", 0, 0, true, false, 0));',
        "foundryDragon.sprites[0] = foundryDragonAnimationId;",
        "foundryDragon.camera1 = FOUNDRY_DRAGON_FRAME_WIDTH;",
        "foundryDragon.camera2 = FOUNDRY_DRAGON_FRAME_HEIGHT;",
    ):
        require(client_defs, text, f"Foundry Dragon external definition missing {text}")
    for text in (
        "loadExternalNpcDirectionSheet(",
		"int[] directionColumnWidths",
		"totalWidth != source.getWidth()",
		"sourceX += frameWidth",
		"direction * framesPerDirection + frameIndex",
    ):
        require(client_asset_loader, text, f"Foundry Dragon native sheet loader missing {text}")
    for text in (
        "DIRECTION_COLUMNS = 5",
        "COMBAT_COLUMN = 5",
        "direction == 5",
        "return 3;",
        "direction == 6",
        "return 2;",
        "direction == 7",
        "return 1;",
        "return direction >= 5;",
    ):
        require(client_direction_mapping, text, f"NPC direction mapping missing {text}")
    for text in (
        "getExternalFoundryDragonSpriteSheet()",
		"final int[] foundryDragonColumnWidths = {228, 203, 263, 215, 218, 338};",
        "EntityHandler.activateFoundryDragonExternalVisual();",
        'npcSprites.put("foundrydragon", spriteEntry);',
        '"foundrydragon".equalsIgnoreCase(s)',
    ):
        require(client, text, f"client Foundry Dragon registration missing {text}")

    ids = parse_int_matrix(client, "SUMMONING_COST_ITEM_IDS")
    amounts = parse_int_matrix(client, "SUMMONING_COST_AMOUNTS")
    if len(ids) != 15 or len(amounts) != 15:
        fail("client summon cost matrices must remain aligned to all 15 summons")
    if ids[12] != [37, 31, 40, 825, 814] or amounts[12] != [2, 5, 1, 1, 1]:
        fail("client Foundry Dragon cost row is incorrect")
    for text in (
        '"Foundry Dragon"',
        '"Replaces smelting coal with runes"',
        "58, 61, 64",
    ):
        require(client, text, f"client summon display missing {text}")
    for text in (
        'addSummonGuide(845, "61", "Foundry Dragon - Support; 2 life, 5 fire, nature, soul, dragon bones")',
        'addSummonGuide(845, "61", "Foundry Dragon - Does not engage in combat")',
        'addSummonGuide(845, "61", "Foundry - each coal costs 5 fire and 1 nature rune")',
    ):
        require(guide, text, f"Summoning guide missing {text}")

    # The shared furnace recipes cover modern UI, retro/direct ore use, and batch use.
    for coal in range(1, 7):
        require(smelting, f"ingredient(ItemId.COAL.id(), {coal})", f"missing furnace coal tier {coal}")
    for text in (
        "Summoning.isFoundryDragonActive(player)",
        "FoundryDragonSmeltingCost.fireRunesForCoal(ingredient.amount)",
        "FoundryDragonSmeltingCost.natureRunesForCoal(ingredient.amount)",
        "recipe.totalInputAmount(player)",
        "recipe.ingredientItemIds(player)",
        "recipe.ingredientAmounts(player)",
        "for (Ingredient ingredient : recipe.effectiveIngredients(player))",
    ):
        require(smelting, text, f"effect-aware furnace path missing {text}")
    consume = method_body(smelting, "private boolean consumeMaterials")
    require(consume, "remove(items, false)", "furnace costs must use one atomic item-vector removal")
    require(consume, "if (!player.getCarriedItems().remove", "failed atomic furnace removal must be checked")
    production = method_body(smelting, "private boolean makeSmeltingProduction")
    if production.index("if (!recipe.consumeMaterials(player))") > production.index("thinkbubble(new Item(recipe.barId))"):
        fail("furnace output must occur only after the complete cost is removed")
    furnace_trigger = "Summoning.triggerFoundryDragonSmeltingAnimation(player);"
    require(production, furnace_trigger, "successful furnace smelting must animate the Foundry Dragon")
    if production.index(furnace_trigger) < production.index("if (!recipe.consumeMaterials(player))"):
        fail("failed furnace material removal must not animate the Foundry Dragon")
    if production.index(furnace_trigger) > production.index("thinkbubble(new Item(recipe.barId))"):
        fail("Foundry Dragon furnace animation must begin with successful production")

    # ItemSmeltingDef feeds Superheat; all configured coal tiers must route through the same conversion.
    for coal in (1, 2, 4, 6):
        require(smelting_defs, f"<amount>{coal}</amount>", f"missing Superheat coal fixture {coal}")
    superheat = method_body(spell_handler, "private void superheatItem")
    for text in (
        "Summoning.isFoundryDragonActive(player)",
        "FoundryDragonSmeltingCost.fireRunesForCoal",
        "FoundryDragonSmeltingCost.natureRunesForCoal",
        "checkSpellRunes(player, spell, true)",
        "remove(completeCostItems, false)",
    ):
        require(superheat, text, f"atomic Superheat path missing {text}")
    if "checkAndRemoveRunes" in superheat:
        fail("Superheat must not consume spell runes separately from smelting materials")
    if superheat.index("remove(completeCostItems, false)") > superheat.index("getInventory().add(bar)"):
        fail("Superheat output must occur only after the complete cost is removed")
    require(superheat, furnace_trigger, "successful Superheat must animate the Foundry Dragon")
    if superheat.index(furnace_trigger) < superheat.index("remove(completeCostItems, false)"):
        fail("failed Superheat material removal must not animate the Foundry Dragon")

    trigger = method_body(summoning, "public static boolean triggerFoundryDragonSmeltingAnimation")
    for text in (
        "getActiveFoundryDragon(player)",
        "return false;",
        "summon.getSprite() & 7",
        "FOUNDRY_DRAGON_ANIMATION_TICKS_KEY",
        "summon.setSprite(8 + baseDirection)",
    ):
        require(trigger, text, f"Foundry Dragon animation trigger missing {text}")
    runtime = method_body(summoning, "private static void startSummonRuntime")
    require(runtime, "tickFoundryDragonSmeltingAnimation(summon)",
            "summon lifecycle must reset the Foundry Dragon animation")

    print("PASS: Foundry Dragon sprite mapping, animation trigger, and atomic smelting validated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
