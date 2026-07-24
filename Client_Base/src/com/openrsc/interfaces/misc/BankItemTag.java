package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.defs.ItemDef;

import java.util.EnumSet;
import java.util.Locale;

enum BankItemTag {
	MINING_SMITHING("Mining & Smithing", Group.SKILLS),
	CRAFTING_LEATHER("Crafting: Leather", Group.SKILLS),
	CRAFTING_JEWELRY("Crafting: Jewelry", Group.SKILLS),
	CRAFTING_WOOD("Crafting: Wood", Group.SKILLS),
	CRAFTING_OTHER("Crafting: Other", Group.SKILLS),
	ENCHANTING("Enchanting", Group.SKILLS),
	PRAYER("Prayer", Group.SKILLS),
	HERBLAW("Herblaw", Group.SKILLS),
	COOKING("Cooking", Group.SKILLS),

	FOOD("Food", Group.ITEM_TYPES),
	TOOLS("Tools", Group.ITEM_TYPES),
	RARE_DROPS("Rare drops", Group.ITEM_TYPES),
	ARMOUR("Armour", Group.ITEM_TYPES),
	MAGIC("Magic & Summoning", Group.ITEM_TYPES),
	MELEE("Melee", Group.ITEM_TYPES),
	RANGED("Ranged", Group.ITEM_TYPES),
	JEWELRY("Jewelry", Group.ITEM_TYPES),
	QUEST_ITEMS("Quest items", Group.ITEM_TYPES);

	enum Group {
		SKILLS,
		ITEM_TYPES
	}

	final String label;
	final Group group;

	BankItemTag(String label, Group group) {
		this.label = label;
		this.group = group;
	}

	static EnumSet<BankItemTag> classify(ItemDef def) {
		EnumSet<BankItemTag> tags = EnumSet.noneOf(BankItemTag.class);
		if (def == null || def.getName() == null) {
			return tags;
		}

		String name = normalize(def.getName());
		String description = normalize(def.getDescription());
		String commands = commands(def);

		boolean rune = endsWithAny(name, "-rune", " rune", " runes")
			|| containsAny(name, "rune essence");
		boolean ore = containsAny(name, " ore") || endsWithAny(name, "ore", "coal");
		boolean bar = endsWithAny(name, " bar");
		boolean raw = startsWithAny(name, "raw ", "raw-");
		boolean gem = containsAny(name, "sapphire", "emerald", "ruby", "diamond", "dragonstone",
			"opal", "jade", "topaz");
		boolean uncutGem = gem && containsAny(name, "uncut");
		boolean jewelry = isJewelry(name);
		boolean logs = containsAny(name, "logs", " log");
		boolean bow = isBowWeapon(name);
		boolean rangedAmmo = containsAny(name, "arrow", "bolt", "dart", "throwing knife",
			"javelin", "cannonball", "cannon ball");
		boolean bones = containsAny(name, "bone");
		boolean ashes = equalsAny(name, "ashes");
		boolean demonAshes = containsAny(name, "demon ash");
		boolean staff = containsAny(name, "staff", "stave", "wand");
		boolean potion = containsAny(name, "potion", "brew", "antidote", "serum")
			|| (containsAny(commands, "drink") && containsAny(description, "dose", "potion"));
		boolean food = containsAny(commands, "eat") || isFoodDrink(name, commands);
		boolean armour = def.isWieldable() && isArmour(name);
		boolean melee = def.isWieldable() && isMeleeWeapon(name);
		boolean prayerEquipment = def.isWieldable() && isPrayerEquipment(name, description, armour);
		boolean potionIngredient = isPotionIngredient(name, description);
		boolean cookingIngredient = isCookingIngredient(name);
		boolean miningMaterial = ore || uncutGem || containsAny(name, "geode", "pickaxe", "mining helmet", "clay")
			|| isRawMiningMaterial(name);
		boolean smithingMaterial = ore || bar || containsAny(name, "hammer") && !containsAny(name, "warhammer");

		if (miningMaterial || smithingMaterial) {
			tags.add(MINING_SMITHING);
		}
		if (isCraftingLeather(name)) {
			tags.add(CRAFTING_LEATHER);
		}
		if (isCraftingJewelry(name, gem)) {
			tags.add(CRAFTING_JEWELRY);
		}
		if (logs) {
			tags.add(CRAFTING_WOOD);
		}
		if (isCraftingOther(name, bow, rangedAmmo)) {
			tags.add(CRAFTING_OTHER);
		}
		if (isEnchantingInput(name, description, rune)) {
			tags.add(ENCHANTING);
		}
		if (bones || demonAshes || prayerEquipment || containsAny(name, "prayer", "holy", "unholy", "blessed symbol",
			"symbol of saradomin", "symbol of zamorak", "symbol of guthix")) {
			tags.add(PRAYER);
		}
		if (potion || potionIngredient) {
			tags.add(HERBLAW);
		}
		if (food || raw || cookingIngredient) {
			tags.add(COOKING);
		}

		if (food) {
			tags.add(FOOD);
		}
		if (isTool(name)) {
			tags.add(TOOLS);
		}
		if (isRareDrop(name)) {
			tags.add(RARE_DROPS);
		}
		if (armour) {
			tags.add(ARMOUR);
		}
		if (rune || staff || containsAny(name, "enchanted", "magic ") || bones || ashes || demonAshes) {
			tags.add(MAGIC);
		}
		if (melee) {
			tags.add(MELEE);
		}
		if (bow || rangedAmmo || containsAny(name, "chinchompa")) {
			tags.add(RANGED);
		}
		if (jewelry) {
			tags.add(JEWELRY);
		}
		if (def.untradeable && !containsAny(description, "swap this note")) {
			tags.add(QUEST_ITEMS);
		}

		return tags;
	}

