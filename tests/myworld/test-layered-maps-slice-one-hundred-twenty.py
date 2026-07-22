#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
OBSERVATION = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventTargetObservation.java"
)
SCHEMA_V41 = ROOT / (
    "tools/layered-maps/schema/"
    "layered-map-parity-event-v41.schema.json"
)
SCHEMA_V42 = ROOT / (
    "tools/layered-maps/schema/"
    "layered-map-parity-event-v42.schema.json"
)
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-eleven.py"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredTwentyTest(unittest.TestCase):
    def test_v41_is_immutable_and_v42_adds_region_boundary_evidence(self):
        v41 = json.loads(SCHEMA_V41.read_text(encoding="utf-8"))
        v42 = json.loads(SCHEMA_V42.read_text(encoding="utf-8"))
        self.assertEqual(
            "layered-map-parity-event-v41",
            v41["properties"]["schema"]["const"],
        )
        self.assertEqual(
            "layered-map-parity-event-v42",
            v42["properties"]["schema"]["const"],
        )
        old_targets = v41["$defs"]["eventTargets"]["properties"]
        new_targets = v42["$defs"]["eventTargets"]["properties"]
        old_target = v41["$defs"]["eventTarget"]["properties"]
        new_target = v42["$defs"]["eventTarget"]["properties"]
        for name in (
            "objectBoundaryClassifiedTargetCount",
            "availableTargetObjectBoundaryClassificationComplete",
            "runtimeTargetClassificationPerformed",
            "atomicWithMutation",
            "runtimeRevalidationPerformed",
        ):
            self.assertNotIn(name, old_targets)
            self.assertIn(name, new_targets)
        self.assertNotIn(
            "objectBoundaryHeldDuringClassification", old_target
        )
        self.assertIn(
            "objectBoundaryHeldDuringClassification", new_target
        )

    def test_v42_boundary_schema_is_closed_and_non_authoritative(self):
        schema = json.loads(SCHEMA_V42.read_text(encoding="utf-8"))
        targets = schema["$defs"]["eventTargets"]
        target = schema["$defs"]["eventTarget"]
        self.assertFalse(targets["additionalProperties"])
        self.assertFalse(target["additionalProperties"])
        for name in (
            "availableTargetObjectBoundaryClassificationComplete",
            "atomicWithMutation", "runtimeRevalidationPerformed",
        ):
            self.assertIn(name, targets["required"])
        self.assertTrue(
            targets["properties"]
            ["availableTargetObjectBoundaryClassificationComplete"]["const"]
        )
        for name in (
            "atomicWithMutation", "runtimeRevalidationPerformed",
            "entityHandleRetained", "achievedStateClaimed", "commitToken",
            "mutationPerformed", "executableRestoration", "arrivalGate",
            "lifecycleAuthority",
        ):
            self.assertFalse(targets["properties"][name]["const"])
        conditions = json.dumps(target["allOf"], sort_keys=True)
        self.assertIn(
            '"objectBoundaryHeldDuringClassification": {"const": true}',
            conditions,
        )
        self.assertIn(
            '"objectBoundaryHeldDuringClassification": {"const": false}',
            conditions,
        )

    def test_observer_validates_and_serializes_only_detached_boundary_facts(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v42"', source)
        matching = source[
            source.index("private static void requireEventTargetsMatchInventory("):
            source.index(
                "private static void appendPackedRegionAuthoredPopulationSupersession(",
                source.index("private static void requireEventTargetsMatchInventory("),
            )
        ]
        self.assertIn(
            "isAvailableTargetObjectBoundaryClassificationComplete()",
            matching,
        )
        serializer = source[
            source.index("private static void appendPackedRegionEventTargets("):
            source.index("private static void appendEventRestorationState(")
        ]
        for name in (
            "getObjectBoundaryClassifiedTargetCount()",
            "isAvailableTargetObjectBoundaryClassificationComplete()",
            "isRuntimeTargetClassificationPerformed()",
            "isAtomicWithMutation()", "isRuntimeRevalidationPerformed()",
            "isObjectBoundaryHeldDuringClassification()",
        ):
            self.assertIn(name, serializer)
        for forbidden in (
            "GameObject ", "Region ", "registerGameObject",
            "unregisterGameObject", "replaceGameObject", ".doRun()",
            "sendUpdatePackets",
        ):
            self.assertNotIn(forbidden, serializer)

    def test_observation_keeps_boundary_evidence_separate_from_revalidation(self):
        source = OBSERVATION.read_text(encoding="utf-8")
        for required in (
            "isRuntimeTargetClassificationPerformed()",
            "isAtomicWithMutation() { return false; }",
            "isRuntimeRevalidationPerformed() { return false; }",
            "isAchievedStateClaimed() { return false; }",
            "isCommitToken() { return false; }",
            "isMutationPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_fixture_readme_and_plan_identify_v42_boundary(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v42.schema.json", fixture)
        self.assertIn("layered-map-parity-event-v42.schema.json", readme)
        self.assertIn(
            "### Slice 120: Private Region-boundary target diagnostics", plan
        )
        self.assertIn("non-atomic with any later mutation", plan)


if __name__ == "__main__":
    unittest.main()
