package com.openrsc.server.runtime;

/** Narrow random source used by combat and NPC behaviour decisions. */
public interface GameRandom {
	int nextInt(int bound);

	double nextDouble();

	default int nextIntInclusive(final int low, final int high) {
		if (high < low) {
			throw new IllegalArgumentException("Random upper bound is below lower bound");
		}
		return low + nextInt(high - low + 1);
	}

	/** Human-readable replay information for deterministic scenario failures. */
	String describeState();
}
