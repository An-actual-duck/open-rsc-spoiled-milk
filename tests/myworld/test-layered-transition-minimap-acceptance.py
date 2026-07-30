#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "Client_Base/src/orsc/graphics/three/World.java"
GRAPHICS = ROOT / "Client_Base/src/orsc/graphics/two/GraphicsController.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
TELEMETRY = ROOT / "Client_Base/src/orsc/RenderTelemetry.java"
PRESENTER = ROOT / "PC_Client/src/orsc/OpenGLFramePresenter.java"
WORLD_CHUNK_FRAME = (
    ROOT
    / "Client_Base/src/orsc/graphics/three/"
    "Renderer3DWorldChunkFrame.java"
)
WORLD_CHUNK_RENDERER = (
    ROOT / "PC_Client/src/orsc/OpenGLWorldChunkRenderer.java"
)
OPENGL_SHADER = ROOT / "PC_Client/src/orsc/OpenGLShaderProgram.java"
PRESENTATION_LATCH = (
    ROOT / "Client_Base/src/orsc/LayeredScenePresentationLatch.java"
)
PACKET_DRAIN_POLICY = (
    ROOT / "Client_Base/src/orsc/LayeredScenePacketDrainPolicy.java"
)


class LayeredTransitionMinimapAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.world = WORLD.read_text(encoding="utf-8")
        cls.graphics = GRAPHICS.read_text(encoding="utf-8")
        cls.client = CLIENT.read_text(encoding="utf-8")
        cls.telemetry = TELEMETRY.read_text(encoding="utf-8")
        cls.presenter = PRESENTER.read_text(encoding="utf-8")
        cls.world_chunk_frame = WORLD_CHUNK_FRAME.read_text(
            encoding="utf-8"
        )
        cls.world_chunk_renderer = WORLD_CHUNK_RENDERER.read_text(
            encoding="utf-8"
        )
        cls.opengl_shader = OPENGL_SHADER.read_text(encoding="utf-8")

    def test_native_minimap_owns_the_complete_active_window(self):
        self.assertIn(
            "NATIVE_MINIMAP_FACE_TILE_COUNT = LOCAL_FACE_TILE_COUNT",
            self.world,
        )
        self.assertIn(
            "new NativeMinimapRaster(\n"
            "\t\t\t\t\t\t\tNATIVE_MINIMAP_FACE_TILE_COUNT)",
            self.world,
        )
        self.assertIn(
            "this.drawMinimapTile(nativeMinimap, face);",
            self.world,
        )
        self.assertIn(
            "drawWallSegmentMinimap(\n"
            "\t\t\t\t\t\tnativeMinimap, segment, wallColor);",
            self.world,
        )
        self.assertIn(
            "this.minimapGraphics.publishMinimapRaster(",
            self.world,
        )
        self.assertIn(
            "rowMajorPixels[x + y * width]",
            self.graphics,
        )

        section_size = 48
        native_faces = section_size * 3 - 1
        legacy_faces = section_size * 2 - 1
        self.assertEqual(native_faces, 143)
        self.assertEqual(native_faces * 3, 429)
        self.assertEqual(
            6040 + (native_faces - legacy_faces) * 64,
            9112,
        )

    def test_native_minimap_center_tracks_the_larger_raster(self):
        self.assertIn(
            "public int getMinimapLocalCenterPixel()",
            self.world,
        )
        self.assertIn(
            "this.world.getMinimapLocalCenterPixel();",
            self.client,
        )
        self.assertNotIn(
            "this.localPlayer.currentX - 6040",
            self.client,
        )
        self.assertNotIn(
            "this.localPlayer.currentZ - 6040",
            self.client,
        )

    def test_legacy_minimap_and_active_click_authority_remain_bounded(self):
        self.assertIn(
            "isLegacyMinimapFaceTile(segment.x, segment.z)",
            self.world,
        )
        self.assertIn(
            "MINIMAP_PIXEL_SIZE, MINIMAP_PIXEL_SIZE",
            self.world,
        )
        self.assertIn(
            "activeGameplayTargetToward(",
            self.client,
        )
        self.assertIn(
            "isTerrainLoadedAtLocalTile",
            self.world,
        )

    def test_protocol_two_prebuilds_the_exact_foreground_product(self):
        self.assertIn(
            "prebuildNativeWorldModelProduct(",
            self.world,
        )
        self.assertIn(
            '"NATIVE_TERRAIN_CONTEXT_PRODUCT"',
            self.world,
        )
        self.assertIn(
            "return sectorPreloadExecutor.submit(",
            self.world,
        )
        self.assertIn(
            '+ "-floor-local";',
            self.world,
        )
        self.assertIn(
            "activeProductMs=",
            self.world,
        )
        self.assertIn(
            "result.sourceRevision != worldEditorTerrainRevision",
            self.world,
        )
        self.assertIn(
            "result.includeRoofGeometry != !Config.C_HIDE_ROOFS",
            self.world,
        )
        self.assertIn(
            "window.hasNativeLayeredTerrain()",
            self.world,
        )
        self.assertIn(
            "nativeLayeredTerrainAppliedSectionX = sectionX;",
            self.world,
        )
        self.assertIn(
            "nativeLayeredTerrainAppliedSectionY = sectionY;",
            self.world,
        )

    def test_protocol_two_publishes_a_complete_terrain_horizon(self):
        self.assertIn(
            'terrainOnly ? "terrain-ready" : "structure"',
            self.world,
        )
        self.assertIn(
            "includeRoofGeometry && !terrainOnly",
            self.world,
        )
        self.assertIn(
            "new WallModelInput(new WallSegmentInput[0])",
            self.world,
        )
        self.assertIn(
            "symmetricOuterRenderer3DWorldChunkFrame =\n"
            "\t\t\tRenderer3DWorldChunkFrame.fromChunks(result.outerChunks);",
            self.world,
        )
        self.assertIn(
            "Keep the retained structural set\n"
            "\t\t * unchanged",
            self.world,
        )
        self.assertNotIn(
            '"halo detail=terrain-cache src="',
            self.world,
        )
        self.assertNotIn(
            '" mesh=deferred activeProduct="',
            self.world,
        )

    def test_cardinal_border_shift_reuses_eighteen_of_twenty_four_cells(self):
        old_cells = {
            (x, y)
            for x in range(-2, 3)
            for y in range(-2, 3)
            if (x, y) != (0, 0)
        }
        for delta_x, delta_y in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            retained = {
                (x, y)
                for x, y in old_cells
                if (x, y) != (delta_x, delta_y)
                and max(abs(x - delta_x), abs(y - delta_y)) <= 2
            }
            retained_outer = {
                (x, y)
                for x, y in retained
                if max(abs(x - delta_x), abs(y - delta_y)) == 2
            }
            self.assertEqual(len(retained), 18)
            self.assertEqual(len(retained_outer), 11)
            self.assertEqual(24 - len(retained), 6)

        self.assertIn("retainedAll.add(rebased);", self.world)
        self.assertIn("retainedOuter.add(rebased);", self.world)
        self.assertIn(
            "findReusableNativePresentationChunk(",
            self.world,
        )
        self.assertIn(
            '" cells=reused:" + reusedCells',
            self.world,
        )
        self.assertIn(
            '"/built:" + builtCells',
            self.world,
        )

    def test_presentation_rebase_keeps_immutable_gpu_storage_identity(self):
        harness = textwrap.dedent(
            """
            package orsc.graphics.three;

            public final class PresentationRebaseStorageHarness {
                public static void main(String[] arguments) {
                    Renderer3DWorldChunkFrame.ChunkMesh source =
                        new Renderer3DWorldChunkFrame.ChunkMesh(
                            0, 10, 10, 100, 200,
                            new int[] {0, 0, 0, 128, 0, 0, 0, 0, 128},
                            new float[] {0.0f, 1.0f, 0.0f},
                            new float[] {0.0f, 0.0f, 1.0f},
                            new int[] {0, 0, 0},
                            new int[] {0, 1, 2},
                            new int[] {1},
                            new int[] {0},
                            new Renderer3DModelKind[] {
                                Renderer3DModelKind.TERRAIN
                            },
                            1, 0, 0, 123L);
                    Renderer3DWorldChunkFrame.ChunkMesh rebased =
                        source.rebasePresentation(-6144, 6144);

                    require(
                        source.getStorageSignature()
                            == rebased.getStorageSignature(),
                        "storage identity must survive a draw-only rebase");
                    require(
                        source.getSignature() != rebased.getSignature(),
                        "presented frame identity must include its rebase");
                    require(rebased.getVertexOffsetX() == -6144,
                        "x draw offset");
                    require(rebased.getVertexOffsetZ() == 6144,
                        "z draw offset");
                    require(rebased.getVertexCoord(0) == -6144,
                        "presented vertex includes x offset");
                    require(source.getVertexCoord(0) == 0,
                        "source geometry stays immutable");
                    require(
                        source.getVertexCoord(0)
                                + source.getLogicalWorldOffsetX()
                            == rebased.getVertexCoord(0)
                                + rebased.getLogicalWorldOffsetX(),
                        "x terrain variation coordinate survives rebase");
                    require(
                        source.getVertexCoord(2)
                                + source.getLogicalWorldOffsetZ()
                            == rebased.getVertexCoord(2)
                                + rebased.getLogicalWorldOffsetZ(),
                        "z terrain variation coordinate survives rebase");

                    Renderer3DWorldChunkFrame.ChunkMesh animatedA =
                        objectChunk(
                            Renderer3DWorldChunkFrame
                                .CHUNK_ROLE_ANIMATED_OBJECTS,
                            200L);
                    Renderer3DWorldChunkFrame.ChunkMesh animatedB =
                        objectChunk(
                            Renderer3DWorldChunkFrame
                                .CHUNK_ROLE_ANIMATED_OBJECTS,
                            201L);
                    Renderer3DWorldChunkFrame frameA =
                        Renderer3DWorldChunkFrame.fromChunks(
                            java.util.Arrays.asList(
                                source, animatedA));
                    Renderer3DWorldChunkFrame frameB =
                        Renderer3DWorldChunkFrame.fromChunks(
                            java.util.Arrays.asList(
                                source, animatedB));
                    require(
                        frameA.getStaticPresentationChunkCount() == 1,
                        "animated chunks do not enter static count");
                    require(
                        frameA.getStaticPresentationSignature()
                            == frameB.getStaticPresentationSignature(),
                        "animated changes do not prevent stability");

                    Renderer3DWorldChunkFrame frameWithStaticObject =
                        Renderer3DWorldChunkFrame.fromChunks(
                            java.util.Arrays.asList(
                                source,
                                objectChunk(
                                    Renderer3DWorldChunkFrame
                                        .CHUNK_ROLE_STATIC_OBJECTS,
                                    300L)));
                    require(
                        frameWithStaticObject
                            .getStaticPresentationChunkCount() == 2,
                        "static objects enter stability count");
                    require(
                        frameA.getStaticPresentationSignature()
                            != frameWithStaticObject
                                .getStaticPresentationSignature(),
                        "static object changes restart stability");
                }

                private static Renderer3DWorldChunkFrame.ChunkMesh
                        objectChunk(int role, long signature) {
                    return new Renderer3DWorldChunkFrame.ChunkMesh(
                        0, 10, 10, 100, 200,
                        new int[] {0, 0, 0, 128, 0, 0, 0, 0, 128},
                        new float[] {0.0f, 1.0f, 0.0f},
                        new float[] {0.0f, 0.0f, 1.0f},
                        new int[] {0, 0, 0},
                        new int[] {0, 1, 2},
                        new int[] {1},
                        new int[] {0},
                        new Renderer3DModelKind[] {
                            Renderer3DModelKind.GAME_OBJECT
                        },
                        null,
                        null,
                        0, 0, 0, true, role, signature);
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
            harness_path = (
                work / "PresentationRebaseStorageHarness.java"
            )
            harness_path.write_text(harness, encoding="utf-8")
            classifier_stub = (
                work / "Renderer3DMaterialClassifier.java"
            )
            classifier_stub.write_text(
                textwrap.dedent(
                    """
                    package orsc.graphics.three;

                    final class Renderer3DMaterialClassifier {
                        static Renderer3DMaterialFamily fallbackFor(
                                Renderer3DModelKind kind) {
                            return Renderer3DMaterialFamily.UNCLASSIFIED;
                        }
                    }
                    """
                ),
                encoding="utf-8",
            )
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
                        / "Client_Base/src/orsc/graphics/three/"
                        "Renderer3DMaterialFamily.java"
                    ),
                    str(
                        ROOT
                        / "Client_Base/src/orsc/graphics/three/"
                        "Renderer3DModelKind.java"
                    ),
                    str(classifier_stub),
                    str(WORLD_CHUNK_FRAME),
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
                    "orsc.graphics.three.PresentationRebaseStorageHarness",
                ],
                cwd=ROOT,
                check=True,
            )

        self.assertIn(
            "? chunk.getStorageSignature()",
            self.world_chunk_renderer,
        )
        self.assertIn(
            "chunk.getVertexOffsetX() - buffer.uploadedVertexOffsetX",
            self.world_chunk_renderer,
        )
        self.assertIn(
            "residentChunkShader.setChunkOffsets(",
            self.world_chunk_renderer,
        )
        self.assertIn(
            "aPosition + vec3(uChunkOffsetX, 0.0, uChunkOffsetZ)",
            self.opengl_shader,
        )
        self.assertIn(
            "vTerrainWorldXZ = rebasedPosition.xz"
            " + vec2(uTerrainWorldOffsetX, uTerrainWorldOffsetZ)",
            self.opengl_shader,
        )
        self.assertIn(
            "vec2 terrainPoint = vTerrainWorldXZ / 128.0",
            self.opengl_shader,
        )

    def test_boundary_trace_records_world_and_shadow_ownership(self):
        self.assertIn(
            "OPENGL_BOUNDARY_TRANSITION_TRACE_FRAMES = 90",
            self.telemetry,
        )
        self.assertIn(
            '"renderer.boundary-transition-frame"',
            self.telemetry,
        )
        self.assertIn(
            '"shadow.overProjectedFallback"',
            self.telemetry,
        )
        self.assertIn(
            "projectedWorldDrawn\n"
            "\t\t\t\t\t&& !residentWorldDrawn\n"
            "\t\t\t\t\t&& explicitRemasterShadowRequested",
            self.telemetry,
        )
        self.assertIn(
            "recordOpenGLBoundaryTransitionFrame(",
            self.presenter,
        )
        self.assertIn(
            "explicitRemasterShadowRequested",
            self.presenter,
        )
        self.assertIn(
            "RendererDiagnosticSession.isEnabled()",
            self.telemetry,
        )

    def test_same_scope_activation_waits_for_stable_static_scene(self):
        harness = textwrap.dedent(
            """
            package orsc;

            public final class LayeredScenePresentationLatchHarness {
                public static void main(String[] arguments) {
                    LayeredScenePresentationLatch latch =
                        new LayeredScenePresentationLatch();
                    require(!latch.shouldRetainLastPresentedFrame(),
                        "initial state");

                    latch.begin(true);
                    require(latch.shouldRetainLastPresentedFrame(),
                        "same-scope activation retains old frame");
                    latch.updatePending(false);
                    require(latch.shouldRetainLastPresentedFrame(),
                        "barrier completion still retains old frame");
                    latch.updatePending(false);
                    require(latch.shouldRetainLastPresentedFrame(),
                        "duplicate completion cannot release early");
                    require(!latch.completeFreshFrame(100L, 4),
                        "first fresh scene frame becomes candidate");
                    require(latch.shouldRetainLastPresentedFrame(),
                        "first candidate remains hidden");
                    require(!latch.completeFreshFrame(200L, 5),
                        "changed static scene restarts stability");
                    require(latch.shouldRetainLastPresentedFrame(),
                        "changed static scene remains hidden");
                    require(latch.completeFreshFrame(200L, 5),
                        "matching static scene releases latch");
                    require(!latch.shouldRetainLastPresentedFrame(),
                        "stable frame is immediately presentable");
                    require(latch.wasLastReleaseStable(),
                        "release records static stability");
                    require(latch.getFreshFrameSamples() == 3,
                        "all candidate frames are counted");
                    require(!latch.completeFreshFrame(200L, 5),
                        "release is single-use");

                    latch.begin(false);
                    require(!latch.shouldRetainLastPresentedFrame(),
                        "initial scope uses loading presentation");
                    latch.updatePending(false);
                    require(!latch.shouldRetainLastPresentedFrame(),
                        "initial scope does not manufacture retention");

                    latch.begin(true);
                    latch.reset();
                    require(!latch.shouldRetainLastPresentedFrame(),
                        "reset cancels pending retention");
                    require(!latch.completeFreshFrame(300L, 6),
                        "reset cancels release");

                    latch.begin(true);
                    latch.updatePending(false);
                    for (int sample = 0;
                            sample
                                < LayeredScenePresentationLatch
                                    .MAX_FRESH_FRAME_SAMPLES - 1;
                            sample++) {
                        require(!latch.completeFreshFrame(
                                1000L + sample, 7),
                            "unstable candidate remains bounded "
                                + sample);
                    }
                    require(latch.completeFreshFrame(2000L, 7),
                        "hard bound eventually releases");
                    require(!latch.wasLastReleaseStable(),
                        "bounded fallback is distinguishable");
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
            harness_path = (
                work / "LayeredScenePresentationLatchHarness.java"
            )
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
                    str(PRESENTATION_LATCH),
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
                    "orsc.LayeredScenePresentationLatchHarness",
                ],
                cwd=ROOT,
                check=True,
            )

        self.assertIn(
            "resetLayeredSceneActivationPresentation();",
            (
                ROOT / "Client_Base/src/orsc/PacketHandler.java"
            ).read_text(encoding="utf-8"),
        )
        self.assertIn(
            "completeLayeredSceneActivationFreshFrame(",
            self.client,
        )
        self.assertIn(
            '"renderer.atomic-presentation-release"',
            self.client,
        )

    def test_atomic_activation_uses_a_bounded_packet_burst(self):
        harness = textwrap.dedent(
            """
            package orsc;

            public final class LayeredScenePacketDrainPolicyHarness {
                public static void main(String[] arguments) {
                    require(LayeredScenePacketDrainPolicy.shouldReadNext(
                        0, false, false, false),
                        "normal cadence reads one packet");
                    require(!LayeredScenePacketDrainPolicy.shouldReadNext(
                        1, false, false, false),
                        "normal cadence remains one packet");

                    require(LayeredScenePacketDrainPolicy.shouldReadNext(
                        1, true, true, false),
                        "atomic activation drains its ordered update");
                    require(!LayeredScenePacketDrainPolicy.shouldReadNext(
                        2, true, true, true),
                        "terrain halo build pauses the drain");
                    require(!LayeredScenePacketDrainPolicy.shouldReadNext(
                        2, true, false, false),
                        "completed activation ends the drain");

                    for (int packet = 0;
                            packet
                                < LayeredScenePacketDrainPolicy
                                    .ATOMIC_ACTIVATION_PACKET_LIMIT;
                            packet++) {
                        require(
                            LayeredScenePacketDrainPolicy.shouldReadNext(
                                packet, true, true, false),
                            "atomic packet inside bound " + packet);
                    }
                    require(!LayeredScenePacketDrainPolicy.shouldReadNext(
                        LayeredScenePacketDrainPolicy
                            .ATOMIC_ACTIVATION_PACKET_LIMIT,
                        true, true, false),
                        "atomic burst has a hard bound");

                    boolean rejectedNegative = false;
                    try {
                        LayeredScenePacketDrainPolicy.shouldReadNext(
                            -1, false, false, false);
                    } catch (IllegalArgumentException expected) {
                        rejectedNegative = true;
                    }
                    require(rejectedNegative, "negative count rejected");
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
            harness_path = (
                work / "LayeredScenePacketDrainPolicyHarness.java"
            )
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
                    str(PACKET_DRAIN_POLICY),
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
                    "orsc.LayeredScenePacketDrainPolicyHarness",
                ],
                cwd=ROOT,
                check=True,
            )

        self.assertIn(
            "boolean activationBurst = "
            "this.layeredSceneActivationPending;",
            self.client,
        )
        self.assertIn(
            "isLayeredTerrainActivationHaloPrebuildPending()",
            self.client,
        )
        self.assertIn(
            "activationBurst |= wasActivationPending",
            self.client,
        )


if __name__ == "__main__":
    unittest.main()
