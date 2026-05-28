package com.openrsc.server.plugins.authentic.npcs.catherby;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.AbstractShop;

import static com.openrsc.server.plugins.Functions.*;

public class HicktonArcheryShop extends AbstractShop {

	private final Shop shop = new Shop(false, 10000, 100, 80, 1,
		new Item(ItemId.TIN_ARROWS.id(), 200), new Item(ItemId.COPPER_ARROWS.id(), 200), new Item(ItemId.BRONZE_ARROWS.id(), 200),
		new Item(ItemId.IRON_ARROWS.id(), 180), new Item(ItemId.STEEL_ARROWS.id(), 160), new Item(ItemId.MITHRIL_ARROWS.id(), 140),
		new Item(ItemId.COPPER_BOLTS.id(), 200), new Item(ItemId.BRONZE_BOLTS.id(), 180), new Item(ItemId.IRON_BOLTS.id(), 160),
		new Item(ItemId.STEEL_BOLTS.id(), 140), new Item(ItemId.MITHRIL_BOLTS.id(), 120),
		new Item(ItemId.TIN_ARROW_HEADS.id(), 200), new Item(ItemId.COPPER_ARROW_HEADS.id(), 180), new Item(ItemId.BRONZE_ARROW_HEADS.id(), 160),
		new Item(ItemId.IRON_ARROW_HEADS.id(), 140), new Item(ItemId.STEEL_ARROW_HEADS.id(), 120), new Item(ItemId.MITHRIL_ARROW_HEADS.id(), 100),
		new Item(ItemId.SHORTBOW.id(), 4), new Item(ItemId.LONGBOW.id(), 2), new Item(ItemId.OAK_SHORTBOW.id(), 4),
		new Item(ItemId.OAK_LONGBOW.id(), 4), new Item(ItemId.WILLOW_SHORTBOW.id(), 3), new Item(ItemId.WILLOW_LONGBOW.id(), 3),
		new Item(ItemId.MAPLE_SHORTBOW.id(), 2), new Item(ItemId.MAPLE_LONGBOW.id(), 2), new Item(ItemId.YEW_SHORTBOW.id(), 1),
		new Item(ItemId.YEW_LONGBOW.id(), 1), new Item(ItemId.MAGIC_SHORTBOW.id(), 1), new Item(ItemId.MAGIC_LONGBOW.id(), 1),
		new Item(ItemId.CROSSBOW.id(), 2), new Item(ItemId.OAK_CROSSBOW.id(), 2), new Item(ItemId.WILLOW_CROSSBOW.id(), 2),
		new Item(ItemId.MAPLE_CROSSBOW.id(), 1), new Item(ItemId.YEW_CROSSBOW.id(), 1), new Item(ItemId.MAGIC_CROSSBOW.id(), 1));

	@Override
	public boolean blockTalkNpc(final Player player, final Npc n) {
		return n.getID() == NpcId.HICKTON.id();
	}

	@Override
	public Shop[] getShops(World world) {
		return new Shop[]{shop};
	}

	@Override
	public boolean isMembers() {
		return true;
	}

	@Override
	public Shop getShop() {
		return shop;
	}

	@Override
	public void onTalkNpc(final Player player, final Npc n) {
		npcsay(player, n, "Welcome to Hickton's Archery Store",
			"Do you want to see my wares?");

		final int option = multi(player, n, false, //do not send over
			"Yes please",
			"No, I prefer to bash things close up");
		if (option == 0) {
			say(player, n, "Yes Please");
			player.setAccessingShop(shop);
			ActionSender.showShop(player, shop);
		} else if (option == 1) {
			say(player, n, "No, I prefer to bash things close up");
		}
	}
}
