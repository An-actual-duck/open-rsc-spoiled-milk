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

	private final int type;
	private final String title;
	private final int inputItemId;
	private final List<ProductionRecipe> recipes;

	public ProductionSession(int type, String title, int inputItemId, List<ProductionRecipe> recipes) {
		if (type != TYPE_SMITHING && type != TYPE_CRAFTING && type != TYPE_SMELTING
			&& type != TYPE_SMITHING_MATERIAL && type != TYPE_FURNACE_CATEGORY
			&& type != TYPE_FURNACE_MATERIAL) {
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
		this.recipes = Collections.unmodifiableList(new ArrayList<>(recipes));
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

	public List<ProductionRecipe> getRecipes() {
		return recipes;
	}

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
