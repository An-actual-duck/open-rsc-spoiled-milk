package orsc;

import com.openrsc.client.model.Sprite;

/** Views of the installed menu artwork, copied once when the source sprite changes. */
final class SideToolbarArtwork {
	private static final int CLASSIC_BUTTON_COUNT = 6;
	private static final int CLASSIC_BUTTON_STRIDE = 33;

	private Sprite iconSource;
	private final Sprite[] icons = new Sprite[CLASSIC_BUTTON_COUNT];

	Sprite icon(Sprite source, int index) {
		if (iconSource != source) {
			iconSource = source;
			for (int button = 0; button < icons.length; button++) {
				icons[button] = crop(source, button * CLASSIC_BUTTON_STRIDE, 0,
					SideToolbarLayout.ICON_SIZE, SideToolbarLayout.ICON_SIZE);
			}
		}
		return icons[index];
	}

	private static Sprite crop(Sprite source, int x, int y, int width, int height) {
		if (source == null || x + width > source.getWidth() || y + height > source.getHeight()) return null;
		int[] pixels = new int[width * height];
		for (int row = 0; row < height; row++) {
			System.arraycopy(source.getPixels(), (y + row) * source.getWidth() + x, pixels, row * width, width);
		}
		return new Sprite(pixels, width, height);
	}
}
