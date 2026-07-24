#!/usr/bin/env python3
import copy
import json
import os
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[2]
SERVER_SOURCE = ROOT / "server/src"
REGION = SERVER_SOURCE / "com/openrsc/server/model/world/region"
BATCH = REGION / "LayeredPackedRegionTerrainVerificationBatch.java"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameEventHandler.java"
)
SCHEMA_DIR = ROOT / "tools/layered-maps/schema"
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v51.schema.json"
SCHEMA_V49 = SCHEMA_DIR / "layered-map-parity-event-v49.schema.json"
SCHEMA_V50 = SCHEMA_DIR / "layered-map-parity-event-v50.schema.json"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_177 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-seventy-seven.py"
)))
SLICE_182 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-eighty-two.py"
)))


FIXTURE = SLICE_182["FIXTURE"].replace(
    """        final LayeredPackedRegionBlankContainerPlan[] containers =
            new LayeredPackedRegionBlankContainerPlan[1];""",
    """        final LayeredPackedRegionBlankContainerPlan[] containers =
            new LayeredPackedRegionBlankContainerPlan[1];
        final LayeredPackedRegionTerrainVerificationBatch[] batch =
            new LayeredPackedRegionTerrainVerificationBatch[1];""",
).replace(
    """                            .defineFromResidentTileStates(
                                container, boundary, captured[0]);""",
    """                            .defineFromResidentTileStates(
                                container, boundary, captured[0]);
                    batch[0] =
                        LayeredPackedRegionTerrainVerificationBatch.capture(
                            manager, boundary, reload,
                            LayeredPackedRegionTerrainVerificationBatch
                                .MAXIMUM_VERIFICATION_SOURCES);""",
).replace(
    """        check(entered && captured[0] != null && terrain[0] != null
                && containers[0] != null,
            "real source lifecycle boundary did not produce terrain input");""",
    """        check(entered && captured[0] != null && terrain[0] != null
                && containers[0] != null && batch[0] != null,
            "real source lifecycle boundary did not produce terrain input");
        LayeredPackedRegionTerrainVerificationBatch verifiedBatch = batch[0];
        LayeredPackedRegionTerrainVerificationBatch.SourceVerification
            source = verifiedBatch.getSources().get(0);
        check(verifiedBatch.getGeneration() == 9L
                && verifiedBatch.getRequirementsObservedAtTick() == 12L
                && verifiedBatch.getObservedAtTick() == 14L
                && verifiedBatch.getAuthoredGeneration() == 9L
                && verifiedBatch.getSourceCount() == 1
                && verifiedBatch.getVerifiedTileCount() == 2304L
                && verifiedBatch.getTerrainCollisionMaskTileCount() == 1L
                && verifiedBatch
                    .getTerrainProjectileBlockedTileCount() == 1L
                && verifiedBatch.getSealedBaseTraversalTileCount() == 1L
                && verifiedBatch.getDisposableRegionConstructionCount() == 1
                && verifiedBatch.getDisposableTerrainApplyCount() == 1
                && verifiedBatch.getUsableRegionContainerCount() == 0
                && source.getSourceOrdinal() == 0
                && source.getPackedRegionX() == 4
                && source.getPackedRegionY() == 0
                && source.getVerifiedTileCount() == 2304
                && source.getTerrainFingerprintSha256().length() == 64,
            "bounded terrain verification summary is incomplete");
        check(verifiedBatch.isPointInTimeOnly()
                && verifiedBatch.isDetachedSummaryOnly()
                && verifiedBatch.isAllSourcesVerified()
                && !verifiedBatch.isRuntimeHandleRetained()
                && !verifiedBatch.isSourceAbsencePerformed()
                && !verifiedBatch.isSourceReconstructionPerformed()
                && !verifiedBatch.isTerrainAppliedToRuntimeSource()
                && !verifiedBatch.isAuthoredReplayPerformed()
                && !verifiedBatch.isDynamicCollisionRebuildPerformed()
                && !verifiedBatch.isActiveFamilyPreservationPerformed()
                && !verifiedBatch.isRegionRegistryMutated()
                && !verifiedBatch.isResidencyMirrorMutated()
                && !verifiedBatch.isVisibilityCacheMutated()
                && !verifiedBatch.isArrivalGate()
                && !verifiedBatch.isVisibilityReleased()
                && !verifiedBatch.isLifecycleAuthority(),
            "terrain verification batch crossed runtime authority");""",
)


