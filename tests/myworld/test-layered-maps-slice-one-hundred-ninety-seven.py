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
    / "LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch.java"
)
BASELINE_BATCH = (
    REGION / "LayeredPackedRegionAuthoredCollisionVerificationBatch.java"
)
RECEIPT = (
    REGION
    / "LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_194 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-ninety-four.py"
)))
SLICE_191 = SLICE_194["SLICE_191"]

TRANSACTIONAL_CAPTURE = (
    SLICE_191["APPLICATION_CAPTURE"]
    .replace("applicationBatches[0] =", "transactionalBatches[0] =")
    .replace(
        "LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch",
        "LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch",
    )
)

TRANSACTIONAL_ASSERTIONS = r'''
        LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
            transactionalBatch = transactionalBatches[0];
        LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch
            .SourceVerification transactionalSource =
                transactionalBatch.getSources().get(0);
        check(transactionalBatch.getGeneration() == 9L
                && transactionalBatch.getRequirementsObservedAtTick() == 12L
                && transactionalBatch.getObservedAtTick() == 14L
                && transactionalBatch.getResidencyMirrorVersion() >= 1L
                && transactionalBatch.getAuthoredGeneration() == 9L
                && transactionalBatch.getSourceCount() == 1
                && transactionalBatch.getReplayPlacementCount() == 5L
                && transactionalBatch
                    .getAuthoredObjectFootprintCount() == 3L
                && transactionalBatch
                    .getContributionTileReferenceCount() == 4L
                && transactionalBatch
                    .getRequiredRegionReferenceCount() == 4L
                && transactionalBatch
                    .getUniqueRequiredRegionReferenceCount() == 2L
                && transactionalBatch
                    .getPreTransactionalDisposableRegionConstructionCount()
                    == 2L
                && transactionalBatch
                    .getTransactionalDisposableRegionConstructionCount()
                    == 2L
                && transactionalBatch
                    .getTotalDisposableRegionConstructionCount() == 4L
                && transactionalBatch
                    .getTransactionalSupportRegionCount() == 1L
                && transactionalBatch
                    .getObjectCollisionTransactionCount() == 3L
                && transactionalBatch
                    .getObjectCollisionTransactionBoundaryCount() == 4L
                && transactionalBatch
                    .getDisposableCacheInvalidationCount() == 3L
                && transactionalBatch.getCollisionRegistrationCount() == 3L
                && transactionalBatch
                    .getCollisionRegistrationContributionCount() == 4L
                && transactionalBatch
                    .getCollisionRegistrationRegionReferenceCount() == 4L
                && transactionalBatch
                    .getTransactionalVerifiedRegionTileCount() == 4608L
                && transactionalBatch
                    .getTransactionalBlockingSceneryContributionCount()
                    == combinedBatch
                        .getCombinedBlockingSceneryContributionCount()
                && transactionalBatch
                    .getTransactionalDynamicCollisionContributionCount()
                    == combinedBatch
                        .getCombinedDynamicCollisionContributionCount()
                && transactionalBatch
                    .getTransactionalDynamicProjectileContributionCount()
                    == combinedBatch
                        .getCombinedDynamicProjectileContributionCount()
                && transactionalBatch.getBaselineFingerprintSha256().equals(
                    collisionBatch.getFingerprintSha256())
                && transactionalBatch.getFingerprintSha256().length() == 64
                && transactionalBatch.getUsableRegionContainerCount() == 0,
            "bounded transactional authored source aggregates drifted");
        check(transactionalSource.getSourceOrdinal() == 0
                && transactionalSource.getPackedRegionX() == 4
                && transactionalSource.getPackedRegionY() == 0
                && transactionalSource.getReplayPlacementCount() == 5
                && transactionalSource
                    .getAuthoredObjectFootprintCount() == 3
                && transactionalSource
                    .getContributionTileReferenceCount() == 4
                && transactionalSource.getRequiredRegionReferenceCount() == 4
                && transactionalSource.getUniqueRequiredRegionCount() == 2
                && transactionalSource
                    .getDisposableRegionConstructionCount() == 2
                && transactionalSource.getSupportRegionCount() == 1
                && transactionalSource
                    .getObjectCollisionTransactionCount() == 3
                && transactionalSource
                    .getObjectCollisionTransactionBoundaryCount() == 4
                && transactionalSource
                    .getDisposableCacheInvalidationCount() == 3
                && transactionalSource.getCollisionRegistrationCount() == 3
                && transactionalSource
                    .getCollisionRegistrationContributionCount() == 4
                && transactionalSource
                    .getCollisionRegistrationRegionReferenceCount() == 4
                && transactionalSource.getVerifiedRegionTileCount() == 4608
                && transactionalSource.getTerrainFingerprintSha256().equals(
                    combinedSource.getTerrainFingerprintSha256())
                && transactionalSource
                    .getAuthoredReplayFingerprintSha256().equals(
                        combinedSource
                            .getAuthoredReplayFingerprintSha256())
                && transactionalSource
                    .getDefinitionCaptureFingerprintSha256().equals(
                        combinedSource
                            .getDefinitionCaptureFingerprintSha256())
                && transactionalSource
                    .getCollisionFootprintFingerprintSha256().equals(
                        combinedSource
                            .getCollisionFootprintFingerprintSha256())
                && transactionalSource
                    .getAppliedCollisionFingerprintSha256().equals(
                        combinedSource
                            .getAppliedCollisionFingerprintSha256())
                && transactionalSource
                    .getFinalStateFingerprintSha256().equals(
                        combinedSource.getFinalStateFingerprintSha256())
                && transactionalSource
                    .getCollisionRegistrationFingerprintSha256().length()
                    == 64,
            "per-source transactional authored state lost exact identity");
        expectUnsupported(() -> transactionalBatch.getSources().clear());
        check(transactionalBatch.isPointInTimeOnly()
                && transactionalBatch.isDetachedSummaryOnly()
                && transactionalBatch.isAllSourcesVerified()
                && transactionalBatch.isRuntimeDefinitionCapturePerformed()
                && transactionalBatch
                    .isCollisionFootprintDerivationPerformed()
                && transactionalBatch
                    .isObjectCollisionTransactionAppliedToDisposableRegions()
                && transactionalBatch
                    .isCollisionRegistrationAttachedToDisposableObjects()
                && transactionalBatch.isDisposableCacheInvalidationOnly()
                && !transactionalBatch.isRuntimeCollisionApplied()
                && !transactionalBatch.isRuntimeHandleRetained()
                && !transactionalBatch.isSourceAbsencePerformed()
                && !transactionalBatch.isSourceReconstructionPerformed()
                && !transactionalBatch.isTerrainAppliedToRuntimeSource()
                && !transactionalBatch
                    .isAuthoredObjectMembershipAppliedToRuntimeSource()
                && !transactionalBatch.isNpcMembershipApplied()
                && !transactionalBatch.isGroundItemMembershipApplied()
                && !transactionalBatch.isSchedulerStateRestored()
                && !transactionalBatch
                    .isActiveFamilyPreservationPerformed()
                && !transactionalBatch.isRuntimeCacheInvalidated()
                && !transactionalBatch.isRegionRegistryMutated()
                && !transactionalBatch.isResidencyMirrorMutated()
                && !transactionalBatch.isVisibilityCacheMutated()
                && !transactionalBatch.isArrivalGate()
                && !transactionalBatch.isVisibilityReleased()
                && !transactionalBatch.isLifecycleAuthority(),
            "transactional source-state batch crossed runtime authority");
'''


