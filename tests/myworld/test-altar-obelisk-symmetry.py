#!/usr/bin/env python3

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
RUNECRAFT_LOCS = ROOT / "server/conf/server/defs/locs/SceneryLocsRunecraft.json"
MYWORLD_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldSceneryLocs.json"

SERVER_OBELISK_IDS = {
    "air": 303,
    "water": 300,
    "earth": 304,
    "fire": 301,
    "mind": 1298,
    "body": 1299,
    "cosmic": 1300,
    "chaos": 1301,
    "nature": 1302,
    "law": 1303,
    "death": 1304,
    "blood": 1305,
    "soul": 1306,
    "life": 1322,
}


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def expected_corners(anchor: tuple[int, int]) -> set[tuple[int, int]]:
    x, y = anchor
    return {(x - 2, y + 3), (x + 3, y + 3), (x + 3, y - 2), (x - 2, y - 2)}


def load_scenery(path: Path) -> list[dict]:
    return json.loads(path.read_text(encoding="utf-8"))["sceneries"]


def parse_int_array(text: str, name: str) -> list[int]:
    match = re.search(rf'{name} = new int\[\] \{{(?P<body>.*?)\n\t\}};', text, re.S)
    if not match:
        fail(f"Could not parse client {name} array")
    return [int(value) for value in re.findall(r'\d+', match.group("body"))]


def parse_client_arrays() -> tuple[
    list[str],
    list[int],
    list[int],
    list[tuple[int, int]],
    list[list[tuple[int, int]]],
]:
    text = CLIENT.read_text(encoding="utf-8")
    elements_match = re.search(r'ALTAR_ELEMENTS = new String\[\] \{(?P<body>.*?)\n\t\};', text, re.S)
    tiles_match = re.search(r'ALTAR_TILES = new int\[\]\[\] \{(?P<body>.*?)\n\t\};', text, re.S)
    obelisks_match = re.search(r'ALTAR_OBELISK_TILES = new int\[\]\[\]\[\] \{(?P<body>.*?)\n\t\};', text, re.S)
    if not elements_match or not tiles_match or not obelisks_match:
        fail("Could not parse client altar visual arrays")

    elements = re.findall(r'"([^"]+)"', elements_match.group("body"))
    altar_ids = parse_int_array(text, "ALTAR_OBJECT_IDS")
    obelisk_ids = parse_int_array(text, "ALTAR_OBELISK_OBJECT_IDS")
    anchors = [
        (int(x), int(y))
        for x, y in re.findall(r'\{(\d+),\s*(\d+)\}', tiles_match.group("body"))
    ]
    obelisks = []
    for line in obelisks_match.group("body").splitlines():
        coords = [
            (int(x), int(y))
            for x, y in re.findall(r'\{(\d+),\s*(\d+)\}', line)
        ]
        if coords:
            obelisks.append(coords)

    if not (len(elements) == len(altar_ids) == len(obelisk_ids) == len(anchors) == len(obelisks)):
        fail(
            "Client altar array lengths differ: "
            f"{len(elements)}, {len(altar_ids)}, {len(obelisk_ids)}, "
            f"{len(anchors)}, {len(obelisks)}"
        )
    return elements, altar_ids, obelisk_ids, anchors, obelisks


def main() -> None:
    elements, client_altar_ids, client_obelisk_ids, anchors, client_obelisks = parse_client_arrays()
    for element, anchor, obelisks in zip(elements, anchors, client_obelisks):
        actual = set(obelisks)
        expected = expected_corners(anchor)
        if actual != expected:
            fail(f"Client {element} obelisks were {sorted(actual)}, expected {sorted(expected)}")

    altar_by_id = {
        1191: "air",
        1195: "water",
        1197: "earth",
        1199: "fire",
        1193: "mind",
        1201: "body",
        1203: "cosmic",
        1205: "chaos",
        1207: "nature",
        1209: "law",
        1211: "death",
        1213: "blood",
        1296: "soul",
        1321: "life",
    }
    expected_altar_ids = [next(object_id for object_id, name in altar_by_id.items() if name == element)
                          for element in elements]
    if client_altar_ids != expected_altar_ids:
        fail(f"Client altar owner IDs were {client_altar_ids}, expected {expected_altar_ids}")

    expected_obelisk_ids = [SERVER_OBELISK_IDS[element] for element in elements]
    if client_obelisk_ids != expected_obelisk_ids:
        fail(f"Client obelisk owner IDs were {client_obelisk_ids}, expected {expected_obelisk_ids}")

    overworld_anchors = {}
    for loc in load_scenery(RUNECRAFT_LOCS) + load_scenery(MYWORLD_LOCS):
        element = altar_by_id.get(int(loc["id"]))
        if element is None:
            continue
        x, y = int(loc["pos"]["X"]), int(loc["pos"]["Y"])
        if y >= 90:
            overworld_anchors[element] = (x, y)

    locs = load_scenery(MYWORLD_LOCS)
    for element, object_id in SERVER_OBELISK_IDS.items():
        anchor = overworld_anchors.get(element)
        if anchor is None:
            fail(f"Missing overworld altar anchor for {element}")
        actual = {
            (int(loc["pos"]["X"]), int(loc["pos"]["Y"]))
            for loc in locs
            if int(loc["id"]) == object_id
        }
        expected = expected_corners(anchor)
        if actual != expected:
            fail(f"Server {element} obelisks were {sorted(actual)}, expected {sorted(expected)}")

    text = CLIENT.read_text(encoding="utf-8")
    if "&& this.altarGlyphOwnerPresent[altarIndex]" not in text:
        fail("Altar glyph rendering is not gated by its scenery owner")
    if "if (!this.altarOrbOwnerPresent[altarIndex][orbIndex])" not in text:
        fail("Altar orb rendering is not gated by its obelisk owner")
    if "this.sceneInstanceStore.getGameObjectRevision()" not in text:
        fail("Altar visual ownership does not use the cached scene revision")

    print("PASS: altar visuals are symmetric and scenery-owner gated")


if __name__ == "__main__":
    main()
