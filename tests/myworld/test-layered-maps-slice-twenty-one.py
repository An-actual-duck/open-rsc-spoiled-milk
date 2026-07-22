#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER_SOURCE = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
REGION_MANAGER_SOURCE = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PLAYER_SOURCE = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"
SCHEMA_ROOT = ROOT / "tools/layered-maps/schema"


class LayeredMapsSliceTwentyOneTest(unittest.TestCase):
    def test_v4_schema_adds_bounded_packed_coverage_and_retains_lineage(self):
        schemas = {
            version: json.loads(
                (SCHEMA_ROOT / f"layered-map-parity-event-v{version}.schema.json").read_text(
                    encoding="utf-8"
                )
            )
            for version in (1, 2, 3, 4)
        }
        for version, schema in schemas.items():
            self.assertEqual(
                f"layered-map-parity-event-v{version}",
                schema["properties"]["schema"]["const"],
            )

        v4 = schemas[4]
        self.assertIn("packedCoverage", v4["required"])
        coverage = v4["$defs"]["packedCoverage"]
        self.assertEqual(
            {
                "minPackedRegionX",
                "minPackedRegionY",
                "maxPackedRegionX",
                "maxPackedRegionY",
                "packedCellCount",
                "unsupportedPackedCellCount",
                "expectedKeyCount",
                "packedCoverageKeyCount",
                "missingKeyCount",
                "extraKeyCount",
                "exact",
                "missingKeys",
                "extraKeys",
            },
            set(coverage["required"]),
        )

        try:
            import jsonschema
        except ImportError:
            jsonschema = None
        if jsonschema is not None:
            for schema in schemas.values():
                jsonschema.Draft202012Validator.check_schema(schema)

    def test_coverage_comparison_remains_private_observer_only(self):
        observer = OBSERVER_SOURCE.read_text(encoding="utf-8")
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v40"', observer)
        self.assertIn("MAX_TRACE_PACKED_CELLS = 4096", observer)
        self.assertIn("LegacyPackedVisibilityCoverageComparison.compare(", observer)
        self.assertIn('out.append(",\\\"packedCoverage\\\":")', observer)
        self.assertIn("appendRegionKeys(out, coverage.getMissingLogicalKeys())", observer)
        self.assertIn("appendRegionKeys(out, coverage.getExtraPackedCoverageKeys())", observer)
        self.assertNotIn("LegacyPackedVisibilityCoverageComparison", player)
        self.assertNotIn("compareLayeredVisibleRegionCoverage(player", manager)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertIn("visibleRegionWindowCache.putIfAbsent", manager)
        self.assertIn("### Slice 21: Private packed/logical coverage diagnostics", plan)


if __name__ == "__main__":
    unittest.main()
