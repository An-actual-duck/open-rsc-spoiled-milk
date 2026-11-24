package com.openrsc.interfaces.misc;

import java.util.List;
import orsc.ClericTextLayout;
import orsc.mudclient;

final class PointShopWindow extends ShopWindow {
	private static final int[] AMOUNTS = {1, 5, 10, 50};
	private final DoSkillInterface session;
	private String[] labels;
	private int tab;
	private long tabRequestAt;
	private int detailLines;

	PointShopWindow(mudclient client, DoSkillInterface session) {
		super(client);
		this.session = session;
	}

	void update(String[] labels, int tab) {
		this.labels = labels;
		if (tab != this.tab) firstItem = 0;
		this.tab = tab;
		tabRequestAt = 0L;
		detailLines = session.isMonsterSlayerRedemptionInterface() ? 6 : 3;
		for (ProductionRecipeView recipe : session.productionRecipes) {
			detailLines = Math.max(detailLines, 3 + recipe.getIngredientCount());
		}
	}

	@Override String title() { return session.isMonsterSlayerRedemptionInterface() ? "Monster Slayer rewards" : "Rangers Guild rewards"; }
	@Override String[] tabs() { return labels; }
	@Override int currentTab() { return tab; }
	@Override void selectTab(int tab) {
		if (awaitingTab()) return;
		tabRequestAt = System.nanoTime();
		session.sendPointShopTab(tab);
	}
	@Override int itemCount() { return session.productionRecipes.size(); }
	@Override boolean includes(int index) { return true; }
	@Override int itemId(int index) { return session.productionRecipes.get(index).getItemId(); }
	@Override int selectedIndex() { return session.selectedRecipeIndex; }
	@Override void selectItem(int index) {
		if (!awaitingTab()) session.selectedRecipeIndex = index;
	}
	@Override String itemName(int index) {
		ProductionRecipeView recipe = session.productionRecipes.get(index);
		return CraftingRecipeTabs.itemName(recipe.getItemId()) + (recipe.getOutputAmount() == 1 ? "" : " x" + recipe.getOutputAmount());
	}
	@Override String itemBadge(int index) {
		int amount = session.productionRecipes.get(index).getStockAmount();
		return amount < 0 ? "" : Integer.toString(amount);
	}

	@Override void close() {
		session.sendProductionClose();
		session.closeProductionInterface();
	}

	@Override int detailLineCount() { return detailLines; }

	@Override
	void drawDetails(int index, int x, int y, int width, int line, int font) {
		ProductionRecipeView recipe = session.productionRecipes.get(index);
		int quantity = session.productionQuantity;
		draw(fit(itemName(index) + " per purchase", font, width), x, y, font, TEXT);
		y += line;
		String cost = "Cost: " + session.pointCostText(recipe) + "   |   Balance: " + session.pointBalancesText(recipe);
		int colour = session.canAffordPointCost(recipe) ? GOOD : WARN;
		List<String> price = ClericTextLayout.wrap(cost, width, text -> measure(font, text));
		for (int i = 0; i < Math.min(2, price.size()); i++) {
			draw(fit(price.get(i), font, width), x, y, font, colour);
			y += line;
		}
		for (int i = 0; i < recipe.getIngredientCount(); i++) {
			int id = recipe.getIngredientItemId(i);
			int fallback = recipe.getIngredientFallbackItemId(i);
			int owned = session.getOwnedIngredientCount(id, fallback);
			int required = recipe.getIngredientAmount(i);
			long needed = (long) required * quantity;
			String amounts = owned + " / " + needed;
			String name = CraftingRecipeTabs.itemName(session.getDisplayIngredientItemId(id, fallback, required));
			int amountsWidth = measure(font, amounts);
			draw(fit(name, font, width - amountsWidth - size(6)), x, y, font, TEXT);
			draw(amounts, x + width - amountsWidth, y, font, owned >= needed ? GOOD : WARN);
			y += line;
		}
	}

	@Override
	void drawControls(int index, int x, int y, int width, int height, int gap, int line, int font) {
		boolean enabled = index >= 0 && !awaitingTab();
		draw(fit("Purchase quantity", font, width), x, y + line, font, TEXT);
		y += line + gap;
		int row = Math.max(line + gap, Math.min(size(30), (height - 2 * line - 5 * gap) / 5));
		int step = (width + gap / 2) / 2;
		int buttonWidth = step - gap / 2;
		for (int i = 0; i < AMOUNTS.length; i++) {
			int amount = AMOUNTS[i];
			if (button(x + i % 2 * step, y + i / 2 * (row + gap / 2), buttonWidth, row,
				Integer.toString(amount), font, session.productionQuantity == amount, enabled)) {
				session.productionQuantity = amount;
			}
		}
		y += 2 * (row + gap / 2);
		if (button(x, y, buttonWidth, row, "-", font, false, enabled && session.productionQuantity > 1)) session.productionQuantity--;
		if (button(x + step, y, buttonWidth, row, "+", font, false, enabled && session.productionQuantity < 1000000)) {
			session.productionQuantity++;
		}
		y += row + gap;
		ProductionRecipeView selected = session.getSelectedRecipe();
		if (selected != null) {
			long units = (long) selected.getOutputAmount() * session.productionQuantity;
			draw(fit("Quantity: " + session.productionQuantity + "   Items: " + units, font, width), x, y + line, font, TEXT);
		}
		y += line + gap;
		boolean canPurchase = enabled && selected != null && selected.isLevelMet() && session.canAffordPointCost(selected)
			&& (selected.getStockAmount() < 0 || selected.getStockAmount() >= session.productionQuantity);
		if (button(x, y, width, row, "Purchase", font, false, canPurchase)) {
			session.sendProductionStart(session.productionQuantity);
		}
	}

	private boolean awaitingTab() {
		return tabRequestAt != 0L && System.nanoTime() - tabRequestAt < 2_000_000_000L;
	}
}
