#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
APPLET = ROOT / "PC_Client/src/orsc/ORSCApplet.java"
SERVER_UPDATER = ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"{label} missing expected snippet: {needle!r}")


def main() -> None:
    client = CLIENT.read_text(encoding="utf-8")
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")
    applet = APPLET.read_text(encoding="utf-8")
    server_updater = SERVER_UPDATER.read_text(encoding="utf-8")

    require(
        client,
        "boolean needNextRegion = loadNextRegion(worldZ, worldX, false);",
        "custom movement region check",
    )
    require(
        client,
        "if (needNextRegion && consumeRegionLoadNeedsHardPlayerReset())",
        "custom movement hard region reset handling",
    )
    require(
        client,
        "if (!World.isLocalTile(this.playerLocalX, this.playerLocalZ))",
        "custom local player tile bounds guard",
    )
    require(
        client,
        "private boolean isValidCustomMovementDirection(int direction)",
        "custom movement direction guard",
    )
    require(
        client,
        "if (character == null || !World.isLocalTile(localTileX, localTileZ) || !isValidCustomMovementDirection(direction))",
        "custom movement waypoint guard",
    )
    require(
        client,
        "private int movementAmountForFrame(ORSCharacter character, int configuredPixelsPerClassicFrame, long frameNow)",
        "custom movement frame-time interpolation helper",
    )
    require(
        client,
        "double pixels = (elapsedMillis / 20.0D) * safeMovePerClassicFrame + character.movementPixelRemainder;",
        "custom movement should derive movement from elapsed frame time",
    )
    require(
        client,
        "resetMovementInterpolation(updateEntity);",
        "custom movement should clear interpolation state when idle or snapped",
    )
    require(
        (ROOT / "Client_Base/src/orsc/ORSCharacter.java").read_text(encoding="utf-8"),
        "public double movementPixelRemainder = 0.0D;",
        "custom movement should retain subpixel interpolation remainder per character",
    )
    require(
        packet_handler,
        "props.setProperty(\"C_NPC_MOVE_PER_FRAME\", String.valueOf(movePerFrame)); //66",
        "custom movement should mirror server movement speed to NPC interpolation",
    )
    require(
        packet_handler,
        "mc.invalidateCustomNpcMovementTarget(npc.serverIndex);\n\t\t\t\t\t\tnpc.animationNext = nextSprite;",
        "stationary NPC stance update should invalidate stale movement direction",
    )
    require(
        packet_handler,
        "if (nextSpriteOffset == 3) {\n\t\t\t\t\t\tmc.detachNpcCombatEffect(npc);\n\t\t\t\t\t\tif (npc != null) {\n\t\t\t\t\t\t\tmc.invalidateCustomNpcMovementTarget(npc.serverIndex);",
        "removed NPC should invalidate stale movement direction",
    )
    require(
        client,
        "if (var8) {\n\t\t\t\tinvalidateCustomNpcMovementTarget(serverIndex);\n\t\t\t\tcharacter.animationNext = sprite;",
        "cached NPC refresh should preserve its direct combat stance",
    )
    require(
        client,
        "this.customNpcMovementTargetValid[serverIndex] = false;\n\t\tthis.customNpcMovementTargetResult[serverIndex] = \"invalidated by direct NPC stance update\";",
        "direct NPC stance invalidation helper",
    )
    require(
        applet,
        "if (imageProducer != null) {\n\t\t\timageProducer.setDimensions(newWidth, newHeight);\n\t\t}",
        "resize image producer null guard",
    )
    require(
        server_updater,
        "(localNpc.inCombat() && !hasPendingDeathVisual && !useCustomMovementStream)",
        "custom movement NPC combat cache stability",
    )
    require(
        server_updater,
        "mobsUpdate.add(bit(UPDATE_NOT_REQUIRED, 1));\n\t\t\t\t\t} else if (localNpc.hasMoved() && !useCustomMovementStream) {",
        "custom movement NPC death visual should preserve its last client-facing pose",
    )
    require(
        server_updater,
        "} else if (localNpc.spriteChanged()) {\n\t\t\t\t\t\tmobsUpdate.add(bit(UPDATE_REQUIRED, 1));",
        "custom movement NPC combat sprite update",
    )
    require(
        server_updater,
        "&& localNpc.getSprite() >= 12;",
        "custom movement NPC high combat sprite refresh guard",
    )
    require(
        server_updater,
        "spriteNeedsFullRefresh ||",
        "custom movement NPC high combat sprite remove/re-add path",
    )
    require(
        server_updater,
        "final int localNpcLimit = localMobLimit(playerToUpdate);",
        "custom movement NPC local cache limit",
    )
    require(
        server_updater,
        "private static List<Npc> prioritizeVisibleNpcs",
        "custom movement NPC prioritization helper",
    )
    require(
        server_updater,
        "npc.equals(playerOpponent) || player.equals(npcOpponent)",
        "custom movement NPC combat target priority",
    )
    require(
        server_updater,
        "evictForNpcPriority ||",
        "custom movement NPC priority eviction path",
    )
    require(
        server_updater,
        "NPC_DEATH_VISUAL_SENT_TICK_PREFIX + playerToUpdate.getIndex()",
        "custom movement NPC death visual should be tracked per viewer",
    )
    require(
        server_updater,
        "deathVisualSentTick != deathVisualTick",
        "custom movement NPC death visual should be single-use per death",
    )
    require(
        server_updater,
        "localNpc.setAttribute(deathVisualViewerKey, deathVisualTick);",
        "custom movement NPC death visual should mark consumption before removal retry",
    )
    require(
        (ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java").read_text(encoding="utf-8"),
        "public static final String DEATH_VISUAL_TICK_ATTRIBUTE = \"npc_death_visual_tick\";",
        "NPC death visual tick marker",
    )

    print("PASS: custom movement update and resize stability guards are present")


if __name__ == "__main__":
    main()
