package com.openrsc.server.content.cleric;

/** Pure, bounded arithmetic for Cleric effects on eligible direct combat hits. */
public final class ClericDirectCombatEffects {
	private ClericDirectCombatEffects() {
	}

	public static double combineUpwardRollChance(double existingChance,
			int clericChancePercent) {
		if (Double.isNaN(existingChance) || existingChance < 0.0D
				|| clericChancePercent < 0 || clericChancePercent > 100) {
			throw new IllegalArgumentException("Invalid direct-roll bias chance");
		}
		return Math.min(1.0D, existingChance + clericChancePercent / 100.0D);
	}

	public static int applyProtection(int damage, int reductionPercent) {
		if (damage < 0 || reductionPercent <= 0 || reductionPercent >= 100) {
			throw new IllegalArgumentException("Invalid Cleric protection input");
		}
		if (damage == 0) {
			return 0;
		}
		long numerator = Math.addExact(
			Math.multiplyExact((long) damage, 100L - reductionPercent), 99L);
		return (int) (numerator / 100L);
	}

	public static int addBounded(int base, int bonus) {
		if (base < 0 || bonus < 0) {
			throw new IllegalArgumentException("Direct damage values cannot be negative");
		}
		return (int) Math.min(Integer.MAX_VALUE, (long) base + bonus);
	}

	public static int stochasticPercentage(int amount, int percent,
			int rollFromZeroToNinetyNine) {
		if (amount < 0 || percent <= 0 || percent > 100
				|| rollFromZeroToNinetyNine < 0 || rollFromZeroToNinetyNine > 99) {
			throw new IllegalArgumentException("Invalid stochastic percentage input");
		}
		long hundredths = Math.multiplyExact((long) amount, (long) percent);
		long whole = hundredths / 100L;
		int remainder = (int) (hundredths % 100L);
		if (rollFromZeroToNinetyNine < remainder) {
			whole++;
		}
		return (int) Math.min(Integer.MAX_VALUE, whole);
	}

	public static boolean isBelowPercent(int current, int ceiling, int percent) {
		if (current < 0 || ceiling <= 0 || percent <= 0 || percent > 100) {
			throw new IllegalArgumentException("Invalid Hits threshold input");
		}
		return (long) current * 100L < (long) ceiling * percent;
	}
}
