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
OBSERVATION = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventTargetObservation.java"
)
SCHEMA_V40 = ROOT / (
    "tools/layered-maps/schema/"
    "layered-map-parity-event-v40.schema.json"
)
SCHEMA_V41 = ROOT / (
    "tools/layered-maps/schema/"
    "layered-map-parity-event-v41.schema.json"
)
FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-eleven.py"
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredSeventeenTest(unittest.TestCase):
    def test_v40_is_immutable_and_v41_adds_target_observation(self):
        v40 = json.loads(SCHEMA_V40.read_text(encoding="utf-8"))
        v41 = json.loads(SCHEMA_V41.read_text(encoding="utf-8"))
        self.assertEqual(
            "layered-map-parity-event-v40",
            v40["properties"]["schema"]["const"],
        )
        self.assertNotIn("packedRegionEventTargets", v40["properties"])
        self.assertEqual(
            "layered-map-parity-event-v41",
            v41["properties"]["schema"]["const"],
        )
        self.assertIn("packedRegionEventTargets", v41["required"])
        self.assertIn("eventTargets", v41["$defs"])
        self.assertIn("eventTarget", v41["$defs"])

    def test_v41_target_schema_is_closed_and_inert(self):
        schema = json.loads(SCHEMA_V41.read_text(encoding="utf-8"))
        aggregate = schema["$defs"]["eventTargets"]
        target = schema["$defs"]["eventTarget"]
        self.assertFalse(aggregate["additionalProperties"])
        self.assertFalse(target["additionalProperties"])
        for field, value in (
            ("outcomeCountComplete", True),
            ("pointInTimeOnly", True),
            ("atomicWithEventInventory", False),
            ("readOnlyTargetLookupPerformed", True),
            ("entityHandleRetained", False),
            ("achievedStateClaimed", False),
            ("commitToken", False),
            ("mutationPerformed", False),
            ("executableRestoration", False),
            ("arrivalGate", False),
            ("lifecycleAuthority", False),
        ):
            self.assertEqual(
                value, aggregate["properties"][field]["const"], field
            )
        serialized = json.dumps(target, sort_keys=True)
        for state in (
            "UNAVAILABLE", "EMPTY", "EXACT_RESTORATION_SCENERY_PRESENT",
            "EXACT_AUTHORED_TRANSIENT_PRESENT",
            "MISMATCHED_OR_IDENTITYLESS_OCCUPANT", "AMBIGUOUS_OCCUPANCY",
        ):
            self.assertIn(state, serialized)
        for private_field in ("owner", "entity", "objectId", "permanentObjectId"):
            self.assertNotIn(private_field, target["properties"])

    def test_observer_captures_correlates_and_serializes_detached_targets(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v42"', source)
        capture_start = source.index(
            "state.packedRegionEventOwnershipSource.capture("
        )
        target_capture = source.index(".captureTargets(", capture_start)
        require_match = source.index(
            "requireEventTargetsMatchInventory(", target_capture
        )
        event_json = source.index("String line = eventJson(", require_match)
        self.assertLess(capture_start, target_capture)
        self.assertLess(target_capture, require_match)
        self.assertLess(require_match, event_json)
        matching = source[
            source.index("private static void requireEventTargetsMatchInventory("):
            source.index(
                "private static void appendPackedRegionAuthoredPopulationSupersession(",
                source.index("private static void requireEventTargetsMatchInventory("),
            )
        ]
        for value in (
            "getProposalGeneration()", "getEventInventoryObservedAtTick()",
            "getSchedulerInstanceIdentity()", "getSnapshotOrdinal()",
            "getRegistrationSequence()", "target.getX()", "target.getY()",
        ):
            self.assertIn(value, matching)
        serializer = source[
            source.index("private static void appendPackedRegionEventTargets("):
            source.index("private static void appendEventRestorationState(")
        ]
        self.assertIn('\\"packedRegionEventTargets\\"', source)
        for value in (
            "getExactAuthoredIdentityCount()", "getObservedTargetState()",
            "getDecisionOutcome()", "getDecisionReason()",
            "isAchievedStateClaimed()", "isCommitToken()",
        ):
            self.assertIn(value, serializer)
        for forbidden in (
            "GameObject ", "Region ", "registerGameObject",
            "unregisterGameObject", ".doRun()", "sendUpdatePackets",
        ):
            self.assertNotIn(forbidden, serializer)

    def test_real_sources_share_region_manager_capture(self):
        for path in (PLAYER, COMMAND):
            source = path.read_text(encoding="utf-8")
            self.assertIn("captureTargets(", source)
            self.assertIn(
                "captureLayeredPackedRegionEventTargetObservation(", source
            )
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            "default LayeredPackedRegionEventTargetObservation captureTargets(",
            observer,
        )
        observation = OBSERVATION.read_text(encoding="utf-8")
        self.assertIn("isEntityHandleRetained() { return false; }", observation)
        self.assertIn("isMutationPerformed() { return false; }", observation)

    def test_fixture_readme_and_plan_identify_v41_boundary(self):
        fixture = FIXTURE.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v41.schema.json", fixture)
        self.assertIn("layered-map-parity-event-v41.schema.json", readme)
        self.assertIn(
            "### Slice 117: Private restoration target diagnostics", plan
        )
        self.assertIn("no achieved-state claim", plan)


if __name__ == "__main__":
    unittest.main()
