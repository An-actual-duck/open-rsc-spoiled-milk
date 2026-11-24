package orsc;

import com.openrsc.client.model.Sprite;

/** Views of the installed menu artwork, copied once when the source sprite changes. */
final class SideToolbarArtwork {
	private static final int CLASSIC_BUTTON_COUNT = 6;
	private static final int CLASSIC_BUTTON_STRIDE = 33;
	private static final int CLASSIC_BAR_OFFSET = 48;
	private static final int CLASSIC_BANNER_TOP = 23;

	private Sprite iconSource;
	private final Sprite[] icons = new Sprite[CLASSIC_BUTTON_COUNT];
	private final Sprite[] bannerSources = new Sprite[CLASSIC_BUTTON_COUNT];
	private final Sprite[] banners = new Sprite[CLASSIC_BUTTON_COUNT];

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

	Sprite banner(Sprite source, int index) {
		if (source == null) return null;
		if (bannerSources[index] != source) {
			bannerSources[index] = source;
			// The active button is baked into the strip above and alongside the
			// title. Copy the banner on either side to keep the original lettering.
			int iconX = CLASSIC_BAR_OFFSET + index * CLASSIC_BUTTON_STRIDE - source.getXShift();
			int width = source.getWidth() - SideToolbarLayout.ICON_SIZE;
			int height = SideToolbarLayout.BANNER_HEIGHT;
			banners[index] = null;
			if (iconX >= 0 && iconX <= width && source.getHeight() >= CLASSIC_BANNER_TOP + height) {
				int[] pixels = new int[width * height];
				for (int row = 0; row < height; row++) {
					int start = (CLASSIC_BANNER_TOP + row) * source.getWidth();
					System.arraycopy(source.getPixels(), start, pixels, row * width, iconX);
					System.arraycopy(source.getPixels(), start + iconX + SideToolbarLayout.ICON_SIZE,
						pixels, row * width + iconX, width - iconX);
				}
				banners[index] = new Sprite(pixels, width, height);
			}
		}
		return banners[index];
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
