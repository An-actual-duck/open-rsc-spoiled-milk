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
    REGION / "LayeredPackedRegionAuthoredSourceStateVerificationBatch.java"
)
BASELINE_BATCH = (
    REGION / "LayeredPackedRegionAuthoredCollisionVerificationBatch.java"
)
RECEIPT = (
    REGION
    / "LayeredPackedRegionIsolatedAuthoredSourceStateVerification.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_191 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-ninety-one.py"
)))

COMBINED_CAPTURE = (
    SLICE_191["APPLICATION_CAPTURE"]
    .replace("applicationBatches[0] =", "combinedBatches[0] =")
    .replace(
        "LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch",
        "LayeredPackedRegionAuthoredSourceStateVerificationBatch",
    )
)

COMBINED_ASSERTIONS = r'''
        LayeredPackedRegionAuthoredSourceStateVerificationBatch
            combinedBatch = combinedBatches[0];
        LayeredPackedRegionAuthoredSourceStateVerificationBatch
            .SourceVerification combinedSource =
                combinedBatch.getSources().get(0);
        check(combinedBatch.getGeneration() == 9L
                && combinedBatch.getRequirementsObservedAtTick() == 12L
                && combinedBatch.getObservedAtTick() == 14L
                && combinedBatch.getResidencyMirrorVersion() >= 1L
                && combinedBatch.getAuthoredGeneration() == 9L
                && combinedBatch.getSourceCount() == 1
                && combinedBatch.getReplayPlacementCount() == 5L
                && combinedBatch.getAuthoredObjectFootprintCount() == 3L
                && combinedBatch.getContributionTileReferenceCount() == 4L
                && combinedBatch.getRequiredRegionReferenceCount() == 4L
                && combinedBatch.getUniqueRequiredRegionReferenceCount()
                    == 2L
                && combinedBatch
                    .getPreCombinedDisposableRegionConstructionCount() == 2L
                && combinedBatch
                    .getCombinedDisposableRegionConstructionCount() == 2L
                && combinedBatch
                    .getTotalDisposableRegionConstructionCount() == 4L
                && combinedBatch.getCombinedSupportRegionCount() == 1L
                && combinedBatch.getPreCombinedTerrainApplyCount() == 2L
                && combinedBatch.getCombinedTerrainApplyCount() == 1L
                && combinedBatch.getTotalTerrainApplyCount() == 3L
                && combinedBatch
                    .getPreCombinedObjectMembershipApplyCount() == 1L
                && combinedBatch
                    .getCombinedObjectMembershipApplicationCount() == 3L
                && combinedBatch
                    .getCombinedObjectMembershipBoundaryCount() == 3L
                && combinedBatch
                    .getCombinedCollisionApplicationCount() == 3L
                && combinedBatch.getCombinedCollisionBoundaryCount() == 4L
                && combinedBatch.getCombinedVerifiedRegionTileCount()
                    == 4608L
                && combinedBatch
                    .getCombinedBlockingSceneryContributionCount() >= 0L
                && combinedBatch
                    .getCombinedDynamicCollisionContributionCount() >= 0L
                && combinedBatch
                    .getCombinedDynamicProjectileContributionCount() >= 0L
                && combinedBatch.getBaselineFingerprintSha256().equals(
                    collisionBatch.getFingerprintSha256())
                && combinedBatch.getFingerprintSha256().length() == 64
                && combinedBatch.getUsableRegionContainerCount() == 0,
            "bounded combined authored source-state aggregates drifted");
        check(combinedSource.getSourceOrdinal() == 0
                && combinedSource.getPackedRegionX() == 4
                && combinedSource.getPackedRegionY() == 0
                && combinedSource.getReplayPlacementCount() == 5
                && combinedSource.getAuthoredObjectFootprintCount() == 3
                && combinedSource.getContributionTileReferenceCount() == 4
                && combinedSource.getRequiredRegionReferenceCount() == 4
                && combinedSource.getUniqueRequiredRegionCount() == 2
                && combinedSource.getDisposableRegionConstructionCount()
                    == 2
                && combinedSource.getSupportRegionCount() == 1
                && combinedSource.getObjectMembershipApplicationCount() == 3
                && combinedSource.getObjectMembershipBoundaryCount() == 3
                && combinedSource.getCollisionApplicationCount() == 3
                && combinedSource.getCollisionBoundaryCount() == 4
                && combinedSource.getVerifiedRegionTileCount() == 4608
                && combinedSource.getTerrainFingerprintSha256().length()
                    == 64
                && combinedSource
                    .getAuthoredReplayFingerprintSha256().length() == 64
                && combinedSource
                    .getDefinitionCaptureFingerprintSha256().length() == 64
                && combinedSource
                    .getCollisionFootprintFingerprintSha256().equals(
                        collisionSource
                            .getCollisionFootprintFingerprintSha256())
                && combinedSource
                    .getAppliedCollisionFingerprintSha256().length() == 64
                && combinedSource.getFinalStateFingerprintSha256().length()
                    == 64,
            "per-source combined authored state lost exact identity");
        expectUnsupported(() -> combinedBatch.getSources().clear());
        check(combinedBatch.isPointInTimeOnly()
                && combinedBatch.isDetachedSummaryOnly()
                && combinedBatch.isAllSourcesVerified()
                && combinedBatch.isRuntimeDefinitionCapturePerformed()
                && combinedBatch.isCollisionFootprintDerivationPerformed()
                && combinedBatch
                    .isTerrainAppliedToCombinedDisposableSourceRegions()
                && combinedBatch
                    .isAuthoredObjectMembershipAppliedToCombinedDisposableSourceRegions()
                && combinedBatch
                    .isCollisionAppliedToSameDisposableRegionUnions()
                && !combinedBatch.isCollisionRegistrationAttached()
                && !combinedBatch.isRuntimeCollisionApplied()
                && !combinedBatch.isRuntimeHandleRetained()
                && !combinedBatch.isSourceAbsencePerformed()
                && !combinedBatch.isSourceReconstructionPerformed()
                && !combinedBatch.isTerrainAppliedToRuntimeSource()
                && !combinedBatch
                    .isAuthoredObjectMembershipAppliedToRuntimeSource()
                && !combinedBatch.isNpcMembershipApplied()
                && !combinedBatch.isGroundItemMembershipApplied()
                && !combinedBatch.isSchedulerStateRestored()
                && !combinedBatch.isActiveFamilyPreservationPerformed()
                && !combinedBatch.isRegionRegistryMutated()
                && !combinedBatch.isResidencyMirrorMutated()
                && !combinedBatch.isVisibilityCacheMutated()
                && !combinedBatch.isArrivalGate()
                && !combinedBatch.isVisibilityReleased()
                && !combinedBatch.isLifecycleAuthority(),
            "combined source-state batch crossed runtime authority");
'''


