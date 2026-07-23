#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_V44 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v44.schema.json"
)
SCHEMA_V45 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v45.schema.json"
)
PREPARATION = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventRestorationLivePreparationCoordinator.java"
)
DIAGNOSTIC = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventRestorationNoOpDiagnostic.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
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
        "proposalRelatedEventCount": 1,
        "recoveryCompleteEventCount": 1,
        "recoveryIncompleteEventCount": 0,
        "incompleteOwnerPositionHintEventCount": 0,
        "incompleteExactSpatialEventCount": 0,
        "firstIncompleteRegistrationSequence": None,
        "firstIncompleteOwnerKind": None,
        "firstIncompleteAttributionKind": None,
        "firstIncompleteRecoveryRequirement": None,
        "preflightComplete": True,
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


def blocked_diagnostic():
    value = ready_diagnostic()
    value.update({
        "reason": "LIVE_CAPTURE_REFUSED",
        "preparationReason": "RELATED_EVENT_RECOVERY_INCOMPLETE",
        "lifecycleReason": None,
        "inventoryEventCount": 3882,
        "recoveryCandidateCount": 1,
        "proposalRelatedEventCount": 1001,
        "recoveryCompleteEventCount": 1,
        "recoveryIncompleteEventCount": 1000,
        "incompleteOwnerPositionHintEventCount": 1000,
        "incompleteExactSpatialEventCount": 0,
        "firstIncompleteRegistrationSequence": 320,
        "firstIncompleteOwnerKind": "NPC",
        "firstIncompleteAttributionKind": "OWNER_POSITION_HINT",
        "firstIncompleteRecoveryRequirement":
            "RESTORATION_STATE_UNAVAILABLE",
        "preflightComplete": False,
        "futureSnapshotCount": 0,
        "runtimeVerificationCount": 0,
        "reconstructionInvoked": False,
        "recoveryInvoked": False,
        "contractuallyReadyForFirstVisibility": False,
    })
    return value


class LayeredMapsSliceOneHundredFiftyNineTest(unittest.TestCase):
    def test_v44_is_immutable_and_v45_adds_recovery_preflight(self):
        v44 = json.loads(SCHEMA_V44.read_text(encoding="utf-8"))
        v45 = json.loads(SCHEMA_V45.read_text(encoding="utf-8"))
        self.assertEqual(
            "layered-map-parity-event-v44",
            v44["properties"]["schema"]["const"],
        )
        self.assertEqual(
            "layered-map-parity-event-v45",
            v45["properties"]["schema"]["const"],
        )
        old = v44["$defs"]["recoveryNoOp"]["properties"]
        new = v45["$defs"]["recoveryNoOp"]["properties"]
        self.assertNotIn("proposalRelatedEventCount", old)
        for name in (
            "proposalRelatedEventCount",
            "recoveryCompleteEventCount",
            "recoveryIncompleteEventCount",
            "incompleteOwnerPositionHintEventCount",
            "incompleteExactSpatialEventCount",
            "firstIncompleteRegistrationSequence",
            "firstIncompleteOwnerKind",
            "firstIncompleteAttributionKind",
            "firstIncompleteRecoveryRequirement",
            "preflightComplete",
        ):
            self.assertIn(name, new)
            self.assertIn(name, v45["$defs"]["recoveryNoOp"]["required"])

    def test_v45_accepts_closed_ready_and_blocked_preflights(self):
        schema = json.loads(SCHEMA_V45.read_text(encoding="utf-8"))
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
        validator.validate(blocked_diagnostic())

        inconsistent = blocked_diagnostic()
        inconsistent["preflightComplete"] = True
        with self.assertRaises(jsonschema.ValidationError):
            validator.validate(inconsistent)

        incomplete_ready = ready_diagnostic()
        incomplete_ready["firstIncompleteRegistrationSequence"] = 320
        with self.assertRaises(jsonschema.ValidationError):
            validator.validate(incomplete_ready)

        mutation = blocked_diagnostic()
        mutation["mutationOperationCount"] = 1
        with self.assertRaises(jsonschema.ValidationError):
            validator.validate(mutation)

    def test_runtime_preflight_is_stable_bounded_and_fail_closed(self):
        preparation = PREPARATION.read_text(encoding="utf-8")
        diagnostic = DIAGNOSTIC.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn("static RecoveryPreflight assessRecovery(", preparation)
        self.assertIn("for (EventRecord event : checked.getEvents())", preparation)
        self.assertIn("RecoveryRequirement.RESTORATION_STATE_UNAVAILABLE", preparation)
        self.assertIn("RecoveryRequirement.GENERATION_BINDING_MISMATCH", preparation)
        self.assertIn("if (!preflight.isComplete())", preparation)
        self.assertIn("Reason.RELATED_EVENT_RECOVERY_INCOMPLETE", preparation)
        self.assertIn("isRuntimeHandleRetained() { return false; }", preparation)
        self.assertIn("getProposalRelatedEventCount()", diagnostic)
        self.assertIn("getFirstIncompleteRecoveryRequirement()", diagnostic)
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v48"',
            observer,
        )
        for field in (
            "proposalRelatedEventCount",
            "recoveryCompleteEventCount",
            "recoveryIncompleteEventCount",
            "firstIncompleteRecoveryRequirement",
            "preflightComplete",
        ):
            self.assertIn(field, observer)

    def test_living_docs_record_slice_one_hundred_fifty_nine(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v45.schema.json", readme)
        self.assertIn("proposal-wide recovery", readme)
        self.assertIn(
            "### Slice 159: Proposal event-recovery preflight",
            plan,
        )
        normalized = " ".join(plan.split())
        self.assertIn(
            "can no longer be confused with either the number examined before refusal",
            normalized,
        )


if __name__ == "__main__":
    unittest.main()
