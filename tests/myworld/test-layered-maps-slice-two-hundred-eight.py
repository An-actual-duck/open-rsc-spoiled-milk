#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CORRELATION = ROOT / (
    "server/src/com/openrsc/server/model/world/region/"
    "LayeredPackedRegionAuthoredDetachmentSchedulerCorrelation.java"
)
SLICE_206 = ROOT / (
    "tests/myworld/test-layered-maps-slice-two-hundred-six.py"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceTwoHundredEightTest(unittest.TestCase):
    def test_owner_index_preserves_identity_without_selected_source_gate(self):
        source = CORRELATION.read_text(encoding="utf-8")
        owner_index = source.split(
            "private static Map<Long, OwnerRequirement> "
            "indexOwnerRequirements(",
            1,
        )[1].split(
            "private static void validateOwnerFenceEvent(",
            1,
        )[0]
        self.assertIn(
            "owner.getGeneration() != requirements.getGeneration()",
            owner_index,
        )
        self.assertNotIn("selectedSourceOrdinals", owner_index)
        self.assertNotIn(
            "NPC owner requirement is outside the detachment sources",
            source,
        )

    def test_compiled_fixture_covers_related_outside_source_owner(self):
        fixture = SLICE_206.read_text(encoding="utf-8")
        self.assertIn(
            "new NpcOwnerIdentity(9L, 5, 0, 30, 10)",
            fixture,
        )
        self.assertIn(
            ".SourcePlan(\n"
            "                            0, 4, 0,",
            fixture,
        )
        self.assertIn(
            "correlation.getRelatedNpcOwnerFenceEventCount() == 1",
            fixture,
        )
        self.assertIn(
            "correlation.getSupportingNpcOwnerFenceEventCount() == 1",
            fixture,
        )

    def test_plan_records_owner_route_failure_and_safety_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 208: Outside-selection NPC-owner correlation repair",
            plan,
        )
        self.assertIn("450 related NPC-owner event links", plan)
        self.assertIn("detached classification", plan)


if __name__ == "__main__":
    unittest.main()
