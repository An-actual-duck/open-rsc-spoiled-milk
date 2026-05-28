package com.openrsc.server.plugins.custom.quests;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.external.Gauntlets;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.authentic.quests.free.ShieldOfArrav;
import com.openrsc.server.plugins.shared.constants.Quest;
import com.openrsc.server.plugins.shared.model.QuestReward;
import com.openrsc.server.plugins.shared.model.XPReward;

import static com.openrsc.server.plugins.Functions.incQP;
import static com.openrsc.server.plugins.Functions.incStat;
import static com.openrsc.server.plugins.Functions.mes;
import static com.openrsc.server.plugins.Functions.npcsay;

public final class MyWorldQuestShortcuts {

	public static final String ALREADY_DONE_OPTION = "I've already done this quest";
	public static final String BLACK_ARM_GANG_OPTION = "I joined the Black Arm Gang";
	public static final String PHOENIX_GANG_OPTION = "I joined the Phoenix Gang";

	private MyWorldQuestShortcuts() {
	}

	public static void completeLostCity(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already been through all that trouble,",
			"I can mark the matter as settled for you.");
		completeQuestWithStandardReward(player, Quest.LOST_CITY,
			"Well done you have completed the Lost City of Zanaris quest");
		ensureUtilityItem(player, ItemId.DRAMEN_STAFF.id(), 1);
	}

	public static void completeBlackKnightsFortress(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.BLACK_KNIGHTS_FORTRESS,
			"If you've already sabotaged the black knights,",
			"then I'll record your service and pay the reward.",
			"Well done. You have completed the Black knights fortress quest");
	}

	public static void completeCooksAssistant(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.COOKS_ASSISTANT,
			"If you've already saved the banquet,",
			"then I can mark the kitchen crisis settled.",
			"Well done you have completed the cook's assistant quest");
	}

	public static void completeDemonSlayer(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.DEMON_SLAYER,
			"If Delrith has already been banished,",
			"then Varrock will recognise the deed.",
			"Well done you have completed the Demon Slayer quest");
	}

	public static void completeDoricsQuest(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.DORICS_QUEST,
			"If you've already brought me the ore,",
			"then I'll count the work done.",
			"Well done you have completed Doric's quest");
	}

	public static void completeDragonSlayer(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.DRAGON_SLAYER,
			"If Elvarg has already fallen,",
			"then I'll recognise you as a dragon slayer.",
			"Congratulations, you have completed the Dragon Slayer quest");
	}

	public static void completeErnestTheChicken(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.ERNEST_THE_CHICKEN,
			"If Ernest has already been restored,",
			"then I'll record the experiment as repaired.",
			"Well done you have completed the Ernest the Chicken quest");
	}

	public static void completeGoblinDiplomacy(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.GOBLIN_DIPLOMACY,
			"If you've already settled the goblins' colour argument,",
			"then I can close the matter.",
			"Well done you have completed the Goblin Diplomacy quest");
	}

	public static void completeKnightsSword(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.THE_KNIGHTS_SWORD,
			"If you've already replaced the lost sword,",
			"then I'll mark the squire's problem solved.",
			"Well done you have completed the Knight's Sword quest");
	}

	public static void completePiratesTreasure(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.PIRATES_TREASURE,
			"If you've already found the pirate treasure,",
			"then I'll call that adventure complete.",
			"Well done you have completed the Pirate's Treasure quest");
	}

	public static void completePrinceAliRescue(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.PRINCE_ALI_RESCUE,
			"If Prince Ali is already free,",
			"then Al Kharid will honour your work.",
			"Well done you have completed the Prince Ali Rescue quest");
	}

	public static void completeRomeoAndJuliet(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.ROMEO_N_JULIET,
			"If Juliet's plan has already played out,",
			"then I'll record the matter complete.",
			"Well done you have completed the Romeo and Juliet quest");
	}

	public static void completeSheepShearer(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.SHEEP_SHEARER,
			"If you've already delivered the wool,",
			"then the job is done.",
			"Well done you have completed the Sheep Shearer quest");
	}

	public static void completeRestlessGhost(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.THE_RESTLESS_GHOST,
			"If the ghost is already at rest,",
			"then I'll mark the churchyard quiet again.",
			"Well done you have completed the Restless Ghost quest");
	}

	public static void completeVampireSlayer(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.VAMPIRE_SLAYER,
			"If Count Draynor is already destroyed,",
			"then Draynor can sleep easier.",
			"Well done you have completed the Vampire Slayer quest");
	}

	public static void completeWitchsPotion(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.WITCHS_POTION,
			"If you've already helped with the potion,",
			"then I'll count the magic done.",
			"Well done you have completed the Witch's Potion quest");
	}

	public static void completeWaterfallQuest(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already sorted out the waterfall business,",
			"then I'll trust your word and wrap it up from here.");
		clearWaterfallCache(player);
		completeQuestWithStandardReward(player, Quest.WATERFALL_QUEST,
			"you have completed the Baxtorian waterfall quest");
		giveOrBank(player, ItemId.MITHRIL_SEED.id(), 40);
		giveOrBank(player, ItemId.GOLD_BAR.id(), 2);
		giveOrBank(player, ItemId.DIAMOND.id(), 2);
		ensureUtilityItem(player, ItemId.GLARIALS_AMULET.id(), 1);
	}

	public static void completeFamilyCrest(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already recovered the crest,",
			"then I shall recognise your deed at once.");
		player.getCache().remove("north_leverA");
		player.getCache().remove("south_lever");
		player.getCache().remove("north_leverB");
		completeQuestWithStandardReward(player, Quest.FAMILY_CREST,
			"Well done you have completed the family crest quest");
		player.getCache().set("famcrest_gauntlets", Gauntlets.STEEL.id());
		ensureUtilityItem(player, ItemId.STEEL_GAUNTLETS.id(), 1);
	}

	public static void completePlagueCity(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If Elena is already safe,",
			"then I'll make good on the reward right now.");
		completeQuestWithStandardReward(player, Quest.PLAGUE_CITY,
			"Well done you have completed the plague city quest");
		ensureUtilityItem(player, ItemId.MAGIC_SCROLL.id(), 1);
		player.message("This story is to be continued");
	}

	public static void completeClockTower(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.CLOCK_TOWER,
			"If you've already repaired the clock tower,",
			"then I'll mark the mechanism restored.",
			"Well done you have completed the Clock Tower quest");
	}

	public static void completeDruidicRitual(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.DRUIDIC_RITUAL,
			"If you've already completed the ritual,",
			"then herblaw training is yours.",
			"Well done you have completed the Druidic Ritual quest");
	}

	public static void completeFightArena(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.FIGHT_ARENA,
			"If the arena slaves are already free,",
			"then I'll honour that victory.",
			"Well done you have completed the Fight Arena quest");
	}

	public static void completeFishingContest(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.FISHING_CONTEST,
			"If you've already won the contest,",
			"then the pass is yours to claim.",
			"Well done you have completed the Fishing Contest quest");
	}

	public static void completeWatchtower(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If the tower has already been restored,",
			"then take my thanks and the reward you earned.");
		completeQuestWithStandardReward(player, Quest.WATCHTOWER, null);
		player.message("The wizard gives you 5000 pieces of gold");
		giveOrBank(player, ItemId.COINS.id(), 5000);
		player.message("The wizard lays his hands on you...");
		player.message("You feel magic power increasing");
		ensureUtilityItem(player, ItemId.SPELL_SCROLL.id(), 1);
		player.message("Congratulations, you have finished the watchtower quest");
	}

	public static void completeJunglePotion(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.JUNGLE_POTION,
			"If you've already gathered the jungle herbs,",
			"then I'll mark the potion work complete.",
			"Well done you have completed the Jungle Potion quest");
	}

	public static void completeMerlinsCrystal(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.MERLINS_CRYSTAL,
			"If Merlin has already been freed,",
			"then Camelot will recognise the rescue.",
			"Well done you have completed the Merlin's Crystal quest");
	}

	public static void completeScorpionCatcher(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.SCORPION_CATCHER,
			"If you've already recovered the scorpions,",
			"then I'll count the task complete.",
			"Well done you have completed the Scorpion Catcher quest");
	}

	public static void completeSheepHerder(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already dealt with the infected sheep,",
			"then Ardougne will pay what you earned.");
		completeQuestWithStandardReward(player, Quest.SHEEP_HERDER,
			"Well done you have completed the Sheep Herder quest");
		giveOrBank(player, ItemId.COINS.id(), 3100);
	}

	public static void completeHolyGrail(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.THE_HOLY_GRAIL,
			"If you've already recovered the grail,",
			"then Camelot will record the holy quest complete.",
			"Well done you have completed the Holy Grail quest");
	}

	public static void completeTribalTotem(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.TRIBAL_TOTEM,
			"If you've already returned the totem,",
			"then I'll consider the matter settled.",
			"Well done you have completed the Tribal Totem quest");
	}

	public static void completeWitchesHouse(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.WITCHS_HOUSE,
			"If you've already recovered the ball,",
			"then the boy can stop worrying.",
			"Well done you have completed the Witch's House quest");
	}

	public static void completeGertrudesCat(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already brought Fluffs and the kittens home,",
			"then I'll trust you and sort the matter out now.");
		completeQuestWithStandardReward(player, Quest.GERTRUDES_CAT,
			"well done, you have completed gertrudes cat quest");
		ensureUtilityItem(player, ItemId.KITTEN.id(), 1);
	}

	public static void completeSeaSlug(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already rescued Kent and Kennith,",
			"then take the reward you earned.");
		completeQuestWithStandardReward(player, Quest.SEA_SLUG,
			"well done, you have completed the sea slug quest");
		ensureUtilityItem(player, ItemId.QUEST_OYSTER_PEARLS.id(), 1);
	}

	public static void completeDigsite(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already finished the digsite investigation,",
			"then I'll record your qualification and reward.");
		completeQuestWithStandardReward(player, Quest.DIGSITE,
			"Well done you have completed the Digsite quest");
		giveOrBank(player, ItemId.GOLD_BAR.id(), 2);
	}

	public static void completeGrandTree(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.GRAND_TREE,
			"If you've already saved the grand tree,",
			"then the gnomes will honour your work.",
			"Well done you have completed the Grand Tree quest");
	}

	public static void completeShiloVillage(final Player player, final Npc npc) {
		completeStandardShortcut(player, npc, Quest.SHILO_VILLAGE,
			"If you've already freed Shilo Village from the curse,",
			"then I'll mark the village open to you.",
			"Well done you have completed the Shilo Village quest");
	}

	public static void completeDwarfCannon(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already sorted out the black guard's troubles,",
			"then I'll authorise your cannon issue right away.");
		clearCannonCache(player);
		completeQuestWithStandardReward(player, Quest.DWARF_CANNON,
			"you have completed the dwarf cannon quest");
		ensureUtilityItem(player, ItemId.DWARF_CANNON_BASE.id(), 1);
		ensureUtilityItem(player, ItemId.DWARF_CANNON_STAND.id(), 1);
		ensureUtilityItem(player, ItemId.DWARF_CANNON_BARRELS.id(), 1);
		ensureUtilityItem(player, ItemId.DWARF_CANNON_FURNACE.id(), 1);
		ensureUtilityItem(player, ItemId.CANNON_AMMO_MOULD.id(), 1);
		ensureUtilityItem(player, ItemId.INSTRUCTION_MANUAL.id(), 1);
	}

	public static void completeImpCatcher(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already chased those imps all over the kingdom,",
			"then I'll hand over the reward straight away.");
		completeQuestWithStandardReward(player, Quest.IMP_CATCHER,
			"Well done. You have completed the Imp catcher quest");
		ensureUtilityItem(player, ItemId.AMULET_OF_ACCURACY.id(), 1);
	}

	public static void completeMonksFriend(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already recovered the blanket and sobered Cedric up,",
			"then the brothers can count the whole business settled.");
		completeQuestWithStandardReward(player, Quest.MONKS_FRIEND,
			"Well done you have completed the monks friend quest");
		giveOrBank(player, ItemId.LAW_RUNE.id(), 8);
	}

	public static void completeMurderMystery(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already solved the Sinclair murder,",
			"then we'll close the case and pay the reward.");
		clearMurderMysteryCache(player);
		completeQuestWithStandardReward(player, Quest.MURDER_MYSTERY,
			"You have completed the Murder Mystery Quest");
		giveOrBank(player, ItemId.COINS.id(), 2000);
	}

	public static void completeTreeGnomeVillage(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already recovered the orb of protection,",
			"then the gnome village will honour that deed at once.");
		clearTreeGnomeCache(player);
		completeQuestWithStandardReward(player, Quest.TREE_GNOME_VILLAGE,
			"Well done you have completed the treequest");
		ensureUtilityItem(player, ItemId.EMERALD_AMULET.id(), 1);
	}

	public static void completeBiohazardLine(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already uncovered the plague hoax and broken through the pass,",
			"then I'll treat the whole line as settled.");
		clearBiohazardCache(player);
		clearUndergroundPassCache(player);
		completeQuestWithStandardReward(player, Quest.BIOHAZARD,
			"you have completed the biohazard quest");
		ensureUtilityItem(player, ItemId.KING_LATHAS_AMULET.id(), 1);
		completeUndergroundPassReward(player);
	}

	public static void completeUndergroundPass(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already survived the underground pass,",
			"then I'll record Iban defeated and restore the useful tools.");
		clearUndergroundPassCache(player);
		completeUndergroundPassReward(player);
	}

	public static void completeObservatoryQuest(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already repaired the telescope and read every constellation,",
			"then I'll award the full set of star-sign rewards now.");
		completeQuestWithStandardReward(player, Quest.OBSERVATORY_QUEST, null);
		clearObservatoryCache(player);
		grantObservatoryConstellationRewards(player);
		ActionSender.sendStats(player);
	}

	public static void completeTouristTrap(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already rescued Ana from the desert trouble,",
			"then I'll settle the reward from here.");
		clearTouristTrapCache(player);
		completeQuestWithStandardReward(player, Quest.TOURIST_TRAP, null);
		grantRemappedSkillReward(player, Quest.TOURIST_TRAP, Skill.FLETCHING, Skill.CRAFTING);
		grantRemappedSkillReward(player, Quest.TOURIST_TRAP, Skill.AGILITY, Skill.AGILITY);
		grantRemappedSkillReward(player, Quest.TOURIST_TRAP, Skill.SMITHING, Skill.SMITHING);
		grantRemappedSkillReward(player, Quest.TOURIST_TRAP, Skill.THIEVING, Skill.THIEVING);
		ensureUtilityItem(player, ItemId.WROUGHT_IRON_KEY.id(), 1);
		ActionSender.sendStats(player);
	}

	public static void completeLegendsQuest(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already earned your place among the legends,",
			"then I'll recognise the deed and arrange the full training package.");
		clearLegendsCache(player);
		completeQuestWithStandardReward(player, Quest.LEGENDS_QUEST,
			"@gre@Well done - you have completed the Legends Guild Quest!");
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.ATTACK, Skill.MELEE);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.DEFENSE, Skill.MELEE);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.STRENGTH, Skill.MELEE);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.HITS, Skill.HITS);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.PRAYER, Skill.PRAYER);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.MAGIC, Skill.MAGIC);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.WOODCUTTING, Skill.WOODCUTTING);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.CRAFTING, Skill.CRAFTING);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.SMITHING, Skill.SMITHING);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.HERBLAW, Skill.HERBLAW);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.AGILITY, Skill.AGILITY);
		grantRemappedSkillReward(player, Quest.LEGENDS_QUEST, Skill.THIEVING, Skill.THIEVING);
		ActionSender.sendStats(player);
	}

	public static void completeHazeelCult(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If you've already untangled the cult business,",
			"then I'll settle the lasting reward and leave the rest buried.");
		clearHazeelCache(player);
		completeQuestWithStandardReward(player, Quest.THE_HAZEEL_CULT,
			"Well done you have completed the Hazeel cult quest");
		giveOrBank(player, ItemId.COINS.id(), 2000);
	}

	public static void completeTempleOfIkov(final Player player, final Npc npc, final boolean sidedWithLucien) {
		npcsay(player, npc,
			"If you've already dealt with the temple and its guardians,",
			"then I'll mark the matter complete from your account.");
		clearTempleOfIkovCache(player);
		completeQuestWithStandardReward(player, Quest.TEMPLE_OF_IKOV,
			"Well done you have completed the temple of Ikov quest");
		if (sidedWithLucien) {
			player.updateQuestStage(Quest.TEMPLE_OF_IKOV.id(), -2);
		}
	}

	public static void completeShieldOfArrav(final Player player, final Npc npc, final boolean joinedBlackArmGang) {
		npcsay(player, npc,
			"If you've already recovered the shield and settled the gang affair,",
			"then I'll record the outcome for the palace archives.");
		clearShieldOfArravCache(player);
		completeQuestWithStandardReward(player, Quest.SHIELD_OF_ARRAV,
			"Well done, you have completed the shield of Arrav quest");
		giveOrBank(player, ItemId.COINS.id(), 600);
		player.updateQuestStage(Quest.SHIELD_OF_ARRAV.id(),
			joinedBlackArmGang ? ShieldOfArrav.BLACK_ARM_COMPLETE : ShieldOfArrav.PHOENIX_COMPLETE);
	}

	public static void completeHerosQuest(final Player player, final Npc npc, final Boolean joinedBlackArmGang) {
		npcsay(player, npc,
			"If you've already earned entry to the heroes' guild,",
			"then I'll honour the deed and settle the record now.");
		clearHerosQuestCache(player);
		if (player.getQuestStage(Quest.SHIELD_OF_ARRAV.id()) >= 0 && joinedBlackArmGang != null) {
			completeShieldOfArrav(player, npc, joinedBlackArmGang);
		}
		completeQuestWithStandardReward(player, Quest.HEROS_QUEST,
			"Well done you have completed the hero guild entry quest");
	}

	public static void completePeelingTheOnion(final Player player, final Npc npc) {
		npcsay(player, npc,
			"If Kresh already has his recipes,",
			"then I'll record the tower's mess as solved.");
		completeQuestWithStandardReward(player, Quest.PEELING_THE_ONION,
			"Well done you have completed the kresh quest");
		player.message("You now have access to new skin colours!");
		player.getCache().store("ogre_makeover_voucher", true);
		player.getCache().store("sedridor_post_kresh_quest_dialogue", true);
		if (player.getCache().hasKey("talkedToSedridorAsOgre")) {
			player.getCache().remove("talkedToSedridorAsOgre");
		}
		giveOrBank(player, ItemId.COINS.id(), 750);
	}

	private static void completeStandardShortcut(final Player player, final Npc npc, final Quest quest,
		final String firstNpcLine, final String secondNpcLine, final String completionMessage) {
		npcsay(player, npc, firstNpcLine, secondNpcLine);
		completeQuestWithStandardReward(player, quest, completionMessage);
	}

	private static void completeUndergroundPassReward(final Player player) {
		completeQuestWithStandardReward(player, Quest.UNDERGROUND_PASS,
			"you have completed the underground pass quest");
		player.getCache().set("Iban blast_casts", 25);
		ensureUtilityItem(player, ItemId.STAFF_OF_IBAN.id(), 1);
		giveOrBank(player, ItemId.DEATH_RUNE.id(), 15);
		giveOrBank(player, ItemId.FIRE_RUNE.id(), 30);
	}

	private static void completeQuestWithStandardReward(final Player player, final Quest quest, final String completionMessage) {
		final QuestReward reward = quest.reward();
		for (XPReward xpReward : reward.getXpRewards()) {
			incStat(player, xpReward.getSkill().id(), xpReward.getBaseXP(), xpReward.getVarXP());
		}
		incQP(player, reward.getQuestPoints(), !player.isUsingClientBeforeQP());
		player.updateQuestStage(quest.id(), -1);
		ActionSender.sendStats(player);
		if (completionMessage != null) {
			player.message(completionMessage);
		}
	}

	private static void ensureUtilityItem(final Player player, final int itemId, final int amount) {
		if (player.getBank().hasItemId(itemId) || player.getCarriedItems().hasCatalogID(itemId)) {
			return;
		}
		giveOrBank(player, itemId, amount);
	}

	private static void giveOrBank(final Player player, final int itemId, final int amount) {
		final Item item = new Item(itemId, amount);
		if (player.getCarriedItems().getInventory().canHold(item)) {
			player.getCarriedItems().getInventory().add(item);
			return;
		}
		if (player.getBank().canHold(item) && player.getBank().add(item, false)) {
			mes("Your bank holds the " + item.getDef(player.getWorld()).getName() + " for you.");
			return;
		}
		player.message("You have no room to receive " + item.getDef(player.getWorld()).getName() + ".");
	}

	private static void grantRemappedSkillReward(final Player player, final Quest quest, final Skill sourceSkill, final Skill targetSkill) {
		final XPReward xpReward = quest.reward().getXpRewards()[0].copyTo(sourceSkill);
		incStat(player, targetSkill.id(), xpReward.getBaseXP(), xpReward.getVarXP());
	}

	private static void grantObservatoryConstellationRewards(final Player player) {
		grantRemappedSkillReward(player, Quest.OBSERVATORY_QUEST, Skill.DEFENSE, Skill.MELEE);
		giveOrBank(player, ItemId.LAW_RUNE.id(), 3);
		giveOrBank(player, ItemId.BLACK_2_HANDED_SWORD.id(), 1);
		giveOrBank(player, ItemId.TUNA.id(), 3);
		giveOrBank(player, ItemId.FULL_SUPER_STRENGTH_POTION.id(), 1);
		giveOrBank(player, ItemId.WATER_RUNE.id(), 25);
		giveOrBank(player, ItemId.WEAPON_POISON.id(), 1);
		grantRemappedSkillReward(player, Quest.OBSERVATORY_QUEST, Skill.ATTACK, Skill.MELEE);
		giveOrBank(player, ItemId.MAPLE_LONGBOW.id(), 1);
		grantRemappedSkillReward(player, Quest.OBSERVATORY_QUEST, Skill.HITS, Skill.HITS);
		grantRemappedSkillReward(player, Quest.OBSERVATORY_QUEST, Skill.STRENGTH, Skill.MELEE);
		giveOrBank(player, ItemId.EMERALD_AMULET.id(), 1);
		giveOrBank(player, ItemId.UNCUT_SAPPHIRE.id(), 12);
	}

	private static void clearWaterfallCache(final Player player) {
		for (int x = 473; x < 478; x++) {
			for (int y = 32; y < 34; y++) {
				final String key = "waterfall_" + x + "_" + y;
				if (player.getCache().hasKey(key)) {
					player.getCache().remove(key);
				}
			}
		}
	}

	private static void clearCannonCache(final Player player) {
		for (String key : new String[] {
			"railone", "railtwo", "railthree", "railfour", "railfive", "railsix",
			"grabed_dwarf_remains", "savedlollk", "cannon_complete", "spoken_nulodion",
			"has_cannon", "cannon_stage", "cannon_x", "cannon_y"
		}) {
			if (player.getCache().hasKey(key)) {
				player.getCache().remove(key);
			}
		}
	}

	private static void clearMurderMysteryCache(final Player player) {
		for (String key : new String[] {
			"thread", "evidence", "culprit", "poison_opt", "poison_opt2",
			"murder_david", "murder_anna", "murder_carol", "murder_bob",
			"murder_frank", "murder_eliz", "p_carol", "p_eliza", "p_anna",
			"p_frank", "p_bob", "p_david"
		}) {
			if (player.getCache().hasKey(key)) {
				player.getCache().remove(key);
			}
		}
	}

	private static void clearTreeGnomeCache(final Player player) {
		for (String key : new String[] {"looted_orbs_protect"}) {
			if (player.getCache().hasKey(key)) {
				player.getCache().remove(key);
			}
		}
	}

	private static void clearBiohazardCache(final Player player) {
		for (String key : new String[] {
			"wrong_vial_hops", "wrong_vial_chancy", "wrong_vial_vinci"
		}) {
			if (player.getCache().hasKey(key)) {
				player.getCache().remove(key);
			}
		}
	}

	private static void clearUndergroundPassCache(final Player player) {
		for (String key : new String[] {
			"advised_koftik", "orb_of_light1", "orb_of_light2", "orb_of_light3",
			"orb_of_light4", "stalagmite", "crate_food", "paladin_food",
			"brew_on_tomb", "rope_wall_grill", "flames_of_zamorak1",
			"flames_of_zamorak2", "flames_of_zamorak3", "doll_of_iban",
			"kardia_cat", "poison_on_doll", "cons_on_doll", "ash_on_doll",
			"shadow_on_doll"
		}) {
			if (player.getCache().hasKey(key)) {
				player.getCache().remove(key);
			}
		}
	}

	private static void clearObservatoryCache(final Player player) {
		if (player.getCache().hasKey("keep_key_gate")) {
			player.getCache().remove("keep_key_gate");
		}
		if (player.getCache().hasKey("observatory_assistant_drink")) {
			player.getCache().remove("observatory_assistant_drink");
		}
	}

	private static void clearTouristTrapCache(final Player player) {
		if (player.getCache().hasKey("advanced1")) {
			player.getCache().remove("advanced1");
		}
		if (player.getCache().hasKey("tried_ana_barrel")) {
			player.getCache().remove("tried_ana_barrel");
		}
	}

	private static void clearLegendsCache(final Player player) {
		for (String key : new String[] {
			"gujuo_potion", "JUNGLE_EAST", "JUNGLE_MIDDLE", "JUNGLE_WEST",
			"already_cast_holy_spell", "ran_from_2nd_nezi", "legends_choose_reward",
			"legends_reward_claimed", "ancient_wall_runes", "gave_glowing_dagger",
			"met_spirit", "cavernous_opening", "viyeldi_companions", "killed_viyeldi",
			"legends_wooden_beam", "rewarded_totem", "holy_water_neiz",
			"crafted_totem_pole"
		}) {
			if (player.getCache().hasKey(key)) {
				player.getCache().remove(key);
			}
		}
	}

	private static void clearHazeelCache(final Player player) {
		for (String key : new String[] {
			"good_side", "evil_side", "clivet_poison", "hazeel_poison",
			"poisoned_carnillean_food", "poisoned_scruffy_food", "use_hazeel_scroll",
			"hazeel_cupboard", "hazeel_crate", "hazeel_bookcase", "hazeel_chest"
		}) {
			if (player.getCache().hasKey(key)) {
				player.getCache().remove(key);
			}
		}
	}

	private static void clearTempleOfIkovCache(final Player player) {
		for (String key : new String[] {
			"openSpiderDoor", "completeLever", "killedLesarkus"
		}) {
			if (player.getCache().hasKey(key)) {
				player.getCache().remove(key);
			}
		}
	}

	private static void clearShieldOfArravCache(final Player player) {
		for (String key : new String[] {
			"read_arrav", "arrav_mission", "spoken_tramp"
		}) {
			if (player.getCache().hasKey(key)) {
				player.getCache().remove(key);
			}
		}
	}

	private static void clearHerosQuestCache(final Player player) {
		for (String key : new String[] {
			"talked_grip", "killed_grip", "looted_grip", "grip_keys",
			"hq_impersonate", "talked_alf", "talked_grubor", "blackarm_mission",
			"garv_door", "armband", "pheonix_mission", "pheonix_alf"
		}) {
			if (player.getCache().hasKey(key)) {
				player.getCache().remove(key);
			}
		}
	}
}
