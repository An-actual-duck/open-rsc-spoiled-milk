package orsc;

import java.util.Locale;
import java.util.Properties;

/** Player-facing geometry shadow quality; independent of lighting/materials. */
final class RendererShadowSettings {
	static final String SETTINGS_KEY = "shadow_quality";
	private static final String PROPERTY = "spoiledmilk.shadowQuality";
	private static final String ENV = "SPOILED_MILK_SHADOW_QUALITY";
	private static final String override = runtimeValue();
	private static volatile Quality quality = Quality.from(override);

	private RendererShadowSettings() { }

	static Quality getQuality() { return quality; }
	static Quality cycle() { return quality = Quality.values()[(quality.ordinal() + 1) % Quality.values().length]; }
	static void setQuality(Quality value) { quality = value == null ? Quality.HIGH : value; }
	static void applyProfileDefault(Quality value) { if (override.isEmpty()) setQuality(value); }
	static void loadFromClientSettings(Properties properties) {
		if (override.isEmpty() && properties != null) {
			if (properties.containsKey(SETTINGS_KEY)) {
				quality = Quality.from(properties.getProperty(SETTINGS_KEY));
			} else if ("classic".equals(properties.getProperty(RendererProfileSettings.PROFILE_PROPERTY_KEY))) {
				quality = Quality.OFF;
			}
		}
	}
	static void saveToClientSettings(Properties properties) {
		if (properties != null) properties.setProperty(SETTINGS_KEY, quality.id);
	}
	private static String runtimeValue() {
		String value = System.getProperty(PROPERTY);
		if (value == null || value.trim().isEmpty()) value = System.getenv(ENV);
		return value == null ? "" : value.trim();
	}

	enum Quality {
		OFF("off", "@red@Off", 0, 0, 0),
		LOW("low", "@yel@Low", 1024, 48, 0),
		HIGH("high", "@gre@High", 2048, 32, 1);
		final String id, label;
		final int nearSize, nearRadiusTiles, filterRadius;
		Quality(String id, String label, int size, int radiusTiles, int filterRadius) {
			this.id = id; this.label = label; nearSize = size;
			nearRadiusTiles = radiusTiles; this.filterRadius = filterRadius;
		}
		static Quality from(String value) {
			String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
			for (Quality q : values()) if (q.id.equals(normalized)) return q;
			return HIGH;
		}
	}
}
