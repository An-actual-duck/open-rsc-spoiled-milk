#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import Any, NoReturn


ROOT = Path(__file__).resolve().parents[2]
ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
ITEM_OVERRIDES_PATH = (
    ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsMyWorld.json"
)
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
    items = {entry["id"]: dict(entry) for entry in entries}
    override_data = json.loads(ITEM_OVERRIDES_PATH.read_text(encoding="utf-8"))
    overrides = (
        override_data["items"] if isinstance(override_data, dict) else override_data
    )
    for override in overrides:
        item_id = override["id"]
        if item_id in items:
            items[item_id].update(override)
    return items


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


def ensure_bangel_lines(items: dict[int, dict[str, Any]]) -> None:
    lines = {
        "Teleportation": range(1709, 1714),
        "Chaos Weaving": range(1719, 1724),
        "Ruin": range(1724, 1729),
        "Siphoning": range(1729, 1734),
        "Attunement": range(1734, 1739),
        "Prowess": range(1739, 1744),
        "Alchemy": range(1744, 1749),
        "Bounty": range(1749, 1754),
        "Renewal": range(1754, 1759),
        "Command": range(3106, 3111),
    }
    tiers = ["Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone"]
    for effect_name, ids in lines.items():
        for tier_name, item_id in zip(tiers, ids):
            entry = expect_item(items, item_id, f"{tier_name} Bangel of {effect_name}")
            if entry["wearSlot"] != 14:
                fail(f"{entry['name']} should use wrist slot")
            if effect_name == "Teleportation":
                commands = entry["command"].split(",")
                if "Teleport" not in commands:
                    fail(f"{entry['name']} should expose Teleport for teleport interaction")
                if "Check" not in commands:
                    fail(f"{entry['name']} should expose Check for charge display")
                if "Use" in commands:
                    fail(f"{entry['name']} should leave generic Use available for altar recharging")
            if effect_name == "Alchemy" and entry["command"] != "Check":
                fail(f"{entry['name']} should expose Check for charge display")
    gatherer_lines = {
        "Woodcutter's Bangel": range(1593, 1598),
        "Angler's Bangel": range(1598, 1603),
        "Harvester's Bangel": range(1603, 1608),
        "Miner's Bangel": range(1608, 1613),
    }
    for amulet_name, ids in gatherer_lines.items():
        for tier_name, item_id in zip(tiers, ids):
            entry = expect_item(items, item_id, f"{tier_name} {amulet_name}")
            if entry["wearSlot"] != 14:
                fail(f"{entry['name']} should use wrist slot")


