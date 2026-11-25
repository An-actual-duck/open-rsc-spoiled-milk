package orsc;

import orsc.graphics.gui.Panel;
import orsc.graphics.two.MudClientGraphics;

/** Places the Classic account controls around the center of the game surface. */
final class AccountEntryLayout {
	final Panel panel;
	private final int centerX;
	private final int centerY;
	private final int scale;

	AccountEntryLayout(MudClientGraphics surface, int capacity, int centerX, int centerY, int scale) {
		this.panel = new Panel(surface, capacity);
		this.centerX = centerX;
		this.centerY = centerY;
		this.scale = scale;
	}

	int label(int x, int y, String text, int font, boolean light) {
		return panel.addCenteredText(centerX + x * scale, centerY + y * scale, text, fontAtScale(font), light);
	}

	int button(int x, int y, int width, int height, String text, int font) {
		background(x, y, width, height);
		label(x, y, text, font, false);
		return panel.addButton(centerX + x * scale, centerY + y * scale, width * scale, height * scale);
	}

	void background(int x, int y, int width, int height) {
		panel.addButtonBackground(centerX + x * scale, centerY + y * scale, width * scale, height * scale);
	}

	int entry(int x, int y, int width, int height, int maximumLength, int font, boolean masked) {
		return panel.addCenteredTextEntry(centerX + x * scale, centerY + y * scale,
			width * scale, maximumLength, height * scale, fontAtScale(font), masked, false);
	}

	int field(int x, int y, int width, int maximumLength, String label, boolean masked) {
		background(x, y, width, 40);
		label(x, y - 9, label, 4, false);
		return entry(x, y + 9, width - 16, 20, maximumLength, 4, masked);
	}

	private int fontAtScale(int font) {
		if (scale == 1) return font;
		return font >= 4 ? 7 : 6;
	}
}
