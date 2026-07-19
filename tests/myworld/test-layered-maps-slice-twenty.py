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


COMPARISON_FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

public final class LegacyPackedVisibilityCoverageComparisonFixture {
    public static void main(String[] args) {
        LegacyPackedVisibilityCoverageComparison surface = compare(223, 620, 16, 64, 128);
        check(surface.isExactCoverage(), "surface exact");
        check(surface.getPackedCellCount() == 42L, "surface packed cells");
        check(surface.getExpectedLogicalKeys().size() == 42, "surface expected");
        check(surface.getPackedCoverageKeys().size() == 42, "surface coverage");
        check(surface.getUnsupportedPackedCellCount() == 0, "surface supported");
        check(surface.getMinPackedRegionX() == 1 && surface.getMaxPackedRegionX() == 7,
            "surface packed X bounds");
        check(surface.getMinPackedRegionY() == 10 && surface.getMaxPackedRegionY() == 15,
            "surface packed Y bounds");

        LegacyPackedVisibilityCoverageComparison underground =
            compare(216, 3300, 16, 64, 128);
        check(underground.isExactCoverage(), "underground exact");
        check(underground.getExpectedLogicalKeys().get(0).getLevel() == -1,
            "underground level");

        LegacyPackedVisibilityCoverageComparison upper = compare(223, 1564, 16, 64, 128);
        check(!upper.isExactCoverage(), "upper reports coarse coverage");
        check(upper.getMissingLogicalKeys().isEmpty(), "upper has no missing keys");
        check(upper.getExpectedLogicalKeys().size() == 42, "upper expected count");
        check(upper.getPackedCoverageKeys().size() == 56, "upper coverage count");
        check(upper.getExtraPackedCoverageKeys().size() == 14, "upper extra count");
        check(keyAt(upper.getExtraPackedCoverageKeys(), 1, 1, 9),
            "upper leading extra");
        check(keyAt(upper.getExtraPackedCoverageKeys(), 1, 7, 16),
            "upper trailing extra");

        LegacyPackedVisibilityCoverageComparison levelEdge =
            compare(223, 944, 16, 64, 128);
        check(!levelEdge.isExactCoverage(), "level edge reports packed boundary");
        check(levelEdge.getExpectedLogicalKeys().size() == 42,
            "level edge expected count");
        check(levelEdge.getPackedCoverageKeys().size() == 49,
            "level edge coverage count");
        check(levelEdge.getMissingLogicalKeys().size() == 21,
            "level edge signed missing count");
        check(levelEdge.getExtraPackedCoverageKeys().size() == 28,
            "level edge packed extra count");
        check(keyAt(levelEdge.getMissingLogicalKeys(), 1, 1, -3),
            "level edge preserves negative local Y");
        check(keyAt(levelEdge.getExtraPackedCoverageKeys(), 0, 1, 17),
            "level edge exposes prior-level candidate");
        check(keyAt(levelEdge.getExtraPackedCoverageKeys(), 1, 7, 3),
            "level edge exposes coarse trailing key");

        LegacyPackedVisibilityCoverageComparison edge = compare(0, 0, 16, 64, 128);
        check(!edge.isExactCoverage(), "legacy edge not exact");
        check(edge.getPackedCellCount() == 36L, "edge packed cells");
        check(edge.getUnsupportedPackedCellCount() == 27, "edge unsupported cells");
        check(edge.getExpectedLogicalKeys().size() == 36, "edge expected");
        check(edge.getPackedCoverageKeys().size() == 9, "edge supported coverage");
        check(edge.getMissingLogicalKeys().size() == 27, "edge missing signed keys");
        check(edge.getExtraPackedCoverageKeys().isEmpty(), "edge no extras");
        check(keyAt(edge.getMissingLogicalKeys(), 0, -3, -3), "edge signed missing key");

        check(WorldRegionInterestDelta.materializeKeys(
            surface.getLogicalWindow(), 42).size() == 42, "public materialization");
        expectUnsupported(() -> surface.getExpectedLogicalKeys().clear());
        expectUnsupported(() -> surface.getPackedCoverageKeys().clear());
        expectIllegal(() -> compare(223, 620, -1, 64, 128));
        expectIllegal(() -> compare(223, 620, 16, 41, 128));
        expectIllegal(() -> compare(223, 620, 16, 64, 41));
        expectIllegal(() -> compare(223, 620, 16, 0, 128));
        expectIllegal(() -> compare(223, 620, 16, 64, 0));
        expectArithmetic(() -> compare(223, 620, Integer.MAX_VALUE, 64, 128));
        expectNull(() -> LegacyPackedVisibilityCoverageComparison.compare(
            null, 16, 64, 128));
        check(upper.toString().contains("extraKeys=14"), "comparison string");
    }

    private static LegacyPackedVisibilityCoverageComparison compare(
            int x, int y, int distance, int cells, int keys) {
        return LegacyPackedVisibilityCoverageComparison.compare(
            Point.location(x, y), distance, cells, keys);
    }

    private static boolean keyAt(
            Iterable<WorldRegionKey> keys, int level, int x, int y) {
        WorldRegionKey expected = new WorldRegionKey(WorldSpaceId.GLOBAL, level, x, y);
        for (WorldRegionKey key : keys) {
            if (key.equals(expected)) {
                return true;
            }
        }
        return false;
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


class LayeredMapsSliceTwentyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-twenty-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LegacyPackedVisibilityCoverageComparisonFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(COMPARISON_FIXTURE, encoding="utf-8")

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

    def test_window_comparison_reports_exact_extra_missing_and_padding(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LegacyPackedVisibilityCoverageComparisonFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_comparison_is_dormant_and_non_authoritative(self):
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "public LegacyPackedVisibilityCoverageComparison "
            "compareLayeredVisibleRegionCoverage(",
            manager,
        )
        self.assertIn("LegacyPackedVisibilityCoverageComparison.compare(", manager)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertIn("visibleRegionWindowCache.putIfAbsent", manager)
        self.assertNotIn("LegacyPackedVisibilityCoverageComparison", player)
        self.assertNotIn("compareLayeredVisibleRegionCoverage(player", manager)
        self.assertIn("### Slice 20: Packed/logical window coverage comparison", plan)


if __name__ == "__main__":
    unittest.main()
