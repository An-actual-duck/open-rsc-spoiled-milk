package com.openrsc.server.content;

/** Applies an Angler's Bangle bonus to every tied best-tier fishing outcome. */
public final class FishingBestCatchWeights {
	private FishingBestCatchWeights() {
	}

	public static int[] applyBonus(final int[] baseWeights, final int[] tiers, final int bonusPercent) {
		if (baseWeights == null || tiers == null || baseWeights.length != tiers.length) {
			throw new IllegalArgumentException("Fishing weights and tiers must have matching lengths");
		}
		final int[] adjusted = baseWeights.clone();
		if (bonusPercent <= 0 || adjusted.length == 0) {
			return adjusted;
		}
		int bestTier = Integer.MIN_VALUE;
		for (int index = 0; index < adjusted.length; index++) {
			if (adjusted[index] > 0) {
				bestTier = Math.max(bestTier, tiers[index]);
			}
		}
		if (bestTier == Integer.MIN_VALUE) {
			return adjusted;
		}
		for (int index = 0; index < adjusted.length; index++) {
			if (adjusted[index] > 0 && tiers[index] == bestTier) {
				adjusted[index] = (int) Math.min(Integer.MAX_VALUE,
					((long) adjusted[index] * (100L + bonusPercent)) / 100L);
			}
		}
		return adjusted;
	}
}
