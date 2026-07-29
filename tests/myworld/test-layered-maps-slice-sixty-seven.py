#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
TOPOLOGY = COORDINATES / (
    "LayeredPackedRegionAuthoredReconstructionTopologyAnalysis.java"
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

public final class AuthoredReconstructionTopologyFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifestBuilder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifestBuilder.recordNpcSpawn(
            4, 0, 10, 230, 20, 230, 250, 20, 20);
        manifestBuilder.recordNpcSpawn(
            5, 0, 11, 250, 20, 230, 250, 20, 20);
        manifestBuilder.recordNpcSpawn(
            6, 0, 12, 300, 20, 250, 300, 20, 20);
        manifestBuilder.recordGroundItemSpawn(
            8, 0, 20, 400, 20, 1, 30, 0);
        manifestBuilder.recordNpcSpawn(
            10, 0, 13, 500, 20, 500, 550, 20, 20);
        LayeredPackedRegionAuthoredPlacementManifest manifest =
            manifestBuilder.build();

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencies =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(9L);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 230, 250, 20, 20, 4, 5, 0, 0);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            5, 0, 230, 250, 20, 20, 4, 5, 0, 0);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            6, 0, 250, 300, 20, 20, 5, 6, 0, 0);
        dependencies.record(
            ConstructionKind.GROUND_ITEM_SPAWN, DependencyKind.ANCHOR_ONLY,
            8, 0, 400, 400, 20, 20, 8, 8, 0, 0);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            10, 0, 500, 550, 20, 20, 10, 11, 0, 0);
        LayeredPackedRegionAuthoredPopulationOutcome outcome =
            LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                .build(manifest);
        LayeredPackedRegionAuthoredReconstructionRecipe recipe =
            LayeredPackedRegionAuthoredReconstructionRecipe.derive(
                manifest, dependencies.build(), outcome);
        LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort =
            LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze(
                recipe, safety(4), 2, 2);

        LayeredPackedRegionAuthoredReconstructionTopologyAnalysis topology =
            LayeredPackedRegionAuthoredReconstructionTopologyAnalysis.analyze(
                recipe, cohort, 5, 9);
        check(topology.getGeneration() == 9L
            && topology.getSafetyObservedAtTick() == 8L
            && topology.getRecipeSourceCount() == 5
            && topology.getAuthoredSourceCount() == 5,
            "whole-recipe authored nodes remain exact");
        check(topology.getDirectedEdgeCount() == 8
            && topology.getSelfEdgeCount() == 5
            && topology.getCrossSourceDirectedEdgeCount() == 3
            && topology.getAuthoredDependencyReferenceCount() == 8
            && topology.getCrossSourceAuthoredReferenceCount() == 3,
            "directed relationships retain exact reference arithmetic");
        check(topology.getExternalSupportSourceCount() == 1
            && topology.getExternalSupportEdgeCount() == 1
            && topology.getExternalSupportReferenceCount() == 1,
            "empty dependency coordinates remain explicit support");
        check(topology.getForwardCohortSourceCount() == 2
            && topology.getForwardAuthoredSourceCount() == 2
            && topology.getTouchedWeakComponentCount() == 1
            && topology.getConservativeConnectedSourceCount() == 3
            && topology.getIncomingOnlySourceCount() == 1
            && topology.getDirectIncomingEdgeCount() == 1
            && topology.getDirectIncomingReferenceCount() == 1,
            "incoming authored dependency expands the conservative component");
        check(topology.getConservativeConnectedEdgeCount() == 6
            && topology.getConservativeConnectedReferenceCount() == 6
            && topology.isForwardDependencyClosed()
            && !topology.isForwardCohortWeaklyClosed(),
            "forward closure remains distinct from bidirectional closure");
        check(topology.getWeakComponentCount() == 3
            && topology.getLargestWeakComponentSourceCount() == 3
            && topology.getStrongComponentCount() == 4
            && topology.getLargestStrongComponentSourceCount() == 2
            && topology.getCyclicStrongComponentCount() == 1,
            "weak and strong component topology is exact");
        LayeredPackedRegionAuthoredReconstructionTopologyAnalysis.KindTopology
            npc = topology.getKinds().get(0);
        check(npc.getConstructionKind() == ConstructionKind.NPC_SPAWN
            && npc.getDependencyKind() == DependencyKind.NPC_ROAMING
            && npc.getAuthoredDependencyReferenceCount() == 7
            && npc.getCrossSourceAuthoredReferenceCount() == 3
            && npc.getExternalSupportReferenceCount() == 1
            && npc.getDirectIncomingReferenceCount() == 1
            && npc.getConservativeConnectedReferenceCount() == 6,
            "kind attribution identifies the incoming roaming edge");
        check(topology.getSources().get(2).getPackedRegionX() == 6
            && topology.getSources().get(2).isIncomingOnlySource()
            && topology.getSources().get(2).isConservativeConnectedSource()
            && !topology.getSources().get(3).isConservativeConnectedSource(),
            "source evidence distinguishes incoming-only and unrelated nodes");
        check(topology.isIdentityMetadataOnly()
            && !topology.isEntityRegistry()
            && !topology.isLifecycleAuthority(),
            "topology remains inert evidence");
        expectImmutable(topology.getSources());
        expectImmutable(topology.getKinds());
        expectImmutable(topology.getWeakComponents());
        expectImmutable(topology.getStrongComponents());
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionTopologyAnalysis.analyze(
                recipe, cohort, 4, 9));
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionTopologyAnalysis.analyze(
                recipe, cohort, 5, 8));
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


class LayeredMapsSliceSixtySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-sixty-seven-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "AuthoredReconstructionTopologyFixture.java"
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

    def test_directed_topology_distinguishes_forward_and_incoming_closure(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "AuthoredReconstructionTopologyFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_topology_is_bounded_detached_evidence_only(self):
        source = TOPOLOGY.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("import com.openrsc.server.model.world.region", source)
        self.assertNotIn("RegionManager", source)
        self.assertIn("maximumSources", source)
        self.assertIn("maximumRelationships", source)
        self.assertIn("incomingOnlySourceCount", source)
        self.assertIn("diagnostic evidence only", source)

    def test_living_plan_records_slice_sixty_seven_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 67: Whole-recipe directed topology audit", plan
        )
        self.assertIn("incoming-only", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
