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
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v52.schema.json"
SCHEMA_V49 = SCHEMA_DIR / "layered-map-parity-event-v49.schema.json"
SCHEMA_V50 = SCHEMA_DIR / "layered-map-parity-event-v50.schema.json"
SCHEMA_V51 = SCHEMA_DIR / "layered-map-parity-event-v51.schema.json"
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
SLICE_183 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-eighty-three.py"
)))


def collision_source(
    ordinal,
    x,
    y,
    replay_count,
    object_count,
    terrain_fingerprint,
):
    return {
        "sourceOrdinal": ordinal,
        "packedRegionX": x,
        "packedRegionY": y,
        "replayPlacementCount": replay_count,
        "authoredObjectFootprintCount": object_count,
        "definitionBackedObjectCount": object_count,
        "specialCollisionlessObjectCount": 0,
        "zeroContributionObjectCount": 0,
        "crossSourceCollisionObjectCount": 0,
        "collisionBeyondAuthoredDependencyObjectCount": 0,
        "contributionTileReferenceCount": object_count * 2,
        "requiredRegionReferenceCount": object_count * 2,
        "uniqueRequiredRegionCount": min(object_count, 1),
        "terrainFingerprintSha256": terrain_fingerprint,
        "authoredReplayFingerprintSha256": f"{ordinal + 11:064x}",
        "definitionCaptureFingerprintSha256": f"{ordinal + 21:064x}",
        "collisionFootprintFingerprintSha256": f"{ordinal + 31:064x}",
    }


class LayeredMapsSliceOneHundredEightyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        cls.v49 = json.loads(SCHEMA_V49.read_text(encoding="utf-8"))
        cls.v50 = json.loads(SCHEMA_V50.read_text(encoding="utf-8"))
        cls.v51 = json.loads(SCHEMA_V51.read_text(encoding="utf-8"))
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": (
                "https://spoiled-milk.invalid/schema/"
                "layered-map-parity-event-v52-isolated.schema.json"
            ),
            "$defs": cls.schema["$defs"],
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        registry = Registry().with_resources([
            (cls.v49["$id"], Resource.from_contents(cls.v49)),
            (cls.v50["$id"], Resource.from_contents(cls.v50)),
            (cls.v51["$id"], Resource.from_contents(cls.v51)),
        ])
        cls.validator = Draft202012Validator(
            isolated, registry=registry
        )

        previous_test = SLICE_183[
            "LayeredMapsSliceOneHundredEightyThreeTest"
        ]
        previous_test.setUpClass()
        cls.evidence = copy.deepcopy(previous_test.evidence)
        terrain_sources = cls.evidence["sourceTerrainVerification"]["sources"]
        reload_sources = cls.evidence["sourceReloadRecipe"]["sources"]
        sources = [
            collision_source(
                ordinal,
                reload_source["packedRegionX"],
                reload_source["packedRegionY"],
                reload_source["authoredPlacementCount"],
                3 if ordinal == 0 else 0,
                terrain_sources[ordinal]["terrainFingerprintSha256"],
            )
            for ordinal, reload_source in enumerate(reload_sources)
        ]
        cls.evidence["sourceAuthoredCollisionVerification"] = {
            "generation": 9,
            "requirementsObservedAtTick": 12,
            "observedAtTick": 14,
            "residencyMirrorVersion": 17,
            "authoredGeneration": 9,
            "sourceCount": 2,
            "replayPlacementCount": 5,
            "authoredObjectFootprintCount": 3,
            "definitionBackedObjectCount": 3,
            "specialCollisionlessObjectCount": 0,
            "zeroContributionObjectCount": 0,
            "crossSourceCollisionObjectCount": 0,
            "collisionBeyondAuthoredDependencyObjectCount": 0,
            "contributionTileReferenceCount": 6,
            "requiredRegionReferenceCount": 6,
            "uniqueRequiredRegionReferenceCount": 1,
            "fingerprintSha256": f"{41:064x}",
            "disposableRegionConstructionCount": 4,
            "disposableTerrainApplyCount": 4,
            "disposableObjectMembershipApplyCount": 2,
            "usableRegionContainerCount": 0,
            "pointInTimeOnly": True,
            "detachedSummaryOnly": True,
            "allSourcesVerified": True,
            "runtimeDefinitionCapturePerformed": True,
            "collisionFootprintDerivationPerformed": True,
            "collisionApplied": False,
            "collisionRegistrationAttached": False,
            "runtimeHandleRetained": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "terrainAppliedToRuntimeSource": False,
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
            "sources": sources,
        }

    def test_v52_accepts_only_bounded_inert_collision_summary(self):
        self.validator.validate(self.evidence)

        owner_refused = copy.deepcopy(self.evidence)
        owner_refused["reason"] = "OWNER_SCOPE_REFUSED"
        owner_refused["ownerScopeEntered"] = False
        owner_refused["sourceLifecycleInvoked"] = False
        owner_refused["sourceAbsencePreflight"] = None
        owner_refused["sourceReloadRecipe"] = None
        owner_refused["sourceTerrainVerification"] = None
        owner_refused["sourceAuthoredCollisionVerification"] = None
        self.validator.validate(owner_refused)

        missing = copy.deepcopy(self.evidence)
        missing["sourceAuthoredCollisionVerification"] = None
        self.assertFalse(self.validator.is_valid(missing))

        for field in (
            "collisionApplied",
            "collisionRegistrationAttached",
            "runtimeHandleRetained",
            "sourceAbsencePerformed",
            "sourceReconstructionPerformed",
            "terrainAppliedToRuntimeSource",
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
            invalid["sourceAuthoredCollisionVerification"][field] = True
            self.assertFalse(
                self.validator.is_valid(invalid),
                f"schema accepted forbidden collision-summary {field}",
            )

    def test_v51_is_immutable_and_v52_extends_only_private_noop(self):
        self.assertEqual(
            "layered-map-parity-event-v51",
            self.v51["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "sourceAuthoredCollisionVerification",
            self.v51["$defs"]["npcOwnerPreservationNoOp"]["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v52",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertIn(
            "sourceAuthoredCollisionVerification",
            self.schema["$defs"]["npcOwnerPreservationNoOp"]["required"],
        )
        self.assertEqual(
            set(self.v51["properties"]),
            set(self.schema["properties"]),
        )

    def test_private_capture_aligns_and_serializes_collision_summary(self):
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        start = handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        )
        boundary = handler[start:handler.index(
            "private void requireExactPackedSourceBoundary(", start
        )]
        self.assertIn(
            "LayeredPackedRegionAuthoredCollisionVerificationBatch", boundary
        )
        self.assertIn(".capture(", boundary)
        self.assertLess(
            boundary.index("terrainVerification[0] ="),
            boundary.index("authoredCollisionVerification[0] ="),
        )
        self.assertLess(
            boundary.index("authoredCollisionVerification[0] ="),
            boundary.index("captured[0] ="),
        )
        for required in (
            'EVENT_SCHEMA = "layered-map-parity-event-v52"',
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v51"',
            '\\"sourceAuthoredCollisionVerification\\":',
            "appendPackedRegionAuthoredCollisionVerificationBatch(",
            "getSourceAuthoredCollisionVerification()",
            "collisionSourcesMatch(",
            "getTerrainFingerprintSha256().equals(",
        ):
            self.assertIn(required, observer)

    def test_readme_and_living_plan_record_slice_one_hundred_eighty_nine(self):
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "schema/layered-map-parity-event-v52.schema.json", readme
        )
        self.assertIn("sourceAuthoredCollisionVerification", readme)
        self.assertIn(
            "### Slice 189: Private authored-collision diagnostics", plan
        )
        self.assertIn("schema-v52", plan)


if __name__ == "__main__":
    unittest.main()
