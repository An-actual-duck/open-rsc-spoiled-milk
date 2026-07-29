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
    REGION / "LayeredPackedRegionIsolatedAuthoredObjectVerifier.java"
)
VERIFICATION = (
    REGION / "LayeredPackedRegionIsolatedAuthoredObjectVerification.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_182 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-eighty-two.py"
)))


AUTHORED_RECIPE = r'''    private static LayeredPackedRegionAuthoredReconstructionRecipe
        authoredRecipe() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifest.recordScenery(
            4, 0, 3, 3, 239, 10, 0, 0, null);
        manifest.recordBoundary(
            4, 0, 4, 4, 201, 11, 1, 1, "gate-owner");
        manifest.recordNpcSpawn(
            4, 0, 5, 202, 12, 190, 250, 10, 20);
        manifest.recordGroundItemSpawn(
            4, 0, 6, 203, 13, 2, 100, 0);
        manifest.recordHarvestingScenery(
            4, 0, 7, 8, 8, 204, 14, 0, 0, "harvest-owner",
            1, 50, 0);
        LayeredPackedRegionAuthoredPlacementManifest definitions =
            manifest.build();
        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencies =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(9L);
        dependencies.record(
            ConstructionKind.SCENERY, DependencyKind.OBJECT_FOOTPRINT,
            4, 0, 239, 240, 10, 10, 4, 5, 0, 0);
        dependencies.record(
            ConstructionKind.BOUNDARY, DependencyKind.OBJECT_FOOTPRINT,
            4, 0, 201, 201, 11, 11, 4, 4, 0, 0);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 190, 250, 10, 20, 3, 5, 0, 0);
        dependencies.record(
            ConstructionKind.GROUND_ITEM_SPAWN, DependencyKind.ANCHOR_ONLY,
            4, 0, 203, 203, 13, 13, 4, 4, 0, 0);
        dependencies.record(
            ConstructionKind.HARVESTING_SCENERY,
            DependencyKind.OBJECT_FOOTPRINT,
            4, 0, 204, 204, 14, 14, 4, 4, 0, 0);
        return LayeredPackedRegionAuthoredReconstructionRecipe.derive(
            definitions, dependencies.build(),
            LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                .build(definitions));
    }

    private static LayeredPackedRegionAuthoredReconstructionRecipe
        collidingAuthoredRecipe() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifest.recordScenery(
            4, 0, 30, 30, 205, 15, 0, 0, null);
        manifest.recordScenery(
            4, 0, 31, 31, 205, 15, 4, 0, null);
        LayeredPackedRegionAuthoredPlacementManifest definitions =
            manifest.build();
        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencies =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(9L);
        dependencies.record(
            ConstructionKind.SCENERY, DependencyKind.OBJECT_FOOTPRINT,
            4, 0, 205, 205, 15, 15, 4, 4, 0, 0);
        dependencies.record(
            ConstructionKind.SCENERY, DependencyKind.OBJECT_FOOTPRINT,
            4, 0, 205, 205, 15, 15, 4, 4, 0, 0);
        return LayeredPackedRegionAuthoredReconstructionRecipe.derive(
            definitions, dependencies.build(),
            LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                .build(definitions));
    }

'''


