#!/usr/bin/env python3
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL_PACKAGE = ROOT / "tools/layered-maps/src/com/openrsc/layeredmaps"
SERVER_PACKAGE = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
GAME_OBJECT_LOC = ROOT / "server/src/com/openrsc/server/external/GameObjectLoc.java"
ITEM_LOC = ROOT / "server/src/com/openrsc/server/external/ItemLoc.java"
NPC_LOC = ROOT / "server/src/com/openrsc/server/external/NPCLoc.java"


POINT_STUB = r"""
package com.openrsc.server.model;

public class Point {
    protected short x;
    protected short y;

    protected Point() {
    }

    public Point(int x, int y) {
        this((short) x, (short) y);
    }

    public Point(short x, short y) {
        this.x = x;
        this.y = y;
    }

    public static Point location(int x, int y) {
        return location((short) x, (short) y);
    }

    public static Point location(short x, short y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("negative packed point");
        }
        return new Point(x, y);
    }

    public final int getX() {
        return x;
    }

    public final int getY() {
        return y;
    }
}
"""


PLACEMENT_FIXTURE = r"""
import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.external.ItemLoc;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import com.openrsc.server.model.world.coordinate.WorldTileBounds;

public final class ServerLayeredPlacementFixture {
    public static void main(String[] args) {
        for (int packedY = LegacyPackedPointAdapter.MIN_PACKED_Y;
                packedY <= LegacyPackedPointAdapter.MAX_PACKED_Y; packedY++) {
            Point point = Point.location(100, packedY);
            WorldLocation expected = LegacyPackedPointAdapter.fromLegacyPoint(point);

            GameObjectLoc object = new GameObjectLoc(1, point, 0, 0);
            check(object.toWorldLocation().equals(expected), "object placement parity");

            ItemLoc item = new ItemLoc(1, 100, packedY, 1, 1);
            check(item.toWorldLocation().equals(expected), "item placement parity");

            NPCLoc npc = new NPCLoc(1, 100, packedY, 99, 101, packedY, packedY);
            check(npc.toWorldStartLocation().equals(expected), "NPC start parity");
            WorldTileBounds bounds = npc.toWorldRoamingBounds();
            check(bounds.getLevel() == expected.getCoordinate().getLevel(), "NPC bounds level");
            check(bounds.contains(expected), "NPC inclusive bounds");
        }

        GameObjectLoc mutableObject = new GameObjectLoc(1, Point.location(10, 20), 0, 0);
        WorldLocation firstObject = mutableObject.toWorldLocation();
        mutableObject.location = Point.location(11, 21);
        check(firstObject.equals(global(10, 20, 0)), "object snapshot immutable");
        check(mutableObject.toWorldLocation().equals(global(11, 21, 0)),
            "object mutation projection");

        ItemLoc mutableItem = new ItemLoc(1, 10, 20, 1, 1);
        WorldLocation firstItem = mutableItem.toWorldLocation();
        mutableItem.x = 12;
        mutableItem.y = 22;
        check(firstItem.equals(global(10, 20, 0)), "item snapshot immutable");
        check(mutableItem.toWorldLocation().equals(global(12, 22, 0)),
            "item mutation projection");

        NPCLoc mutableNpc = new NPCLoc(1, 10, 20, 9, 11, 19, 21);
        WorldTileBounds firstBounds = mutableNpc.toWorldRoamingBounds();
        mutableNpc.startX = 20;
        mutableNpc.startY = 30;
        mutableNpc.minX = 19;
        mutableNpc.maxX = 21;
        mutableNpc.minY = 29;
        mutableNpc.maxY = 31;
        check(firstBounds.contains(global(9, 19, 0)), "inclusive minimum boundary");
        check(firstBounds.contains(global(11, 21, 0)), "inclusive maximum boundary");
        check(mutableNpc.toWorldStartLocation().equals(global(20, 30, 0)),
            "NPC start mutation projection");
        check(mutableNpc.toWorldRoamingBounds().contains(global(21, 31, 0)),
            "NPC bounds mutation projection");

        WorldTileBounds deepInstance = new WorldTileBounds(
            location("instance.quest_1", -100, -100, -2),
            location("instance.quest_1", 100, 100, -2));
        check(deepInstance.contains(location("instance.quest_1", -100, -100, -2)),
            "deep inclusive minimum");
        check(deepInstance.contains(location("instance.quest_1", 100, 100, -2)),
            "deep inclusive maximum");
        check(!deepInstance.contains(location("instance.quest_1", 101, 100, -2)),
            "outside bounds");
        check(!deepInstance.contains(location("instance.quest_1", 0, 0, -1)),
            "bounds level isolation");
        check(!deepInstance.contains(global(0, 0, -2)), "bounds space isolation");
        check(deepInstance.equals(new WorldTileBounds(
            location("instance.quest_1", -100, -100, -2),
            location("instance.quest_1", 100, 100, -2))), "bounds equality");
        check(deepInstance.hashCode() == new WorldTileBounds(
            location("instance.quest_1", -100, -100, -2),
            location("instance.quest_1", 100, 100, -2)).hashCode(), "bounds hash");

        NPCLoc knownRawAnomaly = new NPCLoc(67, 662, 3534, 658, 667, 3519, 6549);
        check(knownRawAnomaly.toWorldStartLocation().getCoordinate().getLevel() == -1,
            "known anomaly start remains representable");
        expectIllegal(() -> knownRawAnomaly.toWorldRoamingBounds());
        expectIllegal(() -> new NPCLoc(1, 10, 943, 9, 11, 943, 944)
            .toWorldRoamingBounds());
        expectIllegal(() -> new ItemLoc(1, 32768, 0, 1, 1).toWorldLocation());
        expectIllegal(() -> new ItemLoc(1, 0, 3776, 1, 1).toWorldLocation());
        GameObjectLoc nullObject = new GameObjectLoc();
        expectNull(() -> nullObject.toWorldLocation());
        expectIllegal(() -> new WorldTileBounds(global(1, 0, 0), global(0, 1, 0)));
        expectIllegal(() -> new WorldTileBounds(global(0, 0, 0), global(1, 1, -1)));
        expectIllegal(() -> new WorldTileBounds(
            global(0, 0, 0), location("instance.quest_1", 1, 1, 0)));
        expectNull(() -> new WorldTileBounds(null, global(1, 1, 0)));
        expectNull(() -> deepInstance.contains(null));
    }

    private static WorldLocation global(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static WorldLocation location(String space, int x, int y, int level) {
        return new WorldLocation(new WorldSpaceId(space), new WorldCoordinate(x, y, level));
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected checked refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected null refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


class LayeredMapsSliceEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-eight-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point_source = cls.temp / "src/com/openrsc/server/model/Point.java"
        point_source.parent.mkdir(parents=True)
        point_source.write_text(POINT_STUB, encoding="utf-8")
        fixture_source = cls.temp / "src/ServerLayeredPlacementFixture.java"
        fixture_source.write_text(PLACEMENT_FIXTURE, encoding="utf-8")

        subprocess.run(
            [
                "javac",
                "-Xlint:all",
                "-source",
                "8",
                "-target",
                "8",
                "-encoding",
                "UTF-8",
                "-d",
                str(cls.classes),
                str(point_source),
                *(str(path) for path in sorted(TOOL_PACKAGE.glob("*.java"))),
                *(str(path) for path in sorted(SERVER_PACKAGE.glob("*.java"))),
                str(GAME_OBJECT_LOC),
                str(ITEM_LOC),
                str(NPC_LOC),
                str(fixture_source),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_placement_projections_preserve_packed_domain_and_bounds(self):
        result = subprocess.run(
            ["java", "-cp", str(self.classes), "ServerLayeredPlacementFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_real_placement_inventory_and_known_anomaly_remain_preserved(self):
        with tempfile.TemporaryDirectory(prefix="layered-placement-normalize-") as workspace:
            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(self.classes),
                    "com.openrsc.layeredmaps.LayeredMapsCli",
                    "normalize",
                    "--root",
                    str(ROOT),
                    "--workspace",
                    workspace,
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            summary = json.loads(
                (Path(workspace) / "normalization-summary.json").read_text(encoding="utf-8")
            )
            self.assertEqual(40, len(summary["placementSources"]))
            self.assertEqual(49816, summary["summary"]["placementRecordCount"])
            self.assertEqual(60679, summary["summary"]["normalizedCoordinateCount"])
            self.assertEqual(1, summary["summary"]["unresolvedCoordinateCount"])
            self.assertEqual(1, len(summary["findings"]))
            finding = summary["findings"][0]
            self.assertEqual("server/conf/server/defs/locs/NpcLocs.json", finding["sourcePath"])
            self.assertEqual(3376, finding["recordIndex"])
            self.assertEqual("max", finding["field"])
            self.assertEqual(6549, finding["legacyY"])

    def test_projections_are_unused_and_packed_fields_remain_authoritative(self):
        expected_signatures = {
            GAME_OBJECT_LOC: "public final WorldLocation toWorldLocation()",
            ITEM_LOC: "public WorldLocation toWorldLocation()",
            NPC_LOC: "public WorldLocation toWorldStartLocation()",
        }
        for source_path, signature in expected_signatures.items():
            source = source_path.read_text(encoding="utf-8")
            self.assertIn(signature, source)
        self.assertIn("public Point location;", GAME_OBJECT_LOC.read_text(encoding="utf-8"))
        self.assertIn("public int x;", ITEM_LOC.read_text(encoding="utf-8"))
        npc_source = NPC_LOC.read_text(encoding="utf-8")
        self.assertIn("public int startY;", npc_source)
        self.assertIn("public WorldTileBounds toWorldRoamingBounds()", npc_source)

        calls = []
        new_methods = (
            ".toWorldLocation(",
            ".toWorldStartLocation(",
            ".toWorldRoamingBounds(",
        )
        placement_sources = {GAME_OBJECT_LOC, ITEM_LOC, NPC_LOC}
        for source_root in (ROOT / "server/src", ROOT / "server/plugins"):
            for path in source_root.rglob("*.java"):
                if path in placement_sources:
                    continue
                source = path.read_text(encoding="utf-8")
                if any(method in source for method in new_methods):
                    calls.append(path.relative_to(ROOT).as_posix())
        self.assertEqual([], calls)


if __name__ == "__main__":
    unittest.main()
