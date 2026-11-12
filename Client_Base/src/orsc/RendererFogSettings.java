package orsc;

import java.util.Properties;

final class RendererFogSettings {
	static final String FOG_PROPERTY_KEY = "opengl_fog_distance";
	private static final String LEGACY_FOG_PROPERTY_KEY = "opengl_fog_strength";
	private static final String FOG_PROPERTY = "spoiledmilk.openglFogDistance";
	private static final String FOG_ENV = "SPOILED_MILK_OPENGL_FOG_DISTANCE";

	private static final boolean runtimeFogOverride =
		hasRuntimeSetting(FOG_PROPERTY, FOG_ENV);
	private static volatile Mode mode = Mode.from(readRuntimeSetting(FOG_PROPERTY, FOG_ENV));

	private RendererFogSettings() {
	}

	static Mode getMode() {
		return BrowserPublicGraphicsPreset.isEnabled() ? Mode.ON : mode;
	}

	static Mode cycleMode() {
		if (BrowserPublicGraphicsPreset.isEnabled()) return getMode();
		Mode next = mode.next();
		mode = next;
		return next;
	}

	static Mode setMode(Mode next) {
		if (BrowserPublicGraphicsPreset.isEnabled()) return getMode();
		mode = next == null ? Mode.ON : next;
		return mode;
	}

	static void loadFromClientSettings(Properties props) {
		if (runtimeFogOverride || props == null) {
			return;
		}
		String configuredMode = props.getProperty(FOG_PROPERTY_KEY);
		if (configuredMode == null || configuredMode.trim().isEmpty()) {
			configuredMode = props.getProperty(LEGACY_FOG_PROPERTY_KEY);
		}
		if (configuredMode != null && !configuredMode.trim().isEmpty()) {
			mode = Mode.from(configuredMode);
		}
	}

	static void saveToClientSettings(Properties props) {
		if (BrowserPublicGraphicsPreset.isEnabled()) return;
		if (props != null) {
			props.setProperty(FOG_PROPERTY_KEY, mode.id);
			props.remove(LEGACY_FOG_PROPERTY_KEY);
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
		ON("on", "@gre@On", 28, 40, 1.0f),
		OFF("off", "@red@Off", 0, 0, 0.0f);

		final String id;
		final String label;
		final int fogStartTiles;
		final int drawDistanceTiles;
		final float multiplier;

		Mode(String id, String label, int fogStartTiles, int drawDistanceTiles, float multiplier) {
			this.id = id;
			this.label = label;
			this.fogStartTiles = fogStartTiles;
			this.drawDistanceTiles = drawDistanceTiles;
			this.multiplier = multiplier;
		}

		Mode next() {
			Mode[] modes = values();
			return modes[(ordinal() + 1) % modes.length];
		}

		static Mode from(String value) {
			if (value == null || value.trim().isEmpty()) {
				return ON;
			}

			String normalized = value.trim().toLowerCase().replace('_', '-');
			if ("classic".equals(normalized)
				|| "close".equals(normalized)
				|| "far".equals(normalized)
				|| "soft".equals(normalized)
				|| "low".equals(normalized)) {
				return ON;
			}
			if ("true".equals(normalized) || "enabled".equals(normalized)) {
				return ON;
			}
			if ("false".equals(normalized) || "disabled".equals(normalized)) {
				return OFF;
			}
			for (Mode mode : values()) {
				if (mode.id.equals(normalized)) {
					return mode;
				}
			}

			System.out.println("[renderer-v2] Unknown OpenGL fog setting '" + value + "'; using on.");
			return ON;
		}
	}
}
