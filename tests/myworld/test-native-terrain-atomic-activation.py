#!/usr/bin/env python3

import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class NativeTerrainAtomicActivationTest(unittest.TestCase):
    def test_client_barrier_requires_exact_player_and_baseline_receipts(self):
        harness = textwrap.dedent(
            """
            package orsc;

            public final class LayeredSceneActivationStateHarness {
                public static void main(String[] arguments) {
                    LayeredSceneActivationState state =
                        new LayeredSceneActivationState();
                    require(!state.isPending(), "initial state");

                    state.begin(17);
                    require(state.isPending(), "begin");
                    state.acceptPlayerReceipt(16);
                    state.acceptStaticBaseline(18);
                    require(state.isPending(), "mismatched receipts");

                    state.acceptStaticBaseline(17);
                    require(state.isPending(), "baseline alone");
                    state.acceptPlayerReceipt(17);
                    require(!state.isPending(), "exact pair");
                    require(state.summary().contains("ready seq 17"),
                        "ready summary");

                    state.begin(18);
                    state.acceptPlayerReceipt(18);
                    require(state.isPending(), "player alone");
                    state.acceptStaticBaseline(18);
                    require(!state.isPending(), "reverse exact pair");
                    require(state.summary().contains("done 2"),
                        "completion count");

                    expectFailure(() -> state.begin(18),
                        "duplicate sequence");
                    expectFailure(() -> state.begin(0),
                        "non-positive sequence");

                    state.reset();
                    require(!state.isPending(), "reset pending");
                    require(!state.hasStarted(), "reset sequence");
                }

                private static void expectFailure(
                        Runnable action, String label) {
                    try {
                        action.run();
                        throw new AssertionError(label);
                    } catch (IllegalArgumentException expected) {
                        // Expected.
                    }
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
            harness_path = work / "LayeredSceneActivationStateHarness.java"
            harness_path.write_text(harness, encoding="utf-8")
            subprocess.run(
                [
                    "javac",
                    "-Xlint:all",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-d",
                    str(work),
                    str(
                        ROOT
                        / "Client_Base/src/orsc/"
                        "LayeredSceneActivationState.java"
                    ),
                    str(harness_path),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    str(work),
                    "orsc.LayeredSceneActivationStateHarness",
                ],
                cwd=ROOT,
                check=True,
            )

    def test_v8_is_opt_in_atomic_and_v7_remains_rollback(self):
        configuration = (
            ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
        ).read_text(encoding="utf-8")
        updater = (
            ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
        ).read_text(encoding="utf-8")
        context = (
            ROOT / "Client_Base/src/orsc/LayeredSceneContextState.java"
        ).read_text(encoding="utf-8")
        decoder = (
            ROOT
            / "Client_Base/src/orsc/NativeLayeredTerrainPacketDecoder.java"
        ).read_text(encoding="utf-8")
        snapshot = (
            ROOT / "Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "WANT_LAYERED_NATIVE_TERRAIN_ATOMIC_ACTIVATION",
            configuration,
        )
        self.assertIn(
            '"OPENRSC_LAYERED_NATIVE_TERRAIN_ATOMIC_ACTIVATION"',
            configuration,
        )
        self.assertIn(
            '"want_layered_native_terrain_atomic_activation",\n'
            "\t\t\t\tfalse);",
            configuration,
        )
        self.assertIn(
            "ATOMIC_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION = 8",
            updater,
        )
        self.assertIn("usesAtomicActivation()", updater)
        self.assertIn("WANT_SYNC_SCENE_BASELINE", updater)
        self.assertIn(
            "READY_RESIDENT_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION",
            updater,
        )
        self.assertIn(
            "ATOMIC_NATIVE_LAYERED_PROTOCOL_VERSION", context
        )
        self.assertIn("decodeV8(", decoder)
        self.assertIn(
            "ATOMIC_ACTIVATION_PROTOCOL_VERSION = 8", snapshot
        )

    def test_cover_waits_for_complete_v8_scene_after_validated_fence(self):
        handler = (
            ROOT / "Client_Base/src/orsc/PacketHandler.java"
        ).read_text(encoding="utf-8")
        baseline = (
            ROOT / "Client_Base/src/orsc/SceneBaselineState.java"
        ).read_text(encoding="utf-8")
        client = (
            ROOT / "Client_Base/src/orsc/mudclient.java"
        ).read_text(encoding="utf-8")
        applet = (
            ROOT / "PC_Client/src/orsc/ORSCApplet.java"
        ).read_text(encoding="utf-8")
        updater = (
            ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "layeredSceneActivationState.begin(sequence);", handler
        )
        self.assertIn(
            "final boolean hadLayeredSceneContext =", handler
        )
        self.assertIn(
            "mc.beginLayeredSceneActivation(\n"
            "\t\t\t\thadLayeredSceneContext && !result.isScopeChanged());",
            handler,
        )
        self.assertIn(
            "layeredSceneActivationState.acceptPlayerReceipt(", handler
        )
        self.assertIn(
            "private void acceptLayeredPlayerPosition(", handler
        )
        self.assertEqual(
            handler.count("acceptLayeredPlayerPosition("),
            4,
            "Player, movement-update, and movement-snapshot paths must share "
            "one position-receipt gate",
        )
        self.assertIn(
            "if (layeredSceneContextState.acceptLegacyPlayerPosition(",
            handler,
        )
        self.assertIn(
            "layeredSceneActivationState.acceptStaticBaseline(", handler
        )
        self.assertIn(
            "sceneBaselineState.matchAtomicFence(", handler
        )
        fence_block = handler.split(
            "if (protocolVersion\n"
            "\t\t\t\t== SceneBaselineState.ATOMIC_FENCE_PROTOCOL_VERSION",
            1,
        )[1].split(
            "sceneBaselineState.recordPacket(", 1
        )[0]
        self.assertNotIn(
            "acceptStaticBaseline(", fence_block,
            "the inner-scene fence must not publish before the outer "
            "presentation ring arrives",
        )
        self.assertLess(
            handler.index(
                "sceneBaselineState.pruneLegacyListsOutsideAtomicFenceRange("
            ),
            handler.index("sceneBaselineState.matchAtomicFence("),
            "stale off-window objects must be pruned before fence validation",
        )
        self.assertIn(
            "ATOMIC_SCENE_FENCE seq ", handler
        )
        self.assertIn(
            "sceneBaselineState.getLocationContextSequence()", handler
        )
        self.assertIn(
            "appliedSceneBaselineKey\n"
            "\t\t\t\t!= sceneBaselineState.legacyApplyKey(mc)",
            handler,
        )
        self.assertIn(
            "appliedScenePresentationKey\n"
            "\t\t\t\t\t!= sceneBaselineState.presentationApplyKey(mc)",
            handler,
            "atomic publication must wait until the complete presentation "
            "product is installed",
        )
        self.assertEqual(
            handler.count("acceptCompleteAtomicSceneBaseline();"),
            2,
            "normal and deferred baseline materialization must release",
        )
        self.assertIn(
            "hash = hash * 31 + locationContextSequence;", baseline
        )
        self.assertIn(
            "this.locationContextSequence = locationContextSequence;",
            baseline,
        )
        self.assertIn(
            "ATOMIC_FENCE_PROTOCOL_VERSION = 7", baseline
        )
        self.assertIn(
            "PAGE_ATOMIC_FENCE = 3", baseline
        )
        self.assertIn(
            "storedBaselineMatchesLegacy", baseline
        )
        self.assertIn(
            "recordLegacyBaselineVerified", handler
        )
        self.assertIn(
            "} else if (this.layeredSceneActivationPending) {", client
        )
        self.assertIn(
            "this.layeredSceneActivationPending\n"
            "\t\t\t\t\t\t\t&& this.shouldRetainLastPresentedFrame()",
            client,
        )
        self.assertIn(
            "public boolean shouldRetainLastPresentedFrame()", client
        )
        self.assertIn(
            "completeLayeredSceneActivationFreshFrame(",
            client,
        )
        self.assertIn(
            "mudclient.shouldRetainLastPresentedFrame()", applet
        )
        self.assertIn(
            "&& !this.layeredSceneActivationPending", client
        )
        self.assertIn(
            "summary.samePagedScenePayload(previous)",
            updater,
        )
        self.assertIn(
            "locationContextSequence\n"
            "\t\t\t\t\t== other.locationContextSequence",
            updater,
        )
        self.assertIn(
            "private boolean requiresBlockingReadiness()", updater
        )
        self.assertIn(
            "return requiresReadiness() && !usesAtomicActivation();",
            updater,
        )
        self.assertIn(
            "return !nativeTerrain.requiresBlockingReadiness();",
            updater,
        )
        self.assertIn(
            "private boolean hasEstablishedLayeredSceneContext(",
            updater,
        )
        self.assertIn(
            "return ensureLayeredSceneContext(player, false);",
            updater,
        )
        self.assertGreaterEqual(
            updater.count(
                "if (!hasEstablishedLayeredSceneContext(player))"
            ),
            2,
            "high-frequency movement streams must not originate "
            "an atomic scene context",
        )
        self.assertIn(
            "maybeSendNativeTerrainSymmetricResidency(\n"
            "\t\t\t\t\tplayer, location, nativeTerrain);",
            updater,
        )
        self.assertIn(
            "if (result.isScopeChanged()) {\n"
            "\t\t\t\tmc.clearStaticScenePresentation();\n"
            "\t\t\t}",
            handler,
            "same-scope atomic shifts must retain the old static ring "
            "until its replacement is complete",
        )
        self.assertIn(
            "protocolVersion == 2\n"
            "\t\t\t\t&& layeredSceneActivationState.isPending()",
            handler,
        )

    def test_predicted_native_window_is_built_before_readiness_ack(self):
        handler = (
            ROOT / "Client_Base/src/orsc/PacketHandler.java"
        ).read_text(encoding="utf-8")
        client = (
            ROOT / "Client_Base/src/orsc/mudclient.java"
        ).read_text(encoding="utf-8")
        world = (
            ROOT / "Client_Base/src/orsc/graphics/three/World.java"
        ).read_text(encoding="utf-8")
        updater = (
            ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
        ).read_text(encoding="utf-8")

        stage_method = handler.split(
            "private void updateLayeredTerrainStage", 1
        )[1].split(
            "public void pollLayeredTerrainStageReady", 1
        )[0]
        poll_method = handler.split(
            "public void pollLayeredTerrainStageReady", 1
        )[1].split(
            "private void sendLayeredTerrainStageReady", 1
        )[0]
        self.assertIn(
            "preloadNativeLayeredTerrainSnapshot(", stage_method
        )
        self.assertNotIn(
            "sendLayeredTerrainStageReady(", stage_method,
            "receipt alone must not claim that the predicted world is ready",
        )
        self.assertIn("if (pending == null || !pending.isDone())", poll_method)
        self.assertLess(
            poll_method.index(
                "canActivateNativeLayeredTerrainPrebuild("
            ),
            poll_method.index("sendLayeredTerrainStageReady("),
        )
        self.assertIn(
            "this.packetHandler.pollLayeredTerrainStageReady();", client
        )
        self.assertIn(
            "buildNativeLayeredTerrainPreload(", world
        )
        self.assertIn(
            "buildNativeLayeredCpuSectionWindow(", world
        )
        self.assertIn(
            "buildWorldGpuChunkMesh(", world
        )
        self.assertIn(
            "publishPredictiveRenderer3DWorldFringe(", world
        )
        self.assertIn(
            "toRenderer3DWorldChunkFringe(", world
        )
        self.assertIn(
            "predictiveRenderer3DWorldChunkFrame", world
        )
        self.assertIn(
            "incomingX || incomingZ", world
        )
        self.assertIn(
            "int fringeVertexCount = includedTriangles * 3;", world
        )
        self.assertIn(
            "fringeIndices[targetVertex] = targetVertex;", world
        )
        self.assertIn(
            "activeFrame.getChunks()", world
        )
        self.assertIn(
            "NATIVE_TERRAIN_FRINGE", world
        )
        prebuild_method = world.split(
            "buildNativeLayeredTerrainPreload(", 1
        )[1].split(
            "private CpuSectionWindow buildNativeLayeredCpuSectionWindow", 1
        )[0]
        self.assertIn(
            "stagedTerrain.getCurrentChunkX(), SECTION_SIZE",
            prebuild_method,
        )
        self.assertNotIn(
            "SECTION_SIZE / 2", prebuild_method,
            "the chunk ID already uses World.worldTileToSection rounding",
        )
        self.assertIn(
            "result.sourceRevision == worldEditorTerrainRevision", world
        )
        self.assertIn(
            "result.stagedScopeIdentity.equals(", world
        )
        self.assertIn("NATIVE_TERRAIN_PREBUILD", world)
        self.assertIn(
            "sendAtomicSceneActivationFenceIfNeeded(player);",
            updater,
        )
        self.assertIn(
            "ATOMIC_SCENE_FENCE_PROTOCOL_VERSION = 7",
            updater,
        )
        self.assertIn(
            "SCENE_BASELINE_PAGE_ATOMIC_FENCE = 3",
            updater,
        )
        self.assertIn(
            "summarizeWireSceneGameObjects(", updater
        )
        self.assertIn(
            "while (sentPages < pageBurstLimit)",
            updater,
        )
        self.assertNotIn(
            "ATOMIC_SCENE_BASELINE_PAGE_BURST_LIMIT",
            updater,
        )
        self.assertLess(
            updater.index("sendAtomicSceneActivationFenceIfNeeded(player);"),
            updater.index(
                "sendSceneBaselineIfEnabled(\n"
                "\t\t\tplayer,\n"
                "\t\t\tsceneryChanged[0]"
            ),
            "validated fence must be ordered before background baseline pages",
        )
        self.assertIn(
            "ATOMIC_SCENE_ACTIVATION ", handler
        )


if __name__ == "__main__":
    unittest.main()
