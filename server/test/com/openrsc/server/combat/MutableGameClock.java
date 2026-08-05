package com.openrsc.server.combat;

import com.openrsc.server.runtime.GameClock;

final class MutableGameClock implements GameClock {
	private long millis;
	private long nanos;

	MutableGameClock(final long initialMillis) {
		reset(initialMillis);
	}

	void reset(final long initialMillis) {
		this.millis = initialMillis;
		this.nanos = Math.multiplyExact(initialMillis, 1_000_000L);
	}

	@Override
	public long currentTimeMillis() {
		return millis;
	}

	@Override
	public long nanoTime() {
		return nanos;
	}

	void advanceMillis(final long delta) {
		if (delta < 0L) {
			throw new IllegalArgumentException("Clock cannot move backwards");
		}
		millis = Math.addExact(millis, delta);
		nanos = Math.addExact(nanos, Math.multiplyExact(delta, 1_000_000L));
	}
}
