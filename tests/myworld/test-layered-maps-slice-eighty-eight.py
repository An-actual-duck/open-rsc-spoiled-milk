#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
INVENTORY = COORDINATES / (
    "LayeredPackedRegionEventOwnershipInventory.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
EVENT_HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
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
    .LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.PackedSource;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SpatialReference;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SpatialRole;
import java.util.Arrays;
import java.util.Collections;

public final class EventOwnershipInventoryFixture {
    public static void main(String[] args) {
        affinityKindsRemainDistinctAndBounded();
        invalidOrAmbiguousInputsRefuse();
    }

    private static void affinityKindsRemainDistinctAndBounded() {
        LayeredPackedRegionEventOwnershipInventory inventory =
            LayeredPackedRegionEventOwnershipInventory.inventory(
                7L, 99L,
                Arrays.asList(PackedSource.of(2, 10), PackedSource.of(3, 10)),
                Arrays.asList(
                    EventState.of(0, OwnerKind.NONE,
                        AttributionKind.EXACT_SPATIAL, true, 4L, 0,
                        Collections.singletonList(SpatialReference.of(
                            SpatialRole.FIXED_EFFECT_LOCATION, 100, 500))),
                    EventState.of(1, OwnerKind.NPC,
                        AttributionKind.EXACT_SPATIAL, true, 0L, 3,
                        Arrays.asList(
                            SpatialReference.of(
                                SpatialRole.SUBJECT_CURRENT_POSITION, 101, 500),
                            SpatialReference.of(
                                SpatialRole.TARGET_CURRENT_POSITION, 150, 500))),
                    EventState.of(2, OwnerKind.PLAYER,
                        AttributionKind.OWNER_POSITION_HINT, true, -1L, 8,
                        Collections.singletonList(SpatialReference.of(
                            SpatialRole.OWNER_CURRENT_POSITION, 145, 500))),
                    EventState.of(3, OwnerKind.NONE,
                        AttributionKind.NON_SPATIAL_GLOBAL, true, 20L, 0,
                        Collections.emptyList()),
                    EventState.of(4, OwnerKind.NONE,
                        AttributionKind.UNATTRIBUTED, false, 0L, 1,
                        Collections.emptyList())),
                2, 5, 4);

        check(inventory.getProposalGeneration() == 7L
            && inventory.getObservedAtTick() == 99L
            && inventory.getSourceCount() == 2
            && inventory.getEventCount() == 5
            && inventory.getSpatialReferenceCount() == 4,
            "proposal and bounded snapshot identity are retained");
        check(inventory.getExactSpatialEventCount() == 2
            && inventory.getOwnerPositionHintEventCount() == 1
            && inventory.getNonSpatialGlobalEventCount() == 1
            && inventory.getUnattributedEventCount() == 1
            && inventory.getCandidateRelatedEventCount() == 3,
            "affinity classes remain distinct");
        check(inventory.getSources().get(0).getExactSpatialEventOrdinals()
                .equals(Arrays.asList(0, 1))
            && inventory.getSources().get(0)
                .getOwnerPositionHintEventCount() == 0
            && inventory.getSources().get(0).getUnattributedEventCount() == 1,
            "first source receives exact events but global uncertainty");
        check(inventory.getSources().get(1).getExactSpatialEventOrdinals()
                .equals(Collections.singletonList(1))
            && inventory.getSources().get(1)
                .getOwnerPositionHintEventOrdinals()
                .equals(Collections.singletonList(2))
            && !inventory.getSources().get(1).isAttributionComplete(),
            "multi-source effects and owner hints remain visible");
        check(inventory.getEvents().get(3).isAttributionComplete()
            && !inventory.getEvents().get(4).isAttributionComplete()
            && !inventory.isCandidateAttributionComplete()
            && inventory.getRestorationStateCompleteEventCount() == 0,
            "non-spatial declaration is not confused with unknown callback state");
        check(inventory.isPointInTimeOnly()
            && inventory.isDetachedPrimitiveCopy()
            && !inventory.isCallbackStateCaptured()
            && !inventory.isSchedulerIdentityCaptured()
            && !inventory.isPreservationPerformed()
            && !inventory.isReloadRequest()
            && !inventory.isEventCancellation()
            && !inventory.isEventReschedule()
            && !inventory.isEntityRegistry()
            && !inventory.isArrivalGate()
            && !inventory.isTeardownTransaction()
            && !inventory.isLifecycleAuthority(),
            "inventory grants no scheduler or lifecycle authority");
    }

    private static void invalidOrAmbiguousInputsRefuse() {
        PackedSource source = PackedSource.of(2, 10);
        SpatialReference fixed = SpatialReference.of(
            SpatialRole.FIXED_EFFECT_LOCATION, 100, 500);
        expectIllegal(() -> EventState.of(0, OwnerKind.NONE,
            AttributionKind.EXACT_SPATIAL, true, 0L, 0,
            Collections.emptyList()));
        expectIllegal(() -> EventState.of(0, OwnerKind.NONE,
            AttributionKind.OWNER_POSITION_HINT, true, 0L, 0,
            Collections.singletonList(fixed)));
        expectIllegal(() -> EventState.of(0, OwnerKind.NONE,
            AttributionKind.NON_SPATIAL_GLOBAL, true, 0L, 0,
            Collections.singletonList(fixed)));
        expectIllegal(() -> LayeredPackedRegionEventOwnershipInventory
            .inventory(1L, 1L,
                Arrays.asList(PackedSource.of(3, 10), source),
                Collections.emptyList(), 2, 0, 0));
        expectIllegal(() -> LayeredPackedRegionEventOwnershipInventory
            .inventory(1L, 1L, Collections.singletonList(source),
                Collections.singletonList(EventState.of(1, OwnerKind.NONE,
                    AttributionKind.UNATTRIBUTED, true, 0L, 0,
                    Collections.emptyList())), 1, 1, 0));
        expectIllegal(() -> LayeredPackedRegionEventOwnershipInventory
            .inventory(1L, 1L, Collections.singletonList(source),
                Collections.singletonList(EventState.of(0, OwnerKind.NONE,
                    AttributionKind.EXACT_SPATIAL, true, 0L, 0,
                    Collections.singletonList(fixed))), 1, 1, 0));
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


class LayeredMapsSliceEightyEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-eighty-eight-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "EventOwnershipInventoryFixture.java"
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

    def test_detached_inventory_contract_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "EventOwnershipInventoryFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_inventory_is_dormant_and_has_no_runtime_handles(self):
        source = INVENTORY.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.event",
            "import com.openrsc.server.model.entity",
            "import com.openrsc.server.model.world.region",
            "GameTickEvent ",
            "Region ",
            "event.stop()",
            "eventStore.remove",
            "eventStore.add",
        ):
            self.assertNotIn(forbidden, source)
        self.assertNotIn(
            "LayeredPackedRegionEventOwnershipInventory",
            REGION_MANAGER.read_text(encoding="utf-8"),
        )
        self.assertNotIn(
            "LayeredPackedRegionEventOwnershipInventory",
            EVENT_HANDLER.read_text(encoding="utf-8"),
        )

    def test_living_plan_records_slice_eighty_eight_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 88: Dormant event-ownership inventory contract", plan
        )
        self.assertIn("OWNER_POSITION_HINT", plan)
        self.assertIn("null-owned callbacks", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
