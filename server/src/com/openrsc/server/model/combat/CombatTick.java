package com.openrsc.server.model.combat;

import java.io.Serializable;

/**
 * A server combat-clock tick. This type deliberately cannot be confused with
 * epoch milliseconds or scheduler delays expressed in another unit.
 */
public final class CombatTick implements Comparable<CombatTick>, Serializable {
	private static final long serialVersionUID = 1L;
	private static final CombatTick UNSET = new CombatTick(Long.MIN_VALUE);

	private final long value;

	private CombatTick(final long value) {
		this.value = value;
	}

	public static CombatTick of(final long value) {
		if (value < 0L) {
			throw new IllegalArgumentException("combat tick must be non-negative");
		}
		return new CombatTick(value);
	}

	public static CombatTick unset() {
		return UNSET;
	}

	public boolean isSet() {
		return value != Long.MIN_VALUE;
	}

	public long value() {
		if (!isSet()) {
			throw new IllegalStateException("unset combat tick has no numeric value");
		}
		return value;
	}

	public CombatTick plus(final long ticks) {
		if (!isSet()) {
			throw new IllegalStateException("cannot add to an unset combat tick");
		}
		if (ticks < 0L) {
			throw new IllegalArgumentException("tick delta must be non-negative");
		}
		return new CombatTick(Math.addExact(value, ticks));
	}

	public long elapsedSince(final CombatTick earlier) {
		if (earlier == null || !earlier.isSet() || !isSet()) {
			throw new IllegalArgumentException("elapsed ticks require two set values");
		}
		return value - earlier.value;
	}

	@Override
	public int compareTo(final CombatTick other) {
		if (other == null) {
			throw new NullPointerException("other");
		}
		return Long.compare(value, other.value);
	}

	@Override
	public boolean equals(final Object other) {
		return other instanceof CombatTick
			&& value == ((CombatTick) other).value;
	}

	@Override
	public int hashCode() {
		return (int) (value ^ (value >>> 32));
	}

	@Override
	public String toString() {
		return isSet() ? Long.toString(value) : "unset";
	}
}
