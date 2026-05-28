package com.openrsc.server.plugins.authentic.npcs.alkharid;

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

public class Tanner extends AbstractShop {
	private final Shop shop = new Shop(false, 25000, 120, 55, 2,
		new Item(ItemId.COW_HIDE_COIF.id(), 2),
		new Item(ItemId.COW_HIDE_GLOVES.id(), 2),
		new Item(ItemId.COW_HIDE_BOOTS.id(), 2),
		new Item(ItemId.COW_HIDE_CHAPS.id(), 2),
		new Item(ItemId.COW_HIDE_CUIRASS.id(), 2),
		new Item(ItemId.GOBLIN_HIDE_COIF.id(), 1),
		new Item(ItemId.GOBLIN_HIDE_GLOVES.id(), 1),
		new Item(ItemId.GOBLIN_HIDE_BOOTS.id(), 1),
		new Item(ItemId.GOBLIN_HIDE_CHAPS.id(), 1),
		new Item(ItemId.GOBLIN_HIDE_CUIRASS.id(), 1));

	@Override
	public void onTalkNpc(Player player, final Npc n) {
		npcsay(player, n, "Greetings friend I'm a worker of hides and leather");
		int option = multi(player, n, false, //do not send over
			"What leather armour do you sell?",
			"How do I tan hides now?",
			"How does hide armour work?");

		switch (option) {
			case 0:
				say(player, n, "What leather armour do you sell?");
				npcsay(player, n, "Mostly cow and goblin hide pieces",
					"Good enough to get a new hunter started");
				player.setAccessingShop(shop);
				ActionSender.showShop(player, shop);
				break;
			case 1:
				say(player, n, "How do I tan hides now?");
				npcsay(player, n, "Use a tanning rack and work the hides yourself",
					"It is proper Crafting work now, not a simple swap");
				break;
			case 2:
				say(player, n, "How does hide armour work?");
				npcsay(player, n, "A hide keeps some of the beast's nature",
					"Tan it into leather, then craft it into armour",
					"If the creature resisted magic, its armour should lean that way too");
				break;
		}
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.TANNER.id();
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
}
