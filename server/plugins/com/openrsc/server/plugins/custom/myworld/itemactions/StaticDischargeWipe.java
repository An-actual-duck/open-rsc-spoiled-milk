package com.openrsc.server.plugins.custom.myworld.itemactions;

import com.openrsc.server.content.monsterslayer.DarkBeastCombat;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;

public final class StaticDischargeWipe implements OpInvTrigger {
	@Override public boolean blockOpInv(Player player, Integer index, Item item, String command) {
		return player.getConfig().WANT_MYWORLD && DarkBeastCombat.uses(item.getCatalogId()) > 0;
	}
	@Override public void onOpInv(Player player, Integer index, Item item, String command) {
		if (!blockOpInv(player, index, item, command) || !"Wipe".equalsIgnoreCase(command)
			|| item.getItemStatus().getNoted() || player.killed || player.getLevel(Skill.HITS.id()) <= 0) return;
		if (DarkBeastCombat.markCount(player) == 0) { player.message("You have no static charge to wipe away."); return; }
		int left = DarkBeastCombat.uses(item.getCatalogId()) - 1;
		int next = left == 0 ? ItemId.EMPTY_VIAL.id() : item.getCatalogId() + 1;
		if (!player.getCarriedItems().getInventory().replaceExact(item, new Item(next), true)) return;
		DarkBeastCombat.wipe(player);
		player.message(left == 0 ? "You have used the last of your Static discharge wipe."
			: "You have " + left + (left == 1 ? " use" : " uses") + " left.");
	}
}