def ensure_necklace_lines(items: dict[int, dict[str, Any]]) -> None:
    necklace_lines = {
        "Evasion": range(1613, 1618),
        "Artifice": range(1618, 1623),
        "Equilibrium": range(1623, 1628),
        "Bulwark": range(1628, 1633),
        "Warding": range(1633, 1638),
        "Labor": range(1638, 1643),
        "Fortune": range(1643, 1648),
        "Chain Lightning": range(1648, 1653),
        "Cleansing": range(1653, 1658),
        "Reaping": range(1663, 1668),
        "Leach": range(1668, 1673),
        "Preservation": range(1759, 1764),
        "Vigor": range(3101, 3106),
    }
    tiers = ["Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone"]
    for effect_name, ids in necklace_lines.items():
        for tier_name, item_id in zip(tiers, ids):
            entry = expect_item(items, item_id, f"{tier_name} Necklace of {effect_name}")
            if entry["wearSlot"] != 10:
                fail(f"{entry['name']} should use neck slot")
    law_banking_necklaces = range(1658, 1663)
    for tier_name, item_id in zip(tiers, law_banking_necklaces):
        entry = expect_item(items, item_id, f"{tier_name} Necklace of Loot Banking")
        if entry["wearSlot"] != 10:
            fail(f"{entry['name']} should use neck slot")
        if entry["command"] != "Check":
            fail(f"{entry['name']} should expose Check for charge display")


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
        1317: "Diamond Ring of Lifesaving",
        1693: "Emerald Ring of Recoil",
        1697: "Emerald Ring of Nourishment",
        1705: "Sapphire Ring of Lifesaving",
        1706: "Emerald Ring of Lifesaving",
        1707: "Ruby Ring of Lifesaving",
        1708: "Dragonstone Ring of Lifesaving",
        1714: "Sapphire Ring of Skill Banking",
        1715: "Emerald Ring of Skill Banking",
        1716: "Ruby Ring of Skill Banking",
        1717: "Diamond Ring of Skill Banking",
        1718: "Dragonstone Ring of Skill Banking",
        3076: "Sapphire Ring of Hearthcraft",
        3081: "Sapphire Ring of Gains",
        3086: "Sapphire Ring of Reckoning",
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
    mudclient_text = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text(encoding="utf-8")

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
        "RANGERS_GUILD",
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
    if '"teleport".equalsIgnoreCase(command)' not in law_text:
        fail("LawJewelry.java should handle Teleport as the law amulet destination command")
    for snippet in (
        "return new Destination[] {Destination.COOKING_GUILD, Destination.PRAYER_GUILD};",
        """return new Destination[] {
					Destination.FISHING_GUILD,
					Destination.RANGERS_GUILD,
					Destination.WIZARDS_GUILD
				};""",
        """return new Destination[] {
					Destination.HEROES_GUILD,
					Destination.CHAMPIONS_GUILD,
					Destination.LEGENDS_GUILD
				};""",
        "final String[] options = new String[destinations.length + 1];",
        "if (option < 0 || option >= destinations.length)",
    ):
        if snippet not in law_text:
            fail(f"LawJewelry.java has incorrect law amulet tier destination pairing: {snippet}")
    if "case 5:" in law_text:
        fail("Dragonstone law amulet destinations should remain free for a future premium teleport set")
    for snippet in (
        "ProductionSession.TYPE_TELEPORT_DESTINATION",
        '"production_context_item_uid"',
        '"Choose a rune altar"',
        "LawJewelry::teleportToRuneAltar",
        "RuneAltarDestination.values()",
        "AIR(ItemId.AIR_RUNE.id(), 305, 593)",
        "LIFE(ItemId.LIFE_RUNE.id(), 282, 694)",
    ):
        if snippet not in law_text:
            fail(f"Dragonstone law amulet altar picker is missing: {snippet}")

    for snippet in (
        "hasInventoryUseCommand(def.getCommand())",
        "hasInventoryUseCommand(equippedItems[j].getCommand())",
    ):
        if snippet not in mudclient_text:
            fail(f"mudclient.java missing duplicate Use menu guard: {snippet}")

    for snippet in (
        'final int[] gemMasks = {19711, 3394611, 16724736, 0, 12255487};',
        'final int[] lawBankCharges = {100, 200, 300, 500, 1000};',
        'addLawAmuletLine(1709, tiers, lawAmuletPrices, gemMasks);',
        '"Teleport,Check"',
        'addGatheringAmuletLine(1593, tiers, "Woodcutter\'s", "Boosts woodcutting log yield by %d%%.", amuletPrices, gemMasks);',
        'addGatheringAmuletLine(1598, tiers, "Angler\'s", "Boosts fishing catch yield by %d%%.", amuletPrices, gemMasks);',
        'addNecklaceLine(1613, tiers, "Evasion", "Adds +%d ranged defense.", 3, necklacePrices, gemMasks);',
        'addNecklaceLine(1623, tiers, "Equilibrium", "Adds +%d melee, ranged, and magic defense.", 2, necklacePrices, gemMasks);',
        'addNecklaceLine(1628, tiers, "Bulwark", "Adds +%d melee defense.", 3, necklacePrices, gemMasks);',
        'addExplicitNecklaceLine(1618, tiers, "Artifice", "Boosts crafting, smithing, and enchanting XP by %d%%.",',
        'addAlchemyAmuletLine(1744, tiers, amuletPrices, gemMasks);',
        'addRingLine(1673, tiers, "Archery", "Adds +%d ranged power.", 3, ringPrices, gemMasks);',
        'addExplicitAmuletLine(1739, tiers, "Prowess", "Boosts agility, thieving, and ranged XP by %d%%.",',
        'addExplicitAttunedRingLine(3076, tiers, "Hearthcraft", "Boosts cooking, herblaw, and fishing XP by %d%%.",',
        'addExplicitAttunedRingLine(3081, tiers, "Gains", "Boosts melee and hits XP by %d%%.",',
        '" Necklace of Loot Banking"',
        '" Ring of Skill Banking"',
        'addLawBankingRingLine(1714, tiers, ringPrices, gemMasks, lawBankCharges);',
        'setCustomItemDefinition(1314, new ItemDef("Sapphire Ring of Recoil"',
        '"Has %d%% chance to recoil 25%% damage taken."',
        '"Has %d%% per-hop chain lightning, up to 3 halving hits."',
        'addChaosWeavingAmuletLine(1719, tiers, amuletPrices, gemMasks);',
        'addDeathReapingNecklaceLine(1663, tiers, necklacePrices, gemMasks);',
        'addDeathReckoningRingLine(3086, tiers, ringPrices, gemMasks);',
        'addSoulRenewalAmuletLine(1754, tiers, amuletPrices, gemMasks);',
        '"Auto-alchs 1000+ gp monster drops. " + charges[i] + " charges."',
        'setCustomItemDefinition(1316, new ItemDef("Sapphire Ring of Nourishment"',
        'setCustomItemDefinition(1317, new ItemDef("Diamond Ring of Lifesaving"',
        'addSoulNecklaceLine(1759, tiers, soulNecklacePrices, gemMasks);',
        "applyBangelVisuals();",
        '"external-png:bangel"',
        '"items:57"',
        '"items:123"',
    ):
        if snippet not in client_text:
            fail(f"Client EntityHandler missing enchanted jewelry generator snippet: {snippet}")
    for forbidden in (
        'items.add(new ItemDef("Sapphire Ring of Recoil"',
        'items.add(new ItemDef("Sapphire Ring of Nourishment"',
        'items.add(new ItemDef("Diamond Ring of Lifesaving"',
    ):
        if forbidden in client_text:
            fail(f"Preserved special ring should use fixed-id replacement, not append: {forbidden}")

    for snippet in (
        "return skillId == Skill.COOKING.id()\n\t\t\t|| skillId == Skill.HERBLAW.id()\n\t\t\t|| skillId == Skill.FISHING.id();",
        "return skillId == Skill.MELEE.id()\n\t\t\t|| skillId == Skill.ATTACK.id()\n\t\t\t|| skillId == Skill.DEFENSE.id()\n\t\t\t|| skillId == Skill.STRENGTH.id()\n\t\t\t|| skillId == Skill.HITS.id();",
    ):
        if snippet not in effects_text:
            fail(f"EnchantingItemEffects.java missing updated ring XP skill grouping: {snippet}")
    if "|| skillId == Skill.FLETCHING.id()" in effects_text:
        fail("Artifice should no longer boost retired Fletching XP")

    hidden_crown_options = """\t\t\toptions = new String[]{
\t\t\t\tring,
\t\t\t\tNecklace,
\t\t\t\tamulet,
\t\t\t\tBangel
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
    for match in re.finditer(r"addExplicitAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addChaosWeavingAmuletLine\((\d+),\s*tiers,", client_text):
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
    for match in re.finditer(r"addExplicitNecklaceLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addLawBankingNecklaceLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addDeathReapingNecklaceLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addLifeNecklaceLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addLawAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addGatheringAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addAlchemyAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addCosmicAmuletLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addSoulRenewalAmuletLine\((\d+),\s*tiers,", client_text):
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
    for match in re.finditer(r"addExplicitRingLine\((\d+),\s*(tiers|new String\[\] \{[^}]+\}),", client_text):
        start = int(match.group(1))
        args = match.group(2)
        count = 5 if args == "tiers" else len(re.findall(r'"[^"]+"', args))
        client_ids.update(range(start, start + count))
    for match in re.finditer(r"addOffsetRingLine\((\d+),\s*new String\[\] \{([^}]+)\},", client_text):
        start = int(match.group(1))
        count = len(re.findall(r'"[^"]+"', match.group(2)))
        client_ids.update(range(start, start + count))
    for match in re.finditer(r"addNatureNourishmentRingLine\((\d+),\s*new String\[\] \{([^}]+)\},", client_text):
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
    for match in re.finditer(r"addExplicitAttunedRingLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addDeathReckoningRingLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))
    for match in re.finditer(r"addLifeRingLine\((\d+),\s*tiers,", client_text):
        start = int(match.group(1))
        client_ids.update(range(start, start + 5))

    missing = [item_id for item_id in range(1593, 1764) if item_id not in client_ids]
    missing.extend(item_id for item_id in (1314, 1316, 1317) if item_id not in client_ids)
    if missing:
        fail(f"Client EntityHandler is missing enchanted jewelry ids: {missing}")


def ensure_client_jewelry_uses_base_visuals() -> None:
    client_text = CLIENT_ENTITY_HANDLER_PATH.read_text(encoding="utf-8")
    required_snippets = (
        "applyBangelVisuals();",
        '"external-png:bangel"',
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
        1593: "Boosts woodcutting log yield by 10%.",
        1598: "Boosts fishing catch yield by 10%.",
        1603: "Boosts harvesting produce yield by 10%.",
        1608: "Boosts mining ore yield by 10%.",
        1613: "Adds +3 ranged defense.",
        1618: "Boosts crafting, smithing, and enchanting XP by 5%.",
        1621: "Boosts crafting, smithing, and enchanting XP by 25%.",
        1622: "Boosts crafting, smithing, and enchanting XP by 50%.",
        1623: "Adds +2 melee, ranged, and magic defense.",
        1628: "Adds +3 melee defense.",
        1633: "Adds +3 magic defense.",
        1638: "Boosts harvesting, mining, and woodcutting XP by 5%.",
        1641: "Boosts harvesting, mining, and woodcutting XP by 25%.",
        1642: "Boosts harvesting, mining, and woodcutting XP by 50%.",
        1643: "Has a 10% chance to roll extra standard monster loot.",
        1648: "Has 10% per-hop chain lightning, up to 3 halving hits.",
        1709: "Stores 3 guild teleports.",
        1658: "Banks non-stack monster loot. 100 charges.",
        1662: "Banks non-stack monster loot. 1000 charges.",
        1714: "Banks non-stack skilling drops. 100 charges.",
        1718: "Banks non-stack skilling drops. 1000 charges.",
        1668: "Has 10% poison Leach from poison damage dealt.",
        1672: "Has 100% poison Leach from poison damage dealt.",
        3091: "Adds +2 max Hits.",
        3095: "Adds +20 max Hits.",
        1719: "Boosts chaos rune yield by 20%; bonus mind/chaos/death/blood: 50/25/20/5.",
        1724: "At 100 death charge, bursts for 1-3 damage within 2 tiles.",
        1729: "Steals 5% of damage dealt as healing.",
        1734: "Boosts magic, summoning, and prayer XP by 5%.",
        1737: "Boosts magic, summoning, and prayer XP by 25%.",
        1738: "Boosts magic, summoning, and prayer XP by 50%.",
        1739: "Boosts agility, thieving, and ranged XP by 5%.",
        1742: "Boosts agility, thieving, and ranged XP by 25%.",
        1743: "Boosts agility, thieving, and ranged XP by 50%.",
        1653: "Adds +1 poison decay per tick.",
        1744: "Auto-alchs 1000+ gp monster drops. 100 charges.",
        1748: "Auto-alchs 1000+ gp monster drops. 1000 charges.",
        1749: "Has a 10% chance to double rare gathering rewards.",
        1754: "At 200 soul charge, heals 1-2 Hits within 2 tiles.",
        1759: "Lets you keep 1 extra item on death.",
        1699: "Boosts food healing by 50%.",
        1700: "Boosts food healing by 100%.",
        3076: "Boosts cooking, herblaw, and fishing XP by 5%.",
        3079: "Boosts cooking, herblaw, and fishing XP by 25%.",
        3080: "Boosts cooking, herblaw, and fishing XP by 50%.",
        3081: "Boosts melee and hits XP by 5%.",
        3084: "Boosts melee and hits XP by 25%.",
        3085: "Boosts melee and hits XP by 50%.",
        3111: "If a monster rare table misses, has a 25% chance to reroll the drop.",
    }
    for item_id, expected in expected_descriptions.items():
        expect_description(items, item_id, expected)


def ensure_jewelry_effect_ladders(items: dict[int, dict[str, Any]]) -> None:
    exact_lines = {
        1314: "Has 10% chance to recoil 25% damage taken.",
        1693: "Has 20% chance to recoil 25% damage taken.",
        1694: "Has 30% chance to recoil 25% damage taken.",
        1695: "Has 50% chance to recoil 25% damage taken.",
        1696: "Has 90% chance to recoil 25% damage taken.",
        1648: "Has 10% per-hop chain lightning, up to 3 halving hits.",
        1649: "Has 20% per-hop chain lightning, up to 3 halving hits.",
        1650: "Has 30% per-hop chain lightning, up to 3 halving hits.",
        1651: "Has 50% per-hop chain lightning, up to 3 halving hits.",
        1652: "Has 90% per-hop chain lightning, up to 3 halving hits.",
        1663: "Has 25% chance to add +1 to each guaranteed NPC drop.",
        1664: "Has 40% chance to add +1 to each guaranteed NPC drop.",
        1665: "Has 60% chance to add +1 to each guaranteed NPC drop.",
        1666: "Has 90% chance to add +1 to each guaranteed NPC drop.",
        1667: "Guaranteed +1 to each guaranteed NPC drop; 10% chance for +2.",
        1668: "Has 10% poison Leach from poison damage dealt.",
        1669: "Has 20% poison Leach from poison damage dealt.",
        1670: "Has 30% poison Leach from poison damage dealt.",
        1671: "Has 50% poison Leach from poison damage dealt.",
        1672: "Has 100% poison Leach from poison damage dealt.",
        3086: "Charges on NPC kills; +1 yellow damage per 10 charges, up to 20.",
        3087: "Charges on NPC kills; +1 yellow damage per 10 charges, up to 30.",
        3088: "Charges on NPC kills; +1 yellow damage per 10 charges, up to 40.",
        3089: "Charges on NPC kills; +1 yellow damage per 10 charges, up to 60.",
        3090: "Charges on NPC kills; +1 yellow damage per 10 charges, up to 100.",
        3091: "Adds +2 max Hits.",
        3092: "Adds +4 max Hits.",
        3093: "Adds +6 max Hits.",
        3094: "Adds +10 max Hits.",
        3095: "Adds +20 max Hits.",
        1705: "Has a 10% chance not to break after saving you.",
        1706: "Has a 20% chance not to break after saving you.",
        1707: "Has a 30% chance not to break after saving you.",
        1317: "Has a 50% chance not to break after saving you.",
        1708: "Has a 90% chance not to break after saving you.",
        1724: "At 100 death charge, bursts for 1-3 damage within 2 tiles.",
        1725: "At 100 death charge, bursts for 3-6 damage within 2 tiles.",
        1726: "At 100 death charge, bursts for 6-9 damage within 2 tiles.",
        1727: "At 100 death charge, bursts for 9-14 damage within 2 tiles.",
        1728: "At 100 death charge, bursts for 10-20 damage within 2 tiles.",
        1754: "At 200 soul charge, heals 1-2 Hits within 2 tiles.",
        1755: "At 200 soul charge, heals 1-3 Hits within 2 tiles.",
        1756: "At 200 soul charge, heals 2-4 Hits within 2 tiles.",
        1757: "At 200 soul charge, heals 3-6 Hits within 2 tiles.",
        1758: "At 200 soul charge, heals 5-10 Hits within 2 tiles.",
        1759: "Lets you keep 1 extra item on death.",
        1760: "Lets you keep 2 extra items on death.",
        1761: "Lets you keep 3 extra items on death.",
        1762: "Lets you keep 5 extra items on death.",
        1763: "Lets you keep 8 extra items on death.",
        1719: "Boosts chaos rune yield by 20%; bonus mind/chaos/death/blood: 50/25/20/5.",
        1720: "Boosts chaos rune yield by 35%; bonus mind/chaos/death/blood: 42/27/23/8.",
        1721: "Boosts chaos rune yield by 50%; bonus mind/chaos/death/blood: 35/28/25/12.",
        1722: "Boosts chaos rune yield by 70%; bonus mind/chaos/death/blood: 27/29/28/16.",
        1723: "Boosts chaos rune yield by 100%; bonus mind/chaos/death/blood: 20/30/30/20.",
        1744: "Auto-alchs 1000+ gp monster drops. 100 charges.",
        1745: "Auto-alchs 1000+ gp monster drops. 200 charges.",
        1746: "Auto-alchs 1000+ gp monster drops. 300 charges.",
        1747: "Auto-alchs 1000+ gp monster drops. 500 charges.",
        1748: "Auto-alchs 1000+ gp monster drops. 1000 charges.",
        1658: "Banks non-stack monster loot. 100 charges.",
        1659: "Banks non-stack monster loot. 200 charges.",
        1660: "Banks non-stack monster loot. 300 charges.",
        1661: "Banks non-stack monster loot. 500 charges.",
        1662: "Banks non-stack monster loot. 1000 charges.",
        1714: "Banks non-stack skilling drops. 100 charges.",
        1715: "Banks non-stack skilling drops. 200 charges.",
        1716: "Banks non-stack skilling drops. 300 charges.",
        1717: "Banks non-stack skilling drops. 500 charges.",
        1718: "Banks non-stack skilling drops. 1000 charges.",
    }
    for item_id, expected in exact_lines.items():
        expect_description(items, item_id, expected)


def ensure_all_enchanted_jewelry_has_effect_examine(items: dict[int, dict[str, Any]]) -> None:
    jewelry_ids = set()
    for start, end in (
        (1593, 1764),
        (3076, 3112),
    ):
        jewelry_ids.update(range(start, end))
    jewelry_ids.update((1314, 1316, 1317))

    vague_descriptions = {
        "",
        "A valuable ring",
        "A valuable necklace",
        "A valuable amulet",
        "I wonder if I can get this enchanted",
        "An enchanted ring.",
        "An enchanted necklace.",
        "An enchanted amulet.",
    }
    effect_words = (
        "Adds ",
        "Has ",
        "Boosts ",
        "Banks ",
        "Stores ",
        "Lets ",
        "Enemy ",
        "Steals ",
        "Raises ",
        "Saves ",
        "Doubles ",
        "Extends ",
        "If ",
        "At ",
        "Auto-alchs ",
        "Creates ",
        "Charges ",
        "Guaranteed ",
    )
    for item_id in sorted(jewelry_ids):
        entry = items.get(item_id)
        if entry is None:
            continue
        name = entry.get("name", "")
        if not any(kind in name for kind in ("Ring", "Necklace", "Bangel")):
            continue
        description = entry.get("description", "")
        if description in vague_descriptions or not description.startswith(effect_words):
            fail(f"Enchanted jewelry {item_id} {name!r} needs effect examine text, found {description!r}")


def ensure_enchanted_wool_robes_have_effect_examine(items: dict[int, dict[str, Any]]) -> None:
    expected = {
        2072: "10% chance to not use air runes with casting\nReduces air magic damage by 2% per robe tier.",
        2076: "10% chance to not use mind runes with casting\nRaises mind spell damage caps by 1% per robe tier.",
        2073: "10% chance to not use water runes with casting\nReduces water magic damage by 2% per robe tier.",
        2074: "10% chance to not use earth runes with casting\nReduces earth magic damage by 2% per robe tier.",
        2075: "10% chance to not use fire runes with casting\nReduces fire magic damage by 2% per robe tier.",
        2077: "10% chance to not use body runes with casting\nDamage taken grants up to +1 weapon power per robe tier.",
        2079: "10% chance to not use cosmic runes with casting\nGrants 1% crit chance per robe tier.",
        2078: "10% chance to not use chaos runes with casting\nAdjacent enemies increase damage by 2% per robe tier.",
        2080: "10% chance to not use nature runes with casting\nPotions are 2% stronger and last 2% longer per robe tier.",
        2081: "10% chance to not use law runes with casting\nRunecrafting yields 2% more runes per robe tier.",
        2082: "10% chance to not use death runes with casting\nOverkill splashes for 2% per robe tier.",
        2084: "10% chance to not use blood runes with casting\nBlood spells splash for 2% per robe tier.",
        2083: "10% chance to not use soul runes with casting\nIncreases health regeneration by 2% per robe tier.",
        2764: "10% chance to not use life runes with casting\nSummons gain 2% duration or health per robe tier.",
        2796: "10% chance to not use air runes with casting\nReduces air magic damage by 2% per robe tier.",
        2936: "10% chance to not use air runes with casting\nReduces air magic damage by 2% per robe tier.",
    }
    for item_id, description in expected.items():
        expect_description(items, item_id, description)

    stale_attuned_robes = []
    robe_terms = ("Wizard Hat", "Robe Top", "Robe Skirt", "Wizard Gloves", "Wizard Boots")
    for item_id, entry in items.items():
        name = entry.get("name", "")
        description = entry.get("description", "")
        if any(term in name for term in robe_terms) and "attuned to the" in description:
            stale_attuned_robes.append(f"{item_id} {name}")
    if stale_attuned_robes:
        fail(f"Enchanted wool robes still have vague altar examine text: {stale_attuned_robes[:8]}")

    client_text = CLIENT_ENTITY_HANDLER_PATH.read_text(encoding="utf-8")
    for snippet in (
        "MYWORLD_WOOL_ROBE_EXAMINE_EFFECTS",
        "applyMyWorldWoolRobeDescriptions();",
        "10% chance to not use ",
        "Increases health regeneration by 2% per robe tier.",
    ):
        if snippet not in client_text:
            fail(f"Client EntityHandler missing wool robe examine helper snippet: {snippet}")


def main() -> None:
    items = load_items()
    ensure_bangel_lines(items)
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
    ensure_jewelry_effect_ladders(items)
    ensure_all_enchanted_jewelry_has_effect_examine(items)
    ensure_enchanted_wool_robes_have_effect_examine(items)
    ensure_source_mappings_exist()
    ensure_client_jewelry_coverage()
    ensure_client_jewelry_uses_base_visuals()
    print("PASS: enchanting jewelry data validated")
    print("Bangel lines validated: 14")
    print("Necklace lines validated: 14")
    print("Elemental ring lines validated: 4")


if __name__ == "__main__":
    main()
