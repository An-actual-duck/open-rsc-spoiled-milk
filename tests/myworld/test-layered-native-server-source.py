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
CATALOG = (
    SERVER / "src/com/openrsc/server/io/NativeLayeredWorldPackageCatalog.java"
)
RUNTIME_PROFILE = (
    SERVER / "src/com/openrsc/server/io/NativeLayeredWorldRuntimeProfile.java"
)
CONFIGURATION = SERVER / "src/com/openrsc/server/ServerConfiguration.java"
REGION_MANAGER = (
    SERVER
    / "src/com/openrsc/server/model/world/region/RegionManager.java"
)
WORLD_POPULATOR = (
    SERVER / "src/com/openrsc/server/database/WorldPopulator.java"
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
CLIENT_NATIVE_RESIDENCY = (
    ROOT / "Client_Base/src/orsc/NativeLayeredTerrainResidentCache.java"
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
        check("0.7.0".equals(world.getPackageVersion()), "package version");
        check(world.getPresentationChunkSize() == 24, "presentation chunk");
        check(world.getWorldSpaceCount() == 1, "world-space count");
        check(world.getLevelCount() == 3, "level count");
        check(world.getTerrainSectorCount() == 3, "sector count");
        check(world.getPlacementSetCount() == 1, "placement-set count");
        check(world.getNpcPlacementCount() == 1, "NPC placement count");
        check(world.getGroundItemPlacementCount() == 1,
            "ground-item placement count");
        check(world.getSceneryPlacementCount() == 2,
            "scenery placement count");
        check(world.getBoundaryPlacementCount() == 2,
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
        NativeLayeredSceneryPlacement tree = placements.getScenery().get(1);
        check("deep-fixture-tree".equals(tree.getPlacementId()),
            "tree placement ID");
        check(tree.getSceneryId() == 1 && tree.getDirection() == 0,
            "tree placement values");
        check(tree.getLocation().getCoordinate().getX() == 456
                && tree.getLocation().getCoordinate().getY() == 604
                && tree.getLocation().getCoordinate().getLevel() == -2,
            "tree layered location");
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
        NativeLayeredBoundaryPlacement door =
            placements.getBoundaries().get(1);
        check("deep-fixture-door".equals(door.getPlacementId()),
            "door placement ID");
        check(door.getBoundaryId() == 2 && door.getDirection() == 0,
            "door placement values");
        check(door.getLocation().getCoordinate().getX() == 452
                && door.getLocation().getCoordinate().getY() == 604
                && door.getLocation().getCoordinate().getLevel() == -2,
            "door layered location");
        check(world.declaresLevel(WorldSpaceId.GLOBAL, 0), "surface declaration");
        check(world.declaresLevel(WorldSpaceId.GLOBAL, -2), "deep declaration");
        check(world.declaresLevel(WorldSpaceId.GLOBAL, -3), "expanded declaration");
        check(!world.declaresLevel(WorldSpaceId.GLOBAL, -4), "absent declaration");

        NativeLayeredTerrainTile full = tile(world, 439, 600, -2);
        check(full.getElevation() == 8, "RLE elevation");
        check(full.getTexture() == 3, "RLE texture");
        check(full.getOverlay() == 2, "RLE overlay");
        check(full.getRoof() == 1, "RLE roof");
        check(full.getVerticalWall() == 1, "RLE vertical wall");
        check(full.getHorizontalWall() == 1, "RLE horizontal wall");
        check(full.getDiagonalWall() == 1, "RLE diagonal wall");
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
        check(currentChunk.getTile(7, 0).getElevation() == 8,
            "presentation chunk x-major tile projection");
        check(currentChunk.getTile(16, 0).getElevation() == 4,
            "presentation chunk non-uniform projection");
        check(currentChunk.copyWireBytes().length == 24 * 24 * 10,
            "presentation chunk wire byte count");
        byte[] wire = currentChunk.copyWireBytes();
        int fullTileOffset = (7 * 24) * 10;
        check((wire[fullTileOffset] & 0xff) == 8,
            "presentation wire elevation");
        check((wire[fullTileOffset + 5] & 0xff) == 1,
            "presentation wire horizontal wall");
        check(wire[fullTileOffset + 6] == 0
                && wire[fullTileOffset + 9] == 1,
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
        check(detached.getTile(0, 0).getGroundElevation() == 8,
            "detached RLE elevation");
        check(detached.getTile(0, 0).getGroundTexture() == 3,
            "detached RLE texture");
        check(detached.getTile(0, 0).getGroundOverlay() == 2,
            "detached RLE overlay");
        check(detached.getTile(0, 0).getRoofTexture() == 1,
            "detached RLE roof");
        check(detached.getTile(0, 0).getVerticalWall() == 1,
            "detached RLE vertical wall");
        check(detached.getTile(0, 0).getHorizontalWall() == 1,
            "detached RLE horizontal wall");
        check(detached.getTile(0, 0).getDiagonalWalls() == 1,
            "detached RLE diagonal bits");
        check(detached.getTile(47, 47).getGroundTexture() == 0,
            "detached sector last tile");
        check(detached.pack().remaining() == 48 * 48 * 10,
            "detached full-fidelity byte count");

        WorldMapSectorId voidId =
            new WorldMapSectorId(WorldSpaceId.GLOBAL, -4, 20, 20);
        NativeLayeredTerrainSector voidSector =
            NativeLayeredTerrainSector.worldBuilderVoid(voidId);
        NativeLayeredTerrainTile voidTile = voidSector.getTile(47, 47);
        check(voidSector.getIdentity().equals(voidId),
            "Builder void identity");
        check(voidTile.getElevation() == 0
                && voidTile.getTexture() == 1
                && voidTile.getOverlay() == 8
                && voidTile.getRoof() == 0
                && voidTile.getVerticalWall() == 0
                && voidTile.getHorizontalWall() == 0
                && voidTile.getDiagonalWall() == 0,
            "Builder void tile");
        check("raw-layered-sector-v1".equals(
                voidSector.getSourceEncoding()),
            "Builder void encoding");
        check(voidSector.getSourceSha256().matches("[0-9a-f]{64}"),
            "Builder void digest");
        check(voidSector.copyWireBytes().length == 48 * 48 * 10,
            "Builder void wire byte count");
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

TERRAIN_ONLY_HARNESS = r"""
import com.openrsc.server.io.NativeLayeredWorldPackage;
import java.nio.file.Paths;

public final class NativeLayeredTerrainOnlyFixture {
    public static void main(String[] args) throws Exception {
        NativeLayeredWorldPackage world =
            NativeLayeredWorldPackage.load(Paths.get(args[0]));
        check(world.getTerrainSectorCount() == 3, "terrain count");
        check(world.getPlacementSetCount() == 0, "placement-set count");
        check(world.getNpcPlacementCount() == 0, "NPC placement count");
        check(world.getGroundItemPlacementCount() == 0,
            "ground-item placement count");
        check(world.getSceneryPlacementCount() == 0,
            "scenery placement count");
        check(world.getBoundaryPlacementCount() == 0,
            "boundary placement count");
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""

PRESERVATION_REVIEW_HARNESS = r"""
import com.openrsc.server.io.NativeLayeredNpcPlacement;
import com.openrsc.server.io.NativeLayeredPlacementSet;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import java.nio.file.Paths;

public final class NativeLayeredPreservationReviewFixture {
    public static void main(String[] args) throws Exception {
        NativeLayeredWorldPackage world =
            NativeLayeredWorldPackage.load(Paths.get(args[0]));
        check(
            "rsc-remastered.preservation-r64-parity-review".equals(
                world.getPackageId()),
            "package ID");
        check("0.4.0".equals(world.getPackageVersion()), "package version");
        check(world.getTerrainSectorCount() == 1764, "terrain count");
        check(world.getLevelCount() == 4, "level count");
        check(world.getPlacementSetCount() == 4, "placement-set count");
        check(world.getNpcPlacementCount() == 3610, "NPC count");
        check(world.getGroundItemPlacementCount() == 1010, "item count");
        check(world.getSceneryPlacementCount() == 26765, "scenery count");
        check(world.getBoundaryPlacementCount() == 966, "boundary count");
        NativeLayeredNpcPlacement first = null;
        for (NativeLayeredPlacementSet set : world.getPlacementSets().values()) {
            for (NativeLayeredNpcPlacement npc : set.getNpcs()) {
                if ("preservation-r64.npc.000000".equals(
                        npc.getPlacementId())) {
                    first = npc;
                }
            }
        }
        check(first != null, "first NPC");
        check(first.getNpcId() == 401, "first NPC definition");
        check(first.getStart().getCoordinate().getX() == 413
                && first.getStart().getCoordinate().getY() == 11
                && first.getStart().getCoordinate().getLevel() == 0,
            "first NPC start");
        check(first.getMinX() == 411 && first.getMinY() == 9
                && first.getMaxX() == 413 && first.getMaxY() == 12,
            "first NPC exact bounds");
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""

SPOILED_MILK_REVIEW_HARNESS = (
    PRESERVATION_REVIEW_HARNESS
    .replace(
        "NativeLayeredPreservationReviewFixture",
        "NativeLayeredSpoiledMilkReviewFixture",
    )
    .replace(
        "rsc-remastered.preservation-r64-parity-review",
        "rsc-remastered.spoiled-milk-layered-world",
    )
    .replace(
        'check("0.4.0".equals(world.getPackageVersion()), "package version");',
        'check("0.5.0".equals(world.getPackageVersion()), "package version");',
    )
    .replace("== 1764", "== 1782")
    .replace("world.getLevelCount() == 4", "world.getLevelCount() == 6")
    .replace("world.getPlacementSetCount() == 4",
             "world.getPlacementSetCount() == 6")
    .replace("== 3610", "== 3775")
    .replace("== 1010", "== 879")
    .replace("== 26765", "== 27887")
    .replace("== 966", "== 971")
    .replace(
        '"preservation-r64.npc.000000"',
        '"spoiled-milk.npc.npclocs-json.000000"',
    )
)

RUNTIME_PROFILE_HARNESS = r"""
import com.openrsc.server.io.NativeLayeredWorldPackageCatalog;
import com.openrsc.server.io.NativeLayeredWorldRuntimeProfile;

public final class NativeLayeredRuntimeProfileFixture {
    public static void main(String[] args) throws Exception {
        NativeLayeredWorldRuntimeProfile profile =
            NativeLayeredWorldRuntimeProfile.fromConfiguration(args[0]);
        NativeLayeredWorldPackageCatalog catalog =
            NativeLayeredWorldPackageCatalog.loadConfigured(args[1]);
        boolean expectAcceptance = Boolean.parseBoolean(args[2]);
        try {
            profile.validate(catalog);
            check(expectAcceptance, "unexpected profile acceptance");
            check(
                profile.replacesLegacyBasePopulation()
                    == Boolean.parseBoolean(args[3]),
                "replacement ownership");
        } catch (IllegalStateException expected) {
            check(!expectAcceptance, "unexpected profile refusal");
        }
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
import java.util.zip.Deflater;
import orsc.NativeLayeredTerrainPacketDecoder;
import orsc.NativeLayeredTerrainResidentCache;
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
        context.nativePackageVersion = "0.6.0";
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
        check(packet != null && packet.getID() == 157, "generated opcode");
        byte[] body = nativeBody(packet, 4);

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
        check(!tile.editorPaintedOverlay,
            "decoded archive tile is not editor-painted");
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
                nativeBody(fullPacket, 4), "global", -2);
        check(fullDecoded.getAvailableChunkCount() == 9,
            "full readiness window round trip");
        testV5StorageSectorWindow();
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

    private static void testV5StorageSectorWindow() {
        LayeredSceneContextStruct context = new LayeredSceneContextStruct();
        context.setOpcode(OpcodeOut.SEND_LAYERED_SCENE_CONTEXT);
        context.protocolVersion = 5;
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
        context.nativePackageVersion = "0.6.0";
        context.nativeManifestSha256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        context.nativePresentationChunkSize = 48;
        context.nativeCurrentChunkX = 9;
        context.nativeCurrentChunkY = 12;
        context.nativeChunkRadius = 1;
        for (int deltaX = -1; deltaX <= 1; deltaX++) {
            for (int deltaY = -1; deltaY <= 1; deltaY++) {
                LayeredSceneTerrainChunkStruct chunk =
                    new LayeredSceneTerrainChunkStruct();
                chunk.chunkX = 9 + deltaX;
                chunk.chunkY = 12 + deltaY;
                chunk.available = true;
                chunk.sourceSectorX = chunk.chunkX;
                chunk.sourceSectorY = chunk.chunkY;
                chunk.sourceEncoding = "raw-layered-sector-v1";
                chunk.sourcePayloadSha256 =
                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
                byte[] raw = new byte[48 * 48 * 10];
                if (deltaX == 0 && deltaY == 0) {
                    int offset = (18 * 48 + 24) * 10;
                    raw[offset] = 4;
                    raw[offset + 1] = 2;
                    raw[offset + 2] = 3;
                    raw[offset + 3] = 5;
                    raw[offset + 4] = 6;
                    raw[offset + 5] = 7;
                    raw[offset + 6] = (byte) 0x89;
                    raw[offset + 7] = (byte) 0xab;
                    raw[offset + 8] = (byte) 0xcd;
                    raw[offset + 9] = (byte) 0xef;
                }
                chunk.tileBytes = compress(raw);
                context.nativeChunks.add(chunk);
            }
        }
        Packet packet = new PayloadCustomGenerator().generate(context, null);
        check(packet.getLength() < 65533,
            "compressed 144-tile window fits custom frame");
        NativeLayeredTerrainSnapshot decoded =
            NativeLayeredTerrainPacketDecoder.decodeV5(
                nativeBody(packet, 5), "global", -2);
        check(decoded.getProtocolVersion() == 5, "v5 decoded protocol");
        check(decoded.getPresentationChunkSize() == 48,
            "v5 storage-sector chunks");
        check(decoded.getAvailableChunkCount() == 9,
            "v5 full client window readiness");
        check(decoded.covers("global", -2, 384, 528)
                && decoded.covers("global", -2, 527, 671),
            "v5 covers complete 144-tile client window");
        com.openrsc.client.model.Tile tile = decoded.createTile(450, 600);
        check((tile.groundElevation & 0xff) == 4, "v5 elevation");
        check(tile.diagonalWalls == 0x89abcdef, "v5 diagonal bits");
        check(!tile.editorPaintedOverlay,
            "v5 archive tile is not editor-painted");
        byte[] truncated = Arrays.copyOf(
            nativeBody(packet, 5), nativeBody(packet, 5).length - 1);
        expectIllegal(() -> NativeLayeredTerrainPacketDecoder.decodeV5(
            truncated, "global", -2));
        testV6ResidentReferences(context);
    }

    private static void testV6ResidentReferences(
            LayeredSceneContextStruct context) {
        context.protocolVersion = 6;
        for (LayeredSceneTerrainChunkStruct chunk : context.nativeChunks) {
            chunk.payloadPresent = true;
        }
        NativeLayeredTerrainResidentCache cache =
            new NativeLayeredTerrainResidentCache();
        Packet firstPacket =
            new PayloadCustomGenerator().generate(context, null);
        NativeLayeredTerrainSnapshot first =
            NativeLayeredTerrainPacketDecoder.decodeV6(
                nativeBody(firstPacket, 6), "global", -2, cache);
        check(first.getProtocolVersion() == 6, "v6 decoded protocol");
        check(first.getAvailableChunkCount() == 9, "v6 payload readiness");
        check(cache.size() == 9, "v6 initial residency");

        for (LayeredSceneTerrainChunkStruct chunk : context.nativeChunks) {
            chunk.payloadPresent = false;
        }
        Packet referencePacket =
            new PayloadCustomGenerator().generate(context, null);
        NativeLayeredTerrainSnapshot referenced =
            NativeLayeredTerrainPacketDecoder.decodeV6(
                nativeBody(referencePacket, 6), "global", -2, cache);
        check(referenced.getAvailableChunkCount() == 9,
            "v6 reference readiness");
        check(referencePacket.getLength() < firstPacket.getLength(),
            "v6 references reduce packet length");
        expectState(() -> NativeLayeredTerrainPacketDecoder.decodeV6(
            nativeBody(referencePacket, 6),
            "global",
            -2,
            new NativeLayeredTerrainResidentCache()));
    }

    private static byte[] compress(byte[] source) {
        Deflater compressor = new Deflater(Deflater.BEST_SPEED);
        try {
            compressor.setInput(source);
            compressor.finish();
            byte[] output = new byte[source.length + 128];
            int length = compressor.deflate(output);
            check(compressor.finished(), "fixture compression");
            return Arrays.copyOf(output, length);
        } finally {
            compressor.end();
        }
    }

    private static byte[] nativeBody(Packet packet, int protocolVersion) {
        ByteBuf input = packet.getBuffer().duplicate();
        check((input.readByte() & 0xff) == protocolVersion, "wire protocol");
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
                str(CLIENT_NATIVE_RESIDENCY),
                str(CLIENT_NATIVE_DECODER),
            ],
            cwd=ROOT,
            check=True,
        )
        fixture = cls.classes / "NativeLayeredServerSourceFixture.java"
        fixture.write_text(HARNESS, encoding="utf-8")
        terrain_only_fixture = (
            cls.classes / "NativeLayeredTerrainOnlyFixture.java"
        )
        terrain_only_fixture.write_text(
            TERRAIN_ONLY_HARNESS, encoding="utf-8"
        )
        preservation_fixture = (
            cls.classes / "NativeLayeredPreservationReviewFixture.java"
        )
        preservation_fixture.write_text(
            PRESERVATION_REVIEW_HARNESS, encoding="utf-8"
        )
        spoiled_milk_fixture = (
            cls.classes / "NativeLayeredSpoiledMilkReviewFixture.java"
        )
        spoiled_milk_fixture.write_text(
            SPOILED_MILK_REVIEW_HARNESS, encoding="utf-8"
        )
        runtime_profile_fixture = (
            cls.classes / "NativeLayeredRuntimeProfileFixture.java"
        )
        runtime_profile_fixture.write_text(
            RUNTIME_PROFILE_HARNESS, encoding="utf-8"
        )
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
                str(terrain_only_fixture),
                str(preservation_fixture),
                str(spoiled_milk_fixture),
                str(runtime_profile_fixture),
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

    def test_detached_server_source_decodes_raw_native_sector(self):
        with tempfile.TemporaryDirectory(prefix="native-server-raw-") as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            json_relative = "terrain/expansion-l3-x9-y12.json"
            raw_relative = "terrain/expansion-l3-x9-y12.raw"
            source = json.loads(
                (package / json_relative).read_text(encoding="utf-8")
            )
            tile = source["tile"]
            raw_tile = bytes(
                [
                    tile["elevation"],
                    tile["texture"],
                    tile["overlay"],
                    tile["roof"],
                    tile["verticalWall"],
                    tile["horizontalWall"],
                ]
            ) + tile["diagonalWall"].to_bytes(4, byteorder="big")
            raw_path = package / raw_relative
            raw_path.write_bytes(raw_tile * (48 * 48))
            manifest_path = package / "manifest.json"
            manifest = json.loads(
                manifest_path.read_text(encoding="utf-8")
            )
            for sector in manifest["terrainSectors"]:
                if sector["path"] == json_relative:
                    sector["encoding"] = "raw-layered-sector-v1"
                    sector["path"] = raw_relative
                    sector["sha256"] = hashlib.sha256(
                        raw_path.read_bytes()
                    ).hexdigest()
            manifest_path.write_text(
                json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
            )

            result = self.run_fixture(package)

            self.assertEqual(0, result.returncode, result.stderr)

    def test_detached_server_source_decodes_v3_exact_npc_roam_bounds(self):
        with tempfile.TemporaryDirectory(
            prefix="native-server-placement-v3-"
        ) as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            relative_path = "placements/deep-l2-entities.json"
            payload_path = package / relative_path
            payload = json.loads(payload_path.read_text(encoding="utf-8"))
            payload["schemaVersion"] = 3
            payload["encoding"] = "layered-world-placements-v3"
            npc = payload["npcs"][0]
            npc.pop("roamRadius")
            npc["roamBounds"] = {
                "minimum": {"x": 450, "y": 599},
                "maximum": {"x": 455, "y": 603},
            }
            payload_path.write_text(
                json.dumps(payload, indent=2) + "\n", encoding="utf-8"
            )
            manifest_path = package / "manifest.json"
            manifest = json.loads(
                manifest_path.read_text(encoding="utf-8")
            )
            manifest["placementSets"][0]["encoding"] = (
                "layered-world-placements-v3"
            )
            manifest["placementSets"][0]["sha256"] = hashlib.sha256(
                payload_path.read_bytes()
            ).hexdigest()
            manifest_path.write_text(
                json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
            )

            probe_source = HARNESS.replace(
                "NativeLayeredServerSourceFixture",
                "NativeLayeredServerV3Fixture",
            ).replace(
                "npc.getNpcId() == 11 && npc.getRoamRadius() == 2",
                "npc.getNpcId() == 11"
                " && npc.getRoamRadius() == -1"
                " && npc.getMinX() == 450 && npc.getMinY() == 599"
                " && npc.getMaxX() == 455 && npc.getMaxY() == 603",
            )
            probe = self.classes / "NativeLayeredServerV3Fixture.java"
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

            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{self.classes}:{CORE_JAR}",
                    "NativeLayeredServerV3Fixture",
                    str(package),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )

            self.assertEqual(0, result.returncode, result.stderr)

    def test_server_loader_accepts_terrain_only_review_package(self):
        with tempfile.TemporaryDirectory(
            prefix="native-server-terrain-only-"
        ) as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            manifest_path = package / "manifest.json"
            manifest = json.loads(
                manifest_path.read_text(encoding="utf-8")
            )
            manifest["packageId"] = "rsc-remastered.terrain-only-review"
            manifest["placementSets"] = []
            manifest_path.write_text(
                json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
            )

            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{self.classes}:{CORE_JAR}",
                    "NativeLayeredTerrainOnlyFixture",
                    str(package),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )

            self.assertEqual(0, result.returncode, result.stderr)

    def test_server_loader_accepts_generated_preservation_review_package(self):
        subprocess.run(
            [str(ROOT / "tools/layered-maps/layered-maps.sh"), "--help"],
            cwd=ROOT,
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        with tempfile.TemporaryDirectory(
            prefix="native-server-preservation-review-"
        ) as temp:
            workspace = Path(temp) / "workspace"
            generated = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(ROOT / "tools/layered-maps/build/classes"),
                    "com.openrsc.layeredmaps.LayeredMapsCli",
                    "preservation-package",
                    "--root",
                    str(ROOT),
                    "--workspace",
                    str(workspace),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            if generated.returncode == 3:
                self.assertIn(
                    "Preservation sources no longer reproduce the accepted "
                    "frozen baseline",
                    generated.stderr,
                )
                self.assertFalse((workspace / "package").exists())
                return
            self.assertEqual(0, generated.returncode, generated.stderr)

            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{self.classes}:{CORE_JAR}",
                    "NativeLayeredPreservationReviewFixture",
                    str(workspace / "package"),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )

            self.assertEqual(0, result.returncode, result.stderr)

            accepted = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{self.classes}:{CORE_JAR}",
                    "NativeLayeredRuntimeProfileFixture",
                    "preservation-r64-replacement",
                    str(workspace / "package"),
                    "true",
                    "true",
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, accepted.returncode, accepted.stderr)

            wrong_profile = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{self.classes}:{CORE_JAR}",
                    "NativeLayeredRuntimeProfileFixture",
                    "fixture-additive",
                    str(workspace / "package"),
                    "false",
                    "false",
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(
                0, wrong_profile.returncode, wrong_profile.stderr
            )

    def test_server_accepts_complete_spoiled_milk_replacement_package(self):
        subprocess.run(
            [str(ROOT / "tools/layered-maps/layered-maps.sh"), "--help"],
            cwd=ROOT,
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        with tempfile.TemporaryDirectory(
            prefix="native-server-spoiled-milk-"
        ) as temp:
            workspace = Path(temp) / "workspace"
            generated = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(ROOT / "tools/layered-maps/build/classes"),
                    "com.openrsc.layeredmaps.LayeredMapsCli",
                    "spoiled-milk-package",
                    "--root",
                    str(ROOT),
                    "--workspace",
                    str(workspace),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, generated.returncode, generated.stderr)

            loaded = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{self.classes}:{CORE_JAR}",
                    "NativeLayeredSpoiledMilkReviewFixture",
                    str(workspace / "package"),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, loaded.returncode, loaded.stderr)

            accepted = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{self.classes}:{CORE_JAR}",
                    "NativeLayeredRuntimeProfileFixture",
                    "spoiled-milk-replacement",
                    str(workspace / "package"),
                    "true",
                    "true",
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, accepted.returncode, accepted.stderr)

            preservation_refused = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{self.classes}:{CORE_JAR}",
                    "NativeLayeredRuntimeProfileFixture",
                    "preservation-r64-replacement",
                    str(workspace / "package"),
                    "false",
                    "true",
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(
                0,
                preservation_refused.returncode,
                preservation_refused.stderr,
            )

            package = workspace / "package"
            manifest_path = package / "manifest.json"
            manifest = json.loads(
                manifest_path.read_text(encoding="utf-8")
            )
            manifest["levels"].append(
                {
                    "level": -3,
                    "name": "Underground level 3",
                    "role": "underground-level-3",
                    "worldSpace": "global",
                }
            )
            starter = bytearray(48 * 48 * 10)
            for offset in range(0, len(starter), 10):
                starter[offset + 1] = 1
            for sector_x in range(1, 4):
                for sector_y in range(12, 15):
                    relative = (
                        "terrain/global/lm3/"
                        f"xp{sector_x}-yp{sector_y}.raw"
                    )
                    payload = package / relative
                    payload.parent.mkdir(parents=True, exist_ok=True)
                    payload.write_bytes(starter)
                    manifest["terrainSectors"].append(
                        {
                            "encoding": "raw-layered-sector-v1",
                            "level": -3,
                            "path": relative,
                            "sectorX": sector_x,
                            "sectorY": sector_y,
                            "sha256": hashlib.sha256(starter).hexdigest(),
                            "worldSpace": "global",
                        }
                    )
            placement_relative = "placements/global/lm3.json"
            placement_path = package / placement_relative
            placement = {
                "boundaries": [],
                "encoding": "layered-world-placements-v3",
                "groundItems": [
                    {
                        "amount": 25,
                        "itemId": 10,
                        "placementId": (
                            "spoiled-milk.builder.ground-item."
                            "lm3.xp140.yp640"
                        ),
                        "position": {"x": 140, "y": 640},
                        "respawnSeconds": 5,
                    }
                ],
                "level": -3,
                "npcs": [],
                "scenery": [],
                "schemaVersion": 3,
                "worldSpace": "global",
            }
            placement_path.write_text(
                json.dumps(placement, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
            manifest["placementSets"].append(
                {
                    "encoding": "layered-world-placements-v3",
                    "id": "spoiled-milk-builder-lm3",
                    "level": -3,
                    "path": placement_relative,
                    "sha256": hashlib.sha256(
                        placement_path.read_bytes()
                    ).hexdigest(),
                    "worldSpace": "global",
                }
            )
            manifest_path.write_text(
                json.dumps(manifest, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )

            builder_draft = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{self.classes}:{CORE_JAR}",
                    "NativeLayeredRuntimeProfileFixture",
                    "spoiled-milk-builder-draft",
                    str(package),
                    "true",
                    "true",
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(
                0, builder_draft.returncode, builder_draft.stderr
            )

            pinned_release_refused = subprocess.run(
                [
                    "java",
                    "-cp",
                    f"{self.classes}:{CORE_JAR}",
                    "NativeLayeredRuntimeProfileFixture",
                    "spoiled-milk-replacement",
                    str(package),
                    "false",
                    "true",
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(
                0,
                pinned_release_refused.returncode,
                pinned_release_refused.stderr,
            )

    def test_fixture_and_replacement_runtime_profiles_do_not_cross_accept(self):
        accepted = subprocess.run(
            [
                "java",
                "-cp",
                f"{self.classes}:{CORE_JAR}",
                "NativeLayeredRuntimeProfileFixture",
                "fixture-additive",
                str(PACKAGE),
                "true",
                "false",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, accepted.returncode, accepted.stderr)

        refused = subprocess.run(
            [
                "java",
                "-cp",
                f"{self.classes}:{CORE_JAR}",
                "NativeLayeredRuntimeProfileFixture",
                "preservation-r64-replacement",
                str(PACKAGE),
                "false",
                "true",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, refused.returncode, refused.stderr)

        spoiled_milk_refused = subprocess.run(
            [
                "java",
                "-cp",
                f"{self.classes}:{CORE_JAR}",
                "NativeLayeredRuntimeProfileFixture",
                "spoiled-milk-replacement",
                str(PACKAGE),
                "false",
                "true",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(
            0, spoiled_milk_refused.returncode, spoiled_milk_refused.stderr
        )

    def test_server_loader_refuses_inverted_v3_npc_roam_bounds(self):
        with tempfile.TemporaryDirectory(
            prefix="native-server-placement-v3-refusal-"
        ) as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            relative_path = "placements/deep-l2-entities.json"
            payload_path = package / relative_path
            payload = json.loads(payload_path.read_text(encoding="utf-8"))
            payload["schemaVersion"] = 3
            payload["encoding"] = "layered-world-placements-v3"
            npc = payload["npcs"][0]
            npc.pop("roamRadius")
            npc["roamBounds"] = {
                "minimum": {"x": 456, "y": 599},
                "maximum": {"x": 455, "y": 603},
            }
            payload_path.write_text(
                json.dumps(payload, indent=2) + "\n", encoding="utf-8"
            )
            manifest_path = package / "manifest.json"
            manifest = json.loads(
                manifest_path.read_text(encoding="utf-8")
            )
            manifest["placementSets"][0]["encoding"] = (
                "layered-world-placements-v3"
            )
            manifest["placementSets"][0]["sha256"] = hashlib.sha256(
                payload_path.read_bytes()
            ).hexdigest()
            manifest_path.write_text(
                json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
            )

            result = self.run_fixture(package)

            self.assertNotEqual(0, result.returncode)
            self.assertIn(
                "minimum must not exceed maximum",
                result.stderr,
            )

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
        catalog = CATALOG.read_text(encoding="utf-8")
        runtime_profile = RUNTIME_PROFILE.read_text(encoding="utf-8")
        region_manager = REGION_MANAGER.read_text(encoding="utf-8")
        world_populator = WORLD_POPULATOR.read_text(encoding="utf-8")
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
            "OPENRSC_LAYERED_NATIVE_WORLD_RUNTIME_PROFILE", configuration
        )
        self.assertIn(
            '"layered_native_world_runtime_profile",\n'
            '\t\t\t"fixture-additive"',
            configuration,
        )
        self.assertIn(
            "NativeLayeredWorldPackageCatalog.loadConfigured", region_manager
        )
        self.assertIn(
            "profile.validate(loaded)",
            region_manager,
        )
        self.assertIn(
            "PRESERVATION_R64_REPLACEMENT", runtime_profile
        )
        self.assertIn(
            "SPOILED_MILK_REPLACEMENT", runtime_profile
        )
        self.assertIn(
            "PRESERVATION_MANIFEST_SHA256", runtime_profile
        )
        self.assertIn(
            "SPOILED_MILK_MANIFEST_SHA256", runtime_profile
        )
        self.assertIn(
            "validatePreservationDefinitionIds(loaded)", runtime_profile
        )
        self.assertIn("VANILLA_MAX_SCENERY_ID", runtime_profile)
        self.assertIn("VANILLA_MAX_NPC_ID", runtime_profile)
        self.assertIn("VANILLA_MAX_ITEM_ID", runtime_profile)
        self.assertIn("VANILLA_MAX_BOUNDARY_ID", runtime_profile)
        self.assertIn(
            "replacesLegacyBasePopulation()", world_populator
        )
        self.assertIn(
            "Suppressing legacy base placement population", world_populator
        )
        self.assertIn(
            "NativeLayeredTerrainTile source =",
            region_manager,
        )
        self.assertIn("resolveNativeTerrainTile(", region_manager)
        self.assertIn("owner.findTile(location).orElse(null)", region_manager)
        self.assertIn("placement.getMinX()", region_manager)
        self.assertIn("placement.getMaxX()", region_manager)
        self.assertIn("placement.getMinY()", region_manager)
        self.assertIn("placement.getMaxY()", region_manager)
        self.assertIn(
            "return nativeLayeredTile(location)", region_manager
        )
        self.assertIn(
            "return syntheticDeepFixtureTile()", region_manager
        )
        self.assertIn(
            "Native layered deep ", development
        )
        self.assertIn(
            "regionManager.runtimeProjectionId(location)", development
        )
        self.assertIn(
            "nativePackage.getPresentationChunkSize()", development
        )
        self.assertIn(
            "Native layered runtime requires 24-tile presentation chunks",
            runtime_profile,
        )
        self.assertIn(
            "hasNativeLayeredTerrain", region_manager
        )
        self.assertIn(
            "Cross-scope native layered movement requires an explicit transition",
            catalog,
        )
        self.assertNotIn(
            "Layered native terrain package requires the accepted synthetic",
            region_manager,
        )
        self.assertIn("findNativeLayeredSceneSector(", game_state_updater)
        self.assertIn(
            "findNativeTerrainSector(terrainPackage,sectorId)",
            game_state_updater,
        )
        self.assertIn("compressNativeTerrain(", game_state_updater)
        self.assertIn(
            '"; ready=" + nativeReadyChunks + "/9"', development
        )
        self.assertIn(
            '"Deep fixture logical="', development
        )


if __name__ == "__main__":
    unittest.main()
