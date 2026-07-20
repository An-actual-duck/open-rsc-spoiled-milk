#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
ATTRIBUTION = COORDINATES / (
    "LayeredPackedRegionAuthoredReconstructionCohortAttribution.java"
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

public final class AuthoredReconstructionCohortAttributionFixture {
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

        LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort =
            LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze(
                recipe, safety(4), 3, 3);
        LayeredPackedRegionAuthoredReconstructionCohortAttribution attribution =
            LayeredPackedRegionAuthoredReconstructionCohortAttribution.analyze(
                recipe, cohort, 4, 2);
        check(attribution.getGeneration() == 9L
            && attribution.getSafetyObservedAtTick() == 8L
            && attribution.getPlacementCount() == 2
            && attribution.getCrossSourcePlacementCount() == 2
            && attribution.getAffectedSourceReferenceCount() == 4
            && attribution.getCrossSourceReferenceCount() == 2,
            "cohort attribution preserves exact recipe arithmetic");
        check(attribution.getKindCount() == 1
            && attribution.getKinds().get(0).getConstructionKind()
                == ConstructionKind.NPC_SPAWN
            && attribution.getKinds().get(0).getDependencyKind()
                == DependencyKind.NPC_ROAMING
            && attribution.getKinds().get(0).getPlacementCount() == 2
            && attribution.getKinds().get(0)
                .getExpansionFrontierReferenceCount() == 1
            && attribution.getKinds().get(0)
                .getExternalSupportReferenceCount() == 1,
            "typed totals distinguish frontier and support references");
        check(attribution.getEdgeCount() == 4
            && attribution.getSelfEdgeCount() == 2
            && attribution.getExpansionFrontierEdgeCount() == 1
            && attribution.getExternalSupportEdgeCount() == 1,
            "every exact owner-to-requirement edge is classified");
        check(attribution.getEdges().get(1).getOwnerPackedRegionX() == 4
            && attribution.getEdges().get(1).getRequiredPackedRegionX() == 5
            && attribution.getEdges().get(1).getOwnerExpansionRound() == 0
            && attribution.getEdges().get(1).getRequiredExpansionRound() == 1
            && attribution.getEdges().get(1).isExpansionFrontier()
            && attribution.getEdges().get(1)
                .getReferenceCount(ConstructionKind.NPC_SPAWN) == 1
            && attribution.getEdges().get(1)
                .getReferenceCount(DependencyKind.NPC_ROAMING) == 1,
            "frontier edge identifies its exact typed bridge");
        check(attribution.getEdges().get(3).getRequiredPackedRegionX() == 6
            && attribution.getEdges().get(3).getRequiredExpansionRound() == -1
            && attribution.getEdges().get(3).isExternalSupportRequired()
            && !attribution.getEdges().get(3).isCohortSource(),
            "support edge remains outside the authored cohort");
        check(attribution.getBridgePlacementCount() == 2
            && attribution.getBridgePlacements().get(0)
                .getIdentityGeneration() == 9L
            && attribution.getBridgePlacements().get(0).getSourceOrdinal() == 1
            && attribution.getBridgePlacements().get(0)
                .getAuthoredDefinitionId() == 10
            && attribution.getBridgePlacements().get(0)
                .getAffectedSourceCount() == 2
            && attribution.getBridgePlacements().get(0)
                .getExpansionFrontierSourceCount() == 1
            && attribution.getBridgePlacements().get(1)
                .getExternalSupportRequirementSourceCount() == 1,
            "cross-source placement identities explain each bridge envelope");
        check(attribution.isIdentityMetadataOnly()
            && !attribution.isEntityRegistry()
            && !attribution.isLifecycleAuthority(),
            "attribution remains inert evidence");
        expectImmutable(attribution.getKinds());
        expectImmutable(attribution.getEdges());
        expectImmutable(attribution.getBridgePlacements());
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionCohortAttribution.analyze(
                recipe, cohort, 3, 2));
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionCohortAttribution.analyze(
                recipe, cohort, 4, 1));
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


class LayeredMapsSliceSixtyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-sixty-five-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "AuthoredReconstructionCohortAttributionFixture.java"
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

    def test_typed_edges_explain_frontier_and_support_bridges(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "AuthoredReconstructionCohortAttributionFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_attribution_is_bounded_detached_evidence_only(self):
        source = ATTRIBUTION.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("import com.openrsc.server.model.world.region", source)
        self.assertNotIn("RegionManager", source)
        self.assertIn("maximumEdges", source)
        self.assertIn("maximumBridgePlacements", source)
        self.assertIn("isExpansionFrontier", source)
        self.assertIn("isExternalSupportRequired", source)
        self.assertIn("diagnostic evidence only", source)

    def test_living_plan_records_slice_sixty_five_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 65: Cohort dependency-edge attribution", plan
        )
        self.assertIn("expansion-frontier", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
