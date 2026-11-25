package com.openrsc.interfaces.misc;

import java.util.Arrays;
import orsc.mudclient;

/** Stocked shops share the shop protocol for purchases and sales. */
public final class StandardShopWindow extends ShopWindow {
	private static final int[] AMOUNTS = {1, 5, 10, 50};
	private static final String[] FLAT = new String[0];
	private String[] labels = FLAT;
	private String[] categories = FLAT;
	private int tab;
	private int count;
	private boolean general;

	public StandardShopWindow(mudclient client) {
		super(client);
	}

	public void update(boolean general, String[] categories, int count) {
		String previous = tab > 0 ? labels[tab] : "";
		this.general = general;
		this.categories = categories;
		this.count = count;
		this.labels = Arrays.stream(categories).filter(name -> name != null && !name.isEmpty()).distinct().toArray(String[]::new);
		if (labels.length > 0) {
			String[] tabs = new String[labels.length + 1];
			tabs[0] = "All stock";
			System.arraycopy(labels, 0, tabs, 1, labels.length);
			labels = tabs;
		}
		tab = 0;
		for (int i = 1; i < labels.length; i++) if (labels[i].equals(previous)) tab = i;
		int selection = client.getShopSelectedItemType();
		selectItem(-1);
		for (int i = 0; selection >= 0 && i < count; i++) {
			if (itemId(i) == selection && includes(i)) {
				selectItem(i);
				break;
			}
		}
	}

	@Override String title() { return general ? "General store" : "Shop"; }
	@Override String[] tabs() { return labels; }
	@Override int currentTab() { return tab; }
	@Override void selectTab(int tab) {
		this.tab = tab;
		if (selectedIndex() >= 0 && !includes(selectedIndex())) selectItem(-1);
	}
	@Override int itemCount() { return count; }
	@Override boolean includes(int index) { return tab == 0 || index < categories.length && labels[tab].equals(categories[index]); }
	@Override int itemId(int index) { return client.getShopCategoryID(index); }
	@Override int selectedIndex() { return client.getShopSelectedItemIndex(); }
	@Override void selectItem(int index) {
		client.setShopSelectedItemIndex(index);
		client.setShopSelectedItemType(index < 0 ? -2 : itemId(index));
	}
	@Override String itemBadge(int index) { return Integer.toString(client.getShopStock(index)); }
	@Override String itemName(int index) { return CraftingRecipeTabs.itemName(itemId(index)); }
	@Override void close() { client.closeShopPreview(); }
	@Override boolean acceptsInput() { return client.shopPreviewInputAvailable(); }
	@Override int detailLineCount() { return 2; }

	@Override
	void drawDetails(int index, int x, int y, int width, int line, int font) {
		draw(fit(itemName(index), font, width), x, y, font, TEXT);
		String stock = "In stock: " + client.getShopStock(index) + "    In inventory: " + client.getInventoryCount(itemId(index));
		draw(fit(stock, font, width), x, y + line, font, MUTED);
	}

	@Override
	void drawControls(int index, int x, int y, int width, int height, int gap, int line, int font) {
		int section = (height - gap) / 2;
		int stock = index < 0 ? 0 : client.getShopStock(index);
		int owned = index < 0 ? 0 : client.getInventoryCount(itemId(index));
		drawSection(index, true, stock, x, y, width, section, gap, line, font);
		drawSection(index, false, owned, x, y + section + gap, width, section, gap, line, font);
	}

	private void drawSection(int index, boolean buy, int available, int x, int y, int width, int height, int gap, int line, int font) {
		frame(x, y, width, height);
		int inset = gap / 2;
		int inner = width - 2 * inset;
		int left = x + inset;
		int row = y + inset + line;
		draw(buy ? "Buy" : "Sell", left, row, font, buy ? GOOD : GOLD);
		row += line + inset;
		String quote = index < 0 ? "" : available == 0
			? (buy ? "Out of stock" : "None in inventory") : client.getShopUnitPrice(index, buy) + " coins for one";
		draw(fit(quote, font, inner), left, row, font, available > 0 ? TEXT : MUTED);
		int buttonHeight = Math.max(line + 2, Math.min(size(29), (height - 2 * line - 5 * inset) / 2));
		int step = inner / 3;
		int buttonY = row + inset;
		boolean enabled = available > 0 && client.shopPreviewInputAvailable();
		for (int i = 0; i < 6; i++) {
			boolean all = i == AMOUNTS.length;
			boolean custom = i > AMOUNTS.length;
			String label = all ? "All" : custom ? "X" : Integer.toString(AMOUNTS[i]);
			if (button(left + i % 3 * step, buttonY + i / 3 * (buttonHeight + inset),
				step - inset, buttonHeight, label, font, false, enabled)) {
				if (custom) client.promptShopQuantity(index, buy);
				else client.sendShopTransaction(index, buy, all ? available : Math.min(available, AMOUNTS[i]), all);
			}
		}
	}
}
