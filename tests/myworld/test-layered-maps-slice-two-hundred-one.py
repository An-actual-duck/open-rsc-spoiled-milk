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
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v56.schema.json"
SCHEMA_V55 = SCHEMA_DIR / "layered-map-parity-event-v55.schema.json"
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
SLICE_198 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-ninety-eight.py"
)))


class LayeredMapsSliceTwoHundredOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.v55 = json.loads(SCHEMA_V55.read_text(encoding="utf-8"))
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
                "layered-map-parity-event-v56-isolated.schema.json"
            ),
            "$defs": cls.schema["$defs"],
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        cls.validator = Draft202012Validator(isolated, registry=registry)

        previous_test = SLICE_198[
            "LayeredMapsSliceOneHundredNinetyEightTest"
        ]
        previous_test.setUpClass()
        cls.evidence = copy.deepcopy(previous_test.evidence)
        transactional = cls.evidence[
            "sourceTransactionalAuthoredStateVerification"
        ]
        observation_sources = []
        comparison_sources = []
        observed_objects = 0
        for source in transactional["sources"]:
            expected = source["authoredObjectFootprintCount"]
            observed_objects += expected
            observation_sources.append({
                "sourceOrdinal": source["sourceOrdinal"],
                "packedRegionX": source["packedRegionX"],
                "packedRegionY": source["packedRegionY"],
                "expectedAuthoredObjectCount": expected,
                "observedObjectCount": expected,
                "identitylessDynamicObjectCount": 0,
                "authoredIdentityObjectCount": expected,
                "recognizedAuthoredInstanceCount": expected,
                "unrecognizedAuthoredInstanceCount": 0,
                "staleGenerationInstanceCount": 0,
                "nonObjectIdentityInstanceCount": 0,
                "unknownRecipeIdentityInstanceCount": 0,
                "uniqueRecognizedIdentityCount": expected,
                "duplicateRecognizedIdentityInstanceCount": 0,
                "missingExpectedIdentityCount": 0,
                "exactFinalLiveInstanceCount": expected,
                "authoredTransientInstanceCount": 0,
                "collisionRegistrationPresentCount": expected,
                "collisionRegistrationMissingCount": 0,
                "collisionRegistrationConstructorMismatchCount": 0,
                "collisionRegistrationContributionCount": (
                    source["collisionRegistrationContributionCount"]
                ),
                "collisionRegistrationRegionReferenceCount": (
                    source["collisionRegistrationRegionReferenceCount"]
                ),
                "collisionRegistrationFingerprintSha256": (
                    source["collisionRegistrationFingerprintSha256"]
                ),
                "fingerprintSha256": f"{source['sourceOrdinal'] + 401:064x}",
                "finalLiveAuthoredSetPresent": True,
                "recognizedRegistrationsConstructorMatched": True,
                "objectBoundaryHeldDuringCapture": True,
            })
            comparison_sources.append({
                "sourceOrdinal": source["sourceOrdinal"],
                "packedRegionX": source["packedRegionX"],
                "packedRegionY": source["packedRegionY"],
                "expectedAuthoredObjectCount": expected,
                "identitylessDynamicObjectCount": 0,
                "exactFinalLiveInstanceCount": expected,
                "authoredTransientInstanceCount": 0,
                "missingExpectedIdentityCount": 0,
                "duplicateRecognizedIdentityInstanceCount": 0,
                "unrecognizedAuthoredInstanceCount": 0,
                "collisionRegistrationPresentCount": expected,
                "collisionRegistrationMissingCount": 0,
                "collisionRegistrationConstructorMismatchCount": 0,
                "collisionRegistrationContributionCount": (
                    source["collisionRegistrationContributionCount"]
                ),
                "collisionRegistrationRegionReferenceCount": (
                    source["collisionRegistrationRegionReferenceCount"]
                ),
                "runtimeRegistrationFingerprintSha256": (
                    source["collisionRegistrationFingerprintSha256"]
                ),
                "baselineRegistrationFingerprintSha256": (
                    source["collisionRegistrationFingerprintSha256"]
                ),
                "registrationFingerprintMatched": True,
                "outcome": "EXACT_BASELINE_MATCH",
            })
        observation_fingerprint = f"{501:064x}"
        cls.evidence["sourceRuntimeAuthoredObjectObservation"] = {
            "generation": transactional["generation"],
            "requirementsObservedAtTick": (
                transactional["requirementsObservedAtTick"]
            ),
            "recipeObservedAtTick": transactional["observedAtTick"],
            "runtimeObservedAtTick": transactional["observedAtTick"] + 1,
            "residencyMirrorVersion": (
                transactional["residencyMirrorVersion"]
            ),
            "sourceCount": transactional["sourceCount"],
            "expectedAuthoredObjectCount": (
                transactional["authoredObjectFootprintCount"]
            ),
            "observedObjectCount": observed_objects,
            "identitylessDynamicObjectCount": 0,
            "authoredIdentityObjectCount": observed_objects,
            "recognizedAuthoredInstanceCount": observed_objects,
            "unrecognizedAuthoredInstanceCount": 0,
            "uniqueRecognizedIdentityCount": observed_objects,
            "duplicateRecognizedIdentityInstanceCount": 0,
            "missingExpectedIdentityCount": 0,
            "exactFinalLiveInstanceCount": observed_objects,
            "authoredTransientInstanceCount": 0,
            "collisionRegistrationPresentCount": observed_objects,
            "collisionRegistrationMissingCount": 0,
            "collisionRegistrationConstructorMismatchCount": 0,
            "collisionRegistrationContributionCount": (
                transactional["collisionRegistrationContributionCount"]
            ),
            "collisionRegistrationRegionReferenceCount": (
                transactional[
                    "collisionRegistrationRegionReferenceCount"
                ]
            ),
            "fingerprintSha256": observation_fingerprint,
            "allObjectBoundariesHeldDuringCapture": True,
            "pointInTimeOnly": True,
            "detachedSummaryOnly": True,
            "sharedCollisionTileComparisonPerformed": False,
            "runtimeHandleRetained": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "runtimeMutationAuthorized": False,
            "runtimeMutationPerformed": False,
            "runtimeCacheInvalidated": False,
            "regionRegistryMutated": False,
            "residencyMirrorMutated": False,
            "visibilityCacheMutated": False,
            "arrivalGate": False,
            "visibilityReleased": False,
            "lifecycleAuthority": False,
            "sources": observation_sources,
        }
        cls.evidence["sourceRuntimeAuthoredObjectBaselineComparison"] = {
            "generation": transactional["generation"],
            "requirementsObservedAtTick": (
                transactional["requirementsObservedAtTick"]
            ),
            "recipeObservedAtTick": transactional["observedAtTick"],
            "runtimeObservedAtTick": transactional["observedAtTick"] + 1,
            "residencyMirrorVersion": (
                transactional["residencyMirrorVersion"]
            ),
            "sourceCount": transactional["sourceCount"],
            "exactBaselineMatchSourceCount": transactional["sourceCount"],
            "nonFinalAuthoredStateSourceCount": 0,
            "identityConflictSourceCount": 0,
            "registrationProvenanceInvalidSourceCount": 0,
            "stableBaselineMismatchSourceCount": 0,
            "expectedAuthoredObjectCount": (
                transactional["authoredObjectFootprintCount"]
            ),
            "identitylessDynamicObjectCount": 0,
            "runtimeObservationFingerprintSha256": observation_fingerprint,
            "transactionalBaselineFingerprintSha256": (
                transactional["fingerprintSha256"]
            ),
            "fingerprintSha256": f"{502:064x}",
            "allSourcesExactBaselineMatches": True,
            "pointInTimeOnly": True,
            "detachedSummaryOnly": True,
            "sharedCollisionTileComparisonPerformed": False,
            "runtimeHandleRetained": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "runtimeMutationAuthorized": False,
            "runtimeMutationPerformed": False,
            "runtimeCacheInvalidated": False,
            "regionRegistryMutated": False,
            "residencyMirrorMutated": False,
            "visibilityCacheMutated": False,
            "schedulerCorrelationPerformed": False,
            "arrivalGate": False,
            "visibilityReleased": False,
            "lifecycleAuthority": False,
            "sources": comparison_sources,
        }

    def test_schema_v56_extends_only_private_preservation_evidence(self):
        self.validator.validate(self.evidence)
        self.assertNotIn(
            "sourceRuntimeAuthoredObjectObservation",
            self.v55["$defs"]["npcOwnerPreservationNoOp"]["properties"],
        )
        self.assertNotIn(
            "sourceRuntimeAuthoredObjectBaselineComparison",
            self.v55["$defs"]["npcOwnerPreservationNoOp"]["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v56",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertEqual(
            set(self.v55["properties"]), set(self.schema["properties"])
        )

    def test_owner_refusal_requires_new_evidence_to_remain_null(self):
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
        ):
            refused[key] = None
        refused["ownerScopeEntered"] = False
        refused["sourceLifecycleInvoked"] = False
        self.validator.validate(refused)

    def test_private_capture_orders_runtime_observation_after_baseline(self):
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        start = handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        )
        boundary = handler[start:handler.index(
            "private void requireExactPackedSourceBoundary(", start
        )]
        self.assertLess(
            boundary.index("transactionalAuthoredSourceVerification[0] ="),
            boundary.index("runtimeAuthoredObjectObservation[0] ="),
        )
        self.assertLess(
            boundary.index("runtimeAuthoredObjectObservation[0] ="),
            boundary.index(
                "runtimeAuthoredObjectBaselineComparison[0] ="
            ),
        )
        self.assertLess(
            boundary.index(
                "runtimeAuthoredObjectBaselineComparison[0] ="
            ),
            boundary.index("captured[0] ="),
        )
        for required in (
            'EVENT_SCHEMA = "layered-map-parity-event-v60"',
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v59"',
            '\\"sourceRuntimeAuthoredObjectObservation\\":',
            '\\"sourceRuntimeAuthoredObjectBaselineComparison\\":',
            "appendPackedRegionRuntimeAuthoredObjectObservation(",
            "appendPackedRegionRuntimeAuthoredObjectBaselineComparison(",
            "getSourceRuntimeAuthoredObjectObservation()",
            "getSourceRuntimeAuthoredObjectBaselineComparison()",
            "isSharedCollisionTileComparisonPerformed()",
            "isSchedulerCorrelationPerformed()",
        ):
            self.assertIn(required, observer)

    def test_evidence_reconciles_runtime_and_baseline_sources(self):
        observation = self.evidence[
            "sourceRuntimeAuthoredObjectObservation"
        ]
        comparison = self.evidence[
            "sourceRuntimeAuthoredObjectBaselineComparison"
        ]
        baseline = self.evidence[
            "sourceTransactionalAuthoredStateVerification"
        ]
        self.assertEqual(
            observation["fingerprintSha256"],
            comparison["runtimeObservationFingerprintSha256"],
        )
        self.assertEqual(
            baseline["fingerprintSha256"],
            comparison["transactionalBaselineFingerprintSha256"],
        )
        for observed, compared, expected in zip(
            observation["sources"],
            comparison["sources"],
            baseline["sources"],
        ):
            self.assertEqual(
                expected["collisionRegistrationFingerprintSha256"],
                observed["collisionRegistrationFingerprintSha256"],
            )
            self.assertEqual(
                observed["collisionRegistrationFingerprintSha256"],
                compared["runtimeRegistrationFingerprintSha256"],
            )
            self.assertEqual(
                compared["runtimeRegistrationFingerprintSha256"],
                compared["baselineRegistrationFingerprintSha256"],
            )
            self.assertEqual("EXACT_BASELINE_MATCH", compared["outcome"])

    def test_readme_and_plan_record_slice_201(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "schema/layered-map-parity-event-v56.schema.json", readme
        )
        self.assertIn(
            "sourceRuntimeAuthoredObjectBaselineComparison", readme
        )
        self.assertIn(
            "### Slice 201: Private runtime authored-state diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
