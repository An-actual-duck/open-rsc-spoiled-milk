package com.openrsc.server.content;

/**
 * Exact fixed-point arithmetic for Devotion balances.
 *
 * <p>The compatible player cache stores whole offering units. Cleric sigils
 * cost half of one offering, so an exact balance is the legacy value multiplied
 * by two plus a signed remainder in {@code [-1, 1]}. One displayed Devotion
 * level is twenty half-offering units.</p>
 */
public final class DevotionHalfOfferingBalance {
	public static final int HALF_UNITS_PER_OFFERING = 2;
	public static final int HALF_UNITS_PER_DEVOTION_LEVEL = 20;
	public static final int MAX_DEVOTION_LEVEL = 1000;
	public static final int MIN_DEVOTION_LEVEL = -1000;
	public static final int MAX_HALF_UNITS = MAX_DEVOTION_LEVEL * HALF_UNITS_PER_DEVOTION_LEVEL;
	public static final int MIN_HALF_UNITS = MIN_DEVOTION_LEVEL * HALF_UNITS_PER_DEVOTION_LEVEL;

	private DevotionHalfOfferingBalance() {
	}

	public static int fromStoredParts(final int wholeOfferings, final int halfOfferingRemainder) {
		if (halfOfferingRemainder < -1 || halfOfferingRemainder > 1) {
			throw new IllegalArgumentException(
				"Half-offering remainder must be -1, 0, or 1: " + halfOfferingRemainder);
		}
		return clampHalfUnits((long) wholeOfferings * HALF_UNITS_PER_OFFERING
			+ halfOfferingRemainder);
	}

	public static int getWholeOfferings(final int exactHalfUnits) {
		return clampHalfUnits(exactHalfUnits) / HALF_UNITS_PER_OFFERING;
	}

	public static int getHalfOfferingRemainder(final int exactHalfUnits) {
		return clampHalfUnits(exactHalfUnits) % HALF_UNITS_PER_OFFERING;
	}

	public static int getDisplayedLevel(final int exactHalfUnits) {
		return clampHalfUnits(exactHalfUnits) / HALF_UNITS_PER_DEVOTION_LEVEL;
	}

	public static int adjust(final int exactHalfUnits, final long deltaHalfUnits) {
		return clampHalfUnits((long) clampHalfUnits(exactHalfUnits) + deltaHalfUnits);
	}

	public static boolean canSpendAboveMinimum(final int exactHalfUnits, final int costHalfUnits) {
		if (costHalfUnits <= 0) {
			return false;
		}
		final int current = clampHalfUnits(exactHalfUnits);
		return current > MIN_HALF_UNITS && (long) current - costHalfUnits >= MIN_HALF_UNITS;
	}

	public static String format(final int exactHalfUnits) {
		final int clamped = clampHalfUnits(exactHalfUnits);
		final long absolute = Math.abs((long) clamped);
		final long whole = absolute / HALF_UNITS_PER_DEVOTION_LEVEL;
		final int fractionalHundredths = (int) (absolute % HALF_UNITS_PER_DEVOTION_LEVEL) * 5;
		if (fractionalHundredths == 0) {
			return (clamped < 0 ? "-" : "") + whole;
		}
		return (clamped < 0 ? "-" : "") + whole + "."
			+ (fractionalHundredths < 10 ? "0" : "") + fractionalHundredths;
	}

	public static int clampHalfUnits(final long halfUnits) {
		return (int) Math.max(MIN_HALF_UNITS, Math.min(MAX_HALF_UNITS, halfUnits));
	}
}
