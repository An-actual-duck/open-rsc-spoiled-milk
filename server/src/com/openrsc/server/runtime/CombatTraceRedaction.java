package com.openrsc.server.runtime;

/** Small, conservative redaction policy shared by combat diagnostics. */
final class CombatTraceRedaction {
	private static final int MAX_EFFECT_KEY_LENGTH = 64;

	private CombatTraceRedaction() {
	}

	/**
	 * Stable combat effect identifiers are useful diagnostics. Arbitrary values
	 * are not logged or retained, because compatibility/plugin callers could
	 * otherwise accidentally place player-facing text in an effect key.
	 */
	static String effectKey(final String value) {
		if (value == null || value.length() > MAX_EFFECT_KEY_LENGTH) {
			return "redacted";
		}
		for (int index = 0; index < value.length(); index++) {
			final char character = value.charAt(index);
			if (!((character >= 'a' && character <= 'z')
					|| (character >= 'A' && character <= 'Z')
					|| (character >= '0' && character <= '9')
					|| character == '-' || character == '_')) {
				return "redacted";
			}
		}
		return value;
	}
}
