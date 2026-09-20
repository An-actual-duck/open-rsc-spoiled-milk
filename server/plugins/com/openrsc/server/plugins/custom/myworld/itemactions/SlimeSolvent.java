package com.openrsc.server.plugins.custom.myworld.itemactions;

import com.openrsc.server.content.monsterslayer.GiantFrogCombat;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;

public final class SlimeSolvent implements OpInvTrigger {
	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return player.getConfig().WANT_MYWORLD && GiantFrogCombat.solventDoses(item.getCatalogId()) > 0;
	}
	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
		if (!blockOpInv(player, invIndex, item, command) || !"Drink".equalsIgnoreCase(command)
			|| com.openrsc.server.content.monsterslayer.AbyssalDemonCombat.actionsBlocked(player)
			|| item.getItemStatus().getNoted() || player.killed
			|| player.getSkills().getLevel(com.openrsc.server.constants.Skill.HITS.id()) <= 0) return;
		int dosesLeft = GiantFrogCombat.solventDoses(item.getCatalogId()) - 1;
		int nextId = dosesLeft == 2 ? GiantFrogCombat.SOLVENT_TWO_DOSE_ID
			: dosesLeft == 1 ? GiantFrogCombat.SOLVENT_ONE_DOSE_ID
			: com.openrsc.server.constants.ItemId.EMPTY_VIAL.id();
		// Preserve the bottle's slot and identity, even with a full inventory.
		// A stale action for the previous dose cannot consume another bottle.
		if (!player.getCarriedItems().getInventory().replaceExact(item, new Item(nextId), true)) return;
		GiantFrogCombat.applySolvent(player);
		player.message(dosesLeft == 0 ? "You have finished your Slime Solvent."
			: "You have " + dosesLeft + (dosesLeft == 1 ? " dose" : " doses") + " of Slime Solvent left.");
	}
}
