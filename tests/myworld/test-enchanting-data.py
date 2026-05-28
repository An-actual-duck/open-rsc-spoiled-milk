#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import Any, NoReturn


ROOT = Path(__file__).resolve().parents[2]
ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
EFFECTS_PATH = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "content"
    / "EnchantingItemEffects.java"
)
LAW_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "skills"
    / "enchanting"
    / "LawJewelry.java"
)
ENCHANTING_PATH = (
    ROOT
    / "server"
    / "plugins"
    / "com"
    / "openrsc"
    / "server"
    / "plugins"
    / "custom"
    / "myworld"
    / "skills"
    / "enchanting"
    / "Enchanting.java"
)
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
CLIENT_ENTITY_HANDLER_PATH = (
    ROOT
    / "Client_Base"
    / "src"
    / "com"
    / "openrsc"
    / "client"
    / "entityhandling"
    / "EntityHandler.java"
)


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_items() -> dict[int, dict[str, Any]]:
    if not ITEMS_PATH.exists():
        fail(f"Missing file: {ITEMS_PATH}")
    data = json.loads(ITEMS_PATH.read_text(encoding="utf-8"))
    entries = data["items"] if isinstance(data, dict) else data
    return {entry["id"]: entry for entry in entries}


def expect_item(
    items: dict[int, dict[str, Any]], item_id: int, name: str
) -> dict[str, Any]:
    entry = items.get(item_id)
    if entry is None:
        fail(f"Missing item id {item_id}: {name}")
    if entry["name"] != name:
        fail(f"Item {item_id} expected name {name!r} but found {entry['name']!r}")
    return entry


def expect_description(
    items: dict[int, dict[str, Any]], item_id: int, expected: str
) -> None:
    entry = items.get(item_id)
    if entry is None:
        fail(f"Missing item id {item_id} for description check")
    if entry["description"] != expected:
        fail(
            f"Item {item_id} expected description {expected!r} but found {entry['description']!r}"
        )


def ensure_amulet_lines(items: dict[int, dict[str, Any]]) -> None:
    lines = {
        "Evasion": range(1593, 1598),
        "Balance": range(1598, 1603),
        "Guarding": range(1603, 1608),
        "Warding": range(1608, 1613),
        "Teleportation": range(1709, 1714),
        "Echoes": range(1719, 1724),
        "Ruin": range(1724, 1729),
        "Siphoning": range(1729, 1734),
        "Combat": range(1734, 1739),
        "Discipline": range(1739, 1744),
        "Cleansing": range(1744, 1749),
        "Bounty": range(1749, 1754),
        "Lifesaving": range(1754, 1759),
        "Command": range(3106, 3111),
    }
    tiers = ["Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone"]
    for effect_name, ids in lines.items():
        for tier_name, item_id in zip(tiers, ids):
            entry = expect_item(items, item_id, f"{tier_name} Amulet of {effect_name}")
            if entry["wearSlot"] != 10:
                fail(f"{entry['name']} should use neck slot")
            if effect_name == "Teleportation" and "Use" not in entry["command"].split(","):
                fail(f"{entry['name']} should expose Use for teleport interaction")
            if effect_name == "Teleportation" and "Check" not in entry["command"].split(","):
                fail(f"{entry['name']} should expose Check for charge display")


def ensure_necklace_lines(items: dict[int, dict[str, Any]]) -> None:
    necklace_lines = {
        "Archery": range(1613, 1618),
        "Craftsmanship": range(1618, 1623),
        "Balance": range(1623, 1628),
        "Force": range(1628, 1633),
        "Sorcery": range(1633, 1638),
        "Discipline": range(1638, 1643),
        "Fortune": range(1643, 1648),
        "Recoil": range(1648, 1653),
        "Nourishment": range(1653, 1658),
        "Banking": range(1658, 1663),
        "Desperation": range(1663, 1668),
        "Vitality": range(1668, 1673),
        "Preservation": range(1759, 1764),
        "Vigor": range(3101, 3106),
    }
    tiers = ["Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone"]
    for effect_name, ids in necklace_lines.items():
        for tier_name, item_id in zip(tiers, ids):
            entry = expect_item(items, item_id, f"{tier_name} Necklace of {effect_name}")
            if entry["wearSlot"] != 10:
                fail(f"{entry['name']} should use neck slot")