def build_fixture():
    fixture = SLICE_182["FIXTURE"]
    fixture = fixture.replace(
        """        final LayeredPackedRegionBlankContainerPlan[] containers =
            new LayeredPackedRegionBlankContainerPlan[1];""",
        """        final LayeredPackedRegionBlankContainerPlan[] containers =
            new LayeredPackedRegionBlankContainerPlan[1];
        final LayeredPackedRegionReloadRecipe[] reloads =
            new LayeredPackedRegionReloadRecipe[1];
        final LayeredPackedRegionBlankContainerPlan[] collidingContainers =
            new LayeredPackedRegionBlankContainerPlan[1];
        final LayeredPackedRegionReloadRecipe[] collidingReloads =
            new LayeredPackedRegionReloadRecipe[1];""",
    )
    fixture = fixture.replace(
        """.SourceInventory.of(
                                        4, 0, true, 0, 0, 1, 1, 0, 4)),""",
        """.SourceInventory.of(
                                        4, 0, true, 0, 1, 3, 0, 1, 4)),""",
    )
    fixture = fixture.replace(
        """                    LayeredPackedRegionBlankContainerPlan container =
                        LayeredPackedRegionBlankContainerPlan.define(
                            reload, 0);""",
        """                    reloads[0] = reload;
                    LayeredPackedRegionSourceAbsencePreflight
                        collidingPreflight =
                            LayeredPackedRegionSourceAbsencePreflight.assess(
                                boundary,
                                Collections.singletonList(
                                    LayeredPackedRegionSourceAbsencePreflight
                                        .SourceInventory.of(
                                            4, 0, true, 0, 0, 2, 0, 0, 0)),
                                14L, false, true);
                    collidingReloads[0] =
                        LayeredPackedRegionReloadRecipe.compose(
                            boundary, collidingPreflight,
                            collidingAuthoredRecipe(), true);
                    collidingContainers[0] =
                        LayeredPackedRegionBlankContainerPlan.define(
                            collidingReloads[0], 0);
                    LayeredPackedRegionBlankContainerPlan container =
                        LayeredPackedRegionBlankContainerPlan.define(
                            reload, 0);""",
    )
    fixture = fixture.replace(
        '''            "isolated terrain verification crossed runtime authority");''',
        '''            "isolated terrain verification crossed runtime authority");

        LayeredPackedRegionAuthoredReplayPlan replay =
            LayeredPackedRegionAuthoredReplayPlan.define(
                reloads[0], 0, verification);
        LayeredPackedRegionIsolatedAuthoredObjectVerification
            authoredResult =
            LayeredPackedRegionIsolatedAuthoredObjectVerifier.verify(
                manager, containers[0], terrain[0], replay);
        check(authoredResult.getGeneration() == 9L
                && authoredResult.getRequirementsObservedAtTick() == 12L
                && authoredResult.getObservedAtTick() == 14L
                && authoredResult.getResidencyMirrorVersion() >= 1L
                && authoredResult.getAuthoredGeneration() == 9L
                && authoredResult.getSourceOrdinal() == 0
                && authoredResult.getPackedRegionX() == 4
                && authoredResult.getPackedRegionY() == 0
                && authoredResult.getTerrainTileCount() == 2304
                && authoredResult.getTerrainFingerprintSha256().length() == 64
                && authoredResult.getReplayPlacementCount() == 5
                && authoredResult.getSceneryPlacementCount() == 1
                && authoredResult.getBoundaryPlacementCount() == 1
                && authoredResult.getHarvestingSceneryPlacementCount() == 1
                && authoredResult.getSkippedNpcSpawnPlacementCount() == 1
                && authoredResult.getSkippedGroundItemSpawnPlacementCount()
                    == 1
                && authoredResult.getConstructedObjectCount() == 3
                && authoredResult.getHeldBoundaryCount() == 3
                && authoredResult.getAuthoredReplayFingerprintSha256().equals(
                    replay.getFingerprintSha256()),
            "isolated authored membership lost exact replay identity");
        check(authoredResult.isVerificationOnly()
                && authoredResult.isDisposableRegionConstructed()
                && authoredResult.isBlankContractMatchedBeforeTerrain()
                && authoredResult.isTerrainAppliedBeforeObjectMembership()
                && authoredResult.isTerrainMatchedAfterObjectMembership()
                && authoredResult.isAuthoredSceneryMembershipApplied()
                && authoredResult.isExactObjectMembershipMatchedAfterReplay()
                && !authoredResult.isNpcMembershipApplied()
                && !authoredResult.isGroundItemMembershipApplied()
                && !authoredResult.isCollisionDerived()
                && !authoredResult.isCollisionRegistrationAttached()
                && !authoredResult.isDynamicCollisionStateChanged()
                && !authoredResult.isSchedulerStateRestored()
                && !authoredResult.isExecutableReload()
                && !authoredResult.isUsableRegionContainerReturned()
                && !authoredResult.isRuntimeHandleRetained()
                && !authoredResult.isRuntimeSourceMutated()
                && !authoredResult.isSourceAbsencePerformed()
                && !authoredResult.isSourceReconstructionPerformed()
                && !authoredResult.isRegionRegistryMutated()
                && !authoredResult.isResidencyMirrorMutated()
                && !authoredResult.isVisibilityCacheMutated()
                && !authoredResult.isArrivalGate()
                && !authoredResult.isVisibilityReleased()
                && !authoredResult.isLifecycleAuthority(),
            "isolated authored membership crossed runtime authority");

        LayeredPackedRegionTerrainInitializationPlan collidingTerrain =
            LayeredPackedRegionTerrainInitializationPlan.define(
                collidingContainers[0], terrain[0].getTiles());
        LayeredPackedRegionIsolatedTerrainVerification
            collidingTerrainVerification =
                LayeredPackedRegionIsolatedTerrainVerifier.verify(
                    manager, collidingContainers[0], collidingTerrain);
        LayeredPackedRegionAuthoredReplayPlan collidingReplay =
            LayeredPackedRegionAuthoredReplayPlan.define(
                collidingReloads[0], 0, collidingTerrainVerification);
        expectIllegalState(() ->
            LayeredPackedRegionIsolatedAuthoredObjectVerifier.verify(
                manager, collidingContainers[0], collidingTerrain,
                collidingReplay));''',
    )
    method_start = fixture.index(
        "    private static LayeredPackedRegionAuthoredReconstructionRecipe"
    )
    method_end = fixture.index(
        "    private static void expectUnsupported", method_start
    )
    return fixture[:method_start] + AUTHORED_RECIPE + fixture[method_end:]


class LayeredMapsSliceOneHundredEightyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-isolated-authored-objects-"
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
            SLICE_182["SLICE_181"]["SLICE_179"]
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

    def test_only_authored_object_families_populate_disposable_region(self):
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

    def test_isolated_object_apply_uses_ordered_membership_without_collision(self):
        source = VERIFIER.read_text(encoding="utf-8")
        for required in (
            "Region isolated = new Region(",
            "requireExactTerrain(isolated, terrain);",
            "new GameObject(null, location)",
            "RegionObjectCollisionMutationBoundary.executeMutation(",
            "isolated.addGameObjectUnderTransaction(",
            "object.getCollisionRegistrationState() != null",
            "contents.getNpcCount() == 0",
            "contents.getGroundItemCount() == 0",
        ):
            self.assertIn(required, source)
        for forbidden in (
            "checkedManager.getRegion(",
            "getRegionFromSectorCoordinates(",
            "peekRegionFromSectorCoordinates(",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
            "attachOrderedCollisionRegistrationState(",
            "addDynamicCollision(",
            "addBlockingScenery(",
            "addDynamicProjectileBlock(",
            "layeredRegionResidencyMirror",
            "visibleRegionWindowCache",
            "visibleObjectWindowCache",
            "visibleObjectSnapshotCache",
            "layeredRegionLifecycleLock",
        ):
            self.assertNotIn(forbidden, source)

    def test_receipt_retains_no_entity_or_runtime_handle(self):
        source = VERIFICATION.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model.entity",
            "private final Region ",
            "private final RegionManager ",
            "private final GameObject ",
            "private final TileValue ",
            "private final List",
            "getRegion()",
            "getRegionManager()",
            "getGameObject()",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isAuthoredSceneryMembershipApplied() { return true; }",
            "isNpcMembershipApplied() { return false; }",
            "isGroundItemMembershipApplied() { return false; }",
            "isCollisionDerived() { return false; }",
            "isCollisionRegistrationAttached() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_living_plan_records_slice_one_hundred_eighty_five(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 185: Isolated authored-object membership", plan
        )
        self.assertIn("collision registration remains absent", plan)


if __name__ == "__main__":
    unittest.main()
