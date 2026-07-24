#!/usr/bin/env python3
import os
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_SOURCE = ROOT / "server/src"
REGION = SERVER_SOURCE / "com/openrsc/server/model/world/region"
CAPTURE = (
    REGION / "LayeredPackedRegionAuthoredCollisionDefinitionCapture.java"
)
COLLISION_PLAN = (
    REGION / "LayeredPackedRegionAuthoredCollisionFootprintPlan.java"
)
REGION_MANAGER = REGION / "RegionManager.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_186 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-eighty-six.py"
)))


CAPTURE_ASSERTIONS = r'''

        final int[] definitionLookupCounts = new int[]{0, 0};
        LayeredPackedRegionAuthoredCollisionDefinitionCapture capture =
            LayeredPackedRegionAuthoredCollisionDefinitionCapture.capture(
                replay, authoredResult,
                new LayeredPackedRegionAuthoredCollisionDefinitionCapture
                    .DefinitionLookup() {
                    @Override
                    public LayeredPackedRegionAuthoredCollisionDefinitionCapture
                        .DefinitionSnapshot lookupScenery(int objectId) {
                        definitionLookupCounts[0]++;
                        return objectId == 3
                            ? LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                .DefinitionSnapshot.scenery(
                                    1, 2, 1, "tree")
                            : null;
                    }

                    @Override
                    public LayeredPackedRegionAuthoredCollisionDefinitionCapture
                        .DefinitionSnapshot lookupBoundary(int objectId) {
                        definitionLookupCounts[1]++;
                        return objectId == 4
                            ? LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                .DefinitionSnapshot.boundary(1, "gate")
                            : null;
                    }
                });
        check(capture.getGeneration() == 9L
                && capture.getRequirementsObservedAtTick() == 12L
                && capture.getObservedAtTick() == 14L
                && capture.getResidencyMirrorVersion() >= 1L
                && capture.getAuthoredGeneration() == 9L
                && capture.getSourceOrdinal() == 0
                && capture.getPackedRegionX() == 4
                && capture.getPackedRegionY() == 0
                && capture.getAuthoredReplayFingerprintSha256().equals(
                    replay.getFingerprintSha256())
                && capture.getDefinitionCount() == 3
                && capture.getSceneryDefinitionCount() == 1
                && capture.getBoundaryDefinitionCount() == 1
                && capture.getHarvestingSceneryDefinitionCount() == 1
                && capture.getSpecialCollisionlessObjectCount() == 1
                && capture.getDefinitionLookupCount() == 2
                && definitionLookupCounts[0] == 1
                && definitionLookupCounts[1] == 1
                && capture.getFingerprintSha256().length() == 64,
            "read-only definition capture lost exact authored identity");
        check(capture.getDefinitions().get(0).getAuthoredSourceOrdinal() == 1
                && capture.getDefinitions().get(0)
                    .getConstructedEntityId() == 3
                && capture.getDefinitions().get(0).getCollisionType() == 1
                && capture.getDefinitions().get(0).getWidth() == 2
                && capture.getDefinitions().get(0).getHeight() == 1
                && "tree".equals(capture.getDefinitions().get(0).getName())
                && capture.getDefinitions().get(1)
                    .getAuthoredSourceOrdinal() == 2
                && capture.getDefinitions().get(1).getObjectType() == 1
                && "gate".equals(capture.getDefinitions().get(1).getName())
                && capture.getDefinitions().get(2)
                    .getAuthoredSourceOrdinal() == 5
                && capture.getDefinitions().get(2)
                    .getConstructedEntityId() == 1147
                && !capture.getDefinitions().get(2)
                    .isDefinitionAvailable(),
            "definition table scalars were not reduced in authored order");
        expectUnsupported(() -> capture.getDefinitions().clear());
        check(capture.getFingerprintSha256().equals(
                LayeredPackedRegionAuthoredCollisionDefinitionCapture.capture(
                    replay, authoredResult,
                    new LayeredPackedRegionAuthoredCollisionDefinitionCapture
                        .DefinitionLookup() {
                        @Override
                        public
                            LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                .DefinitionSnapshot lookupScenery(
                                    int objectId) {
                            return
                                LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                    .DefinitionSnapshot.scenery(
                                        1, 2, 1, "tree");
                        }

                        @Override
                        public
                            LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                .DefinitionSnapshot lookupBoundary(
                                    int objectId) {
                            return
                                LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                    .DefinitionSnapshot.boundary(1, "gate");
                        }
                    }).getFingerprintSha256()),
            "definition capture fingerprint is not deterministic");

        LayeredPackedRegionAuthoredCollisionFootprintPlan capturedPlan =
            LayeredPackedRegionAuthoredCollisionFootprintPlan.define(
                replay, authoredResult, capture,
                new String[]{"gate"}, 1008, 4032);
        check(capturedPlan.isRuntimeDefinitionCapturePerformed()
                && capturedPlan.getDefinitionCaptureFingerprintSha256().equals(
                    capture.getFingerprintSha256())
                && capturedPlan.getObjectFootprintCount() == 3
                && capturedPlan.getContributionTileReferenceCount() == 4
                && capturedPlan.getUniqueRequiredRegionCount() == 2
                && !capturedPlan.isRegionBoundaryAcquired()
                && !capturedPlan.isCollisionApplied()
                && !capturedPlan.isCollisionRegistrationAttached()
                && !capturedPlan.isRuntimeSourceMutated()
                && !capturedPlan.isRuntimeHandleRetained()
                && !capturedPlan.isLifecycleAuthority()
                && !capturedPlan.getFingerprintSha256().equals(
                    collisionPlan.getFingerprintSha256()),
            "captured definitions did not compose as detached provenance");

        expectIllegalArgument(() ->
            LayeredPackedRegionAuthoredCollisionDefinitionCapture.capture(
                replay, authoredResult,
                new LayeredPackedRegionAuthoredCollisionDefinitionCapture
                    .DefinitionLookup() {
                    @Override
                    public LayeredPackedRegionAuthoredCollisionDefinitionCapture
                        .DefinitionSnapshot lookupScenery(int objectId) {
                        return null;
                    }

                    @Override
                    public LayeredPackedRegionAuthoredCollisionDefinitionCapture
                        .DefinitionSnapshot lookupBoundary(int objectId) {
                        return LayeredPackedRegionAuthoredCollisionDefinitionCapture
                            .DefinitionSnapshot.boundary(1, "gate");
                    }
                }));
        expectIllegalArgument(() ->
            LayeredPackedRegionAuthoredCollisionDefinitionCapture.capture(
                replay, authoredResult,
                new LayeredPackedRegionAuthoredCollisionDefinitionCapture
                    .DefinitionLookup() {
                    @Override
                    public LayeredPackedRegionAuthoredCollisionDefinitionCapture
                        .DefinitionSnapshot lookupScenery(int objectId) {
                        return LayeredPackedRegionAuthoredCollisionDefinitionCapture
                            .DefinitionSnapshot.boundary(1, "wrong-kind");
                    }

                    @Override
                    public LayeredPackedRegionAuthoredCollisionDefinitionCapture
                        .DefinitionSnapshot lookupBoundary(int objectId) {
                        return LayeredPackedRegionAuthoredCollisionDefinitionCapture
                            .DefinitionSnapshot.boundary(1, "gate");
                    }
                }));
        check(capture.isReadOnlyDefinitionCapture()
                && capture.isDefinitionSequenceMatched()
                && !capture.isDefinitionLookupRetained()
                && !capture.isDefinitionTableObjectRetained()
                && !capture.isRegionLookupPerformed()
                && !capture.isRegionBoundaryAcquired()
                && !capture.isCollisionApplied()
                && !capture.isCollisionRegistrationAttached()
                && !capture.isRuntimeSourceMutated()
                && !capture.isRuntimeHandleRetained()
                && !capture.isRegionRegistryMutated()
                && !capture.isResidencyMirrorMutated()
                && !capture.isVisibilityCacheMutated()
                && !capture.isArrivalGate()
                && !capture.isVisibilityReleased()
                && !capture.isLifecycleAuthority(),
            "definition capture crossed its read-only boundary");
'''


