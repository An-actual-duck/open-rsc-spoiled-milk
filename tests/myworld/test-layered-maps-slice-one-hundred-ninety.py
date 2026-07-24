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
FOOTPRINT = (
    REGION / "LayeredPackedRegionAuthoredCollisionFootprintPlan.java"
)
VERIFIER = (
    REGION / "LayeredPackedRegionIsolatedAuthoredCollisionVerifier.java"
)
VERIFICATION = (
    REGION
    / "LayeredPackedRegionIsolatedAuthoredCollisionVerification.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_185 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-eighty-five.py"
)))


COLLISION_VERIFICATION = r'''
        LayeredPackedRegionAuthoredCollisionDefinitionCapture
            collisionDefinitions =
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
                                        objectId == 3 ? 1 : 0,
                                        objectId == 3 ? 2 : 1,
                                        1,
                                        objectId == 3 ? "tree" : "harvest");
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
                    });
        LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
            LayeredPackedRegionAuthoredCollisionFootprintPlan.define(
                replay, authoredResult, collisionDefinitions,
                new String[]{"gate"}, 1008, 4032);
        LayeredPackedRegionIsolatedAuthoredCollisionVerification
            appliedCollision =
                LayeredPackedRegionIsolatedAuthoredCollisionVerifier.verify(
                    manager, collision);

        java.util.Set<Long> uniqueContributionTiles =
            new java.util.HashSet<Long>();
        long expectedBlocking = 0L;
        long expectedDynamic = 0L;
        long expectedProjectile = 0L;
        for (LayeredPackedRegionAuthoredCollisionFootprintPlan
                .AuthoredObjectCollisionFootprint footprint
                    : collision.getFootprints()) {
            for (LayeredPackedRegionAuthoredCollisionFootprintPlan
                    .Contribution contribution
                        : footprint.getContributions()) {
                uniqueContributionTiles.add(
                    ((long) contribution.getPackedX() << 32)
                        ^ (contribution.getPackedY() & 0xffffffffL));
                expectedBlocking +=
                    contribution.getBlockingSceneryCount();
                for (int bit = 0; bit < 6; bit++) {
                    expectedDynamic +=
                        contribution.getDynamicCollisionCount(bit);
                }
                expectedProjectile +=
                    contribution.getDynamicProjectileCount();
            }
        }
        check(appliedCollision.getGeneration() == 9L
                && appliedCollision.getRequirementsObservedAtTick() == 12L
                && appliedCollision.getObservedAtTick() == 14L
                && appliedCollision.getResidencyMirrorVersion() >= 1L
                && appliedCollision.getAuthoredGeneration() == 9L
                && appliedCollision.getSourceOrdinal() == 0
                && appliedCollision.getPackedRegionX() == 4
                && appliedCollision.getPackedRegionY() == 0
                && appliedCollision.getAuthoredObjectFootprintCount() == 3
                && appliedCollision.getZeroContributionObjectCount() == 1
                && appliedCollision.getContributionTileReferenceCount() == 4
                && appliedCollision.getUniqueContributionTileCount()
                    == uniqueContributionTiles.size()
                && appliedCollision.getRequiredRegionReferenceCount() == 4
                && appliedCollision.getUniqueRequiredRegionCount() == 2
                && appliedCollision.getDisposableRegionConstructionCount()
                    == 2
                && appliedCollision.getCollisionApplicationCount() == 3
                && appliedCollision.getHeldBoundaryCount() == 4
                && appliedCollision.getVerifiedRegionTileCount() == 4608
                && appliedCollision.getBlockingSceneryContributionCount()
                    == expectedBlocking
                && appliedCollision.getDynamicCollisionContributionCount()
                    == expectedDynamic
                && appliedCollision.getDynamicProjectileContributionCount()
                    == expectedProjectile
                && appliedCollision
                    .getCollisionFootprintFingerprintSha256().equals(
                        collision.getFingerprintSha256())
                && appliedCollision
                    .getAppliedCollisionFingerprintSha256().length() == 64,
            "disposable collision receipt lost exact applied state");
        check(appliedCollision.isVerificationOnly()
                && appliedCollision.isPointInTimeOnly()
                && appliedCollision.isDetachedSummaryOnly()
                && appliedCollision
                    .isDisposableRegionConstructionPerformed()
                && appliedCollision
                    .isBlankDynamicProductsMatchedBeforeApply()
                && appliedCollision
                    .isAllPlannerResultsRecreatedExactly()
                && appliedCollision
                    .isCollisionAppliedToDisposableRegions()
                && appliedCollision.isAllCollisionApplicationsSucceeded()
                && appliedCollision.isAllAppliedTilesMatched()
                && appliedCollision.isAllEntityMembershipRemainedEmpty()
                && !appliedCollision.isAuthoredObjectMembershipApplied()
                && !appliedCollision.isCollisionRegistrationAttached()
                && !appliedCollision.isUsableRegionContainerReturned()
                && !appliedCollision.isRuntimeHandleRetained()
                && !appliedCollision.isRuntimeCollisionApplied()
                && !appliedCollision.isRuntimeSourceMutated()
                && !appliedCollision.isSourceAbsencePerformed()
                && !appliedCollision.isSourceReconstructionPerformed()
                && !appliedCollision.isNpcMembershipApplied()
                && !appliedCollision.isGroundItemMembershipApplied()
                && !appliedCollision.isSchedulerStateRestored()
                && !appliedCollision.isRegionRegistryMutated()
                && !appliedCollision.isResidencyMirrorMutated()
                && !appliedCollision.isVisibilityCacheMutated()
                && !appliedCollision.isArrivalGate()
                && !appliedCollision.isVisibilityReleased()
                && !appliedCollision.isLifecycleAuthority(),
            "disposable collision verification crossed runtime authority");

'''


