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
SCHEMA_V19 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v19.schema.json"
)
SCHEMA_V20 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v20.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceSixtyTwoTest(unittest.TestCase):
    def test_v20_contract_is_additive_and_inert(self):
        v19 = json.loads(SCHEMA_V19.read_text(encoding="utf-8"))
        v20 = json.loads(SCHEMA_V20.read_text(encoding="utf-8"))

        self.assertEqual(
            "layered-map-parity-event-v19",
            v19["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "packedRegionAuthoredReconstruction", v19["properties"]
        )
        self.assertEqual(
            "layered-map-parity-event-v20",
            v20["properties"]["schema"]["const"],
        )
        self.assertIn(
            "packedRegionAuthoredReconstruction", v20["required"]
        )
        reconstruction = v20["$defs"][
            "packedRegionAuthoredReconstruction"
        ]
        for field in (
            "recipeReconstructionPlacementCount",
            "missingRequirementSourceCount",
            "selectionDependencyClosed",
            "identityMetadataOnly",
            "entityRegistry",
            "lifecycleAuthority",
            "entries",
            "requirements",
        ):
            self.assertIn(field, reconstruction["required"])
        self.assertTrue(
            reconstruction["properties"]["identityMetadataOnly"]["const"]
        )
        self.assertFalse(
            reconstruction["properties"]["entityRegistry"]["const"]
        )
        self.assertFalse(
            reconstruction["properties"]["lifecycleAuthority"]["const"]
        )

    def test_observer_serializes_bounded_dependency_evidence(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v49"', observer
        )
        self.assertIn("PackedRegionAuthoredReconstructionSource", observer)
        self.assertIn("maximumRequirementSources", observer)
        self.assertIn("appendPackedRegionAuthoredReconstruction", observer)
        self.assertIn('\\"selectionDependencyClosed\\"', observer)
        self.assertIn('\\"identityMetadataOnly\\":true', observer)
        self.assertIn('\\"entityRegistry\\":false', observer)
        self.assertIn('\\"lifecycleAuthority\\":false', observer)

    def test_player_and_command_sources_both_supply_completed_recipe(self):
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        for source in (player, development):
            self.assertIn(
                "layeredPackedRegionAuthoredReconstructionSource", source
            )
            self.assertIn("getAuthoredReconstructionRecipe()", source)
            self.assertIn(
                "LayeredPackedRegionAuthoredReconstructionObservation.observe",
                source,
            )
            self.assertIn("maximumSafetySources", source)
            self.assertIn("maximumRequirementSources", source)

    def test_living_plan_records_slice_sixty_two_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 62: Authored reconstruction dependency diagnostics",
            plan,
        )
        self.assertIn("schema-v20", plan)
        self.assertIn("no lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
