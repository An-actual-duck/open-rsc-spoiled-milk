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


DELTA_FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import java.util.List;

public final class WorldRegionInterestDeltaFixture {
    public static void main(String[] args) {
        WorldRegionWindow x223 = window(WorldSpaceId.GLOBAL, 0, 1, 10, 7, 15);
        WorldRegionWindow x224 = window(WorldSpaceId.GLOBAL, 0, 2, 10, 7, 15);

        WorldRegionInterestDelta forward = WorldRegionInterestDelta.between(x223, x224, 42);
        check(forward.getEntered().isEmpty(), "forward entered");
        check(forward.getRetained().size() == 36, "forward retained");
        check(forward.getExited().size() == 6, "forward exited");
        check(key(forward.getExited().get(0), 0, 1, 10), "forward first exit");
        check(key(forward.getExited().get(5), 0, 1, 15), "forward last exit");
        check(key(forward.getRetained().get(0), 0, 2, 10), "forward first retain");
        check(!forward.isNoOp(), "forward change");
        check(!forward.changesLevel(), "forward level stable");
        check(!forward.changesWorldSpace(), "forward space stable");

        WorldRegionInterestDelta reverse = WorldRegionInterestDelta.between(x224, x223, 42);
        check(reverse.getEntered().size() == 6, "reverse entered");
        check(reverse.getRetained().size() == 36, "reverse retained");
        check(reverse.getExited().isEmpty(), "reverse exited");
        check(key(reverse.getEntered().get(0), 0, 1, 10), "reverse first enter");

        WorldRegionInterestDelta same = WorldRegionInterestDelta.between(x223, x223, 42);
        check(same.isNoOp(), "same no-op");
        check(same.getRetained().size() == 42, "same retained");
        check(same.getPreviousWindow() == x223 && same.getCurrentWindow() == x223,
            "window identity retained");

        WorldRegionWindow underground = window(WorldSpaceId.GLOBAL, -1, 1, 10, 7, 15);
        WorldRegionInterestDelta level = WorldRegionInterestDelta.between(x223, underground, 42);
        check(level.changesLevel(), "level change");
        check(!level.changesWorldSpace(), "level world space");
        check(level.getEntered().size() == 42 && level.getRetained().isEmpty()
            && level.getExited().size() == 42, "level disjoint");
        check(key(level.getEntered().get(0), -1, 1, 10), "level entered identity");
        check(key(level.getExited().get(0), 0, 1, 10), "level exited identity");

        WorldRegionWindow instance = window(
            new WorldSpaceId("instance.fixture"), 0, 1, 10, 7, 15);
        WorldRegionInterestDelta space = WorldRegionInterestDelta.between(x223, instance, 42);
        check(space.changesWorldSpace(), "world-space change");
        check(!space.changesLevel(), "world-space level stable");
        check(space.getEntered().size() == 42 && space.getRetained().isEmpty()
            && space.getExited().size() == 42, "world-space disjoint");

        WorldRegionWindow shifted = window(WorldSpaceId.GLOBAL, 0, 2, 10, 8, 15);
        WorldRegionInterestDelta shift = WorldRegionInterestDelta.between(x223, shifted, 42);
        check(shift.getEntered().size() == 6 && shift.getRetained().size() == 36
            && shift.getExited().size() == 6, "equal-size shift");
        check(key(shift.getEntered().get(0), 0, 8, 10), "X-major entered order");

        expectUnsupported(() -> forward.getExited().add(
            new WorldRegionKey(WorldSpaceId.GLOBAL, 0, 0, 0)));
        expectIllegal(() -> WorldRegionInterestDelta.between(x223, x224, 41));
        expectIllegal(() -> WorldRegionInterestDelta.between(x223, x224, 0));
        expectIllegal(() -> WorldRegionInterestDelta.between(x223, x224, -1));
        expectIllegal(() -> WorldRegionInterestDelta.between(
            window(WorldSpaceId.GLOBAL, 0, Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0),
            x223,
            42));
        expectNull(() -> WorldRegionInterestDelta.between(null, x224, 42));
        expectNull(() -> WorldRegionInterestDelta.between(x223, null, 42));
        check(forward.toString().contains("retained=36"), "delta string");
    }

    private static WorldRegionWindow window(
            WorldSpaceId space, int level, int minX, int minY, int maxX, int maxY) {
        return new WorldRegionWindow(space, level, minX, minY, maxX, maxY);
    }

    private static boolean key(WorldRegionKey key, int level, int x, int y) {
        return key.getWorldSpace().equals(WorldSpaceId.GLOBAL)
            && key.getLevel() == level
            && key.getRegionX() == x
            && key.getRegionY() == y;
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
            // Expected checked refusal.
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable collection.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceSeventeenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-seventeen-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/WorldRegionInterestDeltaFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(DELTA_FIXTURE, encoding="utf-8")

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

    def test_interest_delta_is_deterministic_level_aware_and_budgeted(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate.WorldRegionInterestDeltaFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_delta_is_not_adopted_by_runtime_storage_or_player(self):
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("compareLayeredRegionInterestResidency(", manager)
        self.assertIn("WorldRegionInterestDelta.between(", manager)
        self.assertNotIn("WorldRegionInterestDelta", player)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertIn("private List<Region> getVisibleRegionWindow", manager)
        self.assertIn("visibleRegionWindowCache.putIfAbsent", manager)
        self.assertNotIn("ConcurrentHashMap<WorldRegionWindow", manager)
        self.assertIn("### Slice 17: Deterministic logical interest delta", plan)


if __name__ == "__main__":
    unittest.main()
