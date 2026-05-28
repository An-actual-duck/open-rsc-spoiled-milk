package com.openrsc.server.content.production;

import com.openrsc.server.model.entity.player.Player;

public interface ProductionStarter {
	boolean start(Player player, ProductionSession session, int itemId, int quantity);
}
