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


class LayeredMapsSliceThirtyFiveTest(unittest.TestCase):
    def test_v9_adds_bounded_recent_traversal_and_retains_v8_fields(self):
        v8 = json.loads(
            (SCHEMA_ROOT / "layered-map-parity-event-v8.schema.json").read_text(
                encoding="utf-8"
            )
        )
        v9 = json.loads(
            (SCHEMA_ROOT / "layered-map-parity-event-v9.schema.json").read_text(
                encoding="utf-8"
            )
        )
        self.assertEqual(
            set(v8["required"]) | {"recentTraversal"},
            set(v9["required"]),
        )
        for field in v8["required"]:
            self.assertIn(field, v9["properties"])
        self.assertEqual(
            {"marker", "stop"},
            set(v9["allOf"][1]["if"]["properties"]["eventType"]["enum"]),
        )
        self.assertEqual(
            {"type": "null"},
            v9["allOf"][1]["else"]["properties"]["recentTraversal"],
        )
        traversal = v9["$defs"]["recentTraversal"]
        self.assertEqual(16, traversal["properties"]["stepCount"]["maximum"])
        self.assertEqual(16, traversal["properties"]["steps"]["maxItems"])
        self.assertEqual(1, traversal["properties"]["steps"]["minItems"])
        step = v9["$defs"]["traversalStep"]
        for field in (
            "index", "source", "offset", "destination",
            "logicalPassable", "packedPassable", "logicalBlockingReason",
            "packedBlockingReason", "passabilityExact", "blockingReasonExact",
        ):
            self.assertIn(field, step["required"])

        try:
            import jsonschema
        except ImportError:
            jsonschema = None
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(v9)

    def test_recent_traversal_stays_private_bounded_and_non_authoritative(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        command = COMMAND.read_text(encoding="utf-8")
        manager = MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v45"', observer)
        self.assertIn("MAX_TRACE_TRAVERSAL_STEPS = 16", observer)
        self.assertIn("TraversalCollisionSource traversalCollisionSource", observer)
        self.assertIn("state.traversalCollisionSource.capture(", observer)
        self.assertIn('out.append(",\\\"recentTraversal\\\":")', observer)
        capture_block = observer.split(
            "private static boolean capturesRecentTraversal", 1
        )[1].split("private static boolean capturesRegionResidency", 1)[0]
        self.assertIn('"marker".equals(eventType)', capture_block)
        self.assertIn('"stop".equals(eventType)', capture_block)
        for event_type in ("start", "move", "teleport", "snapshot", "login", "logout"):
            self.assertNotIn(f'"{event_type}".equals(eventType)', capture_block)
        self.assertIn("state.recentTraversal.remove(0)", observer)
        self.assertIn("state.recentTraversalDroppedStepCount++", observer)
        self.assertIn("state.recentTraversalDiscontinuityCount++", observer)
        self.assertIn("layeredTraversalCollisionSource(player)", command)
        self.assertIn("regionManager.compareLayeredTraversalCollision(route)", command)
        self.assertIn(
            "compareLayeredTraversalCollision(\n\t\tfinal List<WorldLocation> route)",
            manager,
        )
        self.assertNotIn("RecentTraversalMetadata", path_validation)
        self.assertNotIn("RecentTraversalMetadata", player)
        self.assertNotIn("recentTraversal", player)
        self.assertIn(
            "### Slice 35: Bounded private recent-traversal diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
