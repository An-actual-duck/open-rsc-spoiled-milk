#!/usr/bin/env python3
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
OBSERVER_SOURCE = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
REGION_MANAGER_SOURCE = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PLAYER_SOURCE = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
COMMAND_SOURCE = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"
SCHEMA_V1 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v1.schema.json"
SCHEMA_V2 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v2.schema.json"


POINT_STUB = r'''
package com.openrsc.server.model;

public class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static Point location(int x, int y) {
        return new Point(x, y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
'''


MIRROR_FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

public final class LayeredVisibilityWindowMirrorFixture {
    public static void main(String[] args) {
        LayeredVisibilityWindowMirror mirror = new LayeredVisibilityWindowMirror();
        WorldRegionWindow surface = window(WorldSpaceId.GLOBAL, 0, 4, 12, 5, 13);
        check(!mirror.isInitialized(), "new mirror disabled");
        expectState(() -> mirror.requireCurrent(surface));
        check(mirror.synchronize(surface) == surface, "synchronize returns exact value");
        check(mirror.isInitialized(), "mirror initialized");
        check(mirror.requireCurrent(window(WorldSpaceId.GLOBAL, 0, 4, 12, 5, 13)) == surface,
            "equal projection accepted");

        expectState(() -> mirror.requireCurrent(
            window(WorldSpaceId.GLOBAL, -1, 4, 12, 5, 13)));
        expectState(() -> mirror.requireCurrent(
            window(WorldSpaceId.GLOBAL, 0, 5, 12, 6, 13)));
        expectState(() -> mirror.requireCurrent(
            window(new WorldSpaceId("instance.fixture"), 0, 4, 12, 5, 13)));
        check(mirror.requireCurrent(surface) == surface, "refusal does not mutate mirror");
        expectNull(() -> mirror.synchronize(null));
        expectNull(() -> mirror.requireCurrent(null));

        LayeredCoordinateParitySnapshot snapshot = LayeredCoordinateParitySnapshot.capture(
            Point.location(239, 620), 2);
        check(snapshot.getViewGridDistance() == 2, "grid distance");
        check(snapshot.getViewTileRadius() == 16, "tile radius");
        WorldRegionWindow captured = snapshot.getVisibilityWindow();
        check(captured.equals(surface), "captured surface window");
        check(captured.getRegionCount() == 4L, "captured count");
        String json = snapshot.toJsonWithVisibilityWindow();
        check(json.contains("\"worldSpace\":\"global\""), "JSON world space");
        check(json.contains("\"gridDistance\":2"), "JSON grid distance");
        check(json.contains("\"tileRadius\":16"), "JSON tile radius");
        check(json.contains("\"minRegionX\":4"), "JSON min X");
        check(json.contains("\"maxRegionY\":13"), "JSON max Y");
        check(json.contains("\"regionCount\":4"), "JSON count");
        check(snapshot.toCompactString().contains("viewRegions=(4,12..5,13)"),
            "compact window");

        LayeredCoordinateParitySnapshot legacy = LayeredCoordinateParitySnapshot.capture(
            Point.location(239, 620));
        check(legacy.getViewGridDistance() == -1, "legacy diagnostic sentinel");
        check(!legacy.toJson().contains("visibilityWindow"), "v1-compatible snapshot layout");
        expectState(() -> legacy.getVisibilityWindow());
        expectState(() -> legacy.toJsonWithVisibilityWindow());
        expectIllegal(() -> LayeredCoordinateParitySnapshot.capture(
            Point.location(239, 620), -1));
        expectArithmetic(() -> LayeredCoordinateParitySnapshot.capture(
            Point.location(239, 620), Integer.MAX_VALUE));
    }

    private static WorldRegionWindow window(
            WorldSpaceId worldSpace, int level,
            int minX, int minY, int maxX, int maxY) {
        return new WorldRegionWindow(worldSpace, level, minX, minY, maxX, maxY);
    }

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected checked refusal.
        }
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected checked refusal.
        }
    }

    private static void expectArithmetic(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected ArithmeticException");
        } catch (ArithmeticException expected) {
            // Expected checked refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected checked refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceSixteenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-sixteen-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredVisibilityWindowMirrorFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(MIRROR_FIXTURE, encoding="utf-8")

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
                str(point),
                str(fixture),
                *(str(path) for path in sorted(SERVER_COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_checked_window_mirror_and_diagnostic_snapshot_refuse_stale_state(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredVisibilityWindowMirrorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_player_wiring_and_v2_schema_remain_observational(self):
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        command = COMMAND_SOURCE.read_text(encoding="utf-8")
        observer = OBSERVER_SOURCE.read_text(encoding="utf-8")
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("new LayeredVisibilityWindowMirror()", player)
        self.assertIn("public WorldRegionWindow getLayeredVisibilityWindow()", player)
        self.assertIn("layeredVisibilityWindowMirror.synchronize(", player)
        self.assertIn("layeredVisibilityWindowMirror.requireCurrent(projected)", player)
        self.assertIn("getLayeredVisibilityWindow();\n\t\tif (getConfig().WANT_LAYERED_MAP_PARITY_OBSERVER)", player)
        self.assertIn("player.getLayeredVisibilityWindow();", command)
        self.assertIn("player.getConfig().VIEW_DISTANCE", command)
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v20"', observer)
        self.assertIn("toJsonWithVisibilityWindow()", observer)
        self.assertIn("state.viewGridDistance", observer)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertNotIn("ConcurrentHashMap<WorldRegionWindow", manager)
        self.assertNotIn("getRegion(getLayeredVisibilityWindow", player)
        self.assertIn("### Slice 16: Checked Player visibility-window shadow", plan)

        schema_v1 = json.loads(SCHEMA_V1.read_text(encoding="utf-8"))
        schema_v2 = json.loads(SCHEMA_V2.read_text(encoding="utf-8"))
        self.assertEqual("layered-map-parity-event-v1", schema_v1["properties"]["schema"]["const"])
        self.assertEqual("layered-map-parity-event-v2", schema_v2["properties"]["schema"]["const"])
        visibility = schema_v2["$defs"]["visibilityWindow"]
        self.assertEqual(
            {
                "worldSpace",
                "level",
                "gridDistance",
                "tileRadius",
                "minRegionX",
                "minRegionY",
                "maxRegionX",
                "maxRegionY",
                "regionCount",
            },
            set(visibility["required"]),
        )
        self.assertIn("visibilityWindow", schema_v2["$defs"]["snapshot"]["required"])


if __name__ == "__main__":
    unittest.main()
