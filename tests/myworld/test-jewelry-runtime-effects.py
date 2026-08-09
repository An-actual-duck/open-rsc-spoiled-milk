#!/usr/bin/env python3
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
EFFECTS = ROOT / "server/src/com/openrsc/server/content/EnchantingItemEffects.java"
LEACH = ROOT / "server/src/com/openrsc/server/content/Leach.java"
ITEM_DEFS_CUSTOM = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
DROP_TABLE = ROOT / "server/src/com/openrsc/server/content/DropTable.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
POISON_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/PoisonEvent.java"
CORROSIVE_AURA = ROOT / "server/src/com/openrsc/server/content/CorrosiveAura.java"
EATING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/itemactions/Eating.java"
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
COMBAT_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PVM_MELEE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
PROJECTILE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
CHAIN_TRAVERSAL = ROOT / "server/src/com/openrsc/server/model/combat/ChainLightningTraversalPolicy.java"
CHAIN_PROC = ROOT / "server/src/com/openrsc/server/model/combat/ChaosChainLightningProc.java"
FISHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/fishing/Fishing.java"
MINING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/mining/Mining.java"
WOODCUTTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/woodcutting/Woodcutting.java"
HARVESTING = ROOT / "server/plugins/com/openrsc/server/plugins/custom/skills/harvesting/Harvesting.java"
LAW_JEWELRY = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/enchanting/LawJewelry.java"
NATURE_ALCHEMY_AMULET = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/enchanting/NatureAlchemyAmulet.java"

AIR = 1190
MIND = 1192
WATER = 1194
EARTH = 1196
FIRE = 1198
BODY = 1200
COSMIC = 1202
CHAOS = 1204
NATURE = 1206
LAW = 1208
DEATH = 1210
BLOOD = 1212
SOUL = 1296
LIFE = 1321

RANGED = "RANGED"
MELEE = "MELEE"
MAGIC = "MAGIC"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def require_regex(text: str, pattern: str, message: str) -> None:
    if re.search(pattern, text, flags=re.S) is None:
        fail(message)


def near(actual: float, expected: float, message: str) -> None:
    if abs(actual - expected) > 0.000001:
        fail(f"{message}: expected {expected}, found {actual}")


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return "\n".join(line.split("//", 1)[0] for line in text.splitlines())


