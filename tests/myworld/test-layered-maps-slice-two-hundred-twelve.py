#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SHOP = ROOT / (
    "server/src/com/openrsc/server/event/custom/ShopRestockEvent.java"
)
STAT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/impl/"
    "StatRestorationEvent.java"
)
PLUGIN = ROOT / (
    "server/src/com/openrsc/server/event/rsc/PluginTickEvent.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameEventHandler.java"
)
CORRELATION = ROOT / (
    "server/src/com/openrsc/server/model/world/region/"
    "LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceTwoHundredTwelveTest(unittest.TestCase):
    def test_shop_restock_declares_only_world_level_affinity(self):
        source = SHOP.read_text(encoding="utf-8")
        affinity = source.split(
            "public GameTickEventSpatialAffinity getSpatialAffinity()", 1
        )[1].split("}", 1)[0]
        self.assertIn(
            "GameTickEventSpatialAffinity.nonSpatialGlobal()", affinity
        )
        for forbidden in (
            "getX()", "getY()", "exactFixedLocation", "Region",
            "GameObject", "Npc",
        ):
            self.assertNotIn(forbidden, affinity)

    def test_player_stat_restoration_is_narrowly_non_spatial(self):
        source = STAT.read_text(encoding="utf-8")
        affinity = source.split(
            "public GameTickEventSpatialAffinity getSpatialAffinity()", 1
        )[1].split("}", 1)[0]
        self.assertIn(
            "getOwner() != null && getOwner().isPlayer()", affinity
        )
        self.assertIn(
            "GameTickEventSpatialAffinity.nonSpatialGlobal()", affinity
        )
        self.assertIn("super.getSpatialAffinity()", affinity)
        self.assertLess(
            affinity.index("getOwner().isPlayer()"),
            affinity.index("nonSpatialGlobal()"),
        )

    def test_generic_plugin_ticks_remain_unspecified_and_blocking(self):
        source = PLUGIN.read_text(encoding="utf-8")
        self.assertNotIn(
            "GameTickEventSpatialAffinity", source
        )
        self.assertNotIn("getSpatialAffinity()", source)
        self.assertIn("getPluginTask().doRun()", source)
        self.assertIn("getPlayerOwner().getLastExecutedWalkToAction()", source)

    def test_detached_inventory_keeps_explicit_global_events_out_of_blockers(self):
        handler = HANDLER.read_text(encoding="utf-8")
        correlation = CORRELATION.read_text(encoding="utf-8")
        self.assertIn(
            "case NON_SPATIAL_GLOBAL:\n"
            "\t\t\t\tattribution = AttributionKind.NON_SPATIAL_GLOBAL;",
            handler,
        )
        self.assertIn(
            "== AttributionKind.NON_SPATIAL_GLOBAL",
            correlation,
        )
        self.assertIn(
            "### Slice 212: Narrow non-spatial scheduler affinities",
            PLAN.read_text(encoding="utf-8"),
        )


if __name__ == "__main__":
    unittest.main()
