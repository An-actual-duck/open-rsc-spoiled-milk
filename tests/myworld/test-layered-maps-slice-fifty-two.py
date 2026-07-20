#!/usr/bin/env python3
import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ROOT = ROOT / "tools/layered-maps/schema"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
)
OBSERVATION = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionAuthoredConstructionObservation.java"
)
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceFiftyTwoTest(unittest.TestCase):
    def test_v16_adds_closed_origin_counts_and_retains_v15(self):
        schemas = {}
        for version in (11, 12, 13, 14, 15, 16):
            path = SCHEMA_ROOT / f"layered-map-parity-event-v{version}.schema.json"
            schemas[version] = json.loads(path.read_text(encoding="utf-8"))
            Draft202012Validator.check_schema(schemas[version])
        registry = Registry().with_resources([
            (schemas[version]["$id"], Resource.from_contents(schemas[version]))
            for version in (11, 12, 13, 14, 15)
        ])
        Draft202012Validator(schemas[16], registry=registry)

        self.assertEqual(
            "layered-map-parity-event-v15",
            schemas[15]["properties"]["schema"]["const"],
        )
        self.assertEqual(
            "layered-map-parity-event-v16",
            schemas[16]["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "packedRegionAuthoredConstruction", schemas[15]["required"]
        )
        expected_required = list(schemas[15]["required"])
        expected_required.insert(
            expected_required.index("roundTripExact"),
            "packedRegionAuthoredConstruction",
        )
        self.assertEqual(expected_required, schemas[16]["required"])

        aggregate = schemas[16]["$defs"]["packedRegionAuthoredConstruction"]
        entry = schemas[16]["$defs"]["packedRegionAuthoredConstructionEntry"]
        self.assertFalse(aggregate["additionalProperties"])
        self.assertFalse(entry["additionalProperties"])
        self.assertEqual(8192, aggregate["properties"]["sourceCount"]["maximum"])
        self.assertEqual(8192, aggregate["properties"]["entries"]["maxItems"])
        self.assertEqual(True, aggregate["properties"]["originCountsOnly"]["const"])
        self.assertEqual(
            False, aggregate["properties"]["reconstructionManifest"]["const"]
        )
        self.assertEqual(
            {
                "packedRegionX", "packedRegionY", "sceneryCount",
                "boundaryCount", "npcSpawnCount", "groundItemSpawnCount",
                "harvestingSceneryCount", "authoredConstructionCount",
            },
            set(entry["required"]),
        )

    def test_runtime_projects_exact_safety_sources_without_lifecycle_authority(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        observation = OBSERVATION.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v16"', observer)
        self.assertIn('append(",\\"packedRegionAuthoredConstruction\\":")', observer)
        self.assertIn("PackedRegionAuthoredConstructionSource", observer)
        self.assertIn("appendPackedRegionAuthoredConstruction(", observer)
        self.assertIn("originCountsOnly\\\":true", observer)
        self.assertIn("reconstructionManifest\\\":false", observer)
        self.assertIn("checkedSafety.getSources()", observation)
        self.assertIn("checkedInventory.findSource(", observation)
        self.assertIn("Collections.unmodifiableList(sources)", observation)
        self.assertIn(
            "layeredPackedRegionAuthoredConstructionSource(player)", development
        )
        self.assertIn(
            "layeredPackedRegionAuthoredConstructionSource()", player
        )
        self.assertIn("getAuthoredConstructionInventory()", development)
        self.assertIn("getAuthoredConstructionInventory()", player)
        self.assertNotIn("com.openrsc.server.model.world.region.Region", observation)
        self.assertNotIn("TileValue", observation)
        self.assertIn("layered-map-parity-event-v16.schema.json", readme)
        self.assertIn(
            "### Slice 52: Private authored-construction origin diagnostics",
            plan,
        )

        serialization = observer.split(
            "private static void appendPackedRegionAuthoredConstruction(", 1
        )[1].split(
            "private static void appendRegionResidencyCandidates(", 1
        )[0]
        for forbidden in (
            "getRegion(", "unregister", ".unload(", "regions.remove",
            "registerGameObject", "registerNpc", "registerItem",
        ):
            self.assertNotIn(forbidden, serialization)


if __name__ == "__main__":
    unittest.main()
