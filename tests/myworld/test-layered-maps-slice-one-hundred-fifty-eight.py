#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_V43 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v43.schema.json"
)
SCHEMA_V44 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v44.schema.json"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/"
    "Development.java"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


def ready_diagnostic():
    return {
        "reason": "NO_OP_VERIFICATION_READY",
        "preparationReason": "READY_BEFORE_RECONSTRUCTION",
        "lifecycleReason": "CONTRACTUALLY_READY_FOR_FIRST_VISIBILITY",
        "proposalGeneration": 7,
        "inventoryEventCount": 12,
        "recoveryCandidateCount": 1,
        "futureSnapshotCount": 1,
        "runtimeVerificationCount": 1,
        "mutationOperationCount": 0,
        "terminalEventConsumptionCount": 0,
        "reconstructionInvoked": True,
        "recoveryInvoked": True,
        "contractuallyReadyForFirstVisibility": True,
        "freshInventoryRetryRequired": False,
        "verificationOnly": True,
        "noOpReconstruction": True,
        "regionMutationAllowed": False,
        "overdueConsumptionAllowed": False,
        "regionLoadingPerformed": False,
        "retryPerformed": False,
        "arrivalGate": False,
        "visibilityReleased": False,
        "runtimeHandleRetained": False,
    }


class LayeredMapsSliceOneHundredFiftyEightTest(unittest.TestCase):
    def test_v43_is_immutable_and_v44_adds_explicit_no_op_diagnostic(self):
        v43 = json.loads(SCHEMA_V43.read_text(encoding="utf-8"))
        v44 = json.loads(SCHEMA_V44.read_text(encoding="utf-8"))
        name = "packedRegionEventRecoveryNoOp"
        self.assertEqual(
            "layered-map-parity-event-v43",
            v43["properties"]["schema"]["const"],
        )
        self.assertEqual(
            "layered-map-parity-event-v44",
            v44["properties"]["schema"]["const"],
        )
        self.assertNotIn(name, v43["required"])
        self.assertNotIn(name, v43["properties"])
        self.assertIn(name, v44["required"])
        self.assertIn(name, v44["properties"])
        self.assertIn("recovery-noop", v44["properties"]["eventType"]["enum"])
        self.assertFalse(v44["additionalProperties"])

    def test_v44_diagnostic_schema_accepts_only_closed_zero_side_effects(self):
        schema = json.loads(SCHEMA_V44.read_text(encoding="utf-8"))
        try:
            import jsonschema
        except ImportError:
            self.skipTest("jsonschema is not installed")
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$defs": schema["$defs"],
            "$ref": "#/$defs/recoveryNoOp",
        }
        jsonschema.Draft202012Validator.check_schema(isolated)
        validator = jsonschema.Draft202012Validator(isolated)
        validator.validate(ready_diagnostic())

        mutation = ready_diagnostic()
        mutation["mutationOperationCount"] = 1
        with self.assertRaises(jsonschema.ValidationError):
            validator.validate(mutation)

        overdue = ready_diagnostic()
        overdue.update({
            "reason": "NON_FUTURE_CANDIDATE_REFUSED",
            "lifecycleReason": None,
            "futureSnapshotCount": 0,
            "runtimeVerificationCount": 0,
            "reconstructionInvoked": False,
            "recoveryInvoked": False,
            "contractuallyReadyForFirstVisibility": False,
        })
        validator.validate(overdue)
        overdue["terminalEventConsumptionCount"] = 1
        with self.assertRaises(jsonschema.ValidationError):
            validator.validate(overdue)

    def test_route_is_explicit_opt_in_and_both_runtime_sources_match(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        handler = HANDLER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v44"', observer)
        self.assertIn('write(state, "recovery-noop"', observer)
        write = observer[
            observer.index("private static Status write("):
            observer.index("private static String eventJson(")
        ]
        condition = write.index('if ("recovery-noop".equals(eventType))')
        invocation = write.index(".captureRecoveryNoOp(", condition)
        self.assertLess(condition, invocation)
        self.assertEqual(1, write.count(".captureRecoveryNoOp("))
        self.assertIn("packedRegionEventRecoveryNoOp = null", write)
        self.assertIn(
            'out.append(",\\\"packedRegionEventRecoveryNoOp\\\":")',
            observer,
        )
        self.assertIn(
            "captureLayeredPackedRegionEventRecoveryNoOpDiagnostic(",
            handler,
        )
        for source in (player, development):
            self.assertIn("captureRecoveryNoOp(", source)
            self.assertIn(
                "captureLayeredPackedRegionEventRecoveryNoOpDiagnostic(",
                source,
            )
        command_start = development.index(
            "private void layeredCoordinateParity("
        )
        command_end = development.index(
            "private LayeredCoordinateParityObserver.TileSnapshotSource",
            command_start,
        )
        command = development[command_start:command_end]
        self.assertLess(
            command.index("WANT_LAYERED_MAP_PARITY_OBSERVER"),
            command.index('"recover-noop".equals(action)'),
        )
        self.assertIn("LayeredCoordinateParityObserver.recoverNoOp(", command)
        self.assertIn("recover-noop|stop", development)

    def test_living_docs_record_slice_one_hundred_fifty_eight(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v44.schema.json", readme)
        self.assertIn("::layerparity recover-noop", readme)
        self.assertIn("### Slice 158: Explicit private no-op recovery route", plan)
        normalized = " ".join(plan.split())
        self.assertIn("Ordinary movement, snapshots, and markers emit null", normalized)
        self.assertIn("requires the opt-in private parity capability", normalized)


if __name__ == "__main__":
    unittest.main()
