#!/usr/bin/env python3
import json
import re
import subprocess
import sys
import tempfile
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
POLICY_PATH = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "content"
    / "WoolRobeDefense.java"
)
STAT_CALCULATOR_PATH = (
    ROOT
    / "server"
    / "src"
    / "com"
    / "openrsc"
    / "server"
    / "model"
    / "container"
    / "EquipmentStatCalculator.java"
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


def expected_defenses(tier: int, slot: str) -> dict[str, int]:
    cost = {"hat": 1, "top": 4, "skirt": 3, "gloves": 2, "boots": 2}[slot]
    return {
        "meleeDefense": 0,
        "rangedDefense": 0,
        "magicDefense": tier * cost,
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
            expected = expected_defenses(tier, slot)
            label = f"{altar_name} {slot} tier {tier} item {item_id}"
            require_exact(entry, "meleeDefense", expected["meleeDefense"], label)
            require_exact(entry, "rangedDefense", expected["rangedDefense"], label)
            require_exact(entry, "magicDefense", expected["magicDefense"], label)
            require_exact(entry, "requiredLevel", 0, label)
            require_exact(entry, "requiredSkillID", -1, label)


HARNESS = r"""
package com.openrsc.server.content;

public final class WoolRobeDefenseHarness {
    private static void equal(int actual, int expected, String label) {
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        equal(WoolRobeDefense.budget(2, 1), 2, "novice hat");
        equal(WoolRobeDefense.budget(2, 4), 8, "novice top");
        equal(WoolRobeDefense.budget(2, 3), 6, "novice bottom");
        equal(WoolRobeDefense.budget(2, 2), 4, "novice gloves or boots");
        equal(WoolRobeDefense.budget(10, 4), 40, "mythic top");
        equal(WoolRobeDefense.budget(-1, 4), 0, "negative tier");
        equal(WoolRobeDefense.budget(Integer.MAX_VALUE, Integer.MAX_VALUE),
            Integer.MAX_VALUE, "overflow");
    }
}
"""


def compile_and_run_policy() -> None:
    with tempfile.TemporaryDirectory(prefix="wool-robe-defense-") as temp:
        harness = Path(temp) / "WoolRobeDefenseHarness.java"
        harness.write_text(HARNESS, encoding="utf-8")
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-d",
                temp,
                str(POLICY_PATH),
                str(harness),
            ],
            check=True,
        )
        subprocess.run(
            ["java", "-cp", temp, "com.openrsc.server.content.WoolRobeDefenseHarness"],
            check=True,
        )


def main() -> None:
    items_by_id = load_items()
    for item_id, label in ((2794, "unenchanted wool gloves"), (2795, "unenchanted wool boots")):
        entry = items_by_id.get(item_id)
        if entry is None:
            fail(f"{label} missing override for item {item_id}")
        require_exact(entry, "magicDefense", 2, label)
    check_matrix(items_by_id, "WOOL_HAT_PRODUCTS", "hat")
    check_matrix(items_by_id, "WOOL_TOP_PRODUCTS", "top")
    check_matrix(items_by_id, "WOOL_SKIRT_PRODUCTS", "skirt")
    check_matrix(items_by_id, "WOOL_GLOVE_PRODUCTS", "gloves")
    check_matrix(items_by_id, "WOOL_BOOT_PRODUCTS", "boots")
    compile_and_run_policy()

    effects = EFFECTS_PATH.read_text(encoding="utf-8")
    calculator = STAT_CALCULATOR_PATH.read_text(encoding="utf-8")
    if "WoolRobeDefense.budget(tier, resourceCost)" not in effects:
        fail("enchanted wool runtime does not use the shared full defense budget")
    if "WoolRobeDefense.budget(9, resourceCost)" not in calculator:
        fail("blessed wool scaling does not use the shared full defense budget")

    print("PASS: full enchanted wool defense budgets cover all 700 robe products")


if __name__ == "__main__":
    main()
