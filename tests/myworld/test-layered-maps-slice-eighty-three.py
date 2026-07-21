#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
ASSESSMENT = COORDINATES / (
    "LayeredPackedRegionPreservationBurdenAssessment.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
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
    .LayeredPackedRegionPreservationBurdenAssessment.Blocker;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionPreservationBurdenAssessment.BurdenFamily;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionPreservationBurdenAssessment.EvidenceCompleteness;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionPreservationBurdenAssessment.FamilyAssessment;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionPreservationBurdenAssessment.FamilyEvidence;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionPreservationBurdenAssessment.PackedSourceInventory;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionPreservationBurdenAssessment.SourceAssessment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PreservationBurdenAssessmentFixture {
    public static void main(String[] args) {
        inventoryKeepsFivePoliciesDistinct();
        emptyCompleteFamiliesAreClearOnlyAtTheObservation();
        invalidAndMisalignedEvidenceRefuses();
    }

    private static void inventoryKeepsFivePoliciesDistinct() {
        LayeredPackedRegionRetirementSafetyAssessment safety = safety();
        LayeredPackedRegionPreservationBurdenAssessment assessment =
            LayeredPackedRegionPreservationBurdenAssessment.assess(
                safety, Arrays.asList(occupied(), empty()), 31L, 2);

        check(assessment.getObservedAtTick() == 31L
            && assessment.getSafetyObservedAtTick() == 30L
            && !assessment.hasRetirementReadinessEvidence()
            && assessment.getSourceCount() == 2
            && assessment.getBurdenSatisfiedSourceCount() == 1
            && assessment.getBlockedSourceCount() == 1,
            "assessment retains exact safety identity and source arithmetic");

        SourceAssessment source = assessment.getSources().get(0);
        check(source.getPackedRegionX() == 4
            && source.getPackedRegionY() == 0
            && !source.isSafetyContentQuiescent()
            && !source.isSafetyLifecycleReady()
            && source.getBlockedFamilyCount() == 4
            && !source.isBurdenSatisfiedAtObservation(),
            "occupied source retains four distinct family blockers");
        for (int index = 0; index < BurdenFamily.values().length; index++) {
            check(source.getFamilies().get(index).getFamily()
                == BurdenFamily.values()[index],
                "family output remains in stable enum order");
        }

        check(blockers(source, BurdenFamily.PLAYER_SESSION).equals(
                Collections.singletonList(Blocker.ACTIVE_PLAYERS_PRESENT)),
            "players hard-block instead of becoming Region-owned state");
        check(blockers(source, BurdenFamily.DYNAMIC_OBJECT).equals(
                Arrays.asList(
                    Blocker.EVIDENCE_PARTIAL,
                    Blocker.PRESERVATION_PATH_UNAVAILABLE,
                    Blocker.RELOAD_PATH_UNAVAILABLE)),
            "partial dynamic objects retain evidence and state-path blockers");
        check(blockers(source, BurdenFamily.GROUND_ITEM).equals(
                Collections.singletonList(Blocker.RELOAD_PATH_UNAVAILABLE)),
            "preserved ground items still require a restoration path");
        FamilyAssessment collision = source.getFamilyAssessment(
            BurdenFamily.COLLISION_PRODUCT);
        check(collision.isBurdenSatisfiedAtObservation()
            && !collision.isPreservationSupported()
            && collision.isReloadSupported(),
            "derived collision can be rebuilt without serializing its product");
        check(blockers(source, BurdenFamily.OWNED_EVENT).equals(
                Collections.singletonList(Blocker.EVIDENCE_UNAVAILABLE)),
            "unknown event ownership never masquerades as zero events");

        check(assessment.getFamilySummary(BurdenFamily.PLAYER_SESSION)
                .getCompleteSourceCount() == 2
            && assessment.getFamilySummary(BurdenFamily.PLAYER_SESSION)
                .getKnownObservedInstanceCount() == 1L
            && assessment.getFamilySummary(BurdenFamily.DYNAMIC_OBJECT)
                .getPartialSourceCount() == 1
            && assessment.getFamilySummary(BurdenFamily.DYNAMIC_OBJECT)
                .getKnownObservedInstanceCount() == 2L
            && assessment.getFamilySummary(BurdenFamily.OWNED_EVENT)
                .getUnavailableSourceCount() == 1
            && assessment.getFamilySummary(BurdenFamily.OWNED_EVENT)
                .getKnownObservedInstanceCount() == 0L,
            "family summaries separate known counts from unknown evidence");

        check(assessment.isPointInTimeOnly()
            && !assessment.isCandidateSelectionMutated()
            && !assessment.isPreservationPerformed()
            && !assessment.isReloadRequest()
            && !assessment.isEntityRegistry()
            && !assessment.isArrivalGate()
            && !assessment.isTeardownTransaction()
            && !assessment.isLifecycleAuthority(),
            "burden inventory grants no runtime authority");
        expectImmutable(assessment.getSources());
        expectImmutable(assessment.getFamilySummaries());
        expectImmutable(source.getFamilies());
        expectImmutable(blockers(source, BurdenFamily.DYNAMIC_OBJECT));
    }

    private static void emptyCompleteFamiliesAreClearOnlyAtTheObservation() {
        LayeredPackedRegionPreservationBurdenAssessment assessment =
            LayeredPackedRegionPreservationBurdenAssessment.assess(
                safety(), Arrays.asList(occupied(), empty()), 31L, 2);
        SourceAssessment source = assessment.getSources().get(1);
        check(source.isSafetyContentQuiescent()
            && !source.isSafetyLifecycleReady()
            && source.getBlockedFamilyCount() == 0
            && source.isBurdenSatisfiedAtObservation(),
            "complete empty families have no observed preservation burden");
        for (FamilyAssessment family : source.getFamilies()) {
            check(family.getEvidenceCompleteness()
                    == EvidenceCompleteness.COMPLETE
                && family.getObservedInstanceCount() == 0
                && family.getBlockers().isEmpty(),
                "empty family stays explicitly complete and immutable");
        }
        check(assessment.isPointInTimeOnly()
            && !assessment.isArrivalGate()
            && !assessment.isLifecycleAuthority(),
            "an empty observation is neither durable nor authoritative");
    }

    private static void invalidAndMisalignedEvidenceRefuses() {
        LayeredPackedRegionRetirementSafetyAssessment safety = safety();
        List<PackedSourceInventory> valid = Arrays.asList(occupied(), empty());
        expectNull(() -> LayeredPackedRegionPreservationBurdenAssessment
            .assess(null, valid, 31L, 2));
        expectNull(() -> LayeredPackedRegionPreservationBurdenAssessment
            .assess(safety, null, 31L, 2));
        expectNull(() -> LayeredPackedRegionPreservationBurdenAssessment
            .assess(safety, Arrays.asList(occupied(), null), 31L, 2));
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .assess(safety, valid, 29L, 2));
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .assess(safety, valid, 31L, 1));
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .assess(safety, Collections.singletonList(occupied()), 31L, 2));
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .assess(safety, Arrays.asList(inventory(9, 0, completeZeroes()),
                empty()), 31L, 2));

        List<FamilyEvidence> duplicate = completeZeroes();
        duplicate.set(4, complete(BurdenFamily.PLAYER_SESSION, 0, false, false));
        expectIllegal(() -> inventory(4, 0, duplicate));
        expectIllegal(() -> inventory(4, 0,
            completeZeroes().subList(0, 4)));
        expectIllegal(() -> FamilyEvidence.of(
            BurdenFamily.OWNED_EVENT, EvidenceCompleteness.UNAVAILABLE,
            0, false, false));
        expectIllegal(() -> FamilyEvidence.of(
            BurdenFamily.OWNED_EVENT, EvidenceCompleteness.COMPLETE,
            -1, false, false));

        List<FamilyEvidence> wrongPlayer = occupiedFamilies();
        wrongPlayer.set(0, complete(
            BurdenFamily.PLAYER_SESSION, 0, false, false));
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .assess(safety, Arrays.asList(inventory(4, 0, wrongPlayer),
                empty()), 31L, 2));
        List<FamilyEvidence> tooManyDynamic = occupiedFamilies();
        tooManyDynamic.set(1, partial(
            BurdenFamily.DYNAMIC_OBJECT, 4, false, false));
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .assess(safety, Arrays.asList(inventory(4, 0, tooManyDynamic),
                empty()), 31L, 2));
        List<FamilyEvidence> wrongGroundItems = occupiedFamilies();
        wrongGroundItems.set(2, complete(
            BurdenFamily.GROUND_ITEM, 1, true, false));
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .assess(safety, Arrays.asList(inventory(4, 0, wrongGroundItems),
                empty()), 31L, 2));
    }

    private static LayeredPackedRegionRetirementSafetyAssessment safety() {
        return LayeredPackedRegionRetirementSafetyAssessment
            .assessDiagnosticSelection(Arrays.asList(
                LayeredPackedRegionRetirementSafetyAssessment
                    .PackedSourceContents.of(
                        4, 0, true, true, false, 1, 0, 3, 2),
                LayeredPackedRegionRetirementSafetyAssessment
                    .PackedSourceContents.of(
                        5, 0, true, true, false, 0, 0, 0, 0)),
                30L, 2);
    }

    private static PackedSourceInventory occupied() {
        // Deliberately shuffled: the immutable result must canonicalize order.
        return inventory(4, 0, Arrays.asList(
            unavailable(BurdenFamily.OWNED_EVENT),
            complete(BurdenFamily.COLLISION_PRODUCT, 4, false, true),
            complete(BurdenFamily.GROUND_ITEM, 2, true, false),
            partial(BurdenFamily.DYNAMIC_OBJECT, 2, false, false),
            complete(BurdenFamily.PLAYER_SESSION, 1, false, false)));
    }

    private static List<FamilyEvidence> occupiedFamilies() {
        return new ArrayList<FamilyEvidence>(occupied().getFamilies());
    }

    private static PackedSourceInventory empty() {
        return inventory(5, 0, completeZeroes());
    }

    private static List<FamilyEvidence> completeZeroes() {
        List<FamilyEvidence> families = new ArrayList<FamilyEvidence>();
        for (BurdenFamily family : BurdenFamily.values()) {
            families.add(complete(family, 0, false, false));
        }
        return families;
    }

    private static PackedSourceInventory inventory(
            int x, int y, List<FamilyEvidence> families) {
        return PackedSourceInventory.of(x, y, families);
    }

    private static FamilyEvidence complete(
            BurdenFamily family, int count, boolean preserve, boolean reload) {
        return FamilyEvidence.of(
            family, EvidenceCompleteness.COMPLETE, count, preserve, reload);
    }

    private static FamilyEvidence partial(
            BurdenFamily family, int count, boolean preserve, boolean reload) {
        return FamilyEvidence.of(
            family, EvidenceCompleteness.PARTIAL, count, preserve, reload);
    }

    private static FamilyEvidence unavailable(BurdenFamily family) {
        return FamilyEvidence.of(
            family, EvidenceCompleteness.UNAVAILABLE, -1, false, false);
    }

    private static List<Blocker> blockers(
            SourceAssessment source, BurdenFamily family) {
        return source.getFamilyAssessment(family).getBlockers();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void expectImmutable(List values) {
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


class LayeredMapsSliceEightyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-eighty-three-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "PreservationBurdenAssessmentFixture.java"
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

    def test_five_family_burden_contract_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "PreservationBurdenAssessmentFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_assessment_remains_handle_free_and_non_authoritative(self):
        source = ASSESSMENT.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            "LayeredPackedRegionPreservationBurdenAssessment", manager
        )
        self.assertNotIn(
            "LayeredPackedRegionPreservationBurdenAssessment", observer
        )
        self.assertNotIn(
            "LayeredPackedRegionPreservationBurdenAssessment", path_validation
        )
        for forbidden in (
            "com.openrsc.server.model.world.region.Region",
            "com.openrsc.server.model.entity",
            "com.openrsc.server.event",
        ):
            self.assertNotIn(forbidden, source)
        for boundary in (
            "isPreservationPerformed() { return false; }",
            "isReloadRequest() { return false; }",
            "isEntityRegistry() { return false; }",
            "isArrivalGate() { return false; }",
            "isTeardownTransaction() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(boundary, source)

    def test_living_plan_records_slice_eighty_three_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 83: Runtime preservation and reload burden contract",
            plan,
        )
        self.assertIn("PLAYER_SESSION", plan)
        self.assertIn("OWNED_EVENT", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
