#!/usr/bin/env python3
import json
import math
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEMS_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsMyWorld.json"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_items() -> dict[int, dict]:
    payload = json.loads(ITEMS_PATH.read_text(encoding="utf-8"))
    return {entry["id"]: entry for entry in payload["items"]}


def expected_metal_split(tier: int, bars: int) -> tuple[int, int]:
    total_budget = tier * bars
    melee = int(math.ceil(total_budget * 0.75))
    ranged = total_budget - melee
    return melee, ranged


def require_piece(
    items_by_id: dict[int, dict],
    item_id: int,
    tier: int,
    bars: int,
    label: str,
    secondary_defense: str = "ranged",
) -> None:
    expected_melee, expected_secondary = expected_metal_split(tier, bars)
    entry = items_by_id.get(item_id)
    if entry is None:
        fail(f"Missing metal override for {label} ({item_id})")
    actual_melee = entry.get("meleeDefense", 0)
    actual_ranged = entry.get("rangedDefense", 0)
    actual_magic = entry.get("magicDefense", 0)
    expected_ranged = expected_secondary if secondary_defense == "ranged" else 0
    expected_magic = expected_secondary if secondary_defense == "magic" else 0
    if actual_melee != expected_melee or actual_ranged != expected_ranged or actual_magic != expected_magic:
        fail(
            f"{label} ({item_id}) expected melee/ranged/magic="
            f"{expected_melee}/{expected_ranged}/{expected_magic} but found "
            f"{actual_melee}/{actual_ranged}/{actual_magic}"
        )
    if entry.get("requiredLevel") != 0 or entry.get("requiredSkillID") != -1:
        fail(f"{label} ({item_id}) should not carry equip requirements in overrides")


def main() -> None:
    items_by_id = load_items()

    large_helmets = [
        (1959, 1, "Tin helmet"),
        (1965, 2, "Copper helmet"),
        (108, 3, "Large Bronze Helmet"),
        (6, 4, "Large Iron Helmet"),
        (109, 5, "Large Steel Helmet"),
        (110, 6, "Large Mithril Helmet"),
        (1971, 7, "Titan steel helmet"),
        (111, 8, "Large Adamantite Helmet"),
        (1977, 9, "Orichalcum helmet"),
        (112, 10, "Large Rune Helmet"),
    ]
    square_shields = [
        (2224, 1, "Tin Square Shield"),
        (2225, 2, "Copper Square Shield"),
        (124, 3, "Bronze Square Shield"),
        (3, 4, "Iron Square Shield"),
        (125, 5, "Steel Square Shield"),
        (126, 6, "Mithril Square Shield"),
        (2226, 7, "Titan steel Square Shield"),
        (127, 8, "Adamantite Square Shield"),
        (2227, 9, "Orichalcum Square Shield"),
        (403, 10, "Rune Square Shield"),
    ]
    kite_shields = [
        (1962, 1, "Tin Paladin Shield"),
        (1968, 2, "Copper Paladin Shield"),
        (128, 3, "Bronze Paladin Shield"),
        (2, 4, "Iron Paladin Shield"),
        (129, 5, "Steel Paladin Shield"),
        (130, 6, "Mithril Paladin Shield"),
        (1974, 7, "Titan steel Paladin Shield"),
        (131, 8, "Adamantite Paladin Shield"),
        (1980, 9, "Orichalcum Paladin Shield"),
        (404, 10, "Rune Paladin Shield"),
    ]
    plate_bodies = [
        (1964, 1, "Tin plate mail body"),
        (1970, 2, "Copper plate mail body"),
        (117, 3, "Bronze Plate Mail Body"),
        (8, 4, "Iron Plate Mail Body"),
        (118, 5, "Steel Plate Mail Body"),
        (119, 6, "Mithril Plate Mail Body"),
        (1976, 7, "Titan steel plate mail body"),
        (120, 8, "Adamantite Plate Mail Body"),
        (1982, 9, "Orichalcum plate mail body"),
        (401, 10, "Rune Plate Mail Body"),
    ]
    plate_legs = [
        (1963, 1, "Tin plate mail legs"),
        (1969, 2, "Copper plate mail legs"),
        (206, 3, "Bronze Plate Mail Legs"),
        (9, 4, "Iron Plate Mail Legs"),
        (121, 5, "Steel Plate Mail Legs"),
        (122, 6, "Mithril Plate Mail Legs"),
        (1975, 7, "Titan steel plate mail legs"),
        (123, 8, "Adamantite Plate Mail Legs"),
        (1981, 9, "Orichalcum plate mail legs"),
        (402, 10, "Rune Plate Mail Legs"),
    ]
    plated_skirts = [
        (214, 3, "Bronze Plated Skirt"),
        (215, 4, "Iron Plated Skirt"),
        (225, 5, "Steel Plated skirt"),
        (226, 6, "Mithril Plated skirt"),
        (227, 8, "Adamantite Plated skirt"),
        (406, 10, "Rune skirt"),
    ]

    for item_id, tier, label in large_helmets:
        require_piece(items_by_id, item_id, tier, 2, label)
    for item_id, tier, label in square_shields:
        require_piece(items_by_id, item_id, tier, 2, label)
    for item_id, tier, label in kite_shields:
        require_piece(items_by_id, item_id, tier, 3, label, secondary_defense="magic")
    for item_id, tier, label in plate_bodies:
        require_piece(items_by_id, item_id, tier, 5, label)
    for item_id, tier, label in plate_legs:
        require_piece(items_by_id, item_id, tier, 3, label)
    for item_id, tier, label in plated_skirts:
        require_piece(items_by_id, item_id, tier, 3, label)

    print("PASS: smithable metal defense budgets validated")


if __name__ == "__main__":
    main()
