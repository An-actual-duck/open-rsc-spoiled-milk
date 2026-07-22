#!/usr/bin/env python3
import json
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
SCHEMA_V38 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v38.schema.json"
)
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-ninety-three.py"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredTwelveTest(unittest.TestCase):
    def test_inventory_detaches_generation_and_idempotency_rules(self):
        source = INVENTORY.read_text(encoding="utf-8")
        for declaration in (
            "enum GenerationBindingRequirement",
            "MATCH_RECONSTRUCTION_GENERATION",
            "enum DesiredState",
            "AUTHORED_SCENERY_PRESENT",
            "AUTHORED_SCENERY_ABSENT",
            "enum IdempotencyPolicy",
            "ALREADY_SATISFIED_IS_NO_OP_SUCCESS",
            "enum MutationPrecondition",
            "DESTINATION_SLOT_EMPTY",
            "EXACT_AUTHORED_ENTITY_PRESENT",
        ):
            self.assertIn(declaration, source)
        for method in (
            "getGenerationBindingRequirementCapturedEventCount()",
            "isGenerationBindingRequirementComplete()",
            "getGenerationBindingCompleteEventCount()",
            "isGenerationBindingComplete()",
            "getIdempotencyRequirementCapturedEventCount()",
            "isIdempotencyRequirementComplete()",
        ):
            self.assertIn(method, source)
        self.assertIn(
            "isGenerationBindingComplete(proposalGeneration)", source
        )

    def test_handler_copies_only_detached_requirement_values(self):
        source = HANDLER.read_text(encoding="utf-8")
        start = source.index("detachEventRestorationState(")
        end = source.index("public boolean hasEvent", start)
        boundary = source[start:end]
        for method in (
            "requirement.getGenerationBindingRequirement().name()",
            "requirement.getDesiredState().name()",
            "requirement.getIdempotencyPolicy().name()",
            "requirement.getMutationPrecondition().name()",
        ):
            self.assertIn(method, boundary)
        for forbidden in (
            "registerGameObject",
            "unregisterGameObject",
            "getGameObject",
            "sendUpdatePackets",
            "eventStore.remove",
            "eventStore.add",
            "event.stop()",
            "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_executable_inventory_fixture_covers_match_and_mismatch(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        for evidence in (
            "getGenerationBindingRequirementCapturedEventCount() == 2",
            "getGenerationBindingCompleteEventCount() == 1",
            "getIdempotencyRequirementCapturedEventCount() == 2",
            ".isGenerationBindingComplete(7L)",
            ".isGenerationBindingComplete(9L)",
            "DesiredState.AUTHORED_SCENERY_PRESENT",
            "DesiredState.AUTHORED_SCENERY_ABSENT",
            "MutationPrecondition.DESTINATION_SLOT_EMPTY",
            "MutationPrecondition.EXACT_AUTHORED_ENTITY_PRESENT",
        ):
            self.assertIn(evidence, fixture)
        result = subprocess.run(
            ["python3", str(FIXTURE)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_schema_v38_history_and_living_plan_keep_boundary(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        schema_v38 = json.loads(SCHEMA_V38.read_text(encoding="utf-8"))
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v39"', observer)
        for absent in (
            "generationBindingRequirementCapturedEventCount",
            "generationBindingCompleteEventCount",
            "idempotencyRequirementCapturedEventCount",
            "desiredState",
            "mutationPrecondition",
        ):
            self.assertNotIn(absent, json.dumps(schema_v38))
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 112: Detached generation and idempotency prerequisites",
            plan,
        )
        self.assertIn("schema-v38 and the observer remain unchanged", plan)


if __name__ == "__main__":
    unittest.main()
