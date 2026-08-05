package com.openrsc.server.combat;

import com.openrsc.server.runtime.GameRandom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;

final class SeededGameRandom implements GameRandom {
	private long seed;
	private Random fallback;
	private final Deque<Integer> scriptedInts = new ArrayDeque<Integer>();
	private final Deque<Double> scriptedDoubles = new ArrayDeque<Double>();
	private final List<String> draws = new ArrayList<String>();

	SeededGameRandom(final long seed) {
		reset(seed);
	}

	void reset(final long seed) {
		this.seed = seed;
		this.fallback = new Random(seed);
		scriptedInts.clear();
		scriptedDoubles.clear();
		draws.clear();
	}

	SeededGameRandom scriptInts(final Integer... values) {
		scriptedInts.addAll(Arrays.asList(values));
		return this;
	}

	SeededGameRandom scriptDoubles(final Double... values) {
		scriptedDoubles.addAll(Arrays.asList(values));
		return this;
	}

	@Override
	public int nextInt(final int bound) {
		if (bound <= 0) {
			throw new IllegalArgumentException("bound must be positive");
		}
		final int supplied = scriptedInts.isEmpty()
			? fallback.nextInt(bound)
			: scriptedInts.removeFirst().intValue();
		final int result = Math.floorMod(supplied, bound);
		draws.add("int(" + bound + ")=" + result);
		return result;
	}

	@Override
	public double nextDouble() {
		final double result = scriptedDoubles.isEmpty()
			? fallback.nextDouble()
			: scriptedDoubles.removeFirst().doubleValue();
		if (result < 0.0D || result >= 1.0D) {
			throw new IllegalArgumentException(
				"Scripted double must be in [0, 1): " + result);
		}
		draws.add("double=" + result);
		return result;
	}

	@Override
	public String describeState() {
		return "seed=" + seed + " draws=" + draws;
	}
}
