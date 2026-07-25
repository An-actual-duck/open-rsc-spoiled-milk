#!/usr/bin/env python3
import hashlib
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CORE_JAR = SERVER / "core.jar"
PACKAGE = ROOT / "tools/layered-maps/fixtures/native-package-v1"
SOURCE = SERVER / "src/com/openrsc/server/io/NativeLayeredWorldPackage.java"
CONFIGURATION = SERVER / "src/com/openrsc/server/ServerConfiguration.java"
REGION_MANAGER = (
    SERVER
    / "src/com/openrsc/server/model/world/region/RegionManager.java"
)
GAME_STATE_UPDATER = SERVER / "src/com/openrsc/server/GameStateUpdater.java"
DEVELOPMENT = (
    SERVER
    / "plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
CLIENT_TILE = ROOT / "Client_Base/src/com/openrsc/client/model/Tile.java"
CLIENT_NATIVE_CHUNK = ROOT / "Client_Base/src/orsc/NativeLayeredTerrainChunk.java"
CLIENT_NATIVE_SNAPSHOT = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java"
)
CLIENT_NATIVE_DECODER = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainPacketDecoder.java"
)


HARNESS = r"""
import com.openrsc.server.io.NativeLayeredTerrainSector;
import com.openrsc.server.io.NativeLayeredTerrainChunk;
import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.io.NativeLayeredPlacementSet;
import com.openrsc.server.io.NativeLayeredNpcPlacement;
import com.openrsc.server.io.NativeLayeredGroundItemPlacement;
import com.openrsc.server.io.NativeLayeredSceneryPlacement;
import com.openrsc.server.io.NativeLayeredBoundaryPlacement;
import com.openrsc.server.io.Sector;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import java.nio.file.Paths;

public final class NativeLayeredServerSourceFixture {
    public static void main(String[] args) throws Exception {
        NativeLayeredWorldPackage world =
            NativeLayeredWorldPackage.load(Paths.get(args[0]));
        check("rsc-remastered.native-loader-lab".equals(world.getPackageId()), "package ID");
        check("0.4.0".equals(world.getPackageVersion()), "package version");
        check(world.getPresentationChunkSize() == 24, "presentation chunk");
        check(world.getWorldSpaceCount() == 1, "world-space count");
        check(world.getLevelCount() == 3, "level count");
        check(world.getTerrainSectorCount() == 3, "sector count");
        check(world.getPlacementSetCount() == 1, "placement-set count");
        check(world.getNpcPlacementCount() == 1, "NPC placement count");
        check(world.getGroundItemPlacementCount() == 1,
            "ground-item placement count");
        check(world.getSceneryPlacementCount() == 1,
            "scenery placement count");
        check(world.getBoundaryPlacementCount() == 1,
            "boundary placement count");
        NativeLayeredPlacementSet placements =
            world.getPlacementSets().get("deep-fixture-entities");
        check(placements != null, "placement set");
        NativeLayeredNpcPlacement npc = placements.getNpcs().get(0);
        check("deep-fixture-man".equals(npc.getPlacementId()),
            "NPC placement ID");
        check(npc.getNpcId() == 11 && npc.getRoamRadius() == 2,
            "NPC placement values");
        check(npc.getStart().getCoordinate().getX() == 452
                && npc.getStart().getCoordinate().getY() == 600
                && npc.getStart().getCoordinate().getLevel() == -2,
            "NPC layered start");
        NativeLayeredGroundItemPlacement item =
            placements.getGroundItems().get(0);
        check("deep-fixture-coins".equals(item.getPlacementId()),
            "item placement ID");
        check(item.getItemId() == 10 && item.getAmount() == 5
                && item.getRespawnSeconds() == 5,
            "item placement values");
        check(item.getLocation().getCoordinate().getX() == 448
                && item.getLocation().getCoordinate().getY() == 600
                && item.getLocation().getCoordinate().getLevel() == -2,
            "item layered location");
        NativeLayeredSceneryPlacement scenery =
            placements.getScenery().get(0);
        check("deep-fixture-table".equals(scenery.getPlacementId()),
            "scenery placement ID");
        check(scenery.getSceneryId() == 3 && scenery.getDirection() == 0,
            "scenery placement values");
        check(scenery.getLocation().getCoordinate().getX() == 446
                && scenery.getLocation().getCoordinate().getY() == 604
                && scenery.getLocation().getCoordinate().getLevel() == -2,
            "scenery layered location");
        NativeLayeredBoundaryPlacement boundary =
            placements.getBoundaries().get(0);
        check("deep-fixture-fence".equals(boundary.getPlacementId()),
            "boundary placement ID");
        check(boundary.getBoundaryId() == 4 && boundary.getDirection() == 0,
            "boundary placement values");
        check(boundary.getLocation().getCoordinate().getX() == 448
                && boundary.getLocation().getCoordinate().getY() == 604
                && boundary.getLocation().getCoordinate().getLevel() == -2,
            "boundary layered location");
        check(world.declaresLevel(WorldSpaceId.GLOBAL, 0), "surface declaration");
        check(world.declaresLevel(WorldSpaceId.GLOBAL, -2), "deep declaration");
        check(world.declaresLevel(WorldSpaceId.GLOBAL, -3), "expanded declaration");
        check(!world.declaresLevel(WorldSpaceId.GLOBAL, -4), "absent declaration");

        NativeLayeredTerrainTile full = tile(world, 439, 600, -2);
        check(full.getElevation() == 255, "RLE elevation");
        check(full.getTexture() == 254, "RLE texture");
        check(full.getOverlay() == 253, "RLE overlay");
        check(full.getRoof() == 252, "RLE roof");
        check(full.getVerticalWall() == 251, "RLE vertical wall");
        check(full.getHorizontalWall() == 250, "RLE horizontal wall");
        check(full.getDiagonalWall() == -1, "RLE unsigned diagonal bits");
        check(tile(world, 440, 600, -2).getTexture() == 1,
            "first RLE terrain band");
        check(tile(world, 448, 600, -2).getElevation() == 4
                && tile(world, 448, 600, -2).getTexture() == 2,
            "second RLE terrain band");
        check(tile(world, 456, 600, -2).getTexture() == 1,
            "third RLE terrain band");
        NativeLayeredTerrainTile before = tile(world, 479, 600, -2);
        NativeLayeredTerrainTile after = tile(world, 480, 600, -2);
        check(before.getElevation() == 0 && before.getTexture() == 0,
            "left adjacent sector tile");
        check(after.getElevation() == 4 && after.getTexture() == 2,
            "right adjacent sector tile");
        check(tile(world, 450, 600, -3).getElevation() == 8,
            "data-declared expanded level tile");
        check(!world.findTile(location(450, 600, 0)).isPresent(),
            "same X/Y surface isolation");

        NativeLayeredTerrainChunk currentChunk = world.findPresentationChunk(
            WorldSpaceId.GLOBAL, -2, 18, 25)
            .orElseThrow(() -> new AssertionError("current presentation chunk"));
        check(currentChunk.getSize() == 24, "presentation chunk size");
        check(currentChunk.getTile(7, 0).getElevation() == 255,
            "presentation chunk x-major tile projection");
        check(currentChunk.getTile(16, 0).getElevation() == 4,
            "presentation chunk non-uniform projection");
        check(currentChunk.copyWireBytes().length == 24 * 24 * 10,
            "presentation chunk wire byte count");
        byte[] wire = currentChunk.copyWireBytes();
        int fullTileOffset = (7 * 24) * 10;
        check((wire[fullTileOffset] & 0xff) == 255,
            "presentation wire elevation");
        check((wire[fullTileOffset + 5] & 0xff) == 250,
            "presentation wire horizontal wall");
        check(wire[fullTileOffset + 6] == -1
                && wire[fullTileOffset + 9] == -1,
            "presentation wire diagonal bits");
        check(!world.findPresentationChunk(
                WorldSpaceId.GLOBAL, -2, 17, 25).isPresent(),
            "absent presentation chunk remains absent");
        check(world.findPresentationChunk(
                WorldSpaceId.GLOBAL, -2, 20, 25)
                .orElseThrow(() -> new AssertionError("adjacent presentation chunk"))
                .getTile(0, 0).getElevation() == 4,
            "presentation chunk crosses storage page through identity");

        WorldMapSectorId leftId =
            new WorldMapSectorId(WorldSpaceId.GLOBAL, -2, 9, 12);
        NativeLayeredTerrainSector left =
            world.findSector(leftId).orElseThrow(() -> new AssertionError("left sector"));
        check("rle-layered-sector-v1".equals(left.getSourceEncoding()),
            "RLE source encoding");
        Sector detached = left.copyToDetachedLegacySector();
        check(detached.getTile(0, 0).getGroundElevation() == 255,
            "detached RLE elevation");
        check(detached.getTile(0, 0).getGroundTexture() == 254,
            "detached RLE texture");
        check(detached.getTile(0, 0).getGroundOverlay() == 253,
            "detached RLE overlay");
        check(detached.getTile(0, 0).getRoofTexture() == 252,
            "detached RLE roof");
        check(detached.getTile(0, 0).getVerticalWall() == 251,
            "detached RLE vertical wall");
        check(detached.getTile(0, 0).getHorizontalWall() == 250,
            "detached RLE horizontal wall");
        check(detached.getTile(0, 0).getDiagonalWalls() == -1,
            "detached RLE diagonal bits");
        check(detached.getTile(47, 47).getGroundTexture() == 0,
            "detached sector last tile");
        check(detached.pack().remaining() == 48 * 48 * 10,
            "detached full-fidelity byte count");
    }

    private static NativeLayeredTerrainTile tile(
            NativeLayeredWorldPackage world, int x, int y, int level) {
        return world.findTile(location(x, y, level))
            .orElseThrow(() -> new AssertionError("missing tile " + x + "," + y + "," + level));
    }

    private static WorldLocation location(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


WIRE_HARNESS = r"""
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.generators.impl.PayloadCustomGenerator;
import com.openrsc.server.net.rsc.struct.outgoing.LayeredSceneContextStruct;
import com.openrsc.server.net.rsc.struct.outgoing.LayeredSceneTerrainChunkStruct;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import orsc.NativeLayeredTerrainPacketDecoder;
import orsc.NativeLayeredTerrainSnapshot;

