#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGION = ROOT / "server/src/com/openrsc/server/model/world/region"
TERRAIN_PLAN = REGION / (
    "LayeredPackedRegionTerrainInitializationPlan.java"
)
TILE_VALUE = REGION / "TileValue.java"
LAYERED_TILE_STATE = REGION / "LayeredTileState.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_178 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-seventy-eight.py"
)))


TERRAIN_CHECKS = r'''
        LayeredPackedRegionBlankContainerPlan container =
            LayeredPackedRegionBlankContainerPlan.define(reload, 0);
        java.util.List<
            LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput>
                terrainInputs = new java.util.ArrayList<
                    LayeredPackedRegionTerrainInitializationPlan
                        .TerrainTileInput>();
        for (int x = 0; x < 48; x++) {
            for (int y = 0; y < 48; y++) {
                TileValue tile = new TileValue();
                if (x != 2 || y != 0) {
                    tile.initializeTerrainCollision();
                }
                if (x == 0 && y == 0) {
                    tile.diagWallVal = 77;
                    tile.horizontalWallVal = 3;
                    tile.overlay = 11;
                    tile.verticalWallVal = 4;
                    tile.elevation = 5;
                    tile.addTerrainCollision(
                        com.openrsc.server.util.rsc.CollisionFlag.WALL_NORTH);
                    tile.setTerrainOverlayProjectileBlocked(true);
                    tile.addTerrainWallProjectileBlock();
                }
                if (x == 1 && y == 0) {
                    tile.addDynamicCollision(
                        com.openrsc.server.util.rsc.CollisionFlag.WALL_EAST);
                    tile.addBlockingScenery();
                    tile.addDynamicProjectileBlock();
                }
                terrainInputs.add(
                    LayeredPackedRegionTerrainInitializationPlan
                        .TerrainTileInput.fromLegacy(x, y, tile));
            }
        }
        LayeredPackedRegionTerrainInitializationPlan terrain =
            LayeredPackedRegionTerrainInitializationPlan.define(
                container, terrainInputs);
        check(terrain.getGeneration() == 9L
                && terrain.getRequirementsObservedAtTick() == 12L
                && terrain.getObservedAtTick() == 14L
                && terrain.getResidencyMirrorVersion() == 17L
                && terrain.getAuthoredGeneration() == 9L
                && terrain.getSourceOrdinal() == 0
                && terrain.getPackedRegionX() == 4
                && terrain.getPackedRegionY() == 7
                && terrain.getSideTileCount() == 48
                && terrain.getTileCount() == 2304,
            "terrain plan lost exact container identity");
        check(terrain.getTerrainBlockedTileCount() == 0
                && terrain.getTerrainCollisionMaskTileCount() == 1
                && terrain.getTerrainProjectileBlockedTileCount() == 1
                && terrain.getSealedBaseTraversalTileCount() == 1
                && terrain.getFingerprintSha256().length() == 64,
            "terrain plan aggregate counts are inconsistent");
        LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
            staticTile = terrain.getTiles().get(0);
        LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
            dynamicTile = terrain.getTiles().get(48);
        LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
            sealedTile = terrain.getTiles().get(96);
        check(staticTile.getLocalX() == 0
                && staticTile.getLocalY() == 0
                && staticTile.getStaticTraversalMask()
                    == com.openrsc.server.util.rsc.CollisionFlag.WALL_NORTH
                && staticTile.getDiagonalWallValue() == 77
                && staticTile.getHorizontalWallValue() == 3
                && staticTile.getOverlay() == 11
                && staticTile.getVerticalWallValue() == 4
                && staticTile.getElevation() == 5
                && staticTile.isStaticProjectileBlocked()
                && staticTile.getTerrainCollisionMask()
                    == com.openrsc.server.util.rsc.CollisionFlag.WALL_NORTH
                && staticTile.isTerrainOverlayProjectileBlocked()
                && staticTile.getTerrainWallProjectileCount() == 1
                && !staticTile.hasSealedBaseTraversal(),
            "static archive-derived tile values were not retained");
        check(dynamicTile.getStaticTraversalMask() == 0
                && !dynamicTile.isStaticProjectileBlocked()
                && dynamicTile.getTerrainCollisionMask() == 0
                && !dynamicTile.hasSealedBaseTraversal(),
            "dynamic object or projectile state leaked into terrain input");
        check(sealedTile.getStaticTraversalMask()
                    == com.openrsc.server.util.rsc.CollisionFlag.FULL_BLOCK
                && sealedTile.getTerrainCollisionMask() == 0
                && sealedTile.getSealedBaseTraversalMask()
                    == com.openrsc.server.util.rsc.CollisionFlag.FULL_BLOCK
                && sealedTile.hasSealedBaseTraversal(),
            "uninitialized missing-sector seal was lost");
        expectUnsupported(() -> terrain.getTiles().clear());
        expectIllegalArgument(() ->
            LayeredPackedRegionTerrainInitializationPlan.define(
                container, terrainInputs.subList(0, 2303)));
        java.util.List<
            LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput>
                wrongOrder = new java.util.ArrayList<
                    LayeredPackedRegionTerrainInitializationPlan
                        .TerrainTileInput>(terrainInputs);
        java.util.Collections.swap(wrongOrder, 0, 1);
        expectIllegalArgument(() ->
            LayeredPackedRegionTerrainInitializationPlan.define(
                container, wrongOrder));
        check(terrain.isPointInTimeOnly()
                && terrain.isDetachedTerrainDefinition()
                && terrain.isTerrainInputDefinitionComplete()
                && terrain.isDynamicObjectStateExcluded()
                && terrain.isBlockingSceneryStateExcluded()
                && terrain.isDynamicProjectileStateExcluded()
                && !terrain.isArchiveReloadPerformed()
                && !terrain.isTileStorageAllocated()
                && !terrain.isRegionContainerCreated()
                && !terrain.isTerrainApplyPerformed()
                && !terrain.isAuthoredReplayPerformed()
                && !terrain.isDynamicCollisionRebuildPerformed()
                && !terrain.isActiveFamilyPreservationPerformed()
                && !terrain.isRuntimeHandleRetained()
                && !terrain.isRegionRegistryMutated()
                && !terrain.isResidencyMirrorMutated()
                && !terrain.isVisibilityCacheMutated()
                && !terrain.isArrivalGate()
                && !terrain.isVisibilityReleased()
                && !terrain.isLifecycleAuthority(),
            "terrain plan crossed its inert boundary");
'''


