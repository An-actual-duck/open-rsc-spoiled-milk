package com.openrsc.server.plugins.authentic.npcs.shilo;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.authentic.skills.smithing.ShiloFurnaceAccess;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.*;

public class Yohnus implements TalkNpcTrigger {

	@Override
	public void onTalkNpc(Player player, Npc n) {
		if (n.getID() == NpcId.YOHNUS.id()) {
			say(player, n, "Hello");
			npcsay(player, n, "Hello Bwana, can I help you in anyway?");
			ShiloFurnaceAccess.explainAndOffer(player, n);
		}
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.YOHNUS.id();
	}
}
