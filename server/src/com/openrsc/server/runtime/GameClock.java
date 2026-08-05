package com.openrsc.server.runtime;

/**
 * Time source for gameplay decisions that must be reproducible in an isolated
 * server context.
 */
public interface GameClock {
	long currentTimeMillis();

	long nanoTime();
}
