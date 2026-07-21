#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
REASSESSMENT = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionRetirementRefinementReassessment.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceEightyOneTest(unittest.TestCase):
    def test_shared_tick_must_be_newer_than_both_previous_parents(self):
        source = REASSESSMENT.read_text(encoding="utf-8")
        self.assertIn("isFreshObservationTick", source)
        self.assertIn(
            "observedAtTick > previous.getSafetyObservedAtTick()", source
        )
        self.assertIn(
            "observedAtTick > previous.getCensusObservedAtTick()", source
        )
        fixture = (
            ROOT / "tests/myworld/test-layered-maps-slice-seventy-nine.py"
        ).read_text(encoding="utf-8")
        self.assertIn("isFreshObservationTick(previous, 13L)", fixture)
        self.assertIn("isFreshObservationTick(previous, 14L)", fixture)

    def test_region_manager_composes_one_read_only_reassessment_callback(self):
        source = REGION_MANAGER.read_text(encoding="utf-8")
        method_name = (
            "captureLayeredPackedRegionRetirementRefinementReassessmentIfFresh"
        )
        start = source.index(method_name)
        end = source.index("\n\tprivate ", start)
        method = source[start:end]
        self.assertIn("synchronized (layeredRegionLifecycleLock)", method)
        self.assertIn("isFreshObservationTick", method)
        self.assertIn("return null;", method)
        self.assertIn(
            "assessLayeredPackedRegionRetirementRefinementCandidatesLocked",
            method,
        )
        self.assertIn(
            "LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze",
            method,
        )
        self.assertIn("captureActiveNpcResidency", method)
        self.assertIn(
            "LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project",
            method,
        )
        self.assertIn(
            "LayeredPackedRegionRetirementRefinementReassessment.reassess",
            method,
        )
        for forbidden in (
            "getRegion(", "register", "unload", "remove", "evict",
            "setLocation", "teleport",
        ):
            self.assertNotIn(forbidden, method)
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertNotIn(method_name, observer)

    def test_living_plan_records_slice_eighty_one_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 81: Same-tick refinement reassessment source", plan
        )
        self.assertIn("same-tick", plan)
        self.assertIn("No lifecycle authority", plan)


if __name__ == "__main__":
    unittest.main()
