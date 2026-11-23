package orsc;

import java.awt.Dimension;
import java.util.Properties;

final class RenderSurfaceSettings {
	static final String SURFACE_MODE_PROPERTY_KEY = "render_surface_mode";
	private static final String SURFACE_MODE_PROPERTY = "spoiledmilk.renderSurfaceMode";
	private static final String SURFACE_MODE_ENV = "SPOILED_MILK_RENDER_SURFACE_MODE";
	private static final int FULL_HD_HEIGHT = 1080;
	private static final boolean NATIVE_HD_INTERFACE_PREVIEW =
		Boolean.getBoolean("spoiledmilk.nativeHdInterface");

	private static final boolean runtimeSurfaceModeOverride =
		hasRuntimeSetting(SURFACE_MODE_PROPERTY, SURFACE_MODE_ENV);
	private static volatile Mode mode = Mode.from(readRuntimeSetting(SURFACE_MODE_PROPERTY, SURFACE_MODE_ENV));

	private RenderSurfaceSettings() {
	}

	static Mode getMode() {
		return mode;
	}

	static int getWidth() {
		return mode.width;
	}

	static int getHeight() {
		return mode.height;
	}

	static Dimension getDimensions() {
		return new Dimension(mode.width, mode.height);
	}

	static String getAspectLabel() {
		return mode.aspectLabel;
	}

	static String getDebugAspectLabel() {
		return mode.width * 9 == mode.height * 16 ? "16:9" : "4:3";
	}

	static boolean usesNativeHdInterface(int height) {
		return NATIVE_HD_INTERFACE_PREVIEW && height == FULL_HD_HEIGHT;
	}

	static boolean isInterfacePreviewEnabled() {
		return NATIVE_HD_INTERFACE_PREVIEW;
	}

	static Mode cycleMode() {
		Mode next = mode.next();
		mode = next;
		return next;
	}

	static Mode setMode(Mode next) {
		mode = next == null ? Mode.WIDE : next.normalizedPlayerMode();
		return mode;
	}

	static void loadFromClientSettings(Properties props) {
		if (runtimeSurfaceModeOverride || props == null) {
			return;
		}
		String configuredMode = props.getProperty(SURFACE_MODE_PROPERTY_KEY);
		if (configuredMode != null && !configuredMode.trim().isEmpty()) {
			mode = Mode.from(configuredMode);
		}
	}

	static void saveToClientSettings(Properties props) {
		if (props != null) {
			props.setProperty(SURFACE_MODE_PROPERTY_KEY, mode.id);
		}
	}

	private static boolean hasRuntimeSetting(String propertyName, String envName) {
		return !readRuntimeSetting(propertyName, envName).isEmpty();
	}

	private static String readRuntimeSetting(String propertyName, String envName) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(envName);
		}
		return value == null ? "" : value.trim();
	}

	/**
	 * CLASSIC, VGA, WIDE_PLUS, HD, and FULL_HD remain parseable for older settings.
	 * FULL_HD is also selectable in the high-resolution interface preview.
	 */
	enum Mode {
		CLASSIC("512x346", "@gre@4:3", 512, 346, false, "@gre@4:3"),
		VGA("640x480", "@gre@4:3", 640, 480, false, "@gre@4:3"),
		SVGA("800x600", "@gre@4:3", 800, 600, true, "@gre@4:3"),
		WIDE("960x540", "@yel@16:9", 960, 540, true, "@yel@16:9"),
		WIDE_PLUS("1024x576", "@yel@16:9", 1024, 576, false, "@yel@16:9"),
		HD("1280x720", "@yel@16:9", 1280, 720, false, "@yel@16:9"),
		FULL_HD("1920x1080", "@yel@1080p", 1920, FULL_HD_HEIGHT, false, "@yel@1080p");

		final String id;
		final String label;
		final int width;
		final int height;
		final String aspectLabel;
		private final boolean playerVisible;

		Mode(String id, String label, int width, int height, boolean playerVisible, String aspectLabel) {
			this.id = id;
			this.label = label;
			this.width = width;
			this.height = height;
			this.playerVisible = playerVisible;
			this.aspectLabel = aspectLabel;
		}

		Mode next() {
			Mode current = normalizedPlayerMode();
			Mode[] modes = values();
			for (int step = 1; step <= modes.length; step++) {
				Mode candidate = modes[(current.ordinal() + step) % modes.length];
				if (candidate.playerVisible || candidate == FULL_HD && NATIVE_HD_INTERFACE_PREVIEW) {
					return candidate;
				}
			}
			return WIDE;
		}

		Mode normalizedPlayerMode() {
			if (this == FULL_HD && NATIVE_HD_INTERFACE_PREVIEW) {
				return FULL_HD;
			}
			return isWideAspect() ? WIDE : SVGA;
		}

		private boolean isWideAspect() {
			return width * 9 == height * 16;
		}

		static Mode from(String value) {
			if (value == null || value.trim().isEmpty()) {
				return WIDE;
			}

			String normalized = value.trim().toLowerCase().replace('_', '-');
			if ("4:3".equals(normalized) || "4-3".equals(normalized) || "classic".equals(normalized)
				|| "512-346".equals(normalized) || "640x480".equals(normalized) || "640-480".equals(normalized)
				|| "800x600".equals(normalized) || "800-600".equals(normalized)) {
				return SVGA;
			}
			if ("16:9".equals(normalized) || "16-9".equals(normalized) || "wide".equals(normalized)
				|| "960x540".equals(normalized) || "960-540".equals(normalized)
				|| "1024x576".equals(normalized) || "1024-576".equals(normalized)
				|| "720p".equals(normalized) || "hd".equals(normalized) || "1280x720".equals(normalized)
				|| "1280-720".equals(normalized)) {
				return WIDE;
			}
			if ("1080p".equals(normalized) || "full-hd".equals(normalized) || "fhd".equals(normalized)
				|| "1920x1080".equals(normalized) || "1920-1080".equals(normalized)) {
				return FULL_HD.normalizedPlayerMode();
			}

			for (Mode mode : values()) {
				if (mode.id.equals(normalized)) {
					return mode.normalizedPlayerMode();
				}
			}

			System.out.println("[renderer-v2] Unknown render surface mode '" + value + "'; using 16:9.");
			return WIDE;
		}
	}
}
