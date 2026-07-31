package com.openrsc.interfaces.misc;

import com.openrsc.client.model.Sprite;
import orsc.graphics.RendererTransparency;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Cached, semantic access to the replaceable world-editor icon set. */
public final class WorldEditorIconRegistry {
	public static final int ICON_SIZE = 24;
	private static final int ALPHA_THRESHOLD = 64;
	private static final String DEVELOPMENT_ROOT = "dev/myworld/assets/ui/world-editor";
	private static final String RESOURCE_ROOT = "myworld-assets/ui/world-editor/";

	public enum Key {
		TOOLBAR_COLLAPSE("toolbar-collapse.png", "Dock"),
		TOOLBAR_EXPAND("toolbar-expand.png", "Open"),
		MODE_NAVIGATE("mode-navigate.png", "Nav"),
		MODE_INSPECT("mode-inspect.png", "Look"),
		MODE_SCENERY("mode-scenery.png", "Obj"),
		MODE_NPC("mode-npc.png", "NPC"),
		MODE_ITEMS("mode-items.png", "Item"),
		FIELD_ELEVATION("field-elevation.png", "Elev"),
		FIELD_FLOOR_COLOR("field-floor-color.png", "Color"),
		FIELD_FLOOR_TEXTURE("field-floor-texture.png", "Floor"),
		FIELD_ROOF("field-roof.png", "Roof"),
		FIELD_WALL_NORTH("field-wall-north.png", "N Wall"),
		FIELD_WALL_EAST("field-wall-east.png", "E Wall"),
		FIELD_WALL_DIAGONAL("field-wall-diagonal.png", "D Wall"),
		TOOL_BRUSH_1X1("tool-brush-1x1.png", "1x1"),
		TOOL_BRUSH_3X3("tool-brush-3x3.png", "3x3"),
		PROFILE_BUILD("profile-build.png", "Build"),
		ACTION_SAVE("action-save.png", "Save"),
		ACTION_PIN("action-pin.png", "Pin"),
		ACTION_CLOSE("action-close.png", "Close");

		private final String filename;
		private final String fallbackLabel;

		Key(String filename, String fallbackLabel) {
			this.filename = filename;
			this.fallbackLabel = fallbackLabel;
		}

		public String filename() {
			return filename;
		}

		public String fallbackLabel() {
			return fallbackLabel;
		}
	}

	private final Map<Key, Sprite> sprites = new EnumMap<Key, Sprite>(Key.class);
	private final Map<Key, String> failures = new EnumMap<Key, String>(Key.class);
	private boolean initialized;

	public synchronized void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		for (Key key : Key.values()) {
			try {
				Sprite sprite = load(key);
				if (sprite == null) {
					failures.put(key, "missing");
				} else {
					sprites.put(key, sprite);
				}
			} catch (IOException | RuntimeException exception) {
				String message = exception.getMessage();
				failures.put(key, message == null || message.trim().isEmpty() ? "invalid" : message.trim());
			}
		}
		if (!failures.isEmpty()) {
			System.out.println("[world-editor icons] " + failures.size() + " unavailable; labeled fallbacks active: "
				+ joinFailures());
		}
	}

	public Sprite get(Key key) {
		initialize();
		return sprites.get(key);
	}

	public boolean isLoaded(Key key) {
		initialize();
		return sprites.containsKey(key);
	}

	public int loadedCount() {
		initialize();
		return sprites.size();
	}

	public List<Key> missingKeys() {
		initialize();
		return Collections.unmodifiableList(new ArrayList<Key>(failures.keySet()));
	}

	private Sprite load(Key key) throws IOException {
		BufferedImage image = read(key.filename());
		if (image == null) {
			return null;
		}
		if (image.getWidth() != ICON_SIZE || image.getHeight() != ICON_SIZE) {
			throw new IOException("expected 24x24, got " + image.getWidth() + "x" + image.getHeight());
		}
		if (!image.getColorModel().hasAlpha()) {
			throw new IOException("PNG has no alpha channel");
		}
		int[] pixels = new int[ICON_SIZE * ICON_SIZE];
		image.getRGB(0, 0, ICON_SIZE, ICON_SIZE, pixels, 0, ICON_SIZE);
		for (int i = 0; i < pixels.length; i++) {
			int alpha = pixels[i] >>> 24;
			if (alpha < ALPHA_THRESHOLD) {
				pixels[i] = RendererTransparency.TRANSPARENT_SAMPLE;
			} else {
				int rgb = pixels[i] & RendererTransparency.RGB_MASK;
				pixels[i] = rgb == RendererTransparency.TRANSPARENT_SAMPLE
					? RendererTransparency.OPAQUE_BLACK_REPLACEMENT : rgb;
			}
		}
		Sprite sprite = new Sprite(pixels, ICON_SIZE, ICON_SIZE);
		sprite.setShift(0, 0);
		sprite.setRequiresShift(false);
		sprite.setSomething(ICON_SIZE, ICON_SIZE);
		return sprite;
	}

	private BufferedImage read(String filename) throws IOException {
		Path userDirectory = Paths.get(System.getProperty("user.dir", ".")).normalize();
		Path[] candidates = new Path[] {
			userDirectory.resolve(DEVELOPMENT_ROOT).resolve(filename).normalize(),
			userDirectory.resolve("..").resolve(DEVELOPMENT_ROOT).resolve(filename).normalize()
		};
		for (Path candidate : candidates) {
			File file = candidate.toFile();
			if (file.isFile()) {
				return ImageIO.read(file);
			}
		}
		try (InputStream input = WorldEditorIconRegistry.class.getClassLoader()
			.getResourceAsStream(RESOURCE_ROOT + filename)) {
			return input == null ? null : ImageIO.read(input);
		}
	}

	private String joinFailures() {
		StringBuilder text = new StringBuilder();
		for (Key key : Key.values()) {
			String failure = failures.get(key);
			if (failure == null) {
				continue;
			}
			if (text.length() > 0) {
				text.append(", ");
			}
			text.append(key.filename()).append(" (").append(failure).append(')');
		}
		return text.toString();
	}
}
