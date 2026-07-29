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
    REGION / "LayeredPackedRegionIsolatedAuthoredSourceStateVerifier.java"
)
VERIFICATION = (
    REGION
    / "LayeredPackedRegionIsolatedAuthoredSourceStateVerification.java"
)
COLLISION_VERIFIER = (
    REGION / "LayeredPackedRegionIsolatedAuthoredCollisionVerifier.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_190 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-ninety.py"
)))


COMBINED_VERIFICATION = r'''
        LayeredPackedRegionIsolatedAuthoredSourceStateVerification
            combinedState =
                LayeredPackedRegionIsolatedAuthoredSourceStateVerifier.verify(
                    manager, containers[0], terrain[0], replay,
                    authoredResult, collision);
        LayeredPackedRegionIsolatedAuthoredSourceStateVerification
            repeatedCombinedState =
                LayeredPackedRegionIsolatedAuthoredSourceStateVerifier.verify(
                    manager, containers[0], terrain[0], replay,
                    authoredResult, collision);
        check(combinedState.getGeneration() == 9L
                && combinedState.getRequirementsObservedAtTick() == 12L
                && combinedState.getObservedAtTick() == 14L
                && combinedState.getResidencyMirrorVersion() >= 1L
                && combinedState.getAuthoredGeneration() == 9L
                && combinedState.getSourceOrdinal() == 0
                && combinedState.getPackedRegionX() == 4
                && combinedState.getPackedRegionY() == 0
                && combinedState.getTerrainTileCount() == 2304
                && combinedState.getReplayPlacementCount() == 5
                && combinedState.getAuthoredObjectCount() == 3
                && combinedState.getDisposableRegionConstructionCount() == 2
                && combinedState.getSupportRegionCount() == 1
                && combinedState.getObjectMembershipApplicationCount() == 3
                && combinedState.getObjectMembershipBoundaryCount() == 3
                && combinedState.getCollisionApplicationCount() == 3
                && combinedState.getCollisionBoundaryCount() == 4
                && combinedState.getVerifiedRegionTileCount() == 4608
                && combinedState.getUniqueContributionTileCount()
                    == appliedCollision.getUniqueContributionTileCount()
                && combinedState.getBlockingSceneryContributionCount()
                    == appliedCollision
                        .getBlockingSceneryContributionCount()
                && combinedState.getDynamicCollisionContributionCount()
                    == appliedCollision
                        .getDynamicCollisionContributionCount()
                && combinedState.getDynamicProjectileContributionCount()
                    == appliedCollision
                        .getDynamicProjectileContributionCount()
                && combinedState.getTerrainFingerprintSha256().equals(
                    terrain[0].getFingerprintSha256())
                && combinedState.getAuthoredReplayFingerprintSha256().equals(
                    replay.getFingerprintSha256())
                && combinedState
                    .getCollisionFootprintFingerprintSha256().equals(
                        collision.getFingerprintSha256())
                && combinedState
                    .getAppliedCollisionFingerprintSha256().equals(
                        appliedCollision
                            .getAppliedCollisionFingerprintSha256())
                && combinedState.getFinalStateFingerprintSha256().length()
                    == 64
                && combinedState.getFinalStateFingerprintSha256().equals(
                    repeatedCombinedState
                        .getFinalStateFingerprintSha256()),
            "combined disposable authored source state lost exact evidence");
        check(combinedState.isVerificationOnly()
                && combinedState.isPointInTimeOnly()
                && combinedState.isDetachedSummaryOnly()
                && combinedState.isBlankUnionMatchedBeforeApply()
                && combinedState
                    .isTerrainAppliedToDisposableSourceRegion()
                && combinedState
                    .isAuthoredObjectMembershipAppliedToDisposableSourceRegion()
                && combinedState
                    .isCollisionAppliedToSameDisposableRegionUnion()
                && combinedState
                    .isTerrainMatchedBeforeAndAfterCollision()
                && combinedState
                    .isObjectMembershipMatchedBeforeAndAfterCollision()
                && combinedState
                    .isObjectCollisionCoexistedInSourceRegion()
                && combinedState
                    .isSupportRegionsRemainedStaticallyBlank()
                && combinedState.isEntityFamiliesMatchedAfterCollision()
                && !combinedState.isCollisionRegistrationAttached()
                && !combinedState.isUsableRegionContainerReturned()
                && !combinedState.isRuntimeHandleRetained()
                && !combinedState.isRuntimeCollisionApplied()
                && !combinedState.isRuntimeSourceMutated()
                && !combinedState.isSourceAbsencePerformed()
                && !combinedState.isSourceReconstructionPerformed()
                && !combinedState.isNpcMembershipApplied()
                && !combinedState.isGroundItemMembershipApplied()
                && !combinedState.isSchedulerStateRestored()
                && !combinedState.isActiveFamilyPreservationPerformed()
                && !combinedState.isRegionRegistryMutated()
                && !combinedState.isResidencyMirrorMutated()
                && !combinedState.isVisibilityCacheMutated()
                && !combinedState.isArrivalGate()
                && !combinedState.isVisibilityReleased()
                && !combinedState.isLifecycleAuthority(),
            "combined disposable authored state crossed runtime authority");

'''


