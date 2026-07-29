#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/"
    "Development.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


def event_source(text, signature):
    start = text.index(signature)
    end = text.index("\n\tprivate ", start + len(signature))
    return text[start:end]


class LayeredMapsSliceOneHundredSixtyThreeTest(unittest.TestCase):
    def test_both_runtime_sources_delegate_owner_continuity(self):
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
            self.assertEqual(1, source.count("captureNpcOwnerContinuity("))
            self.assertEqual(
                1,
                source.count(
                    "captureLayeredPackedRegionNpcOwnerEventContinuity("
                ),
            )
            self.assertEqual(
                1,
                source.count("getAuthoredReconstructionRecipe()"),
            )

    def test_command_start_and_session_rebind_use_guarded_sources(self):
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        command_start = development.index(
            "private void layeredCoordinateParity("
        )
        command_end = development.index(
            "private LayeredCoordinateParityObserver.TileSnapshotSource",
            command_start,
        )
        command = development[command_start:command_end]
        self.assertIn(
            "layeredPackedRegionEventOwnershipSource(player)",
            command,
        )
        self.assertIn(
            "loggedIn ? layeredPackedRegionEventOwnershipSource() : null",
            player,
        )
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(
            ".captureNpcOwnerContinuity(",
            observer,
        )

    def test_plan_records_incomplete_capture_and_corrective_slice(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 163: Complete private continuity-source wiring",
            plan,
        )
        normalized = " ".join(plan.split())
        self.assertIn(
            "null continuity block was a source-wiring omission",
            normalized,
        )
        self.assertIn(
            "not an NPC continuity result",
            normalized,
        )


if __name__ == "__main__":
    unittest.main()
