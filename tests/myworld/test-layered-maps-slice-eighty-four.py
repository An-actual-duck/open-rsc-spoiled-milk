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
REGION = ROOT / "server/src/com/openrsc/server/model/world/region/Region.java"
TILE_VALUE = ROOT / (
    "server/src/com/openrsc/server/model/world/region/TileValue.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
COMMAND = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/"
    "Development.java"
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
    .LayeredPackedRegionPreservationBurdenAssessment.PackedSourceInventory;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionPreservationBurdenAssessment.SourceAssessment;
import java.util.Arrays;
import java.util.Collections;

public final class RuntimePreservationBurdenFixture {
    public static void main(String[] args) {
        currentRuntimeEvidenceIsConservative();
        absentSourceDoesNotManufactureCollisionOrEvents();
        invalidCountsRefuse();
    }

    private static void currentRuntimeEvidenceIsConservative() {
        LayeredPackedRegionRetirementSafetyAssessment safety =
            LayeredPackedRegionRetirementSafetyAssessment
                .assessDiagnosticSelection(Collections.singletonList(
                    LayeredPackedRegionRetirementSafetyAssessment
                        .PackedSourceContents.of(
                            4, 0, true, true, false, 1, 2, 5, 2)),
                    40L, 1);
        PackedSourceInventory inventory =
            LayeredPackedRegionPreservationBurdenAssessment
                .currentRuntimeInventory(4, 0, 1, 3, 2, 7);
        LayeredPackedRegionPreservationBurdenAssessment assessment =
            LayeredPackedRegionPreservationBurdenAssessment.assess(
                safety, Collections.singletonList(inventory), 40L, 1);
        SourceAssessment source = assessment.getSources().get(0);

        check(source.getBlockedFamilyCount() == 5
            && assessment.getBlockedSourceCount() == 1,
            "every current occupied family remains blocked");
        check(source.getFamilyAssessment(BurdenFamily.PLAYER_SESSION)
                .getEvidenceCompleteness() == EvidenceCompleteness.COMPLETE
            && source.getFamilyAssessment(BurdenFamily.PLAYER_SESSION)
                .getObservedInstanceCount() == 1
            && source.getFamilyAssessment(BurdenFamily.PLAYER_SESSION)
                .getBlockers().equals(Collections.singletonList(
                    Blocker.ACTIVE_PLAYERS_PRESENT)),
            "Region-local Player count is exact but hard-blocking");
        check(source.getFamilyAssessment(BurdenFamily.DYNAMIC_OBJECT)
                .getEvidenceCompleteness() == EvidenceCompleteness.COMPLETE
            && source.getFamilyAssessment(BurdenFamily.DYNAMIC_OBJECT)
                .getObservedInstanceCount() == 3
            && source.getFamilyAssessment(BurdenFamily.DYNAMIC_OBJECT)
                .getBlockers().equals(Arrays.asList(
                    Blocker.PRESERVATION_PATH_UNAVAILABLE,
                    Blocker.RELOAD_PATH_UNAVAILABLE)),
            "active identity-less objects are exact but not recoverable");
        check(source.getFamilyAssessment(BurdenFamily.GROUND_ITEM)
                .getEvidenceCompleteness() == EvidenceCompleteness.PARTIAL
            && source.getFamilyAssessment(BurdenFamily.GROUND_ITEM)
                .getObservedInstanceCount() == 2
            && source.getFamilyAssessment(BurdenFamily.GROUND_ITEM)
                .getBlockers().equals(Arrays.asList(
                    Blocker.EVIDENCE_PARTIAL,
                    Blocker.PRESERVATION_PATH_UNAVAILABLE,
                    Blocker.RELOAD_PATH_UNAVAILABLE)),
            "active ground items cannot hide absent authored respawn state");
        check(source.getFamilyAssessment(BurdenFamily.COLLISION_PRODUCT)
                .getEvidenceCompleteness() == EvidenceCompleteness.PARTIAL
            && source.getFamilyAssessment(BurdenFamily.COLLISION_PRODUCT)
                .getObservedInstanceCount() == 7
            && source.getFamilyAssessment(BurdenFamily.COLLISION_PRODUCT)
                .getBlockers().equals(Arrays.asList(
                    Blocker.EVIDENCE_PARTIAL,
                    Blocker.RELOAD_PATH_UNAVAILABLE)),
            "collision-product tiles require an attributed rebuild");
        check(source.getFamilyAssessment(BurdenFamily.OWNED_EVENT)
                .getEvidenceCompleteness() == EvidenceCompleteness.UNAVAILABLE
            && source.getFamilyAssessment(BurdenFamily.OWNED_EVENT)
                .getObservedInstanceCount() == -1
            && source.getFamilyAssessment(BurdenFamily.OWNED_EVENT)
                .getBlockers().equals(Collections.singletonList(
                    Blocker.EVIDENCE_UNAVAILABLE)),
            "global events remain honestly unavailable");
        check(!assessment.isPreservationPerformed()
            && !assessment.isReloadRequest()
            && !assessment.isLifecycleAuthority(),
            "runtime observation grants no state authority");
    }

    private static void absentSourceDoesNotManufactureCollisionOrEvents() {
        LayeredPackedRegionRetirementSafetyAssessment safety =
            LayeredPackedRegionRetirementSafetyAssessment
                .assessDiagnosticSelection(Collections.singletonList(
                    LayeredPackedRegionRetirementSafetyAssessment
                        .PackedSourceContents.of(
                            5, 0, false, false, false, 0, 0, 0, 0)),
                    41L, 1);
        PackedSourceInventory inventory =
            LayeredPackedRegionPreservationBurdenAssessment
                .currentRuntimeInventory(5, 0, 0, 0, 0, -1);
        LayeredPackedRegionPreservationBurdenAssessment assessment =
            LayeredPackedRegionPreservationBurdenAssessment.assess(
                safety, Collections.singletonList(inventory), 41L, 1);
        SourceAssessment source = assessment.getSources().get(0);
        check(source.getFamilyAssessment(BurdenFamily.PLAYER_SESSION)
                .isBurdenSatisfiedAtObservation()
            && source.getFamilyAssessment(BurdenFamily.DYNAMIC_OBJECT)
                .isBurdenSatisfiedAtObservation(),
            "absent Region has exact zero local Players and objects");
        check(source.getFamilyAssessment(BurdenFamily.GROUND_ITEM)
                .getBlockers().equals(Collections.singletonList(
                    Blocker.EVIDENCE_PARTIAL))
            && source.getFamilyAssessment(BurdenFamily.COLLISION_PRODUCT)
                .getBlockers().equals(Collections.singletonList(
                    Blocker.EVIDENCE_UNAVAILABLE))
            && source.getFamilyAssessment(BurdenFamily.OWNED_EVENT)
                .getBlockers().equals(Collections.singletonList(
                    Blocker.EVIDENCE_UNAVAILABLE)),
            "absent storage retains external ground/event uncertainty");
    }

    private static void invalidCountsRefuse() {
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .currentRuntimeInventory(4, 0, -1, 0, 0, 0));
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .currentRuntimeInventory(4, 0, 0, -1, 0, 0));
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .currentRuntimeInventory(4, 0, 0, 0, -1, 0));
        expectIllegal(() -> LayeredPackedRegionPreservationBurdenAssessment
            .currentRuntimeInventory(4, 0, 0, 0, 0, -2));
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


class LayeredMapsSliceEightyFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-eighty-four-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "RuntimePreservationBurdenFixture.java"
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

    def test_current_runtime_inventory_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "RuntimePreservationBurdenFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_uses_one_noncreating_region_snapshot_per_source(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        region = REGION.read_text(encoding="utf-8")
        tile = TILE_VALUE.read_text(encoding="utf-8")
        start = manager.index("assessLayeredPackedRegionPreservationBurden(")
        end = manager.index(
            "/**\n\t * Captures one strictly newer", start
        )
        boundary = manager[start:end]
        self.assertIn("synchronized (layeredRegionLifecycleLock)", boundary)
        self.assertIn("peekRegionFromSectorCoordinates(", boundary)
        self.assertEqual(1, boundary.count(
            "region.captureRetirementContentsSnapshot()"
        ))
        self.assertIn("currentRuntimeInventory(", boundary)
        self.assertIn("assessDiagnosticSelection(", boundary)
        for forbidden in (
            "getRegion(",
            ".unload(",
            "registerGameObject",
            "unregisterGameObject",
            "registerItem",
            "unregisterItem",
            "regions.remove",
        ):
            self.assertNotIn(forbidden, boundary)
        self.assertIn("object.getAuthoredPlacementIdentity() == null", region)
        self.assertIn("countCollisionProductTiles()", region)
        self.assertIn("hasCollisionProductState()", tile)

    def test_capture_is_attached_only_to_private_diagnostics(self):
        name = "LayeredPackedRegionPreservationBurdenAssessment"
        method = "assessLayeredPackedRegionPreservationBurden("
        self.assertIn(name, OBSERVER.read_text(encoding="utf-8"))
        self.assertIn(method, PLAYER.read_text(encoding="utf-8"))
        self.assertIn(method, COMMAND.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_eighty_four_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 84: Bounded runtime preservation burden capture",
            plan,
        )
        self.assertIn("collision-product tile count", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
