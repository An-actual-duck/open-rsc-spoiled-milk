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
REGION_MANAGER = REGION / "RegionManager.java"
TERRAIN_PLAN = REGION / (
    "LayeredPackedRegionTerrainInitializationPlan.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_179 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-seventy-nine.py"
)))


FIXTURE = r'''
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.ActiveNpcResidencyFixture;
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
import java.util.Collections;
import java.util.List;

public final class PackedRegionTerrainBoundaryCaptureFixture {
    public static void main(String[] args) {
        RegionManager manager = new RegionManager(null);
        Region region = manager.getRegion(4 * 48, 0);
        for (int x = 0; x < 48; x++) {
            for (int y = 0; y < 48; y++) {
                if (x != 2 || y != 0) {
                    region.getMutableTileValue(x, y)
                        .initializeTerrainCollision();
                }
            }
        }
        TileValue staticTile = region.getMutableTileValue(0, 0);
        staticTile.addTerrainCollision(CollisionFlag.WALL_NORTH);
        staticTile.setTerrainOverlayProjectileBlocked(true);
        TileValue dynamicTile = region.getMutableTileValue(1, 0);
        dynamicTile.addDynamicCollision(CollisionFlag.WALL_EAST);
        dynamicTile.addBlockingScenery();
        dynamicTile.addDynamicProjectileBlock();

        LayeredPackedRegionNpcOwnerPreservationRequirements requirements =
            ActiveNpcResidencyFixture.requirementsForIsolatedRegionFixture();
        final List<LayeredTileState>[] captured = new List[1];
        final LayeredPackedRegionTerrainInitializationPlan[] terrain =
            new LayeredPackedRegionTerrainInitializationPlan[1];
        boolean entered =
            manager.withinLayeredPackedRegionSourceLifecycleBoundary(
                requirements, boundary -> {
                    LayeredPackedRegionSourceAbsencePreflight preflight =
                        LayeredPackedRegionSourceAbsencePreflight.assess(
                            boundary,
                            Collections.singletonList(
                                LayeredPackedRegionSourceAbsencePreflight
                                    .SourceInventory.of(
                                        4, 0, true, 0, 0, 1, 1, 0, 4)),
                            14L, false, true);
                    LayeredPackedRegionReloadRecipe reload =
                        LayeredPackedRegionReloadRecipe.compose(
                            boundary, preflight, authoredRecipe(), true);
                    LayeredPackedRegionBlankContainerPlan container =
                        LayeredPackedRegionBlankContainerPlan.define(
                            reload, 0);
                    captured[0] =
                        manager.captureLayeredPackedRegionTerrainTileStates(
                            boundary, 0);
                    terrain[0] =
                        LayeredPackedRegionTerrainInitializationPlan
                            .defineFromResidentTileStates(
                                container, boundary, captured[0]);
                });
        check(entered && captured[0] != null && terrain[0] != null,
            "real source lifecycle boundary did not produce terrain input");
        check(captured[0].size() == 2304
                && captured[0].get(0).getTerrainCollisionMask()
                    == CollisionFlag.WALL_NORTH
                && captured[0].get(48).getDynamicCollisionCounts()[1] == 1
                && captured[0].get(48).getBlockingSceneryCount() == 1
                && captured[0].get(48).getDynamicProjectileCount() == 1,
            "full-fidelity transient tile capture is incomplete");
        LayeredPackedRegionTerrainInitializationPlan result = terrain[0];
        check(result.getSourceOrdinal() == 0
                && result.getPackedRegionX() == 4
                && result.getPackedRegionY() == 0
                && result.getTileCount() == 2304
                && result.getTerrainCollisionMaskTileCount() == 1
                && result.getTerrainProjectileBlockedTileCount() == 1
                && result.getSealedBaseTraversalTileCount() == 1
                && result.getTiles().get(0).getStaticTraversalMask()
                    == CollisionFlag.WALL_NORTH
                && result.getTiles().get(48).getStaticTraversalMask() == 0
                && !result.getTiles().get(48)
                    .isStaticProjectileBlocked()
                && result.getTiles().get(96).getStaticTraversalMask()
                    == CollisionFlag.FULL_BLOCK,
            "terrain-only reduction did not separate static and dynamic state");
        expectUnsupported(() -> captured[0].clear());
        expectIllegalState(() ->
            manager.captureLayeredPackedRegionTerrainTileStates(null, 0));
    }

    private static LayeredPackedRegionAuthoredReconstructionRecipe
        authoredRecipe() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
            LayeredPackedRegionAuthoredPlacementManifest.builder(9L);
        manifest.recordNpcSpawn(
            4, 0, 10, 200, 20, 190, 210, 10, 30);
        LayeredPackedRegionAuthoredPlacementManifest definitions =
            manifest.build();
        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencies =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(9L);
        dependencies.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 190, 210, 10, 30, 3, 4, 0, 0);
        return LayeredPackedRegionAuthoredReconstructionRecipe.derive(
            definitions, dependencies.build(),
            LayeredPackedRegionAuthoredPopulationOutcome.builder(9L)
                .build(definitions));
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected lifecycle refusal");
        } catch (IllegalStateException | NullPointerException expected) {
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


class LayeredMapsSliceOneHundredEightyOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-terrain-boundary-"
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
            SLICE_179["build_requirements_fixture"](),
            encoding="utf-8",
        )
        region_fixture.write_text(FIXTURE, encoding="utf-8")
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

    def test_real_lifecycle_boundary_captures_and_reduces_terrain(self):
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

    def test_capture_requires_exact_resident_source_boundary(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        method_start = manager.index(
            "captureLayeredPackedRegionTerrainTileStates("
        )
        method = manager[method_start:manager.index(
            "/** Opens one dormant owner", method_start
        )]
        for required in (
            "Thread.holdsLock(layeredRegionLifecycleLock)",
            "checked.getResidencyMirrorVersion()",
            "peekRegionFromSectorCoordinates(",
            "isPackedRegionRegistered(",
            "LayeredTileState.fromLegacy(",
            "Collections.unmodifiableList(states)",
        ):
            self.assertIn(required, method)
        for forbidden in (
            "getRegionFromSectorCoordinates(",
            "new Region(",
            "regions.put(",
            "regions.remove(",
            "registerPackedRegion(",
            "unregisterPackedRegion(",
            "invalidateVisibleObjectWindowCache(",
        ):
            self.assertNotIn(forbidden, method)

    def test_terrain_reduction_is_immediate_and_handle_free(self):
        source = TERRAIN_PLAN.read_text(encoding="utf-8")
        self.assertIn("defineFromResidentTileStates(", source)
        self.assertIn("TerrainTileInput.fromLayeredState(", source)
        self.assertNotIn("private final LayeredTileState ", source)
        self.assertNotIn("private final TileValue ", source)
        self.assertNotIn("private final Region ", source)

    def test_living_plan_records_slice_one_hundred_eighty_one(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 181: Lifecycle-bound resident terrain capture",
            plan,
        )
        self.assertIn("transient full-fidelity", plan)


if __name__ == "__main__":
    unittest.main()
