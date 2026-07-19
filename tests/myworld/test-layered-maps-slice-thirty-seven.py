#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LayeredRegionInterestResidencyComparisonFixture {
    public static void main(String[] args) {
        LayeredRegionResidencyMirror mirror = new LayeredRegionResidencyMirror();
        registerUpper(mirror, 4, true);
        registerUpper(mirror, 5, true);
        registerUpper(mirror, 6, false);

        WorldRegionWindow previous = window(1, 4, 0, 5, 0);
        WorldRegionWindow current = window(1, 5, 0, 6, 0);
        WorldRegionInterestDelta delta = WorldRegionInterestDelta.between(
            previous, current, 2);
        long before = mirror.getVersion();
        LayeredRegionInterestResidencyComparison comparison = compare(mirror, delta);
        check(mirror.getVersion() == before, "comparison does not mutate mirror");
        check(comparison.getMirrorVersion() == before, "comparison version");
        check(comparison.getEntries().size() == 3, "entry count");
        check(entry(comparison.getEntries().get(0), 6,
            LayeredRegionInterestResidencyComparison.InterestState.ENTERED,
            LayeredRegionInterestResidencyComparison.ResidencyState.PARTIAL),
            "entered partial");
        check(entry(comparison.getEntries().get(1), 5,
            LayeredRegionInterestResidencyComparison.InterestState.RETAINED,
            LayeredRegionInterestResidencyComparison.ResidencyState.RESIDENT),
            "retained resident");
        check(entry(comparison.getEntries().get(2), 4,
            LayeredRegionInterestResidencyComparison.InterestState.EXITED,
            LayeredRegionInterestResidencyComparison.ResidencyState.RESIDENT),
            "exited resident");
        check(comparison.getLoadCandidates().size() == 1,
            "partial current load candidate");
        check(comparison.getReleaseCandidates().size() == 1,
            "resident exit release candidate");
        check(comparison.getUnsupportedCurrent().isEmpty(),
            "no unsupported current");
        check(comparison.getResidentCurrentCount() == 1, "resident current count");
        check(comparison.getPartialCurrentCount() == 1, "partial current count");
        check(comparison.getMissingCurrentCount() == 0, "missing current count");
        expectUnsupported(() -> comparison.getEntries().clear());
        expectUnsupported(() -> comparison.getLoadCandidates().clear());

        check(mirror.unregisterPackedRegion(6, 20), "remove partial source");
        LayeredRegionInterestResidencyComparison missing = compare(mirror, delta);
        check(missing.getMissingCurrentCount() == 1, "missing current classified");
        check(missing.getPartialCurrentCount() == 0, "partial removed");
        check(missing.getLoadCandidates().get(0).getResidencyState()
            == LayeredRegionInterestResidencyComparison.ResidencyState.MISSING,
            "missing load candidate");

        WorldRegionWindow retainedUpper = window(1, 5, 0, 5, 0);
        WorldRegionWindow deep = window(-2, 5, 0, 5, 0);
        LayeredRegionInterestResidencyComparison unsupported = compare(
            mirror, WorldRegionInterestDelta.between(retainedUpper, deep, 1));
        check(unsupported.getUnsupportedCurrent().size() == 1,
            "deep unsupported current");
        check(unsupported.getLoadCandidates().isEmpty(),
            "unsupported is not a legacy load candidate");
        check(unsupported.getReleaseCandidates().size() == 1,
            "level exit is release evidence");

        List<LayeredRegionResidencyMirror.Snapshot> wrongOrder =
            snapshots(mirror, delta);
        Collections.swap(wrongOrder, 0, 1);
        expectIllegal(() -> LayeredRegionInterestResidencyComparison.compare(
            delta, wrongOrder));
        List<LayeredRegionResidencyMirror.Snapshot> tooShort =
            snapshots(mirror, delta);
        tooShort.remove(tooShort.size() - 1);
        expectIllegal(() -> LayeredRegionInterestResidencyComparison.compare(
            delta, tooShort));

        List<LayeredRegionResidencyMirror.Snapshot> mixedVersions =
            new ArrayList<LayeredRegionResidencyMirror.Snapshot>();
        mixedVersions.add(mirror.snapshot(delta.getEntered().get(0)));
        check(mirror.registerPackedRegion(6, 20), "restore source for version test");
        mixedVersions.add(mirror.snapshot(delta.getRetained().get(0)));
        mixedVersions.add(mirror.snapshot(delta.getExited().get(0)));
        expectIllegal(() -> LayeredRegionInterestResidencyComparison.compare(
            delta, mixedVersions));
        expectNull(() -> LayeredRegionInterestResidencyComparison.compare(
            null, snapshots(mirror, delta)));
        expectNull(() -> LayeredRegionInterestResidencyComparison.compare(delta, null));
        check(comparison.toString().contains("loadCandidates=1"),
            "comparison string");
    }

    private static LayeredRegionInterestResidencyComparison compare(
            LayeredRegionResidencyMirror mirror,
            WorldRegionInterestDelta delta) {
        return LayeredRegionInterestResidencyComparison.compare(
            delta, snapshots(mirror, delta));
    }

    private static List<LayeredRegionResidencyMirror.Snapshot> snapshots(
            LayeredRegionResidencyMirror mirror,
            WorldRegionInterestDelta delta) {
        List<LayeredRegionResidencyMirror.Snapshot> snapshots = new ArrayList<>();
        append(mirror, snapshots, delta.getEntered());
        append(mirror, snapshots, delta.getRetained());
        append(mirror, snapshots, delta.getExited());
        return snapshots;
    }

    private static void append(
            LayeredRegionResidencyMirror mirror,
            List<LayeredRegionResidencyMirror.Snapshot> snapshots,
            List<WorldRegionKey> keys) {
        for (WorldRegionKey key : keys) {
            snapshots.add(mirror.snapshot(key));
        }
    }

    private static void registerUpper(
            LayeredRegionResidencyMirror mirror, int regionX, boolean complete) {
        check(mirror.registerPackedRegion(regionX, 20), "register upper body");
        if (complete) {
            check(mirror.registerPackedRegion(regionX, 19), "register upper seam");
        }
    }

    private static WorldRegionWindow window(
            int level, int minX, int minY, int maxX, int maxY) {
        return new WorldRegionWindow(
            WorldSpaceId.GLOBAL, level, minX, minY, maxX, maxY);
    }

    private static boolean entry(
            LayeredRegionInterestResidencyComparison.Entry entry,
            int regionX,
            LayeredRegionInterestResidencyComparison.InterestState interest,
            LayeredRegionInterestResidencyComparison.ResidencyState residency) {
        return entry.getLogicalRegionKey().getRegionX() == regionX
            && entry.getLogicalRegionKey().getRegionY() == 0
            && entry.getInterestState() == interest
            && entry.getResidencyState() == residency;
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
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


class LayeredMapsSliceThirtySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-thirty-seven-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredRegionInterestResidencyComparisonFixture.java"
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

    def test_interest_residency_comparison_is_versioned_and_deterministic(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredRegionInterestResidencyComparisonFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_projection_cannot_load_release_or_change_path_authority(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("compareLayeredRegionInterestResidency(", manager)
        block = manager.split("compareLayeredRegionInterestResidency(", 1)[1].split(
            "private void appendLayeredRegionResidencySnapshots", 1
        )[0]
        self.assertIn("WorldRegionInterestDelta.between(", block)
        self.assertIn("LayeredRegionInterestResidencyComparison.compare", block)
        self.assertNotIn("getRegion(", block)
        self.assertNotIn("unload(", block)
        self.assertNotIn("register", block)
        self.assertNotIn("LayeredRegionInterestResidencyComparison", path_validation)
        self.assertNotIn("LayeredRegionInterestResidencyComparison", player)
        self.assertIn("return getRegion(x, y).getTileValue(", manager)
        self.assertIn("### Slice 37: Dormant interest/residency projection", plan)


if __name__ == "__main__":
    unittest.main()
