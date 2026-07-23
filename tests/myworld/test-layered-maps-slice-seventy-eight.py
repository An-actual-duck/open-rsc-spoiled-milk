#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
SCHEMA_V27 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v27.schema.json"
)
SCHEMA_V28 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v28.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceSeventyEightTest(unittest.TestCase):
    def test_v28_contract_is_additive_bounded_and_non_authoritative(self):
        v27 = json.loads(SCHEMA_V27.read_text(encoding="utf-8"))
        v28 = json.loads(SCHEMA_V28.read_text(encoding="utf-8"))

        field = "packedRegionRetirementRefinement"
        self.assertEqual(
            "layered-map-parity-event-v27",
            v27["properties"]["schema"]["const"],
        )
        self.assertNotIn(field, v27["properties"])
        self.assertEqual(
            "layered-map-parity-event-v28",
            v28["properties"]["schema"]["const"],
        )
        self.assertIn(field, v28["required"])
        proposal = v28["$defs"]["retirementRefinementProposal"]
        for required in (
            "originalSafetySourceCount",
            "authoredCohortSourceCount",
            "expandedAuthoredSourceCount",
            "activeNpcRequirementSourceCount",
            "candidateSourceCount",
            "addedCandidateSourceCount",
            "externalSupportRequirementSourceCount",
            "hardBlockingConditionCount",
            "hardBlockingEvidenceCount",
            "freshSafetyAssessmentRequired",
            "freshNpcCensusRequired",
            "reassessmentRequired",
            "candidateSelectionMutated",
            "fixedPointClosureProved",
            "loadRequest",
            "entityRegistry",
            "arrivalGate",
            "lifecycleAuthority",
            "candidates",
            "externalSupportRequirements",
        ):
            self.assertIn(required, proposal["required"])
        for true_flag in (
            "freshSafetyAssessmentRequired",
            "freshNpcCensusRequired",
            "reassessmentRequired",
        ):
            self.assertTrue(proposal["properties"][true_flag]["const"])
        for false_flag in (
            "candidateSelectionMutated",
            "fixedPointClosureProved",
            "loadRequest",
            "entityRegistry",
            "arrivalGate",
            "lifecycleAuthority",
        ):
            self.assertFalse(proposal["properties"][false_flag]["const"])
        self.assertEqual(
            8192, proposal["properties"]["candidates"]["maxItems"]
        )
        self.assertEqual(
            8192,
            proposal["properties"]["externalSupportRequirements"][
                "maxItems"
            ],
        )
        candidate = v28["$defs"]["candidateSource"]
        for provenance in (
            "originalSafetySource",
            "authoredCohortSource",
            "externalStaticSupportSource",
            "selectedOwnerCurrentSourceInstanceCount",
            "externalOwnerAuthoredSourceInstanceCount",
            "addedBeyondOriginalSafety",
            "freshSafetyEvidenceRequired",
            "freshNpcCensusRequired",
        ):
            self.assertIn(provenance, candidate["required"])

    def test_observer_derives_refinement_from_the_same_event_parents(self):
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v44"', observer
        )
        self.assertIn("MAX_TRACE_RETIREMENT_REFINEMENT_CANDIDATES", observer)
        self.assertIn("MAX_TRACE_RETIREMENT_REFINEMENT_SUPPORT", observer)
        self.assertRegex(
            observer,
            r"LayeredPackedRegionRetirementRefinementProposal"
            r"\s+\.propose\(\s+packedRegionRetirementSafety,"
            r"\s+packedRegionAuthoredReconstructionCohort,"
            r"\s+packedRegionActiveNpcBoundaryRequirements,",
        )
        self.assertIn("appendPackedRegionRetirementRefinement", observer)
        self.assertNotIn("PackedRegionRetirementRefinementSource", observer)

    def test_living_plan_records_slice_seventy_eight_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 78: Retirement refinement diagnostics", plan
        )
        self.assertIn("schema-v28", plan)
        self.assertIn("same event", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
