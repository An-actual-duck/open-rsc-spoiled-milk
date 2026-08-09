package com.openrsc.server.content;

/**
 * Selects the highest-tier eligible fishing outcome for the Angler's Bangle.
 *
 * <p>The bangle is a direct chance roll, not a weight adjustment: when its
 * roll succeeds, one of the tied highest-tier fish is selected; otherwise the
 * caller performs its normal catch roll.</p>
 */
public final class FishingBestCatchSelector {
	private FishingBestCatchSelector() {
	}

	public static boolean shouldSelectBestCatch(final int chancePercent, final int rollPercent) {
		if (rollPercent < 1 || rollPercent > 100) {
			throw new IllegalArgumentException("Best-catch roll must be between 1 and 100");
		}
		return rollPercent <= Math.max(0, Math.min(100, chancePercent));
	}

	public static int countHighestTierEntries(final int[] tiers) {
		final int highestTier = findHighestTier(tiers);
		int count = 0;
		for (int tier : tiers) {
			if (tier == highestTier) {
				count++;
			}
		}
		return count;
	}

	public static int selectHighestTierIndex(final int[] tiers, final int highestTierEntryIndex) {
		final int highestTierCount = countHighestTierEntries(tiers);
		if (highestTierEntryIndex < 0 || highestTierEntryIndex >= highestTierCount) {
			throw new IllegalArgumentException("Highest-tier entry index is out of range");
		}
		final int highestTier = findHighestTier(tiers);
		int count = 0;
		for (int index = 0; index < tiers.length; index++) {
			if (tiers[index] == highestTier) {
				if (count == highestTierEntryIndex) {
					return index;
				}
				count++;
			}
		}
		throw new IllegalStateException("Highest-tier entry selection was not found");
	}

	private static int findHighestTier(final int[] tiers) {
		if (tiers == null || tiers.length == 0) {
			throw new IllegalArgumentException("Eligible fish tiers must not be empty");
		}
		int highestTier = tiers[0];
		for (int tier : tiers) {
			highestTier = Math.max(highestTier, tier);
		}
		return highestTier;
	}
}
