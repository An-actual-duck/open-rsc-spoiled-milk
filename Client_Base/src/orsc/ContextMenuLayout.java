package orsc;

import java.util.function.ToIntFunction;

/** The shared game menu's visible rows map directly to the sorted action list. */
final class ContextMenuLayout {
	final int viewportWidth;
	final int viewportHeight;
	final int left;
	final int top;
	final int width;
	final int height;
	final int rowHeight;
	final int headerHeight;
	final int padding;
	final int visibleRows;
	final int bodyFont;
	final int titleFont;
	final int bodyLine;
	final int titleLine;
	final String title;
	private final int scrollbarWidth;
	private final int dismissalMargin;
	private final String[] labels;

	ContextMenuLayout(int viewportWidth, int viewportHeight, int anchorX, int anchorY,
					  GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text,
					  int bodyLine, int titleLine, String title, String[] entries,
					  ToIntFunction<String> measureBody, ToIntFunction<String> measureTitle) {
		this.viewportWidth = viewportWidth;
		this.viewportHeight = viewportHeight;
		this.bodyLine = bodyLine;
		this.titleLine = titleLine;
		bodyFont = text.bodyFont;
		titleFont = text.labelFont;
		padding = size.pixels(10);
		dismissalMargin = size.pixels(10);
		rowHeight = Math.max(size.pixels(27), bodyLine + size.pixels(6));
		headerHeight = title == null ? 0 : Math.max(size.pixels(32), titleLine + size.pixels(10));

		int margin = size.pixels(4);
		int bottomPadding = size.pixels(4);
		int room = viewportHeight - 2 * margin - headerHeight - bottomPadding;
		visibleRows = Math.min(entries.length, Math.max(1, room / rowHeight));
		scrollbarWidth = visibleRows < entries.length ? size.pixels(16) : 0;
		int bodyWidth = 0;
		for (String label : entries) bodyWidth = Math.max(bodyWidth, measureBody.applyAsInt(label));
		int titleWidth = title == null ? 0 : measureTitle.applyAsInt(title);
		width = Math.min(viewportWidth - 2 * margin, Math.max(size.pixels(144),
			Math.max(titleWidth, bodyWidth + scrollbarWidth) + 2 * padding));
		height = headerHeight + visibleRows * rowHeight + bottomPadding;
		left = clamp(anchorX - width / 2, margin, viewportWidth - margin - width);
		top = clamp(anchorY - headerHeight / 2, margin, viewportHeight - margin - height);

		this.title = title == null ? null : fitLabel(title, width - 2 * padding, measureTitle);
		labels = new String[entries.length];
		int labelWidth = width - scrollbarWidth - 2 * padding;
		for (int row = 0; row < entries.length; row++) {
			labels[row] = fitLabel(entries[row], labelWidth, measureBody);
		}
	}

	private static String fitLabel(String label, int width, ToIntFunction<String> measure) {
		if (measure.applyAsInt(label) <= width) return label;
		int limit = width - measure.applyAsInt("...");
		int end = label.length();
		while (end > 0 && measure.applyAsInt(label.substring(0, end)) > limit) {
			boolean colorTag = end >= 5 && label.charAt(end - 1) == '@' && label.charAt(end - 5) == '@';
			end -= colorTag ? 5 : 1;
		}
		return label.substring(0, end).trim() + "...";
	}

	String label(int item) { return labels[item]; }
	int rowsTop() { return top + headerHeight; }
	int rowsHeight() { return visibleRows * rowHeight; }
	int rowTop(int row) { return rowsTop() + row * rowHeight; }
	int scrollbarLeft() { return left + width - scrollbarWidth; }
	int maxScroll() { return labels.length - visibleRows; }

	boolean keepsOpenAt(int x, int y) {
		return x >= left - dismissalMargin && x < left + width + dismissalMargin
			&& y >= top - dismissalMargin && y < top + height + dismissalMargin;
	}

	int itemAt(int x, int y, int firstRow) {
		if (x < left + 1 || x >= Math.min(left + width - 1, scrollbarLeft())
			|| y < rowsTop() || y >= rowsTop() + rowsHeight()) return -1;
		return firstRow + (y - rowsTop()) / rowHeight;
	}

	boolean overScrollbar(int x, int y) {
		return maxScroll() > 0 && x >= scrollbarLeft() && x < left + width - 1
			&& y >= rowsTop() && y < rowsTop() + rowsHeight();
	}

	int scrollFor(int y) {
		return Math.round((float) (y - rowsTop()) * maxScroll() / Math.max(1, rowsHeight() - 1));
	}

	int clampScroll(int firstRow) {
		return clamp(firstRow, 0, maxScroll());
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
