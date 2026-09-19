package com.openrsc.server.plugins.custom.myworld.itemactions;

import com.openrsc.server.content.monsterslayer.CockatriceCombat;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;

public final class EyeDrops implements OpInvTrigger {
	@Override public boolean blockOpInv(Player player, Integer index, Item item, String command) {
		return player.getConfig().WANT_MYWORLD && CockatriceCombat.doses(item.getCatalogId()) > 0;
	}
	@Override public void onOpInv(Player player, Integer index, Item item, String command) {
		if (!blockOpInv(player, index, item, command) || !"Apply".equalsIgnoreCase(command)
			|| item.getItemStatus().getNoted() || player.killed || player.getSkills().getLevel(Skill.HITS.id()) <= 0) return;
		int left = CockatriceCombat.doses(item.getCatalogId()) - 1;
		int next = left == 0 ? ItemId.EMPTY_VIAL.id() : item.getCatalogId() + 1;
		if (!player.getCarriedItems().getInventory().replaceExact(item, new Item(next), true)) return;
		CockatriceCombat.applyEyeDrops(player);
		player.message(left == 0 ? "You have finished your Eye Drops."
			: "You have " + left + (left == 1 ? " use" : " uses") + " of Eye Drops left.");
	}
}
