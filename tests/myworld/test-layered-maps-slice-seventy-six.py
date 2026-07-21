#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
SCHEMA_V26 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v26.schema.json"
)
SCHEMA_V27 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v27.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceSeventySixTest(unittest.TestCase):
    def test_v27_contract_is_additive_bounded_and_non_authoritative(self):
        v26 = json.loads(SCHEMA_V26.read_text(encoding="utf-8"))
        v27 = json.loads(SCHEMA_V27.read_text(encoding="utf-8"))

        field = "packedRegionActiveNpcBoundaryRequirements"
        self.assertEqual(
            "layered-map-parity-event-v26",
            v26["properties"]["schema"]["const"],
        )
        self.assertNotIn(field, v26["properties"])
        self.assertEqual(
            "layered-map-parity-event-v27",
            v27["properties"]["schema"]["const"],
        )
        self.assertIn(field, v27["required"])
        projection = v27["$defs"]["activeNpcBoundaryRequirementProjection"]
        for required in (
            "selectedSourceCount",
            "boundaryContainedNow",
            "selectedOwnerOutsideInstanceCount",
            "externalOwnerInsideInstanceCount",
            "expandableBoundaryInstanceCount",
            "uniqueRequiredSourceCount",
            "hardBlockingConditionCount",
            "hardBlockingEvidenceCount",
            "freshSafetyAssessmentRequired",
            "freshNpcCensusRequired",
            "selectionMutated",
            "boundaryClosureProved",
            "entityRegistry",
            "arrivalGate",
            "lifecycleAuthority",
            "requirements",
        ):
            self.assertIn(required, projection["required"])
        self.assertTrue(
            projection["properties"]["freshSafetyAssessmentRequired"]["const"]
        )
        self.assertTrue(
            projection["properties"]["freshNpcCensusRequired"]["const"]
        )
        for false_flag in (
            "selectionMutated",
            "boundaryClosureProved",
            "entityRegistry",
            "arrivalGate",
            "lifecycleAuthority",
        ):
            self.assertFalse(projection["properties"][false_flag]["const"])
        self.assertEqual(
            8192, projection["properties"]["requirements"]["maxItems"]
        )
        self.assertEqual(
            [
                "SELECTED_OWNER_CURRENT_SOURCE",
                "EXTERNAL_OWNER_AUTHORED_SOURCE",
            ],
            v27["$defs"]["requirementReason"]["enum"],
        )

    def test_observer_projects_from_the_same_exact_census(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v33"', observer
        )
        self.assertIn("MAX_TRACE_ACTIVE_NPC_BOUNDARY_REQUIREMENTS", observer)
        self.assertRegex(
            observer,
            r"LayeredPackedRegionActiveNpcBoundaryRequirementProjection"
            r"\s+\.project\(\s+packedRegionActiveNpcResidency,",
        )
        self.assertIn(
            "appendPackedRegionActiveNpcBoundaryRequirements", observer
        )
        self.assertNotIn(
            "PackedRegionActiveNpcBoundaryRequirementSource", observer
        )

    def test_living_plan_records_slice_seventy_six_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 76: Active NPC boundary requirement diagnostics", plan
        )
        self.assertIn("schema-v27", plan)
        self.assertIn("same exact census", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
