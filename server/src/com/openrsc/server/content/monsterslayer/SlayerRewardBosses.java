package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Explicit boss identities: never infer percentage-damage immunity from combat level or name. */
public final class SlayerRewardBosses {
	private static final Set<Integer> IDS = new HashSet<Integer>(Arrays.asList(
		35, 96, 182, 196, 216, 315, 383, 388, 401, 410, 477, 568,
		613, 614, 615, 649, 713, 757, 758, 759, 760, 769, 809, 844, 845));
	private SlayerRewardBosses() { }
	public static boolean isBoss(Mob mob) { return mob instanceof Npc && IDS.contains(mob.getID()); }
}