def build_fixture():
    fixture = SLICE_191["build_fixture"]()
    fixture = fixture.replace(
        """                        LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch[1];""",
        """                        LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch[1];
        final LayeredPackedRegionAuthoredSourceStateVerificationBatch[]
            combinedBatches =
                new
                    LayeredPackedRegionAuthoredSourceStateVerificationBatch[1];""",
        1,
    )
    fixture = fixture.replace(
        """                    expectIllegalArgument(() ->""",
        COMBINED_CAPTURE
        + """                    expectIllegalArgument(() ->""",
        1,
    )
    fixture = fixture.replace(
        """        expectUnsupported(() -> collisionBatch.getSources().clear());""",
        COMBINED_ASSERTIONS
        + """        expectUnsupported(() -> collisionBatch.getSources().clear());""",
        1,
    )
    fixture = fixture.replace(
        """                && applicationBatches[0] != null,""",
        """                && applicationBatches[0] != null
                && combinedBatches[0] != null,""",
        1,
    )
    return fixture


class LayeredMapsSliceOneHundredNinetyFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-authored-state-batch-"
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

    def test_real_boundary_reduces_combined_source_state(self):
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
            "captureWithCombinedSourceStates(",
            "defineLayeredPackedRegionAuthoredCollisionFootprints(",
            "Collections.unmodifiableList(verified)",
            "isCollisionAppliedToSameDisposableRegionUnions()",
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
            "private final LayeredPackedRegionIsolatedAuthoredSourceStateVerification ",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
            "attachOrderedCollisionRegistrationState(",
        ):
            self.assertNotIn(forbidden, source)
        receipt = RECEIPT.read_text(encoding="utf-8")
        self.assertIn(
            "isUsableRegionContainerReturned() { return false; }",
            receipt,
        )

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
            "captureWithCombinedSourceStates(", public_path
        )
        self.assertIn("isCollisionApplied() { return false; }", baseline)

    def test_living_plan_records_slice_one_hundred_ninety_four(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 194: Bounded combined authored source states",
            plan,
        )
        self.assertIn("count/fingerprint-only", plan)


if __name__ == "__main__":
    unittest.main()
