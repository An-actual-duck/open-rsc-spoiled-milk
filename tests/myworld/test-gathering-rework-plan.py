#!/usr/bin/env python3
"""Validate MyWorld gathering rework planning guardrails."""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MINING_DEFS = ROOT / "server/conf/server/defs/extras/ObjectMining.xml"
MINING_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/mining/Mining.java"
WOODCUTTING_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/woodcutting/Woodcutting.java"
HARVESTING_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/custom/skills/harvesting/Harvesting.java"
SHEEP_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/Sheep.java"
SMITHING_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smithing.java"
FORMULAE = ROOT / "server/src/com/openrsc/server/util/rsc/Formulae.java"
ENTITY_HANDLER = ROOT / "server/src/com/openrsc/server/external/EntityHandler.java"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
GAME_STATE_UPDATER = ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
ITEM_IDS = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
ITEM_DEFS_CUSTOM = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
ITEM_DEFS_MYWORLD = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"
GATHERING_PLAN = ROOT / "docs/myworld/work-items.md"

MAINLINE_ORE_LEVELS = {
    202: 1,   # tin
    150: 8,   # copper
    151: 15,  # iron
    155: 22,  # coal
    153: 38,  # mithril
    154: 54,  # adamantite
    409: 70,  # runite
}

PRESERVED_ORE_LEVELS = {
    149: 1,   # clay
    266: 10,  # bluerite
    383: 20,  # silver
    152: 40,  # gold
    690: 40,  # quest gold
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_mining_levels_by_ore() -> dict[int, set[int]]:
    tree = ET.parse(MINING_DEFS)
    root = tree.getroot()
    result: dict[int, set[int]] = {}
    for entry in root.findall("entry"):
        def_node = entry.find("ObjectMiningDef")
        if def_node is None:
            continue
        ore_id = int(def_node.findtext("oreId", default="-1"))
        required_level = int(def_node.findtext("requiredLvl", default="-1"))
        result.setdefault(ore_id, set()).add(required_level)
    return result


def require_ore_levels(expected: dict[int, int], actual: dict[int, set[int]]) -> None:
    for ore_id, expected_level in expected.items():
        levels = actual.get(ore_id)
        if levels is None:
            fail(f"Missing mining definition for ore id {ore_id}")
        if levels != {expected_level}:
            fail(f"Ore id {ore_id} expected level {expected_level}, found {sorted(levels)}")


def require_plan_text() -> None:
    text = GATHERING_PLAN.read_text(encoding="utf-8")
    snippets = (
        "## Per-Resource Yield Ladder",
        "The yield ladder repeats every 10 effective levels from the resource's unlock",
        "unlock+10 to unlock+19",
        "tin `1`, copper `8`,\n  iron `15`, coal `22`, mithril `38`, adamantite `54`, and runite `70`",
        "Stone rocks should unlock at level `1`.",
        "Yield beyond free inventory space should drop on the ground, not be lost.",
        "`Just the ore`, `A few gems`, `Plenty of gems`, and `Lots of gems` behavior.",
    )
    for snippet in snippets:
        if snippet not in text:
            fail(f"Gathering plan missing expected snippet: {snippet}")


def require_stone_mining_baseline() -> None:
    text = MINING_PLUGIN.read_text(encoding="utf-8")
    snippets = (
        "isTemporaryDepletedOreRock(rock)",
        "handleDepletedOreRock(rock, player, click);",
        "int permanentId = rock.getLoc().getPermId();",
        "permanentId > 0",
        "rock.getID() == SceneryId.ROCK_GENERIC.id() || rock.getID() == SceneryId.ROCK_GENERIC2.id()",
        "handleStoneMining(rock, player, click);",
        "int reqlvl = getPickaxeRequiredLevel(axeId);",
        "This rock contains stone",
        "There is currently no ore available in this rock",
    )
    for snippet in snippets:
        if snippet not in text:
            fail(f"Stone mining baseline missing expected snippet: {snippet}")


def require_gathering_yield_helpers() -> None:
    text = FORMULAE.read_text(encoding="utf-8")
    snippets = (
        "calcGatheringEffectiveLevel",
        "skillLevel + Math.max(0, toolTier - 1) * 5",
        "calcGatheringYieldMin",
        "calcGatheringYieldMax",
        "calcGatheringYield",
        "DataConversions.random(1, 10) <= progress + 1",
        "public static final int[] harvestingShearsIDs",
        "public static final int[] harvestingShearsLvls = {70, 62, 54, 46, 38, 30, 22, 15, 8, 1};",
    )
    for snippet in snippets:
        if snippet not in text:
            fail(f"Gathering yield helper missing expected snippet: {snippet}")


def require_tool_equip_gates() -> None:
    entity_text = ENTITY_HANDLER.read_text(encoding="utf-8")
    client_entity_text = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    formulae_text = FORMULAE.read_text(encoding="utf-8")
    mining_text = MINING_PLUGIN.read_text(encoding="utf-8")

    entity_snippets = (
        "configureEquippableTool(ItemId.TIN_PICKAXE.id(), Skill.MINING.id(), 1);",
        "configureEquippableTool(ItemId.COPPER_PICKAXE.id(), Skill.MINING.id(), 8);",
        "configureEquippableTool(ItemId.BRONZE_PICKAXE.id(), Skill.MINING.id(), 15);",
        "configureEquippableTool(ItemId.IRON_PICKAXE.id(), Skill.MINING.id(), 22);",
        "configureEquippableTool(ItemId.STEEL_PICKAXE.id(), Skill.MINING.id(), 30);",
        "configureEquippableTool(ItemId.MITHRIL_PICKAXE.id(), Skill.MINING.id(), 38);",
        "configureEquippableTool(ItemId.TITAN_STEEL_PICKAXE.id(), Skill.MINING.id(), 46);",
        "configureEquippableTool(ItemId.ADAMANTITE_PICKAXE.id(), Skill.MINING.id(), 54);",
        "configureEquippableTool(ItemId.ORICHALCUM_PICKAXE.id(), Skill.MINING.id(), 62);",
        "configureEquippableTool(ItemId.RUNE_PICKAXE.id(), Skill.MINING.id(), 70);",
        "configureEquippableTool(ItemId.TIN_AXE.id(), Skill.WOODCUTTING.id(), 1);",
        "configureEquippableTool(ItemId.COPPER_AXE.id(), Skill.WOODCUTTING.id(), 8);",
        "configureEquippableTool(ItemId.BRONZE_AXE.id(), Skill.WOODCUTTING.id(), 15);",
        "configureEquippableTool(ItemId.IRON_AXE.id(), Skill.WOODCUTTING.id(), 22);",
        "configureEquippableTool(ItemId.STEEL_AXE.id(), Skill.WOODCUTTING.id(), 30);",
        "configureEquippableTool(ItemId.MITHRIL_AXE.id(), Skill.WOODCUTTING.id(), 38);",
        "configureEquippableTool(ItemId.TITAN_STEEL_AXE.id(), Skill.WOODCUTTING.id(), 46);",
        "configureEquippableTool(ItemId.ADAMANTITE_AXE.id(), Skill.WOODCUTTING.id(), 54);",
        "configureEquippableTool(ItemId.ORICHALCUM_AXE.id(), Skill.WOODCUTTING.id(), 62);",
        "configureEquippableTool(ItemId.RUNE_AXE.id(), Skill.WOODCUTTING.id(), 70);",
        "configureEquippableTool(ItemId.DRAGON_AXE.id(), Skill.WOODCUTTING.id(), 80);",
        "configureEquippableTool(ItemId.DRAGON_WOODCUTTING_AXE.id(), Skill.WOODCUTTING.id(), 80);",
        "configureEquippableTool(ItemId.SHEARS.id(), Skill.HARVESTING.id(), 1);",
        "configureEquippableTool(ItemId.COPPER_SHEARS.id(), Skill.HARVESTING.id(), 8);",
        "configureEquippableTool(ItemId.BRONZE_SHEARS.id(), Skill.HARVESTING.id(), 15);",
        "configureEquippableTool(ItemId.IRON_SHEARS.id(), Skill.HARVESTING.id(), 22);",
        "configureEquippableTool(ItemId.STEEL_SHEARS.id(), Skill.HARVESTING.id(), 30);",
        "configureEquippableTool(ItemId.MITHRIL_SHEARS.id(), Skill.HARVESTING.id(), 38);",
        "configureEquippableTool(ItemId.TITAN_STEEL_SHEARS.id(), Skill.HARVESTING.id(), 46);",
        "configureEquippableTool(ItemId.ADAMANTITE_SHEARS.id(), Skill.HARVESTING.id(), 54);",
        "configureEquippableTool(ItemId.ORICHALCUM_SHEARS.id(), Skill.HARVESTING.id(), 62);",
        "configureEquippableTool(ItemId.RUNE_SHEARS.id(), Skill.HARVESTING.id(), 70);",
        "private static final int MYWORLD_PICKAXE_APPEARANCE_START = MYWORLD_RUNE_STAFF_APPEARANCE_START",
        "private static final int[] MYWORLD_PICKAXE_IDS = {",
        "ItemId.COPPER_PICKAXE.id(),",
        "applyMyWorldPickaxeAppearanceOverrides();",
        "setItemAppearance(MYWORLD_PICKAXE_IDS[i], MYWORLD_PICKAXE_APPEARANCE_START + i);",
        "def.setWieldable(true);",
        "def.setWieldPosition(Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex());",
        "def.setRequiredLevel(requiredLevel);",
        "def.setRequiredSkillIndex(skillId);",
    )
    for snippet in entity_snippets:
        if snippet not in entity_text:
            fail(f"Tool equip gate missing EntityHandler snippet: {snippet}")
    if "setInventoryOnlyTool(ItemId." in entity_text:
        fail("MyWorld tools are still being forced to inventory-only")
    for snippet in (
        "private static final int[] MYWORLD_PICKAXE_COLORS = {",
        "0xB7C9D9, 0xC86A2B, 16737817, 15654365, 15658734,",
        "animations.add(new AnimationDef(\"pickaxe\", \"equipment\", pickaxeColor, 0, true, false, 0));",
    ):
        if snippet not in client_entity_text:
            fail(f"Pickaxe client held-sprite mapping missing snippet: {snippet}")

    if "public static final int[] miningAxeLvls = {70, 62, 54, 46, 38, 30, 22, 15, 8, 1};" not in formulae_text:
        fail("Mining tool runtime levels are not on the 1-70 ladder")
    if "public static final int[] woodcuttingAxeLvls = {80, 70, 62, 54, 46, 38, 30, 30, 22, 15, 8, 1};" not in formulae_text:
        fail("Woodcutting tool runtime levels are not on the 1-70 ladder")
    for snippet in (
        "case RUNE_PICKAXE:\n\t\t\t\treturn 70;",
        "case ORICHALCUM_PICKAXE:\n\t\t\t\treturn 62;",
        "case COPPER_PICKAXE:\n\t\t\t\treturn 8;",
        "int reqlvl = getPickaxeRequiredLevel(axeId);",
    ):
        if snippet not in mining_text:
            fail(f"Mining tool required-level helper missing snippet: {snippet}")


def require_tool_combat_appearance_suppression() -> None:
    formulae_text = FORMULAE.read_text(encoding="utf-8")
    equipment_text = EQUIPMENT.read_text(encoding="utf-8")
    player_text = PLAYER.read_text(encoding="utf-8")
    mob_text = MOB.read_text(encoding="utf-8")
    updater_text = GAME_STATE_UPDATER.read_text(encoding="utf-8")

    snippets = (
        (formulae_text, "public static boolean isGatheringTool(int itemId)"),
        (formulae_text, "DataConversions.inArray(miningAxeIDs, itemId)"),
        (formulae_text, "DataConversions.inArray(woodcuttingAxeIDs, itemId)"),
        (formulae_text, "DataConversions.inArray(harvestingShearsIDs, itemId)"),
        (formulae_text, "DataConversions.inArray(fishingToolIDs, itemId)"),
        (equipment_text, "public boolean hasEquippedGatheringToolInMainhand()"),
        (equipment_text, "Formulae.isGatheringTool(item.getCatalogId())"),
        (player_text, "public int[] getWornItemsForAppearanceUpdate()"),
        (player_text, "visibleWornItems[AppearanceId.SLOT_WEAPON] = AppearanceId.NOTHING.id();"),
        (player_text, "public void flagToolCombatAppearanceChanged()"),
        (mob_text, "flagToolCombatAppearanceUpdate();"),
        (updater_text, "playerNeedingAppearanceUpdate.getWornItemsForAppearanceUpdate()"),
    )
    for text, snippet in snippets:
        if snippet not in text:
            fail(f"Tool combat appearance suppression missing snippet: {snippet}")


def require_mining_uses_guaranteed_yield() -> None:
    text = MINING_PLUGIN.read_text(encoding="utf-8")
    snippets = (
        "player.getCarriedItems().getEquipment().hasCatalogID(Formulae.miningAxeIDs[i])\n\t\t\t\t&& lvl >= Formulae.miningAxeLvls[i]",
        "public static int getPickaxeTier(int axeId)",
        "public static String getMiningFocusLabel(int combatStyle)",
        "private static double getRandomGemChance(Player player)",
        "Formulae.calcGatheringYield(def.getReqLevel(), mineLvl, getPickaxeTier(axeId))",
        "Formulae.calcGatheringYield(1, player.getSkills().getLevel(Skill.MINING.id()), getPickaxeTier(axeId))",
        "player.incExp(Skill.MINING.id(), def.getExp() * quantity, true)",
        "changeloc(rock, resourceRespawnMillis(def.getRespawnTime()), SceneryId.ROCK_GENERIC.id())",
        "return Math.max(1, (respawnSeconds * 1000) / 2);",
        "Any excess falls to the ground because you have no room",
    )
    for snippet in snippets:
        if snippet not in text:
            fail(f"Mining guaranteed-yield conversion missing expected snippet: {snippet}")
    if "getOre(def, player.getSkills().getLevel(Skill.MINING.id()), axeId)" in text:
        fail("Mining still gates normal ore rewards behind the old failure roll")


def require_woodcutting_uses_guaranteed_yield() -> None:
    text = WOODCUTTING_PLUGIN.read_text(encoding="utf-8")
    snippets = (
        "public static int getAxeTier(int axeId)",
        "startbatch(1);",
        "Formulae.calcGatheringYield(def.getReqLevel(), player.getSkills().getLevel(Skill.WOODCUTTING.id()), getAxeTier(axeId))",
        "player.incExp(Skill.WOODCUTTING.id(), def.getExp() * quantity, true)",
        "player.getWorld().replaceGameObject(object, newObject)",
        "if (def.getLogId() == ItemId.PALM_LOGS.id())",
        "player.getWorld().unregisterGameObject(object);",
        "return Math.max(1, (respawnSeconds * 1000) / 4);",
        "Any excess falls to the ground because you have no room",
    )
    for snippet in snippets:
        if snippet not in text:
            fail(f"Woodcutting guaranteed-yield conversion missing expected snippet: {snippet}")
    if "getLog(def, player.getSkills().getLevel(Skill.WOODCUTTING.id()), axeId))" in text:
        fail("Woodcutting still gates log rewards behind the old failure roll")


def require_harvesting_uses_guaranteed_yield() -> None:
    text = HARVESTING_PLUGIN.read_text(encoding="utf-8")
    snippets = (
        "public static int getTool(Player player)",
        "public static int getShearsRequiredLevel(int shearsId)",
        "public static int getShearsTier(int shearsId)",
        "public static int getRequiredShearsTierForLevel(int harvestingRequirement)",
        "private static boolean hasRequiredShearsTier(int toolId, int harvestingRequirement)",
        "private static int getMinimumShearsRewardTier(int shearsTier)",
        "private static boolean isInShearsRewardTierRange(int shearsTier, int harvestingRequirement)",
        "public static boolean isShears(int itemId)",
        "calculateHerbDropForShearsTier(prodEnum, getShearsTier(toolId))",
        "isInShearsRewardTierRange(shearsTier, produce.getLevel())",
        "You need \" + requiredShearsTierMessage(reqLevel)",
        "You need \" + requiredShearsTierMessage(def.getReqLevel())",
        "Formulae.calcGatheringYield(prodEnum.get(prodId).getLevel(), player.getSkills().getLevel(Skill.HARVESTING.id()), getShearsTier(toolId))",
        "Formulae.calcGatheringYield(def.getReqLevel(), player.getSkills().getLevel(Skill.HARVESTING.id()), getShearsTier(toolId))",
        "player.incExp(Skill.HARVESTING.id(), prodEnum.get(prodId).getXp() * quantity, true)",
        "player.incExp(Skill.HARVESTING.id(), def.getExp() * quantity, true)",
        "final AtomicInteger evt = new AtomicInteger(HarvestingEvents.NONE.getID());",
        "player.getWorld().replaceGameObject(object, newObject)",
        "Any excess falls to the ground because you have no room",
    )
    for snippet in snippets:
        if snippet not in text:
            fail(f"Harvesting guaranteed-yield conversion missing expected snippet: {snippet}")
    if "getProduce(def.getReqLevel(), player.getSkills().getLevel(Skill.HARVESTING.id()))" in text:
        fail("Harvesting still gates produce rewards behind the old failure roll")
    for retired_gate in (
        "player.getSkills().getLevel(Skill.HARVESTING.id()) < def.getReqLevel()",
        "player.getSkills().getLevel(Skill.HARVESTING.id()) < reqLevel",
    ):
        if retired_gate in text:
            fail(f"Harvesting still uses direct skill-level node gating: {retired_gate}")
    for retired_tool in ("ItemId.FRUIT_PICKER.id()", "ItemId.HAND_SHOVEL.id()", "ItemId.HERB_CLIPPERS.id()"):
        if retired_tool in text:
            fail(f"Harvesting still references retired harvesting tool: {retired_tool}")


def require_shearing_uses_harvesting() -> None:
    text = SHEEP_PLUGIN.read_text(encoding="utf-8")
    npc_defs_text = (ROOT / "server/conf/server/defs/NpcDefs.json").read_text(encoding="utf-8")
    npc_patch18_text = (ROOT / "server/conf/server/defs/NpcDefsPatch18.json").read_text(encoding="utf-8")
    snippets = (
        "Harvesting.isShears(item.getCatalogId())",
        "public class Sheep implements UseNpcTrigger, OpNpcTrigger",
        'private static final String SHEAR_COMMAND = "shear";',
        "int equippedTool = Harvesting.getTool(player);",
        'player.message("You need to equip harvesting shears you can use");',
        "Harvesting.WOOL_UNLOCK_LEVEL",
        "Formulae.calcGatheringYield(Harvesting.WOOL_UNLOCK_LEVEL, player.getSkills().getLevel(Skill.HARVESTING.id()), Harvesting.getShearsTier(shearsId))",
        "player.incExp(Skill.HARVESTING.id(), Harvesting.WOOL_HARVESTING_EXP * quantity, true)",
        "Any excess falls to the ground because you have no room",
    )
    for snippet in snippets:
        if snippet not in text:
            fail(f"Sheep shearing Harvesting conversion missing expected snippet: {snippet}")
    if "The sheep manages to get away from you!" in text:
        fail("Sheep shearing still has the old failure path")
    for label, defs_text in (("NpcDefs.json", npc_defs_text), ("NpcDefsPatch18.json", npc_patch18_text)):
        if '"name": "Sheep"' not in defs_text or '"command": "Shear"' not in defs_text:
            fail(f"{label} does not expose the direct Sheep shearing command")


def require_shears_smithing_and_defs() -> None:
    smithing_text = SMITHING_PLUGIN.read_text(encoding="utf-8")
    client_text = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    item_id_text = ITEM_IDS.read_text(encoding="utf-8")
    custom_defs_text = ITEM_DEFS_CUSTOM.read_text(encoding="utf-8")
    myworld_defs_text = ITEM_DEFS_MYWORLD.read_text(encoding="utf-8")

    smithing_snippets = (
        '"Hatchet", "Pickaxe", "Shears", "Battle Axe (3 bars)"',
        "return 21; // Shears",
        "def.bars = 1;\n\t\t\t\tdef.level = baseLevel;\n\t\t\t\tdef.itemID = getModernShearsId(barId);",
        "private int getModernShearsId(int barId)",
        "case TIN_BAR:\n\t\t\t\treturn ItemId.SHEARS.id();",
        "case RUNITE_BAR:\n\t\t\t\treturn ItemId.RUNE_SHEARS.id();",
    )
    for snippet in smithing_snippets:
        if snippet not in smithing_text:
            fail(f"Shears Smithing integration missing expected snippet: {snippet}")

    for item_name in (
        "COPPER_SHEARS(2215)",
        "BRONZE_SHEARS(2216)",
        "IRON_SHEARS(2217)",
        "STEEL_SHEARS(2218)",
        "MITHRIL_SHEARS(2219)",
        "TITAN_STEEL_SHEARS(2220)",
        "ADAMANTITE_SHEARS(2221)",
        "ORICHALCUM_SHEARS(2222)",
        "RUNE_SHEARS(2223)",
    ):
        if item_name not in item_id_text:
            fail(f"ItemId shears ladder missing expected snippet: {item_name}")
    max_custom_match = re.search(r"public static final int maxCustom = (\d+);", item_id_text)
    if not max_custom_match or int(max_custom_match.group(1)) <= 2690:
        fail("ItemId.maxCustom must include the current gathering tool IDs")

    for item_name in (
        "PINE_FISHING_ROD(2682)",
        "OAK_FISHING_ROD(2683)",
        "WILLOW_FISHING_ROD(2684)",
        "PALM_FISHING_ROD(2685)",
        "MAPLE_FISHING_ROD(2686)",
        "YEW_FISHING_ROD(2687)",
        "EBONY_FISHING_ROD(2688)",
        "MAGIC_FISHING_ROD(2689)",
        "BLOOD_FISHING_ROD(2690)",
    ):
        if item_name not in item_id_text:
            fail(f"ItemId fishing rod ladder missing expected snippet: {item_name}")

    for item_name in (
        '"name": "Copper shears"',
        '"name": "Bronze shears"',
        '"name": "Iron shears"',
        '"name": "Steel shears"',
        '"name": "Mithril shears"',
        '"name": "Titan Steel shears"',
        '"name": "Adamantite shears"',
        '"name": "Orichalcum shears"',
        '"name": "Rune shears"',
    ):
        if item_name not in custom_defs_text:
            fail(f"Custom item defs missing expected shears definition: {item_name}")
    for server_snippet in (
        '"name": "Copper shears",\n      "description": "Copper shears for harvesting",\n      "command": "",\n      "isFemaleOnly": 0,\n      "isMembersOnly": 0,\n      "isStackable": 0,\n      "isUntradable": 0,\n      "isWearable": 1,\n      "appearanceID": 66,',
        '"name": "Rune shears",\n      "description": "Rune shears for harvesting",\n      "command": "",\n      "isFemaleOnly": 0,\n      "isMembersOnly": 0,\n      "isStackable": 0,\n      "isUntradable": 0,\n      "isWearable": 1,\n      "appearanceID": 66,',
    ):
        if server_snippet not in custom_defs_text:
            fail(f"Custom shears defs do not point at the real shears sprite: {server_snippet.splitlines()[0]}")
    if '"id": 144,\n      "name": "Tin shears"' not in myworld_defs_text:
        fail("MyWorld item overrides do not rename legacy shears to Tin shears")

    client_snippets = (
        'new ItemDef("Tin shears", "Tin shears for harvesting", "", 1, 66, "items:66"',
        'addMetalShearsDefinition("Copper shears", 2215, 4, 0xC86A2B);',
        'addMetalShearsDefinition("Rune shears", 2223, 32000, 0x00FFFF);',
        'new ItemDef(name, name + " for harvesting", "", price, 66, "items:66"',
        "while (items.size() <= id)",
        'addMetalArrowHeadDefinition("Tin arrow heads", 2004, 1, 0xB7C9D9);',
        'addMetalArrowHeadDefinition("Copper arrow heads", 2015, 2, 0xC86A2B);',
        'new ItemDef(name, "Dangerous looking arrow heads - need shafts for flight", "", price, 207, "items:207"',
        'addMetalDartTipDefinition("Tin dart tips", 2005, 1, 0xB7C9D9);',
        'addMetalDartTipDefinition("Copper dart tips", 2016, 2, 0xC86A2B);',
        'new ItemDef(name, "Dangerous looking dart tips - need feathers for flight", "", price, 369, "items:369"',
        'addMetalThrowingKnifeDefinition("Tin throwing knife", 1996, 2, 0xB7C9D9);',
        'addMetalThrowingKnifeDefinition("Copper throwing knife", 2007, 4, 0xC86A2B);',
        'new ItemDef(name, "A finely balanced knife", "", price, 80, "items:80"',
        'addMetalBoltDefinition("Copper bolts", 2178, 4, 0xC86A2B);',
        'new ItemDef("Bolt mould", "Use with bars to cast bolts", "", 5, -1, "external-png:bolt-mould"',
        'new ItemDef("Dart mould", "Use with bars to cast dart tips", "", 5, -1, "external-png:dart-mould"',
        'new ItemDef("Throwing knife mould", "Use with bars to cast throwing knives", "", 5, -1, "external-png:throwing-knife-mould"',
        'new ItemDef("Arrowhead mould", "Use with bars to cast arrow heads", "", 5, -1, "external-png:arrowhead-mould"',
    )
    for snippet in client_snippets:
        if snippet not in client_text:
            fail(f"Client item sprite mapping missing expected snippet: {snippet}")


def main() -> None:
    levels_by_ore = load_mining_levels_by_ore()
    require_ore_levels(MAINLINE_ORE_LEVELS, levels_by_ore)
    require_ore_levels(PRESERVED_ORE_LEVELS, levels_by_ore)
    require_plan_text()
    require_stone_mining_baseline()
    require_gathering_yield_helpers()
    require_tool_equip_gates()
    require_tool_combat_appearance_suppression()
    require_mining_uses_guaranteed_yield()
    require_woodcutting_uses_guaranteed_yield()
    require_harvesting_uses_guaranteed_yield()
    require_shearing_uses_harvesting()
    require_shears_smithing_and_defs()
    print("PASS: gathering rework plan and mining levels validated")


if __name__ == "__main__":
    main()
