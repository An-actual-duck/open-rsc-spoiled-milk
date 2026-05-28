#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FORMULAE_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "util" / "rsc" / "Formulae.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def extract_array_constants(text: str, name: str) -> list[str]:
    pattern = re.compile(rf"public static final int\[\] {name} = \{{(?P<body>.*?)\}};", re.DOTALL)
    match = pattern.search(text)
    if not match:
        fail(f"Could not find Formulae.{name}")
    return re.findall(r"ItemId\.([A-Z0-9_]+)\.id\(\)", match.group("body"))


def require_members(actual: list[str], expected: list[str], label: str) -> None:
    missing = [entry for entry in expected if entry not in actual]
    if missing:
        fail(f"{label} missing expected entries: {', '.join(missing)}")


def main() -> None:
    formulae_text = FORMULAE_PATH.read_text(encoding="utf-8")

    arrow_ids = extract_array_constants(formulae_text, "arrowIDs")
    bolt_ids = extract_array_constants(formulae_text, "boltIDs")
    throwing_ids = extract_array_constants(formulae_text, "throwingIDs")

    require_members(
        arrow_ids,
        [
            "TIN_ARROWS",
            "COPPER_ARROWS",
            "TITAN_STEEL_ARROWS",
            "ORICHALCUM_ARROWS",
            "DRAGON_ARROWS",
            "POISON_DRAGON_ARROWS",
        ],
        "Formulae.arrowIDs",
    )
    require_members(
        bolt_ids,
        [
            "CROSSBOW_BOLTS",
            "COPPER_BOLTS",
            "BRONZE_BOLTS",
            "IRON_BOLTS",
            "STEEL_BOLTS",
            "MITHRIL_BOLTS",
            "TITAN_STEEL_BOLTS",
            "ADAMANTITE_BOLTS",
            "ORICHALCUM_BOLTS",
            "RUNE_BOLTS",
            "DRAGON_BOLTS",
            "POISON_DRAGON_BOLTS",
        ],
        "Formulae.boltIDs",
    )
    require_members(
        throwing_ids,
        [
            "TIN_THROWING_DART",
            "COPPER_THROWING_DART",
            "TITAN_STEEL_THROWING_DART",
            "ORICHALCUM_THROWING_DART",
            "POISONED_TIN_THROWING_DART",
            "POISONED_COPPER_THROWING_DART",
            "POISONED_TITAN_STEEL_THROWING_DART",
            "POISONED_ORICHALCUM_THROWING_DART",
            "TIN_THROWING_KNIFE",
            "COPPER_THROWING_KNIFE",
            "TITAN_STEEL_THROWING_KNIFE",
            "ORICHALCUM_THROWING_KNIFE",
            "POISONED_TIN_THROWING_KNIFE",
            "POISONED_COPPER_THROWING_KNIFE",
            "POISONED_TITAN_STEEL_THROWING_KNIFE",
            "POISONED_ORICHALCUM_THROWING_KNIFE",
        ],
        "Formulae.throwingIDs",
    )
    spear_throwing_ids = [entry for entry in throwing_ids if entry.endswith("_SPEAR")]
    if spear_throwing_ids:
        fail(f"Formulae.throwingIDs should not include melee spears: {', '.join(spear_throwing_ids)}")

    print("PASS: ranged runtime tables validated")
    print(f"arrowIDs entries: {len(arrow_ids)}")
    print(f"boltIDs entries: {len(bolt_ids)}")
    print(f"throwingIDs entries: {len(throwing_ids)}")


if __name__ == "__main__":
    main()
