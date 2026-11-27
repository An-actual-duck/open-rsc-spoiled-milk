package com.openrsc.interfaces.misc;

import static com.openrsc.interfaces.misc.WorshipGuideTopics.article;
import static com.openrsc.interfaces.misc.WorshipGuideTopics.topic;

import java.util.Arrays;
import java.util.List;
import orsc.SkillGuideWindow.Article;
import orsc.SkillGuideWindow.Topic;

/** Equipment stats and acquisition routes confirmed for MyWorld. */
final class WorshipEquipmentGuideTopics {
	private static final String KNIGHT_GROWTH = "Worship the gear's god to equip it. Stats grow above 250 Devotion and "
		+ "reach the listed maximums at 1,000. Each piece gains up to 10 additional Prayer points.";

	private WorshipEquipmentGuideTopics() {
	}

	static List<Topic> topics() {
		return Arrays.asList(armor(), robes(), weapons(), artifacts());
	}

	private static Topic armor() {
		return topic("Armor",
			article("Knight armor", "Bless steel armor at your god's altar. White Knights drop Saradomin's white gear, "
				+ "Grey Knights drop Guthix's grey gear, and Dark Warriors drop Zamorak's black gear.",
				"All three colors share the same stats. " + KNIGHT_GROWTH),
			article("Knight armor stats",
				"Helmet gives 8 to 12 melee defense, 2 to 4 ranged defense, and 2 to 12 Prayer points.",
				"Plate body gives 19 to 30 melee defense, 6 to 10 ranged defense, and 4 to 14 Prayer points.",
				"Plate legs give 12 to 18 melee defense, 3 to 6 ranged defense, and 3 to 13 Prayer points.",
				"Gauntlets and greaves each give 8 to 12 melee defense, 2 to 4 ranged defense, and 2 to 12 Prayer points.",
				"Square shield gives 8 to 12 melee defense, 2 to 4 ranged defense, and 3 to 13 Prayer points.",
				"Paladin shield gives 12 to 18 melee defense, 3 to 6 magic defense, and 8 to 18 Prayer points."),
			article("Other armor with Prayer bonuses",
				"Smith regular Paladin shields for 1 to 10 Prayer points from tin through rune. "
					+ "The Dragon Paladin Shield gives 11 and is smithable from the two dragon shield halves.",
				"Craft a full set from unicorn hide for a set bonus of 10 Prayer points. "
					+ "A full set of black unicorn hide improves Devotion gain and restores Hits from your offerings, including those handled by the Mourning Unicorn."));
	}

	private static Topic robes() {
		return topic("Robes",
			article("Blessed wool", "Use plain wool gear on your god's altar. Priests drop Saradomin robe tops and bottoms, "
				+ "Monks of Zamorak drop Zamorak robes, and Druids drop Guthix robes.",
				"Worship the gear's god to equip it. Above 250 Devotion, the gear gains magic defense and Prayer points. "
					+ "The listed maximums are at 1,000 Devotion."),
			article("Blessed wool stats",
				"Hat gives 1 to 9 magic defense and 1 to 11 Prayer points.",
				"Gloves and boots each give 2 to 18 magic defense and 2 to 12 Prayer points.",
				"Robe bottom gives 3 to 27 magic defense and 3 to 13 Prayer points.",
				"Robe top gives 4 to 36 magic defense and 4 to 14 Prayer points."),
			article("Blessed symbols",
				"Craft and string the symbol, then bless it at its god's altar while worshipping that god. "
					+ "A matching equipped symbol doubles the base Devotion from offerings."),
			article("Other clothing and accessories",
				"The level 99 Worship cape gives 8 Prayer points and heals you when you bury bones or scatter demon ash. "
					+ "Brother Jered sells it at the Edgeville Monastery.",
				"The Doctor's gown from Biohazard gives 4 Prayer points. Beads of the dead from Shilo Village give 1. "
					+ "The three quest amulets from Underground Pass each give 3 while worn."));
	}

	private static Topic weapons() {
		return topic("Weapons",
			article("Knight weapons", "Bless compatible steel weapons at your god's altar. "
				+ "White Knights, Grey Knights, and Dark Warriors can also drop weapons of their respective colors.",
				KNIGHT_GROWTH),
			article("Knight weapon stats",
				weaponStats("Dagger", 9, 15, 1),
				weaponStats("Short sword", 20, 32, 1),
				weaponStats("Long sword", 36, 60, 2),
				weaponStats("Two-handed sword", 86, 130, 3),
				weaponStats("Scimitar", 19, 31, 2),
				weaponStats("Battle axe", 38, 62, 3),
				weaponStats("Mace", 25, 40, 6),
				weaponStats("Spear", 24, 38, 2),
				weaponStats("Scythe", 65, 99, 3)),
			article("Blessed staves",
				"Bless a plain wooden staff at your god's altar. To equip it, worship that god and meet its Worship and Magic requirements. "
					+ "Higher woods grant more Prayer points, Magic power, and Holy Power. These stats are fixed by wood tier.",
				"From the basic staff through Blood, the ten tiers give 1 to 10 Prayer points, "
					+ "4 to 28 Magic power, and 8 to 56 Holy Power. See Blessing for the production requirements."),
			article("Other weapons with Prayer bonuses",
				"Regular metal maces give 1 to 10 Prayer points from tin through rune. Smith them at an anvil.",
				"Crafted crossbows give 1 to 9 Prayer points from pine through blood. "
					+ "The Dragon crossbow from the Rangers' Guild gives 10."));
	}

	private static String weaponStats(String name, int basePower, int maxPower, int prayer) {
		return name + " gives " + basePower + " to " + maxPower + " melee power and "
			+ prayer + " to " + (prayer + 10) + " Prayer points.";
	}

	private static Topic artifacts() {
		return topic("Artifacts",
			article("Gifts of Devotion", "Each god has a cape, staff, mace, and crossbow that can be bestowed at high Devotion. "
				+ "Equipment effects require worshipping the matching god. The artifact stats are fixed."),
			article("God capes", "Give 10 Prayer points and reduce the cost of their god's mace prayer from 90 to 60 points. "
				+ "The cape also reduces the crossbow prayer from 80 to 60 points and upgrades its holy bolts to tier 8."),
			article("God staves", "Give 28 magic power, 64 Holy Power, and 11 Prayer points. "
				+ "They let you cast the matching god spells in the Magic spellbook when you meet the spell requirements."),
			article("God maces", "Give 72 melee power and 11 Prayer points. "
				+ "The maces enable Saving Grace for Saradomin, Divine Retribution for Zamorak, or Corrosive Aura for Guthix."),
			article("God crossbows", "Give 38 ranged power. Use normal bolts or activate the matching prayer for free holy bolts. "
				+ "The three prayers add Revelation, Bleed, or Briar Seeds. See the god pages under Prayers for the effects."));
	}
}
