package orsc;

/** Bounds for the minimap frame while its contents are being redesigned. */
final class MinimapFrameLayout {
	final int left;
	final int top;
	final int width;
	final int bottom;
	final int padding;
	final int headerHeight;
	final int bodyFont;
	final int labelFont;
	final int bodyLine;
	final int labelLine;

	MinimapFrameLayout(int surfaceWidth, int gameHeight, int anchor, boolean floating, int chatRight,
					   int chatEntryTop, GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text,
					   int bodyLine, int labelLine) {
		this.bodyLine = bodyLine;
		this.labelLine = labelLine;
		bodyFont = text.bodyFont;
		labelFont = text.labelFont;
		padding = size.pixels(10);
		width = Math.min(size.pixels(244), surfaceWidth - 2 * padding);
		left = surfaceWidth - size.pixels(6) - width;
		top = floating ? padding : anchor;
		int limit = floating ? Math.min(gameHeight, anchor) : gameHeight;
		if (left < chatRight) limit = Math.min(limit, chatEntryTop);
		bottom = Math.min(top + size.pixels(194), limit - padding);
		headerHeight = Math.max(size.pixels(36), labelLine + padding);
	}

	boolean contains(int x, int y) {
		return x >= left && x < left + width && y >= top && y < bottom;
	}
}
