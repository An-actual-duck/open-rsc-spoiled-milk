package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.model.entity.Mob;

/** Shared launch/commit guard; projectiles already in flight remain admitted. */
public final class SlayerCombatEffects {
	private SlayerCombatEffects() { }
	public static boolean attacksBlocked(Mob mob) {
		return GiantFrogCombat.attacksBlocked(mob) || CockatriceCombat.attacksBlocked(mob);
	}
}
