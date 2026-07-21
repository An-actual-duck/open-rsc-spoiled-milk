#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
GROUND_ITEM = ROOT / (
    "server/src/com/openrsc/server/model/entity/GroundItem.java"
)
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
FUNCTIONS = ROOT / "server/src/com/openrsc/server/plugins/Functions.java"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceNinetyTest(unittest.TestCase):
    def test_only_fixed_world_object_callbacks_declare_exact_affinity(self):
        world = WORLD.read_text(encoding="utf-8")
        remove_start = world.index("public void delayedRemoveObject(")
        spawn_start = world.index("public void delayedSpawnObject(", remove_start)
        remove = world[remove_start:spawn_start]
        spawn_end = world.index("public Npc getNpc(", spawn_start)
        spawn = world[spawn_start:spawn_end]
        declaration = "GameTickEventSpatialAffinity.exactFixedLocation("
        self.assertEqual(1, remove.count(declaration))
        self.assertIn("object.getX(), object.getY()", remove)
        self.assertEqual(1, spawn.count(declaration))
        self.assertIn("loc.getX(), loc.getY()", spawn)
        self.assertEqual(2, world.count(declaration))

    def test_other_spatial_callbacks_remain_unclassified(self):
        declaration = "getSpatialAffinity()"
        self.assertNotIn(declaration, GROUND_ITEM.read_text(encoding="utf-8"))
        self.assertNotIn(declaration, NPC.read_text(encoding="utf-8"))
        self.assertNotIn(declaration, FUNCTIONS.read_text(encoding="utf-8"))

    def test_exact_affinity_remains_disconnected_from_private_diagnostics(self):
        self.assertNotIn(
            "captureLayeredPackedRegionEventOwnershipInventory(",
            OBSERVER.read_text(encoding="utf-8"),
        )

    def test_living_plan_records_slice_ninety_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 90: Exact fixed-location scenery event affinity", plan
        )
        self.assertIn("delayed scenery removal", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
