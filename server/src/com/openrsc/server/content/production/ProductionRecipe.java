package com.openrsc.server.content.production;

import com.openrsc.server.net.rsc.struct.outgoing.ProductionInterfaceStruct;

public class ProductionRecipe {
	private final int itemId;
	private final int requiredLevel;
	private final int inputAmount;
	private final int outputAmount;
	private final boolean levelMet;
	private final boolean materialsMet;
	private final int[] ingredientItemIds;
	private final int[] ingredientFallbackItemIds;
	private final int[] ingredientAmounts;

	public ProductionRecipe(int itemId, int requiredLevel, int inputAmount, int outputAmount,
		boolean levelMet, boolean materialsMet) {
		this(itemId, requiredLevel, inputAmount, outputAmount, levelMet, materialsMet,
			null, null, null);
	}

	public ProductionRecipe(int itemId, int requiredLevel, int inputAmount, int outputAmount,
		boolean levelMet, boolean materialsMet, int[] ingredientItemIds,
		int[] ingredientFallbackItemIds, int[] ingredientAmounts) {
		if (inputAmount < 1) {
			throw new IllegalArgumentException("inputAmount must be at least 1");
		}
		if (outputAmount < 1) {
			throw new IllegalArgumentException("outputAmount must be at least 1");
		}
		if ((ingredientItemIds == null) != (ingredientAmounts == null)
			|| (ingredientItemIds == null) != (ingredientFallbackItemIds == null)) {
			throw new IllegalArgumentException("ingredient arrays must be provided together");
		}
		if (ingredientItemIds != null
			&& (ingredientItemIds.length != ingredientAmounts.length
			|| ingredientItemIds.length != ingredientFallbackItemIds.length)) {
			throw new IllegalArgumentException("ingredient arrays must have equal length");
		}
		this.itemId = itemId;
		this.requiredLevel = requiredLevel;
		this.inputAmount = inputAmount;
		this.outputAmount = outputAmount;
		this.levelMet = levelMet;
		this.materialsMet = materialsMet;
		this.ingredientItemIds = ingredientItemIds == null ? new int[0] : ingredientItemIds.clone();
		this.ingredientFallbackItemIds = ingredientFallbackItemIds == null ? new int[0] : ingredientFallbackItemIds.clone();
		this.ingredientAmounts = ingredientAmounts == null ? new int[0] : ingredientAmounts.clone();
	}

	public int getItemId() {
		return itemId;
	}

	public int getRequiredLevel() {
		return requiredLevel;
	}

	public int getInputAmount() {
		return inputAmount;
	}

	public int getOutputAmount() {
		return outputAmount;
	}

	public int[] getIngredientItemIds() {
		return ingredientItemIds.clone();
	}

	public int[] getIngredientFallbackItemIds() {
		return ingredientFallbackItemIds.clone();
	}

	public int[] getIngredientAmounts() {
		return ingredientAmounts.clone();
	}

	public boolean isLevelMet() {
		return levelMet;
	}

	public boolean isMaterialsMet() {
		return materialsMet;
	}

	public int getFlags() {
		int flags = 0;
		if (isLevelMet()) {
			flags |= ProductionInterfaceStruct.FLAG_LEVEL_MET;
		}
		if (isMaterialsMet()) {
			flags |= ProductionInterfaceStruct.FLAG_MATERIALS_MET;
		}
		return flags;
	}
}
