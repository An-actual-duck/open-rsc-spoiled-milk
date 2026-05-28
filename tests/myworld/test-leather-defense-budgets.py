#!/usr/bin/env python3
import json
import math
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFS_DIR = ROOT / "server" / "conf" / "server" / "defs"
ITEMS_PATH = DEFS_DIR / "ItemDefsMyWorld.json"

FAMILY_NPC = {
    "cow": 6,
    "goblin": 4,
    "unicorn": 0,
    "bear": 188,
    "black unicorn": 296,
    "scorpion carapace": 70,
    "wolf": 243,
    "spider carapace": 74,
    "giant": 61,
    "ogre": 531,
    "baby dragon": 203,
    "magic spider carapace": 263,
    "moss giant": 104,
    "ice giant": 135,
    "demon": 22,
    "hellhound": 294,
    "fire giant": 344,
    "blue dragon": 202,
    "dragon": 196,
    "red dragon": 201,
    "black demon": 290,
    "black dragon": 291,
    "balrog": 809,
    "king black dragon": 477,
}

FAMILY_BASE_ID = {
    "cow": 1835,
    "goblin": 1840,
    "unicorn": 1845,
    "bear": 1850,
    "black unicorn": 1855,
    "scorpion carapace": 1860,
    "wolf": 1865,
    "spider carapace": 1870,
    "giant": 1875,
    "ogre": 1880,
    "baby dragon": 1885,
    "magic spider carapace": 1890,
    "moss giant": 1895,
    "ice giant": 1900,
    "demon": 1905,
    "hellhound": 1910,
    "fire giant": 1915,
    "blue dragon": 1920,
    "dragon": 1925,
    "red dragon": 1930,
    "black demon": 1935,
    "black dragon": 1940,
    "balrog": 1945,
    "king black dragon": 1950,
}

FAMILY_TIER = {
    "cow": 1,
    "goblin": 1,
    "unicorn": 2,
    "bear": 2,
    "black unicorn": 2,
    "scorpion carapace": 2,
    "wolf": 3,
    "spider carapace": 3,
    "giant": 3,
    "ogre": 4,
    "baby dragon": 4,
    "magic spider carapace": 5,
    "moss giant": 5,
    "ice giant": 5,
    "demon": 6,
    "hellhound": 7,
    "fire giant": 7,
    "blue dragon": 7,
    "dragon": 7,
    "red dragon": 8,
    "black demon": 8,
    "black dragon": 9,
    "balrog": 9,
    "king black dragon": 10,
}

SLOTS = [
    ("coif", 1),
    ("gloves", 2),
    ("boots", 2),
    ("chaps", 3),
    ("cuirass", 4),
]


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_json_array(path: Path) -> list[dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    return payload[next(iter(payload.keys()))]


def load_npcs() -> dict[int, dict]:
    npcs = {}
    for filename in ("NpcDefs.json", "NpcDefsCustom.json"):
        for npc in load_json_array(DEFS_DIR / filename):
            npcs[npc["id"]] = npc
    for npc in load_json_array(DEFS_DIR / "NpcDefsMyWorld.json"):
        npcs.setdefault(npc["id"], {"id": npc["id"]})
        npcs[npc["id"]].update(npc)
    return npcs


def derive_style_defenses(npc: dict) -> dict[str, int]:
    legacy_defense = int(npc.get("defense", 0))
    values = {}
    defaults = {"melee": 1.0, "ranged": 0.5, "magic": 0.5}
    for style, default_multiplier in defaults.items():
        explicit = npc.get(f"{style}Defense")
        if explicit is not None and int(explicit) > 0:
            values[style] = int(explicit)
            continue
        multiplier = npc.get(f"{style}DefenseMultiplier", -1.0)
        if multiplier is None or float(multiplier) < 0.0:
            divisor = npc.get(f"{style}DefenseDivisor", -1.0)
            if divisor is not None and float(divisor) > 0.0:
                multiplier = 1.0 / float(divisor)
            else:
                multiplier = default_multiplier
        values[style] = max(0, int(math.floor(legacy_defense * float(multiplier))))
    return values


def allocate_budget(total_budget: int, source_profile: dict[str, int]) -> dict[str, int]:
    total_profile = sum(source_profile.values())
    if total_profile <= 0:
        return {"melee": 0, "ranged": 0, "magic": 0}

    raw = {
        style: total_budget * source_profile[style] / total_profile
        for style in ("melee", "ranged", "magic")
    }
    allocated = {style: int(math.floor(value)) for style, value in raw.items()}
    remainder = total_budget - sum(allocated.values())
    fractions = {style: raw[style] - allocated[style] for style in raw}
    for style in sorted(
        ("melee", "ranged", "magic"),
        key=lambda candidate: (-fractions[candidate], ("melee", "ranged", "magic").index(candidate)),
    )[:remainder]:
        allocated[style] += 1
    return allocated


def require_exact(entry: dict, field: str, expected: int, label: str) -> None:
    actual = entry.get(field, 0)
    if actual != expected:
        fail(f"{label} expected {field}={expected} but found {actual}")


def main() -> None:
    npcs = load_npcs()
    items = {entry["id"]: entry for entry in load_json_array(ITEMS_PATH)}

    for family_name, npc_id in FAMILY_NPC.items():
        source_profile = derive_style_defenses(npcs[npc_id])
        tier = FAMILY_TIER[family_name]
        base_id = FAMILY_BASE_ID[family_name]
        for offset, (slot_name, material_cost) in enumerate(SLOTS):
            item_id = base_id + offset
            entry = items.get(item_id)
            if entry is None:
                fail(f"Missing override for {family_name} {slot_name} ({item_id})")
            budget = int(math.ceil(tier * material_cost * 0.9))
            expected = allocate_budget(budget, source_profile)
            label = f"{family_name} {slot_name} ({item_id})"
            require_exact(entry, "meleeDefense", expected["melee"], label)
            require_exact(entry, "rangedDefense", expected["ranged"], label)
            require_exact(entry, "magicDefense", expected["magic"], label)
            require_exact(entry, "requiredLevel", 0, label)
            require_exact(entry, "requiredSkillID", -1, label)

    print("PASS: leather defense budgets validated")


if __name__ == "__main__":
    main()
