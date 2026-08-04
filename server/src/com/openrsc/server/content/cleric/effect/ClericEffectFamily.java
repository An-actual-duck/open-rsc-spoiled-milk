package com.openrsc.server.content.cleric.effect;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Stable exclusive slots for transient Cleric support effects. */
public enum ClericEffectFamily {
	HEALING_PULSES(0, "cleric:healing_pulses"),
	ACCURACY(1, "cleric:accuracy"),
	PROTECTION(2, "cleric:protection"),
	DAMAGE(3, "cleric:damage"),
	REFLECTION(4, "cleric:reflection"),
	LIFESTEAL(5, "cleric:lifesteal"),
	PASSIVE_REGENERATION(6, "cleric:passive_regeneration");

	private static final Map<Integer, ClericEffectFamily> BY_CODE;
	private static final Map<String, ClericEffectFamily> BY_KEY;

	static {
		Map<Integer, ClericEffectFamily> codes = new HashMap<Integer, ClericEffectFamily>();
		Map<String, ClericEffectFamily> keys = new HashMap<String, ClericEffectFamily>();
		for (ClericEffectFamily family : values()) {
			if (codes.put(family.code, family) != null) {
				throw new IllegalStateException("Duplicate Cleric effect family code: " + family.code);
			}
			if (keys.put(family.key, family) != null) {
				throw new IllegalStateException("Duplicate Cleric effect family key: " + family.key);
			}
		}
		BY_CODE = Collections.unmodifiableMap(codes);
		BY_KEY = Collections.unmodifiableMap(keys);
	}

	private final int code;
	private final String key;

	ClericEffectFamily(int code, String key) {
		this.code = code;
		this.key = key;
	}

	public int getCode() {
		return code;
	}

	public String getKey() {
		return key;
	}

	public static ClericEffectFamily fromCode(int code) {
		ClericEffectFamily family = BY_CODE.get(code);
		if (family == null) {
			throw new IllegalArgumentException("Unknown Cleric effect family code: " + code);
		}
		return family;
	}

	public static ClericEffectFamily fromKey(String key) {
		ClericEffectFamily family = BY_KEY.get(key);
		if (family == null) {
			throw new IllegalArgumentException("Unknown Cleric effect family key: " + key);
		}
		return family;
	}
}
