#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
OBSERVATION = COORDINATES / (
    "LayeredPackedRegionActiveNpcResidencyObservation.java"
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
    .LayeredPackedRegionActiveNpcResidencyObservation
        .ActiveResidencyClassification;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionActiveNpcResidencyObservation.IdentityStatus;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionActiveNpcResidencyObservation.NpcInstanceSnapshot;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ActiveNpcResidencyFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifestBuilder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifestBuilder.recordNpcSpawn(
            4, 0, 10, 200, 20, 190, 210, 10, 30);
        LayeredAuthoredPlacementIdentity selectedIdentity =
            manifestBuilder.getLastRecordedIdentity();
        manifestBuilder.recordNpcSpawn(
            5, 0, 11, 250, 20, 240, 260, 10, 30);
        LayeredAuthoredPlacementIdentity externalIdentity =
            manifestBuilder.getLastRecordedIdentity();
        LayeredPackedRegionAuthoredPlacementManifest manifest =
            manifestBuilder.build();

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencies =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(9L);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 190, 210, 10, 30, 3, 4, 0, 0);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            5, 0, 240, 260, 10, 30, 5, 5, 0, 0);
        LayeredPackedRegionAuthoredPopulationOutcome outcome =
            LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                .build(manifest);
        LayeredPackedRegionAuthoredReconstructionRecipe recipe =
            LayeredPackedRegionAuthoredReconstructionRecipe.derive(
                manifest, dependencies.build(), outcome);

        LayeredAuthoredPlacementIdentity staleSelectedIdentity =
            new LayeredAuthoredPlacementIdentity(
                8L, 4, 0, selectedIdentity.getSourceOrdinal(),
                ConstructionKind.NPC_SPAWN);
        List<NpcInstanceSnapshot> census = new ArrayList<NpcInstanceSnapshot>();
        census.add(new NpcInstanceSnapshot(selectedIdentity, 10, 4, 0, true));
        census.add(new NpcInstanceSnapshot(selectedIdentity, 10, 5, 0, true));
        census.add(new NpcInstanceSnapshot(externalIdentity, 11, 4, 0, true));
        census.add(new NpcInstanceSnapshot(null, 20, 4, 0, true));
        census.add(new NpcInstanceSnapshot(
            staleSelectedIdentity, 10, 5, 0, true));
        census.add(new NpcInstanceSnapshot(selectedIdentity, 99, 4, 0, true));
        census.add(new NpcInstanceSnapshot(externalIdentity, 11, 5, 0, true));
        census.add(new NpcInstanceSnapshot(selectedIdentity, 10, 4, 0, false));
        census.add(new NpcInstanceSnapshot(null, 20, 5, 0, false));
        census.add(new NpcInstanceSnapshot(selectedIdentity, 10, 4, 0, true));

        LayeredPackedRegionActiveNpcResidencyObservation observation =
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety(4), 12L, census, 10, 7);
        check(observation.getGeneration() == 9L
            && observation.getSafetyObservedAtTick() == 8L
            && observation.getCensusObservedAtTick() == 12L
            && observation.getSelectedSourceCount() == 1,
            "observation retains recipe, safety, census, and selection identity");
        check(observation.getObservedInstanceCount() == 10
            && observation.getActiveInstanceCount() == 8
            && observation.getInactiveInstanceCount() == 2
            && observation.getActiveRecognizedInstanceCount() == 5
            && observation.getActiveUnrecognizedInstanceCount() == 3
            && observation.getUniqueActiveRecognizedIdentityCount() == 2
            && observation.getDuplicateActiveRecognizedIdentityInstanceCount()
                == 3,
            "whole-census active and identity arithmetic is exact");
        check(observation.getRelevantActiveInstanceCount() == 7
            && observation.getIrrelevantActiveInstanceCount() == 1
            && observation.getSelectedOwnerInsideCount() == 2
            && observation.getSelectedOwnerOutsideCount() == 1
            && observation.getExternalOwnerInsideCount() == 1
            && observation.getUnresolvedInsideCount() == 2
            && observation
                .getUnresolvedClaimedSelectedOwnerOutsideCount() == 1,
            "owner and current residency remain independent");
        check(observation.getInactiveRelevantInstanceCount() == 1
            && observation.getInactiveIrrelevantInstanceCount() == 1,
            "inactive census entries remain explicit but outside active detail");
        check(countClassification(
                observation, ActiveResidencyClassification.SELECTED_OWNER_INSIDE)
                == 2
            && countClassification(
                observation, ActiveResidencyClassification.SELECTED_OWNER_OUTSIDE)
                == 1
            && countClassification(
                observation, ActiveResidencyClassification.EXTERNAL_OWNER_INSIDE)
                == 1
            && countClassification(
                observation, ActiveResidencyClassification.UNRESOLVED_INSIDE)
                == 2
            && countClassification(
                observation,
                ActiveResidencyClassification
                    .UNRESOLVED_CLAIMED_SELECTED_OWNER_OUTSIDE) == 1,
            "relevant detail covers every active classification");
        check(statusCount(observation, IdentityStatus.RECOGNIZED) == 5
            && statusCount(
                observation, IdentityStatus.MISSING_AUTHORED_IDENTITY) == 1
            && statusCount(observation, IdentityStatus.STALE_GENERATION) == 1
            && statusCount(observation, IdentityStatus.NON_NPC_IDENTITY) == 0
            && statusCount(
                observation, IdentityStatus.UNKNOWN_RECIPE_IDENTITY) == 0
            && statusCount(observation, IdentityStatus.RUNTIME_ID_MISMATCH) == 1,
            "identity resolution never invents ownership");
        check(observation.isPointInTimeCensus()
            && observation.isActiveInstanceEvidence()
            && !observation.isEntityRegistry()
            && !observation.isArrivalGate()
            && !observation.isLifecycleAuthority(),
            "active evidence remains inert");
        expectImmutable(observation.getRelevantActiveInstances());
        expectImmutable(observation.getIdentityStatuses());
        expectIllegal(() ->
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety(4), 12L, census, 9, 7));
        expectIllegal(() ->
            LayeredPackedRegionActiveNpcResidencyObservation.observe(
                recipe, safety(4), 12L, census, 10, 6));
    }

    private static int countClassification(
        LayeredPackedRegionActiveNpcResidencyObservation observation,
        ActiveResidencyClassification classification) {
        int count = 0;
        for (LayeredPackedRegionActiveNpcResidencyObservation.InstanceEvidence
                evidence : observation.getRelevantActiveInstances()) {
            count += evidence.getClassification() == classification ? 1 : 0;
        }
        return count;
    }

    private static int statusCount(
        LayeredPackedRegionActiveNpcResidencyObservation observation,
        IdentityStatus status) {
        for (LayeredPackedRegionActiveNpcResidencyObservation.IdentityStatusCount
                entry : observation.getIdentityStatuses()) {
            if (entry.getStatus() == status) {
                return entry.getActiveInstanceCount();
            }
        }
        throw new AssertionError("Missing identity status");
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


class LayeredMapsSliceSeventyOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-seventy-one-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "ActiveNpcResidencyFixture.java"
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

    def test_active_owner_and_current_residency_remain_separate(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "ActiveNpcResidencyFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_observation_is_bounded_detached_evidence_only(self):
        source = OBSERVATION.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("import com.openrsc.server.model.world.region", source)
        self.assertNotIn("RegionManager", source)
        self.assertIn("maximumInstances", source)
        self.assertIn("maximumRelevantDetails", source)
        self.assertIn("point-in-time NPC census", source)
        self.assertIn("isEntityRegistry() { return false; }", source)
        self.assertIn("isArrivalGate() { return false; }", source)
        self.assertIn("isLifecycleAuthority() { return false; }", source)

    def test_living_plan_records_slice_seventy_one_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 71: Active NPC residency classification", plan
        )
        self.assertIn("point-in-time census", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
