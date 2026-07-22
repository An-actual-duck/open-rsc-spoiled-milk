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
SCHEMA_V30 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v30.schema.json"
)
SCHEMA_V31 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v31.schema.json"
)
OBSERVER_FIXTURE_TEST = ROOT / (
    "tests/myworld/test-layered-maps-slice-eleven.py"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceEightySevenTest(unittest.TestCase):
    def test_v31_adds_closed_privacy_safe_dynamic_object_records(self):
        v30 = json.loads(SCHEMA_V30.read_text(encoding="utf-8"))
        v31 = json.loads(SCHEMA_V31.read_text(encoding="utf-8"))
        field = "packedRegionDynamicObjectPreservation"

        self.assertNotIn(field, v30["properties"])
        self.assertEqual(
            "layered-map-parity-event-v31",
            v31["properties"]["schema"]["const"],
        )
        self.assertIn(field, v31["required"])
        contract = v31["$defs"]["dynamicObjectPreservation"]
        for required in (
            "proposalGeneration", "observedAtTick", "sourceCount",
            "dynamicObjectCount", "objectsWithRuntimeAttributesCount",
            "constructorStateCompleteObjectCount",
            "standaloneRestorationCompleteObjectCount", "pointInTimeOnly",
            "detachedPrimitiveCopy", "runtimeAttributesCaptured",
            "eventOwnershipCaptured", "preservationPerformed",
            "reloadRequest", "entityRegistry", "arrivalGate",
            "teardownTransaction", "lifecycleAuthority", "sources",
        ):
            self.assertIn(required, contract["required"])
        self.assertEqual(
            8192, contract["properties"]["sources"]["maxItems"]
        )
        object_contract = v31["$defs"]["dynamicObjectRecord"]
        self.assertIn("ownerPresent", object_contract["required"])
        self.assertNotIn("owner", object_contract["properties"])
        self.assertTrue(
            object_contract["properties"]["constructorStateComplete"]["const"]
        )
        self.assertFalse(
            object_contract["properties"]["standaloneRestorationComplete"]["const"]
        )
        for false_flag in (
            "runtimeAttributesCaptured", "eventOwnershipCaptured",
            "preservationPerformed", "reloadRequest", "entityRegistry",
            "arrivalGate", "teardownTransaction", "lifecycleAuthority",
        ):
            self.assertFalse(contract["properties"][false_flag]["const"])

    def test_observer_correlates_proposal_and_never_serializes_owner_text(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        fixture = OBSERVER_FIXTURE_TEST.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v39"', observer
        )
        self.assertIn("PackedRegionDynamicObjectPreservationSource", observer)
        self.assertIn(
            "requireDynamicObjectPreservationMatchesProposal", observer
        )
        self.assertIn(
            "appendPackedRegionDynamicObjectPreservation", observer
        )
        serializer_start = observer.index(
            "appendPackedRegionDynamicObjectPreservation("
        )
        serializer_end = observer.index(
            "requireDynamicObjectPreservationMatchesProposal(",
            serializer_start,
        )
        serializer = observer[serializer_start:serializer_end]
        self.assertIn("object.hasOwner()", serializer)
        self.assertNotIn("object.getOwner()", serializer)
        self.assertIn('"private-owner", 2', fixture)
        self.assertIn(
            'self.assertNotIn("private-owner", json.dumps(decision_events))',
            fixture,
        )

    def test_private_runtime_sources_use_only_bounded_region_manager_capture(self):
        method = (
            "captureLayeredPackedRegionDynamicObjectPreservationRecord("
        )
        source_name = "layeredPackedRegionDynamicObjectPreservationSource"
        for path in (PLAYER, COMMAND):
            source = path.read_text(encoding="utf-8")
            self.assertIn(method, source)
            self.assertIn(source_name, source)
        readme = README.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v31.schema.json", readme)
        self.assertIn("owner text and attribute values are not", readme)

    def test_living_plan_records_slice_eighty_seven_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 87: Private dynamic-object preservation diagnostics",
            plan,
        )
        self.assertIn("schema-v31", plan)
        self.assertIn("owner text", plan)
        self.assertIn("Private owner validation status:", plan)
        self.assertIn("No object is unregistered", plan)


if __name__ == "__main__":
    unittest.main()
