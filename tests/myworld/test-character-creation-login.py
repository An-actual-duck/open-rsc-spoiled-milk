#!/usr/bin/env python3
"""Guard new-character appearance flow across server and client login."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]
ACTION_SENDER = ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
MYWORLD_CONF = ROOT / "server/myworld.conf"
MYWORLD_HOST_CONF = ROOT / "server/myworld-host.conf"
APPEARANCE_UPDATER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/PlayerAppearanceUpdater.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def forbid(text: str, needle: str, description: str) -> None:
    if needle in text:
        fail(f"retired {description} still present: {needle}")


def main() -> None:
    action_sender = ACTION_SENDER.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    myworld_conf = MYWORLD_CONF.read_text(encoding="utf-8")
    myworld_host_conf = MYWORLD_HOST_CONF.read_text(encoding="utf-8")
    appearance_updater = APPEARANCE_UPDATER.read_text(encoding="utf-8")

    require(
        action_sender,
        "final boolean pendingTutorialAppearance = player.isChangingAppearance()\n"
        "\t\t\t\t\t|| player.getCache().hasKey(\"tutorial_appearance\");",
        "pending tutorial appearance detection",
    )
    require(
        action_sender,
        "if (firstLogin || pendingTutorialAppearance) {\n"
        "\t\t\t\t\tplayer.getCache().store(\"tutorial_appearance\", false);\n\n"
        "\t\t\t\t\tsendAppearanceScreen(player);\n"
        "\t\t\t\t}",
        "appearance screen send for first or interrupted character creation",
    )
    forbid(
        action_sender,
        "if (!player.isChangingAppearance() && player.getCache().hasKey(\"tutorial_appearance\"))",
        "appearance resend guard that skipped already-pending character creation",
    )
    require(
        mudclient,
        "if (show && this.panelAppearance == null) {\n"
        "\t\t\tthis.createAppearancePanel(-24595, Config.S_CHARACTER_CREATION_MODE);\n"
        "\t\t}",
        "lazy appearance panel creation when opcode 59 arrives",
    )
    require(
        mudclient,
        "this.appearanceInputBlockTicks = 2;\n"
        "\t\t\tthis.currentMouseButtonDown = 0;\n"
        "\t\t\tthis.lastMouseButtonDown = 0;\n"
        "\t\t\tthis.enterPressed = false;",
        "stale login input clearing when character creator opens",
    )
    require(
        mudclient,
        "if (this.appearanceInputBlockTicks > 0) {\n"
        "\t\t\t\t\tthis.appearanceInputBlockTicks--;\n"
        "\t\t\t\t\tthis.currentMouseButtonDown = 0;\n"
        "\t\t\t\t\tthis.lastMouseButtonDown = 0;\n"
        "\t\t\t\t} else {\n"
        "\t\t\t\t\tthis.handleAppearancePanelControls(86);\n"
        "\t\t\t\t}",
        "appearance panel input warmup guard",
    )
    renderer_ready = re.search(
        r"public boolean isRenderer3DWorldReady\(\)\s*\{([^}]+)\}",
        mudclient,
    )
    if renderer_ready is None:
        fail("missing renderer world-readiness method")
    readiness = renderer_ready.group(1)
    for condition in (
        "this.currentViewMode == GameMode.GAME",
        "this.hasCompletedInitialRegionLoad",
        "!this.loadingArea",
        "!this.layeredSceneActivationPending",
        "!this.isFullScreenModalUiActive()",
    ):
        require(readiness, condition, "renderer world-readiness guard")
    forbid(readiness, "||", "renderer readiness bypass")
    require(
        mudclient,
        "public boolean isFullScreenModalUiActive() {\n"
        "\t\treturn this.deathScreenTimeout != 0\n"
        "\t\t\t|| this.showAppearanceChange",
        "full-screen modal UI helper includes character creator",
    )
    require(
        mudclient,
        "// type 1 is for worlds with the 1x XP mode selector",
        "character creator mode 1 XP-only label",
    )
    require(
        mudclient,
        "this.packetHandler.getClientStream().bufferBits.putByte(0);\n"
        "\t\t\t\tthis.packetHandler.getClientStream().bufferBits.putByte(Config.S_CHARACTER_CREATION_MODE == 1\n"
        "\t\t\t\t\t? this.panelAppearance.getControlClickedListIndex(this.playerMode2)\n"
        "\t\t\t\t\t: 0);",
        "new characters always send regular Ironman mode while preserving 1x XP choice",
    )
    forbid(mudclient, "modes_ironman", "Ironman character creator mode list")
    forbid(mudclient, "\"Regular\", \"Ironman\", \"Ultimate\", \"Hardcore\"", "Ironman character creator labels")
    require(myworld_conf, "spawn_iron_man_npcs: false # MODIFIED", "local Ironman NPC spawns disabled")
    require(myworld_host_conf, "spawn_iron_man_npcs: false # MODIFIED", "hosted Ironman NPC spawns disabled")
    require(
        appearance_updater,
        "player.setIronMan(IronmanMode.None.id());\n"
        "\t\t\t\tplayer.setOneXp(isOneXp == 1);",
        "server ignores custom Ironman mode during MyWorld character creation",
    )
    forbid(appearance_updater, "player.setIronMan(ironmanMode);", "server-side Ironman creation assignment")

    print("PASS: character creation login opens appearance UI")


if __name__ == "__main__":
    main()
