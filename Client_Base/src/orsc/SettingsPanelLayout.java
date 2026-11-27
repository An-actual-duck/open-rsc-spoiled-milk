package orsc;

/** Scaled draw and hit bounds for the tab strip, rows, and slider tracks. */
final class SettingsPanelLayout {
	static final int SOCIAL = 0;
	static final int GENERAL = 1;
	static final int GRAPHICS = 2;
	static final int TAB_COUNT = 3;

	final int left;
	final int right;
	final int top;
	final int bottom;
	final int padding;
	final int tabHeight;
	final int rowHeight;
	final int sliderHeight;
	final int thumbSize;
	final int trackHeight;
	final int scrollWidth;
	final int bodyFont;
	final int labelFont;
	final int bodyLine;
	final int labelLine;
	final int logoutHeight;
	private final GameInterfaceSettings.Size size;
	private final GameInterfaceSettings.TextSize text;
	private final boolean bottomLauncher;
	private final int width;
	private final int anchor;
	private final int chatTop;
	private final int chatRight;

	SettingsPanelLayout(int width, int anchor, boolean bottomLauncher, int chatRight, int chatTop,
						GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text,
						int bodyLine, int labelLine) {
		this.width = width;
		this.anchor = anchor;
		this.bottomLauncher = bottomLauncher;
		this.chatRight = chatRight;
		this.chatTop = chatTop;
		this.size = size;
		this.text = text;
		this.bodyLine = bodyLine;
		this.labelLine = labelLine;
		bodyFont = text.bodyFont;
		labelFont = text.labelFont;
		padding = size.pixels(10);
		tabHeight = Math.max(size.pixels(36), labelLine + 2 * padding);
		rowHeight = Math.max(size.pixels(29), bodyLine + padding);
		thumbSize = Math.max(10, size.pixels(16));
		trackHeight = Math.max(4, size.pixels(6));
		scrollWidth = size.pixels(14);
		sliderHeight = rowHeight + thumbSize + padding;
		logoutHeight = Math.max(size.pixels(31), labelLine + padding);
		right = width - size.pixels(6);
		left = Math.max(padding, right - Math.max(size.pixels(428), text == GameInterfaceSettings.TextSize.EXTRA_LARGE ? 370 : 0));
		int limit = bottomLauncher ? anchor - padding : chatTop;
		if (left < chatRight) limit = Math.min(limit, chatTop - padding);
		int height = Math.min(size.pixels(438), limit - (bottomLauncher ? padding : anchor));
		top = bottomLauncher ? limit - height : anchor;
		bottom = top + height;
	}

	boolean matches(int width, int anchor, boolean bottomLauncher, int chatRight, int chatTop,
					GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text) {
		return this.width == width && this.anchor == anchor && this.bottomLauncher == bottomLauncher
			&& this.chatTop == chatTop && this.chatRight == chatRight && this.size == size && this.text == text;
	}

	boolean contains(int x, int y) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	int tabLeft(int tab) { return left + tab * (right - left) / TAB_COUNT; }
	int listTop() { return top + tabHeight + padding; }
	int listBottom() { return logoutTop() - padding; }
	int listHeight() { return listBottom() - listTop(); }
	int listLeft() { return left + padding; }
	int scrollbarLeft() { return right - padding - scrollWidth; }
	int logoutTop() { return bottom - padding - logoutHeight; }
	int sliderLeft() { return listLeft() + padding + thumbSize / 2; }
	int sliderRight() { return scrollbarLeft() - padding - thumbSize / 2; }
	int sliderY(int rowTop) { return rowTop + rowHeight + thumbSize / 2; }

	int tabAt(int x, int y) {
		if (!contains(x, y) || y >= top + tabHeight) return -1;
		if (x < tabLeft(GENERAL)) return SOCIAL;
		return x < tabLeft(GRAPHICS) ? GENERAL : GRAPHICS;
	}

	boolean overList(int x, int y) {
		return x >= listLeft() && x < right - padding && y >= listTop() && y < listBottom();
	}

	boolean overSlider(int x, int y, int rowTop) {
		return x >= sliderLeft() - thumbSize / 2 && x <= sliderRight() + thumbSize / 2
			&& y >= rowTop + rowHeight && y < Math.min(rowTop + sliderHeight, listBottom());
	}

	int maxScroll(int[] heights) {
		int index = heights.length;
		int available = listHeight();
		while (index > 0 && heights[index - 1] <= available) {
			available -= heights[--index];
		}
		return Math.min(index, Math.max(0, heights.length - 1));
	}

	static int sliderLevel(int x, int left, int right, int min, int max) {
		if (x <= left) return min;
		if (x >= right) return max;
		return min + Math.round((float) (x - left) * (max - min) / (right - left));
	}

	static int sliderX(int level, int left, int right, int min, int max) {
		return left + Math.round((float) (level - min) * (right - left) / (max - min));
	}
}
