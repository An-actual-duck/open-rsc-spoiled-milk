#!/usr/bin/env python3
import math
import re
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
EFFECTS = ROOT / "server/src/com/openrsc/server/content/EnchantingItemEffects.java"
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
EQUIPMENT = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
DROP_TABLE = ROOT / "server/src/com/openrsc/server/content/DropTable.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
POISON_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/PoisonEvent.java"
EATING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/itemactions/Eating.java"
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
COMBAT_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PVM_MELEE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
PROJECTILE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
FISHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/fishing/Fishing.java"
MINING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/mining/Mining.java"
WOODCUTTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/woodcutting/Woodcutting.java"
HARVESTING = ROOT / "server/plugins/com/openrsc/server/plugins/custom/skills/harvesting/Harvesting.java"
LAW_JEWELRY = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/enchanting/LawJewelry.java"

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


def tier(item_id: int, line: list[int]) -> int:
    return line.index(item_id) + 1 if item_id in line else -1


def tier_for(item_id: int, lines: dict[int, list[int]], altar: int) -> int:
    return tier(item_id, lines.get(altar, []))


def elemental_power(item_id: int, style: str) -> int:
    ring_bonus = elemental_power_from_lines(item_id, ELEMENTAL_RINGS, style)
    return ring_bonus if ring_bonus > 0 else elemental_power_from_lines(item_id, STANDARD_NECKLACES, style)


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
    water_tier = tier_for(item_id, ELEMENTAL_AMULETS, WATER)
    if water_tier != -1:
        return water_tier * 2
    if style == RANGED:
        air_tier = tier_for(item_id, ELEMENTAL_AMULETS, AIR)
        return 0 if air_tier == -1 else air_tier * 3
    if style == MELEE:
        earth_tier = tier_for(item_id, ELEMENTAL_AMULETS, EARTH)
        return 0 if earth_tier == -1 else earth_tier * 3
    if style == MAGIC:
        fire_tier = tier_for(item_id, ELEMENTAL_AMULETS, FIRE)
        return 0 if fire_tier == -1 else fire_tier * 3
    return 0


def death_low_health_bonus(item_id: int, lines: dict[int, list[int]], altar: int, current_hits: int, max_hits: int) -> int:
    item_tier = tier_for(item_id, lines, altar)
    if item_tier <= 0 or current_hits >= max_hits:
        return 0
    missing_percent = max(0.0, (max_hits - current_hits) / float(max_hits))
    if item_tier == 1:
        step, bonus_per_step = 0.25, 1
    elif item_tier == 2:
        step, bonus_per_step = 0.20, 1
    elif item_tier == 3:
        step, bonus_per_step = 0.15, 1
    elif item_tier == 4:
        step, bonus_per_step = 0.10, 1
    else:
        step, bonus_per_step = 0.10, 2
    return math.floor((missing_percent / step) + 0.0000001) * bonus_per_step


