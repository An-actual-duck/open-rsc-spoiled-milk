package orsc;

import java.awt.Rectangle;

/** Shared minimap frame and map viewport bounds. */
final class MinimapFrameLayout {
	final int left;
	final int top;
	final int width;
	final int bottom;
	final int padding;
	final int headerHeight;
	final MapViewport viewport;
	final Rectangle expand;

	MinimapFrameLayout(int surfaceWidth, int gameHeight, int anchor, boolean floating, int chatRight,
					   int chatEntryTop, GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text,
					   int labelLine) {
		padding = size.pixels(12);
		width = Math.min(size.pixels(420), surfaceWidth - 2 * padding);
		left = surfaceWidth - size.pixels(6) - width;
		top = floating ? padding : anchor;
		int limit = floating ? Math.min(gameHeight, anchor) : gameHeight;
		if (left < chatRight) limit = Math.min(limit, chatEntryTop);
		bottom = Math.min(top + size.pixels(420), limit - padding);
		headerHeight = size.pixels(SideToolbarLayout.HEADER_HEIGHT);
		viewport = new MapViewport(left + padding, top + headerHeight + padding / 2,
			left + width - padding, bottom - padding, size, text, labelLine);
		int buttonSize = headerHeight - 2 * size.pixels(2);
		expand = new Rectangle(left + width - padding - buttonSize, top + (headerHeight - buttonSize) / 2,
			buttonSize, buttonSize);
	}

	boolean contains(int x, int y) {
		return x >= left && x < left + width && y >= top && y < bottom;
	}
}
