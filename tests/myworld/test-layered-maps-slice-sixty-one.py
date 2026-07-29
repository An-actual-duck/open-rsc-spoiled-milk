#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
OBSERVATION = COORDINATES / (
    "LayeredPackedRegionAuthoredReconstructionObservation.java"
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

public final class AuthoredReconstructionObservationFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifestBuilder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(5L);
        manifestBuilder.recordNpcSpawn(
            4, 0, 10, 230, 20, 230, 250, 20, 20);
        manifestBuilder.recordGroundItemSpawn(
            5, 0, 20, 250, 20, 1, 30, 0);
        LayeredPackedRegionAuthoredPlacementManifest manifest =
            manifestBuilder.build();

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencyBuilder =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(5L);
        dependencyBuilder.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 230, 250, 20, 20, 4, 5, 0, 0);
        dependencyBuilder.record(
            ConstructionKind.GROUND_ITEM_SPAWN, DependencyKind.ANCHOR_ONLY,
            5, 0, 250, 250, 20, 20, 5, 5, 0, 0);
        LayeredPackedRegionAuthoredPopulationOutcome outcome =
            LayeredPackedRegionAuthoredPopulationOutcome.builder(5L)
                .build(manifest);
        LayeredPackedRegionAuthoredReconstructionRecipe recipe =
            LayeredPackedRegionAuthoredReconstructionRecipe.derive(
                manifest, dependencyBuilder.build(), outcome);

        LayeredPackedRegionAuthoredReconstructionObservation open =
            LayeredPackedRegionAuthoredReconstructionObservation.observe(
                recipe, safety(4), 1, 2);
        check(open.getGeneration() == 5L
            && open.getRecipeSourceCount() == 2
            && open.getRecipeManifestPlacementCount() == 2
            && open.getRecipeSupersededPlacementCount() == 0
            && open.getRecipeReconstructionPlacementCount() == 2,
            "observation retains whole-recipe context");
        check(open.getSourceCount() == 1
            && open.getAuthoredSourceCount() == 1
            && open.getManifestPlacementCount() == 1
            && open.getReconstructionPlacementCount() == 1
            && open.getCrossSourcePlacementCount() == 1
            && open.getAffectedSourceReferenceCount() == 2,
            "observation projects exact selected-source recipe counts");
        check(open.getRequirementSourceCount() == 2
            && open.getSelectedRequirementSourceCount() == 1
            && open.getMissingRequirementSourceCount() == 1
            && !open.isSelectionDependencyClosed(),
            "cross-source reach exposes incomplete selection closure");
        check(open.getSources().get(0).getRequirementSourceCount() == 2
            && open.getSources().get(0).getSelectedRequirementSourceCount() == 1
            && open.getSources().get(0).getMissingRequirementSourceCount() == 1
            && !open.getSources().get(0).isDependencyClosed(),
            "per-source closure matches the selected safety set");
        check(open.getRequirements().get(0).getPackedRegionX() == 4
            && open.getRequirements().get(0).isSelectedSafetySource()
            && open.getRequirements().get(0).isAuthoredRecipeSource()
            && open.getRequirements().get(1).getPackedRegionX() == 5
            && !open.getRequirements().get(1).isSelectedSafetySource()
            && open.getRequirements().get(1).isAuthoredRecipeSource()
            && open.getRequirements().get(1).getOwnerSourceCount() == 1
            && open.getRequirements().get(1).getPlacementReferenceCount() == 1,
            "requirements are exact, sorted, and explanatory");

        LayeredPackedRegionAuthoredReconstructionObservation closed =
            LayeredPackedRegionAuthoredReconstructionObservation.observe(
                recipe, safety(5), 1, 1);
        check(closed.getRequirementSourceCount() == 1
            && closed.getSelectedRequirementSourceCount() == 1
            && closed.getMissingRequirementSourceCount() == 0
            && closed.isSelectionDependencyClosed()
            && closed.getSources().get(0).isDependencyClosed(),
            "anchor-only source selection is dependency closed");
        expectImmutable(open.getSources());
        expectImmutable(open.getRequirements());
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionObservation.observe(
                recipe, safety(4), 1, 1));
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionObservation.observe(
                recipe, safety(4), 0, 2));
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
        LayeredPackedRegionRetirementReadiness.SourceReadiness source =
            readiness.getSources().get(0);
        check(source.getPackedRegionX() == packedRegionX
                && source.getPackedRegionY() == 0,
            "fixture safety source identity");
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


class LayeredMapsSliceSixtyOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-sixty-one-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "AuthoredReconstructionObservationFixture.java"
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

    def test_safety_source_projection_reports_exact_dependency_closure(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "AuthoredReconstructionObservationFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_projection_is_bounded_detached_evidence_only(self):
        source = OBSERVATION.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("import com.openrsc.server.model.world.region", source)
        self.assertNotIn("RegionManager", source)
        self.assertIn("maximumRequirementSources", source)
        self.assertIn("Collections.unmodifiableList", source)
        self.assertIn("isSelectionDependencyClosed", source)
        self.assertIn("not a load request", source)

    def test_living_plan_records_slice_sixty_one_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 61: Safety-source reconstruction requirements", plan
        )
        self.assertIn("diagnostic evidence only", plan)


if __name__ == "__main__":
    unittest.main()
