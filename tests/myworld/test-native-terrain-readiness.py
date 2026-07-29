#!/usr/bin/env python3

import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class NativeTerrainReadinessTest(unittest.TestCase):
    def test_exact_generation_identity_rejects_every_mismatch(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.server.net.rsc.NativeLayeredTerrainReadiness;
            import com.openrsc.server.net.rsc.struct.incoming
                .LayeredTerrainReadyStruct;
            import com.openrsc.server.net.rsc.struct.outgoing
                .LayeredSceneContextStruct;

            public final class NativeTerrainReadinessHarness {
                private static final String MANIFEST =
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

                public static void main(String[] arguments) {
                    LayeredSceneContextStruct context =
                        new LayeredSceneContextStruct();
                    context.sequence = 17;
                    context.worldSpace = "global";
                    context.logicalLevel = -1;
                    context.nativeCurrentChunkX = 5;
                    context.nativeCurrentChunkY = 11;
                    context.nativeManifestSha256 = MANIFEST;

                    NativeLayeredTerrainReadiness expected =
                        NativeLayeredTerrainReadiness.from(context);
                    LayeredTerrainReadyStruct receipt = receipt();
                    require(expected.matches(receipt), "exact receipt");

                    receipt.contextSequence++;
                    require(!expected.matches(receipt), "stale sequence");
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

                    NativeLayeredTerrainReadiness duplicate =
                        NativeLayeredTerrainReadiness.from(context);
                    require(expected.equals(duplicate), "identity equality");
                    require(expected.hashCode() == duplicate.hashCode(),
                        "identity hash");
                }

                private static LayeredTerrainReadyStruct receipt() {
                    LayeredTerrainReadyStruct receipt =
                        new LayeredTerrainReadyStruct();
                    receipt.protocolVersion = 1;
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
        sources = [
            ROOT / "server/src/com/openrsc/server/net/rsc/"
            "NativeLayeredTerrainReadiness.java",
            ROOT / "server/src/com/openrsc/server/net/rsc/struct/"
            "AbstractStruct.java",
            ROOT / "server/src/com/openrsc/server/net/rsc/enums/OpcodeIn.java",
            ROOT / "server/src/com/openrsc/server/net/rsc/enums/OpcodeOut.java",
            ROOT / "server/src/com/openrsc/server/net/rsc/struct/incoming/"
            "LayeredTerrainReadyStruct.java",
            ROOT / "server/src/com/openrsc/server/net/rsc/struct/outgoing/"
            "LayeredSceneContextStruct.java",
            ROOT / "server/src/com/openrsc/server/net/rsc/struct/outgoing/"
            "LayeredSceneTerrainChunkStruct.java",
        ]
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            harness_path = work / "NativeTerrainReadinessHarness.java"
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
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    str(work),
                    "NativeTerrainReadinessHarness",
                ],
                cwd=ROOT,
                check=True,
            )

    def test_v7_gate_is_opt_in_and_v6_remains_the_rollback(self):
        configuration = (
            ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
        ).read_text(encoding="utf-8")
        updater = (
            ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
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
            ROOT / "Client_Base/src/orsc/"
            "NativeLayeredTerrainPacketDecoder.java"
        ).read_text(encoding="utf-8")
        snapshot = (
            ROOT / "Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "WANT_LAYERED_NATIVE_TERRAIN_READINESS", configuration
        )
        self.assertIn(
            '"OPENRSC_LAYERED_NATIVE_TERRAIN_READINESS"', configuration
        )
        self.assertIn(
            '"want_layered_native_terrain_readiness",\n\t\t\tfalse);',
            configuration,
        )
        self.assertIn(
            "READY_RESIDENT_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION = 7",
            updater,
        )
        self.assertIn(
            "RESIDENT_NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION = 6",
            updater,
        )
        self.assertIn("hasAcceptedNativeTerrainReadiness(player)", updater)
        self.assertIn("return false;", updater)
        self.assertIn("acceptLayeredTerrainReady(", updater)
        self.assertIn("NativeLayeredTerrainReadiness.from(context)", updater)
        self.assertIn(
            "NATIVE_TERRAIN_SELF_APPEARANCE_PENDING_ATTRIBUTE", updater
        )
        self.assertIn(
            "player.getUpdateFlags().hasAppearanceChanged()\n"
            "\t\t\t|| selfAppearancePending",
            updater,
        )
        self.assertIn(
            "player.removeAttribute(\n"
            "\t\t\t\tNATIVE_TERRAIN_SELF_APPEARANCE_PENDING_ATTRIBUTE);",
            updater,
        )
        self.assertIn("case 154:", parser)
        self.assertIn("OpcodeIn.LAYERED_TERRAIN_READY", parser)
        self.assertIn("decodeV7(", decoder)
        self.assertIn("READINESS_PROTOCOL_VERSION = 7", snapshot)
        self.assertIn("sendLayeredTerrainReady(", handler)
        self.assertIn("LAYERED_TERRAIN_READY.getOpcode()", handler)
        self.assertIn("terrain.getManifestSha256()", handler)
        self.assertIn('" | ack "', handler)


if __name__ == "__main__":
    unittest.main()
