package orsc;

/** Shared drawing and hit-testing bounds for the minimap and world map. */
final class MapViewport {
	final int left;
	final int top;
	final int right;
	final int bottom;
	final int labelFont;
	final int labelLine;
	final int pixelsPerTile;
	final int dotRadius;
	final int markerSize;

	MapViewport(int left, int top, int right, int bottom, GameInterfaceSettings.Size size,
				GameInterfaceSettings.TextSize text, int labelLine) {
		this.left = left;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.labelFont = text.labelFont;
		this.labelLine = labelLine;
		pixelsPerTile = size.pixels(6);
		dotRadius = Math.max(2, size.pixels(4));
		markerSize = size.pixels(23);
	}

	boolean contains(int x, int y) {
		return x >= left && x < right && y >= top && y < bottom;
	}
}
