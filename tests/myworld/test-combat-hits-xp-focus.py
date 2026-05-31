#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT_CONFIG = ROOT / "Client_Base/src/orsc/Config.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
ACTION_SENDER = ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
GAME_SETTING_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/GameSettingHandler.java"
COMBAT_STYLE_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/CombatStyleHandler.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    config = CLIENT_CONFIG.read_text()
    client = CLIENT.read_text()
    packet_handler = PACKET_HANDLER.read_text()
    action_sender = ACTION_SENDER.read_text()
    game_setting_handler = GAME_SETTING_HANDLER.read_text()
    combat_style_handler = COMBAT_STYLE_HANDLER.read_text()
    player = PLAYER.read_text()
    npc = NPC.read_text()

    require("public static int C_HITS_XP_FOCUS_MENU = 1;" in config,
            "Hits XP focus menu should default to temporary mode")
    require('new String[]{"Select Hits XP focus", "No Hits XP", "Some Hits XP", "More Hits XP", "All Hits XP"}' in client,
            "Client should expose the four Hits XP focus labels")
    require("shouldDrawHitsXpFocusMenu() || shouldDrawGatheringFocusMenu()" in client,
            "Hits XP focus menu should draw independently of gathering focus")
    require("private long hitsXpFocusMenuHideAt = 0L;" in client,
            "Temporary Hits XP focus menu should have an interaction linger timer")
    require("private void showHitsXpFocusMenuTemporarily()" in client,
            "Client should be able to show the Hits XP focus menu for combat interactions")
    require("return true;\n\t\t}\n\t\tif (System.currentTimeMillis() < this.hitsXpFocusMenuHideAt)" in client,
            "Temporary Hits XP focus menu should appear while engaged in combat or during the linger timer")
    require("C_HITS_XP_FOCUS_MENU = (C_HITS_XP_FOCUS_MENU + 1) % 3;" in client,
            "Options menu should expose a three-state Hits XP focus menu setting")
    require("bufferBits.putByte(49);" in client,
            "Client should save the Hits XP focus menu setting at custom setting index 49")
    require("bufferBits.putByte(hitsXpFocusMenu ? selectedStyle + 4 : selectedStyle);" in client,
            "Custom client should send Hits XP focus selections as combat-style values 4..7")

    require("mc.setHitsXpFocusMenuToggle(packetsIncoming.packetEnd < length ? packetsIncoming.getUnsignedByte() : 1); // 49" in packet_handler,
            "Client should read the saved Hits XP menu setting from index 49")
    require("mc.setHitsXpFocus(packetsIncoming.packetEnd < length ? packetsIncoming.getUnsignedByte() : 1); // 50" in packet_handler,
            "Client should read the saved Hits XP focus selection")
    require("customOptions.add(player.getHitsXpFocusMenuToggle());" in action_sender,
            "Server should send the saved Hits XP menu setting")
    require("customOptions.add(player.getHitsXpFocus());" in action_sender,
            "Server should send the saved Hits XP focus selection")
    require('player.getCache().set("setting_hits_xp_focus_menu", focusMenuValue);' in game_setting_handler,
            "Server should persist the Hits XP menu setting")
    require("public int getHitsXpFocus()" in player and 'getCache().set("setting_hits_xp_focus", focus);' in player,
            "Player should persist the selected Hits XP focus")

    require("style >= 4 && style <= 7" in combat_style_handler and "player.setHitsXpFocus(hitsXpFocus);" in combat_style_handler,
            "CombatStyleHandler should route custom values 4..7 to Hits XP focus")
    require("Hits XP focus set to " in combat_style_handler,
            "Server should acknowledge Hits XP focus changes")

    require("awardCombatXpWithHitsFocus(player, Skill.MELEE, meleeXpShare * 4);" in npc,
            "Melee NPC XP should use the full old 3:1 melee/Hits budget for Hits XP focus")
    require("awardCombatXpWithHitsFocus(player, Skill.RANGED, remainderXP);" in npc,
            "Ranged NPC XP should use Hits XP focus")
    require("awardCombatXpWithHitsFocus(player, Skill.MAGIC, magicXpShare);" in npc,
            "Magic NPC XP should use Hits XP focus")
    require("case Skills.CONTROLLED_MODE:\n\t\t\t\treturn 0;" in npc,
            "No Hits XP focus should award no Hits XP")
    require("case Skills.DEFENSIVE_MODE:\n\t\t\t\treturn totalXp;" in npc,
            "All Hits XP focus should move the full combat XP budget into Hits")
    require("player.incExp(primarySkill.id(), primaryXp, true);" in npc,
            "Combat XP focus split should award exact primary XP directly")
    require("player.incExp(Skill.HITS.id(), hitsXp, true);" in npc,
            "Combat XP focus split should award exact Hits XP directly")
    require("player.incExp(skillsDist, totalXp, true);" not in npc,
            "Combat XP focus split must not pass exact XP values to the weighted distribution overload")
    require("case NPC_CAST_SPELL:" in client and "case PLAYER_CAST_SPELL:" in client and
            client.count("this.showHitsXpFocusMenuTemporarily();") >= 4,
            "Magic, NPC attack, and player attack interactions should show the temporary Hits XP focus menu")

    print("PASS: combat Hits XP focus menu, persistence, and NPC XP split validated")


if __name__ == "__main__":
    main()
