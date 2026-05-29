#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GUIDE = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "interfaces" / "misc" / "SkillGuideInterface.java"
CLIENT = ROOT / "Client_Base" / "src" / "orsc" / "mudclient.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def forbid(text: str, needle: str, description: str) -> None:
    if needle in text:
        fail(f"retired {description} is still present: {needle}")


def main() -> None:
    guide = GUIDE.read_text()
    client = CLIENT.read_text()

    require(guide, 'populateRangedGuide();', "ranged guide helper")
    require(guide, 'addRangedBowGuide("Blood", 70, 2130, 2129);', "blood bow ranged requirement")
    require(guide, 'addRangedCrossbowGuide("Blood", 70, 2176, "Rune bolts");', "blood crossbow bolt tier")
    require(guide, 'addThrownGuide("Orichalcum", 62, 2046, 2029, 2210);', "orichalcum thrown tier")

    require(client, 'skillGuideChosenTabs.add("Daggers");', "melee daggers tab")
    require(client, 'skillGuideChosenTabs.add("Swords");', "melee swords tab")
    require(client, 'skillGuideChosenTabs.add("Scimitars");', "melee scimitars tab")
    require(client, 'skillGuideChosenTabs.add("2-Handers");', "melee 2-handers tab")
    require(client, 'skillGuideChosenTabs.add("Battleaxes");', "melee battleaxes tab")
    require(client, 'skillGuideChosenTabs.add("Maces");', "melee maces tab")
    require(client, 'skillGuideChosenTabs.add("Spears");', "melee spears tab")
    require(guide, 'addMeleeTierGuide("Tin dagger", 1, 1995);', "tin dagger melee guide")
    require(guide, 'addMeleeTierGuide("Copper long sword", 8, 2009);', "copper sword melee guide")
    require(guide, 'addMeleeTierGuide("Titan steel long sword", 46, 2020);', "titan steel sword melee guide")
    require(guide, 'addMeleeTierGuide("Orichalcum spear", 62, 2210);', "orichalcum spear melee guide")
    require(guide, 'addMeleeTierGuide("Rune spear", 70, 1092);', "rune spear melee guide")

    require(guide, 'populatePrayerGuide();', "prayer guide helper")
    require(guide, 'addPrayerLine("Magic Power", "5 tiers of magic damage bonuses");', "saradomin prayer summary")
    require(guide, 'addPrayerLine("Smithing Favor", "5 tiers of smithing XP bonuses");', "zamorak prayer summary")
    require(guide, 'addPrayerLine("Crafting Favor", "5 tiers of crafting XP bonuses");', "guthix prayer summary")
    require(guide, 'new SkillMenuItem(1214, "70", "Saradomin Cape - requires Saradomin worship")', "saradomin gear prayer guide")
    require(guide, 'new SkillMenuItem(2237, "70", "Blessed Blood Staff - requires Prayer and Magic")', "blessed blood staff prayer guide")
    require(guide, 'Devotion: every 10 offerings to a god adds +1 Prayer XP', "devotion prayer guide")

    require(guide, 'populateMiningGuide();', "mining guide helper")
    require(guide, 'new SkillMenuItem(1987, "1", "Tin Pickaxe")', "tin pickaxe guide")
    require(guide, 'new SkillMenuItem(2048, "46", "Titan steel Pickaxe")', "titan steel pickaxe guide")
    require(guide, 'new SkillMenuItem(2049, "62", "Orichalcum Pickaxe")', "orichalcum pickaxe guide")
    require(guide, 'new SkillMenuItem(1299, "1", "Stone")', "stone mining guide")
    require(guide, 'new SkillMenuItem(150, "8", "Copper Ore")', "copper ore guide")
    require(guide, 'new SkillMenuItem(155, "22", "Coal")', "coal guide")
    require(guide, 'new SkillMenuItem(153, "38", "Mithril Ore")', "mithril ore guide")
    require(guide, 'new SkillMenuItem(154, "54", "Adamantite Ore")', "adamantite ore guide")
    require(guide, 'new SkillMenuItem(409, "70", "Runite Ore")', "runite ore guide")

    require(guide, 'addAmmoMouldGuide("Tin", 1, 2004, 2039, 2043, 1996);', "tin ammo crafting guide")
    require(guide, 'addAmmoMouldGuide("Rune", 70, 674, 646, 1070, 1080);', "rune ammo crafting guide")
    require(guide, 'addWoodcraftGuide("Pine", 15, 8, 2116, 12, 2115, 15, 59, 16, 2131, 8);', "pine woodcraft recipe guide")
    require(guide, 'addWoodcraftGuide("Blood", 55, 70, 2128, 67, 2127, 70, 2176, 72, 2146, 70);', "blood woodcraft recipe guide")
    require(guide, 'new SkillMenuItem(825, "62", "Soul Rune")', "soul rune guide item id")

    require(client, 'skillGuideChosenTabs.add("MyWorld");', "retired fletching guide tab replacement")
    require(client, 'skillGuideChosenTabs.add("Ammo");', "crafting ammo tab")
    require(client, 'skillGuideChosenTabs.add("Woodcraft");', "crafting woodcraft tab")
    require(client, 'skillGuideChosenTabs.add("Rods");', "fishing rod tab")
    require(client, 'skillGuideChosenTabs.add("Hatchets");', "woodcutting hatchet tab")
    require(guide, 'name.equals("Spore") || name.equals("Wood Drill") || name.equals("Battering Ram")', "wood splinter guide mapping")
    require(guide, 'return "Splinter";', "wood splinter guide label")
    require(client, 'spellName.equals("spore") || spellName.equals("wood drill")', "wood splinter tooltip mapping")
    require(client, 'return "Splinter";', "wood splinter tooltip label")

    require(guide, 'new SkillMenuItem(349, "1", "Shrimp - Fishing Rod (T1)")', "fishing shrimp rod guide")
    require(guide, 'new SkillMenuItem(366, "38", "Tuna - Maple Fishing Rod (T6)")', "fishing tuna rod guide")
    require(guide, 'new SkillMenuItem(545, "70", "Shark - Blood Fishing Rod (T10)")', "fishing shark rod guide")
    require(guide, 'new SkillMenuItem(2686, "38", "Maple Fishing Rod - T6, +25 effective Fishing")', "maple fishing rod guide")
    require(guide, 'new SkillMenuItem(2690, "70", "Blood Fishing Rod - T10, +45 effective Fishing")', "blood fishing rod guide")
    require(guide, 'new SkillMenuItem(377, "", "Rods catch their tier and 3 tiers below")', "fishing rod catch window guide")
    require(guide, 'new SkillMenuItem(439, "", "Herb rolls use shears tier and 3 tiers below")', "harvesting herb roll tier window guide")
    require(guide, 'new SkillMenuItem(2328, "1", "Gnome Stronghold Course - Tier 1 pouch per lap")', "gnome agility pouch guide")
    require(guide, 'new SkillMenuItem(2329, "35", "Barbarian Outpost Course - Tier 2 pouch per lap")', "barbarian agility pouch guide")
    require(guide, 'new SkillMenuItem(2330, "52", "Wilderness Course - Tier 3 pouch per lap")', "wilderness agility pouch guide")
    require(guide, 'new SkillMenuItem(350, "", "Shrimp - Heals 3")', "shrimp heal guide")
    require(guide, 'new SkillMenuItem(352, "", "Anchovies - Heals 1")', "anchovies heal guide")
    require(guide, 'new SkillMenuItem(765, "", "Dwellberries - Heals 2")', "dwellberries heal guide")
    require(guide, 'new SkillMenuItem(210, "", "Kebab - Variable heal or effect")', "variable kebab guide")
    require(guide, 'new SkillMenuItem(923, "", "Ugthanki Kebab - Heals 19")', "ugthanki kebab heal guide")
    require(guide, 'new SkillMenuItem(1493, "", "White pumpkin pie - Heals 16 (8 per slice)")', "white pumpkin pie heal guide")
    require(guide, 'new SkillMenuItem(546, "64", "Shark")', "shark cooking guide")
    require(guide, 'new SkillMenuItem(1191, "70", "Manta Ray")', "manta ray cooking guide")
    require(guide, 'new SkillMenuItem(325, "32", "Plain Pizza")', "plain pizza cooking guide")
    require(guide, 'new SkillMenuItem(1490, "68", "Pumpkin pie")', "pumpkin pie cooking guide")
    require(guide, 'new SkillMenuItem(1493, "68", "White pumpkin pie")', "white pumpkin pie cooking guide")
    require(guide, 'new SkillMenuItem(1463, "67", "Seaweed Soup")', "seaweed soup cooking guide")

    forbid(guide, 'new SkillMenuItem(424, "10", "Black")', "old black melee guide level")
    forbid(guide, 'new SkillMenuItem(150, "1", "Copper Ore")', "old copper mining level")
    forbid(guide, 'new SkillMenuItem(155, "30", "Coal")', "old coal mining level")
    forbid(guide, 'new SkillMenuItem(153, "55", "Mithril Ore")', "old mithril mining level")
    forbid(guide, 'new SkillMenuItem(154, "70", "Adamantite Ore")', "old adamantite mining level")
    forbid(guide, 'new SkillMenuItem(409, "85", "Runite Ore")', "old runite mining level")
    forbid(guide, 'new SkillMenuItem(156, "1", "Bronze Pickaxe")', "old bronze pickaxe level line")
    forbid(guide, 'All throwing knives', "old ranged thrown summary")
    forbid(guide, 'Thick skin - Increases your defense by 5%', "old prayer guide")
    forbid(guide, 'Staff of Air', "legacy staff wording")
    forbid(guide, 'Gold Crown', "retired crown guide")
    forbid(guide, 'Battlestaves', "retired battlestaff guide")
    forbid(guide, 'Small Fishing Net', "old fishing net guide")
    forbid(guide, 'Fishing Rod and Bait', "old fishing bait guide")
    forbid(guide, 'Fly Fishing Rod and Feathers', "old fly fishing guide")
    forbid(guide, 'Big Fishing Net', "old big net guide")
    forbid(guide, 'Harpoon', "old harpoon guide")
    forbid(guide, 'Shrimp - Heals 2', "old shrimp heal guide")
    forbid(guide, 'Anchovies - Heals 2', "old anchovies heal guide")
    forbid(guide, 'Kebab - ???', "unknown kebab guide")
    forbid(guide, 'Ugthanki Kebab - ???', "unknown ugthanki kebab guide")
    forbid(guide, 'new SkillMenuItem(1492, "", "White pumkpin pie', "wrong white pumpkin pie heal id")
    forbid(guide, 'new SkillMenuItem(1492, "80", "White pumpkin pie")', "wrong white pumpkin pie cooking id")
    forbid(guide, 'new SkillMenuItem(546, "80", "Shark")', "old shark cooking level")
    forbid(guide, 'new SkillMenuItem(1191, "91", "Manta Ray")', "old manta ray cooking level")
    forbid(guide, 'new SkillMenuItem(325, "35", "Plain Pizza")', "old plain pizza cooking level")
    forbid(client, 'skillGuideChosenTabs.add("Axes");', "old woodcutting axes tab")

    print("PASS: MyWorld skill guides reflect current tiers and recipes")


if __name__ == "__main__":
    main()