def law_item_max_charges(item_id: int) -> int:
    if tier_for(item_id, SPECIAL_AMULETS, LAW) != -1:
        return 3
    item_tier = tier_for(item_id, SPECIAL_RINGS, LAW)
    if item_tier == -1:
        item_tier = tier_for(item_id, STANDARD_NECKLACES, LAW)
    return {1: 50, 2: 150, 3: 300, 4: 500, 5: 750}.get(item_tier, 0)


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
    require(elemental_power(1627, RANGED) == 10, "Dragonstone water necklace should give +10 ranged power")
    require(elemental_power(1627, MELEE) == 10, "Dragonstone water necklace should give +10 melee power")
    require(elemental_power(1627, MAGIC) == 10, "Dragonstone water necklace should give +10 magic power")

    require(elemental_defense(1597, RANGED) == 15, "Dragonstone air amulet should give +15 ranged defense")
    require(elemental_defense(1607, MELEE) == 15, "Dragonstone earth amulet should give +15 melee defense")
    require(elemental_defense(1612, MAGIC) == 15, "Dragonstone fire amulet should give +15 magic defense")
    require(elemental_defense(1602, RANGED) == 10, "Dragonstone water amulet should give +10 ranged defense")
    require(elemental_defense(1602, MELEE) == 10, "Dragonstone water amulet should give +10 melee defense")
    require(elemental_defense(1602, MAGIC) == 10, "Dragonstone water amulet should give +10 magic defense")

    near(tier_for(1314, SPECIAL_RINGS, CHAOS) * 0.08, 0.08, "Sapphire chaos ring recoil chance")
    near(tier_for(1696, SPECIAL_RINGS, CHAOS) * 0.08, 0.40, "Dragonstone chaos ring recoil chance")
    near(tier_for(1652, STANDARD_NECKLACES, CHAOS) * 0.08, 0.40, "Dragonstone chaos necklace recoil chance")
    near(tier_for(1719, SPECIAL_AMULETS, CHAOS) * 0.15, 0.15, "Sapphire chaos amulet echo chance")
    near(tier_for(1723, SPECIAL_AMULETS, CHAOS) * 0.15, 0.75, "Dragonstone chaos amulet echo chance")

    near(tier_for(1701, SPECIAL_RINGS, COSMIC) * 0.05, 0.05, "Sapphire cosmic ring wealth chance")
    near(tier_for(3111, SPECIAL_RINGS, COSMIC) * 0.05, 0.25, "Dragonstone cosmic ring wealth chance")
    near(tier_for(1647, STANDARD_NECKLACES, COSMIC) * 0.10, 0.50, "Dragonstone cosmic necklace extra standard-drop chance")
    near(tier_for(1753, SPECIAL_AMULETS, COSMIC) * 0.10, 0.50, "Dragonstone cosmic amulet rare-gathering double chance")

    for item_id in SPECIAL_AMULETS[LAW]:
        require(law_item_max_charges(item_id) == 3, f"Law amulet {item_id} should have 3 teleport charges")
    require(law_item_max_charges(1714) == 50, "Sapphire law ring should have 50 banking charges")
    require(law_item_max_charges(1718) == 750, "Dragonstone law ring should have 750 banking charges")
    require(law_item_max_charges(1658) == 50, "Sapphire law necklace should have 50 banking charges")
    require(law_item_max_charges(1662) == 750, "Dragonstone law necklace should have 750 banking charges")

    require(tier_for(3095, SPECIAL_RINGS, BLOOD) * 2 == 10, "Dragonstone blood ring should give +10 hits")
    require(tier_for(1672, STANDARD_NECKLACES, BLOOD) * 2 == 10, "Dragonstone blood necklace should give +10 hits")
    near(tier_for(1733, SPECIAL_AMULETS, BLOOD) * 0.05, 0.25, "Dragonstone blood amulet lifesteal chance")

    require(death_low_health_bonus(3086, SPECIAL_RINGS, DEATH, 75, 100) == 1,
            "Sapphire death ring should grant +1 power at 25% missing hits")
    require(death_low_health_bonus(3090, SPECIAL_RINGS, DEATH, 50, 100) == 10,
            "Dragonstone death ring should grant +10 power at 50% missing hits")
    require(death_low_health_bonus(1667, STANDARD_NECKLACES, DEATH, 50, 100) == 10,
            "Dragonstone death necklace should grant +10 defense at 50% missing hits")
    require(tier_for(1724, SPECIAL_AMULETS, DEATH) == 1, "Sapphire death amulet should be tier 1")
    require(tier_for(1728, SPECIAL_AMULETS, DEATH) == 5, "Dragonstone death amulet should be tier 5")

    near(tier_for(1622, STANDARD_NECKLACES, MIND) * 0.05, 0.25, "Dragonstone mind necklace XP chance")
    near(tier_for(3080, SPECIAL_RINGS, MIND) * 0.05, 0.25, "Dragonstone mind ring XP chance")
    near(tier_for(1738, SPECIAL_AMULETS, MIND) * 0.05, 0.25, "Dragonstone mind amulet combat XP chance")
    near(tier_for(1642, STANDARD_NECKLACES, BODY) * 0.05, 0.25, "Dragonstone body necklace XP chance")
    near(tier_for(3085, SPECIAL_RINGS, BODY) * 0.05, 0.25, "Dragonstone body ring XP chance")
    near(tier_for(1743, SPECIAL_AMULETS, BODY) * 0.05, 0.25, "Dragonstone body amulet discipline XP chance")

    near(tier_for(1657, STANDARD_NECKLACES, NATURE) * 0.10, 0.50, "Dragonstone nature necklace food bonus")
    near(tier_for(1700, SPECIAL_RINGS, NATURE) * 0.10, 0.50, "Dragonstone nature ring food bonus")
    near(tier_for(1748, SPECIAL_AMULETS, NATURE) * 0.10, 0.50, "Dragonstone nature amulet food bonus")
    require(tier_for(1748, SPECIAL_AMULETS, NATURE) == 5, "Dragonstone nature amulet should add 5 poison decay power")

    require(tier_for(1708, SPECIAL_RINGS, SOUL) == 5, "Dragonstone soul ring should keep 5 extra death items")
    require(tier(1763, SOUL_NECKLACES) == 5, "Dragonstone soul necklace should keep 5 extra death items")
    near(tier_for(1758, SPECIAL_AMULETS, SOUL) * 0.10, 0.50, "Dragonstone soul amulet survival chance")

    require(tier(3105, LIFE_NECKLACES) * 10 == 50, "Dragonstone life necklace should add 50% summon health")
    require(tier_for(3100, SPECIAL_RINGS, LIFE) * 20 == 100, "Dragonstone life ring should double support-summon duration")
    require(tier_for(3110, SPECIAL_AMULETS, LIFE) == 5, "Dragonstone life amulet should add +5 summon max damage")


