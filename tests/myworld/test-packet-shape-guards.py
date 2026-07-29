#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MOVEMENT_UPDATE_STRUCT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "struct" / "outgoing" / "MovementUpdateStruct.java"
MOVEMENT_SNAPSHOT_STRUCT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "struct" / "outgoing" / "MovementSnapshotStruct.java"
MOBS_UPDATE_STRUCT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "struct" / "outgoing" / "MobsUpdateStruct.java"
PAYLOAD_235_GENERATOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "generators" / "impl" / "Payload235Generator.java"
PAYLOAD_CUSTOM_GENERATOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "generators" / "impl" / "PayloadCustomGenerator.java"
PAYLOAD_VALIDATOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "PayloadValidator.java"
GAME_STATE_UPDATER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "GameStateUpdater.java"
PACKET_HANDLER = ROOT / "Client_Base" / "src" / "orsc" / "PacketHandler.java"
MOVEMENT_SNAPSHOT_STAGE = ROOT / "Client_Base" / "src" / "orsc" / "MovementSnapshotStage.java"
MOVEMENT_DIAGNOSTICS = ROOT / "Client_Base" / "src" / "orsc" / "MovementSnapshotDiagnostics.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(path: Path, snippet: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if snippet not in text:
        fail(f"{label} missing expected snippet {snippet!r}")


