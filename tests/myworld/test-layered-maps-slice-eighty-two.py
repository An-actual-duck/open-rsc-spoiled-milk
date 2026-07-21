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
SCHEMA_V28 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v28.schema.json"
)
SCHEMA_V29 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v29.schema.json"
)
README = ROOT / "tools/layered-maps/README.md"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceEightyTwoTest(unittest.TestCase):
    def test_v29_contract_is_additive_and_explicit_about_deferral(self):
        v28 = json.loads(SCHEMA_V28.read_text(encoding="utf-8"))
        v29 = json.loads(SCHEMA_V29.read_text(encoding="utf-8"))
        field = "packedRegionRetirementRefinementReassessment"

        self.assertEqual(
            "layered-map-parity-event-v28",
            v28["properties"]["schema"]["const"],
        )
        self.assertNotIn(field, v28["properties"])
        self.assertEqual(
            "layered-map-parity-event-v29",
            v29["properties"]["schema"]["const"],
        )
        self.assertIn(field, v29["required"])
        attempt = v29["$defs"]["reassessmentAttempt"]
        for required in (
            "status", "attempted", "deferredNotNewer",
            "pendingBeforeCandidateSourceCount",
            "pendingAfterCandidateSourceCount", "pendingRetained",
            "reassessment",
        ):
            self.assertIn(required, attempt["required"])
        self.assertIn(
            "DEFERRED_NOT_NEWER",
            attempt["properties"]["status"]["enum"],
        )

        result = v29["$defs"]["reassessmentResult"]
        for required in (
            "freshSafety", "newCandidates", "nextProposal",
            "retirementReadinessEvidence", "freshEvidenceAligned",
            "candidateSetStableAtObservation",
            "furtherRefinementRequired", "nonExpandableHardBlockers",
            "refinementConvergedAtObservation", "pointInTimeOnly",
            "candidateSelectionMutated",
            "fixedPointLifecycleClosureProved", "loadRequest",
            "entityRegistry", "arrivalGate", "retirementCommitToken",
            "lifecycleAuthority",
        ):
            self.assertIn(required, result["required"])
        for false_flag in (
            "retirementReadinessEvidence", "candidateSelectionMutated",
            "fixedPointLifecycleClosureProved", "loadRequest",
            "entityRegistry", "arrivalGate", "retirementCommitToken",
            "lifecycleAuthority",
        ):
            self.assertFalse(result["properties"][false_flag]["const"])
        self.assertTrue(result["properties"]["pointInTimeOnly"]["const"])
        safety_entry = v29["$defs"]["diagnosticRetirementSafetyEntry"]
        self.assertEqual(
            "DIAGNOSTIC_SELECTION_ONLY",
            safety_entry["properties"]["readinessState"]["const"],
        )
        self.assertFalse(
            safety_entry["properties"]["lifecycleReady"]["const"]
        )
        readme = README.read_text(encoding="utf-8")
        self.assertIn("layered-map-parity-event-v29.schema.json", readme)
        self.assertIn("DEFERRED_NOT_NEWER", readme)

    def test_observer_retains_only_one_immutable_pending_proposal(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v36"', source
        )
        self.assertIn(
            "PackedRegionRetirementRefinementReassessmentSource", source
        )
        self.assertIn(
            "pendingPackedRegionRetirementRefinement", source
        )
        self.assertIn(
            "RetirementRefinementReassessmentMetadata.deferred", source
        )
        self.assertIn("DEFERRED_NOT_NEWER", source)
        self.assertIn(
            "!reassessment.isRefinementConvergedAtObservation()", source
        )
        self.assertIn(
            "? reassessment.getNextProposal() : null", source
        )
        self.assertIn(
            "if (!hadPendingRefinementAtEventStart", source
        )
        self.assertIn(
            "appendPackedRegionRetirementRefinementReassessment", source
        )
        for forbidden in (
            "pendingPackedRegionRetirementRefinements",
            "List<LayeredPackedRegionRetirementRefinementProposal> pending",
        ):
            self.assertNotIn(forbidden, source)

    def test_private_runtime_sources_use_the_atomic_region_manager_seam(self):
        method = (
            "captureLayeredPackedRegionRetirementRefinementReassessmentIfFresh"
        )
        for path in (PLAYER, COMMAND):
            source = path.read_text(encoding="utf-8")
            self.assertIn(method, source)
            self.assertIn(
                "layeredPackedRegionRetirementRefinementReassessmentSource",
                source,
            )
            self.assertIn("getAuthoredReconstructionRecipe()", source)

    def test_living_plan_records_slice_eighty_two_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 82: Stateful refinement reassessment diagnostics",
            plan,
        )
        self.assertIn("schema-v29", plan)
        self.assertIn("latest immutable proposal", plan)
        self.assertIn("Private owner validation status:", plan)
        self.assertIn("`EXPANDED` 40-to-41 transition", plan)
        self.assertIn("`STABLE`, and cleared the pending proposal", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
