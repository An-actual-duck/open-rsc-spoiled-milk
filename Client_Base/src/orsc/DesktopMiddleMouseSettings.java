package orsc;

import java.util.Properties;

/**
 * Desktop-only middle-mouse camera control preference.
 */
final class DesktopMiddleMouseSettings {
	static final String PROPERTY_KEY = "middle_mouse_mode";
	private static volatile Mode mode = Mode.WITH_TILT;

	private DesktopMiddleMouseSettings() {
	}

	static Mode getMode() {
		return mode;
	}

	static boolean usesTilt() {
		return mode == Mode.WITH_TILT;
	}

	static Mode cycleMode() {
		mode = mode.next();
		return mode;
	}

	static Mode setMode(Mode next) {
		mode = next == null ? Mode.WITH_TILT : next;
		return mode;
	}

	static void loadFromClientSettings(Properties properties) {
		mode = Mode.from(properties == null ? null : properties.getProperty(PROPERTY_KEY));
	}

	static void saveToClientSettings(Properties properties) {
		if (properties != null) {
			properties.setProperty(PROPERTY_KEY, mode.id);
		}
	}

	enum Mode {
		WITH_TILT("with-tilt", "@gre@With tilt"),
		CLASSIC("classic", "@yel@Classic");

		final String id;
		final String label;

		Mode(String id, String label) {
			this.id = id;
			this.label = label;
		}

		Mode next() {
			return this == WITH_TILT ? CLASSIC : WITH_TILT;
		}

		static Mode from(String value) {
			if (value == null) {
				return WITH_TILT;
			}
			String normalized = value.trim().toLowerCase().replace('_', '-');
			return "classic".equals(normalized) ? CLASSIC : WITH_TILT;
		}
	}
}