def java_method_body(text: str, signature: str) -> str:
    start = text.find(signature)
    if start == -1:
        fail(f"Missing Java method signature: {signature}")
    opening_brace = text.find("{", start)
    if opening_brace == -1:
        fail(f"Missing Java method body: {signature}")
    depth = 0
    for index in range(opening_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[opening_brace + 1:index]
    fail(f"Unterminated Java method body: {signature}")


def parse_item_ids() -> dict[str, int]:
    item_text = ITEM_ID.read_text(encoding="utf-8")
    return {
        match.group(1): int(match.group(2))
        for match in re.finditer(r"\b([A-Z0-9_]+)\((-?\d+)\)", item_text)
    }


ITEM_IDS = parse_item_ids()


def resolve_java_int(token: str) -> int:
    token = token.strip()
    if re.fullmatch(r"-?\d+", token):
        return int(token)
    match = re.fullmatch(r"ItemId\.([A-Z0-9_]+)\.id\(\)", token)
    if match and match.group(1) in ITEM_IDS:
        return ITEM_IDS[match.group(1)]
    fail(f"Could not resolve Java int token: {token!r}")


def parse_int_array(name: str) -> list[int]:
    text = strip_comments(EFFECTS.read_text(encoding="utf-8"))
    pattern = rf"private static final int\[\]\s+{re.escape(name)}\s*=\s*\{{(.*?)\}};"
    match = re.search(pattern, text, flags=re.S)
    if not match:
        fail(f"Missing int[] {name}")
    return [
        resolve_java_int(token)
        for token in match.group(1).replace("\n", " ").split(",")
        if token.strip()
    ]


def parse_int_matrix(name: str) -> list[list[int]]:
    text = strip_comments(EFFECTS.read_text(encoding="utf-8"))
    pattern = rf"private static final int\[\]\[\]\s+{re.escape(name)}\s*=\s*\{{(.*?)\}};"
    match = re.search(pattern, text, flags=re.S)
    if not match:
        fail(f"Missing int[][] {name}")
    return [
        [resolve_java_int(token) for token in row.split(",") if token.strip()]
        for row in re.findall(r"\{([^{}]+)\}", match.group(1))
    ]


def parse_double_array(name: str) -> list[float]:
    text = strip_comments(EFFECTS.read_text(encoding="utf-8"))
    pattern = rf"private static final double\[\]\s+{re.escape(name)}\s*=\s*\{{(.*?)\}};"
    match = re.search(pattern, text, flags=re.S)
    if not match:
        fail(f"Missing double[] {name}")
    return [
        float(token.strip().removesuffix("D"))
        for token in match.group(1).replace("\n", " ").split(",")
        if token.strip()
    ]


ELEMENTAL_RINGS = {
    AIR: [1673, 1674, 1675, 1676, 1677],
    WATER: [1678, 1679, 1680, 1681, 1682],
    EARTH: [1683, 1684, 1685, 1686, 1687],
    FIRE: [1688, 1689, 1690, 1691, 1692],
}
ELEMENTAL_AMULETS = {
    AIR: [1593, 1594, 1595, 1596, 1597],
    WATER: [1598, 1599, 1600, 1601, 1602],
    EARTH: [1603, 1604, 1605, 1606, 1607],
    FIRE: [1608, 1609, 1610, 1611, 1612],
}
STANDARD_NECKLACES = {
    AIR: [1613, 1614, 1615, 1616, 1617],
    MIND: [1618, 1619, 1620, 1621, 1622],
    WATER: [1623, 1624, 1625, 1626, 1627],
    EARTH: [1628, 1629, 1630, 1631, 1632],
    FIRE: [1633, 1634, 1635, 1636, 1637],
    BODY: [1638, 1639, 1640, 1641, 1642],
    COSMIC: [1643, 1644, 1645, 1646, 1647],
    CHAOS: [1648, 1649, 1650, 1651, 1652],
    NATURE: [1653, 1654, 1655, 1656, 1657],
    LAW: [1658, 1659, 1660, 1661, 1662],
    DEATH: [1663, 1664, 1665, 1666, 1667],
    BLOOD: [1668, 1669, 1670, 1671, 1672],
}


def special_rings() -> dict[int, list[int]]:
    return {
        MIND: parse_int_array("MIND_RINGS"),
        BODY: parse_int_array("BODY_RINGS"),
        CHAOS: parse_int_array("CHAOS_RINGS"),
        NATURE: parse_int_array("NATURE_RINGS"),
        COSMIC: parse_int_array("COSMIC_RINGS"),
        LAW: parse_int_array("LAW_RINGS"),
        DEATH: parse_int_array("DEATH_RINGS"),
        BLOOD: parse_int_array("BLOOD_RINGS"),
        SOUL: parse_int_array("SOUL_RINGS"),
        LIFE: parse_int_array("LIFE_RINGS"),
    }


def special_amulets() -> dict[int, list[int]]:
    return {
        LAW: parse_int_array("LAW_AMULETS"),
        CHAOS: parse_int_array("CHAOS_AMULETS"),
        DEATH: parse_int_array("DEATH_AMULETS"),
        BLOOD: parse_int_array("BLOOD_AMULETS"),
        MIND: parse_int_array("MIND_AMULETS"),
        BODY: parse_int_array("BODY_AMULETS"),
        NATURE: parse_int_array("NATURE_AMULETS"),
        COSMIC: parse_int_array("COSMIC_AMULETS"),
        SOUL: parse_int_array("SOUL_AMULETS"),
        LIFE: parse_int_array("LIFE_AMULETS"),
    }


SPECIAL_RINGS = special_rings()
SPECIAL_AMULETS = special_amulets()
SOUL_NECKLACES = parse_int_array("SOUL_NECKLACES")
LIFE_NECKLACES = parse_int_array("LIFE_NECKLACES")
MIND_BODY_JEWELRY_XP_BONUSES = parse_int_array("MIND_BODY_JEWELRY_XP_BONUSES")
NATURE_RING_FOOD_BONUSES = parse_double_array("NATURE_RING_FOOD_BONUSES")
CHAOS_RECOIL_CHANCES = parse_double_array("CHAOS_RECOIL_CHANCES")
CHAOS_CHAIN_LIGHTNING_CHANCES = parse_double_array("CHAOS_CHAIN_LIGHTNING_CHANCES")
BLOOD_RING_HITS_BONUSES = parse_int_array("BLOOD_RING_HITS_BONUSES")
BLOOD_NECKLACE_LEACH_PERCENTS = parse_double_array("BLOOD_NECKLACE_LEACH_PERCENTS")
SOUL_RING_SURVIVAL_CHANCES = parse_double_array("SOUL_RING_SURVIVAL_CHANCES")
SOUL_NECKLACE_EXTRA_KEPT_ITEMS = parse_int_array("SOUL_NECKLACE_EXTRA_KEPT_ITEMS")
DEATH_NECKLACE_GUARANTEED_DROP_BONUS_CHANCES = parse_double_array("DEATH_NECKLACE_GUARANTEED_DROP_BONUS_CHANCES")
DEATH_AMULET_BURST_MIN_DAMAGE = parse_int_array("DEATH_AMULET_BURST_MIN_DAMAGE")
DEATH_AMULET_BURST_MAX_DAMAGE = parse_int_array("DEATH_AMULET_BURST_MAX_DAMAGE")
SOUL_AMULET_BURST_MIN_HEAL = parse_int_array("SOUL_AMULET_BURST_MIN_HEAL")
SOUL_AMULET_BURST_MAX_HEAL = parse_int_array("SOUL_AMULET_BURST_MAX_HEAL")
DEATH_RING_CHARGE_CAPS = parse_int_array("DEATH_RING_CHARGE_CAPS")
CHAOS_AMULET_YIELD_BONUS_PERCENTS = parse_int_array("CHAOS_AMULET_YIELD_BONUS_PERCENTS")
CHAOS_AMULET_BONUS_RUNE_WEIGHTS = parse_int_matrix("CHAOS_AMULET_BONUS_RUNE_WEIGHTS")
LAW_BANKING_CHARGES = parse_int_array("LAW_BANKING_CHARGES")
NATURE_ALCHEMY_AMULET_CHARGES = parse_int_array("NATURE_ALCHEMY_AMULET_CHARGES")
GATHERING_AMULET_YIELD_BONUSES = parse_int_array("GATHERING_AMULET_YIELD_BONUSES")


def tier(item_id: int, line: list[int]) -> int:
    return line.index(item_id) + 1 if item_id in line else -1


def tier_for(item_id: int, lines: dict[int, list[int]], altar: int) -> int:
    return tier(item_id, lines.get(altar, []))


def nature_food_bonus(item_id: int) -> float:
    ring_tier = tier_for(item_id, SPECIAL_RINGS, NATURE)
    if ring_tier != -1:
        return NATURE_RING_FOOD_BONUSES[ring_tier - 1]
    return 0.0


def mind_body_jewelry_xp_bonus(item_id: int, lines: dict[int, list[int]], altar: int) -> float:
    jewelry_tier = tier_for(item_id, lines, altar)
    if jewelry_tier != -1:
        return MIND_BODY_JEWELRY_XP_BONUSES[jewelry_tier - 1] / 100.0
    return 0.0


def elemental_power(item_id: int, style: str) -> int:
    return elemental_power_from_lines(item_id, ELEMENTAL_RINGS, style)


def elemental_power_from_lines(item_id: int, lines: dict[int, list[int]], style: str) -> int:
    water_tier = tier_for(item_id, lines, WATER)
    if water_tier != -1:
        return water_tier * 2
    if style == RANGED:
        air_tier = tier_for(item_id, lines, AIR)
        return 0 if air_tier == -1 else air_tier * 3
    if style == MELEE:
        earth_tier = tier_for(item_id, lines, EARTH)
        return 0 if earth_tier == -1 else earth_tier * 3
    if style == MAGIC:
        fire_tier = tier_for(item_id, lines, FIRE)
        return 0 if fire_tier == -1 else fire_tier * 3
    return 0


def elemental_defense(item_id: int, style: str) -> int:
    water_tier = tier_for(item_id, STANDARD_NECKLACES, WATER)
    if water_tier != -1:
        return water_tier * 2
    if style == RANGED:
        air_tier = tier_for(item_id, STANDARD_NECKLACES, AIR)
        return 0 if air_tier == -1 else air_tier * 3
    if style == MELEE:
        earth_tier = tier_for(item_id, STANDARD_NECKLACES, EARTH)
        return 0 if earth_tier == -1 else earth_tier * 3
    if style == MAGIC:
        fire_tier = tier_for(item_id, STANDARD_NECKLACES, FIRE)
        return 0 if fire_tier == -1 else fire_tier * 3
    return 0


def law_item_max_charges(item_id: int) -> int:
    if tier_for(item_id, SPECIAL_AMULETS, LAW) != -1:
        return 3
    item_tier = tier_for(item_id, SPECIAL_RINGS, LAW)
    if item_tier == -1:
        item_tier = tier_for(item_id, STANDARD_NECKLACES, LAW)
    return 0 if item_tier == -1 else LAW_BANKING_CHARGES[item_tier - 1]


def ensure_formula_source_matches_design() -> None:
    require(SPECIAL_RINGS[CHAOS] == [ITEM_IDS["RING_OF_RECOIL"], 1693, 1694, 1695, 1696],
            "Chaos ring line should keep the vanilla recoil ring as tier 1")
    require(SPECIAL_RINGS[NATURE] == [ITEM_IDS["RING_OF_FORGING"], 1697, 1698, 1699, 1700],
            "Nature ring line should keep the vanilla forging ring as tier 1")
    require(SPECIAL_RINGS[COSMIC][-1] == ITEM_IDS["DRAGONSTONE_RING_OF_FORTUNE"],
            "Cosmic ring line should end on the dragonstone fortune ring")
    require(SPECIAL_RINGS[SOUL][3] == ITEM_IDS["RING_OF_LIFE"],
            "Soul ring line should preserve the vanilla ring of life at diamond tier")

    require(elemental_power(1673, RANGED) == 3, "Sapphire air ring should give +3 ranged power")
    require(elemental_power(1677, RANGED) == 15, "Dragonstone air ring should give +15 ranged power")
    require(elemental_power(1687, MELEE) == 15, "Dragonstone earth ring should give +15 melee power")
    require(elemental_power(1692, MAGIC) == 15, "Dragonstone fire ring should give +15 magic power")
    require(elemental_power(1627, RANGED) == 0, "Dragonstone water necklace should not retain power bonus")

    require(elemental_defense(1617, RANGED) == 15, "Dragonstone air necklace should give +15 ranged defense")
    require(elemental_defense(1632, MELEE) == 15, "Dragonstone earth necklace should give +15 melee defense")
    require(elemental_defense(1637, MAGIC) == 15, "Dragonstone fire necklace should give +15 magic defense")
    require(elemental_defense(1627, RANGED) == 10, "Dragonstone water necklace should give +10 ranged defense")
    require(elemental_defense(1627, MELEE) == 10, "Dragonstone water necklace should give +10 melee defense")
    require(elemental_defense(1627, MAGIC) == 10, "Dragonstone water necklace should give +10 magic defense")

    require(CHAOS_RECOIL_CHANCES == [0.10, 0.20, 0.30, 0.50, 0.90],
            "Chaos recoil should use the requested non-linear chance curve")
    require(CHAOS_CHAIN_LIGHTNING_CHANCES == [0.10, 0.20, 0.30, 0.50, 0.90],
            "Chaos chain lightning should use the requested non-linear chance curve")
    near(CHAOS_RECOIL_CHANCES[tier_for(1314, SPECIAL_RINGS, CHAOS) - 1], 0.10, "Sapphire chaos ring recoil chance")
    near(CHAOS_RECOIL_CHANCES[tier_for(1696, SPECIAL_RINGS, CHAOS) - 1], 0.90, "Dragonstone chaos ring recoil chance")
    near(CHAOS_CHAIN_LIGHTNING_CHANCES[tier_for(1648, STANDARD_NECKLACES, CHAOS) - 1], 0.10, "Sapphire chaos necklace chain chance")
    near(CHAOS_CHAIN_LIGHTNING_CHANCES[tier_for(1652, STANDARD_NECKLACES, CHAOS) - 1], 0.90, "Dragonstone chaos necklace chain chance")
    require(CHAOS_AMULET_YIELD_BONUS_PERCENTS == [20, 35, 50, 70, 100],
            "Chaos amulets should use the requested weighted-rune yield bonuses")
    require(CHAOS_AMULET_BONUS_RUNE_WEIGHTS == [
        [50, 25, 20, 5],
        [42, 27, 23, 8],
        [35, 28, 25, 12],
        [27, 29, 28, 16],
        [20, 30, 30, 20],
    ], "Chaos amulets should shift from mind-heavy to higher-rune-heavy weights")

    near(tier_for(1701, SPECIAL_RINGS, COSMIC) * 0.05, 0.05, "Sapphire cosmic ring wealth chance")
    near(tier_for(3111, SPECIAL_RINGS, COSMIC) * 0.05, 0.25, "Dragonstone cosmic ring wealth chance")
    near(tier_for(1647, STANDARD_NECKLACES, COSMIC) * 0.10, 0.50, "Dragonstone cosmic necklace extra standard-drop chance")
    near(tier_for(1753, SPECIAL_AMULETS, COSMIC) * 0.10, 0.50, "Dragonstone cosmic amulet rare-gathering double chance")

    for item_id in SPECIAL_AMULETS[LAW]:
        require(law_item_max_charges(item_id) == 3, f"Law amulet {item_id} should have 3 teleport charges")
    require(LAW_BANKING_CHARGES == [100, 200, 300, 500, 1000],
            "Law banking jewelry should use the requested charge ladder")
    require(law_item_max_charges(1714) == 100, "Sapphire law ring should have 100 banking charges")
    require(law_item_max_charges(1718) == 1000, "Dragonstone law ring should have 1000 banking charges")
    require(law_item_max_charges(1658) == 100, "Sapphire law necklace should have 100 banking charges")
    require(law_item_max_charges(1662) == 1000, "Dragonstone law necklace should have 1000 banking charges")

    require(BLOOD_RING_HITS_BONUSES == [2, 4, 6, 10, 20],
            "Blood ring should use the requested max-Hits ladder")
    require(BLOOD_RING_HITS_BONUSES[tier_for(3095, SPECIAL_RINGS, BLOOD) - 1] == 20,
            "Dragonstone blood ring should give +20 hits")
    require(BLOOD_NECKLACE_LEACH_PERCENTS == [0.10, 0.20, 0.30, 0.50, 1.00],
            "Blood necklace should use the requested Leach ladder")
    near(BLOOD_NECKLACE_LEACH_PERCENTS[tier_for(1672, STANDARD_NECKLACES, BLOOD) - 1], 1.00,
         "Dragonstone blood necklace should Leach 100 percent of poison damage")
    near(tier_for(1733, SPECIAL_AMULETS, BLOOD) * 0.05, 0.25, "Dragonstone blood amulet lifesteal chance")

    require(DEATH_RING_CHARGE_CAPS == [20, 30, 40, 60, 100],
            "Death rings should use the requested charge caps")
    require(DEATH_RING_CHARGE_CAPS[tier_for(3086, SPECIAL_RINGS, DEATH) - 1] == 20,
            "Sapphire death ring should cap at 20 charge")
    require(DEATH_RING_CHARGE_CAPS[tier_for(3090, SPECIAL_RINGS, DEATH) - 1] == 100,
            "Dragonstone death ring should cap at 100 charge")
    require(DEATH_NECKLACE_GUARANTEED_DROP_BONUS_CHANCES == [0.25, 0.40, 0.60, 0.90, 1.00],
            "Death necklaces should use the requested guaranteed-drop bonus chance ladder")
    near(DEATH_NECKLACE_GUARANTEED_DROP_BONUS_CHANCES[tier_for(1667, STANDARD_NECKLACES, DEATH) - 1], 1.00,
         "Dragonstone death necklace should guarantee +1 on eligible guaranteed drops")
    require(tier_for(1724, SPECIAL_AMULETS, DEATH) == 1, "Sapphire death amulet should be tier 1")
    require(tier_for(1728, SPECIAL_AMULETS, DEATH) == 5, "Dragonstone death amulet should be tier 5")

    require(MIND_BODY_JEWELRY_XP_BONUSES == [5, 10, 15, 25, 50],
            "Mind and Body jewelry should use the requested non-linear XP curve")
    near(mind_body_jewelry_xp_bonus(1621, STANDARD_NECKLACES, MIND), 0.25, "Diamond mind necklace XP chance")
    near(mind_body_jewelry_xp_bonus(1622, STANDARD_NECKLACES, MIND), 0.50, "Dragonstone mind necklace XP chance")
    near(mind_body_jewelry_xp_bonus(3079, SPECIAL_RINGS, MIND), 0.25, "Diamond mind ring XP chance")
    near(mind_body_jewelry_xp_bonus(3080, SPECIAL_RINGS, MIND), 0.50, "Dragonstone mind ring XP chance")
    near(mind_body_jewelry_xp_bonus(1738, SPECIAL_AMULETS, MIND), 0.50, "Dragonstone mind amulet combat XP chance")
    near(mind_body_jewelry_xp_bonus(1641, STANDARD_NECKLACES, BODY), 0.25, "Diamond body necklace XP chance")
    near(mind_body_jewelry_xp_bonus(1642, STANDARD_NECKLACES, BODY), 0.50, "Dragonstone body necklace XP chance")
    near(mind_body_jewelry_xp_bonus(3084, SPECIAL_RINGS, BODY), 0.25, "Diamond body ring XP chance")
    near(mind_body_jewelry_xp_bonus(3085, SPECIAL_RINGS, BODY), 0.50, "Dragonstone body ring XP chance")
    near(mind_body_jewelry_xp_bonus(1743, SPECIAL_AMULETS, BODY), 0.50, "Dragonstone body amulet discipline XP chance")

    near(nature_food_bonus(1657), 0.0, "Dragonstone nature necklace should no longer give food bonus")
    near(nature_food_bonus(1700), 1.00, "Dragonstone nature ring food bonus")
    near(nature_food_bonus(1748), 0.0, "Dragonstone nature amulet should not retain stale food bonus")
    require(tier_for(1657, STANDARD_NECKLACES, NATURE) == 5, "Dragonstone nature necklace should add 5 poison decay power")
    require(NATURE_ALCHEMY_AMULET_CHARGES == [100, 200, 300, 500, 1000],
            "Nature alchemy amulets should use the requested charge ladder")
    require(GATHERING_AMULET_YIELD_BONUSES == [10, 20, 30, 50, 100],
            "Gathering amulets should use the requested non-linear yield curve")

    require(SOUL_RING_SURVIVAL_CHANCES == [0.10, 0.20, 0.30, 0.50, 0.90],
            "Soul rings should use the requested lifesaving survival ladder")
    near(SOUL_RING_SURVIVAL_CHANCES[tier_for(1708, SPECIAL_RINGS, SOUL) - 1], 0.90,
         "Dragonstone soul ring should have 90 percent non-break chance")
    require(SOUL_NECKLACE_EXTRA_KEPT_ITEMS == [1, 2, 3, 5, 8],
            "Soul necklaces should use the requested extra-kept-items ladder")
    require(SOUL_NECKLACE_EXTRA_KEPT_ITEMS[tier(1763, SOUL_NECKLACES) - 1] == 8,
            "Dragonstone soul necklace should keep 8 extra death items")
    require(DEATH_AMULET_BURST_MIN_DAMAGE == [1, 3, 6, 9, 10],
            "Death amulets should use the requested Burst minimum damage ladder")
    require(DEATH_AMULET_BURST_MAX_DAMAGE == [3, 6, 9, 14, 20],
            "Death amulets should use the requested Burst maximum damage ladder")
    require(SOUL_AMULET_BURST_MIN_HEAL == [1, 1, 2, 3, 5],
            "Soul amulets should use the requested Burst minimum healing ladder")
    require(SOUL_AMULET_BURST_MAX_HEAL == [2, 3, 4, 6, 10],
            "Soul amulets should use the requested Burst maximum healing ladder")

    require(tier(3105, LIFE_NECKLACES) * 10 == 50, "Dragonstone life necklace should add 50% summon health")
    require(tier_for(3100, SPECIAL_RINGS, LIFE) * 20 == 100, "Dragonstone life ring should double support-summon duration")
    require(tier_for(3110, SPECIAL_AMULETS, LIFE) == 5, "Dragonstone life amulet should add +5 summon max damage")


def ensure_leach_calculation_behavior() -> None:
    sources = {
        "com/openrsc/server/content/Leach.java": LEACH.read_text(encoding="utf-8"),
        "com/openrsc/server/constants/Skill.java": """
package com.openrsc.server.constants;
public enum Skill {
    HITS;
    public int id() { return 0; }
}
""",
        "com/openrsc/server/model/entity/player/Skills.java": """
package com.openrsc.server.model.entity.player;
public final class Skills {
    private int current;
    private final int maximum;
    public Skills(int current, int maximum) { this.current = current; this.maximum = maximum; }
    public int getMaxStat(int skill) { return maximum; }
    public int getLevel(int skill) { return current; }
    public void setLevel(int skill, int value) { current = value; }
}
""",
        "com/openrsc/server/model/entity/player/UpdateFlags.java": """
package com.openrsc.server.model.entity.player;
import com.openrsc.server.model.entity.update.HitSplat;
public final class UpdateFlags {
    public void addHitSplat(HitSplat hitSplat) {}
}
""",
        "com/openrsc/server/model/entity/player/Player.java": """
package com.openrsc.server.model.entity.player;
public final class Player {
    private final Skills skills;
    private final UpdateFlags flags = new UpdateFlags();
    private final int healingMaximum;
    private boolean removed;
    public Player(int current, int maximum) { this(current, maximum, maximum); }
    public Player(int current, int maximum, int healingMaximum) {
        skills = new Skills(current, maximum);
        this.healingMaximum = healingMaximum;
    }
    public boolean isRemoved() { return removed; }
    public void setRemoved(boolean value) { removed = value; }
    public Skills getSkills() { return skills; }
    public UpdateFlags getUpdateFlags() { return flags; }
    public int getHealingMaximumHits() { return healingMaximum; }
}
""",
        "com/openrsc/server/model/entity/update/HitSplat.java": """
package com.openrsc.server.model.entity.update;
import com.openrsc.server.model.entity.player.Player;
public final class HitSplat {
    public static final int TYPE_HEAL = 1;
    public HitSplat(Player player, int type, int amount) {}
}
""",
        "com/openrsc/server/net/rsc/ActionSender.java": """
package com.openrsc.server.net.rsc;
import com.openrsc.server.model.entity.player.Player;
public final class ActionSender {
    public static void sendStat(Player player, int skill) {}
}
""",
        "LeachCalculationFixture.java": """
import com.openrsc.server.content.Leach;
import com.openrsc.server.model.entity.player.Player;

public final class LeachCalculationFixture {
    private static void equal(int actual, int expected, String label) {
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + ", found " + actual);
        }
    }

    public static void main(String[] args) {
        equal(Leach.calculateHealing(0, 0.25D, 10), 0, "zero damage");
        equal(Leach.calculateHealing(10, 0.0D, 10), 0, "zero percent");
        equal(Leach.calculateHealing(10, 0.25D, 0), 0, "full health");
        equal(Leach.calculateHealing(1, 0.05D, 10), 1, "positive minimum");
        equal(Leach.calculateHealing(19, 0.25D, 20), 4, "flooring");
        equal(Leach.calculateHealing(20, 0.25D, 20), 5, "exact percentage");
        equal(Leach.calculateHealing(100, 1.0D, 3), 3, "missing Hits cap");

        Player player = new Player(7, 10);
        equal(Leach.heal(player, 100, 1.0D), 3, "runtime heal cap");
        equal(player.getSkills().getLevel(0), 10, "runtime maximum Hits");
        player.setRemoved(true);
        equal(Leach.heal(player, 10, 1.0D), 0, "removed player");

        Player boosted = new Player(99, 99, 109);
        equal(Leach.heal(boosted, 20, 1.0D), 10, "temporary maximum heal cap");
        equal(boosted.getSkills().getLevel(0), 109, "temporary maximum Hits");
    }
}
""",
    }
    with tempfile.TemporaryDirectory(prefix="leach-regression-") as temp_directory:
        root = Path(temp_directory)
        for relative_path, source in sources.items():
            path = root / relative_path
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source.strip() + "\n", encoding="utf-8")
        (root / "classes").mkdir()
        java_files = sorted(str(path) for path in root.rglob("*.java"))
        compile_result = subprocess.run(
            ["javac", "-d", str(root / "classes"), *java_files],
            text=True,
            capture_output=True,
            check=False,
        )
        require(
            compile_result.returncode == 0,
            f"Leach behavior fixture did not compile:\n{compile_result.stderr}",
        )
        run_result = subprocess.run(
            ["java", "-cp", str(root / "classes"), "LeachCalculationFixture"],
            text=True,
            capture_output=True,
            check=False,
        )
        require(
            run_result.returncode == 0,
            f"Leach behavior fixture failed:\n{run_result.stderr}",
        )