public final class NativeLayeredChunkWireFixture {
    public static void main(String[] args) {
        LayeredSceneContextStruct context = new LayeredSceneContextStruct();
        context.setOpcode(OpcodeOut.SEND_LAYERED_SCENE_CONTEXT);
        context.protocolVersion = 4;
        context.sequence = 7;
        context.serverTick = 101;
        context.worldSpace = "global";
        context.projectionId = "native-layered-package-v1";
        context.logicalX = 450;
        context.logicalY = 600;
        context.logicalLevel = -2;
        context.legacyX = 450;
        context.legacyY = 600;
        context.nativePackageId = "rsc-remastered.native-loader-lab";
        context.nativePackageVersion = "0.4.0";
        context.nativeManifestSha256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        context.nativePresentationChunkSize = 24;
        context.nativeCurrentChunkX = 18;
        context.nativeCurrentChunkY = 25;
        context.nativeChunkRadius = 1;

        for (int deltaX = -1; deltaX <= 1; deltaX++) {
            for (int deltaY = -1; deltaY <= 1; deltaY++) {
                LayeredSceneTerrainChunkStruct chunk =
                    new LayeredSceneTerrainChunkStruct();
                chunk.chunkX = 18 + deltaX;
                chunk.chunkY = 25 + deltaY;
                chunk.available = deltaX == 0 && deltaY == 0;
                if (chunk.available) {
                    populateAvailable(chunk);
                    int offset = (18 * 24) * 10;
                    chunk.tileBytes[offset] = 4;
                    chunk.tileBytes[offset + 1] = 2;
                    chunk.tileBytes[offset + 2] = 3;
                    chunk.tileBytes[offset + 3] = 5;
                    chunk.tileBytes[offset + 4] = 6;
                    chunk.tileBytes[offset + 5] = 7;
                    chunk.tileBytes[offset + 6] = (byte) 0x89;
                    chunk.tileBytes[offset + 7] = (byte) 0xab;
                    chunk.tileBytes[offset + 8] = (byte) 0xcd;
                    chunk.tileBytes[offset + 9] = (byte) 0xef;
                }
                context.nativeChunks.add(chunk);
            }
        }

        Packet packet = new PayloadCustomGenerator().generate(context, null);
        check(packet != null && packet.getID() == 152, "generated opcode");
        byte[] body = nativeBody(packet);

        NativeLayeredTerrainSnapshot decoded =
            NativeLayeredTerrainPacketDecoder.decodeV4(body, "global", -2);
        check(decoded.getProtocolVersion() == 4, "decoded protocol");
        check(decoded.getCurrentChunkX() == 18
                && decoded.getCurrentChunkY() == 25,
            "decoded current chunk");
        check(decoded.getAvailableChunkCount() == 1,
            "decoded explicit readiness");
        check(decoded.covers("global", -2, 450, 600),
            "decoded receipt coverage");
        com.openrsc.client.model.Tile tile = decoded.createTile(450, 600);
        check((tile.groundElevation & 0xff) == 4, "decoded elevation");
        check((tile.groundTexture & 0xff) == 2, "decoded texture");
        check((tile.groundOverlay & 0xff) == 3, "decoded overlay");
        check((tile.roofTexture & 0xff) == 5, "decoded roof");
        check((tile.verticalWall & 0xff) == 6, "decoded vertical wall");
        check((tile.horizontalWall & 0xff) == 7, "decoded horizontal wall");
        check(tile.diagonalWalls == 0x89abcdef, "decoded diagonal bits");
        expectIllegal(() -> NativeLayeredTerrainPacketDecoder.decodeV4(
            Arrays.copyOf(body, body.length - 1), "global", -2));
        byte[] trailing = Arrays.copyOf(body, body.length + 1);
        expectIllegal(() -> NativeLayeredTerrainPacketDecoder.decodeV4(
            trailing, "global", -2));

        for (LayeredSceneTerrainChunkStruct chunk : context.nativeChunks) {
            if (!chunk.available) {
                chunk.available = true;
                populateAvailable(chunk);
            }
        }
        Packet fullPacket =
            new PayloadCustomGenerator().generate(context, null);
        check(fullPacket.getLength() < 65533,
            "full radius-one packet fits two-byte custom frame");
        NativeLayeredTerrainSnapshot fullDecoded =
            NativeLayeredTerrainPacketDecoder.decodeV4(
                nativeBody(fullPacket), "global", -2);
        check(fullDecoded.getAvailableChunkCount() == 9,
            "full readiness window round trip");
    }

