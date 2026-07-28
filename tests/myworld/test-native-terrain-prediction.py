#!/usr/bin/env python3

import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class NativeTerrainPredictionTest(unittest.TestCase):
    def test_stage_identity_rejects_every_activation_mismatch(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.server.net.rsc
                .NativeLayeredTerrainStageReadiness;
            import com.openrsc.server.net.rsc.struct.incoming
                .LayeredTerrainStageReadyStruct;
            import com.openrsc.server.net.rsc.struct.outgoing
                .LayeredTerrainStageStruct;

            public final class NativeTerrainStageReadinessHarness {
                private static final String MANIFEST =
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

                public static void main(String[] arguments) {
                    LayeredTerrainStageStruct stage =
                        new LayeredTerrainStageStruct();
                    stage.sequence = 8;
                    stage.contextSequence = 17;
                    stage.worldSpace = "global";
                    stage.logicalLevel = -1;
                    stage.nativeCurrentChunkX = 5;
                    stage.nativeCurrentChunkY = 11;
                    stage.nativeManifestSha256 = MANIFEST;

                    NativeLayeredTerrainStageReadiness expected =
                        NativeLayeredTerrainStageReadiness.from(stage);
                    LayeredTerrainStageReadyStruct receipt = receipt();
                    require(expected.matches(receipt), "exact receipt");
                    require(expected.matchesTarget(
                        "global", -1, 5, 11, MANIFEST),
                        "exact activation target");

                    receipt.stageSequence++;
                    require(!expected.matches(receipt), "stage sequence");
                    receipt = receipt();
                    receipt.contextSequence++;
                    require(!expected.matches(receipt), "context sequence");
                    receipt = receipt();
                    receipt.worldSpace = "instance-1";
                    require(!expected.matches(receipt), "world space");
                    receipt = receipt();
                    receipt.logicalLevel = 0;
                    require(!expected.matches(receipt), "signed level");
                    receipt = receipt();
                    receipt.centerSectorX++;
                    require(!expected.matches(receipt), "center X");
                    receipt = receipt();
                    receipt.centerSectorY++;
                    require(!expected.matches(receipt), "center Y");
                    receipt = receipt();
                    receipt.manifestSha256 =
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                        + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
                    require(!expected.matches(receipt), "manifest");
                    require(!expected.matchesTarget(
                        "global", -1, 6, 11, MANIFEST),
                        "wrong activation center");

                    NativeLayeredTerrainStageReadiness duplicate =
                        NativeLayeredTerrainStageReadiness.from(stage);
                    require(expected.equals(duplicate), "identity equality");
                    require(expected.hashCode() == duplicate.hashCode(),
                        "identity hash");
                }

                private static LayeredTerrainStageReadyStruct receipt() {
                    LayeredTerrainStageReadyStruct receipt =
                        new LayeredTerrainStageReadyStruct();
                    receipt.protocolVersion = 1;
                    receipt.stageSequence = 8;
                    receipt.contextSequence = 17;
                    receipt.worldSpace = "global";
                    receipt.logicalLevel = -1;
                    receipt.centerSectorX = 5;
                    receipt.centerSectorY = 11;
                    receipt.manifestSha256 = MANIFEST;
                    return receipt;
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
            "NativeTerrainStageReadinessHarness",
            harness,
            [
                ROOT
                / "server/src/com/openrsc/server/net/rsc/"
                "NativeLayeredTerrainStageReadiness.java",
                ROOT
                / "server/src/com/openrsc/server/net/rsc/struct/"
                "AbstractStruct.java",
                ROOT
                / "server/src/com/openrsc/server/net/rsc/enums/OpcodeIn.java",
                ROOT
                / "server/src/com/openrsc/server/net/rsc/enums/OpcodeOut.java",
                ROOT
                / "server/src/com/openrsc/server/net/rsc/struct/incoming/"
                "LayeredTerrainStageReadyStruct.java",
                ROOT
                / "server/src/com/openrsc/server/net/rsc/struct/outgoing/"
                "LayeredTerrainStageStruct.java",
                ROOT
                / "server/src/com/openrsc/server/net/rsc/struct/outgoing/"
                "LayeredSceneTerrainChunkStruct.java",
            ],
        )

    def test_client_stage_is_adjacent_transactional_and_cache_only(self):
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

            public final class NativeTerrainStageClientHarness {
                private static final String PACKAGE =
                    "spoiled-milk.layered-map";
                private static final String VERSION = "1.0.0";
                private static final String MANIFEST =
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
                private static final String OTHER_MANIFEST =
                    "cccccccccccccccccccccccccccccccc"
                    + "cccccccccccccccccccccccccccccccc";
                private static final String SOURCE_SHA =
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                    + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

                public static void main(String[] arguments) throws Exception {
                    NativeLayeredTerrainResidentCache cache =
                        new NativeLayeredTerrainResidentCache();
                    NativeLayeredTerrainSnapshot active =
                        NativeLayeredTerrainPacketDecoder.decodeV7(
                            receipt(9, Integer.MIN_VALUE, MANIFEST),
                            "global",
                            -1,
                            cache);
                    require(active.getCurrentChunkX() == 9,
                        "active center");
                    require(cache.size() == 9, "active cache");

                    NativeLayeredTerrainSnapshot staged =
                        NativeLayeredTerrainPacketDecoder.decodeV7Stage(
                            receipt(10, 11, MANIFEST),
                            "global",
                            -1,
                            cache,
                            active);
                    require(active.getCurrentChunkX() == 9,
                        "stage changed active snapshot");
                    require(staged.getCurrentChunkX() == 10,
                        "staged center");
                    require(cache.size() == 12,
                        "six overlap plus three staged sectors");
                    require(cache.getLastPayloads() == 3
                            && cache.getLastReferences() == 6,
                        "staged payload/reference counters");

                    int stableSize = cache.size();
                    expectFailure(
                        () -> NativeLayeredTerrainPacketDecoder.decodeV7Stage(
                            receiptUnchecked(
                                9, Integer.MAX_VALUE, MANIFEST),
                            "global", -1, cache, active),
                        "same center accepted");
                    require(cache.size() == stableSize,
                        "same-center failure committed");

                    expectFailure(
                        () -> NativeLayeredTerrainPacketDecoder.decodeV7Stage(
                            receiptUnchecked(11, 12, MANIFEST),
                            "global", -1, cache, active),
                        "two-center lead accepted");
                    require(cache.size() == stableSize,
                        "far-stage failure committed");

                    expectFailure(
                        () -> NativeLayeredTerrainPacketDecoder.decodeV7Stage(
                            receiptUnchecked(
                                10, Integer.MIN_VALUE, OTHER_MANIFEST),
                            "global", -1, cache, active),
                        "cross-manifest stage accepted");
                    require(cache.size() == stableSize,
                        "cross-manifest failure committed");
                }

                private static byte[] receipt(
                        int centerX,
                        int payloadFromX,
                        String manifest) throws Exception {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream output = new DataOutputStream(bytes);
                    line(output, PACKAGE);
                    line(output, VERSION);
                    line(output, manifest);
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
                            boolean payload = chunkX >= payloadFromX;
                            output.writeByte(payload ? 1 : 0);
                            if (payload) {
                                byte[] compressed = sector(chunkX);
                                output.writeShort(compressed.length);
                                output.write(compressed);
                            }
                        }
                    }
                    output.close();
                    return bytes.toByteArray();
                }

                private static byte[] receiptUnchecked(
                        int centerX,
                        int payloadFromX,
                        String manifest) {
                    try {
                        return receipt(centerX, payloadFromX, manifest);
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
            "NativeTerrainStageClientHarness",
            harness,
            [
                ROOT / "Client_Base/src/com/openrsc/client/model/Tile.java",
                ROOT / "Client_Base/src/orsc/NativeLayeredTerrainChunk.java",
                ROOT
                / "Client_Base/src/orsc/"
                "NativeLayeredTerrainSnapshot.java",
                ROOT
                / "Client_Base/src/orsc/"
                "NativeLayeredTerrainResidentCache.java",
                ROOT
                / "Client_Base/src/orsc/"
                "NativeLayeredTerrainPacketDecoder.java",
            ],
        )

    def test_prediction_is_opt_in_bounded_and_never_activates_client_scene(self):
        configuration = (
            ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
        ).read_text(encoding="utf-8")
        updater = (
            ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
        ).read_text(encoding="utf-8")
        walking_queue = (
            ROOT / "server/src/com/openrsc/server/model/WalkingQueue.java"
        ).read_text(encoding="utf-8")
        generator = (
            ROOT
            / "server/src/com/openrsc/server/net/rsc/generators/impl/"
            "PayloadCustomGenerator.java"
        ).read_text(encoding="utf-8")
        parser = (
            ROOT
            / "server/src/com/openrsc/server/net/rsc/parsers/impl/"
            "PayloadCustomParser.java"
        ).read_text(encoding="utf-8")
        handler = (
            ROOT / "Client_Base/src/orsc/PacketHandler.java"
        ).read_text(encoding="utf-8")
        decoder = (
            ROOT
            / "Client_Base/src/orsc/"
            "NativeLayeredTerrainPacketDecoder.java"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "WANT_LAYERED_NATIVE_TERRAIN_PREDICTION", configuration
        )
        self.assertIn(
            '"OPENRSC_LAYERED_NATIVE_TERRAIN_PREDICTION"', configuration
        )
        self.assertIn(
            '"want_layered_native_terrain_prediction",\n\t\t\tfalse);',
            configuration,
        )
        self.assertIn("player.isTeleporting()", updater)
        self.assertIn("NATIVE_LAYERED_PREDICTIVE_LEAD_TILES = 48", updater)
        self.assertIn("player.getWalkingQueue().path.getWaypoints()", updater)
        self.assertIn(
            "NATIVE_TERRAIN_PENDING_STAGE_ATTRIBUTE, null) != null",
            updater,
        )
        self.assertIn("canActivateNativeTerrainStage(", updater)
        self.assertIn("pending.equals(accepted)", updater)
        self.assertIn(
            "isNativeTerrainActivationMovementHeld(", updater
        )
        self.assertIn(
            "NATIVE_TERRAIN_SELF_APPEARANCE_PENDING_ATTRIBUTE",
            updater.split(
                "public boolean isNativeTerrainActivationMovementHeld", 1
            )[1].split(
                "public void acceptLayeredTerrainStageReady", 1
            )[0],
        )
        hold_call = walking_queue.index(
            ".isNativeTerrainActivationMovementHeld((Player) mob)"
        )
        self.assertLess(
            hold_call,
            walking_queue.index(
                "playerWasWalking = true;", hold_call
            ),
        )
        accept_method = updater.split(
            "public void acceptLayeredTerrainStageReady", 1
        )[1].split(
            "private boolean canActivateNativeTerrainStage", 1
        )[0]
        self.assertIn("stagedTerrain.commitResidency();", accept_method)
        send_method = updater.split(
            "private void maybeSendNativeTerrainStage", 1
        )[1].split(
            "private int[] predictNativeTerrainCenter", 1
        )[0]
        self.assertNotIn("stagedTerrain.commitResidency();", send_method)
        self.assertIn("SEND_LAYERED_TERRAIN_STAGE, 154", generator)
        self.assertIn("case 155:", parser)
        self.assertIn("OpcodeIn.LAYERED_TERRAIN_STAGE_READY", parser)
        self.assertIn("decodeV7Stage(", handler)
        self.assertIn(
            "Opcodes.Out.LAYERED_TERRAIN_STAGE_READY.getOpcode()",
            handler,
        )
        stage_method = handler.split(
            "private void updateLayeredTerrainStage", 1
        )[1].split(
            "private void sendLayeredTerrainStageReady", 1
        )[0]
        self.assertNotIn("applyLayeredSceneScope", stage_method)
        self.assertNotIn("acceptNative(", stage_method)
        self.assertNotIn("sceneBaselineState", stage_method)
        self.assertLess(
            decoder.index("requireAdjacentStage(activeTerrain, result);"),
            decoder.index(
                "residentTransaction.commit();",
                decoder.index("requireAdjacentStage(activeTerrain, result);"),
            ),
        )

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
