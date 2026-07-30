#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "Client_Base/src/orsc/graphics/three/World.java"
GRAPHICS = ROOT / "Client_Base/src/orsc/graphics/two/GraphicsController.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


class LayeredTransitionMinimapAcceptanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.world = WORLD.read_text(encoding="utf-8")
        cls.graphics = GRAPHICS.read_text(encoding="utf-8")
        cls.client = CLIENT.read_text(encoding="utf-8")

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


if __name__ == "__main__":
    unittest.main()
