package orsc;

/** Shared draw and pointer bounds for the high-resolution chat panel. */
final class ChatPanelLayout {
	static final int TAB_COUNT = 5;
	final int left;
	final int width;
	final int bottom;
	final int textInset;
	final int tabsHeight;
	final int entryHeight;
	final int historyHeight;
	final int bodyFont;
	final int labelFont;
	final int labelBaseline;
	final int scrollbarWidth;
	final int lineHeight;
	private final int historyInset;

	ChatPanelLayout(int surfaceWidth, int surfaceHeight, GameInterfaceSettings.Size size,
					GameInterfaceSettings.TextSize text, int lineHeight, int labelHeight, int widestLabel) {
		left = 0;
		textInset = size.pixels(10);
		int minimumTabWidth = widestLabel + 2 * textInset;
		width = Math.min(Math.max(1, surfaceWidth - 2 * size.pixels(12)),
			Math.max(size.pixels(900), TAB_COUNT * minimumTabWidth));
		bottom = surfaceHeight;
		bodyFont = text.bodyFont;
		labelFont = text.labelFont;
		tabsHeight = Math.max(size.pixels(32), labelHeight + size.pixels(8));
		entryHeight = Math.max(size.pixels(30), lineHeight + size.pixels(6));
		historyInset = size.pixels(6);
		historyHeight = Math.max(size.pixels(120), 5 * lineHeight + historyInset);
		labelBaseline = (tabsHeight + labelHeight) / 2 - size.pixels(3);
		scrollbarWidth = size.pixels(18);
		this.lineHeight = lineHeight;
	}

	int tabsTop() {
		return bottom - tabsHeight;
	}

	int entryTop() {
		return tabsTop() - entryHeight;
	}

	int historyTop() {
		return entryTop() - historyHeight;
	}

	int recentBaseline(int row) {
		return entryTop() - historyInset - row * lineHeight;
	}

	int tabBoundary(int index) {
		return left + width * index / TAB_COUNT;
	}

	int tabAt(int x, int y) {
		if (x < left || x >= left + width || y < tabsTop() || y >= bottom) {
			return -1;
		}
		for (int index = 0; index < TAB_COUNT; index++) {
			if (x < tabBoundary(index + 1)) {
				return index;
			}
		}
		return -1;
	}

	boolean contains(int x, int y, boolean historyOpen) {
		return x >= left && x < left + width
			&& y >= (historyOpen ? historyTop() : entryTop()) && y < bottom;
	}

	boolean containsHistory(int x, int y) {
		return x >= left && x < left + width && y >= historyTop() && y < entryTop();
	}
}
