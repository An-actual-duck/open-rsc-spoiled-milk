#!/usr/bin/env python3

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text()


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"missing {label}: {needle}")


def main() -> None:
    helper = read("server/plugins/com/openrsc/server/plugins/custom/quests/MyWorldQuestShortcuts.java")
    require(helper, 'ALREADY_DONE_OPTION = "I\'ve already done this quest"', "shortcut option text")
    require(helper, "Quest.LOST_CITY", "lost city reward hook")
    require(helper, "ItemId.DRAMEN_STAFF.id()", "lost city utility item")
    require(helper, "Quest.WATERFALL_QUEST", "waterfall reward hook")
    require(helper, "ItemId.GLARIALS_AMULET.id()", "waterfall utility item")
    require(helper, "Quest.FAMILY_CREST", "family crest reward hook")
    require(helper, 'player.getCache().set("famcrest_gauntlets"', "family crest cache")
    require(helper, "ItemId.STEEL_GAUNTLETS.id()", "family crest utility item")
    require(helper, "Quest.PLAGUE_CITY", "plague city reward hook")
    require(helper, "ItemId.MAGIC_SCROLL.id()", "plague city utility item")
    require(helper, "Quest.WATCHTOWER", "watchtower reward hook")
    require(helper, "ItemId.SPELL_SCROLL.id()", "watchtower utility item")
    require(helper, "Quest.GERTRUDES_CAT", "gertrudes cat reward hook")
    require(helper, "ItemId.KITTEN.id()", "gertrudes cat utility item")
    require(helper, "Quest.SEA_SLUG", "sea slug reward hook")
    require(helper, "ItemId.QUEST_OYSTER_PEARLS.id()", "sea slug utility item")
    require(helper, "Quest.DWARF_CANNON", "dwarf cannon reward hook")
    require(helper, "ItemId.DWARF_CANNON_BASE.id()", "dwarf cannon base")
    require(helper, "ItemId.CANNON_AMMO_MOULD.id()", "dwarf cannon mould")
    require(helper, "Quest.IMP_CATCHER", "imp catcher reward hook")
    require(helper, "ItemId.AMULET_OF_ACCURACY.id()", "imp catcher utility item")
    require(helper, "Quest.MONKS_FRIEND", "monks friend reward hook")
    require(helper, "ItemId.LAW_RUNE.id()", "monks friend runes")
    require(helper, "Quest.MURDER_MYSTERY", "murder mystery reward hook")
    require(helper, "ItemId.COINS.id(), 2000", "murder mystery coins")
    require(helper, "Quest.TREE_GNOME_VILLAGE", "tree gnome village reward hook")
    require(helper, "ItemId.EMERALD_AMULET.id()", "tree gnome village utility item")
    require(helper, "Quest.BIOHAZARD", "biohazard reward hook")
    require(helper, "Quest.UNDERGROUND_PASS", "underground pass line reward hook")
    require(helper, "ItemId.KING_LATHAS_AMULET.id()", "biohazard utility item")
    require(helper, "ItemId.STAFF_OF_IBAN.id()", "underground pass utility item")
    require(helper, 'player.getCache().set("Iban blast_casts"', "underground pass iban casts")
    require(helper, "Quest.OBSERVATORY_QUEST", "observatory reward hook")
    require(helper, "ItemId.UNCUT_SAPPHIRE.id(), 12", "observatory sapphire bundle")
    require(helper, "Quest.TOURIST_TRAP", "tourist trap reward hook")
    require(helper, "ItemId.WROUGHT_IRON_KEY.id()", "tourist trap utility item")
    require(helper, "Quest.LEGENDS_QUEST", "legends reward hook")
    require(helper, "grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.ATTACK, Skill.MELEE)", "legends melee remap")
    require(helper, "grantRemappedSkillReward(player, Quest.TOURIST_TRAP, Skill.FLETCHING, Skill.CRAFTING)", "tourist trap crafting remap")
    require(helper, "Quest.THE_HAZEEL_CULT", "hazeel reward hook")
    require(helper, 'clearHazeelCache(player)', "hazeel cache cleanup")
    require(helper, "Quest.TEMPLE_OF_IKOV", "ikov reward hook")
    require(helper, "player.updateQuestStage(Quest.TEMPLE_OF_IKOV.id(), -2)", "ikov lucien ending state")
    require(helper, 'clearTempleOfIkovCache(player)', "ikov cache cleanup")
    require(helper, "Quest.SHIELD_OF_ARRAV", "shield of arrav reward hook")
    require(helper, "ShieldOfArrav.BLACK_ARM_COMPLETE", "shield black arm completion state")
    require(helper, "ShieldOfArrav.PHOENIX_COMPLETE", "shield phoenix completion state")
    require(helper, "Quest.HEROS_QUEST", "heros quest reward hook")
    require(helper, "completeShieldOfArrav(player, npc, joinedBlackArmGang)", "heros shortcut shield backfill")
    require(helper, 'clearHerosQuestCache(player)', "heros cache cleanup")
    helper_methods = {
        "completeBlackKnightsFortress": "Quest.BLACK_KNIGHTS_FORTRESS",
        "completeCooksAssistant": "Quest.COOKS_ASSISTANT",
        "completeDemonSlayer": "Quest.DEMON_SLAYER",
        "completeDoricsQuest": "Quest.DORICS_QUEST",
        "completeDragonSlayer": "Quest.DRAGON_SLAYER",
        "completeErnestTheChicken": "Quest.ERNEST_THE_CHICKEN",
        "completeGoblinDiplomacy": "Quest.GOBLIN_DIPLOMACY",
        "completeKnightsSword": "Quest.THE_KNIGHTS_SWORD",
        "completePiratesTreasure": "Quest.PIRATES_TREASURE",
        "completePrinceAliRescue": "Quest.PRINCE_ALI_RESCUE",
        "completeRomeoAndJuliet": "Quest.ROMEO_N_JULIET",
        "completeSheepShearer": "Quest.SHEEP_SHEARER",
        "completeRestlessGhost": "Quest.THE_RESTLESS_GHOST",
        "completeVampireSlayer": "Quest.VAMPIRE_SLAYER",
        "completeWitchsPotion": "Quest.WITCHS_POTION",
        "completeClockTower": "Quest.CLOCK_TOWER",
        "completeDruidicRitual": "Quest.DRUIDIC_RITUAL",
        "completeFightArena": "Quest.FIGHT_ARENA",
        "completeFishingContest": "Quest.FISHING_CONTEST",
        "completeJunglePotion": "Quest.JUNGLE_POTION",
        "completeMerlinsCrystal": "Quest.MERLINS_CRYSTAL",
        "completeScorpionCatcher": "Quest.SCORPION_CATCHER",
        "completeSheepHerder": "Quest.SHEEP_HERDER",
        "completeHolyGrail": "Quest.THE_HOLY_GRAIL",
        "completeTribalTotem": "Quest.TRIBAL_TOTEM",
        "completeWitchesHouse": "Quest.WITCHS_HOUSE",
        "completeDigsite": "Quest.DIGSITE",
        "completeGrandTree": "Quest.GRAND_TREE",
        "completeShiloVillage": "Quest.SHILO_VILLAGE",
        "completeUndergroundPass": "Quest.UNDERGROUND_PASS",
        "completePeelingTheOnion": "Quest.PEELING_THE_ONION",
    }
    for method, quest_constant in helper_methods.items():
        require(helper, f"public static void {method}", f"{method} helper")
        require(helper, quest_constant, f"{method} quest reward hook")

    starters = [
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/BlackKnightsFortress.java", "completeBlackKnightsFortress"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/CooksAssistant.java", "completeCooksAssistant"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/DemonSlayer.java", "completeDemonSlayer"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/Dorics.java", "completeDoricsQuest"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/DragonSlayer.java", "completeDragonSlayer"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/ErnestTheChicken.java", "completeErnestTheChicken"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/GoblinDiplomacy.java", "completeGoblinDiplomacy"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/KnightsSword.java", "completeKnightsSword"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/PiratesTreasure.java", "completePiratesTreasure"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/PrinceAliRescue.java", "completePrinceAliRescue"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/RomeoAndJuliet.java", "completeRomeoAndJuliet"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/SheepShearer.java", "completeSheepShearer"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/TheRestlessGhost.java", "completeRestlessGhost"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/VampireSlayer.java", "completeVampireSlayer"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/WitchesPotion.java", "completeWitchsPotion"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/LostCity.java", "completeLostCity"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/Waterfall_Quest.java", "completeWaterfallQuest"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/FamilyCrest.java", "completeFamilyCrest"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/PlagueCity.java", "completePlagueCity"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/ClockTower.java", "completeClockTower"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/DruidicRitual.java", "completeDruidicRitual"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/FightArena.java", "completeFightArena"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/FishingContest.java", "completeFishingContest"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/watchtower/WatchTowerDialogues.java", "completeWatchtower"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/Jungle_Potion.java", "completeJunglePotion"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/MerlinsCrystal.java", "completeHolyGrail"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/MerlinsCrystal.java", "completeMerlinsCrystal"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/ScorpionCatcher.java", "completeScorpionCatcher"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/SheepHerder.java", "completeSheepHerder"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/TribalTotem.java", "completeTribalTotem"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/WitchesHouse.java", "completeWitchesHouse"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/GertrudesCat.java", "completeGertrudesCat"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/SeaSlug.java", "completeSeaSlug"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/digsite/DigsiteExaminer.java", "completeDigsite"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/grandtree/GrandTree.java", "completeGrandTree"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/shilovillage/ShiloVillageMosolRei.java", "completeShiloVillage"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/undergroundpass/npcs/UndergroundPassKoftik.java", "completeUndergroundPass"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/DwarfCannon.java", "completeDwarfCannon"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/ImpCatcher.java", "completeImpCatcher"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/MonksFriend.java", "completeMonksFriend"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/MurderMystery.java", "completeMurderMystery"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/TreeGnomeVillage.java", "completeTreeGnomeVillage"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/BioHazard.java", "completeBiohazardLine"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/Observatory.java", "completeObservatoryQuest"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/touristtrap/TouristTrap.java", "completeTouristTrap"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/LegendsQuestSirRadimusErkle.java", "completeLegendsQuest"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/HazeelCult.java", "completeHazeelCult"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/TempleOfIkov.java", "completeTempleOfIkov"),
        ("server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/Reldo.java", "completeShieldOfArrav"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/HerosQuest.java", "completeHerosQuest"),
        ("server/plugins/com/openrsc/server/plugins/custom/myworld/quests/free/PeelingTheOnion.java", "completePeelingTheOnion"),
    ]
    for rel, method in starters:
        text = read(rel)
        require(text, "MyWorldQuestShortcuts.ALREADY_DONE_OPTION", f"{rel} shortcut option")
        require(text, method, f"{rel} shortcut call")

    redundant_shortcut_say_sites = [
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/BlackKnightsFortress.java", "completeBlackKnightsFortress"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/CooksAssistant.java", "completeCooksAssistant"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/DemonSlayer.java", "completeDemonSlayer"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/Dorics.java", "completeDoricsQuest"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/DragonSlayer.java", "completeDragonSlayer"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/GoblinDiplomacy.java", "completeGoblinDiplomacy"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/KnightsSword.java", "completeKnightsSword"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/PiratesTreasure.java", "completePiratesTreasure"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/PrinceAliRescue.java", "completePrinceAliRescue"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/RomeoAndJuliet.java", "completeRomeoAndJuliet"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/SheepShearer.java", "completeSheepShearer"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/TheRestlessGhost.java", "completeRestlessGhost"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/VampireSlayer.java", "completeVampireSlayer"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/free/WitchesPotion.java", "completeWitchsPotion"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/ClockTower.java", "completeClockTower"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/DruidicRitual.java", "completeDruidicRitual"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/FightArena.java", "completeFightArena"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/FishingContest.java", "completeFishingContest"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/HerosQuest.java", "completeHerosQuest"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/LostCity.java", "completeLostCity"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/MerlinsCrystal.java", "completeHolyGrail"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/ScorpionCatcher.java", "completeScorpionCatcher"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/TempleOfIkov.java", "completeTempleOfIkov"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/Waterfall_Quest.java", "completeWaterfallQuest"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/WitchesHouse.java", "completeWitchesHouse"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/digsite/DigsiteExaminer.java", "completeDigsite"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/grandtree/GrandTree.java", "completeGrandTree"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/shilovillage/ShiloVillageMosolRei.java", "completeShiloVillage"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/undergroundpass/npcs/UndergroundPassKoftik.java", "completeUndergroundPass"),
        ("server/plugins/com/openrsc/server/plugins/authentic/quests/members/watchtower/WatchTowerDialogues.java", "completeWatchtower"),
        ("server/plugins/com/openrsc/server/plugins/custom/myworld/quests/free/PeelingTheOnion.java", "completePeelingTheOnion"),
    ]
    redundant_say = r"say\([^;]*(?:MyWorldQuestShortcuts\.ALREADY_DONE_OPTION|\"I've already done this quest\")[^;]*\);"
    for rel, method in redundant_shortcut_say_sites:
        text = read(rel)
        pattern = redundant_say + r"\s*MyWorldQuestShortcuts\." + method + r"\("
        if re.search(pattern, text):
            raise AssertionError(f"{rel} repeats the shortcut player line before {method}")

    audit = read("docs/myworld/work-items.md")
    require(audit, "MyWorld per-quest shortcut rollout", "quest audit rollout section")
    require(audit, "I've already done this quest", "quest audit shortcut text")
    require(audit, "`Gertrude's Cat`", "quest audit gertrudes cat rollout")
    require(audit, "`Sea Slug`", "quest audit sea slug rollout")
    require(audit, "`Dwarf Cannon`", "quest audit dwarf cannon rollout")
    require(audit, "`Imp Catcher`", "quest audit imp catcher rollout")
    require(audit, "`Monk's Friend`", "quest audit monks friend rollout")
    require(audit, "`Murder Mystery`", "quest audit murder mystery rollout")
    require(audit, "`Tree Gnome Village`", "quest audit tree gnome village rollout")
    require(audit, "`Biohazard`", "quest audit biohazard rollout")
    require(audit, "`Underground Pass`", "quest audit underground pass rollout")
    require(audit, "`Observatory`", "quest audit observatory rollout")
    require(audit, "`Tourist Trap`", "quest audit tourist trap rollout")
    require(audit, "`Legends' Quest`", "quest audit legends rollout")
    require(audit, "`Hazeel Cult`", "quest audit hazeel rollout")
    require(audit, "`Temple of Ikov`", "quest audit ikov rollout")
    require(audit, "`Shield of Arrav`", "quest audit shield rollout")
    require(audit, "`Hero's Quest`", "quest audit hero rollout")


if __name__ == "__main__":
    main()
