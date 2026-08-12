package com.openrsc.server.content.minigame.monsterslayer;

import java.util.Locale;

/** One non-interchangeable Monster Slayer challenge currency. */
public enum MonsterSlayerChallenge {
	FLEDGLING(0, "fledgling"),
	INITIATE(1, "initiate"),
	VETERAN(2, "veteran"),
	ELITE(3, "elite"),
	CHAMPION(4, "champion"),
	HERO(5, "hero");

	private final int code;
	private final String cacheSuffix;

	MonsterSlayerChallenge(int code, String cacheSuffix) {
		this.code = code;
		this.cacheSuffix = cacheSuffix;
	}

	public int getCode() {
		return code;
	}

	public String getCacheSuffix() {
		return cacheSuffix;
	}

	/** Player-facing rank name; INITIATE remains the stable cache/data key. */
	public String getDisplayName() {
		return this == INITIATE ? "Adept" : name().substring(0, 1) + name().substring(1).toLowerCase(Locale.ROOT);
	}

	public boolean isAtOrBelow(MonsterSlayerChallenge shopTier) {
		return code <= shopTier.code;
	}

	public static MonsterSlayerChallenge fromCode(int code) {
		for (MonsterSlayerChallenge challenge : values()) {
			if (challenge.code == code) {
				return challenge;
			}
		}
		throw new IllegalArgumentException("Unknown Monster Slayer challenge code: " + code);
	}

	public static MonsterSlayerChallenge fromKey(String key) {
		if (key == null) {
			throw new IllegalArgumentException("Monster Slayer challenge is required");
		}
		if ("adept".equalsIgnoreCase(key)) {
			return INITIATE;
		}
		try {
			return valueOf(key.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Unknown Monster Slayer challenge: " + key, ex);
		}
	}
}
