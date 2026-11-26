package com.openrsc.server.content.market.task;

import com.openrsc.server.content.market.CollectibleItem;
import com.openrsc.server.content.market.AuctionFeedback;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.model.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class CollectibleItemsNotificationTask extends MarketTask {

	private static final Logger LOGGER = LogManager.getLogger();

	private Player player;

	public CollectibleItemsNotificationTask(Player player) {
		this.player = player;
	}

	@Override
	public void doTask() {
		try {
			ArrayList<CollectibleItem> list = player.getWorld().getServer().getDatabase().getCollectibleItems(player.getDatabaseID());
			if (!list.isEmpty()) AuctionFeedback.collection(player, list, false, 0);
		} catch (final GameDatabaseException e) {
			LOGGER.catching(e);
		}
	}
}
