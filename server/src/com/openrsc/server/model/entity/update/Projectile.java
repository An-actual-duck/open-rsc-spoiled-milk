package com.openrsc.server.model.entity.update;

import com.openrsc.server.model.entity.Mob;

public class Projectile {
	public static final int ORB = 0;
	public static final int MAGIC = 1;
	public static final int RANGED = 2;
	public static final int GNOMEBALL = 3;
	public static final int SKULL = 4;
	public static final int SPIKED_BALL = 5;
	public static final int BLANK = 6;
	public static final int BLOW_SMOKE = 7;
	public static final int FIREBALL = 8;
	public static final int WIND_ARROW = 9;
	public static final int ROCK_THROW = 10;
	public static final int WATER_BALL = 11;
	public static final int THROWING_KNIFE = 12;
	public static final int ARROW = 13;
	public static final int THROWING_DART = 14;
	public static final int CLAWS_OF_GUTHIX = 15;
	public static final int THUNDER_BALL = 16;
	public static final int ICICLE_SHOT = 17;
	public static final int ACID_DROP = 18;
	public static final int BRANCH_SPORE = 19;

	/**
	 * Who fired the projectile
	 */
	private Mob caster;
	/**
	 * The type: 1 = magic, 2 = ranged
	 */
	private int type;
	/**
	 * Who the projectile is being fired at
	 */
	private Mob victim;

	public Projectile(Mob caster, Mob victim, int type) {
		this.caster = caster;
		this.victim = victim;
		this.type = type;
	}

	public Mob getCaster() {
		return caster;
	}

	public int getType() {
		return type;
	}

	public Mob getVictim() {
		return victim;
	}

}
