package com.openrsc.server.plugins.authentic.npcs.varrock;

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

public class Thrander extends AbstractShop {

	private final Shop shop = new Shop(false, 35000, 120, 55, 2,
		new Item(ItemId.TIN_LARGE_HELMET.id(), 2),
		new Item(ItemId.TIN_PLATE_MAIL_LEGS.id(), 2),
		new Item(ItemId.TIN_PLATE_MAIL_BODY.id(), 2),
		new Item(ItemId.COPPER_LARGE_HELMET.id(), 2),
		new Item(ItemId.COPPER_PLATE_MAIL_LEGS.id(), 2),
		new Item(ItemId.COPPER_PLATE_MAIL_BODY.id(), 2),
		new Item(ItemId.LARGE_BRONZE_HELMET.id(), 2),
		new Item(ItemId.BRONZE_PLATE_MAIL_LEGS.id(), 2),
		new Item(ItemId.BRONZE_PLATE_MAIL_BODY.id(), 2),
		new Item(ItemId.LARGE_IRON_HELMET.id(), 1),
		new Item(ItemId.IRON_PLATE_MAIL_LEGS.id(), 1),
		new Item(ItemId.IRON_PLATE_MAIL_BODY.id(), 1),
		new Item(ItemId.LARGE_STEEL_HELMET.id(), 1),
		new Item(ItemId.STEEL_PLATE_MAIL_LEGS.id(), 1),
		new Item(ItemId.STEEL_PLATE_MAIL_BODY.id(), 1));

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.THRANDER.id();
	}

	@Override
	public Shop[] getShops(World world) {
		return new Shop[]{shop};
	}

	@Override
	public boolean isMembers() {
		return false;
	}

	@Override
	public Shop getShop() {
		return shop;
	}

	@Override
	public void onTalkNpc(Player player, Npc n) {
		npcsay(player, n, "Hello I'm Thrander the smith",
			"I sell practical armour for adventurers");
		int option = multi(player, n, false,
			"What armour do you have?",
			"No thank you");
		if (option == 0) {
			player.setAccessingShop(shop);
			ActionSender.showShop(player, shop);
		}
	}
}
