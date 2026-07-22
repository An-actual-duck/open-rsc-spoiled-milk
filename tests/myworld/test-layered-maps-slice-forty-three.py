#!/usr/bin/env python3
import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_ROOT = ROOT / "tools/layered-maps/schema"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


class LayeredMapsSliceFortyThreeTest(unittest.TestCase):
    def test_v12_adds_bounded_retirement_evidence_and_retains_v11(self):
        v11_path = SCHEMA_ROOT / "layered-map-parity-event-v11.schema.json"
        v12_path = SCHEMA_ROOT / "layered-map-parity-event-v12.schema.json"
        self.assertTrue(v11_path.is_file())
        self.assertTrue(v12_path.is_file())
        v11 = json.loads(v11_path.read_text(encoding="utf-8"))
        v12 = json.loads(v12_path.read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(v11)
        Draft202012Validator.check_schema(v12)
        registry = Registry().with_resource(
            v11["$id"], Resource.from_contents(v11)
        )
        Draft202012Validator(v12, registry=registry)

        self.assertEqual(
            "layered-map-parity-event-v11", v11["properties"]["schema"]["const"]
        )
        self.assertEqual(
            "layered-map-parity-event-v12", v12["properties"]["schema"]["const"]
        )
        self.assertNotIn("regionRetirement", v11["required"])
        expected_required = list(v11["required"])
        expected_required.insert(
            expected_required.index("roundTripExact"), "regionRetirement"
        )
        self.assertEqual(expected_required, v12["required"])
        retirement = v12["$defs"]["regionRetirement"]
        entry = v12["$defs"]["regionRetirementEntry"]
        self.assertFalse(retirement["additionalProperties"])
        self.assertFalse(entry["additionalProperties"])
        self.assertEqual(4096, retirement["properties"]["trackedCandidateCount"]["maximum"])
        self.assertEqual(12288, retirement["properties"]["entries"]["maxItems"])
        self.assertEqual(
            [
                "PINNED", "COOLING_DOWN", "RETIREMENT_ELIGIBLE",
                "NOT_RESIDENT", "UNSUPPORTED", "UNTRACKED",
            ],
            entry["properties"]["state"]["enum"],
        )

    def test_private_wiring_is_bounded_rebindable_and_non_authoritative(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v42"', observer)
        self.assertIn("MAX_TRACE_RETIREMENT_CANDIDATES = 4096", observer)
        self.assertIn("MAX_TRACE_RETIREMENT_REGIONS", observer)
        self.assertIn("updateRetirementCandidates(state, ownershipChange)", observer)
        self.assertIn("state.regionRetirementSource.capture(", observer)
        self.assertIn("pruneCanceledRetirementCandidates", observer)
        self.assertIn('append(",\\\"regionRetirement\\\":")', observer)
        self.assertIn("getLayeredRegionRetirementEligibilitySnapshots(", manager)
        self.assertIn("currentRegionRetirementSource", observer)
        self.assertIn("loggedIn ? layeredRegionRetirementSource() : null", player)
        self.assertIn("layeredRegionRetirementSource(player)", development)
        self.assertNotIn("LayeredRegionRetirementEligibilityLedger", path_validation)

        batch = manager.split(
            "Captures one bounded, same-tick retirement-evidence batch", 1
        )[1].split(
            "Compares one bounded logical interest change", 1
        )[0]
        self.assertNotIn("getRegion(", batch)
        self.assertNotIn(".unload(", batch)
        self.assertNotIn("unregisterPackedRegion", batch)
        self.assertIn("Expiry remains evidence only", readme)
        self.assertIn(
            "### Slice 43: Private Region retirement diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
