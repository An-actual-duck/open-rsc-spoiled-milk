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
	public static final int DIVINE_GRACE = 39;
	public static final int DIVINE_RETRIBUTION = 40;
	public static final int CORROSIVE_AURA = 41;
	public static final int LESSER_DEMON_MAGIC = 42;
	public static final int GREATER_DEMON_MAGIC = 43;
	public static final int ENEMY_EARTH_BASIC = 44;
	public static final int BLACK_DEMON_MAGIC = 45;
	public static final int BALROG_MAGIC = 46;
	public static final int BATTLE_MAGE_AIR = 47;
	public static final int BATTLE_MAGE_EARTH = 48;
	public static final int BATTLE_MAGE_WATER = 49;
	public static final int BATTLE_MAGE_FIRE = 50;
	public static final int GREEN_DRAGON_MAGIC = 51;
	public static final int FIRE_DRAGON_MAGIC = 52;
	public static final int OTHERWORLDLY_BEING_MAGIC = 53;
	public static final int PALADIN_MAGIC = 54;
	public static final int FIRE_KIN_MAGIC = 55;
	public static final int ICE_KIN_MAGIC = 56;
	public static final int EARTH_KIN_MAGIC = 57;
	public static final int DRAGON_WEAPON_BREATH = 58;
	public static final int FIRE_SWORD = 59;
	public static final int ICE_SWORD = 60;
	public static final int EARTH_SWORD = 61;
	public static final int ELDER_DRAGON_FIRESHOT = 62;
	public static final int ELDER_DRAGON_BURN = 63;
	public static final int TRUE_DEFENSE = 64;
	public static final int TELEPORT = 65;
	public static final int DRAGON_WEAPON_SLASH_2 = 66;
	public static final int CLERIC_MEND = 67;
	public static final int CLERIC_FERVOR = 68;
	public static final int CLERIC_PURIFY = 69;
	public static final int CLERIC_RESTORE = 70;
	public static final int CLERIC_WARD = 71;
	public static final int CLERIC_GREATER_MEND = 72;
	public static final int CLERIC_ZEAL = 73;
	public static final int CLERIC_THORNS = 74;
	public static final int CLERIC_AEGIS = 75;
	public static final int CLERIC_RALLY = 76;
	public static final int CLERIC_RESPITE = 77;
	public static final int DEMON_EXPLOSION = LESSER_DEMON_MAGIC;

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

	public static int enemyMagicAttackEffect(final String npcName) {
		if (npcName == null) {
			return NONE;
		}
		switch (npcName.toLowerCase()) {
			case "lesser demon":
			case "greater demon":
			case "chronozon":
				return DEMON_EXPLOSION;
			case "black demon":
				return BLACK_DEMON_MAGIC;
			case "balrog":
				return BALROG_MAGIC;
			default:
				return NONE;
		}
	}
}
