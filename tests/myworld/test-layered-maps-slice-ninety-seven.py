#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
SCHEMA_V33 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v33.schema.json"
)
SCHEMA_V34 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v34.schema.json"
)
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-eleven.py"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceNinetySevenTest(unittest.TestCase):
    def test_v34_is_additive_and_keeps_v33_historical_contract_closed(self):
        v33 = json.loads(SCHEMA_V33.read_text(encoding="utf-8"))
        v34 = json.loads(SCHEMA_V34.read_text(encoding="utf-8"))
        self.assertEqual(
            "layered-map-parity-event-v33",
            v33["properties"]["schema"]["const"],
        )
        self.assertEqual(
            "layered-map-parity-event-v34",
            v34["properties"]["schema"]["const"],
        )
        old_contract = v33["$defs"]["eventOwnership"]
        old_event = v33["$defs"]["eventRecord"]
        for field in (
            "registrationIdentityCapturedEventCount",
            "registrationIdentityCaptured", "registrationIdentityComplete",
            "schedulerInstanceIdentityCaptured",
        ):
            self.assertNotIn(field, old_contract["properties"])
        self.assertNotIn("registrationSequence", old_event["properties"])
        self.assertFalse(v34["$defs"]["eventOwnership"][
            "additionalProperties"
        ])
        self.assertFalse(v34["$defs"]["eventRecord"][
            "additionalProperties"
        ])

    def test_v34_identity_contract_is_complete_process_local_and_inert(self):
        schema = json.loads(SCHEMA_V34.read_text(encoding="utf-8"))
        contract = schema["$defs"]["eventOwnership"]
        event = schema["$defs"]["eventRecord"]
        self.assertTrue(contract["properties"][
            "registrationIdentityCaptured"
        ]["const"])
        self.assertTrue(contract["properties"][
            "registrationIdentityComplete"
        ]["const"])
        self.assertFalse(contract["properties"][
            "schedulerInstanceIdentityCaptured"
        ]["const"])
        self.assertEqual(
            1, event["properties"]["registrationSequence"]["minimum"]
        )
        for false_flag in (
            "callbackStateCaptured", "schedulerIdentityCaptured",
            "preservationPerformed", "reloadRequest", "eventCancellation",
            "eventReschedule", "entityRegistry", "arrivalGate",
            "teardownTransaction", "lifecycleAuthority",
        ):
            self.assertFalse(contract["properties"][false_flag]["const"])
        for forbidden in (
            "uuid", "eventKey", "descriptor", "eventClass", "callbackClass",
            "ownerIdentity", "usernameHash",
        ):
            self.assertNotIn(forbidden, event["properties"])

    def test_observer_serializes_registration_and_instance_scope_only(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v36"', source)
        boundary = source[
            source.index("private static void appendPackedRegionEventOwnership("):
            source.index("private static void appendIntegerList(")
        ]
        self.assertIn("getRegistrationSequence()", boundary)
        self.assertIn("isRegistrationIdentityComplete()", boundary)
        self.assertIn("isSchedulerInstanceIdentityCaptured()", boundary)
        self.assertIn("getSchedulerInstanceIdentity()", boundary)
        for forbidden in (
            "getUUID()", "getDescriptor()", "getClass()", "eventKey",
            "ownerIdentity", "usernameHash",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_executable_fixture_covers_identity_and_v34_schema(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        self.assertIn("[101, 102]", fixture)
        self.assertIn("registrationIdentityCapturedEventCount", fixture)
        self.assertIn("schedulerInstanceIdentityCaptured", fixture)
        self.assertIn("layered-map-parity-event-v34.schema.json", fixture)
        self.assertIn("layered-map-parity-event-v34.schema.json", readme)
        self.assertIn("must never be compared", readme)

    def test_living_plan_records_slice_ninety_seven_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 97: Private event registration identity diagnostics",
            plan,
        )
        self.assertIn("schema-v34", plan)
        self.assertIn("Private owner validation status", plan)
        self.assertIn("existing event UUID", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
