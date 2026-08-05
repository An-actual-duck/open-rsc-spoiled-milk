package com.openrsc.server.content;

/**
 * Exact additive Devotion gain for one successful offering.
 *
 * <p>Each flag represents a separate bonus applied to the ordinary one-offering
 * base. The half-offering unit keeps both Black Unicorn bonuses exact without
 * per-player toggles or floating-point balances.</p>
 */
public final class DevotionOfferingGain {
	private static final int BASE_HALF_UNITS = DevotionHalfOfferingBalance.HALF_UNITS_PER_OFFERING;
	private static final int BLESSED_SYMBOL_BONUS_HALF_UNITS = DevotionHalfOfferingBalance.HALF_UNITS_PER_OFFERING;
	private static final int BLACK_UNICORN_BONUS_HALF_UNITS = 1;

	private DevotionOfferingGain() {
	}

	public static int getHalfOfferingUnits(final boolean blessedSymbol,
			final boolean blackUnicornSummon, final boolean blackUnicornSet) {
		int halfOfferingUnits = BASE_HALF_UNITS;
		if (blessedSymbol) {
			halfOfferingUnits += BLESSED_SYMBOL_BONUS_HALF_UNITS;
		}
		if (blackUnicornSummon) {
			halfOfferingUnits += BLACK_UNICORN_BONUS_HALF_UNITS;
		}
		if (blackUnicornSet) {
			halfOfferingUnits += BLACK_UNICORN_BONUS_HALF_UNITS;
		}
		return halfOfferingUnits;
	}
}
