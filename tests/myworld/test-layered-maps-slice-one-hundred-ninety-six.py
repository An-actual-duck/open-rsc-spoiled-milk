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
VERIFIER = (
    REGION
    / "LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerifier.java"
)
VERIFICATION = (
    REGION
    / "LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification.java"
)
BASELINE_VERIFIER = (
    REGION / "LayeredPackedRegionIsolatedAuthoredSourceStateVerifier.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_193 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-ninety-three.py"
)))


TRANSACTIONAL_VERIFICATION = r'''
        LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification
            transactionalState =
                LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerifier
                    .verify(
                        manager, containers[0], terrain[0], replay,
                        authoredResult, collision);
        LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification
            repeatedTransactionalState =
                LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerifier
                    .verify(
                        manager, containers[0], terrain[0], replay,
                        authoredResult, collision);
        check(transactionalState.getGeneration() == 9L
                && transactionalState.getRequirementsObservedAtTick() == 12L
                && transactionalState.getObservedAtTick() == 14L
                && transactionalState.getResidencyMirrorVersion() >= 1L
                && transactionalState.getAuthoredGeneration() == 9L
                && transactionalState.getSourceOrdinal() == 0
                && transactionalState.getPackedRegionX() == 4
                && transactionalState.getPackedRegionY() == 0
                && transactionalState.getTerrainTileCount() == 2304
                && transactionalState.getReplayPlacementCount() == 5
                && transactionalState.getAuthoredObjectCount() == 3
                && transactionalState
                    .getDisposableRegionConstructionCount() == 2
                && transactionalState.getSupportRegionCount() == 1
                && transactionalState
                    .getObjectCollisionTransactionCount() == 3
                && transactionalState
                    .getObjectCollisionTransactionBoundaryCount() == 4
                && transactionalState
                    .getDisposableCacheInvalidationCount() == 3
                && transactionalState.getCollisionRegistrationCount() == 3
                && transactionalState
                    .getCollisionRegistrationContributionCount() == 4
                && transactionalState
                    .getCollisionRegistrationRegionReferenceCount() == 4
                && transactionalState.getVerifiedRegionTileCount() == 4608
                && transactionalState
                    .getBlockingSceneryContributionCount()
                    == appliedCollision
                        .getBlockingSceneryContributionCount()
                && transactionalState
                    .getDynamicCollisionContributionCount()
                    == appliedCollision
                        .getDynamicCollisionContributionCount()
                && transactionalState
                    .getDynamicProjectileContributionCount()
                    == appliedCollision
                        .getDynamicProjectileContributionCount()
                && transactionalState.getTerrainFingerprintSha256().equals(
                    combinedState.getTerrainFingerprintSha256())
                && transactionalState
                    .getAuthoredReplayFingerprintSha256().equals(
                        combinedState
                            .getAuthoredReplayFingerprintSha256())
                && transactionalState
                    .getCollisionFootprintFingerprintSha256().equals(
                        combinedState
                            .getCollisionFootprintFingerprintSha256())
                && transactionalState
                    .getAppliedCollisionFingerprintSha256().equals(
                        combinedState
                            .getAppliedCollisionFingerprintSha256())
                && transactionalState.getFinalStateFingerprintSha256().equals(
                    combinedState.getFinalStateFingerprintSha256())
                && transactionalState
                    .getCollisionRegistrationFingerprintSha256().length()
                    == 64
                && transactionalState
                    .getCollisionRegistrationFingerprintSha256().equals(
                        repeatedTransactionalState
                            .getCollisionRegistrationFingerprintSha256()),
            "transactional disposable source lost exact state/provenance");
        check(transactionalState.isVerificationOnly()
                && transactionalState.isPointInTimeOnly()
                && transactionalState.isDetachedSummaryOnly()
                && transactionalState.isBlankUnionMatchedBeforeApply()
                && transactionalState
                    .isTerrainAppliedToDisposableSourceRegion()
                && transactionalState
                    .isObjectCollisionTransactionAppliedToDisposableRegions()
                && transactionalState
                    .isCollisionRegistrationAttachedToDisposableObjects()
                && transactionalState.isDisposableCacheInvalidationOnly()
                && transactionalState
                    .isTerrainMatchedBeforeAndAfterTransactions()
                && transactionalState
                    .isObjectMembershipMatchedAfterTransactions()
                && transactionalState
                    .isSupportRegionsRemainedStaticallyBlank()
                && transactionalState
                    .isEntityFamiliesMatchedAfterTransactions()
                && transactionalState.isAllCollisionRegistrationsMatched()
                && !transactionalState.isUsableRegionContainerReturned()
                && !transactionalState.isRuntimeHandleRetained()
                && !transactionalState.isRuntimeCollisionApplied()
                && !transactionalState.isRuntimeSourceMutated()
                && !transactionalState.isSourceAbsencePerformed()
                && !transactionalState.isSourceReconstructionPerformed()
                && !transactionalState.isNpcMembershipApplied()
                && !transactionalState.isGroundItemMembershipApplied()
                && !transactionalState.isSchedulerStateRestored()
                && !transactionalState
                    .isActiveFamilyPreservationPerformed()
                && !transactionalState.isRuntimeCacheInvalidated()
                && !transactionalState.isRegionRegistryMutated()
                && !transactionalState.isResidencyMirrorMutated()
                && !transactionalState.isVisibilityCacheMutated()
                && !transactionalState.isArrivalGate()
                && !transactionalState.isVisibilityReleased()
                && !transactionalState.isLifecycleAuthority(),
            "transactional disposable source crossed runtime authority");

'''


