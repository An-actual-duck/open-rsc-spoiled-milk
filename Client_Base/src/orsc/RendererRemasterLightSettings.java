package orsc;

final class RendererRemasterLightSettings {
	private static final String AZIMUTH_PROPERTY = "spoiledmilk.remasterLightAzimuth";
	private static final String AZIMUTH_ENV = "SPOILED_MILK_REMASTER_LIGHT_AZIMUTH";
	private static final String ELEVATION_PROPERTY = "spoiledmilk.remasterLightElevation";
	private static final String ELEVATION_ENV = "SPOILED_MILK_REMASTER_LIGHT_ELEVATION";
	private static final String AMBIENT_PROPERTY = "spoiledmilk.remasterLightAmbient";
	private static final String AMBIENT_ENV = "SPOILED_MILK_REMASTER_LIGHT_AMBIENT";
	private static final String INTENSITY_PROPERTY = "spoiledmilk.remasterLightIntensity";
	private static final String INTENSITY_ENV = "SPOILED_MILK_REMASTER_LIGHT_INTENSITY";
	private static final String SHADOW_SMOOTHING_MILLIS_PROPERTY = "spoiledmilk.remasterShadowLightSmoothingMillis";
	private static final String SHADOW_SMOOTHING_MILLIS_ENV = "SPOILED_MILK_REMASTER_SHADOW_LIGHT_SMOOTHING_MILLIS";
	private static final float MIN_CYCLE_ELEVATION_DEGREES = 10.0f;
	private static final float MAX_CYCLE_ELEVATION_DEGREES = 72.0f;
	private static final float SHADOW_SMOOTHING_MILLIS = readFloat(
		SHADOW_SMOOTHING_MILLIS_PROPERTY,
		SHADOW_SMOOTHING_MILLIS_ENV,
		450.0f,
		0.0f,
		5000.0f);

	private static volatile boolean azimuthOverride = hasRuntimeSetting(AZIMUTH_PROPERTY, AZIMUTH_ENV);
	private static volatile boolean elevationOverride = hasRuntimeSetting(ELEVATION_PROPERTY, ELEVATION_ENV);
	private static volatile float azimuthDegrees = normalizeDegrees(
		readFloat(AZIMUTH_PROPERTY, AZIMUTH_ENV, 135.0f));
	private static volatile float elevationDegrees = clamp(
		readFloat(ELEVATION_PROPERTY, ELEVATION_ENV, 45.0f),
		5.0f,
		85.0f);
	private static volatile float ambient = clamp(
		readFloat(AMBIENT_PROPERTY, AMBIENT_ENV, 0.46f),
		0.0f,
		1.0f);
	private static volatile float intensity = clamp(
		readFloat(INTENSITY_PROPERTY, INTENSITY_ENV, 0.78f),
		0.0f,
		2.0f);
	private static float smoothedShadowAzimuthDegrees;
	private static float smoothedShadowElevationDegrees;
	private static long lastShadowSmoothNanos;
	private static boolean shadowSmoothKnown;

	private RendererRemasterLightSettings() {
	}

	static float getAzimuthDegrees() {
        if (RendererFrameLighting.isUnderground()) return 135.0f;
		if (azimuthOverride) {
			return azimuthDegrees;
		}
		return dayNightAzimuthDegrees();
	}

	static float getElevationDegrees() {
        if (RendererFrameLighting.isUnderground()) return 45.0f;
		if (elevationOverride) {
			return elevationDegrees;
		}
		return dayNightElevationDegrees();
	}

	static float getAmbient() {
		return ambient;
	}

	static float getIntensity() {
		return intensity;
	}

	static float adjustAzimuth(float deltaDegrees) {
		azimuthOverride = true;
		azimuthDegrees = normalizeDegrees(azimuthDegrees + deltaDegrees);
		return azimuthDegrees;
	}

	static float adjustElevation(float deltaDegrees) {
		elevationOverride = true;
		elevationDegrees = clamp(elevationDegrees + deltaDegrees, 5.0f, 85.0f);
		return elevationDegrees;
	}

	static float getLightDirectionX() {
		double azimuth = Math.toRadians(getAzimuthDegrees());
		double elevation = Math.toRadians(getElevationDegrees());
		return (float) (Math.cos(elevation) * Math.cos(azimuth));
	}

