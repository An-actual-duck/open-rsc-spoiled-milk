package com.openrsc.server.content.cleric.effect;

/** Immutable presentation snapshot produced under the registry lock. */
public final class ClericEffectStatusSnapshot {
	private final ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition;
	private final int remainingSeconds;
	private final int remainingCounter;

	ClericEffectStatusSnapshot(
			ClericEffectRankDefinition<? extends ClericEffectMagnitude> definition,
			int remainingSeconds, int remainingCounter) {
		if (definition == null || remainingSeconds <= 0 || remainingCounter < 0) {
			throw new IllegalArgumentException("Invalid Cleric effect status snapshot");
		}
		this.definition = definition;
		this.remainingSeconds = remainingSeconds;
		this.remainingCounter = remainingCounter;
	}

	public ClericEffectRankDefinition<? extends ClericEffectMagnitude> getDefinition() {
		return definition;
	}

	public int getRemainingSeconds() {
		return remainingSeconds;
	}

	public int getRemainingCounter() {
		return remainingCounter;
	}
}
