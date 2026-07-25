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

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

public class Thrander extends AbstractShop {
	private static final int REQUIRED_KEY_HALVES = 3;

	private final Shop shop = new Shop(false, 35000, 120, 55, 2,
		new Item(ItemId.TIN_PLATE_MAIL_BODY.id(), 2),
		new Item(ItemId.COPPER_PLATE_MAIL_BODY.id(), 2),
		new Item(ItemId.BRONZE_PLATE_MAIL_BODY.id(), 2),
		new Item(ItemId.IRON_PLATE_MAIL_BODY.id(), 1),
		new Item(ItemId.STEEL_PLATE_MAIL_BODY.id(), 1),
		new Item(ItemId.MITHRIL_PLATE_MAIL_BODY.id(), 1),
		new Item(ItemId.TIN_GAUNTLETS.id(), 3),
		new Item(ItemId.TIN_GREAVES.id(), 3),
		new Item(ItemId.COPPER_GAUNTLETS.id(), 3),
		new Item(ItemId.COPPER_GREAVES.id(), 3),
		new Item(ItemId.BRONZE_GAUNTLETS.id(), 3),
		new Item(ItemId.BRONZE_GREAVES.id(), 3),
		new Item(ItemId.IRON_GAUNTLETS.id(), 2),
		new Item(ItemId.IRON_GREAVES.id(), 2),
		new Item(ItemId.STEEL_GAUNTLETS.id(), 2),
		new Item(ItemId.STEEL_GREAVES.id(), 2),
		new Item(ItemId.MITHRIL_GAUNTLETS.id(), 1),
		new Item(ItemId.MITHRIL_GREAVES.id(), 1));

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
		int option = multi(player, n,
			"Do you want to trade?",
			"Can you convert crystal key halves?",
			"No thank you");
		switch (option) {
			case 0:
				npcsay(player, n, "Yes, I have a practical selection of armour");
				player.setAccessingShop(shop);
				ActionSender.showShop(player, shop);
				break;
			case 1:
				handleCrystalKeyExchange(player, n);
				break;
			default:
				break;
		}
	}

	private void handleCrystalKeyExchange(final Player player, final Npc n) {
		npcsay(
			player,
			n,
			"Crystal? Well I do have a knack for converting things, but crystal is brittle, "
				+ "I'll need 3 of a kind to cleanly swap them"
		);
		final int option = multi(player, n,
			"Here's 3 loops",
			"Here's three teeth",
			"No thanks");
		switch (option) {
			case 0:
				exchangeKeyHalves(
					player,
					n,
					ItemId.LOOP_KEY_HALF.id(),
					ItemId.TOOTH_KEY_HALF.id(),
					"loop halves",
					"Here's your tooth half"
				);
				break;
			case 1:
				exchangeKeyHalves(
					player,
					n,
					ItemId.TOOTH_KEY_HALF.id(),
					ItemId.LOOP_KEY_HALF.id(),
					"tooth halves",
					"Here's your loop half"
				);
				break;
			default:
				break;
		}
	}

	private void exchangeKeyHalves(
		final Player player,
		final Npc n,
		final int sourceId,
		final int productId,
		final String sourceName,
		final String successMessage
	) {
		if (player.getCarriedItems().getInventory().countId(sourceId) < REQUIRED_KEY_HALVES) {
			npcsay(player, n, "You don't have three " + sourceName + " for me to convert");
			return;
		}

		npcsay(player, n, "Perfect, one moment");
		delay(2);

		boolean sourceMissing = false;
		boolean exchangeCompleted = false;
		synchronized (player) {
			if (player.getCarriedItems().getInventory().countId(sourceId) < REQUIRED_KEY_HALVES
				|| player.getCarriedItems().remove(new Item(sourceId, REQUIRED_KEY_HALVES)) == -1) {
				sourceMissing = true;
			} else if (!player.getCarriedItems().getInventory().add(new Item(productId))) {
				player.getCarriedItems().getInventory().add(new Item(sourceId, REQUIRED_KEY_HALVES));
			} else {
				exchangeCompleted = true;
			}
		}
		if (sourceMissing) {
			npcsay(player, n, "It looks like you no longer have three " + sourceName);
			return;
		}
		if (!exchangeCompleted) {
			npcsay(player, n, "I couldn't complete that exchange");
			return;
		}
		npcsay(player, n, successMessage);
	}
}