FIXTURE = SLICE_178["FIXTURE"].replace(
    """        check(first.isPointInTimeOnly()
                && first.isDetachedConstructionContract()""",
    TERRAIN_CHECKS
    + """
        check(first.isPointInTimeOnly()
                && first.isDetachedConstructionContract()""",
)
FIXTURE = FIXTURE.replace(
    "    private static void expectIndexFailure(Runnable operation) {",
    """    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectIndexFailure(Runnable operation) {""",
)


class LayeredMapsSliceOneHundredEightyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-terrain-input-"
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
            SLICE_178["SLICE_169"]["REQUIREMENTS_STUB"],
            encoding="utf-8",
        )
        constants.write_text(
            SLICE_178["CONSTANTS_STUB"], encoding="utf-8"
        )
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(requirements), str(constants),
                str(SLICE_178["COLLISION_FLAG"]),
                *(str(path) for path in SLICE_178["AUTHORING_SOURCES"]),
                str(SLICE_178["BOUNDARY"]),
                str(SLICE_178["PREFLIGHT"]),
                str(SLICE_178["RELOAD_RECIPE"]),
                str(SLICE_178["CONTAINER_PLAN"]), str(TILE_VALUE),
                str(LAYERED_TILE_STATE), str(TERRAIN_PLAN), str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_terrain_input_is_exact_static_bounded_and_fail_closed(self):
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

    def test_dynamic_runtime_products_are_not_retained(self):
        source = TERRAIN_PLAN.read_text(encoding="utf-8")
        nested = source[source.index(
            "public static final class TerrainTileInput"
        ):]
        for forbidden in (
            "blockingSceneryCount;",
            "dynamicCollisionCounts;",
            "dynamicProjectileCount;",
            "GameObject",
            "GroundItem",
            "Npc",
            "Player",
        ):
            self.assertNotIn(forbidden, nested)
        for required in (
            "tile.getDynamicCollisionCounts()",
            "tile.getBlockingSceneryCount()",
            "staticMask &= ~flag;",
            "staticMask &= ~CollisionFlag.FULL_BLOCK_C;",
            "tile.originalProjectileAllowed",
        ):
            self.assertIn(required, nested)

    def test_plan_has_no_archive_region_or_lifecycle_authority(self):
        source = TERRAIN_PLAN.read_text(encoding="utf-8")
        for forbidden in (
            "new Region(",
            "RegionManager ",
            "WorldLoader",
            "Sector ",
            "ZipFile",
            "getRegion(",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isArchiveReloadPerformed() { return false; }",
            "isTerrainApplyPerformed() { return false; }",
            "isDynamicCollisionRebuildPerformed() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_living_plan_records_slice_one_hundred_eighty(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 180: Detached static terrain initialization input",
            plan,
        )
        self.assertIn("sealed-base traversal", plan)


if __name__ == "__main__":
    unittest.main()
