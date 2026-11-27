package com.openrsc.interfaces.misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import orsc.SkillGuideWindow;
import orsc.mudclient;

final class LeatherGuideTopics {
	private LeatherGuideTopics() {
	}

	static void append(mudclient client, Map<String, List<SkillGuideWindow.Article>> topics) {
		if (topics.remove("More info") == null) {
			return;
		}
		SkillGuideTopics.page(topics, "Making armor",
			new SkillGuideWindow.Article("From hide to armor", Arrays.asList(
				"Take raw hides or carapaces to a tanning rack. Use a needle on the treated material with thread in your inventory to open the crafting window. Each piece shows its material cost before you make it.",
				"Leather armor shares its source creature's defensive strengths and weaknesses. Wearing leather lowers your Magic Power. Examine a piece to check its set trait.")));

		List<SkillGuideWindow.Article> bonuses = new ArrayList<>();
		bonuses.add(new SkillGuideWindow.Article("Wearing a full set", Collections.singletonList(
			"Equip the matching coif, cuirass, chaps, gloves and boots to activate the set bonus.")));
		add(bonuses, client, 1839, "Cow hide", "Hardy adds 5 to your maximum Hits.");
		add(bonuses, client, 1844, "Goblin hide", "Goblin's Tenacity can let you survive a lethal hit with 1 Hit remaining.");
		add(bonuses, client, 1849, "Unicorn hide", "Divine adds 10 to your Prayer point capacity.");
		add(bonuses, client, 1854, "Bear hide", "Bear's Maul splits a melee attack into two smaller hits.");
		add(bonuses, client, 1859, "Black unicorn hide",
			"Earn more Devotion from offerings. Burying bones or scattering ashes can also restore Hits.");
		add(bonuses, client, 1864, "Scorpion carapace", "Poison wears off faster while you wear the full set.");
		add(bonuses, client, 3357, "Giant frog hide", "Sticky Skin can delay an enemy's next attack when they damage you directly.");
		add(bonuses, client, 1869, "Wolf hide", "An invulnerable spirit wolf attacks your target.");
		add(bonuses, client, 1874, "Spider carapace", "Poison wears off faster than with Scorpion carapace.");
		add(bonuses, client, 1889, "Baby dragon hide", "Blow Smoke can make an enemy's next few attacks less accurate.");
		add(bonuses, client, 3394, "Ugthanki hide", "Storage Hump makes food heal 20% more.");
		add(bonuses, client, 1894, "Magic spider carapace", "This is the strongest poison-cleansing carapace set.");
		add(bonuses, client, 3369, "Naga hide", "Cold Blooded reduces damage from fire and ice magic.");
		add(bonuses, client, 1909, "Demon hide", "Hell's Fire can deal extra fire damage and weaken an enemy's defenses.");
		add(bonuses, client, 3375, "Terror dog hide", "Ferocious splits melee attacks into two hits, each stronger than Bear's Maul.");
		add(bonuses, client, 3381, "Bloodveld hide", "Restore 1 Hit each time you deal damage. Your poison and summons can also trigger this.");
		add(bonuses, client, 1914, "Hellhound hide", "An invulnerable spirit hellhound attacks your target and can trigger Hell's Fire.");
		add(bonuses, client, 3387, "Dark beast hide",
			"Taking direct damage builds an electrical charge. A full charge releases a thunder strike around you.");
		add(bonuses, client, 1924, "Blue dragon hide", "Icy Dragon Breath can deal extra damage and lower an enemy's maximum hit.");
		add(bonuses, client, 1929, "Green dragon hide", "Terra Dragon Breath can deal extra damage and slow an enemy's attacks.");
		add(bonuses, client, 1934, "Red dragon hide", "Fiery Dragon Breath can deal extra damage and weaken an enemy's defenses.");
		add(bonuses, client, 1939, "Black demon hide", "Hell's Blaze deals more fire damage and weakens defenses more than Hell's Fire.");
		add(bonuses, client, 1944, "Black dragon hide", "Poisonous Dragon Breath can deal extra damage and inflict strong poison.");
		add(bonuses, client, 3317, "King black dragon hide",
			"True Dragon's Breath can deal extra damage, poison an enemy and apply one of three elemental weaknesses.");
		add(bonuses, client, 1949, "Balrog hide", "Hell's Inferno can deal extra fire damage, weaken the target's defenses and hit nearby enemies.");
		add(bonuses, client, 1954, "Elder green dragon hide",
			"Elder Breath can burn the target and nearby enemies. Its initial damage bypasses defense.");
		topics.put("Set bonuses", bonuses);
	}

	private static void add(List<SkillGuideWindow.Article> bonuses, mudclient client, int itemId, String title, String description) {
		bonuses.add(new SkillGuideWindow.Article(title, client.skillGuideItemIcon(itemId), Collections.singletonList(description)));
	}
}
