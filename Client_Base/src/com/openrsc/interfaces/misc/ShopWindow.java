package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import orsc.GameInterfaceSettings;
import orsc.mudclient;

import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared scaled catalog, tabs and quantity dock for shops. */
abstract class ShopWindow {
	static final int TEXT = 0xE6EDF2;
	static final int MUTED = 0xA9B9C6;
	static final int GOLD = 0xF2D393;
	static final int GOOD = 0xA3D3A8;
	static final int WARN = 0xFFBC8D;
	private static final int BORDER = 0x516679;

	final mudclient client;
	private final AtomicInteger wheel = new AtomicInteger();
	private volatile Rectangle catalogBounds = new Rectangle();
	int firstItem;
	private boolean leftHeld;
	private boolean leftPress;

	ShopWindow(mudclient client) {
		this.client = client;
		leftHeld = client.getMouseButtonDown() == 1;
	}

	abstract String title();
	abstract String[] tabs();
	abstract int currentTab();
	abstract void selectTab(int tab);
	abstract int itemCount();
	abstract boolean includes(int index);
	abstract int itemId(int index);
	abstract int selectedIndex();
	abstract void selectItem(int index);
	abstract String itemBadge(int index);
	abstract String itemName(int index);
	abstract void close();
	abstract int detailLineCount();
	abstract void drawDetails(int index, int x, int y, int width, int line, int font);
	abstract void drawControls(int index, int x, int y, int width, int height, int gap, int line, int font);

	public void scroll(int amount) {
		if (catalogBounds.contains(client.getMouseX(), client.getMouseY())) wheel.addAndGet(amount);
	}

	public void render() {
		boolean down = client.getMouseButtonDown() == 1;
		// A held press can reach the window even when an earlier game frame consumed the click flag.
		leftPress = acceptsInput() && !leftHeld && (down || client.getMouseClick() == 1);
		leftHeld = down;
		int font = GameInterfaceSettings.textSize().labelFont;
		int titleFont = GameInterfaceSettings.textSize().bodyFont;
		int line = client.getSurface().fontHeight(font);
		int gap = size(8);
		int totalWidth = Math.min(size(852), client.getGameWidth() - 2 * gap);
		int dockWidth = Math.min(Math.max(size(227), measure(font, "Quantity per purchase") + 2 * gap), totalWidth / 2);
		int width = totalWidth - dockWidth - gap;
		int inside = width - 2 * gap;
		int x = (client.getGameWidth() - totalWidth) / 2;
		int dockX = x + width + gap;
		int header = Math.max(size(34), client.getSurface().fontHeight(titleFont) + gap);
		int tabHeight = Math.max(size(25), line + gap / 2);
		String[] labels = tabs();
		int tabWidth = size(100);
		for (String label : labels) tabWidth = Math.max(tabWidth, measure(font, label) + gap);
		int tabColumns = Math.max(1, inside / tabWidth);
		int tabRows = (labels.length + tabColumns - 1) / tabColumns;
		int tabSpace = tabRows * (tabHeight + gap / 2);
		int cellWidth = Math.max(size(54), measure(font, "0000") + gap);
		int cellHeight = Math.max(size(42), line + 16);
		int columns = Math.max(1, (inside + gap / 2) / (cellWidth + gap / 2));
		int detailHeight = detailLineCount() * line + gap;
		int chrome = header + tabSpace + tabHeight * 2 + detailHeight + 5 * gap;
		int bottom = client.getCraftingPanelBottom();
		int height = Math.min(bottom - 2 * gap, chrome + 3 * (cellHeight + gap / 2));
		int y = Math.max(gap, (bottom - height) / 2);
		int top = y + header + gap;
		int tabStep = (inside + gap / 2) / tabColumns;
		int contentX = x + gap;

		frame(x, y, width, height);
		frame(dockX, y, dockWidth, height);
		header(x, y, width, header, title(), titleFont, gap);
		header(dockX, y, dockWidth, header, "Quantities", titleFont, gap);
		int closeWidth = Math.max(size(29), measure(font, "X") + gap);
		if (button(x + width - closeWidth - gap, y + gap / 2, closeWidth, header - gap, "X", font, false, true)) {
			close();
			return;
		}

		for (int i = 0; i < labels.length; i++) {
			if (button(contentX + i % tabColumns * tabStep, top + i / tabColumns * (tabHeight + gap / 2),
				tabStep - gap / 2, tabHeight, labels[i], font, i == currentTab(), true) && i != currentTab()) {
				selectTab(i);
				firstItem = 0;
				wheel.set(0);
			}
		}
		int nameY = top + tabSpace + line;
		int gridY = top + tabSpace + tabHeight + gap;
		int detailsY = y + height - detailHeight;
		int gridSpace = detailsY - gridY - tabHeight - gap;
		cellHeight = Math.min(cellHeight, Math.max(line + 2, gridSpace - gap / 2));
		int rowStep = cellHeight + gap / 2;
		int rows = clamp(gridSpace / rowStep, 1, 3);
		int gridHeight = rows * rowStep;
		catalogBounds = new Rectangle(contentX, gridY, inside, gridHeight);
		int count = 0;
		for (int i = 0; i < itemCount(); i++) if (includes(i)) count++;
		int perPage = rows * columns;
		int last = count == 0 ? 0 : (count - 1) / perPage * perPage;
		firstItem = clamp(firstItem + Integer.signum(wheel.getAndSet(0)) * perPage, 0, last);

		int visible = 0;
		int catalogIndex = 0;
		int hovered = -1;
		int cellStep = (inside + gap / 2) / columns;
		for (int i = 0; i < itemCount() && visible < perPage; i++) {
			if (!includes(i) || catalogIndex++ < firstItem) continue;
			int cellX = contentX + visible % columns * cellStep;
			int cellY = gridY + visible / columns * rowStep;
			int tileWidth = cellStep - gap / 2;
			if (button(cellX, cellY, tileWidth, cellHeight, "", font, i == selectedIndex(), true)) selectItem(i);
			if (contains(cellX, cellY, tileWidth, cellHeight)) hovered = i;
			int spriteWidth = Math.min(size(48), tileWidth - gap);
			int spriteHeight = Math.min(size(32), cellHeight - 4);
			drawItem(itemId(i), cellX + (tileWidth - spriteWidth) / 2, cellY + (cellHeight - spriteHeight) / 2,
				spriteWidth, spriteHeight);
			draw(fit(itemBadge(i), font, tileWidth - 4), cellX + 2, cellY + line, font, GOLD);
			visible++;
		}

		if (last > 0) {
			int pageY = gridY + gridHeight;
			int pageButton = Math.max(size(26), measure(font, ">") + gap);
			if (button(contentX, pageY, pageButton, tabHeight, "<", font, false, firstItem > 0)) firstItem -= perPage;
			if (button(contentX + inside - pageButton, pageY, pageButton, tabHeight, ">", font, false, firstItem < last)) firstItem += perPage;
			center((firstItem / perPage + 1) + " / " + (last / perPage + 1),
				contentX, pageY + (tabHeight + line) / 2 - 2, inside, font, MUTED);
		}
		int selected = selectedIndex();
		String name = hovered >= 0 ? itemName(hovered) : selected >= 0 ? itemName(selected) : "Select an item";
		draw(fit(name, font, inside), contentX, nameY, font, GOLD);
		client.getSurface().drawLineHoriz(contentX, detailsY, inside, BORDER);
		if (selected >= 0) drawDetails(selected, contentX, detailsY + line, inside, line, font);
		drawControls(selected, dockX + gap, top, dockWidth - 2 * gap, height - header - 2 * gap, gap, line, font);
	}

