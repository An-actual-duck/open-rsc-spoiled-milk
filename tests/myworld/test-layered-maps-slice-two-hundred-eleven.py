#!/usr/bin/env python3
import copy
import json
import runpy
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "tools/layered-maps/schema"
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v59.schema.json"
SCHEMA_V58 = SCHEMA_DIR / "layered-map-parity-event-v58.schema.json"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameEventHandler.java"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_207 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-two-hundred-seven.py"
)))


class LayeredMapsSliceTwoHundredElevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.v58 = json.loads(SCHEMA_V58.read_text(encoding="utf-8"))
        resources = []
        for path in SCHEMA_DIR.glob("*.schema.json"):
            data = json.loads(path.read_text(encoding="utf-8"))
            if "$id" in data and path != SCHEMA:
                resources.append(
                    (data["$id"], Resource.from_contents(data))
                )
        registry = Registry().with_resources(resources)
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": (
                "https://spoiled-milk.invalid/schema/"
                "layered-map-parity-event-v59-isolated.schema.json"
            ),
            "$defs": cls.schema["$defs"],
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        cls.validator = Draft202012Validator(
            isolated, registry=registry
        )

        previous_test = SLICE_207[
            "LayeredMapsSliceTwoHundredSevenTest"
        ]
        previous_test.setUpClass()
        cls.evidence = copy.deepcopy(previous_test.evidence)
        correlation = cls.evidence[
            "sourceAuthoredDetachmentSchedulerCorrelation"
        ]
        outcomes = (
            (
                "CANDIDATE_NPC_OWNER_UNCORRELATED",
                "com.openrsc.server.event.rsc.NpcEvent",
                "NPC", "OWNER_POSITION_HINT", 1, 1,
            ),
            (
                "CANDIDATE_NON_NPC_OWNER",
                "com.openrsc.server.event.rsc.PlayerEvent",
                "PLAYER", "OWNER_POSITION_HINT", 1, 1,
            ),
            (
                "CANDIDATE_EXACT_RESTORATION_INCOMPLETE",
                "com.openrsc.server.event.rsc.ObjectEvent",
                "NONE", "EXACT_SPATIAL", 1, 1,
            ),
            (
                "UNATTRIBUTED_BLOCKER",
                "com.openrsc.server.model.world.World$1",
                "NONE", "UNATTRIBUTED", 1, 0,
            ),
        )
        families = []
        for ordinal, (
            outcome, runtime_type, owner, attribution, event_count,
            source_references,
        ) in enumerate(outcomes):
            sequence = 101 + ordinal
            families.append({
                "familyOrdinal": ordinal,
                "outcome": outcome,
                "runtimeTypeName": runtime_type,
                "familyTypeName": runtime_type,
                "directSupertypeName": (
                    "com.openrsc.server.event.rsc.GameTickEvent"
                ),
                "anonymousType": runtime_type.endswith("$1"),
                "localType": False,
                "syntheticType": False,
                "ownerKind": owner,
                "attributionKind": attribution,
                "restorationKind": "UNAVAILABLE",
                "eventCount": event_count,
                "runningEventCount": 0,
                "candidateRelatedEventCount": (
                    1 if source_references else 0
                ),
                "selectedSourceReferenceCount": source_references,
                "firstSnapshotOrdinal": ordinal,
                "lastSnapshotOrdinal": ordinal,
                "firstRegistrationSequence": sequence,
                "lastRegistrationSequence": sequence,
                "minimumTicksBeforeRun": ordinal - 2,
                "maximumTicksBeforeRun": ordinal - 2,
                "minimumTimesRan": 0,
                "maximumTimesRan": ordinal,
            })
        cls.evidence["sourceSchedulerBlockerFamilyInventory"] = {
            "generation": correlation["generation"],
            "eventObservedAtTick": correlation["eventObservedAtTick"],
            "schedulerInstanceIdentity": (
                correlation["schedulerInstanceIdentity"]
            ),
            "sourceCorrelationFingerprintSha256": (
                correlation["fingerprintSha256"]
            ),
            "fingerprintSha256": f"{1011:064x}",
            "familyCount": 4,
            "blockerEventCount": 4,
            "candidateNpcOwnerUncorrelatedEventCount": 1,
            "candidateNonNpcOwnerEventCount": 1,
            "candidateExactRestorationIncompleteEventCount": 1,
            "unattributedEventCount": 1,
            "runningEventCount": 0,
            "candidateRelatedEventCount": 3,
            "selectedSourceReferenceCount": 3,
            "eventTypeIdentityComplete": True,
            "pointInTimeOnly": True,
            "detachedSummaryOnly": True,
            "attributionChanged": False,
            "runtimeHandleRetained": False,
            "eventCancellation": False,
            "eventReschedule": False,
            "preservationPerformed": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "runtimeMutationAuthorized": False,
            "runtimeMutationPerformed": False,
            "arrivalGate": False,
            "visibilityReleased": False,
            "lifecycleAuthority": False,
            "families": families,
        }

    def test_schema_v59_extends_only_private_preservation_evidence(self):
        self.validator.validate(self.evidence)
        self.assertNotIn(
            "sourceSchedulerBlockerFamilyInventory",
            self.v58["$defs"]["npcOwnerPreservationNoOp"]["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v59",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertEqual(
            set(self.v58["properties"]), set(self.schema["properties"])
        )

    def test_refusal_and_family_contracts_are_fail_closed(self):
        refused = copy.deepcopy(self.evidence)
        refused["reason"] = "OWNER_SCOPE_REFUSED"
        for key in (
            "sourceAbsencePreflight",
            "sourceReloadRecipe",
            "sourceTerrainVerification",
            "sourceAuthoredCollisionVerification",
            "sourceAuthoredCollisionApplicationVerification",
            "sourceAuthoredStateVerification",
            "sourceTransactionalAuthoredStateVerification",
            "sourceRuntimeAuthoredObjectObservation",
            "sourceRuntimeAuthoredObjectBaselineComparison",
            "sourceAuthoredObjectDetachmentVerification",
            "sourceAuthoredDetachmentSchedulerCorrelation",
            "sourceSchedulerBlockerFamilyInventory",
        ):
            refused[key] = None
        refused["ownerScopeEntered"] = False
        refused["sourceLifecycleInvoked"] = False
        self.validator.validate(refused)

        invalid = copy.deepcopy(self.evidence)
        invalid["sourceSchedulerBlockerFamilyInventory"][
            "attributionChanged"
        ] = True
        with self.assertRaises(Exception):
            self.validator.validate(invalid)

        invalid = copy.deepcopy(self.evidence)
        invalid["sourceSchedulerBlockerFamilyInventory"]["families"][0][
            "runtimeTypeName"
        ] = "invalid\nname"
        with self.assertRaises(Exception):
            self.validator.validate(invalid)

    def test_runtime_wiring_reduces_after_exact_correlation(self):
        handler = HANDLER.read_text(encoding="utf-8")
        boundary = handler[handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        ):handler.index(
            "private void requireExactPackedSourceBoundary("
        )]
        self.assertLess(
            boundary.index("authoredDetachmentSchedulerCorrelation[0] ="),
            boundary.index("schedulerBlockerFamilyInventory[0] ="),
        )
        self.assertIn(
            "LayeredPackedRegionSchedulerBlockerFamilyInventory\n"
            "\t\t\t\t\t\t\t\t.reduce(",
            boundary,
        )
        self.assertIn("checkedInventory,", boundary)

    def test_json_exposes_bounded_families_without_event_payloads(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        for required in (
            'EVENT_SCHEMA = "layered-map-parity-event-v59"',
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v58"',
            '\\"sourceSchedulerBlockerFamilyInventory\\":',
            "appendPackedRegionSchedulerBlockerFamilyInventory(",
            "getSourceSchedulerBlockerFamilyInventory()",
            'field(out, "runtimeTypeName"',
            'field(out, "familyTypeName"',
            '\\"firstRegistrationSequence\\"',
        ):
            self.assertIn(required, observer)
        serializer = observer.split(
            "appendPackedRegionSchedulerBlockerFamilyInventory(", 1
        )[1].split(
            "private static void appendPackedRegionEventTargets(", 1
        )[0]
        for forbidden in (
            "GameTickEvent ", "Class<?>", "callbackPayload",
            "eventPayload", "ownerHandle", "schedulerHandle",
        ):
            self.assertNotIn(forbidden, serializer)

    def test_readme_and_plan_record_slice_211(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "schema/layered-map-parity-event-v59.schema.json", readme
        )
        self.assertIn("sourceSchedulerBlockerFamilyInventory", readme)
        self.assertIn(
            "### Slice 211: Private scheduler-blocker family diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
