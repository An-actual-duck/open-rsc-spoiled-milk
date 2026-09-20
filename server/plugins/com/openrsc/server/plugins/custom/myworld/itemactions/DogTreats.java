package com.openrsc.server.plugins.custom.myworld.itemactions;

import com.openrsc.server.content.monsterslayer.TerrorDogCombat;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;

public final class DogTreats implements OpInvTrigger {
	@Override public boolean blockOpInv(Player player, Integer index, Item item, String command) {
		return player.getConfig().WANT_MYWORLD && TerrorDogCombat.uses(item.getCatalogId()) > 0;
	}
	@Override public void onOpInv(Player player, Integer index, Item item, String command) {
		if (!blockOpInv(player, index, item, command) || !"Scatter".equalsIgnoreCase(command)
			|| item.getItemStatus().getNoted() || player.killed || player.getSkills().getLevel(Skill.HITS.id()) <= 0) return;
		int left = TerrorDogCombat.uses(item.getCatalogId()) - 1;
		int next = left == 0 ? ItemId.EMPTY_VIAL.id() : item.getCatalogId() + 1;
		if (!player.getCarriedItems().getInventory().replaceExact(item, new Item(next), true)) return;
		TerrorDogCombat.applyTreats(player);
		player.message(left == 0 ? "You have used the last of your Dog Treats."
			: "You have " + left + (left == 1 ? " use" : " uses") + " of Dog Treats left.");
	}
}
