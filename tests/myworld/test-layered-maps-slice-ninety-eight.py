#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
STORE = ROOT / "server/src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
HANDLER = ROOT / "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
INVENTORY = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventOwnershipInventory.java"
)
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-ninety-five.py"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceNinetyEightTest(unittest.TestCase):
    def test_store_defines_one_opaque_instance_identity(self):
        source = STORE.read_text(encoding="utf-8")
        self.assertIn(
            "private final String schedulerInstanceIdentity =", source
        )
        self.assertIn("UUID.randomUUID().toString()", source)
        self.assertIn("class RegistrationSnapshot", source)
        self.assertIn("getTrackedEventRegistrationSnapshot()", source)
        self.assertIn("getSchedulerInstanceIdentity()", source)
        self.assertIn("getRegistrations()", source)

    def test_atomic_snapshot_is_read_only_and_has_no_scheduler_authority(self):
        source = STORE.read_text(encoding="utf-8")
        boundary = source[
            source.index(
                "RegistrationSnapshot getTrackedEventRegistrationSnapshot()"
            ):
            source.index("private void registerAccepted")
        ]
        self.assertIn("synchronized (LOCK)", boundary)
        self.assertIn("Collections.unmodifiableList(registrations)", boundary)
        for forbidden in (
            "event.stop()",
            "remove(event)",
            "doRun()",
            "events.clear()",
            "registrationSequences.clear()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_executable_registration_fixture_covers_instance_scope(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        self.assertIn(
            "schedulerInstanceIdentityScopesAtomicSnapshots();", fixture
        )
        self.assertIn(
            "one store lifetime keeps one scheduler-instance identity",
            fixture,
        )
        self.assertIn(
            "different stores have different scheduler-instance identities",
            fixture,
        )

    def test_identity_is_detached_and_privately_published(self):
        handler = HANDLER.read_text(encoding="utf-8")
        inventory = INVENTORY.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            "registrationSnapshot.getSchedulerInstanceIdentity()", handler
        )
        self.assertIn(
            "isSchedulerInstanceIdentityCaptured() { return true; }",
            inventory,
        )
        self.assertIn("getSchedulerInstanceIdentity()", observer)

    def test_living_plan_records_slice_ninety_eight_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 98: Scheduler-instance identity scope", plan
        )
        self.assertIn("different scheduler stores", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
