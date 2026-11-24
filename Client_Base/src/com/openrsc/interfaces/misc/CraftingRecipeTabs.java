package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Groups the recipes supplied for the current production interaction. */
final class CraftingRecipeTabs {
	final List<Family> families = new ArrayList<>();

	CraftingRecipeTabs(List<ProductionRecipeView> recipes, int inputItemId) {
		Map<String, Family> byName = new LinkedHashMap<>();
		for (int i = 0; i < recipes.size(); i++) {
			ProductionRecipeView recipe = recipes.get(i);
			String familyName = familyName(itemName(recipe.getItemId()));
			Family family = byName.computeIfAbsent(familyName, Family::new);
			int material = materialId(recipe, inputItemId, familyName);
			family.materials.computeIfAbsent(material, ignored -> new ArrayList<>()).add(i);
		}
		families.addAll(byName.values());
	}

	static String itemName(int itemId) {
		ItemDef def = itemId >= 0 && itemId < EntityHandler.itemCount() ? EntityHandler.getItemDef(itemId) : null;
		return def == null ? "Materials" : def.getName();
	}

	private static int materialId(ProductionRecipeView recipe, int inputId, String family) {
		boolean jewelry = family.equals("Rings") || family.equals("Necklaces")
			|| family.equals("Amulets") || family.equals("Bangles");
		if (jewelry && itemName(inputId).equalsIgnoreCase("gold bar")) {
			for (int i = 0; i < recipe.getIngredientCount(); i++) {
				int ingredient = recipe.getIngredientItemId(i);
				if (ingredient != inputId && !itemName(ingredient).toLowerCase(Locale.ROOT).endsWith("mould")) {
					return ingredient;
				}
			}
		}
		return recipe.hasIngredientDetails() ? recipe.getIngredientItemId(0) : inputId;
	}

	private static String familyName(String name) {
		String item = " " + name.toLowerCase(Locale.ROOT).replace('-', ' ') + " ";
		if (hasWord(item, "ring")) return "Rings";
		if (hasWord(item, "necklace")) return "Necklaces";
		if (hasWord(item, "amulet")) return "Amulets";
		if (hasWord(item, "bangle")) return "Bangles";
		if (hasWord(item, "sigil")) return "Sigils";
		if (hasWord(item, "symbol")) return "Symbols";
		if (hasWord(item, "longbow", "shortbow", "crossbow")) return "Bows";
		if (hasWord(item, "arrow", "arrows", "arrowheads", "shafts", "bolts", "dart", "darts", "shuriken",
				"throwing", "cannonball", "cannonballs")) return "Ammunition";
		if (hasWord(item, "bar", "bars")) return "Bars";
		if (item.contains(" plate mail ") || item.contains(" chain mail ")
				|| hasWord(item, "helm", "helmet", "shield", "kiteshield", "platebody", "platemail", "platelegs",
				"chainskirt", "chainmail", "plateskirt", "vambraces", "chaps", "cowl", "coif", "armour", "armor")) return "Armour";
		if (hasWord(item, "sword", "longsword", "shortsword", "dagger", "mace", "spear", "scimitar", "battleaxe",
				"halberd", "claws", "blade", "warhammer")) return "Weapons";
		if (hasWord(item, "apron", "boots", "gloves", "hat", "cape", "scarf", "robe", "jacket", "jumper",
				"sweater", "socks", "mittens", "beanie", "body", "top", "trousers", "skirt")) return "Clothing";
		if (hasWord(item, "pickaxe", "axe", "hatchet", "nails", "hammer", "knife", "chisel", "shears", "wire")) return "Tools";
		if (hasWord(item, "pot", "bowl", "dish", "jug", "vial", "bottle", "glass", "pie")) return "Containers";
		return "Items";
	}

	private static boolean hasWord(String item, String... words) {
		for (String word : words) {
			if (item.contains(" " + word + " ")) return true;
		}
		return false;
	}

	static final class Family {
		final String name;
		final Map<Integer, List<Integer>> materials = new LinkedHashMap<>();

		Family(String name) {
			this.name = name;
		}
	}
}
