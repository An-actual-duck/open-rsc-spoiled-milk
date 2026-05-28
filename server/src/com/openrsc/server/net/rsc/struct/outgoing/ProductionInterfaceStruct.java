package com.openrsc.server.net.rsc.struct.outgoing;

import com.openrsc.server.content.production.ProductionRecipe;
import com.openrsc.server.content.production.ProductionSession;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.AbstractStruct;

public class ProductionInterfaceStruct extends AbstractStruct<OpcodeOut> {
	public static final int ACTION_SHOW = 0;
	public static final int ACTION_HIDE = 1;
	public static final int FLAG_LEVEL_MET = 1;
	public static final int FLAG_MATERIALS_MET = 2;

	public int interfaceId;
	public int actionId;
	public String title;
	public int inputItemId;
	public int selectedRecipeId;
	public int selectedQuantity;
	public int[] itemIds;
	public int[] requiredLevels;
	public int[] inputAmounts;
	public int[] outputAmounts;
	public int[] flags;
	public int[][] ingredientItemIds;
	public int[][] ingredientFallbackItemIds;
	public int[][] ingredientAmounts;

	public static ProductionInterfaceStruct open(ProductionSession session) {
		ProductionInterfaceStruct struct = new ProductionInterfaceStruct();
		struct.interfaceId = session.getType();
		struct.actionId = ACTION_SHOW;
		struct.title = session.getTitle();
		struct.inputItemId = session.getInputItemId();
		struct.selectedRecipeId = session.getDefaultRecipeId();
		struct.selectedQuantity = 1;
		int count = session.getRecipes().size();
		struct.itemIds = new int[count];
		struct.requiredLevels = new int[count];
		struct.inputAmounts = new int[count];
		struct.outputAmounts = new int[count];
		struct.flags = new int[count];
		struct.ingredientItemIds = new int[count][];
		struct.ingredientFallbackItemIds = new int[count][];
		struct.ingredientAmounts = new int[count][];
		for (int i = 0; i < count; i++) {
			ProductionRecipe recipe = session.getRecipes().get(i);
			struct.itemIds[i] = recipe.getItemId();
			struct.requiredLevels[i] = recipe.getRequiredLevel();
			struct.inputAmounts[i] = recipe.getInputAmount();
			struct.outputAmounts[i] = recipe.getOutputAmount();
			struct.flags[i] = recipe.getFlags();
			struct.ingredientItemIds[i] = recipe.getIngredientItemIds();
			struct.ingredientFallbackItemIds[i] = recipe.getIngredientFallbackItemIds();
			struct.ingredientAmounts[i] = recipe.getIngredientAmounts();
		}
		return struct;
	}

	public static ProductionInterfaceStruct hide(int interfaceId) {
		ProductionInterfaceStruct struct = new ProductionInterfaceStruct();
		struct.interfaceId = interfaceId;
		struct.actionId = ACTION_HIDE;
		return struct;
	}
}
