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
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v58.schema.json"
SCHEMA_V57 = SCHEMA_DIR / "layered-map-parity-event-v57.schema.json"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameEventHandler.java"
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
SLICE_205 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-two-hundred-five.py"
)))


class LayeredMapsSliceTwoHundredSevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.v57 = json.loads(SCHEMA_V57.read_text(encoding="utf-8"))
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
                "layered-map-parity-event-v58-isolated.schema.json"
            ),
            "$defs": cls.schema["$defs"],
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        cls.validator = Draft202012Validator(
            isolated, registry=registry
        )

        previous_test = SLICE_205[
            "LayeredMapsSliceTwoHundredFiveTest"
        ]
        previous_test.setUpClass()
        cls.evidence = copy.deepcopy(previous_test.evidence)
        detachment = cls.evidence[
            "sourceAuthoredObjectDetachmentVerification"
        ]
        sources = []
        for ordinal, detached in enumerate(detachment["sources"]):
            populated = ordinal == 0
            sources.append({
                "sourceOrdinal": detached["sourceOrdinal"],
                "packedRegionX": detached["packedRegionX"],
                "packedRegionY": detached["packedRegionY"],
                "npcOwnerFenceEventCount": 7 if populated else 0,
                "exactAuthoredRestorationEventCount": (
                    2 if populated else 0
                ),
                "npcOwnerUncorrelatedEventCount": (
                    1 if populated else 0
                ),
                "nonNpcOwnerEventCount": 1 if populated else 0,
                "exactRestorationIncompleteEventCount": (
                    1 if populated else 0
                ),
                "blockerEventReferenceCount": 3 if populated else 0,
            })
        cls.evidence[
            "sourceAuthoredDetachmentSchedulerCorrelation"
        ] = {
            "generation": detachment["generation"],
            "eventObservedAtTick": cls.evidence[
                "requirementsObservedAtTick"
            ],
            "detachmentRuntimeObservedAtTick": (
                detachment["runtimeObservedAtTick"]
            ),
            "schedulerInstanceIdentity": (
                "00000000-0000-0000-0000-000000000207"
            ),
            "detachmentPlanFingerprintSha256": (
                detachment["detachmentPlanFingerprintSha256"]
            ),
            "fingerprintSha256": f"{1007:064x}",
            "sourceCount": detachment["sourceCount"],
            "eventCount": 120,
            "retainedEventCount": 14,
            "npcOwnerFenceEventCount": 8,
            "relatedNpcOwnerFenceEventCount": 7,
            "supportingNpcOwnerFenceEventCount": 1,
            "exactAuthoredRestorationEventCount": 2,
            "candidateNpcOwnerUncorrelatedEventCount": 1,
            "candidateNonNpcOwnerEventCount": 1,
            "candidateExactRestorationIncompleteEventCount": 1,
            "unattributedEventCount": 1,
            "outsideSelectionOwnerHintEventCount": 104,
            "outsideSelectionExactSpatialEventCount": 1,
            "nonSpatialGlobalEventCount": 2,
            "blockerEventCount": 4,
            "allSchedulerEventsClassified": True,
            "detachedSchedulerCorrelationComplete": False,
            "schedulerCorrelationPerformed": True,
            "pointInTimeOnly": True,
            "detachedSummaryOnly": True,
            "runtimeDetachmentReady": False,
            "schedulerBoundaryEntered": False,
            "schedulerIdentityRetained": False,
            "callbackRetained": False,
            "runtimeHandleRetained": False,
            "eventCancellation": False,
            "eventReschedule": False,
            "preservationPerformed": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "runtimeMutationAuthorized": False,
            "runtimeMutationPerformed": False,
            "regionRegistryMutated": False,
            "residencyMirrorMutated": False,
            "visibilityCacheMutated": False,
            "arrivalGate": False,
            "visibilityReleased": False,
            "lifecycleAuthority": False,
            "sources": sources,
        }

    def test_schema_v58_extends_only_private_preservation_evidence(self):
        self.validator.validate(self.evidence)
        self.assertNotIn(
            "sourceAuthoredDetachmentSchedulerCorrelation",
            self.v57["$defs"]["npcOwnerPreservationNoOp"]["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v58",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertEqual(
            set(self.v57["properties"]), set(self.schema["properties"])
        )

    def test_owner_refusal_requires_scheduler_correlation_to_be_null(self):
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
        ):
            refused[key] = None
        refused["ownerScopeEntered"] = False
        refused["sourceLifecycleInvoked"] = False
        self.validator.validate(refused)

    def test_private_capture_reuses_exact_event_inventory(self):
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        start = handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        )
        boundary = handler[start:handler.index(
            "private void requireExactPackedSourceBoundary(", start
        )]
        self.assertLess(
            boundary.index("authoredObjectDetachmentVerification[0] ="),
            boundary.index("authoredDetachmentSchedulerCorrelation[0] ="),
        )
        self.assertLess(
            boundary.index("authoredDetachmentSchedulerCorrelation[0] ="),
            boundary.index("captured[0] ="),
        )
        self.assertIn(
            "authoredObjectDetachmentPlan[0],\n"
            "\t\t\t\t\t\t\t\t\tcheckedInventory, checked,",
            boundary,
        )
        for source in (observer, player, development):
            self.assertIn(
                "captureNpcOwnerPreservationNoOp(", source
            )
            self.assertIn(
                "final LayeredPackedRegionEventOwnershipInventory inventory,",
                source,
            )
        for required in (
            'EVENT_SCHEMA = "layered-map-parity-event-v60"',
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v59"',
            '\\"sourceAuthoredDetachmentSchedulerCorrelation\\":',
            "appendPackedRegionAuthoredDetachmentSchedulerCorrelation(",
            "getSourceAuthoredDetachmentSchedulerCorrelation()",
            "areAllSchedulerEventsClassified()",
            "isRuntimeDetachmentReady()",
        ):
            self.assertIn(required, observer)

    def test_scheduler_summary_reconciles_sources_and_blockers(self):
        correlation = self.evidence[
            "sourceAuthoredDetachmentSchedulerCorrelation"
        ]
        detachment = self.evidence[
            "sourceAuthoredObjectDetachmentVerification"
        ]
        self.assertEqual(
            correlation["npcOwnerFenceEventCount"],
            (
                correlation["relatedNpcOwnerFenceEventCount"]
                + correlation["supportingNpcOwnerFenceEventCount"]
            ),
        )
        self.assertEqual(
            correlation["blockerEventCount"],
            (
                correlation["candidateNpcOwnerUncorrelatedEventCount"]
                + correlation["candidateNonNpcOwnerEventCount"]
                + correlation[
                    "candidateExactRestorationIncompleteEventCount"
                ]
                + correlation["unattributedEventCount"]
            ),
        )
        self.assertEqual(
            correlation["detachmentPlanFingerprintSha256"],
            detachment["detachmentPlanFingerprintSha256"],
        )
        for scheduler, detached in zip(
            correlation["sources"], detachment["sources"]
        ):
            self.assertEqual(
                (
                    scheduler["sourceOrdinal"],
                    scheduler["packedRegionX"],
                    scheduler["packedRegionY"],
                ),
                (
                    detached["sourceOrdinal"],
                    detached["packedRegionX"],
                    detached["packedRegionY"],
                ),
            )

    def test_readme_and_plan_record_slice_207(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "schema/layered-map-parity-event-v58.schema.json", readme
        )
        self.assertIn(
            "sourceAuthoredDetachmentSchedulerCorrelation", readme
        )
        self.assertIn(
            "### Slice 207: Private detachment-scheduler diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
