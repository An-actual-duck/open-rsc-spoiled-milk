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
    / "LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch.java"
)
BASELINE_BATCH = (
    REGION / "LayeredPackedRegionAuthoredCollisionVerificationBatch.java"
)
VERIFIER = (
    REGION / "LayeredPackedRegionIsolatedAuthoredCollisionVerifier.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_188 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-eighty-eight.py"
)))


APPLICATION_CAPTURE = r'''
                    applicationBatches[0] =
                        LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
                            .captureWithCollisionPlanFactory(
                                manager, boundary, reload,
                                LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
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
'''


APPLICATION_ASSERTIONS = r'''
        LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
            applicationBatch = applicationBatches[0];
        LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
            .SourceVerification applicationSource =
                applicationBatch.getSources().get(0);
        check(applicationBatch.getGeneration() == 9L
                && applicationBatch.getRequirementsObservedAtTick() == 12L
                && applicationBatch.getObservedAtTick() == 14L
                && applicationBatch.getResidencyMirrorVersion() >= 1L
                && applicationBatch.getAuthoredGeneration() == 9L
                && applicationBatch.getSourceCount() == 1
                && applicationBatch.getReplayPlacementCount() == 5L
                && applicationBatch.getAuthoredObjectFootprintCount() == 3L
                && applicationBatch.getContributionTileReferenceCount() == 4L
                && applicationBatch
                    .getUniqueContributionTileReferenceCount() == 4L
                && applicationBatch.getRequiredRegionReferenceCount() == 4L
                && applicationBatch
                    .getUniqueRequiredRegionReferenceCount() == 2L
                && applicationBatch
                    .getPreApplicationDisposableRegionConstructionCount()
                        == 2L
                && applicationBatch
                    .getDisposableCollisionRegionConstructionCount() == 2L
                && applicationBatch
                    .getTotalDisposableRegionConstructionCount() == 4L
                && applicationBatch.getDisposableTerrainApplyCount() == 2L
                && applicationBatch
                    .getDisposableObjectMembershipApplyCount() == 1L
                && applicationBatch.getCollisionApplicationCount() == 3L
                && applicationBatch.getHeldBoundaryCount() == 4L
                && applicationBatch.getVerifiedRegionTileCount() == 4608L
                && applicationBatch
                    .getBlockingSceneryContributionCount() >= 0L
                && applicationBatch
                    .getDynamicCollisionContributionCount() >= 0L
                && applicationBatch
                    .getDynamicProjectileContributionCount() >= 0L
                && applicationBatch.getBaselineFingerprintSha256().equals(
                    collisionBatch.getFingerprintSha256())
                && applicationBatch.getFingerprintSha256().length() == 64
                && applicationBatch.getUsableRegionContainerCount() == 0,
            "bounded disposable collision application aggregates drifted");
        check(applicationSource.getSourceOrdinal() == 0
                && applicationSource.getPackedRegionX() == 4
                && applicationSource.getPackedRegionY() == 0
                && applicationSource.getReplayPlacementCount() == 5
                && applicationSource.getAuthoredObjectFootprintCount() == 3
                && applicationSource.getContributionTileReferenceCount() == 4
                && applicationSource.getUniqueContributionTileCount() == 4
                && applicationSource.getRequiredRegionReferenceCount() == 4
                && applicationSource.getUniqueRequiredRegionCount() == 2
                && applicationSource
                    .getDisposableRegionConstructionCount() == 2
                && applicationSource.getCollisionApplicationCount() == 3
                && applicationSource.getHeldBoundaryCount() == 4
                && applicationSource.getVerifiedRegionTileCount() == 4608
                && applicationSource.getTerrainFingerprintSha256().length()
                    == 64
                && applicationSource
                    .getAuthoredReplayFingerprintSha256().length() == 64
                && applicationSource
                    .getDefinitionCaptureFingerprintSha256().length() == 64
                && applicationSource
                    .getCollisionFootprintFingerprintSha256().equals(
                        collisionSource
                            .getCollisionFootprintFingerprintSha256())
                && applicationSource
                    .getAppliedCollisionFingerprintSha256().length() == 64,
            "per-source disposable collision receipt lost identity");
        expectUnsupported(() -> applicationBatch.getSources().clear());
        check(applicationBatch.isPointInTimeOnly()
                && applicationBatch.isDetachedSummaryOnly()
                && applicationBatch.isAllSourcesVerified()
                && applicationBatch.isRuntimeDefinitionCapturePerformed()
                && applicationBatch.isCollisionFootprintDerivationPerformed()
                && applicationBatch
                    .isCollisionAppliedToDisposableRegions()
                && !applicationBatch.isCollisionRegistrationAttached()
                && !applicationBatch.isRuntimeCollisionApplied()
                && !applicationBatch.isRuntimeHandleRetained()
                && !applicationBatch.isSourceAbsencePerformed()
                && !applicationBatch.isSourceReconstructionPerformed()
                && !applicationBatch.isTerrainAppliedToRuntimeSource()
                && !applicationBatch
                    .isAuthoredObjectMembershipAppliedToRuntimeSource()
                && !applicationBatch.isNpcMembershipApplied()
                && !applicationBatch.isGroundItemMembershipApplied()
                && !applicationBatch.isSchedulerStateRestored()
                && !applicationBatch.isActiveFamilyPreservationPerformed()
                && !applicationBatch.isRegionRegistryMutated()
                && !applicationBatch.isResidencyMirrorMutated()
                && !applicationBatch.isVisibilityCacheMutated()
                && !applicationBatch.isArrivalGate()
                && !applicationBatch.isVisibilityReleased()
                && !applicationBatch.isLifecycleAuthority(),
            "disposable collision application batch crossed runtime authority");
'''


