#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
INVENTORY = COORDINATES / "LayeredPackedRegionEventOwnershipInventory.java"
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
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
import java.util.Arrays;
import java.util.Collections;

public final class EventRegistrationInventoryFixture {
    public static void main(String[] args) {
        registrationIdentityIsDistinctFromSnapshotOrder();
        invalidRegistrationIdentityRefuses();
    }

    private static void registrationIdentityIsDistinctFromSnapshotOrder() {
        LayeredPackedRegionEventOwnershipInventory inventory =
            LayeredPackedRegionEventOwnershipInventory.inventory(
                3L, 90L, "00000000-0000-0000-0000-000000000096",
                Collections.singletonList(PackedSource.of(4, 10)),
                Arrays.asList(
                    EventState.of(
                        0, 41L, OwnerKind.NONE,
                        AttributionKind.UNATTRIBUTED, true, 8L, 0,
                        Collections.emptyList()),
                    EventState.of(
                        1, 57L, OwnerKind.NONE,
                        AttributionKind.NON_SPATIAL_GLOBAL, true, 2L, 3,
                        Collections.emptyList())),
                1, 2, 0);
        check(inventory.getRegistrationIdentityCapturedEventCount() == 2
            && inventory.isRegistrationIdentityCaptured()
            && inventory.isRegistrationIdentityComplete()
            && inventory.isSchedulerInstanceIdentityCaptured()
            && inventory.getSchedulerInstanceIdentity().equals(
                "00000000-0000-0000-0000-000000000096")
            && !inventory.isSchedulerIdentityCaptured(),
            "registration evidence carries detached scheduler-instance scope");
        check(inventory.getEvents().get(0).getSnapshotOrdinal() == 0
            && inventory.getEvents().get(0).getRegistrationSequence() == 41L
            && inventory.getEvents().get(1).getSnapshotOrdinal() == 1
            && inventory.getEvents().get(1).getRegistrationSequence() == 57L,
            "snapshot order and registration identity remain distinct");
    }

    private static void invalidRegistrationIdentityRefuses() {
        expectIllegal(() -> EventState.of(
            0, 0L, OwnerKind.NONE, AttributionKind.UNATTRIBUTED,
            true, 1L, 0, Collections.emptyList()));
        expectIllegal(() -> LayeredPackedRegionEventOwnershipInventory
            .inventory(
                1L, 1L, "00000000-0000-0000-0000-000000000096",
                Collections.emptyList(),
                Arrays.asList(
                    EventState.of(
                        0, 8L, OwnerKind.NONE,
                        AttributionKind.UNATTRIBUTED, true, 1L, 0,
                        Collections.emptyList()),
                    EventState.of(
                        1, 8L, OwnerKind.NONE,
                        AttributionKind.UNATTRIBUTED, true, 1L, 0,
                        Collections.emptyList())),
                0, 2, 0));
        expectIllegal(() -> LayeredPackedRegionEventOwnershipInventory
            .inventory(
                1L, 1L, "not-a-scheduler-instance",
                Collections.emptyList(), Collections.emptyList(), 0, 0, 0));
        expectIllegal(() -> LayeredPackedRegionEventOwnershipInventory
            .inventory(
                1L, 1L, null,
                Collections.emptyList(), Collections.emptyList(), 0, 0, 0));
        expectIllegal(() -> LayeredPackedRegionEventOwnershipInventory
            .inventory(
                1L, 1L, "00000000-0000-0000-0000-000000000096",
                Collections.emptyList(),
                Arrays.asList(
                    EventState.of(
                        0, 9L, OwnerKind.NONE,
                        AttributionKind.UNATTRIBUTED, true, 1L, 0,
                        Collections.emptyList()),
                    EventState.of(
                        1, 7L, OwnerKind.NONE,
                        AttributionKind.UNATTRIBUTED, true, 1L, 0,
                        Collections.emptyList())),
                0, 2, 0));
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceNinetySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-ninety-six-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "EventRegistrationInventoryFixture.java"
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

    def test_registration_inventory_fixture(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "EventRegistrationInventoryFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_handler_detaches_one_atomic_registration_snapshot(self):
        source = HANDLER.read_text(encoding="utf-8")
        start = source.index(
            "captureLayeredPackedRegionEventOwnershipInventory("
        )
        end = source.index("public boolean hasEvent", start)
        boundary = source[start:end]
        self.assertIn("getTrackedEventAtomicTimingSnapshot(", boundary)
        self.assertIn("timingSnapshot.getRegistrations()", boundary)
        self.assertIn(
            "timingSnapshot.getSchedulerInstanceIdentity()", boundary
        )
        self.assertIn("registration.getRegistrationSequence()", boundary)
        self.assertNotIn("List<GameTickEvent> liveEvents", boundary)
        for forbidden in (
            "getUUID()", "getDescriptor()", "getClass()", "GameTickKey",
            ".stop()", "eventStore.remove", "eventStore.add", "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_identity_is_published_minimally_and_remains_non_authoritative(self):
        inventory = INVENTORY.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn("getRegistrationSequence()", inventory)
        self.assertIn("isSchedulerInstanceIdentityCaptured()", inventory)
        self.assertIn("getRegistrationSequence()", observer)
        self.assertIn("registrationSequence", observer)
        for forbidden in (
            "import java.util.UUID", "GameTickEvent ", "GameTickKey",
            "event.stop()", "eventStore.remove", "eventStore.add",
        ):
            self.assertNotIn(forbidden, inventory)

    def test_living_plan_records_slice_ninety_six_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 96: Bounded event registration identity capture",
            plan,
        )
        self.assertIn("schema-v33 remains unchanged", plan)
        self.assertIn("scheduler-instance identity", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
