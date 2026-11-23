package orsc;

import orsc.graphics.two.GraphicsController;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Layout, rendering, and hit targets for an existing server response menu. */
final class DialogueChoicesPanel {
	static final int CANCEL = -1;
	static final int SCROLLED = -2;
	private static final String HEADING = "Choose a response";

	private final int left;
	private final int top;
	private final int width;
	private final int bottom;
	private final int padding;
	private final int headerHeight;
	private final int lineHeight;
	private final int bodyFont;
	private final int labelFont;
	private final int scrollWidth;
	private final List<Response> responses = new ArrayList<>();
	private final int maxScroll;
	private int scroll;

	DialogueChoicesPanel(int viewportWidth, int anchorBottom, GameInterfaceSettings.Size size,
						 GameInterfaceSettings.TextSize text, int lineHeight, int labelHeight,
						 boolean numbered, String[] choices, int count, ToIntFunction<String> measure) {
		padding = size.pixels(12);
		this.lineHeight = lineHeight;
		bodyFont = text.bodyFont;
		labelFont = text.labelFont;
		headerHeight = labelHeight + padding * 2;
		width = Math.min(viewportWidth - 2 * padding, size.pixels(720));
		left = (viewportWidth - width) / 2;
		bottom = anchorBottom;
		scrollWidth = size.pixels(24);
		int textWidth = Math.max(1, width - scrollWidth - padding * 4);
		int contentHeight = 0;
		for (int choice = 0; choice < count; choice++) {
			String response = choices[choice];
			if (numbered) {
				response = (choice + 1) + ". " + response;
			}
			List<String> lines = ClericTextLayout.wrap(response, textWidth, measure);
			int rowHeight = Math.max(1, lines.size()) * lineHeight + padding;
			responses.add(new Response(lines, contentHeight, rowHeight));
			contentHeight += rowHeight;
		}
		int panelHeight = Math.min(headerHeight + contentHeight + padding, anchorBottom - padding);
		top = bottom - panelHeight;
		maxScroll = Math.max(0, contentHeight - (visibleBottom() - visibleTop()));
	}

	int activate(int mouseX, int mouseY) {
		if (maxScroll > 0 && mouseX >= scrollX() && mouseX < scrollX() + scrollWidth
			&& mouseY >= visibleTop() && mouseY < visibleBottom()) {
			scrollRows(mouseY < scrollMidpoint() ? -1 : 1);
			return SCROLLED;
		}
		return optionAt(mouseX, mouseY);
	}

	void scrollRows(int direction) {
		scroll = Math.max(0, Math.min(maxScroll, scroll + direction * lineHeight));
	}

	int optionAt(int x, int y) {
		if (x < left + padding || x >= responseRight() || y < visibleTop() || y >= visibleBottom()) {
			return CANCEL;
		}
		int offset = y - visibleTop() + scroll;
		for (int option = 0; option < responses.size(); option++) {
			Response response = responses.get(option);
			if (offset < response.offset + response.height) {
				return option;
			}
		}
		return CANCEL;
	}

	void draw(GraphicsController surface, int mouseX, int mouseY) {
		int height = bottom - top;
		surface.drawBoxAlpha(left, top, width, height, 0x182333, 224);
		surface.drawBoxBorder(left, width, top, height, 0x667993);
		surface.drawString(HEADING, left + padding * 2, top + headerHeight - padding, 0xFFDC82, labelFont);
		int highlighted = optionAt(mouseX, mouseY);
		surface.setClip(left + 1, responseRight(), visibleBottom(), visibleTop());
		try {
			for (int option = 0; option < responses.size(); option++) {
				Response response = responses.get(option);
				int y = visibleTop() + response.offset - scroll;
				if (y + response.height <= visibleTop() || y >= visibleBottom()) {
					continue;
				}
				int color = option == highlighted ? 0xFFDC82 : 0xFFFFFF;
				if (option == highlighted) {
					surface.drawBoxAlpha(left + padding, y, responseRight() - left - padding,
						response.height, 0x54647C, 176);
				}
				int baseline = y + lineHeight + padding / 2 - 3;
				for (String line : response.lines) {
					surface.drawString(line, left + padding * 2, baseline, color, bodyFont);
					baseline += lineHeight;
				}
			}
		} finally {
			surface.clearClip();
		}
		if (maxScroll > 0) {
			drawScrollControl(surface, visibleTop(), scrollMidpoint(), "^", scroll > 0);
			drawScrollControl(surface, scrollMidpoint(), visibleBottom(), "v", scroll < maxScroll);
		}
	}

	private void drawScrollControl(GraphicsController surface, int y, int end, String arrow, boolean enabled) {
		surface.drawBoxAlpha(scrollX(), y, scrollWidth, end - y, 0x354258, 224);
		surface.drawColoredStringCentered(scrollX() + scrollWidth / 2, arrow,
			enabled ? 0xFFFFFF : 0x929CAA, 0, labelFont, (y + end + lineHeight / 2) / 2);
	}

	private int scrollX() {
		return left + width - padding - scrollWidth;
	}

	private int scrollMidpoint() {
		return (visibleTop() + visibleBottom()) / 2;
	}

	private int responseRight() {
		return maxScroll > 0 ? scrollX() : left + width - padding;
	}

	private int visibleTop() {
		return top + headerHeight;
	}

	private int visibleBottom() {
		return bottom - padding;
	}

	private static final class Response {
		final List<String> lines;
		final int offset;
		final int height;

		Response(List<String> lines, int offset, int height) {
			this.lines = lines;
			this.offset = offset;
			this.height = height;
		}
	}
}
