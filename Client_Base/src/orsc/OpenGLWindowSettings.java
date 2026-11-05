package orsc;

import java.util.Properties;

final class OpenGLWindowSettings {
	static final String WINDOW_MODE_PROPERTY_KEY = "opengl_window_mode";
	private static final String WINDOWED_X_PROPERTY_KEY = "opengl_windowed_x";
	private static final String WINDOWED_Y_PROPERTY_KEY = "opengl_windowed_y";
	private static final String WINDOWED_WIDTH_PROPERTY_KEY = "opengl_windowed_width";
	private static final String WINDOWED_HEIGHT_PROPERTY_KEY = "opengl_windowed_height";
	private static final String WINDOW_MODE_PROPERTY = "spoiledmilk.openglWindowMode";
	private static final String WINDOW_MODE_ENV = "SPOILED_MILK_OPENGL_WINDOW_MODE";

	private static final boolean runtimeWindowModeOverride = hasRuntimeSetting(WINDOW_MODE_PROPERTY, WINDOW_MODE_ENV);
	private static volatile Mode mode = Mode.from(readRuntimeSetting(WINDOW_MODE_PROPERTY, WINDOW_MODE_ENV));
	private static volatile int windowedX = 80;
	private static volatile int windowedY = 80;
	private static volatile int windowedWidth = 512;
	private static volatile int windowedHeight = 346;
	private static volatile boolean hasWindowedBounds;

	private OpenGLWindowSettings() {
	}

	static Mode getMode() {
		return mode;
	}

	static Mode cycleMode() {
		Mode next = mode.next();
		mode = next;
		return next;
	}

	static Mode setMode(Mode next) {
		mode = next == null ? Mode.BORDERLESS_FULLSCREEN : next;
		return mode;
	}

	static boolean hasWindowedBounds() {
		return hasWindowedBounds;
	}

	static int getWindowedX() {
		return windowedX;
	}

	static int getWindowedY() {
		return windowedY;
	}

	static int getWindowedWidth() {
		return windowedWidth;
	}

	static int getWindowedHeight() {
		return windowedHeight;
	}

	static boolean setWindowedBounds(int x, int y, int width, int height) {
		if (width <= 0 || height <= 0) {
			return false;
		}
		boolean changed = !hasWindowedBounds
			|| windowedX != x
			|| windowedY != y
			|| windowedWidth != width
			|| windowedHeight != height;
		windowedX = x;
		windowedY = y;
		windowedWidth = width;
		windowedHeight = height;
		hasWindowedBounds = true;
		return changed;
	}

	static void loadFromClientSettings(Properties props) {
		if (props == null) {
			return;
		}
		if (!runtimeWindowModeOverride) {
			String configuredMode = props.getProperty(WINDOW_MODE_PROPERTY_KEY);
			if (configuredMode != null && !configuredMode.trim().isEmpty()) {
				mode = Mode.from(configuredMode);
			}
		}
		loadWindowedBounds(props);
	}

	static void saveToClientSettings(Properties props) {
		if (props != null) {
			props.setProperty(WINDOW_MODE_PROPERTY_KEY, mode.id);
			if (hasWindowedBounds) {
				props.setProperty(WINDOWED_X_PROPERTY_KEY, String.valueOf(windowedX));
				props.setProperty(WINDOWED_Y_PROPERTY_KEY, String.valueOf(windowedY));
				props.setProperty(WINDOWED_WIDTH_PROPERTY_KEY, String.valueOf(windowedWidth));
				props.setProperty(WINDOWED_HEIGHT_PROPERTY_KEY, String.valueOf(windowedHeight));
			}
		}
	}

	private static void loadWindowedBounds(Properties props) {
		if (!hasText(props.getProperty(WINDOWED_WIDTH_PROPERTY_KEY))
			|| !hasText(props.getProperty(WINDOWED_HEIGHT_PROPERTY_KEY))) {
			return;
		}
		int x = readInt(props, WINDOWED_X_PROPERTY_KEY, windowedX);
		int y = readInt(props, WINDOWED_Y_PROPERTY_KEY, windowedY);
		int width = readInt(props, WINDOWED_WIDTH_PROPERTY_KEY, windowedWidth);
		int height = readInt(props, WINDOWED_HEIGHT_PROPERTY_KEY, windowedHeight);
		if (width > 0 && height > 0) {
			setWindowedBounds(x, y, width, height);
		}
	}

	private static int readInt(Properties props, String key, int fallback) {
		String value = props.getProperty(key);
		if (value == null || value.trim().isEmpty()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static boolean hasRuntimeSetting(String propertyName, String envName) {
		return !readRuntimeSetting(propertyName, envName).isEmpty();
	}

	private static boolean hasText(String value) {
		return value != null && !value.trim().isEmpty();
	}

	private static String readRuntimeSetting(String propertyName, String envName) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(envName);
		}
		return value == null ? "" : value.trim();
	}

	enum Mode {
		WINDOWED("windowed", "@gre@Windowed", "Windowed"),
		FULLSCREEN("fullscreen", "@cya@Fullscreen", "Fullscreen"),
		BORDERLESS_FULLSCREEN("borderless-fullscreen", "@yel@Borderless", "Borderless");

		final String id;
		final String label;
		final String displayName;

		Mode(String id, String label, String displayName) {
			this.id = id;
			this.label = label;
			this.displayName = displayName;
		}

		Mode next() {
			Mode[] modes = values();
			return modes[(ordinal() + 1) % modes.length];
		}

		static Mode from(String value) {
			if (value == null || value.trim().isEmpty()) {
				return BORDERLESS_FULLSCREEN;
			}

			String normalized = value.trim().toLowerCase().replace('_', '-');
			if ("borderless".equals(normalized)) {
				return BORDERLESS_FULLSCREEN;
			}
			for (Mode mode : values()) {
				if (mode.id.equals(normalized)) {
					return mode;
				}
			}

			System.out.println("[renderer-v2 opengl] Unknown OpenGL window mode '" + value + "'; using windowed.");
			return WINDOWED;
		}
	}
}
