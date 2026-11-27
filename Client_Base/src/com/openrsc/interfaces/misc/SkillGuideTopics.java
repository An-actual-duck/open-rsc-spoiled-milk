package com.openrsc.interfaces.misc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import orsc.Config;
import orsc.SkillGuideWindow.Article;
import orsc.mudclient;

final class SkillGuideTopics {
	private static final String TOOL_FOCUS_HELP =
		"Set Tool Focus Menu to Always in Options to keep it visible while your gathering tool is equipped.";

	private SkillGuideTopics() {
	}

	static String defaultTopic(String skill, String section, boolean overview) {
		if ("Hits".equals(skill)) {
			return "Healing";
		}
		if ("Harvest".equals(skill) && !"Shears".equals(section)) {
			return "Produce";
		}
		if ("Summoning".equals(skill) && "Traits".equals(section)) {
			return "Companion traits";
		}
		if ("Cooking".equals(skill) && "Info".equals(section)) {
			return "Unlocks";
		}
		return overview ? "Overview" : "Unlocks";
	}

	static void append(mudclient client, String skill, String section, Map<String, List<Article>> topics) {
		switch (skill) {
			case "Attack":
			case "Melee":
				melee(section, topics);
				break;
			case "Hits":
				hits(section, topics);
				break;
			case "Ranged":
				ranged(section, topics);
				break;
			case "Magic":
				magic(section, topics);
				break;
			case "Cooking":
				cooking(section, topics);
				break;
			case "Woodcutting":
				woodcutting(section, topics);
				break;
			case "Fishing":
				fishing(section, topics);
				break;
			case "Crafting":
				crafting(client, section, topics);
				break;
			case "Smithing":
				smithing(section, topics);
				break;
			case "Mining":
				mining(section, topics);
				break;
			case "Herblaw":
				herblaw(section, topics);
				break;
			case "Agility":
				agility(client, section, topics);
				break;
			case "Thieving":
				thieving(section, topics);
				break;
			case "Harvest":
				harvesting(section, topics);
				break;
			case "Summoning":
				summoning(section, topics);
				break;
			default:
				break;
		}
	}

	private static void melee(String section, Map<String, List<Article>> topics) {
		switch (section) {
			case "Scythes":
				page(topics, "Fighting", article("Sweeping attacks",
					"Scythes can hit enemies around you. You need both hands free to wield one."));
				break;
			case "2-Handers":
				page(topics, "Fighting", article("Two-handed swords", "Wielding a two-handed sword takes up your shield slot."));
				break;
			case "Maces":
				page(topics, "Prayer bonus", article("Maces and Prayer",
					"Maces increase your Prayer point capacity. See the Worship guide for how reserved Prayer points work."));
				break;
			case "Scimitars":
				page(topics, "Coating", article("Anti-coagulant",
					"Coat a scimitar with anti-coagulant to make hard hits open bleeding wounds. See Herblaw for the recipe."));
				break;
			case "Battleaxes":
				page(topics, "Coating", article("Viscous coating",
					"A coated battleaxe can slow and poison enemies. See Herblaw for the recipe."));
				break;
			case "Info":
				topics.clear();
				page(topics, "Slayer", article("Starting Slayer tasks",
					"Speak to Hobart in Falador. Tasks with special mechanics come with starter supplies, and Slayer associates sell replacements."));
				page(topics, "Wilderness", wilderness());
				page(topics, "Progression", article("Melee equipment",
					"Weapon tabs show the level you need to wield a weapon. See Smithing for how to make metal equipment and Worship for god-aligned gear.",
					"The Melee skill cape unlocks at level 99."));
				break;
			default:
				break;
		}
	}

	private static void hits(String section, Map<String, List<Article>> topics) {
		if ("Pizzas".equals(section) || "Pies".equals(section)) {
			page(topics, "Portions", article("Food you eat in portions",
				"The total healing is spread across the portions shown in the list. Each bite restores one portion and leaves the rest in your inventory."));
		}
		if ("Info".equals(section)) {
			page(topics, "Food bonuses", article("Getting more from food",
				"A full Ugthanki hide set makes food heal more. See Crafting's Leather section for other sets that can help you stay alive."));
		}
	}

