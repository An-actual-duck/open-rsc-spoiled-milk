package orsc;

import java.util.Properties;

final class RendererGeometrySettings {
	static final String GEOMETRY_PROPERTY_KEY = "opengl_geometry";
	private static final String GEOMETRY_PROPERTY = "spoiledmilk.openglGeometry";
	private static final String GEOMETRY_ENV = "SPOILED_MILK_OPENGL_GEOMETRY";

	private static final boolean runtimeGeometryOverride =
		hasRuntimeSetting(GEOMETRY_PROPERTY, GEOMETRY_ENV);
	private static volatile Mode mode = Mode.from(readRuntimeSetting(GEOMETRY_PROPERTY, GEOMETRY_ENV));

	private RendererGeometrySettings() {
	}

	static Mode getMode() {
		return BrowserPublicGraphicsPreset.isEnabled() ? Mode.SMOOTH : mode;
	}

	static Mode cycleMode() {
		if (BrowserPublicGraphicsPreset.isEnabled()) return getMode();
		Mode next = mode.next();
		mode = next;
		return next;
	}

	/** Browser WebGL has filled triangles only; native Wire remains desktop-only. */
	static Mode cycleBrowserMode() {
		if (BrowserPublicGraphicsPreset.isEnabled()) return getMode();
		mode = mode == Mode.SMOOTH ? Mode.FACETED : Mode.SMOOTH;
		return mode;
	}

	static Mode setMode(Mode next) {
		if (BrowserPublicGraphicsPreset.isEnabled()) return getMode();
		mode = next == null ? Mode.SMOOTH : next;
		return mode;
	}

	static void loadFromClientSettings(Properties props) {
		if (runtimeGeometryOverride || props == null) {
			return;
		}
		String configuredMode = props.getProperty(GEOMETRY_PROPERTY_KEY);
		if (configuredMode != null && !configuredMode.trim().isEmpty()) {
			mode = Mode.from(configuredMode);
		}
	}

	static void saveToClientSettings(Properties props) {
		if (BrowserPublicGraphicsPreset.isEnabled()) return;
		if (props != null) {
			props.setProperty(GEOMETRY_PROPERTY_KEY, mode.id);
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

	enum Mode {
		SMOOTH("smooth", "@gre@Smooth"),
		FACETED("faceted", "@yel@Faceted"),
		WIRE("wire", "@cya@Wire");

		final String id;
		final String label;

		Mode(String id, String label) {
			this.id = id;
			this.label = label;
		}

		Mode next() {
			Mode[] modes = values();
			return modes[(ordinal() + 1) % modes.length];
		}

		static Mode from(String value) {
			if (value == null || value.trim().isEmpty()) {
				return SMOOTH;
			}

			String normalized = value.trim().toLowerCase().replace('_', '-');
			if ("geometric".equals(normalized) || "flat".equals(normalized)) {
				return FACETED;
			}
			if ("wireframe".equals(normalized) || "triangle".equals(normalized)) {
				return WIRE;
			}
			for (Mode mode : values()) {
				if (mode.id.equals(normalized)) {
					return mode;
				}
			}

			System.out.println("[renderer-v2] Unknown OpenGL geometry '" + value + "'; using smooth.");
			return SMOOTH;
		}
	}
}
