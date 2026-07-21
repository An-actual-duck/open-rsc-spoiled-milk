#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
SCHEMA_V25 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v25.schema.json"
)
SCHEMA_V26 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v26.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceSeventyFourTest(unittest.TestCase):
    def test_v26_contract_is_additive_bounded_and_non_authoritative(self):
        v25 = json.loads(SCHEMA_V25.read_text(encoding="utf-8"))
        v26 = json.loads(SCHEMA_V26.read_text(encoding="utf-8"))

        field = "packedRegionActiveNpcContainment"
        self.assertEqual(
            "layered-map-parity-event-v25",
            v25["properties"]["schema"]["const"],
        )
        self.assertNotIn(field, v25["properties"])
        self.assertEqual(
            "layered-map-parity-event-v26",
            v26["properties"]["schema"]["const"],
        )
        self.assertIn(field, v26["required"])
        assessment = v26["$defs"]["activeNpcContainmentAssessment"]
        for required in (
            "sameSourceSelectedOwnerInsideCount",
            "crossSourceSelectedOwnerInsideCount",
            "activePreservationRequiredInstanceCount",
            "relevantDuplicateIdentityInstanceCount",
            "blockingConditionCount",
            "blockingEvidenceCount",
            "boundaryContained",
            "pointInTimeOnly",
            "containmentEvidence",
            "entityPreservationRequired",
            "lifecycleReady",
            "entityRegistry",
            "arrivalGate",
            "lifecycleAuthority",
            "blockers",
        ):
            self.assertIn(required, assessment["required"])
        self.assertTrue(assessment["properties"]["pointInTimeOnly"]["const"])
        self.assertTrue(
            assessment["properties"]["containmentEvidence"]["const"]
        )
        self.assertFalse(assessment["properties"]["lifecycleReady"]["const"])
        self.assertFalse(assessment["properties"]["entityRegistry"]["const"])
        self.assertFalse(assessment["properties"]["arrivalGate"]["const"])
        self.assertFalse(
            assessment["properties"]["lifecycleAuthority"]["const"]
        )
        self.assertEqual(6, assessment["properties"]["blockers"]["minItems"])
        self.assertEqual(6, assessment["properties"]["blockers"]["maxItems"])
        self.assertEqual(6, len(v26["$defs"]["blockerKind"]["enum"]))

    def test_observer_derives_containment_from_the_same_census(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v36"', observer
        )
        self.assertRegex(
            observer,
            r"LayeredPackedRegionActiveNpcContainmentAssessment\.assess\("
            r"\s+packedRegionActiveNpcResidency\)",
        )
        self.assertIn("appendPackedRegionActiveNpcContainment", observer)
        self.assertIn(r'\"pointInTimeOnly\":true', observer)
        self.assertIn(r'\"containmentEvidence\":true', observer)
        self.assertIn(r'\"lifecycleReady\":false', observer)
        self.assertIn(r'\"entityRegistry\":false', observer)
        self.assertIn(r'\"arrivalGate\":false', observer)
        self.assertIn(r'\"lifecycleAuthority\":false', observer)
        self.assertNotIn("PackedRegionActiveNpcContainmentSource", observer)

    def test_living_plan_records_slice_seventy_four_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 74: Active NPC containment diagnostics", plan
        )
        self.assertIn("schema-v26", plan)
        self.assertIn("same exact census", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
