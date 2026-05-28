package com.openrsc.server.model.entity.update;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;

public class HitSplat {
	public static final int TYPE_STANDARD = 0;
	public static final int TYPE_POISON = 1;
	public static final int TYPE_ARMOR_PROC = 2;
	public static final int TYPE_HEAL = 3;

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
