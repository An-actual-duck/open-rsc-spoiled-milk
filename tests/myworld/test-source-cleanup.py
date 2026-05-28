#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
SPELL_HANDLER_PATH = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "net"
    / "rsc"
    / "handlers"
    / "SpellHandler.java"
)
DRAGONSTONE_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "misc"
    / "DragonstoneAmulet.java"
)
ITEM_DEFS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefs.json"
ITEM_DEFS_CUSTOM_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
CRAFTING_PATH = (
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
CRAFTING_DEF_PATH = ROOT / "server" / "conf" / "server" / "defs" / "extras" / "ItemCraftingDef.xml"
RETRO_CRAFTING_DEF_PATH = ROOT / "server" / "conf" / "server" / "defs" / "extras" / "retro" / "ItemCraftingDef.xml"
OBJECT_FISHING_PATH = ROOT / "server" / "conf" / "server" / "defs" / "extras" / "ObjectFishing.xml"
IMP_CATCHER_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "quests"
    / "free"
    / "ImpCatcher.java"
)
OBSERVATORY_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "quests"
    / "members"
    / "Observatory.java"
)
POINTS_STORE_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "npcs"
    / "PointsStore.java"
)
CERTER_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "Certer.java"
)
SIDNEY_SMITH_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "yanille"
    / "SidneySmith.java"
)
APOTHECARY_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "varrock"
    / "Apothecary.java"
)
BATTLESTAFF_CRAFTING_PATH = (
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
    / "BattlestaffCrafting.java"
)
MONK_OF_ENTRANA_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "portsarim"
    / "MonkOfEntrana.java"
)
THRANDER_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "varrock"
    / "Thrander.java"
)
THIEVING_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "skills"
    / "thieving"
    / "Thieving.java"
)
TAILOR_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "varrock"
    / "Tailor.java"
)
JIMINUA_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "shilo"
    / "Jiminua.java"
)
OBLI_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "shilo"
    / "Obli.java"
)
DOOR_ACTION_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "defaults"
    / "DoorAction.java"
)
BLACK_KNIGHTS_FORTRESS_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "quests"
    / "free"
    / "BlackKnightsFortress.java"
)
WITCHES_HOUSE_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "quests"
    / "members"
    / "WitchesHouse.java"
)
PRESENT_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "misc"
    / "Present.java"
)
HALLOWEEN_CRACKER_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "misc"
    / "HalloweenCracker.java"
)
ADMINS_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "commands"
    / "Admins.java"
)
DIGSITE_DIG_AREAS_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "quests"
    / "members"
    / "digsite"
    / "DigsiteDigAreas.java"
)
SCAVVO_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "varrock"
    / "Scavvo.java"
)
WATERFALL_QUEST_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "quests"
    / "members"
    / "Waterfall_Quest.java"
)
NPC_DROPS_PATH = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "constants"
    / "NpcDrops.java"
)
DROP_TABLE_PATH = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "content"
    / "DropTable.java"
)
WAYNES_CHAINS_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "npcs"
    / "falador"
    / "WaynesChains.java"
)
CHAMBER_GUARDIAN_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "authentic"
    / "minigames"
    / "mage_arena"
    / "Chamber_Guardian.java"
)


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require_snippet(path: Path, snippet: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def ensure_spell_redirects() -> None:
    spell_text = SPELL_HANDLER_PATH.read_text(encoding="utf-8")
    required_snippets = [
        'player.playerServerMessage(MessageType.QUEST, "Amulets are enchanted at elemental altars now.");',
        'player.playerServerMessage(MessageType.QUEST, "Rings are enchanted at altars now.");',
        'player.playerServerMessage(MessageType.QUEST, "Opal rings no longer have a separate enchantment path.");',
        'player.playerServerMessage(MessageType.QUEST, "Dragonstone amulets are enchanted at altars now.");',
        'player.playerServerMessage(MessageType.QUEST, "Necklaces are enchanted at altars now.");',
        'player.playerServerMessage(MessageType.QUEST, "Dragonstone jewelry is enchanted at altars now.");',
    ]
    for snippet in required_snippets:
        if snippet not in spell_text:
            fail(f"SpellHandler.java missing expected redirect: {snippet}")


def ensure_dragonstone_redirects() -> None:
    if DRAGONSTONE_PATH.exists():
        fail("DragonstoneAmulet.java should be removed now that the legacy dragonstone path is retired")


def ensure_legacy_jewelry_inert() -> None:
    base_items = json.loads(ITEM_DEFS_PATH.read_text(encoding="utf-8"))["item"]
    custom_items = json.loads(ITEM_DEFS_CUSTOM_PATH.read_text(encoding="utf-8"))["items"]
    all_items = {item["id"]: item for item in base_items}
    all_items.update({item["id"]: item for item in custom_items})

    retired_items = {
        522: "Dragonstone Amulet",
        597: "Charged Dragonstone Amulet",
        1320: "Dwarven ring",
    }
    for item_id, name in retired_items.items():
        item = all_items.get(item_id)
        if item is None:
            fail(f"Missing retired legacy jewelry item {item_id}")
        if item.get("isWearable") != 0:
            fail(f"{name} should be inert and no longer wearable")
        if item.get("wearSlot") != -1:
            fail(f"{name} should not occupy a wearable slot")
        if item.get("isUntradable") != 1:
            fail(f"{name} should be retained only as untradable compatibility baggage")


def ensure_crowns_hidden_from_crafting() -> None:
    crafting_text = CRAFTING_PATH.read_text(encoding="utf-8")
    crafting_def_text = CRAFTING_DEF_PATH.read_text(encoding="utf-8")
    retro_crafting_def_text = RETRO_CRAFTING_DEF_PATH.read_text(encoding="utf-8")
    hidden_crown_options = """\t\t\toptions = new String[]{
\t\t\t\tring,
\t\t\t\tNecklace,
\t\t\t\tamulet
\t\t\t};"""
    if hidden_crown_options not in crafting_text:
        fail("Crafting.java should hide crown production from the gold jewelry menu")
    for item_id in ("1503", "1504", "1505", "1506", "1507", "1508"):
        snippet = f"<itemID>{item_id}</itemID>"
        if snippet in crafting_def_text:
            fail(f"ItemCraftingDef.xml should not define retired crown production: {snippet}")
        if snippet in retro_crafting_def_text:
            fail(f"retro/ItemCraftingDef.xml should not define retired crown production: {snippet}")


def ensure_opal_ring_retired_from_crafting() -> None:
    crafting_text = CRAFTING_PATH.read_text(encoding="utf-8")
    crafting_def_text = CRAFTING_DEF_PATH.read_text(encoding="utf-8")
    if "private static final String Opal" in crafting_text:
        fail("Crafting.java should not expose Opal as a gold-jewelry menu option")
    if "ItemId.OPAL_RING" in crafting_text:
        fail("Crafting.java should not produce opal rings")
    if "<itemID>1321</itemID>" in crafting_def_text:
        fail("ItemCraftingDef.xml should not define opal ring production")


def ensure_remaining_holdovers_documented_in_code() -> None:
    require_snippet(
        IMP_CATCHER_PATH,
        "give(player, ItemId.AMULET_OF_ACCURACY.id(), 1);",
        "ImpCatcher.java",
    )
    require_snippet(
        OBSERVATORY_PATH,
        "give(player, ItemId.EMERALD_AMULET.id(), 1);",
        "Observatory.java",
    )


def ensure_phase_one_content_cleanup() -> None:
    points_store_text = POINTS_STORE_PATH.read_text(encoding="utf-8")
    certer_text = CERTER_PATH.read_text(encoding="utf-8")
    sidney_text = SIDNEY_SMITH_PATH.read_text(encoding="utf-8")
    apothecary_text = APOTHECARY_PATH.read_text(encoding="utf-8")
    battlestaff_text = BATTLESTAFF_CRAFTING_PATH.read_text(encoding="utf-8")
    monk_of_entrana_text = MONK_OF_ENTRANA_PATH.read_text(encoding="utf-8")
    thrander_text = THRANDER_PATH.read_text(encoding="utf-8")
    thieving_text = THIEVING_PATH.read_text(encoding="utf-8")
    tailor_text = TAILOR_PATH.read_text(encoding="utf-8")
    jiminua_text = JIMINUA_PATH.read_text(encoding="utf-8")
    obli_text = OBLI_PATH.read_text(encoding="utf-8")
    door_action_text = DOOR_ACTION_PATH.read_text(encoding="utf-8")
    black_knights_text = BLACK_KNIGHTS_FORTRESS_PATH.read_text(encoding="utf-8")
    witches_house_text = WITCHES_HOUSE_PATH.read_text(encoding="utf-8")
    present_text = PRESENT_PATH.read_text(encoding="utf-8")
    halloween_cracker_text = HALLOWEEN_CRACKER_PATH.read_text(encoding="utf-8")
    digsite_text = DIGSITE_DIG_AREAS_PATH.read_text(encoding="utf-8")
    scavvo_text = SCAVVO_PATH.read_text(encoding="utf-8")
    waterfall_text = WATERFALL_QUEST_PATH.read_text(encoding="utf-8")
    npc_drops_text = NPC_DROPS_PATH.read_text(encoding="utf-8")
    drop_table_text = DROP_TABLE_PATH.read_text(encoding="utf-8")
    waynes_chains_text = WAYNES_CHAINS_PATH.read_text(encoding="utf-8")
    chamber_guardian_text = CHAMBER_GUARDIAN_PATH.read_text(encoding="utf-8")
    admins_text = ADMINS_PATH.read_text(encoding="utf-8")
    object_fishing_text = OBJECT_FISHING_PATH.read_text(encoding="utf-8")

    removed_shop_potions = (
        "ItemId.FULL_SUPER_ATTACK_POTION.id()",
        "ItemId.FULL_SUPER_STRENGTH_POTION.id()",
        "ItemId.FULL_SUPER_DEFENSE_POTION.id()",
        "ItemId.FULL_RESTORE_PRAYER_POTION.id()",
        "ItemId.FULL_RANGING_POTION.id()",
        "ItemId.FULL_CURE_POISON_POTION.id()",
    )
    for snippet in removed_shop_potions:
        if snippet in points_store_text:
            fail(f"PointsStore should no longer sell retired potion inflow: {snippet}")

    removed_cert_items = (
        "ItemId.FULL_SUPER_ATTACK_POTION.id()",
        "ItemId.FULL_SUPER_STRENGTH_POTION.id()",
        "ItemId.FULL_SUPER_DEFENSE_POTION.id()",
        "ItemId.FULL_RESTORE_PRAYER_POTION.id()",
        "ItemId.FULL_STAT_RESTORATION_POTION.id()",
        "ItemId.FULL_CURE_POISON_POTION.id()",
        "ItemId.FULL_POISON_ANTIDOTE.id()",
        "ItemId.SUPER_ATTACK_POTION_CERTIFICATE.id()",
        "ItemId.SUPER_DEFENSE_POTION_CERTIFICATE.id()",
        "ItemId.SUPER_STRENGTH_POTION_CERTIFICATE.id()",
        "ItemId.PRAYER_POTION_CERTIFICATE.id()",
        "ItemId.STAT_RESTORATION_POTION_CERTIFICATE.id()",
        "ItemId.POISON_ANTIDOTE_CERTIFICATE.id()",
        "ItemId.CURE_POISON_POTION_CERTIFICATE.id()",
    )
    for snippet in removed_cert_items:
        if snippet in certer_text:
            fail(f"Certer should no longer expose retired potion certification: {snippet}")
    npc_certers_text = (ROOT / "server" / "conf" / "server" / "defs" / "extras" / "NpcCerters.xml").read_text(encoding="utf-8")
    cert_util_text = (ROOT / "server" / "src" / "com" / "openrsc" / "server" / "util" / "rsc" / "CertUtil.java").read_text(encoding="utf-8")
    bank_text = (ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "container" / "Bank.java").read_text(encoding="utf-8")
    custom_certer_text = (ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "npcs" / "CustomCerter.java").read_text(encoding="utf-8")
    removed_potion_cert_support = (
        "<type>potion</type>",
        "<certID>1272</certID>",
        "<certID>1273</certID>",
        "<certID>1274</certID>",
        "<certID>1275</certID>",
        "ItemId.PRAYER_POTION_CERTIFICATE.id()",
        "ItemId.SUPER_ATTACK_POTION_CERTIFICATE.id()",
        "ItemId.SUPER_DEFENSE_POTION_CERTIFICATE.id()",
        "ItemId.SUPER_STRENGTH_POTION_CERTIFICATE.id()",
        "stat restoration potions",
        "cure poison potions",
        "poison antidotes",
    )
    for snippet in removed_potion_cert_support:
        if snippet in npc_certers_text:
            fail(f"NpcCerters.xml should not keep retired potion certification support: {snippet}")
        if snippet in cert_util_text:
            fail(f"CertUtil should not keep retired potion certification support: {snippet}")
        if snippet in bank_text:
            fail(f"Bank should not remap retired potion certificates: {snippet}")
        if snippet in custom_certer_text:
            fail(f"CustomCerter should not advertise retired potion certification support: {snippet}")

    removed_sidney_potions = (
        "PRAYER_RESTORE_POT",
        "SUPER_ATTACK_POT",
        "SUPER_STRENGTH_POT",
        "SUPER_DEFENSE_POT",
        "PRAYER_CERT",
        "SUPER_ATTACK_CERT",
        "SUPER_DEFENSE_CERT",
        "SUPER_STRENGTH_CERT",
        "Prayer Restore Potion,",
        "Super Attack Potion,",
        "Super Defense Potion,",
        "Super Strength Potion,",
    )
    for snippet in removed_sidney_potions:
        if snippet in sidney_text:
            fail(f"Sidney Smith should no longer trade retired potion lines: {snippet}")

    removed_apothecary_strength_path = (
        'options.add("Can you make a strength potion?");',
        "private void batchPotion(Player player)",
        'player.message("The Apothecary gives you a strength potion");',
    )
    for snippet in removed_apothecary_strength_path:
        if snippet in apothecary_text:
            fail(f"Apothecary should no longer offer the legacy strength-potion source: {snippet}")

    retired_battlestaff_production_snippets = (
        "enum Battlestaff",
        "resultItemString",
        "BATTLESTAFF_OF_WATER.id()",
        "BATTLESTAFF_OF_EARTH.id()",
        "BATTLESTAFF_OF_FIRE.id()",
        "BATTLESTAFF_OF_AIR.id()",
    )
    for snippet in retired_battlestaff_production_snippets:
        if snippet in battlestaff_text:
            fail(f"BattlestaffCrafting should no longer advertise live battlestaff outputs: {snippet}")

    required_battlestaff_retirement_snippets = (
        "private static final int[][] RETIRED_COMBINATIONS = {",
        "Battlestaff crafting has been retired. Use a staff directly on an altar through Enchanting instead.",
    )
    for snippet in required_battlestaff_retirement_snippets:
        if snippet not in battlestaff_text:
            fail(f"BattlestaffCrafting should remain a minimal retirement blocker: {snippet}")

    tailoring_material_stock = (
        "ItemId.SHEARS.id()",
        "ItemId.NEEDLE.id()",
        "ItemId.WOOL.id()",
        "ItemId.BALL_OF_WOOL.id()",
        "ItemId.THREAD.id()",
        "ItemId.COW_HIDE.id()",
        "ItemId.GOBLIN_HIDE.id()",
        "ItemId.UNICORN_HIDE.id()",
        "ItemId.BEAR_HIDE.id()",
    )
    for snippet in tailoring_material_stock:
        if snippet not in tailor_text:
            fail(f"Tailor should stock tailoring materials and tools: {snippet}")

    retired_tailor_clothing = (
        "ItemId.CHEFS_HAT.id()",
        "ItemId.PRIEST_ROBE.id()",
        "ItemId.PRIEST_GOWN.id()",
        "ItemId.PINK_SKIRT.id()",
        "ItemId.LEATHER.id()",
        "ItemId.COW_HIDE_CUIRASS.id()",
        "ItemId.GOBLIN_HIDE_CUIRASS.id()",
        "ItemId.UNICORN_HIDE_CUIRASS.id()",
        "ItemId.BEAR_HIDE_CUIRASS.id()",
    )
    for snippet in retired_tailor_clothing:
        if snippet in tailor_text:
            fail(f"Tailor should no longer stock retired generic or quest clothing: {snippet}")

    removed_general_store_leather = (
        "ItemId.LEATHER_ARMOUR.id()",
        "ItemId.LEATHER_GLOVES.id()",
    )
    for label, text in (("Jiminua", jiminua_text), ("Obli", obli_text)):
        for snippet in removed_general_store_leather:
            if snippet in text:
                fail(f"{label} should no longer sell legacy leather equipment as generic store stock: {snippet}")

    removed_drop_lines = (
        "currentNpcDrops.addItemDrop(ItemId.MAGIC_STAFF.id(), 1, 2);",
        "currentNpcDrops.addItemDrop(ItemId.FULL_CURE_POISON_POTION.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.FULL_POISON_ANTIDOTE.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.WEAPON_POISON.id(), 1, 1);",
    )
    for snippet in removed_drop_lines:
        if snippet in npc_drops_text:
            fail(f"NpcDrops should no longer contain retired phase-one inflow: {snippet}")

    required_drop_cleanup_snippets = (
        "removeLegacyItemFromDropTables(ItemId.BRONZE_PLATED_SKIRT.id());",
        "removeLegacyItemFromDropTables(ItemId.IRON_PLATED_SKIRT.id());",
        "removeLegacyItemFromDropTables(ItemId.STEEL_PLATED_SKIRT.id());",
        "removeLegacyItemFromDropTables(ItemId.BLACK_PLATED_SKIRT.id());",
        "removeLegacyItemFromDropTables(ItemId.MITHRIL_PLATED_SKIRT.id());",
        "removeLegacyItemFromDropTables(ItemId.ADAMANTITE_PLATED_SKIRT.id());",
        "removeLegacyItemFromDropTables(ItemId.RUNE_SKIRT.id());",
    )
    for snippet in required_drop_cleanup_snippets:
        if snippet not in npc_drops_text:
            fail(f"NpcDrops should retire the authored MyWorld drop outliers centrally: {snippet}")

    retired_present_snippets = (
        "ItemId.BRONZE_PLATED_SKIRT.id()",
        "ItemId.IRON_PLATED_SKIRT.id()",
        "ItemId.STEEL_PLATED_SKIRT.id()",
        "ItemId.BLACK_AXE.id()",
        "ItemId.BLACK_LONG_SWORD.id()",
        "ItemId.BLACK_PLATE_MAIL_BODY.id()",
        "ItemId.BLACK_PLATED_SKIRT.id()",
        "ItemId.MITHRIL_PLATED_SKIRT.id()",
        "ItemId.ADAMANTITE_PLATED_SKIRT.id()",
        "ItemId.RUNE_SKIRT.id()",
        "cabbagePresentDrops.addTableDrop(blackTable, 12);",
    )
    for snippet in retired_present_snippets:
        if snippet in present_text:
            fail(f"Present.java should not keep retired skirt or black-tier standard stock: {snippet}")

    retired_halloween_cracker_snippets = (
        "ItemId.BLACK_PLATED_SKIRT.id()",
        "ItemId.RUNE_SKIRT.id()",
    )
    for snippet in retired_halloween_cracker_snippets:
        if snippet in halloween_cracker_text:
            fail(f"HalloweenCracker.java should not keep retired skirt rewards: {snippet}")

    required_digsite_snippets = (
        "ItemId.LARGE_BLACK_HELMET.id()",
        "You find a black helmet",
    )
    for snippet in required_digsite_snippets:
        if snippet not in digsite_text:
            fail(f"DigsiteDigAreas.java should use the active black helmet variant: {snippet}")

    retired_digsite_snippets = (
        "ItemId.MEDIUM_BLACK_HELMET.id()",
        "You find an black helmet",
    )
    for snippet in retired_digsite_snippets:
        if snippet in digsite_text:
            fail(f"DigsiteDigAreas.java should not keep the retired black helmet variant: {snippet}")

    if "ItemId.RUNE_SKIRT.id()" in scavvo_text:
        fail("Scavvo.java should not stock the retired rune skirt")

    if 'item.getCatalogId() == ItemId.RUNE_SKIRT.id()' in waterfall_text:
        fail("Waterfall_Quest.java should not special-case the retired rune skirt")

    retired_rare_normal_drop_snippets = (
        "ItemId.MEDIUM_RUNE_HELMET.id()",
        "ItemId.RUNE_CHAIN_MAIL_BODY.id()",
        "ItemId.RUNE_SQUARE_SHIELD.id()",
        "ItemId.RUNE_SKIRT.id()",
    )
    for snippet in retired_rare_normal_drop_snippets:
        if snippet in drop_table_text:
            fail(f"DropTable.java should not treat retired standard families as rare normal drops: {snippet}")

    if 'Welcome to Wayne\'s chains' in waynes_chains_text or 'chain mail' in waynes_chains_text:
        fail("WaynesChains.java should no longer advertise chain mail")

    if 'magic staff' in chamber_guardian_text:
        fail("Chamber_Guardian.java should use current god-staff wording")

    required_black_knight_snippets = (
        "ItemId.IRON_PLATE_MAIL_BODY.id()",
        "ItemId.IRON_PLATE_MAIL_TOP.id()",
        "ItemId.LARGE_BRONZE_HELMET.id()",
        "It's an iron platebody and a large bronze helmet",
    )
    for snippet in required_black_knight_snippets:
        if snippet not in black_knights_text:
            fail(f"BlackKnightsFortress.java should use the active disguise requirement: {snippet}")

    retired_black_knight_snippets = (
        "ItemId.IRON_CHAIN_MAIL_BODY.id()",
        "ItemId.IRON_CHAIN_MAIL_TOP.id()",
        "ItemId.MEDIUM_BRONZE_HELMET.id()",
        "It's iron chain mail and a medium bronze helmet",
    )
    for snippet in retired_black_knight_snippets:
        if snippet in black_knights_text:
            fail(f"BlackKnightsFortress.java should not keep the retired disguise requirement: {snippet}")

    required_door_action_snippets = (
        "ItemId.IRON_PLATE_MAIL_BODY.id()",
        "ItemId.LARGE_BRONZE_HELMET.id()",
    )
    for snippet in required_door_action_snippets:
        if snippet not in door_action_text:
            fail(f"DoorAction.java should use the active Black Knights disguise requirement: {snippet}")

    retired_door_action_snippets = (
        "ItemId.IRON_CHAIN_MAIL_BODY.id()",
        "ItemId.MEDIUM_BRONZE_HELMET.id()",
    )
    for snippet in retired_door_action_snippets:
        if snippet in door_action_text:
            fail(f"DoorAction.java should not use retired Black Knights disguise gear: {snippet}")

    retired_entrana_snippets = (
        "ItemId.BRONZE_CHAIN_MAIL_BODY.id()",
        "ItemId.IRON_CHAIN_MAIL_BODY.id()",
        "ItemId.MEDIUM_BRONZE_HELMET.id()",
        "ItemId.BRONZE_PLATED_SKIRT.id()",
        "ItemId.BRONZE_SQUARE_SHIELD.id()",
    )
    for snippet in retired_entrana_snippets:
        if snippet in monk_of_entrana_text:
            fail(f"MonkOfEntrana.java should not enumerate retired standard families: {snippet}")

    required_entrana_exception_snippets = (
        "ItemId.DRAGON_MEDIUM_HELMET.id()",
        "ItemId.DRAGON_SQUARE_SHIELD.id()",
    )
    for snippet in required_entrana_exception_snippets:
        if snippet not in monk_of_entrana_text:
            fail(f"MonkOfEntrana.java should keep explicit dragon exceptions: {snippet}")

    required_entrana_hide_armour_snippets = (
        "ItemId.COW_HIDE_CUIRASS.id()",
        "ItemId.GOBLIN_HIDE_CUIRASS.id()",
        "ItemId.UNICORN_HIDE_CUIRASS.id()",
        "ItemId.BEAR_HIDE_CUIRASS.id()",
        "ItemId.DRAGON_CUIRASS.id()",
        "ItemId.KING_BLACK_DRAGON_CUIRASS.id()",
    )
    for snippet in required_entrana_hide_armour_snippets:
        if snippet not in monk_of_entrana_text:
            fail(f"MonkOfEntrana.java should block active hide cuirasses on Entrana: {snippet}")

    retired_thrander_snippets = (
        "implements TalkNpcTrigger, UseNpcTrigger",
        "blockUseNpc",
        "onUseNpc",
        "getNewId",
        "isExchangeable",
        "isDragon",
        "case BRONZE_PLATE_MAIL_LEGS:",
        "case BRONZE_CHAIN_MAIL_TOP:",
        "case BRONZE_CHAIN_MAIL_BODY:",
        "isChainmail = Functions.inArray",
    )
    for snippet in retired_thrander_snippets:
        if snippet in thrander_text:
            fail(f"Thrander.java should no longer provide legacy armor conversion: {snippet}")

    required_thrander_shop_snippets = (
        "public class Thrander extends AbstractShop",
        "ItemId.TIN_PLATE_MAIL_BODY.id()",
        "ItemId.COPPER_PLATE_MAIL_BODY.id()",
        "ItemId.BRONZE_PLATE_MAIL_BODY.id()",
        "ItemId.IRON_PLATE_MAIL_BODY.id()",
        "ItemId.STEEL_PLATE_MAIL_BODY.id()",
    )
    for snippet in required_thrander_shop_snippets:
        if snippet not in thrander_text:
            fail(f"Thrander.java should now be a low-tier armor shop: {snippet}")

    retired_thieving_loot_snippets = (
        "new LootItem(ItemId.LEATHER_GLOVES.id(), 1, 10)",
        "new LootItem(ItemId.GREY_WOLF_FUR.id(), 1, 100)",
    )
    for snippet in retired_thieving_loot_snippets:
        if snippet in thieving_text:
            fail(f"Thieving.java should no longer award retired leather or fur loot: {snippet}")

    required_thieving_replacement_snippets = (
        "new LootItem(ItemId.COW_HIDE_GLOVES.id(), 1, 10)",
        "new LootItem(ItemId.BEAR_HIDE.id(), 1, 70)",
        "new LootItem(ItemId.UNICORN_HIDE.id(), 1, 30)",
    )
    for snippet in required_thieving_replacement_snippets:
        if snippet not in thieving_text:
            fail(f"Thieving.java should route live MyWorld replacements into those slots: {snippet}")

    retired_big_net_glove_snippets = (
        "<fishId>16</fishId> <!-- gloves -->",
    )
    for snippet in retired_big_net_glove_snippets:
        if snippet in object_fishing_text:
            fail(f"ObjectFishing.xml should no longer award legacy leather gloves through big-net fishing: {snippet}")

    required_big_net_glove_snippets = (
        "<fishId>1836</fishId> <!-- cow hide gloves -->",
    )
    for snippet in required_big_net_glove_snippets:
        if snippet not in object_fishing_text:
            fail(f"ObjectFishing.xml should award cow-hide gloves instead of legacy leather gloves: {snippet}")

    retired_witches_house_snippets = (
        "ItemId.BRONZE_CHAIN_MAIL_BODY.id()",
        "ItemId.BRONZE_CHAIN_MAIL_LEGS.id()",
        "ItemId.BRONZE_PLATED_SKIRT.id()",
        "ItemId.MEDIUM_BRONZE_HELMET.id()",
        "ItemId.BRONZE_SQUARE_SHIELD.id()",
    )
    for snippet in retired_witches_house_snippets:
        if snippet in witches_house_text:
            fail(f"WitchesHouse.java should not classify retired standard metal families as active metal armour: {snippet}")

    required_witches_house_exception_snippets = (
        "ItemId.DRAGON_SCALE_MAIL.id()",
        "ItemId.DRAGON_MEDIUM_HELMET.id()",
        "ItemId.DRAGON_SQUARE_SHIELD.id()",
    )
    for snippet in required_witches_house_exception_snippets:
        if snippet not in witches_house_text:
            fail(f"WitchesHouse.java should keep explicit dragon exceptions in its metal-armour check: {snippet}")

    witches_house_glove_support = (
        "ItemId.LEATHER_GLOVES.id()",
        "ItemId.COW_HIDE_GLOVES.id()",
        "ItemId.ICE_GLOVES.id()",
    )
    for snippet in witches_house_glove_support:
        if snippet not in witches_house_text:
            fail(f"WitchesHouse.java should accept both legacy and cow-hide gloves for compatibility: {snippet}")

    retired_admin_bis_snippets = (
        "new Item(ItemId.RUNE_SKIRT.id())",
        "new Item(ItemId.ADAMANTITE_PLATED_SKIRT.id())",
    )
    for snippet in retired_admin_bis_snippets:
        if snippet in admins_text:
            fail(f"Admins.java should not reintroduce retired plated skirts through the best-in-slot kit: {snippet}")


def main() -> None:
    ensure_spell_redirects()
    ensure_dragonstone_redirects()
    ensure_legacy_jewelry_inert()
    ensure_crowns_hidden_from_crafting()
    ensure_opal_ring_retired_from_crafting()
    ensure_remaining_holdovers_documented_in_code()
    ensure_phase_one_content_cleanup()
    print("PASS: source cleanup redirects and holdovers validated")


if __name__ == "__main__":
    main()
