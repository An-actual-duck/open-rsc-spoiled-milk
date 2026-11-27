package orsc;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import orsc.graphics.two.MudClientGraphics;

/** Layout and input for welcome, server notices, and logout feedback. */
final class SessionNoticeWindow {
	enum Action { NONE, DISMISS, SECONDARY }

	private final String title;
	private final String message;
	private final String dismissLabel;
	private final String cancelLabel;
	private final int preferredWidth;
	private final boolean centered;
	private final boolean welcomeStyle;
	private final AtomicInteger scrollInput = new AtomicInteger();
	private final AtomicInteger keyInput = new AtomicInteger();
	private List<String> rows;
	private List<String> titleRows;
	private int wrapWidth, wrapFont = -1;
	private int firstRow;

	SessionNoticeWindow(String title, String message, String dismissLabel, String cancelLabel, int preferredWidth,
						boolean centered) {
		this(title, message, dismissLabel, cancelLabel, preferredWidth, centered, false);
	}

	static SessionNoticeWindow welcome(String title, String message, String dismissLabel, String cancelLabel) {
		return new SessionNoticeWindow(title, message, dismissLabel, cancelLabel, 660, true, true);
	}

	private SessionNoticeWindow(String title, String message, String dismissLabel, String cancelLabel,
								int preferredWidth, boolean centered, boolean welcomeStyle) {
		this.title = title;
		this.message = message;
		this.dismissLabel = dismissLabel;
		this.cancelLabel = cancelLabel;
		this.preferredWidth = preferredWidth;
		this.centered = centered;
		this.welcomeStyle = welcomeStyle;
	}

	void scroll(int amount) {
		if (dismissLabel != null) scrollInput.addAndGet(amount);
	}

	void keyPressed(int code) {
		switch (code) {
			case KeyEvent.VK_ENTER: case KeyEvent.VK_ESCAPE:
			case KeyEvent.VK_PAGE_UP: case KeyEvent.VK_PAGE_DOWN:
			case KeyEvent.VK_HOME: case KeyEvent.VK_END:
				keyInput.set(code);
				break;
			default:
				break;
		}
	}

	Action draw(mudclient client, int bottom) {
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		boolean compactViewport = bottom < 530;
		int padding = compactViewport ? Math.min(size.pixels(12), 12) : size.pixels(12);
		int bodyLine = surface.fontHeight(text.bodyFont);
		int labelLine = surface.fontHeight(text.labelFont);
		int rowHeight = bodyLine + size.pixels(3);
		int targetHeader = compactViewport ? Math.min(size.pixels(42), 50) : size.pixels(42);
		int targetButton = compactViewport ? Math.min(size.pixels(34), 42) : size.pixels(34);
		int buttonHeight = dismissLabel == null ? 0 : Math.max(targetButton, labelLine + padding / 2);
		int width = Math.min(client.getGameWidth() - 2 * padding, size.pixels(preferredWidth));
		int contentWidth = width - 2 * padding;
		if (wrapWidth != contentWidth || wrapFont != text.bodyFont) {
			wrapWidth = contentWidth;
			wrapFont = text.bodyFont;
			rows = wrap(message, contentWidth, part -> surface.literalStringWidth(text.bodyFont, part));
			if (welcomeStyle) {
				titleRows = ClericTextLayout.wrap(title, contentWidth, part -> surface.literalStringWidth(text.bodyFont, part));
			}
		}
		int headingHeight = welcomeStyle ? titleRows.size() * bodyLine : bodyLine;
		int header = Math.max(targetHeader, headingHeight + padding);
		int gaps = padding * (dismissLabel == null ? 2 : 3);
		int capacity = Math.max(1, (bottom - header - buttonHeight - gaps - padding) / rowHeight);
		int visibleRows = Math.min(Math.min(capacity, 15), rows.size());
		int maxScroll = rows.size() - visibleRows;
		int key = keyInput.getAndSet(0);
		if (dismissLabel != null && cancelLabel == null && (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_ESCAPE)) {
			return Action.DISMISS;
		}
		firstRow += scrollInput.getAndSet(0);
		switch (key) {
			case KeyEvent.VK_PAGE_UP: firstRow -= visibleRows; break;
			case KeyEvent.VK_PAGE_DOWN: firstRow += visibleRows; break;
			case KeyEvent.VK_HOME: firstRow = 0; break;
			case KeyEvent.VK_END: firstRow = maxScroll; break;
			default: break;
		}
		firstRow = Math.max(0, Math.min(maxScroll, firstRow));

		int height = header + gaps + visibleRows * rowHeight + buttonHeight;
		int x = (client.getGameWidth() - width) / 2;
		int y = Math.max(0, (bottom - height) / 2);
		int left = x + padding;
		int footerY = y + height - buttonHeight - padding;
		Rectangle bounds = new Rectangle(x, y, width, height);
		if (welcomeStyle) {
			surface.drawBox(x, y, width, height, 0);
			surface.drawBoxBorder(x, width, y, height, 0xFFFFFF);
			int titleBaseline = y + (header - headingHeight) / 2 + bodyLine - 3;
			for (String line : titleRows) {
				surface.drawColoredStringCentered(x + width / 2, line, 0xFFFF00, 0, text.bodyFont, titleBaseline);
				titleBaseline += bodyLine;
			}
		} else {
			SocialTextPrompt.drawFrame(surface, bounds, header, padding, title, text.bodyFont, bodyLine);
		}
		if (dismissLabel != null) {
			int actions = cancelLabel == null ? 1 : 2;
			int minButtonWidth = surface.stringWidth(text.labelFont, dismissLabel) + 2 * padding;
			if (cancelLabel != null) minButtonWidth = Math.max(minButtonWidth,
				surface.stringWidth(text.labelFont, cancelLabel) + 2 * padding);
			int buttonWidth = Math.min((contentWidth - (actions - 1) * padding) / actions,
				Math.max(size.pixels(104), minButtonWidth));
			int dismissX = welcomeStyle && actions == 1 && maxScroll == 0
				? x + (width - buttonWidth) / 2 : x + width - padding - buttonWidth;
			Rectangle dismiss = new Rectangle(dismissX, footerY, buttonWidth, buttonHeight);
			if (button(client, dismiss, dismissLabel, true, text.labelFont, labelLine)) return Action.DISMISS;
			if (cancelLabel != null) {
				Rectangle cancel = new Rectangle(dismiss.x - padding - buttonWidth, footerY, buttonWidth, buttonHeight);
				if (button(client, cancel, cancelLabel, true, text.labelFont, labelLine)) return Action.SECONDARY;
			}
			if (maxScroll > 0) {
				int arrowWidth = Math.max(size.pixels(25), labelLine + 6);
				Rectangle previous = new Rectangle(left, footerY, arrowWidth, buttonHeight);
				Rectangle next = new Rectangle(left + arrowWidth + padding / 2, footerY, arrowWidth, buttonHeight);
				if (button(client, previous, "^", firstRow > 0, text.labelFont, labelLine)) {
					firstRow = Math.max(0, firstRow - visibleRows);
				}
				if (button(client, next, "v", firstRow < maxScroll, text.labelFont, labelLine)) {
					firstRow = Math.min(maxScroll, firstRow + visibleRows);
				}
				if (cancelLabel == null) {
					surface.drawString((firstRow + 1) + "-" + (firstRow + visibleRows) + " / " + rows.size(),
						next.x + next.width + padding, footerY + (buttonHeight + labelLine) / 2 - 3, InterfacePalette.MUTED_TEXT, text.labelFont);
				}
			}
		}
		int baseline = y + header + padding + bodyLine - 3;
		for (int row = 0; row < visibleRows; row++) {
			String line = rows.get(firstRow + row);
			int textX = centered ? left + (contentWidth - surface.stringWidth(text.bodyFont, line)) / 2 : left;
			surface.drawString(line, textX, baseline + row * rowHeight,
				welcomeStyle ? 0xFFFFFF : InterfacePalette.TEXT, text.bodyFont);
		}
		return Action.NONE;
	}

