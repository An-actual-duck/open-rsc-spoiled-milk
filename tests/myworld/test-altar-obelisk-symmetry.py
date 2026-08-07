#!/usr/bin/env python3

import json
import re
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
ANCHOR = ROOT / "Client_Base/src/orsc/AltarVisualAnchor.java"
RUNECRAFT_LOCS = ROOT / "server/conf/server/defs/locs/SceneryLocsRunecraft.json"
MYWORLD_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldSceneryLocs.json"
LEVEL_STRIDE = 944

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

SERVER_ALTAR_IDS = {
    "air": 1191,
    "water": 1195,
    "earth": 1197,
    "fire": 1199,
    "mind": 1193,
    "body": 1201,
    "cosmic": 1203,
    "chaos": 1205,
    "nature": 1207,
    "law": 1209,
    "death": 1211,
    "blood": 1213,
    "soul": 1296,
    "life": 1321,
}


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def decode_legacy(point: tuple[int, int]) -> tuple[int, int, int]:
    x, packed_y = point
    plane = packed_y // LEVEL_STRIDE
    levels = {0: 0, 1: 1, 2: 2, 3: -1}
    require(plane in levels, f"Unsupported legacy plane {plane} for {point}")
    return x, packed_y % LEVEL_STRIDE, levels[plane]


def expected_corners(anchor: tuple[int, int, int]) -> set[tuple[int, int, int]]:
    x, y, level = anchor
    return {
        (x - 2, y + 3, level),
        (x + 3, y + 3, level),
        (x + 3, y - 2, level),
        (x - 2, y - 2, level),
    }


def load_scenery(path: Path) -> list[dict]:
    return json.loads(path.read_text(encoding="utf-8"))["sceneries"]


def parse_int_array(text: str, name: str) -> list[int]:
    match = re.search(rf'{name} = new int\[\] \{{(?P<body>.*?)\n\t\}};', text, re.S)
    if not match:
        fail(f"Could not parse client {name} array")
    return [int(value) for value in re.findall(r'\d+', match.group("body"))]


def parse_anchor_calls(body: str) -> list[tuple[int, int]]:
    return [
        (int(x), int(y))
        for x, y in re.findall(r'altarAnchor\((\d+),\s*(\d+)\)', body)
    ]


def parse_client_arrays() -> tuple[
    list[str],
    list[int],
    list[int],
    list[tuple[int, int]],
    list[list[tuple[int, int]]],
]:
    text = CLIENT.read_text(encoding="utf-8")
    elements_match = re.search(r'ALTAR_ELEMENTS = new String\[\] \{(?P<body>.*?)\n\t\};', text, re.S)
    anchors_match = re.search(
        r'ALTAR_ANCHORS = new AltarVisualAnchor\[\] \{(?P<body>.*?)\n\t\};', text, re.S)
    obelisks_match = re.search(
        r'ALTAR_OBELISK_ANCHORS = new AltarVisualAnchor\[\]\[\] \{(?P<body>.*?)\n\t\};',
        text,
        re.S,
    )
    if not elements_match or not anchors_match or not obelisks_match:
        fail("Could not parse client altar visual anchor arrays")

    elements = re.findall(r'"([^"]+)"', elements_match.group("body"))
    altar_ids = parse_int_array(text, "ALTAR_OBJECT_IDS")
    obelisk_ids = parse_int_array(text, "ALTAR_OBELISK_OBJECT_IDS")
    anchors = parse_anchor_calls(anchors_match.group("body"))
    obelisks = []
    for line in obelisks_match.group("body").splitlines():
        coordinates = parse_anchor_calls(line)
        if coordinates:
            obelisks.append(coordinates)

    lengths = (len(elements), len(altar_ids), len(obelisk_ids), len(anchors), len(obelisks))
    require(len(set(lengths)) == 1, f"Client altar array lengths differ: {lengths}")
    require(all(len(points) == 4 for points in obelisks), "Every altar must have four obelisk anchors")
    return elements, altar_ids, obelisk_ids, anchors, obelisks


def compile_and_run_anchor_harness() -> None:
    harness = textwrap.dedent(
        """
        package orsc;

        public final class AltarVisualAnchorHarness {
            private static void require(boolean condition, String message) {
                if (!condition) {
                    throw new AssertionError(message);
                }
            }

            public static void main(String[] args) {
                AltarVisualAnchor surface = AltarVisualAnchor.globalFromLegacy(306, 593);
                require(surface.getLevel() == 0, "surface level");
                require(surface.getLogicalY() == 593, "surface logical Y");
                require(surface.projectedY(true) == surface.projectedY(false),
                    "surface projections stay identical");
                require(surface.matchesOwner("global", 0, 306, 593, true),
                    "surface native owner");
                require(surface.matchesOwner("global", 0, 306, 593, false),
                    "surface legacy owner");

                assertUnderground("cosmic", 104, 3556, 724);
                assertUnderground("death", 392, 3540, 708);
                assertUnderground("soul", 611, 3599, 767);

                boolean invalidRejected = false;
                try {
                    AltarVisualAnchor.globalFromLegacy(1, 3776);
                } catch (IllegalArgumentException expected) {
                    invalidRejected = true;
                }
                require(invalidRejected, "out-of-range packed Y rejected");
            }

            private static void assertUnderground(
                String name, int x, int packedY, int logicalY) {
                AltarVisualAnchor anchor =
                    AltarVisualAnchor.globalFromLegacy(x, packedY);
                require(anchor.getLevel() == -1, name + " signed level");
                require(anchor.getLogicalX() == x, name + " logical X");
                require(anchor.getLogicalY() == logicalY, name + " logical Y");
                require(anchor.getLegacyX() == x, name + " legacy X");
                require(anchor.getLegacyY() == packedY, name + " legacy Y");
                require(anchor.matchesOwner("global", -1, x, logicalY, true),
                    name + " native owner");
                require(anchor.matchesOwner("global", -1, x, packedY, false),
                    name + " legacy owner");
                require(!anchor.matchesOwner("global", 0, x, logicalY, true),
                    name + " wrong level rejected");
                require(!anchor.matchesOwner("instance", -1, x, logicalY, true),
                    name + " wrong world space rejected");
                require(!anchor.matchesOwner("global", -1, x, packedY, true),
                    name + " packed Y rejected by native projection");
                require(!anchor.matchesOwner("global", -1, x, logicalY, false),
                    name + " logical Y rejected by legacy projection");
            }
        }
        """
    )
    with tempfile.TemporaryDirectory(prefix="altar-visual-anchor-") as temporary:
        temp = Path(temporary)
        harness_path = temp / "AltarVisualAnchorHarness.java"
        harness_path.write_text(harness, encoding="utf-8")
        result = subprocess.run(
            ["javac", "-d", str(temp), str(ANCHOR), str(harness_path)],
            text=True,
            capture_output=True,
        )
        require(result.returncode == 0, f"Anchor harness compilation failed:\n{result.stderr}")
        result = subprocess.run(
            ["java", "-cp", str(temp), "orsc.AltarVisualAnchorHarness"],
            text=True,
            capture_output=True,
        )
        require(result.returncode == 0, f"Anchor harness failed:\n{result.stderr}")