	private static void ranged(String section, Map<String, List<Article>> topics) {
		switch (section) {
			case "Bows":
				page(topics, "Arrows", article("Using a bow",
					"Shortbows and longbows fire arrows. Equip your ammunition before attacking. The Crafting guide covers making bowstrings, bows and arrows.",
					"Metal armor in the major armor slots reduces Ranged Power."));
				if (Config.S_WANT_CUSTOM_SPRITES) {
					page(topics, "Wilderness", wilderness());
					page(topics, "Progression", article("Ranged skill cape", "The Ranged skill cape unlocks at level 99."));
				}
				break;
			case "Crossbows":
				page(topics, "Bolts", article("Matching bolts to your crossbow",
					"The highest bolt tier each crossbow can fire is shown next to its unlock. You can also equip weaker bolts."));
				break;
			case "Thrown":
				page(topics, "Making them", article("Darts, knives and shuriken",
					"Thrown weapons are their own ammunition. The Ammo section of Crafting shows the molds and materials you need to make more."));
				break;
			default:
				break;
		}
	}

	private static Article wilderness() {
		return article("Attacking players",
			"Direct attacks must follow Wilderness combat rules. Area attacks aimed at a player can hit other eligible players nearby. Fighting an NPC does not splash onto players.",
			"Area attacks and curses skip your party, clan and friends. Adding someone to your own friends list protects them from your area effects.");
	}

	private static void magic(String section, Map<String, List<Article>> topics) {
		if (!"Spells".equals(section) || !topics.containsKey("Casting")) {
			return;
		}
		page(topics, "Casting",
			article("Combat and healing",
				"Toggle autocast on a combat spell to use it when you attack. Healing spells have their own cooldown."),
			article("Teleporting", "Teleport spells charge for a short time before moving you."));
		page(topics, "Spell effects",
			article("Elemental effects",
				"Air spells can make attacks Unsteady, water can Dampen maximum damage, earth can Slow actions and fire can Scorch defenses. Withering combines these effects."),
			article("Other effects",
				"Thunder can briefly Startle an enemy. Acid can corrode and poison, ice can reflect damage through Frostbite, and wood can Splinter into another enemy."));
		// The exact per-spell timing and requirements are shown in the Magic panel.
	}

	private static void cooking(String section, Map<String, List<Article>> topics) {
		switch (section) {
			case "Meats":
				page(topics, "Cooking food", article("Fires and ranges",
					"Use raw meat or fish on a fire or a range. Foods that need a dish or dough are prepared first and then cooked on a range.",
					"The Hits guide lists how much health each portion restores."));
				page(topics, "Cooking Guild", article("Getting in",
					"Wear a chef's hat to enter the Cooking Guild once you reach level 32."));
				break;
			case "Pizzas":
				page(topics, "Making pizza", article("Dough, toppings and portions",
					"Add tomato and cheese to a pizza base and cook it on a range. Add a topping to a cooked plain pizza to make one of the other varieties."));
				break;
			case "Pies":
				page(topics, "Making pies", article("Fill the pie before cooking",
					"Use pastry dough with a pie dish, add the filling and cook the pie on a range."));
				break;
			default:
				break;
		}
	}

	private static void woodcutting(String section, Map<String, List<Article>> topics) {
		if ("Trees".equals(section) && topics.containsKey("Seed rewards")) {
			page(topics, "Seed rewards", article("Finding seeds while chopping",
				"Use the woodcutting focus menu to choose how often seeds appear. Higher focus gives you more chances at seed rewards.",
				"Resource seeds can be up to two tiers above your hatchet, with rarer rolls above its tier. Knowledge and Money seeds scale with your level.",
				TOOL_FOCUS_HELP));
		}
	}

	private static void fishing(String section, Map<String, List<Article>> topics) {
		if ("Rods".equals(section) && topics.containsKey("More info")) {
			topics.remove("More info");
			page(topics, "Choosing a rod", article("What each rod can catch",
				"A rod catches fish at its own tier and up to three tiers below. Fishing shops stock rods through tier 6. The Fishing Guild stocks rods through tier 10."));
		} else if ("Catches".equals(section) && topics.containsKey("Side rewards")) {
			page(topics, "Side rewards", article("More than fish",
				"You can reel in oysters, seaweed, caskets, leather gloves and boots. The fishing focus menu changes how often you find these extras.",
				"Side loot can be up to two tiers above your rod. Above-tier finds are rarer.",
				TOOL_FOCUS_HELP));
		}
	}