	private boolean button(mudclient client, Rectangle bounds, String label, boolean enabled, int font, int line) {
		boolean hover = bounds.contains(client.getMouseX(), client.getMouseY());
		MudClientGraphics surface = client.getSurface();
		if (welcomeStyle) {
			int color = !enabled ? InterfacePalette.DISABLED_TEXT : hover ? 0xFFFF00 : 0xFFFFFF;
			surface.drawBoxBorder(bounds.x, bounds.width, bounds.y, bounds.height, color);
			surface.drawColoredStringCentered(bounds.x + bounds.width / 2, label, color, 0, font,
				bounds.y + (bounds.height + line) / 2 - 3);
		} else {
			SocialTextPrompt.drawButton(surface, bounds, label, enabled, hover, font, line);
		}
		return enabled && hover && client.getMouseClick() == 1;
	}

	/** Wrap the server's percent-separated text without splitting colour codes or glyphs. */
	static List<String> wrap(String message, int width, ToIntFunction<String> literalWidth) {
		List<String> lines = new ArrayList<>();
		String color = "";
		String text = message.replaceAll("~[^~\\r\\n]{3}~", "").replace("\r\n", "\n").trim();
		for (String paragraph : text.split("[%\\r\\n]", -1)) {
			String content = paragraph.trim();
			if (content.isEmpty()) {
				lines.add("");
				continue;
			}
			for (int start = 0; start < content.length();) {
				int end = start;
				int pixels = 0;
				int lastSpace = -1;
				while (end < content.length()) {
					if (colorTagAt(content, end)) {
						end += 5;
						continue;
					}
					int next = content.offsetByCodePoints(end, 1);
					if (content.charAt(end) == ' ') lastSpace = end;
					pixels += literalWidth.applyAsInt(content.substring(end, next));
					if (pixels > width) {
						if (lastSpace > start) end = lastSpace;
						else if (end == start) end = next;
						break;
					}
					end = next;
				}
				String line = content.substring(start, end);
				lines.add(color + line);
				for (int index = 0; index < line.length(); index++) {
					if (colorTagAt(line, index)) {
						color = line.substring(index, index + 5);
						index += 4;
					}
				}
				start = end;
				while (start < content.length() && content.charAt(start) == ' ') start++;
			}
		}
		while (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
		return lines;
	}

	private static boolean colorTagAt(String value, int index) {
		return value.charAt(index) == '@' && index + 4 < value.length() && value.charAt(index + 4) == '@';
	}
}
