package orsc;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import orsc.graphics.two.Fonts;
import orsc.graphics.two.MudClientGraphics;

/** The focused editor and modal layout for name entry and private messages. */
final class SocialTextPrompt {
	enum Result { OPEN, SUBMIT, CANCEL }

	final String recipient;
	final int limit;
	private final StringBuilder text = new StringBuilder();
	private int caret;
	private boolean selectedAll;
	private Result result = Result.OPEN;

	SocialTextPrompt(String recipient) {
		this.recipient = recipient;
		limit = recipient == null ? 20 : 80;
	}

	synchronized String value() {
		return text.toString();
	}

	private boolean canSubmit() {
		return !value().trim().isEmpty();
	}

	synchronized void keyPressed(char key, int code, boolean shortcut) {
		if (result != Result.OPEN) return;
		if (code == KeyEvent.VK_ESCAPE || key == 27) {
			result = Result.CANCEL;
			return;
		}
		if (code == KeyEvent.VK_ENTER || key == '\r' || key == '\n') {
			if (canSubmit()) result = Result.SUBMIT;
			return;
		}
		if (shortcut) {
			if (code == KeyEvent.VK_A) {
				selectedAll = text.length() > 0;
				caret = text.length();
			}
			return;
		}
		if (key == '\b' || code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_DELETE) {
			if (clearSelection()) return;
			if (code == KeyEvent.VK_DELETE) {
				if (caret < text.length()) text.deleteCharAt(caret);
			} else if (caret > 0) {
				text.deleteCharAt(--caret);
			}
			return;
		}
		switch (code) {
			case KeyEvent.VK_LEFT: caret = selectedAll ? 0 : Math.max(0, caret - 1); break;
			case KeyEvent.VK_RIGHT: caret = selectedAll ? text.length() : Math.min(text.length(), caret + 1); break;
			case KeyEvent.VK_HOME: caret = 0; break;
			case KeyEvent.VK_END: caret = text.length(); break;
			default:
				if (Fonts.inputFilterChars.indexOf(key) < 0) return;
				clearSelection();
				if (text.length() < limit) text.insert(caret++, key);
				return;
		}
		selectedAll = false;
	}

	private boolean clearSelection() {
		if (!selectedAll) return false;
		text.setLength(0);
		caret = 0;
		selectedAll = false;
		return true;
	}

