#!/usr/bin/env python3
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
SMITHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smithing.java"
SMELTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java"
SKILL_GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
ITEM_DEFS_CUSTOM = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
INV_ACTION = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/itemactions/InvAction.java"
DWARF_RESCUE = ROOT / "server/plugins/com/openrsc/server/plugins/custom/minigames/DwarfRescue.java"
WAYNE = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/falador/WaynesChains.java"
LEGENDS_RADIMUS = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/"
    "legendsquest/npcs/LegendsQuestSirRadimusErkle.java"
)


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def forbid(text: str, needle: str, message: str) -> None:
    if needle in text:
        fail(message)


def require(text: str, needle: str, message: str) -> None:
    if needle not in text:
        fail(message)


def main() -> None:
    smithing = SMITHING.read_text(encoding="utf-8")
    smelting = SMELTING.read_text(encoding="utf-8")
    guide = SKILL_GUIDE.read_text(encoding="utf-8")
    client_entity_handler = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    item_defs_custom = ITEM_DEFS_CUSTOM.read_text(encoding="utf-8")
    inv_action = INV_ACTION.read_text(encoding="utf-8")
    dwarf_rescue = DWARF_RESCUE.read_text(encoding="utf-8")
    wayne = WAYNE.read_text(encoding="utf-8")
    radimus = LEGENDS_RADIMUS.read_text(encoding="utf-8")

    require(smithing, "ItemId.DRAGON_BAR.id()", "Dragon bar should be a normal Smithing material")
    require(smithing, "case DRAGON_BAR:\n\t\t\t\treturn 11;", "Dragon bar should map to tier 11")
    require(smithing, "return ItemId.LARGE_DRAGON_HELMET.id();", "Dragon smithing should include the large helm")
    require(smithing, "return ItemId.DRAGON_PLATE_MAIL_BODY.id();", "Dragon smithing should include platebody")
    require(smithing, "return ItemId.DRAGON_PLATE_MAIL_LEGS.id();", "Dragon smithing should include platelegs")
    require(smithing, "return ItemId.DRAGON_SQUARE_SHIELD.id();", "Dragon smithing should include square shield")
    require(smithing, "return ItemId.DRAGON_KITE_SHIELD.id();", "Dragon smithing should include paladin shield")
    require(smithing, "return ItemId.DRAGON_DAGGER.id();", "Dragon smithing should include dagger")
    require(smithing, "return ItemId.DRAGON_SWORD.id();", "Dragon smithing should include sword")
    require(smithing, "return ItemId.DRAGON_2_HANDED_SWORD.id();", "Dragon smithing should include 2-handed sword")
    require(smithing, "return ItemId.DRAGON_AXE.id();", "Dragon smithing should include hatchet")
    require(smithing, "return ItemId.DRAGON_BATTLE_AXE.id();", "Dragon smithing should include battle axe")
    forbid(smithing, "DRAGON_METAL_CHAIN.id()", "Dragon metal chain should not have a Smithing recipe")
    require(smithing, "attemptDragonSquareCombine", "Dragon shield half repair should remain available")

    require(smelting, 'LAVA_FORGE_REPAIRED_CACHE_KEY = "myworld_lava_forge_repaired"', "Lava forge should have a repair state")
    require(smelting, "LAVA_FORGE_REPAIR_SCALE_AMOUNT = 100", "Lava forge repair should require 100 black dragon scales")
    require(smelting, "LAVA_FORGE_REPAIR_COIN_AMOUNT = 1000000", "Lava forge repair should require 1,000,000 coins")
    require(smelting, "new SmeltRecipe(ItemId.DRAGON_BAR.id(), DRAGON_SMELTING_LEVEL, RAW_DRAGON_METAL_XP, 1, \"lava forge\"", "Dragon bar should be made at the lava forge")
    require(smelting, "ingredient(ItemId.RAW_DRAGON_METAL.id(), 1), ingredient(ItemId.DRAGON_SULFUR.id(), 6)", "Dragon bar should require dragon metal scrap and dragon sulfur")
    require(smelting, "new SmeltRecipe(MyWorldItemId.PURIFIED_RUNE_BAR, 90, 500, 1, \"lava forge\"", "Purified rune should be moved to the lava forge")
    require(smelting, "ingredient(ItemId.RUNITE_BAR.id(), 1), ingredient(ItemId.DRAGON_SULFUR.id(), 14)", "Purified rune should require runite and dragon sulfur")
    require(client_entity_handler, '"external-png:raw-dragon-metal@43x27"', "Dragon metal scrap should use its custom sprite")
    require(client_entity_handler, 'new ItemDef("Dragon Metal Scrap",', "Client should display the dragon metal scrap name")
    require(item_defs_custom, '"name": "Dragon Metal Scrap"', "Server should display the dragon metal scrap name")
    forbid(client_entity_handler, 'new ItemDef("Raw Dragon Metal",', "Client should not retain the retired item name")
    forbid(item_defs_custom, '"name": "Raw Dragon Metal"', "Server should not retain the retired item name")
    forbid(smelting, "Dragon metal chains", "Lava forge should not offer dragon metal chains")
    forbid(smelting, "DRAGON_METAL_CHAIN.id()", "Lava forge should not create dragon metal chains")

    forbid(guide, "Dragon metal chains", "Smithing guide should not advertise dragon chains")
    require(
        guide,
        'new SkillMenuItem(1365, "80", "Dragon bar - dragon metal scrap and 6 dragon sulfur")',
        "Smithing guide should keep dragon bar smelting text compact",
    )
    require(
        guide,
        'new SkillMenuItem(3261, "90", "Purified Rune Bar - rune bar and 14 dragon sulfur")',
        "Smithing guide should keep purified rune smelting text compact",
    )
    require(guide, "Dragon bars require the repaired lava forge", "Smithing info should explain repaired lava forge dragon bars")
    require(guide, "Purified Rune Bars require the repaired lava forge", "Smithing info should explain repaired lava forge purified rune")
    require(guide, 'addSmithingTier("Dragon", 80, 1447, -1, 593, -1, 1346, 594, -1, 2752, -1, -1, -1, -1, 1425, -1, -1, 1278, 1426, 1429, 1427);', "Smithing guide should list the dragon tier")
    require(guide, "private void addSmithingGuideItem", "Smithing guide should skip unavailable dragon outputs")

    for note_text in (inv_action, dwarf_rescue):
        require(note_text, "Repair the lava forge with 100 Black dragon scales and 1,000,000 coins", "Dwarf note should explain lava forge repair")
        require(note_text, "1 Dragon metal scrap", "Dwarf note should explain dragon bar raw metal")
        require(note_text, "6 Dragon sulfur", "Dwarf note should explain dragon bar sulfur")
        forbid(note_text, "Dragon Metal Chains", "Dwarf note should not list dragon metal chains")
        forbid(note_text, "Chipped Dragon Scales", "Dwarf note should not list chipped scale costs")
        forbid(note_text, "Wayne", "Dwarf note should not route dragon armor through Wayne")
        forbid(note_text, "Combat Odyssey through the Legends Guild", "Dwarf note should not route platelegs through Combat Odyssey")

    forbid(wayne, "special armour", "Wayne should no longer offer the special armor route")
    forbid(wayne, "DRAGON_SCALE_MAIL", "Wayne should not craft dragon scale mail")
    forbid(wayne, "DRAGON_PLATE_MAIL_BODY", "Wayne should not craft dragon platebody")
    require(radimus, "if (CombatOdyssey.getIntroStage(player) == CombatOdyssey.NOT_STARTED) {\n\t\t\t\t\treturn false;\n\t\t\t\t}", "Radimus should hide new Combat Odyssey starts")

    print("PASS: dragon metal production route validated")


if __name__ == "__main__":
    main()