def build_fixture():
    fixture = SLICE_190["build_fixture"]()
    fixture = fixture.replace(
        """        LayeredPackedRegionTerrainInitializationPlan collidingTerrain =
            LayeredPackedRegionTerrainInitializationPlan.define(""",
        COMBINED_VERIFICATION
        + """        LayeredPackedRegionTerrainInitializationPlan collidingTerrain =
            LayeredPackedRegionTerrainInitializationPlan.define(""",
        1,
    )
    return fixture


class LayeredMapsSliceOneHundredNinetyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-combined-authored-state-"
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
            SLICE_190["SLICE_185"]["SLICE_182"]["SLICE_181"]["SLICE_179"]
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

    def test_complete_authored_state_coexists_in_one_disposable_union(self):
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

    def test_verifier_reuses_canonical_membership_and_collision_seams(self):
        source = VERIFIER.read_text(encoding="utf-8")
        for required in (
            "new Region(manager, regionX, regionY)",
            ".applyTerrainTile(",
            ".addMembership(source, object)",
            ".verifyExactMembership(source, replay)",
            ".applyToDisposableRegions(disposable, collision)",
            "verifySupportStatic(",
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
            "attachOrderedCollisionRegistrationState(",
        ):
            self.assertNotIn(forbidden, source)

    def test_existing_collision_verifier_retains_empty_membership_contract(self):
        source = COLLISION_VERIFIER.read_text(encoding="utf-8")
        self.assertIn("applyToDisposableRegions(", source)
        self.assertIn("disposable, collision);", source)
        self.assertIn("verifyEntityMembershipEmpty(disposable)", source)
        helper = source.index("static Application applyToDisposableRegions(")
        self.assertNotIn("public", source[helper - 20:helper])

    def test_detached_receipt_retains_no_runtime_handles(self):
        source = VERIFICATION.read_text(encoding="utf-8")
        for forbidden in (
            "private final Region ",
            "private final RegionManager ",
            "private final TileValue ",
            "private final GameObject ",
            "private final Application ",
            "private final LayeredPackedRegion",
            "private final List<",
            "private final Map<",
            "private final Set<",
        ):
            self.assertNotIn(forbidden, source)
        self.assertIn(
            "isCollisionAppliedToSameDisposableRegionUnion()",
            source,
        )
        self.assertIn("isRuntimeCollisionApplied() { return false; }", source)
        self.assertIn("isRegionRegistryMutated() { return false; }", source)
        self.assertIn("isLifecycleAuthority() { return false; }", source)

    def test_living_plan_records_slice_one_hundred_ninety_three(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 193: Combined disposable authored source state",
            plan,
        )
        self.assertIn("same disposable", plan)


if __name__ == "__main__":
    unittest.main()
