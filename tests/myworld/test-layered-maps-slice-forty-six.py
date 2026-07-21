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


class LayeredMapsSliceFortySixTest(unittest.TestCase):
    def test_v13_adds_bounded_decision_evidence_and_retains_v12(self):
        v11_path = SCHEMA_ROOT / "layered-map-parity-event-v11.schema.json"
        v12_path = SCHEMA_ROOT / "layered-map-parity-event-v12.schema.json"
        v13_path = SCHEMA_ROOT / "layered-map-parity-event-v13.schema.json"
        v11 = json.loads(v11_path.read_text(encoding="utf-8"))
        v12 = json.loads(v12_path.read_text(encoding="utf-8"))
        v13 = json.loads(v13_path.read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(v12)
        Draft202012Validator.check_schema(v13)
        registry = Registry().with_resources([
            (v11["$id"], Resource.from_contents(v11)),
            (v12["$id"], Resource.from_contents(v12)),
        ])
        Draft202012Validator(v13, registry=registry)

        self.assertEqual(
            "layered-map-parity-event-v12", v12["properties"]["schema"]["const"]
        )
        self.assertEqual(
            "layered-map-parity-event-v13", v13["properties"]["schema"]["const"]
        )
        self.assertNotIn("regionRetirementDecisions", v12["required"])
        expected_required = list(v12["required"])
        expected_required.insert(
            expected_required.index("roundTripExact"),
            "regionRetirementDecisions",
        )
        self.assertEqual(expected_required, v13["required"])
        aggregate = v13["$defs"]["regionRetirementDecisions"]
        entry = v13["$defs"]["regionRetirementDecisionEntry"]
        self.assertFalse(aggregate["additionalProperties"])
        self.assertFalse(entry["additionalProperties"])
        self.assertEqual(4096, aggregate["properties"]["candidateCount"]["maximum"])
        self.assertEqual(4096, aggregate["properties"]["entries"]["maxItems"])
        self.assertEqual(
            [
                "ELIGIBLE", "FOREIGN_PROJECTION", "CANDIDATE_NOT_ELIGIBLE",
                "PINNED", "COOLING_DOWN", "NOT_RESIDENT", "UNSUPPORTED",
                "UNTRACKED", "RELEASE_CHANGED", "RESIDENCY_CHANGED",
            ],
            entry["properties"]["decisionState"]["enum"],
        )

    def test_runtime_wiring_is_bounded_rebindable_and_non_authoritative(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v36"', observer)
        self.assertIn("MAX_TRACE_RETIREMENT_CANDIDATES = 4096", observer)
        self.assertIn("retirementDecisionCandidates", observer)
        self.assertIn("updateRetirementDecisionCandidates(", observer)
        self.assertIn("pruneRefusedRetirementDecisionCandidates(", observer)
        self.assertIn("RegionRetirementDecisionSource", observer)
        self.assertIn('append(",\\\"regionRetirementDecisions\\\":")', observer)
        self.assertIn("evaluateLayeredRegionRetirementCandidates(", manager)
        self.assertIn("layeredRegionRetirementDecisionSource()", player)
        self.assertIn("currentRegionRetirementDecisionSource", observer)
        self.assertIn("layeredRegionRetirementDecisionSource(player)", development)
        self.assertNotIn("LayeredRegionRetirementDecisionArbiter", path_validation)
        self.assertNotIn("getRegion(", observer)
        self.assertNotIn("unregisterPackedRegion", observer)
        self.assertNotIn(".unload(", observer)
        self.assertIn("Refused candidates are reported once", readme)
        self.assertIn(
            "### Slice 46: Private Region retirement decision diagnostics", plan
        )


if __name__ == "__main__":
    unittest.main()
