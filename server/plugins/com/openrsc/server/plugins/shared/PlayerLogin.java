package com.openrsc.server.plugins.shared;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.content.worldedit.WorldBuilderPlayerSession;
import com.openrsc.server.net.rsc.InventoryCapacityPackets;
import com.openrsc.server.plugins.triggers.PlayerLoginTrigger;

public class PlayerLogin implements PlayerLoginTrigger {
	@Override
	public void onPlayerLogin(Player player) {
		WorldBuilderPlayerSession.activate(player);
		InventoryCapacityPackets.send(player);
	}

	@Override
	public boolean blockPlayerLogin(Player player) {
		return true;
	}
}
