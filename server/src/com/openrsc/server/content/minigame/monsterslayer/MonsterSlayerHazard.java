package com.openrsc.server.content.minigame.monsterslayer;

/** Explicit preparation hazards attached to a Slayer task definition. */
public enum MonsterSlayerHazard {
	DESERT_HEAT,
	WILDERNESS,
	PRAYER_DRAIN,
	POISON,
	DRAGON_FIRE;

	public static MonsterSlayerHazard fromKey(String key) {
		try {
			return valueOf(key);
		} catch (RuntimeException failure) {
			throw new IllegalArgumentException("Unknown Monster Slayer hazard: " + key, failure);
		}
	}
}
