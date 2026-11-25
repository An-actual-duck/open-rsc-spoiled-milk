package orsc;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;
import orsc.graphics.two.MudClientGraphics;

/** Editing and drawing shared by focused modal text fields. */
final class ModalTextField {
	private final StringBuilder text = new StringBuilder();
	private final int limit;
	private final IntPredicate accepts;
	private int caret;
	private boolean selectedAll;

	ModalTextField(int limit, IntPredicate accepts) {
		this.limit = limit;
		this.accepts = accepts;
	}

	String value() {
		return text.toString();
	}

	void keyPressed(char key, int code, boolean shortcut) {
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
				if (!accepts.test(key)) return;
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

	void draw(mudclient client, Rectangle field, int rows, int font, int pad) {
		MudClientGraphics surface = client.getSurface();
		int mouseX = client.getMouseX();
		int mouseY = client.getMouseY();
		boolean clicked = client.getMouseClick() == 1;
		String value = value();
		ToIntFunction<String> measure = part -> surface.literalStringWidth(font, part);
		int bodyLine = surface.fontHeight(font);
		List<Integer> starts = lineStarts(value, Math.max(1, field.width - 2 * pad - 2), measure);
		int first = Math.max(0, Math.min(lineAt(starts, caret) - rows + 1, starts.size() - rows));
		if (clicked && field.contains(mouseX, mouseY)) {
			int line = Math.max(0, (mouseY - field.y - pad) / bodyLine);
			int row = Math.min(starts.size() - 1, first + Math.min(rows - 1, line));
			caret = caretAt(value, starts.get(row), endOfLine(starts, value, row), mouseX - field.x - pad, measure);
			selectedAll = false;
		}

		surface.drawBox(field.x, field.y, field.width, field.height, 0x121B28);
		surface.drawBoxBorder(field.x, field.width, field.y, field.height, 0xD2B873);
		for (int i = first; i < starts.size() && i < first + rows; i++) {
			int start = starts.get(i);
			String line = value.substring(start, endOfLine(starts, value, i));
			int rowY = field.y + pad + (i - first) * bodyLine;
			if (selectedAll && !line.isEmpty()) {
				surface.drawBox(field.x + pad, rowY, measure.applyAsInt(line), bodyLine, 0x405978);
			}
			surface.drawLiteralString(line, field.x + pad, rowY + bodyLine - 3, 0xFFFFFF, font);
			if (!selectedAll && i == lineAt(starts, caret)) {
				int caretX = field.x + pad + measure.applyAsInt(value.substring(start, caret));
				surface.drawLineVert(caretX, rowY + 2, 0xFFDC82, bodyLine - 4);
			}
		}
	}

	private static List<Integer> lineStarts(String value, int width, ToIntFunction<String> measure) {
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
