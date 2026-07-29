#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
COMMAND = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
SCHEMA_V31 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v31.schema.json"
)
SCHEMA_V32 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v32.schema.json"
)
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-eleven.py"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceNinetyOneTest(unittest.TestCase):
    def test_v32_adds_closed_bounded_event_ownership_evidence(self):
        v31 = json.loads(SCHEMA_V31.read_text(encoding="utf-8"))
        v32 = json.loads(SCHEMA_V32.read_text(encoding="utf-8"))
        field = "packedRegionEventOwnership"
        self.assertNotIn(field, v31["properties"])
        self.assertEqual(
            "layered-map-parity-event-v32",
            v32["properties"]["schema"]["const"],
        )
        self.assertIn(field, v32["required"])
        contract = v32["$defs"]["eventOwnership"]
        self.assertFalse(contract["additionalProperties"])
        self.assertEqual(65536, contract["properties"]["events"]["maxItems"])
        self.assertEqual(
            262144,
            v32["$defs"]["eventRecord"]["properties"]
                ["spatialReferences"]["maxItems"],
        )
        for false_flag in (
            "callbackStateCaptured", "schedulerIdentityCaptured",
            "preservationPerformed", "reloadRequest", "eventCancellation",
            "eventReschedule", "entityRegistry", "arrivalGate",
            "teardownTransaction", "lifecycleAuthority",
        ):
            self.assertFalse(contract["properties"][false_flag]["const"])
        event_properties = v32["$defs"]["eventRecord"]["properties"]
        for forbidden in (
            "descriptor", "uuid", "eventClass", "callbackClass",
            "ownerIdentity", "usernameHash",
        ):
            self.assertNotIn(forbidden, event_properties)

    def test_observer_correlates_and_serializes_complete_inventory(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v49"', source)
        self.assertIn("PackedRegionEventOwnershipSource", source)
        self.assertIn("appendPackedRegionEventOwnership(", source)
        self.assertIn("requireEventOwnershipMatchesProposal(", source)
        self.assertIn("MAX_TRACE_EVENT_OWNERSHIP_EVENTS", source)
        self.assertIn("MAX_TRACE_EVENT_OWNERSHIP_REFERENCES", source)
        boundary = source[source.index("appendPackedRegionEventOwnership("):
                          source.index("appendPackedRegionAuthoredPopulationSupersession(")]
        for forbidden in (
            "getDescriptor()", "getUUID()", "getClass()", "ownerIdentity",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_private_start_and_reconnect_use_runtime_capture_seam(self):
        method = "captureLayeredPackedRegionEventOwnershipInventory("
        source_name = "layeredPackedRegionEventOwnershipSource"
        for path in (PLAYER, COMMAND):
            source = path.read_text(encoding="utf-8")
            self.assertIn(method, source)
            self.assertIn(source_name, source)

    def test_fixture_proves_exact_and_unknown_events_together(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        self.assertIn("EXACT_SPATIAL", fixture)
        self.assertIn("UNATTRIBUTED", fixture)
        self.assertIn('event_ownership["unattributedEventCount"]', fixture)
        self.assertIn("layered-map-parity-event-v32.schema.json", fixture)
        readme = README.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v32.schema.json", readme)
        self.assertIn("descriptors, UUIDs, callback classes", readme)
        self.assertIn("::lp mark", readme)

    def test_living_plan_records_slice_ninety_one_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 91: Private event-ownership diagnostics", plan
        )
        self.assertIn("schema-v32", plan)
        self.assertIn("Private owner validation status", plan)
        self.assertIn("exact fixed effect at `(524,489)`", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
