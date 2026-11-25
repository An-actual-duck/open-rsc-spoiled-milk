package orsc;

/** Shared minimap frame, map viewport and legend bounds. */
final class MinimapFrameLayout {
	final int left;
	final int top;
	final int width;
	final int bottom;
	final int padding;
	final int headerHeight;
	final int labelFont;
	final int labelLine;
	final int pixelsPerTile;
	final int dotRadius;
	final int bankSize;
	final int legendFont;
	private final int legendHeight;

	MinimapFrameLayout(int surfaceWidth, int gameHeight, int anchor, boolean floating, int chatRight,
					   int chatEntryTop, GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text,
					   int labelLine) {
		this.labelLine = labelLine;
		labelFont = text.labelFont;
		padding = size.pixels(10);
		width = Math.min(size.pixels(264), surfaceWidth - 2 * padding);
		left = surfaceWidth - size.pixels(6) - width;
		top = floating ? padding : anchor;
		int limit = floating ? Math.min(gameHeight, anchor) : gameHeight;
		if (left < chatRight) limit = Math.min(limit, chatEntryTop);
		bottom = Math.min(top + size.pixels(258), limit - padding);
		headerHeight = Math.max(size.pixels(36), labelLine + padding);
		pixelsPerTile = size.pixels(4);
		dotRadius = Math.max(2, size.pixels(3));
		bankSize = size.pixels(17);
		legendFont = width < 230 ? 1 : Math.max(1, labelFont - 1);
		legendHeight = Math.max(size.pixels(30), labelLine + padding);
	}

	int mapLeft() { return left + padding; }
	int mapRight() { return left + width - padding; }
	int mapTop() { return top + headerHeight + padding / 2; }
	int mapBottom() { return bottom - legendHeight; }

	boolean containsMap(int x, int y) {
		return x >= mapLeft() && x < mapRight() && y >= mapTop() && y < mapBottom();
	}

	boolean contains(int x, int y) {
		return x >= left && x < left + width && y >= top && y < bottom;
	}
}
