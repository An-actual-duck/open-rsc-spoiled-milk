#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION = ROOT / "server/src/com/openrsc/server/model/world/region"
BOUNDARY = REGION / "LayeredPackedRegionSourceLifecycleBoundary.java"
PREFLIGHT = REGION / "LayeredPackedRegionSourceAbsencePreflight.java"
RELOAD_RECIPE = REGION / "LayeredPackedRegionReloadRecipe.java"
CONTAINER_PLAN = REGION / "LayeredPackedRegionBlankContainerPlan.java"
REGION_SOURCE = REGION / "Region.java"
TILE_VALUE = REGION / "TileValue.java"
COLLISION_FLAG = (
    ROOT / "server/src/com/openrsc/server/util/rsc/CollisionFlag.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_169 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-sixty-nine.py"
)))


AUTHORING_SOURCES = (
    COORDINATES / "LayeredAuthoredPlacementIdentity.java",
    COORDINATES / "LayeredPackedRegionAuthoredConstructionInventory.java",
    COORDINATES / "LayeredPackedRegionAuthoredPlacementManifest.java",
    COORDINATES / "LayeredPackedRegionAuthoredPlacementDependencyInventory.java",
    COORDINATES / "LayeredPackedRegionAuthoredPopulationOutcome.java",
    COORDINATES / "LayeredPackedRegionAuthoredReconstructionRecipe.java",
)


CONSTANTS_STUB = r'''
package com.openrsc.server.constants;

public final class Constants {
    public static final int REGION_SIZE = 48;
    private Constants() {}
}
'''


