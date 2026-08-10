package com.openrsc.server.content.minigame.monsterslayer;

import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;

/** Single authoritative policy for Slayer contacts and their associates. */
public final class MonsterSlayerGuildAccess {
	private MonsterSlayerGuildAccess() { }

	public static boolean allows(Player player, int contactIndex) {
		if (contactIndex == 3) {
			if (!player.getConfig().INFLUENCE_INSTEAD_QP) return player.getQuestPoints() >= 32;
			return Skill.INFLUENCE.id() >= 0
				&& player.getSkills().getLevel(Skill.INFLUENCE.id()) >= 20;
		}
		if (contactIndex == 4) return player.getQuestStage(Quests.HEROS_QUEST) == -1;
		return contactIndex != 5 || player.getQuestStage(Quests.LEGENDS_QUEST) >= 11 || player.getQuestStage(Quests.LEGENDS_QUEST) == -1;
	}
}
