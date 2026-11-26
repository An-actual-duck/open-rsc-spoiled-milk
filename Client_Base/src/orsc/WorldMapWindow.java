package orsc;

import orsc.graphics.two.MudClientGraphics;

import java.awt.Rectangle;

final class WorldMapWindow {
	private static final MinimapMarkerIcon[] ICONS = MinimapMarkerIcon.values();
	private static final int KEY_ROWS = ICONS.length + 1;
	private static final int BACKGROUND = InterfacePalette.PANEL;
	private static final int BORDER = InterfacePalette.BORDER;
	private static final int GOLD = 0xFFDC82;
	private static final int TEXT = InterfacePalette.TEXT;

	private Layout layout;
	private MinimapAtlas atlas;
	private double centerX;
	private double centerY;
	private int mouseX = -1;
	private int mouseY = -1;
	private int keyOffset;
	private boolean dragging;
	private boolean closePressed;
	private boolean closeRequested;

	WorldMapWindow(double playerX, double playerY) {
		centerX = playerX;
		centerY = playerY;
	}

	synchronized void press(int x, int y, boolean primary) {
		move(x, y);
		if (!primary || layout == null || closeRequested) return;
		if (layout.close.contains(x, y)) {
			closePressed = true;
		} else if (layout.viewport.contains(x, y)) {
			dragging = true;
		} else if (layout.maxKeyOffset > 0 && layout.scrollbar.contains(x, y)) {
			int scrollHeight = layout.scrollbar.height - layout.thumbHeight();
			int offset = (y - layout.scrollbar.y - layout.thumbHeight() / 2) * layout.maxKeyOffset
				/ Math.max(1, scrollHeight);
			keyOffset = layout.clampKeyOffset(offset);
		}
	}

	synchronized void move(int x, int y) {
		if (dragging && atlas != null) {
			centerX = atlas.clampX(centerX + (x - mouseX) / (double) layout.viewport.pixelsPerTile);
			centerY = atlas.clampY(centerY - (y - mouseY) / (double) layout.viewport.pixelsPerTile);
		}
		mouseX = x;
		mouseY = y;
	}

	synchronized void release(int x, int y) {
		if (x >= 0 && y >= 0) {
			move(x, y);
			if (closePressed && layout.close.contains(x, y)) requestClose();
		}
		dragging = false;
		closePressed = false;
	}

	synchronized void scroll(int x, int y, int amount) {
		if (layout != null && layout.key.contains(x, y)) {
			keyOffset = layout.clampKeyOffset(keyOffset + amount);
		}
	}

	synchronized void requestClose() {
		closeRequested = true;
		dragging = false;
	}

	synchronized boolean shouldClose() {
		return closeRequested;
	}

	void draw(MudClientGraphics surface, MinimapView map, NativeLayeredTerrainSnapshot terrain,
			  double playerX, double playerY) {
		GameInterfaceSettings.Size size = GameInterfaceSettings.size();
		GameInterfaceSettings.TextSize text = GameInterfaceSettings.textSize();
		MinimapAtlas current = map.atlasFor(terrain);
		MinimapAtlas active;
		Layout frame;
		double mapX;
		double mapY;
		int pointerX;
		int pointerY;
		int offset;
		boolean isDragging;
		synchronized (this) {
			if (layout == null || !layout.matches(surface.width2, surface.height2, size, text)) {
				layout = new Layout(surface.width2, surface.height2, size, text, surface.fontHeight(text.labelFont));
			}
			if (current != null && atlas != current) {
				atlas = current;
				centerX = playerX;
				centerY = playerY;
				dragging = false;
			}
			active = atlas;
			frame = layout;
			mapX = centerX;
			mapY = centerY;
			pointerX = mouseX;
			pointerY = mouseY;
			keyOffset = frame.clampKeyOffset(keyOffset);
			offset = keyOffset;
			isDragging = dragging;
		}

		surface.drawBox(0, 0, frame.width, frame.height, BACKGROUND);
		surface.drawVerticalGradient(0, 0, frame.width, frame.headerHeight, InterfacePalette.HEADER_LIGHT, InterfacePalette.HEADER_DARK);
		surface.drawLineHoriz(0, frame.headerHeight - 1, frame.width, BORDER);
		int baseline = (frame.headerHeight + frame.labelLine) / 2 - 2;
		String title = "World map";
		surface.drawString(title, frame.padding, baseline, GOLD, text.labelFont);
		int hintX = frame.padding * 2 + surface.stringWidth(text.labelFont, title);
		surface.drawString("Drag to pan", hintX, baseline, TEXT, text.labelFont);
		drawControl(surface, frame.close, frame.close.contains(pointerX, pointerY), true);
		map.drawWorld(surface, frame.viewport, active, mapX, mapY, playerX, playerY,
			isDragging ? -1 : pointerX, isDragging ? -1 : pointerY);
		drawKey(surface, frame, offset);
	}

