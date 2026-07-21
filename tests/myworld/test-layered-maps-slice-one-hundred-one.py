#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationState.java"
)
HANDLER = ROOT / "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
INVENTORY = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventOwnershipInventory.java"
)
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-ninety-two.py"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceOneHundredOneTest(unittest.TestCase):
    def test_known_scenery_state_declares_one_shot_continuing_time(self):
        source = STATE.read_text(encoding="utf-8")
        self.assertIn("enum ExecutionSemantics", source)
        self.assertIn("ONE_SHOT", source)
        self.assertIn("enum TimeProgressionPolicy", source)
        self.assertIn("CONTINUE_SERVER_TICKS", source)
        self.assertIn("getExecutionSemantics()", source)
        self.assertIn("getTimeProgressionPolicy()", source)
        self.assertIn("isExecutionSemanticsCaptured()", source)

    def test_both_known_factories_bind_the_explicit_contract(self):
        source = STATE.read_text(encoding="utf-8")
        spawn = source[
            source.index("scenerySpawn("):
            source.index("sceneryRemove(")
        ]
        removal = source[source.index("sceneryRemove("):source.index("public Kind getKind()")]
        for boundary in (spawn, removal):
            self.assertIn("ExecutionSemantics.ONE_SHOT", boundary)
            self.assertIn(
                "TimeProgressionPolicy.CONTINUE_SERVER_TICKS", boundary
            )

    def test_executable_fixture_covers_known_and_unavailable_semantics(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        self.assertIn(
            "GameTickEventRestorationState.ExecutionSemantics.ONE_SHOT",
            fixture,
        )
        self.assertIn("CONTINUE_SERVER_TICKS", fixture)
        self.assertIn("ExecutionSemantics.UNAVAILABLE", fixture)
        self.assertIn("!unavailable.isExecutionSemanticsCaptured()", fixture)

    def test_semantics_are_detached_but_not_yet_published(self):
        handler = HANDLER.read_text(encoding="utf-8")
        inventory = INVENTORY.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        for method in (
            "getExecutionSemantics()",
            "getTimeProgressionPolicy()",
            "isExecutionSemanticsCaptured()",
        ):
            self.assertIn(method, inventory)
            self.assertNotIn(method, observer)
        self.assertIn("state.getExecutionSemantics()", handler)
        self.assertIn("state.getTimeProgressionPolicy()", handler)
        for forbidden in (
            "registerGameObject",
            "unregisterGameObject",
            "eventStore",
            "event.stop()",
            "doRun()",
        ):
            self.assertNotIn(forbidden, STATE.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_one_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 101: Dormant scenery-event execution semantics",
            plan,
        )
        self.assertIn("CONTINUE_SERVER_TICKS", plan)
        self.assertIn("No callback is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
