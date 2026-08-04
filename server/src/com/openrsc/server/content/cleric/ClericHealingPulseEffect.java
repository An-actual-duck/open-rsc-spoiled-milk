package com.openrsc.server.content.cleric;

/** Pure healing-ceiling clamp shared by immediate and delayed Mend pulses. */
public final class ClericHealingPulseEffect {
	private ClericHealingPulseEffect() {
	}

	public static int healedHits(final int currentHits, final int healingCeiling,
			final int hitsPerPulse) {
		if (currentHits < 0 || healingCeiling < 0 || hitsPerPulse <= 0) {
			throw new IllegalArgumentException("Invalid Cleric healing-pulse state");
		}
		if (currentHits >= healingCeiling) {
			return 0;
		}
		return Math.min(hitsPerPulse, healingCeiling - currentHits);
	}
}
