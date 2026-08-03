package com.openrsc.server.content.cleric;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Stable primary-sigil alignment independent of a player's selected worship. */
public enum ClericAlignment {
	NEUTRAL("neutral"),
	SARADOMIN("saradomin"),
	GUTHIX("guthix"),
	ZAMORAK("zamorak");

	private static final Map<String, ClericAlignment> BY_KEY;

	static {
		Map<String, ClericAlignment> values = new HashMap<String, ClericAlignment>();
		for (ClericAlignment alignment : ClericAlignment.values()) {
			if (values.put(alignment.key, alignment) != null) {
				throw new IllegalStateException("Duplicate Cleric alignment key: " + alignment.key);
			}
		}
		BY_KEY = Collections.unmodifiableMap(values);
	}

	private final String key;

	ClericAlignment(String key) {
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	public static ClericAlignment fromKey(String key) {
		ClericAlignment alignment = BY_KEY.get(key);
		if (alignment == null) {
			throw new IllegalArgumentException("Unknown Cleric alignment key: " + key);
		}
		return alignment;
	}
}