	private static boolean isFoodDrink(String name, String commands) {
		return containsAny(commands, "drink") && containsAny(name, "wine", "beer", "ale", "cider",
			"milk", "tea", "stew", "soup");
	}

	private static boolean isTool(String name) {
		return isWoodcuttingAxe(name)
			|| containsAny(name, "pickaxe", "fishing rod", "fly fishing rod", "harpoon", "lobster pot",
				"fishing net", "shears", "hammer", "chisel", "knife", "mould", "needle", "spade",
				"rake", "seed dibber", "watering can", "tinderbox", "pestle and mortar")
			|| equalsAny(name, "pot", "bucket", "bowl");
	}

	private static boolean isRareDrop(String name) {
		return endsWithAny(name, " seed")
			|| containsAny(name, "casket", "oyster", "key half", "crystal key", "geode")
			|| equalsAny(name, "dragonstone", "uncut dragonstone", "dragon shield half left");
	}

	private static boolean isEnchantingInput(String name, String description, boolean rune) {
		if (containsAny(name, "mould", "blessed", "holy", "unholy", "symbol", "enchanted")
			|| containsAny(description, "blessed by")) {
			return false;
		}
		return rune
			|| containsAny(name, "unenchant", "unenchanted")
			|| isEnchantableBaseJewelry(name)
			|| isEnchantableBaseStaff(name)
			|| isEnchantableBaseWool(name);
	}

	private static boolean isEnchantableBaseJewelry(String name) {
		return equalsAny(name,
			"gold ring", "sapphire ring", "emerald ring", "ruby ring", "diamond ring", "dragonstone ring",
			"gold necklace", "sapphire necklace", "emerald necklace", "ruby necklace", "diamond necklace",
			"dragonstone necklace", "sapphire bangel", "emerald bangel", "ruby bangel",
			"diamond bangel", "dragonstone bangel");
	}

	private static boolean isEnchantableBaseStaff(String name) {
		if (containsAny(name, "dramen", "iban", "blessed", " attuned to ")
			|| startsWithAny(name, "air ", "water ", "earth ", "fire ", "mind ", "body ", "cosmic ",
				"chaos ", "nature ", "law ", "death ", "soul ", "life ", "blood rune ")) {
			return false;
		}
		return equalsAny(name, "staff", "pine staff", "oak staff", "willow staff", "palm staff",
			"maple staff", "yew staff", "ebony staff", "magic staff", "blood staff");
	}

	private static boolean isEnchantableBaseWool(String name) {
		return startsWithAny(name, "wool ")
			&& containsAny(name, "wizard hat", "robe top", "robe skirt", "gloves", "boots");
	}

	private static boolean isJewelry(String name) {
		return containsAny(name, "amulet", "necklace", "bracelet", "bangel", "medallion", "symbol", "holy mould", "tiara")
			|| startsWithAny(name, "ring ", "ring-")
			|| containsAny(name, " ring ")
			|| endsWithAny(name, " ring", "-ring")
			|| equalsAny(name, "ring");
	}

	private static boolean isCraftingLeather(String name) {
		return containsAny(name, "thread", "needle")
			|| equalsAny(name, "leather", "hard leather")
			|| endsWithAny(name, " hide", "-hide", " leather");
	}

	private static boolean isCraftingJewelry(String name, boolean gem) {
		return isJewelryGemMaterial(name, gem)
			|| equalsAny(name, "gold nugget", "gold bar", "silver nugget", "silver bar", "wool", "ball of wool")
			|| isJewelryMould(name);
	}

