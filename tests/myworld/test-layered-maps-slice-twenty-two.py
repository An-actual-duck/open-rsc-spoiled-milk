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


PARTITION_FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import java.util.List;

public final class LegacyPackedRegionPartitionFixture {
    public static void main(String[] args) {
        LegacyPackedRegionPartition surface = partition(4, 12);
        check(!surface.isEmpty(), "surface populated");
        check(!surface.requiresSplit(), "surface aligned");
        check(surface.getPartitionedTileCount() == 2304L, "surface tile count");
        check(surface.getFragments().size() == 1, "surface fragment count");
        LegacyPackedRegionPartition.Fragment surfaceFragment = surface.getFragments().get(0);
        check(key(surfaceFragment, 0, 4, 12), "surface key");
        check(surfaceFragment.getMinPackedTileX() == 192
            && surfaceFragment.getMaxPackedTileX() == 239, "surface packed X");
        check(surfaceFragment.getMinPackedTileY() == 576
            && surfaceFragment.getMaxPackedTileY() == 623, "surface packed Y");
        check(surfaceFragment.getMinPackedLocalX() == 0
            && surfaceFragment.getMaxPackedLocalX() == 47
            && surfaceFragment.getMinPackedLocalY() == 0
            && surfaceFragment.getMaxPackedLocalY() == 47, "surface packed local");
        check(surfaceFragment.getLogicalBounds().getMinY() == 576
            && surfaceFragment.getLogicalBounds().getMaxY() == 623,
            "surface logical bounds");
        check(surfaceFragment.containsPackedTile(192, 576), "surface packed contains");
        check(!surfaceFragment.containsPackedTile(191, 576), "surface packed excludes");
        check(surfaceFragment.containsLogicalLocation(WorldLocation.global(
            new WorldCoordinate(223, 620, 0))), "surface logical contains");
        check(!surfaceFragment.containsLogicalLocation(WorldLocation.global(
            new WorldCoordinate(223, 620, 1))), "surface logical level isolation");

        LegacyPackedRegionPartition levelStraddle = partition(4, 19);
        check(levelStraddle.requiresSplit(), "level straddle split");
        check(levelStraddle.getFragments().size() == 2, "level straddle fragments");
        check(levelStraddle.getPartitionedTileCount() == 2304L,
            "level straddle tile count");
        LegacyPackedRegionPartition.Fragment lower = levelStraddle.getFragments().get(0);
        LegacyPackedRegionPartition.Fragment upper = levelStraddle.getFragments().get(1);
        check(key(lower, 0, 4, 19), "level straddle lower key");
        check(lower.getMinPackedTileY() == 912 && lower.getMaxPackedTileY() == 943,
            "level straddle lower packed");
        check(lower.getMinPackedLocalY() == 0 && lower.getMaxPackedLocalY() == 31,
            "level straddle lower local");
        check(lower.getLogicalBounds().getMinY() == 912
            && lower.getLogicalBounds().getMaxY() == 943
            && lower.getTileCount() == 1536L, "level straddle lower logical");
        check(key(upper, 1, 4, 0), "level straddle upper key");
        check(upper.getMinPackedTileY() == 944 && upper.getMaxPackedTileY() == 959,
            "level straddle upper packed");
        check(upper.getMinPackedLocalY() == 32 && upper.getMaxPackedLocalY() == 47,
            "level straddle upper local");
        check(upper.getLogicalBounds().getMinY() == 0
            && upper.getLogicalBounds().getMaxY() == 15
            && upper.getTileCount() == 768L, "level straddle upper logical");

        LegacyPackedRegionPartition upperMisaligned = partition(4, 20);
        check(upperMisaligned.requiresSplit(), "upper misalignment split");
        check(upperMisaligned.getFragments().size() == 2,
            "upper misalignment fragments");
        check(key(upperMisaligned.getFragments().get(0), 1, 4, 0),
            "upper misalignment first key");
        check(upperMisaligned.getFragments().get(0).getLogicalBounds().getMinY() == 16
            && upperMisaligned.getFragments().get(0).getLogicalBounds().getMaxY() == 47,
            "upper misalignment first logical");
        check(key(upperMisaligned.getFragments().get(1), 1, 4, 1),
            "upper misalignment second key");
        check(upperMisaligned.getFragments().get(1).getLogicalBounds().getMinY() == 48
            && upperMisaligned.getFragments().get(1).getLogicalBounds().getMaxY() == 63,
            "upper misalignment second logical");

        LegacyPackedRegionPartition terminal = partition(682, 78);
        check(!terminal.requiresSplit(), "terminal one key");
        check(terminal.getPartitionedTileCount() == 1024L, "terminal partial count");
        LegacyPackedRegionPartition.Fragment terminalFragment = terminal.getFragments().get(0);
        check(key(terminalFragment, -1, 682, 19), "terminal key");
        check(terminalFragment.getMinPackedLocalX() == 0
            && terminalFragment.getMaxPackedLocalX() == 31
            && terminalFragment.getMinPackedLocalY() == 0
            && terminalFragment.getMaxPackedLocalY() == 31, "terminal local bounds");
        check(terminalFragment.getLogicalBounds().getMinX() == 32736
            && terminalFragment.getLogicalBounds().getMaxX() == 32767
            && terminalFragment.getLogicalBounds().getMinY() == 912
            && terminalFragment.getLogicalBounds().getMaxY() == 943,
            "terminal logical bounds");

        LegacyPackedRegionPartition padded = partition(4, 79);
        check(padded.isEmpty(), "padded empty");
        check(!padded.requiresSplit(), "padded no split");
        check(padded.getPartitionedTileCount() == 0L, "padded tile count");
        check(padded.getFragments().isEmpty(), "padded fragments");

        for (int packedY = 0; packedY < 84; packedY++) {
            LegacyPackedRegionPartition candidate = partition(4, packedY);
            LegacyPackedRegionCoverage coverage = candidate.getCoverage();
            check(candidate.getPartitionedTileCount() == coverage.getLegacyTileCount(),
                "exhaustive tile count " + packedY);
            check(candidate.getFragments().size() == coverage.getCoveredKeys().size(),
                "exhaustive key count " + packedY);
            for (int index = 0; index < candidate.getFragments().size(); index++) {
                check(candidate.getFragments().get(index).getLogicalRegionKey().equals(
                    coverage.getCoveredKeys().get(index)),
                    "exhaustive key order " + packedY + ':' + index);
            }
        }

        expectUnsupported(() -> surface.getFragments().clear());
        expectNull(() -> surfaceFragment.containsLogicalLocation(null));
        expectIllegal(() -> partition(-1, 0));
        expectIllegal(() -> partition(0, -1));
        expectArithmetic(() -> partition(Integer.MAX_VALUE, 0));
        check(levelStraddle.toString().contains("fragments="), "partition string");
        check(upper.toString().contains("tileCount=768"), "fragment string");
    }

