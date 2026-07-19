#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ROOT = ROOT / "tools/layered-maps/schema"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
COMMAND = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceThirtyThreeTest(unittest.TestCase):
    def test_v8_adds_bounded_adjacent_collision_and_retains_v7_fields(self):
        v7 = json.loads(
            (SCHEMA_ROOT / "layered-map-parity-event-v7.schema.json").read_text(
                encoding="utf-8"
            )
        )
        v8 = json.loads(
            (SCHEMA_ROOT / "layered-map-parity-event-v8.schema.json").read_text(
                encoding="utf-8"
            )
        )
        self.assertEqual(
            set(v7["required"]) | {"adjacentCollision"},
            set(v8["required"]),
        )
        for field in v7["required"]:
            self.assertIn(field, v8["properties"])
        sampled = ["start", "marker", "teleport", "stop"]
        conditional = v8["allOf"][0]
        self.assertEqual(
            sampled,
            conditional["if"]["properties"]["eventType"]["enum"],
        )
        self.assertEqual(
            {"$ref": "#/$defs/adjacentCollision"},
            conditional["then"]["properties"]["adjacentCollision"],
        )
        self.assertEqual(
            {"type": "null"},
            conditional["else"]["properties"]["adjacentCollision"],
        )
        collision = v8["$defs"]["adjacentCollision"]
        self.assertEqual({"const": 8}, collision["properties"]["directionCount"])
        directions = collision["properties"]["directions"]
        self.assertEqual(8, directions["minItems"])
        self.assertEqual(8, directions["maxItems"])
        direction = v8["$defs"]["adjacentDirection"]
        self.assertIn("logicalPassable", direction["required"])
        self.assertIn("packedBlockingReason", direction["required"])
        self.assertIn("DIAGONAL_PASS_THROUGH", v8["$defs"]["blockingReason"]["enum"])

        try:
            import jsonschema
        except ImportError:
            jsonschema = None
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(v8)

    def test_v8_collision_capture_stays_private_bounded_and_non_authoritative(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        command = COMMAND.read_text(encoding="utf-8")
        manager = MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v12"', observer)
        self.assertIn("AdjacentCollisionSource adjacentCollisionSource", observer)
        self.assertIn("AdjacentCollisionMetadata adjacentCollision = null", observer)
        self.assertIn("state.adjacentCollisionSource.capture(current)", observer)
        self.assertIn('out.append(",\\\"adjacentCollision\\\":")', observer)
        capture_block = observer.split(
            "private static boolean capturesTileComparisons", 1
        )[1].split("private static boolean capturesRecentTraversal", 1)[0]
        for event_type in ("start", "marker", "teleport", "stop"):
            self.assertIn(f'"{event_type}".equals(eventType)', capture_block)
        for event_type in ("move", "snapshot", "login", "logout"):
            self.assertNotIn(f'"{event_type}".equals(eventType)', capture_block)
        self.assertIn("layeredAdjacentCollisionSource(player)", command)
        self.assertIn("regionManager.compareLayeredAdjacentStepCollisions(current)", command)
        batch_block = manager.split(
            "compareLayeredAdjacentStepCollisions(final WorldLocation logicalCenter)",
            1,
        )[1].split("private LayeredTileStateParityComparison", 1)[0]
        self.assertEqual(1, batch_block.count("compareLayeredTileNeighborhood(logicalCenter)"))
        self.assertNotIn("LayeredAdjacentStepCollisionComparison", path_validation)
        self.assertNotIn("AdjacentCollisionMetadata", player)
        self.assertNotIn("AdjacentDirectionMetadata", player)
        self.assertIn(
            "### Slice 33: Bounded private adjacent-collision diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
