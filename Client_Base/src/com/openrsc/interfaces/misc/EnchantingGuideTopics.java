package com.openrsc.interfaces.misc;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import orsc.SkillGuideWindow.Article;
import orsc.SkillGuideWindow.Topic;

/** Enchanting instructions grouped with the unlock lists they explain. */
final class EnchantingGuideTopics {
	private EnchantingGuideTopics() {
	}

	static void append(String section, List<Topic> topics) {
		switch (section) {
			case "Robes":
				topics.add(robeCosts());
				topics.add(robeEffects());
				topics.add(topic("More info",
					article("Enchanting wool",
						"Use an unenchanted wool hat, robe top, robe skirt, pair of gloves, or pair of boots on a rune altar. The altar gives that piece its tier 1 enchantment.",
						"Bring the matching runes. Your Enchanting level must meet both the altar's requirement and the requirement for the tier you are making."),
					article("Upgrading a piece",
						"Return to the same altar with the enchanted piece to upgrade it. Each use raises it by one tier up to tier 10. You cannot skip tiers or switch the piece to another altar."),
					article("Wearing enchanted wool",
						"Higher tiers grant more magic defense and a stronger altar effect. Pieces bound to the same altar add their tiers together for that effect. For example, a tier 2 Fire hat and a tier 3 Fire robe top count as five Fire tiers.",
						"Each worn piece also gives a 10% chance to save its matching rune when casting or summoning. Five matching pieces and a matching enchanted staff can save that rune every time.")));
				break;
			case "Staves":
				topics.add(staffCosts());
				topics.add(topic("More info",
					article("Attuning a staff",
						"Use an unenchanted staff on the rune altar you want to bind it to. You attune it in a single action. Its wood tier sets its Enchanting requirement, and you must also meet the altar's requirement."),
					article("Saving runes",
						"An equipped enchanted staff has a 50% chance to save its matching rune when casting or summoning. This stacks with the matching pieces of an enchanted wool set.")));
				break;
			case "Jewelry":
				topics.add(jewelryCosts());
				topics.add(topic("More info",
					article("Choosing an altar",
						"Use an unenchanted ring, necklace, or bangle on a rune altar. Your Enchanting level must be high enough for both the gem and the altar.",
						"The item type, gem, and altar determine the result. Examine an enchanted piece to read its effect.")));
				break;
			case "Altars":
				topics.add(topic("More info",
					article("Making runes",
						"Mine stone from regular rocks. Using stone on a rune altar turns the unnoted stone in your inventory into that altar's runes.",
						"You make one additional rune per stone for every 10 Enchanting levels above the altar's requirement. For example, the Air altar requires level 1 and makes two runes per stone at level 11."),
					article("Enchanting cape", "Unlocked at level 99 Enchanting.")));
				break;
			default:
				break;
		}
	}

	private static Topic robeCosts() {
		List<Article> costs = costPage("These are the costs for one piece at each step. Tier 1 binds plain wool. Later tiers each upgrade the previous tier.");
		// Same target-tier rule as EnchantingItemEffects.getWoolRobeRuneCost.
		for (int tier = 1; tier <= 10; tier++) addCost(costs, "Tier " + tier, tier * tier * 50);
		return new Topic("Costs", costs);
	}

	private static Topic staffCosts() {
		List<Article> costs = costPage("Attunement costs depend on the staff's wood tier.");
		// Same wood-tier rule as EnchantingItemEffects.getStaffRuneCost.
		for (int tier = 1; tier <= 10; tier++) addCost(costs, "Tier " + tier, tier * 200);
		return new Topic("Costs", costs);
	}

	private static Topic jewelryCosts() {
		List<Article> costs = costPage("The cost is the same for a ring, necklace, or bangle made from the same gem.");
		String[] gems = {"Sapphire", "Emerald", "Ruby", "Diamond", "Dragonstone"};
		// Same gem-tier rule as EnchantingItemEffects.getRuneCostForTier.
		for (int tier = 1; tier <= gems.length; tier++) addCost(costs, gems[tier - 1], tier * 50);
		return new Topic("Costs", costs);
	}

	private static List<Article> costPage(String note) {
		List<Article> costs = new ArrayList<>();
		costs.add(article("Matching altar runes", note, "Use the rune made by the altar. At the Fire altar, pay in fire runes."));
		return costs;
	}

	private static void addCost(List<Article> costs, String label, int count) {
		String amount = NumberFormat.getIntegerInstance(Locale.US).format(count);
		costs.add(article("", label + "  -  " + amount + " runes"));
	}

	private static Topic robeEffects() {
		return topic("Effects",
			article("How effects scale", "Add the tiers of your equipped pieces that share the same altar. Unless stated otherwise, the bonuses below are per tier in that total."),
			article("Air", "Take 2% less Air magic damage."),
			article("Water", "Take 2% less Water and Ice magic damage."),
			article("Earth", "Take 2% less Earth magic damage."),
			article("Fire", "Take 2% less Fire magic damage."),
			article("Life", "Combat summons gain 2% more health. Support summons last 2% longer."),
			article("Mind", "Raises the maximum damage cap of Mind spells by 1%."),
			article("Body", "Taking damage stores a temporary bonus to melee, ranged, and magic weapon power. The maximum stored bonus is one point per Body tier."),
			article("Chaos", "Deal 2% more damage for each adjacent enemy NPC."),
			article("Cosmic", "Gain 1% combat luck. Combat luck can roll eligible combat outcomes a second time and keep the more favorable result."),
			article("Nature", "Potions are 2% stronger and their effects last 2% longer."),
			article("Law", "Craft 2% extra runes. Portions of a bonus rune carry over to your next craft of the same rune."),
			article("Death", "When a hit overkills an NPC, 2% of that excess damage splashes to nearby enemies."),
			article("Soul", "Regenerate health 2% faster."),
			article("Blood", "Blood spells splash 2% of the damage dealt to nearby enemies."));
	}

	private static Article article(String title, String... paragraphs) {
		return new Article(title, Arrays.asList(paragraphs));
	}

	private static Topic topic(String label, Article... articles) {
		return new Topic(label, Arrays.asList(articles));
	}
}
