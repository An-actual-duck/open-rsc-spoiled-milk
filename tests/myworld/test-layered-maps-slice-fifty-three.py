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

public final class AuthoredPlacementManifestFixture {
    public static void main(String[] args) {
        definitionsAreExactStableAndCountEquivalent();
        invalidAndCompletedBuildersRefuseMutation();
    }

    private static void definitionsAreExactStableAndCountEquivalent() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder builder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(4L);
        builder.recordNpcSpawn(5, 4, 7, 251, 210, 250, 252, 208, 212)
            .recordScenery(2, 9, 100, 100, 110, 440, 4, 0, null)
            .recordBoundary(2, 9, 5, 5, 111, 440, 1, 1, null)
            .recordNpcSpawn(5, 4, 7, 251, 210, 250, 252, 208, 212)
            .recordGroundItemSpawn(5, 4, 10, 250, 211, 3, 25, 1)
            .recordHarvestingScenery(
                5, 4, 12, 211, 211, 252, 212, 0, 0, null, 1, 30, 0);
        LayeredPackedRegionAuthoredPlacementManifest manifest = builder.build();

        check(manifest.getGeneration() == 4L
            && manifest.getSourceCount() == 2
            && manifest.getPlacementCount() == 6
            && manifest.getSceneryCount() == 1
            && manifest.getBoundaryCount() == 1
            && manifest.getNpcSpawnCount() == 2
            && manifest.getGroundItemSpawnCount() == 1
            && manifest.getHarvestingSceneryCount() == 1,
            "manifest totals are exact");

        List<LayeredPackedRegionAuthoredPlacementManifest.PackedSourceManifest>
            sources = manifest.getSources();
        check(sources.get(0).getPackedRegionX() == 2
            && sources.get(0).getPackedRegionY() == 9
            && sources.get(1).getPackedRegionX() == 5
            && sources.get(1).getPackedRegionY() == 4,
            "sources are coordinate sorted");
        check(manifest.findSource(5, 4) == sources.get(1)
            && manifest.findSource(9, 9) == null,
            "source lookup is exact");

        LayeredPackedRegionAuthoredPlacementManifest.PackedSourceManifest busy =
            sources.get(1);
        check(busy.getPlacementCount() == 4
            && busy.findPlacement(1).getSourceOrdinal() == 1
            && busy.findPlacement(2).getSourceOrdinal() == 2
            && busy.findPlacement(3).getSourceOrdinal() == 3
            && busy.findPlacement(4).getSourceOrdinal() == 4
            && busy.findPlacement(0) == null
            && busy.findPlacement(5) == null,
            "source ordinals are stable and one based");
        check(busy.findPlacement(1) != busy.findPlacement(2)
            && busy.findPlacement(1).getAuthoredDefinitionId()
                == busy.findPlacement(2).getAuthoredDefinitionId()
            && busy.findPlacement(1).getPackedX()
                == busy.findPlacement(2).getPackedX()
            && busy.findPlacement(1).getPackedY()
                == busy.findPlacement(2).getPackedY(),
            "exact duplicate definitions remain distinct");

        LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement npc =
            busy.findPlacement(1);
        check(npc.getKind()
                == LayeredPackedRegionAuthoredConstructionInventory
                    .ConstructionKind.NPC_SPAWN
            && npc.getConstructedEntityId() == 7
            && npc.getNpcMinimumX() == 250
            && npc.getNpcMaximumX() == 252
            && npc.getNpcMinimumY() == 208
            && npc.getNpcMaximumY() == 212
            && npc.getPermanentObjectId()
                == LayeredPackedRegionAuthoredPlacementManifest.NOT_APPLICABLE,
            "NPC spawn origin and roaming bounds are detached");

        LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement harvest =
            busy.findPlacement(4);
        check(harvest.getKind()
                == LayeredPackedRegionAuthoredConstructionInventory
                    .ConstructionKind.HARVESTING_SCENERY
            && harvest.getAuthoredDefinitionId() == 12
            && harvest.getConstructedEntityId() == 211
            && harvest.getPermanentObjectId() == 211
            && harvest.getObjectType() == 0
            && harvest.getItemAmount() == 1
            && harvest.getItemRespawnTime() == 30
            && harvest.getItemNoted() == 0,
            "harvesting source and constructed scenery both survive");

        LayeredPackedRegionAuthoredConstructionInventory.Builder counts =
            LayeredPackedRegionAuthoredConstructionInventory.builder(4L);
        counts.record(kind("NPC_SPAWN"), 5, 4)
            .record(kind("SCENERY"), 2, 9)
            .record(kind("BOUNDARY"), 2, 9)
            .record(kind("NPC_SPAWN"), 5, 4)
            .record(kind("GROUND_ITEM_SPAWN"), 5, 4)
            .record(kind("HARVESTING_SCENERY"), 5, 4);
        check(manifest.isCountEquivalentTo(counts.build()),
            "manifest matches the completed count inventory");
        LayeredPackedRegionAuthoredConstructionInventory.Builder mismatch =
            LayeredPackedRegionAuthoredConstructionInventory.builder(4L);
        mismatch.record(kind("NPC_SPAWN"), 5, 4);
        check(!manifest.isCountEquivalentTo(mismatch.build())
            && !manifest.isCountEquivalentTo(null),
            "count mismatch and absent inventory are refused");

