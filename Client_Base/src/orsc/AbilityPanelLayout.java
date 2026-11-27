package orsc;

/** Coordinates shared by the ability books' icon grids, text rows and controls. */
final class AbilityPanelLayout {
	final int left;
	final int top;
	final int right;
	final int bottom;
	final int padding;
	final int headerHeight;
	final int bodyFont;
	final int labelFont;
	final int lineHeight;
	final int labelHeight;
	final int scrollWidth;
	final int iconSize;
	final int columns;
	final int rowHeight;
	final int firstRowY;
	final int lastRowY;
	final int toggleLeft;
	private final int gap;
	private final int gridLeft;
	private final int headerInset;

	AbilityPanelLayout(int surfaceWidth, int anchor, boolean bottomLauncher, int chatRight, int chatTop,
					   GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text, int lineHeight,
					   int labelHeight, int toggleWidth) {
		padding = size.pixels(10);
		gap = size.pixels(5);
		bodyFont = text.bodyFont;
		labelFont = text.labelFont;
		this.lineHeight = lineHeight;
		this.labelHeight = labelHeight;
		iconSize = size.pixels(43);
		scrollWidth = size.pixels(14);
		headerHeight = size.pixels(SideToolbarLayout.HEADER_HEIGHT);
		headerInset = size.pixels(2);
		right = surfaceWidth - size.pixels(6);
		int width = Math.min(surfaceWidth - 2 * padding, Math.max(size.pixels(420), toggleWidth * 3 + 3 * padding));
		left = right - width;
		int limit = bottomLauncher ? anchor - padding : chatTop;
		if (left < chatRight) limit = Math.min(limit, chatTop - padding);
		int height = Math.min(size.pixels(450), limit - (bottomLauncher ? padding : anchor));
		top = bottomLauncher ? limit - height : anchor;
		bottom = top + height;
		rowHeight = lineHeight + padding;
		firstRowY = top + headerHeight + padding;
		int detailHeight = Math.min(Math.max(size.pixels(126), 5 * lineHeight + padding),
			(bottom - firstRowY) * 9 / 20);
		lastRowY = Math.max(firstRowY + Math.max(iconSize + gap, rowHeight), bottom - detailHeight);
		toggleLeft = right - padding - toggleWidth;
		int bodyWidth = scrollbarLeft() - left - padding;
		columns = Math.max(1, Math.min(6, (bodyWidth + gap) / (iconSize + gap)));
		gridLeft = left + padding + (bodyWidth - (columns * (iconSize + gap) - gap)) / 2;
	}

	boolean contains(int x, int y) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	int toggleTop() {
		return top + headerInset;
	}

	int toggleHeight() {
		return headerHeight - 2 * headerInset;
	}

	boolean onToggle(int x, int y) {
		return x >= toggleLeft && x < right - padding && y >= toggleTop() && y < toggleTop() + toggleHeight();
	}

	int scrollbarLeft() {
		return right - padding - scrollWidth;
	}

	int columns(boolean text) {
		return text ? 1 : columns;
	}

	int visibleRows(boolean text) {
		int height = text ? rowHeight : iconSize + gap;
		return Math.max(1, (lastRowY - firstRowY + (text ? 0 : gap)) / height);
	}

	int maxScroll(int count, boolean text) {
		int cols = columns(text);
		return Math.max(0, (count + cols - 1) / cols - visibleRows(text));
	}

	int entryX(int visible, boolean text) {
		return text ? left + padding : gridLeft + (visible % columns) * (iconSize + gap);
	}

	int entryY(int visible, boolean text) {
		return firstRowY + (text ? visible * rowHeight : visible / columns * (iconSize + gap));
	}

	int entryAt(int x, int y, int scroll, int count, boolean text) {
		if (x >= scrollbarLeft() || y < firstRowY || y >= lastRowY) return -1;
		int col = 0;
		int step = text ? rowHeight : iconSize + gap;
		int row = (y - firstRowY) / step;
		if (row >= visibleRows(text)) return -1;
		if (text) {
			if (x < left + padding) return -1;
		} else {
			int localX = x - gridLeft;
			if (localX < 0 || localX % step >= iconSize || (y - firstRowY) % step >= iconSize) return -1;
			col = localX / step;
			if (col >= columns) return -1;
		}
		int index = (scroll + row) * columns(text) + col;
		return index < count ? index : -1;
	}
}
