#!/usr/bin/env python3

import re
import subprocess
import tempfile
import textwrap
import unittest
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PACKAGE_TERRAIN = (
    ROOT
    / "tools/layered-maps/workspace/spoiled-milk-package-v3/package/"
    "terrain/global"
)


class NativeTerrainSymmetricResidencyTest(unittest.TestCase):
    def test_visual_halo_is_radius_two_same_center_and_transactional(self):
        harness = textwrap.dedent(
            """
            import java.io.ByteArrayOutputStream;
            import java.io.DataOutputStream;
            import java.nio.charset.StandardCharsets;
            import java.util.Arrays;
            import java.util.zip.Deflater;
            import orsc.NativeLayeredTerrainChunk;
            import orsc.NativeLayeredTerrainPacketDecoder;
            import orsc.NativeLayeredTerrainResidentCache;
            import orsc.NativeLayeredTerrainSnapshot;

            public final class NativeTerrainSymmetricHaloHarness {
                private static final String PACKAGE =
                    "spoiled-milk.layered-map";
                private static final String VERSION = "1.0.0";
                private static final String MANIFEST =
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
                private static final String SOURCE_SHA =
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                    + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

                public static void main(String[] arguments) throws Exception {
                    NativeLayeredTerrainSnapshot active = active(10, 12);
                    NativeLayeredTerrainResidentCache cache =
                        new NativeLayeredTerrainResidentCache();
                    NativeLayeredTerrainSnapshot halo =
                        NativeLayeredTerrainPacketDecoder.decodeV9Halo(
                            halo(10, 12),
                            "global",
                            0,
                            cache,
                            active);
                    require(halo.getChunkRadius() == 2, "halo radius");
                    require(halo.getAvailableChunkCount() == 16,
                        "outer sector count");
                    require(cache.size() == 16
                            && cache.getLastPayloads() == 16,
                        "visual residency");
                    com.openrsc.client.model.Tile tile =
                        halo.createTile(8 * 48, 12 * 48);
                    require((tile.groundElevation & 0xff) == 33,
                        "visual elevation");
                    require((tile.groundTexture & 0xff) == 44,
                        "visual texture");
                    require((tile.groundOverlay & 0xff) == 5,
                        "visual overlay");
                    require((tile.roofTexture & 0xff) == 0
                            && (tile.verticalWall & 0xff) == 0
                            && (tile.horizontalWall & 0xff) == 0
                            && tile.diagonalWalls == 0,
                        "visual payload gained authoritative fields");
                    NativeLayeredTerrainSnapshot structural =
                        NativeLayeredTerrainPacketDecoder.decodeV10Structure(
                            structure(10, 12),
                            "global",
                            0,
                            cache,
                            active);
                    NativeLayeredTerrainSnapshot presentation =
                        NativeLayeredTerrainSnapshot.mergePresentation(
                            visualWithResidentInner(10, 12), structural);
                    com.openrsc.client.model.Tile complete =
                        presentation.createTile(8 * 48, 12 * 48);
                    require((complete.groundElevation & 0xff) == 33
                            && (complete.groundTexture & 0xff) == 44
                            && (complete.groundOverlay & 0xff) == 5,
                        "merged presentation lost visual terrain");
                    require((complete.roofTexture & 0xff) == 6
                            && (complete.verticalWall & 0xff) == 7
                            && (complete.horizontalWall & 0xff) == 8
                            && complete.diagonalWalls == 0x01020304,
                        "merged presentation lost structural terrain");
                    com.openrsc.client.model.Tile inner =
                        presentation.createTile(10 * 48, 12 * 48);
                    require((inner.groundElevation & 0xff) == 33
                            && (inner.groundTexture & 0xff) == 44,
                        "merged presentation lost resident seam source");
                    require(cache.size() == 32,
                        "structural residency was not distinct");

                    int stableSize = cache.size();
                    expectFailure(
                        () -> NativeLayeredTerrainPacketDecoder.decodeV9Halo(
                            uncheckedHalo(11, 12),
                            "global", 0, cache, active),
                        "shifted halo center accepted");
                    require(cache.size() == stableSize,
                        "failed halo committed residency");
                }

                private static NativeLayeredTerrainSnapshot active(
                        int centerX, int centerY) {
                    NativeLayeredTerrainChunk[] chunks =
                        new NativeLayeredTerrainChunk[9];
                    int index = 0;
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            chunks[index++] =
                                NativeLayeredTerrainChunk.voidChunk(
                                    48, centerX + x, centerY + y);
                        }
                    }
                    return new NativeLayeredTerrainSnapshot(
                        NativeLayeredTerrainSnapshot
                            .ATOMIC_ACTIVATION_PROTOCOL_VERSION,
                        PACKAGE,
                        VERSION,
                        MANIFEST,
                        48,
                        "global",
                        0,
                        centerX,
                        centerY,
                        1,
                        chunks);
                }

                private static NativeLayeredTerrainSnapshot
                        visualWithResidentInner(
                            int centerX, int centerY) {
                    NativeLayeredTerrainChunk[] chunks =
                        new NativeLayeredTerrainChunk[25];
                    byte[] expanded = new byte[48 * 48 * 10];
                    for (int offset = 0;
                            offset < expanded.length;
                            offset += 10) {
                        expanded[offset] = 33;
                        expanded[offset + 1] = 44;
                        expanded[offset + 2] = 5;
                    }
                    int index = 0;
                    for (int x = -2; x <= 2; x++) {
                        for (int y = -2; y <= 2; y++) {
                            int chunkX = centerX + x;
                            int chunkY = centerY + y;
                            chunks[index++] =
                                NativeLayeredTerrainChunk.available(
                                    48,
                                    chunkX,
                                    chunkY,
                                    chunkX,
                                    chunkY,
                                    Math.max(Math.abs(x), Math.abs(y)) == 2
                                        ? NativeLayeredTerrainChunk
                                            .VISUAL_ENCODING
                                        : NativeLayeredTerrainChunk
                                            .RAW_ENCODING,
                                    SOURCE_SHA,
                                    expanded);
                        }
                    }
                    return new NativeLayeredTerrainSnapshot(
                        NativeLayeredTerrainSnapshot
                            .SYMMETRIC_RESIDENCY_PROTOCOL_VERSION,
                        PACKAGE,
                        VERSION,
                        MANIFEST,
                        48,
                        "global",
                        0,
                        centerX,
                        centerY,
                        2,
                        chunks);
                }

                private static byte[] halo(
                        int centerX, int centerY) throws Exception {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream output = new DataOutputStream(bytes);
                    line(output, PACKAGE);
                    line(output, VERSION);
                    line(output, MANIFEST);
                    output.writeByte(48);
                    output.writeInt(centerX);
                    output.writeInt(centerY);
                    output.writeByte(2);
                    output.writeByte(25);
                    for (int x = -2; x <= 2; x++) {
                        for (int y = -2; y <= 2; y++) {
                            int chunkX = centerX + x;
                            int chunkY = centerY + y;
                            output.writeInt(chunkX);
                            output.writeInt(chunkY);
                            boolean outer =
                                Math.max(Math.abs(x), Math.abs(y)) == 2;
                            output.writeByte(outer ? 1 : 0);
                            if (!outer) {
                                continue;
                            }
                            output.writeInt(chunkX);
                            output.writeInt(chunkY);
                            line(output,
                                NativeLayeredTerrainChunk.VISUAL_ENCODING);
                            line(output, SOURCE_SHA);
                            output.writeByte(1);
                            byte[] compressed = visualSector();
                            output.writeShort(compressed.length);
                            output.write(compressed);
                        }
                    }
                    output.close();
                    return bytes.toByteArray();
                }

                private static byte[] structure(
                        int centerX, int centerY) throws Exception {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream output = new DataOutputStream(bytes);
                    line(output, PACKAGE);
                    line(output, VERSION);
                    line(output, MANIFEST);
                    output.writeByte(48);
                    output.writeInt(centerX);
                    output.writeInt(centerY);
                    output.writeByte(2);
                    output.writeByte(25);
                    for (int x = -2; x <= 2; x++) {
                        for (int y = -2; y <= 2; y++) {
                            int chunkX = centerX + x;
                            int chunkY = centerY + y;
                            output.writeInt(chunkX);
                            output.writeInt(chunkY);
                            boolean outer =
                                Math.max(Math.abs(x), Math.abs(y)) == 2;
                            output.writeByte(outer ? 1 : 0);
                            if (!outer) {
                                continue;
                            }
                            output.writeInt(chunkX);
                            output.writeInt(chunkY);
                            line(output,
                                NativeLayeredTerrainChunk
                                    .STRUCTURAL_ENCODING);
                            line(output, SOURCE_SHA);
                            output.writeByte(1);
                            byte[] compressed = structuralSector();
                            output.writeShort(compressed.length);
                            output.write(compressed);
                        }
                    }
                    output.close();
                    return bytes.toByteArray();
                }

                private static byte[] uncheckedHalo(
                        int centerX, int centerY) {
                    try {
                        return halo(centerX, centerY);
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    }
                }

                private static byte[] visualSector() {
                    byte[] raw = new byte[48 * 48 * 3];
                    for (int offset = 0; offset < raw.length; offset += 3) {
                        raw[offset] = 33;
                        raw[offset + 1] = 44;
                        raw[offset + 2] = 5;
                    }
                    Deflater compressor =
                        new Deflater(Deflater.BEST_SPEED);
                    try {
                        compressor.setInput(raw);
                        compressor.finish();
                        byte[] compressed = new byte[raw.length + 128];
                        int length = compressor.deflate(compressed);
                        require(compressor.finished(), "compression");
                        return Arrays.copyOf(compressed, length);
                    } finally {
                        compressor.end();
                    }
                }

                private static byte[] structuralSector() {
                    byte[] raw = new byte[48 * 48 * 7];
                    for (int offset = 0; offset < raw.length; offset += 7) {
                        raw[offset] = 6;
                        raw[offset + 1] = 7;
                        raw[offset + 2] = 8;
                        raw[offset + 3] = 1;
                        raw[offset + 4] = 2;
                        raw[offset + 5] = 3;
                        raw[offset + 6] = 4;
                    }
                    Deflater compressor =
                        new Deflater(Deflater.BEST_SPEED);
                    try {
                        compressor.setInput(raw);
                        compressor.finish();
                        byte[] compressed = new byte[raw.length + 128];
                        int length = compressor.deflate(compressed);
                        require(compressor.finished(), "compression");
                        return Arrays.copyOf(compressed, length);
                    } finally {
                        compressor.end();
                    }
                }

                private static void line(
                        DataOutputStream output, String value)
                        throws Exception {
                    output.write(value.getBytes(StandardCharsets.US_ASCII));
                    output.writeByte(10);
                }

                private static void expectFailure(
                        Runnable operation, String label) {
                    try {
                        operation.run();
                        throw new AssertionError(label);
                    } catch (IllegalArgumentException
                            | IllegalStateException expected) {
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
        self._compile_and_run(
            "NativeTerrainSymmetricHaloHarness",
            harness,
            [
                ROOT / "Client_Base/src/com/openrsc/client/model/Tile.java",
                ROOT / "Client_Base/src/orsc/NativeLayeredTerrainChunk.java",
                ROOT
                / "Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java",
                ROOT
                / "Client_Base/src/orsc/NativeLayeredTerrainResidentCache.java",
                ROOT
                / "Client_Base/src/orsc/"
                "NativeLayeredTerrainPacketDecoder.java",
            ],
        )

    def test_spoiled_milk_outer_visual_ring_fits_one_wire_frame(self):
        coordinate = re.compile(
            r"l(?P<level>[pm]\d+)/"
            r"x(?P<x>[pm]\d+)-y(?P<y>[pm]\d+)\.raw$"
        )

        def signed(value):
            return -int(value[1:]) if value.startswith("m") else int(value[1:])

        sectors = {}
        for path in PACKAGE_TERRAIN.rglob("*.raw"):
            match = coordinate.search(path.relative_to(PACKAGE_TERRAIN).as_posix())
            self.assertIsNotNone(match, path)
            raw = path.read_bytes()
            self.assertEqual(len(raw), 48 * 48 * 10)
            visual = b"".join(raw[offset : offset + 3]
                              for offset in range(0, len(raw), 10))
            sectors[
                (
                    signed(match["level"]),
                    signed(match["x"]),
                    signed(match["y"]),
                )
            ] = len(zlib.compress(visual, 1))

        worst_payload = 0
        for level in {key[0] for key in sectors}:
            xs = [key[1] for key in sectors if key[0] == level]
            ys = [key[2] for key in sectors if key[0] == level]
            for center_x in range(min(xs) - 2, max(xs) + 3):
                for center_y in range(min(ys) - 2, max(ys) + 3):
                    payload = sum(
                        sectors.get((level, center_x + dx, center_y + dy), 0)
                        for dx in range(-2, 3)
                        for dy in range(-2, 3)
                        if max(abs(dx), abs(dy)) == 2
                    )
                    worst_payload = max(worst_payload, payload)

        # 5 KiB safely covers the envelope and all 25 slot identities.
        self.assertLess(worst_payload + 5 * 1024, 65_536)

    def test_spoiled_milk_outer_structural_ring_fits_one_wire_frame(self):
        coordinate = re.compile(
            r"l(?P<level>[pm]\d+)/"
            r"x(?P<x>[pm]\d+)-y(?P<y>[pm]\d+)\.raw$"
        )

        def signed(value):
            return -int(value[1:]) if value.startswith("m") else int(value[1:])

        sectors = {}
        for path in PACKAGE_TERRAIN.rglob("*.raw"):
            match = coordinate.search(path.relative_to(PACKAGE_TERRAIN).as_posix())
            self.assertIsNotNone(match, path)
            raw = path.read_bytes()
            structural = b"".join(
                raw[offset + 3 : offset + 10]
                for offset in range(0, len(raw), 10)
            )
            sectors[
                (
                    signed(match["level"]),
                    signed(match["x"]),
                    signed(match["y"]),
                )
            ] = len(zlib.compress(structural, 1))

        worst_payload = 0
        for level in {key[0] for key in sectors}:
            xs = [key[1] for key in sectors if key[0] == level]
            ys = [key[2] for key in sectors if key[0] == level]
            for center_x in range(min(xs) - 2, max(xs) + 3):
                for center_y in range(min(ys) - 2, max(ys) + 3):
                    payload = sum(
                        sectors.get((level, center_x + dx, center_y + dy), 0)
                        for dx in range(-2, 3)
                        for dy in range(-2, 3)
                        if max(abs(dx), abs(dy)) == 2
                    )
                    worst_payload = max(worst_payload, payload)

        self.assertLess(worst_payload + 5 * 1024, 65_536)

    def test_gate_and_renderer_field_are_explicit_and_default_off(self):
        configuration = (
            ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
        ).read_text(encoding="utf-8")
        updater = (
            ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
        ).read_text(encoding="utf-8")
        world = (
            ROOT / "Client_Base/src/orsc/graphics/three/World.java"
        ).read_text(encoding="utf-8")
        handler = (
            ROOT / "Client_Base/src/orsc/PacketHandler.java"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "WANT_LAYERED_NATIVE_TERRAIN_SYMMETRIC_RESIDENCY",
            configuration,
        )
        self.assertIn(
            '"want_layered_native_terrain_symmetric_residency",'
            "\n\t\t\t\tfalse);",
            configuration,
        )
        self.assertIn("NATIVE_LAYERED_SYMMETRIC_RESIDENCY_RADIUS = 2", updater)
        self.assertIn("visualTerrainWireBytes(", updater)
        self.assertIn("structuralTerrainWireBytes(", updater)
        self.assertIn("decodeV9Halo(", handler)
        self.assertIn("decodeV10Structure(", handler)
        self.assertIn("preloadNativeLayeredTerrainHalo(", handler)
        self.assertIn(
            "activeFrame.getChunkCount() + outerFrame.getChunkCount()",
            world,
        )
        self.assertIn("NATIVE_TERRAIN_SYMMETRIC_RETAIN", world)
        self.assertIn("rebasePresentation(rebaseX, rebaseZ)", world)
        self.assertIn("Math.max(Math.abs(deltaX), Math.abs(deltaY)) != 2", world)
        self.assertIn("NATIVE_TERRAIN_SYMMETRIC_FIELD", world)
        self.assertIn("includePresentationEffects", world)

    def _compile_and_run(self, class_name, harness, sources):
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            harness_path = work / f"{class_name}.java"
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
                    *[str(source) for source in sources],
                    str(harness_path),
                ],
                check=True,
                cwd=ROOT,
            )
            subprocess.run(
                ["java", "-cp", str(work), class_name],
                check=True,
                cwd=ROOT,
            )


if __name__ == "__main__":
    unittest.main()
