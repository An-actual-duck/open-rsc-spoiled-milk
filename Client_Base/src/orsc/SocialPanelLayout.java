package orsc;

import java.util.function.ToIntFunction;

/** Draw and hit bounds for the scaled Social sidebar. */
final class SocialPanelLayout {
	static final int FRIENDS = 0;
	static final int CLAN = 1;
	static final int PARTY = 2;
	static final int ONLINE = 3;
	private static final String[] LABELS = {"Friends", "Clan", "Party", "All Online"};

	final int left;
	final int right;
	final int top;
	final int bottom;
	final int padding;
	final int tabHeight;
	final int rowHeight;
	final int bodyFont;
	final int labelFont;
	final int bodyLine;
	final int labelLine;
	final int actionHeight;
	final int scrollWidth;
	private final int surfaceWidth;
	private final int anchor;
	private final boolean bottomLauncher;
	private final int chatRight;
	private final int chatTop;
	private final GameInterfaceSettings.Size size;
	private final GameInterfaceSettings.TextSize text;
	private final boolean clans;
	private final int[] tabEdges = new int[LABELS.length + 1];

	SocialPanelLayout(int surfaceWidth, int anchor, boolean bottomLauncher, int chatRight, int chatTop,
					  GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text, boolean clans,
					  int bodyLine, int labelLine, ToIntFunction<String> measureLabel) {
		this.surfaceWidth = surfaceWidth;
		this.anchor = anchor;
		this.bottomLauncher = bottomLauncher;
		this.chatRight = chatRight;
		this.chatTop = chatTop;
		this.size = size;
		this.text = text;
		this.clans = clans;
		this.bodyLine = bodyLine;
		this.labelLine = labelLine;
		bodyFont = text.bodyFont;
		labelFont = text.labelFont;
		padding = size.pixels(10);
		scrollWidth = size.pixels(14);
		tabHeight = Math.max(size.pixels(36), labelLine + 2 * padding);
		rowHeight = Math.max(size.pixels(28), bodyLine + padding);
		actionHeight = Math.max(size.pixels(32), labelLine + padding);

		int labelsWidth = 0;
		for (int tab = FRIENDS; tab <= ONLINE; tab++) {
			int width = tab == CLAN && !clans ? 0 : measureLabel.applyAsInt(LABELS[tab]) + 2 * padding;
			labelsWidth += width;
			tabEdges[tab + 1] = labelsWidth;
		}
		right = surfaceWidth - size.pixels(6);
		int width = Math.min(right - padding, Math.max(size.pixels(365), labelsWidth));
		left = right - width;
		for (int edge = 1; edge < tabEdges.length; edge++) {
			tabEdges[edge] = left + tabEdges[edge] * width / labelsWidth;
		}
		tabEdges[0] = left;

		int limit = bottomLauncher ? anchor - padding : chatTop;
		if (left < chatRight) limit = Math.min(limit, chatTop - padding);
		int height = Math.min(size.pixels(342), limit - (bottomLauncher ? padding : anchor));
		top = bottomLauncher ? limit - height : anchor;
		bottom = top + height;
	}

	boolean matches(int width, int anchor, boolean bottomLauncher, int chatRight, int chatTop,
					GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text, boolean clans) {
		return surfaceWidth == width && this.anchor == anchor && this.bottomLauncher == bottomLauncher
			&& this.chatRight == chatRight && this.chatTop == chatTop && this.size == size && this.text == text
			&& this.clans == clans;
	}

	boolean contains(int x, int y) {
		return x >= left && x < right && y >= top && y < bottom;
	}

	String tabLabel(int tab) { return LABELS[tab]; }
	int tabLeft(int tab) { return tabEdges[tab]; }
	int tabWidth(int tab) { return tabEdges[tab + 1] - tabEdges[tab]; }
	int contentTop() { return top + tabHeight + padding; }
	int bodyLeft() { return left + padding; }
	int bodyRight() { return right - padding; }
	int actionTop() { return bottom - padding - actionHeight; }
	int listBottom(int tab) { return tab == ONLINE ? bottom - padding : actionTop() - padding; }
	int scrollbarLeft() { return bodyRight() - scrollWidth; }

	int tabAt(int x, int y) {
		if (!contains(x, y) || y >= top + tabHeight) return -1;
		for (int tab = FRIENDS; tab <= ONLINE; tab++) {
			if (x < tabEdges[tab + 1]) return tab;
		}
		return -1;
	}

	int listTop(int tab, boolean inClan) {
		if (tab == CLAN && inClan) return contentTop() + 3 * bodyLine + padding;
		if (tab == FRIENDS || tab == ONLINE) return contentTop() + bodyLine + padding;
		return contentTop();
	}

	int visibleRows(int tab, int listTop) {
		return Math.max(0, (listBottom(tab) - listTop) / rowHeight);
	}

	int rowAt(int tab, int x, int y, int listTop, int scroll, int count) {
		if (x < bodyLeft() || x >= scrollbarLeft() || y < listTop) return -1;
		int row = (y - listTop) / rowHeight;
		int index = scroll + row;
		return row < visibleRows(tab, listTop) && index < count ? index : -1;
	}

	boolean isOverList(int tab, int x, int y, int listTop) {
		return x >= bodyLeft() && x < bodyRight() && y >= listTop && y < listBottom(tab);
	}

	int actionLeft(int index, int count) {
		return bodyLeft() + (bodyRight() - bodyLeft() + padding) * index / count;
	}
}
