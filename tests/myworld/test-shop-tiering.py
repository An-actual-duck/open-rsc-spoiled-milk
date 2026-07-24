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
AUBURY_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "AuburysRunes.java"
AUBURY_OPENPK_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "npcs" / "AuburysRunesOpenPk.java"
LUNDAIL_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "minigames" / "mage_arena" / "Lundail.java"
CRAFTING_EQUIPMENT_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "CraftingEquipmentShops.java"
GARDENER_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "npcs" / "Gardener.java"
NPC_DEFS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "NpcDefs.json"
GAME_OBJECT_DEFS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "GameObjectDef.xml"
CLIENT_ENTITY_HANDLER_PATH = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "EntityHandler.java"
ENTITY_HANDLER_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "external" / "EntityHandler.java"
ABSTRACT_SHOP_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "plugins" / "AbstractShop.java"
HEAD_CHEF_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "npcs" / "varrock" / "HeadChef.java"


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
        "ItemId.TIN_LARGE_HELMET.id()",
        "ItemId.MITHRIL_SQUARE_SHIELD.id()",
        "ItemId.MITHRIL_PLATE_MAIL_LEGS.id()",
    ))
    ensure_not_contains(HORVIK_PATH, (
        "ItemId.TIN_PLATE_MAIL_BODY.id()",
        "ItemId.COPPER_PLATE_MAIL_BODY.id()",
        "ItemId.MITHRIL_PLATE_MAIL_BODY.id()",
        "ItemId.BLACK_PLATE_MAIL_BODY.id()",
        "ItemId.BLACK_KITE_SHIELD.id()",
        "ItemId.LARGE_BLACK_HELMET.id()",
    ))

    ensure_contains(THRANDER_PATH, (
        "ItemId.TIN_PLATE_MAIL_BODY.id()",
        "ItemId.COPPER_PLATE_MAIL_BODY.id()",
        "ItemId.STEEL_PLATE_MAIL_BODY.id()",
        "ItemId.MITHRIL_PLATE_MAIL_BODY.id()",
        "ItemId.TIN_GAUNTLETS.id()",
        "ItemId.TIN_GREAVES.id()",
        "ItemId.COPPER_GAUNTLETS.id()",
        "ItemId.COPPER_GREAVES.id()",
        "ItemId.BRONZE_GAUNTLETS.id()",
        "ItemId.BRONZE_GREAVES.id()",
        "ItemId.IRON_GAUNTLETS.id()",
        "ItemId.IRON_GREAVES.id()",
        "ItemId.STEEL_GAUNTLETS.id()",
        "ItemId.STEEL_GREAVES.id()",
        "ItemId.MITHRIL_GAUNTLETS.id()",
        "ItemId.MITHRIL_GREAVES.id()",
        "Do you want to trade?",
        "ActionSender.showShop(player, shop);",
    ))
    ensure_contains(NPC_DEFS_PATH, (
        '"id": 160',
        '"name": "Thrander"',
        '"command": "Trade"',
        '"command2": "Shop"',
    ))
    ensure_contains(ENTITY_HANDLER_PATH, (
        "NpcId.THRANDER.id()",
    ))
    ensure_contains(ABSTRACT_SHOP_PATH, (
        'command.equalsIgnoreCase("Shop")',
    ))

    ensure_contains(ZEKE_PATH, (
        "ItemId.TIN_SCIMITAR.id()",
        "ItemId.COPPER_SCIMITAR.id()",
        "ItemId.MITHRIL_SCIMITAR.id()",
        "ItemId.STAFF.id()",
        "ItemId.PINE_STAFF.id()",
        "ItemId.OAK_STAFF.id()",
        "ItemId.WILLOW_STAFF.id()",
    ))
    ensure_not_contains(ZEKE_PATH, (
        "ItemId.BLACK_SCIMITAR.id()",
    ))

    ensure_contains(CASSIE_PATH, (
        "I buy and sell armor and shields",
        "ItemId.TIN_SQUARE_SHIELD.id()",
        "ItemId.COPPER_SQUARE_SHIELD.id()",
        "ItemId.MITHRIL_SQUARE_SHIELD.id()",
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
    ensure_not_contains(CASSIE_PATH, (
        "I buy and sell shields",
    ))
    ensure_contains(WAYNE_PATH, (
        "I've decided to pivot my business to Throwing weapons!",
        "Care to try some darts or shuriken?",
        "ItemId.TIN_THROWING_DART.id()",
        "ItemId.COPPER_THROWING_DART.id()",
        "ItemId.MITHRIL_THROWING_DART.id()",
        "ItemId.TIN_THROWING_KNIFE.id()",
        "ItemId.COPPER_THROWING_KNIFE.id()",
        "ItemId.MITHRIL_THROWING_KNIFE.id()",
        "ItemId.TIN_SHURIKEN.id()",
        "ItemId.COPPER_SHURIKEN.id()",
        "ItemId.MITHRIL_SHURIKEN.id()",
    ))
    ensure_not_contains(WAYNE_PATH, (
        "ItemId.TIN_PLATE_MAIL_BODY.id()",
        "ItemId.COPPER_PLATE_MAIL_BODY.id()",
        "ItemId.BRONZE_PLATE_MAIL_BODY.id()",
        "ItemId.IRON_PLATE_MAIL_BODY.id()",
        "ItemId.STEEL_PLATE_MAIL_BODY.id()",
        "ItemId.MITHRIL_PLATE_MAIL_BODY.id()",
        "CHAIN_MAIL",
        "ADAMANTITE_",
        "TITAN_STEEL_",
        "ORICHALCUM_",
        "RUNE_",
    ))

    ensure_contains(SCAVVO_PATH, (
        "implements TalkNpcTrigger",
        "RUNE_PLATE_PRICE = 800000",
        "ItemId.RUNE_PLATE_MAIL_BODY.id()",
        "You EARNED the right to buy it. For 800k.",
        "player.getQuestStage(Quests.DRAGON_SLAYER) != -1",
        "player.getCarriedItems().remove(new Item(ItemId.COINS.id(), RUNE_PLATE_PRICE))",
    ))
    ensure_not_contains(SCAVVO_PATH, (
        "extends AbstractShop",
        "new Shop(",
        "ActionSender.showShop",
        "ItemId.RUNE_PLATE_MAIL_LEGS.id()",
        "ItemId.RUNE_KITE_SHIELD.id()",
        "ItemId.RUNE_MACE.id()",
        "ItemId.RUNE_LONG_SWORD.id()",
        "ItemId.RUNE_SHORT_SWORD.id()",
        "ItemId.RUNE_SCIMITAR.id()",
        "ItemId.RUNE_2_HANDED_SWORD.id()",
    ))
    ensure_contains(CLIENT_ENTITY_HANDLER_PATH, (
        'new NPCDef("Scavvo", "He has lopsided eyes", "",',
    ))
    ensure_not_contains(CLIENT_ENTITY_HANDLER_PATH, (
        'new NPCDef("Scavvo", "He has lopsided eyes", shopOption',
    ))
    ensure_not_contains(ENTITY_HANDLER_PATH, (
        "NpcId.SCAVVO.id(),",
    ))

    ensure_contains(OZIACH_PATH, (
        "implements TalkNpcTrigger",
        "I have slain the dragon.",
        "Well done.",
        "Go speak with Scavvo at the Champion's Guild to get your Rune Platebody",
    ))
    ensure_not_contains(OZIACH_PATH, (
        "extends AbstractShop",
        "new Shop(",
        "ActionSender.showShop",
        "ItemId.RUNE_PLATE_MAIL_BODY.id()",
    ))
    ensure_contains(CLIENT_ENTITY_HANDLER_PATH, (
        'new NPCDef("Oziach", "A strange little man", "",',
    ))
    ensure_not_contains(CLIENT_ENTITY_HANDLER_PATH, (
        'new NPCDef("Oziach", "A strange little man", shopOption',
    ))
    ensure_not_contains(ENTITY_HANDLER_PATH, (
        "NpcId.OZIACH.id(),",
    ))

    ensure_contains(VALAINE_PATH, (
        "new Shop(false, 60000, 130, 40, 3,",
        "ItemId.BLUE_CAPE.id()",
        "ItemId.ORICHALCUM_LARGE_HELMET.id()",
        "ItemId.ORICHALCUM_GAUNTLETS.id()",
        "ItemId.ORICHALCUM_GREAVES.id()",
        "ItemId.ORICHALCUM_SQUARE_SHIELD.id()",
        "ItemId.ORICHALCUM_KITE_SHIELD.id()",
        "ItemId.ORICHALCUM_PLATE_MAIL_LEGS.id()",
        "ItemId.ORICHALCUM_PLATE_MAIL_BODY.id()",
        "ItemId.RUNE_DAGGER.id()",
        "ItemId.RUNE_SHORT_SWORD.id()",
        "ItemId.RUNE_LONG_SWORD.id()",
        "ItemId.RUNE_SCIMITAR.id()",
        "ItemId.RUNE_2_HANDED_SWORD.id()",
        "ItemId.RUNE_AXE.id()",
        "ItemId.RUNE_BATTLE_AXE.id()",
        "ItemId.RUNE_MACE.id()",
        "ItemId.RUNE_SPEAR.id()",
        "ItemId.RUNE_SCYTHE.id()",
    ))
    ensure_not_contains(VALAINE_PATH, (
        "new Shop(true, 60000, 130, 40, 3,",
        "ItemId.LARGE_WHITE_HELMET.id()",
        "ItemId.WHITE_PLATE_MAIL_BODY.id()",
        "ItemId.LARGE_BLACK_HELMET.id()",
        "ItemId.BLACK_PLATE_MAIL_LEGS.id()",
        "ItemId.ADAMANTITE_PLATE_MAIL_BODY.id()",
        "ItemId.TITAN_STEEL_LARGE_HELMET.id()",
        "ItemId.TITAN_STEEL_PLATE_MAIL_LEGS.id()",
        "ItemId.TITAN_STEEL_PLATE_MAIL_BODY.id()",
        "ItemId.ORICHALCUM_DAGGER.id()",
        "ItemId.ORICHALCUM_SHORT_SWORD.id()",
        "ItemId.ORICHALCUM_LONG_SWORD.id()",
        "ItemId.ORICHALCUM_SCIMITAR.id()",
        "ItemId.ORICHALCUM_2_HANDED_SWORD.id()",
        "ItemId.ORICHALCUM_AXE.id()",
        "ItemId.ORICHALCUM_BATTLE_AXE.id()",
        "ItemId.ORICHALCUM_MACE.id()",
        "ItemId.ORICHALCUM_SPEAR.id()",
        "ItemId.ORICHALCUM_SCYTHE.id()",
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
    ensure_contains(NPC_DEFS_PATH, (
        '"id": 56',
        '"name": "Slade"',
        '"id": 130',
        '"name": "Hagger"',
    ))
    ensure_contains(GAME_OBJECT_DEFS_PATH, (
        "Slade and Hagger's Blades and Daggers",
    ))
    ensure_contains(CLIENT_ENTITY_HANDLER_PATH, (
        'new NPCDef("Slade", "I can buy swords off him", shopOption',
        'new NPCDef("Hagger", "I can buy swords off him", shopOption',
        'new GameObjectDef("sign", "Slade and Hagger\'s Blades and Daggers"',
    ))

    ensure_contains(HEAD_CHEF_PATH, (
        "extends AbstractShop",
        "ItemId.CHOCOLATE_BAR.id()",
        "ItemId.REDBERRIES.id()",
        "ItemId.GRAIN.id()",
        "ItemId.COOKING_APPLE.id()",
        "ItemId.EGG.id()",
        "ItemId.MILK.id()",
        "I'd like to buy cooking supplies",
    ))
    ensure_contains(NPC_DEFS_PATH, (
        '"id": 133',
        '"name": "Head chef"',
        '"command": "Trade"',
        '"command2": "Shop"',
    ))
    ensure_contains(CLIENT_ENTITY_HANDLER_PATH, (
        'new NPCDef("Head chef", "He looks after the chef\'s guild", shopOption',
    ))
    ensure_contains(ENTITY_HANDLER_PATH, (
        "NpcId.HEAD_CHEF.id()",
    ))

    ensure_contains(HICKTON_PATH, (
        "new Item(ItemId.TIN_ARROWS.id(), 1000)",
        "new Item(ItemId.MITHRIL_BOLTS.id(), 1000)",
        "new Item(ItemId.MITHRIL_ARROW_HEADS.id(), 1000)",
        "ItemId.TIN_ARROWS.id()",
        "ItemId.COPPER_ARROWS.id()",
        "ItemId.WILLOW_SHORTBOW.id()",
        "ItemId.WILLOW_LONGBOW.id()",
        "ItemId.WILLOW_CROSSBOW.id()",
    ))
    ensure_not_contains(HICKTON_PATH, (
        "ItemId.MAPLE_SHORTBOW.id()",
        "ItemId.MAPLE_LONGBOW.id()",
        "ItemId.YEW_SHORTBOW.id()",
        "ItemId.YEW_LONGBOW.id()",
        "ItemId.MAGIC_SHORTBOW.id()",
        "ItemId.MAGIC_LONGBOW.id()",
        "ItemId.MAPLE_CROSSBOW.id()",
        "ItemId.YEW_CROSSBOW.id()",
        "ItemId.MAGIC_CROSSBOW.id()",
        "ItemId.CROSSBOW_BOLTS.id()",
        "ItemId.TITAN_STEEL_BOLTS.id()",
    ))

    for path in (KING_LATHAS_PATH, GULLUCK_PATH):
        ensure_contains(path, (
            "new Item(ItemId.TIN_ARROWS.id(), 1000)",
            "new Item(ItemId.MITHRIL_BOLTS.id(), 1000)",
            "new Item(ItemId.MITHRIL_ARROW_HEADS.id(), 1000)",
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

    ensure_contains(LOWE_PATH, (
        "new Item(ItemId.TIN_ARROWS.id(), 1000)",
        "new Item(ItemId.MITHRIL_BOLTS.id(), 1000)",
        "new Item(ItemId.MITHRIL_ARROW_HEADS.id(), 1000)",
        "ItemId.PINE_SHORTBOW.id()",
        "ItemId.PALM_SHORTBOW.id()",
        "ItemId.YEW_SHORTBOW.id()",
        "ItemId.PHOENIX_CROSSBOW.id()",
        "ItemId.PALM_CROSSBOW.id()",
        "ItemId.YEW_CROSSBOW.id()",
    ))
    ensure_not_contains(LOWE_PATH, (
        "ItemId.MAGIC_SHORTBOW.id()",
        "ItemId.MAGIC_LONGBOW.id()",
        "ItemId.MAGIC_CROSSBOW.id()",
        "ItemId.EBONY_SHORTBOW.id()",
        "ItemId.BLOOD_SHORTBOW.id()",
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

    ensure_contains(FLYNN_PATH, (
        "ItemId.TIN_MACE.id()",
        "ItemId.COPPER_MACE.id()",
        "ItemId.MITHRIL_MACE.id()",
        "ItemId.TIN_KITE_SHIELD.id()",
        "ItemId.COPPER_KITE_SHIELD.id()",
        "ItemId.MITHRIL_KITE_SHIELD.id()",
    ))
    ensure_not_contains(FLYNN_PATH, (
        "ItemId.ADAMANTITE_MACE.id()",
        "ItemId.TITAN_STEEL_MACE.id()",
        "ItemId.ORICHALCUM_MACE.id()",
        "ItemId.ADAMANTITE_KITE_SHIELD.id()",
        "ItemId.TITAN_STEEL_KITE_SHIELD.id()",
        "ItemId.ORICHALCUM_KITE_SHIELD.id()",
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
        "ItemId.BROWN_APRON.id()",
        "ItemId.GOBLIN_HIDE.id()",
        "ItemId.UNICORN_HIDE.id()",
        "ItemId.BEAR_HIDE.id()",
    ))
    ensure_contains(THESSALIA_PATH, (
        "Do you need tailoring supplies or an apron?",
        "Wool, thread, tools, hides, and brown aprons",
    ))
    ensure_not_contains(THESSALIA_PATH, (
        "ItemId.LEATHER_ARMOUR.id()",
        "ItemId.LEATHER_GLOVES.id()",
        "ItemId.WHITE_APRON.id()",
        "ItemId.PINK_SKIRT.id()",
        "ItemId.SILK.id()",
        "ItemId.PRIEST_ROBE.id()",
        "ItemId.PRIEST_GOWN.id()",
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

    ensure_contains(CRAFTING_EQUIPMENT_PATH, (
        "ItemId.BROWN_APRON.id()",
    ))

    for path in (MAGIC_STORE_PATH, BETTY_PATH, AUBURY_PATH, LUNDAIL_PATH):
        ensure_contains(path, (
            "1000",
            "ItemId.FIRE_RUNE.id()",
            "ItemId.WATER_RUNE.id()",
            "ItemId.AIR_RUNE.id()",
            "ItemId.EARTH_RUNE.id()",
            "ItemId.MIND_RUNE.id()",
            "ItemId.BODY_RUNE.id()",
        ))

    ensure_contains(AUBURY_PATH, (
        'command.equalsIgnoreCase("Trade") || command.equalsIgnoreCase("Shop")',
        "player.getConfig().RIGHT_CLICK_TRADE",
        "player.setAccessingShop(shop);",
        "ActionSender.showShop(player, shop);",
    ))
    ensure_contains(AUBURY_OPENPK_PATH, (
        'command.equalsIgnoreCase("Trade") || command.equalsIgnoreCase("Shop")',
        "ActionSender.showShop(player, shop);",
    ))
    ensure_contains(NPC_DEFS_PATH, (
        '"id": 54',
        '"name": "Aubury"',
        '"command": "Trade"',
        '"command2": "Shop"',
    ))

    for path in (ZAFF_PATH, MAGIC_STORE_PATH, BETTY_PATH):
        ensure_contains(path, (
            "ItemId.STAFF.id()",
            "ItemId.WOOL_WIZARD_HAT.id()",
            "ItemId.WOOL_ROBE_TOP.id()",
            "ItemId.WOOL_ROBE_SKIRT.id()",
        ))

    ensure_not_contains(ZAFF_PATH, (
        "ItemId.FIRE_RUNE.id()",
        "ItemId.WATER_RUNE.id()",
        "ItemId.AIR_RUNE.id()",
        "ItemId.EARTH_RUNE.id()",
        "ItemId.MIND_RUNE.id()",
        "ItemId.BODY_RUNE.id()",
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
        "MyWorldItemId.BANGEL_MOULD",
        "ItemId.HOLY_SYMBOL_MOULD.id()",
        "ItemId.UNHOLY_SYMBOL_MOULD.id()",
        "ItemId.GUTHIX_SYMBOL_MOULD.id()",
        "ItemId.BOLT_MOULD.id()",
        "ItemId.DART_MOULD.id()",
        "ItemId.THROWING_KNIFE_MOULD.id()",
        "ItemId.ARROWHEAD_MOULD.id()",
        "ItemId.SHURIKEN_MOULD.id()",
    ))
    if "WANT_CUSTOM_SPRITES" in text(CRAFTING_EQUIPMENT_PATH):
        fail("Crafting shop mould stock should not depend on custom sprite config")
    ensure_not_contains(CRAFTING_EQUIPMENT_PATH, (
        "ItemId.CROWN_MOULD.id()",
    ))

    ensure_contains(GARDENER_PATH, (
        "ItemId.SHEARS.id()",
        "ItemId.COPPER_SHEARS.id()",
        "ItemId.BRONZE_SHEARS.id()",
        "ItemId.IRON_SHEARS.id()",
        "ItemId.STEEL_SHEARS.id()",
        "ItemId.MITHRIL_SHEARS.id()",
        "ItemId.TIN_SCYTHE.id()",
        "ItemId.COPPER_SCYTHE.id()",
        "ItemId.BRONZE_SCYTHE.id()",
        "ItemId.IRON_SCYTHE.id()",
        "ItemId.STEEL_SCYTHE.id()",
        "ItemId.MITHRIL_SCYTHE.id()",
        "ItemId.WATERING_CAN.id()",
        "ItemId.SOIL.id()",
        "such as harvesting shears",
    ))
    ensure_not_contains(GARDENER_PATH, (
        "ItemId.FRUIT_PICKER.id()",
        "ItemId.HAND_SHOVEL.id()",
        "ItemId.HERB_CLIPPERS.id()",
        "fruit pickers or hand shovels",
    ))

    print("PASS: shop tiering and clothier restructuring validated")


if __name__ == "__main__":
    main()
