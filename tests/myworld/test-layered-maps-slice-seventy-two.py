#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/"
    "Development.java"
)
SCHEMA_V24 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v24.schema.json"
)
SCHEMA_V25 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v25.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceSeventyTwoTest(unittest.TestCase):
    def test_v25_contract_is_additive_bounded_and_non_authoritative(self):
        v24 = json.loads(SCHEMA_V24.read_text(encoding="utf-8"))
        v25 = json.loads(SCHEMA_V25.read_text(encoding="utf-8"))

        field = "packedRegionActiveNpcResidency"
        self.assertEqual(
            "layered-map-parity-event-v24",
            v24["properties"]["schema"]["const"],
        )
        self.assertNotIn(field, v24["properties"])
        self.assertEqual(
            "layered-map-parity-event-v25",
            v25["properties"]["schema"]["const"],
        )
        self.assertIn(field, v25["required"])
        observation = v25["$defs"]["activeNpcResidencyObservation"]
        for required in (
            "selectedSourceCount",
            "observedInstanceCount",
            "activeInstanceCount",
            "inactiveInstanceCount",
            "activeRecognizedInstanceCount",
            "activeUnrecognizedInstanceCount",
            "selectedOwnerInsideCount",
            "selectedOwnerOutsideCount",
            "externalOwnerInsideCount",
            "unresolvedInsideCount",
            "pointInTimeCensus",
            "activeInstanceEvidence",
            "entityRegistry",
            "arrivalGate",
            "lifecycleAuthority",
            "identityStatuses",
            "relevantActiveInstances",
        ):
            self.assertIn(required, observation["required"])
        self.assertTrue(
            observation["properties"]["pointInTimeCensus"]["const"]
        )
        self.assertTrue(
            observation["properties"]["activeInstanceEvidence"]["const"]
        )
        self.assertFalse(observation["properties"]["entityRegistry"]["const"])
        self.assertFalse(observation["properties"]["arrivalGate"]["const"])
        self.assertFalse(
            observation["properties"]["lifecycleAuthority"]["const"]
        )
        self.assertEqual(
            6, observation["properties"]["identityStatuses"]["maxItems"]
        )
        self.assertEqual(
            8192,
            observation["properties"]["relevantActiveInstances"]["maxItems"],
        )

    def test_observer_serializes_census_with_independent_budgets(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v44"', observer
        )
        self.assertIn("PackedRegionActiveNpcResidencySource", observer)
        self.assertIn("MAX_TRACE_ACTIVE_NPC_INSTANCES", observer)
        self.assertIn("MAX_TRACE_ACTIVE_NPC_RELEVANT_DETAILS", observer)
        self.assertIn("appendPackedRegionActiveNpcResidency", observer)
        self.assertIn("packedRegionRetirementSafety,", observer)
        self.assertIn(r'\"pointInTimeCensus\":true', observer)
        self.assertIn(r'\"activeInstanceEvidence\":true', observer)
        self.assertIn(r'\"entityRegistry\":false', observer)
        self.assertIn(r'\"arrivalGate\":false', observer)
        self.assertIn(r'\"lifecycleAuthority\":false', observer)

    def test_runtime_snapshot_is_detached_and_uses_existing_npc_collection(self):
        region_manager = REGION_MANAGER.read_text(encoding="utf-8")
        self.assertIn("captureActiveNpcResidency", region_manager)
        self.assertIn("synchronized (world.getNpcs())", region_manager)
        self.assertIn("for (Npc npc : world.getNpcs())", region_manager)
        self.assertIn("Point location = npc.getLocation();", region_manager)
        self.assertIn("npc.getAuthoredPlacementIdentity(), npc.getID()", region_manager)
        self.assertIn("!npc.isRemoved() && !npc.isRespawning()", region_manager)
        self.assertNotIn(
            "if (npc.getAuthoredPlacementIdentity() != null) {\n"
            "\t\t\t\t\tinstances.add",
            region_manager,
        )

    def test_player_and_command_paths_supply_recipe_safety_and_current_tick(self):
        for source in (
            PLAYER.read_text(encoding="utf-8"),
            DEVELOPMENT.read_text(encoding="utf-8"),
        ):
            self.assertIn("layeredPackedRegionActiveNpcResidencySource", source)
            self.assertIn("LayeredPackedRegionActiveNpcResidencyObservation", source)
            self.assertIn("getAuthoredReconstructionRecipe()", source)
            self.assertIn("captureActiveNpcResidency", source)
            self.assertIn("getCurrentTick()", source)
            self.assertIn("maximumInstances", source)
            self.assertIn("maximumRelevantDetails", source)

    def test_living_plan_records_slice_seventy_two_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 72: Active NPC residency diagnostics", plan
        )
        self.assertIn("schema-v25", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
