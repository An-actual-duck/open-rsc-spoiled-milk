#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_PACKAGE = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
AREA_SOURCE = ROOT / "server/src/com/openrsc/server/model/world/Area.java"


POINT_STUB = r"""
package com.openrsc.server.model;

public class Point {
    private final short x;
    private final short y;

    private Point(short x, short y) {
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


AREA_FIXTURE = r"""
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.Area;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldArea;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;

public final class ServerLayeredAreaFixture {
    public static void main(String[] args) {
        for (int plane = 0; plane < LegacyPackedPointAdapter.LEGACY_PLANE_COUNT; plane++) {
            int packedBase = plane * LegacyPackedPointAdapter.LEVEL_STRIDE;
            int level = LegacyPackedPointAdapter.levelForLegacyPlane(plane);
            Area area = new Area(0, 100, packedBase, packedBase + 943, "plane-" + plane);
            WorldArea layered = area.toWorldArea();

            check(layered.getWorldSpace().equals(WorldSpaceId.GLOBAL), "global area");
            check(layered.getLevel() == level, "area level");
            check(layered.getMinX() == 0 && layered.getMaxX() == 100, "area X bounds");
            check(layered.getMinY() == 0 && layered.getMaxY() == 943, "area Y bounds");

            int[] xs = {0, 1, 50, 99, 100};
            for (int packedY = packedBase; packedY <= packedBase + 943; packedY++) {
                for (int x : xs) {
                    Point point = Point.location(x, packedY);
                    WorldLocation location = LegacyPackedPointAdapter.fromLegacyPoint(point);
                    check(area.inBounds(point) == area.inBounds(location),
                        "packed/layered area parity");
                    check(area.inBounds(location) == layered.contains(location),
                        "projected area parity");
                }
            }
        }

        Area mutable = new Area(10, 20, 20, 30, "mutable");
        WorldArea firstSnapshot = mutable.toWorldArea();
        mutable.setMinX(11);
        mutable.setMaxX(21);
        mutable.setMinY(21);
        mutable.setMaxY(31);
        WorldArea secondSnapshot = mutable.toWorldArea();
        check(firstSnapshot.getMinX() == 10 && firstSnapshot.getMaxY() == 30,
            "layered snapshot remains immutable");
        check(secondSnapshot.getMinX() == 11 && secondSnapshot.getMaxX() == 21
            && secondSnapshot.getMinY() == 21 && secondSnapshot.getMaxY() == 31,
            "legacy mutations appear in next snapshot");
        check(mutable.inBounds(Point.location(12, 22)), "legacy mutation behavior");
        check(mutable.inBounds(global(12, 22, 0)), "layered mutation behavior");
        check(!mutable.inBounds(global(12, 22, -1)), "cross-level exclusion");
        check(!mutable.inBounds(location("instance.quest_1", 12, 22, 0)),
            "cross-space exclusion");

        WorldArea deepInstance = new WorldArea(
            location("instance.quest_1", -100, -100, -2),
            location("instance.quest_1", 100, 100, -2));
        check(deepInstance.contains(location("instance.quest_1", 0, 0, -2)),
            "signed instance area");
        check(!deepInstance.contains(location("instance.quest_1", -100, 0, -2)),
            "open minimum boundary");
        check(!deepInstance.contains(location("instance.quest_1", 0, 0, -1)),
            "deep level isolation");
        check(!deepInstance.contains(global(0, 0, -2)), "instance isolation");
        check(deepInstance.equals(new WorldArea(
            location("instance.quest_1", -100, -100, -2),
            location("instance.quest_1", 100, 100, -2))), "area equality");
        check(deepInstance.hashCode() == new WorldArea(
            location("instance.quest_1", -100, -100, -2),
            location("instance.quest_1", 100, 100, -2)).hashCode(), "area hash");
        check(deepInstance.toString().contains("minimumBoundary"), "area description");

        expectIllegal(() -> new Area(0, 10, 943, 944).toWorldArea());
        expectIllegal(() -> new Area(-1, 10, 0, 10).toWorldArea());
        expectIllegal(() -> new Area(0, 32768, 0, 10).toWorldArea());
        expectIllegal(() -> new Area(0, 10, 0, 3776).toWorldArea());
        expectIllegal(() -> new WorldArea(global(1, 0, 0), global(0, 1, 0)));
        expectIllegal(() -> new WorldArea(global(0, 0, 0), global(1, 1, -1)));
        expectIllegal(() -> new WorldArea(
            global(0, 0, 0), location("instance.quest_1", 1, 1, 0)));
        expectNull(() -> new WorldArea(null, global(1, 1, 0)));
        expectNull(() -> deepInstance.contains(null));
    }

    private static WorldLocation global(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static WorldLocation location(String space, int x, int y, int level) {
        return new WorldLocation(
            new WorldSpaceId(space), new WorldCoordinate(x, y, level));
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


class LayeredMapsSliceFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-four-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point_source = cls.temp / "src/com/openrsc/server/model/Point.java"
        point_source.parent.mkdir(parents=True)
        point_source.write_text(POINT_STUB, encoding="utf-8")
        fixture_source = cls.temp / "src/ServerLayeredAreaFixture.java"
        fixture_source.write_text(AREA_FIXTURE, encoding="utf-8")

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
                *(str(path) for path in sorted(SERVER_PACKAGE.glob("*.java"))),
                str(AREA_SOURCE),
                str(fixture_source),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_area_projection_preserves_legacy_behavior(self):
        result = subprocess.run(
            ["java", "-cp", str(self.classes), "ServerLayeredAreaFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_area_and_region_manager_are_the_only_layered_contract_consumers(self):
        package_name = "com.openrsc.server.model.world.coordinate"
        consumers = []
        for source_root in (ROOT / "server/src", ROOT / "server/plugins"):
            for path in source_root.rglob("*.java"):
                if SERVER_PACKAGE in path.parents:
                    continue
                if package_name in path.read_text(encoding="utf-8"):
                    consumers.append(path.relative_to(ROOT).as_posix())
        consumers.sort()
        self.assertEqual(
            [
                "server/src/com/openrsc/server/database/GameDatabase.java",
                "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java",
                "server/src/com/openrsc/server/external/EntityHandler.java",
                "server/src/com/openrsc/server/external/GameObjectLoc.java",
                "server/src/com/openrsc/server/external/ItemLoc.java",
                "server/src/com/openrsc/server/external/NPCLoc.java",
                "server/src/com/openrsc/server/io/WorldEditorTerrainArchive.java",
                "server/src/com/openrsc/server/model/entity/player/Player.java",
                "server/src/com/openrsc/server/model/world/Area.java",
                "server/src/com/openrsc/server/model/world/region/LayeredRegionTileSnapshot.java",
                "server/src/com/openrsc/server/model/world/region/RegionManager.java",
                "server/src/com/openrsc/server/service/PlayerService.java",
            ],
            consumers,
        )

    def test_legacy_area_storage_and_methods_remain_authoritative(self):
        source = AREA_SOURCE.read_text(encoding="utf-8")
        self.assertIn("private int minX, maxX, minY, maxY;", source)
        self.assertIn("return x > minX && x < maxX && y > minY && y < maxY;", source)
        self.assertNotIn("private WorldArea", source)
        self.assertIn("public WorldArea toWorldArea()", source)
        self.assertIn("public boolean inBounds(WorldLocation location)", source)


if __name__ == "__main__":
    unittest.main()