	final void frame(int x, int y, int width, int height) {
		client.getSurface().drawBoxAlpha(x, y, width, height, 0x1B2935, 242);
		client.getSurface().drawBoxBorder(x, width, y, height, BORDER);
	}

	private void header(int x, int y, int width, int height, String heading, int font, int gap) {
		client.getSurface().drawVerticalGradient(x + 1, y + 1, width - 2, height - 1, 0x395065, 0x263B4A);
		draw(fit(heading, font, width - 5 * gap), x + gap,
			y + (height + client.getSurface().fontHeight(font)) / 2 - 2, font, TEXT);
	}

	boolean acceptsInput() {
		return true;
	}

	final boolean button(int x, int y, int width, int height, String label, int font, boolean selected, boolean enabled) {
		boolean hovered = contains(x, y, width, height);
		int background = selected ? 0x405C65 : enabled && hovered ? 0x415468 : enabled ? 0x2C3C50 : 0x202C38;
		client.getSurface().drawBoxAlpha(x, y, width, height, background, 245);
		client.getSurface().drawBoxBorder(x, width, y, height, selected ? 0xA8BB9F : BORDER);
		if (!label.isEmpty()) {
			center(fit(label, font, width - 4), x, y + (height + client.getSurface().fontHeight(font)) / 2 - 2,
				width, font, enabled ? TEXT : MUTED);
		}
		if (hovered && enabled && leftPress) {
			leftPress = false;
			client.setMouseClick(0);
			return true;
		}
		return false;
	}

	private void drawItem(int id, int x, int y, int width, int height) {
		if (id < 0 || id >= EntityHandler.itemCount()) return;
		ItemDef def = EntityHandler.getItemDef(id);
		client.getSurface().drawSpriteClipping(client.spriteSelect(def), x, y, width, height,
			def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
	}

	private boolean contains(int x, int y, int width, int height) {
		return client.getMouseX() >= x && client.getMouseX() < x + width
			&& client.getMouseY() >= y && client.getMouseY() < y + height;
	}

	final void center(String text, int x, int y, int width, int font, int color) {
		draw(text, x + (width - measure(font, text)) / 2, y, font, color);
	}

	final void draw(String text, int x, int y, int font, int color) {
		client.getSurface().drawString(text, x, y, color, font);
	}

	final int measure(int font, String text) {
		return client.getSurface().stringWidth(font, text);
	}

	final String fit(String text, int font, int width) {
		if (measure(font, text) <= width) return text;
		int end = text.length();
		while (end > 0 && measure(font, text.substring(0, end) + "...") > width) end--;
		return end == 0 ? "" : text.substring(0, end) + "...";
	}

	static int size(int pixels) {
		return GameInterfaceSettings.size().pixels(pixels);
	}

	static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}
