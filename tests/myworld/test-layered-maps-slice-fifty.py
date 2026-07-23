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
ASSESSMENT = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionRetirementSafetyAssessment.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceFiftyTest(unittest.TestCase):
    def test_v15_adds_closed_packed_source_safety_and_retains_v14(self):
        schemas = {}
        for version in (11, 12, 13, 14, 15):
            path = SCHEMA_ROOT / f"layered-map-parity-event-v{version}.schema.json"
            schemas[version] = json.loads(path.read_text(encoding="utf-8"))
            Draft202012Validator.check_schema(schemas[version])
        registry = Registry().with_resources([
            (schemas[version]["$id"], Resource.from_contents(schemas[version]))
            for version in (11, 12, 13, 14)
        ])
        Draft202012Validator(schemas[15], registry=registry)

        self.assertEqual(
            "layered-map-parity-event-v14",
            schemas[14]["properties"]["schema"]["const"],
        )
        self.assertEqual(
            "layered-map-parity-event-v15",
            schemas[15]["properties"]["schema"]["const"],
        )
        self.assertNotIn("packedRegionRetirementSafety", schemas[14]["required"])
        expected_required = list(schemas[14]["required"])
        expected_required.insert(
            expected_required.index("roundTripExact"),
            "packedRegionRetirementSafety",
        )
        self.assertEqual(expected_required, schemas[15]["required"])
        self.assertEqual(
            "layered-map-parity-event-v14.schema.json#/$defs/"
            "packedRegionRetirementReadiness",
            schemas[15]["properties"]["packedRegionRetirementReadiness"]
            ["oneOf"][0]["$ref"],
        )

        aggregate = schemas[15]["$defs"]["packedRegionRetirementSafety"]
        entry = schemas[15]["$defs"]["packedRegionRetirementSafetyEntry"]
        self.assertFalse(aggregate["additionalProperties"])
        self.assertFalse(entry["additionalProperties"])
        self.assertEqual(8192, aggregate["properties"]["sourceCount"]["maximum"])
        self.assertEqual(8192, aggregate["properties"]["entries"]["maxItems"])
        self.assertEqual(
            [
                "READY", "INCOMPLETE_COVERAGE", "REFUSED_COVERAGE",
                "PARTIAL_RESIDENCY", "PARTIAL_LEGACY_DOMAIN",
            ],
            entry["properties"]["readinessState"]["enum"],
        )
        self.assertEqual(
            [
                "READINESS_NOT_READY", "SOURCE_NOT_RESIDENT",
                "TILE_STORAGE_UNAVAILABLE", "PLAYERS_PRESENT", "NPCS_PRESENT",
                "OBJECTS_PRESENT", "GROUND_ITEMS_PRESENT",
                "RELOAD_PATH_UNAVAILABLE",
            ],
            entry["properties"]["blockers"]["items"]["enum"],
        )

    def test_runtime_captures_exact_readiness_without_lifecycle_authority(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        assessment = ASSESSMENT.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v45"', observer)
        self.assertIn('append(",\\"packedRegionRetirementSafety\\":")', observer)
        self.assertIn("PackedRegionRetirementSafetySource", observer)
        self.assertIn("appendPackedRegionRetirementSafety(", observer)
        self.assertIn(
            "regionRetirementDecisions.getPackedSourceReadiness()", observer
        )
        self.assertIn(
            "assessLayeredPackedRegionRetirementSafety(", manager
        )
        self.assertIn("layeredPackedRegionRetirementSafetySource(player)", development)
        self.assertIn("layeredPackedRegionRetirementSafetySource()", player)
        self.assertIn(
            ".assessLayeredPackedRegionRetirementSafety(", development
        )
        self.assertIn(
            ".assessLayeredPackedRegionRetirementSafety(", player
        )
        self.assertNotIn("LayeredPackedRegionRetirementSafetyAssessment", path_validation)
        self.assertNotIn(
            "com.openrsc.server.model.world.region.Region", assessment
        )
        self.assertIn("layered-map-parity-event-v15.schema.json", readme)
        self.assertIn(
            "### Slice 50: Private packed-source contents safety diagnostics",
            plan,
        )

        serialization = observer.split(
            "private static void appendPackedRegionRetirementSafety(", 1
        )[1].split(
            "private static void appendRegionResidencyCandidates(", 1
        )[0]
        self.assertNotIn("getRegion(", serialization)
        self.assertNotIn("unregisterPackedRegion", serialization)
        self.assertNotIn(".unload(", serialization)
        self.assertNotIn("regions.remove", serialization)


if __name__ == "__main__":
    unittest.main()