def ensure_ring_lines(items: dict[int, dict[str, Any]]) -> None:
    elemental = {
        "Air": range(1673, 1678),
        "Water": range(1678, 1683),
        "Earth": range(1683, 1688),
        "Fire": range(1688, 1693),
    }
    tiers = ["Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone"]
    elemental_names = {"Air": "Archery", "Water": "Balance", "Earth": "Force", "Fire": "Sorcery"}
    for altar_name, ids in elemental.items():
        for tier_name, item_id in zip(tiers, ids):
            entry = expect_item(items, item_id, f"{tier_name} Ring of {elemental_names[altar_name]}")
            if entry["wearSlot"] != 13:
                fail(f"{entry['name']} should use ring slot")

    special_rings = {
        1314: "Sapphire Ring of Recoil",
        1316: "Sapphire Ring of Nourishment",
        1317: "Diamond Ring of Preservation",
        1693: "Emerald Ring of Recoil",
        1697: "Emerald Ring of Nourishment",
        1705: "Sapphire Ring of Preservation",
        1706: "Emerald Ring of Preservation",
        1707: "Ruby Ring of Preservation",
        1708: "Dragonstone Ring of Preservation",
        1714: "Sapphire Ring of Banking",
        1715: "Emerald Ring of Banking",
        1716: "Ruby Ring of Banking",
        1717: "Diamond Ring of Banking",
        1718: "Dragonstone Ring of Banking",
        3076: "Sapphire Ring of Craftsmanship",
        3081: "Sapphire Ring of Discipline",
        3086: "Sapphire Ring of Desperation",
        3091: "Sapphire Ring of Vitality",
        3096: "Sapphire Ring of Endurance",
        3111: "Dragonstone Ring of Fortune",
    }
    for item_id, name in special_rings.items():
        entry = expect_item(items, item_id, name)
        if entry["wearSlot"] != 13:
            fail(f"{entry['name']} should use ring slot")


