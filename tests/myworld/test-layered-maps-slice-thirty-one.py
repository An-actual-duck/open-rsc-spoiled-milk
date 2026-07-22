#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ROOT = ROOT / "tools/layered-maps/schema"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
COMMAND = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceThirtyOneTest(unittest.TestCase):
    def test_v7_adds_bounded_neighborhood_summary_and_retains_v6_fields(self):
        v6 = json.loads(
            (SCHEMA_ROOT / "layered-map-parity-event-v6.schema.json").read_text(
                encoding="utf-8"
            )
        )
        v7 = json.loads(
            (SCHEMA_ROOT / "layered-map-parity-event-v7.schema.json").read_text(
                encoding="utf-8"
            )
        )
        self.assertEqual(
            set(v6["required"]) | {"tileNeighborhood"},
            set(v7["required"]),
        )
        for field in v6["required"]:
            self.assertIn(field, v7["properties"])
        sampled = ["start", "marker", "teleport", "stop"]
        self.assertEqual(
            sampled,
            v7["allOf"][0]["if"]["properties"]["eventType"]["enum"],
        )
        then_properties = v7["allOf"][0]["then"]["properties"]
        else_properties = v7["allOf"][0]["else"]["properties"]
        self.assertEqual({"$ref": "#/$defs/tileParity"}, then_properties["tileParity"])
        self.assertEqual(
            {"$ref": "#/$defs/tileNeighborhood"},
            then_properties["tileNeighborhood"],
        )
        self.assertEqual({"type": "null"}, else_properties["tileParity"])
        self.assertEqual({"type": "null"}, else_properties["tileNeighborhood"])
        neighborhood = v7["$defs"]["tileNeighborhood"]
        self.assertEqual(
            {
                "center",
                "cellCount",
                "legacyRepresentableCount",
                "unsupportedCount",
                "packedSourcePresentCount",
                "missingPackedSourceCount",
                "comparableCount",
                "exactCount",
                "complete",
                "exact",
            },
            set(neighborhood["required"]),
        )
        self.assertEqual({"const": 9}, neighborhood["properties"]["cellCount"])
        for name in (
            "legacyRepresentableCount",
            "unsupportedCount",
            "packedSourcePresentCount",
            "missingPackedSourceCount",
            "comparableCount",
            "exactCount",
        ):
            self.assertEqual(9, neighborhood["properties"][name]["maximum"])

        try:
            import jsonschema
        except ImportError:
            jsonschema = None
        if jsonschema is not None:
            jsonschema.Draft202012Validator.check_schema(v7)

    def test_neighborhood_diagnostics_stay_private_bounded_and_read_only(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        command = COMMAND.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v40"', observer)
        self.assertIn("TileNeighborhoodSource tileNeighborhoodSource", observer)
        self.assertIn("TileNeighborhoodMetadata tileNeighborhood = null", observer)
        self.assertIn("state.tileNeighborhoodSource.capture(current)", observer)
        self.assertIn('out.append(",\\\"tileNeighborhood\\\":")', observer)
        capture_block = observer.split(
            "private static boolean capturesTileComparisons", 1
        )[1].split("private static boolean capturesRecentTraversal", 1)[0]
        for event_type in ("start", "marker", "teleport", "stop"):
            self.assertIn(f'"{event_type}".equals(eventType)', capture_block)
        self.assertNotIn('"move".equals(eventType)', capture_block)
        self.assertNotIn('"snapshot".equals(eventType)', capture_block)
        self.assertNotIn('"login".equals(eventType)', capture_block)
        self.assertNotIn('"logout".equals(eventType)', capture_block)
        self.assertIn("layeredTileNeighborhoodSource(player)", command)
        self.assertIn("regionManager.compareLayeredTileNeighborhood(current)", command)
        self.assertNotIn("TileNeighborhoodMetadata", player)
        self.assertNotIn("LayeredTileNeighborhoodParityComparison", player)
        self.assertIn(
            "### Slice 31: Bounded private tile-neighborhood diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
