#!/usr/bin/env python3
import json
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
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-ninety-six.py"
SCHEMA_V34 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v34.schema.json"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceNinetyNineTest(unittest.TestCase):
    def test_inventory_requires_detached_canonical_instance_identity(self):
        source = INVENTORY.read_text(encoding="utf-8")
        self.assertIn("Pattern SCHEDULER_INSTANCE_IDENTITY", source)
        self.assertIn("final String schedulerInstanceIdentity", source)
        self.assertIn("getSchedulerInstanceIdentity()", source)
        self.assertIn(
            "isSchedulerInstanceIdentityCaptured() { return true; }",
            source,
        )
        self.assertIn("isSchedulerIdentityCaptured() { return false; }", source)

    def test_handler_consumes_one_atomic_scoped_registration_snapshot(self):
        source = HANDLER.read_text(encoding="utf-8")
        start = source.index(
            "captureLayeredPackedRegionEventOwnershipInventory("
        )
        end = source.index("public boolean hasEvent", start)
        boundary = source[start:end]
        self.assertEqual(
            1, boundary.count("getTrackedEventAtomicTimingSnapshot(")
        )
        self.assertIn("timingSnapshot.getRegistrations()", boundary)
        self.assertIn(
            "timingSnapshot.getSchedulerInstanceIdentity()", boundary
        )
        for forbidden in (
            "getUUID()",
            "GameTickKey",
            "eventStore.remove",
            "eventStore.add",
            ".stop()",
            "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_executable_inventory_fixture_covers_scope_and_refusal(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        self.assertIn(
            "registration evidence carries detached scheduler-instance scope",
            fixture,
        )
        self.assertIn('"not-a-scheduler-instance"', fixture)
        self.assertIn("1L, 1L, null", fixture)

    def test_schema_v34_stays_private_while_current_observer_is_additive(self):
        schema_text = SCHEMA_V34.read_text(encoding="utf-8")
        schema = json.loads(schema_text)
        observer = OBSERVER.read_text(encoding="utf-8")
        contract = schema["$defs"]["eventOwnership"]
        self.assertNotIn("schedulerInstanceIdentity", contract["properties"])
        self.assertFalse(
            contract["properties"]["schedulerInstanceIdentityCaptured"][
                "const"
            ]
        )
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v43"', observer)
        self.assertIn("getSchedulerInstanceIdentity()", observer)
        self.assertIn("isSchedulerInstanceIdentityCaptured()", observer)
        self.assertIn(
            'field(out, "schedulerInstanceIdentity"',
            observer,
        )

    def test_living_plan_records_slice_ninety_nine_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 99: Detached scheduler-instance identity", plan
        )
        self.assertIn("schema-v34 remains unchanged", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