    private static void populateAvailable(
            LayeredSceneTerrainChunkStruct chunk) {
        chunk.sourceSectorX = Math.floorDiv(chunk.chunkX * 24, 48);
        chunk.sourceSectorY = Math.floorDiv(chunk.chunkY * 24, 48);
        chunk.sourceEncoding = "rle-layered-sector-v1";
        chunk.sourcePayloadSha256 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        chunk.tileBytes = new byte[24 * 24 * 10];
    }

    private static byte[] nativeBody(Packet packet) {
        ByteBuf input = packet.getBuffer().duplicate();
        check((input.readByte() & 0xff) == 4, "wire protocol");
        check(input.readInt() == 7, "wire sequence");
        check(input.readInt() == 101, "wire tick");
        check("global".equals(readString(input)), "wire world space");
        check("native-layered-package-v1".equals(readString(input)),
            "wire projection");
        check(input.readInt() == 450 && input.readInt() == 600,
            "wire logical coordinates");
        check(input.readInt() == -2, "wire signed level");
        check(input.readShort() == 450 && input.readShort() == 600,
            "wire compatibility receipt");
        byte[] body = new byte[input.readableBytes()];
        input.readBytes(body);
        return body;
    }

    private static String readString(ByteBuf input) {
        StringBuilder result = new StringBuilder();
        byte value;
        while ((value = input.readByte()) != 10) {
            result.append((char) value);
        }
        return result.toString();
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
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


class LayeredNativeServerSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        subprocess.run(
            [str(ROOT / "scripts/build-server.sh")],
            cwd=ROOT,
            check=True,
            stdout=subprocess.DEVNULL,
        )
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-native-server-source-"
        )
        cls.classes = Path(cls.compile_temp.name)
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-cp",
                str(CORE_JAR),
                "-d",
                str(cls.classes),
                str(CLIENT_TILE),
                str(CLIENT_NATIVE_CHUNK),
                str(CLIENT_NATIVE_SNAPSHOT),
                str(CLIENT_NATIVE_DECODER),
            ],
            cwd=ROOT,
            check=True,
        )
        fixture = cls.classes / "NativeLayeredServerSourceFixture.java"
        fixture.write_text(HARNESS, encoding="utf-8")
        wire_fixture = cls.classes / "NativeLayeredChunkWireFixture.java"
        wire_fixture.write_text(WIRE_HARNESS, encoding="utf-8")
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-cp",
                f"{cls.classes}:{CORE_JAR}",
                "-d",
                str(cls.classes),
                str(fixture),
                str(wire_fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def run_fixture(self, package):
        return subprocess.run(
            [
                "java",
                "-cp",
                f"{self.classes}:{CORE_JAR}",
                "NativeLayeredServerSourceFixture",
                str(package),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_detached_server_source_loads_adjacent_pages_and_expanded_depth(self):
        result = self.run_fixture(PACKAGE)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_server_generator_and_client_decoder_share_chunk_wire_contract(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                f"{self.classes}:{CORE_JAR}",
                "NativeLayeredChunkWireFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_server_loader_has_no_minus_two_or_minus_three_level_enumeration(self):
        with tempfile.TemporaryDirectory(prefix="native-server-depth-") as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            path = package / "manifest.json"
            manifest = json.loads(path.read_text(encoding="utf-8"))
            for level in manifest["levels"]:
                if level["level"] == -3:
                    level["level"] = -37
            for sector in manifest["terrainSectors"]:
                if sector["level"] == -3:
                    sector["level"] = -37
            path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

            probe_source = HARNESS.replace(
                'check(world.declaresLevel(WorldSpaceId.GLOBAL, -3), "expanded declaration");',
                'check(world.declaresLevel(WorldSpaceId.GLOBAL, -37), "expanded declaration");',
            ).replace(
                "tile(world, 450, 600, -3)",
                "tile(world, 450, 600, -37)",
            )
            probe = self.classes / "NativeLayeredServerSourceFixture.java"
            probe.write_text(probe_source, encoding="utf-8")
            subprocess.run(
                [
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-cp",
                    str(CORE_JAR),
                    "-d",
                    str(self.classes),
                    str(probe),
                ],
                cwd=ROOT,
                check=True,
            )

            result = self.run_fixture(package)
            self.assertEqual(0, result.returncode, result.stderr)

    def test_server_loader_refuses_underfilled_rle_payload_after_hash_check(self):
        with tempfile.TemporaryDirectory(prefix="native-server-rle-refusal-") as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            relative_path = "terrain/deep-l2-x9-y12.json"
            payload_path = package / relative_path
            payload = json.loads(payload_path.read_text(encoding="utf-8"))
            payload["runs"][-1]["count"] -= 1
            payload_path.write_text(
                json.dumps(payload, indent=2) + "\n", encoding="utf-8"
            )
            manifest_path = package / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            for sector in manifest["terrainSectors"]:
                if sector["path"] == relative_path:
                    sector["sha256"] = hashlib.sha256(
                        payload_path.read_bytes()
                    ).hexdigest()
            manifest_path.write_text(
                json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
            )

            result = self.run_fixture(package)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("exactly 2304", result.stderr)

    def test_server_loader_refuses_invalid_placement_after_hash_check(self):
        with tempfile.TemporaryDirectory(
            prefix="native-server-placement-refusal-"
        ) as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            relative_path = "placements/deep-l2-entities.json"
            payload_path = package / relative_path
            payload = json.loads(payload_path.read_text(encoding="utf-8"))
            payload["groundItems"][0]["respawnSeconds"] = 0
            payload_path.write_text(
                json.dumps(payload, indent=2) + "\n", encoding="utf-8"
            )
            manifest_path = package / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            for placement_set in manifest["placementSets"]:
                if placement_set["path"] == relative_path:
                    placement_set["sha256"] = hashlib.sha256(
                        payload_path.read_bytes()
                    ).hexdigest()
            manifest_path.write_text(
                json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
            )

            result = self.run_fixture(package)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("must be positive", result.stderr)

    def test_source_is_detached_from_runtime_world_and_region_authority(self):
        source = SOURCE.read_text(encoding="utf-8")
        forbidden = (
            "com.openrsc.server.model.world.World",
            "RegionManager",
            "TileValue",
            "register",
            "unregister",
        )
        for token in forbidden:
            with self.subTest(token=token):
                self.assertNotIn(token, source)

    def test_private_runtime_gate_is_explicit_fail_closed_and_reversible(self):
        configuration = CONFIGURATION.read_text(encoding="utf-8")
        region_manager = REGION_MANAGER.read_text(encoding="utf-8")
        game_state_updater = GAME_STATE_UPDATER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        self.assertIn("WANT_LAYERED_NATIVE_TERRAIN_PACKAGE", configuration)
        self.assertIn(
            "OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE", configuration
        )
        self.assertIn(
            '"want_layered_native_terrain_package",\n\t\t\tfalse',
            configuration,
        )
        self.assertIn(
            "LAYERED_NATIVE_TERRAIN_PACKAGE_PATH", configuration
        )
        self.assertIn(
            "NativeLayeredWorldPackage.load", region_manager
        )
        self.assertIn(
            "validateNativeDeepFixturePackage(loaded)", region_manager
        )
        self.assertIn(
            "NativeLayeredTerrainTile source = nativeLayeredWorldPackage",
            region_manager,
        )
        self.assertIn(
            "return nativeDeepFixtureTile(location)", region_manager
        )
        self.assertIn(
            "return syntheticDeepFixtureTile()", region_manager
        )
        self.assertIn(
            "Native layered deep ", development
        )
        self.assertIn(
            "NativeLayeredWorldPackage.RUNTIME_PROJECTION_ID", development
        )
        self.assertIn(
            "nativePackage.getPresentationChunkSize()", development
        )
        self.assertIn(
            "The first native layered streaming route requires 24-tile chunks",
            region_manager,
        )
        self.assertIn("findPresentationChunk(", game_state_updater)
        self.assertIn(
            '"; ready=" + nativeReadyChunks + "/9"', development
        )
        self.assertIn(
            '"Deep fixture logical="', development
        )


if __name__ == "__main__":
    unittest.main()
