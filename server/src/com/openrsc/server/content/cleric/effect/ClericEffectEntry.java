package com.openrsc.server.content.cleric.effect;

/** Immutable active effect state; registry updates replace the complete entry. */
public final class ClericEffectEntry {
	private final ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition;
	private final ClericEffectOrigin origin;
	private final long appliedAtNanos;
	private final long expiresAtNanos;
	private final int remainingCounter;

	ClericEffectEntry(ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition,
			ClericEffectOrigin origin, long appliedAtNanos, long expiresAtNanos,
			int remainingCounter) {
		if (definition == null || origin == null || expiresAtNanos <= appliedAtNanos) {
			throw new IllegalArgumentException("Complete bounded Cleric effect state is required");
		}
		if (definition.getCounterKind() == ClericEffectCounterKind.NONE
				&& remainingCounter != 0) {
			throw new IllegalArgumentException("Counter-free Cleric entry cannot carry a counter");
		}
		if (definition.getCounterKind() != ClericEffectCounterKind.NONE
				&& remainingCounter <= 0) {
			throw new IllegalArgumentException("Counted Cleric entry requires a remaining counter");
		}
		this.definition = definition;
		this.origin = origin;
		this.appliedAtNanos = appliedAtNanos;
		this.expiresAtNanos = expiresAtNanos;
		this.remainingCounter = remainingCounter;
	}

	ClericEffectEntry withRemainingCounter(int counter) {
		return new ClericEffectEntry(definition, origin, appliedAtNanos, expiresAtNanos, counter);
	}

	public ClericEffectRankDefinition<? extends ClericEffectMagnitude> getDefinition() {
		return definition;
	}

	public ClericEffectOrigin getOrigin() {
		return origin;
	}

	public long getAppliedAtNanos() {
		return appliedAtNanos;
	}

	public long getExpiresAtNanos() {
		return expiresAtNanos;
	}

	public int getRemainingCounter() {
		return remainingCounter;
	}

	public boolean isExpired(long nowNanos) {
		return nowNanos >= expiresAtNanos;
	}

	public long getRemainingNanos(long nowNanos) {
		return nowNanos >= expiresAtNanos ? 0L : expiresAtNanos - nowNanos;
	}
}