def ensure_source_mappings_exist() -> None:
    effects_text = EFFECTS_PATH.read_text(encoding="utf-8")
    law_text = LAW_PATH.read_text(encoding="utf-8")
    enchanting_text = ENCHANTING_PATH.read_text(encoding="utf-8")
    crafting_text = CRAFTING_PATH.read_text(encoding="utf-8")
    client_text = CLIENT_ENTITY_HANDLER_PATH.read_text(encoding="utf-8")

    required_snippets = [
        "private static final int[] SOUL_AMULETS = {",
        "private static final int[] SOUL_NECKLACES = {",
        "public static final int SOUL_ALTAR = 1296;",
        "ItemId.RING_OF_RECOIL.id(), 1693, 1694, 1695, 1696",
        "ItemId.RING_OF_FORGING.id(), 1697, 1698, 1699, 1700",
        "1701, 1702, 1703, 1704, ItemId.DRAGONSTONE_RING_OF_FORTUNE.id()",
        "1705, 1706, 1707, ItemId.RING_OF_LIFE.id(), 1708",
        "ItemId.SOUL_RUNE.id()",
        "return 3;",
        "private static final class TieredLine",
        "private static final TieredLine[] SPECIAL_AMULET_LINES = {",
        "private static final TieredLine[] SPECIAL_RING_LINES = {",
        "private static final TieredLine[] ELEMENTAL_AMULET_LINES = {",
        "private static final TieredLine[] ELEMENTAL_RING_LINES = {",
        "private static final TieredLine[] STANDARD_STAFF_LINES = {",
        "private static final TieredLine[] STANDARD_NECKLACE_LINES = {",
        "private static double getTierScaledValue",
        "private static double getTierScaledValueByAltar",
        "private static int getTierForAltar",
        "private static int getTier(",
        "public static boolean isBaseWoolRobePiece",
        "public static int getWoolRobeProduct",
        "public static int getHighestEnchantingTierForLevel",
        "public static int getWoolRobeTier",
        "public static int getWoolRobeMagicDefense",
        "public static int getAltarTier",
        "{2072, 2331, 2332, 2333, 2334, 2335, 2336, 2337, 2338, 2339}",
        "{2097, 2547, 2548, 2549, 2550, 2551, 2552, 2553, 2554, 2555}",
        "{2109, 2673, 2674, 2675, 2676, 2677, 2678, 2679, 2680, 2681}",
    ]
    for snippet in required_snippets:
        if snippet not in effects_text:
            fail(f"EnchantingItemEffects.java missing expected snippet: {snippet}")

    for snippet in (
        "enchantOrUpgradeRobe",
        "getWoolRobeUpgradeRuneCost",
        "This robe is already bound to another altar.",
        "You strengthen the robe",
    ):
        if snippet not in enchanting_text:
            fail(f"Enchanting.java missing direct robe upgrade snippet: {snippet}")
    if "multi(player" in enchanting_text:
        fail("Robe enchanting should use the production window, not the legacy multi menu")

    for destination in (
        "CRAFTING_GUILD",
        "MINING_GUILD",
        "WOODCUTTING_GUILD",
        "PRAYER_GUILD",
        "FISHING_GUILD",
        "COOKING_GUILD",
        "HEROES_GUILD",
        "WIZARDS_GUILD",
        "CHAMPIONS_GUILD",
        "LEGENDS_GUILD",
    ):
        if destination not in law_text:
            fail(f"LawJewelry.java missing destination {destination}")

    for snippet in (
        'final int[] gemMasks = {19711, 3394611, 16724736, 0, 12255487};',
        'addLawAmuletLine(1709, tiers, lawAmuletPrices, gemMasks);',
        'addAmuletLine(1593, tiers, "Evasion", "Adds +%d ranged defense.", 3, amuletPrices, gemMasks, "");',
        'addNecklaceLine(1613, tiers, "Archery", "Adds +%d ranged power.", 3, necklacePrices, gemMasks);',
        'addRingLine(1673, tiers, "Archery", "Adds +%d ranged power.", 3, ringPrices, gemMasks);',
        'addLawBankingRingLine(1714, tiers, ringPrices, gemMasks, lawBankCharges);',
        'addSoulNecklaceLine(1759, tiers, soulNecklacePrices, gemMasks);',
        '"items:125"',
        '"items:57"',
        '"items:123"',
    ):
        if snippet not in client_text:
            fail(f"Client EntityHandler missing enchanted jewelry generator snippet: {snippet}")

    hidden_crown_options = """\t\t\toptions = new String[]{
\t\t\t\tring,
\t\t\t\tNecklace,
\t\t\t\tamulet
\t\t\t};"""
    if hidden_crown_options not in crafting_text:
        fail("Crafting.java should hide crown production from the gold jewelry menu")