FIXTURE = r'''
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementDependencyInventory;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementManifest;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPopulationOutcome;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredReconstructionRecipe;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.util.rsc.CollisionFlag;
import java.util.Arrays;

public final class PackedRegionBlankContainerPlanFixture {
    public static void main(String[] args) {
        LayeredPackedRegionReloadRecipe reload;
        Object lifecycleLock = new Object();
        synchronized (lifecycleLock) {
            LayeredPackedRegionSourceLifecycleBoundary boundary =
                LayeredPackedRegionSourceLifecycleBoundary.open(
                    new LayeredPackedRegionNpcOwnerPreservationRequirements(
                        true),
                    17L, Thread.holdsLock(lifecycleLock));
            LayeredPackedRegionSourceAbsencePreflight preflight =
                LayeredPackedRegionSourceAbsencePreflight.assess(
                    boundary,
                    Arrays.asList(
                        inventory(4, 7, true, 1, 2, 2, 1, 3, 5),
                        inventory(5, 7, true, 0, 0, 0, 0, 0, 0)),
                    14L, false, Thread.holdsLock(lifecycleLock));
            reload = LayeredPackedRegionReloadRecipe.compose(
                boundary, preflight, authoredRecipe(9L),
                Thread.holdsLock(lifecycleLock));
            boundary.invalidate();
        }

        LayeredPackedRegionBlankContainerPlan first =
            LayeredPackedRegionBlankContainerPlan.define(reload, 0);
        check(first.getGeneration() == 9L
                && first.getRequirementsObservedAtTick() == 12L
                && first.getObservedAtTick() == 14L
                && first.getResidencyMirrorVersion() == 17L
                && first.getAuthoredGeneration() == 9L
                && first.getSourceOrdinal() == 0
                && first.getPackedRegionX() == 4
                && first.getPackedRegionY() == 7,
            "blank-container plan lost exact source identity");
        check(first.wasTileStorageAvailableAtObservation()
                && first.getAuthoredPlacementCount() == 1
                && first.getPlayerCountAtObservation() == 1
                && first.getNpcCountAtObservation() == 2
                && first.getDynamicObjectCountAtObservation() == 1
                && first.getGroundItemCountAtObservation() == 3
                && first.getCollisionProductTileCountAtObservation() == 5,
            "blank-container plan lost source burdens");
        check(first.getContainerSideTileCount() == 48
                && first.getContainerTileSlotCount() == 2304
                && first.getInitialTraversalMask() == CollisionFlag.FULL_BLOCK
                && first.getInitialDiagonalWallValue() == 0
                && first.getInitialHorizontalWallValue() == 0
                && first.getInitialOverlayValue() == 0
                && first.getInitialVerticalWallValue() == 0
                && first.getInitialElevationValue() == 0
                && first.getInitialCollisionProductTileCount() == 0
                && first.getInitialPlayerCount() == 0
                && first.getInitialNpcCount() == 0
                && first.getInitialObjectCount() == 0
                && first.getInitialGroundItemCount() == 0
                && !first.isInitialProjectileAllowed()
                && !first.isInitialOriginalProjectileAllowed(),
            "blank-container initial state is not exact and sealed");
        check(first.isExpandedTileStorageRequired()
                && first.isIndependentMutableTilePerSlotRequired()
                && first.isSealedUntilTerrainInitialization()
                && first.isTerrainInitializationRequired()
                && first.isAuthoredReplayRequired()
                && first.isPlayerPreservationRequired()
                && first.isNpcPreservationRequired()
                && first.isDynamicObjectPreservationRequired()
                && first.isGroundItemPreservationRequired()
                && first.isCollisionRebuildRequired()
                && first.isRegionManagerBindingRequired()
                && first.isTransactionalRegistrationRequired()
                && first.isRollbackRequired()
                && first.isArrivalGateRequired()
                && first.isVisibilityGateRequired(),
            "blank-container later-stage requirements were weakened");

        LayeredPackedRegionBlankContainerPlan empty =
            LayeredPackedRegionBlankContainerPlan.define(reload, 1);
        check(empty.getSourceOrdinal() == 1
                && empty.getPackedRegionX() == 5
                && empty.getPackedRegionY() == 7
                && empty.getAuthoredPlacementCount() == 0
                && !empty.isAuthoredReplayRequired()
                && !empty.isPlayerPreservationRequired()
                && !empty.isNpcPreservationRequired()
                && !empty.isDynamicObjectPreservationRequired()
                && !empty.isGroundItemPreservationRequired()
                && empty.isCollisionRebuildRequired(),
            "exact empty replay gained content or skipped collision rebuild");

        check(first.isPointInTimeOnly()
                && first.isDetachedConstructionContract()
                && first.isConstructionDefinitionComplete()
                && !first.isExecutableConstruction()
                && !first.isRegionContainerCreated()
                && !first.isTileStorageAllocated()
                && !first.isRegionManagerBound()
                && !first.isSourceAbsencePerformed()
                && !first.isSourceReconstructionPerformed()
                && !first.isTerrainInitialized()
                && !first.isAuthoredReplayPerformed()
                && !first.isActiveFamilyPreservationPerformed()
                && !first.isCollisionRebuildPerformed()
                && !first.isRuntimeHandleRetained()
                && !first.isRegionRegistryMutated()
                && !first.isResidencyMirrorMutated()
                && !first.isVisibilityCacheMutated()
                && !first.isArrivalGate()
                && !first.isVisibilityReleased()
                && !first.isLifecycleAuthority(),
            "blank-container plan crossed its inert boundary");
        expectIndexFailure(() ->
            LayeredPackedRegionBlankContainerPlan.define(reload, -1));
        expectIndexFailure(() ->
            LayeredPackedRegionBlankContainerPlan.define(reload, 2));
    }

    private static LayeredPackedRegionAuthoredReconstructionRecipe
        authoredRecipe(long generation) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
            LayeredPackedRegionAuthoredPlacementManifest.builder(generation);
        manifest.recordScenery(
            4, 7, 3, 3, 200, 340, 0, 0, null);
        LayeredPackedRegionAuthoredPlacementManifest definitions =
            manifest.build();
        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencies =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(generation);
        dependencies.record(
            ConstructionKind.SCENERY, DependencyKind.OBJECT_FOOTPRINT,
            4, 7, 200, 200, 340, 340, 4, 4, 7, 7);
        return LayeredPackedRegionAuthoredReconstructionRecipe.derive(
            definitions, dependencies.build(),
            LayeredPackedRegionAuthoredPopulationOutcome.builder(generation)
                .build(definitions));
    }

    private static LayeredPackedRegionSourceAbsencePreflight.SourceInventory
        inventory(
            int x, int y, boolean tiles, int players, int npcs, int objects,
            int dynamicObjects, int items, int collisionTiles) {
        return LayeredPackedRegionSourceAbsencePreflight.SourceInventory.of(
            x, y, tiles, players, npcs, objects, dynamicObjects, items,
            collisionTiles);
    }

    private static void expectIndexFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


class LayeredMapsSliceOneHundredSeventyEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-blank-container-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        requirements = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredPackedRegionNpcOwnerPreservationRequirements.java"
        )
        constants = cls.temp / (
            "src/com/openrsc/server/constants/Constants.java"
        )
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/region/"
            "PackedRegionBlankContainerPlanFixture.java"
        )
        requirements.parent.mkdir(parents=True, exist_ok=True)
        constants.parent.mkdir(parents=True, exist_ok=True)
        fixture.parent.mkdir(parents=True, exist_ok=True)
        requirements.write_text(
            SLICE_169["REQUIREMENTS_STUB"], encoding="utf-8"
        )
        constants.write_text(CONSTANTS_STUB, encoding="utf-8")
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(requirements), str(constants), str(COLLISION_FLAG),
                *(str(path) for path in AUTHORING_SOURCES),
                str(BOUNDARY), str(PREFLIGHT), str(RELOAD_RECIPE),
                str(CONTAINER_PLAN), str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_plan_is_exact_sealed_and_fail_closed(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.region."
                "PackedRegionBlankContainerPlanFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_contract_matches_current_region_and_tile_defaults(self):
        region = REGION_SOURCE.read_text(encoding="utf-8")
        tile = TILE_VALUE.read_text(encoding="utf-8")
        constructor_start = region.index(
            "public Region(final RegionManager regionManager"
        )
        constructor = region[constructor_start:region.index(
            "\n\tpublic void unload()", constructor_start
        )]
        for required in (
            "new TileValue[Constants.REGION_SIZE][Constants.REGION_SIZE]",
            "new TileValue()",
            "new RegionObjectCollisionMutationBoundary(regionX, regionY)",
        ):
            self.assertIn(required, constructor)
        self.assertIn(
            "public byte traversalMask = CollisionFlag.FULL_BLOCK;", tile
        )
        for default in (
            "private boolean terrainBlocked = false;",
            "private int blockingSceneryCount = 0;",
            "private int terrainCollisionMask = 0;",
            "private int terrainWallProjectileCount = 0;",
            "private int dynamicProjectileCount = 0;",
        ):
            self.assertIn(default, tile)

    def test_plan_retains_no_runtime_handle_or_authority(self):
        source = CONTAINER_PLAN.read_text(encoding="utf-8")
        self.assertIn("Constants.REGION_SIZE", source)
        self.assertIn("CollisionFlag.FULL_BLOCK", source)
        for forbidden in (
            "import com.openrsc.server.model.entity",
            "new Region(",
            "new TileValue(",
            "RegionManager ",
            "getRegion(",
            "regions.put(",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
            "invalidateVisibleObjectWindowCache(",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isExecutableConstruction() { return false; }",
            "isRegionContainerCreated() { return false; }",
            "isTileStorageAllocated() { return false; }",
            "isCollisionRebuildPerformed() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_living_plan_records_slice_one_hundred_seventy_eight(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 178: Detached sealed blank-container contract", plan
        )
        self.assertIn(
            "collision reconstruction remains mandatory", plan
        )


if __name__ == "__main__":
    unittest.main()
