package com.openrsc.server.plugins.authentic.npcs.ardougne.east;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.AbstractShop;

import java.time.Instant;

import static com.openrsc.server.plugins.Functions.*;

public class FurMerchant extends AbstractShop {

	private final Shop shop = new Shop(false, 25000, 120, 55, 2,
		new Item(ItemId.UNICORN_HIDE_COIF.id(), 1),
		new Item(ItemId.UNICORN_HIDE_GLOVES.id(), 1),
		new Item(ItemId.UNICORN_HIDE_BOOTS.id(), 1),
		new Item(ItemId.UNICORN_HIDE_CHAPS.id(), 1),
		new Item(ItemId.UNICORN_HIDE_CUIRASS.id(), 1),
		new Item(ItemId.BEAR_HIDE_COIF.id(), 1),
		new Item(ItemId.BEAR_HIDE_GLOVES.id(), 1),
		new Item(ItemId.BEAR_HIDE_BOOTS.id(), 1),
		new Item(ItemId.BEAR_HIDE_CHAPS.id(), 1),
		new Item(ItemId.BEAR_HIDE_CUIRASS.id(), 1));

	@Override
	public void onTalkNpc(Player player, Npc n) {
		if (player.getCache().hasKey("furStolen") && (Instant.now().getEpochSecond() < player.getCache().getLong("furStolen") + 1200)) {
			npcsay(player, n, "Do you really think I'm going to buy something",
				"That you have just stolen from me",
				"guards guards");

			Npc attacker = ifnearvisnpc(player, NpcId.KNIGHT.id(), 5); // Knight first
			if (attacker == null)
				attacker = ifnearvisnpc(player, NpcId.GUARD_ARDOUGNE.id(), 5); // Guard second

			if (attacker != null)
				attacker.setChasing(player);

		} else {
			npcsay(player, n, "I trade in sturdier hides and leathers",
				"Bear and unicorn pieces carry different protections");
			int menu = multi(player, n, false,
				"What leather armour do you sell?",
				"How does hide armour work?",
				"No thank you");
			if (menu == 0) {
				say(player, n, "What leather armour do you sell?");
				player.setAccessingShop(shop);
				ActionSender.showShop(player, shop);
			} else if (menu == 1) {
				say(player, n, "How does hide armour work?");
				npcsay(player, n, "The better the creature's defenses",
					"the more interesting the armour can be",
					"A bear hide and a unicorn hide should not protect you the same way");
			} else if (menu == 2) {
				say(player, n, "No thank you");
			}
		}
	}

	// WHEN STEALING AND CAUGHT BY A MERCHANT ("Hey thats mine");
	// Delay player busy (3000); after stealing and Npc shout out to you.

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.FUR_TRADER.id();
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
}
