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


class LayeredMapsSliceEighteenTest(unittest.TestCase):
    def test_v3_schema_adds_bounded_interest_delta_and_retains_lineage(self):
        schemas = {
            version: json.loads(
                (SCHEMA_ROOT / f"layered-map-parity-event-v{version}.schema.json").read_text(
                    encoding="utf-8"
                )
            )
            for version in (1, 2, 3)
        }
        for version, schema in schemas.items():
            self.assertEqual(
                f"layered-map-parity-event-v{version}",
                schema["properties"]["schema"]["const"],
            )

        v3 = schemas[3]
        self.assertIn("interestDelta", v3["required"])
        interest = v3["$defs"]["interestDelta"]
        self.assertEqual(
            {
                "previousRegionCount",
                "currentRegionCount",
                "enteredCount",
                "retainedCount",
                "exitedCount",
                "worldSpaceChanged",
                "levelChanged",
                "noOp",
                "enteredKeys",
                "exitedKeys",
            },
            set(interest["required"]),
        )
        self.assertEqual(
            {"worldSpace", "level", "x", "y"},
            set(v3["$defs"]["regionKey"]["required"]),
        )

        try:
            import jsonschema
        except ImportError:
            jsonschema = None
        if jsonschema is not None:
            for schema in schemas.values():
                jsonschema.Draft202012Validator.check_schema(schema)

    def test_interest_delta_remains_private_observer_only(self):
        observer = OBSERVER_SOURCE.read_text(encoding="utf-8")
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v18"', observer)
        self.assertIn("MAX_TRACE_REGIONS_PER_WINDOW = 4096", observer)
        self.assertIn("WorldRegionInterestDelta.between(", observer)
        self.assertIn('out.append(",\\\"interestDelta\\\":")', observer)
        self.assertIn("appendRegionKeys(out, delta.getEntered())", observer)
        self.assertIn("appendRegionKeys(out, delta.getExited())", observer)
        self.assertIn("compareLayeredRegionInterestResidency(", manager)
        self.assertNotIn("WorldRegionInterestDelta", player)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertIn("visibleRegionWindowCache.putIfAbsent", manager)
        self.assertIn("### Slice 18: Private logical interest-delta diagnostics", plan)


if __name__ == "__main__":
    unittest.main()
