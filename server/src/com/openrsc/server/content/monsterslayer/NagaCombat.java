package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;

/** Positional dual-wield encounter; deliberately has no Slayer counter-item. */
public final class NagaCombat {
	public static final int NPC_ID = 866;
	public static final int MELEE_TICKS = 2;
	public static final int THROW_TICKS = 3;
	private static final String NEXT_ATTACK = "slayer_naga_next_attack_tick";
	private NagaCombat() { }
	public static boolean isNaga(Mob mob) {
		return mob instanceof Npc && mob.getConfig().WANT_MYWORLD && ((Npc) mob).getID() == NPC_ID;
	}
	public static boolean attackReady(Mob mob) {
		return mob.getWorld().getServer().getCurrentTick() >= mob.getAttribute(NEXT_ATTACK, -1L);
	}
	public static void recordAttack(Mob mob, int ticks) {
		mob.setAttribute(NEXT_ATTACK, mob.getWorld().getServer().getCurrentTick() + ticks);
	}
	public static boolean canOffhand(Mob source, Mob target, boolean suppressed) {
		return isNaga(source) && !suppressed && !source.isRemoved() && !target.isRemoved()
			&& source.getLevel(Skill.HITS.id()) > 0 && target.getLevel(Skill.HITS.id()) > 0
			&& source.withinRange(target, 1);
	}
}
