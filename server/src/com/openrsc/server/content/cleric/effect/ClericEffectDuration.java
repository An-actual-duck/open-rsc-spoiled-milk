package com.openrsc.server.content.cleric.effect;

/** Immutable authored duration, including game-tick-relative Mend cadence. */
public final class ClericEffectDuration {
	public enum Unit {
		MILLISECONDS,
		GAME_TICKS
	}

	private static final long NANOS_PER_MILLISECOND = 1_000_000L;

	private final long amount;
	private final Unit unit;

	private ClericEffectDuration(long amount, Unit unit) {
		if (amount <= 0L || unit == null) {
			throw new IllegalArgumentException("Cleric effect duration must be positive and typed");
		}
		this.amount = amount;
		this.unit = unit;
	}

	public static ClericEffectDuration milliseconds(long milliseconds) {
		return new ClericEffectDuration(milliseconds, Unit.MILLISECONDS);
	}

	public static ClericEffectDuration seconds(long seconds) {
		return milliseconds(Math.multiplyExact(seconds, 1_000L));
	}

	public static ClericEffectDuration minutes(long minutes) {
		return seconds(Math.multiplyExact(minutes, 60L));
	}

	public static ClericEffectDuration gameTicks(long gameTicks) {
		return new ClericEffectDuration(gameTicks, Unit.GAME_TICKS);
	}

	public long getAmount() {
		return amount;
	}

	public Unit getUnit() {
		return unit;
	}

	public long toNanos(long gameTickMilliseconds) {
		if (gameTickMilliseconds <= 0L) {
			throw new IllegalArgumentException("Game tick duration must be positive");
		}
		long milliseconds = unit == Unit.GAME_TICKS
			? Math.multiplyExact(amount, gameTickMilliseconds)
			: amount;
		return Math.multiplyExact(milliseconds, NANOS_PER_MILLISECOND);
	}
}
