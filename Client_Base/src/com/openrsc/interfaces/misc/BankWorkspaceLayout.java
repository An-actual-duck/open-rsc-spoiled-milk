package com.openrsc.interfaces.misc;

import java.awt.Rectangle;
import orsc.GameInterfaceSettings;
import orsc.mudclient;

/** Shared screen bounds for the bank preview's drawing, scrolling and hit tests. */
final class BankWorkspaceLayout {
	final Rectangle filters;
	final Rectangle bank;
	final Rectangle actions;
	final Rectangle stock;
	final Rectangle filterList;
	final Rectangle carried;
	final int font;
	final int titleFont;
	final int line;
	final int gap;
	final int header;
	final int button;
	final int tabHeight;
	final int stockColumns;
	final int stockRows;
	final int stockCellWidth;
	final int stockCellHeight;
	final int filterRowHeight;

	BankWorkspaceLayout(mudclient client, boolean hasPresets, boolean hasNotes) {
		font = GameInterfaceSettings.textSize().labelFont;
		titleFont = GameInterfaceSettings.textSize().bodyFont;
		line = client.getSurface().fontHeight(font);
		gap = size(8);
		header = Math.max(size(34), client.getSurface().fontHeight(titleFont) + gap);
		button = Math.max(size(25), line + 4);
		tabHeight = Math.max(size(35), line + 6);
		int windowWidth = Math.min(size(1180), client.getGameWidth() - 2 * gap);
		int filtersWidth = Math.min(windowWidth / 4,
			Math.max(size(190), client.getSurface().stringWidth(font, "Crafting: Jewelry") + 5 * gap));
		int actionsWidth = Math.min(windowWidth / 3,
			Math.max(size(255), client.getSurface().stringWidth(font, "Deposit all inventory") + 4 * gap));
		int bankWidth = windowWidth - filtersWidth - actionsWidth - 2 * gap;
		int bottom = client.getCraftingPanelBottom();
		int height = Math.min(size(570), bottom - 2 * gap);
		int x = (client.getGameWidth() - windowWidth) / 2;
		int y = (bottom - height) / 2;
		filters = new Rectangle(x, y, filtersWidth, height);
		bank = new Rectangle(x + filtersWidth + gap, y, bankWidth, height);
		actions = new Rectangle(bank.x + bank.width + gap, y, actionsWidth, height);

		int stockWidth = bank.width - 2 * gap;
		stockColumns = Math.max(1, stockWidth / Math.max(size(54), client.getSurface().stringWidth(font, "999M") + 4));
		stockCellWidth = stockWidth / stockColumns;
		stockCellHeight = Math.max(size(39), line + 8);
		int stockTop = y + header + tabHeight + line + 3 * gap;
		int footer = button + line + 2 * gap;
		stockRows = Math.max(1, (y + height - stockTop - footer) / stockCellHeight);
		stock = new Rectangle(bank.x + gap, stockTop, stockColumns * stockCellWidth, stockRows * stockCellHeight);

		int filterTop = y + header + 3 * (button + gap) + line + gap;
		filterRowHeight = Math.max(size(18), line + 2);
		filterList = new Rectangle(filters.x + gap, filterTop, filters.width - 2 * gap,
			Math.max(1, (y + height - filterTop - button - 2 * gap) / filterRowHeight) * filterRowHeight);
		int actionRows = 3 + (hasPresets ? 2 : 0) + (hasNotes ? 1 : 0);
		int carryTop = y + header + gap + actionRows * (button + gap / 2) + 2 * line + gap;
		carried = new Rectangle(actions.x + gap, carryTop, actionsWidth - 2 * gap, y + height - carryTop - gap);
	}

	boolean contains(int x, int y) {
		return filters.contains(x, y) || bank.contains(x, y) || actions.contains(x, y);
	}

	static int size(int pixels) {
		return GameInterfaceSettings.size().pixels(pixels);
	}
}
