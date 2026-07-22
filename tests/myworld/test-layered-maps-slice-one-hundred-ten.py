#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
SCHEMA_V37 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v37.schema.json"
)
SCHEMA_V38 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v38.schema.json"
)
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-eleven.py"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredTenTest(unittest.TestCase):
    def test_schema_v37_is_immutable_and_v38_is_additive(self):
        v37 = json.loads(SCHEMA_V37.read_text(encoding="utf-8"))
        v38 = json.loads(SCHEMA_V38.read_text(encoding="utf-8"))
        old_aggregate = v37["$defs"]["eventOwnership"]
        new_aggregate = v38["$defs"]["eventOwnership"]
        old_restoration = v37["$defs"]["restorationState"]
        new_restoration = v38["$defs"]["restorationState"]

        self.assertEqual(
            "layered-map-parity-event-v37",
            v37["properties"]["schema"]["const"],
        )
        for name in (
            "targetBindingRequirementCapturedEventCount",
            "targetBindingRequirementCaptured",
            "targetBindingRequirementComplete",
            "targetBindingCompleteEventCount",
            "targetBindingComplete",
            "arrivalOrderingCapturedEventCount",
            "arrivalOrderingCaptured",
            "arrivalOrderingComplete",
        ):
            self.assertNotIn(name, old_aggregate["properties"])
            self.assertIn(name, new_aggregate["required"])
            self.assertIn(name, new_aggregate["properties"])
        for name in (
            "targetSubject",
            "bindingEvidence",
            "targetConflictPolicy",
            "targetBindingRequirementCaptured",
            "targetBindingComplete",
            "arrivalOrderingRequirement",
            "arrivalOrderingCaptured",
        ):
            self.assertNotIn(name, old_restoration["properties"])
            self.assertIn(name, new_restoration["required"])
            self.assertIn(name, new_restoration["properties"])

    def test_v38_schema_keeps_binding_and_arrival_claims_fail_closed(self):
        schema = json.loads(SCHEMA_V38.read_text(encoding="utf-8"))
        aggregate = schema["$defs"]["eventOwnership"]["properties"]
        restoration = schema["$defs"]["restorationState"]
        properties = restoration["properties"]

        self.assertTrue(
            aggregate["targetBindingRequirementComplete"]["const"]
        )
        self.assertTrue(aggregate["arrivalOrderingComplete"]["const"])
        self.assertEqual({"type": "boolean"},
                         aggregate["targetBindingComplete"])
        self.assertEqual(
            "REFUSE_MISMATCH_OR_AMBIGUITY",
            properties["targetConflictPolicy"]["const"],
        )
        self.assertEqual(
            "RECONCILE_BEFORE_FIRST_VISIBILITY",
            properties["arrivalOrderingRequirement"]["const"],
        )
        serialized = json.dumps(restoration["allOf"], sort_keys=True)
        self.assertIn("AUTHORED_DESTINATION_SLOT", serialized)
        self.assertIn("AUTHORED_EXISTING_ENTITY", serialized)
        self.assertIn("MISSING_AUTHORED_PLACEMENT_IDENTITY", serialized)
        self.assertIn('"targetBindingComplete": {"const": false}', serialized)
        self.assertFalse(properties["targetBindingLookupPerformed"]["const"])
        self.assertFalse(properties["standaloneRestorationComplete"]["const"])

    def test_observer_publishes_only_detached_requirement_values(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v40"', source)
        boundary = source[
            source.index("private static void appendPackedRegionEventOwnership("):
            source.index("private static void appendIntegerList(")
        ]
        for method in (
            "getTargetBindingRequirementCapturedEventCount()",
            "isTargetBindingRequirementComplete()",
            "getTargetBindingCompleteEventCount()",
            "isTargetBindingComplete()",
            "getArrivalOrderingCapturedEventCount()",
            "isArrivalOrderingComplete()",
            "state.getTargetSubject()",
            "state.getBindingEvidence()",
            "state.getTargetConflictPolicy()",
            "state.getArrivalOrderingRequirement()",
        ):
            self.assertIn(method, boundary)
        for forbidden in (
            "GameTickEventRestorationRequirement",
            "GameTickEventStore",
            "GameTickEvent ",
            "registerGameObject",
            "unregisterGameObject",
            "sendUpdatePackets",
            "eventStore.remove",
            "eventStore.add",
            ".stop()",
            "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_fixture_and_readme_use_schema_v38_contract(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v38.schema.json", fixture)
        self.assertIn(
            '"targetBindingRequirementCapturedEventCount"', fixture
        )
        self.assertIn('restoration["targetSubject"]', fixture)
        self.assertIn('restoration["arrivalOrderingRequirement"]', fixture)
        self.assertIn("layered-map-parity-event-v38.schema.json", readme)
        self.assertIn("Authored spawn records require", readme)
        self.assertIn("arrival gating", readme)

    def test_living_plan_records_slice_one_hundred_ten_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 110: Private scenery target and arrival diagnostics",
            plan,
        )
        self.assertIn("Historical schema-v37 remains immutable", plan)
        self.assertIn("No target lookup is performed", plan)


if __name__ == "__main__":
    unittest.main()