	synchronized Result draw(mudclient client, int bottom) {
		if (result != Result.OPEN) return result;
		MudClientGraphics surface = client.getSurface();
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize style = GameInterfaceSettings.textSize();
		int bodyLine = surface.fontHeight(style.bodyFont);
		int labelLine = surface.fontHeight(style.labelFont);
		int pad = size.pixels(12);
		int width = Math.min(client.getGameWidth() - 2 * pad, size.pixels(recipient == null ? 420 : 608));
		int header = Math.max(size.pixels(46), bodyLine + 2 * pad);
		int buttonHeight = Math.max(size.pixels(34), labelLine + pad);
		int availableRows = (bottom - header - labelLine - buttonHeight - 7 * pad) / bodyLine;
		int rows = Math.min(recipient == null ? 2 : 3, Math.max(1, availableRows));
		int fieldHeight = rows * bodyLine + 2 * pad;
		int height = header + labelLine + fieldHeight + buttonHeight + 4 * pad;
		int x = (client.getGameWidth() - width) / 2;
		int y = Math.max(pad, (bottom - height) / 2);
		Rectangle field = new Rectangle(x + pad, y + header + labelLine + pad, width - 2 * pad, fieldHeight);
		int buttonWidth = Math.min((field.width - pad) / 2, Math.max(size.pixels(88),
			surface.stringWidth(style.labelFont, "Cancel") + 2 * pad));
		int actionsY = field.y + field.height + pad;
		Rectangle submit = new Rectangle(x + width - pad - buttonWidth, actionsY, buttonWidth, buttonHeight);
		Rectangle cancel = new Rectangle(submit.x - pad - buttonWidth, actionsY, buttonWidth, buttonHeight);
		int mouseX = client.getMouseX();
		int mouseY = client.getMouseY();
		boolean clicked = client.getMouseClick() == 1;
		boolean enabled = canSubmit();
		if (clicked && cancel.contains(mouseX, mouseY)) return result = Result.CANCEL;
		if (clicked && enabled && submit.contains(mouseX, mouseY)) return result = Result.SUBMIT;

		String value = value();
		ToIntFunction<String> measure = part -> surface.literalStringWidth(style.bodyFont, part);
		List<Integer> starts = lineStarts(value, Math.max(1, field.width - 2 * pad - 2), measure);
		int first = Math.max(0, Math.min(lineAt(starts, caret) - rows + 1, starts.size() - rows));
		if (clicked && field.contains(mouseX, mouseY)) {
			int line = Math.max(0, (mouseY - field.y - pad) / bodyLine);
			int row = Math.min(starts.size() - 1, first + Math.min(rows - 1, line));
			caret = caretAt(value, starts.get(row), endOfLine(starts, value, row), mouseX - field.x - pad, measure);
			selectedAll = false;
		}

		surface.drawBoxAlpha(x, y, width, height, 0x182333, 246);
		surface.drawVerticalGradient(x, y, width, header, 0x3B4C67, 0x28354A);
		surface.drawBoxBorder(x, width, y, height, 0x667993);
		surface.drawString(recipient == null ? "Add Friend" : "Private Message", x + pad,
			y + (header + bodyLine) / 2 - 3, 0xFFFFFF, style.bodyFont);
		String label = recipient == null ? "Player name" : "To " + recipient;
		label = ContextMenuLayout.fitLabel(label, field.width - pad, part -> surface.stringWidth(style.labelFont, part));
		surface.drawString(label, field.x, field.y - Math.max(5, pad / 2), 0xC4D0E0, style.labelFont);
		surface.drawBox(field.x, field.y, field.width, field.height, 0x121B28);
		surface.drawBoxBorder(field.x, field.width, field.y, field.height, 0xD2B873);
		for (int i = first; i < starts.size() && i < first + rows; i++) {
			int start = starts.get(i);
			String line = value.substring(start, endOfLine(starts, value, i));
			int rowY = field.y + pad + (i - first) * bodyLine;
			if (selectedAll && !line.isEmpty()) {
				surface.drawBox(field.x + pad, rowY, measure.applyAsInt(line), bodyLine, 0x405978);
			}
			surface.drawLiteralString(line, field.x + pad, rowY + bodyLine - 3, 0xFFFFFF, style.bodyFont);
			if (!selectedAll && i == lineAt(starts, caret)) {
				int caretX = field.x + pad + measure.applyAsInt(value.substring(start, caret));
				surface.drawLineVert(caretX, rowY + 2, 0xFFDC82, bodyLine - 4);
			}
		}
		drawButton(surface, cancel, "Cancel", true, cancel.contains(mouseX, mouseY), style.labelFont, labelLine);
		drawButton(surface, submit, recipient == null ? "Add" : "Send", enabled, submit.contains(mouseX, mouseY),
			style.labelFont, labelLine);
		return Result.OPEN;
	}

	private static void drawButton(MudClientGraphics surface, Rectangle bounds, String label, boolean enabled,
								   boolean hover, int font, int line) {
		surface.drawBox(bounds.x, bounds.y, bounds.width, bounds.height,
			!enabled ? 0x253042 : hover ? 0x465873 : 0x34425A);
		surface.drawBoxBorder(bounds.x, bounds.width, bounds.y, bounds.height, 0x667993);
		surface.drawColoredStringCentered(bounds.x + bounds.width / 2, label,
			enabled ? 0xFFFFFF : 0x929FB1, 0, font, bounds.y + (bounds.height + line) / 2 - 3);
	}

	static List<Integer> lineStarts(String value, int width, ToIntFunction<String> measure) {
		List<Integer> starts = new ArrayList<>();
		starts.add(0);
		for (int begin = 0; begin < value.length();) {
			int end = begin + 1;
			int lastSpace = -1;
			while (end <= value.length() && measure.applyAsInt(value.substring(begin, end)) <= width) {
				if (value.charAt(end - 1) == ' ') lastSpace = end;
				end++;
			}
			if (end > value.length()) break;
			begin = lastSpace > begin ? lastSpace : Math.max(begin + 1, end - 1);
			starts.add(begin);
		}
		return starts;
	}

	private static int endOfLine(List<Integer> starts, String value, int line) {
		return line + 1 < starts.size() ? starts.get(line + 1) : value.length();
	}

	private static int lineAt(List<Integer> starts, int position) {
		int line = 0;
		while (line + 1 < starts.size() && starts.get(line + 1) <= position) line++;
		return line;
	}

	private static int caretAt(String value, int start, int end, int x, ToIntFunction<String> measure) {
		int prior = 0;
		for (int position = start; position < end; position++) {
			int next = measure.applyAsInt(value.substring(start, position + 1));
			if (x < (prior + next + 1) / 2) return position;
			prior = next;
		}
		return end;
	}
}
