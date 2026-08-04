package com.openrsc.server.content;

/** Shared bounded arithmetic and cure threshold for current poison power. */
public final class PoisonPowerReduction {
	public static final int CURE_THRESHOLD = 10;

	private PoisonPowerReduction() {
	}

	public static int remainingPower(final int currentPower, final int reduction) {
		if (currentPower < 0) {
			throw new IllegalArgumentException("Current poison power cannot be negative");
		}
		if (reduction <= 0) {
			throw new IllegalArgumentException("Poison-power reduction must be positive");
		}
		return reduction >= currentPower ? 0 : currentPower - reduction;
	}

	public static boolean shouldCure(final int poisonPower) {
		if (poisonPower < 0) {
			throw new IllegalArgumentException("Poison power cannot be negative");
		}
		return poisonPower < CURE_THRESHOLD;
	}
}
