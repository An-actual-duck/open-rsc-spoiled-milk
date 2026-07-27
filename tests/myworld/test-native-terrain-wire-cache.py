#!/usr/bin/env python3

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

    def test_runtime_uses_cache_without_weakening_builder_revision_path(self):
        updater = UPDATER.read_text(encoding="utf-8")
        server = SERVER.read_text(encoding="utf-8")
        profiler = PROFILER.read_text(encoding="utf-8")

        self.assertIn(
            "NativeLayeredTerrainWireCache nativeTerrainWireCache", updater
        )
        self.assertIn("wireCache.getOrCompress(", updater)
        self.assertIn("chunk::copyWireBytes", updater)
        self.assertIn("if(server.getConfig().WORLD_BUILDER_MODE)", updater)
        self.assertIn("nativeTerrainSectorSha256(chunk)", updater)
        self.assertIn("copyNativeTerrainSectorWireBytes(chunk)", updater)
        self.assertIn("server.addNativeTerrainTransferMetrics(", updater)
        self.assertIn("lastNativeTerrainWireCacheHits", server)
        self.assertIn("nativeTerrainWireCacheHits=", server)
        self.assertIn("=== Native Terrain Transfer ===", profiler)


if __name__ == "__main__":
    unittest.main()
