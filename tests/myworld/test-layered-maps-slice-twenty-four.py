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
        if (x < 0 || y < 0 || x > Short.MAX_VALUE || y > Short.MAX_VALUE) {
            throw new IllegalArgumentException("packed point out of range");
        }
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


ADDRESS_FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

public final class LegacyLogicalTileAddressFixture {
    public static void main(String[] args) {
        WorldRegionKey surfaceKey = key(WorldSpaceId.GLOBAL, 0, 4, 12);
        LegacyLogicalTileAddress surface = address(surfaceKey, 31, 44);
        check(surface.isLegacyRepresentable(), "surface representable");
        check(surface.getLogicalRegionKey().equals(surfaceKey), "surface key");
        check(surface.getLogicalLocalX() == 31 && surface.getLogicalLocalY() == 44,
            "surface logical local");
        check(location(surface, 223, 620, 0), "surface logical location");
        check(surface.getLegacyPoint().getX() == 223
            && surface.getLegacyPoint().getY() == 620, "surface packed point");
        check(packed(surface, 4, 12, 31, 44), "surface packed address");
        check(surface.getSourceFragment().getPackedRegionX() == 4
            && surface.getSourceFragment().getPackedRegionY() == 12,
            "surface source");

        WorldRegionKey upperKey = key(WorldSpaceId.GLOBAL, 1, 4, 12);
        LegacyLogicalTileAddress upper = address(upperKey, 31, 44);
        check(location(upper, 223, 620, 1), "upper logical location");
        check(upper.getLegacyPoint().getX() == 223
            && upper.getLegacyPoint().getY() == 1564, "upper packed point");
        check(packed(upper, 4, 32, 31, 28), "upper packed address");

        WorldRegionKey boundaryKey = key(WorldSpaceId.GLOBAL, 1, 4, 0);
        LegacyLogicalTileAddress boundaryFirst = address(boundaryKey, 0, 0);
        LegacyLogicalTileAddress boundarySecond = address(boundaryKey, 0, 16);
        check(packed(boundaryFirst, 4, 19, 0, 32), "boundary first source");
        check(packed(boundarySecond, 4, 20, 0, 0), "boundary second source");

        int firstSourceTiles = 0;
        int secondSourceTiles = 0;
        for (int localX = 0; localX < 48; localX++) {
            for (int localY = 0; localY < 48; localY++) {
                LegacyLogicalTileAddress candidate =
                    address(boundaryKey, localX, localY);
                check(candidate.isLegacyRepresentable(),
                    "boundary exhaustive representable");
                check(LegacyPackedPointAdapter.fromLegacyPoint(
                    candidate.getLegacyPoint()).equals(candidate.getLogicalLocation()),
                    "boundary exhaustive round trip");
                if (candidate.getPackedRegionY() == 19) {
                    firstSourceTiles++;
                } else if (candidate.getPackedRegionY() == 20) {
                    secondSourceTiles++;
                } else {
                    throw new AssertionError("Unexpected boundary source");
                }
            }
        }
        check(firstSourceTiles == 768 && secondSourceTiles == 1536,
            "boundary source distribution");

        WorldRegionKey terminalKey = key(WorldSpaceId.GLOBAL, -1, 682, 19);
        int terminalSupported = 0;
        int terminalUnsupported = 0;
        for (int localX = 0; localX < 48; localX++) {
            for (int localY = 0; localY < 48; localY++) {
                LegacyLogicalTileAddress candidate =
                    address(terminalKey, localX, localY);
                if (localX < 32 && localY < 32) {
                    check(candidate.isLegacyRepresentable(), "terminal supported");
                    terminalSupported++;
                } else {
                    check(!candidate.isLegacyRepresentable(), "terminal unsupported");
                    check(candidate.getLegacyPoint() == null
                        && candidate.getSourceFragment() == null,
                        "terminal unsupported null source");
                    terminalUnsupported++;
                }
            }
        }
        check(terminalSupported == 1024 && terminalUnsupported == 1280,
            "terminal distribution");
        LegacyLogicalTileAddress terminalCorner = address(terminalKey, 31, 31);
        check(location(terminalCorner, 32767, 943, -1), "terminal logical corner");
        check(terminalCorner.getLegacyPoint().getX() == 32767
            && terminalCorner.getLegacyPoint().getY() == 3775,
            "terminal packed corner");
        check(packed(terminalCorner, 682, 78, 31, 31),
            "terminal packed address");
        LegacyLogicalTileAddress beyondTerminal = address(terminalKey, 32, 31);
        expectState(() -> beyondTerminal.getPackedRegionX());
        expectState(() -> beyondTerminal.getPackedLocalY());

        LegacyLogicalTileAddress negative =
            address(key(WorldSpaceId.GLOBAL, 0, -1, 12), 47, 47);
        check(!negative.isLegacyRepresentable(), "negative unsupported");
        check(location(negative, -1, 623, 0), "negative location preserved");
        LegacyLogicalTileAddress deep =
            address(key(WorldSpaceId.GLOBAL, -2, 4, 12), 31, 44);
        check(!deep.isLegacyRepresentable(), "deep unsupported");
        LegacyLogicalTileAddress instance =
            address(key(new WorldSpaceId("instance.test"), 0, 4, 12), 31, 44);
        check(!instance.isLegacyRepresentable(), "instance unsupported");

        expectIllegal(() -> address(surfaceKey, -1, 0));
        expectIllegal(() -> address(surfaceKey, 48, 0));
        expectIllegal(() -> address(surfaceKey, 0, -1));
        expectIllegal(() -> address(surfaceKey, 0, 48));
        expectNull(() -> LegacyLogicalTileAddress.resolve(null, 0, 0));
        check(surface.toString().contains("packedRegion=(4,12)"),
            "represented string");
        check(deep.toString().contains("legacyRepresentable=false"),
            "unsupported string");
    }

