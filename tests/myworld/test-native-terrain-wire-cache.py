#!/usr/bin/env python3

import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CACHE = (
    ROOT
    / "server/src/com/openrsc/server/net/rsc/NativeLayeredTerrainWireCache.java"
)
CLIENT_TILE = ROOT / "Client_Base/src/com/openrsc/client/model/Tile.java"
CLIENT_CHUNK = ROOT / "Client_Base/src/orsc/NativeLayeredTerrainChunk.java"
CLIENT_SNAPSHOT = ROOT / "Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java"
CLIENT_RESIDENCY = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainResidentCache.java"
)
CLIENT_DECODER = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainPacketDecoder.java"
)
SPOILED_MILK_PACKAGE = (
    Path(
        os.environ.get(
            "SPOILED_MILK_LAYERED_PACKAGE",
            ROOT / "tools/layered-maps/workspace/spoiled-milk-package/package",
        )
    )
)
UPDATER = ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
SERVER = ROOT / "server/src/com/openrsc/server/Server.java"
PROFILER = (
    ROOT
    / "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)


class NativeTerrainWireCacheTest(unittest.TestCase):
    def test_cache_reuses_and_replaces_exact_sector_products(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.server.net.rsc.NativeLayeredTerrainWireCache;
            import java.util.Arrays;
            import java.util.concurrent.atomic.AtomicInteger;
            import java.util.zip.Inflater;

            public final class NativeTerrainWireCacheHarness {
                private static byte[] inflate(byte[] compressed, int size)
                    throws Exception {
                    Inflater inflater = new Inflater();
                    try {
                        inflater.setInput(compressed);
                        byte[] result = new byte[size];
                        int count = inflater.inflate(result);
                        if (count != size || !inflater.finished()) {
                            throw new AssertionError("invalid compressed product");
                        }
                        return result;
                    } finally {
                        inflater.end();
                    }
                }

                public static void main(String[] arguments) throws Exception {
                    NativeLayeredTerrainWireCache cache =
                        new NativeLayeredTerrainWireCache();
                    byte[] firstRaw = new byte[23040];
                    Arrays.fill(firstRaw, (byte) 3);
                    AtomicInteger builds = new AtomicInteger();
                    NativeLayeredTerrainWireCache.Lookup first =
                        cache.getOrCompress(
                            "package:global:0:1:2",
                            "content-a",
                            firstRaw.length,
                            () -> {
                                builds.incrementAndGet();
                                return firstRaw;
                            });
                    if (first.isCacheHit() || builds.get() != 1
                        || first.getRawBytes() != firstRaw.length
                        || first.getCompressedByteCount() <= 0
                        || first.getBuildNanos() <= 0
                        || first.getCacheEntries() != 1
                        || !Arrays.equals(
                            firstRaw,
                            inflate(
                                first.getCompressedBytes(),
                                firstRaw.length))) {
                        throw new AssertionError("first cache miss is invalid");
                    }

                    NativeLayeredTerrainWireCache.Lookup hit =
                        cache.getOrCompress(
                            "package:global:0:1:2",
                            "content-a",
                            firstRaw.length,
                            () -> {
                                throw new AssertionError(
                                    "cache hit rebuilt raw sector bytes");
                            });
                    if (!hit.isCacheHit() || hit.getBuildNanos() != 0
                        || hit.getCompressedBytes()
                            != first.getCompressedBytes()
                        || cache.size() != 1) {
                        throw new AssertionError("cache hit is invalid");
                    }

                    byte[] replacementRaw = new byte[23040];
                    Arrays.fill(replacementRaw, (byte) 9);
                    NativeLayeredTerrainWireCache.Lookup replacement =
                        cache.getOrCompress(
                            "package:global:0:1:2",
                            "content-b",
                            replacementRaw.length,
                            () -> replacementRaw);
                    if (replacement.isCacheHit() || cache.size() != 1
                        || !Arrays.equals(
                            replacementRaw,
                            inflate(
                                replacement.getCompressedBytes(),
                                replacementRaw.length))) {
                        throw new AssertionError(
                            "changed sector content was not replaced");
                    }

                    cache.getOrCompress(
                        "package:global:-1:1:2",
                        "content-c",
                        firstRaw.length,
                        () -> firstRaw);
                    if (cache.size() != 2) {
                        throw new AssertionError(
                            "distinct signed-level slot was not retained");
                    }
                    cache.clear();
                    if (cache.size() != 0) {
                        throw new AssertionError("cache clear failed");
                    }
                }
            }
            """
        )
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            harness_path = work / "NativeTerrainWireCacheHarness.java"
            harness_path.write_text(harness, encoding="utf-8")
            subprocess.run(
                [
                    "javac",
                    "-d",
                    str(work),
                    str(CACHE),
                    str(harness_path),
                ],
                check=True,
                cwd=ROOT,
            )
            subprocess.run(
                ["java", "-cp", str(work), "NativeTerrainWireCacheHarness"],
                check=True,
                cwd=ROOT,
            )

    def test_mining_guild_real_package_window_survives_cache_and_v5_decode(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.client.model.Tile;
            import com.openrsc.server.net.rsc.NativeLayeredTerrainWireCache;
            import java.io.ByteArrayOutputStream;
            import java.io.DataOutputStream;
            import java.nio.charset.StandardCharsets;
            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.nio.file.Paths;
            import java.security.MessageDigest;
            import orsc.NativeLayeredTerrainPacketDecoder;
            import orsc.NativeLayeredTerrainSnapshot;

            public final class NativeTerrainMiningGuildWireHarness {
                private static final int SIZE = 48;
                private static final int TILE_BYTES = 10;
				private static final int SOURCE_TILE_BYTES = 11;

                public static void main(String[] arguments) throws Exception {
                    Path packageRoot = Paths.get(arguments[0]);
                    NativeLayeredTerrainWireCache cache =
                        new NativeLayeredTerrainWireCache();
                    byte[][] source = new byte[9][];
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream output = new DataOutputStream(bytes);
                    line(output, "rsc-remastered.spoiled-milk-layered-world");
                    line(output, "0.3.0");
                    line(output,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
                    output.writeByte(SIZE);
                    output.writeInt(6);
                    output.writeInt(12);
                    output.writeByte(1);
                    output.writeByte(9);
                    int index = 0;
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        for (int deltaY = -1; deltaY <= 1; deltaY++) {
                            int sectorX = 6 + deltaX;
                            int sectorY = 12 + deltaY;
                            Path sectorPath = packageRoot.resolve(
                                "terrain/global/lm1/xp" + sectorX
                                    + "-yp" + sectorY + ".raw");
                            byte[] raw = Files.readAllBytes(sectorPath);
							boolean wide =
								raw.length == SIZE * SIZE * SOURCE_TILE_BYTES;
                            require(wide || raw.length == SIZE * SIZE * TILE_BYTES,
                                "source byte count " + sectorX + ","
                                    + sectorY);
							byte[] wire;
							if (wide) {
								wire = new byte[SIZE * SIZE * TILE_BYTES];
								for (int tile = 0; tile < SIZE * SIZE; tile++) {
									int sourceOffset = tile * SOURCE_TILE_BYTES;
									int targetOffset = tile * TILE_BYTES;
									int elevation = (raw[sourceOffset] & 0xff) << 8
										| raw[sourceOffset + 1] & 0xff;
									require(elevation <= 255, "wire elevation range");
									wire[targetOffset] = (byte) elevation;
									System.arraycopy(
										raw, sourceOffset + 2,
										wire, targetOffset + 1,
										TILE_BYTES - 1);
								}
							} else {
								wire = raw;
							}
                            source[index++] = wire;
                            String contentSha = sha256(raw);
                            NativeLayeredTerrainWireCache.Lookup lookup =
                                cache.getOrCompress(
                                    "package:global:-1:" + sectorX
                                        + ":" + sectorY,
                                    contentSha,
									wire.length,
									() -> wire);
                            output.writeInt(sectorX);
                            output.writeInt(sectorY);
                            output.writeByte(1);
                            output.writeInt(sectorX);
                            output.writeInt(sectorY);
							line(output, wide
								? "raw-layered-sector-v2-u16"
								: "raw-layered-sector-v1");
                            line(output, contentSha);
                            output.writeShort(
                                lookup.getCompressedByteCount());
                            output.write(lookup.getCompressedBytes());
                        }
                    }
                    output.close();

                    NativeLayeredTerrainSnapshot snapshot =
                        NativeLayeredTerrainPacketDecoder.decodeV5(
                            bytes.toByteArray(), "global", -1);
                    require(snapshot.getAvailableChunkCount() == 9,
                        "decoded readiness");
                    index = 0;
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        for (int deltaY = -1; deltaY <= 1; deltaY++) {
                            int sectorX = 6 + deltaX;
                            int sectorY = 12 + deltaY;
                            byte[] raw = source[index++];
                            for (int localX = 0; localX < SIZE; localX++) {
                                for (int localY = 0;
                                        localY < SIZE; localY++) {
                                    int offset =
                                        (localX * SIZE + localY) * TILE_BYTES;
                                    Tile tile = snapshot.createTile(
                                        sectorX * SIZE + localX,
                                        sectorY * SIZE + localY);
                                    require(
                                        (tile.groundElevation & 0xff)
                                            == (raw[offset] & 0xff),
                                        "elevation");
                                    require(
                                        (tile.groundTexture & 0xff)
                                            == (raw[offset + 1] & 0xff),
                                        "texture");
                                    require(
                                        (tile.groundOverlay & 0xff)
                                            == (raw[offset + 2] & 0xff),
                                        "overlay");
                                    require(
                                        (tile.roofTexture & 0xff)
                                            == (raw[offset + 3] & 0xff),
                                        "roof");
                                    require(
                                        (tile.verticalWall & 0xff)
                                            == (raw[offset + 4] & 0xff),
                                        "vertical wall");
                                    require(
                                        (tile.horizontalWall & 0xff)
                                            == (raw[offset + 5] & 0xff),
                                        "horizontal wall");
                                    int diagonal =
                                        (raw[offset + 6] & 0xff) << 24
                                            | (raw[offset + 7] & 0xff) << 16
                                            | (raw[offset + 8] & 0xff) << 8
                                            | raw[offset + 9] & 0xff;
                                    require(
                                        tile.diagonalWalls == diagonal,
                                        "diagonal wall");
                                }
                            }
                        }
                    }
                    Tile login = snapshot.createTile(274, 565);
                    require((login.groundOverlay & 0xff) == 0,
                        "Mining Guild login tile became void");
                    require((login.groundTexture & 0xff) == 182,
                        "Mining Guild login texture changed");
                    require((login.groundElevation & 0xff) == 20,
                        "Mining Guild login elevation changed");
                    require(cache.size() == 9, "cache window size");
                }

                private static String sha256(byte[] bytes) throws Exception {
                    MessageDigest digest =
                        MessageDigest.getInstance("SHA-256");
                    byte[] hash = digest.digest(bytes);
                    StringBuilder result = new StringBuilder(64);
                    for (byte value : hash) {
                        result.append(String.format(
                            "%02x", value & 0xff));
                    }
                    return result.toString();
                }

                private static void line(
                        DataOutputStream output, String value)
                        throws Exception {
                    output.write(
                        value.getBytes(StandardCharsets.US_ASCII));
                    output.writeByte(10);
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
            harness_path = work / "NativeTerrainMiningGuildWireHarness.java"
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
                    str(CACHE),
                    str(CLIENT_TILE),
                    str(CLIENT_CHUNK),
                    str(CLIENT_SNAPSHOT),
                    str(CLIENT_RESIDENCY),
                    str(CLIENT_DECODER),
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
                    "NativeTerrainMiningGuildWireHarness",
                    str(SPOILED_MILK_PACKAGE),
                ],
                check=True,
                cwd=ROOT,
            )

    def test_runtime_uses_cache_without_weakening_builder_revision_path(self):
        updater = UPDATER.read_text(encoding="utf-8")
        server = SERVER.read_text(encoding="utf-8")
        profiler = PROFILER.read_text(encoding="utf-8")

        self.assertIn(
            "NativeLayeredTerrainWireCache nativeTerrainWireCache", updater
        )
        self.assertIn("wireCache.getOrCompress(", updater)
        self.assertIn("chunk.copyWireBytes()", updater)
        self.assertIn("visualTerrainWireBytes(", updater)
        self.assertIn("structuralTerrainWireBytes(", updater)
        self.assertIn("if(server.getConfig().WORLD_BUILDER_MODE)", updater)
        self.assertIn("nativeTerrainSectorSha256(chunk)", updater)
        self.assertIn("copyNativeTerrainSectorWireBytes(chunk)", updater)
        self.assertIn("server.addNativeTerrainTransferMetrics(", updater)
        self.assertIn("lastNativeTerrainWireCacheHits", server)
        self.assertIn("nativeTerrainWireCacheHits=", server)
        self.assertIn("=== Native Terrain Transfer ===", profiler)


if __name__ == "__main__":
    unittest.main()
