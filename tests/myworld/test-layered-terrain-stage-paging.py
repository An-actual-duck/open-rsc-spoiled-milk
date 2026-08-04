#!/usr/bin/env python3

import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class LayeredTerrainStagePagingTest(unittest.TestCase):
    def test_observed_cache_churn_size_round_trips_with_bounded_pages(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.server.net.PacketFrameLengthGuard;
            import com.openrsc.server.net.rsc.LayeredTerrainStagePaging;
            import com.openrsc.server.net.rsc.LayeredTerrainStagePaging.Page;
            import java.nio.ByteBuffer;
            import java.util.Arrays;
            import java.util.List;
            import orsc.NativeLayeredTerrainStageAssembler;

            public final class LayeredTerrainStagePagingHarness {
                private static final int OBSERVED_CACHE_CHURN_BYTES = 69043;

                public static void main(String[] arguments) {
                    byte[] observed = bytes(OBSERVED_CACHE_CHURN_BYTES, 7);
                    observed[0] = 4;
                    List<Page> pages = LayeredTerrainStagePaging.split(
                        observed, 38, 14);
                    require(pages.size() == 3, "observed page count");
                    require(pages.get(0).getFragmentLength() == 24000,
                        "first page bound");
                    require(pages.get(1).getFragmentLength() == 24000,
                        "second page bound");
                    require(pages.get(2).getFragmentLength() == 21043,
                        "final page remainder");
                    ByteBuffer firstWire = ByteBuffer.wrap(
                        pages.get(0).toWirePayload());
                    require((firstWire.get() & 0xff) == 6,
                        "page transport marker");
                    require(firstWire.getInt() == 38
                            && firstWire.getInt() == 14,
                        "page wire identity");
                    require(firstWire.getInt() == OBSERVED_CACHE_CHURN_BYTES,
                        "page wire total");
                    require(firstWire.getInt() == pages.get(0).getCrc32(),
                        "page wire CRC");
                    require((firstWire.getShort() & 0xffff) == 0
                            && (firstWire.getShort() & 0xffff) == 3
                            && (firstWire.getShort() & 0xffff) == 24000,
                        "page wire indexes and length");
                    require(firstWire.remaining() == 24000,
                        "page wire fragment");
                    for (Page page : pages) {
                        int payloadBytes =
                            LayeredTerrainStagePaging.PAGE_ENVELOPE_BYTES
                                + page.getFragmentLength();
                        PacketFrameLengthGuard
                            .requireSimplifiedPayloadLength(payloadBytes);
                        PacketFrameLengthGuard
                            .requireAuthenticPacketLength(payloadBytes + 1);
                    }

                    NativeLayeredTerrainStageAssembler assembler =
                        new NativeLayeredTerrainStageAssembler();
                    NativeLayeredTerrainStageAssembler.CompletedStage complete =
                        null;
                    for (Page page : pages) {
                        complete = accept(
                            assembler, page, page.copyFragment(), 37, 14);
                    }
                    require(complete != null, "observed stage completion");
                    require(complete.getStageSequence() == 38
                            && complete.getContextSequence() == 14,
                        "completed identity");
                    require(Arrays.equals(observed, complete.copyBytes()),
                        "lossless observed stage");
                    require(!assembler.hasPendingStage()
                            && assembler.getBufferedByteCount() == 0,
                        "completion releases assembly");

                    require(!LayeredTerrainStagePaging.requiresPaging(65532),
                        "maximum unpaged payload");
                    require(LayeredTerrainStagePaging.requiresPaging(65533),
                        "first paged payload");
                    expectIllegal(() -> LayeredTerrainStagePaging
                        .requiresPaging(1048577));
                    testFrameGuards();
                    testFailureIsolation(observed, pages);
                }

                private static void testFrameGuards() {
                    PacketFrameLengthGuard
                        .requireSimplifiedPayloadLength(65532);
                    expectIllegal(() -> PacketFrameLengthGuard
                        .requireSimplifiedPayloadLength(65533));
                    PacketFrameLengthGuard
                        .requireAuthenticPacketLength(24575);
                    expectIllegal(() -> PacketFrameLengthGuard
                        .requireAuthenticPacketLength(24576));
                    PacketFrameLengthGuard
                        .requireLegacyPayloadLength(65534);
                    expectIllegal(() -> PacketFrameLengthGuard
                        .requireLegacyPayloadLength(65535));
                }

                private static void testFailureIsolation(
                        byte[] observed, List<Page> pages) {
                    NativeLayeredTerrainStageAssembler assembler =
                        new NativeLayeredTerrainStageAssembler();
                    Page first = pages.get(0);
                    Page second = pages.get(1);
                    Page third = pages.get(2);

                    require(accept(assembler, first, first.copyFragment(),
                            38, 14) == null,
                        "stale stage ignored");
                    require(assembler.getBufferedByteCount() == 0,
                        "stale stage retained no bytes");
                    require(accept(assembler, first, first.copyFragment(),
                            37, 15) == null,
                        "wrong context ignored");
                    require(assembler.getBufferedByteCount() == 0,
                        "wrong context retained no bytes");

                    require(accept(assembler, first, first.copyFragment(),
                            37, 14) == null,
                        "first page pending");
                    int buffered = assembler.getBufferedByteCount();
                    require(accept(assembler, first, first.copyFragment(),
                            37, 14) == null,
                        "duplicate page ignored");
                    require(assembler.getBufferedByteCount() == buffered,
                        "duplicate page did not append");
                    require(accept(assembler, second, second.copyFragment(),
                            37, 14) == null,
                        "second page pending");
                    require(accept(assembler, third, third.copyFragment(),
                            37, 14) != null,
                        "duplicate-safe completion");

                    accept(assembler, first, first.copyFragment(), 37, 14);
                    require(accept(assembler, third, third.copyFragment(),
                            37, 14) == null,
                        "out-of-order page rejected");
                    require(!assembler.hasPendingStage(),
                        "out-of-order page discards partial assembly");

                    accept(assembler, first, first.copyFragment(), 37, 14);
                    accept(assembler, second, second.copyFragment(), 37, 14);
                    byte[] corrupt = third.copyFragment();
                    corrupt[corrupt.length - 1] ^= 0x5a;
                    require(accept(assembler, third, corrupt, 37, 14) == null,
                        "CRC mismatch rejected");
                    require(!assembler.hasPendingStage(),
                        "CRC mismatch releases assembly");

                    byte[] replacement = Arrays.copyOf(
                        observed, observed.length);
                    replacement[replacement.length - 1] ^= 0x33;
                    List<Page> replacementPages =
                        LayeredTerrainStagePaging.split(replacement, 39, 14);
                    accept(assembler, first, first.copyFragment(), 37, 14);
                    accept(
                        assembler,
                        replacementPages.get(0),
                        replacementPages.get(0).copyFragment(),
                        37,
                        14);
                    accept(assembler, second, second.copyFragment(), 37, 14);
                    NativeLayeredTerrainStageAssembler.CompletedStage complete =
                        null;
                    for (int index = 1;
                            index < replacementPages.size(); index++) {
                        Page page = replacementPages.get(index);
                        complete = accept(
                            assembler, page, page.copyFragment(), 37, 14);
                    }
                    require(complete != null
                            && Arrays.equals(
                                replacement, complete.copyBytes()),
                        "newer stage replaces stale partial assembly");
                }

                private static NativeLayeredTerrainStageAssembler.CompletedStage
                        accept(
                            NativeLayeredTerrainStageAssembler assembler,
                            Page page,
                            byte[] fragment,
                            int completedStageSequence,
                            int activeContextSequence) {
                    return assembler.accept(
                        page.getStageSequence(),
                        page.getContextSequence(),
                        page.getTotalBytes(),
                        page.getCrc32(),
                        page.getPageIndex(),
                        page.getPageCount(),
                        fragment,
                        completedStageSequence,
                        activeContextSequence);
                }

                private static byte[] bytes(int length, int seed) {
                    byte[] result = new byte[length];
                    int value = seed;
                    for (int index = 0; index < result.length; index++) {
                        value = value * 1103515245 + 12345;
                        result[index] = (byte) (value >>> 16);
                    }
                    return result;
                }

                private static void expectIllegal(Runnable action) {
                    try {
                        action.run();
                        throw new AssertionError(
                            "Expected IllegalArgumentException");
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
        self._compile_and_run(
            "LayeredTerrainStagePagingHarness",
            harness,
            [
                ROOT
                / "server/src/com/openrsc/server/net/PacketFrameLengthGuard.java",
                ROOT
                / "server/src/com/openrsc/server/net/rsc/"
                "LayeredTerrainStagePaging.java",
                ROOT
                / "Client_Base/src/orsc/"
                "NativeLayeredTerrainStageAssembler.java",
            ],
        )

    def test_transport_is_wired_before_the_common_encoder(self):
        action_sender = (
            ROOT
            / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
        ).read_text(encoding="utf-8")
        encoder = (
            ROOT / "server/src/com/openrsc/server/net/RSCProtocolEncoderMain.java"
        ).read_text(encoding="utf-8")
        handler = (
            ROOT / "Client_Base/src/orsc/PacketHandler.java"
        ).read_text(encoding="utf-8")

        self.assertIn("LayeredTerrainStagePacketPager", action_sender)
        self.assertIn("for (Packet terrainPacket : terrainPackets)", action_sender)
        self.assertIn(
            "PacketFrameLengthGuard.requireSimplifiedPayloadLength", encoder
        )
        self.assertIn("NativeLayeredTerrainStageAssembler", handler)
        self.assertIn("TRANSPORT_PROTOCOL_VERSION", handler)
        self.assertIn("processLayeredTerrainStage(", handler)
        self.assertIn("layeredTerrainStageAssembler.reset();", handler)

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
