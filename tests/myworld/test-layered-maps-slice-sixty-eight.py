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
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/"
    "Development.java"
)
SCHEMA_V22 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v22.schema.json"
)
SCHEMA_V23 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v23.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceSixtyEightTest(unittest.TestCase):
    def test_v23_contract_is_additive_bounded_and_non_authoritative(self):
        v22 = json.loads(SCHEMA_V22.read_text(encoding="utf-8"))
        v23 = json.loads(SCHEMA_V23.read_text(encoding="utf-8"))

        field = "packedRegionAuthoredReconstructionTopology"
        self.assertEqual(
            "layered-map-parity-event-v22",
            v22["properties"]["schema"]["const"],
        )
        self.assertNotIn(field, v22["properties"])
        self.assertEqual(
            "layered-map-parity-event-v23",
            v23["properties"]["schema"]["const"],
        )
        self.assertIn(field, v23["required"])
        topology = v23["$defs"]["topology"]
        for required in (
            "authoredSourceCount",
            "weakComponentCount",
            "strongComponentCount",
            "incomingOnlySourceCount",
            "directIncomingReferenceCount",
            "forwardDependencyClosed",
            "forwardCohortWeaklyClosed",
            "identityMetadataOnly",
            "entityRegistry",
            "lifecycleAuthority",
            "sources",
            "kinds",
            "weakComponents",
            "strongComponents",
        ):
            self.assertIn(required, topology["required"])
        self.assertTrue(
            topology["properties"]["identityMetadataOnly"]["const"]
        )
        self.assertFalse(topology["properties"]["entityRegistry"]["const"])
        self.assertFalse(
            topology["properties"]["lifecycleAuthority"]["const"]
        )
        for collection in ("sources", "weakComponents", "strongComponents"):
            self.assertEqual(
                8192, topology["properties"][collection]["maxItems"]
            )

    def test_observer_serializes_same_cohort_with_topology_budgets(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v33"', observer
        )
        self.assertIn(
            "PackedRegionAuthoredReconstructionTopologySource", observer
        )
        self.assertIn("MAX_TRACE_TOPOLOGY_SOURCES", observer)
        self.assertIn("MAX_TRACE_TOPOLOGY_RELATIONSHIPS", observer)
        self.assertIn(
            "appendPackedRegionAuthoredReconstructionTopology", observer
        )
        self.assertIn(
            "packedRegionAuthoredReconstructionCohort,", observer
        )
        self.assertIn('\\"incomingOnlySource\\"', observer)
        self.assertIn('\\"forwardCohortWeaklyClosed\\"', observer)
        self.assertIn('\\"identityMetadataOnly\\":true', observer)
        self.assertIn('\\"entityRegistry\\":false', observer)
        self.assertIn('\\"lifecycleAuthority\\":false', observer)

    def test_player_and_command_paths_supply_the_completed_recipe(self):
        for source in (
            PLAYER.read_text(encoding="utf-8"),
            DEVELOPMENT.read_text(encoding="utf-8"),
        ):
            self.assertIn(
                "layeredPackedRegionAuthoredReconstructionTopologySource",
                source,
            )
            self.assertIn(
                "LayeredPackedRegionAuthoredReconstructionTopologyAnalysis",
                source,
            )
            self.assertIn("getAuthoredReconstructionRecipe()", source)
            self.assertIn("maximumSources", source)
            self.assertIn("maximumRelationships", source)

    def test_living_plan_records_slice_sixty_eight_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 68: Whole-recipe topology diagnostics", plan
        )
        self.assertIn("schema-v23", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