def build_fixture():
    fixture = SLICE_194["build_fixture"]()
    fixture = fixture.replace(
        """        boolean entered =
            manager.withinLayeredPackedRegionSourceLifecycleBoundary(""",
        """        final
            LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch[]
                transactionalBatches =
                    new
                        LayeredPackedRegionTransactionalAuthoredSourceVerificationBatch[1];
        boolean entered =
            manager.withinLayeredPackedRegionSourceLifecycleBoundary(""",
        1,
    )
    fixture = fixture.replace(
        """                    expectIllegalArgument(() ->""",
        TRANSACTIONAL_CAPTURE
        + """                    expectIllegalArgument(() ->""",
        1,
    )
    fixture = fixture.replace(
        """        expectUnsupported(() -> collisionBatch.getSources().clear());""",
        TRANSACTIONAL_ASSERTIONS
        + """        expectUnsupported(() -> collisionBatch.getSources().clear());""",
        1,
    )
    fixture = fixture.replace(
        """                && combinedBatches[0] != null,""",
        """                && combinedBatches[0] != null
                && transactionalBatches[0] != null,""",
        1,
    )
    return fixture


class LayeredMapsSliceOneHundredNinetySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-transactional-state-batch-"
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
            SLICE_191["SLICE_188"]["SLICE_185"]["SLICE_182"]
            ["SLICE_181"]["SLICE_179"]["build_requirements_fixture"](),
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

    def test_real_boundary_reduces_transactional_source_states(self):
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

    def test_batch_is_exactly_bounded_and_keeps_provenance(self):
        source = BATCH.read_text(encoding="utf-8")
        for required in (
            "MAXIMUM_VERIFICATION_SOURCES",
            "captureWithTransactionalSourceStates(",
            "getCollisionRegistrationFingerprintSha256()",
            "Collections.unmodifiableList(verified)",
            "isObjectCollisionTransactionAppliedToDisposableRegions()",
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
            "private final GameObjectCollisionRegistrationState ",
            "private final LayeredPackedRegionAuthoredReplayPlan ",
            "private final LayeredPackedRegionAuthoredCollisionFootprintPlan ",
            "private final LayeredPackedRegionIsolatedTransactional",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
        ):
            self.assertNotIn(forbidden, source)
        self.assertIn(
            "isUsableRegionContainerReturned() { return false; }",
            RECEIPT.read_text(encoding="utf-8"),
        )

    def test_baseline_public_capture_remains_derivation_only(self):
        baseline = BASELINE_BATCH.read_text(encoding="utf-8")
        public_start = baseline.index(
            "public static LayeredPackedRegionAuthoredCollisionVerificationBatch"
        )
        test_start = baseline.index(
            "captureWithCollisionPlanFactory(", public_start
        )
        public_path = baseline[public_start:test_start]
        self.assertNotIn(
            "captureWithTransactionalSourceStates(", public_path
        )
        self.assertIn("isCollisionApplied() { return false; }", baseline)
        self.assertIn(
            "isCollisionRegistrationAttached() { return false; }", baseline
        )

    def test_living_plan_records_slice_one_hundred_ninety_seven(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 197: Bounded transactional authored source states",
            plan,
        )
        self.assertIn("registration fingerprint", plan)


if __name__ == "__main__":
    unittest.main()
