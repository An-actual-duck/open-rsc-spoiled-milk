#!/usr/bin/env python3
import os
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_SOURCE = ROOT / "server/src"
COLLISION_PLAN = SERVER_SOURCE / (
    "com/openrsc/server/model/world/region/"
    "LayeredPackedRegionAuthoredCollisionFootprintPlan.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_185 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-eighty-five.py"
)))


COLLISION_ASSERTIONS = r'''

        java.util.List<
            LayeredPackedRegionAuthoredCollisionFootprintPlan
                .AuthoredObjectCollisionDefinition> collisionDefinitions =
                    java.util.Arrays.asList(
                        LayeredPackedRegionAuthoredCollisionFootprintPlan
                            .AuthoredObjectCollisionDefinition.scenery(
                                1, 3, 1, 2, 1, "tree"),
                        LayeredPackedRegionAuthoredCollisionFootprintPlan
                            .AuthoredObjectCollisionDefinition.boundary(
                                2, 4, 1, "gate"),
                        LayeredPackedRegionAuthoredCollisionFootprintPlan
                            .AuthoredObjectCollisionDefinition
                                .specialCollisionlessScenery(5));
        LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan =
            LayeredPackedRegionAuthoredCollisionFootprintPlan.define(
                replay, authoredResult, collisionDefinitions,
                new String[]{"gate"}, 1008, 4032);
        check(collisionPlan.getGeneration() == 9L
                && collisionPlan.getRequirementsObservedAtTick() == 12L
                && collisionPlan.getObservedAtTick() == 14L
                && collisionPlan.getResidencyMirrorVersion() >= 1L
                && collisionPlan.getAuthoredGeneration() == 9L
                && collisionPlan.getSourceOrdinal() == 0
                && collisionPlan.getPackedRegionX() == 4
                && collisionPlan.getPackedRegionY() == 0
                && collisionPlan.getAuthoredReplayFingerprintSha256().equals(
                    replay.getFingerprintSha256())
                && collisionPlan.getObjectFootprintCount() == 3
                && collisionPlan.getDefinitionBackedObjectCount() == 2
                && collisionPlan.getSpecialCollisionlessObjectCount() == 1
                && collisionPlan.getZeroContributionObjectCount() == 1
                && collisionPlan.getCrossSourceCollisionObjectCount() == 1
                && collisionPlan
                    .getCollisionBeyondAuthoredDependencyObjectCount() == 1
                && collisionPlan.getContributionTileReferenceCount() == 4
                && collisionPlan.getRequiredRegionReferenceCount() == 4
                && collisionPlan.getUniqueRequiredRegionCount() == 2
                && collisionPlan.getRequiredRegions().get(0)
                    .getPackedRegionX() == 4
                && collisionPlan.getRequiredRegions().get(1)
                    .getPackedRegionX() == 5
                && collisionPlan.getFingerprintSha256().length() == 64,
            "authored collision footprint aggregates are inconsistent");

        LayeredPackedRegionAuthoredCollisionFootprintPlan
            .AuthoredObjectCollisionFootprint scenery =
                collisionPlan.getFootprints().get(0);
        check(scenery.getAuthoredSourceOrdinal() == 1
                && scenery.getConstructionKind()
                    == ConstructionKind.SCENERY
                && scenery.getObjectId() == 3
                && scenery.isDefinitionAvailable()
                && scenery.getCollisionType() == 1
                && scenery.getDefinitionWidth() == 2
                && scenery.getDefinitionHeight() == 1
                && "tree".equals(scenery.getDefinitionName())
                && scenery.isProjectileClipAllowed()
                && scenery.getContributionTileCount() == 2
                && scenery.getContributions().get(0).getPackedX() == 239
                && scenery.getContributions().get(0)
                    .getBlockingSceneryCount() == 1
                && scenery.getContributions().get(0)
                    .getDynamicProjectileCount() == 1
                && scenery.getRequiredRegionCount() == 2
                && scenery.isCrossSourceCollision()
                && !scenery.isCollisionBeyondAuthoredDependency(),
            "cross-source scenery collision was not copied exactly");

        LayeredPackedRegionAuthoredCollisionFootprintPlan
            .AuthoredObjectCollisionFootprint boundary =
                collisionPlan.getFootprints().get(1);
        check(boundary.getAuthoredSourceOrdinal() == 2
                && boundary.getConstructionKind()
                    == ConstructionKind.BOUNDARY
                && boundary.getContributionTileCount() == 2
                && boundary.getContributions().get(0).getPackedX() == 200
                && boundary.getContributions().get(0)
                    .getDynamicCollisionCount(3) == 1
                && boundary.getContributions().get(1).getPackedX() == 201
                && boundary.getContributions().get(1)
                    .getDynamicCollisionCount(1) == 1
                && boundary.getContributions().get(1)
                    .getDynamicProjectileCount() == 1
                && !boundary.isCrossSourceCollision()
                && boundary.isCollisionBeyondAuthoredDependency(),
            "directional boundary collision reach was not kept separate");

        LayeredPackedRegionAuthoredCollisionFootprintPlan
            .AuthoredObjectCollisionFootprint special =
                collisionPlan.getFootprints().get(2);
        check(special.getAuthoredSourceOrdinal() == 5
                && special.getConstructionKind()
                    == ConstructionKind.HARVESTING_SCENERY
                && special.getObjectId() == 1147
                && !special.isDefinitionAvailable()
                && special.isZeroContributionFootprint()
                && special.getRequiredRegionCount() == 1
                && !special.isCrossSourceCollision()
                && !special.isCollisionBeyondAuthoredDependency(),
            "special collisionless register semantics were not preserved");

        expectUnsupported(() -> collisionPlan.getFootprints().clear());
        expectUnsupported(() -> collisionPlan.getRequiredRegions().clear());
        expectUnsupported(() -> scenery.getContributions().clear());
        int[] copiedCounts =
            boundary.getContributions().get(0).getDynamicCollisionCounts();
        copiedCounts[3] = 99;
        check(boundary.getContributions().get(0)
                .getDynamicCollisionCount(3) == 1,
            "collision contribution counts leaked mutable storage");
        check(collisionPlan.getFingerprintSha256().equals(
                LayeredPackedRegionAuthoredCollisionFootprintPlan.define(
                    replay, authoredResult, collisionDefinitions,
                    new String[]{"gate"}, 1008, 4032)
                        .getFingerprintSha256()),
            "authored collision fingerprint is not deterministic");

        java.util.List<
            LayeredPackedRegionAuthoredCollisionFootprintPlan
                .AuthoredObjectCollisionDefinition> mismatched =
                    new java.util.ArrayList<
                        LayeredPackedRegionAuthoredCollisionFootprintPlan
                            .AuthoredObjectCollisionDefinition>(
                                collisionDefinitions);
        mismatched.set(
            0,
            LayeredPackedRegionAuthoredCollisionFootprintPlan
                .AuthoredObjectCollisionDefinition.scenery(
                    2, 3, 1, 2, 1, "tree"));
        expectIllegalArgument(() ->
            LayeredPackedRegionAuthoredCollisionFootprintPlan.define(
                replay, authoredResult, mismatched,
                new String[]{"gate"}, 1008, 4032));
        expectIllegalArgument(() ->
            LayeredPackedRegionAuthoredCollisionFootprintPlan.define(
                replay, authoredResult,
                collisionDefinitions.subList(0, 2),
                new String[]{"gate"}, 1008, 4032));
        expectIllegalArgument(() ->
            LayeredPackedRegionAuthoredCollisionFootprintPlan.define(
                replay, authoredResult, collisionDefinitions,
                new String[]{"gate"}, 240, 4032));
        check(collisionPlan.isPointInTimeOnly()
                && collisionPlan.isDetachedCollisionDefinition()
                && collisionPlan.isIsolatedMembershipVerificationMatched()
                && collisionPlan.isDefinitionIdentityMatched()
                && collisionPlan.isRegisterFootprintDerived()
                && !collisionPlan.isForceFullBlockEnabled()
                && !collisionPlan.isRuntimeDefinitionCapturePerformed()
                && !collisionPlan.isRegionBoundaryAcquired()
                && !collisionPlan.isCollisionApplied()
                && !collisionPlan.isCollisionRegistrationAttached()
                && !collisionPlan.isRuntimeSourceMutated()
                && !collisionPlan.isRuntimeHandleRetained()
                && !collisionPlan.isRegionRegistryMutated()
                && !collisionPlan.isResidencyMirrorMutated()
                && !collisionPlan.isVisibilityCacheMutated()
                && !collisionPlan.isArrivalGate()
                && !collisionPlan.isVisibilityReleased()
                && !collisionPlan.isLifecycleAuthority(),
            "authored collision plan crossed runtime authority");
'''


