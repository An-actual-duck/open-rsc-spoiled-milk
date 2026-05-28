package com.openrsc.server.util.rsc;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import java.util.Arrays;

public final class CombatEffectUtil {
	private CombatEffectUtil() {
	}

	public static int remapLegacyPlayerMeleeStat(Mob mob, int skillId) {
		if (mob != null && mob.isPlayer() && isLegacyMeleeStat(skillId)) {
			return Skill.MELEE.id();
		}
		return skillId;
	}

	public static int[] remapLegacyPlayerMeleeStats(Mob mob, int... skillIds) {
		return Arrays.stream(skillIds)
			.map(skillId -> remapLegacyPlayerMeleeStat(mob, skillId))
			.distinct()
			.toArray();
	}

	public static void sendInfernalProcDebug(Player player, String source, Mob target, int damage, int pieces,
											 int maxHit, double roll, double chance, boolean procced,
											 int procDamageRoll, int procDamageDealt) {
	}

	private static boolean isLegacyMeleeStat(int skillId) {
		return skillId == Skill.ATTACK.id()
			|| skillId == Skill.DEFENSE.id()
			|| skillId == Skill.STRENGTH.id();
	}
}
