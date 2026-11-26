package orsc;

import java.awt.Rectangle;
import orsc.graphics.two.MudClientGraphics;

/** Scaled in-game readouts and XP choices. */
final class GameplayHud {
	private GameplayHud() {
	}

	static int focusRowHeight(MudClientGraphics surface) {
		return Math.max(GameInterfaceSettings.size().pixels(30),
			surface.fontHeight(GameInterfaceSettings.textSize().labelFont) + GameInterfaceSettings.size().pixels(8));
	}

	static int drawFocus(mudclient client, int x, int y, String[] labels, int selected, boolean acceptInput) {
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		int font = GameInterfaceSettings.textSize().labelFont;
		int line = surface.fontHeight(font);
		int pad = size.pixels(8);
		int height = focusRowHeight(surface);
		int width = size.pixels(174);
		for (String label : labels) width = Math.max(width, surface.stringWidth(font, label) + 3 * pad);
		SocialTextPrompt.drawFrame(surface, new Rectangle(x, y, width, height * labels.length),
			height, pad, labels[0], font, line);
		int mouseX = client.getMouseX();
		int mouseY = client.getMouseY();
		int chosen = -1;
		for (int row = 1; row < labels.length; row++) {
			Rectangle bounds = new Rectangle(x + 1, y + height * row, width - 2, height - 1);
			boolean hover = acceptInput && bounds.contains(mouseX, mouseY);
			drawChoice(surface, bounds, labels[row], row == selected + 1, hover, font, line);
			if (hover && client.getMouseClick() == 1) chosen = row - 1;
		}
		return chosen;
	}

	static void drawChoice(MudClientGraphics surface, Rectangle bounds, String label,
						   boolean selected, boolean hover, int font, int line) {
		if (!selected) {
			SocialTextPrompt.drawButton(surface, bounds, label, true, hover, font, line);
			return;
		}
		surface.drawBox(bounds.x, bounds.y, bounds.width, bounds.height, hover ? 0x565260 : 0x45475D);
		surface.drawBoxBorder(bounds.x, bounds.width, bounds.y, bounds.height, 0xD2B873);
		surface.drawColoredStringCentered(bounds.x + bounds.width / 2, label, 0xFFDC82, 0,
			font, bounds.y + (bounds.height + line) / 2 - 3);
	}

	static void drawWilderness(mudclient client, int bottom, int level) {
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		int font = GameInterfaceSettings.textSize().labelFont;
		int pad = size.pixels(8);
		int icon = size.pixels(23);
		int height = Math.max(icon, surface.fontHeight(font)) + 2 * pad;
		String label = "Wilderness level " + level;
		int width = surface.stringWidth(font, label) + icon + 3 * pad;
		int left = client.getGameWidth() - width - pad;
		int top = bottom - height - pad;
		surface.drawBoxAlpha(left, top, width, height, 0x221B25, 232);
		surface.drawBoxBorder(left, width, top, height, 0x93696E);
		client.drawWildernessHudSkull(left + pad, top + (height - icon) / 2, icon);
		surface.drawString(label, left + 2 * pad + icon, top + (height + surface.fontHeight(font)) / 2 - 3,
			0xFFDC82, font);
	}

	static Rectangle experienceBounds(mudclient client, String label) {
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		int pad = size.pixels(8);
		int font = GameInterfaceSettings.textSize().labelFont;
		MudClientGraphics surface = client.getSurface();
		int width = Math.max(size.pixels(142), surface.stringWidth(font, label) + 2 * pad);
		return new Rectangle((client.getGameWidth() - width) / 2, size.pixels(4), width,
			Math.max(size.pixels(31), surface.fontHeight(font) + 2 * pad));
	}

	static void drawExperience(mudclient client, Rectangle bounds, String label, int color,
							   double progress, boolean hover, String[] details) {
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		int font = GameInterfaceSettings.textSize().labelFont;
		int line = surface.fontHeight(font);
		int pad = size.pixels(8);
		surface.drawBoxAlpha(bounds.x, bounds.y, bounds.width, bounds.height, hover ? InterfacePalette.CONTROL_HOVER : InterfacePalette.PANEL, 238);
		surface.drawBoxBorder(bounds.x, bounds.width, bounds.y, bounds.height, hover ? 0xD2B873 : InterfacePalette.BORDER);
		surface.drawColoredStringCentered(bounds.x + bounds.width / 2, label, color, 0, font,
			bounds.y + (bounds.height + line) / 2 - 3);
		if (progress >= 0) {
			int barHeight = Math.max(2, size.pixels(3));
			int width = bounds.width - 2;
			int filled = (int) Math.round(width * progress);
			int y = bounds.y + bounds.height - barHeight - 1;
			surface.drawBox(bounds.x + 1, y, width, barHeight, InterfacePalette.CONTROL);
			if (filled > 0) surface.drawBox(bounds.x + 1, y, filled, barHeight, 0xA3D3A8);
		}
		if (details == null) return;
		int width = bounds.width;
		for (String detail : details) width = Math.max(width, surface.stringWidth(font, detail) + 2 * pad);
		int x = (client.getGameWidth() - width) / 2;
		int y = bounds.y + bounds.height + 2;
		int height = details.length * line + 2 * pad;
		surface.drawBoxAlpha(x, y, width, height, InterfacePalette.PANEL, 238);
		surface.drawBoxBorder(x, width, y, height, InterfacePalette.BORDER);
		for (int i = 0; i < details.length; i++) {
			surface.drawString(details[i], x + pad, y + pad + (i + 1) * line - 3, InterfacePalette.TEXT, font);
		}
	}
}
