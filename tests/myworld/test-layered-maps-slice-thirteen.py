#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
PLAYER_SOURCE = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
REGION_MANAGER_SOURCE = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
COMMAND_SOURCE = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
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


REGION_FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

public final class LayeredRegionMembershipMirrorFixture {
    public static void main(String[] args) {
        LayeredRegionMembershipMirror mirror = new LayeredRegionMembershipMirror();
        check(!mirror.isInitialized(), "initially uninitialized");
        expectState(() -> mirror.requireCurrent(global(0, 0, 0)));

        WorldLocation beforeBoundary = global(47, 47, 0);
        WorldRegionKey first = mirror.synchronize(beforeBoundary);
        check(first.getRegionX() == 0 && first.getRegionY() == 0, "initial region");
        check(mirror.requireCurrent(global(1, 1, 0)).equals(first),
            "different tile in same region retains membership");

        WorldLocation afterBoundary = global(48, 47, 0);
        expectState(() -> mirror.requireCurrent(afterBoundary));
        WorldRegionKey second = mirror.synchronize(afterBoundary);
        check(second.getRegionX() == 1 && second.getRegionY() == 0, "X boundary");
        check(!second.equals(first), "boundary changes membership");

        WorldLocation surface = global(216, 468, 0);
        WorldLocation underground = global(216, 468, -1);
        WorldLocation upper = global(216, 468, 1);
        WorldRegionKey surfaceKey = mirror.synchronize(surface);
        expectState(() -> mirror.requireCurrent(underground));
        WorldRegionKey undergroundKey = mirror.synchronize(underground);
        WorldRegionKey upperKey = mirror.synchronize(upper);
        check(!surfaceKey.equals(undergroundKey), "surface and underground isolated");
        check(!surfaceKey.equals(upperKey), "surface and upper isolated");
        check(!undergroundKey.equals(upperKey), "vertical levels isolated");
        check(surfaceKey.getRegionX() == undergroundKey.getRegionX(), "aligned region X");
        check(surfaceKey.getRegionY() == undergroundKey.getRegionY(), "aligned region Y");

        WorldRegionKey negative = mirror.synchronize(global(-1, -49, -2));
        check(negative.getRegionX() == -1 && negative.getRegionY() == -2,
            "signed floor-divided membership");
        WorldLocation instance = new WorldLocation(
            new WorldSpaceId("instance.test"), new WorldCoordinate(-1, -49, -2));
        WorldRegionKey instanceKey = mirror.synchronize(instance);
        check(!instanceKey.equals(negative), "world-space isolation");
        check(mirror.requireCurrent(instance).equals(instanceKey), "instance current");

        expectNull(() -> mirror.synchronize(null));
        expectNull(() -> mirror.requireCurrent(null));
    }

    private static WorldLocation global(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
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


class LayeredMapsSliceThirteenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-thirteen-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredRegionMembershipMirrorFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(REGION_FIXTURE, encoding="utf-8")

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

    def test_shadow_membership_covers_boundaries_levels_signed_space_and_instances(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredRegionMembershipMirrorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_player_shadow_does_not_replace_authoritative_region_storage(self):
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        region_manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        command = COMMAND_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("private final LayeredRegionMembershipMirror", player)
        self.assertIn("synchronizeLayeredMirrors(final Point point)", player)
        self.assertIn("layeredRegionMembershipMirror.synchronize(layeredLocation)", player)
        self.assertIn("public WorldRegionKey getLayeredRegionKey()", player)
        self.assertIn("requireCurrent(getLayeredLocation())", player)
        self.assertIn("player.getLayeredRegionKey();", command)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", region_manager)
        self.assertNotIn("ConcurrentHashMap<WorldRegionKey", region_manager)
        self.assertNotIn("getRegion(getLayeredRegionKey", player)
        self.assertNotIn("setRegion", player)
        self.assertIn("### Slice 13: Checked Player logical-region membership shadow", plan)


if __name__ == "__main__":
    unittest.main()
