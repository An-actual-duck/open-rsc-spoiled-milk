#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HANDLER = ROOT / "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
INVENTORY = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventOwnershipInventory.java"
)
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
SCHEMA_V35 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v35.schema.json"
SCHEMA_V36 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v36.schema.json"
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-ninety-three.py"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceOneHundredTwoTest(unittest.TestCase):
    def test_inventory_keeps_semantics_and_atomic_timing_distinct(self):
        source = INVENTORY.read_text(encoding="utf-8")
        self.assertIn("enum ExecutionSemantics", source)
        self.assertIn("enum TimeProgressionPolicy", source)
        self.assertIn("getExecutionSemantics()", source)
        self.assertIn("getTimeProgressionPolicy()", source)
        self.assertIn("getExecutionSemanticsCapturedEventCount()", source)
        self.assertIn("isExecutionSemanticsComplete()", source)
        self.assertIn("getAtomicTimingCapturedEventCount()", source)
        self.assertIn("isAtomicTimingComplete()", source)
        self.assertIn("Atomic timing requires explicit execution semantics", source)

    def test_handler_maps_declared_values_without_class_inference(self):
        source = HANDLER.read_text(encoding="utf-8")
        start = source.index("detachEventRestorationState(")
        end = source.index("public boolean hasEvent", start)
        boundary = source[start:end]
        self.assertIn("state.getExecutionSemantics().name()", boundary)
        self.assertIn("state.getTimeProgressionPolicy().name()", boundary)
        self.assertIn("ExecutionSemantics.valueOf", boundary)
        self.assertIn("TimeProgressionPolicy.valueOf", boundary)
        for forbidden in (
            "instanceof SingleEvent",
            "getClass()",
            "getDescriptor()",
            "getUUID()",
            "event.stop()",
            "eventStore.remove",
            "eventStore.add",
            "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_executable_fixture_covers_counts_values_and_refusal(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        self.assertIn("getExecutionSemanticsCapturedEventCount() == 2", fixture)
        self.assertIn("isExecutionSemanticsComplete()", fixture)
        self.assertIn("getAtomicTimingCapturedEventCount() == 0", fixture)
        self.assertIn("ExecutionSemantics.UNAVAILABLE", fixture)
        self.assertIn("CONTINUE_SERVER_TICKS", fixture)

    def test_schema_v35_stays_immutable_while_v36_publishes_semantics(self):
        schema_v35 = json.loads(SCHEMA_V35.read_text(encoding="utf-8"))
        schema_v36 = json.loads(SCHEMA_V36.read_text(encoding="utf-8"))
        observer = OBSERVER.read_text(encoding="utf-8")
        old_contract = schema_v35["$defs"]["eventOwnership"]
        new_contract = schema_v36["$defs"]["eventOwnership"]
        for name in (
            "executionSemanticsCapturedEventCount",
            "executionSemanticsCaptured",
            "executionSemanticsComplete",
            "atomicTimingCapturedEventCount",
            "atomicTimingCaptured",
        ):
            self.assertNotIn(name, old_contract["properties"])
            self.assertIn(name, new_contract["properties"])
            self.assertIn(name, observer)
        self.assertIn("getExecutionSemantics()", observer)
        self.assertIn("getTimeProgressionPolicy()", observer)

    def test_living_plan_records_slice_one_hundred_two_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 102: Detached scenery-event execution semantics",
            plan,
        )
        self.assertIn("atomic timing remains false", plan)
        self.assertIn("No callback is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