def build_fixture():
    fixture = SLICE_193["build_fixture"]()
    fixture = fixture.replace(
        """        LayeredPackedRegionTerrainInitializationPlan collidingTerrain =
            LayeredPackedRegionTerrainInitializationPlan.define(""",
        TRANSACTIONAL_VERIFICATION
        + """        LayeredPackedRegionTerrainInitializationPlan collidingTerrain =
            LayeredPackedRegionTerrainInitializationPlan.define(""",
        1,
    )
    return fixture


class LayeredMapsSliceOneHundredNinetySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-transactional-authored-state-"
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
            SLICE_193["SLICE_190"]["SLICE_185"]["SLICE_182"]
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

    def test_transactional_source_matches_combined_disposable_state(self):
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

    def test_verifier_uses_atomic_transaction_and_exact_provenance(self):
        source = VERIFIER.read_text(encoding="utf-8")
        for required in (
            "RegionObjectCollisionTransactionExecutor.execute(",
            "footprint.recreateVerifiedPlannerResult(",
            "object.getCollisionRegistrationState()",
            "registrationMatches(",
            "verifyAppliedState(disposable, collision)",
            "fingerprintRegistrations(constructed)",
            "fingerprintFinalState(",
        ):
            self.assertIn(required, source)
        for forbidden in (
            "getRegionFromSectorCoordinates(",
            "peekRegionFromSectorCoordinates(",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
            "registerGameObject(",
            "unregisterGameObject(",
        ):
            self.assertNotIn(forbidden, source)

    def test_accepted_combined_verifier_remains_registration_free(self):
        source = BASELINE_VERIFIER.read_text(encoding="utf-8")
        self.assertIn(
            ".addMembership(source, object)", source
        )
        self.assertIn(
            ".applyToDisposableRegions(disposable, collision)", source
        )
        self.assertNotIn(
            "RegionObjectCollisionTransactionExecutor.execute(", source
        )
        self.assertNotIn(
            "attachOrderedCollisionRegistrationState(", source
        )

    def test_detached_receipt_retains_no_runtime_handles(self):
        source = VERIFICATION.read_text(encoding="utf-8")
        for forbidden in (
            "private final Region ",
            "private final RegionManager ",
            "private final TileValue ",
            "private final GameObject ",
            "private final GameObjectCollisionRegistrationState ",
            "private final PostStateVerification ",
            "private final LayeredPackedRegion",
            "private final List<",
            "private final Map<",
            "private final Set<",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isCollisionRegistrationAttachedToDisposableObjects()",
            "isDisposableCacheInvalidationOnly()",
            "isRuntimeCacheInvalidated() { return false; }",
            "isRegionRegistryMutated() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_living_plan_records_slice_one_hundred_ninety_six(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 196: Disposable transactional authored source",
            plan,
        )
        self.assertIn("collision-registration", plan)


if __name__ == "__main__":
    unittest.main()
