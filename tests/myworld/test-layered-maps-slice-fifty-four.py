#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
INVENTORY = COORDINATES / (
    "LayeredPackedRegionAuthoredConstructionInventory.java"
)
MANIFEST = COORDINATES / "LayeredPackedRegionAuthoredPlacementManifest.java"
DEPENDENCIES = COORDINATES / (
    "LayeredPackedRegionAuthoredPlacementDependencyInventory.java"
)
POPULATOR = ROOT / "server/src/com/openrsc/server/database/WorldPopulator.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import java.util.List;

public final class AuthoredPlacementDependencyFixture {
    public static void main(String[] args) {
        reachIsExactSortedImmutableAndAligned();
        invalidAndCompletedBuildersRefuseMutation();
    }

    private static void reachIsExactSortedImmutableAndAligned() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder definitions =
            LayeredPackedRegionAuthoredPlacementManifest.builder(8L);
        definitions.recordNpcSpawn(5, 4, 7, 250, 210, 239, 250, 210, 210)
            .recordScenery(2, 9, 100, 100, 96, 432, 0, 0, null)
            .recordBoundary(2, 9, 5, 5, 100, 432, 1, 1, null)
            .recordGroundItemSpawn(5, 4, 10, 250, 211, 1, 20, 0)
            .recordHarvestingScenery(
                5, 4, 12, 211, 211, 287, 212, 0, 0, null, 1, 30, 0);
        LayeredPackedRegionAuthoredPlacementManifest manifest =
            definitions.build();

        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder builder =
            LayeredPackedRegionAuthoredPlacementDependencyInventory.builder(8L);
        builder.record(kind("NPC_SPAWN"), dependency("NPC_ROAMING"),
                5, 4, 239, 250, 210, 210, 4, 5, 4, 4)
            .record(kind("SCENERY"), dependency("OBJECT_FOOTPRINT"),
                2, 9, 95, 96, 432, 432, 1, 2, 9, 9)
            .record(kind("BOUNDARY"), dependency("OBJECT_FOOTPRINT"),
                2, 9, 100, 100, 432, 432, 2, 2, 9, 9)
            .record(kind("GROUND_ITEM_SPAWN"), dependency("ANCHOR_ONLY"),
                5, 4, 250, 250, 211, 211, 5, 5, 4, 4)
            .record(kind("HARVESTING_SCENERY"),
                dependency("OBJECT_FOOTPRINT"),
                5, 4, 287, 288, 212, 212, 5, 6, 4, 4);
        LayeredPackedRegionAuthoredPlacementDependencyInventory inventory =
            builder.build();

        check(inventory.getGeneration() == 8L
            && inventory.getSourceCount() == 2
            && inventory.getDependencyCount() == 5
            && inventory.getCrossSourceDependencyCount() == 3
            && inventory.getAffectedSourceReferenceCount() == 8
            && inventory.getMaximumAffectedSourceCount() == 2
            && inventory.getObjectFootprintDependencyCount() == 3
            && inventory.getNpcRoamingDependencyCount() == 1
            && inventory.getAnchorOnlyDependencyCount() == 1
            && inventory.getCrossSourceObjectFootprintCount() == 2
            && inventory.getCrossSourceNpcRoamingCount() == 1
            && inventory.getObjectFootprintSourceReferenceCount() == 5
            && inventory.getNpcRoamingSourceReferenceCount() == 2
            && inventory.getAnchorOnlySourceReferenceCount() == 1
            && inventory.getMaximumObjectFootprintSourceCount() == 2
            && inventory.getMaximumNpcRoamingSourceCount() == 2,
            "dependency totals are exact");
        List<LayeredPackedRegionAuthoredPlacementDependencyInventory
            .PackedSourceDependencies> sources = inventory.getSources();
        check(sources.get(0).getPackedRegionX() == 2
            && sources.get(0).getPackedRegionY() == 9
            && sources.get(1).getPackedRegionX() == 5
            && sources.get(1).getPackedRegionY() == 4,
            "sources are coordinate sorted");
        check(inventory.findSource(5, 4) == sources.get(1)
            && inventory.findSource(8, 8) == null,
            "source lookup is exact");
        check(sources.get(0).findDependency(1).getSourceOrdinal() == 1
            && sources.get(0).findDependency(2).getSourceOrdinal() == 2
            && sources.get(0).findDependency(3) == null,
            "dependency ordinals retain manifest order");

        LayeredPackedRegionAuthoredPlacementDependencyInventory
            .PlacementDependency scenery = sources.get(0).findDependency(1);
        check(scenery.isCrossSource()
            && scenery.getAffectedSourceCount() == 2
            && scenery.getMinimumPackedX() == 95
            && scenery.getMaximumPackedX() == 96
            && scenery.getMinimumPackedRegionX() == 1
            && scenery.getMaximumPackedRegionX() == 2,
            "cross-source object reach is retained");
        check(inventory.isAlignedWith(manifest),
            "source identity and family align with the manifest");