    private static WorldRegionKey key(
            WorldSpaceId worldSpace, int level, int regionX, int regionY) {
        return new WorldRegionKey(worldSpace, level, regionX, regionY);
    }

    private static LegacyLogicalTileAddress address(
            WorldRegionKey key, int localX, int localY) {
        return LegacyLogicalTileAddress.resolve(key, localX, localY);
    }

    private static boolean location(
            LegacyLogicalTileAddress address, int x, int y, int level) {
        WorldCoordinate coordinate = address.getLogicalLocation().getCoordinate();
        return coordinate.getX() == x && coordinate.getY() == y
            && coordinate.getLevel() == level;
    }

    private static boolean packed(
            LegacyLogicalTileAddress address,
            int regionX,
            int regionY,
            int localX,
            int localY) {
        return address.getPackedRegionX() == regionX
            && address.getPackedRegionY() == regionY
            && address.getPackedLocalX() == localX
            && address.getPackedLocalY() == localY;
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


class LayeredMapsSliceTwentyFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-twenty-four-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LegacyLogicalTileAddressFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(ADDRESS_FIXTURE, encoding="utf-8")

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

    def test_address_is_exact_checked_and_preserves_unsupported_locations(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LegacyLogicalTileAddressFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_address_is_dormant_and_does_not_read_tiles(self):
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "public LegacyLogicalTileAddress getLegacyLogicalTileAddress(",
            manager,
        )
        self.assertIn("LegacyLogicalTileAddress.resolve(", manager)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertIn("visibleRegionWindowCache.putIfAbsent", manager)
        self.assertNotIn("LegacyLogicalTileAddress", player)
        self.assertNotIn("getLegacyLogicalTileAddress(player", manager)
        self.assertIn("### Slice 24: Logical-tile packed-source addressing", plan)


if __name__ == "__main__":
    unittest.main()
