#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
ASSESSMENT = COORDINATES / (
    "LayeredPackedRegionRetirementSafetyAssessment.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


POINT_STUB = r'''
package com.openrsc.server.model;

public class Point {
    private final int x;
    private final int y;
    public Point(int x, int y) { this.x = x; this.y = y; }
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DiagnosticCandidateSelectionFixture {
    public static void main(String[] args) {
        LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents
            occupied = contents(4, true, true, false, 0, 2, 1, 0);
        LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents
            absent = contents(5, false, false, false, 0, 0, 0, 0);
        LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents
            quiescent = contents(6, true, true, true, 0, 0, 0, 0);
        List<LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents>
            contents = Arrays.asList(occupied, absent, quiescent);
        LayeredPackedRegionRetirementSafetyAssessment assessment =
            LayeredPackedRegionRetirementSafetyAssessment
                .assessDiagnosticSelection(contents, 30L, 3);

        check(assessment.getObservedAtTick() == 30L
            && assessment.getReadinessObservedAtTick() == -1L
            && assessment.getOwnershipVersion() == -1L
            && assessment.getResidencyMirrorVersion() == -1L
            && !assessment.hasRetirementReadinessEvidence(),
            "diagnostic selection cannot manufacture readiness metadata");
        check(assessment.getSourceCount() == 3
            && assessment.getContentQuiescentSourceCount() == 1
            && assessment.getLifecycleReadySourceCount() == 0
            && assessment.getBlockedSourceCount() == 3,
            "all diagnostic candidates remain lifecycle blocked");

        LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment first =
            assessment.getSources().get(0);
        check(first.getPackedRegionX() == 4
            && first.getReadinessState()
                == LayeredPackedRegionRetirementReadiness.SourceState
                    .DIAGNOSTIC_SELECTION_ONLY
            && first.getBlockers().equals(Arrays.asList(
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .READINESS_NOT_READY,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .NPCS_PRESENT,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .OBJECTS_PRESENT,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .RELOAD_PATH_UNAVAILABLE)),
            "occupied source retains exact diagnostic blockers");
        check(assessment.getSources().get(1).getBlockers().equals(Arrays.asList(
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .READINESS_NOT_READY,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .SOURCE_NOT_RESIDENT,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .TILE_STORAGE_UNAVAILABLE,
                LayeredPackedRegionRetirementSafetyAssessment.Blocker
                    .RELOAD_PATH_UNAVAILABLE)),
            "absent source is observed without being loaded");
        check(assessment.getSources().get(2).isContentQuiescent()
            && !assessment.getSources().get(2).isLifecycleReady()
            && assessment.getSources().get(2).getBlockers().equals(
                Collections.singletonList(
                    LayeredPackedRegionRetirementSafetyAssessment.Blocker
                        .READINESS_NOT_READY)),
            "quiescent diagnostic source still lacks retirement readiness");
        expectImmutable(assessment.getSources());
        expectImmutable(first.getBlockers());

        expectNull(() -> LayeredPackedRegionRetirementSafetyAssessment
            .assessDiagnosticSelection(null, 30L, 3));
        expectNull(() -> LayeredPackedRegionRetirementSafetyAssessment
            .assessDiagnosticSelection(Arrays.asList(occupied, null), 30L, 2));
        expectIllegal(() -> LayeredPackedRegionRetirementSafetyAssessment
            .assessDiagnosticSelection(contents, -1L, 3));
        expectIllegal(() -> LayeredPackedRegionRetirementSafetyAssessment
            .assessDiagnosticSelection(contents, 30L, 2));
        expectIllegal(() -> LayeredPackedRegionRetirementSafetyAssessment
            .assessDiagnosticSelection(Arrays.asList(occupied, occupied), 30L, 2));
    }

    private static LayeredPackedRegionRetirementSafetyAssessment
        .PackedSourceContents contents(
            int sourceX,
            boolean resident,
            boolean tiles,
            boolean reload,
            int players,
            int npcs,
            int objects,
            int groundItems) {
        return LayeredPackedRegionRetirementSafetyAssessment
            .PackedSourceContents.of(
                sourceX, 0, resident, tiles, reload, players, npcs, objects,
                groundItems);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void expectImmutable(java.util.List values) {
        try {
            values.add(new Object());
            throw new AssertionError("Expected immutable list");
        } catch (UnsupportedOperationException expected) {
            // Expected refusal.
        }
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

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceEightyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-eighty-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "DiagnosticCandidateSelectionFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes), str(point),
                str(fixture),
                *(str(path) for path in sorted(COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_diagnostic_selection_never_manufactures_readiness(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "DiagnosticCandidateSelectionFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_manager_candidate_observation_is_peek_only(self):
        source = REGION_MANAGER.read_text(encoding="utf-8")
        method_name = (
            "assessLayeredPackedRegionRetirementRefinementCandidates"
        )
        start = source.index(method_name)
        end = source.index("\n\t/**", start)
        method = source[start:end]
        self.assertIn("peekRegionFromSectorCoordinates", method)
        self.assertIn("captureRetirementContentsSnapshot", method)
        self.assertIn("assessDiagnosticSelection", method)
        self.assertNotIn("getRegion(", method)
        self.assertNotIn("register", method)
        self.assertNotIn("unload", method)
        self.assertNotIn("remove", method)

    def test_living_plan_records_slice_eighty_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 80: Read-only refinement-candidate observation", plan
        )
        self.assertIn("DIAGNOSTIC_SELECTION_ONLY", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
