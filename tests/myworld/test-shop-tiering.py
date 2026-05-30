#!/usr/bin/env python3
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]

HORVIK_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "HorvikTheArmourer.java"
THRANDER_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "Thrander.java"
ZEKE_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "alkharid" / "ZekeScimitars.java"
WAYNE_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "falador" / "WaynesChains.java"
SCAVVO_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "Scavvo.java"
OZIACH_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "edgeville" / "OziachsRunePlateShop.java"
VARROCK_SWORDS_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "VarrockSwords.java"
LOWE_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "LowesArchery.java"
HICKTON_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "catherby" / "HicktonArcheryShop.java"
KING_LATHAS_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "ardougne" / "east" / "KingLathasKeeper.java"
GULLUCK_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "grandtree" / "Gulluck.java"
GAIUS_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "taverly" / "GaiusTwoHandlerShop.java"
BRIAN_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "portsarim" / "BriansBattleAxes.java"
PEKSA_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "barbarian" / "PeksaHelmets.java"
CASSIE_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "falador" / "CassieShields.java"
FLYNN_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "falador" / "FlynnMaces.java"
BOB_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "lumbridge" / "BobsAxes.java"
NURMOF_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "dwarvenmine" / "NurmofPickaxe.java"
DROGO_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "dwarvenmine" / "Drogo.java"
LOUIE_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "alkharid" / "LouieLegs.java"
ZENESHA_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "ardougne" / "east" / "Zenesha.java"
VALAINE_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "Valaine.java"
THESSALIA_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "ThessaliasClothes.java"
TAILOR_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "Tailor.java"
ZAFF_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "ZaffsStaffs.java"
MAGIC_STORE_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "yanille" / "MagicStoreOwner.java"
BETTY_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "portsarim" / "BettysMagicEmporium.java"
CRAFTING_EQUIPMENT_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "CraftingEquipmentShops.java"
NPC_DEFS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "NpcDefs.json"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def ensure_contains(path: Path, snippets: tuple[str, ...]) -> None:
    content = text(path)
    for snippet in snippets:
        if snippet not in content:
            fail(f"{path.name} missing expected stock snippet: {snippet}")


def ensure_not_contains(path: Path, snippets: tuple[str, ...]) -> None:
    content = text(path)
    for snippet in snippets:
        if snippet in content:
            fail(f"{path.name} still contains retired/old stock snippet: {snippet}")


