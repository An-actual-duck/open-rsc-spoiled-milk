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
BATCH = (
    REGION
    / "LayeredPackedRegionAuthoredCollisionVerificationBatch.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_185 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-eighty-five.py"
)))


BATCH_CAPTURE = r'''
                    collisionBatches[0] =
                        LayeredPackedRegionAuthoredCollisionVerificationBatch
                            .captureWithCollisionPlanFactory(
                                manager, boundary, reload,
                                LayeredPackedRegionAuthoredCollisionVerificationBatch
                                    .MAXIMUM_VERIFICATION_SOURCES,
                                new
                                    LayeredPackedRegionAuthoredCollisionVerificationBatch
                                        .CollisionPlanFactory() {
                                    @Override
                                    public
                                        LayeredPackedRegionAuthoredCollisionFootprintPlan
                                            define(
                                                LayeredPackedRegionAuthoredReplayPlan
                                                    replay,
                                                LayeredPackedRegionIsolatedAuthoredObjectVerification
                                                    membership) {
                                        LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                            capture =
                                                LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                                    .capture(
                                                        replay, membership,
                                                        new
                                                            LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                                                .DefinitionLookup() {
                                                            @Override
                                                            public
                                                                LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                                                    .DefinitionSnapshot
                                                                        lookupScenery(
                                                                            int objectId) {
                                                                return
                                                                    LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                                                        .DefinitionSnapshot
                                                                            .scenery(
                                                                                objectId == 3
                                                                                    ? 1
                                                                                    : 0,
                                                                                objectId == 3
                                                                                    ? 2
                                                                                    : 1,
                                                                                1,
                                                                                objectId == 3
                                                                                    ? "tree"
                                                                                    : "harvest");
                                                            }

                                                            @Override
                                                            public
                                                                LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                                                    .DefinitionSnapshot
                                                                        lookupBoundary(
                                                                            int objectId) {
                                                                return
                                                                    LayeredPackedRegionAuthoredCollisionDefinitionCapture
                                                                        .DefinitionSnapshot
                                                                            .boundary(
                                                                                1,
                                                                                "gate");
                                                            }
                                                        });
                                        return
                                            LayeredPackedRegionAuthoredCollisionFootprintPlan
                                                .define(
                                                    replay, membership,
                                                    capture,
                                                    new String[]{"gate"},
                                                    1008, 4032);
                                    }
                                });
                    expectIllegalArgument(() ->
                        LayeredPackedRegionAuthoredCollisionVerificationBatch
                            .captureWithCollisionPlanFactory(
                                manager, boundary, reload, 0,
                                (replay, membership) -> {
                                    throw new AssertionError(
                                        "invalid budget reached factory");
                                }));
'''


BATCH_ASSERTIONS = r'''
        LayeredPackedRegionAuthoredCollisionVerificationBatch collisionBatch =
            collisionBatches[0];
        LayeredPackedRegionAuthoredCollisionVerificationBatch
            .SourceVerification collisionSource =
                collisionBatch.getSources().get(0);
        check(collisionBatch.getGeneration() == 9L
                && collisionBatch.getRequirementsObservedAtTick() == 12L
                && collisionBatch.getObservedAtTick() == 14L
                && collisionBatch.getResidencyMirrorVersion() >= 1L
                && collisionBatch.getAuthoredGeneration() == 9L
                && collisionBatch.getSourceCount() == 1
                && collisionBatch.getReplayPlacementCount() == 5L
                && collisionBatch.getAuthoredObjectFootprintCount() == 3L
                && collisionBatch.getDefinitionBackedObjectCount() == 3L
                && collisionBatch.getSpecialCollisionlessObjectCount() == 0L
                && collisionBatch.getZeroContributionObjectCount() == 1L
                && collisionBatch.getCrossSourceCollisionObjectCount() == 1L
                && collisionBatch
                    .getCollisionBeyondAuthoredDependencyObjectCount() == 1L
                && collisionBatch.getContributionTileReferenceCount() == 4L
                && collisionBatch.getRequiredRegionReferenceCount() == 4L
                && collisionBatch
                    .getUniqueRequiredRegionReferenceCount() == 2L
                && collisionBatch.getFingerprintSha256().length() == 64
                && collisionBatch.getDisposableRegionConstructionCount() == 2
                && collisionBatch.getDisposableTerrainApplyCount() == 2
                && collisionBatch
                    .getDisposableObjectMembershipApplyCount() == 1
                && collisionBatch.getUsableRegionContainerCount() == 0,
            "bounded collision verification aggregates are incomplete");
        check(collisionSource.getSourceOrdinal() == 0
                && collisionSource.getPackedRegionX() == 4
                && collisionSource.getPackedRegionY() == 0
                && collisionSource.getReplayPlacementCount() == 5
                && collisionSource.getAuthoredObjectFootprintCount() == 3
                && collisionSource.getDefinitionBackedObjectCount() == 3
                && collisionSource.getSpecialCollisionlessObjectCount() == 0
                && collisionSource.getZeroContributionObjectCount() == 1
                && collisionSource.getCrossSourceCollisionObjectCount() == 1
                && collisionSource
                    .getCollisionBeyondAuthoredDependencyObjectCount() == 1
                && collisionSource.getContributionTileReferenceCount() == 4
                && collisionSource.getRequiredRegionReferenceCount() == 4
                && collisionSource.getUniqueRequiredRegionCount() == 2
                && collisionSource.getTerrainFingerprintSha256().length()
                    == 64
                && collisionSource
                    .getAuthoredReplayFingerprintSha256().length() == 64
                && collisionSource
                    .getDefinitionCaptureFingerprintSha256().length() == 64
                && collisionSource
                    .getCollisionFootprintFingerprintSha256().length() == 64,
            "per-source collision verification lost exact identity");
        expectUnsupported(() -> collisionBatch.getSources().clear());
        check(collisionBatch.isPointInTimeOnly()
                && collisionBatch.isDetachedSummaryOnly()
                && collisionBatch.isAllSourcesVerified()
                && collisionBatch.isRuntimeDefinitionCapturePerformed()
                && collisionBatch.isCollisionFootprintDerivationPerformed()
                && !collisionBatch.isCollisionApplied()
                && !collisionBatch.isCollisionRegistrationAttached()
                && !collisionBatch.isRuntimeHandleRetained()
                && !collisionBatch.isSourceAbsencePerformed()
                && !collisionBatch.isSourceReconstructionPerformed()
                && !collisionBatch.isTerrainAppliedToRuntimeSource()
                && !collisionBatch.isNpcMembershipApplied()
                && !collisionBatch.isGroundItemMembershipApplied()
                && !collisionBatch.isSchedulerStateRestored()
                && !collisionBatch.isActiveFamilyPreservationPerformed()
                && !collisionBatch.isRegionRegistryMutated()
                && !collisionBatch.isResidencyMirrorMutated()
                && !collisionBatch.isVisibilityCacheMutated()
                && !collisionBatch.isArrivalGate()
                && !collisionBatch.isVisibilityReleased()
                && !collisionBatch.isLifecycleAuthority(),
            "collision verification batch crossed runtime authority");
'''


