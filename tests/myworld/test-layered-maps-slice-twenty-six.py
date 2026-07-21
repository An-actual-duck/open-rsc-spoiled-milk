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


class LayeredMapsSliceTwentySixTest(unittest.TestCase):
    def test_v5_schema_adds_bounded_tile_snapshot_metadata_and_retains_lineage(self):
        schemas = {
            version: json.loads(
                (SCHEMA_ROOT / f"layered-map-parity-event-v{version}.schema.json").read_text(
                    encoding="utf-8"
                )
            )
            for version in (1, 2, 3, 4, 5, 6)
        }
        for version, schema in schemas.items():
            self.assertEqual(
                f"layered-map-parity-event-v{version}",
                schema["properties"]["schema"]["const"],
            )

        self.assertNotIn("tileSnapshot", schemas[4]["required"])
        v5 = schemas[5]
        self.assertIn("tileSnapshot", v5["required"])
        self.assertEqual(
            {
                "logicalRegion",
                "sourceFragmentCount",
                "missingSourceRegionCount",
                "supportedTileCount",
                "targetTileCount",
                "complete",
                "fingerprint",
            },
            set(v5["$defs"]["tileSnapshot"]["required"]),
        )
        self.assertEqual(2304, v5["$defs"]["tileSnapshot"]["properties"]["targetTileCount"]["const"])
        self.assertEqual(
            "^[0-9a-f]{64}$",
            v5["$defs"]["tileSnapshot"]["properties"]["fingerprint"]["pattern"],
        )
        v6 = schemas[6]
        self.assertNotIn("tileParity", v5["required"])
        self.assertIn("tileParity", v6["required"])
        self.assertEqual(
            {
                "logicalLocation",
                "legacyPackedAddress",
                "legacyRepresentable",
                "packedSourcePresent",
                "missingPackedSource",
                "comparable",
                "exact",
            },
            set(v6["$defs"]["tileParity"]["required"]),
        )

        try:
            import jsonschema
        except ImportError:
            jsonschema = None
        if jsonschema is not None:
            for schema in schemas.values():
                jsonschema.Draft202012Validator.check_schema(schema)

    def test_snapshot_capture_is_bound_only_to_the_private_observer(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        command = COMMAND.read_text(encoding="utf-8")
        manager = MANAGER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v35"', observer)
        self.assertIn("public interface TileSnapshotSource", observer)
        self.assertIn("public static final class TileSnapshotMetadata", observer)
        self.assertIn("state.tileSnapshotSource.capture(to.getRegionKey())", observer)
        self.assertIn('out.append(",\\\"tileSnapshot\\\":")', observer)
        self.assertIn("appendTileSnapshot(out, tileSnapshot)", observer)
        self.assertIn("public interface TileParitySource", observer)
        self.assertIn("public static final class TileParityMetadata", observer)
        self.assertIn("state.tileParitySource.capture(current)", observer)
        self.assertIn('out.append(",\\\"tileParity\\\":")', observer)
        self.assertIn("layeredTileSnapshotSource(player)", command)
        self.assertIn("layeredTileParitySource(player)", command)
        self.assertIn("regionManager.getLayeredRegionTileSnapshot(logicalRegionKey)", command)
        self.assertIn("regionManager.compareLayeredTileState(current)", command)
        self.assertIn(
            "public LayeredRegionTileSnapshot getLayeredRegionTileSnapshot(",
            manager,
        )
        self.assertNotIn("LayeredRegionTileSnapshot", player)
        self.assertNotIn("TileSnapshotMetadata", player)
        self.assertNotIn("TileParityMetadata", player)
        self.assertIn(
            "### Slice 26: Private logical-region tile-snapshot diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
