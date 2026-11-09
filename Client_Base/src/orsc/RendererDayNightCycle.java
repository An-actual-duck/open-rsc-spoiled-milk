package orsc;

final class RendererDayNightCycle {
	private static final int DEFAULT_CYCLE_MILLIS = 60 * 60 * 1000;
	private static final int MIN_CYCLE_MILLIS = 60 * 1000;
	private static volatile int syncedCycleMillis = DEFAULT_CYCLE_MILLIS;
	private static volatile int syncedCyclePositionMillis = -1;
	private static volatile int syncedRateMultiplier = 1;
	private static volatile long syncedAtClientMillis = 0L;
	private static final SkyColor SKY_DAY = new SkyColor(0.62f, 0.78f, 0.95f);
	private static final SkyColor SKY_DAWN = new SkyColor(1.0f, 0.50f, 0.20f);
	private static final SkyColor SKY_DUSK = new SkyColor(0.90f, 0.55f, 0.26f);
	private static final SkyColor SKY_COOL_NIGHT = new SkyColor(0.12f, 0.18f, 0.38f);
	private static final SkyColor SKY_DEEP_NIGHT = new SkyColor(0.03f, 0.06f, 0.20f);
	private static final SkyColor SKY_FOG_WHITE = new SkyColor(1.0f, 1.0f, 1.0f);
	private static final float SKY_FOG_WHITE_BLEND = 0.35f;

	private RendererDayNightCycle() {
	}

	static void syncServerTime(int cycleMillis, int currentCycleMillis) {
		syncServerTime(cycleMillis, currentCycleMillis, 1);
	}

	static void syncServerTime(int cycleMillis, int currentCycleMillis, int rateMultiplier) {
		if (cycleMillis < MIN_CYCLE_MILLIS) {
			return;
		}
		syncedCycleMillis = cycleMillis;
		syncedCyclePositionMillis = Math.floorMod(currentCycleMillis, cycleMillis);
		syncedRateMultiplier = clampRateMultiplier(rateMultiplier);
		syncedAtClientMillis = System.currentTimeMillis();
	}

	static Presentation currentPresentation() {
        if (RendererFrameLighting.isUnderground()) {
            return Presentation.fromMode(RendererToneSettings.Mode.DAY,
                RendererBrightnessSettings.getMode().multiplier, "underground-neutral");
        }
		RendererToneSettings.Mode toneMode = RendererToneSettings.getMode();
		if (toneMode != RendererToneSettings.Mode.CYCLE) {
			return Presentation.fromMode(
				toneMode,
				RendererBrightnessSettings.getMode().multiplier,
				toneMode.id);
		}

		CyclePosition cycle = currentCyclePosition();
		long cycleMillis = cycle.positionMillis;
		long dawnEndMillis = cycle.cycleMillis * 5L / 60L;
		long dayEndMillis = cycle.cycleMillis * 30L / 60L;
		long duskEndMillis = cycle.cycleMillis * 35L / 60L;
		long nightCoolInEndMillis = cycle.cycleMillis * 375L / 600L;
		long nightDeepEndMillis = cycle.cycleMillis * 575L / 600L;
		float savedBrightness = RendererBrightnessSettings.getMode().multiplier;
		if (cycleMillis < dawnEndMillis) {
			float t = fraction(cycleMillis, 0L, dawnEndMillis);
			return new Presentation(
				applyTransitionDim(morningTone(t), t),
				savedBrightness,
				"dawn",
				morningSky(t));
		}
		if (cycleMillis < dayEndMillis) {
			return Presentation.fromMode(RendererToneSettings.Mode.DAY, savedBrightness, "day");
		}
		if (cycleMillis < duskEndMillis) {
			float t = fraction(cycleMillis, dayEndMillis, duskEndMillis);
			return new Presentation(
				applyTransitionDim(eveningTone(t), t),
				savedBrightness,
				"dusk",
				eveningSky(t));
		}
		if (cycleMillis < nightCoolInEndMillis) {
			float t = fraction(cycleMillis, duskEndMillis, nightCoolInEndMillis);
			return new Presentation(
				mix(
					RendererToneSettings.Mode.COOL_NIGHT,
					RendererToneSettings.Mode.DEEP_BLUE,
					smoothstep(t)),
				savedBrightness,
				"night-cool-deep",
				SkyColor.mix(SKY_COOL_NIGHT, SKY_DEEP_NIGHT, smoothstep(t)));
		}
		if (cycleMillis < nightDeepEndMillis) {
			return Presentation.fromMode(RendererToneSettings.Mode.DEEP_BLUE, savedBrightness, "night-deep");
		}
		return new Presentation(
			mix(
				RendererToneSettings.Mode.DEEP_BLUE,
				RendererToneSettings.Mode.COOL_NIGHT,
				smoothstep(fraction(cycleMillis, nightDeepEndMillis, cycle.cycleMillis))),
			savedBrightness,
			"night-deep-cool",
			SkyColor.mix(
				SKY_DEEP_NIGHT,
				SKY_COOL_NIGHT,
				smoothstep(fraction(cycleMillis, nightDeepEndMillis, cycle.cycleMillis))));
	}

