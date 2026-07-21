#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
RECORD = COORDINATES / (
    "LayeredPackedRegionDynamicObjectPreservationRecord.java"
)
REGION = ROOT / "server/src/com/openrsc/server/model/world/region/Region.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
ENTITY = ROOT / "server/src/com/openrsc/server/model/entity/Entity.java"
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
    .LayeredPackedRegionDynamicObjectPreservationRecord.DynamicObjectRecord;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionDynamicObjectPreservationRecord.DynamicObjectState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionDynamicObjectPreservationRecord.PackedSourceCapture;
import java.util.Arrays;
import java.util.Collections;

public final class DynamicObjectPreservationFixture {
    public static void main(String[] args) {
        constructorStateIsDetachedAndDeterministic();
        invalidOrUnboundedInputsRefuse();
    }

    private static void constructorStateIsDetachedAndDeterministic() {
        DynamicObjectState later = DynamicObjectState.of(
            64, 63, 121, 509, 4, 0, "devduck", 0);
        DynamicObjectState earlier = DynamicObjectState.of(
            1, 1, 120, 508, 0, 0, null, 2);
        LayeredPackedRegionDynamicObjectPreservationRecord record =
            LayeredPackedRegionDynamicObjectPreservationRecord.record(
                7L, 99L,
                Arrays.asList(
                    PackedSourceCapture.of(
                        2, 10, true, Arrays.asList(later, earlier)),
                    PackedSourceCapture.of(
                        3, 10, false, Collections.emptyList())),
                2, 2);

        check(record.getProposalGeneration() == 7L
            && record.getObservedAtTick() == 99L
            && record.getSourceCount() == 2
            && record.getDynamicObjectCount() == 2,
            "bounded record retains proposal and observation identity");
        check(record.getConstructorStateCompleteObjectCount() == 2
            && record.getStandaloneRestorationCompleteObjectCount() == 0
            && record.getObjectsWithRuntimeAttributesCount() == 1,
            "constructor state is distinct from standalone restoration");
        DynamicObjectRecord first = record.getSources().get(0)
            .getDynamicObjects().get(0);
        DynamicObjectRecord second = record.getSources().get(0)
            .getDynamicObjects().get(1);
        check(first.getSourceOrdinal() == 0 && first.getObjectId() == 1
            && first.getX() == 120 && first.getY() == 508
            && first.getRuntimeAttributeCount() == 2 && !first.hasOwner(),
            "object order is deterministic and attributes remain counted");
        check(second.getSourceOrdinal() == 1 && second.getObjectId() == 64
            && second.getPermanentObjectId() == 63
            && second.getDirection() == 4 && second.getType() == 0
            && second.hasOwner() && "devduck".equals(second.getOwner()),
            "every current constructor input is detached");
        check(record.isPointInTimeOnly() && record.isDetachedPrimitiveCopy()
            && !record.isRuntimeAttributesCaptured()
            && !record.isEventOwnershipCaptured()
            && !record.isPreservationPerformed() && !record.isReloadRequest()
            && !record.isEntityRegistry() && !record.isArrivalGate()
            && !record.isTeardownTransaction() && !record.isLifecycleAuthority(),
            "record grants no preservation or lifecycle authority");
    }

    private static void invalidOrUnboundedInputsRefuse() {
        DynamicObjectState state = DynamicObjectState.of(
            1, 1, 120, 508, 0, 0, null, 0);
        expectIllegal(() -> PackedSourceCapture.of(
            2, 10, false, Collections.singletonList(state)));
        expectIllegal(() -> LayeredPackedRegionDynamicObjectPreservationRecord
            .record(1L, 1L, Arrays.asList(
                PackedSourceCapture.of(3, 10, true, Collections.emptyList()),
                PackedSourceCapture.of(2, 10, true, Collections.emptyList())),
                2, 0));
        expectIllegal(() -> LayeredPackedRegionDynamicObjectPreservationRecord
            .record(1L, 1L, Collections.singletonList(
                PackedSourceCapture.of(
                    2, 10, true, Collections.singletonList(state))),
                1, 0));
        expectIllegal(() -> DynamicObjectState.of(
            1, 1, 120, 508, 8, 0, null, 0));
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
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceEightySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-eighty-six-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "DynamicObjectPreservationFixture.java"
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

    def test_detached_record_contract_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "DynamicObjectPreservationFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_capture_detaches_state_without_exposing_attributes(self):
        region = REGION.read_text(encoding="utf-8")
        entity = ENTITY.read_text(encoding="utf-8")
        self.assertIn("new DynamicObjectSnapshot(object)", region)
        self.assertIn("object.getAuthoredPlacementIdentity() == null", region)
        self.assertIn("DynamicObjectSnapshot.ORDER", region)
        self.assertIn("object.getRuntimeAttributeCount()", region)
        self.assertIn("public final int getRuntimeAttributeCount()", entity)
        self.assertNotIn("getRuntimeAttributes", entity)

    def test_manager_uses_noncreating_bounded_proposal_order(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        start = manager.index(
            "captureLayeredPackedRegionDynamicObjectPreservationRecord("
        )
        end = manager.index(
            "/**\n\t * Captures one strictly newer", start
        )
        boundary = manager[start:end]
        self.assertIn("checked.getCandidates()", boundary)
        self.assertIn("peekRegionFromSectorCoordinates", boundary)
        self.assertIn("captureRetirementContentsSnapshot()", boundary)
        self.assertIn("maximumDynamicObjects", boundary)
        for forbidden in (
            "getRegion(", "registerGameObject", "unregisterGameObject",
            "replaceGameObject", ".remove()", "setLocation(",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_living_plan_records_slice_eighty_six_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 86: Detached dynamic-object preservation record", plan
        )
        self.assertIn("constructor state", plan)
        self.assertIn("opaque runtime attributes", plan)
        self.assertIn("event ownership", plan)
        self.assertIn("No object is unregistered", plan)


if __name__ == "__main__":
    unittest.main()
