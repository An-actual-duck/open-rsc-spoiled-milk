#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
PROJECTION = COORDINATES / (
    "LayeredPackedRegionActiveNpcBoundaryRequirementProjection.java"
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
    .LayeredPackedRegionActiveNpcBoundaryRequirementProjection
    .RequirementReason;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionActiveNpcBoundaryRequirementProjection
    .SourceRequirement;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionActiveNpcResidencyObservation.NpcInstanceSnapshot;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ActiveNpcBoundaryRequirementFixture {
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
        LayeredAuthoredPlacementIdentity externalA =
            manifest.getLastRecordedIdentity();
        manifest.recordNpcSpawn(6, 0, 13, 302, 20, 280, 312, 10, 30);
        LayeredAuthoredPlacementIdentity externalB =
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
        deps.record(ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            6, 0, 280, 312, 10, 30, 5, 6, 0, 0);
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
            new NpcInstanceSnapshot(externalA, 11, 6, 0, true));
        LayeredPackedRegionActiveNpcResidencyObservation containedObservation =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety, 12L, containedCensus, 3, 2);
        LayeredPackedRegionActiveNpcBoundaryRequirementProjection contained =
            LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project(
                containedObservation, 2);
        check(contained.isBoundaryContainedNow()
            && contained.getExpandableBoundaryInstanceCount() == 0
            && contained.getUniqueRequiredSourceCount() == 0
            && contained.getRequirements().isEmpty(),
            "contained census requires no expansion source");
        check(contained.getHardBlockingConditionCount() == 0
            && contained.getHardBlockingEvidenceCount() == 0,
            "contained census has no hard blocker");
        check(contained.isFreshSafetyAssessmentRequired()
            && contained.isFreshNpcCensusRequired()
            && !contained.isSelectionMutated()
            && !contained.isBoundaryClosureProved(),
            "contained-now result still cannot prove later closure");

        LayeredAuthoredPlacementIdentity staleSelected =
            new LayeredAuthoredPlacementIdentity(
                8L, 4, 0, selectedA.getSourceOrdinal(),
                ConstructionKind.NPC_SPAWN);
        List<NpcInstanceSnapshot> openCensus = Arrays.asList(
            new NpcInstanceSnapshot(selectedA, 10, 7, 0, true),
            new NpcInstanceSnapshot(externalA, 11, 5, 0, true),
            new NpcInstanceSnapshot(externalB, 13, 5, 0, true),
            new NpcInstanceSnapshot(null, 20, 5, 0, true),
            new NpcInstanceSnapshot(staleSelected, 10, 8, 0, true),
            new NpcInstanceSnapshot(selectedB, 12, 4, 0, true),
            new NpcInstanceSnapshot(selectedB, 12, 4, 0, true),
            new NpcInstanceSnapshot(selectedA, 10, 4, 0, false));
        LayeredPackedRegionActiveNpcResidencyObservation openObservation =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety, 13L, openCensus, 8, 7);
        LayeredPackedRegionActiveNpcBoundaryRequirementProjection open =
            LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project(
                openObservation, 2);
        check(!open.isBoundaryContainedNow()
            && open.getSelectedOwnerOutsideInstanceCount() == 1
            && open.getExternalOwnerInsideInstanceCount() == 2
            && open.getExpandableBoundaryInstanceCount() == 3,
            "recognized crossings remain expandable evidence");
        check(open.getUniqueRequiredSourceCount() == 2
            && open.getRequirements().size() == 2,
            "three crossings deduplicate to two exact sources");
        SourceRequirement owner = open.getRequirements().get(0);
        SourceRequirement current = open.getRequirements().get(1);
        check(owner.getPackedRegionX() == 6 && owner.getPackedRegionY() == 0
            && owner.getBoundaryInstanceCount() == 2
            && owner.getExternalOwnerAuthoredSourceInstanceCount() == 2
            && owner.getSelectedOwnerCurrentSourceInstanceCount() == 0,
            "external instances request their shared authored owner source");
        check(current.getPackedRegionX() == 7 && current.getPackedRegionY() == 0
            && current.getBoundaryInstanceCount() == 1
            && current.getSelectedOwnerCurrentSourceInstanceCount() == 1
            && current.getExternalOwnerAuthoredSourceInstanceCount() == 0,
            "selected-owned outside instance requests its current source");
        check(reason(owner, RequirementReason.EXTERNAL_OWNER_AUTHORED_SOURCE)
                == 2
            && reason(owner, RequirementReason.SELECTED_OWNER_CURRENT_SOURCE)
                == 0
            && reason(current,
                RequirementReason.SELECTED_OWNER_CURRENT_SOURCE) == 1,
            "stable reason counts preserve the crossing explanation");
        check(open.getUnresolvedInsideInstanceCount() == 1
            && open.getUnresolvedClaimedSelectedOwnerOutsideInstanceCount()
                == 1
            && open.getRelevantInactiveInstanceCount() == 1
            && open.getRelevantDuplicateIdentityInstanceCount() == 1
            && open.getHardBlockingConditionCount() == 4
            && open.getHardBlockingEvidenceCount() == 4,
            "non-expandable evidence remains four explicit hard blockers");
        check(!open.isSelectionMutated() && !open.isBoundaryClosureProved()
            && !open.isEntityRegistry() && !open.isArrivalGate()
            && !open.isLifecycleAuthority(),
            "projection remains inert and non-authoritative");
        expectImmutable(open.getRequirements());
        expectImmutable(owner.getReasons());
        expectFailure(() ->
            LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project(
                openObservation, 1));
        expectFailure(() ->
            LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project(
                openObservation, -1));
        expectFailure(() ->
            LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project(
                openObservation,
                LayeredPackedRegionActiveNpcBoundaryRequirementProjection
                    .MAXIMUM_REQUIREMENTS + 1));
        expectNullFailure(() ->
            LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project(
                null, 1));
    }

    private static int reason(
        SourceRequirement requirement,
        RequirementReason reason) {
        for (LayeredPackedRegionActiveNpcBoundaryRequirementProjection
                .ReasonCount count : requirement.getReasons()) {
            if (count.getReason() == reason) {
                return count.getInstanceCount();
            }
        }
        throw new AssertionError("Missing reason " + reason);
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

    private static void expectFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
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


class LayeredMapsSliceSeventyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-seventy-five-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "ActiveNpcBoundaryRequirementFixture.java"
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

    def test_projection_separates_expandable_sources_from_hard_blockers(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "ActiveNpcBoundaryRequirementFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_projection_is_detached_and_never_claims_closure(self):
        source = PROJECTION.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("import com.openrsc.server.model.world.region", source)
        self.assertNotIn("RegionManager", source)
        self.assertIn("never mutates a selection and never proves closure", source)
        self.assertIn("isFreshSafetyAssessmentRequired() { return true; }", source)
        self.assertIn("isFreshNpcCensusRequired() { return true; }", source)
        self.assertIn("isSelectionMutated() { return false; }", source)
        self.assertIn("isBoundaryClosureProved() { return false; }", source)
        self.assertIn("isEntityRegistry() { return false; }", source)
        self.assertIn("isArrivalGate() { return false; }", source)
        self.assertIn("isLifecycleAuthority() { return false; }", source)

    def test_living_plan_records_slice_seventy_five_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 75: Active NPC boundary requirement projection", plan
        )
        self.assertIn("fresh complete safety assessment", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