def build_fixture():
    fixture = SLICE_186["build_fixture"]()
    marker = (
        "        LayeredPackedRegionTerrainInitializationPlan collidingTerrain ="
    )
    return fixture.replace(marker, CAPTURE_ASSERTIONS + "\n" + marker, 1)


class LayeredMapsSliceOneHundredEightySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-definition-capture-"
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
            SLICE_186["SLICE_185"]["SLICE_182"]["SLICE_181"]["SLICE_179"]
            ["build_requirements_fixture"](),
            encoding="utf-8",
        )
        region_fixture.write_text(build_fixture(), encoding="utf-8")
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

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_definition_capture_composes_with_detached_collision_plan(self):
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

    def test_capture_retains_no_definition_table_or_runtime_handle(self):
        source = CAPTURE.read_text(encoding="utf-8")
        for required in (
            "lookup.lookupScenery(",
            "lookup.lookupBoundary(",
            "specialCollisionlessScenery(",
            "Collections.unmodifiableList(copied)",
            "isDefinitionLookupRetained() { return false; }",
            "isDefinitionTableObjectRetained() { return false; }",
            "isCollisionApplied() { return false; }",
        ):
            self.assertIn(required, source)
        for forbidden in (
            "import com.openrsc.server.external",
            "import com.openrsc.server.model.entity",
            "private final DefinitionLookup ",
            "private final DefinitionSnapshot ",
            "private final LayeredPackedRegionAuthoredReplayPlan ",
            "private final LayeredPackedRegionIsolatedAuthoredObjectVerification ",
            "RegionManager ",
            "new Region(",
            "getRegion(",
            "TileValue ",
            "applyCollisionFootprint",
            "attachOrderedCollisionRegistrationState(",
        ):
            self.assertNotIn(forbidden, source)

    def test_region_manager_adapter_reads_only_active_definition_table(self):
        source = REGION_MANAGER.read_text(encoding="utf-8")
        start = source.index(
            "captureLayeredPackedRegionAuthoredCollisionDefinitions("
        )
        end = source.index("\n\tpublic World getWorld()", start)
        adapter = source[start:end]
        for required in (
            "final EntityHandler entityHandler",
            "entityHandler.getGameObjectDef(objectId)",
            "entityHandler.getDoorDef(objectId)",
            "DefinitionSnapshot.scenery(",
            "DefinitionSnapshot.boundary(",
            "Constants.objectsProjectileClipAllowed",
            "Constants.MAX_WIDTH, Constants.MAX_HEIGHT",
        ):
            self.assertIn(required, adapter)
        for forbidden in (
            "getRegion(",
            "getMutableTile(",
            "getTile(",
            "applyCollisionFootprint",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
            "layeredRegionResidencyMirror",
            "visibleRegionWindowCache",
            "layeredRegionLifecycleLock",
        ):
            self.assertNotIn(forbidden, adapter)
        collision = COLLISION_PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "true, capture.getFingerprintSha256());", collision
        )
        self.assertIn(
            "return runtimeDefinitionCapturePerformed;", collision
        )

    def test_living_plan_records_slice_one_hundred_eighty_seven(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 187: Active collision-definition capture", plan
        )
        self.assertIn("active server definition table", plan)
        self.assertIn("object ID 1147", plan)


if __name__ == "__main__":
    unittest.main()
