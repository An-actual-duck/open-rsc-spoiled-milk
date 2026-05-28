package com.openrsc.server.plugins.custom.myworld.itemactions;

import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;

public class RunecraftPotion implements OpInvTrigger {

	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return false;
	}

	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
	}
}
