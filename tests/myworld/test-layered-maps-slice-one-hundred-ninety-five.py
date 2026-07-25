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
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v54.schema.json"
SCHEMA_V53 = SCHEMA_DIR / "layered-map-parity-event-v53.schema.json"
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
SLICE_192 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-ninety-two.py"
)))


class LayeredMapsSliceOneHundredNinetyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.v53 = json.loads(SCHEMA_V53.read_text(encoding="utf-8"))
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
                "layered-map-parity-event-v54-isolated.schema.json"
            ),
            "$defs": cls.schema["$defs"],
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        cls.validator = Draft202012Validator(isolated, registry=registry)

        previous_test = SLICE_192[
            "LayeredMapsSliceOneHundredNinetyTwoTest"
        ]
        previous_test.setUpClass()
        cls.evidence = copy.deepcopy(previous_test.evidence)
        collision = cls.evidence["sourceAuthoredCollisionVerification"]
        application = cls.evidence[
            "sourceAuthoredCollisionApplicationVerification"
        ]
        state_sources = []
        for source in application["sources"]:
            state_sources.append({
                **{
                    key: value for key, value in source.items()
                    if key != "heldBoundaryCount"
                },
                "supportRegionCount": max(
                    0, source["disposableRegionConstructionCount"] - 1
                ),
                "objectMembershipApplicationCount": (
                    source["authoredObjectFootprintCount"]
                ),
                "objectMembershipBoundaryCount": (
                    source["requiredRegionReferenceCount"]
                ),
                "collisionBoundaryCount": source["heldBoundaryCount"],
                "finalStateFingerprintSha256": (
                    f"{source['sourceOrdinal'] + 81:064x}"
                ),
            })
        cls.evidence["sourceAuthoredStateVerification"] = {
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
            "uniqueContributionTileReferenceCount": (
                application["uniqueContributionTileReferenceCount"]
            ),
            "requiredRegionReferenceCount": (
                collision["requiredRegionReferenceCount"]
            ),
            "uniqueRequiredRegionReferenceCount": (
                collision["uniqueRequiredRegionReferenceCount"]
            ),
            "preCombinedDisposableRegionConstructionCount": (
                collision["sourceCount"] * 2
            ),
            "combinedDisposableRegionConstructionCount": (
                application["disposableCollisionRegionConstructionCount"]
            ),
            "totalDisposableRegionConstructionCount": (
                collision["sourceCount"] * 2
                + application["disposableCollisionRegionConstructionCount"]
            ),
            "combinedSupportRegionCount": sum(
                source["supportRegionCount"] for source in state_sources
            ),
            "preCombinedTerrainApplyCount": collision["sourceCount"] * 2,
            "combinedTerrainApplyCount": collision["sourceCount"],
            "totalTerrainApplyCount": collision["sourceCount"] * 3,
            "preCombinedObjectMembershipApplyCount": collision["sourceCount"],
            "combinedObjectMembershipApplicationCount": (
                collision["authoredObjectFootprintCount"]
            ),
            "combinedObjectMembershipBoundaryCount": sum(
                source["objectMembershipBoundaryCount"]
                for source in state_sources
            ),
            "combinedCollisionApplicationCount": (
                application["collisionApplicationCount"]
            ),
            "combinedCollisionBoundaryCount": (
                application["heldBoundaryCount"]
            ),
            "combinedVerifiedRegionTileCount": (
                application["verifiedRegionTileCount"]
            ),
            "combinedBlockingSceneryContributionCount": (
                application["blockingSceneryContributionCount"]
            ),
            "combinedDynamicCollisionContributionCount": (
                application["dynamicCollisionContributionCount"]
            ),
            "combinedDynamicProjectileContributionCount": (
                application["dynamicProjectileContributionCount"]
            ),
            "baselineFingerprintSha256": collision["fingerprintSha256"],
            "fingerprintSha256": f"{91:064x}",
            "usableRegionContainerCount": 0,
            "pointInTimeOnly": True,
            "detachedSummaryOnly": True,
            "allSourcesVerified": True,
            "runtimeDefinitionCapturePerformed": True,
            "collisionFootprintDerivationPerformed": True,
            "terrainAppliedToCombinedDisposableSourceRegions": True,
            "authoredObjectMembershipAppliedToCombinedDisposableSourceRegions": True,
            "collisionAppliedToSameDisposableRegionUnions": True,
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
            "sources": state_sources,
        }

    def test_v54_accepts_only_combined_disposable_state(self):
        self.validator.validate(self.evidence)

        missing = copy.deepcopy(self.evidence)
        missing["sourceAuthoredStateVerification"] = None
        self.assertFalse(self.validator.is_valid(missing))

        for positive in (
            "terrainAppliedToCombinedDisposableSourceRegions",
            "authoredObjectMembershipAppliedToCombinedDisposableSourceRegions",
            "collisionAppliedToSameDisposableRegionUnions",
        ):
            invalid = copy.deepcopy(self.evidence)
            invalid["sourceAuthoredStateVerification"][positive] = False
            self.assertFalse(self.validator.is_valid(invalid), positive)

        for forbidden in (
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
            invalid["sourceAuthoredStateVerification"][forbidden] = True
            self.assertFalse(self.validator.is_valid(invalid), forbidden)

    def test_v53_is_immutable_and_v54_extends_only_private_noop(self):
        self.assertEqual(
            "layered-map-parity-event-v53",
            self.v53["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "sourceAuthoredStateVerification",
            self.v53["$defs"]["npcOwnerPreservationNoOp"]["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v54",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertIn(
            "sourceAuthoredStateVerification",
            self.schema["$defs"]["npcOwnerPreservationNoOp"]["required"],
        )
        self.assertEqual(
            set(self.v53["properties"]), set(self.schema["properties"])
        )

    def test_private_capture_aligns_and_serializes_combined_state(self):
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        start = handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        )
        boundary = handler[start:handler.index(
            "private void requireExactPackedSourceBoundary(", start
        )]
        self.assertLess(
            boundary.index("authoredCollisionApplicationVerification[0] ="),
            boundary.index("authoredSourceStateVerification[0] ="),
        )
        self.assertLess(
            boundary.index("authoredSourceStateVerification[0] ="),
            boundary.index("captured[0] ="),
        )
        for required in (
            'EVENT_SCHEMA = "layered-map-parity-event-v60"',
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v59"',
            '\\"sourceAuthoredStateVerification\\":',
            "appendPackedRegionAuthoredSourceStateVerificationBatch(",
            "getSourceAuthoredStateVerification()",
            "authoredStateSourcesMatch(",
            "getFinalStateFingerprintSha256()",
            "isCollisionAppliedToSameDisposableRegionUnions()",
        ):
            self.assertIn(required, observer)

    def test_readme_and_living_plan_record_slice_one_hundred_ninety_five(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "schema/layered-map-parity-event-v56.schema.json", readme
        )
        self.assertIn("sourceAuthoredStateVerification", readme)
        self.assertIn(
            "### Slice 195: Private combined authored-state diagnostics",
            plan,
        )
        self.assertIn("schema-v54", plan)


if __name__ == "__main__":
    unittest.main()