def terrain_source(ordinal, x, y):
    return {
        "sourceOrdinal": ordinal,
        "packedRegionX": x,
        "packedRegionY": y,
        "verifiedTileCount": 2304,
        "terrainBlockedTileCount": 10,
        "terrainCollisionMaskTileCount": 20,
        "terrainProjectileBlockedTileCount": 30,
        "sealedBaseTraversalTileCount": 40,
        "terrainFingerprintSha256": f"{ordinal + 1:064x}",
    }


class LayeredMapsSliceOneHundredEightyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-terrain-batch-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        requirements_fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "ActiveNpcResidencyFixture.java"
        )
        region_fixture = cls.temp / (
            "src/com/openrsc/server/model/world/region/"
            "PackedRegionTerrainBoundaryCaptureFixture.java"
        )
        requirements_fixture.parent.mkdir(parents=True, exist_ok=True)
        region_fixture.parent.mkdir(parents=True, exist_ok=True)
        requirements_fixture.write_text(
            SLICE_182["SLICE_181"]["SLICE_179"][
                "build_requirements_fixture"
            ](),
            encoding="utf-8",
        )
        region_fixture.write_text(FIXTURE, encoding="utf-8")
        classpath = os.pathsep.join(
            str(path) for path in sorted((ROOT / "server/lib").glob("*.jar"))
        )
        subprocess.run(
            [
                "javac", "-Xlint:none", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-cp", classpath,
                "-sourcepath", os.pathsep.join(
                    (str(cls.temp / "src"), str(SERVER_SOURCE))
                ),
                "-d", str(cls.classes), str(requirements_fixture),
                str(region_fixture),
            ],
            cwd=ROOT,
            check=True,
        )
        cls.runtime_classpath = os.pathsep.join(
            (str(cls.classes), classpath)
        )

        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        v49 = json.loads(SCHEMA_V49.read_text(encoding="utf-8"))
        v50 = json.loads(SCHEMA_V50.read_text(encoding="utf-8"))
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$id": (
                "https://spoiled-milk.invalid/schema/"
                "layered-map-parity-event-v51-isolated.schema.json"
            ),
            "$defs": cls.schema["$defs"],
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        registry = Registry().with_resources([
            (v49["$id"], Resource.from_contents(v49)),
            (v50["$id"], Resource.from_contents(v50)),
        ])
        cls.validator = Draft202012Validator(
            isolated, registry=registry
        )
        previous_test = SLICE_177[
            "LayeredMapsSliceOneHundredSeventySevenTest"
        ]
        previous_test.setUpClass()
        cls.evidence = copy.deepcopy(previous_test.evidence)
        cls.evidence["sourceTerrainVerification"] = {
            "generation": 9,
            "requirementsObservedAtTick": 12,
            "observedAtTick": 14,
            "residencyMirrorVersion": 17,
            "authoredGeneration": 9,
            "sourceCount": 2,
            "verifiedTileCount": 4608,
            "terrainBlockedTileCount": 20,
            "terrainCollisionMaskTileCount": 40,
            "terrainProjectileBlockedTileCount": 60,
            "sealedBaseTraversalTileCount": 80,
            "disposableRegionConstructionCount": 2,
            "disposableTerrainApplyCount": 2,
            "usableRegionContainerCount": 0,
            "pointInTimeOnly": True,
            "detachedSummaryOnly": True,
            "allSourcesVerified": True,
            "runtimeHandleRetained": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "terrainAppliedToRuntimeSource": False,
            "authoredReplayPerformed": False,
            "dynamicCollisionRebuildPerformed": False,
            "activeFamilyPreservationPerformed": False,
            "regionRegistryMutated": False,
            "residencyMirrorMutated": False,
            "visibilityCacheMutated": False,
            "arrivalGate": False,
            "visibilityReleased": False,
            "lifecycleAuthority": False,
            "sources": [
                terrain_source(0, 4, 7),
                terrain_source(1, 5, 7),
            ],
        }

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_real_boundary_verifies_disposable_terrain_batch(self):
        result = subprocess.run(
            [
                "java", "-cp", self.runtime_classpath,
                "com.openrsc.server.model.world.region."
                "PackedRegionTerrainBoundaryCaptureFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_v51_schema_accepts_only_bounded_inert_summary(self):
        self.validator.validate(self.evidence)

        owner_refused = copy.deepcopy(self.evidence)
        owner_refused["reason"] = "OWNER_SCOPE_REFUSED"
        owner_refused["ownerScopeEntered"] = False
        owner_refused["sourceLifecycleInvoked"] = False
        owner_refused["sourceAbsencePreflight"] = None
        owner_refused["sourceReloadRecipe"] = None
        owner_refused["sourceTerrainVerification"] = None
        self.validator.validate(owner_refused)

        missing = copy.deepcopy(self.evidence)
        missing["sourceTerrainVerification"] = None
        self.assertFalse(self.validator.is_valid(missing))

        for field in (
            "runtimeHandleRetained",
            "sourceAbsencePerformed",
            "sourceReconstructionPerformed",
            "terrainAppliedToRuntimeSource",
            "authoredReplayPerformed",
            "dynamicCollisionRebuildPerformed",
            "activeFamilyPreservationPerformed",
            "regionRegistryMutated",
            "residencyMirrorMutated",
            "visibilityCacheMutated",
            "arrivalGate",
            "visibilityReleased",
            "lifecycleAuthority",
        ):
            invalid = copy.deepcopy(self.evidence)
            invalid["sourceTerrainVerification"][field] = True
            self.assertFalse(
                self.validator.is_valid(invalid),
                f"schema accepted forbidden terrain-verification {field}",
            )

    def test_batch_is_bounded_and_observer_serializes_no_tiles(self):
        batch = BATCH.read_text(encoding="utf-8")
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        start = handler.index(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic("
        )
        boundary = handler[start:handler.index(
            "private void requireExactPackedSourceBoundary(", start
        )]
        self.assertIn("MAXIMUM_VERIFICATION_SOURCES = 128", batch)
        self.assertIn(
            "LayeredPackedRegionIsolatedTerrainVerifier.verify(", batch
        )
        self.assertNotIn("private final Region ", batch)
        self.assertNotIn("private final TileValue ", batch)
        self.assertLess(
            boundary.index("reloadRecipe[0] ="),
            boundary.index("terrainVerification[0] ="),
        )
        self.assertLess(
            boundary.index("terrainVerification[0] ="),
            boundary.index("captured[0] ="),
        )
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v51"', observer
        )
        self.assertIn(
            'PREVIOUS_EVENT_SCHEMA = "layered-map-parity-event-v50"',
            observer,
        )
        serializer_start = observer.index(
            "appendPackedRegionTerrainVerificationBatch("
        )
        serializer = observer[serializer_start:observer.index(
            "private static void appendPackedRegionEventTargets(",
            serializer_start,
        )]
        self.assertIn("terrainFingerprintSha256", serializer)
        self.assertNotIn("getTiles()", serializer)
        self.assertNotIn("TileValue", serializer)
        self.assertNotIn("LayeredTileState", serializer)

    def test_living_plan_records_slice_one_hundred_eighty_three(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 183: Private isolated-terrain diagnostics", plan
        )
        self.assertIn("schema-v51", plan)


if __name__ == "__main__":
    unittest.main()
