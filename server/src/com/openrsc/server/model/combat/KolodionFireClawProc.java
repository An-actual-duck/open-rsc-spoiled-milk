package com.openrsc.server.model.combat;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.runtime.GameRandom;

/** Shared eligibility and chance policy for Kolodion demon's Fire Claw. */
public final class KolodionFireClawProc {
	private KolodionFireClawProc() { }

	public static boolean tryApply(final Mob hitter, final Mob target,
			final int primaryDamage, final double chance,
			final GameRandom random) {
		return hitter.isNpc() && primaryDamage > 0
			&& target.getSkills().getLevel(Skill.HITS.id()) > 0
			&& ((Npc) hitter).getID() == NpcId.KOLODION_DEMON.id()
			&& random.nextDouble() < chance;
	}
}
