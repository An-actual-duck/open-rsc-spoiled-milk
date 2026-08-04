package com.openrsc.server.event.rsc.impl;

/** Pure interval math for independently owned natural Hits-regeneration factors. */
public final class NaturalHitsRegeneration {
	private NaturalHitsRegeneration() {
}

	/**
	 * Applies one independent speed bonus without changing regeneration-clock
	 * state. A future Respite status uses this same operation, so applying,
	 * refreshing, replacing, or expiring that status cannot itself reset the
	 * clock or award a healing tick.
	 */
	public static long applySpeedBonus(final long currentIntervalMillis,
			final long minimumIntervalMillis, final double speedBonus) {
		if (currentIntervalMillis <= 0L || minimumIntervalMillis <= 0L
			|| Double.isNaN(speedBonus) || Double.isInfinite(speedBonus)
			|| speedBonus < 0.0D) {
			throw new IllegalArgumentException("Invalid natural Hits-regeneration factor");
		}
		return Math.max(minimumIntervalMillis,
			(long) Math.ceil(currentIntervalMillis / (1.0D + speedBonus)));
	}
}
