#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
INVENTORY = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionAuthoredConstructionInventory.java"
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

public final class AuthoredConstructionInventoryFixture {
    public static void main(String[] args) {
        countsAreExactSortedAndImmutable();
        invalidAndCompletedBuildersRefuseMutation();
    }

    private static void countsAreExactSortedAndImmutable() {
        LayeredPackedRegionAuthoredConstructionInventory.Builder builder =
            LayeredPackedRegionAuthoredConstructionInventory.builder(3L);
        builder.record(kind("NPC_SPAWN"), 5, 4)
            .record(kind("SCENERY"), 2, 9)
            .record(kind("BOUNDARY"), 2, 9)
            .record(kind("GROUND_ITEM_SPAWN"), 5, 4)
            .record(kind("HARVESTING_SCENERY"), 5, 4)
            .record(kind("SCENERY"), 5, 4);
        LayeredPackedRegionAuthoredConstructionInventory inventory =
            builder.build();

        check(inventory.getGeneration() == 3L
            && inventory.getSourceCount() == 2
            && inventory.getSceneryCount() == 2
            && inventory.getBoundaryCount() == 1
            && inventory.getNpcSpawnCount() == 1
            && inventory.getGroundItemSpawnCount() == 1
            && inventory.getHarvestingSceneryCount() == 1
            && inventory.getAuthoredConstructionCount() == 6,
            "aggregate counts are exact");
        List<LayeredPackedRegionAuthoredConstructionInventory
            .PackedSourceInventory> sources = inventory.getSources();
        check(sources.get(0).getPackedRegionX() == 2
            && sources.get(0).getPackedRegionY() == 9
            && sources.get(0).getAuthoredConstructionCount() == 2
            && sources.get(1).getPackedRegionX() == 5
            && sources.get(1).getPackedRegionY() == 4
            && sources.get(1).getAuthoredConstructionCount() == 4,
            "sources are ordered by packed coordinate");
        check(inventory.findSource(5, 4) == sources.get(1)
            && inventory.findSource(8, 8) == null,
            "lookup returns only inventoried construction origins");
        try {
            sources.clear();
            throw new AssertionError("Expected immutable sources");
        } catch (UnsupportedOperationException expected) {
            // Expected refusal.
        }

        LayeredPackedRegionAuthoredConstructionInventory empty =
            LayeredPackedRegionAuthoredConstructionInventory.empty();
        check(empty.getGeneration() == 0L
            && empty.getSourceCount() == 0
            && empty.getAuthoredConstructionCount() == 0,
            "pre-population inventory is explicitly empty");
    }

    private static void invalidAndCompletedBuildersRefuseMutation() {
        expectIllegal(() ->
            LayeredPackedRegionAuthoredConstructionInventory.builder(0L));
        LayeredPackedRegionAuthoredConstructionInventory.Builder builder =
            LayeredPackedRegionAuthoredConstructionInventory.builder(1L);
        expectNull(() -> builder.record(null, 0, 0));
        expectIllegal(() -> builder.record(kind("SCENERY"), -1, 0));
        builder.record(kind("SCENERY"), 0, 0).build();
        expectState(() -> builder.record(kind("SCENERY"), 0, 0));
        expectState(builder::build);
    }

    private static LayeredPackedRegionAuthoredConstructionInventory
            .ConstructionKind kind(String name) {
        return LayeredPackedRegionAuthoredConstructionInventory
            .ConstructionKind.valueOf(name);
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
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


class LayeredMapsSliceFiftyOneTest(unittest.TestCase):
    def test_count_only_inventory_contract(self):
        with tempfile.TemporaryDirectory(prefix="layered-slice51-") as temp:
            temp_path = Path(temp)
            fixture_path = temp_path / (
                "com/openrsc/server/model/world/coordinate/"
                "AuthoredConstructionInventoryFixture.java"
            )
            fixture_path.parent.mkdir(parents=True)
            fixture_path.write_text(FIXTURE, encoding="utf-8")
            classes = temp_path / "classes"
            classes.mkdir()
            subprocess.run(
                [
                    "javac", "-d", str(classes), str(INVENTORY),
                    str(fixture_path),
                ],
                check=True,
                cwd=ROOT,
            )
            subprocess.run(
                [
                    "java", "-cp", str(classes),
                    "com.openrsc.server.model.world.coordinate."
                    "AuthoredConstructionInventoryFixture",
                ],
                check=True,
                cwd=ROOT,
            )

    def test_world_population_records_only_successful_authored_construction(self):
        inventory = INVENTORY.read_text(encoding="utf-8")
        populator = POPULATOR.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("count-only inventory", inventory)
        self.assertIn("is not a reload manifest", inventory)
        self.assertIn("authoredConstructionInventory", populator)
        self.assertIn("constructionInventory.build()", populator)
        for kind in (
            "ConstructionKind.SCENERY",
            "ConstructionKind.BOUNDARY",
            "ConstructionKind.NPC_SPAWN",
            "ConstructionKind.GROUND_ITEM_SPAWN",
            "ConstructionKind.HARVESTING_SCENERY",
        ):
            self.assertIn(kind, populator)
        self.assertLess(
            populator.index("getWorld().registerGameObject(obj);"),
            populator.index("obj.getType() == 0 ? ConstructionKind.SCENERY"),
        )
        self.assertLess(
            populator.index("getWorld().registerNpc(npc);"),
            populator.index("ConstructionKind.NPC_SPAWN"),
        )
        self.assertIn("if (authoredItem != null)", populator)
        self.assertNotIn(
            "LayeredPackedRegionAuthoredConstructionInventory", manager
        )
        self.assertNotIn(
            "LayeredPackedRegionAuthoredConstructionInventory", observer
        )
        self.assertIn(
            "### Slice 51: Authored packed-source construction inventory",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
