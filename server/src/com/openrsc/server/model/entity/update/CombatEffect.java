package com.openrsc.server.model.entity.update;

import com.openrsc.server.model.entity.Mob;

public class CombatEffect {
	public static final int NONE = 0;
	public static final int HELLS_FIRE = 1;
	public static final int HELLS_BLAZE = 2;
	public static final int AIR_SLASH = 3;
	public static final int EARTH_BURST = 4;
	public static final int WATER_BURST = 5;
	public static final int FIRE_CLAW = 6;
	public static final int WIND_BEAM = 7;
	public static final int EARTH_HAMMER = 8;
	public static final int HURRICANE = 9;
	public static final int FIRE_BOMB = 10;
	public static final int TORNADO = 11;
	public static final int EARTH_IMPALE = 12;
	public static final int KRAKEN = 13;
	public static final int PHOENIX = 14;
	public static final int HELLS_INFERNO = 15;
	public static final int LESSER_HEAL = 16;
	public static final int GREATER_HEAL = 17;
	public static final int ALCHEMY = 18;
	public static final int SARADOMIN_STRIKE = 19;
	public static final int ZAMORAKS_VOID = 20;
	public static final int IBAN_BLAST = 21;
	public static final int EYE_OF_GUTHIX = 22;
	public static final int ZAMORAKS_APOCOLYPSE = 23;
	public static final int SARADOMIN_SOUL_SLASH = 24;
	public static final int CLAW_OF_GUTHIX = 25;
	public static final int SUMMON = 26;
	public static final int SUMMON_COMBAT = 27;
	public static final int SUMMON_SUPPORT = 28;
	public static final int SUMMON_UTILITY = 29;
	public static final int THUNDER_SPLASH = 30;
	public static final int ICE_BURST = 31;
	public static final int ACID_FROG = 32;
	public static final int WOOD_DRILL = 33;
	public static final int THUNDER_STRIKE = 34;
	public static final int ICE_CRYSTAL = 35;
	public static final int ACID_GUSH = 36;
	public static final int BATTERING_RAM = 37;
	public static final int DRAGON_BREATH = 38;

	public static final int HELLFIRE = HELLS_FIRE;
	public static final int WIND_SLASH = AIR_SLASH;
	public static final int WATER_ERUPTION = HURRICANE;
	public static final int EXPLOSION = FIRE_BOMB;
	public static final int WATER_VORTEX = KRAKEN;
	public static final int FIRE_PILLAR = PHOENIX;

	private final Mob target;
	private final int effectType;

	public CombatEffect(final Mob target, final int effectType) {
		this.target = target;
		this.effectType = effectType;
	}

	public Mob getTarget() {
		return target;
	}

	public int getEffectType() {
		return effectType;
	}

	public static int infernalEffectForMaxHit(final int maxHit) {
		if (maxHit >= 18) {
			return HELLS_INFERNO;
		}
		if (maxHit >= 12) {
			return HELLS_BLAZE;
		}
		return HELLS_FIRE;
	}
}
