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
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v57.schema.json"
SCHEMA_V56 = SCHEMA_DIR / "layered-map-parity-event-v56.schema.json"
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
SLICE_201 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-two-hundred-one.py"
)))


class LayeredMapsSliceTwoHundredFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.v56 = json.loads(SCHEMA_V56.read_text(encoding="utf-8"))
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
                "layered-map-parity-event-v57-isolated.schema.json"
            ),
            "$defs": cls.schema["$defs"],
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        cls.validator = Draft202012Validator(
            isolated, registry=registry
        )

        previous_test = SLICE_201[
            "LayeredMapsSliceTwoHundredOneTest"
        ]
        previous_test.setUpClass()
        cls.evidence = copy.deepcopy(previous_test.evidence)
        transactional = cls.evidence[
            "sourceTransactionalAuthoredStateVerification"
        ]
        sources = []
        totals = {
            "replayPlacementCount": 0,
            "authoredObjectCount": 0,
            "disposableRegionConstructionCount": 0,
            "supportRegionCount": 0,
            "reconstructionTransactionCount": 0,
            "reconstructionBoundaryCount": 0,
            "reconstructionCacheInvalidationCount": 0,
            "detachmentTransactionCount": 0,
            "detachmentBoundaryCount": 0,
            "detachmentCacheInvalidationCount": 0,
            "collisionRegistrationCount": 0,
            "collisionRegistrationClearedCount": 0,
            "collisionContributionReferenceCount": 0,
            "collisionRegionReferenceCount": 0,
            "verifiedRegionTileCount": 0,
        }
        plan_fingerprint = f"{601:064x}"
        for source in transactional["sources"]:
            objects = source["authoredObjectFootprintCount"]
            region_refs = source[
                "collisionRegistrationRegionReferenceCount"
            ]
            detachment_source = {
                "sourceOrdinal": source["sourceOrdinal"],
                "packedRegionX": source["packedRegionX"],
                "packedRegionY": source["packedRegionY"],
                "replayPlacementCount": source["replayPlacementCount"],
                "authoredObjectCount": objects,
                "disposableRegionConstructionCount": (
                    source["disposableRegionConstructionCount"]
                ),
                "supportRegionCount": source["supportRegionCount"],
                "reconstructionTransactionCount": objects,
                "reconstructionBoundaryCount": region_refs,
                "reconstructionCacheInvalidationCount": objects,
                "detachmentTransactionCount": objects,
                "detachmentBoundaryCount": region_refs,
                "detachmentCacheInvalidationCount": objects,
                "collisionRegistrationCount": objects,
                "collisionRegistrationClearedCount": objects,
                "collisionContributionReferenceCount": (
                    source["collisionRegistrationContributionCount"]
                ),
                "collisionRegionReferenceCount": region_refs,
                "verifiedRegionTileCount": (
                    source["verifiedRegionTileCount"]
                ),
                "terrainFingerprintSha256": (
                    source["terrainFingerprintSha256"]
                ),
                "authoredReplayFingerprintSha256": (
                    source["authoredReplayFingerprintSha256"]
                ),
                "collisionFootprintFingerprintSha256": (
                    source["collisionFootprintFingerprintSha256"]
                ),
                "detachmentPlanFingerprintSha256": (
                    f"{source['sourceOrdinal'] + 602:064x}"
                ),
                "preDetachmentRegistrationFingerprintSha256": (
                    source["collisionRegistrationFingerprintSha256"]
                ),
                "preDetachmentStateFingerprintSha256": (
                    source["finalStateFingerprintSha256"]
                ),
                "postDetachmentStateFingerprintSha256": (
                    f"{source['sourceOrdinal'] + 702:064x}"
                ),
                "fingerprintSha256": (
                    f"{source['sourceOrdinal'] + 802:064x}"
                ),
            }
            sources.append(detachment_source)
            for key in totals:
                totals[key] += detachment_source[key]

        cls.evidence["sourceAuthoredObjectDetachmentVerification"] = {
            "generation": transactional["generation"],
            "requirementsObservedAtTick": (
                transactional["requirementsObservedAtTick"]
            ),
            "observedAtTick": transactional["observedAtTick"],
            "runtimeObservedAtTick": cls.evidence[
                "sourceRuntimeAuthoredObjectBaselineComparison"
            ]["runtimeObservedAtTick"],
            "residencyMirrorVersion": (
                transactional["residencyMirrorVersion"]
            ),
            "authoredGeneration": transactional["authoredGeneration"],
            "sourceCount": transactional["sourceCount"],
            **totals,
            "detachmentPlanFingerprintSha256": plan_fingerprint,
            "fingerprintSha256": f"{902:064x}",
            "allSourcesVerified": True,
            "pointInTimeOnly": True,
            "detachedSummaryOnly": True,
            "disposableReconstructionPerformed": True,
            "disposableDetachmentPerformed": True,
            "runtimeHandleRetained": False,
            "runtimeSourceMutated": False,
            "runtimeCollisionMutated": False,
            "runtimeCacheInvalidated": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "schedulerCorrelationPerformed": False,
            "activeFamilyPreservationPerformed": False,
            "regionRegistryMutated": False,
            "residencyMirrorMutated": False,
            "visibilityCacheMutated": False,
            "arrivalGate": False,
            "visibilityReleased": False,
            "lifecycleAuthority": False,
            "sources": sources,
        }

    def test_schema_v57_extends_only_private_preservation_evidence(self):
        self.validator.validate(self.evidence)
        self.assertNotIn(
            "sourceAuthoredObjectDetachmentVerification",
            self.v56["$defs"]["npcOwnerPreservationNoOp"]["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v57",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertEqual(
            set(self.v56["properties"]), set(self.schema["properties"])
        )

    def test_owner_refusal_requires_detachment_evidence_to_be_null(self):
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
        ):
            refused[key] = None
        refused["ownerScopeEntered"] = False
        refused["sourceLifecycleInvoked"] = False
        self.validator.validate(refused)

    def test_private_capture_orders_disposable_detachment_after_baseline(self):
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        start = handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        )
        boundary = handler[start:handler.index(
            "private void requireExactPackedSourceBoundary(", start
        )]
        self.assertLess(
            boundary.index(
                "runtimeAuthoredObjectBaselineComparison[0] ="
            ),
            boundary.index("authoredObjectDetachmentPlan[0] ="),
        )
        self.assertLess(
            boundary.index("authoredObjectDetachmentPlan[0] ="),
            boundary.index(
                "authoredObjectDetachmentVerification[0] ="
            ),
        )
        self.assertLess(
            boundary.index(
                "authoredObjectDetachmentVerification[0] ="
            ),
            boundary.index("captured[0] ="),
        )
        for required in (
            'EVENT_SCHEMA = "layered-map-parity-event-v59"',
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v58"',
            '\\"sourceAuthoredObjectDetachmentVerification\\":',
            "appendPackedRegionAuthoredObjectDetachmentVerificationBatch(",
            "getSourceAuthoredObjectDetachmentVerification()",
            "isDisposableReconstructionPerformed()",
            "isDisposableDetachmentPerformed()",
            "isRuntimeSourceMutated()",
            "isRuntimeCollisionMutated()",
        ):
            self.assertIn(required, observer)

    def test_detachment_evidence_reconciles_transactional_baseline(self):
        detachment = self.evidence[
            "sourceAuthoredObjectDetachmentVerification"
        ]
        baseline = self.evidence[
            "sourceTransactionalAuthoredStateVerification"
        ]
        self.assertEqual(
            baseline["authoredObjectFootprintCount"],
            detachment["authoredObjectCount"],
        )
        self.assertEqual(
            baseline["collisionRegistrationContributionCount"],
            detachment["collisionContributionReferenceCount"],
        )
        self.assertEqual(
            baseline["collisionRegistrationRegionReferenceCount"],
            detachment["collisionRegionReferenceCount"],
        )
        for detached, expected in zip(
            detachment["sources"], baseline["sources"]
        ):
            self.assertEqual(
                expected["collisionFootprintFingerprintSha256"],
                detached["collisionFootprintFingerprintSha256"],
            )
            self.assertEqual(
                expected["collisionRegistrationFingerprintSha256"],
                detached[
                    "preDetachmentRegistrationFingerprintSha256"
                ],
            )

    def test_readme_and_plan_record_slice_205(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "schema/layered-map-parity-event-v57.schema.json", readme
        )
        self.assertIn(
            "sourceAuthoredObjectDetachmentVerification", readme
        )
        self.assertIn(
            "### Slice 205: Private disposable detachment diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
