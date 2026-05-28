#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MOVEMENT_UPDATE_STRUCT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "struct" / "outgoing" / "MovementUpdateStruct.java"
MOBS_UPDATE_STRUCT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "struct" / "outgoing" / "MobsUpdateStruct.java"
PAYLOAD_235_GENERATOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "generators" / "impl" / "Payload235Generator.java"
PAYLOAD_CUSTOM_GENERATOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "generators" / "impl" / "PayloadCustomGenerator.java"
PAYLOAD_VALIDATOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "PayloadValidator.java"
GAME_STATE_UPDATER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "GameStateUpdater.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(path: Path, snippet: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if snippet not in text:
        fail(f"{label} missing expected snippet {snippet!r}")


def main() -> None:
    require(PAYLOAD_VALIDATOR, "put(OpcodeOut.SEND_MOVEMENT_UPDATE, MovementUpdateStruct.class)", "custom movement payload validator mapping")

    require(MOVEMENT_UPDATE_STRUCT, "public int localX;", "custom movement local X")
    require(MOVEMENT_UPDATE_STRUCT, "public int localY;", "custom movement local Y")
    require(MOVEMENT_UPDATE_STRUCT, "public int localSprite;", "custom movement local sprite")
    require(MOVEMENT_UPDATE_STRUCT, "public List<MobMovement> players = new ArrayList<>();", "custom movement player list")
    require(MOVEMENT_UPDATE_STRUCT, "public List<MobMovement> npcs = new ArrayList<>();", "custom movement NPC list")
    require(MOVEMENT_UPDATE_STRUCT, "public final int serverIndex;", "custom movement server index")
    require(MOVEMENT_UPDATE_STRUCT, "public final int sprite;", "custom movement sprite")

    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(movement.localX);", "custom movement local X encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(movement.localY);", "custom movement local Y encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeByte((byte) movement.localSprite);", "custom movement local sprite encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeByte((byte) movement.players.size());", "custom movement player count encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeByte((byte) movement.npcs.size());", "custom movement NPC count encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(playerMovement.serverIndex);", "custom movement player index encoding")
    require(PAYLOAD_CUSTOM_GENERATOR, "builder.writeShort(npcMovement.serverIndex);", "custom movement NPC index encoding")

    require(MOBS_UPDATE_STRUCT, "public static final class BitUpdate implements Map.Entry<Integer, Integer>", "retro bit update entry contract")
    require(MOBS_UPDATE_STRUCT, "throw new UnsupportedOperationException(\"BitUpdate is immutable\")", "retro bit update immutability")
    require(PAYLOAD_235_GENERATOR, "if (entry instanceof MobsUpdateStruct.BitUpdate)", "235 bit update fast path")
    require(PAYLOAD_235_GENERATOR, "builder.writeBits(bit.getRawKey(), bit.getRawValue());", "235 primitive bit writer")
    require(PAYLOAD_235_GENERATOR, "builder.writeBits(entry.getKey(), entry.getValue());", "235 compatibility bit writer")

    require(GAME_STATE_UPDATER, "if (!player.isUsingCustomClient()) {\n\t\t\treturn false;\n\t\t}", "custom movement stream client gate")
    require(GAME_STATE_UPDATER, "public boolean sendMovementUpdatePacket", "custom movement update path")
    require(GAME_STATE_UPDATER, "tryFinalizeAndSendPacket(OpcodeOut.SEND_MOVEMENT_UPDATE, struct, player);", "custom movement packet send")
    require(GAME_STATE_UPDATER, "final boolean useCustomMovementStream = playerToUpdate.isUsingCustomClient()\n\t\t\t\t&& getServer().getConfig().WANT_CUSTOM_WALK_SPEED;", "custom movement stream config gate")
    require(GAME_STATE_UPDATER, "mobCoordOffset(", "retro coordinate offset helper")

    print("PASS: packet shape guards validated")


if __name__ == "__main__":
    main()