ILLEGAL_ARGUMENT_HELPER = r'''
    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

'''


def build_fixture():
    fixture = SLICE_185["build_fixture"]()
    fixture = fixture.replace(
        "7, 8, 8, 204, 14, 0, 0, \"harvest-owner\",",
        "7, 1147, 1147, 204, 14, 0, 0, \"harvest-owner\",",
    )
    marker = (
        '            "isolated authored membership crossed runtime authority");'
    )
    fixture = fixture.replace(marker, marker + COLLISION_ASSERTIONS, 1)
    fixture = fixture.replace(
        "    private static void expectUnsupported",
        ILLEGAL_ARGUMENT_HELPER + "    private static void expectUnsupported",
        1,
    )
    return fixture


class LayeredMapsSliceOneHundredEightySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-authored-collision-"
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

    def test_exact_collision_reach_is_derived_without_application(self):
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

    def test_collision_plan_has_no_runtime_capture_or_mutation_path(self):
        source = COLLISION_PLAN.read_text(encoding="utf-8")
        for required in (
            "GameTickEventRestorationCollisionFootprintPlanner.plan(",
            "Operation.REGISTER",
            "input.requireMatches(placement);",
            "collisionBeyondAuthoredDependency",
            "false, null);",
            "isRuntimeDefinitionCapturePerformed() {",
            "isCollisionApplied() { return false; }",
            "isCollisionRegistrationAttached() { return false; }",
        ):
            self.assertIn(required, source)
        for forbidden in (
            "import com.openrsc.server.external",
            "import com.openrsc.server.model.entity",
            "new Region(",
            "RegionManager ",
            "GameObject ",
            "TileValue ",
            "getGameObjectDef(",
            "getDoorDef(",
            "getRegion(",
            "registerPackedRegion(",
            "applyCollisionFootprint",
            "attachOrderedCollisionRegistrationState(",
            "addDynamicCollision(",
            "addBlockingScenery(",
            "addDynamicProjectileBlock(",
            "layeredRegionResidencyMirror",
            "visibleRegionWindowCache",
            "layeredRegionLifecycleLock",
        ):
            self.assertNotIn(forbidden, source)

    def test_collision_plan_retains_only_detached_definition_and_counts(self):
        source = COLLISION_PLAN.read_text(encoding="utf-8")
        for forbidden in (
            "private final Result ",
            "private final Definition ",
            "private final CollisionContribution ",
            "private final PackedRegionCoordinate ",
            "private final LayeredPackedRegionAuthoredReplayPlan ",
            "private final LayeredPackedRegionIsolatedAuthoredObjectVerification ",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "Collections.unmodifiableList(planned)",
            "this.contributions = Collections.unmodifiableList(",
            "copiedContributions);",
            "dynamicCollisionCounts.clone()",
            "isRuntimeHandleRetained() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_living_plan_records_slice_one_hundred_eighty_six(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 186: Detached authored collision footprints", plan
        )
        self.assertIn("collision reach", plan)
        self.assertIn("authored dependency envelope", plan)


if __name__ == "__main__":
    unittest.main()
