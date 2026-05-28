#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEM_ID_PATH = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
CRAFTING_PATH = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java"
TANNING_PATH = ROOT / "server/plugins/com/openrsc/server/plugins/custom/skills/crafting/TanningRack.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


HIDE_LINES = [
    {
        "material": "LEATHER",
        "tanned": None,
        "outputs": [
            "COW_HIDE_COIF",
            "COW_HIDE_GLOVES",
            "COW_HIDE_BOOTS",
            "COW_HIDE_CHAPS",
            "COW_HIDE_CUIRASS",
        ],
    },
    {
        "material": "GOBLIN_LEATHER",
        "tanned": "GOBLIN_LEATHER",
        "outputs": [
            "GOBLIN_HIDE_COIF",
            "GOBLIN_HIDE_GLOVES",
            "GOBLIN_HIDE_BOOTS",
            "GOBLIN_HIDE_CHAPS",
            "GOBLIN_HIDE_CUIRASS",
        ],
    },
    {
        "material": "UNICORN_LEATHER",
        "tanned": "UNICORN_LEATHER",
        "outputs": [
            "UNICORN_HIDE_COIF",
            "UNICORN_HIDE_GLOVES",
            "UNICORN_HIDE_BOOTS",
            "UNICORN_HIDE_CHAPS",
            "UNICORN_HIDE_CUIRASS",
        ],
    },
    {
        "material": "BEAR_LEATHER",
        "tanned": "BEAR_LEATHER",
        "outputs": [
            "BEAR_HIDE_COIF",
            "BEAR_HIDE_GLOVES",
            "BEAR_HIDE_BOOTS",
            "BEAR_HIDE_CHAPS",
            "BEAR_HIDE_CUIRASS",
        ],
    },
    {
        "material": "BLACK_UNICORN_LEATHER",
        "tanned": "BLACK_UNICORN_LEATHER",
        "outputs": [
            "BLACK_UNICORN_HIDE_COIF",
            "BLACK_UNICORN_HIDE_GLOVES",
            "BLACK_UNICORN_HIDE_BOOTS",
            "BLACK_UNICORN_HIDE_CHAPS",
            "BLACK_UNICORN_HIDE_CUIRASS",
        ],
    },
    {
        "material": "CURED_SCORPION_CARAPACE",
        "tanned": "CURED_SCORPION_CARAPACE",
        "outputs": [
            "SCORPION_CARAPACE_COIF",
            "SCORPION_CARAPACE_GLOVES",
            "SCORPION_CARAPACE_BOOTS",
            "SCORPION_CARAPACE_CHAPS",
            "SCORPION_CARAPACE_CUIRASS",
        ],
    },
    {
        "material": "WOLF_LEATHER",
        "tanned": "WOLF_LEATHER",
        "outputs": ["WOLF_COIF", "WOLF_GLOVES", "WOLF_BOOTS", "WOLF_CHAPS", "WOLF_CUIRASS"],
    },
    {
        "material": "CURED_SPIDER_CARAPACE",
        "tanned": "CURED_SPIDER_CARAPACE",
        "outputs": ["SPIDER_COIF", "SPIDER_GLOVES", "SPIDER_BOOTS", "SPIDER_CHAPS", "SPIDER_CUIRASS"],
    },
    {
        "material": "GIANT_LEATHER",
        "tanned": "GIANT_LEATHER",
        "outputs": ["GIANT_COIF", "GIANT_GLOVES", "GIANT_BOOTS", "GIANT_CHAPS", "GIANT_CUIRASS"],
    },
    {
        "material": "OGRE_LEATHER",
        "tanned": "OGRE_LEATHER",
        "outputs": ["OGRE_COIF", "OGRE_GLOVES", "OGRE_BOOTS", "OGRE_CHAPS", "OGRE_CUIRASS"],
    },
    {
        "material": "BABY_DRAGON_LEATHER",
        "tanned": "BABY_DRAGON_LEATHER",
        "outputs": [
            "BABY_DRAGON_COIF",
            "BABY_DRAGON_GLOVES",
            "BABY_DRAGON_BOOTS",
            "BABY_DRAGON_CHAPS",
            "BABY_DRAGON_CUIRASS",
        ],
    },
    {
        "material": "CURED_MAGIC_SPIDER_CARAPACE",
        "tanned": "CURED_MAGIC_SPIDER_CARAPACE",
        "outputs": [
            "MAGIC_SPIDER_COIF",
            "MAGIC_SPIDER_GLOVES",
            "MAGIC_SPIDER_BOOTS",
            "MAGIC_SPIDER_CHAPS",
            "MAGIC_SPIDER_CUIRASS",
        ],
    },
    {
        "material": "MOSS_GIANT_LEATHER",
        "tanned": "MOSS_GIANT_LEATHER",
        "outputs": [
            "MOSS_GIANT_COIF",
            "MOSS_GIANT_GLOVES",
            "MOSS_GIANT_BOOTS",
            "MOSS_GIANT_CHAPS",
            "MOSS_GIANT_CUIRASS",
        ],
    },
    {
        "material": "ICE_GIANT_LEATHER",
        "tanned": "ICE_GIANT_LEATHER",
        "outputs": [
            "ICE_GIANT_COIF",
            "ICE_GIANT_GLOVES",
            "ICE_GIANT_BOOTS",
            "ICE_GIANT_CHAPS",
            "ICE_GIANT_CUIRASS",
        ],
    },
    {
        "material": "DEMON_LEATHER",
        "tanned": "DEMON_LEATHER",
        "outputs": ["DEMON_COIF", "DEMON_GLOVES", "DEMON_BOOTS", "DEMON_CHAPS", "DEMON_CUIRASS"],
    },
    {
        "material": "HELLHOUND_LEATHER",
        "tanned": "HELLHOUND_LEATHER",
        "outputs": [
            "HELLHOUND_COIF",
            "HELLHOUND_GLOVES",
            "HELLHOUND_BOOTS",
            "HELLHOUND_CHAPS",
            "HELLHOUND_CUIRASS",
        ],
    },
    {
        "material": "FIRE_GIANT_LEATHER",
        "tanned": "FIRE_GIANT_LEATHER",
        "outputs": [
            "FIRE_GIANT_COIF",
            "FIRE_GIANT_GLOVES",
            "FIRE_GIANT_BOOTS",
            "FIRE_GIANT_CHAPS",
            "FIRE_GIANT_CUIRASS",
        ],
    },
    {
        "material": "BLUE_DRAGON_LEATHER",
        "tanned": "BLUE_DRAGON_LEATHER",
        "outputs": [
            "BLUE_DRAGON_COIF",
            "BLUE_DRAGON_GLOVES",
            "BLUE_DRAGON_BOOTS",
            "BLUE_DRAGON_CHAPS",
            "BLUE_DRAGON_CUIRASS",
        ],
    },
    {
        "material": "DRAGON_LEATHER",
        "tanned": "DRAGON_LEATHER",
        "outputs": ["DRAGON_COIF", "DRAGON_GLOVES", "DRAGON_BOOTS", "DRAGON_CHAPS", "DRAGON_CUIRASS"],
    },
    {
        "material": "RED_DRAGON_LEATHER",
        "tanned": "RED_DRAGON_LEATHER",
        "outputs": [
            "RED_DRAGON_COIF",
            "RED_DRAGON_GLOVES",
            "RED_DRAGON_BOOTS",
            "RED_DRAGON_CHAPS",
            "RED_DRAGON_CUIRASS",
        ],
    },
    {
        "material": "BLACK_DEMON_LEATHER",
        "tanned": "BLACK_DEMON_LEATHER",
        "outputs": [
            "BLACK_DEMON_COIF",
            "BLACK_DEMON_GLOVES",
            "BLACK_DEMON_BOOTS",
            "BLACK_DEMON_CHAPS",
            "BLACK_DEMON_CUIRASS",
        ],
    },
    {
        "material": "BLACK_DRAGON_LEATHER",
        "tanned": "BLACK_DRAGON_LEATHER",
        "outputs": [
            "BLACK_DRAGON_COIF",
            "BLACK_DRAGON_GLOVES",
            "BLACK_DRAGON_BOOTS",
            "BLACK_DRAGON_CHAPS",
            "BLACK_DRAGON_CUIRASS",
        ],
    },
    {
        "material": "BALROG_LEATHER",
        "tanned": "BALROG_LEATHER",
        "outputs": ["BALROG_COIF", "BALROG_GLOVES", "BALROG_BOOTS", "BALROG_CHAPS", "BALROG_CUIRASS"],
    },
    {
        "material": "KING_BLACK_DRAGON_LEATHER",
        "tanned": "KING_BLACK_DRAGON_LEATHER",
        "outputs": [
            "KING_BLACK_DRAGON_COIF",
            "KING_BLACK_DRAGON_GLOVES",
            "KING_BLACK_DRAGON_BOOTS",
            "KING_BLACK_DRAGON_CHAPS",
            "KING_BLACK_DRAGON_CUIRASS",
        ],
    },
]


