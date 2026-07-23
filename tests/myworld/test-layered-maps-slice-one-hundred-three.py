#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
SCHEMA_V35 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v35.schema.json"
SCHEMA_V36 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v36.schema.json"
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-eleven.py"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceOneHundredThreeTest(unittest.TestCase):
    def test_schema_v35_is_immutable_and_v36_is_additive(self):
        v35 = json.loads(SCHEMA_V35.read_text(encoding="utf-8"))
        v36 = json.loads(SCHEMA_V36.read_text(encoding="utf-8"))
        old = v35["$defs"]["eventOwnership"]
        current = v36["$defs"]["eventOwnership"]
        added = (
            "executionSemanticsCapturedEventCount",
            "executionSemanticsCaptured",
            "executionSemanticsComplete",
            "atomicTimingCapturedEventCount",
            "atomicTimingCaptured",
        )
        for name in added:
            self.assertNotIn(name, old["properties"])
            self.assertIn(name, current["required"])
            self.assertIn(name, current["properties"])

    def test_v36_records_closed_semantics_and_non_atomic_timing(self):
        schema = json.loads(SCHEMA_V36.read_text(encoding="utf-8"))
        aggregate = schema["$defs"]["eventOwnership"]["properties"]
        restoration = schema["$defs"]["restorationState"]
        self.assertTrue(aggregate["executionSemanticsComplete"]["const"])
        self.assertEqual(0, aggregate["atomicTimingCapturedEventCount"]["const"])
        self.assertFalse(aggregate["atomicTimingCaptured"]["const"])
        self.assertEqual(
            "ONE_SHOT",
            restoration["properties"]["executionSemantics"]["const"],
        )
        self.assertEqual(
            "CONTINUE_SERVER_TICKS",
            restoration["properties"]["timeProgressionPolicy"]["const"],
        )
        self.assertTrue(
            restoration["properties"]["executionSemanticsCaptured"]["const"]
        )
        self.assertFalse(
            restoration["properties"]["atomicTimingCaptured"]["const"]
        )

    def test_observer_exposes_only_detached_semantic_values(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v45"', source)
        boundary = source[
            source.index("private static void appendPackedRegionEventOwnership("):
            source.index("private static void appendIntegerList(")
        ]
        for method in (
            "getExecutionSemanticsCapturedEventCount()",
            "isExecutionSemanticsCaptured()",
            "isExecutionSemanticsComplete()",
            "getExecutionSemantics()",
            "getTimeProgressionPolicy()",
        ):
            self.assertIn(method, boundary)
        self.assertIn("atomicTimingCapturedEventCount", boundary)
        self.assertIn("atomicTimingCaptured", boundary)
        self.assertIn("getAtomicTimingCapturedEventCount()", boundary)
        self.assertIn("isAtomicTimingCaptured()", boundary)
        for forbidden in (
            "GameTickEventStore",
            "GameTickEvent",
            "eventStore.remove",
            "eventStore.add",
            ".stop()",
            "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_executable_fixture_and_readme_use_v36_contract(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v36.schema.json", fixture)
        self.assertIn('"executionSemanticsCapturedEventCount"', fixture)
        self.assertIn('restoration["timeProgressionPolicy"]', fixture)
        self.assertIn("layered-map-parity-event-v36.schema.json", readme)
        self.assertIn("ONE_SHOT", readme)
        self.assertIn("CONTINUE_SERVER_TICKS", readme)

    def test_living_plan_records_slice_one_hundred_three_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 103: Private scenery-event execution-semantics diagnostics",
            plan,
        )
        self.assertIn("Historical schema-v35 remains", plan)
        self.assertIn("No callback is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
