package com.openrsc.server.content.cleric.effect;

/** Injectable monotonic time and configured game-tick duration. */
public interface ClericEffectClock {
	long nanoTime();

	long getGameTickMilliseconds();

	static ClericEffectClock system(final long gameTickMilliseconds) {
		if (gameTickMilliseconds <= 0L) {
			throw new IllegalArgumentException("Game tick duration must be positive");
		}
		return new ClericEffectClock() {
			@Override
			public long nanoTime() {
				return System.nanoTime();
			}

			@Override
			public long getGameTickMilliseconds() {
				return gameTickMilliseconds;
			}
		};
	}
}
