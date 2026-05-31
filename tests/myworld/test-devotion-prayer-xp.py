#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEVOTION = ROOT / "server/src/com/openrsc/server/content/Devotion.java"
BONES = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/Bones.java"
GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
ACTION_SENDER = ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
CUSTOM_GENERATOR = ROOT / "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL: {message}")


def main() -> None:
    devotion = DEVOTION.read_text(encoding="utf-8")
    bones = BONES.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")
    client = CLIENT.read_text(encoding="utf-8")
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")
    action_sender = ACTION_SENDER.read_text(encoding="utf-8")
    custom_generator = CUSTOM_GENERATOR.read_text(encoding="utf-8")

    require('CACHE_PREFIX = "devotion_"' in devotion, "devotion cache prefix should be stable")
    require('CACHE_SUFFIX = "_offerings"' in devotion, "devotion cache suffix should be stable")
    require("OFFERINGS_PER_BONUS_XP = 10" in devotion, "devotion should award +1 XP per 10 offerings")
    require("MAX_DEVOTION_LEVEL = 1000" in devotion, "devotion should be capped at 1000")
    require("DEVOTION_REQUIREMENT_PER_RESOURCE = 50" in devotion,
            "blessing devotion requirements should be resource cost * 50")
    require("getDevotionRequirementForResourceCost" in devotion,
            "devotion resource-cost requirement helper should exist")
    require("getBlessingPrayerXp" in devotion and "100 + devotionLevel" in devotion,
            "blessing Prayer XP should scale by 1% per devotion")
    require("previousOfferings / OFFERINGS_PER_BONUS_XP" in devotion,
            "devotion bonus should be based on completed prior offering tiers")
    require("OFFERINGS_PER_DEVOTION_LEVEL = OFFERINGS_PER_BONUS_XP" in devotion,
            "devotion levels should use the same 10-offering cadence as bonus XP")
    require("getDevotionLevel" in devotion, "devotion levels should be readable per god")
    require("addDevotionLevels" in devotion, "one-off devotion rewards should be supported")
    require("ActionSender.sendDevotion(player)" in devotion, "devotion changes should update the client")
    require("player.getPrayerBook()" in devotion, "devotion should track against the active worshipped god")
    require("safeGodLine.name().toLowerCase()" in devotion, "devotion cache keys should be per god")
    require("recordOfferingAndGetPrayerXpBonus" in bones, "bones and ashes should record devotion offerings")
    require("xpToGive += devotionBonusXp;" in bones, "devotion bonus should be added to Prayer XP")
    require("Every 10 offerings gives +1 devotion" in guide,
            "Prayer skill guide should explain devotion levels")
    require("+1 Prayer XP per offering for each devotion" in guide,
            "Prayer skill guide should explain devotion XP scaling")
    require('drawString("Devotion: " + this.currentDevotionLevel' in client,
            "Prayer skill tooltip should show current devotion")
    require("opcode == 145" in packet_handler and "setCurrentDevotionLevel" in packet_handler,
            "client should accept devotion updates from the server")
    require("sendDevotion(Player player)" in action_sender, "server should send devotion updates")
    require("put(OpcodeOut.SEND_DEVOTION, 145)" in custom_generator,
            "devotion packet should have a custom client opcode")

    print("PASS: devotion Prayer XP scaling is wired to bone and ash offerings")


if __name__ == "__main__":
    main()
