package com.openrsc.interfaces.misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import orsc.SkillGuideWindow.Article;
import orsc.SkillGuideWindow.Topic;

/** Player-facing summaries of the server's EnchantingItemEffects and related effects. */
final class JewelryGuideTopics {
	private static final String XP_BONUS = "5%, 10%, 15%, 25%, 50%.";
	private static final String YIELD_BONUS = "10%, 20%, 30%, 50%, 100%";
	private static final String CHARGES = "Capacity 100, 200, 300, 500, 1,000 charges.";

	private JewelryGuideTopics() {
	}

	static Topic rings() {
		return topic("Ring enchantments",
			effect("Air - Archery", "Ranged power increases by 3, 6, 9, 12, 15."),
			effect("Water - Balance", "Melee, ranged, and magic power each increase by 2, 4, 6, 8, 10."),
			effect("Earth - Force", "Melee power increases by 3, 6, 9, 12, 15."),
			effect("Fire - Sorcery", "Magic power increases by 3, 6, 9, 12, 15."),
			effect("Life - Combat Command", "Combat summons gain health (" + percentages(10, 20, 30, 50, 100)
				+ ") and deal more damage (" + percentages(3, 6, 9, 15, 25) + ")."),
			effect("Mind - Hearthcraft", "Bonus Cooking, Herblaw, and Fishing XP of " + XP_BONUS),
			effect("Body - Gains", "Bonus Attack, Defense, Strength, and Hits XP of " + XP_BONUS),
			effect("Chaos - Recoil", "Reflect a quarter of the damage you take from an attack with a "
				+ "10%, 20%, 30%, 50%, 90% chance by gem."),
			effect("Cosmic - Fortune", "Reroll a failed rare monster drop roll with a 5%, 10%, 15%, 20%, 25% chance by gem."),
			effect("Nature - Nourishment", "Restores " + YIELD_BONUS + " more Hits from food."),
			effect("Law - Skill Banking", "Sends non-stackable gathered resources to your bank, using one charge per item. "
				+ CHARGES + " Recharge at the Law altar."),
			effect("Death - Reckoning", "NPC kills add 10 charge. Every 10 stored charge adds 1 damage against NPCs. "
				+ "Charge decays out of combat. The maximum is 20, 30, 40, 60, 100 by gem."),
			effect("Soul - Lifesaving", "Teleports you to your respawn point at 10% Hits or below where teleporting is allowed. "
				+ "The ring has a base survival chance of 10%, 20%, 30%, 50%, 90%."),
			effect("Blood - Vitality", "Maximum Hits increase by 2, 4, 6, 10, 20."));
	}

	static Topic bangles() {
		return topic("Bangle enchantments",
			effect("Air - Woodcutter's", "Woodcutting log yield increases by " + YIELD_BONUS + "."),
			effect("Water - Angler's", "Land the best fish available for your spot, rod, and Fishing level with a "
				+ "10%, 20%, 30%, 60%, 100% chance by gem."),
			effect("Earth - Harvester's", "Harvesting produce yield increases by " + YIELD_BONUS + "."),
			effect("Fire - Miner's", "Mining ore yield increases by " + YIELD_BONUS + "."),
			effect("Life - Utility Command", "Pack Rat and Delivery Camel gain 1, 2, 3, 5, 8 uses on top of their first use."),
			effect("Mind - Attunement", "Bonus Magic, Summoning, and Worship XP of " + XP_BONUS),
			effect("Body - Prowess", "Bonus Agility, Thieving, and Ranged XP of " + XP_BONUS),
			effect("Chaos - Chaos Weaving", "Crafting chaos runes adds a bonus yield of 20%, 35%, 50%, 70%, 100%. "
				+ "The bonus can contain mind, chaos, death, and blood runes."),
			effect("Cosmic - Bounty", "Double a rare gathering find with a 10%, 20%, 30%, 40%, 50% chance by gem."),
			effect("Nature - Alchemy", "Converts monster loot with a High Alchemy value of 1,000 coins or more into coins, "
				+ "using one charge per conversion. " + CHARGES + " Recharge at the Nature altar."),
			effect("Law - Teleportation", "Stores three teleports. Sapphire through Diamond travel to different guilds. "
				+ "Dragonstone travels to rune altars. Recharge at the Law altar."),
			effect("Death - Ruin", "NPC kills charge the bangle. When full, it damages nearby enemies within two tiles. "
				+ "The gem sets the damage range (1-3, 3-6, 6-9, 9-14, 10-20)."),
			effect("Soul - Renewal", "NPC kills charge the bangle. When full, it heals you and other players within two tiles. "
				+ "The gem sets the healing range (1-2, 1-3, 2-4, 3-6, 5-10 Hits)."),
			effect("Blood - Siphoning", "Heals you for 5%, 10%, 15%, 20%, 25% of the damage you deal with direct attacks."));
	}

	static Topic necklaces() {
		return topic("Necklace enchantments",
			effect("Air - Evasion", "Ranged defense increases by 3, 6, 9, 12, 15."),
			effect("Water - Equilibrium", "Melee, ranged, and magic defense each increase by 2, 4, 6, 8, 10."),
			effect("Earth - Bulwark", "Melee defense increases by 3, 6, 9, 12, 15."),
			effect("Fire - Warding", "Magic defense increases by 3, 6, 9, 12, 15."),
			effect("Life - Support Command", "Support summons go longer between upkeep payments ("
				+ percentages(10, 20, 30, 50, 100) + "). Each active minute they grant Summoning XP with a bonus of "
				+ percentages(50, 100, 150, 250, 400) + "."),
			effect("Mind - Artifice", "Bonus Crafting, Smithing, and Enchanting XP of " + XP_BONUS),
			effect("Body - Labor", "Bonus Harvesting, Mining, and Woodcutting XP of " + XP_BONUS),
			effect("Chaos - Chain Lightning", "NPC hits can arc to nearby enemies up to three times. Each jump deals half the previous "
				+ "hit's damage rounded up and has a 10%, 20%, 30%, 50%, 90% chance by gem."),
			effect("Cosmic - Fortune", "Get an extra standard monster loot roll with a 10%, 20%, 30%, 40%, 50% chance by gem."),
			effect("Nature - Cleansing", "Poison weakens by an extra 1, 2, 3, 4, 5 each tick."),
			effect("Law - Loot Banking", "Sends non-stackable monster loot to your bank, using one charge per item. "
				+ CHARGES + " Recharge at the Law altar."),
			effect("Death - Reaping", "Add one item to a guaranteed monster drop with a 25%, 40%, 60%, 90%, 100% chance by gem. "
				+ "Dragonstone also has a 10% chance to add a second item."),
			effect("Soul - Preservation", "Lets you keep 1, 2, 3, 5, 8 additional non-stackable items on death."),
			effect("Blood - Leach", "Heals you for 10%, 20%, 30%, 50%, 100% of the poison damage you deal."));
	}

	private static Topic topic(String label, Article... effects) {
		List<Article> articles = new ArrayList<>();
		articles.add(effect("Gem tiers", "Values are listed in gem order, starting with Sapphire, then Emerald, Ruby, Diamond, "
			+ "and Dragonstone. Each heading names the altar that gives the effect."));
		articles.addAll(Arrays.asList(effects));
		return new Topic(label, articles);
	}

	private static Article effect(String title, String detail) {
		return new Article(title, Collections.singletonList(detail));
	}

	private static String percentages(int sapphire, int emerald, int ruby, int diamond, int dragonstone) {
		return sapphire + "%, " + emerald + "%, " + ruby + "%, " + diamond + "%, " + dragonstone + "%";
	}
}