	static float getLightDirectionY() {
		return (float) Math.sin(Math.toRadians(getElevationDegrees()));
	}

	static float getLightDirectionZ() {
		double azimuth = Math.toRadians(getAzimuthDegrees());
		double elevation = Math.toRadians(getElevationDegrees());
		return (float) (Math.cos(elevation) * Math.sin(azimuth));
	}

	static synchronized LightAngles getShadowMaskLightAngles() {
		float targetAzimuthDegrees = getAzimuthDegrees();
		float targetElevationDegrees = getElevationDegrees();
		long nowNanos = System.nanoTime();
		if (!shadowSmoothKnown || SHADOW_SMOOTHING_MILLIS <= 0.0f) {
			smoothedShadowAzimuthDegrees = targetAzimuthDegrees;
			smoothedShadowElevationDegrees = targetElevationDegrees;
			lastShadowSmoothNanos = nowNanos;
			shadowSmoothKnown = true;
			return new LightAngles(smoothedShadowAzimuthDegrees, smoothedShadowElevationDegrees);
		}
		float elapsedMillis = Math.max(0.0f, (nowNanos - lastShadowSmoothNanos) / 1000000.0f);
		lastShadowSmoothNanos = nowNanos;
		float alpha = 1.0f - (float) Math.exp(-elapsedMillis / Math.max(1.0f, SHADOW_SMOOTHING_MILLIS));
		smoothedShadowAzimuthDegrees = normalizeDegrees(
			smoothedShadowAzimuthDegrees
				+ angleDeltaDegrees(targetAzimuthDegrees, smoothedShadowAzimuthDegrees) * alpha);
		smoothedShadowElevationDegrees = clamp(
			smoothedShadowElevationDegrees
				+ (targetElevationDegrees - smoothedShadowElevationDegrees) * alpha,
			5.0f,
			85.0f);
		return new LightAngles(smoothedShadowAzimuthDegrees, smoothedShadowElevationDegrees);
	}

	static String debugSummary() {
		return (azimuthOverride || elevationOverride ? "manual " : "cycle ")
			+ "az " + Math.round(getAzimuthDegrees())
			+ " elev " + Math.round(getElevationDegrees())
			+ " amb " + roundedPercent(getAmbient())
			+ " int " + roundedPercent(getIntensity());
	}

	private static int roundedPercent(float value) {
		return Math.round(value * 100.0f);
	}

	private static float normalizeDegrees(float degrees) {
		float normalized = degrees % 360.0f;
		return normalized < 0.0f ? normalized + 360.0f : normalized;
	}

	private static float angleDeltaDegrees(float targetDegrees, float currentDegrees) {
		return normalizeDegrees(targetDegrees - currentDegrees + 180.0f) - 180.0f;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float dayNightAzimuthDegrees() {
		return normalizeDegrees(180.0f - 360.0f * RendererDayNightCycle.currentCycleFraction());
	}

	private static float dayNightElevationDegrees() {
		double daylightArc = Math.sin(RendererDayNightCycle.currentCycleFraction() * Math.PI * 2.0);
		float dayHeight = (float) Math.max(0.0, daylightArc);
		return MIN_CYCLE_ELEVATION_DEGREES
			+ (MAX_CYCLE_ELEVATION_DEGREES - MIN_CYCLE_ELEVATION_DEGREES) * dayHeight;
	}

	private static boolean hasRuntimeSetting(String propertyName, String envName) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(envName);
		}
		return value != null && !value.trim().isEmpty();
	}

	private static float readFloat(String propertyName, String envName, float defaultValue) {
		return readFloat(propertyName, envName, defaultValue, -Float.MAX_VALUE, Float.MAX_VALUE);
	}

	private static float readFloat(
		String propertyName,
		String envName,
		float defaultValue,
		float min,
		float max) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(envName);
		}
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			return clamp(Float.parseFloat(value.trim()), min, max);
		} catch (NumberFormatException ignored) {
			return defaultValue;
		}
	}

	static final class LightAngles {
		final float azimuthDegrees;
		final float elevationDegrees;

		LightAngles(float azimuthDegrees, float elevationDegrees) {
			this.azimuthDegrees = azimuthDegrees;
			this.elevationDegrees = elevationDegrees;
		}
	}
}
