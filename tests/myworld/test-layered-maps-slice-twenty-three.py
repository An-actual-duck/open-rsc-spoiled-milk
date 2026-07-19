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


ASSEMBLY_FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

public final class LegacyLogicalRegionAssemblyFixture {
    public static void main(String[] args) {
        LegacyLogicalRegionAssembly surface = assembly(WorldSpaceId.GLOBAL, 0, 4, 12);
        check(surface.isComplete(), "surface complete");
        check(!surface.isPartial() && !surface.isUnsupported(), "surface status");
        check(surface.getTargetTileCount() == 2304L
            && surface.getAssembledTileCount() == 2304L, "surface counts");
        check(surface.getTargetBounds().equals(surface.getLegacySupportedBounds()),
            "surface support bounds");
        check(surface.getSourceFragments().size() == 1, "surface sources");
        check(source(surface, 0, 4, 12, 576, 623), "surface source");

        LegacyLogicalRegionAssembly upper = assembly(WorldSpaceId.GLOBAL, 1, 4, 0);
        check(upper.isComplete(), "upper complete");
        check(upper.getSourceFragments().size() == 2, "upper sources");
        check(source(upper, 0, 4, 19, 0, 15), "upper first source");
        check(source(upper, 1, 4, 20, 16, 47), "upper second source");
        check(upper.getSourceFragments().get(0).getFragment().getTileCount() == 768L,
            "upper first count");
        check(upper.getSourceFragments().get(1).getFragment().getTileCount() == 1536L,
            "upper second count");

        LegacyLogicalRegionAssembly secondUpper = assembly(WorldSpaceId.GLOBAL, 2, 4, 0);
        check(secondUpper.isComplete(), "second upper complete");
        check(secondUpper.getSourceFragments().size() == 2, "second upper sources");
        check(source(secondUpper, 0, 4, 39, 0, 31), "second upper first source");
        check(source(secondUpper, 1, 4, 40, 32, 47), "second upper second source");

        LegacyLogicalRegionAssembly underground =
            assembly(WorldSpaceId.GLOBAL, -1, 4, 9);
        check(underground.isComplete(), "underground complete");
        check(underground.getSourceFragments().size() == 1, "underground sources");
        check(source(underground, 0, 4, 68, 432, 479), "underground source");

        LegacyLogicalRegionAssembly terminal =
            assembly(WorldSpaceId.GLOBAL, -1, 682, 19);
        check(!terminal.isComplete() && terminal.isPartial(), "terminal partial");
        check(!terminal.isUnsupported(), "terminal supported");
        check(terminal.getAssembledTileCount() == 1024L, "terminal count");
        check(terminal.getTargetBounds().getMinX() == 32736
            && terminal.getTargetBounds().getMaxX() == 32783
            && terminal.getTargetBounds().getMinY() == 912
            && terminal.getTargetBounds().getMaxY() == 959,
            "terminal target");
        check(terminal.getLegacySupportedBounds().getMinX() == 32736
            && terminal.getLegacySupportedBounds().getMaxX() == 32767
            && terminal.getLegacySupportedBounds().getMinY() == 912
            && terminal.getLegacySupportedBounds().getMaxY() == 943,
            "terminal support");
        check(source(terminal, 0, 682, 78, 912, 943), "terminal source");

        LegacyLogicalRegionAssembly negativeX =
            assembly(WorldSpaceId.GLOBAL, 0, -1, 12);
        check(negativeX.isUnsupported(), "negative X unsupported");
        check(negativeX.getLegacySupportedBounds() == null, "negative X no support");
        check(negativeX.getSourceFragments().isEmpty(), "negative X no sources");
        LegacyLogicalRegionAssembly negativeY =
            assembly(WorldSpaceId.GLOBAL, 0, 4, -1);
        check(negativeY.isUnsupported(), "negative Y unsupported");
        LegacyLogicalRegionAssembly deep =
            assembly(WorldSpaceId.GLOBAL, -2, 4, 12);
        check(deep.isUnsupported(), "deep unsupported");
        LegacyLogicalRegionAssembly instance =
            assembly(new WorldSpaceId("instance.test"), 0, 4, 12);
        check(instance.isUnsupported(), "instance unsupported");

        int[] levels = {0, 1, 2, -1};
        for (int level : levels) {
            for (int regionY = 0; regionY <= 19; regionY++) {
                LegacyLogicalRegionAssembly candidate =
                    assembly(WorldSpaceId.GLOBAL, level, 4, regionY);
                long expected = regionY == 19 ? 1536L : 2304L;
                check(candidate.getAssembledTileCount() == expected,
                    "level/Y count " + level + ':' + regionY);
                check(candidate.isComplete() == (regionY < 19),
                    "level/Y complete " + level + ':' + regionY);
                check(candidate.getSourceFragments().size() >= 1
                    && candidate.getSourceFragments().size() <= 2,
                    "level/Y source count " + level + ':' + regionY);
            }
        }
        for (int regionX = 0; regionX <= 682; regionX++) {
            LegacyLogicalRegionAssembly candidate =
                assembly(WorldSpaceId.GLOBAL, 0, regionX, 12);
            long expected = regionX == 682 ? 1536L : 2304L;
            check(candidate.getAssembledTileCount() == expected,
                "X count " + regionX);
            check(candidate.isComplete() == (regionX < 682),
                "X complete " + regionX);
        }

        expectUnsupported(() -> surface.getSourceFragments().clear());
        expectNull(() -> LegacyLogicalRegionAssembly.fromLogicalRegionKey(null));
        expectArithmetic(() -> assembly(
            WorldSpaceId.GLOBAL, 0, Integer.MAX_VALUE, 0));
        check(terminal.toString().contains("assembledTileCount=1024"),
            "assembly string");
        check(upper.getSourceFragments().get(0).toString().contains("packedRegionY=19"),
            "source string");
    }

    private static LegacyLogicalRegionAssembly assembly(
            WorldSpaceId worldSpace, int level, int regionX, int regionY) {
        return LegacyLogicalRegionAssembly.fromLogicalRegionKey(
            new WorldRegionKey(worldSpace, level, regionX, regionY));
    }

    private static boolean source(
            LegacyLogicalRegionAssembly assembly,
            int index,
            int packedRegionX,
            int packedRegionY,
            int logicalMinY,
            int logicalMaxY) {
        LegacyLogicalRegionAssembly.SourceFragment source =
            assembly.getSourceFragments().get(index);
        WorldTileBounds bounds = source.getFragment().getLogicalBounds();
        return source.getPackedRegionX() == packedRegionX
            && source.getPackedRegionY() == packedRegionY
            && bounds.getMinY() == logicalMinY
            && bounds.getMaxY() == logicalMaxY;
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


class LayeredMapsSliceTwentyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-twenty-three-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LegacyLogicalRegionAssemblyFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(ASSEMBLY_FIXTURE, encoding="utf-8")

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

    def test_assembly_is_exact_complete_partial_unsupported_and_immutable(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LegacyLogicalRegionAssemblyFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_assembly_is_dormant_and_non_authoritative(self):
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "public LegacyLogicalRegionAssembly getLegacyLogicalRegionAssembly(",
            manager,
        )
        self.assertIn("LegacyLogicalRegionAssembly.fromLogicalRegionKey(logicalRegionKey)", manager)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertIn("visibleRegionWindowCache.putIfAbsent", manager)
        self.assertNotIn("LegacyLogicalRegionAssembly", player)
        self.assertNotIn("getLegacyLogicalRegionAssembly(player", manager)
        self.assertIn("### Slice 23: Logical-region legacy assembly plans", plan)


if __name__ == "__main__":
    unittest.main()
