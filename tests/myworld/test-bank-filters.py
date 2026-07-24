#!/usr/bin/env python3
"""Validate the custom bank filter panel and its item-tag classifier."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
BANK = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java"
TAGS = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/BankItemTag.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def forbid(text: str, snippet: str, label: str) -> None:
    if snippet in text:
        fail(f"{label} contains excluded feature: {snippet}")


def main() -> None:
    bank = BANK.read_text(encoding="utf-8")
    tags = TAGS.read_text(encoding="utf-8")

    expected_tags = (
        'MINING_SMITHING("Mining & Smithing", Group.SKILLS)',
        'CRAFTING_LEATHER("Crafting: Leather", Group.SKILLS)',
        'CRAFTING_JEWELRY("Crafting: Jewelry", Group.SKILLS)',
        'CRAFTING_WOOD("Crafting: Wood", Group.SKILLS)',
        'CRAFTING_OTHER("Crafting: Other", Group.SKILLS)',
        'ENCHANTING("Enchanting", Group.SKILLS)',
        'PRAYER("Prayer", Group.SKILLS)',
        'HERBLAW("Herblaw", Group.SKILLS)',
        'COOKING("Cooking", Group.SKILLS)',
        'FOOD("Food", Group.ITEM_TYPES)',
        'TOOLS("Tools", Group.ITEM_TYPES)',
        'RARE_DROPS("Rare drops", Group.ITEM_TYPES)',
        'ARMOUR("Armour", Group.ITEM_TYPES)',
        'MAGIC("Magic & Summoning", Group.ITEM_TYPES)',
        'MELEE("Melee", Group.ITEM_TYPES)',
        'RANGED("Ranged", Group.ITEM_TYPES)',
        'JEWELRY("Jewelry", Group.ITEM_TYPES)',
        'QUEST_ITEMS("Quest items", Group.ITEM_TYPES)',
    )
    for tag in expected_tags:
        require(tags, tag, "BankItemTag")

    for excluded in (
        "FIREMAKING",
        "SEEDS",
        "HARVESTING",
        'MINING("Mining"',
        'SMITHING("Smithing"',
        'CRAFTING("Crafting"',
        "WOODCUTTING",
        "FLETCHING",
        "FISHING",
        "POTIONS",
        "POTION_INGREDIENTS",
        "COOKING_INGREDIENTS",
        "CURRENCY",
        "VALUABLES",
        "STACKABLE",
        "NOTED",
        "EQUIPPABLE",
    ):
        forbid(tags, excluded, "BankItemTag")

    for snippet in (
        'drawString("Filters"',
        'drawFilterModeButton("AND"',
        'drawFilterModeButton("OR"',
        'drawString("Search"',
        "private ArrayList<BankItem> filteredBankItems()",
        "matches = (!hasSearch || searchMatch) && (!hasTags || tagMatch);",
        "matches = (hasSearch && searchMatch) || (hasTags && tagMatch);",
        "selectedBankSlot = bankItem.bankID;",
        "if (filtersActive())",
        "filterPanelDocked",
        "filterDrawerOpen",
    ):
        require(bank, snippet, "CustomBankInterface")

    forbid(bank, 'drawString("Search for item:"', "legacy bank search")
    require(tags, 'endsWithAny(name, "-rune", " rune", " runes")', "rune item classification")
    require(tags, "if (def.untradeable", "quest item classification")
    require(tags, 'boolean ashes = equalsAny(name, "ashes");', "summoning ashes classification")
    require(tags, "boolean demonAshes = containsAny(name, \"demon ash\");", "demon ash prayer classification")
    require(tags, "|| bones || ashes || demonAshes", "summoning reagent magic classification")
    require(tags, "boolean prayerEquipment = def.isWieldable() && isPrayerEquipment", "prayer equipment classification")
    require(tags, 'startsWithAny(name, "ring ", "ring-")', "whole-word jewelry ring classification")
    require(tags, 'containsAny(name, " ring ")', "middle-word jewelry ring classification")
    require(tags, "boolean miningMaterial =", "combined mining/smithing classification")
    require(tags, "tags.add(MINING_SMITHING);", "combined mining/smithing tag")
    require(tags, "private static boolean isCraftingLeather", "crafting leather classification")
    require(tags, "private static boolean isCraftingJewelry", "crafting jewelry classification")
    require(tags, "private static boolean isJewelryGemMaterial", "crafting jewelry gem material classification")
    require(tags, "if (!gem || isJewelry(name))", "finished jewelry exclusion from crafting jewelry")
    require(tags, 'equalsAny(name, "gold nugget", "gold bar", "silver nugget", "silver bar", "wool", "ball of wool")', "crafting jewelry metal and wool materials")
    require(tags, "private static boolean isJewelryMould", "crafting jewelry mould classification")
    require(tags, 'equalsAny(name, "ring mould", "necklace mould", "bangle mould", "holy symbol mould"', "crafting jewelry exact mould classification")
    require(tags, "private static boolean isCraftingOther", "crafting other classification")
    require(tags, "if (logs)", "crafting wood log classification")
    require(tags, "tags.add(CRAFTING_WOOD);", "crafting wood tag")
    require(tags, "bow || rangedAmmo", "fletching materials moved into crafting other")
    require(tags, "if (potion || potionIngredient)", "herblaw skill classification")
    require(tags, "if (food || raw || cookingIngredient)", "cooking skill classification")
    require(tags, "private static boolean isTool(String name)", "tool classification")
    require(tags, 'containsAny(name, "pickaxe", "fishing rod"', "tool keyword classification")
    require(tags, "private static boolean isRareDrop(String name)", "rare drop classification")
    require(tags, 'endsWithAny(name, " seed")', "rare drop seed classification")
    require(tags, 'containsAny(name, "casket", "oyster", "key half", "crystal key", "geode")', "rare drop named classification")
    require(tags, 'equalsAny(name, "dragonstone", "uncut dragonstone", "dragon shield half left")', "rare drop exact classification")
    require(tags, "tags.add(RARE_DROPS);", "rare drops tag assignment")
    require(tags, "private static boolean isEnchantingInput", "enchanting input classifier")
    require(tags, 'containsAny(name, "mould", "blessed", "holy", "unholy", "symbol", "enchanted")', "enchanting exclusion classifier")
    require(tags, "isEnchantableBaseJewelry(name)", "base jewelry enchanting input")
    require(tags, "isEnchantableBaseStaff(name)", "base staff enchanting input")
    require(tags, "boolean bow = isBowWeapon(name);", "whole-word bow classification")
    require(tags, 'endsWithAny(name, " longbow", " shortbow", " crossbow"', "bow suffix classification")
    forbid(tags, 'boolean bonesOrAshes = containsAny(name, "bone", "ashes", "demon ash");', "generic ashes classification")
    forbid(tags, "uncutGem || gem || jewelry ||", "finished jewelry crafting classification")
    forbid(tags, 'containsAny(name, "gold nugget", "gold bar", "silver nugget", "silver bar", "mould")', "generic mould crafting jewelry classification")
    forbid(tags, '|| containsAny(name, "tiara");', "finished tiara crafting jewelry classification")
    forbid(tags, '"knife", "ball of wool", "clay"', "ball of wool crafting other classification")
    forbid(tags, '|| equalsAny(name, "wool");', "wool crafting other classification")
    forbid(tags, 'boolean bow = containsAny(name, "bow", "crossbow");', "bowl-safe ranged classification")
    forbid(tags, "tags.add(MINING);", "retired mining bank filter")
    forbid(tags, "tags.add(SMITHING);", "retired smithing bank filter")
    forbid(tags, "tags.add(CRAFTING);", "retired broad crafting bank filter")
    forbid(tags, "tags.add(WOODCUTTING);", "retired woodcutting bank filter")
    forbid(tags, "tags.add(FLETCHING);", "retired fletching bank filter")
    forbid(tags, "tags.add(FISHING);", "retired fishing bank filter")
    forbid(tags, "tags.add(POTIONS);", "retired potions bank filter")
    forbid(tags, "tags.add(POTION_INGREDIENTS);", "retired potion ingredients bank filter")
    forbid(tags, "tags.add(COOKING_INGREDIENTS);", "retired cooking ingredients bank filter")
    forbid(tags, 'if (rune || containsAny(name, "stone") || jewelry || staff', "broad enchanting bank filter")

    print("PASS: bank filters expose the agreed tags and preserve bank-slot-safe filtering")


if __name__ == "__main__":
    main()
