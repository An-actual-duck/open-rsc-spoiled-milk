#!/usr/bin/env python3
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[2]
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
PRAYER_DEF = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/defs/PrayerDef.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
ACTION_SENDER = ROOT / "server/src/com/openrsc/server/net/rsc/ActionSender.java"
CUSTOM_GENERATOR = ROOT / "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java"
PRAYER_ASSETS = ROOT / "dev/myworld/assets/sprites/UI/prayer"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def png_size(path):
    data = path.read_bytes()
    require(data[:8] == b"\x89PNG\r\n\x1a\n", f"Invalid PNG: {path}")
    return struct.unpack(">II", data[16:24])


def main():
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    entity_handler = ENTITY_HANDLER.read_text(encoding="utf-8")
    prayer_def = PRAYER_DEF.read_text(encoding="utf-8")
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")
    action_sender = ACTION_SENDER.read_text(encoding="utf-8")
    custom_generator = CUSTOM_GENERATOR.read_text(encoding="utf-8")

    require("private static final int PRAYER_ICON_COLUMNS = MAGIC_ICON_COLUMNS;" in mudclient,
            "Prayer UI should match the magic icon column count")
    require("private static final int PRAYER_ICON_VISIBLE_ROWS = MAGIC_ICON_VISIBLE_ROWS;" in mudclient,
            "Prayer UI should match the magic icon visible row count")
    require("int prayerColumns = PRAYER_ICON_COLUMNS;" in mudclient,
            "Prayer UI should render through the shared prayer icon grid constant")
    require("prayerRow < PRAYER_ICON_VISIBLE_ROWS" in mudclient,
            "Prayer UI should limit interaction through the shared visible-row constant")
    require("drawBoxAlpha(prayerX, prayerY, prayerIconSize, prayerIconSize" in mudclient,
            "Prayer UI should draw icon placeholder cells")
    require("int prayerTooltipY = prayerGridY + prayerGridHeight + 14;" in mudclient,
            "Prayer detail text should use the compact vertical gap below the icon grid")
    require("Reserved points: \" + prayerDef.getPointCost()" in mudclient,
            "Prayer tooltip should show allocation cost instead of drain rate")
    require("getCompactPrayerTierIconFile" in mudclient,
            "Prayer icon loader should support compact tier names like xp.png, xp2.png")
    require('"dev/myworld/assets/sprites/UI/prayer"' in mudclient,
            "Prayer icon loader should search the current UI prayer asset folder")
    require("getExternalIconFile(assetName + tier)" in mudclient,
            "Prayer icon loader should support numbered tier names like magic-power1.png")
    for asset_name in ["enchanting-xp", "smithing-xp", "crafting-xp"]:
        require(f'return "{asset_name}";' in mudclient,
                f"Prayer icon loader should map the {asset_name} favor family")
    require("Drain rate:" not in mudclient[mudclient.find("// 1 is prayer list"):mudclient.find("if (var1)")],
            "Prayer UI should no longer present drain rate text")
    require("Your prayer ability is not high enough for this prayer" not in mudclient[
        mudclient.find("if (this.mouseButtonClick == 1 && this.magicOrPrayerList == 1)") :
        mudclient.find("this.mouseButtonClick = 0;")
    ], "Prayer click path should not client-side gate by old level thresholds")

    required_prayers = [
        "Weak Magic Power",
        "Greater Magic Power",
        "Weak Melee Protection",
        "Greater Melee Protection",
        "Weak Enchanting Favor",
        "Greater Enchanting Favor",
        "Weak Melee Power",
        "Greater Ranged Protection",
        "Greater Smithing Favor",
        "Weak Ranged Power",
        "Greater Magic Protection",
        "Greater Crafting Favor",
    ]
    for prayer in required_prayers:
        require(prayer in entity_handler, f"Missing client prayer definition: {prayer}")

    require(entity_handler.count("prayers.add(new PrayerDef") == 45,
            "Client prayer definitions should cover all three 15-slot god lines")
    require("public static void setPrayerBook(String prayerBook)" in entity_handler,
            "Client prayer definitions should support swapping visible prayer books")
    require('activePrayerBook = "SARADOMIN"' in entity_handler,
            "Client prayer book should default to Saradomin until altar switching is wired")
    require("public int getPointCost()" in prayer_def,
            "Client PrayerDef should expose allocation point cost terminology")
    require("opcode == 139" in packet_handler and "mc.setPrayerBook" in packet_handler,
            "Client should handle the server prayer-book swap packet")
    require("sendPrayerBook" in action_sender and "PrayerCatalog.getBookId" in action_sender,
            "Server should expose an ActionSender prayer-book packet")
    require("SEND_PRAYER_BOOK, 139" in custom_generator,
            "Custom protocol should reserve opcode 139 for prayer-book swaps")
    for family in [
        "magic-power", "melee-protection", "melee-power",
        "ranged-protection", "ranged-power", "magic-protection",
        "enchanting-xp", "smithing-xp", "crafting-xp",
    ]:
        for tier in range(1, 6):
            icon_path = PRAYER_ASSETS / f"{family}{tier}.png"
            require(icon_path.exists(), f"Missing prayer icon asset: {icon_path.name}")
            require(png_size(icon_path) == (512, 512),
                    f"Unexpected prayer icon geometry: {icon_path.name}")

    print("prayer UI checks passed")


if __name__ == "__main__":
    main()