def build_fixture():
    fixture = SLICE_188["build_fixture"]()
    fixture = fixture.replace(
        """        final LayeredPackedRegionAuthoredCollisionVerificationBatch[]
            collisionBatches =
                new LayeredPackedRegionAuthoredCollisionVerificationBatch[1];""",
        """        final LayeredPackedRegionAuthoredCollisionVerificationBatch[]
            collisionBatches =
                new LayeredPackedRegionAuthoredCollisionVerificationBatch[1];
        final
            LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch[]
                applicationBatches =
                    new
                        LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch[1];""",
        1,
    )
    fixture = fixture.replace(
        """                                });
                    expectIllegalArgument(() ->""",
        """                                });
"""
        + APPLICATION_CAPTURE
        + """                    expectIllegalArgument(() ->""",
        1,
    )
    fixture = fixture.replace(
        """        expectUnsupported(() -> collisionBatch.getSources().clear());""",
        APPLICATION_ASSERTIONS
        + """        expectUnsupported(() -> collisionBatch.getSources().clear());""",
        1,
    )
    fixture = fixture.replace(
        """                && containers[0] != null && collisionBatches[0] != null,""",
        """                && containers[0] != null && collisionBatches[0] != null
                && applicationBatches[0] != null,""",
        1,
    )
    return fixture


class LayeredMapsSliceOneHundredNinetyOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-collision-application-batch-"
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
            SLICE_188["SLICE_185"]["SLICE_182"]["SLICE_181"]["SLICE_179"]
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

    def test_real_boundary_reduces_disposable_collision_application(self):
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

    def test_batch_is_exactly_bounded_and_uses_active_definitions(self):
        source = BATCH.read_text(encoding="utf-8")
        for required in (
            "MAXIMUM_VERIFICATION_SOURCES",
            "captureWithDisposableCollisionApplications(",
            "defineLayeredPackedRegionAuthoredCollisionFootprints(",
            "Collections.unmodifiableList(verified)",
            "isCollisionAppliedToDisposableRegions() { return true; }",
            "isRuntimeCollisionApplied() { return false; }",
        ):
            self.assertIn(required, source)
        self.assertIn(
            "MAXIMUM_VERIFICATION_SOURCES = 128",
            BASELINE_BATCH.read_text(encoding="utf-8"),
        )

    def test_batch_retains_no_disposable_or_runtime_handles(self):
        source = BATCH.read_text(encoding="utf-8")
        for forbidden in (
            "private final Region ",
            "private final RegionManager ",
            "private final TileValue ",
            "private final GameObject ",
            "private final LayeredPackedRegionAuthoredReplayPlan ",
            "private final LayeredPackedRegionAuthoredCollisionFootprintPlan ",
            "private final LayeredPackedRegionIsolatedAuthoredCollisionVerification ",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
            "attachOrderedCollisionRegistrationState(",
        ):
            self.assertNotIn(forbidden, source)

    def test_baseline_public_capture_still_derives_without_application(self):
        baseline = BASELINE_BATCH.read_text(encoding="utf-8")
        public_start = baseline.index(
            "public static LayeredPackedRegionAuthoredCollisionVerificationBatch"
        )
        test_start = baseline.index(
            "captureWithCollisionPlanFactory(", public_start
        )
        public_path = baseline[public_start:test_start]
        self.assertNotIn(
            "captureWithDisposableCollisionApplications(", public_path
        )
        self.assertIn("isCollisionApplied() { return false; }", baseline)
        self.assertIn(
            "LayeredPackedRegionIsolatedAuthoredCollisionVerifier",
            VERIFIER.read_text(encoding="utf-8"),
        )

    def test_living_plan_records_slice_one_hundred_ninety_one(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 191: Bounded disposable collision application",
            plan,
        )
        self.assertIn("count/fingerprint-only", plan)


if __name__ == "__main__":
    unittest.main()
