package com.openrsc.server.plugins.authentic.misc;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.ChatMessage;
import com.openrsc.server.plugins.triggers.SpellNpcTrigger;
import com.openrsc.server.util.rsc.CombatEffectUtil;

import static com.openrsc.server.plugins.Functions.delay;

public class SalarinTheTwistedMageAI implements SpellNpcTrigger {

	/*
	 * Player maging Salarin the twisted AI - Just to degenerate ATTACK AND STRENGTH if over 2 in said skill.
	 */

	@Override
	public boolean blockSpellNpc(Player player, Npc n) {
		return n.getID() == NpcId.SALARIN_THE_TWISTED.id()
			&& player.getSkills().getLevel(CombatEffectUtil.remapLegacyPlayerMeleeStat(player, Skill.ATTACK.id())) > 2;
	}

	@Override
	public void onSpellNpc(Player player, Npc n) {
		int meleeStat = CombatEffectUtil.remapLegacyPlayerMeleeStat(player, Skill.ATTACK.id());
		if (n.getID() == NpcId.SALARIN_THE_TWISTED.id() && player.getSkills().getLevel(meleeStat) > 2) {
			if (!player.withinRange(n, 5))
				return;
			n.getUpdateFlags().setChatMessage(new ChatMessage(n, "Amshalaraz Nithcosh dimarilo", player));
			delay();
			player.message("You suddenly feel much weaker");
			boolean sendUpdate = player.getClientLimitations().supportsSkillUpdate;
			player.getSkills().setLevel(meleeStat, 0, sendUpdate);
			if (!sendUpdate) {
				player.getSkills().sendUpdateAll();
			}
		}
	}
}
