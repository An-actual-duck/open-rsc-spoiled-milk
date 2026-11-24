package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import orsc.ClericTextLayout;
import orsc.GameInterfaceSettings;
import orsc.graphics.two.MudClientGraphics;
import orsc.mudclient;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class CraftingWindow {
	private static final int TEXT = 0xE6EDF2;
	private static final int MUTED = 0xA9B9C6;
	private static final int GOLD = 0xF2D393;
	private static final int BORDER = 0x516679;
	private static final int GOOD = 0xA3D3A8;
	private static final int WARN = 0xFFBC8D;
	private static final int[] QUANTITY_STEPS = {-5, -1, 1, 5};

	private final mudclient client;
	private final DoSkillInterface session;
	private final CraftingRecipeTabs tabs;
	private final AtomicInteger materialWheel = new AtomicInteger();
	private final AtomicInteger recipeWheel = new AtomicInteger();
	private int familyIndex;
	private int materialId;
	private int materialScroll;
	private int recipeScroll;
	private boolean revealSelection = true;
	private volatile Rectangle materialArea = new Rectangle();
	private volatile Rectangle recipeArea = new Rectangle();

	CraftingWindow(mudclient client, DoSkillInterface session, CraftingRecipeTabs tabs) {
		this.client = client;
		this.session = session;
		this.tabs = tabs;
		for (int i = 0; i < tabs.families.size(); i++) {
			for (Map.Entry<Integer, List<Integer>> entry : tabs.families.get(i).materials.entrySet()) {
				if (entry.getValue().contains(session.selectedRecipeIndex)) {
					familyIndex = i;
					materialId = entry.getKey();
					return;
				}
			}
		}
	}

	void scroll(int amount) {
		if (materialArea.contains(client.getMouseX(), client.getMouseY())) materialWheel.addAndGet(amount);
		else if (recipeArea.contains(client.getMouseX(), client.getMouseY())) recipeWheel.addAndGet(amount);
	}

	void render() {
		MudClientGraphics surface = client.getSurface();
		int font = GameInterfaceSettings.textSize().labelFont;
		int titleFont = GameInterfaceSettings.textSize().bodyFont;
		int line = surface.fontHeight(font);
		int gap = size(8);
		int available = Math.max(1, client.getGameWidth() - 2 * gap);
		int dockWidth = Math.min(Math.max(size(218), measure(font, "Keep window open") + 5 * gap), available / 3);
		int width = Math.min(size(600), available - dockWidth - gap);
		int x = (client.getGameWidth() - width - dockWidth - gap) / 2;
		int header = Math.max(size(37), surface.fontHeight(titleFont) + 2 * gap);
		int tabHeight = Math.max(size(26), line + gap);
		int tabColumns = Math.max(1, (width - gap) / Math.max(size(110), measure(font, "Ammunition") + 2 * gap));
		int tabRows = Math.max(1, (tabs.families.size() + tabColumns - 1) / tabColumns);
		int tabsHeight = tabRows * (tabHeight + gap / 2);
		int tooltipHeight = 2 * line + gap;
		int costHeight = Math.max(size(82), 2 * line + size(40));
		int height = Math.min(size(428), client.getGameHeight() - 2 * gap);
		int y = (client.getGameHeight() - height) / 2;
		int tabsY = y + header + gap;
		int tooltipY = tabsY + tabsHeight + gap;
		int contentY = tooltipY + tooltipHeight + gap;
		int costY = y + height - costHeight;
		int contentHeight = costY - contentY - gap;
		int materialWidth = Math.min(Math.max(size(148), measure(font, "Materials") + 3 * gap), (width - 4 * gap) / 2);
		int gridX = x + materialWidth + 2 * gap;
		int gridWidth = x + width - gap - gridX;

		frame(x, y, width, height);
		drawHeader(x, y, width, header, gap, titleFont, font);
		if (!session.isVisible()) return;
		drawFamilies(x + gap, tabsY, width - 2 * gap, tabColumns, tabHeight, gap / 2, font);
		String hover = "";
		if (!tabs.families.isEmpty()) {
			hover = drawMaterials(x + gap, contentY, materialWidth, contentHeight, tabHeight, gap, font);
			String recipeHover = drawRecipes(gridX, contentY, gridWidth, contentHeight, gap, font);
			if (!recipeHover.isEmpty()) hover = recipeHover;
		}
		String costHover = drawCosts(x + gap, costY, width - 2 * gap, costHeight - gap, gap, font);
		if (!costHover.isEmpty()) hover = costHover;
		ProductionRecipeView selected = session.getSelectedRecipe();
		if (hover.isEmpty() && selected != null) hover = recipeTooltip(selected);
		drawTooltip(hover, x + gap, tooltipY, width - 2 * gap, tooltipHeight, gap, font);
		drawControls(x + width + gap, y, dockWidth, height, header, gap, titleFont, font);
	}

	private void drawHeader(int x, int y, int width, int height, int gap, int titleFont, int font) {
		client.getSurface().drawVerticalGradient(x + 1, y + 1, width - 2, height - 1, 0x395065, 0x263B4A);
		int buttonHeight = height - gap;
		int closeWidth = Math.max(size(30), measure(font, "X") + gap);
		int start = x + gap;
		int end = x + width - gap;
		if (button(end - closeWidth, y + gap / 2, closeWidth, buttonHeight, "X", font, false, true)) {
			session.sendProductionClose();
			session.closeProductionInterface();
			return;
		}
		if (session.productionCanGoBack) {
			int backWidth = Math.max(size(50), measure(font, "Back") + gap);
			if (button(start, y + gap / 2, backWidth, buttonHeight, "Back", font, false, true)) session.sendProductionBack();
			start += backWidth + gap;
		}
		draw(fit(session.title, titleFont, end - closeWidth - gap - start),
			start, baseline(y, height, titleFont), titleFont, TEXT);
	}

	private void drawFamilies(int x, int y, int width, int columns, int height, int gap, int font) {
		int step = (width + gap) / columns;
		for (int i = 0; i < tabs.families.size(); i++) {
			CraftingRecipeTabs.Family family = tabs.families.get(i);
			if (button(x + i % columns * step, y + i / columns * (height + gap), step - gap, height,
					family.name, font, familyIndex == i, true) && familyIndex != i) {
				familyIndex = i;
				if (!family.materials.containsKey(materialId)) {
					materialId = family.materials.keySet().iterator().next();
					for (Map.Entry<Integer, List<Integer>> entry : family.materials.entrySet()) {
						if (entry.getValue().stream().anyMatch(index -> isCraftable(session.productionRecipes.get(index)))) {
							materialId = entry.getKey();
							break;
						}
					}
				}
				selectFirstRecipe();
				revealSelection = true;
			}
		}
	}

	private String drawMaterials(int x, int y, int width, int height, int tabHeight, int gap, int font) {
		int listY = y + client.getSurface().fontHeight(font) + gap;
		int rows = Math.max(1, (height - (listY - y) - tabHeight) / (tabHeight + 2));
		materialArea = new Rectangle(x, y, width, height);
		draw("Materials", x + 2, y + client.getSurface().fontHeight(font), font, MUTED);
		List<Integer> items = new ArrayList<>(tabs.families.get(familyIndex).materials.keySet());
		int maximum = Math.max(0, items.size() - rows);
		if (revealSelection) materialScroll = items.indexOf(materialId);
		materialScroll = limit(materialScroll + materialWheel.getAndSet(0), maximum);
		String hover = "";
		for (int row = 0; row < rows && row + materialScroll < items.size(); row++) {
			int id = items.get(row + materialScroll);
			int rowY = listY + row * (tabHeight + 2);
			String name = CraftingRecipeTabs.itemName(id);
			int icon = session.isValidItemId(id) ? Math.min(tabHeight, size(25)) : 0;
			if (button(x, rowY, width, tabHeight, "", font, id == materialId, true) && id != materialId) {
				materialId = id;
				selectFirstRecipe();
			}
			if (icon > 0) drawItem(id, x + 2, rowY + (tabHeight - icon) / 2, icon, icon);
			draw(fit(name, font, width - icon - 10), x + icon + 5, baseline(rowY, tabHeight, font), font, TEXT);
			if (over(x, rowY, width, tabHeight)) hover = name;
		}
		materialScroll = pageButtons(x, y + height - tabHeight, width, tabHeight, font, materialScroll, rows, maximum);
		return hover;
	}

	private String drawRecipes(int x, int y, int width, int height, int gap, int font) {
		int cellWidth = size(64);
		int cellHeight = size(47);
		int columns = Math.max(1, width / (cellWidth + gap));
		int pageHeight = Math.max(size(24), client.getSurface().fontHeight(font) + gap / 2);
		int rows = Math.max(1, (height - pageHeight) / (cellHeight + gap));
		recipeArea = new Rectangle(x, y, width, height);
		List<Integer> recipes = visibleRecipes();
		int maxScroll = Math.max(0, (recipes.size() + columns - 1) / columns - rows);
		if (revealSelection) {
			recipeScroll = Math.max(0, recipes.indexOf(session.selectedRecipeIndex) / columns - rows + 1);
			revealSelection = false;
		}
		recipeScroll = limit(recipeScroll + recipeWheel.getAndSet(0), maxScroll);
		String hover = "";
		for (int i = 0; i < columns * rows && i + recipeScroll * columns < recipes.size(); i++) {
			int index = recipes.get(i + recipeScroll * columns);
			ProductionRecipeView recipe = session.productionRecipes.get(index);
			int cellX = x + i % columns * (cellWidth + gap);
			int cellY = y + i / columns * (cellHeight + gap);
			if (button(cellX, cellY, cellWidth, cellHeight, "", font, session.selectedRecipeIndex == index, true)) {
				session.selectedRecipeIndex = index;
			}
			int spriteWidth = size(48);
			int spriteHeight = size(32);
			drawItem(recipe.getItemId(), cellX + (cellWidth - spriteWidth) / 2,
				cellY + (cellHeight - spriteHeight) / 2, spriteWidth, spriteHeight);
			if (!isCraftable(recipe)) client.getSurface().drawBoxAlpha(cellX + 1, cellY + 1, cellWidth - 2, cellHeight - 2, 0, 100);
			if (over(cellX, cellY, cellWidth, cellHeight)) hover = recipeTooltip(recipe);
		}
		recipeScroll = pageButtons(x, y + height - pageHeight, width, pageHeight, font, recipeScroll, rows, maxScroll);
		return hover;
	}

	private List<Integer> visibleRecipes() {
		return tabs.families.get(familyIndex).materials.get(materialId);
	}

	private void selectFirstRecipe() {
		List<Integer> recipes = visibleRecipes();
		session.selectedRecipeIndex = recipes.get(0);
		for (int index : recipes) {
			if (isCraftable(session.productionRecipes.get(index))) {
				session.selectedRecipeIndex = index;
				break;
			}
		}
		recipeScroll = 0;
		recipeWheel.set(0);
	}

	private String drawCosts(int x, int y, int width, int height, int gap, int font) {
		client.getSurface().drawBoxAlpha(x, y, width, height, 0x131F2A, 245);
		client.getSurface().drawBoxBorder(x, width, y, height, BORDER);
		draw("Materials per craft", x + gap, y + gap + client.getSurface().fontHeight(font) - 2, font, MUTED);
		ProductionRecipeView recipe = session.getSelectedRecipe();
		if (recipe == null) return "";
		boolean detailed = recipe.hasIngredientDetails();
		int count = detailed ? recipe.getIngredientCount() : session.isValidItemId(session.inputItemId) ? 1 : 0;
		if (count == 0) {
			draw(recipe.getInputAmount() > 0 ? "Materials required: " + recipe.getInputAmount() : "No materials required",
				x + gap, y + height - gap, font, TEXT);
			return "";
		}
		int iconWidth = size(38);
		int iconHeight = size(27);
		int step = Math.min(size(94), (width - 2 * gap) / count);
		int top = y + gap + client.getSurface().fontHeight(font);
		String hover = "";
		for (int i = 0; i < count; i++) {
			int item = detailed ? recipe.getIngredientItemId(i) : session.inputItemId;
			int fallback = detailed ? recipe.getIngredientFallbackItemId(i) : -1;
			int required = detailed ? recipe.getIngredientAmount(i) : recipe.getInputAmount();
			if (!session.isValidItemId(item)) continue;
			int owned = session.getOwnedIngredientCount(item, fallback);
			int start = x + gap + i * step;
			drawItem(session.getDisplayIngredientItemId(item, fallback, required),
				start + (step - iconWidth) / 2, top, iconWidth, iconHeight);
			center(fit(owned + "/" + required, font, step - 2), start, y + height - gap,
				step, font, owned >= required ? GOOD : WARN);
			if (over(start, top, step, y + height - top)) hover = session.ingredientTooltip(item, fallback, owned, required);
		}
		return hover;
	}

	private void drawTooltip(String text, int x, int y, int width, int height, int gap, int font) {
		client.getSurface().drawBoxAlpha(x, y, width, height, 0x131F2A, 230);
		List<String> lines = ClericTextLayout.wrap(text, width - 2 * gap, value -> measure(font, value));
		int line = client.getSurface().fontHeight(font);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			String row = lines.get(i);
			if (i == 1 && lines.size() > 2) row = fit(row + " " + lines.get(2) + "...", font, width - 2 * gap);
			draw(row, x + gap, y + (i + 1) * line + gap / 2 - 2, font, GOLD);
		}
	}

	private void drawControls(int x, int y, int width, int height, int header, int gap, int titleFont, int font) {
		frame(x, y, width, height);
		client.getSurface().drawVerticalGradient(x + 1, y + 1, width - 2, header - 1, 0x395065, 0x263B4A);
		draw("Crafting controls", x + gap, baseline(y, header, titleFont), titleFont, TEXT);
		int left = x + gap;
		int inner = width - 2 * gap;
		int line = client.getSurface().fontHeight(font);
		int buttonHeight = Math.max(size(26), line + gap);
		int row = y + header + gap;
		center("Quantity", left, row + line, inner, font, MUTED);
		row += line + gap / 2;
		boolean active = session.hasCraftingProgress();
		int stepWidth = Math.max(1, (inner - 4 * gap / 2) / 6);
		int numberWidth = inner - 4 * stepWidth - 4 * (gap / 2);
		int buttonGap = gap / 2;
		int numberX = left + 2 * (stepWidth + buttonGap);
		button(numberX, row, numberWidth, buttonHeight, Integer.toString(session.productionQuantity), font, false, false);
		for (int i = 0; i < QUANTITY_STEPS.length; i++) {
			int delta = QUANTITY_STEPS[i];
			int buttonX = left + i * (stepWidth + buttonGap);
			if (i >= 2) buttonX += numberWidth + buttonGap;
			if (button(buttonX, row, stepWidth, buttonHeight, delta > 0 ? "+" + delta : Integer.toString(delta), font, false, !active)) {
				session.productionQuantity = Math.max(1, Math.min(DoSkillInterface.PRODUCTION_ALL_QUANTITY, session.productionQuantity + delta));
			}
		}
		row += buttonHeight + gap;
		ProductionRecipeView recipe = session.getSelectedRecipe();
		boolean canStart = !active && recipe != null && isCraftable(recipe);
		int half = (inner - gap) / 2;
		if (button(left, row, half, buttonHeight, "Start", font, canStart, canStart)) {
			session.sendProductionStart(session.productionQuantity);
		}
		if (button(left + half + gap, row, inner - half - gap, buttonHeight, "All", font, false, canStart)) {
			session.sendProductionStart(DoSkillInterface.PRODUCTION_ALL_QUANTITY);
		}
		row += buttonHeight + 2 * gap;
		if (session.productionRememberSupported) {
			if (checkbox(left, row, inner, buttonHeight, "Remember last input", font, session.productionRememberEnabled, true)) {
				session.productionRememberEnabled = !session.productionRememberEnabled;
				session.sendProductionRememberPreference();
			}
			row += buttonHeight;
		}
		if (session.productionKeepOpenSupported) {
			if (checkbox(left, row, inner, buttonHeight, "Keep window open", font, session.productionKeepOpenEnabled, !active)) {
				session.productionKeepOpenEnabled = !session.productionKeepOpenEnabled;
				session.sendProductionKeepOpenPreference();
			}
			row += buttonHeight;
		}
		if (active && session.hasInWindowCraftingProgress()) {
			int progressY = Math.max(row + gap, y + height - 3 * buttonHeight - gap);
			draw(fit(session.craftingProgressName(), font, inner), left, progressY + line, font, GOLD);
			session.drawCraftingProgress(left, progressY + buttonHeight, inner, buttonHeight, font);
			if (button(left, progressY + 2 * buttonHeight + gap / 2, inner, buttonHeight, "Cancel", font, false, true)) {
				session.sendCancelProductionBatch();
			}
		}
	}

	private boolean checkbox(int x, int y, int width, int height, String text, int font, boolean checked, boolean enabled) {
		int square = Math.max(size(16), measure(font, "X") + 2);
		boolean clicked = button(x, y + (height - square) / 2, square, square, checked ? "X" : "", font, checked, enabled);
		draw(fit(text, font, width - square - size(6)), x + square + size(6), baseline(y, height, font), font, enabled ? TEXT : MUTED);
		return clicked || enabled && click(x + square, y, width - square, height);
	}

	private int pageButtons(int x, int y, int width, int height, int font, int offset, int step, int maximum) {
		if (maximum == 0) return 0;
		int button = Math.max(size(22), measure(font, "<") + 8);
		if (button(x, y, button, height, "<", font, false, offset > 0)) offset -= step;
		if (button(x + width - button, y, button, height, ">", font, false, offset < maximum)) offset += step;
		return limit(offset, maximum);
	}

	private boolean isCraftable(ProductionRecipeView recipe) {
		return recipe.isLevelMet() && session.hasCraftingMaterials(recipe);
	}

	private String recipeTooltip(ProductionRecipeView recipe) {
		String name = CraftingRecipeTabs.itemName(recipe.getItemId());
		String hint = name + "  |  Level " + recipe.getRequiredLevel() + "  |  Makes " + recipe.getOutputAmount();
		if (!recipe.isLevelMet()) return hint + "  |  Level too low";
		return session.hasCraftingMaterials(recipe) ? hint : hint + "  |  Missing materials";
	}

	private void frame(int x, int y, int width, int height) {
		client.getSurface().drawBoxAlpha(x, y, width, height, 0x1B2935, 242);
		client.getSurface().drawBoxBorder(x, width, y, height, BORDER);
	}

	private boolean button(int x, int y, int width, int height, String text, int font, boolean selected, boolean enabled) {
		boolean hover = enabled && over(x, y, width, height);
		int background = selected ? 0x405C65 : hover ? 0x415468 : enabled ? 0x2C3C50 : 0x202C38;
		client.getSurface().drawBoxAlpha(x, y, width, height, background, 245);
		client.getSurface().drawBoxBorder(x, width, y, height, selected ? 0xA8BB9F : BORDER);
		if (!text.isEmpty()) center(fit(text, font, width - 4), x, baseline(y, height, font), width, font, enabled ? TEXT : MUTED);
		return hover && click(x, y, width, height);
	}

	private void drawItem(int id, int x, int y, int width, int height) {
		if (!session.isValidItemId(id)) return;
		ItemDef def = EntityHandler.getItemDef(id);
		client.getSurface().drawSpriteClipping(client.spriteSelect(def), x, y, width, height,
			def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
	}

	private boolean click(int x, int y, int width, int height) {
		if (client.getMouseClick() != 1 || !over(x, y, width, height)) return false;
		client.setMouseClick(0);
		return true;
	}

	private boolean over(int x, int y, int width, int height) {
		int mouseX = client.getMouseX();
		int mouseY = client.getMouseY();
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private void center(String text, int x, int y, int width, int font, int color) {
		draw(text, x + (width - measure(font, text)) / 2, y, font, color);
	}

	private void draw(String text, int x, int y, int font, int color) {
		client.getSurface().drawString(text, x, y, color, font);
	}

	private int measure(int font, String text) {
		return client.getSurface().stringWidth(font, text);
	}

	private int baseline(int y, int height, int font) {
		return y + (height + client.getSurface().fontHeight(font)) / 2 - 2;
	}

	private String fit(String text, int font, int width) {
		if (measure(font, text) <= width) return text;
		int end = text.length();
		while (end > 0 && measure(font, text.substring(0, end) + "...") > width) end--;
		return end == 0 ? "" : text.substring(0, end).trim() + "...";
	}

	private static int size(int pixels) {
		return GameInterfaceSettings.size().pixels(pixels);
	}

	private static int limit(int value, int maximum) {
		return Math.max(0, Math.min(value, maximum));
	}
}
