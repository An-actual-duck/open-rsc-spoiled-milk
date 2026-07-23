#!/usr/bin/env python3
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HANDLER = ROOT / "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
INVENTORY = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventOwnershipInventory.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-ninety-three.py"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredNineTest(unittest.TestCase):
    def test_inventory_detaches_target_and_arrival_requirements(self):
        source = INVENTORY.read_text(encoding="utf-8")
        for declaration in (
            "enum TargetSubject",
            "AUTHORED_DESTINATION_SLOT",
            "AUTHORED_EXISTING_ENTITY",
            "enum BindingEvidence",
            "MISSING_AUTHORED_PLACEMENT_IDENTITY",
            "enum TargetConflictPolicy",
            "REFUSE_MISMATCH_OR_AMBIGUITY",
            "enum ArrivalOrderingRequirement",
            "RECONCILE_BEFORE_FIRST_VISIBILITY",
        ):
            self.assertIn(declaration, source)
        for method in (
            "getTargetBindingRequirementCapturedEventCount()",
            "isTargetBindingRequirementComplete()",
            "getTargetBindingCompleteEventCount()",
            "isTargetBindingComplete()",
            "getArrivalOrderingCapturedEventCount()",
            "isArrivalOrderingComplete()",
        ):
            self.assertIn(method, source)

    def test_handler_copies_and_reconciles_one_dormant_requirement(self):
        source = HANDLER.read_text(encoding="utf-8")
        start = source.index("detachEventRestorationState(")
        end = source.index("public boolean hasEvent", start)
        boundary = source[start:end]
        self.assertIn("GameTickEventRestorationRequirement.from(state)", boundary)
        self.assertIn("validateDetachedRestorationTarget(", boundary)
        self.assertIn("requirement.getTargetSubject().name()", boundary)
        self.assertIn("requirement.getBindingEvidence().name()", boundary)
        self.assertIn("requirement.getTargetConflictPolicy().name()", boundary)
        self.assertIn(
            "requirement.getArrivalOrderingRequirement().name()", boundary
        )
        for scalar in (
            "target.getGeneration()",
            "target.getPackedRegionX()",
            "target.getPackedRegionY()",
            "target.getSourceOrdinal()",
            "target.getConstructionKind()",
        ):
            self.assertIn(scalar, boundary)
        for forbidden in (
            "registerGameObject",
            "unregisterGameObject",
            "sendUpdatePackets",
            "eventStore.remove",
            "eventStore.add",
            "event.stop()",
            "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_executable_inventory_fixture_covers_complete_and_missing_binding(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        for evidence in (
            "getTargetBindingRequirementCapturedEventCount() == 2",
            "getTargetBindingCompleteEventCount() == 1",
            "getArrivalOrderingCapturedEventCount() == 2",
            "TargetSubject.AUTHORED_DESTINATION_SLOT",
            "TargetSubject.AUTHORED_EXISTING_ENTITY",
            "BindingEvidence.MISSING_AUTHORED_PLACEMENT_IDENTITY",
            "TargetConflictPolicy.REFUSE_MISMATCH_OR_AMBIGUITY",
            "RECONCILE_BEFORE_FIRST_VISIBILITY",
        ):
            self.assertIn(evidence, fixture)
        result = subprocess.run(
            ["python3", str(FIXTURE)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_current_v38_observer_exposes_only_inventory_values(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v49"', source)
        for present in (
            "getTargetBindingRequirementCapturedEventCount",
            "getTargetBindingCompleteEventCount",
            "getArrivalOrderingCapturedEventCount",
        ):
            self.assertIn(present, source)
        for absent in (
            "GameTickEventRestorationRequirement",
            "registerGameObject",
            "unregisterGameObject",
        ):
            self.assertNotIn(absent, source)

    def test_living_plan_records_slice_one_hundred_nine_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 109: Detached scenery target and arrival requirements",
            plan,
        )
        self.assertIn("schema-v37 and the observer remain unchanged", plan)
        self.assertIn("No target lookup is performed", plan)


if __name__ == "__main__":
    unittest.main()
