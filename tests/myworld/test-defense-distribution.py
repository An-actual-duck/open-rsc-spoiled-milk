#!/usr/bin/env python3
import json
import math
import sys
from collections import Counter, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFS_DIR = ROOT / "server" / "conf" / "server" / "defs"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_json_array(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    return data[next(iter(data.keys()))]


def load_npcs():
    npcs = {}
    for filename in ("NpcDefs.json", "NpcDefsCustom.json"):
        for npc in load_json_array(DEFS_DIR / filename):
            npcs[npc["id"]] = npc
    overrides = {}
    for npc in load_json_array(DEFS_DIR / "NpcDefsMyWorld.json"):
        overrides[npc["id"]] = npc
        npcs.setdefault(npc["id"], {"id": npc["id"]})
        npcs[npc["id"]].update(npc)
    return npcs, overrides


def band_for_combat(combat_level: int) -> str:
    if combat_level <= 25:
        return "early"
    if combat_level <= 75:
        return "mid"
    if combat_level <= 149:
        return "high"
    return "boss"


def derive_style_defenses(npc: dict) -> dict:
    legacy_defense = int(npc.get("defense", 0))
    values = {}
    defaults = {"melee": 1.0, "ranged": 0.5, "magic": 0.5}
    for style, default_multiplier in defaults.items():
        explicit_defense = npc.get(f"{style}Defense")
        if explicit_defense is not None:
            values[style] = int(explicit_defense)
            continue
        multiplier = npc.get(f"{style}DefenseMultiplier", -1.0)
        if multiplier is None or multiplier < 0.0:
            divisor = npc.get(f"{style}DefenseDivisor", -1.0)
            if divisor is not None and divisor > 0.0:
                multiplier = 1.0 / float(divisor)
            else:
                multiplier = default_multiplier
        values[style] = max(0, int(math.floor(legacy_defense * float(multiplier))))
    return values


def preferred_style(style_defenses: dict) -> str:
    best = max(style_defenses.values())
    winners = [style for style, value in style_defenses.items() if value == best]
    return winners[0] if len(winners) == 1 else "tie"


def family_name(name: str) -> str:
    return name.lower().strip()


def main() -> None:
    npcs, overrides = load_npcs()
    attackable = [npc for npc in npcs.values() if npc.get("attackable") == 1 and int(npc.get("combatlvl", 0)) > 0]
    missing_overrides = sorted((npc["id"], npc["name"]) for npc in attackable if npc["id"] not in overrides)
    if missing_overrides:
        fail(f"Attackable NPCs missing MyWorld overrides: {missing_overrides[:10]}")

    for npc_id, override in overrides.items():
        for field in ("meleeDefenseMultiplier", "rangedDefenseMultiplier", "magicDefenseMultiplier"):
            if field in override:
                value = float(override[field])
                if value < 0.0 or value > 1.0:
                    fail(f"{field} must stay in [0.0, 1.0] for npc {npc_id}; found {value}")

    overall = Counter()
    by_band = defaultdict(Counter)
    families_by_preference = defaultdict(set)
    for npc in attackable:
        band = band_for_combat(int(npc["combatlvl"]))
        style = preferred_style(derive_style_defenses(npc))
        overall[style] += 1
        by_band[band][style] += 1
        families_by_preference[style].add(family_name(npc["name"]))

    for band in ("early", "mid", "high"):
        for style in ("melee", "ranged", "magic"):
            if by_band[band][style] <= 0:
                fail(f"{band} band must include at least one {style}-favored NPC")

    if by_band["boss"]["tie"] <= 0:
        fail("Boss band should include at least one full-coverage or tie-profile boss")

    print("== Defense Preference Summary ==")
    print(
        "OVERALL: "
        f"melee={overall['melee']} ranged={overall['ranged']} "
        f"magic={overall['magic']} tie={overall['tie']}"
    )
    for band in ("early", "mid", "high", "boss"):
        counts = by_band[band]
        print(
            f"BAND {band}: "
            f"melee={counts['melee']} ranged={counts['ranged']} "
            f"magic={counts['magic']} tie={counts['tie']}"
        )
    print(
        "FAMILIES: "
        f"melee={len(families_by_preference['melee'])} "
        f"ranged={len(families_by_preference['ranged'])} "
        f"magic={len(families_by_preference['magic'])} "
        f"tie={len(families_by_preference['tie'])}"
    )
    print(f"PASS: defense distribution validated across {len(attackable)} attackable NPCs")


if __name__ == "__main__":
    main()
