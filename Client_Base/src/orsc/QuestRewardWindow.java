package orsc;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import orsc.graphics.two.MudClientGraphics;

/** An informational window for rewards already granted by the server. */
final class QuestRewardWindow {
	private final String name;
	private final List<String> rewards;
	private int firstRow;
	private int wrapWidth, wrapFont = -1;
	private List<String> rows;

	QuestRewardWindow(String name, List<String> rewards) {
		this.name = name;
		this.rewards = new ArrayList<>(rewards);
	}

	boolean draw(mudclient client, int bottom, int wheel) {
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		int padding = size.pixels(14);
		int heading = Math.max(size.pixels(40), surface.fontHeight(text.bodyFont) + 12);
		int lineHeight = Math.max(size.pixels(25), surface.fontHeight(text.bodyFont) + 6);
		int buttonHeight = Math.max(size.pixels(36), surface.fontHeight(text.bodyFont) + 8);
		int width = Math.min(client.getGameWidth() - 2 * padding, size.pixels(540));
		int textWidth = width - 4 * padding;
		if (wrapWidth != textWidth || wrapFont != text.bodyFont) {
			wrapWidth = textWidth;
			wrapFont = text.bodyFont;
			rows = new ArrayList<>();
			for (String reward : rewards) rows.addAll(ClericTextLayout.wrap(reward, textWidth,
				value -> surface.stringWidth(text.bodyFont, value)));
		}
		int maxRows = Math.max(1, (bottom - heading - lineHeight - buttonHeight - 5 * padding) / lineHeight);
		int visibleRows = Math.min(rows.size(), maxRows);
		int maxScroll = Math.max(0, rows.size() - visibleRows);
		int height = heading + lineHeight * (1 + visibleRows) + buttonHeight + 4 * padding;
		int x = (client.getGameWidth() - width) / 2;
		int y = Math.max(0, (bottom - height) / 2);
		int rowTop = y + heading + padding + lineHeight;
		int buttonWidth = Math.min(size.pixels(142), width / 3);
		int buttonY = y + height - padding - buttonHeight;
		Rectangle continueButton = new Rectangle(x + (width - buttonWidth) / 2, buttonY, buttonWidth, buttonHeight);
		int arrowWidth = size.pixels(32);
		Rectangle next = new Rectangle(x + width - padding - arrowWidth, buttonY, arrowWidth, buttonHeight);
		Rectangle previous = new Rectangle(next.x - arrowWidth - padding / 2, buttonY, arrowWidth, buttonHeight);
		int mouseX = client.getMouseX(), mouseY = client.getMouseY();
		boolean clicked = client.getMouseClick() == 1;
		if (clicked && continueButton.contains(mouseX, mouseY)) return true;
		firstRow = Math.max(0, Math.min(maxScroll, firstRow + wheel));
		if (clicked && previous.contains(mouseX, mouseY)) firstRow = Math.max(0, firstRow - 1);
		if (clicked && next.contains(mouseX, mouseY)) firstRow = Math.min(maxScroll, firstRow + 1);

		surface.drawBoxAlpha(x, y, width, height, InterfacePalette.PANEL, 248);
		surface.drawVerticalGradient(x, y, width, heading, InterfacePalette.HEADER_LIGHT, InterfacePalette.HEADER_DARK);
		surface.drawBoxBorder(x, width, y, height, InterfacePalette.BORDER);
		center(surface, "Quest complete", x + width / 2, y, heading, text.bodyFont, 0xFFDC82);
		String title = ContextMenuLayout.fitLabel(name, textWidth, value -> surface.stringWidth(text.bodyFont, value));
		center(surface, title, x + width / 2, rowTop - lineHeight, lineHeight, text.bodyFont, 0xFFFFFF);
		for (int index = 0; index < visibleRows; index++) {
			int top = rowTop + index * lineHeight;
			surface.drawBox(x + padding, top, width - 2 * padding, lineHeight - 1, InterfacePalette.ROW);
			surface.drawString(rows.get(firstRow + index), x + 2 * padding, baseline(surface, top, lineHeight, text.bodyFont),
				InterfacePalette.TEXT, text.bodyFont);
		}
		button(surface, continueButton, "Continue", text.labelFont, mouseX, mouseY, true);
		if (maxScroll > 0) {
			button(surface, previous, "^", text.labelFont, mouseX, mouseY, firstRow > 0);
			button(surface, next, "v", text.labelFont, mouseX, mouseY, firstRow < maxScroll);
			surface.drawString((firstRow + 1) + "-" + (firstRow + visibleRows) + " / " + rows.size(), x + padding,
				baseline(surface, buttonY, buttonHeight, text.labelFont), InterfacePalette.MUTED_TEXT, text.labelFont);
		}
		return false;
	}

	private static void button(MudClientGraphics surface, Rectangle bounds, String label, int font, int x, int y, boolean enabled) {
		surface.drawBox(bounds.x, bounds.y, bounds.width, bounds.height,
			enabled && bounds.contains(x, y) ? InterfacePalette.CONTROL_HOVER : InterfacePalette.HEADER_DARK);
		surface.drawBoxBorder(bounds.x, bounds.width, bounds.y, bounds.height, enabled ? InterfacePalette.BORDER : InterfacePalette.CONTROL_DISABLED);
		center(surface, label, bounds.x + bounds.width / 2, bounds.y, bounds.height, font, enabled ? 0xFFFFFF : InterfacePalette.MUTED_TEXT);
	}

	private static void center(MudClientGraphics surface, String label, int x, int top, int height, int font, int color) {
		surface.drawColoredStringCentered(x, label, color, 0, font, baseline(surface, top, height, font));
	}

	private static int baseline(MudClientGraphics surface, int top, int height, int font) {
		return top + (height + surface.fontHeight(font)) / 2 - 3;
	}
}
