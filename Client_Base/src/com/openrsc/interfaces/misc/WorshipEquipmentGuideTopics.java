package com.openrsc.interfaces.misc;

import static com.openrsc.interfaces.misc.WorshipGuideTopics.article;
import static com.openrsc.interfaces.misc.WorshipGuideTopics.topic;

import com.openrsc.client.entityhandling.EntityHandler;
import java.util.Arrays;
import java.util.List;
import orsc.SkillGuideWindow.Topic;
import orsc.mudclient;

/** Equipment effects and acquisition routes confirmed for MyWorld. */
final class WorshipEquipmentGuideTopics {
	private static final String BLESSING_GUIDE = " See Equipment in the Blessing guide for details.";

	private WorshipEquipmentGuideTopics() {
	}

	static List<Topic> topics(mudclient client) {
		return Arrays.asList(armor(), robes(), weapons(), artifacts(client));
	}

	private static Topic armor() {
		return topic("Armor",
			article("Blessing", "You can bless steel armor at your aligned god's altar." + BLESSING_GUIDE),
			article("Drops", "White Knights drop Saradomin's white armor, Grey Knights drop Guthix's grey armor, "
				+ "and Dark Warriors drop Zamorak's black armor. The armor drops already blessed."),
			article("Defense and Prayer points", "Your Devotion to the god whose armor you wear determines its defensive stats. "
				+ "The higher your Devotion, the stronger the armor, from steel-like protection up to roughly orichalcum-tier protection. "
				+ "Its Prayer point bonus grows with Devotion too."),
			article("Other armor with Prayer bonuses",
				"Smith Paladin shields for extra Prayer points. Higher metals give a larger bonus.",
				"A full set of unicorn-hide armor adds Prayer points. A full black unicorn-hide set improves Devotion gain and restores Hits "
					+ "from offerings, including those made by the Mourning Unicorn."));
	}

	private static Topic robes() {
		return topic("Robes",
			article("Blessing", "You can bless plain wool gear at your aligned god's altar." + BLESSING_GUIDE),
			article("Drops", "Priests drop Saradomin robe tops and bottoms, Monks of Zamorak drop Zamorak robes, "
				+ "and Druids drop Guthix robes. These robes already carry their god's blessing."),
			article("Magic defense and Prayer points", "Blessed wool offers magic defense and extra Prayer points. "
				+ "The higher your Devotion to the god whose robes you wear, the stronger both bonuses become."),
			article("Blessed symbols", "A matching equipped symbol doubles the base Devotion from offerings. "
				+ "Craft, string, and bless the symbol of your god. See Blessing for details."),
			article("Other clothing and accessories",
				"The Worship cape adds Prayer points and restores Hits when you make offerings. Brother Jered sells it at the Edgeville Monastery.",
				"The Doctor's gown, Beads of the dead, and Underground Pass quest amulets also add Prayer points while worn."));
	}

	private static Topic weapons() {
		return topic("Weapons",
			article("Blessing", "You can bless compatible steel weapons and plain wooden staves at your aligned god's altar." + BLESSING_GUIDE),
			article("Drops", "White Knights, Grey Knights, and Dark Warriors drop weapons aligned with their respective gods. "
				+ "The weapons drop already blessed."),
			article("Knight weapon power and Prayer points", "Knight weapons start with steel-like melee power. "
				+ "As your Devotion to their god grows, their melee power approaches orichalcum-tier strength and their Prayer point bonus increases."),
			article("Blessed staves", "Blessed staves improve magic power, Holy Power, and Prayer points. "
				+ "Their bonuses depend on the wood tier. Wield them with the matching alignment and the required Worship and Magic levels."),
			article("Other weapons with Prayer bonuses",
				"Regular metal maces and crafted crossbows add Prayer points. Higher metals and woods give stronger bonuses. "
					+ "The Dragon crossbow from the Rangers' Guild gives a Prayer bonus too."));
	}

	private static Topic artifacts(mudclient client) {
		String god = EntityHandler.getActivePrayerBook();
		int cape = "ZAMORAK".equals(god) ? 1213 : "GUTHIX".equals(god) ? 1215 : 1214;
		int staff = "ZAMORAK".equals(god) ? 1216 : "GUTHIX".equals(god) ? 1217 : 1218;
		int mace = "ZAMORAK".equals(god) ? 3253 : "GUTHIX".equals(god) ? 3254 : 3252;
		int crossbow = "ZAMORAK".equals(god) ? 3445 : "GUTHIX".equals(god) ? 3446 : 3444;
		return topic("Artifacts",
			article("Gifts of Devotion", "Each god has a cape, staff, mace, and crossbow that can be bestowed at high Devotion. "
				+ "These artifacts have fixed stats and require worshipping the matching god."),
			article("God capes", client.skillGuideItemIcon(cape), "Add Prayer points, lower the cost of the matching mace and crossbow prayers, "
				+ "strengthen holy bolts, and increase the maximum damage of god spells."),
			article("God staves", client.skillGuideItemIcon(staff), "Add magic power, Holy Power, and Prayer points. "
				+ "They let you cast the matching god spells in the Magic spellbook when you meet the spell requirements."),
			article("God maces", client.skillGuideItemIcon(mace), "Add melee power and Prayer points. The maces enable Saving Grace "
				+ "for Saradomin, Divine Retribution for Zamorak, or Corrosive Aura for Guthix."),
			article("God crossbows", client.skillGuideItemIcon(crossbow), "Add ranged power and can fire normal bolts. "
				+ "Activate the matching prayer for free holy bolts with Revelation, Bleed, or Briar Seeds. See the god pages under Prayers."));
	}
}
