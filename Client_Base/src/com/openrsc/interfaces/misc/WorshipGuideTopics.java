package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.defs.ClericSpellDef;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import orsc.ClericPowerPresentation;
import orsc.SkillGuideWindow.Article;
import orsc.SkillGuideWindow.Topic;

/** Worship guide for the scaled interface. Cleric entries use the active server catalog. */
final class WorshipGuideTopics {
	static final List<String> SECTIONS = Collections.unmodifiableList(
		Arrays.asList("Prayers", "Devotion", "Equipment", "Cleric Spells"));

	private static final String SPECIAL_COST = "Reserve 90 points with the mace or 60 with the matching cape equipped.";
	private static final String BOLT_COST = "Your god's crossbow reserves 80 points for free tier 6 bolts. "
		+ "With the matching cape it reserves 60 and fires tier 8 bolts. Equipped ammunition is saved while the prayer is active.";

	private WorshipGuideTopics() {
	}

	static List<Topic> load(int section, List<ClericSpellDef> spells, IntFunction<String> itemName) {
		switch (section) {
			case 0: return prayers();
			case 1: return devotion();
			case 2: return WorshipEquipmentGuideTopics.topics();
			case 3: return cleric(spells, itemName);
			default: throw new IllegalArgumentException("Unknown Worship guide section " + section);
		}
	}

	private static List<Topic> prayers() {
		return Arrays.asList(
			topic("Saradomin",
				combatPrayer("Magic Power", "Increases magic damage."),
				combatPrayer("Melee Protection", "Reduces melee damage taken."),
				favor("Enchanting"),
				article("Saving Grace", "Requires the Saradomin mace. "
					+ "A successful roll heals you for the damage dealt, up to your missing Hits. "
					+ "The chance rises as your Hits fall.", SPECIAL_COST),
				article("Saradomin's Divine Bolts", BOLT_COST,
					"Revelation raises the minimum damage roll by 1% of maximum per kill of the same NPC type, up to 15%. "
					+ "Switching enemy type, removing the crossbow, or turning off the prayer resets the bonus.")),
			topic("Zamorak",
				combatPrayer("Melee Power", "Increases melee damage."),
				combatPrayer("Ranged Protection", "Reduces ranged damage taken."),
				favor("Smithing"),
				article("Divine Retribution", "Requires the Zamorak mace. Incoming attacks can reflect twice the damage taken. "
					+ "Bigger hits have a higher chance to trigger it.", SPECIAL_COST),
				article("Zamorak's Bolts of Wrath", BOLT_COST,
					"Critical hits or main hits of at least 70% of maximum can open a bleed. "
					+ "The wound deals three pulses of 3-5 damage, three ticks apart. Reopening it refreshes your wound.")),
			topic("Guthix",
				combatPrayer("Ranged Power", "Increases ranged damage."),
				combatPrayer("Magic Protection", "Reduces magic damage taken."),
				favor("Crafting"),
				article("Corrosive Aura", "Requires the Guthix mace. Enemies that damage you gain 10-50 poison stacks. "
					+ "Lower Hits apply more stacks.", SPECIAL_COST),
				article("Guthix's Thorned Bolts", BOLT_COST,
					"Five main shots against the same enemy, including zeroes, trigger a Briar Seed burst. "
					+ "Each enemy within one tile can take 0-3 extra damage.")),
			topic("Mechanics",
				article("Reserve points", "Each prayer reserves part of your prayer point pool while active. "
					+ "The pool stays steady over time, and turning a prayer off immediately returns its reserved points for reuse."),
				article("Activate prayers", "Prayers have no Worship level requirement. Activate any prayer in your current book "
					+ "as soon as you have enough free points. The special prayers also need the matching god mace or crossbow.",
					"Your current Worship level supplies points. Equipment Prayer bonuses and an active Sacred Unicorn can add to the pool."),
				article("Combine prayers", "Keep several prayers active by covering their combined cost. "
					+ "Bonuses from active tiers in the same line add together. The five tiers run from Weak and Lesser through "
					+ "the standard, Strong, and Greater versions."),
				article("Change alignment", "Worship at a god's altar to switch to that god's prayers. "
					+ "Switching clears active prayers and unequips gear tied to a different god.")));
	}

	private static Article combatPrayer(String name, String effect) {
		return article(name, effect + " The five tiers give 5%, 10%, 15%, 20%, and 25%. "
			+ "They reserve 3, 6, 15, 29, and 49 points respectively.");
	}

	private static Article favor(String skill) {
		return article(skill + " Favor", "Gain 10%, 15%, 20%, 25%, or 30% extra " + skill + " XP by tier. "
			+ "The tiers reserve 2, 7, 22, 46, and 80 points respectively.");
	}