	static float currentBrightnessMultiplier() {
		return currentPresentation().brightnessMultiplier;
	}

	static float currentCycleFraction() {
		CyclePosition cycle = currentCyclePosition();
		return cycle.positionMillis / (float) Math.max(1, cycle.cycleMillis);
	}

	static String debugSummary() {
		Presentation presentation = currentPresentation();
		if (RendererToneSettings.getMode() != RendererToneSettings.Mode.CYCLE) {
			return presentation.debugLabel;
		}
		CyclePosition cycle = currentCyclePosition();
		long minute = cycle.positionMillis / 60000L;
		long second = (cycle.positionMillis / 1000L) % 60L;
		return (cycle.serverSynced ? "server-cycle:" : "local-cycle:")
			+ presentation.debugLabel + " "
			+ twoDigits(minute) + ":" + twoDigits(second)
			+ (cycle.rateMultiplier > 1 ? " x" + cycle.rateMultiplier : "");
	}

	private static CyclePosition currentCyclePosition() {
		int cycleMillis = syncedCycleMillis;
		int cyclePositionMillis = syncedCyclePositionMillis;
		long syncedAtMillis = syncedAtClientMillis;
		int rateMultiplier = syncedRateMultiplier;
		long now = System.currentTimeMillis();
		if (cyclePositionMillis >= 0 && cycleMillis >= MIN_CYCLE_MILLIS) {
			long elapsedMillis = Math.max(0L, now - syncedAtMillis) * (long) clampRateMultiplier(rateMultiplier);
			return new CyclePosition(
				cycleMillis,
				Math.floorMod((long) cyclePositionMillis + elapsedMillis, (long) cycleMillis),
				true,
				rateMultiplier);
		}
		return new CyclePosition(
			DEFAULT_CYCLE_MILLIS,
			Math.floorMod(now, (long) DEFAULT_CYCLE_MILLIS),
			false,
			1);
	}

	private static int clampRateMultiplier(int rateMultiplier) {
		if (rateMultiplier < 1) {
			return 1;
		}
		return Math.min(rateMultiplier, 3600);
	}

	private static String twoDigits(long value) {
		return value < 10L ? "0" + value : Long.toString(value);
	}

	private static RendererToneSettings.ToneValues applyTransitionDim(
		RendererToneSettings.ToneValues toneValues,
		float phaseFraction) {
		float dimFactor = transitionDimFactor(phaseFraction);
		return new RendererToneSettings.ToneValues(
			toneValues.redMultiplier * dimFactor,
			toneValues.greenMultiplier * dimFactor,
			toneValues.blueMultiplier * dimFactor,
			toneValues.blend);
	}

	private static float transitionDimFactor(float phaseFraction) {
		float savedBrightness = RendererBrightnessSettings.getMode().multiplier;
		float dimmedBrightness;
		switch (RendererBrightnessSettings.getMode()) {
			case HIGH:
				dimmedBrightness = RendererBrightnessSettings.Mode.MEDIUM.multiplier;
				break;
			case MEDIUM:
				dimmedBrightness = RendererBrightnessSettings.Mode.LOW.multiplier;
				break;
			case LOW:
			default:
				dimmedBrightness = 0.7f;
				break;
		}
		if (savedBrightness <= 0.0f) {
			return 1.0f;
		}
		float dimAmount;
		if (phaseFraction < 0.2f) {
			dimAmount = smoothstep(phaseFraction / 0.2f);
		} else if (phaseFraction > 0.8f) {
			dimAmount = 1.0f - smoothstep((phaseFraction - 0.8f) / 0.2f);
		} else {
			dimAmount = 1.0f;
		}
		return lerp(1.0f, dimmedBrightness / savedBrightness, dimAmount);
	}

	private static RendererToneSettings.ToneValues morningTone(float t) {
		if (t < 0.2f) {
			return mix(
				RendererToneSettings.Mode.COOL_NIGHT,
				RendererToneSettings.Mode.SUNRISE_AMBER,
				smoothstep(t / 0.2f));
		}
		if (t > 0.8f) {
			return mix(
				RendererToneSettings.Mode.SUNRISE_AMBER,
				RendererToneSettings.Mode.DAY,
				smoothstep((t - 0.8f) / 0.2f));
		}
		return RendererToneSettings.Mode.SUNRISE_AMBER.toneValues();
	}

	private static SkyColor morningSky(float t) {
		if (t < 0.2f) {
			return SkyColor.mix(SKY_COOL_NIGHT, SKY_DAWN, smoothstep(t / 0.2f));
		}
		if (t > 0.8f) {
			return SkyColor.mix(SKY_DAWN, SKY_DAY, smoothstep((t - 0.8f) / 0.2f));
		}
		return SKY_DAWN;
	}

