package com.openrsc.server.runtime;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Seeded runtime random source for explicitly enabled replay/benchmark runs.
 * Production gameplay continues to use {@link ProductionGameRandom}.
 */
public final class DeterministicGameRandom implements GameRandom {
	private final long seed;
	private final Random random;
	private final AtomicLong draws = new AtomicLong();

	public DeterministicGameRandom(final long seed) {
		this.seed = seed;
		this.random = new Random(seed);
	}

	@Override
	public int nextInt(final int bound) {
		if (bound <= 0) {
			throw new IllegalArgumentException("bound must be positive");
		}
		draws.incrementAndGet();
		return random.nextInt(bound);
	}

	@Override
	public double nextDouble() {
		draws.incrementAndGet();
		return random.nextDouble();
	}

	public long getSeed() {
		return seed;
	}

	public long getDrawCount() {
		return draws.get();
	}

	@Override
	public String describeState() {
		return "seed=" + seed + ",draws=" + draws.get();
	}
}
