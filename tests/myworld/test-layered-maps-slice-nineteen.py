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


COVERAGE_FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

public final class LegacyPackedRegionCoverageFixture {
    public static void main(String[] args) {
        int oneKeyRows = 0;
        int twoKeyRows = 0;
        int emptyPaddingRows = 0;
        int levelStraddlingRows = 0;
        for (int packedRegionY = 0; packedRegionY < 84; packedRegionY++) {
            LegacyPackedRegionCoverage coverage =
                LegacyPackedRegionCoverage.fromPackedRegionCoordinates(0, packedRegionY);
            int keyCount = coverage.getCoveredKeys().size();
            if (keyCount == 0) {
                emptyPaddingRows++;
            } else if (keyCount == 1) {
                oneKeyRows++;
            } else if (keyCount == 2) {
                twoKeyRows++;
            } else {
                throw new AssertionError("Unexpected logical-key coverage count " + keyCount);
            }
            if (coverage.spansLevels()) {
                levelStraddlingRows++;
            }
        }
        check(oneKeyRows == 39, "single-key rows");
        check(twoKeyRows == 40, "dual-key rows");
        check(emptyPaddingRows == 5, "padding rows");
        check(levelStraddlingRows == 2, "level-straddling rows");

        for (int packedY = LegacyPackedPointAdapter.MIN_PACKED_Y;
                packedY <= LegacyPackedPointAdapter.MAX_PACKED_Y; packedY++) {
            int packedRegionY = Math.floorDiv(packedY, WorldRegionKey.REGION_SIZE);
            LegacyPackedRegionCoverage coverage =
                LegacyPackedRegionCoverage.fromPackedRegionCoordinates(20, packedRegionY);
            WorldRegionKey expected = WorldRegionKey.fromLegacyPoint(Point.location(1007, packedY));
            check(coverage.contains(expected), "tile key covered at Y " + packedY);
        }

        LegacyPackedRegionCoverage surface = coverage(0, 18);
        check(surface.getCoveredKeys().size() == 1 && !surface.spansLevels(),
            "aligned surface row");
        check(key(surface, 0, 0, 18), "surface key");

        LegacyPackedRegionCoverage firstBoundary = coverage(0, 19);
        check(firstBoundary.spansLevels(), "first level boundary");
        check(keyAt(firstBoundary, 0, 0, 19), "first boundary lower key");
        check(keyAt(firstBoundary, 1, 0, 0), "first boundary upper key");

        LegacyPackedRegionCoverage shiftedUpper = coverage(0, 20);
        check(!shiftedUpper.spansLevels(), "same upper level");
        check(keyAt(shiftedUpper, 1, 0, 0), "shifted upper first key");
        check(keyAt(shiftedUpper, 1, 0, 1), "shifted upper second key");

        LegacyPackedRegionCoverage secondBoundary = coverage(0, 39);
        check(secondBoundary.spansLevels(), "second level boundary");
        check(keyAt(secondBoundary, 1, 0, 19), "second boundary lower key");
        check(keyAt(secondBoundary, 2, 0, 0), "second boundary upper key");

        LegacyPackedRegionCoverage planeTwoTail = coverage(0, 58);
        check(!planeTwoTail.spansLevels(), "plane-two tail level");
        check(keyAt(planeTwoTail, 2, 0, 18), "plane-two tail first key");
        check(keyAt(planeTwoTail, 2, 0, 19), "plane-two tail second key");

        LegacyPackedRegionCoverage underground = coverage(0, 59);
        check(underground.getCoveredKeys().size() == 1 && key(underground, -1, 0, 0),
            "aligned underground row");

        LegacyPackedRegionCoverage partialY = coverage(0, 78);
        check(partialY.hasLegacyTiles() && !partialY.isFullyInsideLegacyDomain(),
            "partial terminal Y row");
        check(partialY.getLegacyTileCount() == 48L * 32L, "partial Y tile count");
        LegacyPackedRegionCoverage padding = coverage(0, 79);
        check(!padding.hasLegacyTiles() && padding.getLegacyTileCount() == 0L,
            "post-codec padding row");

        LegacyPackedRegionCoverage partialX = coverage(682, 0);
        check(partialX.hasLegacyTiles() && !partialX.isFullyInsideLegacyDomain(),
            "partial terminal X column");
        check(partialX.getLegacyTileCount() == 32L * 48L, "partial X tile count");
        check(!coverage(683, 0).hasLegacyTiles(), "post-codec X padding");
        expectUnsupported(() -> firstBoundary.getCoveredKeys().clear());
        expectNull(() -> firstBoundary.contains(null));
        expectIllegal(() -> coverage(-1, 0));
        expectIllegal(() -> coverage(0, -1));
        expectArithmetic(() -> coverage(Integer.MAX_VALUE, 0));
        check(firstBoundary.toString().contains("coveredKeys="), "coverage string");
    }

    private static LegacyPackedRegionCoverage coverage(int x, int y) {
        return LegacyPackedRegionCoverage.fromPackedRegionCoordinates(x, y);
    }

    private static boolean key(
            LegacyPackedRegionCoverage coverage, int level, int x, int y) {
        return coverage.getCoveredKeys().size() == 1 && keyAt(coverage, level, x, y);
    }

    private static boolean keyAt(
            LegacyPackedRegionCoverage coverage, int level, int x, int y) {
        return coverage.contains(new WorldRegionKey(WorldSpaceId.GLOBAL, level, x, y));
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

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable list.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceNineteenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-nineteen-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/LegacyPackedRegionCoverageFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(COVERAGE_FIXTURE, encoding="utf-8")

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

    def test_packed_region_coverage_is_complete_and_reports_alignment(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate.LegacyPackedRegionCoverageFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_projection_remains_read_only_and_non_authoritative(self):
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("public LegacyPackedRegionCoverage getLayeredRegionCoverage(", manager)
        self.assertIn("LegacyPackedRegionCoverage.fromPackedRegionCoordinates(", manager)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertIn("private List<Region> getVisibleRegionWindow", manager)
        self.assertNotIn("ConcurrentHashMap<WorldRegionKey", manager)
        self.assertNotIn("LegacyPackedRegionCoverage", player)
        self.assertIn("### Slice 19: Legacy packed-region coverage projection", plan)


if __name__ == "__main__":
    unittest.main()
