package com.openrsc.interfaces.misc;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import orsc.SkillGuideWindow.Article;
import orsc.SkillGuideWindow.Topic;
import orsc.mudclient;

/** Blessing recipes and requirements for the scaled skill guide. */
final class BlessingGuideTopics {
	static final List<String> SECTIONS = Collections.unmodifiableList(Arrays.asList("Sigils", "Equipment"));

	// Global logical coordinates and signed levels, matching the Tile HUD.
	private static final String DIVINE_WELLSPRING = "(253,462,L-1)";
	private static final String ICHOR_WELL = "(104,672,L-1)";
	private static final String ICHOR_ENTRANCE = "(108,681,L0)";

	// Same wood tiers as BlessedStaffs and EnchantingItemEffects.getTemporaryEnchantingRequirementForTier.
	private static final int[] STAFF_LEVELS = {1, 8, 15, 22, 30, 38, 46, 54, 62, 70};
	private static final String[] STAFF_NAMES = {"Basic", "Pine", "Oak", "Willow", "Palm", "Maple", "Yew", "Ebony", "Magic", "Blood"};

	private BlessingGuideTopics() {
	}

	static List<Topic> load(int section, mudclient client) {
		if (section == 0) return Arrays.asList(unlocks(client), imbuing(client));
		return Arrays.asList(staves(client), armorAndWeapons(), symbols(client));
	}

	private static Topic unlocks(mudclient client) {
		return topic("Unlocks",
			article("Sigil materials", "Blessing level determines what you can imbue. Crafting level determines what you can carve. "
				+ "Every material can be carved into a god, Grace, or Blight sigil."),
			Article.unlock("Stone sigils. Requires Crafting 1 to carve.", "1", client.skillGuideItemIcon(3300)),
			Article.unlock("Silver sigils. Requires Crafting 20 to mould and carve.", "16", client.skillGuideItemIcon(3308)),
			Article.unlock("Moonsilver sigils. Requires Crafting 40 to mould and carve.", "40", client.skillGuideItemIcon(3441)),
			article("Extra sigils", "Every 10 Blessing levels above a material's requirement adds another imbued sigil per carving. "
				+ "For example, at Blessing 11 you can imbue one Stone carving into two finished sigils."));
	}

	private static Topic imbuing(mudclient client) {
		return topic("Imbuing",
			article("Get stone", client, 1299, "Mine ordinary stone rocks with a pickaxe. Mining level 1 is enough. You can carve the Stone directly."),
			article("Make metal blanks", client, 3433,
				"Smelt a silver nugget into a Silver bar at Smithing 20. At Smithing 38, smelt one Silver bar and one Mithril bar together to make a Moonsilver bar.",
				"Bring your bars and a sigil mould to a furnace. Choose Blank sigils. Each bar makes three blanks. "
					+ "Buy a mould from Dommik in Al Kharid or Rommik in Rimmington."),
			article("Carve a sigil", client, 3293, "Use a chisel on Stone or a metal blank. Choose Saradomin, Guthix, Zamorak, Grace, or Blight in the carving menu. "
				+ "Carving earns Crafting XP and produces an unimbued sigil."),
			article("God sigils", "Use an unimbued god sigil on that god's altar. The altar imbues every matching unimbued sigil "
				+ "of the same god and material in your inventory. Each carving spends 0.05 Devotion with that god."),
			article("Grace sigils", client, 3300, "Imbue Grace at Divine wellsprings beneath sacred places. "
				+ "You can find one beneath the Edgeville Monastery at " + DIVINE_WELLSPRING + "."),
			article("Blight sigils", client, 3427, "Imbue Blight at Ichor wells beneath places of grief and sorrow. "
				+ "You can find one beneath a graveyard at " + ICHOR_WELL + ". Take the surface ladder down at " + ICHOR_ENTRANCE + "."),
			article("Dip sigils", "Choose Dip sigils from the shore to imbue the matching Grace or Blight carvings in your inventory. "
				+ "You can also use one carving on the well to imbue its material only. Wells imbue sigils without spending Devotion."));
	}

	private static Topic staves(mudclient client) {
		List<Article> articles = new ArrayList<>();
		articles.add(article("Bless a wooden staff", "Use a plain wooden staff on the altar of the god you worship. "
			+ "The levels below are Worship requirements. To wield the result, you also need Magic at the listed level and the matching alignment."));
		articles.add(article("Holy Power", "An equipped blessed staff adds Holy Power alongside your current Worship level, "
			+ "helping your Cleric spells reach higher power levels. Higher wood tiers give more Holy Power."));
		NumberFormat number = NumberFormat.getIntegerInstance(Locale.US);
		for (int tier = 0; tier < STAFF_LEVELS.length; tier++) {
			String detail = STAFF_NAMES[tier] + " staff  -  " + number.format((tier + 1) * 100) + " Devotion";
			articles.add(Article.unlock(detail, Integer.toString(STAFF_LEVELS[tier]), client.skillGuideItemIcon(3152 + tier)));
		}
		articles.add(article("Devotion cost", "Blessing a staff spends half a point of Devotion per wood tier. "
			+ "The basic staff costs 0.5 and the Blood staff costs 5."));
		return new Topic("Staves", articles);
	}

	private static Topic armorAndWeapons() {
		return topic("Armor and weapons",
			article("Bless steel and wool", "Use an unnoted steel armor piece, steel melee weapon, or plain wool robe piece on the altar of the god you worship. "
				+ "The altar replaces it with the aligned version. Every piece of the wool set can be blessed."),
			article("100 Devotion", "Steel dagger, short sword, mace, or helmet. Wool hat. Each blessing spends 0.5 Devotion."),
			article("200 Devotion", "Steel long sword, scimitar, spear, gauntlets, or greaves. Wool gloves or boots. Each blessing spends 1 Devotion."),
			article("300 Devotion", "Steel two-handed sword, battle axe, scythe, square shield, Paladin shield, or plate legs. "
				+ "Wool robe bottom. Each blessing spends 1.5 Devotion."),
			article("400 Devotion", "Steel plate body or wool robe top. Each blessing spends 2 Devotion."),
			article("Blessing limit", "You can bless up to ten pieces of equipment per hour. Armor, weapons, robes, staves, and symbols share this limit."));
	}

	private static Topic symbols(mudclient client) {
		return topic("Symbols",
			article("Bless your god's symbol", client, 385, "Craft and string the symbol first. Use the unblessed symbol on its god's altar while worshipping that god.",
				"Blessing a symbol requires 50 Devotion and leaves your Devotion balance intact."),
			article("Wear a blessed symbol", "A matching symbol increases how much Devotion you gain from offerings."));
	}

	private static Topic topic(String label, Article... articles) {
		return new Topic(label, Arrays.asList(articles));
	}

	private static Article article(String title, String... paragraphs) {
		return new Article(title, Arrays.asList(paragraphs));
	}

	private static Article article(String title, mudclient client, int itemId, String... paragraphs) {
		return new Article(title, client.skillGuideItemIcon(itemId), Arrays.asList(paragraphs));
	}
}
