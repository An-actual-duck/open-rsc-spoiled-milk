#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
SCHEMA_V32 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v32.schema.json"
)
SCHEMA_V33 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v33.schema.json"
)
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-eleven.py"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceNinetyFourTest(unittest.TestCase):
    def test_v33_is_additive_and_keeps_v32_historical_contract_closed(self):
        v32 = json.loads(SCHEMA_V32.read_text(encoding="utf-8"))
        v33 = json.loads(SCHEMA_V33.read_text(encoding="utf-8"))
        self.assertEqual(
            "layered-map-parity-event-v32",
            v32["properties"]["schema"]["const"],
        )
        self.assertEqual(
            "layered-map-parity-event-v33",
            v33["properties"]["schema"]["const"],
        )
        old_contract = v32["$defs"]["eventOwnership"]
        old_event = v32["$defs"]["eventRecord"]
        for field in (
            "restorationStateAvailableEventCount",
            "detachedCallbackPayloadCompleteEventCount",
        ):
            self.assertNotIn(field, old_contract["properties"])
        self.assertNotIn("restorationState", old_event["properties"])
        for name in ("eventOwnership", "eventSource", "eventRecord"):
            self.assertFalse(v33["$defs"][name]["additionalProperties"])

    def test_v33_restoration_contract_is_bounded_private_and_inert(self):
        schema = json.loads(SCHEMA_V33.read_text(encoding="utf-8"))
        contract = schema["$defs"]["eventOwnership"]
        event = schema["$defs"]["eventRecord"]
        state = schema["$defs"]["restorationState"]
        scenery = schema["$defs"]["restorationScenery"]
        self.assertIn("restorationState", event["required"])
        self.assertEqual(0, contract["properties"][
            "restorationStateCompleteEventCount"
        ]["const"])
        self.assertNotIn("owner", scenery["properties"])
        self.assertIn("ownerPresent", scenery["properties"])
        self.assertEqual(65536, contract["properties"]["events"]["maxItems"])
        for false_flag in (
            "schedulerIdentityCaptured", "targetBindingLookupPerformed",
            "standaloneRestorationComplete",
        ):
            self.assertFalse(state["properties"][false_flag]["const"])
        for false_flag in (
            "callbackStateCaptured", "schedulerIdentityCaptured",
            "preservationPerformed", "reloadRequest", "eventCancellation",
            "eventReschedule", "entityRegistry", "arrivalGate",
            "teardownTransaction", "lifecycleAuthority",
        ):
            self.assertFalse(contract["properties"][false_flag]["const"])

    def test_observer_serializes_minimum_state_without_raw_owner(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v38"', source)
        self.assertIn("appendEventRestorationState(", source)
        self.assertIn("getRestorationStateAvailableEventCount()", source)
        self.assertIn("getDetachedCallbackPayloadCompleteEventCount()", source)
        boundary = source[
            source.index("private static void appendEventRestorationState("):
            source.index("private static void appendIntegerList(")
        ]
        self.assertIn("scenery.hasOwner()", boundary)
        for forbidden in (
            "scenery.getOwner()", "ownerIdentity", "getDescriptor()",
            "getUUID()", "getClass()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_executable_fixture_covers_payload_privacy_and_v33_schema(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        self.assertIn("private-event-owner", fixture)
        self.assertIn(
            'self.assertNotIn("private-event-owner", json.dumps(decision_events))',
            fixture,
        )
        self.assertIn("detachedCallbackPayloadCompleteEventCount", fixture)
        self.assertIn("restorationStateEventOrdinals", fixture)
        self.assertIn("layered-map-parity-event-v33.schema.json", fixture)
        self.assertIn("layered-map-parity-event-v33.schema.json", readme)
        self.assertIn("owner presence (never owner text)", readme)

    def test_living_plan_records_slice_ninety_four_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 94: Private scenery-event restoration diagnostics",
            plan,
        )
        self.assertIn("schema-v33", plan)
        self.assertIn("Private owner validation status", plan)
        self.assertIn("Raw owner text is never serialized", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