        expectImmutable(sources);
        expectImmutable(busy.getPlacements());
        LayeredPackedRegionAuthoredPlacementManifest empty =
            LayeredPackedRegionAuthoredPlacementManifest.empty();
        check(empty.getGeneration() == 0L
            && empty.getSourceCount() == 0
            && empty.getPlacementCount() == 0,
            "pre-population manifest is explicitly empty");
    }

    private static void invalidAndCompletedBuildersRefuseMutation() {
        expectIllegal(() ->
            LayeredPackedRegionAuthoredPlacementManifest.builder(0L));
        LayeredPackedRegionAuthoredPlacementManifest.Builder builder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(1L);
        expectIllegal(() -> builder.recordScenery(
            -1, 0, 1, 1, 0, 0, 0, 0, null));
        expectIllegal(() -> builder.recordScenery(
            0, 0, 1, 1, 0, 0, 0, 1, null));
        builder.recordScenery(0, 0, 1, 1, 0, 0, 0, 0, null).build();
        expectState(() -> builder.recordScenery(
            0, 0, 1, 1, 0, 0, 0, 0, null));
        expectState(builder::build);
    }

    private static LayeredPackedRegionAuthoredConstructionInventory
            .ConstructionKind kind(String name) {
        return LayeredPackedRegionAuthoredConstructionInventory
            .ConstructionKind.valueOf(name);
    }

    private static void expectImmutable(List<?> values) {
        try {
            values.clear();
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


class LayeredMapsSliceFiftyThreeTest(unittest.TestCase):
    def test_detached_manifest_contract(self):
        with tempfile.TemporaryDirectory(prefix="layered-slice53-") as temp:
            temp_path = Path(temp)
            fixture_path = temp_path / (
                "com/openrsc/server/model/world/coordinate/"
                "AuthoredPlacementManifestFixture.java"
            )
            fixture_path.parent.mkdir(parents=True)
            fixture_path.write_text(FIXTURE, encoding="utf-8")
            classes = temp_path / "classes"
            classes.mkdir()
            subprocess.run(
                [
                    "javac", "-d", str(classes), str(INVENTORY),
                    str(MANIFEST), str(fixture_path),
                ],
                check=True,
                cwd=ROOT,
            )
            subprocess.run(
                [
                    "java", "-cp", str(classes),
                    "com.openrsc.server.model.world.coordinate."
                    "AuthoredPlacementManifestFixture",
                ],
                check=True,
                cwd=ROOT,
            )

    def test_population_publishes_only_completed_detached_definitions(self):
        manifest = MANIFEST.read_text(encoding="utf-8")
        populator = POPULATOR.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("stable identity within its packed source", manifest)
        self.assertIn("exact duplicate definitions remain distinct", manifest)
        self.assertIn("It is not a reload", manifest)
        self.assertIn("MAXIMUM_AUTHORED_PLACEMENTS", manifest)
        self.assertIn("Collections.unmodifiableList", manifest)
        for forbidden in (
            "model.entity", "model.world.region", "server.external",
            "TileValue", "AuthoredGroundItemRegistry", "GameEvent",
        ):
            self.assertNotIn(forbidden, manifest)

        self.assertIn("authoredPlacementManifest", populator)
        self.assertIn("placementManifest.build()", populator)
        self.assertIn("isCountEquivalentTo(completedInventory)", populator)
        self.assertIn("getAuthoredPlacementManifest()", populator)
        for recorder in (
            "recordObjectPlacement(placementManifest, obj)",
            "recordNpcPlacement(placementManifest, n)",
            "recordGroundItemPlacement(placementManifest, i)",
            "recordHarvestingPlacement(",
        ):
            self.assertIn(recorder, populator)
        self.assertLess(
            populator.index("getWorld().registerGameObject(obj);"),
            populator.index("recordObjectPlacement(placementManifest, obj)"),
        )
        self.assertLess(
            populator.index("getWorld().registerNpc(npc);"),
            populator.index("recordNpcPlacement(placementManifest, n)"),
        )
        self.assertLess(
            populator.index("getWorld().registerGameObject(harvestingScenery);"),
            populator.index("recordHarvestingPlacement("),
        )
        self.assertIn("if (authoredItem != null)", populator)

        manifest_name = "LayeredPackedRegionAuthoredPlacementManifest"
        self.assertNotIn(manifest_name, manager)
        self.assertNotIn(manifest_name, observer)
        self.assertIn(
            "### Slice 53: Detached authored-placement manifest", plan
        )


if __name__ == "__main__":
    unittest.main()