def main() -> None:
    ensure_contains(HORVIK_PATH, (
        "ItemId.TIN_PLATE_MAIL_BODY.id()",
        "ItemId.COPPER_PLATE_MAIL_BODY.id()",
        "ItemId.MITHRIL_PLATE_MAIL_BODY.id()",
    ))
    ensure_not_contains(HORVIK_PATH, (
        "ItemId.BLACK_PLATE_MAIL_BODY.id()",
        "ItemId.BLACK_KITE_SHIELD.id()",
        "ItemId.LARGE_BLACK_HELMET.id()",
    ))

    ensure_contains(THRANDER_PATH, (
        "ItemId.TIN_PLATE_MAIL_BODY.id()",
        "ItemId.COPPER_PLATE_MAIL_BODY.id()",
        "ItemId.STEEL_PLATE_MAIL_BODY.id()",
        "Do you want to trade?",
        "ActionSender.showShop(player, shop);",
    ))
    ensure_contains(NPC_DEFS_PATH, (
        '"id": 160',
        '"name": "Thrander"',
        '"command": "Trade"',
    ))

    ensure_contains(ZEKE_PATH, (
        "ItemId.TIN_SCIMITAR.id()",
        "ItemId.COPPER_SCIMITAR.id()",
        "ItemId.MITHRIL_SCIMITAR.id()",
    ))
    ensure_not_contains(ZEKE_PATH, (
        "ItemId.BLACK_SCIMITAR.id()",
    ))

    ensure_contains(WAYNE_PATH, (
        "ItemId.TIN_PLATE_MAIL_BODY.id()",
        "ItemId.COPPER_PLATE_MAIL_BODY.id()",
        "ItemId.BRONZE_PLATE_MAIL_BODY.id()",
        "ItemId.IRON_PLATE_MAIL_BODY.id()",
        "ItemId.STEEL_PLATE_MAIL_BODY.id()",
        "ItemId.MITHRIL_PLATE_MAIL_BODY.id()",
        "ItemId.TIN_PLATE_MAIL_LEGS.id()",
        "ItemId.COPPER_PLATE_MAIL_LEGS.id()",
        "ItemId.BRONZE_PLATE_MAIL_LEGS.id()",
        "ItemId.IRON_PLATE_MAIL_LEGS.id()",
        "ItemId.STEEL_PLATE_MAIL_LEGS.id()",
        "ItemId.MITHRIL_PLATE_MAIL_LEGS.id()",
        "ItemId.TIN_LARGE_HELMET.id()",
        "ItemId.COPPER_LARGE_HELMET.id()",
        "ItemId.LARGE_BRONZE_HELMET.id()",
        "ItemId.LARGE_IRON_HELMET.id()",
        "ItemId.LARGE_STEEL_HELMET.id()",
        "ItemId.LARGE_MITHRIL_HELMET.id()",
    ))
    ensure_not_contains(WAYNE_PATH, (
        "CHAIN_MAIL",
        "ADAMANTITE_",
        "TITAN_STEEL_",
        "ORICHALCUM_",
        "RUNE_",
    ))

    ensure_contains(SCAVVO_PATH, (
        "ItemId.RUNE_PLATE_MAIL_BODY.id()",
        "ItemId.RUNE_SCIMITAR.id()",
        "ItemId.RUNE_2_HANDED_SWORD.id()",
    ))

    ensure_contains(OZIACH_PATH, (
        "ItemId.RUNE_PLATE_MAIL_BODY.id()",
    ))

    ensure_contains(VALAINE_PATH, (
        "ItemId.LARGE_WHITE_HELMET.id()",
        "ItemId.WHITE_PLATE_MAIL_BODY.id()",
        "ItemId.TITAN_STEEL_LARGE_HELMET.id()",
        "ItemId.TITAN_STEEL_PLATE_MAIL_BODY.id()",
        "ItemId.ORICHALCUM_LARGE_HELMET.id()",
        "ItemId.ORICHALCUM_PLATE_MAIL_BODY.id()",
    ))

    ensure_contains(VARROCK_SWORDS_PATH, (
        "ItemId.TIN_SHORT_SWORD.id()",
        "ItemId.COPPER_SHORT_SWORD.id()",
        "ItemId.MITHRIL_SHORT_SWORD.id()",
        "ItemId.TIN_LONG_SWORD.id()",
        "ItemId.COPPER_LONG_SWORD.id()",
        "ItemId.MITHRIL_LONG_SWORD.id()",
        "ItemId.TIN_DAGGER.id()",
        "ItemId.COPPER_DAGGER.id()",
        "ItemId.MITHRIL_DAGGER.id()",
    ))
    ensure_not_contains(VARROCK_SWORDS_PATH, (
        "ItemId.RUNE_SHORT_SWORD.id()",
        "ItemId.RUNE_LONG_SWORD.id()",
        "ItemId.RUNE_DAGGER.id()",
        "ItemId.BLACK_SHORT_SWORD.id()",
        "ItemId.BLACK_LONG_SWORD.id()",
        "ItemId.BLACK_DAGGER.id()",
        "ItemId.ADAMANTITE_SHORT_SWORD.id()",
        "ItemId.ADAMANTITE_LONG_SWORD.id()",
        "ItemId.ADAMANTITE_DAGGER.id()",
    ))

    for path in (LOWE_PATH, HICKTON_PATH, KING_LATHAS_PATH, GULLUCK_PATH):
        ensure_contains(path, (
            "ItemId.TIN_ARROWS.id()",
            "ItemId.COPPER_ARROWS.id()",
            "ItemId.MAGIC_SHORTBOW.id()",
            "ItemId.MAGIC_LONGBOW.id()",
            "ItemId.MAGIC_CROSSBOW.id()",
        ))
        ensure_not_contains(path, (
            "ItemId.CROSSBOW_BOLTS.id()",
            "ItemId.TITAN_STEEL_BOLTS.id()",
        ))

    for path in (KING_LATHAS_PATH, GULLUCK_PATH):
        ensure_not_contains(path, (
            "ItemId.BLACK_AXE.id()",
            "ItemId.BLACK_BATTLE_AXE.id()",
            "ItemId.BLACK_2_HANDED_SWORD.id()",
        ))

    for path in (GAIUS_PATH,):
        ensure_contains(path, (
            "ItemId.TIN_2_HANDED_SWORD.id()",
            "ItemId.COPPER_2_HANDED_SWORD.id()",
            "ItemId.MITHRIL_2_HANDED_SWORD.id()",
        ))
        ensure_not_contains(path, (
            "ItemId.BLACK_2_HANDED_SWORD.id()",
            "ItemId.ADAMANTITE_2_HANDED_SWORD.id()",
        ))

    ensure_contains(BRIAN_PATH, (
        "ItemId.TIN_BATTLE_AXE.id()",
        "ItemId.COPPER_BATTLE_AXE.id()",
        "ItemId.MITHRIL_BATTLE_AXE.id()",
    ))
    ensure_not_contains(BRIAN_PATH, (
        "ItemId.RUNE_BATTLE_AXE.id()",
        "ItemId.BLACK_BATTLE_AXE.id()",
        "ItemId.ADAMANTITE_BATTLE_AXE.id()",
    ))

    ensure_contains(PEKSA_PATH, (
        "ItemId.TIN_LARGE_HELMET.id()",
        "ItemId.COPPER_LARGE_HELMET.id()",
        "ItemId.LARGE_MITHRIL_HELMET.id()",
    ))
    ensure_not_contains(PEKSA_PATH, (
        "ItemId.LARGE_ADAMANTITE_HELMET.id()",
    ))

    ensure_contains(CASSIE_PATH, (
        "ItemId.TIN_SQUARE_SHIELD.id()",
        "ItemId.COPPER_SQUARE_SHIELD.id()",
        "ItemId.MITHRIL_SQUARE_SHIELD.id()",
    ))

    ensure_contains(FLYNN_PATH, (
        "ItemId.ADAMANTITE_MACE.id()",
        "ItemId.TITAN_STEEL_MACE.id()",
        "ItemId.ORICHALCUM_MACE.id()",
    ))

    ensure_contains(BOB_PATH, (
        "ItemId.TIN_PICKAXE.id()",
        "ItemId.COPPER_PICKAXE.id()",
        "ItemId.TIN_AXE.id()",
        "ItemId.COPPER_AXE.id()",
        "ItemId.MITHRIL_AXE.id()",
        "ItemId.TIN_BATTLE_AXE.id()",
        "ItemId.COPPER_BATTLE_AXE.id()",
        "ItemId.MITHRIL_BATTLE_AXE.id()",
    ))
    ensure_not_contains(BOB_PATH, (
        "ItemId.RUNE_PICKAXE.id()",
        "ItemId.RUNE_AXE.id()",
        "ItemId.RUNE_BATTLE_AXE.id()",
    ))

    ensure_contains(NURMOF_PATH, (
        "ItemId.TIN_PICKAXE.id()",
        "ItemId.COPPER_PICKAXE.id()",
        "ItemId.TITAN_STEEL_PICKAXE.id()",
        "ItemId.ORICHALCUM_PICKAXE.id()",
        "ItemId.RUNE_PICKAXE.id()",
    ))

    ensure_contains(DROGO_PATH, (
        "ItemId.TIN_PICKAXE.id()",
        "ItemId.COPPER_PICKAXE.id()",
        "ItemId.TIN_BAR.id()",
        "ItemId.COPPER_BAR.id()",
        "ItemId.BRONZE_BAR.id()",
        "ItemId.IRON_BAR.id()",
    ))

    RANAEL_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "alkharid" / "RanaelSkirt.java"

    ensure_contains(LOUIE_PATH, (
        "ItemId.TIN_PLATE_MAIL_LEGS.id()",
        "ItemId.COPPER_PLATE_MAIL_LEGS.id()",
        "ItemId.MITHRIL_PLATE_MAIL_LEGS.id()",
    ))
    ensure_not_contains(LOUIE_PATH, (
        "BLACK_",
        "ADAMANTITE_PLATE_MAIL_LEGS.id()",
    ))

    ensure_contains(ZENESHA_PATH, (
        "ItemId.BRONZE_PLATE_MAIL_TOP.id()",
        "ItemId.IRON_PLATE_MAIL_TOP.id()",
        "ItemId.MITHRIL_PLATE_MAIL_TOP.id()",
    ))
    ensure_not_contains(ZENESHA_PATH, (
        "ItemId.BLACK_PLATE_MAIL_TOP.id()",
    ))

    ensure_contains(TAILOR_PATH, (
        "ItemId.SHEARS.id()",
        "ItemId.NEEDLE.id()",
        "ItemId.WOOL.id()",
        "ItemId.BALL_OF_WOOL.id()",
        "ItemId.THREAD.id()",
        "ItemId.COW_HIDE.id()",
        "ItemId.GOBLIN_HIDE.id()",
        "ItemId.UNICORN_HIDE.id()",
        "ItemId.BEAR_HIDE.id()",
    ))
    ensure_contains(TAILOR_PATH, (
        "I keep supplies for tailoring and leatherwork",
        "Wool, thread, tools, and low tier hides",
    ))
    ensure_not_contains(TAILOR_PATH, (
        "COW_HIDE_CUIRASS",
        "GOBLIN_HIDE_CUIRASS",
        "UNICORN_HIDE_CUIRASS",
        "BEAR_HIDE_CUIRASS",
        "ItemId.SILK.id()",
        "ItemId.LEATHER.id()",
        "fancy dress parties",
        "selection of garments",
    ))

    ensure_contains(THESSALIA_PATH, (
        "ItemId.SHEARS.id()",
        "ItemId.WOOL.id()",
        "ItemId.BALL_OF_WOOL.id()",
        "ItemId.COW_HIDE.id()",
        "ItemId.GOBLIN_HIDE.id()",
        "ItemId.UNICORN_HIDE.id()",
        "ItemId.BEAR_HIDE.id()",
        "ItemId.PRIEST_ROBE.id()",
        "ItemId.PRIEST_GOWN.id()",
    ))
    ensure_contains(THESSALIA_PATH, (
        "Do you need tailoring supplies or simple robes?",
        "Wool, thread, tools, hides, and a few simple robes",
    ))
    ensure_not_contains(THESSALIA_PATH, (
        "ItemId.LEATHER_ARMOUR.id()",
        "ItemId.LEATHER_GLOVES.id()",
        "ItemId.WHITE_APRON.id()",
        "ItemId.BROWN_APRON.id()",
        "ItemId.PINK_SKIRT.id()",
        "ItemId.SILK.id()",
        "Do you want to buy any fine clothes?",
    ))

    ensure_contains(RANAEL_PATH, (
        "ItemId.SHEARS.id()",
        "ItemId.WOOL.id()",
        "ItemId.BALL_OF_WOOL.id()",
        "ItemId.COW_HIDE.id()",
        "ItemId.GOBLIN_HIDE.id()",
        "ItemId.UNICORN_HIDE.id()",
        "ItemId.BEAR_HIDE.id()",
    ))
    ensure_contains(RANAEL_PATH, (
        "Do you need tailoring supplies?",
        "I carry wool, thread, tools, and low tier hides",
    ))
    ensure_not_contains(RANAEL_PATH, (
        "PLATE_MAIL_LEGS",
        "Do you want to buy any armoured skirts?",
        "Designed especially for ladies who like to fight",
    ))

    for path in (ZAFF_PATH, MAGIC_STORE_PATH, BETTY_PATH):
        ensure_contains(path, (
            "ItemId.FIRE_RUNE.id()",
            "ItemId.WATER_RUNE.id()",
            "ItemId.AIR_RUNE.id()",
            "ItemId.EARTH_RUNE.id()",
            "ItemId.MIND_RUNE.id()",
            "ItemId.BODY_RUNE.id()",
            "ItemId.STAFF.id()",
            "ItemId.WOOL_WIZARD_HAT.id()",
            "ItemId.WOOL_ROBE_TOP.id()",
            "ItemId.WOOL_ROBE_SKIRT.id()",
        ))

    for path in (ZAFF_PATH, MAGIC_STORE_PATH):
        ensure_not_contains(path, (
            "ItemId.BLUE_WIZARDSHAT.id()",
            "ItemId.WIZARDS_ROBE.id()",
            "ItemId.BLUE_SKIRT.id()",
            "ItemId.BLACK_WIZARDSHAT.id()",
            "ItemId.BLACK_ROBE.id()",
            "ItemId.BLACK_SKIRT.id()",
            "ItemId.STAFF_OF_AIR.id()",
            "ItemId.STAFF_OF_WATER.id()",
            "ItemId.STAFF_OF_EARTH.id()",
            "ItemId.STAFF_OF_FIRE.id()",
        ))

    ensure_not_contains(BETTY_PATH, (
        "new Item(ItemId.BLUE_WIZARDSHAT.id()",
        "new Item(ItemId.WIZARDS_ROBE.id()",
        "new Item(ItemId.BLUE_SKIRT.id()",
        "new Item(ItemId.BLACK_WIZARDSHAT.id()",
        "new Item(ItemId.BLACK_ROBE.id()",
        "new Item(ItemId.BLACK_SKIRT.id()",
    ))

    ensure_contains(CRAFTING_EQUIPMENT_PATH, (
        "ItemId.RING_MOULD.id()",
        "ItemId.NECKLACE_MOULD.id()",
        "ItemId.AMULET_MOULD.id()",
        "ItemId.DART_MOULD.id()",
        "ItemId.THROWING_KNIFE_MOULD.id()",
        "ItemId.ARROWHEAD_MOULD.id()",
    ))
    ensure_not_contains(CRAFTING_EQUIPMENT_PATH, (
        "ItemId.CROWN_MOULD.id()",
    ))

    print("PASS: shop tiering and clothier restructuring validated")


if __name__ == "__main__":
    main()
