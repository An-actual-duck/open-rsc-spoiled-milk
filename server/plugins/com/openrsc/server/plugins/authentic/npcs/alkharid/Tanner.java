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
import com.openrsc.server.util.rsc.MessageType;

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
	private final Shop masterShop = new Shop(false, 35000, 120, 55, 2,
		new Item(ItemId.WOLF_LEATHER.id(), 10),
		new Item(ItemId.HELLHOUND_LEATHER.id(), 10),
		new Item(ItemId.CURED_SPIDER_CARAPACE.id(), 10),
		new Item(ItemId.CURED_MAGIC_SPIDER_CARAPACE.id(), 10),
		new Item(ItemId.CURED_SCORPION_CARAPACE.id(), 10),
		new Item(ItemId.THREAD.id(), 100),
		new Item(ItemId.NEEDLE.id(), 10));

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
				if (isMasterTanner(n)) {
					npcsay(player, n, "Only the best leather, of course");
				} else {
					npcsay(player, n, "Mostly cow and goblin hide pieces",
						"Good enough to get a new hunter started");
				}
				openShop(player, n);
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
		return n.getID() == NpcId.TANNER.id() || isMasterTanner(n);
	}

	@Override
	public void onOpNpc(Player player, Npc n, String command) {
		Npc storeOwner = player.getWorld().getNpc(n.getID(),
			player.getX() - 2, player.getX() + 2,
			player.getY() - 2, player.getY() + 2);
		if (storeOwner == null) return;
		if (command.equalsIgnoreCase("Trade") && config().RIGHT_CLICK_TRADE) {
			if (!player.getQolOptOut()) {
				openShop(player, n);
			} else {
				player.playerServerMessage(MessageType.QUEST, "Right click trading is a QoL feature which you are opted out of.");
				player.playerServerMessage(MessageType.QUEST, "Consider using an original RSC client so that you don't see the option.");
			}
		}
	}

	@Override
	public Shop[] getShops(World world) {
		return new Shop[]{shop, masterShop};
	}

	@Override
	public boolean isMembers() {
		return false;
	}

	@Override
	public Shop getShop() {
		return shop;
	}

	private boolean isMasterTanner(Npc n) {
		return n.getID() == NpcId.MASTER_TANNER.id();
	}

	private Shop getShop(Npc n) {
		return isMasterTanner(n) ? masterShop : shop;
	}

	private void openShop(Player player, Npc n) {
		Shop selectedShop = getShop(n);
		player.setAccessingShop(selectedShop);
		ActionSender.showShop(player, selectedShop);
	}
}
