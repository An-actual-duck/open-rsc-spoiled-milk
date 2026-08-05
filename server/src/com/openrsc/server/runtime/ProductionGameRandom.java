package com.openrsc.server.runtime;

import com.openrsc.server.util.rsc.DataConversions;

/**
 * Production adapter over the legacy random generator. Keeping the adapter
 * here preserves the current distribution and existing non-combat callers.
 */
public final class ProductionGameRandom implements GameRandom {
	public static final ProductionGameRandom INSTANCE = new ProductionGameRandom();

	private ProductionGameRandom() { }

	@Override
	public int nextInt(final int bound) {
		return DataConversions.getRandom().nextInt(bound);
	}

	@Override
	public double nextDouble() {
		return DataConversions.getRandom().nextDouble();
	}

	@Override
	public String describeState() {
		return "production-random";
	}
}
