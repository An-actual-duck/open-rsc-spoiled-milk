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
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v55.schema.json"
SCHEMA_V54 = SCHEMA_DIR / "layered-map-parity-event-v54.schema.json"
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
SLICE_195 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-ninety-five.py"
)))


class LayeredMapsSliceOneHundredNinetyEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.v54 = json.loads(SCHEMA_V54.read_text(encoding="utf-8"))
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
                "layered-map-parity-event-v55-isolated.schema.json"
            ),
            "$defs": cls.schema["$defs"],
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        cls.validator = Draft202012Validator(isolated, registry=registry)

        previous_test = SLICE_195[
            "LayeredMapsSliceOneHundredNinetyFiveTest"
        ]
        previous_test.setUpClass()
        cls.evidence = copy.deepcopy(previous_test.evidence)
        collision = cls.evidence["sourceAuthoredCollisionVerification"]
        state = cls.evidence["sourceAuthoredStateVerification"]
        transactional_sources = []
        for source in state["sources"]:
            transactional_sources.append({
                "sourceOrdinal": source["sourceOrdinal"],
                "packedRegionX": source["packedRegionX"],
                "packedRegionY": source["packedRegionY"],
                "replayPlacementCount": source["replayPlacementCount"],
                "authoredObjectFootprintCount": (
                    source["authoredObjectFootprintCount"]
                ),
                "contributionTileReferenceCount": (
                    source["contributionTileReferenceCount"]
                ),
                "requiredRegionReferenceCount": (
                    source["requiredRegionReferenceCount"]
                ),
                "uniqueRequiredRegionCount": (
                    source["uniqueRequiredRegionCount"]
                ),
                "disposableRegionConstructionCount": (
                    source["disposableRegionConstructionCount"]
                ),
                "supportRegionCount": source["supportRegionCount"],
                "objectCollisionTransactionCount": (
                    source["authoredObjectFootprintCount"]
                ),
                "objectCollisionTransactionBoundaryCount": (
                    source["collisionBoundaryCount"]
                ),
                "disposableCacheInvalidationCount": (
                    source["authoredObjectFootprintCount"]
                ),
                "collisionRegistrationCount": (
                    source["authoredObjectFootprintCount"]
                ),
                "collisionRegistrationContributionCount": (
                    source["contributionTileReferenceCount"]
                ),
                "collisionRegistrationRegionReferenceCount": (
                    source["requiredRegionReferenceCount"]
                ),
                "verifiedRegionTileCount": (
                    source["verifiedRegionTileCount"]
                ),
                "blockingSceneryContributionCount": (
                    source["blockingSceneryContributionCount"]
                ),
                "dynamicCollisionContributionCount": (
                    source["dynamicCollisionContributionCount"]
                ),
                "dynamicProjectileContributionCount": (
                    source["dynamicProjectileContributionCount"]
                ),
                "terrainFingerprintSha256": (
                    source["terrainFingerprintSha256"]
                ),
                "authoredReplayFingerprintSha256": (
                    source["authoredReplayFingerprintSha256"]
                ),
                "definitionCaptureFingerprintSha256": (
                    source["definitionCaptureFingerprintSha256"]
                ),
                "collisionFootprintFingerprintSha256": (
                    source["collisionFootprintFingerprintSha256"]
                ),
                "appliedCollisionFingerprintSha256": (
                    source["appliedCollisionFingerprintSha256"]
                ),
                "collisionRegistrationFingerprintSha256": (
                    f"{source['sourceOrdinal'] + 101:064x}"
                ),
                "finalStateFingerprintSha256": (
                    source["finalStateFingerprintSha256"]
                ),
            })
        cls.evidence["sourceTransactionalAuthoredStateVerification"] = {
            "generation": collision["generation"],
            "requirementsObservedAtTick": (
                collision["requirementsObservedAtTick"]
            ),
            "observedAtTick": collision["observedAtTick"],
            "residencyMirrorVersion": collision["residencyMirrorVersion"],
            "authoredGeneration": collision["authoredGeneration"],
            "sourceCount": collision["sourceCount"],
            "replayPlacementCount": collision["replayPlacementCount"],
            "authoredObjectFootprintCount": (
                collision["authoredObjectFootprintCount"]
            ),
            "contributionTileReferenceCount": (
                collision["contributionTileReferenceCount"]
            ),
            "requiredRegionReferenceCount": (
                collision["requiredRegionReferenceCount"]
            ),
            "uniqueRequiredRegionReferenceCount": (
                collision["uniqueRequiredRegionReferenceCount"]
            ),
            "preTransactionalDisposableRegionConstructionCount": (
                collision["sourceCount"] * 2
            ),
            "transactionalDisposableRegionConstructionCount": sum(
                source["disposableRegionConstructionCount"]
                for source in transactional_sources
            ),
            "totalDisposableRegionConstructionCount": (
                collision["sourceCount"] * 2
                + sum(
                    source["disposableRegionConstructionCount"]
                    for source in transactional_sources
                )
            ),
            "transactionalSupportRegionCount": sum(
                source["supportRegionCount"]
                for source in transactional_sources
            ),
            "objectCollisionTransactionCount": (
                collision["authoredObjectFootprintCount"]
            ),
            "objectCollisionTransactionBoundaryCount": (
                collision["requiredRegionReferenceCount"]
            ),
            "disposableCacheInvalidationCount": (
                collision["authoredObjectFootprintCount"]
            ),
            "collisionRegistrationCount": (
                collision["authoredObjectFootprintCount"]
            ),
            "collisionRegistrationContributionCount": (
                collision["contributionTileReferenceCount"]
            ),
            "collisionRegistrationRegionReferenceCount": (
                collision["requiredRegionReferenceCount"]
            ),
            "transactionalVerifiedRegionTileCount": (
                state["combinedVerifiedRegionTileCount"]
            ),
            "transactionalBlockingSceneryContributionCount": (
                state["combinedBlockingSceneryContributionCount"]
            ),
            "transactionalDynamicCollisionContributionCount": (
                state["combinedDynamicCollisionContributionCount"]
            ),
            "transactionalDynamicProjectileContributionCount": (
                state["combinedDynamicProjectileContributionCount"]
            ),
            "baselineFingerprintSha256": collision["fingerprintSha256"],
            "fingerprintSha256": f"{111:064x}",
            "usableRegionContainerCount": 0,
            "pointInTimeOnly": True,
            "detachedSummaryOnly": True,
            "allSourcesVerified": True,
            "runtimeDefinitionCapturePerformed": True,
            "collisionFootprintDerivationPerformed": True,
            "objectCollisionTransactionAppliedToDisposableRegions": True,
            "collisionRegistrationAttachedToDisposableObjects": True,
            "disposableCacheInvalidationOnly": True,
            "runtimeCollisionApplied": False,
            "runtimeHandleRetained": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "terrainAppliedToRuntimeSource": False,
            "authoredObjectMembershipAppliedToRuntimeSource": False,
            "npcMembershipApplied": False,
            "groundItemMembershipApplied": False,
            "schedulerStateRestored": False,
            "activeFamilyPreservationPerformed": False,
            "runtimeCacheInvalidated": False,
            "regionRegistryMutated": False,
            "residencyMirrorMutated": False,
            "visibilityCacheMutated": False,
            "arrivalGate": False,
            "visibilityReleased": False,
            "lifecycleAuthority": False,
            "sources": transactional_sources,
        }

    def test_v55_accepts_only_transactional_disposable_state(self):
        self.validator.validate(self.evidence)

        missing = copy.deepcopy(self.evidence)
        missing["sourceTransactionalAuthoredStateVerification"] = None
        self.assertFalse(self.validator.is_valid(missing))

        for positive in (
            "objectCollisionTransactionAppliedToDisposableRegions",
            "collisionRegistrationAttachedToDisposableObjects",
            "disposableCacheInvalidationOnly",
        ):
            invalid = copy.deepcopy(self.evidence)
            invalid[
                "sourceTransactionalAuthoredStateVerification"
            ][positive] = False
            self.assertFalse(self.validator.is_valid(invalid), positive)

        for forbidden in (
            "runtimeCollisionApplied",
            "runtimeHandleRetained",
            "sourceAbsencePerformed",
            "sourceReconstructionPerformed",
            "terrainAppliedToRuntimeSource",
            "authoredObjectMembershipAppliedToRuntimeSource",
            "npcMembershipApplied",
            "groundItemMembershipApplied",
            "schedulerStateRestored",
            "activeFamilyPreservationPerformed",
            "runtimeCacheInvalidated",
            "regionRegistryMutated",
            "residencyMirrorMutated",
            "visibilityCacheMutated",
            "arrivalGate",
            "visibilityReleased",
            "lifecycleAuthority",
        ):
            invalid = copy.deepcopy(self.evidence)
            invalid[
                "sourceTransactionalAuthoredStateVerification"
            ][forbidden] = True
            self.assertFalse(self.validator.is_valid(invalid), forbidden)

    def test_v54_is_immutable_and_v55_extends_only_private_noop(self):
        self.assertEqual(
            "layered-map-parity-event-v54",
            self.v54["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "sourceTransactionalAuthoredStateVerification",
            self.v54["$defs"]["npcOwnerPreservationNoOp"]["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v55",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertIn(
            "sourceTransactionalAuthoredStateVerification",
            self.schema["$defs"]["npcOwnerPreservationNoOp"]["required"],
        )
        self.assertEqual(
            set(self.v54["properties"]), set(self.schema["properties"])
        )

    def test_private_capture_aligns_and_serializes_transactional_state(self):
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        start = handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        )
        boundary = handler[start:handler.index(
            "private void requireExactPackedSourceBoundary(", start
        )]
        self.assertLess(
            boundary.index("authoredSourceStateVerification[0] ="),
            boundary.index("transactionalAuthoredSourceVerification[0] ="),
        )
        self.assertLess(
            boundary.index("transactionalAuthoredSourceVerification[0] ="),
            boundary.index("captured[0] ="),
        )
        for required in (
            'EVENT_SCHEMA = "layered-map-parity-event-v58"',
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v57"',
            '\\"sourceTransactionalAuthoredStateVerification\\":',
            (
                "appendPackedRegionTransactionalAuthoredSource"
                "VerificationBatch("
            ),
            "getSourceTransactionalAuthoredStateVerification()",
            "transactionalAuthoredStateSourcesMatch(",
            "getCollisionRegistrationFingerprintSha256()",
            "isRuntimeCacheInvalidated()",
        ):
            self.assertIn(required, observer)

    def test_readme_and_living_plan_record_slice_one_hundred_ninety_eight(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "schema/layered-map-parity-event-v56.schema.json", readme
        )
        self.assertIn(
            "sourceTransactionalAuthoredStateVerification", readme
        )
        self.assertIn(
            "### Slice 198: Private transactional authored-state diagnostics",
            plan,
        )
        self.assertIn("schema-v55", plan)


if __name__ == "__main__":
    unittest.main()
