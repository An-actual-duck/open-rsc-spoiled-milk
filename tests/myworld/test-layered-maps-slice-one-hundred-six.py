#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
SCHEMA_V36 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v36.schema.json"
)
SCHEMA_V37 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v37.schema.json"
)
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-eleven.py"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredSixTest(unittest.TestCase):
    def test_schema_v36_is_immutable_and_v37_is_additive(self):
        v36 = json.loads(SCHEMA_V36.read_text(encoding="utf-8"))
        v37 = json.loads(SCHEMA_V37.read_text(encoding="utf-8"))
        old_aggregate = v36["$defs"]["eventOwnership"]
        new_aggregate = v37["$defs"]["eventOwnership"]
        old_event = v36["$defs"]["eventRecord"]
        new_event = v37["$defs"]["eventRecord"]

        self.assertEqual(
            "layered-map-parity-event-v36",
            v36["properties"]["schema"]["const"],
        )
        self.assertEqual(
            0,
            old_aggregate["properties"]
            ["atomicTimingCapturedEventCount"]["const"],
        )
        self.assertFalse(
            old_aggregate["properties"]["atomicTimingCaptured"]["const"]
        )
        self.assertNotIn("atomicTimingComplete", old_aggregate["properties"])
        self.assertNotIn("atomicTimingCaptured", old_event["properties"])
        self.assertIn("atomicTimingComplete", new_aggregate["required"])
        self.assertIn("atomicTimingComplete", new_aggregate["properties"])
        self.assertIn("atomicTimingCaptured", new_event["required"])
        self.assertIn("atomicTimingCaptured", new_event["properties"])

    def test_v37_distinguishes_known_and_unknown_atomic_timing(self):
        schema = json.loads(SCHEMA_V37.read_text(encoding="utf-8"))
        aggregate = schema["$defs"]["eventOwnership"]["properties"]
        event = schema["$defs"]["eventRecord"]["properties"]
        restoration = schema["$defs"]["restorationState"]["properties"]

        self.assertEqual(
            {"$ref": (
                "layered-map-parity-event-v34.schema.json"
                "#/$defs/boundedEventCount"
            )},
            aggregate["atomicTimingCapturedEventCount"],
        )
        self.assertEqual({"type": "boolean"},
                         aggregate["atomicTimingCaptured"])
        self.assertTrue(aggregate["atomicTimingComplete"]["const"])
        self.assertEqual({"type": "boolean"}, event["atomicTimingCaptured"])
        self.assertTrue(restoration["atomicTimingCaptured"]["const"])

    def test_observer_publishes_only_detached_timing_primitives(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v41"', source)
        boundary = source[
            source.index("private static void appendPackedRegionEventOwnership("):
            source.index("private static void appendIntegerList(")
        ]
        for method in (
            "getAtomicTimingCapturedEventCount()",
            "isAtomicTimingCaptured()",
            "isAtomicTimingComplete()",
            "event.isAtomicTimingCaptured()",
        ):
            self.assertIn(method, boundary)
        for forbidden in (
            "GameTickEventStore",
            "GameTickEvent",
            "captureAtomicTimingSnapshot",
            "eventStore.remove",
            "eventStore.add",
            ".stop()",
            "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_fixture_and_readme_use_schema_v37_contract(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v37.schema.json", fixture)
        self.assertIn('event_ownership["atomicTimingComplete"]', fixture)
        self.assertIn('["events"][0]["atomicTimingCaptured"]', fixture)
        self.assertIn("layered-map-parity-event-v37.schema.json", readme)
        self.assertIn("Unknown callbacks remain visible", readme)
        self.assertIn("lifecycle-authority flag remain absent", readme)

    def test_living_plan_records_slice_one_hundred_six_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 106: Private atomic scenery-event timing diagnostics",
            plan,
        )
        self.assertIn("Historical schema-v36 remains immutable", plan)
        self.assertIn("No callback is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
