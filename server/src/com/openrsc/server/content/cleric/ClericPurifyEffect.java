package com.openrsc.server.content.cleric;

import com.openrsc.server.content.PoisonPowerReduction;

/** Pure rank and poison-power calculations for the instant Purify effect. */
public final class ClericPurifyEffect {
	private static final int[] REDUCTION_BY_RANK = {10, 20, 30, 40};

	private ClericPurifyEffect() {
	}

	public static Plan plan(final int currentPoisonPower, final int effectRank) {
		final int reduction = reductionForRank(effectRank);
		return new Plan(currentPoisonPower, reduction,
			PoisonPowerReduction.remainingPower(currentPoisonPower, reduction));
	}

	public static int reductionForRank(final int effectRank) {
		if (effectRank < 1 || effectRank > REDUCTION_BY_RANK.length) {
			throw new IllegalArgumentException("Unsupported Purify effect rank: " + effectRank);
		}
		return REDUCTION_BY_RANK[effectRank - 1];
	}

	public static final class Plan {
		private final int currentPoisonPower;
		private final int reduction;
		private final int remainingPoisonPower;

		private Plan(final int currentPoisonPower, final int reduction,
				final int remainingPoisonPower) {
			this.currentPoisonPower = currentPoisonPower;
			this.reduction = reduction;
			this.remainingPoisonPower = remainingPoisonPower;
		}

		public boolean isUseful() {
			return currentPoisonPower > 0;
		}

		public int getReduction() {
			return reduction;
		}

		public int getRemainingPoisonPower() {
			return remainingPoisonPower;
		}
	}
}
