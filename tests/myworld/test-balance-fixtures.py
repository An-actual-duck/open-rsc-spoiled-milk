#!/usr/bin/env python3
import json
import math
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFS_DIR = ROOT / "server" / "conf" / "server" / "defs"
DEFAULT_MELEE_DEFENSE_MULTIPLIER = 1.0
DEFAULT_RANGED_DEFENSE_MULTIPLIER = 0.5
DEFAULT_MAGIC_DEFENSE_MULTIPLIER = 0.5
SCIMITAR_IDS = {82, 83, 84, 427, 85, 86, 398, 1999, 2010, 2021, 2032}
BATTLEAXE_IDS = {205, 89, 90, 429, 91, 92, 93, 2002, 2013, 2024, 2035}
DRAGON_SPEED_ARMOR_IDS = {795, 1368, 1537, 1426}


def load_json_array(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    return data[next(iter(data.keys()))]


def load_items():
    items = {}
    for filename in ("ItemDefs.json", "ItemDefsCustom.json"):
        for item in load_json_array(DEFS_DIR / filename):
            items[item["id"]] = item
    for override in load_json_array(DEFS_DIR / "ItemDefsMyWorld.json"):
        items.setdefault(override["id"], {"id": override["id"]})
        items[override["id"]].update(override)
    return items


def load_npcs():
    npcs = {}
    for filename in ("NpcDefs.json", "NpcDefsCustom.json"):
        for npc in load_json_array(DEFS_DIR / filename):
            npcs[npc["id"]] = npc
    override_path = DEFS_DIR / "NpcDefsMyWorld.json"
    if override_path.exists():
        for override in load_json_array(override_path):
            npcs.setdefault(override["id"], {"id": override["id"]})
            npcs[override["id"]].update(override)
    return npcs


def offense_to_max_hit(total_offense: int) -> int:
    return max(1, 1 + math.ceil(total_offense / 7.0))


def defense_to_mitigation(total_defense: int) -> int:
    return max(0, math.ceil(total_defense / 7.0))


def average_damage(attack_max: int, mitigation_cap: int) -> float:
    if attack_max <= 0:
        return 0.0
    offense_rolls = range(1, attack_max + 1)
    defense_rolls = [0] if mitigation_cap <= 0 else range(1, mitigation_cap + 1)
    outcomes = 0
    total = 0
    for offense_roll in offense_rolls:
        for defense_roll in defense_rolls:
            total += max(offense_roll - defense_roll, 0)
            outcomes += 1
    return total / outcomes


def is_medium_helm(item_id: int) -> bool:
    return item_id in {104, 5, 105, 470, 106, 107, 399}


def is_chain_body(item_id: int) -> bool:
    return item_id in {113, 7, 114, 431, 115, 116, 400}


def armor_speed_multiplier(item_ids) -> float:
    dragon_speed_pieces = sum(1 for item_id in item_ids if item_id in DRAGON_SPEED_ARMOR_IDS)
    if dragon_speed_pieces > 0:
        return 1.0 + (dragon_speed_pieces * 0.05)
    return 1.05 if any(is_chain_body(item_id) for item_id in item_ids) else 1.0


def damage_roll_high_bias(item_ids) -> float:
    bonus = 0.10 if any(is_medium_helm(item_id) for item_id in item_ids) else 0.0
    if any(item_id in SCIMITAR_IDS or item_id in BATTLEAXE_IDS for item_id in item_ids):
        bonus += 0.20
    return bonus


def biased_average_damage(attack_max: int, mitigation_cap: int, high_bias_chance: float) -> float:
    if attack_max <= 0:
        return 0.0
    defense_rolls = [0] if mitigation_cap <= 0 else range(1, mitigation_cap + 1)
    outcomes = 0
    total = 0.0
    for offense_roll in range(1, attack_max + 1):
        effective_roll = offense_roll
        if high_bias_chance > 0.0 and offense_roll < attack_max:
            effective_roll = offense_roll + high_bias_chance
        for defense_roll in defense_rolls:
            total += max(effective_roll - defense_roll, 0)
            outcomes += 1
    return total / outcomes


def weapon_speed_multiplier(speed: int) -> float:
    return {
        1: 0.8,
        2: 0.9,
        3: 1.0,
        4: 1.1,
        5: 1.2,
    }.get(speed, 1.0)


def npc_attack_distribution(attack_max: int, resolution: int = 4096):
    if attack_max <= 0:
        return {0: 1.0}
    distribution = {}
    for sample in range(resolution):
        unit = (sample + 0.5) / resolution
        roll = min(attack_max, int(math.floor((unit ** 1.5) * (attack_max + 1))))
        distribution[roll] = distribution.get(roll, 0) + 1
    return {roll: count / resolution for roll, count in distribution.items()}


def npc_average_damage(attack_max: int, mitigation_cap: int) -> float:
    offense_distribution = npc_attack_distribution(attack_max)
    defense_rolls = [0] if mitigation_cap <= 0 else list(range(1, mitigation_cap + 1))
    total = 0.0
    for offense_roll, offense_weight in offense_distribution.items():
        for defense_roll in defense_rolls:
            total += offense_weight * max(offense_roll - defense_roll, 0) / len(defense_rolls)
    return total


def npc_zero_hit_rate(attack_max: int, mitigation_cap: int) -> float:
    offense_distribution = npc_attack_distribution(attack_max)
    defense_rolls = [0] if mitigation_cap <= 0 else list(range(1, mitigation_cap + 1))
    zero_rate = 0.0
    for offense_roll, offense_weight in offense_distribution.items():
        zero_outcomes = sum(1 for defense_roll in defense_rolls if max(offense_roll - defense_roll, 0) == 0)
        zero_rate += offense_weight * (zero_outcomes / len(defense_rolls))
    return zero_rate


def sum_item_stat(items, item_ids, field):
    return sum(int(items[item_id].get(field, 0)) for item_id in item_ids)


def max_item_stat(items, item_ids, field):
    return max((int(items[item_id].get(field, 0)) for item_id in item_ids), default=0)


def player_fixture(items, name, skill_level, style, equipment_ids):
    style_field = f"{style}Offense"
    defense_field = f"{style}Defense"
    equipment_offense = sum_item_stat(items, equipment_ids, style_field)
    equipment_defense = sum_item_stat(items, equipment_ids, defense_field)
    melee_defense = sum_item_stat(items, equipment_ids, "meleeDefense")
    ranged_defense = sum_item_stat(items, equipment_ids, "rangedDefense")
    magic_defense = sum_item_stat(items, equipment_ids, "magicDefense")
    total_offense = skill_level + equipment_offense
    roll_bias = damage_roll_high_bias(equipment_ids)
    armor_speed = armor_speed_multiplier(equipment_ids)
    weapon_speed = max_item_stat(items, equipment_ids, "weaponSpeed")
    if style == "melee" and equipment_offense <= 0 and weapon_speed <= 0:
        total_offense = max(1, math.floor(total_offense * 0.70))
    total_speed = weapon_speed_multiplier(weapon_speed) * armor_speed
    return {
        "name": name,
        "style": style,
        "skill": skill_level,
        "equipment": equipment_ids,
        "equipment_offense": equipment_offense,
        "offense": total_offense,
        "max_hit": offense_to_max_hit(total_offense),
        "defense": equipment_defense,
        "mitigation_cap": defense_to_mitigation(equipment_defense),
        "melee_defense": melee_defense,
        "melee_mitigation_cap": defense_to_mitigation(melee_defense),
        "ranged_defense": ranged_defense,
        "ranged_mitigation_cap": defense_to_mitigation(ranged_defense),
        "magic_defense": magic_defense,
        "magic_mitigation_cap": defense_to_mitigation(magic_defense),
        "weapon_speed": weapon_speed,
        "armor_speed_multiplier": armor_speed,
        "roll_high_bias": roll_bias,
        "throughput_multiplier": total_speed,
    }


def spell_cap_percent(spell_band: str) -> float:
    return {
        "mind": 0.40,
        "chaos": 0.60,
        "death": 0.80,
        "blood": 1.00,
    }[spell_band]


def magic_spell_fixture(items, name, skill_level, equipment_ids, spell_band):
    base = player_fixture(items, name, skill_level, "magic", equipment_ids)
    capped_max = max(1, math.ceil(base["max_hit"] * spell_cap_percent(spell_band)))
    base["spell_band"] = spell_band
    base["spell_cap_percent"] = spell_cap_percent(spell_band)
    base["spell_capped_max_hit"] = capped_max
    return base


def capped_biased_average_damage(attack_max: int, damage_cap: int, mitigation_cap: int, high_bias_chance: float) -> float:
    if attack_max <= 0:
        return 0.0
    defense_rolls = [0] if mitigation_cap <= 0 else range(1, mitigation_cap + 1)
    outcomes = 0
    total = 0.0
    for offense_roll in range(1, attack_max + 1):
        effective_roll = offense_roll
        if high_bias_chance > 0.0 and offense_roll < attack_max:
            effective_roll = offense_roll + high_bias_chance
        capped_roll = min(effective_roll, damage_cap)
        for defense_roll in defense_rolls:
            total += max(capped_roll - defense_roll, 0)
            outcomes += 1
    return total / outcomes


def npc_fixture(npcs, npc_id):
    npc = npcs[npc_id]
    legacy_def = int(npc.get("defense", 0))
    melee_def = int(npc.get("meleeDefense", 0)) or derive_npc_defense(
        legacy_def, npc.get("meleeDefenseMultiplier", -1.0), npc.get("meleeDefenseDivisor", -1.0), DEFAULT_MELEE_DEFENSE_MULTIPLIER
    )
    ranged_def = int(npc.get("rangedDefense", 0)) or derive_npc_defense(
        legacy_def, npc.get("rangedDefenseMultiplier", -1.0), npc.get("rangedDefenseDivisor", -1.0), DEFAULT_RANGED_DEFENSE_MULTIPLIER
    )
    magic_def = int(npc.get("magicDefense", 0)) or derive_npc_defense(
        legacy_def, npc.get("magicDefenseMultiplier", -1.0), npc.get("magicDefenseDivisor", -1.0), DEFAULT_MAGIC_DEFENSE_MULTIPLIER
    )
    melee_off = int(npc.get("strength", 0))
    return {
        "id": npc_id,
        "name": npc["name"],
        "combat": int(npc.get("combatlvl", 0)),
        "hits": int(npc.get("hits", 0)),
        "legacy_defense": legacy_def,
        "melee_defense_multiplier": effective_npc_multiplier(
            npc.get("meleeDefenseMultiplier", -1.0), npc.get("meleeDefenseDivisor", -1.0), DEFAULT_MELEE_DEFENSE_MULTIPLIER
        ),
        "ranged_defense_multiplier": effective_npc_multiplier(
            npc.get("rangedDefenseMultiplier", -1.0), npc.get("rangedDefenseDivisor", -1.0), DEFAULT_RANGED_DEFENSE_MULTIPLIER
        ),
        "magic_defense_multiplier": effective_npc_multiplier(
            npc.get("magicDefenseMultiplier", -1.0), npc.get("magicDefenseDivisor", -1.0), DEFAULT_MAGIC_DEFENSE_MULTIPLIER
        ),
        "melee_offense": melee_off,
        "melee_max_hit": offense_to_max_hit(melee_off),
        "melee_defense": melee_def,
        "melee_mitigation_cap": defense_to_mitigation(melee_def),
        "ranged_defense": ranged_def,
        "ranged_mitigation_cap": defense_to_mitigation(ranged_def),
        "magic_defense": magic_def,
        "magic_mitigation_cap": defense_to_mitigation(magic_def),
    }


def effective_npc_multiplier(configured_multiplier: float, legacy_divisor: float, default_multiplier: float) -> float:
    if configured_multiplier is not None and configured_multiplier >= 0.0:
        return float(configured_multiplier)
    if legacy_divisor is not None and legacy_divisor > 0.0:
        return 1.0 / float(legacy_divisor)
    return float(default_multiplier)


def derive_npc_defense(legacy_defense: int, configured_multiplier: float, legacy_divisor: float, default_multiplier: float) -> int:
    if legacy_defense <= 0:
        return 0
    multiplier = effective_npc_multiplier(configured_multiplier, legacy_divisor, default_multiplier)
    return max(0, int(math.floor(legacy_defense * multiplier)))


def print_player_fixture(fixture):
    print(
        f"PLAYER {fixture['name']}: style={fixture['style']} skill={fixture['skill']} "
        f"offense={fixture['offense']} max_hit={fixture['max_hit']} "
        f"defense={fixture['defense']} mitigation_cap={fixture['mitigation_cap']} "
        f"weapon_speed={fixture['weapon_speed']} armor_speed_x={fixture['armor_speed_multiplier']:.2f} "
        f"high_roll_bias={fixture['roll_high_bias']:.2f} throughput_x={fixture['throughput_multiplier']:.2f}"
    )


def print_npc_fixture(fixture):
    print(
        f"NPC {fixture['name']} ({fixture['id']}): cb={fixture['combat']} hp={fixture['hits']} "
        f"melee_offense={fixture['melee_offense']} melee_max_hit={fixture['melee_max_hit']} "
        f"legacy_def={fixture['legacy_defense']} "
        f"melee_def={fixture['melee_defense']} ranged_def={fixture['ranged_defense']} "
        f"magic_def={fixture['magic_defense']} "
        f"multipliers=({fixture['melee_defense_multiplier']:.2f},"
        f"{fixture['ranged_defense_multiplier']:.2f},{fixture['magic_defense_multiplier']:.2f})"
    )


def print_matchup(attacker_name, defender_name, average, attack_max, mitigation_cap):
    print(
        f"MATCHUP {attacker_name} -> {defender_name}: avg={average:.2f} "
        f"attack_max={attack_max} mitigation_cap={mitigation_cap}"
    )


def print_throughput_matchup(attacker_name, defender_name, average, throughput):
    print(
        f"THROUGHPUT {attacker_name} -> {defender_name}: avg_hit={average:.2f} "
        f"throughput_avg={throughput:.2f}"
    )


def style_matrix_entry(player_fixture, npc_fixture):
    style = player_fixture["style"]
    mitigation_cap = npc_fixture[f"{style}_mitigation_cap"]
    if style == "magic":
        average = capped_biased_average_damage(
            player_fixture["max_hit"],
            player_fixture["spell_capped_max_hit"],
            mitigation_cap,
            player_fixture["roll_high_bias"],
        )
    else:
        average = biased_average_damage(
            player_fixture["max_hit"],
            mitigation_cap,
            player_fixture["roll_high_bias"],
        )
    throughput = average * player_fixture["throughput_multiplier"]
    return average, throughput


def print_style_band(name, style_attackers, targets):
    print(f"\n== {name} Style Band ==")
    for npc_fixture_data in targets:
        print(
            f"BAND TARGET {npc_fixture_data['name']} ({npc_fixture_data['id']}): "
            f"cb={npc_fixture_data['combat']} "
            f"melee_def={npc_fixture_data['melee_defense']} "
            f"ranged_def={npc_fixture_data['ranged_defense']} "
            f"magic_def={npc_fixture_data['magic_defense']}"
        )
        for attacker_name, attacker_fixture in style_attackers:
            average, throughput = style_matrix_entry(attacker_fixture, npc_fixture_data)
            print(
                f"BAND STYLE {attacker_name} -> {npc_fixture_data['name']}: "
                f"avg_hit={average:.2f} throughput_avg={throughput:.2f}"
            )


def print_ranged_tier_band(tier, checkpoints):
    print(f"\n== Ranged Tier-{tier} Family Checkpoints ==")
    for family_name in ("longbow", "shortbow", "crossbow", "dart", "knife"):
        print_player_fixture(checkpoints[(tier, family_name)])


def print_melee_tier_band(tier, checkpoints):
    print(f"\n== Melee Tier-{tier} Reference Checkpoints ==")
    for family_name in ("longsword", "spear"):
        print_player_fixture(checkpoints[(tier, family_name)])


def check(condition, message):
    if not condition:
        print(f"FAIL: {message}")
        sys.exit(1)


def main():
    items = load_items()
    npcs = load_npcs()

    players = [
        player_fixture(items, "bronze_melee_no_armor", 1, "melee", [82]),
        player_fixture(items, "bronze_melee_bronze_set", 1, "melee", [82, 104, 113, 117, 206, 124]),
        player_fixture(items, "rune_melee_rune_set", 40, "melee", [398, 399, 400, 401, 402, 403]),
        player_fixture(items, "rune_longsword_heavy_rune", 40, "melee", [75, 112, 401, 402, 404]),
        player_fixture(items, "beginner_bronze_longsword", 1, "melee", [70]),
        player_fixture(items, "rune_longsword_full_rune", 40, "melee", [75, 399, 401, 402, 403]),
        player_fixture(items, "rune_scimitar_compare", 40, "melee", [398]),
        player_fixture(items, "rune_mace_compare", 40, "melee", [98]),
        player_fixture(items, "rune_longsword_compare", 40, "melee", [75]),
        player_fixture(items, "rune_battleaxe_compare", 40, "melee", [93]),
        player_fixture(items, "rune_2h_compare", 40, "melee", [81]),
        player_fixture(items, "rune_dagger_compare", 40, "melee", [396]),
        player_fixture(items, "rune_spear_compare", 40, "melee", [1092]),
        player_fixture(items, "wizard_apprentice", 10, "magic", [100, 2050, 2051]),
        player_fixture(items, "battle_mage", 50, "magic", [617, 702, 703, 1264]),
        magic_spell_fixture(items, "tier6_air_mind_lesser_demon", 51, [1785], "mind"),
        magic_spell_fixture(items, "tier6_air_chaos_lesser_demon", 51, [1785], "chaos"),
        magic_spell_fixture(items, "tier6_air_death_lesser_demon", 51, [1785], "death"),
        magic_spell_fixture(items, "tier6_air_blood_lesser_demon", 51, [1785], "blood"),
        player_fixture(items, "archer_basic", 1, "ranged", [189, 11, 15, 16, 1370, 1371]),
        player_fixture(items, "archer_mid", 35, "ranged", [653, 642, 15, 16, 1370, 1371]),
        player_fixture(items, "archer_longbow_compare", 50, "ranged", [2129, 646]),
        player_fixture(items, "archer_shortbow_compare", 50, "ranged", [2130, 646]),
        player_fixture(items, "archer_crossbow_compare", 50, "ranged", [2176, 2194]),
        player_fixture(items, "rune_dart_compare", 35, "ranged", [1070]),
        player_fixture(items, "rune_knife_compare", 35, "ranged", [1080]),
        player_fixture(items, "leather_armor_melee_view", 1, "melee", [15, 16, 1370, 1371, 1372]),
        player_fixture(items, "leather_armor_ranged_view", 1, "ranged", [15, 16, 1370, 1371, 1372]),
        player_fixture(items, "dragon_speed_set", 80, "melee", [795, 1368, 1426]),
        player_fixture(items, "dragon_heavy_set", 80, "melee", [1425, 1427, 1429, 1278]),
        player_fixture(items, "tier6_scimitar_lesser_demon", 40, "melee", [398]),
        player_fixture(items, "tier6_mace_lesser_demon", 40, "melee", [98]),
        player_fixture(items, "tier6_longsword_lesser_demon", 40, "melee", [75]),
        player_fixture(items, "tier6_battleaxe_lesser_demon", 40, "melee", [93]),
        player_fixture(items, "tier6_2h_lesser_demon", 40, "melee", [81]),
        player_fixture(items, "tier6_dagger_lesser_demon", 40, "melee", [396]),
        player_fixture(items, "tier6_spear_lesser_demon", 40, "melee", [1092]),
        player_fixture(items, "tier6_longbow_lesser_demon", 50, "ranged", [652, 642]),
        player_fixture(items, "tier6_shortbow_lesser_demon", 50, "ranged", [653, 642]),
        player_fixture(items, "tier6_crossbow_lesser_demon", 50, "ranged", [2172, 2186]),
        player_fixture(items, "tier6_dart_lesser_demon", 35, "ranged", [1070]),
        player_fixture(items, "tier6_knife_lesser_demon", 35, "ranged", [1080]),
    ]

    npcs_to_check = [3, 4, 6, 15, 19, 22, 23, 40, 41, 43, 65, 66, 70, 74, 81, 102, 136, 177, 184, 188, 196, 202, 232, 263, 271, 277, 291, 323, 324, 477, 531, 768, 789, 809]
    npc_fixtures = [npc_fixture(npcs, npc_id) for npc_id in npcs_to_check]

    ranged_tier_skills = {
        1: 10,
        6: 35,
        10: 50,
    }
    ranged_tier_loadouts = {
        (1, "longbow"): [188, 2039],
        (1, "shortbow"): [189, 2039],
        (1, "crossbow"): [60, 190],
        (1, "dart"): [2043],
        (1, "knife"): [1996],
        (6, "longbow"): [652, 642],
        (6, "shortbow"): [653, 642],
        (6, "crossbow"): [2172, 2186],
        (6, "dart"): [1068],
        (6, "knife"): [1078],
        (10, "longbow"): [2129, 646],
        (10, "shortbow"): [2130, 646],
        (10, "crossbow"): [2176, 2194],
        (10, "dart"): [1070],
        (10, "knife"): [1080],
    }
    ranged_tier_checkpoints = {
        key: player_fixture(items, f"tier{key[0]}_{key[1]}", ranged_tier_skills[key[0]], "ranged", loadout)
        for key, loadout in ranged_tier_loadouts.items()
    }
    melee_tier_loadouts = {
        (1, "longsword"): [70],
        (1, "spear"): [2207],
        (6, "longsword"): [73],
        (6, "spear"): [1090],
        (10, "longsword"): [75],
        (10, "spear"): [1092],
    }
    melee_tier_checkpoints = {
        key: player_fixture(items, f"tier{key[0]}_{key[1]}", ranged_tier_skills[key[0]], "melee", loadout)
        for key, loadout in melee_tier_loadouts.items()
    }

    print("== Player Fixtures ==")
    for fixture in players:
        print_player_fixture(fixture)

    print("\n== NPC Fixtures ==")
    for fixture in npc_fixtures:
        print_npc_fixture(fixture)

    print("\n== Weapon Family Compare ==")
    for fixture in players[5:11]:
        print_player_fixture(fixture)

    print("\n== Ranged Family Compare ==")
    for fixture in players[18:23]:
        print_player_fixture(fixture)
    print_melee_tier_band(1, melee_tier_checkpoints)
    print_ranged_tier_band(1, ranged_tier_checkpoints)
    print_melee_tier_band(6, melee_tier_checkpoints)
    print_ranged_tier_band(6, ranged_tier_checkpoints)
    print_melee_tier_band(10, melee_tier_checkpoints)
    print_ranged_tier_band(10, ranged_tier_checkpoints)

    print("\n== Matchup Fixtures ==")

    bronze_no_armor = players[0]
    bronze_set = players[1]
    rune_set = players[2]
    rune_heavy = players[3]
    beginner_longsword = players[4]
    rune_longsword = players[5]
    rune_scimitar_compare = players[6]
    rune_mace_compare = players[7]
    rune_longsword_compare = players[8]
    rune_battleaxe_compare = players[9]
    rune_2h_compare = players[10]
    rune_dagger_compare = players[11]
    rune_spear_compare = players[12]
    wizard = players[13]
    battlemage = players[14]
    tier6_air_mind = players[15]
    tier6_air_chaos = players[16]
    tier6_air_death = players[17]
    tier6_air_blood = players[18]
    archer_basic = players[19]
    archer_mid = players[20]
    archer_longbow_compare = players[21]
    archer_shortbow_compare = players[22]
    archer_crossbow_compare = players[23]
    rune_dart_compare = players[24]
    rune_knife_compare = players[25]
    leather_armor_melee = players[26]
    leather_armor_ranged = players[27]
    dragon_speed_set = players[28]
    dragon_heavy_set = players[29]
    tier6_scimitar = players[30]
    tier6_mace = players[31]
    tier6_longsword = players[32]
    tier6_battleaxe = players[33]
    tier6_2h = players[34]
    tier6_dagger = players[35]
    tier6_spear = players[36]
    tier6_longbow = players[37]
    tier6_shortbow = players[38]
    tier6_crossbow = players[39]
    tier6_dart = players[40]
    tier6_knife = players[41]

    check(bronze_no_armor["max_hit"] == 2, "Bronze melee no-armor fixture should max hit 2")
    check(bronze_set["mitigation_cap"] >= bronze_no_armor["mitigation_cap"], "Bronze armor should not reduce mitigation")
    check(rune_set["max_hit"] > bronze_set["max_hit"], "Rune melee should outscale bronze melee")
    check(rune_heavy["mitigation_cap"] > rune_longsword["mitigation_cap"], "Heavy rune should mitigate more than lighter rune longsword set")
    check(rune_dagger_compare["offense"] < rune_scimitar_compare["offense"], "Rune dagger should stay below rune scimitar")
    check(rune_scimitar_compare["offense"] < rune_mace_compare["offense"], "Rune mace should carry a slightly larger raw budget than rune scimitar at medium speed")
    check(rune_mace_compare["offense"] < rune_longsword_compare["offense"], "Rune mace should stay below rune longsword on raw offense")
    check(rune_scimitar_compare["offense"] < rune_longsword_compare["offense"], "Rune scimitar should stay below rune longsword")
    check(rune_scimitar_compare["offense"] <= rune_spear_compare["offense"], "Rune spear should keep pace with the 2-bar band")
    check(rune_spear_compare["offense"] < rune_longsword_compare["offense"], "Rune spear should stay below rune longsword on raw offense")
    check(rune_longsword_compare["offense"] < rune_battleaxe_compare["offense"], "Rune battleaxe should stay above rune longsword")
    check(rune_battleaxe_compare["offense"] < rune_2h_compare["offense"], "Rune 2h should stay above rune battleaxe")
    check(battlemage["max_hit"] > wizard["max_hit"], "Battle mage fixture should outscale apprentice mage")
    check(tier6_air_mind["spell_capped_max_hit"] < tier6_air_chaos["spell_capped_max_hit"], "Mind cap should stay below chaos cap")
    check(tier6_air_chaos["spell_capped_max_hit"] < tier6_air_death["spell_capped_max_hit"], "Chaos cap should stay below death cap")
    check(tier6_air_death["spell_capped_max_hit"] < tier6_air_blood["spell_capped_max_hit"], "Death cap should stay below blood cap")
    check(tier6_air_blood["spell_capped_max_hit"] == tier6_air_blood["max_hit"], "Blood tier should be uncapped")
    check(archer_mid["max_hit"] > archer_basic["max_hit"], "Mid archer should outscale basic archer")
    check(archer_longbow_compare["equipment_offense"] == items[75]["meleeOffense"], "Tier-10 longbow plus rune arrows should match the rune longsword offense band")
    check(archer_shortbow_compare["offense"] < archer_longbow_compare["offense"], "Tier-10 shortbow pair should stay slightly below the matching longbow pair on direct power")
    check(archer_shortbow_compare["throughput_multiplier"] > archer_longbow_compare["throughput_multiplier"], "Tier-10 shortbow should attack faster than the matching longbow")
    check(archer_crossbow_compare["weapon_speed"] > archer_longbow_compare["weapon_speed"], "Crossbows should attack faster than longbows")
    check(archer_crossbow_compare["offense"] < archer_longbow_compare["offense"], "Top-tier crossbow pair should stay below longbow direct power")
    check(archer_crossbow_compare["offense"] < archer_shortbow_compare["offense"], "Top-tier crossbow pair should stay below shortbow direct power")
    check(rune_dart_compare["weapon_speed"] > rune_knife_compare["weapon_speed"], "Darts should stay faster than knives")
    check(rune_dart_compare["offense"] < rune_knife_compare["offense"], "Darts should trade direct power for speed")
    check(leather_armor_melee["melee_defense"] > 0 and leather_armor_ranged["ranged_defense"] > 0, "Leather armor should contribute to both melee and ranged defense")
    check(leather_armor_melee["melee_defense"] <= leather_armor_ranged["ranged_defense"], "Leather armor should lean slightly toward ranged defense on odd splits")
    check(dragon_speed_set["armor_speed_multiplier"] == 1.15, "Dragon medium/scale/kite set should grant 15% total armor speed")
    check(dragon_speed_set["melee_defense"] == 70, "Dragon speed set should carry the tier-10 hybrid melee defense total")
    check(dragon_speed_set["ranged_defense"] == 31, "Dragon speed set should not get ranged defense from paladin shields")
    check(dragon_speed_set["magic_defense"] == 43, "Dragon speed set should get magic defense from the paladin shield line")
    check(dragon_speed_set["throughput_multiplier"] > dragon_heavy_set["throughput_multiplier"], "Dragon speed set should attack faster than the heavy dragon plate line")
    check(dragon_heavy_set["melee_defense"] == 121, "Dragon heavy set should carry the tier-11 hybrid melee defense total")
    check(dragon_heavy_set["ranged_defense"] == 92, "Dragon heavy set should carry the tier-11 hybrid ranged defense total")

    npc_by_id = {fixture["id"]: fixture for fixture in npc_fixtures}

    matchup_fixtures = {
        "beginner_longsword_vs_cow": biased_average_damage(beginner_longsword["max_hit"], npc_by_id[6]["melee_mitigation_cap"], beginner_longsword["roll_high_bias"]),
        "bronze_no_armor_vs_goblin_melee": biased_average_damage(bronze_no_armor["max_hit"], npc_by_id[4]["melee_mitigation_cap"], bronze_no_armor["roll_high_bias"]),
        "bronze_no_armor_vs_ghost_melee": biased_average_damage(bronze_no_armor["max_hit"], npc_by_id[15]["melee_mitigation_cap"], bronze_no_armor["roll_high_bias"]),
        "bronze_no_armor_vs_skeleton_melee": biased_average_damage(bronze_no_armor["max_hit"], npc_by_id[40]["melee_mitigation_cap"], bronze_no_armor["roll_high_bias"]),
        "rune_longsword_vs_lesser_demon": biased_average_damage(rune_longsword["max_hit"], npc_by_id[22]["melee_mitigation_cap"], rune_longsword["roll_high_bias"]),
        "rune_heavy_vs_lesser_demon": biased_average_damage(rune_heavy["max_hit"], npc_by_id[22]["melee_mitigation_cap"], rune_heavy["roll_high_bias"]),
        "basic_archer_vs_ghost": biased_average_damage(archer_basic["max_hit"], npc_by_id[15]["ranged_mitigation_cap"], archer_basic["roll_high_bias"]),
        "basic_archer_vs_skeleton": biased_average_damage(archer_basic["max_hit"], npc_by_id[40]["ranged_mitigation_cap"], archer_basic["roll_high_bias"]),
        "longbow_vs_skeleton": biased_average_damage(archer_longbow_compare["max_hit"], npc_by_id[40]["ranged_mitigation_cap"], archer_longbow_compare["roll_high_bias"]),
        "shortbow_vs_skeleton": biased_average_damage(archer_shortbow_compare["max_hit"], npc_by_id[40]["ranged_mitigation_cap"], archer_shortbow_compare["roll_high_bias"]),
        "crossbow_vs_skeleton": biased_average_damage(archer_crossbow_compare["max_hit"], npc_by_id[40]["ranged_mitigation_cap"], archer_crossbow_compare["roll_high_bias"]),
        "apprentice_mage_vs_ghost": biased_average_damage(wizard["max_hit"], npc_by_id[15]["magic_mitigation_cap"], wizard["roll_high_bias"]),
        "apprentice_mage_vs_skeleton": biased_average_damage(wizard["max_hit"], npc_by_id[40]["magic_mitigation_cap"], wizard["roll_high_bias"]),
        "rune_melee_vs_blue_dragon": biased_average_damage(rune_set["max_hit"], npc_by_id[202]["melee_mitigation_cap"], rune_set["roll_high_bias"]),
        "battle_mage_vs_blue_dragon": biased_average_damage(battlemage["max_hit"], npc_by_id[202]["magic_mitigation_cap"], battlemage["roll_high_bias"]),
        "battle_mage_vs_kbd": biased_average_damage(battlemage["max_hit"], npc_by_id[477]["magic_mitigation_cap"], battlemage["roll_high_bias"]),
        "tier6_air_mind_vs_lesser_demon": capped_biased_average_damage(tier6_air_mind["max_hit"], tier6_air_mind["spell_capped_max_hit"], npc_by_id[22]["magic_mitigation_cap"], tier6_air_mind["roll_high_bias"]),
        "tier6_air_chaos_vs_lesser_demon": capped_biased_average_damage(tier6_air_chaos["max_hit"], tier6_air_chaos["spell_capped_max_hit"], npc_by_id[22]["magic_mitigation_cap"], tier6_air_chaos["roll_high_bias"]),
        "tier6_air_death_vs_lesser_demon": capped_biased_average_damage(tier6_air_death["max_hit"], tier6_air_death["spell_capped_max_hit"], npc_by_id[22]["magic_mitigation_cap"], tier6_air_death["roll_high_bias"]),
        "tier6_air_blood_vs_lesser_demon": capped_biased_average_damage(tier6_air_blood["max_hit"], tier6_air_blood["spell_capped_max_hit"], npc_by_id[22]["magic_mitigation_cap"], tier6_air_blood["roll_high_bias"]),
        "tier6_scimitar_vs_lesser_demon": biased_average_damage(tier6_scimitar["max_hit"], npc_by_id[22]["melee_mitigation_cap"], tier6_scimitar["roll_high_bias"]),
        "tier6_mace_vs_lesser_demon": biased_average_damage(tier6_mace["max_hit"], npc_by_id[22]["melee_mitigation_cap"], tier6_mace["roll_high_bias"]),
        "tier6_longsword_vs_lesser_demon": biased_average_damage(tier6_longsword["max_hit"], npc_by_id[22]["melee_mitigation_cap"], tier6_longsword["roll_high_bias"]),
        "tier6_battleaxe_vs_lesser_demon": biased_average_damage(tier6_battleaxe["max_hit"], npc_by_id[22]["melee_mitigation_cap"], tier6_battleaxe["roll_high_bias"]),
        "tier6_2h_vs_lesser_demon": biased_average_damage(tier6_2h["max_hit"], npc_by_id[22]["melee_mitigation_cap"], tier6_2h["roll_high_bias"]),
        "tier6_dagger_vs_lesser_demon": biased_average_damage(tier6_dagger["max_hit"], npc_by_id[22]["melee_mitigation_cap"], tier6_dagger["roll_high_bias"]),
        "tier6_spear_vs_lesser_demon": biased_average_damage(tier6_spear["max_hit"], npc_by_id[22]["melee_mitigation_cap"], tier6_spear["roll_high_bias"]),
        "tier6_longbow_vs_lesser_demon": biased_average_damage(tier6_longbow["max_hit"], npc_by_id[22]["ranged_mitigation_cap"], tier6_longbow["roll_high_bias"]),
        "tier6_shortbow_vs_lesser_demon": biased_average_damage(tier6_shortbow["max_hit"], npc_by_id[22]["ranged_mitigation_cap"], tier6_shortbow["roll_high_bias"]),
        "tier6_crossbow_vs_lesser_demon": biased_average_damage(tier6_crossbow["max_hit"], npc_by_id[22]["ranged_mitigation_cap"], tier6_crossbow["roll_high_bias"]),
        "tier6_dart_vs_lesser_demon": biased_average_damage(tier6_dart["max_hit"], npc_by_id[22]["ranged_mitigation_cap"], tier6_dart["roll_high_bias"]),
        "tier6_knife_vs_lesser_demon": biased_average_damage(tier6_knife["max_hit"], npc_by_id[22]["ranged_mitigation_cap"], tier6_knife["roll_high_bias"]),
        "cow_vs_beginner_longsword": npc_average_damage(npc_by_id[6]["melee_max_hit"], beginner_longsword["mitigation_cap"]),
        "lesser_demon_vs_rune_longsword": npc_average_damage(npc_by_id[22]["melee_max_hit"], rune_longsword["mitigation_cap"]),
        "lesser_demon_vs_rune_heavy": npc_average_damage(npc_by_id[22]["melee_max_hit"], rune_heavy["mitigation_cap"]),
        "goblin_vs_bronze_no_armor": npc_average_damage(npc_by_id[4]["melee_max_hit"], bronze_no_armor["mitigation_cap"]),
        "goblin_vs_bronze_set": npc_average_damage(npc_by_id[4]["melee_max_hit"], bronze_set["mitigation_cap"]),
        "goblin_vs_leather": npc_average_damage(npc_by_id[4]["melee_max_hit"], leather_armor_melee["melee_mitigation_cap"]),
        "skeleton_vs_bronze_no_armor": npc_average_damage(npc_by_id[40]["melee_max_hit"], bronze_no_armor["mitigation_cap"]),
        "skeleton_vs_bronze_set": npc_average_damage(npc_by_id[40]["melee_max_hit"], bronze_set["mitigation_cap"]),
        "skeleton_vs_leather_ranged": npc_average_damage(npc_by_id[40]["melee_max_hit"], leather_armor_melee["melee_mitigation_cap"]),
        "skeleton_zero_rate_vs_no_armor": npc_zero_hit_rate(npc_by_id[40]["melee_max_hit"], bronze_no_armor["mitigation_cap"]),
        "skeleton_uniform_avg_vs_no_armor": average_damage(npc_by_id[40]["melee_max_hit"], bronze_no_armor["mitigation_cap"]),
    }

    throughput_fixtures = {
        "beginner_longsword_vs_cow": matchup_fixtures["beginner_longsword_vs_cow"] * beginner_longsword["throughput_multiplier"],
        "rune_longsword_vs_lesser_demon": matchup_fixtures["rune_longsword_vs_lesser_demon"] * rune_longsword["throughput_multiplier"],
        "rune_dagger_vs_baseline": rune_dagger_compare["max_hit"] * rune_dagger_compare["throughput_multiplier"],
        "rune_scimitar_vs_baseline": rune_scimitar_compare["max_hit"] * rune_scimitar_compare["throughput_multiplier"],
        "rune_mace_vs_baseline": rune_mace_compare["max_hit"] * rune_mace_compare["throughput_multiplier"],
        "rune_longsword_vs_baseline": rune_longsword_compare["max_hit"] * rune_longsword_compare["throughput_multiplier"],
        "rune_spear_vs_baseline": rune_spear_compare["max_hit"] * rune_spear_compare["throughput_multiplier"],
        "rune_battleaxe_vs_baseline": rune_battleaxe_compare["max_hit"] * rune_battleaxe_compare["throughput_multiplier"],
        "rune_2h_vs_baseline": rune_2h_compare["max_hit"] * rune_2h_compare["throughput_multiplier"],
        "magic_longbow_vs_baseline": archer_longbow_compare["max_hit"] * archer_longbow_compare["throughput_multiplier"],
        "magic_shortbow_vs_baseline": archer_shortbow_compare["max_hit"] * archer_shortbow_compare["throughput_multiplier"],
        "dragon_crossbow_vs_baseline": archer_crossbow_compare["max_hit"] * archer_crossbow_compare["throughput_multiplier"],
        "rune_dart_vs_baseline": rune_dart_compare["max_hit"] * rune_dart_compare["throughput_multiplier"],
        "rune_knife_vs_baseline": rune_knife_compare["max_hit"] * rune_knife_compare["throughput_multiplier"],
        "tier6_scimitar_vs_lesser_demon": matchup_fixtures["tier6_scimitar_vs_lesser_demon"] * tier6_scimitar["throughput_multiplier"],
        "tier6_air_mind_vs_lesser_demon": matchup_fixtures["tier6_air_mind_vs_lesser_demon"] * tier6_air_mind["throughput_multiplier"],
        "tier6_air_chaos_vs_lesser_demon": matchup_fixtures["tier6_air_chaos_vs_lesser_demon"] * tier6_air_chaos["throughput_multiplier"],
        "tier6_air_death_vs_lesser_demon": matchup_fixtures["tier6_air_death_vs_lesser_demon"] * tier6_air_death["throughput_multiplier"],
        "tier6_air_blood_vs_lesser_demon": matchup_fixtures["tier6_air_blood_vs_lesser_demon"] * tier6_air_blood["throughput_multiplier"],
        "tier6_longsword_vs_lesser_demon": matchup_fixtures["tier6_longsword_vs_lesser_demon"] * tier6_longsword["throughput_multiplier"],
        "tier6_mace_vs_lesser_demon": matchup_fixtures["tier6_mace_vs_lesser_demon"] * tier6_mace["throughput_multiplier"],
        "tier6_battleaxe_vs_lesser_demon": matchup_fixtures["tier6_battleaxe_vs_lesser_demon"] * tier6_battleaxe["throughput_multiplier"],
        "tier6_2h_vs_lesser_demon": matchup_fixtures["tier6_2h_vs_lesser_demon"] * tier6_2h["throughput_multiplier"],
        "tier6_dagger_vs_lesser_demon": matchup_fixtures["tier6_dagger_vs_lesser_demon"] * tier6_dagger["throughput_multiplier"],
        "tier6_spear_vs_lesser_demon": matchup_fixtures["tier6_spear_vs_lesser_demon"] * tier6_spear["throughput_multiplier"],
        "tier6_longbow_vs_lesser_demon": matchup_fixtures["tier6_longbow_vs_lesser_demon"] * tier6_longbow["throughput_multiplier"],
        "tier6_shortbow_vs_lesser_demon": matchup_fixtures["tier6_shortbow_vs_lesser_demon"] * tier6_shortbow["throughput_multiplier"],
        "tier6_crossbow_vs_lesser_demon": matchup_fixtures["tier6_crossbow_vs_lesser_demon"] * tier6_crossbow["throughput_multiplier"],
        "tier6_dart_vs_lesser_demon": matchup_fixtures["tier6_dart_vs_lesser_demon"] * tier6_dart["throughput_multiplier"],
        "tier6_knife_vs_lesser_demon": matchup_fixtures["tier6_knife_vs_lesser_demon"] * tier6_knife["throughput_multiplier"],
    }
    ranged_tier_matchups = {}
    for key, fixture in ranged_tier_checkpoints.items():
        ranged_tier_matchups[key] = biased_average_damage(
            fixture["max_hit"],
            npc_by_id[22]["ranged_mitigation_cap"],
            fixture["roll_high_bias"],
        )
    melee_tier_raw_throughput = {
        key: fixture["max_hit"] * fixture["throughput_multiplier"]
        for key, fixture in melee_tier_checkpoints.items()
    }
    ranged_tier_raw_throughput = {
        key: fixture["max_hit"] * fixture["throughput_multiplier"]
        for key, fixture in ranged_tier_checkpoints.items()
    }

    print_matchup("bronze_melee_no_armor", "Goblin", matchup_fixtures["bronze_no_armor_vs_goblin_melee"], bronze_no_armor["max_hit"], npc_by_id[4]["melee_mitigation_cap"])
    print_matchup("bronze_melee_no_armor", "Ghost", matchup_fixtures["bronze_no_armor_vs_ghost_melee"], bronze_no_armor["max_hit"], npc_by_id[15]["melee_mitigation_cap"])
    print_matchup("bronze_melee_no_armor", "skeleton", matchup_fixtures["bronze_no_armor_vs_skeleton_melee"], bronze_no_armor["max_hit"], npc_by_id[40]["melee_mitigation_cap"])
    print_matchup("beginner_bronze_longsword", "cow", matchup_fixtures["beginner_longsword_vs_cow"], beginner_longsword["max_hit"], npc_by_id[6]["melee_mitigation_cap"])
    print_matchup("rune_longsword_full_rune", "Lesser Demon", matchup_fixtures["rune_longsword_vs_lesser_demon"], rune_longsword["max_hit"], npc_by_id[22]["melee_mitigation_cap"])
    print_matchup("rune_longsword_heavy_rune", "Lesser Demon", matchup_fixtures["rune_heavy_vs_lesser_demon"], rune_heavy["max_hit"], npc_by_id[22]["melee_mitigation_cap"])
    print_matchup("archer_basic", "Ghost", matchup_fixtures["basic_archer_vs_ghost"], archer_basic["max_hit"], npc_by_id[15]["ranged_mitigation_cap"])
    print_matchup("archer_basic", "skeleton", matchup_fixtures["basic_archer_vs_skeleton"], archer_basic["max_hit"], npc_by_id[40]["ranged_mitigation_cap"])
    print_matchup("archer_longbow_compare", "skeleton", matchup_fixtures["longbow_vs_skeleton"], archer_longbow_compare["max_hit"], npc_by_id[40]["ranged_mitigation_cap"])
    print_matchup("archer_shortbow_compare", "skeleton", matchup_fixtures["shortbow_vs_skeleton"], archer_shortbow_compare["max_hit"], npc_by_id[40]["ranged_mitigation_cap"])
    print_matchup("archer_crossbow_compare", "skeleton", matchup_fixtures["crossbow_vs_skeleton"], archer_crossbow_compare["max_hit"], npc_by_id[40]["ranged_mitigation_cap"])
    print_matchup("wizard_apprentice", "Ghost", matchup_fixtures["apprentice_mage_vs_ghost"], wizard["max_hit"], npc_by_id[15]["magic_mitigation_cap"])
    print_matchup("wizard_apprentice", "skeleton", matchup_fixtures["apprentice_mage_vs_skeleton"], wizard["max_hit"], npc_by_id[40]["magic_mitigation_cap"])
    print_matchup("rune_melee", "Blue Dragon", matchup_fixtures["rune_melee_vs_blue_dragon"], rune_set["max_hit"], npc_by_id[202]["melee_mitigation_cap"])
    print_matchup("battle_mage", "Blue Dragon", matchup_fixtures["battle_mage_vs_blue_dragon"], battlemage["max_hit"], npc_by_id[202]["magic_mitigation_cap"])
    print_matchup("battle_mage", "King Black Dragon", matchup_fixtures["battle_mage_vs_kbd"], battlemage["max_hit"], npc_by_id[477]["magic_mitigation_cap"])
    print_matchup("cow", "beginner_bronze_longsword", matchup_fixtures["cow_vs_beginner_longsword"], npc_by_id[6]["melee_max_hit"], beginner_longsword["mitigation_cap"])
    print_matchup("Lesser Demon", "rune_longsword_full_rune", matchup_fixtures["lesser_demon_vs_rune_longsword"], npc_by_id[22]["melee_max_hit"], rune_longsword["mitigation_cap"])
    print_matchup("Lesser Demon", "rune_longsword_heavy_rune", matchup_fixtures["lesser_demon_vs_rune_heavy"], npc_by_id[22]["melee_max_hit"], rune_heavy["mitigation_cap"])
    print_matchup("goblin", "bronze_melee_no_armor", matchup_fixtures["goblin_vs_bronze_no_armor"], npc_by_id[4]["melee_max_hit"], bronze_no_armor["mitigation_cap"])
    print_matchup("goblin", "bronze_melee_bronze_set", matchup_fixtures["goblin_vs_bronze_set"], npc_by_id[4]["melee_max_hit"], bronze_set["mitigation_cap"])
    print_matchup("skeleton", "bronze_melee_no_armor", matchup_fixtures["skeleton_vs_bronze_no_armor"], npc_by_id[40]["melee_max_hit"], bronze_no_armor["mitigation_cap"])
    print_matchup("skeleton", "bronze_melee_bronze_set", matchup_fixtures["skeleton_vs_bronze_set"], npc_by_id[40]["melee_max_hit"], bronze_set["mitigation_cap"])

    print("\n== Lesser Demon Tier-6 Averages ==")
    print_matchup("tier6_air_mind", "Lesser Demon", matchup_fixtures["tier6_air_mind_vs_lesser_demon"], tier6_air_mind["spell_capped_max_hit"], npc_by_id[22]["magic_mitigation_cap"])
    print_matchup("tier6_air_chaos", "Lesser Demon", matchup_fixtures["tier6_air_chaos_vs_lesser_demon"], tier6_air_chaos["spell_capped_max_hit"], npc_by_id[22]["magic_mitigation_cap"])
    print_matchup("tier6_air_death", "Lesser Demon", matchup_fixtures["tier6_air_death_vs_lesser_demon"], tier6_air_death["spell_capped_max_hit"], npc_by_id[22]["magic_mitigation_cap"])
    print_matchup("tier6_air_blood", "Lesser Demon", matchup_fixtures["tier6_air_blood_vs_lesser_demon"], tier6_air_blood["spell_capped_max_hit"], npc_by_id[22]["magic_mitigation_cap"])
    print_matchup("tier6_scimitar", "Lesser Demon", matchup_fixtures["tier6_scimitar_vs_lesser_demon"], tier6_scimitar["max_hit"], npc_by_id[22]["melee_mitigation_cap"])
    print_matchup("tier6_mace", "Lesser Demon", matchup_fixtures["tier6_mace_vs_lesser_demon"], tier6_mace["max_hit"], npc_by_id[22]["melee_mitigation_cap"])
    print_matchup("tier6_longsword", "Lesser Demon", matchup_fixtures["tier6_longsword_vs_lesser_demon"], tier6_longsword["max_hit"], npc_by_id[22]["melee_mitigation_cap"])
    print_matchup("tier6_battleaxe", "Lesser Demon", matchup_fixtures["tier6_battleaxe_vs_lesser_demon"], tier6_battleaxe["max_hit"], npc_by_id[22]["melee_mitigation_cap"])
    print_matchup("tier6_2h", "Lesser Demon", matchup_fixtures["tier6_2h_vs_lesser_demon"], tier6_2h["max_hit"], npc_by_id[22]["melee_mitigation_cap"])
    print_matchup("tier6_dagger", "Lesser Demon", matchup_fixtures["tier6_dagger_vs_lesser_demon"], tier6_dagger["max_hit"], npc_by_id[22]["melee_mitigation_cap"])
    print_matchup("tier6_spear", "Lesser Demon", matchup_fixtures["tier6_spear_vs_lesser_demon"], tier6_spear["max_hit"], npc_by_id[22]["melee_mitigation_cap"])
    print_matchup("tier6_longbow", "Lesser Demon", matchup_fixtures["tier6_longbow_vs_lesser_demon"], tier6_longbow["max_hit"], npc_by_id[22]["ranged_mitigation_cap"])
    print_matchup("tier6_shortbow", "Lesser Demon", matchup_fixtures["tier6_shortbow_vs_lesser_demon"], tier6_shortbow["max_hit"], npc_by_id[22]["ranged_mitigation_cap"])
    print_matchup("tier6_crossbow", "Lesser Demon", matchup_fixtures["tier6_crossbow_vs_lesser_demon"], tier6_crossbow["max_hit"], npc_by_id[22]["ranged_mitigation_cap"])
    print_matchup("tier6_dart", "Lesser Demon", matchup_fixtures["tier6_dart_vs_lesser_demon"], tier6_dart["max_hit"], npc_by_id[22]["ranged_mitigation_cap"])
    print_matchup("tier6_knife", "Lesser Demon", matchup_fixtures["tier6_knife_vs_lesser_demon"], tier6_knife["max_hit"], npc_by_id[22]["ranged_mitigation_cap"])

    print("\n== Throughput Fixtures ==")
    print_throughput_matchup("beginner_bronze_longsword", "cow", matchup_fixtures["beginner_longsword_vs_cow"], throughput_fixtures["beginner_longsword_vs_cow"])
    print_throughput_matchup("rune_longsword_full_rune", "Lesser Demon", matchup_fixtures["rune_longsword_vs_lesser_demon"], throughput_fixtures["rune_longsword_vs_lesser_demon"])
    print(f"THROUGHPUT rune_dagger_compare: {throughput_fixtures['rune_dagger_vs_baseline']:.2f}")
    print(f"THROUGHPUT rune_scimitar_compare: {throughput_fixtures['rune_scimitar_vs_baseline']:.2f}")
    print(f"THROUGHPUT rune_mace_compare: {throughput_fixtures['rune_mace_vs_baseline']:.2f}")
    print(f"THROUGHPUT rune_longsword_compare: {throughput_fixtures['rune_longsword_vs_baseline']:.2f}")
    print(f"THROUGHPUT rune_spear_compare: {throughput_fixtures['rune_spear_vs_baseline']:.2f}")
    print(f"THROUGHPUT rune_battleaxe_compare: {throughput_fixtures['rune_battleaxe_vs_baseline']:.2f}")
    print(f"THROUGHPUT rune_2h_compare: {throughput_fixtures['rune_2h_vs_baseline']:.2f}")
    print(f"THROUGHPUT magic_longbow_compare: {throughput_fixtures['magic_longbow_vs_baseline']:.2f}")
    print(f"THROUGHPUT magic_shortbow_compare: {throughput_fixtures['magic_shortbow_vs_baseline']:.2f}")
    print(f"THROUGHPUT dragon_crossbow_compare: {throughput_fixtures['dragon_crossbow_vs_baseline']:.2f}")
    print(f"THROUGHPUT rune_dart_compare: {throughput_fixtures['rune_dart_vs_baseline']:.2f}")
    print(f"THROUGHPUT rune_knife_compare: {throughput_fixtures['rune_knife_vs_baseline']:.2f}")
    print(f"THROUGHPUT tier6_air_mind_vs_lesser_demon: {throughput_fixtures['tier6_air_mind_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_air_chaos_vs_lesser_demon: {throughput_fixtures['tier6_air_chaos_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_air_death_vs_lesser_demon: {throughput_fixtures['tier6_air_death_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_air_blood_vs_lesser_demon: {throughput_fixtures['tier6_air_blood_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_mace_vs_lesser_demon: {throughput_fixtures['tier6_mace_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_scimitar_vs_lesser_demon: {throughput_fixtures['tier6_scimitar_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_longsword_vs_lesser_demon: {throughput_fixtures['tier6_longsword_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_battleaxe_vs_lesser_demon: {throughput_fixtures['tier6_battleaxe_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_2h_vs_lesser_demon: {throughput_fixtures['tier6_2h_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_dagger_vs_lesser_demon: {throughput_fixtures['tier6_dagger_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_spear_vs_lesser_demon: {throughput_fixtures['tier6_spear_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_longbow_vs_lesser_demon: {throughput_fixtures['tier6_longbow_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_shortbow_vs_lesser_demon: {throughput_fixtures['tier6_shortbow_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_crossbow_vs_lesser_demon: {throughput_fixtures['tier6_crossbow_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_dart_vs_lesser_demon: {throughput_fixtures['tier6_dart_vs_lesser_demon']:.2f}")
    print(f"THROUGHPUT tier6_knife_vs_lesser_demon: {throughput_fixtures['tier6_knife_vs_lesser_demon']:.2f}")
    print("\n== Ranged Tier Throughput Checkpoints ==")
    for tier in (1, 6, 10):
        for family_name in ("longbow", "shortbow", "crossbow", "dart", "knife"):
            fixture = ranged_tier_checkpoints[(tier, family_name)]
            average = ranged_tier_matchups[(tier, family_name)]
            throughput = average * fixture["throughput_multiplier"]
            print(
                f"THROUGHPUT tier{tier}_{family_name}_vs_lesser_demon: "
                f"{throughput:.2f}"
            )
    print("\n== Ranged Vs Melee DPS Anchors ==")
    for tier in (1, 6, 10):
        print(
            f"TIER {tier}: 3bar_ref={melee_tier_raw_throughput[(tier, 'longsword')]:.2f} "
            f"2bar_ref={melee_tier_raw_throughput[(tier, 'spear')]:.2f}"
        )
        for family_name in ("longbow", "shortbow", "crossbow", "dart", "knife"):
            print(
                f"DPS tier{tier}_{family_name}: "
                f"{ranged_tier_raw_throughput[(tier, family_name)]:.2f}"
            )

    print("\n== Style Matchup Matrix ==")
    style_attackers = [
        ("tier6_longsword", tier6_longsword),
        ("tier6_longbow", tier6_longbow),
        ("tier6_air_blood", tier6_air_blood),
    ]
    matrix_targets = [
        npc_by_id[3],    # chicken
        npc_by_id[6],    # cow
        npc_by_id[15],   # ghost
        npc_by_id[40],   # skeleton
        npc_by_id[41],   # zombie
        npc_by_id[65],   # guard
        npc_by_id[66],   # black knight
        npc_by_id[102],  # white knight
        npc_by_id[74],   # giant spider
        npc_by_id[70],   # scorpion
        npc_by_id[43],   # giant bat
        npc_by_id[22],   # lesser demon
        npc_by_id[202],  # blue dragon
        npc_by_id[789],  # battle mage
        npc_by_id[477],  # king black dragon
    ]
    style_matrix = {}
    for npc_fixture_data in matrix_targets:
        style_matrix[npc_fixture_data["id"]] = {}
        print(
            f"TARGET {npc_fixture_data['name']} ({npc_fixture_data['id']}): "
            f"melee_def={npc_fixture_data['melee_defense']} "
            f"ranged_def={npc_fixture_data['ranged_defense']} "
            f"magic_def={npc_fixture_data['magic_defense']}"
        )
        for attacker_name, attacker_fixture in style_attackers:
            average, throughput = style_matrix_entry(attacker_fixture, npc_fixture_data)
            style_matrix[npc_fixture_data["id"]][attacker_name] = {
                "average": average,
                "throughput": throughput,
            }
            print(
                f"STYLE {attacker_name} -> {npc_fixture_data['name']}: "
                f"avg_hit={average:.2f} throughput_avg={throughput:.2f}"
            )

    early_band_targets = [
        npc_by_id[4],   # goblin
        npc_by_id[15],  # ghost
        npc_by_id[23],  # giant spider (early)
        npc_by_id[40],  # skeleton
        npc_by_id[41],  # zombie
        npc_by_id[70],  # scorpion
    ]
    mid_band_targets = [
        npc_by_id[65],   # guard
        npc_by_id[188],  # bear
        npc_by_id[232],  # bandit
        npc_by_id[43],   # giant bat
        npc_by_id[271],  # poison scorpion
        npc_by_id[789],  # battle mage
    ]
    high_band_targets = [
        npc_by_id[22],   # lesser demon
        npc_by_id[184],  # greater demon
        npc_by_id[202],  # blue dragon
        npc_by_id[324],  # hero
        npc_by_id[531],  # ogre chieftan
        npc_by_id[768],  # death wing
    ]
    boss_band_targets = [
        npc_by_id[291],  # black dragon
        npc_by_id[477],  # king black dragon
        npc_by_id[809],  # balrog
    ]

    print_style_band("Early", style_attackers, early_band_targets)
    print_style_band("Mid", style_attackers, mid_band_targets)
    print_style_band("High", style_attackers, high_band_targets)
    print_style_band("Boss", style_attackers, boss_band_targets)

    check(npc_by_id[4]["melee_offense"] <= npc_by_id[40]["melee_offense"], "Goblins should not outscale skeleton offense")
    check(npc_by_id[3]["melee_defense"] > npc_by_id[3]["ranged_defense"] == npc_by_id[3]["magic_defense"], "Chickens should keep light melee cover while staying nearly undefended to ranged and magic")
    check(npc_by_id[6]["melee_defense"] > npc_by_id[6]["ranged_defense"] == npc_by_id[6]["magic_defense"], "Cows should keep light melee cover while staying nearly undefended to ranged and magic")
    check(npc_by_id[19]["ranged_defense"] > npc_by_id[19]["melee_defense"] == npc_by_id[19]["magic_defense"], "Low-tier rats should keep ranged as their full inherited defense style")
    check(npc_by_id[177]["ranged_defense"] > npc_by_id[177]["melee_defense"] == npc_by_id[177]["magic_defense"], "Stronger rats should also keep ranged as their full inherited defense style")
    check(npc_by_id[15]["melee_defense"] > 0 and npc_by_id[15]["ranged_defense"] > 0 and npc_by_id[15]["magic_defense"] > npc_by_id[15]["melee_defense"], "Ghosts should strongly prefer magic defense without dropping other styles to zero")
    check(npc_by_id[40]["ranged_defense"] > npc_by_id[40]["magic_defense"] > npc_by_id[40]["melee_defense"], "Skeletons should prefer ranged defense first, then magic, without dropping melee to zero")
    check(npc_by_id[41]["melee_defense"] > npc_by_id[41]["ranged_defense"] == npc_by_id[41]["magic_defense"], "Zombies should keep melee as their strongest defense while ranged and magic stay secondary")
    check(npc_by_id[22]["magic_defense"] > 0, "Lesser demons should have magic defense")
    check(npc_by_id[202]["melee_defense"] > 0 and npc_by_id[202]["ranged_defense"] > 0 and npc_by_id[202]["magic_defense"] > 0, "Blue dragons should have defense coverage in all styles")
    check(npc_by_id[477]["magic_defense"] >= npc_by_id[202]["magic_defense"], "KBD should not be weaker than blue dragon vs magic")
    check(npc_by_id[789]["magic_defense"] > 0, "Battle mages should have magic defense")
    check(matchup_fixtures["beginner_longsword_vs_cow"] > 0.5, "Beginner bronze-longsword player should deal meaningful damage to cows")
    check(matchup_fixtures["basic_archer_vs_skeleton"] <= matchup_fixtures["basic_archer_vs_ghost"], "Skeletons should stay tougher against early ranged damage than ghosts")
    check(matchup_fixtures["shortbow_vs_skeleton"] <= matchup_fixtures["longbow_vs_skeleton"], "Shortbow direct hits should stay below longbow direct hits")
    check(throughput_fixtures["magic_shortbow_vs_baseline"] >= throughput_fixtures["magic_longbow_vs_baseline"], "Top-tier shortbow throughput should meet or exceed top-tier longbow throughput")
    check(throughput_fixtures["dragon_crossbow_vs_baseline"] < throughput_fixtures["magic_shortbow_vs_baseline"], "Top-tier crossbow should stay below shortbow throughput")
    check(throughput_fixtures["dragon_crossbow_vs_baseline"] < throughput_fixtures["magic_longbow_vs_baseline"], "Top-tier crossbow should stay below longbow throughput")
    check(throughput_fixtures["rune_dart_vs_baseline"] > throughput_fixtures["rune_knife_vs_baseline"], "Rune darts should beat rune knives on throughput")
    check(matchup_fixtures["apprentice_mage_vs_ghost"] <= matchup_fixtures["apprentice_mage_vs_skeleton"], "Ghosts should remain at least as magic-resistant as skeletons")
    check(matchup_fixtures["tier6_air_mind_vs_lesser_demon"] < matchup_fixtures["tier6_air_chaos_vs_lesser_demon"], "Mind-tier spells should stay below chaos-tier spells on lesser demons")
    check(matchup_fixtures["tier6_air_chaos_vs_lesser_demon"] < matchup_fixtures["tier6_air_death_vs_lesser_demon"], "Chaos-tier spells should stay below death-tier spells on lesser demons")
    check(matchup_fixtures["tier6_air_death_vs_lesser_demon"] < matchup_fixtures["tier6_air_blood_vs_lesser_demon"], "Death-tier spells should stay below blood-tier spells on lesser demons")
    check(matchup_fixtures["rune_longsword_vs_lesser_demon"] > 1.0, "Full rune melee should still deal meaningful damage to lesser demons")
    check(matchup_fixtures["tier6_dagger_vs_lesser_demon"] <= matchup_fixtures["tier6_scimitar_vs_lesser_demon"], "Tier-6 dagger should not exceed scimitar on direct lesser-demon damage")
    check(matchup_fixtures["tier6_scimitar_vs_lesser_demon"] <= matchup_fixtures["tier6_spear_vs_lesser_demon"], "Tier-6 spear should sit on or above the 2-bar line before poison")
    check(matchup_fixtures["tier6_spear_vs_lesser_demon"] < matchup_fixtures["tier6_longsword_vs_lesser_demon"], "Tier-6 spear should stay below longsword on direct lesser-demon damage")
    check(matchup_fixtures["tier6_scimitar_vs_lesser_demon"] <= matchup_fixtures["tier6_longsword_vs_lesser_demon"], "Tier-6 scimitar should not exceed longsword on direct lesser-demon damage")
    check(matchup_fixtures["tier6_longsword_vs_lesser_demon"] <= matchup_fixtures["tier6_battleaxe_vs_lesser_demon"], "Tier-6 battleaxe should stay at or above longsword on direct lesser-demon damage")
    check(matchup_fixtures["tier6_battleaxe_vs_lesser_demon"] < matchup_fixtures["tier6_2h_vs_lesser_demon"], "Tier-6 2h should stay above battleaxe on direct lesser-demon damage")
    check(matchup_fixtures["tier6_shortbow_vs_lesser_demon"] <= matchup_fixtures["tier6_longbow_vs_lesser_demon"], "Tier-6 shortbow should not exceed longbow on direct lesser-demon damage")
    check(matchup_fixtures["tier6_crossbow_vs_lesser_demon"] < matchup_fixtures["tier6_shortbow_vs_lesser_demon"], "Tier-6 crossbow should stay below shortbow direct lesser-demon damage")
    check(matchup_fixtures["tier6_crossbow_vs_lesser_demon"] < matchup_fixtures["tier6_longbow_vs_lesser_demon"], "Tier-6 crossbow should stay below longbow direct lesser-demon damage")
    check(throughput_fixtures["tier6_shortbow_vs_lesser_demon"] > throughput_fixtures["tier6_longbow_vs_lesser_demon"], "Tier-6 shortbow should beat longbow on lesser-demon throughput")
    check(throughput_fixtures["tier6_dart_vs_lesser_demon"] > throughput_fixtures["tier6_knife_vs_lesser_demon"], "Tier-6 darts should beat knives on lesser-demon throughput")
    for family_name in ("longbow", "shortbow", "crossbow", "dart", "knife"):
        check(
            ranged_tier_checkpoints[(1, family_name)]["offense"] < ranged_tier_checkpoints[(6, family_name)]["offense"],
            f"Tier-1 {family_name} should stay below tier-6 on raw offense",
        )
        check(
            ranged_tier_checkpoints[(6, family_name)]["offense"] < ranged_tier_checkpoints[(10, family_name)]["offense"],
            f"Tier-6 {family_name} should stay below tier-10 on raw offense",
        )
    for tier in (1, 6, 10):
        longbow_tier = ranged_tier_checkpoints[(tier, "longbow")]
        shortbow_tier = ranged_tier_checkpoints[(tier, "shortbow")]
        crossbow_tier = ranged_tier_checkpoints[(tier, "crossbow")]
        dart_tier = ranged_tier_checkpoints[(tier, "dart")]
        knife_tier = ranged_tier_checkpoints[(tier, "knife")]
        three_bar_ref = melee_tier_raw_throughput[(tier, "longsword")]
        two_bar_ref = melee_tier_raw_throughput[(tier, "spear")]
        check(longbow_tier["offense"] > shortbow_tier["offense"], f"Tier-{tier} longbow should stay above shortbow on raw offense")
        check(shortbow_tier["offense"] > crossbow_tier["offense"], f"Tier-{tier} shortbow should stay above crossbow on raw offense")
        check(shortbow_tier["throughput_multiplier"] > longbow_tier["throughput_multiplier"], f"Tier-{tier} shortbow should stay faster than longbow")
        check(crossbow_tier["throughput_multiplier"] == shortbow_tier["throughput_multiplier"], f"Tier-{tier} crossbow should stay in the shortbow-speed lane")
        check(
            0.80 * three_bar_ref <= ranged_tier_raw_throughput[(tier, "longbow")] <= 1.15 * three_bar_ref,
            f"Tier-{tier} longbow DPS should stay near the 3-bar melee lane",
        )
        check(
            0.80 * three_bar_ref <= ranged_tier_raw_throughput[(tier, "shortbow")] <= 1.15 * three_bar_ref,
            f"Tier-{tier} shortbow DPS should stay near the 3-bar melee lane",
        )
        for family_name in ("crossbow", "dart", "knife"):
            check(
                0.80 * two_bar_ref <= ranged_tier_raw_throughput[(tier, family_name)] <= 1.20 * two_bar_ref,
                f"Tier-{tier} {family_name} DPS should stay near the 2-bar melee lane",
            )
        check(
            ranged_tier_matchups[(tier, "longbow")] >= ranged_tier_matchups[(tier, "shortbow")],
            f"Tier-{tier} longbow should stay at or above shortbow on direct lesser-demon damage",
        )
        check(
            ranged_tier_matchups[(tier, "shortbow")] > ranged_tier_matchups[(tier, "crossbow")],
            f"Tier-{tier} shortbow should stay above crossbow on direct lesser-demon damage",
        )
        check(
            ranged_tier_matchups[(tier, "shortbow")] * shortbow_tier["throughput_multiplier"]
            >= ranged_tier_matchups[(tier, "longbow")] * longbow_tier["throughput_multiplier"],
            f"Tier-{tier} shortbow should meet or beat longbow on lesser-demon throughput",
        )
        check(
            ranged_tier_matchups[(tier, "longbow")] * longbow_tier["throughput_multiplier"]
            > ranged_tier_matchups[(tier, "crossbow")] * crossbow_tier["throughput_multiplier"],
            f"Tier-{tier} longbow should stay above crossbow on lesser-demon throughput",
        )
        check(dart_tier["offense"] < knife_tier["offense"], f"Tier-{tier} darts should trade direct power for speed against knives")
        check(dart_tier["throughput_multiplier"] > knife_tier["throughput_multiplier"], f"Tier-{tier} darts should stay faster than knives")
        check(
            ranged_tier_matchups[(tier, "dart")] * dart_tier["throughput_multiplier"]
            >= ranged_tier_matchups[(tier, "knife")] * knife_tier["throughput_multiplier"],
            f"Tier-{tier} darts should meet or beat knives on lesser-demon throughput",
        )
    check(matchup_fixtures["lesser_demon_vs_rune_heavy"] <= matchup_fixtures["lesser_demon_vs_rune_longsword"], "Heavy rune should not take more lesser demon damage than lighter rune")
    check(matchup_fixtures["rune_melee_vs_blue_dragon"] > 0.5, "Rune melee should still deal measurable damage to blue dragons")
    check(matchup_fixtures["battle_mage_vs_kbd"] <= matchup_fixtures["battle_mage_vs_blue_dragon"], "KBD should not be easier to damage with magic than blue dragon")
    check(matchup_fixtures["cow_vs_beginner_longsword"] < matchup_fixtures["skeleton_vs_bronze_no_armor"], "Cows should be less threatening to beginners than skeletons")
    check(matchup_fixtures["goblin_vs_bronze_set"] < matchup_fixtures["goblin_vs_bronze_no_armor"], "Bronze armor should reduce incoming goblin damage")
    check(matchup_fixtures["goblin_vs_leather"] < matchup_fixtures["goblin_vs_bronze_no_armor"], "Leather armor should still reduce incoming melee damage")
    check(matchup_fixtures["skeleton_vs_bronze_set"] < matchup_fixtures["skeleton_vs_bronze_no_armor"], "Bronze armor should reduce incoming skeleton damage")
    check(matchup_fixtures["skeleton_zero_rate_vs_no_armor"] > 0.0, "NPCs should retain a non-zero zero-hit rate")
    check(matchup_fixtures["skeleton_vs_bronze_no_armor"] < matchup_fixtures["skeleton_uniform_avg_vs_no_armor"], "NPC outgoing damage should be lower-weighted than a flat player-style roll")
    check(npc_by_id[40]["ranged_defense"] > npc_by_id[40]["magic_defense"], "Skeletons should mitigate ranged more than magic")
    check(npc_by_id[40]["magic_defense"] > npc_by_id[40]["melee_defense"], "Skeletons should mitigate magic more than melee")
    check(npc_by_id[41]["melee_defense"] > npc_by_id[41]["ranged_defense"], "Zombies should mitigate melee more than ranged")
    check(npc_by_id[41]["melee_defense"] > npc_by_id[41]["magic_defense"], "Zombies should mitigate melee more than magic")
    check(npc_by_id[65]["melee_defense"] > npc_by_id[65]["ranged_defense"] == npc_by_id[65]["magic_defense"], "Guards should keep only melee at full inherited defense under the audit")
    check(npc_by_id[66]["melee_defense"] > npc_by_id[66]["ranged_defense"] == npc_by_id[66]["magic_defense"], "Black knights should keep only melee at full inherited defense under the audit")
    check(npc_by_id[74]["ranged_defense"] > npc_by_id[74]["melee_defense"] > npc_by_id[74]["magic_defense"], "Giant spiders should favor ranged defense first, then melee")
    check(npc_by_id[43]["ranged_defense"] > npc_by_id[43]["melee_defense"] > 0 and npc_by_id[43]["melee_defense"] >= npc_by_id[43]["magic_defense"], "Giant bats should stay ranged-favored without dropping other defenses to placeholder values")
    check(npc_by_id[70]["ranged_defense"] > npc_by_id[70]["magic_defense"] and npc_by_id[70]["ranged_defense"] > npc_by_id[70]["melee_defense"], "Scorpions should favor ranged defense")
    check(npc_by_id[136]["ranged_defense"] > npc_by_id[136]["melee_defense"] > npc_by_id[136]["magic_defense"], "King scorpions should keep the scorpion defense profile at a higher tier")
    check(npc_by_id[22]["magic_defense"] > npc_by_id[22]["melee_defense"] > npc_by_id[22]["ranged_defense"], "Lesser demons should favor magic defense, then melee")
    check(npc_by_id[202]["melee_defense"] == npc_by_id[202]["magic_defense"] and npc_by_id[202]["melee_defense"] > npc_by_id[202]["ranged_defense"], "Blue dragons should favor melee and magic over ranged")
    check(npc_by_id[263]["ranged_defense"] > npc_by_id[263]["magic_defense"] > npc_by_id[263]["melee_defense"], "Ice spiders should keep the audit-defined ranged-first profile with magic above melee")
    check(npc_by_id[102]["melee_defense"] > npc_by_id[102]["ranged_defense"] == npc_by_id[102]["magic_defense"], "White knights should keep only melee at full inherited defense under the audit")
    check(npc_by_id[277]["melee_defense"] > npc_by_id[277]["ranged_defense"] == npc_by_id[277]["magic_defense"], "Renegade knights should follow the audit knight profile")
    check(npc_by_id[323]["melee_defense"] > npc_by_id[323]["ranged_defense"] == npc_by_id[323]["magic_defense"], "Paladins should follow the audit knight profile")
    check(npc_by_id[324]["melee_defense"] > npc_by_id[324]["ranged_defense"] == npc_by_id[324]["magic_defense"], "Heroes should follow the audit knight profile")
    check(npc_by_id[789]["magic_defense"] > npc_by_id[789]["melee_defense"] == npc_by_id[789]["ranged_defense"], "Battle mages should heavily favor magic defense while keeping melee and ranged equal")
    check(style_matrix[40]["tier6_air_blood"]["throughput"] > style_matrix[40]["tier6_longbow"]["throughput"], "Skeletons should now punish ranged more than magic in the benchmark matrix")
    check(style_matrix[41]["tier6_air_blood"]["throughput"] > style_matrix[41]["tier6_longbow"]["throughput"], "Zombies should now punish ranged more than magic in the benchmark matrix")
    check(style_matrix[65]["tier6_longsword"]["throughput"] < style_matrix[65]["tier6_longbow"]["throughput"], "Guards should be tougher against melee than ranged in the benchmark matrix")
    check(style_matrix[66]["tier6_longsword"]["throughput"] < style_matrix[66]["tier6_longbow"]["throughput"], "Black knights should be tougher against melee than ranged in the benchmark matrix")
    check(style_matrix[102]["tier6_longsword"]["throughput"] < style_matrix[102]["tier6_longbow"]["throughput"], "White knights should be tougher against melee than ranged in the benchmark matrix")
    check(style_matrix[74]["tier6_longsword"]["throughput"] >= style_matrix[74]["tier6_longbow"]["throughput"], "Tier-6 melee should now keep pace with ranged on giant spiders in the benchmark matrix")
    check(style_matrix[43]["tier6_longsword"]["throughput"] >= style_matrix[43]["tier6_longbow"]["throughput"], "Tier-6 melee should now keep pace with ranged on giant bats in the benchmark matrix")
    check(style_matrix[70]["tier6_longsword"]["throughput"] >= style_matrix[70]["tier6_longbow"]["throughput"], "Tier-6 melee should now keep pace with ranged on scorpions in the benchmark matrix")
    check(style_matrix[22]["tier6_air_blood"]["throughput"] < style_matrix[22]["tier6_longbow"]["throughput"], "Lesser demons should be weaker to ranged than magic in the benchmark matrix")
    check(style_matrix[202]["tier6_longsword"]["throughput"] >= style_matrix[202]["tier6_air_blood"]["throughput"], "Blue dragons should still be at least as vulnerable to melee as magic in the benchmark matrix after the melee rebalance")
    check(style_matrix[789]["tier6_air_blood"]["throughput"] < style_matrix[789]["tier6_longsword"]["throughput"], "Battle mages should be sturdier against magic than melee in the benchmark matrix")
    check(style_matrix[477]["tier6_air_blood"]["throughput"] < style_matrix[202]["tier6_air_blood"]["throughput"], "KBD should stay sturdier than blue dragon against magic in the benchmark matrix")

    print("\nPASS: balance fixtures validated")


if __name__ == "__main__":
    main()
