#!/usr/bin/env python3
import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_DIR = ROOT / "tools/layered-maps/schema"
SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v48.schema.json"
PREVIOUS_SCHEMA = SCHEMA_DIR / "layered-map-parity-event-v47.schema.json"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/"
    "Development.java"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredSeventyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$defs": {
                "npcOwnerPreservationNoOp":
                    cls.schema["$defs"]["npcOwnerPreservationNoOp"],
            },
            "$ref": "#/$defs/npcOwnerPreservationNoOp",
        }
        Draft202012Validator.check_schema(isolated)
        cls.validator = Draft202012Validator(isolated)
        cls.evidence = {
            "reason": "SOURCE_LIFECYCLE_UNAVAILABLE",
            "generation": 1,
            "requirementsObservedAtTick": 914,
            "selectedSourceCount": 36,
            "requiredEventLinkCount": 449,
            "requiredOwnerCount": 449,
            "ownerScopeEntered": True,
            "sourceLifecycleInvoked": True,
            "absentSourceCount": 0,
            "reconstructedSourceCount": 0,
            "preservedConsumerInvoked": False,
            "preservationEstablishedForConsumedWork": False,
            "preservationPerformed": False,
            "sourceAbsencePerformed": False,
            "sourceReconstructionPerformed": False,
            "regionMutationPerformed": False,
            "runtimeHandleRetained": False,
            "arrivalGate": False,
            "visibilityReleased": False,
            "lifecycleAuthority": False,
        }

    def test_additive_schema_accepts_only_closed_refusal_shapes(self):
        self.validator.validate(self.evidence)

        scope_refused = dict(self.evidence)
        evidence = scope_refused
        evidence["reason"] = "OWNER_SCOPE_REFUSED"
        evidence["ownerScopeEntered"] = False
        evidence["sourceLifecycleInvoked"] = False
        self.validator.validate(scope_refused)

        for field, value in (
            ("absentSourceCount", 1),
            ("reconstructedSourceCount", 1),
            ("preservedConsumerInvoked", True),
            ("preservationEstablishedForConsumedWork", True),
            ("preservationPerformed", True),
            ("sourceAbsencePerformed", True),
            ("sourceReconstructionPerformed", True),
            ("regionMutationPerformed", True),
            ("runtimeHandleRetained", True),
            ("arrivalGate", True),
            ("visibilityReleased", True),
            ("lifecycleAuthority", True),
        ):
            invalid = dict(self.evidence)
            invalid[field] = value
            self.assertFalse(
                self.validator.is_valid(invalid),
                f"schema accepted forbidden {field}",
            )

        crossed = dict(self.evidence)
        crossed["ownerScopeEntered"] = False
        self.assertFalse(self.validator.is_valid(crossed))

    def test_v47_is_immutable_and_v48_is_additive(self):
        previous = json.loads(PREVIOUS_SCHEMA.read_text(encoding="utf-8"))
        self.assertEqual(
            "layered-map-parity-event-v47",
            previous["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "packedRegionNpcOwnerPreservationNoOp",
            previous["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v48",
            self.schema["properties"]["schema"]["const"],
        )
        self.assertIn(
            "packedRegionNpcOwnerPreservationNoOp",
            self.schema["required"],
        )

    def test_private_action_uses_both_real_runtime_sources(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        handler = HANDLER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")

        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v48"', observer
        )
        self.assertIn(
            '"preservation-noop".equals(eventType)', observer
        )
        self.assertEqual(
            1, observer.count(".captureNpcOwnerPreservationNoOp(")
        )
        self.assertIn(
            'out.append(",\\"packedRegionNpcOwnerPreservationNoOp\\":")',
            observer,
        )
        self.assertIn(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic(",
            handler,
        )
        for source in (player, development):
            self.assertIn("captureNpcOwnerPreservationNoOp(", source)
            self.assertIn(
                "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic(",
                source,
            )
        self.assertIn('"preserve-noop".equals(action)', development)
        self.assertIn("recover-noop|preserve-noop|stop", development)
        self.assertIn("::layerparity preserve-noop", readme)
        self.assertIn("SOURCE_LIFECYCLE_UNAVAILABLE", readme)

    def test_living_plan_records_slice_one_hundred_seventy_two(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 172: Private source-lifecycle refusal diagnostics",
            plan,
        )
        self.assertIn("schema-v48", plan)
        self.assertIn("preserve-noop", plan)


if __name__ == "__main__":
    unittest.main()
