#!/usr/bin/env python3

import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_RESIDENCY = (
    ROOT
    / "server/src/com/openrsc/server/net/rsc/"
    "NativeLayeredTerrainClientResidency.java"
)
CLIENT_TILE = ROOT / "Client_Base/src/com/openrsc/client/model/Tile.java"
CLIENT_CHUNK = ROOT / "Client_Base/src/orsc/NativeLayeredTerrainChunk.java"
CLIENT_SNAPSHOT = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java"
)
CLIENT_RESIDENCY = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainResidentCache.java"
)
CLIENT_DECODER = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainPacketDecoder.java"
)


class NativeTerrainResidencyTest(unittest.TestCase):
    def test_server_mirror_is_transactional_and_lru_bounded(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.server.net.rsc
                .NativeLayeredTerrainClientResidency;

            public final class NativeTerrainServerResidencyHarness {
                public static void main(String[] arguments) {
                    NativeLayeredTerrainClientResidency residency =
                        new NativeLayeredTerrainClientResidency(9);
                    NativeLayeredTerrainClientResidency.Transaction first =
                        residency.begin();
                    for (int index = 0; index < 9; index++) {
                        require(first.requiresPayload("sector-" + index),
                            "initial payload " + index);
                    }
                    require(residency.size() == 0,
                        "uncommitted receipt changed residency");
                    first.commit();
                    require(residency.size() == 9, "initial commit size");

                    /* v0.2.68: stage 213 predicted (7,11), then context 85
                     * activated (7,10) in the same tick. A superseded
                     * prediction is deliberately not committed, so the new
                     * authoritative context must carry a payload. */
                    NativeLayeredTerrainClientResidency.Transaction predicted =
                        residency.begin();
                    require(predicted.requiresPayload("prediction-7,11"),
                        "stage 213 prediction payload");
                    NativeLayeredTerrainClientResidency.Transaction context85 =
                        residency.begin();
                    require(context85.requiresPayload("context-7,10"),
                        "context 85 cannot reference superseded prediction");
                    context85.commit();

                    NativeLayeredTerrainClientResidency.Transaction overlap =
                        residency.begin();
                    for (int index = 3; index < 9; index++) {
                        require(!overlap.requiresPayload("sector-" + index),
                            "overlap reference " + index);
                    }
                    for (int index = 9; index < 12; index++) {
                        require(overlap.requiresPayload("sector-" + index),
                            "new payload " + index);
                    }
                    overlap.commit();
                    require(residency.size() == 9, "bounded overlap size");

                    NativeLayeredTerrainClientResidency.Transaction inspect =
                        residency.begin();
                    require(!inspect.requiresPayload("sector-3"),
                        "recent overlap was evicted");
                    require(inspect.requiresPayload("sector-0"),
                        "oldest sector was not evicted");

                    NativeLayeredTerrainClientResidency.Transaction left =
                        residency.begin();
                    NativeLayeredTerrainClientResidency.Transaction right =
                        residency.begin();
                    left.requiresPayload("left");
                    left.commit();
                    right.requiresPayload("right");
                    try {
                        right.commit();
                        throw new AssertionError(
                            "stale transaction was committed");
                    } catch (IllegalStateException expected) {
                        // Expected.
                    }

                    residency.clear();
                    require(residency.size() == 0, "reconnect clear");
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
            "NativeTerrainServerResidencyHarness",
            harness,
            [SERVER_RESIDENCY],
        )

    def test_full_cache_prediction_context_halo_order_stays_in_lockstep(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.server.net.rsc
                .NativeLayeredTerrainClientResidency;
            import java.util.Arrays;
            import orsc.NativeLayeredTerrainChunk;
            import orsc.NativeLayeredTerrainResidentCache;

            /**
             * End-to-end cache ordering model for the v0.2.68 failure:
             * stage 213 predicts (7,11), context 85 supersedes it with
             * (7,10), then the acknowledged context is followed by its halo.
             */
            public final class NativeTerrainFullCacheOrderingHarness {
                private static final String SHA =
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

                public static void main(String[] arguments) {
                    NativeLayeredTerrainClientResidency server =
                        new NativeLayeredTerrainClientResidency(64);
                    NativeLayeredTerrainResidentCache client =
                        new NativeLayeredTerrainResidentCache(64);

                    deliver(server, client, range("context-0-", 0, 64),
                        "initial authoritative context");
                    require(server.size() == 64 && client.size() == 64,
                        "initial full cache capacity");

                    /* Stage 213 predicts (7,11), but it is superseded. The
                     * new entries and LRU touches stay transaction-private on
                     * both peers, so canonical residency remains identical. */
                    predict(server, client, concat(
                        range("context-0-", 48, 64),
                        range("prediction-7,11-", 0, 16)),
                        "stage 213 prediction (7,11)");

                    /* Context 85 for (7,10) is delivered and acknowledged.
                     * Its commit is the only canonical change before halo. */
                    deliver(server, client, concat(
                        range("context-0-", 16, 64),
                        range("context-7,10-", 0, 16)),
                        "context 85 acknowledgement (7,10)");

                    /* The initial 5x5 symmetric halo is created only after
                     * context acknowledgement, therefore from this committed
                     * cache version rather than the earlier one. */
                    deliver(server, client, concat(
                        range("context-0-", 40, 64),
                        new String[] {"halo-7,10"}),
                        "initial symmetric halo delivery");

                    /* A later prediction must again leave canonical order
                     * untouched, including after 64-entry capacity churn. */
                    predict(server, client, concat(
                        new String[] {"halo-7,10"},
                        range("subsequent-prediction-", 0, 24)),
                        "subsequent prediction");
                }

                private static void deliver(
                        NativeLayeredTerrainClientResidency server,
                        NativeLayeredTerrainResidentCache client,
                        String[] identities, String label) {
                    NativeLayeredTerrainClientResidency.Transaction serverTx =
                        server.begin();
                    NativeLayeredTerrainResidentCache.Transaction clientTx =
                        client.begin();
                    for (String identity : identities) {
                        if (serverTx.requiresPayload(identity)) {
                            clientTx.acceptPayload(identity, chunk());
                        } else {
                            clientTx.resolveReference(identity);
                        }
                    }
                    /* Client decode commits on receipt; server commits only
                     * when that receipt is acknowledged. */
                    clientTx.commit();
                    serverTx.commit();
                    assertParity(server, client, label);
                }

                private static void predict(
                        NativeLayeredTerrainClientResidency server,
                        NativeLayeredTerrainResidentCache client,
                        String[] identities, String label) {
                    NativeLayeredTerrainClientResidency.Transaction serverTx =
                        server.begin();
                    NativeLayeredTerrainResidentCache.Transaction clientTx =
                        client.begin();
                    for (String identity : identities) {
                        if (serverTx.requiresPayload(identity)) {
                            clientTx.acceptPayload(identity, chunk());
                        } else {
                            clientTx.resolveReference(identity);
                        }
                    }
                    /* Predictions have no authoritative receipt. Deliberately
                     * discard both staged transactions without committing. */
                    assertParity(server, client, label);
                }

                private static void assertParity(
                        NativeLayeredTerrainClientResidency server,
                        NativeLayeredTerrainResidentCache client,
                        String label) {
                    require(server.size() == client.size(),
                        label + " size parity");
                    require(server.getAccessOrder().equals(
                            client.getAccessOrder()),
                        label + " access-order parity: server="
                            + server.getAccessOrder() + " client="
                            + client.getAccessOrder());
                }

                private static String[] range(
                        String prefix, int first, int endExclusive) {
                    String[] values = new String[endExclusive - first];
                    for (int index = first; index < endExclusive; index++) {
                        values[index - first] = prefix + index;
                    }
                    return values;
                }

                private static String[] concat(
                        String[] left, String[] right) {
                    String[] result = Arrays.copyOf(
                        left, left.length + right.length);
                    System.arraycopy(right, 0, result, left.length,
                        right.length);
                    return result;
                }

                private static NativeLayeredTerrainChunk chunk() {
                    return NativeLayeredTerrainChunk.available(
                        1, 0, 0, 0, 0,
                        NativeLayeredTerrainChunk.UNIFORM_ENCODING,
                        SHA, new byte[10]);
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
            "NativeTerrainFullCacheOrderingHarness",
            harness,
            [
                SERVER_RESIDENCY,
                CLIENT_TILE,
                CLIENT_CHUNK,
                CLIENT_SNAPSHOT,
                CLIENT_RESIDENCY,
            ],
        )

    def test_client_v6_decodes_overlap_and_rejects_missing_references(self):
        harness = textwrap.dedent(
            """
            import java.io.ByteArrayOutputStream;
            import java.io.DataOutputStream;
            import java.nio.charset.StandardCharsets;
            import java.util.Arrays;
            import java.util.zip.Deflater;
            import orsc.NativeLayeredTerrainPacketDecoder;
            import orsc.NativeLayeredTerrainResidentCache;
            import orsc.NativeLayeredTerrainSnapshot;

            public final class NativeTerrainClientResidencyHarness {
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
                    NativeLayeredTerrainResidentCache cache =
                        new NativeLayeredTerrainResidentCache();
                    NativeLayeredTerrainSnapshot first =
                        NativeLayeredTerrainPacketDecoder.decodeV6(
                            receipt(9, true, false),
                            "global",
                            -2,
                            cache);
                    require(first.getProtocolVersion() == 6, "v6 protocol");
                    require(first.getAvailableChunkCount() == 9,
                        "initial readiness");
                    require(cache.size() == 9, "initial resident size");
                    require(cache.getLastPayloads() == 9
                            && cache.getLastReferences() == 0,
                        "initial receipt counters");

                    NativeLayeredTerrainSnapshot shifted =
                        NativeLayeredTerrainPacketDecoder.decodeV6(
                            receipt(10, false, false),
                            "global",
                            -2,
                            cache);
                    require(shifted.getAvailableChunkCount() == 9,
                        "shifted readiness");
                    require(cache.size() == 12, "six overlap plus three new");
                    require(cache.getLastPayloads() == 3
                            && cache.getLastReferences() == 6,
                        "shifted receipt counters");
                    require(
                        (shifted.createTile(9 * 48, 12 * 48)
                            .groundElevation & 0xff) == 9,
                        "resident reference tile");
                    require(
                        (shifted.createTile(11 * 48, 12 * 48)
                            .groundElevation & 0xff) == 11,
                        "new payload tile");

                    NativeLayeredTerrainResidentCache reconnect =
                        new NativeLayeredTerrainResidentCache();
                    expectFailure(
                        () -> NativeLayeredTerrainPacketDecoder.decodeV6(
                            receiptUnchecked(10, false, false),
                            "global",
                            -2,
                            reconnect),
                        "fresh connection accepted references");
                    require(reconnect.size() == 0,
                        "failed reference changed fresh cache");

                    NativeLayeredTerrainResidentCache malformed =
                        new NativeLayeredTerrainResidentCache();
                    expectFailure(
                        () -> NativeLayeredTerrainPacketDecoder.decodeV6(
                            receiptUnchecked(9, true, true),
                            "global",
                            -2,
                            malformed),
                        "trailing receipt was accepted");
                    require(malformed.size() == 0,
                        "malformed receipt partially poisoned cache");

                    cache.clear();
                    require(cache.getLastPayloads() == 0
                            && cache.getLastReferences() == 0,
                        "clear receipt counters");
                    expectFailure(
                        () -> NativeLayeredTerrainPacketDecoder.decodeV6(
                            receiptUnchecked(10, false, false),
                            "global",
                            -2,
                            cache),
                        "cleared reconnect cache accepted references");
                }

                private static byte[] receipt(
                        int centerX,
                        boolean allPayloads,
                        boolean trailing) throws Exception {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream output = new DataOutputStream(bytes);
                    line(output, PACKAGE);
                    line(output, VERSION);
                    line(output, MANIFEST);
                    output.writeByte(48);
                    output.writeInt(centerX);
                    output.writeInt(12);
                    output.writeByte(1);
                    output.writeByte(9);
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        for (int deltaY = -1; deltaY <= 1; deltaY++) {
                            int chunkX = centerX + deltaX;
                            int chunkY = 12 + deltaY;
                            output.writeInt(chunkX);
                            output.writeInt(chunkY);
                            output.writeByte(1);
                            output.writeInt(chunkX);
                            output.writeInt(chunkY);
                            line(output, "raw-layered-sector-v1");
                            line(output, SOURCE_SHA);
                            boolean payload =
                                allPayloads || chunkX >= 11;
                            output.writeByte(payload ? 1 : 0);
                            if (payload) {
                                byte[] compressed = sector(chunkX);
                                output.writeShort(compressed.length);
                                output.write(compressed);
                            }
                        }
                    }
                    if (trailing) {
                        output.writeByte(99);
                    }
                    output.close();
                    return bytes.toByteArray();
                }

                private static byte[] receiptUnchecked(
                        int centerX,
                        boolean allPayloads,
                        boolean trailing) {
                    try {
                        return receipt(centerX, allPayloads, trailing);
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    }
                }

                private static byte[] sector(int elevation) {
                    byte[] raw = new byte[48 * 48 * 10];
                    for (int offset = 0; offset < raw.length; offset += 10) {
                        raw[offset] = (byte) elevation;
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
            "NativeTerrainClientResidencyHarness",
            harness,
            [
                CLIENT_TILE,
                CLIENT_CHUNK,
                CLIENT_SNAPSHOT,
                CLIENT_RESIDENCY,
                CLIENT_DECODER,
            ],
        )

    def test_v6_gate_wire_and_disconnect_contracts_are_integrated(self):
        configuration = (
            ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
        ).read_text(encoding="utf-8")
        updater = (
            ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
        ).read_text(encoding="utf-8")
        generator = (
            ROOT
            / "server/src/com/openrsc/server/net/rsc/generators/impl/"
            "PayloadCustomGenerator.java"
        ).read_text(encoding="utf-8")
        action_sender = (
            ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
        ).read_text(encoding="utf-8")
        packet_handler = (
            ROOT / "Client_Base/src/orsc/PacketHandler.java"
        ).read_text(encoding="utf-8")
        applet = (
            ROOT / "PC_Client/src/orsc/ORSCApplet.java"
        ).read_text(encoding="utf-8")

        self.assertIn("WANT_LAYERED_NATIVE_TERRAIN_RESIDENCY", configuration)
        self.assertIn('"want_layered_native_terrain_residency",', configuration)
        self.assertIn(
            '"want_layered_native_terrain_residency",\n\t\t\tfalse);',
            configuration,
        )
        self.assertIn("residencyTransaction.requiresPayload(", updater)
        self.assertIn("acknowledgedTerrain.commitResidency();", updater)
        self.assertIn(
            "NATIVE_TERRAIN_READINESS_TRANSACTION_ATTRIBUTE", updater
        )
        self.assertIn("receipt.protocolVersion == 2", updater)
        self.assertIn("final boolean residencyAcknowledged", updater)
        self.assertIn(
            "if (ready && residencyAcknowledged && nativeTerrain != null)",
            updater,
        )
        self.assertIn(
            "initial symmetric halo is deliberately deferred until this",
            updater,
        )
        self.assertIn("tryFinalizeAndSendPacketChecked(", updater)
        self.assertIn(
            "public static boolean tryFinalizeAndSendPacketChecked(",
            action_sender,
        )
        self.assertIn("player.write(p);", action_sender)
        self.assertIn("return true;", action_sender)
        self.assertIn("context.protocolVersion >= 6", generator)
        self.assertIn("chunk.payloadPresent ? 1 : 0", generator)
        self.assertIn(
            "nativeLayeredTerrainResidentCache.clear();", packet_handler
        )
        self.assertIn("requestNativeTerrainResynchronization(", packet_handler)
        self.assertIn("MissingReferenceException", packet_handler)
        self.assertIn(
            "NativeLayeredTerrainPacketDecoder.decodeV6(", packet_handler
        )
        self.assertIn('" residentSectors="', packet_handler)
        self.assertIn('" lastPayloads="', packet_handler)
        self.assertIn('" lastReferences="', packet_handler)
        self.assertIn(
            "getLayeredTerrainDeliveryDebugSummaryLine()", packet_handler
        )
        self.assertIn(
            "activePacketHandler.getLayeredTerrainDeliveryDebugSummaryLine()",
            applet,
        )
        region_refresh = updater.split(
            "private static void updateCustomMovementClientRegion", 1
        )[1].split("private static int currentClientLocalBaseX", 1)[0]
        self.assertIn("if (viewer.isTeleporting()) {", region_refresh)
        self.assertIn(
            "midpointX = clientLocalMidpointForTile(\n"
            "\t\t\t\tviewer.getX(), CLIENT_LOCAL_PLANE_WIDTH);",
            region_refresh,
        )
        self.assertIn(
            "midpointY = clientLocalMidpointForTile(\n"
            "\t\t\t\tviewer.getY(), CLIENT_LOCAL_PLANE_HEIGHT);",
            region_refresh,
        )
        self.assertLess(
            region_refresh.index("if (viewer.isTeleporting()) {"),
            region_refresh.index("CLIENT_LOCAL_REGION_RELOAD_RADIUS"),
        )

    def _compile_and_run(self, class_name, harness, sources):
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            harness_path = work / f"{class_name}.java"
            harness_path.write_text(harness, encoding="utf-8")
            compile_sources = list(sources)
            if CLIENT_DECODER in compile_sources:
                profile_stub = work / "orsc/WorldBuilderClientProfile.java"
                profile_stub.parent.mkdir(parents=True)
                profile_stub.write_text(
                    textwrap.dedent(
                        """
                        package orsc;

                        public final class WorldBuilderClientProfile {
                            private static final WorldBuilderClientProfile CURRENT =
                                new WorldBuilderClientProfile();

                            public static WorldBuilderClientProfile current() {
                                return CURRENT;
                            }

                            public void requireNativePackageIdentity(
                                    String packageId,
                                    String packageVersion,
                                    String manifestSha256) {
                                if (packageId == null || packageVersion == null
                                        || manifestSha256 == null) {
                                    throw new IllegalArgumentException(
                                        "native package identity is required");
                                }
                            }
                        }
                        """
                    ),
                    encoding="utf-8",
                )
                compile_sources.append(profile_stub)
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
                    *[str(source) for source in compile_sources],
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
