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
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v53.schema.json"
SCHEMA_V52 = SCHEMA_DIR / "layered-map-parity-event-v52.schema.json"
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
SLICE_189 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-eighty-nine.py"
)))


class LayeredMapsSliceOneHundredNinetyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.v52 = json.loads(SCHEMA_V52.read_text(encoding="utf-8"))
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
                "layered-map-parity-event-v53-isolated.schema.json"
            ),
            "$defs": cls.schema["$defs"],
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        cls.validator = Draft202012Validator(
            isolated, registry=registry
        )

        previous_test = SLICE_189[
            "LayeredMapsSliceOneHundredEightyNineTest"
        ]
        previous_test.setUpClass()
        cls.evidence = copy.deepcopy(previous_test.evidence)
        collision = cls.evidence[
            "sourceAuthoredCollisionVerification"
        ]
        application_sources = []
        for source in collision["sources"]:
            region_count = max(1, source["uniqueRequiredRegionCount"])
            application_sources.append({
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
                "uniqueContributionTileCount": min(
                    source["contributionTileReferenceCount"],
                    source["authoredObjectFootprintCount"],
                ),
                "requiredRegionReferenceCount": (
                    source["requiredRegionReferenceCount"]
                ),
                "uniqueRequiredRegionCount": (
                    source["uniqueRequiredRegionCount"]
                ),
                "disposableRegionConstructionCount": region_count,
                "collisionApplicationCount": (
                    source["authoredObjectFootprintCount"]
                ),
                "heldBoundaryCount": (
                    source["requiredRegionReferenceCount"]
                ),
                "verifiedRegionTileCount": region_count * 2304,
                "blockingSceneryContributionCount": (
                    source["authoredObjectFootprintCount"]
                ),
                "dynamicCollisionContributionCount": 0,
                "dynamicProjectileContributionCount": 0,
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
                    f"{source['sourceOrdinal'] + 51:064x}"
                ),
            })
        source_count = len(application_sources)
        cls.evidence[
            "sourceAuthoredCollisionApplicationVerification"
        ] = {
            "generation": collision["generation"],
            "requirementsObservedAtTick": (
                collision["requirementsObservedAtTick"]
            ),
            "observedAtTick": collision["observedAtTick"],
            "residencyMirrorVersion": collision["residencyMirrorVersion"],
            "authoredGeneration": collision["authoredGeneration"],
            "sourceCount": source_count,
            "replayPlacementCount": collision["replayPlacementCount"],
            "authoredObjectFootprintCount": (
                collision["authoredObjectFootprintCount"]
            ),
            "contributionTileReferenceCount": (
                collision["contributionTileReferenceCount"]
            ),
            "uniqueContributionTileReferenceCount": sum(
                source["uniqueContributionTileCount"]
                for source in application_sources
            ),
            "requiredRegionReferenceCount": (
                collision["requiredRegionReferenceCount"]
            ),
            "uniqueRequiredRegionReferenceCount": (
                collision["uniqueRequiredRegionReferenceCount"]
            ),
            "preApplicationDisposableRegionConstructionCount": (
                source_count * 2
            ),
            "disposableCollisionRegionConstructionCount": sum(
                source["disposableRegionConstructionCount"]
                for source in application_sources
            ),
            "totalDisposableRegionConstructionCount": (
                source_count * 2
                + sum(
                    source["disposableRegionConstructionCount"]
                    for source in application_sources
                )
            ),
            "disposableTerrainApplyCount": source_count * 2,
            "disposableObjectMembershipApplyCount": source_count,
            "collisionApplicationCount": sum(
                source["collisionApplicationCount"]
                for source in application_sources
            ),
            "heldBoundaryCount": sum(
                source["heldBoundaryCount"]
                for source in application_sources
            ),
            "verifiedRegionTileCount": sum(
                source["verifiedRegionTileCount"]
                for source in application_sources
            ),
            "blockingSceneryContributionCount": sum(
                source["blockingSceneryContributionCount"]
                for source in application_sources
            ),
            "dynamicCollisionContributionCount": 0,
            "dynamicProjectileContributionCount": 0,
            "baselineFingerprintSha256": collision["fingerprintSha256"],
            "fingerprintSha256": f"{61:064x}",
            "usableRegionContainerCount": 0,
            "pointInTimeOnly": True,
            "detachedSummaryOnly": True,
            "allSourcesVerified": True,
            "runtimeDefinitionCapturePerformed": True,
            "collisionFootprintDerivationPerformed": True,
            "collisionAppliedToDisposableRegions": True,
            "collisionRegistrationAttached": False,
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
            "regionRegistryMutated": False,
            "residencyMirrorMutated": False,
            "visibilityCacheMutated": False,
            "arrivalGate": False,
            "visibilityReleased": False,
            "lifecycleAuthority": False,
            "sources": application_sources,
        }

    def test_v53_accepts_only_disposable_collision_application(self):
        self.validator.validate(self.evidence)

        owner_refused = copy.deepcopy(self.evidence)
        owner_refused["reason"] = "OWNER_SCOPE_REFUSED"
        owner_refused["ownerScopeEntered"] = False
        owner_refused["sourceLifecycleInvoked"] = False
        owner_refused["sourceAbsencePreflight"] = None
        owner_refused["sourceReloadRecipe"] = None
        owner_refused["sourceTerrainVerification"] = None
        owner_refused["sourceAuthoredCollisionVerification"] = None
        owner_refused[
            "sourceAuthoredCollisionApplicationVerification"
        ] = None
        self.validator.validate(owner_refused)

        missing = copy.deepcopy(self.evidence)
        missing["sourceAuthoredCollisionApplicationVerification"] = None
        self.assertFalse(self.validator.is_valid(missing))

        invalid = copy.deepcopy(self.evidence)
        invalid[
            "sourceAuthoredCollisionApplicationVerification"
        ]["collisionAppliedToDisposableRegions"] = False
        self.assertFalse(self.validator.is_valid(invalid))

        for field in (
            "collisionRegistrationAttached",
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
            "regionRegistryMutated",
            "residencyMirrorMutated",
            "visibilityCacheMutated",
            "arrivalGate",
            "visibilityReleased",
            "lifecycleAuthority",
        ):
            invalid = copy.deepcopy(self.evidence)
            invalid[
                "sourceAuthoredCollisionApplicationVerification"
            ][field] = True
            self.assertFalse(
                self.validator.is_valid(invalid),
                f"schema accepted forbidden application-summary {field}",
            )

    def test_v52_is_immutable_and_v53_extends_only_private_noop(self):
        self.assertEqual(
            "layered-map-parity-event-v52",
            self.v52["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "sourceAuthoredCollisionApplicationVerification",
            self.v52["$defs"]["npcOwnerPreservationNoOp"]["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v53",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertIn(
            "sourceAuthoredCollisionApplicationVerification",
            self.schema["$defs"]["npcOwnerPreservationNoOp"]["required"],
        )
        self.assertEqual(
            set(self.v52["properties"]),
            set(self.schema["properties"]),
        )

    def test_private_capture_aligns_and_serializes_application(self):
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        start = handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        )
        boundary = handler[start:handler.index(
            "private void requireExactPackedSourceBoundary(", start
        )]
        self.assertIn(
            "LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch",
            boundary,
        )
        self.assertLess(
            boundary.index("authoredCollisionVerification[0] ="),
            boundary.index(
                "authoredCollisionApplicationVerification[0] ="
            ),
        )
        self.assertLess(
            boundary.index(
                "authoredCollisionApplicationVerification[0] ="
            ),
            boundary.index("captured[0] ="),
        )
        for required in (
            'EVENT_SCHEMA = "layered-map-parity-event-v59"',
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v58"',
            '\\"sourceAuthoredCollisionApplicationVerification\\":',
            (
                "appendPackedRegionAuthoredCollisionApplication"
                "VerificationBatch("
            ),
            "getSourceAuthoredCollisionApplicationVerification()",
            "collisionApplicationSourcesMatch(",
            "getBaselineFingerprintSha256().equals(",
            "isRuntimeCollisionApplied()",
        ):
            self.assertIn(required, observer)

    def test_readme_and_living_plan_record_slice_one_hundred_ninety_two(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "schema/layered-map-parity-event-v56.schema.json", readme
        )
        self.assertIn(
            "sourceAuthoredCollisionApplicationVerification", readme
        )
        self.assertIn(
            (
                "### Slice 192: Private disposable "
                "collision-application diagnostics"
            ),
            plan,
        )
        self.assertIn("schema-v53", plan)


if __name__ == "__main__":
    unittest.main()
