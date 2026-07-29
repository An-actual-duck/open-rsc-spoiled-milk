#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
ANALYSIS = COORDINATES / (
    "LayeredPackedRegionAuthoredReconstructionCohortAnalysis.java"
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
import java.util.Collections;

public final class AuthoredReconstructionCohortFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifestBuilder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifestBuilder.recordNpcSpawn(
            4, 0, 10, 230, 20, 230, 250, 20, 20);
        manifestBuilder.recordNpcSpawn(
            5, 0, 11, 250, 20, 250, 300, 20, 20);
        manifestBuilder.recordGroundItemSpawn(
            8, 0, 20, 400, 20, 1, 30, 0);
        LayeredPackedRegionAuthoredPlacementManifest manifest =
            manifestBuilder.build();

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencyBuilder =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(9L);
        dependencyBuilder.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 230, 250, 20, 20, 4, 5, 0, 0);
        dependencyBuilder.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            5, 0, 250, 300, 20, 20, 5, 6, 0, 0);
        dependencyBuilder.record(
            ConstructionKind.GROUND_ITEM_SPAWN, DependencyKind.ANCHOR_ONLY,
            8, 0, 400, 400, 20, 20, 8, 8, 0, 0);
        LayeredPackedRegionAuthoredPopulationOutcome outcome =
            LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                .build(manifest);
        LayeredPackedRegionAuthoredReconstructionRecipe recipe =
            LayeredPackedRegionAuthoredReconstructionRecipe.derive(
                manifest, dependencyBuilder.build(), outcome);

        LayeredPackedRegionAuthoredReconstructionCohortAnalysis open =
            LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze(
                recipe, safety(4), 3, 3);
        check(open.getGeneration() == 9L
            && open.getSeedSourceCount() == 1
            && open.getCohortSourceCount() == 2
            && open.getExpandedAuthoredSourceCount() == 1
            && open.getAuthoredContentSourceCount() == 2
            && open.getMaximumExpansionRound() == 1,
            "authored dependencies expand to a fixed-point cohort");
        check(open.getReconstructionPlacementCount() == 2
            && open.getCrossSourcePlacementCount() == 2
            && open.getAffectedSourceReferenceCount() == 4,
            "cohort arithmetic includes recursively added content");
        check(open.getRequirementSourceCount() == 3
            && open.getCohortRequirementSourceCount() == 2
            && open.getExternalSupportRequirementSourceCount() == 1
            && open.isAuthoredClosureComplete()
            && !open.isFullySelfContained(),
            "empty dependency coordinates remain external support");
        check(open.getSources().get(0).getPackedRegionX() == 4
            && open.getSources().get(0).getRole()
                == LayeredPackedRegionAuthoredReconstructionCohortAnalysis
                    .CohortRole.SEED
            && open.getSources().get(0).getExpansionRound() == 0
            && open.getSources().get(0).isDependencySelfContained(),
            "seed order and direct authored closure remain visible");
        check(open.getSources().get(1).getPackedRegionX() == 5
            && open.getSources().get(1).getRole()
                == LayeredPackedRegionAuthoredReconstructionCohortAnalysis
                    .CohortRole.EXPANDED_AUTHORED
            && open.getSources().get(1).getExpansionRound() == 1
            && open.getSources().get(1)
                .getExternalSupportRequirementSourceCount() == 1
            && !open.getSources().get(1).isDependencySelfContained(),
            "expanded source explains its support-only neighbor");
        check(open.getRequirements().get(1).getPackedRegionX() == 5
            && open.getRequirements().get(1).isCohortSource()
            && open.getRequirements().get(1).hasAuthoredContent()
            && open.getRequirements().get(1).getOwnerSourceCount() == 2
            && open.getRequirements().get(1).getPlacementReferenceCount() == 2,
            "shared authored requirement is exact");
        check(open.getRequirements().get(2).getPackedRegionX() == 6
            && !open.getRequirements().get(2).isCohortSource()
            && !open.getRequirements().get(2).isRecipeSourcePresent()
            && !open.getRequirements().get(2).hasAuthoredContent()
            && open.getRequirements().get(2).isExternalSupportRequired(),
            "empty neighbor is support rather than reconstructable content");

        LayeredPackedRegionAuthoredReconstructionCohortAnalysis closed =
            LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze(
                recipe, safety(8), 1, 1);
        check(closed.getCohortSourceCount() == 1
            && closed.getExpandedAuthoredSourceCount() == 0
            && closed.getRequirementSourceCount() == 1
            && closed.getExternalSupportRequirementSourceCount() == 0
            && closed.isFullySelfContained(),
            "anchor-only seed is self-contained");
        expectImmutable(open.getSources());
        expectImmutable(open.getRequirements());
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze(
                recipe, safety(4), 1, 3));
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze(
                recipe, safety(4), 3, 2));
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


class LayeredMapsSliceSixtyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-sixty-three-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "AuthoredReconstructionCohortFixture.java"
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

    def test_fixed_point_expansion_separates_authored_and_support_sources(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "AuthoredReconstructionCohortFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_cohort_analysis_is_bounded_detached_evidence_only(self):
        source = ANALYSIS.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("import com.openrsc.server.model.world.region", source)
        self.assertNotIn("RegionManager", source)
        self.assertIn("maximumCohortSources", source)
        self.assertIn("maximumRequirementSources", source)
        self.assertIn("EXPANDED_AUTHORED", source)
        self.assertIn("isExternalSupportRequired", source)
        self.assertIn("detached evidence only", source)

    def test_living_plan_records_slice_sixty_three_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 63: Fixed-point authored reconstruction cohort", plan
        )
        self.assertIn("external support requirement", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
