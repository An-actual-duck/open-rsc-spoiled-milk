#!/usr/bin/env python3
"""Protect stable worn-appearance IDs from append-order drift."""

import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
SERVER = ROOT / "server/src/com/openrsc/server/external/EntityHandler.java"
ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
AUTHENTIC_ITEMS = ROOT / "server/conf/server/defs/ItemDefs.json"

EXPECTED_EXALTED_APPEARANCES = {
    3262: 1047,
    3263: 1047,
    3264: 1047,
    3265: 1047,
    3266: 1048,
    3267: 1049,
    3268: 1050,
    3269: 1049,
    3270: 1051,
    3271: 1052,
    3272: 1042,
    3273: 1053,
    3274: 1054,
    3275: 1055,
    3276: 1056,
    3277: 1057,
    3278: 1058,
    3279: 1059,
    3280: 1060,
}

EXPECTED_SCYTHE_APPEARANCES = {
    1289: 229,  # Authentic holiday Scythe -> client animation 228.
    **{item_id: 1034 for item_id in range(3181, 3191)},
    **{item_id: 1034 for item_id in range(3232, 3235)},
    3270: 1051,  # Exalted Rune Scythe -> client animation 1050.
}

SCYTHE_ANIMATION_MARKERS = {
    229: 'new AnimationDef("scythe", "equipment", 0, 0, true, false, 0));//228',
    1034: (
        'new AnimationDef("scythe", "equipment", 0xF0F0F0, 0, '
        'true, false, 0)); // 1033 - Combat Scythe'
    ),
    1051: (
        'new AnimationDef("scythe", "equipment", EXALTED_RUNE_COLOR, 0, '
        'true, false, 0)); // 1050 - Exalted Rune scythe'
    ),
}


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    client = CLIENT.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    server = SERVER.read_text(encoding="utf-8")
    items = {
        int(item["id"]): item
        for item in json.loads(ITEMS.read_text(encoding="utf-8"))["items"]
    }
    authentic_items = {
        int(item["id"]): item
        for item in json.loads(AUTHENTIC_ITEMS.read_text(encoding="utf-8"))["item"]
    }
    all_items = {**authentic_items, **items}

    for item_id, appearance_id in EXPECTED_EXALTED_APPEARANCES.items():
        actual = items.get(item_id, {}).get("appearanceID")
        if actual != appearance_id:
            fail(
                f"Exalted Rune item {item_id} appearance is {actual}, "
                f"expected stable ID {appearance_id}"
            )

    for item_id, appearance_id in EXPECTED_SCYTHE_APPEARANCES.items():
        item = all_items.get(item_id)
        if item is None:
            fail(f"Scythe item {item_id} is missing from server definitions")
        if "Scythe" not in item["name"]:
            fail(f"Scythe item {item_id} has unexpected name {item['name']!r}")
        actual = item.get("appearanceID")
        if actual != appearance_id:
            fail(
                f"{item['name']} ({item_id}) appearance is {actual}, expected "
                f"one-based scythe appearance {appearance_id}"
            )
        marker = SCYTHE_ANIMATION_MARKERS[appearance_id]
        if marker not in client:
            fail(
                f"{item['name']} ({item_id}) appearance {appearance_id} must resolve "
                f"to client scythe animation {appearance_id - 1}"
            )

    if "int animID = player.layerAnimation[mappedLayer] - 1;" not in mudclient:
        fail("Worn appearance protocol must retain its one-based wire-to-client conversion")

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
        "MYWORLD_RUNE_STAFF_EXTENSION_APPEARANCE_START = 1062",
        "runeIndex < MYWORLD_RUNE_STAFF_STABLE_COLOR_COUNT",
    ):
        if marker not in server:
            fail(f"Server staff appearance mapping is missing {marker}")

    print("PASS: stable equipment animation IDs and all scythe appearances align")


if __name__ == "__main__":
    main()
