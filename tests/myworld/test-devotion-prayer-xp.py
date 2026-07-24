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
    require("MIN_DEVOTION_LEVEL = -1000" in devotion, "devotion should go down to -1000")
    require("MIN_OFFERINGS = MIN_DEVOTION_LEVEL * OFFERINGS_PER_DEVOTION_LEVEL" in devotion,
            "negative devotion should use the same offering scale")
    require("DEVOTION_REQUIREMENT_PER_RESOURCE = 100" in devotion,
            "blessing devotion requirements should be resource cost * 100")
    require("BLESSING_OFFERING_COST_PER_RESOURCE = OFFERINGS_PER_DEVOTION_LEVEL / 2" in devotion,
            "blessing cost should be five offering units per resource")
    require("getDevotionRequirementForResourceCost" in devotion,
            "devotion resource-cost requirement helper should exist")
    require("getBlessingOfferingCostForResourceCost" in devotion,
            "devotion fractional blessing-cost helper should exist")
    require("getBlessingPrayerXp" in devotion and "100.0D + devotionLevel" in devotion,
            "blessing Prayer XP should scale by 1% per devotion")
    require("clampOfferings((long) previousOfferings + offeringGain + blackUnicornOfferingGain)" in devotion,
            "offering gains should clamp after long arithmetic")
    require("clampOfferings((long) previousOfferings + ((long) devotionLevels * OFFERINGS_PER_DEVOTION_LEVEL))" in devotion,
            "devotion-level adjustments should not overflow before clamping")
    require("clampOfferings((long) previousOfferings + offerings)" in devotion,
            "offering adjustments should not overflow before clamping")
    require("clampPositiveInt((long) resourceCost * DEVOTION_REQUIREMENT_PER_RESOURCE)" in devotion,
            "resource-cost devotion requirements should not overflow before clamping")
    require("clampPositiveInt((long) resourceCost * BLESSING_OFFERING_COST_PER_RESOURCE)" in devotion,
            "resource-cost blessing costs should not overflow before clamping")
    require("player.getPrayers().deactivateOverflowingPrayers();" in devotion,
            "Devotion reductions should deactivate prayers above the new capacity")
    require("scaledXp >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(scaledXp)" in devotion,
            "blessing Prayer XP should saturate instead of overflowing")
    require("getDevotionLevelFromOfferings(previousOfferings)" in devotion,
            "devotion bonus should be based on completed prior offering tiers")
    require("if (newDevotion > previousDevotion)" in devotion and "sendDevotionIncreaseMessage" in devotion,
            "devotion increase messages should trigger even while recovering from negative devotion")
    require("recovers. Current devotion:" in devotion and "recovers to neutral" in devotion,
            "negative and neutral devotion recovery should have clear messages")
    require("OFFERINGS_PER_DEVOTION_LEVEL = OFFERINGS_PER_BONUS_XP" in devotion,
            "devotion levels should use the same 10-offering cadence as bonus XP")
    require("getDevotionLevel" in devotion, "devotion levels should be readable per god")
    require("addDevotionLevels" in devotion, "one-off devotion rewards should be supported")
    require("ActionSender.sendDevotion(player)" in devotion, "devotion changes should update the client")
    require("player.getPrayerBook()" in devotion, "devotion should track against the active worshipped god")
    require("safeGodLine.name().toLowerCase()" in devotion, "devotion cache keys should be per god")
    require("recordOfferingAndGetPrayerXpBonus" in bones, "bones and ashes should record devotion offerings")
    require("return bonusXp * 4;" in devotion,
            "devotion bonus should convert displayed XP to internal quarter-XP units")
    require("awardOfferingPrayerXpBonus" in devotion,
            "devotion bonus should have a flat unmodified XP award helper")
    require("player.getFatigue() >= player.MAX_FATIGUE" in devotion,
            "flat devotion XP should respect max fatigue")
    require("Devotion.awardOfferingPrayerXpBonus(player, praySkillId, devotionBonusXp);" in bones,
            "devotion bonus should be awarded separately from normal Prayer XP modifiers")
    require("Every 10 offerings gives +1 devotion" in guide,
            "Prayer skill guide should explain devotion levels")
    require("Devotion ranges from -1000 to 1000" in guide,
            "Prayer skill guide should explain negative devotion range")
    require("Blessed symbols give 1.5x devotion from offerings" in guide,
            "Prayer skill guide should explain blessed symbol offering bonus")
    require("+1 Prayer XP per offering for each devotion" in guide,
            "Prayer skill guide should explain devotion XP scaling")
    require('drawString("Devotion: " + this.currentDevotionLevel' in client,
            "Prayer skill tooltip should show current devotion")
    require("private int readSignedShort()" in packet_handler
            and "value > Short.MAX_VALUE ? value - 0x10000 : value" in packet_handler,
            "client should have a signed short reader for devotion")
    require("opcode == 145" in packet_handler and "setCurrentDevotionLevel(readSignedShort())" in packet_handler,
            "client should accept signed devotion updates from the server")
    require("sendDevotion(Player player)" in action_sender, "server should send devotion updates")
    require("put(OpcodeOut.SEND_DEVOTION, 145)" in custom_generator,
            "devotion packet should have a custom client opcode")
    require("builder.writeShort(devotion.devotionLevel)" in custom_generator,
            "server should send devotion as a signed short-sized value")

    print("PASS: devotion Prayer XP scaling is wired to bone and ash offerings")


if __name__ == "__main__":
    main()
