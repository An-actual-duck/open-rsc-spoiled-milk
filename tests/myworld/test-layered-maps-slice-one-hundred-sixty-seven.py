#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_V46 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v46.schema.json"
)
SCHEMA_V47 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v47.schema.json"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
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


def scope_ready_boundary():
    return {
        "generation": 9,
        "requirementsObservedAtTick": 120,
        "boundaryObservedAtTick": 121,
        "schedulerInstanceIdentity":
            "00000000-0000-0000-0000-000000000167",
        "selectedSourceCount": 36,
        "proposalRelatedEventCount": 457,
        "relatedOwnerPositionHintEventCount": 457,
        "npcOwnerEventCount": 449,
        "separateNonNpcOwnerEventCount": 8,
        "preservationRequiredEventCount": 449,
        "previouslyEligibleEventCount": 0,
        "npcHardBlockerEventCount": 0,
        "requiredOwnerCount": 449,
        "relatedEventLinkCount": 449,
        "supportingEventLinkCount": 0,
        "requiredEventLinkCount": 449,
        "schedulerInstanceMatched": True,
        "registrationSetComplete": True,
        "eventExecutionBoundaryCount": 449,
        "eventTimingBoundaryCount": 449,
        "worldRegistrationBoundaryHeld": True,
        "npcLifecycleBoundaryCount": 449,
        "regionAbsenceQuiescenceHeld": True,
        "exactReferenceOwnerCount": 449,
        "reason": "PRESERVATION_SCOPE_READY",
        "referenceBoundaryComplete": True,
        "preservationScopeReadyAtBoundary": True,
        "pointInTimeOnly": True,
        "preservationFactEstablished": False,
        "runtimeHandleRetained": False,
        "preservationPerformed": False,
        "eventReschedule": False,
        "entityRegistry": False,
        "arrivalGate": False,
        "lifecycleAuthority": False,
        "owners": [{
            "generation": 9,
            "packedRegionX": 4,
            "packedRegionY": 0,
            "sourceOrdinal": 1,
            "runtimeNpcId": 10,
            "requiredEventLinkCount": 1,
            "validatedEventLinkCount": 1,
            "worldIdentityMatchCount": 1,
            "outcome": "EXACT_REFERENCE_BOUNDARY",
        }],
    }


def event_source(text, signature):
    start = text.index(signature)
    end = text.index("\n\tprivate ", start + len(signature))
    return text[start:end]


class LayeredMapsSliceOneHundredSixtySevenTest(unittest.TestCase):
    def test_v46_is_immutable_and_v47_adds_the_boundary(self):
        v46 = json.loads(SCHEMA_V46.read_text(encoding="utf-8"))
        v47 = json.loads(SCHEMA_V47.read_text(encoding="utf-8"))
        self.assertEqual(
            "layered-map-parity-event-v46",
            v46["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "packedRegionNpcOwnerPreservationBoundary",
            v46["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v47",
            v47["properties"]["schema"]["const"],
        )
        self.assertIn(
            "packedRegionNpcOwnerPreservationBoundary",
            v47["required"],
        )
        boundary = v47["$defs"]["npcOwnerPreservationBoundary"]
        self.assertFalse(
            boundary["properties"]["preservationFactEstablished"]["const"],
        )
        self.assertFalse(
            boundary["properties"]["lifecycleAuthority"]["const"],
        )

    def test_v47_accepts_scope_evidence_but_rejects_authority(self):
        schema = json.loads(SCHEMA_V47.read_text(encoding="utf-8"))
        try:
            import jsonschema
        except ImportError:
            self.skipTest("jsonschema is not installed")
        isolated = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "$defs": {
                name: schema["$defs"][name]
                for name in (
                    "npcOwnerPreservationBoundary",
                    "npcOwnerPreservationRecord",
                    "eventCount",
                )
            },
            "$ref": "#/$defs/npcOwnerPreservationBoundary",
        }
        jsonschema.Draft202012Validator.check_schema(isolated)
        validator = jsonschema.Draft202012Validator(isolated)
        validator.validate(scope_ready_boundary())

        for field in (
            "preservationFactEstablished",
            "runtimeHandleRetained",
            "preservationPerformed",
            "eventReschedule",
            "entityRegistry",
            "arrivalGate",
            "lifecycleAuthority",
        ):
            invalid = scope_ready_boundary()
            invalid[field] = True
            with self.assertRaises(jsonschema.ValidationError, msg=field):
                validator.validate(invalid)

    def test_both_runtime_sources_and_observer_use_the_boundary(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        sources = (
            event_source(
                PLAYER.read_text(encoding="utf-8"),
                "layeredPackedRegionEventOwnershipSource() {",
            ),
            event_source(
                DEVELOPMENT.read_text(encoding="utf-8"),
                "layeredPackedRegionEventOwnershipSource(final Player player) {",
            ),
        )
        for source in sources:
            self.assertEqual(
                1,
                source.count("captureNpcOwnerPreservationBoundary("),
            )
            self.assertEqual(
                1,
                source.count(
                    "captureLayeredPackedRegionNpcOwnerPreservationBoundary("
                ),
            )
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v48"',
            observer,
        )
        self.assertIn(
            "LayeredPackedRegionNpcOwnerPreservationRequirements",
            observer,
        )
        self.assertIn(".captureNpcOwnerPreservationBoundary(", observer)
        self.assertIn(
            "appendPackedRegionNpcOwnerPreservationBoundary(",
            observer,
        )
        self.assertIn(
            "layered-map-parity-event-v48.schema.json",
            README.read_text(encoding="utf-8"),
        )

    def test_living_plan_records_slice_one_hundred_sixty_seven(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 167: Private NPC owner preservation-boundary diagnostics",
            plan,
        )
        self.assertIn("schema-v47", plan)
        self.assertIn("owner validation is pending", plan)


if __name__ == "__main__":
    unittest.main()
