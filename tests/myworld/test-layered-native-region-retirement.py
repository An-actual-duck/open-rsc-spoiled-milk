#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
ENTITY = SERVER / "src/com/openrsc/server/model/entity/Entity.java"
GROUND_ITEM = SERVER / "src/com/openrsc/server/model/entity/GroundItem.java"
REGION_MANAGER = (
    SERVER
    / "src/com/openrsc/server/model/world/region/RegionManager.java"
)
PATH_VALIDATION = SERVER / "src/com/openrsc/server/model/PathValidation.java"
WALKING_QUEUE = SERVER / "src/com/openrsc/server/model/WalkingQueue.java"
PLAYER = SERVER / "src/com/openrsc/server/model/entity/player/Player.java"
SCRIPT_CONTEXT = (
    SERVER / "src/com/openrsc/server/model/entity/player/ScriptContext.java"
)
GROUND_ITEM_TAKE = (
    SERVER / "src/com/openrsc/server/net/rsc/handlers/GroundItemTake.java"
)
ITEM_USE = (
    SERVER
    / "src/com/openrsc/server/net/rsc/handlers/ItemUseOnGroundItem.java"
)
DEVELOPMENT = (
    SERVER
    / "plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PLAN = (
    ROOT
    / "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
ROADMAP = (
    ROOT
    / "docs/myworld/in-progress-work-plans/remaster-suite-roadmap.md"
)


class LayeredNativeRegionRetirementTest(unittest.TestCase):
    def test_native_entities_use_only_exact_layered_membership(self):
        entity = ENTITY.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")

        self.assertIn(
            "usesNativeLayeredRegionlessMembership(\n"
            "\t\t\t\t\t\tgetWorldLocation())",
            entity,
        )
        self.assertIn("region.set(null);", entity)
        self.assertIn(
            "Native layered entity unexpectedly occupies a packed Region",
            entity,
        )
        self.assertIn(
            "public boolean usesNativeLayeredRegionlessMembership",
            manager,
        )
        self.assertIn("requireEntitySpatialCarrier", manager)
        self.assertIn(
            '"Native layered entity occupies a packed Region"',
            manager,
        )

    def test_native_reachable_lookups_do_not_require_entity_region(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        callers = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (
                PATH_VALIDATION,
                WALKING_QUEUE,
                PLAYER,
                SCRIPT_CONTEXT,
                GROUND_ITEM_TAKE,
                ITEM_USE,
            )
        )

        for helper in (
            "findInteractionScenery",
            "findInteractionBoundary",
            "findInteractionNpc",
            "findInteractionPlayer",
            "findInteractionGroundItem",
        ):
            self.assertIn(helper, manager)
            self.assertIn(helper, manager + callers)
        for packed_facade in (
            "getRegion().getItem",
            "getRegion().getGameObject",
            "getRegion().getWallGameObject",
            "getRegion().getPlayer",
        ):
            self.assertNotIn(packed_facade, callers)

    def test_owner_scoped_runtime_items_preserve_signed_domain(self):
        ground_item = GROUND_ITEM.read_text(encoding="utf-8")
        self.assertIn("trySetLocation(Point.location(x, y), owner)", ground_item)
        self.assertIn(
            "WorldLocation ownerLocation =\n"
            "\t\t\t\t\tspatialOwner.getWorldLocation();",
            ground_item,
        )
        self.assertIn(
            "ownerLocation.getCoordinate().getLevel()",
            ground_item,
        )
        self.assertIn("updateRegion();", ground_item)

    def test_private_diagnostics_and_plans_expose_the_boundary(self):
        development = DEVELOPMENT.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        roadmap = ROADMAP.read_text(encoding="utf-8")

        self.assertIn("spatialCarrier=", development)
        self.assertIn("; packedRegion=detached", development)
        self.assertIn("runtime checkpoint 13", plan)
        self.assertIn("packed-Region entity carrier", plan)
        self.assertIn("Region-free native runtime", roadmap)

    def test_same_coordinate_scope_change_forces_terrain_rebuild(self):
        client = CLIENT.read_text(encoding="utf-8")
        same_loaded_region = (
            "if (!hardAreaLoad\n"
            "\t\t\t\t\t&& this.hasCompletedInitialRegionLoad\n"
            "\t\t\t\t\t&& this.lastHeightOffset == this.requestedPlane"
        )
        self.assertIn(same_loaded_region, client)
        self.assertNotIn(
            "if (this.hasCompletedInitialRegionLoad\n"
            "\t\t\t\t\t&& this.lastHeightOffset == this.requestedPlane",
            client,
        )


if __name__ == "__main__":
    unittest.main()
