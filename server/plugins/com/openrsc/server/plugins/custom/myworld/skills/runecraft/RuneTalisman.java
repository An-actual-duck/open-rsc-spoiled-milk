package com.openrsc.server.plugins.custom.myworld.skills.runecraft;

import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;

public class RuneTalisman implements OpInvTrigger {

	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
		player.message("This talisman no longer serves a purpose.");
	}

	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return false;
	}
}
