#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_STATE = (
    ROOT / "Client_Base/src/orsc/LayeredSceneContextState.java"
)
NATIVE_TERRAIN = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java"
)
NATIVE_CHUNK = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainChunk.java"
)
NATIVE_DECODER = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainPacketDecoder.java"
)
CLIENT_TILE = (
    ROOT / "Client_Base/src/com/openrsc/client/model/Tile.java"
)
CLIENT_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
CLIENT_WORLD = ROOT / "Client_Base/src/orsc/graphics/three/World.java"
CLIENT_SECTOR = (
    ROOT / "Client_Base/src/com/openrsc/client/model/Sector.java"
)
SCENE_BASELINE_STATE = ROOT / "Client_Base/src/orsc/SceneBaselineState.java"
MOVEMENT_STAGE = ROOT / "Client_Base/src/orsc/MovementSnapshotStage.java"
CONFIGURATION = ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
REGION_MANAGER = (
    ROOT
    / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
GAME_STATE_UPDATER = ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
OPCODES = ROOT / "server/src/com/openrsc/server/net/rsc/enums/OpcodeOut.java"
PAYLOAD_VALIDATOR = (
    ROOT / "server/src/com/openrsc/server/net/rsc/PayloadValidator.java"
)
CUSTOM_GENERATOR = (
    ROOT
    / "server/src/com/openrsc/server/net/rsc/generators/impl/"
    "PayloadCustomGenerator.java"
)
CONTEXT_STRUCT = (
    ROOT
    / "server/src/com/openrsc/server/net/rsc/struct/outgoing/"
    "LayeredSceneContextStruct.java"
)
PLAN = (
    ROOT
    / "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r"""
package orsc;

public final class LayeredProtocolClientAuthorityFixture {
    public static void main(String[] args) {
        LayeredSceneContextState state = new LayeredSceneContextState();
        check("layer client waiting".equals(state.summary()), "initial state");

        LayeredSceneContextState.ApplyResult surface = state.accept(
            1, 1, 100, "global", 100, 400, 0, 100, 400);
        check(!surface.isScopeChanged(), "initial context is not a transition");
        check(surface.getLegacyPlane() == 0, "surface plane");
        check(!surface.isSyntheticDeepFixture(), "surface terrain mode");
        check(state.matchesSequence(1), "surface sequence");
        check("global:0:1".equals(state.scopeIdentity()), "surface scope");
        state.acceptLegacyPlayerPosition(100, 400);
        state.acceptLegacyPlayerPosition(101, 401);
        check(state.summary().contains("101,401,L0"), "surface movement");

        expectState(() -> state.accept(
            1, 1, 101, "global", 101, 401, 0, 101, 401));
        expectIllegal(() -> state.accept(
            1, 2, 102, "global", 101, 401, 0, 101, 402));
        check(state.matchesSequence(1), "failed receipt is atomic");

        LayeredSceneContextState.ApplyResult sameScope = state.accept(
            1, 2, 103, "global", 101, 401, 0, 101, 401);
        check(!sameScope.isScopeChanged(), "same-scope refresh");
        state.acceptLegacyPlayerPosition(101, 401);

        LayeredSceneContextState.ApplyResult upper = state.accept(
            1, 3, 104, "global", 101, 401, 1, 101, 1345);
        check(upper.isScopeChanged(), "upper transition");
        check(upper.getLegacyPlane() == 1, "upper plane");
        state.acceptLegacyPlayerPosition(101, 1345);
        expectState(() -> state.acceptLegacyPlayerPosition(101, 401));

        LayeredSceneContextState.ApplyResult underground = state.accept(
            1, 4, 105, "global", 101, 401, -1, 101, 3233);
        check(underground.isScopeChanged(), "underground transition");
        check(underground.getLegacyPlane() == 3, "underground plane");
        state.acceptLegacyPlayerPosition(101, 3233);
        check(state.summary().contains("contexts/positions/scopes 4/5/2"),
            "acceptance counters");

        expectIllegal(() -> state.accept(
            1, 5, 106, "instance-1", 101, 401, -1, 101, 3233));
        expectIllegal(() -> state.accept(
            1, 5, 106, "global", 101, 401, -2, 101, 401));
        expectIllegal(() -> state.accept(
            2, 5, 106, "global", 101, 401, -1, 101, 3233));
        expectIllegal(() -> state.accept(
            1, 5, 106, "Bad Space", 101, 401, -1, 101, 3233));
        check(state.matchesSequence(4), "refusals preserve context");

        LayeredSceneContextState.ApplyResult deep = state.accept(
            2, 5, 107, "global", "synthetic-deep-fixture-v1",
            450, 600, -2, 450, 600);
        check(deep.isScopeChanged(), "deep transition");
        check(deep.getLegacyPlane() == 0, "deep compatibility plane");
        check(deep.isSyntheticDeepFixture(), "deep terrain mode");
        check("global:-2:synthetic-deep-fixture-v1:5".equals(
            state.scopeIdentity()), "deep scope identity");
        state.acceptLegacyPlayerPosition(450, 600);
        state.acceptLegacyPlayerPosition(451, 600);
        check(state.summary().contains("451,600,L-2"),
            "deep movement");
        expectIllegal(() -> state.acceptLegacyPlayerPosition(461, 600));
        check(state.summary().contains("contexts/positions/scopes 5/7/3"),
            "deep acceptance counters");

        NativeLayeredTerrainSnapshot nativeLeft = nativeTerrain(-2, 9, 12, 0);
        check(nativeLeft.getPresentationChunkSize() == 24,
            "presentation size independent from storage page");
        check(nativeLeft.createUniformTile().groundOverlay == 0,
            "native tile decode");
        LayeredSceneContextState.ApplyResult nativeDeep = state.acceptNative(
            NativeLayeredTerrainSnapshot.UNIFORM_PAGE_PROTOCOL_VERSION,
            6, 108, "global", "native-layered-package-v1",
            450, 600, -2, 450, 600, nativeLeft);
        check(nativeDeep.isScopeChanged(), "native transition");
        check(nativeDeep.getLegacyPlane() == 0, "native compatibility plane");
        check(!nativeDeep.isSyntheticDeepFixture(), "native is not synthetic");
        check(nativeDeep.getNativeTerrainSnapshot().equals(nativeLeft),
            "native terrain result");
        state.acceptLegacyPlayerPosition(450, 600);
        state.acceptLegacyPlayerPosition(479, 600);
        expectIllegal(() -> state.acceptLegacyPlayerPosition(480, 600));

        NativeLayeredTerrainSnapshot nativeRight = nativeTerrain(-2, 10, 12, 4);
        LayeredSceneContextState.ApplyResult adjacent = state.acceptNative(
            NativeLayeredTerrainSnapshot.UNIFORM_PAGE_PROTOCOL_VERSION,
            7, 109, "global", "native-layered-package-v1",
            480, 600, -2, 480, 600, nativeRight);
        check(adjacent.isScopeChanged(), "native page transition");
        state.acceptLegacyPlayerPosition(480, 600);

        NativeLayeredTerrainSnapshot expanded = nativeTerrain(-37, 9, 12, 8);
        LayeredSceneContextState.ApplyResult arbitraryDepth = state.acceptNative(
            NativeLayeredTerrainSnapshot.UNIFORM_PAGE_PROTOCOL_VERSION,
            8, 110, "global", "native-layered-package-v1",
            450, 600, -37, 450, 600, expanded);
        check(arbitraryDepth.isScopeChanged(), "arbitrary signed level transition");
        check(arbitraryDepth.getLegacyPlane() == 0, "arbitrary compatibility plane");
        state.acceptLegacyPlayerPosition(450, 600);

        NativeLayeredTerrainSnapshot chunked = chunkTerrain(-2, 18, 25);
        check(chunked.getProtocolVersion() == 4, "chunk protocol");
        check(chunked.getAvailableChunkCount() == 4,
            "explicit ready and void chunk slots");
        check(chunked.covers("global", -2, 450, 600),
            "chunk readiness covers receipt");
        check((chunked.createTile(440, 600).groundTexture & 0xff) == 1,
            "first non-uniform chunk band");
        check((chunked.createTile(448, 600).groundElevation & 0xff) == 4
                && (chunked.createTile(448, 600).groundTexture & 0xff) == 2,
            "second non-uniform chunk band");
        check((chunked.createTile(456, 600).groundTexture & 0xff) == 1,
            "neighbor chunk band");
        LayeredSceneContextState.ApplyResult chunkedDeep = state.acceptNative(
            4, 9, 111, "global", "native-layered-package-v1",
            450, 600, -2, 450, 600, chunked);
        check(chunkedDeep.isScopeChanged(), "v3 to v4 transition");
        state.acceptLegacyPlayerPosition(450, 600);
        state.acceptLegacyPlayerPosition(479, 600);
        expectIllegal(() -> state.acceptLegacyPlayerPosition(480, 600));

        NativeLayeredTerrainSnapshot shifted = chunkTerrain(-2, 19, 25);
        LayeredSceneContextState.ApplyResult shiftedResult = state.acceptNative(
            4, 10, 112, "global", "native-layered-package-v1",
            480, 600, -2, 480, 600, shifted);
        check(!shiftedResult.isScopeChanged(),
            "chunk readiness shift is not a world-scope reset");
        check(shifted.getAvailableChunkCount() == 6,
            "shifted readiness adds adjacent storage-page chunks");
        state.acceptLegacyPlayerPosition(480, 600);

        state.reset();
        check(!state.hasContext(), "logout reset");
        state.accept(1, 1, 200, "global", 120, 648, 0, 120, 648);
        state.acceptLegacyPlayerPosition(120, 648);
        check(state.matchesSequence(1), "reconnect sequence restart");

        state.reset();
        state.accept(
            2, 1, 201, "global", "synthetic-deep-fixture-v1",
            450, 600, -2, 450, 600);
        state.acceptLegacyPlayerPosition(450, 600);
        check(state.matchesSequence(1)
            && state.summary().contains("450,600,L-2"),
            "deep reconnect sequence restart");

        state.reset();
        state.acceptNative(
            4, 1, 202, "global", "native-layered-package-v1",
            450, 600, -2, 450, 600, chunked);
        state.acceptLegacyPlayerPosition(450, 600);
        check(state.matchesSequence(1)
            && state.summary().contains("native terrain"),
            "native reconnect sequence restart");
    }

    private static NativeLayeredTerrainSnapshot chunkTerrain(
            int level, int centerChunkX, int centerChunkY) {
        NativeLayeredTerrainChunk[] chunks =
            new NativeLayeredTerrainChunk[9];
        int index = 0;
        for (int deltaX = -1; deltaX <= 1; deltaX++) {
            for (int deltaY = -1; deltaY <= 1; deltaY++) {
                int chunkX = centerChunkX + deltaX;
                int chunkY = centerChunkY + deltaY;
                if (chunkX >= 18 && chunkX <= 21
                        && chunkY >= 24 && chunkY <= 25) {
                    int sectorX = Math.floorDiv(chunkX * 24, 48);
                    chunks[index] = NativeLayeredTerrainChunk.available(
                        24,
                        chunkX,
                        chunkY,
                        sectorX,
                        12,
                        sectorX == 9
                            ? "rle-layered-sector-v1"
                            : "uniform-layered-sector-v1",
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        terrainBytes(chunkX));
                } else {
                    chunks[index] = NativeLayeredTerrainChunk.voidChunk(
                        24, chunkX, chunkY);
                }
                index++;
            }
        }
        return new NativeLayeredTerrainSnapshot(
            "rsc-remastered.native-loader-lab",
            "0.2.0",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            24,
            "global",
            level,
            centerChunkX,
            centerChunkY,
            1,
            chunks);
    }

    private static byte[] terrainBytes(int chunkX) {
        byte[] result = new byte[24 * 24 * 10];
        for (int localX = 0; localX < 24; localX++) {
            for (int localY = 0; localY < 24; localY++) {
                int offset = (localX * 24 + localY) * 10;
                if (chunkX == 18 && localX < 8) {
                    result[offset] = (byte) 255;
                    result[offset + 1] = (byte) 254;
                    result[offset + 2] = (byte) 253;
                    result[offset + 3] = (byte) 252;
                    result[offset + 4] = (byte) 251;
                    result[offset + 5] = (byte) 250;
                    result[offset + 6] = (byte) 255;
                    result[offset + 7] = (byte) 255;
                    result[offset + 8] = (byte) 255;
                    result[offset + 9] = (byte) 255;
                } else if ((chunkX == 18 && localX < 16)
                        || (chunkX == 19 && localX < 8)) {
                    result[offset + 1] = 1;
                } else if (chunkX == 18 || chunkX >= 20) {
                    result[offset] = 4;
                    result[offset + 1] = 2;
                }
            }
        }
        return result;
    }

    private static NativeLayeredTerrainSnapshot nativeTerrain(
            int level, int sectorX, int sectorY, int elevation) {
        return new NativeLayeredTerrainSnapshot(
            "rsc-remastered.native-loader-lab",
            "0.2.0",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            24,
            "global",
            level,
            sectorX,
            sectorY,
            "uniform-layered-sector-v1",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            elevation,
            0,
            0,
            0,
            0,
            0,
            0);
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


class LayeredProtocolClientAuthorityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-protocol-client-authority-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        fixture = cls.temp / "src/orsc/LayeredProtocolClientAuthorityFixture.java"
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac",
                "-Xlint:all",
                "-source",
                "8",
                "-target",
                "8",
                "-encoding",
                "UTF-8",
                "-d",
                str(cls.classes),
                str(CLIENT_TILE),
                str(NATIVE_CHUNK),
                str(NATIVE_TERRAIN),
                str(CLIENT_STATE),
                str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_context_receipts_scope_changes_refusals_and_reconnect(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "orsc.LayeredProtocolClientAuthorityFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_gate_protocol_and_client_consumers_are_explicit(self):
        configuration = CONFIGURATION.read_text(encoding="utf-8")
        region_manager = REGION_MANAGER.read_text(encoding="utf-8")
        updater = GAME_STATE_UPDATER.read_text(encoding="utf-8")
        opcodes = OPCODES.read_text(encoding="utf-8")
        validator = PAYLOAD_VALIDATOR.read_text(encoding="utf-8")
        generator = CUSTOM_GENERATOR.read_text(encoding="utf-8")
        handler = CLIENT_HANDLER.read_text(encoding="utf-8")
        native_decoder = NATIVE_DECODER.read_text(encoding="utf-8")
        client = CLIENT.read_text(encoding="utf-8")
        client_world = CLIENT_WORLD.read_text(encoding="utf-8")
        client_sector = CLIENT_SECTOR.read_text(encoding="utf-8")
        baseline = SCENE_BASELINE_STATE.read_text(encoding="utf-8")
        movement_stage = MOVEMENT_STAGE.read_text(encoding="utf-8")
        context_struct = CONTEXT_STRUCT.read_text(encoding="utf-8")

        self.assertIn("WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY", configuration)
        self.assertIn(
            "OPENRSC_LAYERED_PROTOCOL_CLIENT_AUTHORITY", configuration
        )
        self.assertIn('"want_layered_protocol_client_authority"', configuration)
        self.assertIn(
            "Layered protocol/client authority requires layered Player",
            region_manager,
        )
        self.assertIn("ensureLayeredSceneContext(player)", updater)
        self.assertIn("LAYERED_SCENE_BASELINE_PROTOCOL_VERSION = 6", updater)
        self.assertIn(
            "LAYERED_MOVEMENT_SNAPSHOT_PROTOCOL_VERSION = 2", updater
        )
        self.assertIn("SEND_LAYERED_SCENE_CONTEXT", opcodes)
        self.assertIn("LayeredSceneContextStruct.class", validator)
        self.assertIn("SEND_LAYERED_SCENE_CONTEXT, 152", generator)
        self.assertIn("updateLayeredSceneContext(length)", handler)
        self.assertIn(
            "NATIVE_LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION = 4", updater
        )
        self.assertIn("nativeLayeredSceneTerrain(location)", updater)
        self.assertIn(
            "|| !LayeredCompatibilityPointAdapter\n"
            "\t\t\t\t.isSyntheticDeepLevel(location)) {\n"
            "\t\t\treturn null;",
            updater,
        )
        self.assertNotIn(
            "native terrain gate currently requires the accepted synthetic",
            updater,
        )
        self.assertIn("protocolVersion >= 3", generator)
        self.assertIn("context.protocolVersion >= 4", generator)
        self.assertIn("nativeCurrentChunkX", context_struct)
        self.assertIn("nativeChunks", context_struct)
        self.assertIn("NativeLayeredTerrainSnapshot", handler)
        self.assertIn("NativeLayeredTerrainPacketDecoder.decodeV4", handler)
        self.assertIn(
            "Native terrain packet has trailing bytes", native_decoder
        )
        self.assertIn("nativeManifestSha256", context_struct)
        self.assertIn("nativePresentationChunkSize", context_struct)
        self.assertIn("nativeLayeredVoidSector", client_world)
        self.assertIn("applyNativeLayeredFixtureTerrain", client_world)
        self.assertIn(
            "snapshot.createTile(logicalX, logicalZ)", client_world
        )
        self.assertIn(
            "nativeLayeredTerrainSnapshot.scopeIdentity()", client_world
        )
        self.assertIn("public static Sector blankLoaded()", client_sector)
        self.assertIn("acceptLegacyPlayerPosition", handler)
        self.assertIn("resetLayeredSceneIdentityCaches", client)
        self.assertIn("resetForScopeChange", baseline)
        self.assertIn("void reset()", movement_stage)

    def test_plan_preserves_ordinary_wire_compatibility_and_defines_deep_v2(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "Phase 5 Authority Milestone C: Protocol and Client Location Identity",
            plan,
        )
        self.assertIn(
            "existing opcode layouts for", plan
        )
        self.assertIn(
            "synthetic-deep-fixture-v1",
            plan,
        )
        self.assertIn("protocol v2", plan)


if __name__ == "__main__":
    unittest.main()
