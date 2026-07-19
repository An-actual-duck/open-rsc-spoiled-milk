#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_PACKAGE = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION_MANAGER_SOURCE = (
    ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)


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


REGION_KEY_FIXTURE = r"""
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;

public final class ServerLayeredRegionKeyFixture {
    public static void main(String[] args) {
        int[] xs = {0, 1, 47, 48, 1008, LegacyPackedPointAdapter.MAX_LEGACY_X};
        for (int packedY = LegacyPackedPointAdapter.MIN_PACKED_Y;
                packedY <= LegacyPackedPointAdapter.MAX_PACKED_Y; packedY++) {
            for (int x : xs) {
                Point point = Point.location(x, packedY);
                WorldLocation location = LegacyPackedPointAdapter.fromLegacyPoint(point);
                WorldCoordinate coordinate = location.getCoordinate();
                WorldRegionKey key = WorldRegionKey.fromLegacyPoint(point);

                check(key.equals(WorldRegionKey.from(location)), "legacy/layered key parity");
                check(key.getWorldSpace().equals(WorldSpaceId.GLOBAL), "global key");
                check(key.getLevel() == coordinate.getLevel(), "key level");
                check(key.getRegionX() == Math.floorDiv(coordinate.getX(), 48), "region X");
                check(key.getRegionY() == Math.floorDiv(coordinate.getY(), 48), "region Y");
            }
        }

        assertPackedStraddle(943, 944, 0, 1);
        assertPackedStraddle(1887, 1888, 1, 2);
        check(Math.floorDiv(2831, 48) != Math.floorDiv(2832, 48),
            "third boundary already separates packed regions");
        WorldRegionKey planeTwoEdge = WorldRegionKey.fromLegacyPoint(Point.location(100, 2831));
        WorldRegionKey undergroundEdge = WorldRegionKey.fromLegacyPoint(Point.location(100, 2832));
        check(planeTwoEdge.getLevel() == 2 && undergroundEdge.getLevel() == -1,
            "third boundary layered levels");
        check(!planeTwoEdge.equals(undergroundEdge), "third boundary key isolation");

        WorldRegionKey signed = WorldRegionKey.from(
            location("instance.quest_1", -1, -49, -2));
        check(signed.getRegionX() == -1 && signed.getRegionY() == -2,
            "signed floor region addressing");
        check(signed.getLevel() == -2, "deep region level");
        WorldRegionKey sameSignedRegion = WorldRegionKey.from(
            location("instance.quest_1", -48, -96, -2));
        check(signed.equals(sameSignedRegion), "same signed region identity");
        check(signed.hashCode() == sameSignedRegion.hashCode(), "region key hash");
        check(!signed.equals(WorldRegionKey.from(global(-1, -49, -2))),
            "world-space key isolation");
        check(!signed.equals(WorldRegionKey.from(
            location("instance.quest_1", -1, -49, -1))), "level key isolation");
        check(signed.toString().contains("regionX=-1"), "region key description");

        expectNull(() -> WorldRegionKey.from(null));
        expectNull(() -> WorldRegionKey.fromLegacyPoint(null));
        expectNull(() -> new WorldRegionKey(null, 0, 0, 0));
    }

    private static void assertPackedStraddle(
            int belowY, int aboveY, int belowLevel, int aboveLevel) {
        check(Math.floorDiv(belowY, 48) == Math.floorDiv(aboveY, 48),
            "legacy packed region straddles level boundary");
        WorldRegionKey below = WorldRegionKey.fromLegacyPoint(Point.location(100, belowY));
        WorldRegionKey above = WorldRegionKey.fromLegacyPoint(Point.location(100, aboveY));
        check(below.getLevel() == belowLevel && above.getLevel() == aboveLevel,
            "straddled layered levels");
        check(!below.equals(above), "layered keys separate straddled levels");
    }

    private static WorldLocation global(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static WorldLocation location(String space, int x, int y, int level) {
        return new WorldLocation(
            new WorldSpaceId(space), new WorldCoordinate(x, y, level));
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


class LayeredMapsSliceFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-five-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point_source = cls.temp / "src/com/openrsc/server/model/Point.java"
        point_source.parent.mkdir(parents=True)
        point_source.write_text(POINT_STUB, encoding="utf-8")
        fixture_source = cls.temp / "src/ServerLayeredRegionKeyFixture.java"
        fixture_source.write_text(REGION_KEY_FIXTURE, encoding="utf-8")

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
                str(fixture_source),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_region_key_parity_and_level_isolation(self):
        result = subprocess.run(
            ["java", "-cp", str(self.classes), "ServerLayeredRegionKeyFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_manager_exposes_projection_without_replacing_storage(self):
        source = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        self.assertIn(
            "ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>> regions;",
            source,
        )
        self.assertIn("final int regionX = x / Constants.REGION_SIZE;", source)
        self.assertIn("final int regionY = y / Constants.REGION_SIZE;", source)
        self.assertIn("public WorldRegionKey getLayeredRegionKey(final Point", source)
        self.assertIn("public WorldRegionKey getLayeredRegionKey(final WorldLocation", source)
        self.assertIn("return WorldRegionKey.fromLegacyPoint(objectCoordinates);", source)
        self.assertIn("return WorldRegionKey.from(location);", source)
        self.assertNotIn("ConcurrentHashMap<WorldRegionKey", source)

    def test_only_approved_staged_sources_consume_the_contract(self):
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


if __name__ == "__main__":
    unittest.main()