	private static boolean isJewelryGemMaterial(String name, boolean gem) {
		if (!gem || isJewelry(name)) {
			return false;
		}
		return equalsAny(name, "sapphire", "emerald", "ruby", "diamond", "dragonstone", "opal", "jade",
			"red topaz", "topaz", "uncut sapphire", "uncut emerald", "uncut ruby", "uncut diamond",
			"uncut dragonstone", "uncut opal", "uncut jade", "uncut red topaz", "uncut topaz");
	}

	private static boolean isJewelryMould(String name) {
		return equalsAny(name, "ring mould", "necklace mould", "bangel mould", "holy symbol mould",
			"unholy symbol mould", "tiara mould");
	}

	private static boolean isCraftingOther(String name, boolean bow, boolean rangedAmmo) {
		return bow || rangedAmmo
			|| containsAny(name, "bow string", "bowstring", "feather", "arrow shaft", "headless arrow",
				"knife", "clay", "chisel", "glass", "spinning", "pottery");
	}

	private static boolean isPrayerEquipment(String name, String description, boolean armour) {
		boolean alignedArmour = armour && startsWithAny(name, "white ", "black ", "grey ");
		boolean unicornArmour = armour && containsAny(name, "unicorn-hide", "unicorn hide");
		return containsAny(name, "mace", "paladin shield")
			|| alignedArmour
			|| unicornArmour
			|| containsAny(description, "blessed by saradomin", "blessed by zamorak", "blessed by guthix");
	}

	private static boolean isRawMiningMaterial(String name) {
		return equalsAny(name, "stone", "silver", "silver nugget", "gold", "gold nugget", "coal", "limestone", "granite");
	}

	private static boolean isWoodcuttingAxe(String name) {
		return containsAny(name, " axe", "hatchet") && !containsAny(name, "battle axe", "battleaxe",
			"pickaxe", "throwing axe");
	}

	private static boolean isBowWeapon(String name) {
		return equalsAny(name, "bow", "longbow", "shortbow", "crossbow")
			|| endsWithAny(name, " longbow", " shortbow", " crossbow", "-longbow", "-shortbow", "-crossbow");
	}

	private static boolean isFish(String name) {
		return containsAny(name, "shrimp", "sardine", "herring", "anchov", "trout", "salmon",
			"tuna", "lobster", "swordfish", "shark", "mackerel", "cod", "bass", "pike",
			"karambwan", "eel", "fish");
	}

	private static boolean isPotionIngredient(String name, String description) {
		return containsAny(name, "herb", "vial", "eye of newt", "limpwurt root", "red spiders",
			"unicorn horn", "snape grass", "white berries", "blue dragon scale", "wine of zamorak",
			"toadflax", "avantoe", "kwuarm", "cadantine", "lantadyme", "dwarf weed", "torstol",
			"marrentill", "tarromin", "harralander", "ranarr", "irit", "guam")
			|| containsAny(description, "potion ingredient", "used in potion", "herblaw");
	}

	private static boolean isCookingIngredient(String name) {
		return containsAny(name, "flour", "grain", "wheat", "egg", "milk", "butter", "cheese",
			"tomato", "onion", "potato", "cabbage", "chocolate", "cream", "dough", "pastry",
			"pie dish", "pie plate", "cake tin", "baking pan", "bowl", "cooking apple",
			"banana", "orange", "lemon", "lime", "pineapple", "seaweed");
	}

	private static boolean isArmour(String name) {
		return containsAny(name, "armour", "armor", "helmet", "helm", "plate body", "platebody",
			"plate mail", "chain mail", "chainbody", "plate legs", "platelegs", "skirt", "shield",
			"boots", "gloves", "gauntlet", "greaves", "chaps", "vambrace", "cape", "robe",
			"coif", "hood", "mask", "defender");
	}

	private static boolean isMeleeWeapon(String name) {
		return containsAny(name, "sword", "dagger", "scimitar", "mace", "battle axe", "battleaxe",
			"warhammer", "spear", "halberd", "claws", "whip", "maul", "longsword", "shortsword",
			"2-handed", "two-handed");
	}

	private static String commands(ItemDef def) {
		if (def.getCommand() == null) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		for (String command : def.getCommand()) {
			if (command != null) {
				result.append(' ').append(command);
			}
		}
		return normalize(result.toString());
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ENGLISH);
	}

	private static boolean containsAny(String value, String... fragments) {
		for (String fragment : fragments) {
			if (value.contains(fragment)) {
				return true;
			}
		}
		return false;
	}

	private static boolean startsWithAny(String value, String... prefixes) {
		for (String prefix : prefixes) {
			if (value.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private static boolean endsWithAny(String value, String... suffixes) {
		for (String suffix : suffixes) {
			if (value.endsWith(suffix)) {
				return true;
			}
		}
		return false;
	}

	private static boolean equalsAny(String value, String... choices) {
		for (String choice : choices) {
			if (value.equals(choice)) {
				return true;
			}
		}
		return false;
	}
}
