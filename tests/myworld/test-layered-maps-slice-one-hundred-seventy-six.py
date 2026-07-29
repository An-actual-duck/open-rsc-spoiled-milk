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
REGION_MANAGER = REGION / "RegionManager.java"
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
import java.util.Arrays;

public final class PackedRegionReloadRecipeFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredReconstructionRecipe authored =
            authoredRecipe(9L);
        Object lifecycleLock = new Object();
        LayeredPackedRegionReloadRecipe reload;
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
                boundary, preflight, authored,
                Thread.holdsLock(lifecycleLock));

            expectIllegalArgument(() ->
                LayeredPackedRegionReloadRecipe.compose(
                    boundary, preflight, authoredRecipe(10L),
                    Thread.holdsLock(lifecycleLock)));
            boundary.invalidate();
        }

        check(reload.getGeneration() == 9L
                && reload.getRequirementsObservedAtTick() == 12L
                && reload.getObservedAtTick() == 14L
                && reload.getResidencyMirrorVersion() == 17L
                && reload.getAuthoredGeneration() == 9L,
            "reload recipe lost lifecycle identity");
        check(reload.getSourceCount() == 2
                && reload.getAuthoredSourceCount() == 1
                && reload.getEmptyAuthoredSourceCount() == 1
                && reload.getAuthoredPlacementCount() == 1
                && reload.getManifestPlacementCount() == 1
                && reload.getSupersededPlacementCount() == 0
                && reload.getAffectedSourceReferenceCount() == 1,
            "reload recipe authored totals are inconsistent");
        check(reload.getPlayerCount() == 1L
                && reload.getNpcCount() == 2L
                && reload.getDynamicObjectCount() == 1L
                && reload.getGroundItemCount() == 3L
                && reload.getCollisionProductTileCount() == 5L,
            "reload recipe lost unresolved runtime-family counts");

        LayeredPackedRegionReloadRecipe.SourceRecipe first =
            reload.getSources().get(0);
        LayeredPackedRegionReloadRecipe.SourceRecipe second =
            reload.getSources().get(1);
        check(first.getPackedRegionX() == 4
                && first.getPackedRegionY() == 7
                && first.isTileStorageAvailableAtObservation()
                && first.isAuthoredSourceDeclared()
                && !first.isEmptyAuthoredReplay()
                && first.getAuthoredPlacementCount() == 1
                && first.getAuthoredPlacements().get(0).getPlacement()
                    .getAuthoredDefinitionId() == 3,
            "declared authored source was not copied exactly");
        check(second.getPackedRegionX() == 5
                && second.getPackedRegionY() == 7
                && !second.isAuthoredSourceDeclared()
                && second.isEmptyAuthoredReplay()
                && second.getManifestPlacementCount() == 0
                && second.getAffectedSourceReferenceCount() == 0,
            "source without authored entries is not an exact empty replay");
        expectUnsupported(() -> reload.getSources().clear());
        expectUnsupported(() -> first.getAuthoredPlacements().clear());

        check(reload.isPointInTimeOnly()
                && reload.isDetachedDefinitionComplete()
                && !reload.isExecutableReload()
                && !reload.isRegionContainerCreated()
                && !reload.isSourceAbsencePerformed()
                && !reload.isSourceReconstructionPerformed()
                && !reload.isAuthoredReplayPerformed()
                && !reload.isCollisionRebuildPerformed()
                && !reload.isRuntimeHandleRetained()
                && !reload.isRegionRegistryMutated()
                && !reload.isResidencyMirrorMutated()
                && !reload.isVisibilityCacheMutated()
                && !reload.isArrivalGate()
                && !reload.isLifecycleAuthority(),
            "detached recipe crossed its inert boundary");
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

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
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


class LayeredMapsSliceOneHundredSeventySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-region-reload-recipe-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        requirements = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredPackedRegionNpcOwnerPreservationRequirements.java"
        )
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/region/"
            "PackedRegionReloadRecipeFixture.java"
        )
        requirements.parent.mkdir(parents=True, exist_ok=True)
        fixture.parent.mkdir(parents=True, exist_ok=True)
        requirements.write_text(
            SLICE_169["REQUIREMENTS_STUB"], encoding="utf-8"
        )
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(requirements), *(str(path) for path in AUTHORING_SOURCES),
                str(BOUNDARY), str(PREFLIGHT), str(RELOAD_RECIPE),
                str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_reload_recipe_is_exact_detached_and_fail_closed(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.region."
                "PackedRegionReloadRecipeFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_manager_captures_only_under_real_boundary(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        method_start = manager.index(
            "public LayeredPackedRegionReloadRecipe"
        )
        method = manager[method_start:manager.index(
            "/** Opens one dormant owner", method_start
        )]
        for required in (
            "Thread.holdsLock(layeredRegionLifecycleLock)",
            "checkedBoundary.getResidencyMirrorVersion()",
            "peekRegionFromSectorCoordinates(",
            "isPackedRegionRegistered(",
            "LayeredPackedRegionReloadRecipe.compose(",
        ):
            self.assertIn(required, method)
        for forbidden in (
            "getRegionFromSectorCoordinates(",
            "new Region(",
            "regions.put(",
            "regions.remove(",
            ".unload()",
            "unregisterPackedRegion(",
            "registerPackedRegion(",
            "invalidateVisibleObjectWindowCache(",
        ):
            self.assertNotIn(forbidden, method)

    def test_reload_recipe_has_no_runtime_or_lifecycle_authority(self):
        source = RELOAD_RECIPE.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", source)
        self.assertNotIn("new Region(", source)
        self.assertNotIn("getWorld()", source)
        self.assertIn("isExecutableReload() { return false; }", source)
        self.assertIn(
            "isCollisionRebuildPerformed() { return false; }", source
        )
        self.assertIn("isLifecycleAuthority() { return false; }", source)

    def test_living_plan_records_slice_one_hundred_seventy_six(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 176: Detached exact Region reload recipe", plan
        )
        self.assertIn("collision rebuild remains separate", plan)


if __name__ == "__main__":
    unittest.main()
