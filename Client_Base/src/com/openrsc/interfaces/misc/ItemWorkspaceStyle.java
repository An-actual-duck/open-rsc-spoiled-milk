package com.openrsc.interfaces.misc;

import java.awt.Rectangle;
import java.util.function.BooleanSupplier;
import orsc.GameInterfaceSettings;
import orsc.mudclient;

/** Shared styling and pointer handling for the bank and auction windows. */
final class ItemWorkspaceStyle {
	static final int TEXT = 0xE6EDF2;
	static final int GOLD = 0xF2D393;
	static final int MUTED = 0xA9B9C6;
	static final int BORDER = 0x516679;

	final int font, titleFont, line, gap, header, button;
	private final mudclient client;
	private final BooleanSupplier canClick;

	ItemWorkspaceStyle(mudclient client, BooleanSupplier canClick) {
		this.client = client;
		this.canClick = canClick;
		font = GameInterfaceSettings.textSize().labelFont;
		titleFont = GameInterfaceSettings.textSize().bodyFont;
		line = client.getSurface().fontHeight(font);
		gap = size(8);
		header = Math.max(size(34), client.getSurface().fontHeight(titleFont) + gap);
		button = Math.max(size(25), line + 4);
	}

	static int size(int pixels) {
		return GameInterfaceSettings.size().pixels(pixels);
	}

	void panel(Rectangle area, String title) {
		client.getSurface().drawBoxAlpha(area.x, area.y, area.width, area.height, 0x1B2935, 242);
		client.getSurface().drawBoxBorder(area.x, area.width, area.y, area.height, BORDER);
		client.getSurface().drawVerticalGradient(area.x + 1, area.y + 1, area.width - 2, header - 1, 0x395065, 0x263B4A);
		client.getSurface().drawString(title, area.x + gap,
			area.y + (header + client.getSurface().fontHeight(titleFont)) / 2 - 2, TEXT, titleFont);
	}

	boolean button(int x, int y, int w, int h, String text, boolean selected, boolean enabled) {
		boolean hover = over(x, y, w, h);
		int background = selected ? 0x405C65 : enabled && hover ? 0x415468 : enabled ? 0x2C3C50 : 0x202C38;
		client.getSurface().drawBoxAlpha(x, y, w, h, background, 245);
		client.getSurface().drawBoxBorder(x, w, y, h, selected ? 0xA8BB9F : BORDER);
		if (!text.isEmpty()) {
			String label = fit(text, w - 4);
			draw(label, x + (w - measure(label)) / 2, y + (h + line) / 2 - 2, enabled ? TEXT : MUTED);
		}
		return enabled && click(x, y, w, h);
	}

	void draw(String text, int x, int y, int colour) {
		client.getSurface().drawString(text, x, y, colour, font);
	}

	int measure(String text) {
		return client.getSurface().stringWidth(font, text);
	}

	String fit(String text, int width) {
		if (measure(text) <= width) return text;
		int end = text.length();
		while (end > 0 && measure(text.substring(0, end) + "...") > width) end--;
		return end == 0 ? "" : text.substring(0, end) + "...";
	}

	boolean click(int x, int y, int w, int h) {
		if (client.getMouseClick() != 1 || !canClick.getAsBoolean() || !over(x, y, w, h)) return false;
		client.setMouseClick(0);
		return true;
	}

	boolean over(int x, int y, int w, int h) {
		return client.getMouseX() >= x && client.getMouseX() < x + w
			&& client.getMouseY() >= y && client.getMouseY() < y + h;
	}
}
