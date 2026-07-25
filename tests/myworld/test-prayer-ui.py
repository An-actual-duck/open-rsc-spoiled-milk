#!/usr/bin/env python3
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[2]
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
PRAYER_BOOKS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/PrayerBookDefinitions.java"
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
    prayer_books = PRAYER_BOOKS.read_text(encoding="utf-8")
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
    require("prayerTooltipY = prayerGridY + prayerGridHeight + 14;" in mudclient,
            "Prayer icon detail text should use the compact vertical gap below the icon grid")
    require("int prayerTooltipY = magicPanelYStart + 122;" in mudclient,
            "Prayer text detail should share the compact description panel")
    require("Reserved points: \" + this.getPrayerPointCost(hoveredPrayer)" in mudclient,
            "Prayer tooltip should show the effective allocation cost instead of drain rate")
    require("int prayerLevel = this.playerStatCurrent.length > 5 ? this.playerStatCurrent[5] : 0;" in mudclient,
            "Prayer UI should base available allocation on current Prayer points")
    require("int prayerLevel = this.playerStatBase.length > 5 ? this.playerStatBase[5] : 0;" not in mudclient,
            "Prayer UI should not use max Worship level for available allocation")
    require("getCompactPrayerTierIconFile" in mudclient,
            "Prayer icon loader should support compact tier names like xp.png, xp2.png")
    require("isSinglePrayerIconAsset(assetName)" in mudclient
            and '"divine-grace".equals(assetName)' in mudclient
            and '"divine-retribution".equals(assetName)' in mudclient
            and '"corrosive-aura".equals(assetName)' in mudclient,
            "Prayer icon loader should support single-file special prayer icons")
    require('"dev/myworld/assets/sprites/UI/prayer"' in mudclient,
            "Prayer icon loader should search the current UI prayer asset folder")
    require("getExternalIconFile(assetName + tier)" in mudclient,
            "Prayer icon loader should support numbered tier names like magic-power1.png")
    for asset_name in ["enchanting-xp", "smithing-xp", "crafting-xp"]:
        require(f'return "{asset_name}";' in mudclient,
                f"Prayer icon loader should map the {asset_name} favor family")
    require('"Saving Grace".equalsIgnoreCase(baseName)' in mudclient
            and 'return "divine-grace";' in mudclient,
            "Prayer icon loader should keep Saving Grace mapped to the divine-grace asset")
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
        "Saving Grace",
        "Weak Melee Power",
        "Greater Ranged Protection",
        "Greater Smithing Favor",
        "Divine Retribution",
        "Weak Ranged Power",
        "Greater Magic Protection",
        "Greater Crafting Favor",
        "Corrosive Aura",
    ]
    for prayer in required_prayers:
        require(prayer in prayer_books, f"Missing client prayer definition: {prayer}")

    require(prayer_books.count("\t\taddPrayerDefinition(") == 45
            and prayer_books.count("\t\taddSpecialPrayerDefinition(") == 3,
            "Client prayer definitions should cover the three god lines plus all special prayers")
    for snippet in (
        'addPrayerDefinition(49, "Greater Magic Power", "Magic damage +25%.");',
        'addPrayerDefinition(49, "Greater Melee Power", "Melee damage +25%.");',
        'addPrayerDefinition(49, "Greater Ranged Power", "Ranged damage +25%.");',
        'addPrayerDefinition(80, "Greater Enchanting Favor", "Enchanting XP +30%.");',
        'addPrayerDefinition(80, "Greater Smithing Favor", "Smithing XP +30%.");',
        'addPrayerDefinition(80, "Greater Crafting Favor", "Crafting XP +30%.");',
        'addSpecialPrayerDefinition(60, "Saving Grace", "Chance to lifesteal 100% of attack damage. Lower HP is more likely to trigger.");',
        'addSpecialPrayerDefinition(60, "Divine Retribution", "Chance to recoil double damage taken. Higher hits are more likely to trigger.");',
        'addSpecialPrayerDefinition(60, "Corrosive Aura", "Enemies that damage you receive 10-50 poison stacks. Lower HP applies more stacks.");',
        '"Reserve " + pointCost + " prayer points. " + effectText',
    ):
        require(snippet in prayer_books, f"Client prayer tooltip cost missing: {snippet}")
    require('prayers.add(new PrayerDef(0, pointCost, name, effectText));' in prayer_books,
            "Special prayer descriptions should remain limited to their effects")
    require("cape" not in prayer_books.lower(),
            "Special prayer descriptions should not expose the god-cape discount")
    for snippet in (
        "private static final int SPECIAL_PRAYER_INDEX = 15;",
        "private static final int SPECIAL_PRAYER_NO_CAPE_COST_NUMERATOR = 3;",
        "private static final int SPECIAL_PRAYER_NO_CAPE_COST_DENOMINATOR = 2;",
        "private static final int ZAMORAK_CAPE_ID = 1213;",
        "private static final int SARADOMIN_CAPE_ID = 1214;",
        "private static final int GUTHIX_CAPE_ID = 1215;",
        "private int getPrayerPointCost(int prayerIndex)",
        "prayerIndex != SPECIAL_PRAYER_INDEX || this.hasMatchingGodPrayerCape()",
        "pointCost * SPECIAL_PRAYER_NO_CAPE_COST_NUMERATOR / SPECIAL_PRAYER_NO_CAPE_COST_DENOMINATOR",
        '"ZAMORAK".equals(prayerBook)',
        '"GUTHIX".equals(prayerBook)',
        "this.hasEquippedItem(ZAMORAK_CAPE_ID)",
        "this.hasEquippedItem(SARADOMIN_CAPE_ID)",
        "this.hasEquippedItem(GUTHIX_CAPE_ID)",
        '"Reserved points: " + this.getPrayerPointCost(hoveredPrayer)',
        'prayerDef.getName() + " [" + this.getPrayerPointCost(prayerIndex) + "]"',
        '"You need " + this.getPrayerPointCost(prayerIndex) + " free prayer points to activate this prayer"',
        "allocatedPoints += this.getPrayerPointCost(i);",
        "this.getAllocatedPrayerPoints() + this.getPrayerPointCost(prayerIndex)",
    ):
        require(snippet in mudclient, f"Effective special-prayer UI cost missing: {snippet}")
    for old_cost in (
        "Reserve 10 prayer points",
        "Reserve 15 prayer points",
        "Reserve 21 prayer points",
        "Reserve 8 prayer points",
        "Reserve 20 prayer points",
        "Reserve 35 prayer points",
        "Reserve 55 prayer points",
    ):
        require(old_cost not in prayer_books, f"Client prayer tooltip still has old cost: {old_cost}")
    require("public static void setPrayerBook(String prayerBook)" in entity_handler,
            "Client prayer definitions should support swapping visible prayer books")
    require('activePrayerBook = SARADOMIN' in prayer_books,
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

    for icon_name in ["divine-grace.png", "divine-retribution.png"]:
        icon_path = PRAYER_ASSETS / icon_name
        require(icon_path.exists(), f"Missing special prayer icon asset: {icon_name}")
        require(png_size(icon_path) == (512, 512),
                f"Unexpected special prayer icon geometry: {icon_name}")
    corrosive_icon = PRAYER_ASSETS / "corrosive-aura.png"
    require(corrosive_icon.exists(), "Missing special prayer icon asset: corrosive-aura.png")
    width, height = png_size(corrosive_icon)
    require(width > 0 and height > 0, "Unexpected special prayer icon geometry: corrosive-aura.png")

    print("prayer UI checks passed")


if __name__ == "__main__":
    main()
