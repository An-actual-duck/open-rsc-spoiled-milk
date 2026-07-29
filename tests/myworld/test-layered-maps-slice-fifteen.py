#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION_MANAGER_SOURCE = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PLAYER_SOURCE = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


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


WINDOW_FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

public final class WorldRegionWindowFixture {
    public static void main(String[] args) {
        WorldLocation center = global(239, 620, 0);
        WorldRegionWindow window = WorldRegionWindow.around(center, 16);
        check(window.getMinRegionX() == 4 && window.getMaxRegionX() == 5,
            "surface X bounds");
        check(window.getMinRegionY() == 12 && window.getMaxRegionY() == 13,
            "surface Y bounds");
        check(window.getLevel() == 0, "surface level");
        check(window.getRegionCount() == 4L, "surface count");
        check(window.contains(new WorldRegionKey(WorldSpaceId.GLOBAL, 0, 4, 12)),
            "minimum key included");
        check(window.contains(new WorldRegionKey(WorldSpaceId.GLOBAL, 0, 5, 13)),
            "maximum key included");
        check(!window.contains(new WorldRegionKey(WorldSpaceId.GLOBAL, 0, 6, 13)),
            "outside key excluded");
        check(!window.contains(new WorldRegionKey(WorldSpaceId.GLOBAL, -1, 4, 12)),
            "other level excluded");
        check(!window.contains(new WorldRegionKey(new WorldSpaceId("instance.test"), 0, 4, 12)),
            "other world space excluded");

        WorldRegionWindow surfaceEdge = WorldRegionWindow.around(global(0, 0, 0), 16);
        WorldRegionWindow upperEdge = WorldRegionWindow.around(global(0, 0, 1), 16);
        WorldRegionWindow undergroundEdge = WorldRegionWindow.around(global(0, 0, -1), 16);
        check(surfaceEdge.getMinRegionX() == -1 && surfaceEdge.getMaxRegionX() == 0,
            "signed X edge");
        check(surfaceEdge.getMinRegionY() == -1 && surfaceEdge.getMaxRegionY() == 0,
            "signed Y edge");
        check(!surfaceEdge.equals(upperEdge), "upper level isolated");
        check(!surfaceEdge.equals(undergroundEdge), "underground level isolated");

        WorldLocation instanceCenter = new WorldLocation(
            new WorldSpaceId("instance.test"), new WorldCoordinate(-1, -49, -2));
        WorldRegionWindow instance = WorldRegionWindow.around(instanceCenter, 0);
        check(instance.getMinRegionX() == -1 && instance.getMaxRegionX() == -1,
            "negative X floor division");
        check(instance.getMinRegionY() == -2 && instance.getMaxRegionY() == -2,
            "negative Y floor division");
        check(instance.getLevel() == -2, "deep level retained");
        check(instance.getWorldSpace().equals(new WorldSpaceId("instance.test")),
            "instance retained");
        check(instance.getRegionCount() == 1L, "single region count");

        WorldRegionWindow copy = new WorldRegionWindow(
            WorldSpaceId.GLOBAL, 0, 4, 12, 5, 13);
        check(window.equals(copy), "window equality");
        check(window.hashCode() == copy.hashCode(), "window hash");
        check(window.toString().contains("minRegionX=4"), "window string");
        check(WorldRegionKey.REGION_SIZE == 48, "logical region size");

        expectIllegal(() -> WorldRegionWindow.around(center, -1));
        expectIllegal(() -> new WorldRegionWindow(WorldSpaceId.GLOBAL, 0, 1, 0, 0, 0));
        expectIllegal(() -> new WorldRegionWindow(WorldSpaceId.GLOBAL, 0, 0, 1, 0, 0));
        expectArithmetic(() -> WorldRegionWindow.around(global(Integer.MAX_VALUE, 0, 0), 1));
        expectArithmetic(() -> WorldRegionWindow.around(global(0, Integer.MIN_VALUE, 0), 1));
        WorldRegionWindow enormous = new WorldRegionWindow(
            WorldSpaceId.GLOBAL, 0,
            Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE);
        expectArithmetic(() -> enormous.getRegionCount());
        expectNull(() -> WorldRegionWindow.around(null, 0));
        expectNull(() -> new WorldRegionWindow(null, 0, 0, 0, 0, 0));
        expectNull(() -> window.contains(null));
    }

    private static WorldLocation global(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void expectArithmetic(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected ArithmeticException");
        } catch (ArithmeticException expected) {
            // Expected refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceFifteenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-fifteen-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/WorldRegionWindowFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(WINDOW_FIXTURE, encoding="utf-8")

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

    def test_logical_window_covers_signed_bounds_levels_spaces_and_overflow(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate.WorldRegionWindowFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_projection_does_not_adopt_logical_window_for_storage(self):
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("public WorldRegionWindow getLayeredVisibleRegionWindow(", manager)
        self.assertIn("Math.multiplyExact(gridDistance, 8)", manager)
        self.assertIn("return WorldRegionWindow.around(location", manager)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertIn("getVisibleRegionWindow(final Point location", manager)
        self.assertNotIn("ConcurrentHashMap<WorldRegionWindow", manager)
        self.assertNotIn("getRegion(getLayeredVisibleRegionWindow", manager)
        self.assertNotIn("getRegion(getLayeredVisibilityWindow", player)
        self.assertIn("### Slice 15: Logical visibility-window projection", plan)


if __name__ == "__main__":
    unittest.main()
