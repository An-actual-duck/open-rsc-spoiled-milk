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
SCHEMA_V23 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v23.schema.json"
)
SCHEMA_V24 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v24.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceSeventyTest(unittest.TestCase):
    def test_v24_contract_is_additive_bounded_and_non_authoritative(self):
        v23 = json.loads(SCHEMA_V23.read_text(encoding="utf-8"))
        v24 = json.loads(SCHEMA_V24.read_text(encoding="utf-8"))

        field = "packedRegionAuthoredReconstructionDependencySemantics"
        self.assertEqual(
            "layered-map-parity-event-v23",
            v23["properties"]["schema"]["const"],
        )
        self.assertNotIn(field, v23["properties"])
        self.assertEqual(
            "layered-map-parity-event-v24",
            v24["properties"]["schema"]["const"],
        )
        self.assertIn(field, v24["required"])
        semantics = v24["$defs"]["dependencySemanticsAnalysis"]
        for required in (
            "selectedSourceCount",
            "selectedAuthoredReplaySourceCount",
            "replayPlacementCount",
            "outboundSupportSourceCount",
            "externalOutboundSupportSourceCount",
            "incomingOwnerSourceCount",
            "incomingPlacementCount",
            "sourceLocalReplay",
            "spatialReachPreserved",
            "activeInstanceEvidence",
            "entityRegistry",
            "lifecycleAuthority",
            "selectedSources",
            "outboundSupportSources",
            "incomingOwners",
            "kinds",
        ):
            self.assertIn(required, semantics["required"])
        self.assertTrue(semantics["properties"]["sourceLocalReplay"]["const"])
        self.assertTrue(
            semantics["properties"]["spatialReachPreserved"]["const"]
        )
        self.assertFalse(
            semantics["properties"]["activeInstanceEvidence"]["const"]
        )
        self.assertFalse(semantics["properties"]["entityRegistry"]["const"])
        self.assertFalse(
            semantics["properties"]["lifecycleAuthority"]["const"]
        )
        for collection in (
            "selectedSources",
            "outboundSupportSources",
            "incomingOwners",
        ):
            self.assertEqual(
                8192, semantics["properties"][collection]["maxItems"]
            )

    def test_observer_serializes_exact_safety_with_independent_budgets(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v37"', observer
        )
        self.assertIn(
            "PackedRegionAuthoredReconstructionDependencySemanticsSource",
            observer,
        )
        for budget in (
            "MAX_TRACE_DEPENDENCY_SEMANTICS_SELECTED_SOURCES",
            "MAX_TRACE_DEPENDENCY_SEMANTICS_SUPPORT_SOURCES",
            "MAX_TRACE_DEPENDENCY_SEMANTICS_INCOMING_OWNERS",
            "MAX_TRACE_DEPENDENCY_SEMANTICS_INCOMING_PLACEMENTS",
        ):
            self.assertIn(budget, observer)
        self.assertIn(
            "appendPackedRegionAuthoredReconstructionDependencySemantics",
            observer,
        )
        self.assertIn("packedRegionRetirementSafety,", observer)
        self.assertIn(r'\"sourceLocalReplay\":true', observer)
        self.assertIn(r'\"spatialReachPreserved\":true', observer)
        self.assertIn(r'\"activeInstanceEvidence\":false', observer)
        self.assertIn(r'\"entityRegistry\":false', observer)
        self.assertIn(r'\"lifecycleAuthority\":false', observer)

    def test_player_and_command_paths_supply_completed_recipe_and_safety(self):
        for source in (
            PLAYER.read_text(encoding="utf-8"),
            DEVELOPMENT.read_text(encoding="utf-8"),
        ):
            self.assertIn(
                "layeredPackedRegionAuthoredReconstructionDependencySemanticsSource",
                source,
            )
            self.assertIn(
                "LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis",
                source,
            )
            self.assertIn("getAuthoredReconstructionRecipe()", source)
            self.assertIn("maximumSelectedSources", source)
            self.assertIn("maximumSupportSources", source)
            self.assertIn("maximumIncomingOwners", source)
            self.assertIn("maximumIncomingPlacements", source)

    def test_living_plan_records_slice_seventy_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 70: Reconstruction dependency semantics diagnostics",
            plan,
        )
        self.assertIn("schema-v24", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
