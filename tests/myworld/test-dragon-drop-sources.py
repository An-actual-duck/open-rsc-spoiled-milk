#!/usr/bin/env python3
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
NPC_DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"
PRESENT = ROOT / "server/plugins/com/openrsc/server/plugins/custom/misc/Present.java"
HALLOWEEN_CRACKER = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/HalloweenCracker.java"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, needle: str, message: str) -> None:
    if needle not in text:
        fail(message)


def forbid(text: str, needle: str, message: str) -> None:
    if needle in text:
        fail(message)


def between(text: str, start: str, end: str, message: str) -> str:
    if start not in text:
        fail(f"Missing start marker for {message}: {start}")
    tail = text.split(start, 1)[1]
    if end not in tail:
        fail(f"Missing end marker for {message}: {end}")
    return tail.split(end, 1)[0]


def main() -> None:
    drops = NPC_DROPS.read_text(encoding="utf-8")
    present = PRESENT.read_text(encoding="utf-8")
    halloween_cracker = HALLOWEEN_CRACKER.read_text(encoding="utf-8")
    elder_green_dragon = between(
        drops,
        "private void createElderGreenDragonDropTable() {",
        "\n\tprivate void addNormalDrop",
        "elder green dragon drop table",
    )
    for item in (
        "DRAGON_SWORD",
        "DRAGON_AXE",
        "DRAGON_2_HANDED_SWORD",
        "DRAGON_DAGGER",
        "POISONED_DRAGON_DAGGER",
        "DRAGON_BATTLE_AXE",
        "DRAGON_CROSSBOW",
        "DRAGON_LONGBOW",
        "DRAGON_ARROWS",
        "POISON_DRAGON_ARROWS",
        "DRAGON_BOLTS",
        "POISON_DRAGON_BOLTS",
    ):
        forbid(
            drops,
            f"addItemDrop(ItemId.{item}.id()",
            f"{item} should not be a direct NPC item drop",
        )

    shared_ultra = between(
        drops,
        "private void createUltraRareDropTable() {",
        "\n\tprivate DropTable createBlackDemonUltraRareDropTable()",
        "shared ultra rare drop table",
    )
    forbid(shared_ultra, "DRAGON_MEDIUM_HELMET", "Shared ultra rare table should not drop dragon medium helm")
    forbid(shared_ultra, "LARGE_DRAGON_HELMET", "Shared ultra rare table should not drop dragon full helm")

    forbid(drops, "dragonDropTable", "Retired OpenPK dragon drop table should stay removed")

    black_demon_ultra = between(
        drops,
        "private DropTable createBlackDemonUltraRareDropTable() {",
        "\n\tprivate void createBoneDrops()",
        "black demon ultra rare drop table",
    )
    forbid(black_demon_ultra, "DRAGON_MEDIUM_HELMET", "Black Demon ultra rare table should not directly drop dragon medium helm")
    forbid(black_demon_ultra, "LARGE_DRAGON_HELMET", "Black Demon ultra rare table should not directly drop dragon full helm")

    black_demon = between(
        drops,
        'currentNpcDrops = new DropTable("Black Demon (290)")',
        "this.npcDrops.put(NpcId.BLACK_DEMON.id(), currentNpcDrops);",
        "black demon drop table",
    )
    require(
        black_demon,
        "currentNpcDrops.addTableDrop(createBlackDemonUltraRareDropTable(), 1);",
        "Black Demon should use its dragon-helm-specific ultra rare table",
    )

    kbd = between(
        drops,
        'currentNpcDrops = new DropTable("King Black Dragon (477)")',
        "this.npcDrops.put(NpcId.KING_BLACK_DRAGON.id(), currentNpcDrops);",
        "king black dragon drop table",
    )
    forbid(kbd, "DRAGON_MEDIUM_HELMET", "KBD should not directly drop dragon medium helm")
    forbid(kbd, "LARGE_DRAGON_HELMET", "KBD should not directly drop dragon full helm")

    kbd_custom = between(
        drops,
        "private void initializeCustomRareDropTables()",
        "\n\tprivate void createCustomQuestDrops()",
        "custom KBD rare drop table",
    )
    forbid(kbd_custom, "DRAGON_2_HANDED_SWORD", "KBD custom rare table should not drop dragon weapons")
    forbid(kbd_custom, "KING_BLACK_DRAGON_SCALE", "KBD custom rare table should not drop black dragon scales")
    forbid(kbd_custom, "RAW_DRAGON_METAL", "KBD custom rare table should not drop dragon metal scrap")

    hidden_unique = between(
        drops,
        "private void createHiddenUniqueDrops()",
        "\n\tprivate void addHiddenUniqueDrop(final int npcId, final int itemId, final int amount, final HiddenUniqueRarity rarity)",
        "hidden unique drop registry",
    )
    forbid(
        hidden_unique,
        "addHiddenUniqueDrop(NpcId.BLACK_DEMON.id(), ItemId.DRAGON_MEDIUM_HELMET.id(), 1, HiddenUniqueRarity.VERY_RARE_UNIQUE);",
        "Black Demon should not drop dragon medium helm through the hidden unique layer",
    )
    require(
        hidden_unique,
        "addHiddenUniqueDrop(NpcId.BLACK_DEMON.id(), ItemId.LARGE_DRAGON_HELMET.id(), 1, HiddenUniqueRarity.VERY_RARE_UNIQUE);",
        "Black Demon should drop dragon full helm through the hidden unique layer",
    )
    for needle, message in (
        ("addHiddenUniqueDrop(NpcId.ELDER_GREEN_DRAGON.id(), ItemId.RAW_DRAGON_METAL.id(), 1, 1, 64);", "Elder Green Dragon should drop dragon metal scrap at 1/64"),
        ("addHiddenUniqueDrop(NpcId.KING_BLACK_DRAGON.id(), ItemId.RAW_DRAGON_METAL.id(), 1, 1, 64);", "King Black Dragon should drop dragon metal scrap at 1/64"),
        ("addHiddenUniqueDrop(NpcId.BLACK_DRAGON.id(), ItemId.RAW_DRAGON_METAL.id(), 1, 1, 128);", "Black Dragon should drop dragon metal scrap at 1/128"),
        ("addHiddenUniqueDrop(NpcId.RED_DRAGON.id(), ItemId.RAW_DRAGON_METAL.id(), 1, 1, 512);", "Red Dragon should drop dragon metal scrap at 1/512"),
		("addHiddenUniqueDrop(NpcId.GREEN_DRAGON.id(), ItemId.RAW_DRAGON_METAL.id(), 1, 1, 1024);", "Ordinary Green Dragon should drop dragon metal scrap at 1/1024"),
        ("addHiddenUniqueDrop(NpcId.BLUE_DRAGON.id(), ItemId.RAW_DRAGON_METAL.id(), 1, 1, 1024);", "Blue Dragon should drop dragon metal scrap at 1/1024"),
    ):
        require(hidden_unique, needle, message)
    for reward_source, source_name in (
        (present, "Present"),
        (halloween_cracker, "Halloween Cracker"),
    ):
        forbid(
            reward_source,
            "ItemId.DRAGON_MEDIUM_HELMET.id()",
            f"{source_name} should not award dragon medium helm",
        )
        require(
            reward_source,
            "ItemId.LARGE_DRAGON_HELMET.id()",
            f"{source_name} should award dragon full helm instead of dragon medium helm",
        )
    require(
        hidden_unique,
        "addHiddenUniqueDrop(NpcId.BLACK_DRAGON.id(), ItemId.DRAGON_SQUARE_SHIELD.id(), 1, HiddenUniqueRarity.VERY_RARE_UNIQUE);",
        "Black Dragon should drop dragon square shield through the hidden unique layer",
    )
    require(
        hidden_unique,
        "addHiddenUniqueDrop(NpcId.KING_BLACK_DRAGON.id(), ItemId.DRAGON_SQUARE_SHIELD.id(), 1, HiddenUniqueRarity.VERY_RARE_UNIQUE);",
        "King Black Dragon should drop dragon square shield through the hidden unique layer",
    )
    require(
        drops,
        'addGuaranteedDrop(NpcId.KING_BLACK_DRAGON.id(), ItemId.BLACK_DRAGON_HIDE.id(), "King Black Dragon black dragon hide");',
        "King Black Dragon should retain its ordinary black dragon hide drop",
    )
    require(
        drops,
        'addGuaranteedDrop(NpcId.KING_BLACK_DRAGON.id(), MyWorldItemId.KING_BLACK_DRAGON_HIDE, "King Black Dragon hide");',
        "King Black Dragon should drop its purpose-specific tier-10 hide",
    )
    require(
        drops,
        'addGuaranteedDrop(NpcId.BLACK_DRAGON.id(), ItemId.KING_BLACK_DRAGON_SCALE.id(), "Black dragon scale");',
        "Black Dragon should drop one black dragon scale",
    )
    require(
        drops,
        'addGuaranteedDrop(NpcId.KING_BLACK_DRAGON.id(), ItemId.KING_BLACK_DRAGON_SCALE.id(), 2, "King Black Dragon black dragon scale");',
        "King Black Dragon should drop two black dragon scales",
    )
    require(
        drops,
        'addGuaranteedDrop(NpcId.ELDER_GREEN_DRAGON.id(), ItemId.ELDER_GREEN_DRAGON_HIDE.id(), "Elder Green Dragon hide");',
        "Elder Green Dragon should own the former special hide drop",
    )
    require(
        drops,
        "this.dragonNpcs.add(NpcId.ELDER_GREEN_DRAGON.id());",
        "Elder Green Dragon should be in the dragon-bone list",
    )
    for needle, message in (
        ("commonDrops.addItemDrop(ItemId.CHAOS_RUNE.id(), 200, 1);", "Elder Green Dragon common drops should include 200 chaos runes"),
        ("commonDrops.addItemDrop(ItemId.DEATH_RUNE.id(), 50, 1);", "Elder Green Dragon common drops should include 50 death runes"),
        ("commonDrops.addItemDrop(ItemId.MITHRIL_ARROWS.id(), 200, 1);", "Elder Green Dragon common drops should include mithril arrows"),
        ("commonDrops.addItemDrop(ItemId.MITHRIL_BOLTS.id(), 200, 1);", "Elder Green Dragon common drops should include mithril bolts"),
        ("commonDrops.addItemDrop(ItemId.MITHRIL_THROWING_DART.id(), 200, 1);", "Elder Green Dragon common drops should include mithril darts"),
        ("commonDrops.addItemDrop(ItemId.MITHRIL_THROWING_KNIFE.id(), 200, 1);", "Elder Green Dragon common drops should include mithril knives"),
        ("commonDrops.addItemDrop(ItemId.MITHRIL_SHURIKEN.id(), 200, 1);", "Elder Green Dragon common drops should include mithril shuriken"),
        ("commonDrops.addItemDrop(ItemId.EBONY_LONGBOW.id(), 1, 1);", "Elder Green Dragon common drops should include an ebony bow"),
        ("commonDrops.addItemDrop(ItemId.EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include an ebony staff"),
        ("commonDrops.addItemDrop(ItemId.MIND_EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include mind ebony staff"),
        ("commonDrops.addItemDrop(ItemId.BODY_EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include body ebony staff"),
        ("commonDrops.addItemDrop(ItemId.COSMIC_EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include cosmic ebony staff"),
        ("commonDrops.addItemDrop(ItemId.CHAOS_EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include chaos ebony staff"),
        ("commonDrops.addItemDrop(ItemId.NATURE_EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include nature ebony staff"),
        ("commonDrops.addItemDrop(ItemId.LAW_EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include law ebony staff"),
        ("commonDrops.addItemDrop(ItemId.DEATH_EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include death ebony staff"),
        ("commonDrops.addItemDrop(ItemId.BLOOD_RUNE_EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include blood rune ebony staff"),
        ("commonDrops.addItemDrop(ItemId.SOUL_EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include soul ebony staff"),
        ("commonDrops.addItemDrop(ItemId.LIFE_EBONY_STAFF.id(), 1, 1);", "Elder Green Dragon common drops should include life ebony staff"),
        ("commonDrops.addItemDrop(ItemId.ORICHALCUM_2_HANDED_SWORD.id(), 1, 1);", "Elder Green Dragon common drops should include orichalcum two hander"),
        ("commonDrops.addItemDrop(ItemId.ORICHALCUM_PLATE_MAIL_BODY.id(), 1, 1);", "Elder Green Dragon common drops should include orichalcum platemail"),
        ("commonDrops.addItemDrop(ItemId.MEDIUM_RUNE_HELMET.id(), 1, 1);", "Elder Green Dragon common drops should include medium rune helmet"),
        ("commonDrops.addItemDrop(ItemId.LARGE_RUNE_HELMET.id(), 1, 1);", "Elder Green Dragon common drops should include large rune helmet"),
        ("uncommonDrops.addItemDrop(ItemId.RUNITE_GREAVES.id(), 1, 1);", "Elder Green Dragon uncommon drops should include rune greaves"),
        ("uncommonDrops.addItemDrop(ItemId.RUNITE_GAUNTLETS.id(), 1, 1);", "Elder Green Dragon uncommon drops should include rune gauntlets"),
        ("uncommonDrops.addItemDrop(ItemId.RUNE_PLATE_MAIL_LEGS.id(), 1, 1);", "Elder Green Dragon uncommon drops should include rune platelegs"),
        ("uncommonDrops.addItemDrop(ItemId.DEATH_RUNE.id(), 100, 1);", "Elder Green Dragon uncommon drops should include 100 death runes"),
        ("uncommonDrops.addItemDrop(ItemId.BLOOD_RUNE.id(), 40, 1);", "Elder Green Dragon uncommon drops should include 40 blood runes"),
        ("uncommonDrops.addItemDrop(ItemId.TITAN_STEEL_ARROWS.id(), 100, 1);", "Elder Green Dragon uncommon drops should include titan steel arrows"),
        ("uncommonDrops.addItemDrop(ItemId.TITAN_STEEL_BOLTS.id(), 100, 1);", "Elder Green Dragon uncommon drops should include titan steel bolts"),
        ("uncommonDrops.addItemDrop(ItemId.TITAN_STEEL_THROWING_DART.id(), 100, 1);", "Elder Green Dragon uncommon drops should include titan steel darts"),
        ("uncommonDrops.addItemDrop(ItemId.TITAN_STEEL_THROWING_KNIFE.id(), 100, 1);", "Elder Green Dragon uncommon drops should include titan steel knives"),
        ("uncommonDrops.addItemDrop(ItemId.TITAN_STEEL_SHURIKEN.id(), 100, 1);", "Elder Green Dragon uncommon drops should include titan steel shuriken"),
        ("uncommonDrops.addItemDrop(ItemId.BLOOD_LONGBOW.id(), 1, 1);", "Elder Green Dragon uncommon drops should include a blood bow"),
        ("uncommonDrops.addItemDrop(ItemId.DEATH_BLOOD_STAFF.id(), 1, 1);", "Elder Green Dragon uncommon drops should include a blood staff enchanted at the death altar"),
        ("DropTable rareDrops = new DropTable(\"Elder Green Dragon rare drops\", true);", "Elder Green Dragon rare drops should be contribution-gated"),
        ("rareDrops.addItemDrop(ItemId.BLOOD_RUNE.id(), 100, 1);", "Elder Green Dragon rare drops should include 100 blood runes"),
        ("rareDrops.addItemDrop(ItemId.DEATH_RUNE.id(), 400, 1);", "Elder Green Dragon rare drops should include 400 death runes"),
        ("rareDrops.addItemDrop(ItemId.LAW_RUNE.id(), 200, 1);", "Elder Green Dragon rare drops should include 200 law runes"),
        ("rareDrops.addItemDrop(ItemId.RUNE_ARROWS.id(), 200, 1);", "Elder Green Dragon rare drops should include rune arrows"),
        ("rareDrops.addItemDrop(ItemId.RUNE_BOLTS.id(), 200, 1);", "Elder Green Dragon rare drops should include rune bolts"),
        ("rareDrops.addItemDrop(ItemId.RUNE_THROWING_DART.id(), 200, 1);", "Elder Green Dragon rare drops should include rune darts"),
        ("rareDrops.addItemDrop(ItemId.RUNE_THROWING_KNIFE.id(), 200, 1);", "Elder Green Dragon rare drops should include rune knives"),
        ("rareDrops.addItemDrop(ItemId.RUNE_SHURIKEN.id(), 200, 1);", "Elder Green Dragon rare drops should include rune shuriken"),
        ("rareDrops.addItemDrop(ItemId.RUNE_PLATE_MAIL_BODY.id(), 1, 1);", "Elder Green Dragon rare drops should include rune platemail"),
        ("elderGreenDragonDrops.addTableDrop(commonDrops, 96);", "Elder Green Dragon common bucket should use 96 weight"),
        ("elderGreenDragonDrops.addTableDrop(uncommonDrops, 24);", "Elder Green Dragon uncommon bucket should use 24 weight"),
        ("elderGreenDragonDrops.addTableDrop(rareDrops, 8);", "Elder Green Dragon rare bucket should use 8 weight"),
    ):
        require(elder_green_dragon, needle, message)

    print("PASS: dragon NPC drop sources validated")


if __name__ == "__main__":
    main()
