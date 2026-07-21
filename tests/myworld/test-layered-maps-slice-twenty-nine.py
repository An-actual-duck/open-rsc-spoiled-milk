#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ROOT = ROOT / "tools/layered-maps/schema"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
COMMAND = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceTwentyNineTest(unittest.TestCase):
    def test_v6_adds_bounded_current_tile_parity_and_retains_v5_fields(self):
        v5 = json.loads(
            (SCHEMA_ROOT / "layered-map-parity-event-v5.schema.json").read_text(
                encoding="utf-8"
            )
        )
        v6 = json.loads(
            (SCHEMA_ROOT / "layered-map-parity-event-v6.schema.json").read_text(
                encoding="utf-8"
            )
        )
        self.assertEqual(
            set(v5["required"]) | {"tileParity"},
            set(v6["required"]),
        )
        for field in v5["required"]:
            self.assertIn(field, v6["properties"])
        self.assertEqual(
            ["start", "marker", "teleport", "stop"],
            v6["allOf"][0]["if"]["properties"]["eventType"]["enum"],
        )
        self.assertEqual(
            {"type": "null"},
            v6["allOf"][0]["else"]["properties"]["tileParity"],
        )

        try:
            import jsonschema
        except ImportError:
            jsonschema = None
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(v6)

    def test_current_tile_parity_stays_private_bounded_and_read_only(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        command = COMMAND.read_text(encoding="utf-8")
        manager = MANAGER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v33"', observer)
        self.assertIn('"start".equals(eventType)', observer)
        self.assertIn('"marker".equals(eventType)', observer)
        self.assertIn('"teleport".equals(eventType)', observer)
        self.assertIn('"stop".equals(eventType)', observer)
        capture_block = observer.split(
            "private static boolean capturesTileComparisons", 1
        )[1].split("private static boolean capturesRecentTraversal", 1)[0]
        self.assertNotIn('"move".equals(eventType)', capture_block)
        self.assertNotIn('"login".equals(eventType)', capture_block)
        self.assertNotIn('"logout".equals(eventType)', capture_block)
        self.assertIn("layeredTileParitySource(player)", command)
        self.assertIn("regionManager.compareLayeredTileState(current)", command)
        self.assertIn("peekRegionFromSectorCoordinates(", manager)
        self.assertNotIn("TileParityMetadata", player)
        self.assertNotIn("LayeredTileStateParityComparison", player)
        self.assertIn(
            "### Slice 29: Bounded private current-tile parity diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
