#!/usr/bin/env python3
import json
import math
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsMyWorld.json"
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

ALTAR_NAMES = [
    "Air",
    "Mind",
    "Water",
    "Earth",
    "Fire",
    "Body",
    "Cosmic",
    "Chaos",
    "Nature",
    "Law",
    "Death",
    "Blood",
    "Soul",
    "Life",
]


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_items() -> dict[int, dict]:
    payload = json.loads(ITEMS_PATH.read_text(encoding="utf-8"))
    return {entry["id"]: entry for entry in payload["items"]}


def parse_matrix(name: str) -> list[list[int]]:
    text = EFFECTS_PATH.read_text(encoding="utf-8")
    match = re.search(
        rf"private static final int\[\]\[\] {name} = \{{(.*?)\n\t\}};",
        text,
        re.S,
    )
    if not match:
        fail(f"Missing matrix {name} in EnchantingItemEffects.java")
    rows = []
    for row_text in re.findall(r"\{([^{}]+)\}", match.group(1)):
        rows.append([int(part.strip()) for part in row_text.split(",") if part.strip()])
    return rows


def allocate_with_priority(total: int, channels: list[str]) -> dict[str, int]:
    base = total // len(channels)
    remainder = total % len(channels)
    result = {channel: base for channel in channels}
    for channel in channels[:remainder]:
        result[channel] += 1
    return result


def expected_defenses(altar_index: int, tier: int, slot: str) -> dict[str, int]:
    baseline = {"hat": 1, "top": 4, "skirt": 3}[slot]
    cost = {"hat": 1, "top": 4, "skirt": 3}[slot]
    remaining = tier * cost - baseline
    magic = baseline
    melee = 0
    ranged = 0

    if altar_index == 0:  # Air
        split = allocate_with_priority(remaining, ["magic", "ranged"])
        magic += split["magic"]
        ranged = split["ranged"]
    elif altar_index == 2:  # Water
        split = allocate_with_priority(remaining, ["magic", "ranged", "melee"])
        magic += split["magic"]
        ranged = split["ranged"]
        melee = split["melee"]
    elif altar_index == 3:  # Earth
        split = allocate_with_priority(remaining, ["magic", "melee"])
        magic += split["magic"]
        melee = split["melee"]
    elif altar_index == 13:  # Life: new cloth line uses intended 0.6x budget
        magic = max(baseline, math.ceil(tier * cost * 0.6))
    else:
        magic += remaining

    return {
        "meleeDefense": melee,
        "rangedDefense": ranged,
        "magicDefense": magic,
    }


def require_exact(entry: dict, field: str, expected: int, label: str) -> None:
    actual = entry.get(field, 0)
    if actual != expected:
        fail(f"{label} expected {field}={expected} but found {actual}")


def check_matrix(items_by_id: dict[int, dict], matrix_name: str, slot: str) -> None:
    rows = parse_matrix(matrix_name)
    for altar_index, row in enumerate(rows):
        altar_name = ALTAR_NAMES[altar_index]
        for tier, item_id in enumerate(row, start=1):
            entry = items_by_id.get(item_id)
            if entry is None:
                fail(f"{altar_name} {slot} tier {tier} missing override for item {item_id}")
            expected = expected_defenses(altar_index, tier, slot)
            label = f"{altar_name} {slot} tier {tier} item {item_id}"
            require_exact(entry, "meleeDefense", expected["meleeDefense"], label)
            require_exact(entry, "rangedDefense", expected["rangedDefense"], label)
            require_exact(entry, "magicDefense", expected["magicDefense"], label)
            require_exact(entry, "requiredLevel", 0, label)
            require_exact(entry, "requiredSkillID", -1, label)


def main() -> None:
    items_by_id = load_items()
    check_matrix(items_by_id, "WOOL_HAT_PRODUCTS", "hat")
    check_matrix(items_by_id, "WOOL_TOP_PRODUCTS", "top")
    check_matrix(items_by_id, "WOOL_SKIRT_PRODUCTS", "skirt")
    print("PASS: enchanted robe defense overrides cover all robe products")


if __name__ == "__main__":
    main()
