#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
SCHEMA_V34 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v34.schema.json"
SCHEMA_V35 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v35.schema.json"
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-eleven.py"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceOneHundredTest(unittest.TestCase):
    def test_schema_v34_is_immutable_and_v35_is_additive(self):
        v34 = json.loads(SCHEMA_V34.read_text(encoding="utf-8"))
        v35 = json.loads(SCHEMA_V35.read_text(encoding="utf-8"))
        old = v34["$defs"]["eventOwnership"]
        current = v35["$defs"]["eventOwnership"]
        self.assertNotIn("schedulerInstanceIdentity", old["properties"])
        self.assertFalse(
            old["properties"]["schedulerInstanceIdentityCaptured"]["const"]
        )
        self.assertIn("schedulerInstanceIdentity", current["required"])
        self.assertTrue(
            current["properties"]["schedulerInstanceIdentityCaptured"][
                "const"
            ]
        )
        self.assertEqual(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            current["properties"]["schedulerInstanceIdentity"]["pattern"],
        )

    def test_v35_keeps_full_scheduler_and_authority_flags_false(self):
        schema = json.loads(SCHEMA_V35.read_text(encoding="utf-8"))
        properties = schema["$defs"]["eventOwnership"]["properties"]
        for name in (
            "callbackStateCaptured",
            "schedulerIdentityCaptured",
            "preservationPerformed",
            "reloadRequest",
            "eventCancellation",
            "eventReschedule",
            "entityRegistry",
            "arrivalGate",
            "teardownTransaction",
            "lifecycleAuthority",
        ):
            self.assertFalse(properties[name]["const"], name)

    def test_observer_exposes_only_detached_scope(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v42"', source)
        boundary = source[
            source.index("private static void appendPackedRegionEventOwnership("):
            source.index("private static void appendIntegerList(")
        ]
        self.assertIn("isSchedulerInstanceIdentityCaptured()", boundary)
        self.assertIn("getSchedulerInstanceIdentity()", boundary)
        for forbidden in (
            "GameTickEventStore",
            "RegistrationSnapshot",
            "getUUID()",
            "GameTickKey",
            "ownerUUID",
            "eventStore.remove",
            "eventStore.add",
            ".stop()",
            "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_executable_fixture_and_readme_use_v35_contract(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v35.schema.json", fixture)
        self.assertIn(
            'event_ownership["schedulerInstanceIdentity"]', fixture
        )
        self.assertIn("layered-map-parity-event-v35.schema.json", readme)
        self.assertIn("scheduler-instance", readme)
        self.assertIn("server restart", readme)

    def test_living_plan_records_slice_one_hundred_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 100: Private scheduler-instance scope diagnostics",
            plan,
        )
        self.assertIn("Historical schema-v34", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