def build_fixture():
    fixture = SLICE_185["build_fixture"]()
    fixture = fixture.replace(
        """        LayeredPackedRegionTerrainInitializationPlan collidingTerrain =
            LayeredPackedRegionTerrainInitializationPlan.define(""",
        COLLISION_VERIFICATION
        + """        LayeredPackedRegionTerrainInitializationPlan collidingTerrain =
            LayeredPackedRegionTerrainInitializationPlan.define(""",
        1,
    )
    return fixture


class LayeredMapsSliceOneHundredNinetyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-isolated-authored-collision-"
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

    def test_collision_applies_only_to_disposable_region_union(self):
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

    def test_verifier_uses_canonical_executor_without_runtime_lookup(self):
        source = VERIFIER.read_text(encoding="utf-8")
        for required in (
            "new Region(manager, regionX, regionY)",
            "recreateVerifiedPlannerResult(",
            "RegionCollisionFootprintMutationExecutor.execute(",
            "getObjectCollisionMutationBoundary()",
            "verifyBlankDynamicProducts(",
            "verifyEntityMembershipEmpty(",
            "isCollisionAppliedToDisposableRegions()",
        ):
            target = source if required != (
                "isCollisionAppliedToDisposableRegions()"
            ) else VERIFICATION.read_text(encoding="utf-8")
            self.assertIn(required, target)
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

    def test_detached_receipt_retains_no_runtime_handles(self):
        source = VERIFICATION.read_text(encoding="utf-8")
        for forbidden in (
            "private final Region ",
            "private final RegionManager ",
            "private final TileValue ",
            "private final GameObject ",
            "private final Result ",
            "private final LayeredPackedRegionAuthoredCollisionFootprintPlan ",
            "private final List<",
            "private final Map<",
        ):
            self.assertNotIn(forbidden, source)
        self.assertIn("isRuntimeCollisionApplied() { return false; }", source)
        self.assertIn("isRegionRegistryMutated() { return false; }", source)
        self.assertIn("isLifecycleAuthority() { return false; }", source)

    def test_planner_result_recreation_is_package_local_and_exact(self):
        source = FOOTPRINT.read_text(encoding="utf-8")
        method = source.index("Result recreateVerifiedPlannerResult(")
        self.assertNotIn("public", source[method - 20:method])
        body = source[method:source.index(
            "private void updateDigest", method
        )]
        self.assertIn(
            "GameTickEventRestorationCollisionFootprintPlanner.plan(", body
        )
        self.assertIn("Operation.REGISTER, worldWidth, worldHeight", body)
        self.assertIn("if (!matches(recreated, operation))", body)
        self.assertNotIn("new Region(", body)
        self.assertNotIn("RegionManager", body)
        self.assertNotIn("TileValue", body)

    def test_living_plan_records_slice_one_hundred_ninety(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 190: Disposable authored-collision application",
            plan,
        )
        self.assertIn("unregistered Region union", plan)


if __name__ == "__main__":
    unittest.main()
