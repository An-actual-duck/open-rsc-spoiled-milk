package com.openrsc.server.plugins.authentic.npcs;

import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;

public final class SummoningNpcs implements OpNpcTrigger {
	@Override
	public void onOpNpc(final Player player, final Npc npc, final String command) {
		Summoning.handleSummonCommand(player, npc, command);
	}

	@Override
	public boolean blockOpNpc(final Player player, final Npc npc, final String command) {
		return Summoning.isSummon(npc);
	}
}
