package com.openrsc.server.plugins.authentic.skills.crafting;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.UseInvTrigger;
import com.openrsc.server.util.rsc.MessageType;

public class BattlestaffCrafting implements UseInvTrigger {

	private static final int[][] RETIRED_COMBINATIONS = {
		{ItemId.BATTLESTAFF.id(), ItemId.WATER_ORB.id()},
		{ItemId.BATTLESTAFF.id(), ItemId.EARTH_ORB.id()},
		{ItemId.BATTLESTAFF.id(), ItemId.FIRE_ORB.id()},
		{ItemId.BATTLESTAFF.id(), ItemId.AIR_ORB.id()},
	};

	private boolean isRetiredBattlestaffPair(Item itemOne, Item itemTwo) {
		int first = itemOne.getCatalogId();
		int second = itemTwo.getCatalogId();
		for (int[] pair : RETIRED_COMBINATIONS) {
			if ((pair[0] == first && pair[1] == second) || (pair[0] == second && pair[1] == first)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		player.playerServerMessage(MessageType.QUEST,
			"Battlestaff crafting has been retired. Use a staff directly on an altar through Enchanting instead.");
	}

	@Override
	public boolean blockUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		return isRetiredBattlestaffPair(item1, item2);
	}
}
