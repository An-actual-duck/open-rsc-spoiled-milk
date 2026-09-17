package com.openrsc.server.plugins.custom.myworld.itemactions;

import com.openrsc.server.content.monsterslayer.GiantFrogCombat;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;

public final class SlimeSolvent implements OpInvTrigger {
	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return player.getConfig().WANT_MYWORLD && item.getCatalogId() == GiantFrogCombat.SOLVENT_ITEM_ID;
	}
	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
		if (!blockOpInv(player, invIndex, item, command) || !"Drink".equalsIgnoreCase(command)
			|| item.getItemStatus().getNoted() || player.killed
			|| player.getSkills().getLevel(com.openrsc.server.constants.Skill.HITS.id()) <= 0) return;
		// One single-use bottle. No delay between removal and application.
		if (player.getCarriedItems().remove(new Item(GiantFrogCombat.SOLVENT_ITEM_ID)) < 0) return;
		GiantFrogCombat.applySolvent(player);
	}
}