	private static void crafting(mudclient client, String section, Map<String, List<Article>> topics) {
		switch (section) {
			case "Leather":
				LeatherGuideTopics.append(client, topics);
				break;
			case "Wool":
				page(topics, "Wool and flax", article("Spinning supplies",
					"Use a spinning wheel to turn sheep's wool into balls of wool or flax into bowstrings. Use a needle on a ball of wool to see what you can stitch and how much wool each garment needs.",
					"See Blessing for god-aligned robes and Enchanting for robe upgrades."));
				break;
			case "Materials":
				if (topics.containsKey("More info")) {
					topics.remove("More info");
					page(topics, "Crafting Guild", article("The guild and caves",
						"Wear a brown apron to enter the Crafting Guild at level 40. At level 70 you can enter the gem-dragon caves below the guild. Prepare for dangerous combat inside.",
						"The Crafting skill cape unlocks at level 99."));
				}
				break;
			case "Jewelry":
				page(topics, "Making jewelry", article("From raw gem to jewelry",
					"Cut the gem with a chisel, then use a furnace with a gold bar and the mold for the piece you want to make.",
					"See Enchanting for the effects you can add to rings, bangles and necklaces."));
				break;
			case "Ammo":
				if (topics.containsKey("Molds")) {
					page(topics, "Molds", article("Making ammunition",
						"Use the appropriate mold and a metal bar at a furnace to make arrowheads, bolts, dart tips, throwing knives or shuriken.",
						"Add feathers to arrow shafts for headless arrows, then attach arrowheads. Add feathers to dart tips to make throwing darts."));
				}
				break;
			case "Woodcraft":
				page(topics, "Working logs", article("Carving and stringing",
					"Use a knife on logs to carve bow stocks, shafts, staves and fishing rods. Finish an unstrung bow with a bowstring. See Ranged for the level needed to wield it."));
				break;
			default:
				break;
		}
	}

	private static void smithing(String section, Map<String, List<Article>> topics) {
		if ("Smelting".equals(section) && topics.containsKey("Lava forge")) {
			page(topics, "Lava forge", article("Dragon and Purified Rune",
				"Smelt Dragon bars and Purified Rune Bars at the repaired lava forge. You can repair the forge after Dwarf Youth Rescue with 100 black dragon scales and 1,000,000 coins.",
				"Take the finished bars to an ordinary anvil. The Special section shows the Dragon and Exalted Rune recipes."));
		}
	}

	private static void mining(String section, Map<String, List<Article>> topics) {
		if ("Ores".equals(section) && topics.containsKey("Geodes")) {
			page(topics, "Geodes", article("Finding geodes",
				"Use the mining focus menu to look for more geodes. Choose Just the ore when you want to skip geode rewards.",
				TOOL_FOCUS_HELP));
		}
	}

	private static void herblaw(String section, Map<String, List<Article>> topics) {
		if ("Herbs".equals(section)) {
			page(topics, "Preparing herbs", article("Identifying and mixing",
				"Identify a herb before using it. Add the prepared herb to a vial of water and then add the secondary ingredient for the recipe."),
				article("Finding supplies", "Herb patches give a chance at herbs. See Harvest for herb and Brawn ingredient sources."));
		} else if ("Potions".equals(section)) {
			List<Article> otherRecipes = topics.remove("Unlocks");
			if (otherRecipes != null) {
				topics.put("Other recipes", otherRecipes);
			}
			page(topics, "Potion effects",
				article("Skill boosts",
					"Brawn boosts Melee, Hits, Mining, Smithing and Woodcutting. Deftness boosts Ranged, Agility, Fishing, Crafting and Pickpocketing. Insight boosts Magic, Enchanting, Summoning, Cooking and Worship.",
					"Higher versions give a stronger boost for longer. Version 1 gives 5% for 5 minutes and version 6 gives 20% for 20 minutes."),
				article("Experience brews",
					"Use a Skiller's Brew for non-combat experience or a Warrior's Brew for combat experience. Regular brews give 20% for 30 minutes. Strong brews give 40% for 60 minutes."),
				article("Antidotes and stat restore",
					"Antidotes help poison wear off for 10 minutes. The strongest active dose applies, and another dose of the same strength refreshes it. Stat restore reverses stat reductions and protects against them."));
			if (Config.S_WANT_CUSTOM_SPRITES) {
				page(topics, "Progression", article("Herblaw skill cape", "The Herblaw skill cape unlocks at level 99."));
			}
		}
	}

	private static void agility(mudclient client, String section, Map<String, List<Article>> topics) {
		if (!"Courses".equals(section) || !Config.S_WANT_CUSTOM_SPRITES) {
			return;
		}
		page(topics, "Lap rewards", article("Course chests",
			"Finish a lap to earn the course chest shown in Unlocks. Chests can contain supplies such as ammunition, life runes and empowered sigils. Higher-tier chests can also contain ingredients for weapon coatings.",
			"The Agility skill cape unlocks at level 99."));
		page(topics, "Course gear",
			new Article("Boots of Swiftness", client.skillGuideItemIcon(3472), Collections.singletonList(
				"Charged boots make you move faster outside combat. Complete laps with them equipped to recharge them.")),
			new Article("Gloves of Suregrip", client.skillGuideItemIcon(3473), Collections.singletonList(
				"Gloves let you clear obstacles faster and fail less often. They also stretch your boots' charges.")),
			new Article("Cape of Traversal", client.skillGuideItemIcon(3474), Collections.singletonList(
				"The cape carries three course teleports. Walking with the cape equipped restores charges. Wear it with charged boots to earn Agility XP while walking.")),
			article("Experience bonuses", "Each equipped piece adds 10% Agility XP. Gear charges and recharge progress belong to your character."));
	}