	private static void drawKey(MudClientGraphics surface, Layout frame, int offset) {
		Rectangle key = frame.key;
		surface.drawBox(key.x, key.y, key.width, key.height, InterfacePalette.ROW_ALTERNATE);
		surface.drawBoxBorder(key.x, key.width, key.y, key.height, BORDER);
		surface.drawString("Map key", key.x + frame.padding, frame.keyRowsTop - frame.padding, GOLD, frame.text.labelFont);
		surface.drawLineHoriz(key.x + frame.padding, frame.keyRowsTop - 4, key.width - frame.padding * 2, BORDER);
		int left = key.x + frame.padding;
		int iconSize = frame.viewport.markerSize;
		int labelX = left + iconSize + frame.padding;
		for (int row = 0; row < frame.visibleKeyRows && row + offset < KEY_ROWS; row++) {
			int index = row + offset;
			int y = frame.keyRowsTop + row * frame.keyRowHeight;
			int middle = y + frame.keyRowHeight / 2;
			if (index == 0) {
				MinimapView.drawLocalPlayer(surface, left + iconSize / 2, middle, frame.viewport.dotRadius + 1);
			} else {
				MinimapView.drawLocationMarker(surface, left, middle - iconSize / 2, iconSize, ICONS[index - 1].symbol);
			}
			String label = index == 0 ? "You" : ICONS[index - 1].keyLabel;
			surface.drawString(label, labelX, middle + frame.labelLine / 2 - 2, TEXT, frame.text.labelFont);
		}
		if (frame.maxKeyOffset > 0) {
			Rectangle bar = frame.scrollbar;
			int thumb = frame.thumbHeight();
			int top = bar.y + offset * (bar.height - thumb) / frame.maxKeyOffset;
			surface.drawBox(bar.x, bar.y, bar.width, bar.height, InterfacePalette.INSET);
			surface.drawBox(bar.x, top, bar.width, thumb, BORDER);
		}
	}

	static void drawControl(MudClientGraphics surface, Rectangle bounds, boolean hover, boolean close) {
		surface.drawBox(bounds.x, bounds.y, bounds.width, bounds.height, hover ? InterfacePalette.BORDER : InterfacePalette.ROW);
		surface.drawBoxBorder(bounds.x, bounds.width, bounds.y, bounds.height, BORDER);
		int edge = bounds.width / 4;
		int left = bounds.x + edge;
		int top = bounds.y + edge;
		int right = bounds.x + bounds.width - edge - 1;
		int bottom = bounds.y + bounds.height - edge - 1;
		if (close) {
			for (int step = 0; step <= right - left; step++) {
				surface.drawBox(left + step, top + step, 2, 2, TEXT);
				surface.drawBox(right - step, top + step, 2, 2, TEXT);
			}
		} else {
			int arm = Math.max(3, bounds.width / 6);
			surface.drawLineHoriz(left, top, arm, TEXT);
			surface.drawLineHoriz(right - arm + 1, top, arm, TEXT);
			surface.drawLineHoriz(left, bottom, arm, TEXT);
			surface.drawLineHoriz(right - arm + 1, bottom, arm, TEXT);
			surface.drawLineVert(left, top, TEXT, arm);
			surface.drawLineVert(right, top, TEXT, arm);
			surface.drawLineVert(left, bottom - arm + 1, TEXT, arm);
			surface.drawLineVert(right, bottom - arm + 1, TEXT, arm);
		}
	}

	static final class Layout {
		final int width;
		final int height;
		final GameInterfaceSettings.Size size;
		final GameInterfaceSettings.TextSize text;
		final int labelLine;
		final int padding;
		final int headerHeight;
		final MapViewport viewport;
		final Rectangle close;
		final Rectangle key;
		final Rectangle scrollbar;
		final int keyRowsTop;
		final int keyRowHeight;
		final int visibleKeyRows;
		final int maxKeyOffset;

		Layout(int width, int height, GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text, int labelLine) {
			this.width = width;
			this.height = height;
			this.size = size;
			this.text = text;
			this.labelLine = labelLine;
			padding = size.pixels(14);
			int closeSize = size.pixels(32);
			headerHeight = Math.max(size.pixels(52), labelLine + padding * 2);
			close = new Rectangle(width - padding - closeSize, (headerHeight - closeSize) / 2, closeSize, closeSize);
			int keyWidth = Math.min(Math.max(size.pixels(250), labelLine * 12), width / 2);
			int top = headerHeight + padding;
			int bottom = height - padding;
			key = new Rectangle(width - padding - keyWidth, top, keyWidth, bottom - top);
			viewport = new MapViewport(padding, top, key.x - padding, bottom, size, text, labelLine);
			keyRowsTop = top + labelLine + padding * 2;
			keyRowHeight = Math.max(viewport.markerSize, labelLine) + size.pixels(5);
			visibleKeyRows = Math.max(0, (bottom - padding - keyRowsTop) / keyRowHeight);
			maxKeyOffset = Math.max(0, KEY_ROWS - visibleKeyRows);
			scrollbar = new Rectangle(width - padding * 2 - size.pixels(6), keyRowsTop, size.pixels(6),
				Math.max(0, visibleKeyRows * keyRowHeight));
		}

		boolean matches(int width, int height, GameInterfaceSettings.Size size, GameInterfaceSettings.TextSize text) {
			return this.width == width && this.height == height && this.size == size && this.text == text;
		}

		int clampKeyOffset(int offset) {
			return Math.max(0, Math.min(offset, maxKeyOffset));
		}

		int thumbHeight() {
			return Math.min(scrollbar.height, Math.max(size.pixels(20), scrollbar.height * visibleKeyRows / KEY_ROWS));
		}
	}
}
