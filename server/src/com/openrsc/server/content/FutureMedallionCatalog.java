package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.custom.MyWorldItemId;

/**
 * Definition-only metadata for the future Medallion jewelry family.
 *
 * Medallions deliberately remain absent from production menus, altar inputs,
 * shops, guides, and active effect lookups. The gate constants make enabling
 * those systems an explicit later design decision instead of an accidental
 * consequence of adding item definitions.
 */
public final class FutureMedallionCatalog {
	public static final boolean PRODUCTION_ENABLED = false;
	public static final boolean ALTAR_ENCHANTING_ENABLED = false;

	private static final Recipe[] RECIPES = {
		new Recipe(MyWorldItemId.SAPPHIRE_MEDALLION, ItemId.SAPPHIRE.id(), 13, 260, 1),
		new Recipe(MyWorldItemId.EMERALD_MEDALLION, ItemId.EMERALD.id(), 26, 280, 2),
		new Recipe(MyWorldItemId.RUBY_MEDALLION, ItemId.RUBY.id(), 44, 340, 3),
		new Recipe(MyWorldItemId.DIAMOND_MEDALLION, ItemId.DIAMOND.id(), 60, 400, 4),
		new Recipe(MyWorldItemId.DRAGONSTONE_MEDALLION, ItemId.DRAGONSTONE.id(), 70, 600, 5)
	};

	private FutureMedallionCatalog() {
	}

	public static boolean contains(final int itemId) {
		return getRecipe(itemId) != null;
	}

	public static Recipe getRecipe(final int itemId) {
		for (Recipe recipe : RECIPES) {
			if (recipe.itemId == itemId) {
				return recipe;
			}
		}
		return null;
	}

	public static Recipe[] recipes() {
		return RECIPES.clone();
	}

	public static final class Recipe {
		private final int itemId;
		private final int gemId;
		private final int craftingLevel;
		private final int craftingExperience;
		private final int enchantingTier;

		private Recipe(final int itemId, final int gemId, final int craftingLevel,
			final int craftingExperience, final int enchantingTier) {
			this.itemId = itemId;
			this.gemId = gemId;
			this.craftingLevel = craftingLevel;
			this.craftingExperience = craftingExperience;
			this.enchantingTier = enchantingTier;
		}

		public int getItemId() {
			return itemId;
		}

		public int getGemId() {
			return gemId;
		}

		public int getBarId() {
			return ItemId.SILVER_BAR.id();
		}

		public int getCraftingLevel() {
			return craftingLevel;
		}

		public int getCraftingExperience() {
			return craftingExperience;
		}

		public int getEnchantingTier() {
			return enchantingTier;
		}
	}
}