def ensure_client_jewelry_coverage() -> None:
    client_text = CLIENT_ENTITY_HANDLER_PATH.read_text(encoding="utf-8")
    client_ids = set()
    for match in re.finditer(r"new ItemDef\((.*?)\)\);", client_text, re.S):
        body = match.group(1).strip()
        id_match = re.search(r",\s*(-?\d+)\s*$", body)
        if id_match:
            client_ids.add(int(id_match.group(1)))

    for match in re.finditer(r"addAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addDeathAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addLifeAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addNecklaceLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addLawBankingNecklaceLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addLifeNecklaceLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addLawAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addCosmicAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addSoulAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addSoulNecklaceLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addRingLine\((\d+),\s*(tiers|new String\[\] \{[^}]+\}),", client_text):
        start = int(match.group(1))
        args = match.group(2)
        count = 5 if args == "tiers" else len(re.findall(r'"[^"]+"', args))
        client_ids.update(range(start, start + count))
    for match in re.finditer(r"addOffsetRingLine\((\d+),\s*new String\[\] \{([^}]+)\},", client_text):
        start = int(match.group(1))
        count = len(re.findall(r'"[^"]+"', match.group(2)))
        client_ids.update(range(start, start + count))
    for match in re.finditer(r"addSoulRingLine\((\d+),\s*new String\[\] \{([^}]+)\},", client_text):
        start = int(match.group(1))
        count = len(re.findall(r'"[^"]+"', match.group(2)))
        client_ids.update(range(start, start + count))
    for match in re.finditer(r"addLawBankingRingLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addAttunedRingLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addLifeRingLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))

    missing = [item_id for item_id in range(1593, 1764) if item_id not in client_ids]
    if missing:
        fail(f"Client EntityHandler is missing enchanted jewelry ids: {missing}")


def ensure_client_jewelry_uses_base_visuals() -> None:
    client_text = CLIENT_ENTITY_HANDLER_PATH.read_text(encoding="utf-8")
    required_snippets = (
        'new ItemDef(tiers[i] + " Amulet of " + altarName,',
        '"items:125"',
        'new ItemDef(tiers[i] + " Necklace of " + altarName,',
        '"items:57"',
        'new ItemDef(tiers[i] + " Ring of " + altarName,',
        '"items:123"',
    )
    for snippet in required_snippets:
        if snippet not in client_text:
            fail(f"Client enchanted jewelry visuals drifted from base jewelry visuals: {snippet}")


def ensure_examine_copy(items: dict[int, dict[str, Any]]) -> None:
    expected_descriptions = {
        1613: "Adds +3 ranged power.",
        1643: "Has a 10% chance to roll extra standard monster loot.",
        1648: "Has 8% chance to recoil attacker damage.",
        1709: "Stores 3 guild teleports.",
        1714: "Banks non-stack skilling drops. 50 charges.",
        1719: "Has a 15% chance to echo half damage nearby.",
        1724: "Enemy deaths hit foes within 1 tile for 5% max Hits.",
        1729: "Steals 5% of damage dealt as healing.",
        1734: "Boosts combat XP by 5%.",
        1739: "Boosts hits, agility, prayer, and thieving XP by 5%.",
        1744: "Adds +1 poison decay per tick.",
        1749: "Has a 10% chance to double rare gathering rewards.",
        1754: "Has a 10% chance not to break after saving you.",
        1759: "Lets you keep 1 extra item on death.",
        3111: "Has a 25% chance for an extra full monster drop.",
    }
    for item_id, expected in expected_descriptions.items():
        expect_description(items, item_id, expected)


def main() -> None:
    items = load_items()
    ensure_amulet_lines(items)
    ensure_necklace_lines(items)
    ensure_ring_lines(items)
    expect_item(items, 2072, "Beginner's Air Wizard Hat")
    expect_item(items, 2085, "Beginner's Air Robe Top")
    expect_item(items, 2098, "Beginner's Air Robe Skirt")
    expect_item(items, 2083, "Beginner's Soul Wizard Hat")
    expect_item(items, 2096, "Beginner's Soul Robe Top")
    expect_item(items, 2109, "Beginner's Soul Robe Skirt")
    expect_item(items, 2084, "Beginner's Blood Wizard Hat")
    expect_item(items, 2097, "Beginner's Blood Robe Top")
    expect_item(items, 2110, "Beginner's Blood Robe Skirt")
    expect_item(items, 2339, "Mythic Air Wizard Hat")
    expect_item(items, 2555, "Mythic Blood Robe Top")
    expect_item(items, 2681, "Mythic Soul Robe Skirt")
    ensure_examine_copy(items)
    ensure_source_mappings_exist()
    ensure_client_jewelry_coverage()
    ensure_client_jewelry_uses_base_visuals()
    print("PASS: enchanting jewelry data validated")
    print("Amulet lines validated: 14")
    print("Necklace lines validated: 14")
    print("Elemental ring lines validated: 4")


if __name__ == "__main__":
    main()
