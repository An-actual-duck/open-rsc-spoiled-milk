#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
COMMAND = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/"
    "Development.java"
)
SCHEMA_V29 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v29.schema.json"
)
SCHEMA_V30 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v30.schema.json"
)
OBSERVER_FIXTURE_TEST = ROOT / (
    "tests/myworld/test-layered-maps-slice-eleven.py"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceEightyFiveTest(unittest.TestCase):
    def test_v30_contract_adds_bounded_preservation_burden(self):
        v29 = json.loads(SCHEMA_V29.read_text(encoding="utf-8"))
        v30 = json.loads(SCHEMA_V30.read_text(encoding="utf-8"))
        field = "packedRegionPreservationBurden"

        self.assertNotIn(field, v29["properties"])
        self.assertEqual(
            "layered-map-parity-event-v30",
            v30["properties"]["schema"]["const"],
        )
        self.assertIn(field, v30["required"])
        burden = v30["$defs"]["preservationBurden"]
        for required in (
            "retirementReadinessEvidence", "sourceCount",
            "burdenSatisfiedSourceCount", "blockedSourceCount",
            "pointInTimeOnly", "candidateSelectionMutated",
            "preservationPerformed", "reloadRequest", "entityRegistry",
            "arrivalGate", "teardownTransaction", "lifecycleAuthority",
            "familySummaries", "sources",
        ):
            self.assertIn(required, burden["required"])
        self.assertTrue(burden["properties"]["pointInTimeOnly"]["const"])
        for false_flag in (
            "retirementReadinessEvidence", "candidateSelectionMutated",
            "preservationPerformed", "reloadRequest", "entityRegistry",
            "arrivalGate", "teardownTransaction", "lifecycleAuthority",
        ):
            self.assertFalse(burden["properties"][false_flag]["const"])
        self.assertEqual(
            5, burden["properties"]["familySummaries"]["maxItems"]
        )
        self.assertEqual(
            8192, burden["properties"]["sources"]["maxItems"]
        )

    def test_observer_uses_exact_proposal_and_serializes_all_families(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        fixture = OBSERVER_FIXTURE_TEST.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v44"', observer
        )
        self.assertIn("PackedRegionPreservationBurdenSource", observer)
        self.assertIn(
            "requirePreservationBurdenMatchesProposal", observer
        )
        self.assertIn("appendPackedRegionPreservationBurden", observer)
        for family in (
            "PLAYER_SESSION", "DYNAMIC_OBJECT", "GROUND_ITEM",
            "COLLISION_PRODUCT", "OWNED_EVENT",
        ):
            self.assertIn(family, fixture)
        self.assertIn("preservation_burden = decision_events[2]", fixture)
        self.assertIn(
            'decision_events[4]["packedRegionPreservationBurden"]', fixture
        )

    def test_private_runtime_sources_use_only_region_manager_assessment(self):
        method = "assessLayeredPackedRegionPreservationBurden("
        source_name = "layeredPackedRegionPreservationBurdenSource"
        for path in (PLAYER, COMMAND):
            source = path.read_text(encoding="utf-8")
            self.assertIn(method, source)
            self.assertIn(source_name, source)
        readme = README.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v30.schema.json", readme)
        self.assertIn("point-in-time preservation-burden", readme)

    def test_living_plan_records_slice_eighty_five_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 85: Private preservation-burden diagnostics", plan
        )
        self.assertIn("schema-v30", plan)
        self.assertIn("same exact proposal order", plan)
        self.assertIn("Private owner validation status:", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
