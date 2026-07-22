#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
SCHEMA_V38 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v38.schema.json"
)
SCHEMA_V39 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v39.schema.json"
)
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-eleven.py"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredThirteenTest(unittest.TestCase):
    def test_schema_v38_is_immutable_and_v39_is_additive(self):
        v38 = json.loads(SCHEMA_V38.read_text(encoding="utf-8"))
        v39 = json.loads(SCHEMA_V39.read_text(encoding="utf-8"))
        old_aggregate = v38["$defs"]["eventOwnership"]
        new_aggregate = v39["$defs"]["eventOwnership"]
        old_restoration = v38["$defs"]["restorationState"]
        new_restoration = v39["$defs"]["restorationState"]

        self.assertEqual(
            "layered-map-parity-event-v38",
            v38["properties"]["schema"]["const"],
        )
        for name in (
            "generationBindingRequirementCapturedEventCount",
            "generationBindingRequirementCaptured",
            "generationBindingRequirementComplete",
            "generationBindingCompleteEventCount",
            "generationBindingComplete",
            "idempotencyRequirementCapturedEventCount",
            "idempotencyRequirementCaptured",
            "idempotencyRequirementComplete",
        ):
            self.assertNotIn(name, old_aggregate["properties"])
            self.assertIn(name, new_aggregate["required"])
            self.assertIn(name, new_aggregate["properties"])
        for name in (
            "generationBindingRequirement",
            "generationBindingRequirementCaptured",
            "generationBindingComplete",
            "desiredState",
            "idempotencyPolicy",
            "mutationPrecondition",
            "idempotencyRequirementCaptured",
        ):
            self.assertNotIn(name, old_restoration["properties"])
            self.assertIn(name, new_restoration["required"])
            self.assertIn(name, new_restoration["properties"])

    def test_v39_schema_keeps_generation_and_idempotency_fail_closed(self):
        schema = json.loads(SCHEMA_V39.read_text(encoding="utf-8"))
        aggregate = schema["$defs"]["eventOwnership"]["properties"]
        restoration = schema["$defs"]["restorationState"]
        properties = restoration["properties"]

        self.assertTrue(
            aggregate["generationBindingRequirementComplete"]["const"]
        )
        self.assertEqual(
            {"type": "boolean"}, aggregate["generationBindingComplete"]
        )
        self.assertTrue(
            aggregate["idempotencyRequirementComplete"]["const"]
        )
        self.assertEqual(
            "MATCH_RECONSTRUCTION_GENERATION",
            properties["generationBindingRequirement"]["const"],
        )
        self.assertEqual(
            "ALREADY_SATISFIED_IS_NO_OP_SUCCESS",
            properties["idempotencyPolicy"]["const"],
        )
        serialized = json.dumps(restoration["allOf"], sort_keys=True)
        for value in (
            "AUTHORED_SCENERY_PRESENT",
            "AUTHORED_SCENERY_ABSENT",
            "DESTINATION_SLOT_EMPTY",
            "EXACT_AUTHORED_ENTITY_PRESENT",
        ):
            self.assertIn(value, serialized)
        self.assertIn(
            '"generationBindingComplete": {"const": false}', serialized
        )
        self.assertFalse(properties["targetBindingLookupPerformed"]["const"])
        self.assertFalse(properties["standaloneRestorationComplete"]["const"])

    def test_observer_publishes_only_detached_rule_values(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v41"', source)
        boundary = source[
            source.index("private static void appendPackedRegionEventOwnership("):
            source.index("private static void appendIntegerList(")
        ]
        for method in (
            "getGenerationBindingRequirementCapturedEventCount()",
            "isGenerationBindingRequirementComplete()",
            "getGenerationBindingCompleteEventCount()",
            "isGenerationBindingComplete()",
            "getIdempotencyRequirementCapturedEventCount()",
            "isIdempotencyRequirementComplete()",
            "state.getGenerationBindingRequirement()",
            "state.isGenerationBindingComplete(reconstructionGeneration)",
            "state.getDesiredState()",
            "state.getIdempotencyPolicy()",
            "state.getMutationPrecondition()",
        ):
            self.assertIn(method, boundary)
        for forbidden in (
            "GameTickEventRestorationRequirement",
            "GameTickEventStore",
            "GameTickEvent ",
            "registerGameObject",
            "unregisterGameObject",
            "getGameObject",
            "sendUpdatePackets",
            "eventStore.remove",
            "eventStore.add",
            ".stop()",
            "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_fixture_and_readme_use_schema_v39_contract(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v39.schema.json", fixture)
        self.assertIn(
            '"generationBindingRequirementCapturedEventCount"', fixture
        )
        self.assertIn('restoration["desiredState"]', fixture)
        self.assertIn('restoration["mutationPrecondition"]', fixture)
        self.assertIn("layered-map-parity-event-v39.schema.json", readme)
        self.assertIn("already-satisfied desired state", readme)
        self.assertIn("target-state inspection", readme)

    def test_living_plan_records_slice_one_hundred_thirteen_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 113: Private generation and idempotency diagnostics",
            plan,
        )
        self.assertIn("Historical schema-v38 remains immutable", plan)
        self.assertIn("No target state is inspected", plan)


if __name__ == "__main__":
    unittest.main()
