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
SCHEMA_V20 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v20.schema.json"
)
SCHEMA_V21 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v21.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceSixtyFourTest(unittest.TestCase):
    def test_v21_contract_is_additive_and_non_authoritative(self):
        v20 = json.loads(SCHEMA_V20.read_text(encoding="utf-8"))
        v21 = json.loads(SCHEMA_V21.read_text(encoding="utf-8"))

        self.assertEqual(
            "layered-map-parity-event-v20",
            v20["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "packedRegionAuthoredReconstructionCohort", v20["properties"]
        )
        self.assertEqual(
            "layered-map-parity-event-v21",
            v21["properties"]["schema"]["const"],
        )
        self.assertIn(
            "packedRegionAuthoredReconstructionCohort", v21["required"]
        )
        cohort = v21["$defs"][
            "packedRegionAuthoredReconstructionCohort"
        ]
        for field in (
            "seedSourceCount",
            "expandedAuthoredSourceCount",
            "externalSupportRequirementSourceCount",
            "maximumExpansionRound",
            "authoredClosureComplete",
            "fullySelfContained",
            "identityMetadataOnly",
            "entityRegistry",
            "lifecycleAuthority",
            "entries",
            "requirements",
        ):
            self.assertIn(field, cohort["required"])
        self.assertTrue(
            cohort["properties"]["authoredClosureComplete"]["const"]
        )
        self.assertTrue(
            cohort["properties"]["identityMetadataOnly"]["const"]
        )
        self.assertFalse(cohort["properties"]["entityRegistry"]["const"])
        self.assertFalse(cohort["properties"]["lifecycleAuthority"]["const"])

    def test_observer_serializes_bounded_cohort_evidence(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v40"', observer
        )
        self.assertIn(
            "PackedRegionAuthoredReconstructionCohortSource", observer
        )
        self.assertIn("maximumCohortSources", observer)
        self.assertIn("maximumRequirementSources", observer)
        self.assertIn(
            "appendPackedRegionAuthoredReconstructionCohort", observer
        )
        self.assertIn('\\"externalSupportRequired\\"', observer)
        self.assertIn('\\"identityMetadataOnly\\":true', observer)
        self.assertIn('\\"entityRegistry\\":false', observer)
        self.assertIn('\\"lifecycleAuthority\\":false', observer)

    def test_player_and_command_sources_both_supply_completed_recipe(self):
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        for source in (player, development):
            self.assertIn(
                "layeredPackedRegionAuthoredReconstructionCohortSource",
                source,
            )
            self.assertIn("getAuthoredReconstructionRecipe()", source)
            self.assertIn(
                "LayeredPackedRegionAuthoredReconstructionCohortAnalysis",
                source,
            )
            self.assertIn("maximumCohortSources", source)
            self.assertIn("maximumRequirementSources", source)

    def test_living_plan_records_slice_sixty_four_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 64: Fixed-point cohort diagnostics", plan
        )
        self.assertIn("schema-v21", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
