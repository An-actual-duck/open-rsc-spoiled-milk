#!/usr/bin/env python3

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FORMULAE = ROOT / "server/src/com/openrsc/server/util/rsc/Formulae.java"
MINING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/mining/Mining.java"
WOODCUTTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/woodcutting/Woodcutting.java"
JUNGLE = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/woodcutting/WoodcutJungle.java"
WOOD_DEF = ROOT / "server/src/com/openrsc/server/external/ObjectWoodcuttingDef.java"
HARVESTING = ROOT / "server/plugins/com/openrsc/server/plugins/custom/skills/harvesting/Harvesting.java"


def method_body(source: str, signature: str, next_signature: str) -> str:
    start = source.find(signature)
    end = source.find(next_signature, start + len(signature))
    if start < 0 or end < 0:
        raise AssertionError(f"unable to isolate {signature}")
    return source[start:end]


def require(source: str, snippet: str, label: str, failures: list[str]) -> None:
    if snippet not in source:
        failures.append(label)


def main() -> int:
    formulae = FORMULAE.read_text(encoding="utf-8")
    mining = MINING.read_text(encoding="utf-8")
    woodcutting = WOODCUTTING.read_text(encoding="utf-8")
    jungle = JUNGLE.read_text(encoding="utf-8")
    wood_def = WOOD_DEF.read_text(encoding="utf-8")
    harvesting = HARVESTING.read_text(encoding="utf-8")
    failures: list[str] = []

    require(
        formulae,
        "public static final int[] miningAxeIDs = {MyWorldItemId.EXALTED_RUNE_PICKAXE",
        "Exalted Rune pickaxe must be the highest-priority Mining tool",
        failures,
    )
    require(
        formulae,
        "public static final int[] miningAxeLvls = {90, 70, 62, 54, 46, 38, 30, 22, 15, 8, 1}",
        "Mining tool level ladder must assign Exalted Rune level 90",
        failures,
    )

    mining_expectations = (
        ("public static int getPickaxeRequiredLevel", "public static int getPickaxeRepeat", 90),
        ("public static int getPickaxeRepeat", "public static int getPickaxeTier", 32),
        ("public static int getPickaxeTier", "public static String getMiningFocusLabel", 11),
        ("private int calcAxeBonus", "private boolean getOre", 56),
    )
    for signature, next_signature, value in mining_expectations:
        body = method_body(mining, signature, next_signature)
        require(
            body,
            f"if (axeId == MyWorldItemId.EXALTED_RUNE_PICKAXE) {{\n\t\t\treturn {value};",
            f"{signature} must assign Exalted Rune pickaxe value {value} before legacy ItemId fallback",
            failures,
        )

    require(
        formulae,
        "public static final int[] woodcuttingAxeIDs = {MyWorldItemId.EXALTED_RUNE_HATCHET",
        "Exalted Rune hatchet must be the highest-priority Woodcutting tool",
        failures,
    )
    require(
        formulae,
        "public static final int[] woodcuttingAxeLvls = {90, 80, 80, 70, 62, 54, 46, 38, 30, 30, 22, 15, 8, 1}",
        "Woodcutting tool level ladder must assign Exalted Rune level 90",
        failures,
    )
    require(
        method_body(woodcutting, "public static int getAxeTier", "public static String getWoodcuttingFocusLabel"),
        "if (axeId == MyWorldItemId.EXALTED_RUNE_HATCHET) {\n\t\t\treturn 12;",
        "ordinary Woodcutting must assign Exalted Rune hatchet tier 12",
        failures,
    )
    require(
        method_body(jungle, "public int calcAxeBonus", "private boolean getLog"),
        "if (axeId == MyWorldItemId.EXALTED_RUNE_HATCHET) {\n\t\t\treturn 64;",
        "jungle Woodcutting must assign Exalted Rune hatchet bonus 64",
        failures,
    )
    require(
        method_body(wood_def, "public double getRate", "private double getExtendedRate"),
        "case MyWorldItemId.EXALTED_RUNE_HATCHET:\n\t\t\t\treturn getExtendedRate(level, 6);",
        "ordinary tree rates must extend the Exalted Rune hatchet beyond Dragon",
        failures,
    )

    require(
        formulae,
        "public static final int[] harvestingShearsIDs = {MyWorldItemId.EXALTED_RUNE_SHEARS",
        "Exalted Rune shears must be the highest-priority Harvesting tool",
        failures,
    )
    require(
        formulae,
        "public static final int[] harvestingShearsLvls = {90, 70, 62, 54, 46, 38, 30, 22, 15, 8, 1}",
        "Harvesting tool level ladder must assign Exalted Rune level 90",
        failures,
    )
    shears_tier = method_body(harvesting, "public static int getShearsTier", "public static int getRequiredShearsTierForLevel")
    require(
        shears_tier,
        "return Formulae.harvestingShearsIDs.length - i;",
        "Harvesting must derive Exalted Rune shears as tier 11 from ordered tool IDs",
        failures,
    )
    shears_level = method_body(harvesting, "public static int getShearsRequiredLevel", "public static int getShearsTier")
    require(
        shears_level,
        "return Formulae.harvestingShearsLvls[i];",
        "Harvesting must derive Exalted Rune shears level 90 from the parallel level ladder",
        failures,
    )

    if "switch (ItemId.getById(shearsId))" in harvesting:
        failures.append("Harvesting shears must not regress to legacy ItemId enum fallthrough")

    if failures:
        print("FAIL: Exalted Rune gathering tool checks failed", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1

    print("PASS: Exalted Rune pickaxe, hatchet, and shears retain top-tier gathering behavior")
    return 0


if __name__ == "__main__":
    sys.exit(main())