def main() -> None:
    item_id_text = ITEM_ID_PATH.read_text(encoding="utf-8")
    crafting_text = CRAFTING_PATH.read_text(encoding="utf-8")
    tanning_text = TANNING_PATH.read_text(encoding="utf-8")

    required_crafting_snippets = (
        "createLeatherProductionSession(player, leather)",
        'new ProductionSession(ProductionSession.TYPE_CRAFTING, "Choose a leather item to craft"',
        "getHideArmorPieceByItemId",
        "batchLeather(player, leather, result, piece.materialCost, piece.reqLvl, piece.exp);",
    )
    for snippet in required_crafting_snippets:
        if snippet not in crafting_text:
            fail(f"Crafting.java missing leather-production support snippet: {snippet}")

    for line in HIDE_LINES:
        material = line["material"]
        if f"{material}(" not in item_id_text:
            fail(f"ItemId.java missing material constant: {material}")
        if f"case {material}:" not in crafting_text:
            fail(f"Crafting.java missing hide-armour recipe case: {material}")
        if line["tanned"] is not None and f"ItemId.{line['tanned']}.id()" not in tanning_text:
            fail(f"TanningRack.java missing tanning output for: {line['tanned']}")
        for output in line["outputs"]:
            if f"{output}(" not in item_id_text:
                fail(f"ItemId.java missing hide-armour output constant: {output}")
            if f"ItemId.{output}.id()" not in crafting_text:
                fail(f"Crafting.java missing hide-armour output wiring: {output}")

    print("PASS: hide and carapace armour coverage looks complete")


if __name__ == "__main__":
    main()
