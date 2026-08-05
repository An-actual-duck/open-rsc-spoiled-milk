package com.openrsc.server.content.cleric.effect;

import com.openrsc.server.runtime.GameClock;
import com.openrsc.server.runtime.SystemGameClock;

/** Injectable monotonic time and configured game-tick duration. */
public interface ClericEffectClock {
	long nanoTime();

	long getGameTickMilliseconds();

	static ClericEffectClock system(final long gameTickMilliseconds) {
		return game(SystemGameClock.INSTANCE, gameTickMilliseconds);
	}

	static ClericEffectClock game(final GameClock clock,
			final long gameTickMilliseconds) {
		if (clock == null) {
			throw new NullPointerException("clock");
		}
		if (gameTickMilliseconds <= 0L) {
			throw new IllegalArgumentException("Game tick duration must be positive");
		}
		return new ClericEffectClock() {
			@Override
			public long nanoTime() {
				return clock.nanoTime();
			}

			@Override
			public long getGameTickMilliseconds() {
				return gameTickMilliseconds;
			}
		};
	}
}