def ensure_runtime_paths_are_wired() -> None:
    effects = EFFECTS.read_text(encoding="utf-8")
    item_defs_custom = ITEM_DEFS_CUSTOM.read_text(encoding="utf-8")
    client_entity_handler = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    npc = NPC.read_text(encoding="utf-8")
    drop_table = DROP_TABLE.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")
    mob = MOB.read_text(encoding="utf-8")
    poison_event = POISON_EVENT.read_text(encoding="utf-8")
    corrosive_aura = CORROSIVE_AURA.read_text(encoding="utf-8")
    leach = LEACH.read_text(encoding="utf-8")
    eating = EATING.read_text(encoding="utf-8")
    summoning = SUMMONING.read_text(encoding="utf-8")
    combat_event = COMBAT_EVENT.read_text(encoding="utf-8")
    pvm_melee = PVM_MELEE_EVENT.read_text(encoding="utf-8")
    projectile_event = PROJECTILE_EVENT.read_text(encoding="utf-8")
    chain_traversal = CHAIN_TRAVERSAL.read_text(encoding="utf-8")
    law_jewelry = LAW_JEWELRY.read_text(encoding="utf-8")
    nature_alchemy_amulet = NATURE_ALCHEMY_AMULET.read_text(encoding="utf-8")
    fishing = FISHING.read_text(encoding="utf-8")
    mining = MINING.read_text(encoding="utf-8")
    woodcutting = WOODCUTTING.read_text(encoding="utf-8")
    harvesting = HARVESTING.read_text(encoding="utf-8")

    require("public static double getCosmicAmuletExtraResourceChance" in effects
            and "return 0.0D;" in effects,
            "Cosmic amulet should not inflate base resource gather chances")
    require("getCosmicItemMaxCharges" not in effects
            and "COSMIC_BANKING_CHARGES_CACHE_PREFIX" not in effects,
            "Cosmic jewelry should remain passive bonus jewelry, not charged jewelry")
    require("getChaosRecoilChance()" in equipment
            and "return ringItem == null ? 0.0D : EnchantingItemEffects.getChaosRingRecoilChance(ringItem.getCatalogId());" in equipment,
            "Chaos recoil should come only from the ring")
    require_regex(equipment, r"getChaosRecoilDamageDivisor\(\)\s*\{\s*return 4;\s*\}",
                  "Chaos recoil should reflect 25% damage on successful procs")
    require("getChaosAmuletYieldBonusPercent()" in equipment
            and "EnchantingItemEffects.getChaosAmuletYieldBonusPercent(wristItem.getCatalogId())" in equipment
            and "getChaosAmuletBonusRuneWeights()" in equipment
            and "EnchantingItemEffects.getChaosAmuletBonusRuneWeights(wristItem.getCatalogId())" in equipment,
            "Chaos Bangle weighted-rune yield data should be exposed through equipped wrist items")
    require_regex(equipment, r"getEquippedElementalPowerBonus.*?getElementalPowerBonus\(ringItem",
                  "Elemental power should come from the equipped ring")
    require_regex(equipment, r"getEquippedElementalDefenseBonus.*?getElementalDefenseBonus\(neckItem",
                  "Elemental defense should come from the equipped Necklace neck slot")

    require("getWealthChance(owner)" in drop_table, "Cosmic rings should feed drop-table wealth chance")
    drop_roll = java_method_body(
        drop_table,
        "private RollResult rollItem(double wealthChance, Player owner, boolean allowExtraRoll, "
        "double contributionScale, boolean scaleRareNormalDrops, boolean suppressRareTables)",
    )
    wealth_guard_match = re.search(
        r"if\s*\((.*?)\)\s*\{\s*owner\s*\.\s*playerServerMessage\s*"
        r"\(\s*MessageType\s*\.\s*QUEST\s*,\s*\"@ora@Your ring of wealth shines brightly!\"\s*\)",
        drop_roll,
        flags=re.S,
    )
    require(wealth_guard_match is not None,
            "Cosmic ring retry should remain inside the wealth notification guard")
    wealth_guard = wealth_guard_match.group(1)
    for pattern, message in (
        (r"\ballowExtraRoll\b", "Cosmic ring retry should require extra rolls to be enabled"),
        (r"!\s*result\s*\.\s*receivedRareTableReward\b",
         "Cosmic ring retry should require the primary roll to miss rare-table rewards"),
        (r"wealthChance\s*>\s*0(?:\.0+)?D?\b",
         "Cosmic ring retry should require a positive wealth chance"),
        (r"random\s*\.\s*nextDouble\s*\(\s*\)\s*<\s*wealthChance\b",
         "Cosmic ring retry should require the random roll to pass"),
    ):
        require_regex(wealth_guard, pattern, message)
    require_regex(
        drop_roll,
        r"result\s*\.\s*merge\s*\(\s*rollRareTableChance\s*"
        r"\(\s*owner\s*,\s*contributionScale\s*\)\s*\)\s*;",
        "Cosmic rings should only retry the monster rare-table chance",
    )
    require("drop.type == dropType.TABLE && drop.table.isRare()" in drop_table,
            "Ring of wealth retry should ignore standard monster-table drops")
    require("result.hitRareTable = true;" in drop_table,
            "DropTable should track when a roll reaches a rare table")
    require("If a monster rare table misses, has a 5% chance to reroll the drop." in item_defs_custom,
            "Sapphire cosmic ring examine should describe rare-table miss rerolls")
    require("If a monster rare table misses, has a 25% chance to reroll the drop." in item_defs_custom,
            "Dragonstone cosmic ring examine should describe rare-table miss rerolls")
    require("If a monster rare table misses, has a %d%% chance to reroll the drop." in client_entity_handler,
            "Client cosmic ring examine generator should describe rare-table miss rerolls")
    require("If a monster rare table misses, has a 25% chance to reroll the drop." in client_entity_handler,
            "Client dragonstone cosmic ring examine should describe rare-table miss rerolls")
    require("getCosmicNecklaceStandardDropChance(owner)" in drop_table,
            "Cosmic necklaces should feed extra standard drop rolls")
    require("suppressRareTables" in drop_table and "Your cosmic necklace gleams." in drop_table,
            "Cosmic necklace extra rolls should suppress rare-table access")

    for path, text in (
        (FISHING, FISHING.read_text(encoding="utf-8")),
        (MINING, MINING.read_text(encoding="utf-8")),
        (WOODCUTTING, WOODCUTTING.read_text(encoding="utf-8")),
        (HARVESTING, HARVESTING.read_text(encoding="utf-8")),
    ):
        require("maybeDoubleRareGatheringReward" in text
                and "getCosmicAmuletRareGatheringDoubleChance" in text,
                f"Cosmic amulet rare-gathering double path missing from {path.name}")

    require("POWER_DRAIN_PER_TICK = 3" in poison_event, "Poison base drain should remain 3 power per tick")
    require("poisonDrain += player.getCarriedItems().getEquipment().getNatureCleansingPoisonDecayBonus();" in poison_event,
            "Nature cleansing necklace should add to poison tick drain")
    require_regex(equipment, r"getNatureCleansingPoisonDecayBonus.*?getNatureCleansingPoisonDecayBonus\(neckItem",
                  "Nature cleansing should come from the equipped Necklace neck slot")
    require("getNatureFoodHealingBonus" in eating and "Math.ceil(totalHeal * (1.0D + foodBonus))" in eating,
            "Nature jewelry should increase food healing")
    require("getNatureFoodHealingBonus()" in equipment
            and "getNatureFoodHealingBonus(ringItem.getCatalogId())" in equipment,
            "Nature ring should provide food healing")
    require("getNatureFoodHealingBonus(neckItem.getCatalogId())" not in equipment
            and "Math.max(neckBonus, ringBonus)" not in equipment,
            "Nature necklace should not continue providing food healing")
    require("public static int getNatureAlchemyAmuletMaxCharges" in effects
            and "getNatureAlchemyAmuletCharges(final Player player, final Item item)" in effects
            and "setNatureAlchemyAmuletCharges(final Player player, final Item item, final int charges)" in effects
            and "NATURE_ALCHEMY_CHARGES_CACHE_PREFIX" in effects,
            "Nature alchemy amulet charges should be account-backed")
    require("tryAlchemyMonsterLootWithNatureAmulet" in equipment
            and "EnchantingItemEffects.getNatureAlchemyAmuletCharges(player, wristItem)" in equipment
            and "EnchantingItemEffects.setNatureAlchemyAmuletCharges(player, wristItem, charges)" in equipment
            and "getHighAlchemyValue(itemDef, item.getAmount())" in equipment
            and "alchemyValue < 1000" in equipment
            and "new Item(ItemId.COINS.id(), alchemyValue)" in equipment
            and "player.getCarriedItems().getInventory().canHold(coins)" in equipment
            and "remove(wristItem, 1, true);" not in equipment,
            "Nature alchemy Bangle should auto-alch valuable monster drops without breaking")
    require("tryAlchemyMonsterLootWithNatureAmulet(new Item(dropID, amount))" in npc
            and "tryAlchemyMonsterLootWithNatureAmulet(new Item(dropID, amount, item.getNoted()))" in npc
            and "tryAlchemyMonsterLootWithNatureAmulet(item)" in npc,
            "NPC monster drop paths should offer drops to the nature alchemy amulet")
    require("implements OpInvTrigger, UseLocTrigger" in nature_alchemy_amulet
            and '"check".equalsIgnoreCase(command)' in nature_alchemy_amulet
            and "EnchantingItemEffects.NATURE_ALTAR" in nature_alchemy_amulet
            and "ItemId.NATURE_RUNE.id()" in nature_alchemy_amulet
            and "getRechargeRuneCost" in nature_alchemy_amulet
            and "return Math.max(10, ((maxCharges + 99) / 100) * 10);" in nature_alchemy_amulet
            and "setNatureAlchemyAmuletCharges(player, item, maxCharges)" in nature_alchemy_amulet,
            "Nature alchemy amulet should support Check and full-capacity nature-rune recharging")
    for snippet in (
        'GATHERING_AMULET_YIELD_CACHE_PREFIX = "gathering_amulet_yield_bonus_"',
        "GATHERING_AMULET_YIELD_CACHE_PREFIX + skillId + \"_\" + itemId",
        "itemCount * bonusPercent * GATHERING_AMULET_YIELD_POINTS_PER_PERCENT",
        "totalPoints / GATHERING_AMULET_YIELD_POINTS_PER_ITEM",
        "Skill.WOODCUTTING.id()",
        "Skill.FISHING.id()",
        "Skill.HARVESTING.id()",
        "Skill.MINING.id()",
    ):
        require(snippet in effects, f"Gathering amulet shared carryover missing: {snippet}")
    for text, snippets, label in (
        (woodcutting, (
            "int rewardQuantity = quantity + addGatheringAmuletBonusLogs(player, def.getLogId(), quantity);",
            "Skill.WOODCUTTING.id(), logId, logCount",
            "bankSkillingDropWithLawRing(new Item(def.getLogId(), rewardQuantity))",
        ), "woodcutting"),
        (mining, (
            "int rewardQuantity = quantity + addGatheringAmuletBonusOre(player, ore.getCatalogId(), quantity);",
            "Skill.MINING.id(), oreId, oreCount",
            "bankSkillingDropWithLawRing(new Item(ore.getCatalogId(), rewardQuantity))",
        ), "mining"),
        (fishing, (
            "final int rewardQuantity = 1 + addGatheringAmuletBonusFish(player, fish.getCatalogId(), 1);",
            "Skill.FISHING.id(), fishId, fishCount",
            "awardFishingCatch(player, object, fish, true);",
        ), "fishing"),
        (harvesting, (
            "int rewardQuantity = quantity + addGatheringAmuletBonusProduce(player, produce.getCatalogId(), quantity);",
            "Skill.HARVESTING.id(), produceId, produceCount",
            "bankSkillingDropWithLawRing(new Item(produce.getCatalogId(), rewardQuantity))",
        ), "harvesting"),
    ):
        for snippet in snippets:
            require(snippet in text, f"Gathering amulet carryover missing from {label}: {snippet}")

    require("getMindJewelryXpBonus(skill)" in player and "getBodyJewelryXpBonus(skill)" in player,
            "Mind and body jewelry XP bonuses should apply through the unified skill-aware path")
    require("isMindNecklaceXpSkill(skillId)" in equipment and "getMindNecklaceXpBonus(neckItem.getCatalogId())" in equipment,
            "Mind necklaces should apply only to their mapped skills")
    require("isMindRingXpSkill(skillId)" in equipment and "getMindRingXpBonus(ringItem.getCatalogId())" in equipment,
            "Mind rings should apply only to their mapped skills")
    require("isBodyNecklaceXpSkill(skillId)" in equipment and "getBodyNecklaceXpBonus(neckItem.getCatalogId())" in equipment,
            "Body necklaces should apply only to their mapped skills")
    require("isBodyRingXpSkill(skillId)" in equipment and "getBodyRingXpBonus(ringItem.getCatalogId())" in equipment,
            "Body rings should apply only to their mapped skills")
    require("isMindCombatAmuletXpSkill(skillId)" in equipment and "getMindCombatAmuletXpBonus(wristItem.getCatalogId())" in equipment,
            "Mind Bangles should apply only to their mapped skills")
    require("isBodyCombatAmuletXpSkill(skillId)" in equipment and "getBodyDisciplineAmuletXpBonus(wristItem.getCatalogId())" in equipment,
            "Body Bangles should apply only to their mapped skills")

    require("public final class Leach" in leach
            and "calculateHealing" in leach
            and "HitSplat.TYPE_HEAL" in leach,
            "Leach should be a named reusable heal-from-damage effect")
    require("applyBloodAmuletLifesteal" in player and "Leach.heal(this, damageDealt, lifestealPercent);" in player,
            "Blood amulet lifesteal should use the shared Leach helper")
    require("Math.max(1, (int) Math.floor(damageDealt * leachPercent))" in leach
            and "Math.min(healing, missingHits)" in leach,
            "Leach healing should round down, guarantee one for positive damage, and cap to missing Hits")
    require("syncHitsEquipmentBonuses" in player and "getBloodRingHitsBonus" in player,
            "Blood ring should synchronize max Hits bonuses")
    require("getBloodNecklaceLeachPercent" in equipment
            and "getBloodNecklaceLeachPercent(neckItem.getCatalogId())" in equipment,
            "Blood necklace should expose the Leach percentage")
    require("damageAndGetActualDamage(final int damage, final int hitSplatType)" in mob
            and "final int actualDamage = Math.min(Math.max(0, appliedDamage), currentHp);" in mob,
            "Damage callers that own follow-up effects should be able to use post-mitigation, overkill-capped damage")
    require("setPoisonOwnerId" in poison_event
            and "getBloodNecklaceLeachPercent()" in poison_event
            and "final int actualDamage = settleTypedPoisonDamage(damage);" in poison_event
            and 'DamageRequest.SourceCategory.DOT, "generic-poison"' in poison_event
            and "getResolvedDamageTransaction().apply(request)" in poison_event
            and "applyLeach(actualDamage);" in poison_event
            and "Leach.heal(poisonOwner, damage, leachPercent);" in poison_event,
            "Typed poison ticks should Leach their factual applied damage to the player source when equipped")
    require("attacker.applyPoison(poisonPower, attacker.getCurrentPoisonPower() + poisonPower, defender);" in corrosive_aura,
            "Corrosive Aura should attribute its poison to the defending player who owns the effect")
    require("applyBloodAmuletLifesteal" not in poison_event
            and "applyBloodAmuletLifesteal" not in corrosive_aura
            and "getBloodNecklaceLeachPercent" not in corrosive_aura,
            "Corrosive poison should use the poison-owner Leach path without also triggering direct-hit Siphoning")

    combat_direct = java_method_body(
        combat_event,
        "private void inflictDamage(final Mob hitter, final Mob target, int damage)",
    )
    pvm_direct = java_method_body(
        pvm_melee,
        "private void inflictDamage(final Mob hitter, final Mob target, int damage)",
    )
    projectile_direct = java_method_body(projectile_event, "private void projectileDamage()")
    scythe_cleave = java_method_body(
        pvm_melee,
        "private void inflictScytheCleaveDamage(final Player player, final Npc npc, int damage)",
    )
    projectile_chain = java_method_body(
        projectile_event,
        "private void inflictChainLightningDamage(final Player casterPlayer, final Mob chainTarget, int chainDamage)",
    )
    blood_robe_splash = java_method_body(
        projectile_event,
        "private void applyBloodRobeSplash(final Player casterPlayer, final int damageDealt)",
    )
    for body, label in (
        (combat_direct, "standard melee/PvP"),
        (pvm_direct, "PvM melee"),
    ):
        require("if (hitter.isPlayer())" in body
                and "applyBloodAmuletLifesteal(damageDealt);" in body
                and body.count("applyBloodAmuletLifesteal(") == 1,
                f"Siphoning should activate exactly once from actual direct damage in {label}")
    require("isPrimaryProjectileAttackType()" in projectile_direct
            and "applyBloodAmuletLifesteal(damageDealt);" in projectile_direct
            and "applyBloodAmuletLifesteal(damage);" not in projectile_direct
            and projectile_direct.count("applyBloodAmuletLifesteal(") == 1,
            "Siphoning should cover ranged and magic primary projectiles exactly once using actual damage")
    require("applyBloodAmuletLifesteal(damageDealt);" in scythe_cleave
            and scythe_cleave.count("applyBloodAmuletLifesteal(") == 1,
            "Siphoning should cover each actual scythe cleave hit exactly once")
    require("applyBloodAmuletLifesteal" not in projectile_chain
            and "applyBloodAmuletLifesteal" not in blood_robe_splash
            and "applyBloodAmuletLifesteal" not in summoning,
            "Siphoning should not recurse through chain, blood-robe splash, or summon damage")
    require(
        (combat_event + pvm_melee + projectile_event).count("applyBloodAmuletLifesteal(") == 4,
        "Siphoning should have only direct melee, projectile, and explicit scythe-cleave activation points",
    )
    require(
        (player + poison_event).count("Leach.heal(") == 2,
        "Shared Leach healing should have only the Siphoning and owned-poison entry points",
    )
    require("applyDeathAmuletBurst" in player
            and "getDeathAmuletBurstChargePointsForNpc" in player
            and "setDeathAmuletBurstChargePoints(this, deathAmulet" in player
            and "getDeathAmuletBurstMinDamage(deathAmulet.getCatalogId())" in player
            and "HitSplat.TYPE_ARMOR_PROC" in player
            and "Summoning.isSummon(npc)" in player
            and "owner.applyDeathAmuletBurst(this);" in npc,
            "Death amulet should charge persistently from NPC kills and fire Burst damage")
    require("applyDeathRingChargeHit" in player
            and "chargeDeathRingFromKill" in player
            and "tickDeathRingChargeDecay" in player
            and "getDeathRingChargeDamage(this, deathRing)" in player
            and "HitSplat.TYPE_ARMOR_PROC" in player,
            "Death ring should use persistent charge for yellow bonus damage")
    require("DEATH_RING_CHARGE_CACHE_PREFIX" in effects
            and "getDeathRingChargeCacheKey(final int itemId)" in effects
            and "getDeathRingChargePoints(final Player player, final Item item)" in effects
            and "setDeathRingChargePoints(final Player player, final Item item, final int chargePoints)" in effects
            and "decayDeathRingCharges(final Player player, final int chargePoints)" in effects,
            "Death ring charge should use player-cache storage instead of item variants")
    require("chargeDeathRingFromKill(this);" in npc,
            "NPC deaths should charge equipped Death rings")
    require("player.tickDeathRingChargeDecay();" in (ROOT / "server/src/com/openrsc/server/event/rsc/impl/StatRestorationEvent.java").read_text(encoding="utf-8"),
            "Death ring charge should decay from the player tick path")
    require("applyDeathRingChargeHit" in combat_event
            and "applyDeathRingChargeHit" in pvm_melee
            and "applyDeathRingChargeHit" in projectile_event,
            "Death ring yellow damage should be wired into melee and projectile combat")
    require("rollDeathNecklaceGuaranteedDropBonus" in equipment
            and "getDeathNecklaceGuaranteedDropBonusChance(neckItem.getCatalogId())" in equipment
            and "getDeathNecklaceGuaranteedDropExtraChance(neckItem.getCatalogId())" in equipment,
            "Death necklace should roll the guaranteed-drop bonus from equipment")
    require("getDeathNecklaceLowHealthDefenseBonus" not in equipment
            and "getDeathNecklaceLowHealthDefenseBonus" not in effects,
            "Death necklace should no longer provide low-health defenses")
    require("applyDeathNecklaceGuaranteedDropBonus(owner, new Item(bones, 1))" in npc
            and "item = applyDeathNecklaceGuaranteedDropBonus(owner, item);" in npc
            and "Your death necklace shines brightly" in npc,
            "Death necklace should boost bones and invariable guaranteed NPC drops")
    inventory = (ROOT / "server/src/com/openrsc/server/model/container/Inventory.java").read_text(encoding="utf-8")
    require("getSoulNecklaceExtraKeptItems" in equipment
            and "getSoulNecklaceExtraKeptItems()" in inventory,
            "Soul necklace should feed death item retention")
    require("checkRingOfLife" in player
            and "getRingItem()" in player
            and "isSoulRing" in player
            and "getSoulRingSurvivalChance" in player,
            "Soul ring should own the life-saving break chance")
    require("applySoulAmuletBurst" in player
            and "getSoulAmuletBurstChargePointsForNpc" in player
            and "setSoulAmuletBurstChargePoints(this, soulAmulet" in player
            and "getSoulAmuletBurstMinHeal(soulAmulet.getCatalogId())" in player
            and "healSoulBurstTarget" in player
            and "HitSplat.TYPE_HEAL" in player
            and "owner.applySoulAmuletBurst(this);" in npc,
            "Soul amulet should charge persistently from NPC kills and fire Renewal healing")

    require("getLifeNecklaceSummonHealthPercent" in summoning,
            "Life necklace should increase combat summon health")
    require("getLifeRingSupportDurationPercent" in summoning,
            "Life ring should extend support summon duration")
    require("getLifeAmuletSummonMaxDamageBonus" in summoning,
            "Life amulet should increase combat summon max damage")

    chain_paths = (
        (
            combat_event,
            "melee",
            "private void applyChaosAmuletChainLightning(final Mob hitter, final Mob target, final int baseDamage)",
            "private Mob selectChaosChainLightningTarget(final Player player,",
            "private void inflictChainLightningDamage(final Mob hitter, final Mob target, final int damage)",
            (
                r"!\s*hitter\s*\.\s*isPlayer\s*\(\s*\)",
                r"baseDamage\s*<=\s*0",
                r"!\s*target\s*\.\s*isNpc\s*\(\s*\)",
            ),
        ),
        (
            pvm_melee,
            "PvM melee",
            "private void applyChaosAmuletChainLightning(final Mob hitter, final Mob target, final int baseDamage)",
            "private Mob selectChaosChainLightningTarget(final Player player,",
            "private void inflictChainLightningDamage(final Mob hitter, final Mob target, final int damage)",
            (
                r"!\s*hitter\s*\.\s*isPlayer\s*\(\s*\)",
                r"baseDamage\s*<=\s*0",
                r"!\s*target\s*\.\s*isNpc\s*\(\s*\)",
            ),
        ),
        (
            projectile_event,
            "projectile",
            "private void applyChaosAmuletChainLightning()",
            "private Mob selectChaosChainLightningTarget(final Player player,",
            "private void inflictChainLightningDamage(final Player casterPlayer, final Mob chainTarget, int chainDamage)",
            (
                r"!\s*caster\s*\.\s*isPlayer\s*\(\s*\)",
                r"secondaryEffectDamage\s*<=\s*0",
                r"!\s*opponent\s*\.\s*isNpc\s*\(\s*\)",
            ),
        ),
    )
    chain_proc = CHAIN_PROC.read_text(encoding="utf-8")
    for text, label, apply_signature, select_signature, damage_signature, eligibility_guards in chain_paths:
        apply_chain = java_method_body(text, apply_signature)
        select_chain = java_method_body(text, select_signature)
        inflict_chain = java_method_body(text, damage_signature)
        for pattern, guard_label in (
            (pattern, "primary attack eligibility") for pattern in eligibility_guards
        ):
            require_regex(apply_chain, pattern,
                          f"Chaos necklace {label} chain lightning {guard_label} is missing")
        require("ChaosChainLightningProc.tryApply" in apply_chain,
                f"Chaos necklace {label} must delegate its shared proc policy")
        require(
            "ChainLightningTraversalPolicy.selectNext" in select_chain,
            f"Chaos necklace {label} must use the shared traversal policy",
        )
        require_regex(
            inflict_chain,
            r"HitSplat\s*\.\s*TYPE_ARMOR_PROC\b",
            f"Chaos necklace {label} chain lightning yellow hitsplat is missing",
        )
    for pattern, label in (
        (r"Summoning\s*\.\s*isPlayerAreaEffectSuppressed\s*\(", "Guard Dog AoE suppression"),
        (r"chance\s*<=\s*0(?:\.0+)?D?\b", "non-positive proc chance"),
        (r"random\s*\.\s*nextDouble\s*\(\s*\)\s*>=\s*chance\b", "per-hop chance roll"),
        (r"target\s*==\s*null", "missing-target rejection"),
        (r"ChainLightningTraversalPolicy\s*\.\s*MAX_HOPS", "shared hop cap"),
        (r"new\s+Projectile\s*\(\s*anchor\s*,\s*target\s*,\s*projectileForHop\s*\(\s*hop\s*\)",
         "projectile presentation"),
        (r"childDamage\s*\.\s*apply\s*\(\s*target\s*,\s*damage\s*\)", "owner damage callback"),
        (r"Math\s*\.\s*ceil\s*\(\s*primaryDamage\s*/\s*2(?:\.0+)?D?\s*\)", "initial damage falloff"),
        (r"Math\s*\.\s*ceil\s*\(\s*priorDamage\s*/\s*2(?:\.0+)?D?\s*\)", "per-hop damage falloff"),
    ):
        require_regex(chain_proc, pattern,
                      f"Shared Chaos necklace chain lightning {label} is missing")
    for projectile_name in ("A", "B", "C"):
        require_regex(
            chain_proc,
            rf"Projectile\s*\.\s*CHAIN_LIGHTNING_{projectile_name}\b",
            f"Shared Chaos necklace chain lightning projectile {projectile_name} is missing",
        )
    require_regex(
        chain_traversal,
        r"!\s*Summoning\s*\.\s*isSummon\s*\(\s*npc\s*\)",
        "Chaos necklace traversal summon exclusion is missing",
    )
    require_regex(
        chain_traversal,
        r"\bMAX_HOPS\s*=\s*3\b",
        "Chaos necklace traversal should retain its three-hop limit",
    )
    require("getChaosRecoilChance" in combat_event
            and "getChaosRecoilChance" in pvm_melee
            and "getChaosRecoilChance" in projectile_event
            and "HitSplat.TYPE_ARMOR_PROC" in combat_event
            and "HitSplat.TYPE_ARMOR_PROC" in pvm_melee
            and "HitSplat.TYPE_ARMOR_PROC" in projectile_event,
            "Chaos recoil should use yellow hitsplats across melee and projectile paths")

    require("getLawItemMaxCharges" in law_jewelry
            and "new Item(target.getCatalogId(), target.getAmount(), target.getNoted())" in law_jewelry,
            "Law amulet manual banking should preserve selected stack amount and note state")
    require("public static boolean isLawBankingNecklace" in effects
            and "public static boolean isLawBankingItem" in effects
            and "LAW_BANKING_CHARGES_CACHE_PREFIX" in effects,
            "Law banking necklace/ring charge helpers should be account-backed")
    require("getLawBankingItemCharges(final Player player, final Item item)" in effects
            and "setLawBankingItemCharges(final Player player, final Item item, final int charges)" in effects
            and "player.getCache().set(getLawBankingChargesCacheKey(item.getCatalogId()), clampedCharges);" in effects,
            "Law banking necklace/ring charges should be stored on the player cache")
    require("DEATH_AMULET_BURST_CHARGE_CACHE_PREFIX" in effects
            and "DEATH_AMULET_BURST_CHARGE_REQUIRED_POINTS = 10000" in effects
            and "DEATH_AMULET_BURST_CHARGE_POINTS_PER_COMBAT_LEVEL = 10" in effects
            and "getDeathAmuletBurstChargeCacheKey(final int itemId)" in effects
            and "getDeathAmuletBurstChargePoints(final Player player, final Item item)" in effects
            and "setDeathAmuletBurstChargePoints(final Player player, final Item item, final int chargePoints)" in effects
            and "player.getCache().set(getDeathAmuletBurstChargeCacheKey(item.getCatalogId()), clampedChargePoints);" in effects,
            "Death amulet Burst charge should use player-cache storage instead of per-item charge variants")
    require("SOUL_AMULET_BURST_CHARGE_CACHE_PREFIX" in effects
            and "SOUL_AMULET_BURST_CHARGE_REQUIRED_POINTS = 20000" in effects
            and "SOUL_AMULET_BURST_CHARGE_POINTS_PER_COMBAT_LEVEL = 10" in effects
            and "getSoulAmuletBurstChargeCacheKey(final int itemId)" in effects
            and "getSoulAmuletBurstChargePoints(final Player player, final Item item)" in effects
            and "setSoulAmuletBurstChargePoints(final Player player, final Item item, final int chargePoints)" in effects
            and "player.getCache().set(getSoulAmuletBurstChargeCacheKey(item.getCatalogId()), clampedChargePoints);" in effects,
            "Soul amulet Burst charge should use player-cache storage instead of per-item charge variants")
    require("EnchantingItemEffects.isLawBankingItem(item.getCatalogId())" in law_jewelry
            and "EnchantingItemEffects.getLawBankingItemCharges(player, item)" in law_jewelry
            and "EnchantingItemEffects.setLawBankingItemCharges(player, item, charges)" in law_jewelry,
            "Manual law ring use and recharge should share account-backed banking charges")
    require("chargeLawRunesForBankingRecharge" in law_jewelry
            and "getLawBankingRechargeRuneCost" in law_jewelry
            and "return Math.max(10, (missingCharges + 9) / 10);" in law_jewelry
            and "You need \" + requiredRunes + \" law runes to recharge this jewelry." in law_jewelry,
            "Law banking necklaces/rings should cost 10 law runes per 100 missing charges with a 10-rune minimum")
    require("tryBankMonsterLootWithLawNecklace" in equipment
            and "final int chargeCost = Math.max(1, item.getAmount());" in equipment
            and "EnchantingItemEffects.getLawBankingItemCharges(player, neckItem)" in equipment
            and "EnchantingItemEffects.setLawBankingItemCharges(player, neckItem, charges)" in equipment
            and "Your law necklace sends the loot to your bank and runs out of charges." in equipment
            and "remove(neckItem, 1, true);" not in equipment,
            "Law necklace monster-loot banking should spend account-backed charges and run out at zero")
    require("bankSkillingDropWithLawRing" in equipment
            and "if (item.getDef(player.getWorld()).isStackable())" in equipment
            and "EnchantingItemEffects.getLawBankingItemCharges(player, ringItem)" in equipment
            and "EnchantingItemEffects.setLawBankingItemCharges(player, ringItem, charges)" in equipment
            and "Your law ring sends your resources to your bank and runs out of charges." in equipment
            and "remove(ringItem, 1, true);" not in equipment
            and "for (int count = 0; count < item.getAmount() && charges > 0; count++)" in equipment,
            "Law ring skilling banking should skip stackables and charge per unstackable item from account-backed charges")


def main() -> None:
    ensure_formula_source_matches_design()
    ensure_leach_calculation_behavior()
    ensure_runtime_paths_are_wired()
    print("PASS: jewelry runtime effect formulas and wiring validated")


if __name__ == "__main__":
    main()
