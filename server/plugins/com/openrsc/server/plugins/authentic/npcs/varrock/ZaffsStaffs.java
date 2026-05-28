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

public final class ZaffsStaffs extends AbstractShop {

	private Shop shop = null;

	@Override
	public boolean blockTalkNpc(final Player player, final Npc n) {
		return n.getID() == NpcId.ZAFF.id();
	}

	@Override
	public Shop[] getShops(World world) {
		return new Shop[]{getShop(world)};
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
	public void onTalkNpc(final Player player, final Npc n) {
		npcsay(player, n, "Would you like to buy or sell some staffs?");
		int option = multi(player, n, "Yes please", "No, thank you");
		if (option == 0) {
			player.setAccessingShop(getShop(player.getWorld()));
			ActionSender.showShop(player, getShop(player.getWorld()));
		}
	}

	public Shop getShop(World world) {
		if(shop == null) {
			shop = (world.getServer().getConfig().MEMBER_WORLD) ?
				new Shop(false, 30000, 100, 55, 2,
					new Item(ItemId.FIRE_RUNE.id(), 50), new Item(ItemId.WATER_RUNE.id(), 50),
					new Item(ItemId.AIR_RUNE.id(), 50), new Item(ItemId.EARTH_RUNE.id(), 50),
					new Item(ItemId.MIND_RUNE.id(), 50), new Item(ItemId.BODY_RUNE.id(), 50),
					new Item(ItemId.STAFF.id(), 5),
					new Item(ItemId.WOOL_WIZARD_HAT.id(), 3), new Item(ItemId.WOOL_ROBE_TOP.id(), 3), new Item(ItemId.WOOL_ROBE_SKIRT.id(), 3)) :
				new Shop(false, 30000, 100, 55, 2,
					new Item(ItemId.FIRE_RUNE.id(), 50), new Item(ItemId.WATER_RUNE.id(), 50),
					new Item(ItemId.AIR_RUNE.id(), 50), new Item(ItemId.EARTH_RUNE.id(), 50),
					new Item(ItemId.MIND_RUNE.id(), 50), new Item(ItemId.BODY_RUNE.id(), 50),
					new Item(ItemId.STAFF.id(), 5),
					new Item(ItemId.WOOL_WIZARD_HAT.id(), 3), new Item(ItemId.WOOL_ROBE_TOP.id(), 3), new Item(ItemId.WOOL_ROBE_SKIRT.id(), 3));
		}

		return shop;
	}
}
