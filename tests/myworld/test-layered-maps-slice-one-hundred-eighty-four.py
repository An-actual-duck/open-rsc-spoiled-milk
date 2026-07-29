#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION = ROOT / "server/src/com/openrsc/server/model/world/region"
REPLAY_PLAN = REGION / "LayeredPackedRegionAuthoredReplayPlan.java"
TERRAIN_PLAN = REGION / "LayeredPackedRegionTerrainInitializationPlan.java"
TERRAIN_VERIFICATION = (
    REGION / "LayeredPackedRegionIsolatedTerrainVerification.java"
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PackedRegionAuthoredReplayPlanFixture {
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
                        inventory(4, 7, true, 1, 2, 3, 1, 3, 5),
                        inventory(5, 7, true, 0, 0, 0, 0, 0, 0)),
                    14L, false, Thread.holdsLock(lifecycleLock));
            reload = LayeredPackedRegionReloadRecipe.compose(
                boundary, preflight, authoredRecipe(9L),
                Thread.holdsLock(lifecycleLock));
            boundary.invalidate();
        }

        LayeredPackedRegionBlankContainerPlan container =
            LayeredPackedRegionBlankContainerPlan.define(reload, 0);
        LayeredPackedRegionTerrainInitializationPlan terrain =
            LayeredPackedRegionTerrainInitializationPlan.define(
                container, passableTerrainInputs());
        LayeredPackedRegionIsolatedTerrainVerification verification =
            LayeredPackedRegionIsolatedTerrainVerification.verified(
                container, terrain, true, true, true, true);
        LayeredPackedRegionAuthoredReplayPlan plan =
            LayeredPackedRegionAuthoredReplayPlan.define(
                reload, 0, verification);

        check(plan.getGeneration() == 9L
                && plan.getRequirementsObservedAtTick() == 12L
                && plan.getObservedAtTick() == 14L
                && plan.getResidencyMirrorVersion() == 17L
                && plan.getAuthoredGeneration() == 9L
                && plan.getSelectedSourceOrdinal() == 0
                && plan.getPackedRegionX() == 4
                && plan.getPackedRegionY() == 7,
            "authored replay plan lost exact terrain-verified identity");
        check(plan.isAuthoredSourceDeclared()
                && !plan.isExactEmptyReplay()
                && plan.getManifestPlacementCount() == 5
                && plan.getSupersededPlacementCount() == 0
                && plan.getPlacementCount() == 5
                && plan.getSceneryPlacementCount() == 1
                && plan.getBoundaryPlacementCount() == 1
                && plan.getNpcSpawnPlacementCount() == 1
                && plan.getGroundItemSpawnPlacementCount() == 1
                && plan.getHarvestingSceneryPlacementCount() == 1
                && plan.getAuthoredObjectPlacementCount() == 3
                && plan.getCrossSourcePlacementCount() == 2
                && plan.getAffectedSourceReferenceCount() == 8
                && plan.getFingerprintSha256().length() == 64,
            "authored replay family totals are inconsistent");

        List<LayeredPackedRegionAuthoredReplayPlan.AuthoredReplayPlacement>
            placements = plan.getPlacements();
        check(placements.get(0).getAuthoredSourceOrdinal() == 1
                && placements.get(0).getConstructionKind()
                    == ConstructionKind.SCENERY
                && placements.get(0).getPackedX() == 239
                && placements.get(0).getObjectType() == 0
                && placements.get(0).getDependencyKind()
                    == DependencyKind.OBJECT_FOOTPRINT
                && placements.get(0).isCrossSource()
                && placements.get(0).getAffectedSourceCount() == 2,
            "scenery constructor or footprint was not copied exactly");
        check(placements.get(1).getConstructionKind()
                    == ConstructionKind.BOUNDARY
                && placements.get(1).getPermanentObjectId() == 4
                && placements.get(1).getDirection() == 1
                && placements.get(1).getObjectType() == 1
                && "gate-owner".equals(placements.get(1).getObjectOwner()),
            "boundary constructor was not copied exactly");
        check(placements.get(2).getConstructionKind()
                    == ConstructionKind.NPC_SPAWN
                && placements.get(2).getConstructedEntityId() == 5
                && placements.get(2).getNpcMinimumX() == 190
                && placements.get(2).getNpcMaximumX() == 250
                && placements.get(2).getDependencyKind()
                    == DependencyKind.NPC_ROAMING
                && placements.get(2).isCrossSource()
                && placements.get(2).getAffectedSourceCount() == 3,
            "NPC constructor or roaming envelope was not copied exactly");
        check(placements.get(3).getConstructionKind()
                    == ConstructionKind.GROUND_ITEM_SPAWN
                && placements.get(3).getItemAmount() == 2
                && placements.get(3).getItemRespawnTime() == 100
                && placements.get(3).getItemNoted() == 0
                && placements.get(3).getDependencyKind()
                    == DependencyKind.ANCHOR_ONLY,
            "ground-item constructor was not copied exactly");
        check(placements.get(4).getConstructionKind()
                    == ConstructionKind.HARVESTING_SCENERY
                && placements.get(4).getAuthoredDefinitionId() == 7
                && placements.get(4).getConstructedEntityId() == 8
                && placements.get(4).getItemAmount() == 1
                && placements.get(4).getItemRespawnTime() == 50
                && "harvest-owner".equals(
                    placements.get(4).getObjectOwner()),
            "harvesting constructor was not copied exactly");
        expectUnsupported(() -> placements.clear());
        check(plan.getFingerprintSha256().equals(
                LayeredPackedRegionAuthoredReplayPlan.define(
                    reload, 0, verification).getFingerprintSha256()),
            "authored replay fingerprint is not deterministic");

        LayeredPackedRegionBlankContainerPlan emptyContainer =
            LayeredPackedRegionBlankContainerPlan.define(reload, 1);
        LayeredPackedRegionTerrainInitializationPlan emptyTerrain =
            LayeredPackedRegionTerrainInitializationPlan.define(
                emptyContainer, passableTerrainInputs());
        LayeredPackedRegionIsolatedTerrainVerification emptyVerification =
            LayeredPackedRegionIsolatedTerrainVerification.verified(
                emptyContainer, emptyTerrain, true, true, true, true);
        LayeredPackedRegionAuthoredReplayPlan empty =
            LayeredPackedRegionAuthoredReplayPlan.define(
                reload, 1, emptyVerification);
        check(!empty.isAuthoredSourceDeclared()
                && empty.isExactEmptyReplay()
                && empty.getPlacementCount() == 0
                && empty.getAffectedSourceReferenceCount() == 0
                && empty.getFingerprintSha256().length() == 64,
            "exact empty authored replay was not retained");
        expectIndexFailure(() ->
            LayeredPackedRegionAuthoredReplayPlan.define(
                reload, 2, verification));
        expectIllegalArgument(() ->
            LayeredPackedRegionAuthoredReplayPlan.define(
                reload, 1, verification));

        check(plan.isPointInTimeOnly()
                && plan.isDetachedReplayDefinition()
                && plan.isReplayDefinitionComplete()
                && plan.isTerrainVerificationRequiredAndMatched()
                && !plan.isExecutableReplay()
                && !plan.isRegionContainerReturned()
                && !plan.isAuthoredSceneryMembershipApplied()
                && !plan.isNpcMembershipApplied()
                && !plan.isGroundItemMembershipApplied()
                && !plan.isCollisionDerived()
                && !plan.isSchedulerStateRestored()
                && !plan.isSourceAbsencePerformed()
                && !plan.isSourceReconstructionPerformed()
                && !plan.isRuntimeHandleRetained()
                && !plan.isRegionRegistryMutated()
                && !plan.isResidencyMirrorMutated()
                && !plan.isVisibilityCacheMutated()
                && !plan.isArrivalGate()
                && !plan.isVisibilityReleased()
                && !plan.isLifecycleAuthority(),
            "authored replay plan crossed its inert boundary");
    }

    private static List<
        LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput>
            passableTerrainInputs() {
        List<LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput>
            inputs = new ArrayList<
                LayeredPackedRegionTerrainInitializationPlan
                    .TerrainTileInput>();
        for (int x = 0; x < 48; x++) {
            for (int y = 0; y < 48; y++) {
                TileValue tile = new TileValue();
                tile.initializeTerrainCollision();
                inputs.add(
                    LayeredPackedRegionTerrainInitializationPlan
                        .TerrainTileInput.fromLegacy(x, y, tile));
            }
        }
        return inputs;
    }

    private static LayeredPackedRegionAuthoredReconstructionRecipe
        authoredRecipe(long generation) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
            LayeredPackedRegionAuthoredPlacementManifest.builder(generation);
        manifest.recordScenery(
            4, 7, 3, 3, 239, 340, 0, 0, null);
        manifest.recordBoundary(
            4, 7, 4, 4, 201, 341, 1, 1, "gate-owner");
        manifest.recordNpcSpawn(
            4, 7, 5, 202, 342, 190, 250, 340, 350);
        manifest.recordGroundItemSpawn(
            4, 7, 6, 203, 343, 2, 100, 0);
        manifest.recordHarvestingScenery(
            4, 7, 7, 8, 8, 204, 344, 0, 0, "harvest-owner",
            1, 50, 0);
        LayeredPackedRegionAuthoredPlacementManifest definitions =
            manifest.build();
        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencies =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(generation);
        dependencies.record(
            ConstructionKind.SCENERY, DependencyKind.OBJECT_FOOTPRINT,
            4, 7, 239, 240, 340, 340, 4, 5, 7, 7);
        dependencies.record(
            ConstructionKind.BOUNDARY, DependencyKind.OBJECT_FOOTPRINT,
            4, 7, 201, 201, 341, 341, 4, 4, 7, 7);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 7, 190, 250, 340, 350, 3, 5, 7, 7);
        dependencies.record(
            ConstructionKind.GROUND_ITEM_SPAWN, DependencyKind.ANCHOR_ONLY,
            4, 7, 203, 203, 343, 343, 4, 4, 7, 7);
        dependencies.record(
            ConstructionKind.HARVESTING_SCENERY,
            DependencyKind.OBJECT_FOOTPRINT,
            4, 7, 204, 204, 344, 344, 4, 4, 7, 7);
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

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void expectIndexFailure(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
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

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


class LayeredMapsSliceOneHundredEightyFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-authored-replay-"
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
            "PackedRegionAuthoredReplayPlanFixture.java"
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
                str(LAYERED_TILE_STATE), str(TERRAIN_PLAN),
                str(TERRAIN_VERIFICATION), str(REPLAY_PLAN),
                str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_replay_plan_preserves_all_typed_constructor_families(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.region."
                "PackedRegionAuthoredReplayPlanFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_replay_plan_retains_no_runtime_handle_or_apply_path(self):
        source = REPLAY_PLAN.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model.entity",
            "private final ReconstructionPlacement ",
            "private final AuthoredPlacement ",
            "private final PlacementDependency ",
            "new Region(",
            "new GameObject(",
            "new Npc(",
            "new GroundItem(",
            "RegionManager ",
            "getRegion(",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isTerrainVerificationRequiredAndMatched() { return true; }",
            "isExecutableReplay() { return false; }",
            "isAuthoredSceneryMembershipApplied() { return false; }",
            "isNpcMembershipApplied() { return false; }",
            "isGroundItemMembershipApplied() { return false; }",
            "isCollisionDerived() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_living_plan_records_slice_one_hundred_eighty_four(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 184: Detached authored replay definition", plan
        )
        self.assertIn("stable final-live placement order", plan)


if __name__ == "__main__":
    unittest.main()
