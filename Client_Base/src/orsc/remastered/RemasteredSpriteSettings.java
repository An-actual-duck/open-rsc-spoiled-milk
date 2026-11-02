package orsc.remastered;

import java.util.Locale;
import java.util.Properties;

public final class RemasteredSpriteSettings {
	// Owner pause: retain the catalog/art, but do not offer or render these overrides.
	private static final boolean AVAILABLE = false;
	public static final String PROPERTY_KEY = "remastered_sprites";
	private static final String RUNTIME_PROPERTY = "spoiledmilk.remasteredSprites";
	private static final String RUNTIME_ENV = "SPOILED_MILK_REMASTERED_SPRITES";

	private static final String runtimeValue = readRuntimeSetting();
	private static final boolean runtimeOverride = !runtimeValue.isEmpty();
	private static volatile boolean enabled = parse(runtimeValue, false);

	private RemasteredSpriteSettings() {
	}

	public static boolean isAvailable() {
		return AVAILABLE;
	}

	public static boolean isEnabled() {
		return AVAILABLE && enabled;
	}

	public static boolean hasRuntimeOverride() {
		return runtimeOverride;
	}

	public static boolean toggle() {
		return setEnabled(!enabled);
	}

	public static boolean setEnabled(boolean next) {
		if (!runtimeOverride) {
			enabled = next;
		}
		return isEnabled();
	}

	public static boolean applyClassicProfile() {
		return setEnabled(false);
	}

	public static void loadFromClientSettings(Properties properties) {
		if (runtimeOverride || properties == null) {
			return;
		}
		enabled = parse(properties.getProperty(PROPERTY_KEY), false);
	}

	public static void saveToClientSettings(Properties properties) {
		if (properties != null) {
			properties.setProperty(PROPERTY_KEY, Boolean.toString(isEnabled()));
		}

	}

	public static String description() {
		return (isEnabled() ? "enabled" : "disabled")
			+ (AVAILABLE ? "" : " (temporarily unavailable)")
			+ (runtimeOverride ? " (runtime override)" : "")
			+ " catalog=" + RemasteredSpriteCatalog.revision();
	}

	private static boolean parse(String value, boolean fallback) {
		if (value == null || value.trim().isEmpty()) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase(Locale.ENGLISH);
		if ("true".equals(normalized) || "1".equals(normalized)
			|| "on".equals(normalized) || "yes".equals(normalized)) {
			return true;
		}
		if ("false".equals(normalized) || "0".equals(normalized)
			|| "off".equals(normalized) || "no".equals(normalized)) {
			return false;
		}
		System.out.println("[remastered-sprites] Unknown setting '" + value + "'; using " + fallback + ".");
		return fallback;
	}

	private static String readRuntimeSetting() {
		String value = System.getProperty(RUNTIME_PROPERTY);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(RUNTIME_ENV);
		}
		return value == null ? "" : value.trim();
	}
}