def build_fixture():
    fixture = SLICE_185["build_fixture"]()
    fixture = fixture.replace(
        """        final LayeredPackedRegionReloadRecipe[] collidingReloads =
            new LayeredPackedRegionReloadRecipe[1];""",
        """        final LayeredPackedRegionReloadRecipe[] collidingReloads =
            new LayeredPackedRegionReloadRecipe[1];
        final LayeredPackedRegionAuthoredCollisionVerificationBatch[]
            collisionBatches =
                new LayeredPackedRegionAuthoredCollisionVerificationBatch[1];""",
        1,
    )
    fixture = fixture.replace(
        """                    terrain[0] =
                        LayeredPackedRegionTerrainInitializationPlan
                            .defineFromResidentTileStates(
                                container, boundary, captured[0]);
                });""",
        """                    terrain[0] =
                        LayeredPackedRegionTerrainInitializationPlan
                            .defineFromResidentTileStates(
                                container, boundary, captured[0]);
"""
        + BATCH_CAPTURE
        + """                });""",
        1,
    )
    fixture = fixture.replace(
        """        check(entered && captured[0] != null && terrain[0] != null
                && containers[0] != null,
            "real source lifecycle boundary did not produce terrain input");""",
        """        check(entered && captured[0] != null && terrain[0] != null
                && containers[0] != null && collisionBatches[0] != null,
            "real source lifecycle boundary did not produce collision input");
"""
        + BATCH_ASSERTIONS,
        1,
    )
    fixture = fixture.replace(
        """    private static void expectUnsupported(Runnable operation) {""",
        """    private static void expectIllegalArgument(
        Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Runnable operation) {""",
        1,
    )
    return fixture


class LayeredMapsSliceOneHundredEightyEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-collision-batch-"
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
            SLICE_185["SLICE_182"]["SLICE_181"]["SLICE_179"]
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

    def test_real_boundary_reduces_collision_verification_to_summary(self):
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

    def test_batch_discards_every_runtime_and_disposable_handle(self):
        source = BATCH.read_text(encoding="utf-8")
        for required in (
            "MAXIMUM_VERIFICATION_SOURCES = 128",
            "captureLayeredPackedRegionTerrainTileStates(",
            "LayeredPackedRegionIsolatedTerrainVerifier.verify(",
            "LayeredPackedRegionIsolatedAuthoredObjectVerifier.verify(",
            "defineLayeredPackedRegionAuthoredCollisionFootprints(",
            "Collections.unmodifiableList(verified)",
            "isCollisionApplied() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
        ):
            self.assertIn(required, source)
        for forbidden in (
            "private final Region ",
            "private final RegionManager ",
            "private final TileValue ",
            "private final GameObject ",
            "private final LayeredPackedRegionAuthoredReplayPlan ",
            "private final LayeredPackedRegionIsolatedAuthoredObjectVerification ",
            "private final LayeredPackedRegionAuthoredCollisionFootprintPlan ",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
            "applyCollisionFootprint",
            "attachOrderedCollisionRegistrationState(",
        ):
            self.assertNotIn(forbidden, source)

    def test_public_path_requires_active_definition_capture(self):
        source = BATCH.read_text(encoding="utf-8")
        public_start = source.index(
            "public static LayeredPackedRegionAuthoredCollisionVerificationBatch"
        )
        test_start = source.index(
            "captureWithCollisionPlanFactory(", public_start
        )
        public_path = source[public_start:test_start]
        self.assertIn(
            "defineLayeredPackedRegionAuthoredCollisionFootprints(",
            public_path,
        )
        self.assertNotIn("DefinitionLookup", public_path)
        self.assertIn(
            "!collision.isRuntimeDefinitionCapturePerformed()", source
        )

    def test_living_plan_records_slice_one_hundred_eighty_eight(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 188: Bounded authored-collision verification batch",
            plan,
        )
        self.assertIn("count/fingerprint-only", plan)


if __name__ == "__main__":
    unittest.main()
