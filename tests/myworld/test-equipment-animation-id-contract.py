#!/usr/bin/env python3
"""Protect stable worn-appearance IDs from append-order drift."""

import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
SERVER = ROOT / "server/src/com/openrsc/server/external/EntityHandler.java"
ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"

EXPECTED_EXALTED_APPEARANCES = {
    3262: 1046,
    3263: 1046,
    3264: 1046,
    3265: 1046,
    3266: 1047,
    3267: 1048,
    3268: 1049,
    3269: 1048,
    3270: 1050,
    3271: 1051,
    3272: 1041,
    3273: 1052,
    3274: 1053,
    3275: 1054,
    3276: 1055,
    3277: 1056,
    3278: 1057,
    3279: 1058,
    3280: 1059,
}


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    client = CLIENT.read_text(encoding="utf-8")
    server = SERVER.read_text(encoding="utf-8")
    items = {
        int(item["id"]): item
        for item in json.loads(ITEMS.read_text(encoding="utf-8"))["items"]
    }

    for item_id, appearance_id in EXPECTED_EXALTED_APPEARANCES.items():
        actual = items.get(item_id, {}).get("appearanceID")
        if actual != appearance_id:
            fail(
                f"Exalted Rune item {item_id} appearance is {actual}, "
                f"expected stable ID {appearance_id}"
            )

    reserved = 'new AnimationDef("nothing", "equipment", 0, 0, true, false, 0));//788 - reserved'
    stable_loop = (
        "for (int runeIndex = 0; runeIndex < "
        "MYWORLD_RUNE_STAFF_STABLE_COLOR_COUNT; runeIndex++)"
    )
    extension_loop = (
        "for (int runeIndex = MYWORLD_RUNE_STAFF_STABLE_COLOR_COUNT;"
    )
    exalted_scythe = (
        'new AnimationDef("scythe", "equipment", EXALTED_RUNE_COLOR, 0, '
        "true, false, 0)); // 1050 - Exalted Rune scythe"
    )
    for marker, label in (
        (reserved, "reserved appearance 788"),
        (stable_loop, "stable staff-colour block"),
        (extension_loop, "staff extension block"),
        (exalted_scythe, "Exalted Rune scythe animation"),
        ('verifyAnimationDefinition(1050, "scythe", EXALTED_RUNE_COLOR);', "runtime scythe guard"),
    ):
        if marker not in client:
            fail(f"Client is missing {label}")

    if not (
        client.index(reserved)
        < client.index(stable_loop)
        < client.index(exalted_scythe)
        < client.index(extension_loop)
    ):
        fail("Staff extension animations must remain after the stable equipment block")

    for marker in (
        "MYWORLD_RUNE_STAFF_STABLE_COLOR_COUNT = 15",
        "MYWORLD_RUNE_STAFF_EXTENSION_APPEARANCE_START = 1061",
        "runeIndex < MYWORLD_RUNE_STAFF_STABLE_COLOR_COUNT",
    ):
        if marker not in server:
            fail(f"Server staff appearance mapping is missing {marker}")

    print("PASS: stable equipment animation IDs and Exalted Rune appearances align")


if __name__ == "__main__":
    main()
