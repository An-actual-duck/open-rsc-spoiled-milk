#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
PLAYER_SOURCE = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
COMMAND_SOURCE = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"
GITIGNORE = ROOT / ".gitignore"


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

public final class LayeredLocationMirrorFixture {
    public static void main(String[] args) {
        LayeredLocationMirror mirror = new LayeredLocationMirror();
        check(!mirror.isInitialized(), "initially uninitialized");
        expectState(() -> mirror.requireCurrent(Point.location(100, 500)));

        int[] packedBoundaries = {0, 943, 944, 1887, 1888, 2831, 2832, 3775};
        int[] expectedLevels = {0, 0, 1, 1, 2, 2, -1, -1};
        int[] expectedY = {0, 943, 0, 943, 0, 943, 0, 943};
        for (int index = 0; index < packedBoundaries.length; index++) {
            Point point = Point.location(100 + index, packedBoundaries[index]);
            WorldLocation synchronizedLocation = mirror.synchronize(point);
            WorldCoordinate coordinate = synchronizedLocation.getCoordinate();
            check(coordinate.getX() == 100 + index, "boundary X");
            check(coordinate.getY() == expectedY[index], "boundary Y");
            check(coordinate.getLevel() == expectedLevels[index], "boundary level");
            check(mirror.requireCurrent(Point.location(100 + index, packedBoundaries[index]))
                .equals(synchronizedLocation), "equal packed value accepted");
        }

        WorldLocation walking = mirror.synchronize(Point.location(216, 468));
        check(walking.getCoordinate().getLevel() == 0, "walking level");
        WorldLocation underground = mirror.synchronize(Point.location(216, 3300));
        check(underground.getCoordinate().getX() == 216, "underground X alignment");
        check(underground.getCoordinate().getY() == 468, "underground Y alignment");
        check(underground.getCoordinate().getLevel() == -1, "underground level");
        check(mirror.requireCurrent(Point.location(216, 3300)).equals(underground),
            "underground current");

        expectState(() -> mirror.requireCurrent(Point.location(216, 468)));
        check(mirror.requireCurrent(Point.location(216, 3300)).equals(underground),
            "stale assertion does not mutate mirror");
        expectIllegal(() -> mirror.synchronize(Point.location(216, 3776)));
        check(mirror.requireCurrent(Point.location(216, 3300)).equals(underground),
            "invalid synchronization preserves prior state");
        expectNull(() -> mirror.synchronize(null));
        expectNull(() -> mirror.requireCurrent(null));
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
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


class LayeredMapsSliceTwelveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-twelve-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/LayeredLocationMirrorFixture.java"
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

    def test_checked_mirror_tracks_boundaries_vertical_changes_and_divergence(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate.LayeredLocationMirrorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_player_is_the_first_read_only_dual_representation_owner(self):
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        command = COMMAND_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        gitignore = GITIGNORE.read_text(encoding="utf-8")

        self.assertIn("private final LayeredLocationMirror layeredLocationMirror", player)
        self.assertIn("public void setInitialLocation(final Point point)", player)
        self.assertIn("layeredLocationMirror.synchronize(point);", player)
        self.assertIn("public WorldLocation getLayeredLocation()", player)
        self.assertIn("layeredLocationMirror.requireCurrent(getLocation())", player)
        self.assertIn("getLayeredRegionKey();\n\t\tif (getConfig().WANT_LAYERED_MAP_PARITY_OBSERVER)", player)
        self.assertNotIn("LegacyPackedPointAdapter.toLegacyPoint", player)
        self.assertNotIn("setLocation(getLayeredLocation", player)
        self.assertIn("player.getLayeredRegionKey();", command)
        self.assertIn("Layered player mirror mismatch", command)
        self.assertIn("### Slice 12: Checked Player layered-location mirror", plan)
        self.assertIn("server/logs/layered-map-parity/*.jsonl", gitignore)


if __name__ == "__main__":
    unittest.main()
