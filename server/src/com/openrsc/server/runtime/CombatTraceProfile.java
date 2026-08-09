package com.openrsc.server.runtime;

import java.util.Locale;

/**
 * Explicit, opt-in views available to the bounded combat trace recorder.
 *
 * <p>This is deliberately a closed vocabulary. Configuration integration must
 * resolve a value through {@link #fromExternalValue(String)} before creating a
 * recorder; an unknown profile must not silently enable a broader trace.</p>
 */
public enum CombatTraceProfile {
	OFF(false, false),
	DAMAGE(true, false),
	LIFECYCLE(false, true),
	FULL(true, true);

	private final boolean recordsDamage;
	private final boolean recordsLifecycle;

	CombatTraceProfile(final boolean recordsDamage,
			final boolean recordsLifecycle) {
		this.recordsDamage = recordsDamage;
		this.recordsLifecycle = recordsLifecycle;
	}

	public boolean recordsDamage() {
		return recordsDamage;
	}

	public boolean recordsLifecycle() {
		return recordsLifecycle;
	}

	/** Resolves only the documented lower-case profile vocabulary. */
	public static CombatTraceProfile fromExternalValue(final String value) {
		if (value == null) {
			throw new IllegalArgumentException("combat trace profile is required");
		}
		final String normalized = value.trim().toUpperCase(Locale.ROOT);
		for (final CombatTraceProfile profile : values()) {
			if (profile.name().equals(normalized)) {
				return profile;
			}
		}
		throw new IllegalArgumentException("unknown combat trace profile: "
			+ value);
	}
}