    private static LegacyPackedRegionPartition partition(int x, int y) {
        return LegacyPackedRegionPartition.fromPackedRegionCoordinates(x, y);
    }

    private static boolean key(
            LegacyPackedRegionPartition.Fragment fragment,
            int level,
            int regionX,
            int regionY) {
        return fragment.getLogicalRegionKey().equals(
            new WorldRegionKey(WorldSpaceId.GLOBAL, level, regionX, regionY));
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


class LayeredMapsSliceTwentyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-twenty-two-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LegacyPackedRegionPartitionFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(PARTITION_FIXTURE, encoding="utf-8")

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

    def test_partition_is_exact_ordered_lossless_and_immutable(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LegacyPackedRegionPartitionFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_partition_is_dormant_and_non_authoritative(self):
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "public LegacyPackedRegionPartition getLayeredRegionPartition(",
            manager,
        )
        self.assertIn("LegacyPackedRegionPartition.fromPackedRegionCoordinates(", manager)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertIn("visibleRegionWindowCache.putIfAbsent", manager)
        self.assertNotIn("LegacyPackedRegionPartition", player)
        self.assertNotIn("getLayeredRegionPartition(player", manager)
        self.assertIn("### Slice 22: Exact packed-cell tile partitions", plan)


if __name__ == "__main__":
    unittest.main()