def ensure_runtime_paths_are_wired() -> None:
    effects = EFFECTS.read_text(encoding="utf-8")
    equipment = EQUIPMENT.read_text(encoding="utf-8")
    drop_table = DROP_TABLE.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")
    poison_event = POISON_EVENT.read_text(encoding="utf-8")
    eating = EATING.read_text(encoding="utf-8")
    summoning = SUMMONING.read_text(encoding="utf-8")
    combat_event = COMBAT_EVENT.read_text(encoding="utf-8")
    pvm_melee = PVM_MELEE_EVENT.read_text(encoding="utf-8")
    projectile_event = PROJECTILE_EVENT.read_text(encoding="utf-8")
    law_jewelry = LAW_JEWELRY.read_text(encoding="utf-8")

    require("public static double getCosmicAmuletExtraResourceChance" in effects
            and "return 0.0D;" in effects,
            "Cosmic amulet should not inflate base resource gather chances")
    require_regex(equipment, r"getChaosRecoilChance\(\).*?return Math\.min\(1\.0D,",
                  "Chaos recoil ring and necklace chance should stack and cap at 100%")
    require_regex(equipment, r"getChaosRecoilDamageDivisor\(\).*?return hasNeck && hasRing \? 5 : 10;",
                  "Chaos recoil damage should double when ring and necklace are both worn")
    require_regex(equipment, r"getEquippedElementalPowerBonus.*?getElementalPowerBonus\(neckItem.*?getElementalPowerBonus\(ringItem",
                  "Elemental necklace and ring power bonuses should both count")
    require_regex(equipment, r"getEquippedElementalDefenseBonus.*?getElementalDefenseBonus\(neckItem",
                  "Elemental defense should come from the equipped amulet slot")

    require("getWealthChance(owner)" in drop_table, "Cosmic rings should feed drop-table wealth chance")
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
    require("poisonDrain += player.getCarriedItems().getEquipment().getNatureAmuletPoisonDecayBonus();" in poison_event,
            "Nature amulet poison decay should add to poison tick drain")
    require("getNatureAmuletFoodBonus" in eating and "Math.ceil(totalHeal * (1.0D + foodBonus))" in eating,
            "Nature jewelry should increase food healing")
    require("getNatureAmuletFoodBonus()" in equipment
            and "getNatureAmuletFoodBonus(neckItem.getCatalogId())" in equipment
            and "getNatureAmuletFoodBonus(ringItem.getCatalogId())" in equipment,
            "Nature ring and necklace food bonuses should stack")

    require("isCraftingSkill(skill)" in player and "getMindAmuletXpBonus()" in player,
            "Mind ring/necklace XP bonus should apply to crafting skills")
    require("isGatheringSkill(skill)" in player and "getBodyAmuletXpBonus()" in player,
            "Body ring/necklace XP bonus should apply to gathering skills")
    require("isMindCombatXpSkill(skill)" in player and "getMindCombatAmuletXpBonus()" in player,
            "Mind amulet XP bonus should apply to combat skills")
    require("isBodyDisciplineXpSkill(skill)" in player and "getBodyDisciplineAmuletXpBonus()" in player,
            "Body amulet XP bonus should apply to discipline skills")

    require("applyBloodAmuletLifesteal" in player and "HitSplat.TYPE_HEAL" in player,
            "Blood amulet lifesteal should heal with a heal hitsplat")
    require("syncHitsEquipmentBonuses" in player and "getBloodAmuletHitsBonus" in player,
            "Blood ring and necklace should synchronize max Hits bonuses")
    require("applyDeathAmuletBurst" in player and "HitSplat.TYPE_ARMOR_PROC" in player,
            "Death amulet bursts should use non-direct-combat yellow hitsplats")
    require("getSoulAmuletExtraKeptItems" in equipment and "extraKeptItems" in (ROOT / "server/src/com/openrsc/server/model/container/Inventory.java").read_text(encoding="utf-8"),
            "Soul ring and necklace should feed death item retention")
    require("checkRingOfLife" in player and "getSoulAmuletSurvivalChance" in player,
            "Soul amulet should own the life-saving break chance")

    require("getLifeNecklaceSummonHealthPercent" in summoning,
            "Life necklace should increase combat summon health")
    require("getLifeRingSupportDurationPercent" in summoning,
            "Life ring should extend support summon duration")
    require("getLifeAmuletSummonMaxDamageBonus" in summoning,
            "Life amulet should increase combat summon max damage")

    require("getChaosAmuletSecondHitChance" in combat_event
            and "HitSplat.TYPE_ARMOR_PROC" in combat_event,
            "Chaos amulet melee echo should use yellow hitsplats")
    require("getChaosAmuletSecondHitChance" in pvm_melee
            and "HitSplat.TYPE_ARMOR_PROC" in pvm_melee,
            "Chaos amulet PvM melee echo should use yellow hitsplats")
    require("getChaosAmuletSecondHitChance" in projectile_event
            and "HitSplat.TYPE_ARMOR_PROC" in projectile_event,
            "Chaos amulet projectile echo should use yellow hitsplats")
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
    require("tryBankMonsterLootWithLawNecklace" in equipment
            and "final int chargeCost = Math.max(1, item.getAmount());" in equipment
            and "remove(neckItem, 1, true);" in equipment,
            "Law necklace monster-loot banking should spend charges and break at zero")
    require("bankSkillingDropWithLawRing" in equipment
            and "if (item.getDef(player.getWorld()).isStackable())" in equipment
            and "for (int count = 0; count < item.getAmount() && charges > 0; count++)" in equipment,
            "Law ring skilling banking should skip stackables and charge per unstackable item")


def main() -> None:
    ensure_formula_source_matches_design()
    ensure_runtime_paths_are_wired()
    print("PASS: jewelry runtime effect formulas and wiring validated")


if __name__ == "__main__":
    main()
