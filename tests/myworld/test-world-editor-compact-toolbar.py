#!/usr/bin/env python3
import binascii
import re
import struct
import subprocess
import tempfile
import textwrap
import unittest
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src"
PC_CLIENT = ROOT / "PC_Client/src"
UI = CLIENT / "com/openrsc/interfaces/misc/WorldEditorInterface.java"
TOOLBAR_STATE = CLIENT / "com/openrsc/interfaces/misc/WorldEditorToolbarState.java"
ICON_REGISTRY = CLIENT / "com/openrsc/interfaces/misc/WorldEditorIconRegistry.java"
BUILD_SETTINGS = CLIENT / "orsc/WorldEditorBuildSettings.java"
TERRAIN_GRID = CLIENT / "orsc/graphics/three/WorldEditorTerrainGrid.java"


def rgba_png(width, height):
    pixels = []
    for y in range(height):
        row = bytearray([0])
        for x in range(width):
            if x == 0 and y == 0:
                row.extend((255, 0, 0, 0))
            elif x == 1 and y == 0:
                row.extend((0, 0, 0, 255))
            else:
                row.extend((0, 255, 0, 255))
        pixels.append(bytes(row))
    raw = b"".join(pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", binascii.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw))
            + chunk(b"IEND", b""))


class WorldEditorCompactToolbarTest(unittest.TestCase):
    def compile_and_run(self, sources, class_name, harness, cwd=None):
        with tempfile.TemporaryDirectory() as output:
            output_path = Path(output)
            harness_path = output_path / (class_name.rsplit(".", 1)[-1] + ".java")
            harness_path.write_text(textwrap.dedent(harness))
            subprocess.run(
                ["javac", "-d", output, *map(str, sources), str(harness_path)],
                cwd=ROOT,
                check=True,
                text=True,
                capture_output=True,
            )
            return subprocess.run(
                ["java", "-cp", output, class_name],
                cwd=cwd or ROOT,
                check=True,
                text=True,
                capture_output=True,
            ).stdout

    def test_toolbar_state_keeps_one_mode_flyout_and_preserves_pin_semantics(self):
        output = self.compile_and_run(
            [TOOLBAR_STATE],
            "com.openrsc.interfaces.misc.ToolbarStateHarness",
            """
                package com.openrsc.interfaces.misc;
                public final class ToolbarStateHarness {
                    private static void require(boolean value) {
                        if (!value) throw new AssertionError();
                    }
                    public static void main(String[] args) {
                        WorldEditorToolbarState state = new WorldEditorToolbarState();
                        require(state.getFlyout() == WorldEditorToolbarState.Flyout.NAVIGATE);
                        state.selectMode(WorldEditorToolbarState.Flyout.NAVIGATE);
                        require(!state.isFlyoutOpen());
                        state.open(WorldEditorToolbarState.Flyout.TERRAIN);
                        require(state.getFlyout() == WorldEditorToolbarState.Flyout.TERRAIN);
                        state.togglePinned();
                        state.closeUnpinnedAfterWorldAction();
                        require(state.getFlyout() == WorldEditorToolbarState.Flyout.TERRAIN);
                        state.togglePinned();
                        state.closeUnpinnedAfterWorldAction();
                        require(!state.isFlyoutOpen());
                        state.open(WorldEditorToolbarState.Flyout.NPC);
                        state.toggleCollapsed();
                        require(state.isCollapsed() && !state.isFlyoutOpen());
                        state.setExpandedFallback(true);
                        require(state.isExpandedFallback() && !state.isCollapsed());
                        state.reset();
                        require(!state.isExpandedFallback() && !state.isPinned());
                        require(state.getFlyout() == WorldEditorToolbarState.Flyout.NAVIGATE);
                        System.out.println("toolbar-state-ok");
                    }
                }
            """,
        )
        self.assertEqual("toolbar-state-ok", output.strip())

    def test_icon_registry_caches_valid_png_and_bounds_malformed_assets(self):
        with tempfile.TemporaryDirectory() as working:
            icon_root = Path(working) / "dev/myworld/assets/ui/world-editor"
            icon_root.mkdir(parents=True)
            (icon_root / "mode-navigate.png").write_bytes(rgba_png(24, 24))
            (icon_root / "mode-inspect.png").write_bytes(rgba_png(12, 12))
            output = self.compile_and_run(
                [
                    CLIENT / "com/openrsc/client/model/Sprite.java",
                    CLIENT / "orsc/graphics/RendererTransparency.java",
                    ICON_REGISTRY,
                ],
                "com.openrsc.interfaces.misc.IconRegistryHarness",
                """
                    package com.openrsc.interfaces.misc;
                    import com.openrsc.client.model.Sprite;
                    public final class IconRegistryHarness {
                        private static void require(boolean value) {
                            if (!value) throw new AssertionError();
                        }
                        public static void main(String[] args) {
                            WorldEditorIconRegistry registry = new WorldEditorIconRegistry();
                            registry.initialize();
                            require(registry.loadedCount() == 1);
                            require(registry.missingKeys().size() == 18);
                            require(registry.isLoaded(WorldEditorIconRegistry.Key.MODE_NAVIGATE));
                            require(!registry.isLoaded(WorldEditorIconRegistry.Key.MODE_INSPECT));
                            Sprite first = registry.get(WorldEditorIconRegistry.Key.MODE_NAVIGATE);
                            require(first == registry.get(WorldEditorIconRegistry.Key.MODE_NAVIGATE));
                            require(first.getWidth() == 24 && first.getHeight() == 24);
                            require(first.getPixel(0) == 0);
                            require(first.getPixel(1) == 1);
                            require(first.getPixel(2) == 0x00ff00);
                            System.out.println("icon-registry-ok");
                        }
                    }
                """,
                cwd=working,
            )
        self.assertEqual(1, output.count("[world-editor icons]"))
        self.assertIn("18 unavailable", output)
        self.assertIn("mode-inspect.png (expected 24x24, got 12x12)", output)
        self.assertTrue(output.rstrip().endswith("icon-registry-ok"))

    def test_asset_contract_and_required_semantic_keys_stay_complete(self):
        source = ICON_REGISTRY.read_text()
        expected = {
            "toolbar-collapse.png", "toolbar-expand.png", "mode-navigate.png",
            "mode-inspect.png", "mode-scenery.png", "mode-npc.png",
            "field-elevation.png", "field-floor-color.png",
            "field-floor-texture.png", "field-roof.png",
            "field-wall-north.png", "field-wall-east.png",
            "field-wall-diagonal.png", "tool-brush-1x1.png",
            "tool-brush-3x3.png", "profile-build.png", "action-save.png",
            "action-pin.png", "action-close.png",
        }
        self.assertEqual(expected, set(re.findall(r'\("([a-z0-9-]+\.png)"', source)))
        build = (ROOT / "Client_Base/build.xml").read_text()
        self.assertIn('<include name="ui/world-editor/**"/>', build)
        readme = (ROOT / "dev/myworld/assets/ui/world-editor/README.md").read_text()
        credits = (ROOT / "dev/myworld/assets/ui/world-editor/CREDITS.md").read_text()
        for contract in ("24x24", "RGBA", "kebab-case", "myworld-assets/ui/world-editor"):
            self.assertIn(contract, readme)
        for heading in ("Source", "Author", "License", "Modifications"):
            self.assertIn(heading, credits)

        asset_root = ROOT / "dev/myworld/assets/ui/world-editor"
        for filename in expected:
            data = (asset_root / filename).read_bytes()
            self.assertEqual(b"\x89PNG\r\n\x1a\n", data[:8], filename)
            width, height = struct.unpack(">II", data[16:24])
            self.assertEqual((24, 24), (width, height), filename)
            self.assertEqual(8, data[24], filename)
            self.assertEqual(6, data[25], filename)

    def test_supplied_icon_set_loads_without_fallbacks(self):
        output = self.compile_and_run(
            [
                CLIENT / "com/openrsc/client/model/Sprite.java",
                CLIENT / "orsc/graphics/RendererTransparency.java",
                ICON_REGISTRY,
            ],
            "com.openrsc.interfaces.misc.CompleteIconRegistryHarness",
            """
                package com.openrsc.interfaces.misc;
                public final class CompleteIconRegistryHarness {
                    public static void main(String[] args) {
                        WorldEditorIconRegistry registry = new WorldEditorIconRegistry();
                        registry.initialize();
                        if (registry.loadedCount() != WorldEditorIconRegistry.Key.values().length
                            || !registry.missingKeys().isEmpty()) {
                            throw new AssertionError(registry.missingKeys());
                        }
                        System.out.println("complete-icon-registry-ok");
                    }
                }
            """,
        )
        self.assertEqual("complete-icon-registry-ok", output.strip())

    def test_compact_input_contract_and_temporary_fallback_are_explicit(self):
        ui = UI.read_text()
        applet = (PC_CLIENT / "orsc/ORSCApplet.java").read_text()
        client = (CLIENT / "orsc/mudclient.java").read_text()
        self.assertIn("DOCK_WIDTH=70", ui)
        self.assertIn("FLYOUT_WIDTH=180", ui)
        self.assertIn("if(click!=1&&click!=2)return false", ui)
        self.assertIn("if(click==2)toggleTerrainField(field)", ui)
        self.assertIn("else openTerrainTool(field)", ui)
        self.assertIn("else toggleBrushFlyout()", ui)
        self.assertIn("viewed?0x526f24:selected?0x365b82", ui)
        self.assertIn("int strokeSize=terrainBrushSize", ui)
        self.assertNotIn("mask&112", ui)
        self.assertIn("if(click==2)return true", ui)
        self.assertIn("closeUnpinnedAfterWorldAction", ui)
        self.assertIn('button(x+278,y,82,"Compact")', ui)
        self.assertIn("toolbar.setExpandedFallback(false)", ui)
        self.assertIn("toolbar.closeFlyout()", ui)
        self.assertIn("Config.isAndroid()", ui)
        self.assertIn("middleMouseOrbit.begin(var1.getButton(), clientMouseX, clientMouseY)", applet)
        self.assertRegex(applet, r"(?s)middleMouseOrbit\.update\(.*?currentMouseButtonDown = 0;.*?var1\.consume\(\);.*?return;")
        self.assertIn("controlPressed&&currentMouseButtonDown==1", client)
        self.assertIn("updateTerrainDrag(controlPressed,currentMouseButtonDown==1", client)
        self.assertIn("toggleBrushSize()", ui)
        self.assertIn("TOOL_BRUSH_1X1", ui)
        self.assertIn("TOOL_BRUSH_3X3", ui)
        self.assertIn("toolbar.isCollapsed()?WorldEditorIconRegistry.Key.TOOLBAR_EXPAND", ui)
        self.assertNotIn('return "Raw value "+activeTerrainText()', ui)

    def test_compact_grid_exposes_every_terrain_field_without_tab_gating(self):
        ui = UI.read_text()
        paint_mask = re.search(r"private int terrainPaintMask\(\)\{(?P<body>.*?)\}", ui, re.S)
        self.assertIsNotNone(paint_mask)
        self.assertNotIn("terrainStructureTab?", paint_mask.group("body"))
        for mask in (
            "paintElevation?1:0", "paintFloorColor?2:0", "paintFloorTexture?4:0",
            "paintRoof?8:0", "paintEastWall?16:0", "paintNorthWall?32:0",
            "paintDiagonalWall?64:0",
        ):
            self.assertIn(mask, paint_mask.group("body"))
        for field in range(6, 13):
            self.assertIn(f"return {field}", ui)

    def test_dirty_save_and_build_profile_restoration_guards_are_visible(self):
        ui = UI.read_text()
        client = (CLIENT / "orsc/mudclient.java").read_text()
        self.assertIn("unsavedChanges||saveRequested", ui)
        self.assertIn("Wait for the active terrain stroke to finish before saving.", ui)
        self.assertIn("Unsaved edits remain. Select Close again", ui)
        self.assertIn("observeGameMessage", ui)
        self.assertIn("worldEditorBuildSnapshotValid=true", client)
        self.assertIn("else if(worldEditorBuildSnapshotValid)", client)
        self.assertIn("worldEditorSavedGeometry=RendererGeometrySettings.getMode()", client)
        self.assertIn("RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.FACETED)", client)
        self.assertIn("setMode(worldEditorSavedGeometry)", client)
        self.assertIn("WorldEditorBuildSettings.setEnabled(enabled)", client)
        self.assertIn("setTerrainLevel(worldEditorSavedTerrainRelief)", client)
        self.assertIn("rejectLayeredReviewMutation(", ui)
        self.assertIn("mc.showWorldEditorStatus(message)", ui)
        self.assertIn("public void showWorldEditorStatus(String message)", client)
        self.assertIn("setObjectLevel(worldEditorSavedObjectRelief)", client)
        self.assertNotIn("worldEditorSavedTerrainRelief>0", client)
        self.assertNotIn("worldEditorSavedObjectRelief>0", client)
        self.assertGreaterEqual(client.count("setWorldEditorBuildMode(false)"), 2)
        self.assertIn("closeFromServer(){setTerrainBuildMode(false)", ui)
        self.assertIn("requestEditorClose()", ui)

    def test_build_mode_state_and_grid_extract_square_boundaries_only(self):
        output = self.compile_and_run(
            [
                CLIENT / "com/openrsc/client/entityhandling/defs/EntityDef.java",
                CLIENT / "com/openrsc/client/entityhandling/defs/GameObjectDef.java",
                CLIENT / "orsc/graphics/three/Renderer3DModelKind.java",
                CLIENT / "orsc/graphics/three/Renderer3DMaterialFamily.java",
                CLIENT / "orsc/graphics/three/Renderer3DMaterialClassifier.java",
                CLIENT / "orsc/graphics/three/Renderer3DWorldChunkFrame.java",
                TERRAIN_GRID,
                BUILD_SETTINGS,
            ],
            "orsc.graphics.three.WorldEditorGridHarness",
            """
                package orsc.graphics.three;
                import java.util.Arrays;
                import orsc.WorldEditorBuildSettings;
                public final class WorldEditorGridHarness {
                    private static void require(boolean value) {
                        if (!value) throw new AssertionError();
                    }
                    private static Renderer3DWorldChunkFrame.ChunkMesh chunk(int plane, boolean object) {
                        int[] coordinates = {0,0,0, 128,-8,0, 128,-16,128, 0,-4,128};
                        int[] indices = {0,1,2, 0,2,3};
                        int[] textures = {0,0};
                        Renderer3DModelKind[] kinds = {
                            Renderer3DModelKind.TERRAIN, Renderer3DModelKind.TERRAIN
                        };
                        return new Renderer3DWorldChunkFrame.ChunkMesh(
                            plane, 0, 0, 0, 0, coordinates, null, null, null,
                            indices, textures, new int[] {0,0}, kinds, 2, 0, 0, object, 91L + plane);
                    }
                    private static Renderer3DWorldChunkFrame.ChunkMesh completeGridChunk() {
                        Renderer3DWorldChunkFrame.ChunkMesh chunk = chunk(0, false);
                        chunk.setWorldEditorTerrainGrid(3, new int[] {
                            0, -1, -2,
                            -3, -4, -5,
                            -6, -7, -8
                        });
                        return chunk;
                    }
                    public static void main(String[] args) {
                        require(!WorldEditorBuildSettings.isEnabled());
                        WorldEditorBuildSettings.setEnabled(true);
                        require(WorldEditorBuildSettings.isEnabled());
                        WorldEditorBuildSettings.setEnabled(false);
                        Renderer3DWorldChunkFrame frame = Renderer3DWorldChunkFrame.fromChunks(
                            Arrays.asList(chunk(0, false), chunk(0, true), chunk(1, false)));
                        WorldEditorTerrainGrid grid = new WorldEditorTerrainGrid();
                        int[] segments = grid.segments(frame, 0);
                        require(segments.length == 24);
                        require(segments == grid.segments(frame, 0));
                        for (int offset = 0; offset < segments.length; offset += 6) {
                            int dx = Math.abs(segments[offset] - segments[offset + 3]);
                            int dz = Math.abs(segments[offset + 2] - segments[offset + 5]);
                            require((dx == 128 && dz == 0) || (dx == 0 && dz == 128));
                        }
                        require(grid.segments(frame, 2).length == 0);
                        Renderer3DWorldChunkFrame completeFrame = Renderer3DWorldChunkFrame.fromChunks(
                            Arrays.asList(completeGridChunk()));
                        int[] complete = grid.segments(completeFrame, 0);
                        require(complete.length == 72);
                        boolean includesVoidHeight = false;
                        for (int coordinate : complete) if (coordinate == -4) includesVoidHeight = true;
                        require(includesVoidHeight);
                        System.out.println("world-editor-grid-ok");
                    }
                }
            """,
        )
        self.assertEqual("world-editor-grid-ok", output.strip())

    def test_build_grid_is_wired_to_legacy_and_opengl_without_wire_geometry(self):
        client = (CLIENT / "orsc/mudclient.java").read_text()
        chunk_renderer = (PC_CLIENT / "orsc/OpenGLWorldChunkRenderer.java").read_text()
        presenter = (PC_CLIENT / "orsc/OpenGLFramePresenter.java").read_text()
        grid = TERRAIN_GRID.read_text()
        settings = (CLIENT / "orsc/graphics/three/Renderer3DSettings.java").read_text()
        self.assertIn("drawWorldEditorBuildGridLegacy(renderer3DFrame)", client)
        self.assertIn("ScaledWindow.isOpenGLPrimaryWindowEnabled()", client)
        self.assertIn("GEOMETRY_CAPTURE_ENABLED || WorldEditorBuildSettings.isEnabled()", settings)
        self.assertIn("drawWorldEditorBuildGrid(frame.renderer3DFrame)", presenter)
        self.assertIn("gl.glBegin(gl.GL_LINES)", chunk_renderer)
        self.assertIn("gl.glEnable(gl.GL_DEPTH_TEST)", chunk_renderer)
        self.assertIn("chunk.getTriangleModelKind(triangle) != Renderer3DModelKind.TERRAIN", grid)
        self.assertIn("chunk.getChunkRole() == Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD", grid)
        self.assertIn("chunk.hasWorldEditorTerrainGrid()", grid)
        self.assertIn("worldEditorTerrainGridHeights(product.terrainInput)",
                      (CLIENT / "orsc/graphics/three/World.java").read_text())
        self.assertNotIn("RendererGeometrySettings.Mode.WIRE", grid)


if __name__ == "__main__":
    unittest.main()
