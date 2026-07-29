#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DTO = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventAtomicTargetRevalidation.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/"
    "Development.java"
)
SCHEMA_V42 = ROOT / (
    "tools/layered-maps/schema/"
    "layered-map-parity-event-v42.schema.json"
)
SCHEMA_V43 = ROOT / (
    "tools/layered-maps/schema/"
    "layered-map-parity-event-v43.schema.json"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


def sample_atomic_revalidation():
    return {
        "proposalGeneration": 7,
        "eventInventoryObservedAtTick": 100,
        "revalidationObservedAtTick": 100,
        "schedulerInstanceIdentity": "fixture-scheduler",
        "recordCount": 2,
        "outerFenceAcceptedCount": 1,
        "outerFenceRefusedCount": 1,
        "lifecycleChangeDetectedCount": 1,
        "runtimeTargetLookupPerformedCount": 2,
        "runtimeRevalidationPerformedCount": 1,
        "contractRefusedCount": 0,
        "noOpContractSatisfiedCount": 0,
        "mutationPreconditionContractSatisfiedCount": 1,
        "outerOutcomeCountComplete": True,
        "acceptedContractOutcomeCountComplete": True,
        "pointInTimeOnly": True,
        "atomicWithEventInventory": False,
        "runtimeTargetLookupPerformed": True,
        "runtimeRevalidationPerformed": True,
        "atomicWithMutation": False,
        "entityHandleRetained": False,
        "achievedStateClaimed": False,
        "commitToken": False,
        "mutationPerformed": False,
        "executableRestoration": False,
        "arrivalGate": False,
        "lifecycleAuthority": False,
        "records": [
            {
                "snapshotOrdinal": 1,
                "registrationSequence": 4,
                "packedX": 524,
                "packedY": 489,
                "outerFenceReason": "OPERATION_COMPLETED",
                "outerFenceAccepted": True,
                "operationInvoked": True,
                "lifecycleVersionBeforeOperation": 8,
                "lifecycleVersionAfterOperation": 8,
                "timingStableAcrossOperation": True,
                "lifecycleChangeDetected": False,
                "runtimeTargetLookupPerformed": True,
                "runtimeRevalidationPerformed": True,
                "target": {
                    "regionAvailable": True,
                    "slotObjectCount": 0,
                    "exactRestorationSceneryCount": 0,
                    "exactAuthoredIdentityCount": 0,
                    "objectBoundaryHeldDuringClassification": True,
                    "observedTargetState": "EMPTY",
                    "targetOutcome": "MUTATION_PRECONDITION_SATISFIED",
                    "targetReason": "SPAWN_DESTINATION_EMPTY",
                    "contractOutcome":
                        "MUTATION_PRECONDITION_CONTRACT_SATISFIED",
                    "contractReason": "MUTATION_PRECONDITION_REVALIDATED",
                },
            },
            {
                "snapshotOrdinal": 3,
                "registrationSequence": 9,
                "packedX": 600,
                "packedY": 600,
                "outerFenceReason":
                    "EVENT_LIFECYCLE_CHANGED_DURING_OPERATION",
                "outerFenceAccepted": False,
                "operationInvoked": True,
                "lifecycleVersionBeforeOperation": 11,
                "lifecycleVersionAfterOperation": 12,
                "timingStableAcrossOperation": False,
                "lifecycleChangeDetected": True,
                "runtimeTargetLookupPerformed": True,
                "runtimeRevalidationPerformed": False,
                "target": None,
            },
        ],
    }


class LayeredMapsSliceOneHundredTwentyFiveTest(unittest.TestCase):
    def test_v42_is_immutable_and_v43_adds_atomic_target_diagnostics(self):
        v42 = json.loads(SCHEMA_V42.read_text(encoding="utf-8"))
        v43 = json.loads(SCHEMA_V43.read_text(encoding="utf-8"))
        name = "packedRegionEventAtomicTargetRevalidation"
        self.assertEqual(
            "layered-map-parity-event-v42",
            v42["properties"]["schema"]["const"],
        )
        self.assertEqual(
            "layered-map-parity-event-v43",
            v43["properties"]["schema"]["const"],
        )
        self.assertNotIn(name, v42["required"])
        self.assertNotIn(name, v42["properties"])
        self.assertIn(name, v43["required"])
        self.assertIn(name, v43["properties"])
        self.assertFalse(v43["additionalProperties"])

    def test_v43_atomic_target_schema_accepts_closed_stable_and_race_records(self):
        schema = json.loads(SCHEMA_V43.read_text(encoding="utf-8"))
        try:
            import jsonschema
        except ImportError:
            self.skipTest("jsonschema is not installed")
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$defs": schema["$defs"],
            "$ref": "#/$defs/atomicTargetRevalidation",
        }
        jsonschema.Draft202012Validator.check_schema(isolated)
        validator = jsonschema.Draft202012Validator(isolated)
        sample = sample_atomic_revalidation()
        validator.validate(sample)
        unsafe = json.loads(json.dumps(sample))
        unsafe["records"][0]["mutationPerformed"] = True
        with self.assertRaises(jsonschema.ValidationError):
            validator.validate(unsafe)
        stale = json.loads(json.dumps(sample))
        stale["records"][1]["target"] = sample["records"][0]["target"]
        with self.assertRaises(jsonschema.ValidationError):
            validator.validate(stale)

    def test_handler_correlates_inventory_through_composed_store_seam(self):
        source = HANDLER.read_text(encoding="utf-8")
        start = source.index(
            "captureLayeredPackedRegionEventAtomicTargetRevalidation("
        )
        end = source.index("private EventState detachEventState(", start)
        method = source[start:end]
        for required in (
            "getRestorationStateAvailableEventCount()",
            "checked.getSchedulerInstanceIdentity()",
            "event.getRegistrationSequence()",
            "checked.getProposalGeneration()",
            "withValidatedRestorationTargetRevalidation(",
            "TargetEvidence.evidence(",
            "Record.record(",
        ):
            self.assertIn(required, method)
        self.assertNotIn("getEvents() ==", method)
        for forbidden in (
            "registerGameObject", "unregisterGameObject",
            "replaceGameObject", ".doRun()", ".stop()",
        ):
            self.assertNotIn(forbidden, method)

    def test_observer_and_player_emit_bounded_non_authoritative_v43(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        dto = DTO.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v49"', observer)
        for required in (
            "captureAtomicTargetRevalidation(",
            "requireAtomicEventTargetsMatchInventory(",
            "appendPackedRegionEventAtomicTargetRevalidation(",
            "packedRegionEventAtomicTargetRevalidation",
        ):
            self.assertIn(required, observer)
        self.assertIn(
            "captureLayeredPackedRegionEventAtomicTargetRevalidation(",
            player,
        )
        command_source_start = development.index(
            "layeredPackedRegionEventOwnershipSource(final Player player)"
        )
        command_source_end = development.index(
            "private List<LayeredCoordinateParityObserver.RegionResidencyCandidateMetadata>",
            command_source_start,
        )
        command_source = development[command_source_start:command_source_end]
        self.assertIn(
            "captureLayeredPackedRegionEventAtomicTargetRevalidation(",
            command_source,
        )
        for required in (
            "isAtomicWithMutation() { return false; }",
            "isEntityHandleRetained() { return false; }",
            "isAchievedStateClaimed() { return false; }",
            "isCommitToken() { return false; }",
            "isMutationPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, dto)
        self.assertIn("layered-map-parity-event-v43.schema.json", readme)

    def test_living_plan_records_slice_one_hundred_twenty_five(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 125: Private composed-target diagnostics", plan
        )
        self.assertIn("additive private schema-v43", plan)
        self.assertIn("broad human timing", plan)


if __name__ == "__main__":
    unittest.main()
