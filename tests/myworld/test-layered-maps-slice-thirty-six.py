#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
TILE_VALUE = ROOT / "server/src/com/openrsc/server/model/world/region/TileValue.java"
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

    public int getX() { return x; }
    public int getY() { return y; }
}
'''


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

public final class LayeredRegionResidencyMirrorFixture {
    public static void main(String[] args) {
        LayeredRegionResidencyMirror mirror = new LayeredRegionResidencyMirror();
        WorldRegionKey upper = key(1, 4, 0);

        LayeredRegionResidencyMirror.Snapshot absent = mirror.snapshot(upper);
        check(absent.getMirrorVersion() == 0L, "initial version");
        check(absent.getSourceCount() == 2, "upper source count");
        check(absent.getResidentSourceCount() == 0, "upper absent sources");
        check(absent.getLegacySupportedTileCount() == 2304L, "upper support");
        check(absent.isLegacyCoverageComplete(), "upper complete coverage");
        check(!absent.isResident(), "upper initially absent");

        check(mirror.registerPackedRegion(4, 19), "register first upper source");
        check(!mirror.registerPackedRegion(4, 19), "duplicate registration is no-op");
        LayeredRegionResidencyMirror.Snapshot partial = mirror.snapshot(upper);
        check(partial.getMirrorVersion() == 1L, "duplicate preserves version");
        check(partial.getResidentSourceCount() == 1, "upper partial source");
        check(partial.getResidentTileCount() == 48L * 16L, "upper partial tiles");
        check(partial.getMissingSourceCount() == 1, "upper missing source");
        check(!partial.isResident(), "upper partial not resident");

        check(mirror.registerPackedRegion(4, 20), "register second upper source");
        LayeredRegionResidencyMirror.Snapshot resident = mirror.snapshot(upper);
        check(resident.getMirrorVersion() == 2L, "upper resident version");
        check(resident.getResidentSourceCount() == 2, "upper resident sources");
        check(resident.getResidentTileCount() == 2304L, "upper resident tiles");
        check(resident.isResident(), "upper fully resident");
        check(resident.getSources().get(0).getPackedRegionY() == 19,
            "upper sources ordered");
        check(resident.getSources().get(1).getPackedRegionY() == 20,
            "upper second source ordered");
        expectUnsupported(() -> resident.getSources().clear());

        check(mirror.registerPackedRegion(-1, 0), "track packed-only negative cell");
        check(mirror.getPackedRegionCount() == 3, "packed-only count");
        check(mirror.getLogicalRegionCount() == 3, "negative has no logical claim");
        check(mirror.unregisterPackedRegion(-1, 0), "remove packed-only cell");
        check(!mirror.unregisterPackedRegion(-1, 0), "duplicate removal is no-op");

        check(mirror.unregisterPackedRegion(4, 19), "remove first upper source");
        LayeredRegionResidencyMirror.Snapshot afterRemoval = mirror.snapshot(upper);
        check(afterRemoval.getResidentSourceCount() == 1, "upper removal reflected");
        check(afterRemoval.getMirrorVersion() == 5L, "removal version");

        WorldRegionKey terminal = key(-1, 682, 19);
        check(mirror.registerPackedRegion(682, 78), "register terminal source");
        LayeredRegionResidencyMirror.Snapshot terminalSnapshot =
            mirror.snapshot(terminal);
        check(terminalSnapshot.getLegacySupportedTileCount() == 1024L,
            "terminal supported tiles");
        check(terminalSnapshot.getResidentTileCount() == 1024L,
            "terminal resident tiles");
        check(!terminalSnapshot.isLegacyCoverageComplete(),
            "terminal partial legacy coverage");
        check(terminalSnapshot.isResident(), "terminal supported source resident");

        LayeredRegionResidencyMirror.Snapshot deep = mirror.snapshot(key(-2, 4, 12));
        check(!deep.isLegacySupported(), "deep legacy unsupported");
        check(!deep.isResident(), "unsupported is not resident");
        check(deep.getSourceCount() == 0, "deep has no fabricated source");

        check(mirror.clear(), "clear populated mirror");
        check(!mirror.clear(), "clear empty mirror is no-op");
        check(mirror.getPackedRegionCount() == 0, "packed count cleared");
        check(mirror.getLogicalRegionCount() == 0, "logical count cleared");
        check(!mirror.snapshot(upper).isResident(), "upper absent after clear");
        expectNull(() -> mirror.snapshot(null));
        check(resident.toString().contains("residentSources=2/2"),
            "snapshot string");
    }

    private static WorldRegionKey key(int level, int x, int y) {
        return new WorldRegionKey(WorldSpaceId.GLOBAL, level, x, y);
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable view.
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


class LayeredMapsSliceThirtySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-thirty-six-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredRegionResidencyMirrorFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")

        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes), str(point),
                str(fixture),
                *(str(path) for path in sorted(SERVER_COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_residency_tracks_split_partial_unsupported_and_lifecycle_states(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredRegionResidencyMirrorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_mirror_is_checked_and_never_becomes_tile_authority(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        tile_value = TILE_VALUE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "private final LayeredRegionResidencyMirror layeredRegionResidencyMirror;",
            manager,
        )
        self.assertIn(
            "layeredRegionResidencyMirror.registerPackedRegion(regionX, regionY);",
            manager,
        )
        self.assertIn("layeredRegionResidencyMirror.clear();", manager)
        self.assertIn("getLayeredRegionResidencySnapshot", manager)
        self.assertIn("packedResident != source.isResident()", manager)
        self.assertIn(
            "return getRegion(x, y).getTileValue(",
            manager,
        )
        self.assertIn(
            "return getRegion(x, y).getMutableTileValue(",
            manager,
        )
        self.assertNotIn("LayeredRegionResidencyMirror", path_validation)
        self.assertNotIn("LayeredRegionResidencyMirror", tile_value)
        self.assertIn(
            "### Slice 36: Checked logical Region residency mirror",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
