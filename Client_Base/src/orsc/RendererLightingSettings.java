package orsc;

import java.util.Properties;

final class RendererLightingSettings {
	static final String LIGHTING_PROPERTY_KEY = "opengl_lighting";
	private static final String LIGHTING_PROPERTY = "spoiledmilk.openglLighting";
	private static final String LIGHTING_ENV = "SPOILED_MILK_OPENGL_LIGHTING";

	private static final boolean runtimeLightingOverride =
		hasRuntimeSetting(LIGHTING_PROPERTY, LIGHTING_ENV);
	private static volatile Mode mode = Mode.from(readRuntimeSetting(LIGHTING_PROPERTY, LIGHTING_ENV));
	private static boolean loggedRuntimeOverride;

	private RendererLightingSettings() {
	}

	static Mode getMode() {
		return BrowserPublicGraphicsPreset.isEnabled() ? Mode.DIRECTIONAL : mode;
	}

	static Mode cycleMode() {
		if (BrowserPublicGraphicsPreset.isEnabled()) return getMode();
		Mode next = mode.next();
		mode = next;
		return next;
	}

	static Mode setMode(Mode next) {
		if (BrowserPublicGraphicsPreset.isEnabled()) return getMode();
		mode = next == null ? Mode.CLASSIC : next;
		return mode;
	}

	static void loadFromClientSettings(Properties props) {
		if (runtimeLightingOverride) {
			logRuntimeOverride();
			return;
		}
		if (props == null) {
			return;
		}
		String configuredMode = props.getProperty(LIGHTING_PROPERTY_KEY);
		if (configuredMode != null && !configuredMode.trim().isEmpty()) {
			mode = Mode.from(configuredMode);
		} else if (RendererProfileSettings.getMode() == RendererProfileSettings.Mode.CLASSIC) {
			mode = Mode.CLASSIC;
		} else {
			mode = Mode.DIRECTIONAL;
		}
	}

	static void saveToClientSettings(Properties props) {
		if (BrowserPublicGraphicsPreset.isEnabled()) return;
		if (props != null) {
			props.setProperty(LIGHTING_PROPERTY_KEY, mode.id);
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

	private static void logRuntimeOverride() {
		if (loggedRuntimeOverride) {
			return;
		}
		loggedRuntimeOverride = true;
		System.out.println("[renderer-v2] OpenGL lighting runtime override: " + mode.id);
	}

	enum Mode {
		CLASSIC("classic", "@gre@Classic"),
		DIRECTIONAL("directional", "@yel@Directional");

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
				return DIRECTIONAL;
			}

			String normalized = value.trim().toLowerCase().replace('_', '-');
			for (Mode mode : values()) {
				if (mode.id.equals(normalized)) {
					return mode;
				}
			}

			System.out.println("[renderer-v2] Unknown OpenGL lighting '" + value + "'; using classic.");
			return CLASSIC;
		}
	}
}
