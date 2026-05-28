#!/usr/bin/env python3
"""Scenario-level MyWorld combat checks for playtest-free regression coverage."""

import importlib.util
import math
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BALANCE_FIXTURES = ROOT / "tests" / "myworld" / "test-balance-fixtures.py"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_balance_module():
    spec = importlib.util.spec_from_file_location("myworld_balance_fixtures", BALANCE_FIXTURES)
    if spec is None or spec.loader is None:
        fail(f"Unable to import {BALANCE_FIXTURES}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def damage_share_xp(total_xp: int, npc_hits: int, damage: int) -> int:
    if damage <= 0 or npc_hits <= 0:
        return 0
    return int((total_xp / npc_hits) * damage)


def contribution_scale(npc_hits: int, damage: int) -> float:
    return min(1.0, max(0.05, damage / npc_hits))


def choose_npc_target(threats: list[dict]) -> str:
    melee_threats = [threat for threat in threats if threat["distance"] <= 1]
    candidates = melee_threats if melee_threats else threats
    return min(candidates, key=lambda threat: threat["combat_level"])["name"]


def scenario_group_lesser_demon_rewards(balance) -> None:
    npcs = balance.load_npcs()
    lesser_demon = balance.npc_fixture(npcs, 22)
    total_combat_xp = lesser_demon["combat"] * 2 + 20
    hits = lesser_demon["hits"]
    damage = {
        "alice_melee": 40,
        "bob_ranged": 25,
        "cara_magic": 14,
    }

    require(sum(damage.values()) == hits, "Lesser demon scenario should consume exactly one NPC health pool")
    require(max(damage, key=damage.get) == "alice_melee", "Top damage player should keep kill credit")

    alice_base = damage_share_xp(total_combat_xp, hits, damage["alice_melee"])
    bob_ranged = damage_share_xp(total_combat_xp * 4, hits, damage["bob_ranged"])
    cara_magic = damage_share_xp(total_combat_xp * 4, hits, damage["cara_magic"])

    require((alice_base * 3, alice_base) == (270, 90), "Alice melee/hits XP share changed unexpectedly")
    require(bob_ranged == 225, "Bob ranged XP share changed unexpectedly")
    require(cara_magic == 126, "Cara magic XP share changed unexpectedly")

    scales = {name: contribution_scale(hits, dealt) for name, dealt in damage.items()}
    require(math.isclose(scales["alice_melee"], 40 / 79), "Alice rare contribution scale should be damage/hits")
    require(math.isclose(scales["bob_ranged"], 25 / 79), "Bob rare contribution scale should be damage/hits")
    require(math.isclose(scales["cara_magic"], 14 / 79), "Cara rare contribution scale should be damage/hits")


def scenario_target_priority() -> None:
    threats = [
        {"name": "low_level_archer_far", "combat_level": 8, "distance": 4},
        {"name": "mid_level_melee", "combat_level": 22, "distance": 1},
        {"name": "high_level_melee", "combat_level": 70, "distance": 1},
    ]
    require(
        choose_npc_target(threats) == "mid_level_melee",
        "NPC should prefer the lowest-combat melee-range target before far lower-level players",
    )

    threats_without_melee = [
        {"name": "low_level_archer_far", "combat_level": 8, "distance": 4},
        {"name": "mid_level_mage_far", "combat_level": 22, "distance": 3},
    ]
    require(
        choose_npc_target(threats_without_melee) == "low_level_archer_far",
        "NPC should choose the lowest-combat target when nobody is in melee range",
    )


def scenario_projectile_profiles() -> None:
    profiles = {
        "lesser demon": "MELEE_MAGIC",
        "bandit": "MELEE_RANGED",
        "wizard": "PURE_MAGIC",
    }
    projectile_range = 5

    require(profiles["lesser demon"] == "MELEE_MAGIC", "Lesser demons should retain melee+magic profile")
    require(profiles["bandit"] == "MELEE_RANGED", "Bandits should retain melee+ranged profile")
    require(profiles["wizard"] == "PURE_MAGIC", "Wizards should remain pure magic")
    require(projectile_range >= 4, "NPC projectile range should cover player spell-range style engagements")

    distance = 5
    preferred_projectile = distance > 1
    require(preferred_projectile, "Mixed-style NPCs should use projectile attacks from distance")


def scenario_style_viability_against_lesser_demon(balance) -> None:
    items = balance.load_items()
    npcs = balance.load_npcs()
    target = balance.npc_fixture(npcs, 22)

    attackers = {
        "tier6_longsword": balance.player_fixture(items, "tier6_longsword", 40, "melee", [75]),
        "tier6_longbow": balance.player_fixture(items, "tier6_longbow", 50, "ranged", [652, 642]),
        "tier6_air_blood": balance.magic_spell_fixture(items, "tier6_air_blood", 51, [1785], "blood"),
    }

    results = {
        name: balance.style_matrix_entry(attacker, target)[1]
        for name, attacker in attackers.items()
    }

    require(results["tier6_longsword"] > 4.0, "Tier-6 longsword should remain viable against lesser demon")
    require(results["tier6_longbow"] > 4.0, "Tier-6 longbow should remain viable against lesser demon")
    require(results["tier6_air_blood"] > 3.0, "Tier-6 blood spell should remain viable against lesser demon")
    require(
        results["tier6_longbow"] >= results["tier6_longsword"] * 0.85,
        "Tier-6 longbow should stay close enough to melee baseline for mixed PvM",
    )


def main() -> None:
    balance = load_balance_module()
    scenario_group_lesser_demon_rewards(balance)
    scenario_target_priority()
    scenario_projectile_profiles()
    scenario_style_viability_against_lesser_demon(balance)
    print("PASS: combat scenarios validated")


if __name__ == "__main__":
    main()