	private static RendererToneSettings.ToneValues eveningTone(float t) {
		if (t < 0.2f) {
			return mix(
				RendererToneSettings.Mode.DAY,
				RendererToneSettings.Mode.ROSE_DUSK,
				smoothstep(t / 0.2f));
		}
		if (t > 0.8f) {
			return mix(
				RendererToneSettings.Mode.ROSE_DUSK,
				RendererToneSettings.Mode.COOL_NIGHT,
				smoothstep((t - 0.8f) / 0.2f));
		}
		return RendererToneSettings.Mode.ROSE_DUSK.toneValues();
	}

	private static SkyColor eveningSky(float t) {
		if (t < 0.2f) {
			return SkyColor.mix(SKY_DAY, SKY_DUSK, smoothstep(t / 0.2f));
		}
		if (t > 0.8f) {
			return SkyColor.mix(SKY_DUSK, SKY_COOL_NIGHT, smoothstep((t - 0.8f) / 0.2f));
		}
		return SKY_DUSK;
	}

	private static RendererToneSettings.ToneValues mix(
		RendererToneSettings.Mode from,
		RendererToneSettings.Mode to,
		float t) {
		return RendererToneSettings.ToneValues.mix(from.toneValues(), to.toneValues(), clamp01(t));
	}

	private static float fraction(long value, long start, long end) {
		return clamp01((value - start) / (float) Math.max(1L, end - start));
	}

	private static float smoothstep(float value) {
		float t = clamp01(value);
		return t * t * (3.0f - 2.0f * t);
	}

	private static float lerp(float from, float to, float t) {
		return from + (to - from) * clamp01(t);
	}

	private static float clamp01(float value) {
		if (value <= 0.0f) {
			return 0.0f;
		}
		if (value >= 1.0f) {
			return 1.0f;
		}
		return value;
	}

	static final class Presentation {
		final float redMultiplier;
		final float greenMultiplier;
		final float blueMultiplier;
		final float toneBlend;
		final float brightnessMultiplier;
		final float skyRed;
		final float skyGreen;
		final float skyBlue;
		final float fogRed;
		final float fogGreen;
		final float fogBlue;
		final String debugLabel;

		Presentation(
			RendererToneSettings.ToneValues toneValues,
			float brightnessMultiplier,
			String debugLabel) {
			this(toneValues, brightnessMultiplier, debugLabel, skyColorForToneMode(RendererToneSettings.Mode.DAY));
		}

		Presentation(
			RendererToneSettings.ToneValues toneValues,
			float brightnessMultiplier,
			String debugLabel,
			SkyColor skyColor) {
			this.redMultiplier = toneValues.redMultiplier;
			this.greenMultiplier = toneValues.greenMultiplier;
			this.blueMultiplier = toneValues.blueMultiplier;
			this.toneBlend = toneValues.blend;
			this.brightnessMultiplier = brightnessMultiplier;
			this.skyRed = skyColor.red;
			this.skyGreen = skyColor.green;
			this.skyBlue = skyColor.blue;
			SkyColor fogColor = SkyColor.mix(skyColor, SKY_FOG_WHITE, SKY_FOG_WHITE_BLEND);
			this.fogRed = fogColor.red;
			this.fogGreen = fogColor.green;
			this.fogBlue = fogColor.blue;
			this.debugLabel = debugLabel;
		}

		static Presentation fromMode(
			RendererToneSettings.Mode mode,
			float brightnessMultiplier,
			String debugLabel) {
			return new Presentation(mode.toneValues(), brightnessMultiplier, debugLabel, skyColorForToneMode(mode));
		}
	}

	private static SkyColor skyColorForToneMode(RendererToneSettings.Mode mode) {
		if (mode == RendererToneSettings.Mode.SUNRISE_AMBER || mode == RendererToneSettings.Mode.DAWN_GOLD) {
			return SKY_DAWN;
		}
		if (mode == RendererToneSettings.Mode.ROSE_DUSK) {
			return SKY_DUSK;
		}
		if (mode == RendererToneSettings.Mode.COOL_NIGHT) {
			return SKY_COOL_NIGHT;
		}
		if (mode == RendererToneSettings.Mode.DEEP_BLUE) {
			return SKY_DEEP_NIGHT;
		}
		return SKY_DAY;
	}

	private static final class SkyColor {
		final float red;
		final float green;
		final float blue;

		SkyColor(float red, float green, float blue) {
			this.red = red;
			this.green = green;
			this.blue = blue;
		}

		static SkyColor mix(SkyColor from, SkyColor to, float t) {
			return new SkyColor(
				lerp(from.red, to.red, t),
				lerp(from.green, to.green, t),
				lerp(from.blue, to.blue, t));
		}
	}

	private static final class CyclePosition {
		final int cycleMillis;
		final long positionMillis;
		final boolean serverSynced;
		final int rateMultiplier;

		CyclePosition(int cycleMillis, long positionMillis, boolean serverSynced, int rateMultiplier) {
			this.cycleMillis = cycleMillis;
			this.positionMillis = positionMillis;
			this.serverSynced = serverSynced;
			this.rateMultiplier = rateMultiplier;
		}
	}
}