	private static List<Topic> devotion() {
		return Arrays.asList(
			topic("Earning",
				article("Bones and ashes", "Bury bones or scatter ashes to make an offering to the god you currently worship. "
					+ "Ten ordinary offerings add one Devotion. Each god has a separate balance.",
					"A matching blessed symbol adds 100% to Devotion from offerings. The Mourning Unicorn adds 50% on "
					+ "offerings it handles, and a full black unicorn-hide set adds another 50%. These bonuses add together."),
				article("Offer rival equipment", "Use unnoted blessed equipment from another god on your own god's altar. "
					+ "The altar destroys the item, raises your god's Devotion, and lowers Devotion with the god it belonged to.",
					"Rival gear can transfer several points of Devotion at once. The amount depends on the item. "
					+ "Most gear transfers one point per material, blessed staves use the wood tier, and a blessed symbol transfers 0.2.")),
			topic("Benefits",
				article("More Worship XP", "Positive Devotion adds Worship experience each time you bury bones or scatter ashes. "
					+ "At 100 Devotion, ordinary bones or ashes give 100 bonus XP. Big bones, bat bones, demon ash, and dragon bones give larger bonuses."),
				article("Stronger equipment", "After 250 Devotion, aligned knight armor, knight weapons, and blessed wool gain "
					+ "combat stats and more Prayer points. Each piece can gain up to 10 extra Prayer points at 1,000 Devotion.",
					"Higher-tier blessings require more Devotion."),
				article("Artifacts", "With high Devotion, the god you worship may bestow a unique artifact. "
					+ "See Equipment > Artifacts for what the items do.")),
			topic("More info",
				article("Negative Devotion", "A god's followers can turn hostile when your Devotion with that god falls below zero. "
					+ "The more negative it gets, the more likely normally peaceful followers are to attack. "
					+ "The Devotion XP bonus returns once your standing turns positive."),
				article("Rebuilding standing", "Devotion ranges from -1,000 to 1,000 for each god. "
					+ "Worship that god and make offerings to rebuild your standing. "
					+ "Hover Worship in Stats to see your active god's Devotion.")));
	}

	private static List<Topic> cleric(List<ClericSpellDef> spells, IntFunction<String> itemName) {
		Map<Integer, List<String>> spellsByLevel = new TreeMap<>();
		List<Article> descriptions = new ArrayList<>();
		for (ClericSpellDef spell : spells) {
			spellsByLevel.computeIfAbsent(spell.getWorshipLevel(), level -> new ArrayList<>()).add(spell.getName());
		}
		for (ClericSpellGuideCatalog.Entry entry : ClericSpellGuideCatalog.buildDocument(spells, itemName)) {
			List<String> paragraphs = new ArrayList<>();
			paragraphs.add(entry.getMechanics());
			paragraphs.addAll(entry.getParagraphs());
			descriptions.add(new Article(entry.getHeader(), paragraphs));
		}
		List<Article> unlocks = new ArrayList<>();
		unlocks.add(article("Worship level", "Your base Worship level unlocks Cleric spells. Each spell also requires the listed sigils to cast."));
		spellsByLevel.forEach((level, names) -> unlocks.add(article("Level " + level, String.join(", ", names))));
		if (spells.isEmpty()) {
			Article unavailable = article("", "Spell details are available once the Cleric spellbook loads.");
			unlocks.add(unavailable);
			descriptions.add(unavailable);
		}
		return Arrays.asList(new Topic("Unlocks", unlocks), new Topic("Descriptions", descriptions),
			topic("More info",
				article("Who spells affect", "Cleric spells support nearby party members or affect nearby enemies. "
					+ "Your own spells always exclude you.",
					"Hostile spells can affect eligible enemy players in the Wilderness. Party members, clanmates, "
					+ "and players on your friends list are excluded from your area attacks."),
				article("Holy Power", "Current Worship and your equipped holy staff determine Holy Power. "
					+ "Tiers 1, 2, and 3 allow up to power levels 2, 4, and 6 respectively.",
					"Power levels 1 through 6 require " + ClericPowerPresentation.thresholds(6) + " Holy Power in that order."),
				article("Casting with sigils", "Blessings use Grace and a god sigil. Curses use Blight and a god sigil. "
					+ "Unify and Greater Unify use Grace alone and follow normal teleport restrictions. See Blessing for making sigils."),
				article("Combine effects", "Effects from different gods can coexist. Each recipient can have one blessing and one "
					+ "curse per god. A stronger version replaces a weaker one, and an equal cast refreshes the duration.")));
	}

	static Topic topic(String label, Article... articles) {
		return new Topic(label, Arrays.asList(articles));
	}

	static Article article(String title, String... paragraphs) {
		return new Article(title, Arrays.asList(paragraphs));
	}
}
