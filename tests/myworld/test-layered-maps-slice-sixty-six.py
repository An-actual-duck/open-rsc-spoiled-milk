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
SCHEMA_V21 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v21.schema.json"
)
SCHEMA_V22 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v22.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceSixtySixTest(unittest.TestCase):
    def test_v22_contract_is_additive_and_non_authoritative(self):
        v21 = json.loads(SCHEMA_V21.read_text(encoding="utf-8"))
        v22 = json.loads(SCHEMA_V22.read_text(encoding="utf-8"))

        field = "packedRegionAuthoredReconstructionCohortAttribution"
        self.assertEqual(
            "layered-map-parity-event-v21",
            v21["properties"]["schema"]["const"],
        )
        self.assertNotIn(field, v21["properties"])
        self.assertEqual(
            "layered-map-parity-event-v22",
            v22["properties"]["schema"]["const"],
        )
        self.assertIn(field, v22["required"])
        attribution = v22["$defs"]["cohortAttribution"]
        for required in (
            "kindCount",
            "edgeCount",
            "bridgePlacementCount",
            "expansionFrontierReferenceCount",
            "externalSupportReferenceCount",
            "identityMetadataOnly",
            "entityRegistry",
            "lifecycleAuthority",
            "kinds",
            "edges",
            "bridgePlacements",
        ):
            self.assertIn(required, attribution["required"])
        self.assertTrue(
            attribution["properties"]["identityMetadataOnly"]["const"]
        )
        self.assertFalse(
            attribution["properties"]["entityRegistry"]["const"]
        )
        self.assertFalse(
            attribution["properties"]["lifecycleAuthority"]["const"]
        )
        self.assertEqual(
            8192, attribution["properties"]["edges"]["maxItems"]
        )
        self.assertEqual(
            8192,
            attribution["properties"]["bridgePlacements"]["maxItems"],
        )

    def test_observer_serializes_same_cohort_with_independent_budgets(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v22"', observer
        )
        self.assertIn(
            "PackedRegionAuthoredReconstructionCohortAttributionSource",
            observer,
        )
        self.assertIn("MAX_TRACE_ATTRIBUTION_EDGES", observer)
        self.assertIn(
            "MAX_TRACE_ATTRIBUTION_BRIDGE_PLACEMENTS", observer
        )
        self.assertIn(
            "appendPackedRegionAuthoredReconstructionCohortAttribution",
            observer,
        )
        self.assertIn(
            "packedRegionAuthoredReconstructionCohort,", observer
        )
        self.assertIn('\\"expansionFrontier\\"', observer)
        self.assertIn('\\"externalSupportRequired\\"', observer)
        self.assertIn('\\"identityMetadataOnly\\":true', observer)
        self.assertIn('\\"entityRegistry\\":false', observer)
        self.assertIn('\\"lifecycleAuthority\\":false', observer)

    def test_player_and_command_paths_supply_the_completed_recipe(self):
        for source in (
            PLAYER.read_text(encoding="utf-8"),
            DEVELOPMENT.read_text(encoding="utf-8"),
        ):
            self.assertIn(
                "layeredPackedRegionAuthoredReconstructionCohortAttributionSource",
                source,
            )
            self.assertIn(
                "LayeredPackedRegionAuthoredReconstructionCohortAttribution",
                source,
            )
            self.assertIn("getAuthoredReconstructionRecipe()", source)
            self.assertIn("maximumEdges", source)
            self.assertIn("maximumBridgePlacements", source)

    def test_living_plan_records_slice_sixty_six_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 66: Cohort attribution diagnostics", plan
        )
        self.assertIn("schema-v22", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
