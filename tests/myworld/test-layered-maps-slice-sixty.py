#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
RECIPE = COORDINATES / "LayeredPackedRegionAuthoredReconstructionRecipe.java"
POPULATOR = ROOT / "server/src/com/openrsc/server/database/WorldPopulator.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


POINT_STUB = r'''
package com.openrsc.server.model;

public class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static Point location(int x, int y) {
        if (x < 0 || y < 0 || x > Short.MAX_VALUE || y > Short.MAX_VALUE) {
            throw new IllegalArgumentException("packed point out of range");
        }
        return new Point(x, y);
    }

    public int getX() { return x; }
    public int getY() { return y; }
}
'''


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;

public final class AuthoredReconstructionRecipeFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifestBuilder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(5L);
        manifestBuilder.recordScenery(
            4, 0, 3, 3, 239, 20, 0, 0, null);
        LayeredAuthoredPlacementIdentity table =
            manifestBuilder.getLastRecordedIdentity();
        manifestBuilder.recordHarvestingScenery(
            4, 0, 18, 1262, 1262, 239, 20, 0, 0, null, 1, 30, 0);
        LayeredAuthoredPlacementIdentity cabbage =
            manifestBuilder.getLastRecordedIdentity();
        manifestBuilder.recordBoundary(
            4, 0, 1, 1, 200, 20, 0, 1, null);
        LayeredAuthoredPlacementIdentity frame =
            manifestBuilder.getLastRecordedIdentity();
        manifestBuilder.recordBoundary(
            4, 0, 2, 2, 200, 20, 0, 1, null);
        LayeredAuthoredPlacementIdentity door =
            manifestBuilder.getLastRecordedIdentity();
        manifestBuilder.recordNpcSpawn(
            4, 0, 10, 230, 20, 230, 250, 20, 20);
        LayeredAuthoredPlacementIdentity npc =
            manifestBuilder.getLastRecordedIdentity();
        manifestBuilder.recordGroundItemSpawn(
            5, 0, 20, 250, 20, 1, 30, 0);
        LayeredAuthoredPlacementIdentity item =
            manifestBuilder.getLastRecordedIdentity();
        LayeredPackedRegionAuthoredPlacementManifest manifest =
            manifestBuilder.build();

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
            dependencyBuilder =
                LayeredPackedRegionAuthoredPlacementDependencyInventory
                    .builder(5L);
        dependencyBuilder.record(
            ConstructionKind.SCENERY, DependencyKind.OBJECT_FOOTPRINT,
            4, 0, 239, 240, 20, 20, 4, 5, 0, 0);
        dependencyBuilder.record(
            ConstructionKind.HARVESTING_SCENERY,
            DependencyKind.OBJECT_FOOTPRINT,
            4, 0, 239, 240, 20, 20, 4, 5, 0, 0);
        dependencyBuilder.record(
            ConstructionKind.BOUNDARY, DependencyKind.OBJECT_FOOTPRINT,
            4, 0, 200, 200, 20, 20, 4, 4, 0, 0);
        dependencyBuilder.record(
            ConstructionKind.BOUNDARY, DependencyKind.OBJECT_FOOTPRINT,
            4, 0, 200, 200, 20, 20, 4, 4, 0, 0);
        dependencyBuilder.record(
            ConstructionKind.NPC_SPAWN, DependencyKind.NPC_ROAMING,
            4, 0, 230, 250, 20, 20, 4, 5, 0, 0);
        dependencyBuilder.record(
            ConstructionKind.GROUND_ITEM_SPAWN, DependencyKind.ANCHOR_ONLY,
            5, 0, 250, 250, 20, 20, 5, 5, 0, 0);
        LayeredPackedRegionAuthoredPlacementDependencyInventory dependencies =
            dependencyBuilder.build();

        LayeredPackedRegionAuthoredPopulationOutcome outcome =
            LayeredPackedRegionAuthoredPopulationOutcome.builder(5L)
                .recordSupersession(table, cabbage)
                .recordSupersession(frame, door)
                .build(manifest);
        LayeredPackedRegionAuthoredReconstructionRecipe recipe =
            LayeredPackedRegionAuthoredReconstructionRecipe.derive(
                manifest, dependencies, outcome);

        check(recipe.getGeneration() == 5L
            && recipe.getSourceCount() == 2
            && recipe.getManifestPlacementCount() == 6
            && recipe.getSupersededPlacementCount() == 2
            && recipe.getReconstructionPlacementCount() == 4,
            "recipe separates replay history from final-live inputs");
        check(recipe.getCrossSourcePlacementCount() == 2
            && recipe.getAffectedSourceReferenceCount() == 6
            && recipe.getMaximumAffectedSourceCount() == 2,
            "recipe retains conservative dependency reach");

        LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
            sourceFour = recipe.findSource(4, 0);
        check(sourceFour != null
            && sourceFour.getManifestPlacementCount() == 5
            && sourceFour.getSupersededPlacementCount() == 2
            && sourceFour.getReconstructionPlacementCount() == 3,
            "source recipe retains final-live arithmetic");
        check(sourceFour.getPlacements().get(0).getIdentity().equals(cabbage)
            && sourceFour.getPlacements().get(0).getSourceOrdinal() == 2
            && sourceFour.getPlacements().get(1).getIdentity().equals(door)
            && sourceFour.getPlacements().get(1).getSourceOrdinal() == 4
            && sourceFour.getPlacements().get(2).getIdentity().equals(npc)
            && sourceFour.getPlacements().get(2).getSourceOrdinal() == 5,
            "recipe preserves original order and ordinal gaps");
        check(sourceFour.getPlacements().get(0).getPlacement()
                .getAuthoredDefinitionId() == 18
            && sourceFour.getPlacements().get(0).getPlacement()
                .getConstructedEntityId() == 1262
            && sourceFour.getPlacements().get(0).getDependency()
                .getMaximumPackedRegionX() == 5,
            "recipe retains primitive construction and dependency metadata");
        check(recipe.findSource(5, 0).getPlacements().get(0)
                .getIdentity().equals(item)
            && recipe.findSource(9, 9) == null,
            "source lookup is exact");
        expectImmutable(recipe.getSources());
        expectImmutable(sourceFour.getPlacements());

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder bad =
            LayeredPackedRegionAuthoredPlacementDependencyInventory.builder(6L);
        bad.record(
            ConstructionKind.GROUND_ITEM_SPAWN, DependencyKind.ANCHOR_ONLY,
            5, 0, 250, 250, 20, 20, 5, 5, 0, 0);
        expectIllegal(() ->
            LayeredPackedRegionAuthoredReconstructionRecipe.derive(
                manifest, bad.build(), outcome));
        check(!recipe.findSource(4, 0).getPlacements().get(0)
                .getIdentity().equals(table)
            && !recipe.findSource(4, 0).getPlacements().get(1)
                .getIdentity().equals(frame),
            "superseded predecessors cannot enter the recipe");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void expectImmutable(java.util.List values) {
        try {
            values.add(new Object());
            throw new AssertionError("Expected immutable list");
        } catch (UnsupportedOperationException expected) {
            // Expected refusal.
        }
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceSixtyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-sixty-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "AuthoredReconstructionRecipeFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes), str(point),
                str(fixture),
                *(str(path) for path in sorted(COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_final_live_recipe_is_exact_ordered_and_detached(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "AuthoredReconstructionRecipeFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_recipe_boundary_has_no_lifecycle_authority(self):
        recipe = RECIPE.read_text(encoding="utf-8")
        populator = POPULATOR.read_text(encoding="utf-8")
        self.assertNotIn("import com.openrsc.server.model.entity", recipe)
        self.assertNotIn("import com.openrsc.server.model.world.region", recipe)
        self.assertNotIn("RegionManager", recipe)
        self.assertNotIn("register", recipe)
        self.assertNotIn("unregister", recipe)
        self.assertIn("Collections.unmodifiableList", recipe)
        self.assertIn("getAuthoredReconstructionRecipe", populator)
        self.assertIn(
            "LayeredPackedRegionAuthoredReconstructionRecipe.derive",
            populator,
        )

    def test_living_plan_records_slice_sixty_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 60: Inert final-live reconstruction recipe", plan
        )
        self.assertIn("lifecycle authority", plan.lower())
        self.assertIn("does not change any safety blocker", plan)


if __name__ == "__main__":
    unittest.main()
