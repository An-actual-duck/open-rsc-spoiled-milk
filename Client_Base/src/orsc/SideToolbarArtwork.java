package orsc;

import com.openrsc.client.model.Sprite;
import java.io.File;
import java.util.EnumMap;
import java.util.Locale;

/** Classic menu crops and the ability launchers' packaged artwork. */
final class SideToolbarArtwork {
	private static final int CLASSIC_BUTTON_COUNT = 6;
	private static final int CLASSIC_BUTTON_STRIDE = 33;
	static final int ICON_BACKING = 0x7092BE;

	private Sprite iconSource;
	private final Sprite[] icons = new Sprite[CLASSIC_BUTTON_COUNT];
	private final EnumMap<AbilityBook, Sprite> abilityIcons = new EnumMap<>(AbilityBook.class);

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

	Sprite icon(AbilityBook book, ClientExternalAssetLoader assets) {
		if (!abilityIcons.containsKey(book)) {
			String name = book.name().toLowerCase(Locale.ROOT);
			File source = new File(ClientExternalAssetLoader.DEVELOPMENT_ASSET_ROOT + "ui/toolbar/" + name + ".png");
			int size = SideToolbarLayout.ICON_SIZE;
			abilityIcons.put(book, assets.loadExternalInterfaceSprite(source, size, size));
		}
		return abilityIcons.get(book);
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
