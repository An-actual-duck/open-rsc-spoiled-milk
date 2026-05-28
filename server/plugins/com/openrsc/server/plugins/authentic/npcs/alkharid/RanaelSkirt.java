package com.openrsc.server.plugins.authentic.npcs.alkharid;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.AbstractShop;

import static com.openrsc.server.plugins.Functions.*;

public final class RanaelSkirt extends AbstractShop {

	private final Shop shop = new Shop(false, 25000, 100, 65, 1,
		new Item(ItemId.SHEARS.id(), 3), new Item(ItemId.NEEDLE.id(), 3),
		new Item(ItemId.WOOL.id(), 30), new Item(ItemId.BALL_OF_WOOL.id(), 30), new Item(ItemId.THREAD.id(), 100),
		new Item(ItemId.COW_HIDE.id(), 20), new Item(ItemId.GOBLIN_HIDE.id(), 15),
		new Item(ItemId.UNICORN_HIDE.id(), 10), new Item(ItemId.BEAR_HIDE.id(), 10)
	);

	@Override
	public boolean blockTalkNpc(final Player player, final Npc n) {
		return n.getID() == NpcId.RANAEL.id();
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
	public void onTalkNpc(final Player player, final Npc n) {
		final String[] options;
		npcsay(player, n, "Do you need tailoring supplies?",
			"I carry wool, thread, tools, and low tier hides");
		if (player.getQuestStage(Quests.FAMILY_CREST) <= 2 || player.getQuestStage(Quests.FAMILY_CREST) >= 5) {
			options = new String[]{
				"Yes please",
				"No thank you"
			};
		} else {
			options = new String[]{
				"Yes please",
				"No thank you",
				"I'm in search of a man named adam fitzharmon"
			};
		}
		int option = multi(player, n, false, options);

		if (option == 0) {
			say(player, n, "Yes Please");
			player.setAccessingShop(shop);
			ActionSender.showShop(player, shop);
		} else if (option == 1) {
			say(player, n, "No thank you");
		} else if (option == 2) {
			say(player, n, "I'm in search of a man named adam fitzharmon");
			npcsay(player, n, "I haven't seen him",
					"If he's been to Al Kharid recently",
					"Someone around here will have seen him though");
		}
	}
}
