#!/usr/bin/env python3

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"

# Exalted Rune deliberately reuses the established item silhouettes with its
# own colour mask. Keeping the complete family here prevents one item from
# silently drifting to an unrelated sprite (for example, sprite 85 is a staff).
EXPECTED_ICONS = {
    3261: (79, "items:79", "bar"),
    3262: (80, "items:80", "dagger"),
    3263: (1, "items:1", "short sword"),
    3264: (81, "items:81", "long sword"),
    3265: (83, "items:83", "scimitar"),
    3266: (82, "items:82", "two-handed sword"),
    3267: (12, "items:12", "hatchet"),
    3268: (72, "items:72", "pickaxe"),
    3269: (84, "items:84", "battle axe"),
    3270: (434, "items:434", "scythe"),
    3271: (0, "items:0", "mace"),
    3272: (66, "items:66", "shears"),
    3273: (283, "items:283", "spear"),
    3274: (6, "items:6", "helmet"),
    3275: (217, "items:217", "gauntlets"),
    3276: (223, "items:223", "greaves"),
    3277: (3, "items:3", "square shield"),
    3278: (2, "items:2", "paladin shield"),
    3279: (9, "items:9", "plate legs"),
    3280: (8, "items:8", "plate body"),
}


def main() -> int:
    source = ENTITY_HANDLER.read_text(encoding="utf-8")
    block_match = re.search(
        r"private static void addExaltedRuneDefinitions\(\) \{(?P<body>.*?)\n\t\}",
        source,
        re.DOTALL,
    )
    if block_match is None:
        print("FAIL: Exalted Rune client-definition block is missing", file=sys.stderr)
        return 1

    block = block_match.group("body")
    failures: list[str] = []
    resolved: dict[int, tuple[int, str]] = {}
    for match in re.finditer(
        r"setCustomItemDefinition\((?P<id>32(?:6[1-9]|7[0-9]|80)),\s*"
        r"new ItemDef\(.*?\n\s*\d+,\s*(?P<sprite>-?\d+),\s*"
        r'"(?P<asset>items:\d+)"',
        block,
        re.DOTALL,
    ):
        resolved[int(match.group("id"))] = (
            int(match.group("sprite")),
            match.group("asset"),
        )

    shears_call = 'addMetalShearsDefinition("Exalted Rune shears", 3272, 52000, EXALTED_RUNE_COLOR);'
    if shears_call not in block:
        failures.append("Exalted Rune shears no longer use the shared metal-shears definition")
    helper = re.search(
        r"private static void addMetalShearsDefinition\(.*?\{(?P<body>.*?)\n\t\}",
        source,
        re.DOTALL,
    )
    if helper is None or 'price, 66, "items:66"' not in helper.group("body"):
        failures.append("shared metal-shears inventory icon is not items:66")
    else:
        resolved[3272] = (66, "items:66")

    for item_id, (expected_sprite, expected_asset, label) in EXPECTED_ICONS.items():
        actual = resolved.get(item_id)
        if actual is None:
            failures.append(f"missing Exalted Rune {label} client icon definition ({item_id})")
        elif actual != (expected_sprite, expected_asset):
            failures.append(
                f"Exalted Rune {label} ({item_id}) uses {actual}, expected "
                f"({expected_sprite}, {expected_asset!r})"
            )

    if set(resolved) != set(EXPECTED_ICONS):
        failures.append(
            "Exalted Rune icon audit does not exactly cover IDs 3261-3280: "
            f"resolved={sorted(resolved)}"
        )

    if failures:
        print("FAIL: Exalted Rune inventory icon checks failed", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1

    print("PASS: all 20 Exalted Rune inventory icons use their intended silhouettes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
