package com.openrsc.server.runtime;

/** Production clock adapter. */
public final class SystemGameClock implements GameClock {
	public static final SystemGameClock INSTANCE = new SystemGameClock();

	private SystemGameClock() { }

	@Override
	public long currentTimeMillis() {
		return System.currentTimeMillis();
	}

	@Override
	public long nanoTime() {
		return System.nanoTime();
	}
}
