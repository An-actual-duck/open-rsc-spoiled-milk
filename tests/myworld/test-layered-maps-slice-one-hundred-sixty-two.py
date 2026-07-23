#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_V45 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v45.schema.json"
)
SCHEMA_V46 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v46.schema.json"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PLAYER = ROOT / (
    "server/src/com/openrsc/server/model/entity/player/Player.java"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


def blocked_continuity():
    return {
        "generation": 9,
        "eventObservedAtTick": 120,
        "censusObservedAtTick": 121,
        "selectedSourceCount": 60,
        "proposalRelatedEventCount": 1001,
        "relatedOwnerPositionHintEventCount": 1000,
        "npcOwnerPositionHintEventCount": 1000,
        "capturedNpcOwnerIdentityCount": 1000,
        "uniquelyMatchedActiveOwnerCount": 1000,
        "continuityEligibleEventCount": 0,
        "preservationUnprovedEventCount": 1000,
        "hardBlockerEventCount": 0,
        "exactSelectionAligned": True,
        "ownerPreservationProved": False,
        "allRelatedOwnerContinuityReadyAtObservation": False,
        "firstUnmetRegistrationSequence": 320,
        "firstUnmetOutcome": "OWNER_PRESERVATION_UNPROVED",
        "pointInTimeOnly": True,
        "runtimeHandleRetained": False,
        "preservationPerformed": False,
        "eventReschedule": False,
        "entityRegistry": False,
        "arrivalGate": False,
        "lifecycleAuthority": False,
        "events": [{
            "snapshotOrdinal": 319,
            "registrationSequence": 320,
            "ownerKind": "NPC",
            "npcOwnerIdentityCaptured": True,
            "outcome": "OWNER_PRESERVATION_UNPROVED",
            "activeIdentityMatchCount": 1,
            "matchedIdentityStatus": "RECOGNIZED",
            "matchedClassification": "SELECTED_OWNER_INSIDE",
            "uniqueActiveOwnerMatch": True,
        }],
    }


class LayeredMapsSliceOneHundredSixtyTwoTest(unittest.TestCase):
    def test_v45_is_immutable_and_v46_adds_closed_owner_evidence(self):
        v45 = json.loads(SCHEMA_V45.read_text(encoding="utf-8"))
        v46 = json.loads(SCHEMA_V46.read_text(encoding="utf-8"))
        self.assertEqual(
            "layered-map-parity-event-v45",
            v45["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "packedRegionNpcOwnerEventContinuity",
            v45["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v46",
            v46["properties"]["schema"]["const"],
        )
        self.assertIn(
            "packedRegionNpcOwnerEventContinuity",
            v46["required"],
        )
        ownership = v46["$defs"]["eventOwnership"]
        record = v46["$defs"]["eventRecord"]
        self.assertIn(
            "npcOwnerIdentityCapturedEventCount",
            ownership["required"],
        )
        self.assertIn("npcOwnerIdentity", record["required"])
        self.assertEqual(
            0,
            v46["$defs"]["npcOwnerEventContinuity"]["properties"][
                "continuityEligibleEventCount"
            ]["const"],
        )
        self.assertFalse(
            v46["$defs"]["npcOwnerEventContinuity"]["properties"][
                "ownerPreservationProved"
            ]["const"],
        )

    def test_v46_accepts_diagnostic_match_but_rejects_authority_claims(self):
        schema = json.loads(SCHEMA_V46.read_text(encoding="utf-8"))
        try:
            import jsonschema
        except ImportError:
            self.skipTest("jsonschema is not installed")
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$defs": {
                name: schema["$defs"][name]
                for name in (
                    "npcOwnerEventContinuity",
                    "npcOwnerContinuityOutcome",
                    "npcOwnerEventContinuityRecord",
                )
            },
            "$ref": "#/$defs/npcOwnerEventContinuity",
        }
        jsonschema.Draft202012Validator.check_schema(isolated)
        validator = jsonschema.Draft202012Validator(isolated)
        validator.validate(blocked_continuity())

        for field, value in (
            ("continuityEligibleEventCount", 1),
            ("ownerPreservationProved", True),
            ("allRelatedOwnerContinuityReadyAtObservation", True),
            ("preservationPerformed", True),
            ("eventReschedule", True),
            ("entityRegistry", True),
            ("arrivalGate", True),
            ("lifecycleAuthority", True),
        ):
            invalid = blocked_continuity()
            invalid[field] = value
            with self.assertRaises(jsonschema.ValidationError, msg=field):
                validator.validate(invalid)

        inconsistent = blocked_continuity()
        inconsistent["firstUnmetOutcome"] = None
        with self.assertRaises(jsonschema.ValidationError):
            validator.validate(inconsistent)

    def test_runtime_capture_is_fresh_bounded_and_documented(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v46"',
            observer,
        )
        self.assertIn(
            "appendPackedRegionNpcOwnerEventContinuity(",
            observer,
        )
        self.assertIn(
            "captureLayeredPackedRegionNpcOwnerEventContinuity(",
            manager,
        )
        self.assertIn(
            "assessLayeredPackedRegionRetirementRefinementCandidatesLocked(",
            manager,
        )
        self.assertIn("captureActiveNpcResidency(", manager)
        self.assertIn(
            "checkedInventory, observation, true, false,",
            manager,
        )
        self.assertIn("captureNpcOwnerContinuity(", player)
        self.assertIn(
            "layered-map-parity-event-v46.schema.json",
            readme,
        )
        self.assertIn(
            "### Slice 162: Private NPC owner-event continuity diagnostics",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
