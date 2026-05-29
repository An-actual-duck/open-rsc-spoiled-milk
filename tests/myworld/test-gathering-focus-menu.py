#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT_CONFIG = ROOT / "Client_Base/src/orsc/Config.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
ACTION_SENDER = ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
GAME_SETTING_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/GameSettingHandler.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    config = CLIENT_CONFIG.read_text()
    client = CLIENT.read_text()
    packet_handler = PACKET_HANDLER.read_text()
    action_sender = ACTION_SENDER.read_text()
    game_setting_handler = GAME_SETTING_HANDLER.read_text()
    player = PLAYER.read_text()

    require("public static int C_GATHERING_FOCUS_MENU = 1;" in config,
            "Tool focus menu should default to temporary mode")
    require("showGatheringFocusMenuTemporarily(itemId);" in client,
            "Gathering focus menu should be triggered by action progress")
    require("completeActionProgressBar();" in client and "this.gatheringFocusMenuHideAt = 0L;" in client,
            "Walking or cancellation should hide the temporary focus menu instead of extending it")
    require("getGatheringFocusKindForItem(int itemId)" in client and "hasEquippedGatheringFocusTool(this.activeGatheringFocusKind)" in client,
            "Temporary focus menu should only show for the gathering action matching the equipped tool")
    require("shouldDrawGatheringFocusMenu()" in client,
            "Focus menu drawing should be gated by the new visibility mode")
    require("Tool Focus Menu - " in client and "C_GATHERING_FOCUS_MENU = (C_GATHERING_FOCUS_MENU + 1) % 3;" in client,
            "Options menu should expose a three-state tool focus menu setting")
    require("bufferBits.putByte(48);" in client,
            "Client should save the tool focus menu setting at custom setting index 48")
    require("mc.setGatheringFocusMenuToggle(packetsIncoming.packetEnd < length ? packetsIncoming.getUnsignedByte() : 1); // 48" in packet_handler,
            "Client should read the saved tool focus menu setting from index 48")
    require("customOptions.add(player.getGatheringFocusMenuToggle());" in action_sender,
            "Server should send the saved tool focus menu setting")
    require('player.getCache().set("setting_gathering_focus_menu", focusMenuValue);' in game_setting_handler,
            "Server should persist the tool focus menu setting")
    require("public int getGatheringFocusMenuToggle()" in player and "return 1;" in player,
            "Server should default the focus menu setting to temporary")

    print("PASS: gathering focus menu behavior and saved option validated")


if __name__ == "__main__":
    main()