        LayeredPackedRegionAuthoredPlacementManifest.Builder mismatch =
            LayeredPackedRegionAuthoredPlacementManifest.builder(8L);
        mismatch.recordGroundItemSpawn(5, 4, 10, 250, 211, 1, 20, 0);
        check(!inventory.isAlignedWith(mismatch.build())
            && !inventory.isAlignedWith(null),
            "missing and misaligned manifests are refused");
        expectImmutable(sources);
        expectImmutable(sources.get(0).getDependencies());
        LayeredPackedRegionAuthoredPlacementDependencyInventory empty =
            LayeredPackedRegionAuthoredPlacementDependencyInventory.empty();
        check(empty.getGeneration() == 0L
            && empty.getSourceCount() == 0
            && empty.getDependencyCount() == 0,
            "pre-population dependencies are explicitly empty");
    }

    private static void invalidAndCompletedBuildersRefuseMutation() {
        expectIllegal(() ->
            LayeredPackedRegionAuthoredPlacementDependencyInventory.builder(0L));
        LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder builder =
            LayeredPackedRegionAuthoredPlacementDependencyInventory.builder(1L);
        expectNull(() -> builder.record(
            null, dependency("ANCHOR_ONLY"),
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        expectIllegal(() -> builder.record(
            kind("NPC_SPAWN"), dependency("ANCHOR_ONLY"),
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        expectIllegal(() -> builder.record(
            kind("GROUND_ITEM_SPAWN"), dependency("ANCHOR_ONLY"),
            1, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        builder.record(
            kind("GROUND_ITEM_SPAWN"), dependency("ANCHOR_ONLY"),
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0).build();
        expectState(() -> builder.record(
            kind("GROUND_ITEM_SPAWN"), dependency("ANCHOR_ONLY"),
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        expectState(builder::build);
    }

    private static LayeredPackedRegionAuthoredConstructionInventory
            .ConstructionKind kind(String name) {
        return LayeredPackedRegionAuthoredConstructionInventory
            .ConstructionKind.valueOf(name);
    }

    private static LayeredPackedRegionAuthoredPlacementDependencyInventory
            .DependencyKind dependency(String name) {
        return LayeredPackedRegionAuthoredPlacementDependencyInventory
            .DependencyKind.valueOf(name);
    }

    private static void expectImmutable(List<?> values) {
        try {
            values.clear();
            throw new AssertionError("Expected immutable list");
        } catch (UnsupportedOperationException expected) {
            // Expected refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
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

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
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


class LayeredMapsSliceFiftyFourTest(unittest.TestCase):
    def test_detached_dependency_inventory_contract(self):
        with tempfile.TemporaryDirectory(prefix="layered-slice54-") as temp:
            temp_path = Path(temp)
            fixture_path = temp_path / (
                "com/openrsc/server/model/world/coordinate/"
                "AuthoredPlacementDependencyFixture.java"
            )
            fixture_path.parent.mkdir(parents=True)
            fixture_path.write_text(FIXTURE, encoding="utf-8")
            classes = temp_path / "classes"
            classes.mkdir()
            subprocess.run(
                [
                    "javac", "-d", str(classes), str(INVENTORY),
                    str(MANIFEST), str(DEPENDENCIES), str(fixture_path),
                ],
                check=True,
                cwd=ROOT,
            )
            subprocess.run(
                [
                    "java", "-cp", str(classes),
                    "com.openrsc.server.model.world.coordinate."
                    "AuthoredPlacementDependencyFixture",
                ],
                check=True,
                cwd=ROOT,
            )

    def test_population_records_reach_without_lifecycle_consumers(self):
        dependencies = DEPENDENCIES.read_text(encoding="utf-8")
        populator = POPULATOR.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("conservative packed-source reach", dependencies)
        self.assertIn("dependency closure", dependencies)
        self.assertIn("isAlignedWith(", dependencies)
        self.assertIn("Collections.unmodifiableList", dependencies)
        for forbidden in (
            "model.entity", "model.world.region", "server.external",
            "TileValue", "GameEvent", "AuthoredGroundItemRegistry",
        ):
            self.assertNotIn(forbidden, dependencies)

        self.assertIn("authoredPlacementDependencies", populator)
        self.assertIn("placementDependencies.build()", populator)
        self.assertIn("isAlignedWith(completedManifest)", populator)
        self.assertIn("getObjectBoundary()", populator)
        self.assertIn("DependencyKind.NPC_ROAMING", populator)
        self.assertIn("DependencyKind.ANCHOR_ONLY", populator)
        self.assertIn("getAuthoredPlacementDependencies()", populator)
        dependency_name = (
            "LayeredPackedRegionAuthoredPlacementDependencyInventory"
        )
        self.assertNotIn(dependency_name, manager)
        self.assertNotIn(dependency_name, observer)
        self.assertIn(
            "### Slice 54: Authored placement dependency envelopes", plan
        )


if __name__ == "__main__":
    unittest.main()
