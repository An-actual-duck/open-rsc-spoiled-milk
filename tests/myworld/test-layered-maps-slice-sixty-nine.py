#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
ANALYSIS = COORDINATES / (
    "LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis.java"
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
    .LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
        .DependencySemantics;
import java.util.Collections;

public final class AuthoredReconstructionDependencySemanticsFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifestBuilder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifestBuilder.recordScenery(
            4, 0, 100, 1000, 230, 20, 0, 0, "fixture");
        manifestBuilder.recordNpcSpawn(
            4, 0, 10, 230, 20, 230, 250, 20, 20);
        manifestBuilder.recordBoundary(
            5, 0, 101, 1001, 250, 20, 0, 1, "fixture");
        manifestBuilder.recordNpcSpawn(
            5, 0, 11, 250, 20, 230, 250, 20, 20);
        manifestBuilder.recordGroundItemSpawn(
            8, 0, 20, 400, 20, 1, 30, 0);
        LayeredPackedRegionAuthoredPlacementManifest manifest =
            manifestBuilder.build();

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencies =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(9L);
        dependencies.record(
            ConstructionKind.SCENERY, DependencyKind.OBJECT_FOOTPRINT,
            4, 0, 230, 250, 20, 20, 4, 5, 0, 0);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 230, 250, 20, 20, 4, 5, 0, 0);
        dependencies.record(
            ConstructionKind.BOUNDARY, DependencyKind.OBJECT_FOOTPRINT,
            5, 0, 230, 250, 20, 20, 4, 5, 0, 0);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            5, 0, 230, 250, 20, 20, 4, 5, 0, 0);
        dependencies.record(
            ConstructionKind.GROUND_ITEM_SPAWN, DependencyKind.ANCHOR_ONLY,
            8, 0, 400, 400, 20, 20, 8, 8, 0, 0);
        LayeredPackedRegionAuthoredPopulationOutcome outcome =
            LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                .build(manifest);
        LayeredPackedRegionAuthoredReconstructionRecipe recipe =
            LayeredPackedRegionAuthoredReconstructionRecipe.derive(
                manifest, dependencies.build(), outcome);

        LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
            analysis =
                LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
                    .analyze(recipe, safety(4), 1, 2, 1, 2);
        check(analysis.getGeneration() == 9L
            && analysis.getSafetyObservedAtTick() == 8L
            && analysis.getSelectedSourceCount() == 1
            && analysis.getSelectedAuthoredReplaySourceCount() == 1
            && analysis.getSelectedContentEmptySourceCount() == 0
            && analysis.getReplayPlacementCount() == 2,
            "replay contains only the exact selected authored source");
        check(analysis.getSelectedSources().get(0).getPackedRegionX() == 4
            && analysis.getSelectedSources().get(0).hasAuthoredContent()
            && analysis.getSelectedSources().get(0).getReplayPlacementCount()
                == 2,
            "selected source retains its own replay recipe");
        check(analysis.getOutboundSupportSourceCount() == 2
            && analysis.getExternalOutboundSupportSourceCount() == 1
            && analysis.getOutboundSupportReferenceCount() == 4
            && analysis.getExternalOutboundSupportReferenceCount() == 2,
            "outbound spatial support does not recursively import a recipe");
        check(analysis.getOutboundSupportSources().get(0).isSelectedSource()
            && analysis.getOutboundSupportSources().get(0)
                .getPlacementReferenceCount() == 2
            && analysis.getOutboundSupportSources().get(1)
                .isExternalSupportSource()
            && analysis.getOutboundSupportSources().get(1)
                .hasAuthoredContent()
            && analysis.getOutboundSupportSources().get(1)
                .getStaticFootprintReferenceCount() == 1
            && analysis.getOutboundSupportSources().get(1)
                .getPotentialMobileReferenceCount() == 1,
            "authored target content remains support rather than replay");
        check(analysis.getIncomingOwnerSourceCount() == 1
            && analysis.getIncomingPlacementCount() == 2
            && analysis.getIncomingReferenceCount() == 2,
            "external owner reach is reported separately");
        check(analysis.getIncomingOwners().get(0).getPackedRegionX() == 5
            && analysis.getIncomingOwners().get(0)
                .getOwnerReplayPlacementCount() == 2
            && analysis.getIncomingOwners().get(0)
                .getIncomingPlacementCount() == 2
            && analysis.getIncomingOwners().get(0)
                .getStaticFootprintPlacementCount() == 1
            && analysis.getIncomingOwners().get(0)
                .getPotentialMobilePlacementCount() == 1,
            "incoming owner retains static and potential-mobile semantics");
        check(analysis.getKindCount() == 3
            && analysis.getKinds().get(0).getConstructionKind()
                == ConstructionKind.SCENERY
            && analysis.getKinds().get(0).getSemantics()
                == DependencySemantics.STATIC_FOOTPRINT_SUPPORT
            && analysis.getKinds().get(0).getReplayPlacementCount() == 1
            && analysis.getKinds().get(1).getConstructionKind()
                == ConstructionKind.BOUNDARY
            && analysis.getKinds().get(1).getIncomingPlacementCount() == 1
            && analysis.getKinds().get(2).getConstructionKind()
                == ConstructionKind.NPC_SPAWN
            && analysis.getKinds().get(2).getSemantics()
                == DependencySemantics.POTENTIAL_MOBILE_SUPPORT
            && analysis.getKinds().get(2).getReplayPlacementCount() == 1
            && analysis.getKinds().get(2).getIncomingReferenceCount() == 1,
            "kind evidence keeps replay, support, and incoming roles distinct");
        check(analysis.isSourceLocalReplay()
            && analysis.isSpatialReachPreserved()
            && !analysis.isActiveInstanceEvidence()
            && !analysis.isEntityRegistry()
            && !analysis.isLifecycleAuthority(),
            "semantic classification remains inert evidence");
        expectImmutable(analysis.getSelectedSources());
        expectImmutable(analysis.getOutboundSupportSources());
        expectImmutable(analysis.getIncomingOwners());
        expectImmutable(analysis.getKinds());
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
                .analyze(recipe, safety(4), 0, 2, 1, 2));
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
                .analyze(recipe, safety(4), 1, 1, 1, 2));
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
                .analyze(recipe, safety(4), 1, 2, 0, 2));
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
                .analyze(recipe, safety(4), 1, 2, 1, 1));
    }

    private static LayeredPackedRegionRetirementSafetyAssessment safety(
        int packedRegionX) {
        LayeredRegionInterestOwnershipLedger ownership =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionRetirementEligibilityLedger retirement =
            new LayeredRegionRetirementEligibilityLedger(5L);
        LayeredRegionResidencyMirror residency =
            new LayeredRegionResidencyMirror();
        LayeredRegionRetirementDecisionArbiter arbiter =
            new LayeredRegionRetirementDecisionArbiter();
        WorldRegionKey key = new WorldRegionKey(
            WorldSpaceId.GLOBAL, 0, packedRegionX, 0);
        WorldRegionWindow window = new WorldRegionWindow(
            key.getWorldSpace(), key.getLevel(), key.getRegionX(),
            key.getRegionY(), key.getRegionX(), key.getRegionY());
        check(residency.registerPackedRegion(packedRegionX, 0),
            "register packed source");
        LayeredRegionInterestOwnershipLedger.OpenedOwner opened =
            ownership.openOwner(window, 1);
        retirement.observeOwnershipChange(opened.getChange(), 1L);
        retirement.observeOwnershipChange(
            ownership.closeOwner(opened.getOwnerToken()), 2L);
        LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
            retirement.snapshot(
                ownership.snapshot(key), residency.snapshot(key), 7L);
        LayeredRegionRetirementDecisionArbiter.Decision decision =
            arbiter.evaluate(candidate, retirement.snapshot(
                ownership.snapshot(key), residency.snapshot(key), 7L));
        LayeredPackedRegionRetirementReadiness readiness =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.singletonList(decision), 1, 2);
        return LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness,
            Collections.singletonList(
                LayeredPackedRegionRetirementSafetyAssessment
                    .PackedSourceContents.of(
                        packedRegionX, 0, true, true, false,
                        0, 0, 0, 0)),
            8L, 1);
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

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceSixtyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-sixty-nine-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "AuthoredReconstructionDependencySemanticsFixture.java"
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

    def test_replay_support_and_incoming_reach_remain_separate(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "AuthoredReconstructionDependencySemanticsFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_analysis_is_bounded_detached_evidence_only(self):
        source = ANALYSIS.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("import com.openrsc.server.model.world.region", source)
        self.assertNotIn("RegionManager", source)
        self.assertIn("maximumSelectedSources", source)
        self.assertIn("maximumSupportSources", source)
        self.assertIn("maximumIncomingOwners", source)
        self.assertIn("maximumIncomingPlacements", source)
        self.assertIn("potential reach, not active-instance evidence", source)

    def test_living_plan_records_slice_sixty_nine_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 69: Reconstruction dependency semantics", plan
        )
        self.assertIn("source-local", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
