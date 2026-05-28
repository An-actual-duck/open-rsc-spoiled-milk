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

public final class Tailor extends AbstractShop {

	private final Shop shop = new Shop(false, 30000, 130, 40, 2,
		new Item(ItemId.SHEARS.id(), 3), new Item(ItemId.NEEDLE.id(), 3),
		new Item(ItemId.WOOL.id(), 30), new Item(ItemId.BALL_OF_WOOL.id(), 30), new Item(ItemId.THREAD.id(), 100),
		new Item(ItemId.COW_HIDE.id(), 20), new Item(ItemId.GOBLIN_HIDE.id(), 15),
		new Item(ItemId.UNICORN_HIDE.id(), 10), new Item(ItemId.BEAR_HIDE.id(), 10));

	@Override
	public boolean blockTalkNpc(final Player player, final Npc n) {
		return n.getID() == NpcId.TAILOR.id();
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
		npcsay(player, n, "I keep supplies for tailoring and leatherwork");
		say(player, n, "What sort of supplies?");
		npcsay(player, n, "Wool, thread, tools, and low tier hides");
		int opt = multi(player, n, false, //do not send over
			"I have enough supplies for now",
			"Let's see what you've got then");
		if (opt == 0) {
			say(player, n, "I have enough supplies for now");
		} else if (opt == 1) {
			say(player, n, "Let's see what you've got then");
			player.setAccessingShop(shop);
			ActionSender.showShop(player, shop);
		}
	}
}
