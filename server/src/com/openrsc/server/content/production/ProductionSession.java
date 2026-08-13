package com.openrsc.server.content.production;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProductionSession {
	public static final int TYPE_SMITHING = 1;
	public static final int TYPE_CRAFTING = 2;
	public static final int TYPE_SMELTING = 3;
	public static final int TYPE_SMITHING_MATERIAL = 4;
	public static final int TYPE_FURNACE_CATEGORY = 5;
	public static final int TYPE_FURNACE_MATERIAL = 6;
	public static final int TYPE_TELEPORT_DESTINATION = 7;
	public static final int TYPE_RANGERS_REDEMPTION_CATEGORY = 8;
	public static final int TYPE_RANGERS_REDEMPTION = 9;
	public static final int TYPE_MONSTER_SLAYER_REDEMPTION = 10;
	public static final int TYPE_MONSTER_SLAYER_SHOP_CATEGORY = 11;

	private final int type;
	private final String title;
	private final int inputItemId;
	private int resourceAmount;
	private final List<ProductionRecipe> recipes;
	private final String memoryKey;
	private final PointShopDetails pointShopDetails;

	public ProductionSession(int type, String title, int inputItemId, List<ProductionRecipe> recipes) {
		this(type, title, inputItemId, 0, recipes);
	}

	public ProductionSession(int type, String title, int inputItemId, int resourceAmount, List<ProductionRecipe> recipes) {
		this(type, title, inputItemId, resourceAmount, recipes, null, null);
	}

	/** Optional key/details are used by graphical non-inventory point shops. */
	public ProductionSession(int type, String title, int inputItemId, int resourceAmount, List<ProductionRecipe> recipes,
		String memoryKey, PointShopDetails pointShopDetails) {
		if (type != TYPE_SMITHING && type != TYPE_CRAFTING && type != TYPE_SMELTING
			&& type != TYPE_SMITHING_MATERIAL && type != TYPE_FURNACE_CATEGORY
			&& type != TYPE_FURNACE_MATERIAL && type != TYPE_TELEPORT_DESTINATION
			&& type != TYPE_RANGERS_REDEMPTION_CATEGORY && type != TYPE_RANGERS_REDEMPTION
			&& type != TYPE_MONSTER_SLAYER_REDEMPTION
			&& type != TYPE_MONSTER_SLAYER_SHOP_CATEGORY) {
			throw new IllegalArgumentException("Unknown production session type: " + type);
		}
		if (title == null || title.isEmpty()) {
			throw new IllegalArgumentException("title must not be empty");
		}
		if (recipes == null || recipes.isEmpty()) {
			throw new IllegalArgumentException("recipes must not be empty");
		}
		this.type = type;
		this.title = title;
		this.inputItemId = inputItemId;
		this.resourceAmount = Math.max(0, resourceAmount);
		this.recipes = Collections.unmodifiableList(new ArrayList<>(recipes));
		this.memoryKey = memoryKey;
		this.pointShopDetails = pointShopDetails;
	}

	public int getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public int getInputItemId() {
		return inputItemId;
	}

	public int getResourceAmount() {
		return resourceAmount;
	}

	/** Live point-shop panels refresh this after a successful authoritative spend. */
	public void setResourceAmount(int resourceAmount) {
		this.resourceAmount = Math.max(0, resourceAmount);
	}

	public List<ProductionRecipe> getRecipes() {
		return recipes;
	}

	public String getMemoryKey() { return memoryKey; }
	public PointShopDetails getPointShopDetails() { return pointShopDetails; }

	public boolean isType(int expectedType) {
		return type == expectedType;
	}

	public ProductionRecipe getRecipeByItemId(int itemId) {
		for (ProductionRecipe recipe : recipes) {
			if (recipe.getItemId() == itemId) {
				return recipe;
			}
		}
		return null;
	}

	public boolean hasAnyCraftableRecipe() {
		for (ProductionRecipe recipe : recipes) {
			if (recipe.isLevelMet()) {
				return true;
			}
		}
		return false;
	}

	public int getDefaultRecipeId() {
		for (ProductionRecipe recipe : recipes) {
			if (recipe.isLevelMet() && recipe.isMaterialsMet()) {
				return recipe.getItemId();
			}
		}
		for (ProductionRecipe recipe : recipes) {
			if (recipe.isLevelMet()) {
				return recipe.getItemId();
			}
		}
		return recipes.isEmpty() ? -1 : recipes.get(0).getItemId();
	}
}
