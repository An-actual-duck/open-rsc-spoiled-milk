#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NPC_DROPS_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "constants" / "NpcDrops.java"
DROP_TABLE_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "content" / "DropTable.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def require_absent(text: str, snippet: str, label: str) -> None:
    if snippet in text:
        fail(f"{label} still contains retired snippet: {snippet}")


def main() -> None:
    text = NPC_DROPS_PATH.read_text(encoding="utf-8")
    drop_table_text = DROP_TABLE_PATH.read_text(encoding="utf-8")

    required_snippets = (
        "currentNpcDrops.addItemDrop(ItemId.COPPER_SCIMITAR.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.COPPER_AXE.id(), 1, 3);",
        "currentNpcDrops.addItemDrop(ItemId.COPPER_SPEAR.id(), 1, 9);",
        "currentNpcDrops.addItemDrop(ItemId.COPPER_ARROWS.id(), 7, 3);",
        "currentNpcDrops.addItemDrop(ItemId.TIN_LARGE_HELMET.id(), 1, 2);",
        "currentNpcDrops.addItemDrop(ItemId.COPPER_DAGGER.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.LARGE_BRONZE_HELMET.id(), 1, 3);",
        "currentNpcDrops.addItemDrop(ItemId.COPPER_PLATE_MAIL_BODY.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.BRONZE_MACE.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.BRONZE_DAGGER.id(), 1, 2);",
        "currentNpcDrops.addItemDrop(ItemId.COPPER_KITE_SHIELD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.BRONZE_PLATE_MAIL_BODY.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.COPPER_LONG_SWORD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.TIN_SPEAR.id(), 1, 12);",
        "currentNpcDrops.addItemDrop(ItemId.TIN_KITE_SHIELD.id(), 1, 3);",
        "currentNpcDrops.addItemDrop(ItemId.COPPER_BOLTS.id(), 8, 3);",
        "currentNpcDrops.addItemDrop(ItemId.STEEL_SHORT_SWORD.id(), 1, 4);",
        "currentNpcDrops.addItemDrop(ItemId.LARGE_STEEL_HELMET.id(), 1, 2);",
        "currentNpcDrops.addItemDrop(ItemId.LONGBOW.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.IRON_SPEAR.id(), 1, 3);",
        "currentNpcDrops.addItemDrop(ItemId.STEEL_AXE.id(), 1, 6);",
        "currentNpcDrops.addItemDrop(ItemId.IRON_ARROWS.id(), 15, 3);",
        "currentNpcDrops.addItemDrop(ItemId.STEEL_PICKAXE.id(), 1, 13);",
        "currentNpcDrops.addItemDrop(ItemId.LARGE_IRON_HELMET.id(), 1, 4);",
        "currentNpcDrops.addItemDrop(ItemId.STEEL_BATTLE_AXE.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.IRON_SCIMITAR.id(), 1, 10);",
        "currentNpcDrops.addItemDrop(ItemId.BRONZE_PLATE_MAIL_BODY.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.LARGE_MITHRIL_HELMET.id(), 1, 2);",
        "currentNpcDrops.addItemDrop(ItemId.MITHRIL_KITE_SHIELD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.STEEL_SCIMITAR.id(), 1, 4);",
        "currentNpcDrops.addItemDrop(ItemId.STEEL_KITE_SHIELD.id(), 1, 2);",
        "currentNpcDrops.addItemDrop(ItemId.MITHRIL_LONG_SWORD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.MITHRIL_SHORT_SWORD.id(), 1, 2);",
        "currentNpcDrops.addItemDrop(ItemId.MITHRIL_KITE_SHIELD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.MITHRIL_SCIMITAR.id(), 1, 3);",
        "currentNpcDrops.addItemDrop(ItemId.LARGE_MITHRIL_HELMET.id(), 1, 4);",
        "currentNpcDrops.addItemDrop(ItemId.TITAN_STEEL_KITE_SHIELD.id(), 1, 5);",
        "currentNpcDrops.addItemDrop(ItemId.ADAMANTITE_2_HANDED_SWORD.id(), 1, 5);",
        "currentNpcDrops.addItemDrop(ItemId.ORICHALCUM_KITE_SHIELD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.ORICHALCUM_2_HANDED_SWORD.id(), 1, 4);",
        "currentNpcDrops.addItemDrop(ItemId.RUNE_PLATE_MAIL_LEGS.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.TITAN_STEEL_2_HANDED_SWORD.id(), 1, 4);",
        "currentNpcDrops.addItemDrop(ItemId.ORICHALCUM_PLATE_MAIL_BODY.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.TITAN_STEEL_PLATE_MAIL_LEGS.id(), 1, 4);",
        "currentNpcDrops.addItemDrop(ItemId.TITAN_STEEL_SPEAR.id(), 1, 2);",
        "currentNpcDrops.addItemDrop(ItemId.RUNE_PLATE_MAIL_BODY.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.RUNE_KITE_SHIELD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.LARGE_RUNE_HELMET.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.ORICHALCUM_LONG_SWORD.id(), 1, 1);",
        "runeDropTable.addItemDrop(ItemId.RUNE_SCIMITAR.id(), 1, 13);",
        "runeDropTable.addItemDrop(ItemId.RUNE_MACE.id(), 1, 6);",
        "arrowsRunesDropTable.addItemDrop(ItemId.TITAN_STEEL_ARROWS.id(), 50, 20);",
        "arrowsRunesDropTable.addItemDrop(ItemId.ORICHALCUM_ARROWS.id(), 40, 16);",
        "ultraRareDropTable.addItemDrop(ItemId.RUNE_SCIMITAR.id(), 1, 2);",
        "ultraRareDropTable.addItemDrop(ItemId.RUNE_ARROWS.id(), 150, 2);",
        "currentNpcDrops.addItemDrop(ItemId.IRON_ARROWS.id(), 15, 3);",
        "currentNpcDrops.addItemDrop(ItemId.WHITE_LONG_SWORD.id(), 1, 2);",
        "currentNpcDrops.addItemDrop(ItemId.LARGE_WHITE_HELMET.id(), 1, 2);",
        "currentNpcDrops.addItemDrop(ItemId.WHITE_KITE_SHIELD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.WHITE_PLATE_MAIL_BODY.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.IRON_ARROWS.id(), 8, 4);",
        "currentNpcDrops.addItemDrop(ItemId.LARGE_BLACK_HELMET.id(), 1, 3);",
        "currentNpcDrops.addItemDrop(ItemId.BLACK_MACE.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.BLACK_DAGGER.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.BLACK_SHORT_SWORD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.BLACK_LONG_SWORD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.BLACK_AXE.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.BLACK_KITE_SHIELD.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.BLACK_PLATE_MAIL_BODY.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.BLACK_PLATE_MAIL_LEGS.id(), 1, 1);",
    )
    for snippet in required_snippets:
        require(text, snippet, "NpcDrops.java")

    retired_snippets = (
        "currentNpcDrops.addItemDrop(ItemId.LEATHER_GLOVES.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.FUR.id(), 1, 1);",
        "currentNpcDrops.addItemDrop(ItemId.GREY_WOLF_FUR.id(), 1, 0);",
        "removeLegacyItemFromDropTables(ItemId.LEATHER_GLOVES.id());",
        "removeLegacyItemFromDropTables(ItemId.FUR.id());",
        "removeLegacyItemFromDropTables(ItemId.GREY_WOLF_FUR.id());",
    )
    for snippet in retired_snippets[:3]:
        require_absent(text, snippet, "NpcDrops.java")
    for snippet in retired_snippets[3:]:
        require(text, snippet, "NpcDrops.java")

    require(text, "if (!dropTable.hasItemDrop(itemId, 1, 0, false))", "NpcDrops.java")
    require(drop_table_text, "public boolean hasItemDrop(int itemId, int amount, int weight, boolean noted)", "DropTable.java")

    print("PASS: low-level NPC drop redistribution validated")


if __name__ == "__main__":
    main()