	private static void thieving(String section, Map<String, List<Article>> topics) {
		switch (section) {
			case "Pickpocket":
				page(topics, "Pickpocketing", article("Choosing a target",
					"Right-click an eligible character to pickpocket them. If you fail, they can catch you and start a fight."));
				if (Config.S_WANT_CUSTOM_SPRITES) {
					page(topics, "Progression", article("Thieving skill cape", "The Thieving skill cape unlocks at level 99."));
				}
				break;
			case "Stalls":
				page(topics, "Stealing", article("Watch the guards",
					"Steal from a stall when the shopkeeper and guards are out of the way. A stall needs time to restock after a successful theft."));
				break;
			case "Chests":
				page(topics, "Traps", article("Opening thieving chests",
					"Choose Search for traps to disarm and loot a thieving chest. The list shows where to find chests and the level they require."));
				break;
			default:
				break;
		}
	}

	private static void harvesting(String section, Map<String, List<Article>> topics) {
		switch (section) {
			case "Allotments":
				page(topics, "Gathering", article("Equip your shears",
					"Each crop lists the minimum shears tier. Tin shears are enough to pick flax for bowstrings.",
					"Harvesting experience still comes from gathering. Your level helps determine which shears you can equip and how much produce you gather."));
				page(topics, "Seed rewards", article("Finding seeds",
					"Use the harvesting focus menu to choose how often seeds appear. Resource seeds can be up to two tiers above your shears, with rarer rolls above their tier. Knowledge and Money seeds scale with your level.",
					TOOL_FOCUS_HELP));
				break;
			case "Herbs":
				page(topics, "Herb patches", article("What patches can give you",
					"Herb patches roll for herbs at your shears' tier and up to three tiers below. Identify the herbs before using them in potions."));
				break;
			case "Brawn":
				page(topics, "Uses", article("Potion ingredients",
					"These ingredients go into Brawn potions. See the Brawn side tab in Herblaw for the herb and secondary ingredient at each strength."));
				break;
			case "Shears":
				if (Config.S_WANT_CUSTOM_SPRITES) {
					page(topics, "Progression", article("Better shears",
						"Equip the shears that your Harvesting level allows. Higher tiers open up more gatherable plants. The Harvesting skill cape unlocks at level 99."));
				}
				break;
			default:
				break;
		}
	}

	private static void summoning(String section, Map<String, List<Article>> topics) {
		if ("Scaling".equals(section)) {
			List<Article> stats = topics.remove("Unlocks");
			page(topics, "Overview", article("How companions grow",
				"As your Summoning level rises, combat summons can take more damage and deal stronger hits. Use Traits to compare what they do in a fight.",
				"Support and utility companions help through their traits and commands. See Summons for upkeep and task uses."));
			if (stats != null) {
				topics.put("Combat stats", stats);
			}
		} else if ("Summons".equals(section) && topics.containsKey("Casting")) {
			page(topics, "Casting", article("Calling a summon",
				"Carry the recipe materials and choose your summon in the Summoning panel. Casting takes five seconds and can be interrupted. You can have one manually summoned creature active at a time."));
			page(topics, "Combat", article("Fighting companions",
				"Combat summons stay until killed or dismissed. Some can absorb a share of incoming damage. See Traits for what each companion does."));
			page(topics, "Support upkeep", article("Keeping a support summon",
				"Support summons last one minute, then use life runes to stay. Each successful upkeep payment gives Summoning XP. Commanding a support summon can also earn XP as it stays active.",
				"Upkeep rises every three active minutes and recovers gradually while the summon is away. If equipment preserves the upkeep runes, Command XP can still progress."));
			page(topics, "Utility", article("Spending uses",
				"Utility summons spend uses only when they complete a task. Enchanted utility bangles give them extra uses. See Enchanting for the bonus at each tier."));
		}
	}

	static void page(Map<String, List<Article>> topics, String label, Article... articles) {
		topics.put(label, Arrays.asList(articles));
	}

	private static Article article(String title, String... paragraphs) {
		return new Article(title, Arrays.asList(paragraphs));
	}
}
