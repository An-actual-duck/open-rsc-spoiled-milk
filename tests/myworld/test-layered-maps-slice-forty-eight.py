#!/usr/bin/env python3
import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ROOT = ROOT / "tools/layered-maps/schema"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
READINESS = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionRetirementReadiness.java"
)
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceFortyEightTest(unittest.TestCase):
    def test_v14_adds_closed_packed_source_readiness_and_retains_v13(self):
        schemas = {}
        for version in (11, 12, 13, 14):
            path = SCHEMA_ROOT / f"layered-map-parity-event-v{version}.schema.json"
            schemas[version] = json.loads(path.read_text(encoding="utf-8"))
            Draft202012Validator.check_schema(schemas[version])
        registry = Registry().with_resources([
            (schemas[version]["$id"], Resource.from_contents(schemas[version]))
            for version in (11, 12, 13)
        ])
        Draft202012Validator(schemas[14], registry=registry)

        self.assertEqual(
            "layered-map-parity-event-v13",
            schemas[13]["properties"]["schema"]["const"],
        )
        self.assertEqual(
            "layered-map-parity-event-v14",
            schemas[14]["properties"]["schema"]["const"],
        )
        self.assertNotIn("packedRegionRetirementReadiness", schemas[13]["required"])
        expected_required = list(schemas[13]["required"])
        expected_required.insert(
            expected_required.index("roundTripExact"),
            "packedRegionRetirementReadiness",
        )
        self.assertEqual(expected_required, schemas[14]["required"])

        aggregate = schemas[14]["$defs"]["packedRegionRetirementReadiness"]
        entry = schemas[14]["$defs"]["packedRegionRetirementReadinessEntry"]
        self.assertFalse(aggregate["additionalProperties"])
        self.assertFalse(entry["additionalProperties"])
        self.assertEqual(4096, aggregate["properties"]["logicalDecisionCount"]["maximum"])
        self.assertEqual(8192, aggregate["properties"]["sourceCount"]["maximum"])
        self.assertEqual(8192, aggregate["properties"]["entries"]["maxItems"])
        self.assertEqual(2, entry["properties"]["coveredLogicalRegions"]["maxItems"])
        self.assertEqual(
            [
                "READY", "INCOMPLETE_COVERAGE", "REFUSED_COVERAGE",
                "PARTIAL_RESIDENCY", "PARTIAL_LEGACY_DOMAIN",
            ],
            entry["properties"]["sourceState"]["enum"],
        )

    def test_observer_serializes_same_decision_batch_without_lifecycle_authority(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        readiness = READINESS.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v31"', observer)
        self.assertIn("MAX_TRACE_PACKED_RETIREMENT_SOURCES", observer)
        self.assertIn('append(",\\\"packedRegionRetirementReadiness\\\":")', observer)
        self.assertIn("appendPackedRegionRetirementReadiness(", observer)
        self.assertIn("getPackedSourceReadiness()", observer)
        self.assertIn(
            "LayeredPackedRegionRetirementReadiness.fromDecisions(", observer
        )
        self.assertNotIn(
            "prepareLayeredPackedRegionRetirementReadiness(", observer
        )
        self.assertIn(
            "prepareLayeredPackedRegionRetirementReadiness(", manager
        )
        self.assertNotIn("LayeredPackedRegionRetirementReadiness", path_validation)
        self.assertNotIn(
            "com.openrsc.server.model.world.region.Region", readiness
        )
        self.assertNotIn("getRegion(", observer)
        self.assertNotIn("unregisterPackedRegion", observer)
        self.assertNotIn(".unload(", observer)
        self.assertIn("layered-map-parity-event-v14.schema.json", readme)
        self.assertIn(
            "### Slice 48: Private packed-source retirement readiness diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