def main() -> None:
    require(PAYLOAD_VALIDATOR, "put(OpcodeOut.SEND_MOVEMENT_UPDATE, MovementUpdateStruct.class)", "custom movement payload validator mapping")
    require(PAYLOAD_VALIDATOR, "put(OpcodeOut.SEND_MOVEMENT_SNAPSHOT, MovementSnapshotStruct.class)", "custom movement snapshot payload validator mapping")

    require(MOVEMENT_UPDATE_STRUCT, "public int localX;", "custom movement local X")
    require(MOVEMENT_UPDATE_STRUCT, "public int localY;", "custom movement local Y")
    require(MOVEMENT_UPDATE_STRUCT, "public int localSprite;", "custom movement local sprite")
    require(MOVEMENT_UPDATE_STRUCT, "public List<MobMovement> players = new ArrayList<>();", "custom movement player list")
    require(MOVEMENT_UPDATE_STRUCT, "public List<MobMovement> npcs = new ArrayList<>();", "custom movement NPC list")
    require(MOVEMENT_UPDATE_STRUCT, "public final int serverIndex;", "custom movement server index")
    require(MOVEMENT_UPDATE_STRUCT, "public final int sprite;", "custom movement sprite")
    require(MOVEMENT_SNAPSHOT_STRUCT, "public int protocolVersion;", "custom movement snapshot protocol version")
    require(MOVEMENT_SNAPSHOT_STRUCT, "public int serverTick;", "custom movement snapshot server tick")
    require(MOVEMENT_SNAPSHOT_STRUCT, "public int sequence;", "custom movement snapshot sequence")
    require(MOVEMENT_SNAPSHOT_STRUCT, "public List<MobMovement> players = new ArrayList<>();", "custom movement snapshot player list")
    require(MOVEMENT_SNAPSHOT_STRUCT, "public List<MobMovement> npcs = new ArrayList<>();", "custom movement snapshot NPC list")

    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(movement.localX);", "custom movement local X encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(movement.localY);", "custom movement local Y encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeByte((byte) movement.localSprite);", "custom movement local sprite encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(movement.players.size());", "custom movement player count encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(movement.npcs.size());", "custom movement NPC count encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(playerMovement.serverIndex);", "custom movement player index encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(npcMovement.serverIndex);", "custom movement NPC index encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "put(OpcodeOut.SEND_MOVEMENT_SNAPSHOT, 146);", "custom movement snapshot opcode")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeByte((byte) movementSnapshot.protocolVersion);", "custom movement snapshot protocol encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeInt(movementSnapshot.serverTick);", "custom movement snapshot tick encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeInt(movementSnapshot.sequence);", "custom movement snapshot sequence encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(movementSnapshot.players.size());", "custom movement snapshot player count encoding")
    require(PACKET_HANDLER, "int playerCount = packetsIncoming.getShort();", "custom movement player count decoding")
    require(PACKET_HANDLER, "int npcCount = packetsIncoming.getShort();", "custom movement NPC count decoding")
    require(PACKET_HANDLER, "put(146, \"MOVEMENT_SNAPSHOT\");", "custom movement snapshot opcode name")
    require(PACKET_HANDLER, "else if (opcode == 146) updateMovementSnapshot(length);", "custom movement snapshot packet handler")
    require(MOVEMENT_DIAGNOSTICS, "static final class Fingerprint", "custom movement packet fingerprint")
    require(PACKET_HANDLER, "movementSnapshotDiagnostics.recordMovementUpdate(fingerprint);", "custom movement update fingerprint recording")
    require(PACKET_HANDLER, "movementSnapshotDiagnostics.recordSnapshot(", "custom movement snapshot wire comparison")
    require(PACKET_HANDLER, "private final MovementSnapshotDiagnostics movementSnapshotDiagnostics", "custom movement diagnostics owner")
    require(PACKET_HANDLER, "private final MovementSnapshotStage movementSnapshotStage = new MovementSnapshotStage();", "custom movement snapshot staging state")
    require(PACKET_HANDLER, "MovementSnapshotStage.Frame stageFrame = null;", "custom movement snapshot staging frame")
    require(PACKET_HANDLER, "stageFrame.addPlayer(serverIndex, x, z, direction);", "custom movement snapshot staged player target")
    require(PACKET_HANDLER, "stageFrame.addNpc(serverIndex, x, z, direction);", "custom movement snapshot staged NPC target")
    require(PACKET_HANDLER, "movementSnapshotStage.replaceFromSnapshot(stageFrame, mc)", "custom movement snapshot staging compare")
    require(MOVEMENT_DIAGNOSTICS, "static final class CacheParity", "custom movement snapshot parity helper")
    require(PACKET_HANDLER, "parity.checkPlayer(mc, serverIndex, x, z, direction);", "custom movement snapshot player parity")
    require(PACKET_HANDLER, "parity.checkNpc(mc, serverIndex, x, z, direction);", "custom movement snapshot NPC parity")
    require(MOVEMENT_DIAGNOSTICS, "private static final class SnapshotDebugState", "custom movement snapshot debug state")
    require(MOVEMENT_SNAPSHOT_STAGE, "final class MovementSnapshotStage", "custom movement snapshot staging helper")
    require(MOVEMENT_SNAPSHOT_STAGE, "Result replaceFromSnapshot(Frame frame, mudclient mc)", "custom movement snapshot staging replacement")
    require(MOVEMENT_SNAPSHOT_STAGE, "private Result compareToVisibleCache(mudclient mc)", "custom movement snapshot staging cache comparison")
    require(MOVEMENT_SNAPSHOT_STAGE, "static final class Frame", "custom movement snapshot staging frame type")
    require(MOVEMENT_DIAGNOSTICS, "\"wire ok\"", "custom movement snapshot wire summary")
    require(MOVEMENT_DIAGNOSTICS, "\"cache ok c\" + cacheChecked", "custom movement snapshot cache summary")
    require(MOVEMENT_DIAGNOSTICS, "\"stage \" + (stageCurrentMismatches == 0 ? \"ok\" : \"bad\")", "custom movement snapshot staging summary")

    require(MOBS_UPDATE_STRUCT, "public static final class BitUpdate implements Map.Entry<Integer, Integer>", "retro bit update entry contract")
    require(MOBS_UPDATE_STRUCT, "throw new UnsupportedOperationException(\"BitUpdate is immutable\")", "retro bit update immutability")
    require(PAYLOAD_235_GENERATOR, "if (entry instanceof MobsUpdateStruct.BitUpdate)", "235 bit update fast path")
    require(PAYLOAD_235_GENERATOR, "builder.writeBits(bit.getRawKey(), bit.getRawValue());", "235 primitive bit writer")
    require(PAYLOAD_235_GENERATOR, "builder.writeBits(entry.getKey(), entry.getValue());", "235 compatibility bit writer")

    require(GAME_STATE_UPDATER, "if (!player.isUsingCustomClient()) {\n\t\t\treturn false;\n\t\t}", "custom movement stream client gate")
    require(GAME_STATE_UPDATER, "public boolean sendMovementUpdatePacket", "custom movement update path")
    require(GAME_STATE_UPDATER, "tryFinalizeAndSendPacket(OpcodeOut.SEND_MOVEMENT_UPDATE, struct, player);", "custom movement packet send")
    require(GAME_STATE_UPDATER, "public boolean sendMovementSnapshotPacket", "custom movement snapshot path")
    require(GAME_STATE_UPDATER, "WANT_SYNC_MOVEMENT_SNAPSHOT", "custom movement snapshot config gate")
    require(GAME_STATE_UPDATER, "tryFinalizeAndSendPacket(OpcodeOut.SEND_MOVEMENT_SNAPSHOT, struct, player);", "custom movement snapshot packet send")
    require(GAME_STATE_UPDATER, "private static boolean isWithinClientLocalTileWindow", "custom movement local tile window guard")
    require(GAME_STATE_UPDATER, "if (!isWithinMobPacketRange(player, movedNpc))", "custom movement NPC local window filter")
    require(GAME_STATE_UPDATER, "return isWithinClientLocalTileWindow(", "custom movement helper delegates to the local tile window")
    require(GAME_STATE_UPDATER, "final boolean useCustomMovementStream = playerToUpdate.isUsingCustomClient()\n\t\t\t\t&& getServer().getConfig().WANT_CUSTOM_WALK_SPEED;", "custom movement stream config gate")
    require(GAME_STATE_UPDATER, "mobCoordOffset(", "retro coordinate offset helper")

    print("PASS: packet shape guards validated")


if __name__ == "__main__":
    main()