def main() -> None:
    elements, client_altar_ids, client_obelisk_ids, packed_anchors, packed_obelisks = parse_client_arrays()
    logical_anchors = [decode_legacy(point) for point in packed_anchors]
    logical_obelisks = [
        [decode_legacy(point) for point in points]
        for points in packed_obelisks
    ]

    for element, anchor, obelisks in zip(elements, logical_anchors, logical_obelisks):
        require(
            set(obelisks) == expected_corners(anchor),
            f"Client {element} logical obelisks were {sorted(obelisks)}, "
            f"expected {sorted(expected_corners(anchor))}",
        )

    expected_altar_ids = [SERVER_ALTAR_IDS[element] for element in elements]
    require(client_altar_ids == expected_altar_ids, "Client altar owner IDs differ from server IDs")
    expected_obelisk_ids = [SERVER_OBELISK_IDS[element] for element in elements]
    require(client_obelisk_ids == expected_obelisk_ids, "Client obelisk owner IDs differ from server IDs")

    all_scenery = load_scenery(RUNECRAFT_LOCS) + load_scenery(MYWORLD_LOCS)
    for element, object_id, packed_anchor in zip(elements, client_altar_ids, packed_anchors):
        authoritative = {
            (int(location["pos"]["X"]), int(location["pos"]["Y"]))
            for location in all_scenery
            if int(location["id"]) == object_id
        }
        require(
            packed_anchor in authoritative,
            f"Client {element} anchor {packed_anchor} lacks an authoritative placement",
        )

    myworld_scenery = load_scenery(MYWORLD_LOCS)
    for element, object_id, logical_anchor in zip(elements, client_obelisk_ids, logical_anchors):
        actual = {
            decode_legacy((int(location["pos"]["X"]), int(location["pos"]["Y"])))
            for location in myworld_scenery
            if int(location["id"]) == object_id
        }
        require(
            actual == expected_corners(logical_anchor),
            f"Server {element} logical obelisks were {sorted(actual)}, "
            f"expected {sorted(expected_corners(logical_anchor))}",
        )

    underground = {
        element: anchor
        for element, anchor in zip(elements, logical_anchors)
        if anchor[2] == -1
    }
    require(
        underground == {
            "cosmic": (104, 724, -1),
            "death": (392, 708, -1),
            "soul": (611, 767, -1),
        },
        f"Unexpected underground altar anchors: {underground}",
    )

    # A radius-one native scene is 144 tiles wide. Each underground center and
    # all four obelisks must project inside its authoritative 3x3 window.
    for element in ("cosmic", "death", "soul"):
        index = elements.index(element)
        x, y, _ = logical_anchors[index]
        base_x = (x // 48 - 1) * 48
        base_y = (y // 48 - 1) * 48
        for role, point in [("glyph", logical_anchors[index])] + [
            (f"orb {orb_index}", point)
            for orb_index, point in enumerate(logical_obelisks[index])
        ]:
            local_x = point[0] - base_x
            local_y = point[1] - base_y
            require(
                0 <= local_x < 144 and 0 <= local_y < 144,
                f"{element} {role} escaped native window at {local_x},{local_y}",
            )

    text = CLIENT.read_text(encoding="utf-8")
    require("&& this.altarGlyphOwnerPresent[altarIndex]" in text, "Glyph owner gate missing")
    require("if (!this.altarOrbOwnerPresent[altarIndex][orbIndex])" in text, "Orb owner gate missing")
    require("this.sceneInstanceStore.getGameObjectRevision()" in text, "Scene revision cache missing")
    require("altarVisualOwnerWorldSpace.equals(activeWorldSpace)" in text, "World-space cache key missing")
    require("altarVisualOwnerLevel == activeLevel" in text, "Signed-level cache key missing")
    require("matchesOwner(" in text, "Typed altar owner matching missing")
    require("projectedY(nativeAltarProjection)" in text, "Projection-aware drawing missing")

    compile_and_run_anchor_harness()
    print("PASS: altar visuals use typed logical/legacy anchors and scenery-owner gates")


if __name__ == "__main__":
    main()
