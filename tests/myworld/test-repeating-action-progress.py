#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BATCH = ROOT / "server/src/com/openrsc/server/plugins/Batch.java"
FUNCTIONS = ROOT / "server/src/com/openrsc/server/plugins/Functions.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PROCESSORS = ROOT / "server/src/com/openrsc/server/net/rsc/PayloadProcessorManager.java"
FISHING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/fishing/Fishing.java"
GEODES = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/gathering/Geodes.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PACKETS = ROOT / "Client_Base/src/orsc/PacketHandler.java"
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"


def require(text, snippet, message):
    if snippet not in text:
        raise AssertionError(message)


def forbid(text, snippet, message):
    if snippet in text:
        raise AssertionError(message)


def main():
    batch = BATCH.read_text(encoding="utf-8")
    functions = FUNCTIONS.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")
    processors = PROCESSORS.read_text(encoding="utf-8")
    fishing = FISHING.read_text(encoding="utf-8")
    geodes = GEODES.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    packets = PACKETS.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")

    forbid(batch, "sendProgressBar(", "The legacy Batching window must not be shown")
    forbid(batch, "sendUpdateProgressBar(", "The legacy batch counter must not be updated")
    require(
        batch,
        "ActionSender.sendRemoveActionProgressBar(getPlayer());",
        "Stopping a repeat must clear the actor-attached progress bar",
    )
    require(functions, "public static void startbatchunlimited()", "Unlimited repeat helper must exist")
    require(batch, "private static final int UNLIMITED_BATCH_SIZE = -1;", "Unlimited batches need a sentinel")

    forbid(fishing, "FISHING_BATCH_SIZE", "Fishing must not stop at the old 30-action limit")
    if fishing.count("startbatchunlimited();") < 2:
        raise AssertionError("Both MyWorld and legacy fishing paths must repeat until a real stop condition")
    require(
        fishing,
        "ActionSender.sendActionProgressBar(player, rodId, delay);",
        "MyWorld fishing must show its rod and per-catch progress",
    )
    require(
        fishing,
        "ActionSender.sendActionProgressBar(player, def.getNetId(), 3);",
        "Legacy fishing must show its tool and per-catch progress",
    )

    require(geodes, "startbatchunlimited();", "Geodes must continue beyond 30")
    require(
        geodes,
        "ActionSender.sendActionProgressBar(player, ItemId.CHISEL.id(), 3);",
        "Each geode must show chisel progress",
    )
    require(
        geodes,
        "player.getCarriedItems().getInventory().countId(size.itemId, Optional.of(false)) > 0",
        "Geodes must stop when the selected geode size runs out",
    )

    require(
        processors,
        "player.hasActiveBatch() && ACTION_OPCODES.contains(payload.getOpcode())",
        "Gameplay input must interrupt active repeating actions",
    )
    require(player, "if (batch != null) {", "Plugin interruption must stop the active batch")
    require(player, "batch.stop();", "Plugin interruption must clear repeat progress")
    require(player, "setBusy(false);", "The action that interrupts a repeat must proceed immediately")

    require(client, 'addSkill("Harvest");', "Skills tab must display Harvest")
    forbid(client, 'addSkill("Harvesting");', "Skills tab must not display Harvesting")
    require(
        packets,
        'mc.getSkillNamesLong()[skill]',
        "Skill updates must use the append-only runtime skill names, including Harvest and Blessing",
    )
    require(guide, 'equalsIgnoreCase("Harvest")', "Harvest skill-guide routing must match the new label")

    print("PASS: repeating action progress, interruption, and Harvest label validated")


if __name__ == "__main__":
    main()
