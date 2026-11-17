package com.openrsc.server.model.entity.update;

import com.openrsc.server.model.combat.DamageRequest;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;

public class HitSplat {
	public static final int TYPE_STANDARD = 0;
	public static final int TYPE_POISON = 1;
	public static final int TYPE_ARMOR_PROC = 2;
	public static final int TYPE_HEAL = 3;
	// Additive custom-client presentation IDs; classic damage updates are unchanged.
	public static final int TYPE_SUMMON = 4;
	public static final int TYPE_RECOIL = 5;
	public static final int TYPE_BURN = 6;
	public static final int TYPE_RANGED = 7;
	public static final int TYPE_MAGIC = 8;
	public static final int TYPE_BLEED = 9;

	/** Explicit source/effect colors win; ordinary direct hits use their damage style. */
	public static int typeForDamage(DamageRequest request) {
		int explicit = request.getHitSplatType();
		if (explicit != TYPE_STANDARD
			|| request.getSourceCategory() == DamageRequest.SourceCategory.DOT
			|| request.getStyle() == null) return explicit;
		switch (request.getStyle()) {
			case RANGED: case THROWING: return TYPE_RANGED;
			case MAGIC: return TYPE_MAGIC;
			default: return TYPE_STANDARD;
		}
	}

	private final Mob mob;
	private final int type;
	private final int amount;
	private final int index;

	public HitSplat(Mob mob, int type, int amount) {
		this.mob = mob;
		this.type = type;
		this.amount = Math.max(0, Math.min(255, amount));
		this.index = mob.getIndex();
	}

	public int getType() {
		return type;
	}

	public int getAmount() {
		return amount;
	}

	public int getCurHits() {
		return mob.getSkills().getLevel(Skill.HITS.id());
	}

	public int getMaxHits() {
		return mob.getSkills().getMaxStat(Skill.HITS.id());
	}

	public int getIndex() {
		return index;
	}
}
