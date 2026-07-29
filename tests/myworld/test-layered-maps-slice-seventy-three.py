#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
ASSESSMENT = COORDINATES / (
    "LayeredPackedRegionActiveNpcContainmentAssessment.java"
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

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionActiveNpcContainmentAssessment.BlockerKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionActiveNpcResidencyObservation.NpcInstanceSnapshot;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ActiveNpcContainmentFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifest.recordNpcSpawn(4, 0, 10, 200, 20, 190, 210, 10, 30);
        LayeredAuthoredPlacementIdentity selectedA =
            manifest.getLastRecordedIdentity();
        manifest.recordNpcSpawn(4, 0, 12, 205, 20, 190, 220, 10, 30);
        LayeredAuthoredPlacementIdentity selectedB =
            manifest.getLastRecordedIdentity();
        manifest.recordNpcSpawn(6, 0, 11, 300, 20, 280, 310, 10, 30);
        LayeredAuthoredPlacementIdentity external =
            manifest.getLastRecordedIdentity();
        LayeredPackedRegionAuthoredPlacementManifest builtManifest =
            manifest.build();

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder deps =
            LayeredPackedRegionAuthoredPlacementDependencyInventory.builder(9L);
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 190, 210, 10, 30, 3, 4, 0, 0);
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 190, 220, 10, 30, 3, 4, 0, 0);
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            6, 0, 280, 310, 10, 30, 5, 6, 0, 0);
        LayeredPackedRegionAuthoredPopulationOutcome outcome =
            LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                .build(builtManifest);
        LayeredPackedRegionAuthoredReconstructionRecipe recipe =
            LayeredPackedRegionAuthoredReconstructionRecipe.derive(
                builtManifest, deps.build(), outcome);
        LayeredPackedRegionRetirementSafetyAssessment safety = safety(4, 5);

        List<NpcInstanceSnapshot> containedCensus = Arrays.asList(
            new NpcInstanceSnapshot(selectedA, 10, 4, 0, true),
            new NpcInstanceSnapshot(selectedB, 12, 5, 0, true),
            new NpcInstanceSnapshot(external, 11, 6, 0, true),
            new NpcInstanceSnapshot(external, 11, 6, 0, false));
        LayeredPackedRegionActiveNpcResidencyObservation containedObservation =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety, 12L, containedCensus, 4, 2);
        LayeredPackedRegionActiveNpcContainmentAssessment contained =
            LayeredPackedRegionActiveNpcContainmentAssessment.assess(
                containedObservation);
        check(contained.isBoundaryContained()
            && contained.getBlockingConditionCount() == 0
            && contained.getBlockingEvidenceCount() == 0,
            "inside-only movement is boundary-contained");
        check(contained.getSelectedOwnerInsideCount() == 2
            && contained.getSameSourceSelectedOwnerInsideCount() == 1
            && contained.getCrossSourceSelectedOwnerInsideCount() == 1
            && contained.getCurrentInsideCount() == 2,
            "cross-source movement inside the selection remains explicit");
        check(contained.getActivePreservationRequiredInstanceCount() == 2
            && contained.isEntityPreservationRequired()
            && !contained.isLifecycleReady(),
            "contained active entities still require preservation");
        for (LayeredPackedRegionActiveNpcContainmentAssessment.BlockerCount
                blocker : contained.getBlockers()) {
            check(blocker.getInstanceCount() == 0,
                "contained assessment has zero blocker counts");
        }

        LayeredAuthoredPlacementIdentity staleSelected =
            new LayeredAuthoredPlacementIdentity(
                8L, 4, 0, selectedA.getSourceOrdinal(),
                ConstructionKind.NPC_SPAWN);
        List<NpcInstanceSnapshot> openCensus = Arrays.asList(
            new NpcInstanceSnapshot(selectedA, 10, 4, 0, true),
            new NpcInstanceSnapshot(selectedA, 10, 4, 0, true),
            new NpcInstanceSnapshot(selectedB, 12, 6, 0, true),
            new NpcInstanceSnapshot(external, 11, 5, 0, true),
            new NpcInstanceSnapshot(null, 20, 5, 0, true),
            new NpcInstanceSnapshot(staleSelected, 10, 6, 0, true),
            new NpcInstanceSnapshot(selectedA, 10, 4, 0, false));
        LayeredPackedRegionActiveNpcResidencyObservation openObservation =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety, 13L, openCensus, 7, 6);
        LayeredPackedRegionActiveNpcContainmentAssessment open =
            LayeredPackedRegionActiveNpcContainmentAssessment.assess(
                openObservation);
        check(!open.isBoundaryContained()
            && open.getBlockingConditionCount() == 6
            && open.getBlockingEvidenceCount() == 6,
            "every independent boundary blocker remains explicit");
        check(blocker(open, BlockerKind.SELECTED_OWNER_OUTSIDE) == 1
            && blocker(open, BlockerKind.EXTERNAL_OWNER_INSIDE) == 1
            && blocker(open, BlockerKind.UNRESOLVED_INSIDE) == 1
            && blocker(open,
                BlockerKind.UNRESOLVED_CLAIMED_SELECTED_OWNER_OUTSIDE) == 1
            && blocker(open, BlockerKind.RELEVANT_INACTIVE) == 1
            && blocker(open, BlockerKind.RELEVANT_DUPLICATE_IDENTITY) == 1,
            "blocker kinds retain exact counts");
        check(open.getRelevantDuplicateIdentityInstanceCount() == 1
            && open.getActivePreservationRequiredInstanceCount() == 6
            && open.isEntityPreservationRequired()
            && !open.isLifecycleReady(),
            "open assessment retains duplicates and preservation burden");
        check(open.isPointInTimeOnly() && open.isContainmentEvidence()
            && !open.isEntityRegistry() && !open.isArrivalGate()
            && !open.isLifecycleAuthority(),
            "assessment remains inert point-in-time evidence");
        expectImmutable(open.getBlockers());
        expectNullFailure(() ->
            LayeredPackedRegionActiveNpcContainmentAssessment.assess(null));
    }

    private static int blocker(
        LayeredPackedRegionActiveNpcContainmentAssessment assessment,
        BlockerKind kind) {
        for (LayeredPackedRegionActiveNpcContainmentAssessment.BlockerCount
                blocker : assessment.getBlockers()) {
            if (blocker.getKind() == kind) {
                return blocker.getInstanceCount();
            }
        }
        throw new AssertionError("Missing blocker " + kind);
    }

    private static LayeredPackedRegionRetirementSafetyAssessment safety(
        int minimumPackedRegionX, int maximumPackedRegionX) {
        LayeredRegionInterestOwnershipLedger ownership =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionRetirementEligibilityLedger retirement =
            new LayeredRegionRetirementEligibilityLedger(5L);
        LayeredRegionResidencyMirror residency =
            new LayeredRegionResidencyMirror();
        LayeredRegionRetirementDecisionArbiter arbiter =
            new LayeredRegionRetirementDecisionArbiter();
        WorldRegionWindow window = new WorldRegionWindow(
            WorldSpaceId.GLOBAL, 0, minimumPackedRegionX, 0,
            maximumPackedRegionX, 0);
        for (int x = minimumPackedRegionX; x <= maximumPackedRegionX; x++) {
            check(residency.registerPackedRegion(x, 0),
                "register packed source " + x);
        }
        LayeredRegionInterestOwnershipLedger.OpenedOwner opened =
            ownership.openOwner(window, 4);
        retirement.observeOwnershipChange(opened.getChange(), 1L);
        retirement.observeOwnershipChange(
            ownership.closeOwner(opened.getOwnerToken()), 2L);
        List<LayeredRegionRetirementDecisionArbiter.Decision> decisions =
            new ArrayList<LayeredRegionRetirementDecisionArbiter.Decision>();
        for (int x = minimumPackedRegionX; x <= maximumPackedRegionX; x++) {
            WorldRegionKey key = new WorldRegionKey(
                WorldSpaceId.GLOBAL, 0, x, 0);
            LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
                retirement.snapshot(
                    ownership.snapshot(key), residency.snapshot(key), 7L);
            decisions.add(arbiter.evaluate(candidate, retirement.snapshot(
                ownership.snapshot(key), residency.snapshot(key), 7L)));
        }
        LayeredPackedRegionRetirementReadiness readiness =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                decisions, 4, 4);
        List<LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents>
            contents = new ArrayList<
                LayeredPackedRegionRetirementSafetyAssessment
                    .PackedSourceContents>();
        for (LayeredPackedRegionRetirementReadiness.SourceReadiness source
                : readiness.getSources()) {
            contents.add(LayeredPackedRegionRetirementSafetyAssessment
                .PackedSourceContents.of(
                    source.getPackedRegionX(), source.getPackedRegionY(),
                    true, true, false, 0, 0, 0, 0));
        }
        return LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness, contents, 8L, 4);
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

    private static void expectNullFailure(Runnable operation) {
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


class LayeredMapsSliceSeventyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-seventy-three-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "ActiveNpcContainmentFixture.java"
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

    def test_contained_and_open_boundaries_remain_distinct_from_lifecycle(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "ActiveNpcContainmentFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_assessment_is_detached_point_in_time_evidence_only(self):
        source = ASSESSMENT.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("import com.openrsc.server.model.world.region", source)
        self.assertNotIn("RegionManager", source)
        self.assertIn("Containment is never lifecycle readiness", source)
        self.assertIn("isPointInTimeOnly() { return true; }", source)
        self.assertIn("isLifecycleReady() { return false; }", source)
        self.assertIn("isEntityRegistry() { return false; }", source)
        self.assertIn("isArrivalGate() { return false; }", source)
        self.assertIn("isLifecycleAuthority() { return false; }", source)

    def test_living_plan_records_slice_seventy_three_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 73: Active NPC containment assessment", plan
        )
        self.assertIn("contained now", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
