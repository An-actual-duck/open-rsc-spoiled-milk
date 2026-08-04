#!/usr/bin/env python3
"""Regression coverage for bounded same-tick scene-baseline delivery."""

from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / "server/src/com/openrsc/server/SceneBaselineDeliveryPolicy.java"
UPDATER = ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"


class SceneBaselineDeliveryPolicyTest(unittest.TestCase):
    def test_limits_accounting_and_atomic_completion(self):
        harness = textwrap.dedent(
            """
            package com.openrsc.server;

            public final class SceneBaselineDeliveryPolicyHarness {
                public static void main(String[] arguments) {
                    require(
                        SceneBaselineDeliveryPolicy.remainingPageCount(
                            0, 0, 512) == 0,
                        "empty category has no pages");
                    require(
                        SceneBaselineDeliveryPolicy.remainingPageCount(
                            512, 0, 512) == 1,
                        "exact page count");
                    require(
                        SceneBaselineDeliveryPolicy.remainingPageCount(
                            1025, 1, 512) == 2,
                        "cursor leaves one full and one partial page");
                    require(
                        SceneBaselineDeliveryPolicy.remainingPageCount(
                            1025, 3, 512) == 0,
                        "completed cursor has no pages");

                    long wireBytes =
                        SceneBaselineDeliveryPolicy.remainingWireBytes(
                            1025, 1, 512, 74, 12, 3);
                    require(wireBytes == (2L * 77L) + (513L * 12L),
                        "wire bytes include exact records and frame overhead");

                    SceneBaselineDeliveryPolicy.Decision legacy =
                        SceneBaselineDeliveryPolicy.decide(
                            4, false, 20, 120000L);
                    require(legacy.getPageLimit() == 4,
                        "legacy standard limit retained");
                    require(legacy.getMode()
                            == SceneBaselineDeliveryPolicy.Mode.STANDARD_BOUNDED,
                        "legacy delivery remains bounded");

                    SceneBaselineDeliveryPolicy.Decision layered =
                        SceneBaselineDeliveryPolicy.decide(
                            8, false, 11, 52000L);
                    require(layered.getPageLimit() == 8,
                        "ordinary layered standard limit retained");

                    SceneBaselineDeliveryPolicy.Decision observedDense =
                        SceneBaselineDeliveryPolicy.decide(
                            8, true, 11, 52000L);
                    require(observedDense.getPageLimit() == 11,
                        "observed eleven-page activation completes same tick");
                    require(observedDense.completesAtomicProduct(),
                        "observed dense activation selects atomic completion");

                    SceneBaselineDeliveryPolicy.Decision exactCaps =
                        SceneBaselineDeliveryPolicy.decide(
                            8,
                            true,
                            SceneBaselineDeliveryPolicy
                                .ATOMIC_COMPLETE_MAX_PAGES,
                            SceneBaselineDeliveryPolicy
                                .ATOMIC_COMPLETE_MAX_WIRE_BYTES);
                    require(exactCaps.getPageLimit()
                            == SceneBaselineDeliveryPolicy
                                .ATOMIC_COMPLETE_MAX_PAGES,
                        "exact caps remain eligible");

                    SceneBaselineDeliveryPolicy.Decision pageOverflow =
                        SceneBaselineDeliveryPolicy.decide(
                            8,
                            true,
                            SceneBaselineDeliveryPolicy
                                .ATOMIC_COMPLETE_MAX_PAGES + 1,
                            52000L);
                    require(pageOverflow.getPageLimit() == 8,
                        "page overflow falls back to bounded paging");
                    require(pageOverflow.getMode()
                            == SceneBaselineDeliveryPolicy.Mode
                                .ATOMIC_OVERSIZED_FALLBACK,
                        "page overflow is diagnostically explicit");

                    SceneBaselineDeliveryPolicy.Decision byteOverflow =
                        SceneBaselineDeliveryPolicy.decide(
                            8,
                            true,
                            11,
                            SceneBaselineDeliveryPolicy
                                .ATOMIC_COMPLETE_MAX_WIRE_BYTES + 1L);
                    require(byteOverflow.getPageLimit() == 8,
                        "byte overflow falls back to bounded paging");

                    SceneBaselineDeliveryPolicy.Decision empty =
                        SceneBaselineDeliveryPolicy.decide(
                            8, true, 0, 0L);
                    require(empty.getPageLimit() == 0,
                        "empty product sends no pages");
                    require(empty.getMode()
                            == SceneBaselineDeliveryPolicy.Mode.EMPTY,
                        "empty product is explicit");

                    expectIllegalArgument("zero standard limit", new Action() {
                        public void run() {
                            SceneBaselineDeliveryPolicy.decide(
                                0, false, 1, 1L);
                        }
                    });
                    expectIllegalArgument("negative remaining work", new Action() {
                        public void run() {
                            SceneBaselineDeliveryPolicy.decide(
                                8, true, -1, 1L);
                        }
                    });
                    expectIllegalArgument("negative category cursor", new Action() {
                        public void run() {
                            SceneBaselineDeliveryPolicy.remainingPageCount(
                                1, -1, 512);
                        }
                    });
                    expectIllegalArgument("zero page size", new Action() {
                        public void run() {
                            SceneBaselineDeliveryPolicy.remainingWireBytes(
                                1, 0, 0, 74, 12, 3);
                        }
                    });
                }

                private interface Action {
                    void run();
                }

                private static void expectIllegalArgument(
                        String label, Action action) {
                    boolean rejected = false;
                    try {
                        action.run();
                    } catch (IllegalArgumentException expected) {
                        rejected = true;
                    }
                    require(rejected, label);
                }

                private static void require(
                        boolean condition, String label) {
                    if (!condition) {
                        throw new AssertionError(label);
                    }
                }
            }
            """
        )
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            harness_path = work / "SceneBaselineDeliveryPolicyHarness.java"
            harness_path.write_text(harness, encoding="utf-8")
            subprocess.run(
                [
                    "javac",
                    "-source",
                    "1.8",
                    "-target",
                    "1.8",
                    "-d",
                    str(work),
                    str(POLICY),
                    str(harness_path),
                ],
                check=True,
                cwd=ROOT,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    str(work),
                    "com.openrsc.server.SceneBaselineDeliveryPolicyHarness",
                ],
                check=True,
                cwd=ROOT,
            )

    def test_game_state_integration_is_transactional_and_bounded(self):
        updater = UPDATER.read_text(encoding="utf-8")
        method = updater.split(
            "private void sendSceneBaselineIfEnabled(", 1
        )[1].split(
            "private void clearCompletedAtomicSceneBaseline(", 1
        )[0]
        self.assertIn("remainingSceneBaselinePageCount(current)", method)
        self.assertIn("remainingSceneBaselineWireBytes(current)", method)
        self.assertIn("SceneBaselineDeliveryPolicy.decide(", method)
        self.assertIn("while (sentPages < pageBurstLimit)", method)
        self.assertIn("if (!sendSceneBaselinePacket(player, current, page))", method)
        self.assertLess(
            method.index("if (!sendSceneBaselinePacket(player, current, page))"),
            method.index("current.recordSentPage(page);"),
            "page ownership must advance only after successful queueing",
        )
        self.assertIn("ATOMIC_OVERSIZED_FALLBACK", POLICY.read_text())
        self.assertIn("remainingBeforeWireBytes", method)
        self.assertIn("completeCapWireBytes", method)
        self.assertIn(
            "ATOMIC_SCENE_BASELINE_PENDING_SEQUENCE_ATTRIBUTE", updater
        )
        self.assertIn("clearCompletedAtomicSceneBaseline(", updater)


if __name__ == "__main__":
    unittest.main()
