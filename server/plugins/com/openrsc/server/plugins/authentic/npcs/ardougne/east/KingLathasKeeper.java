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

import static com.openrsc.server.plugins.Functions.*;

public final class KingLathasKeeper extends AbstractShop {

	private final Shop shop = new Shop(false, 3000, 150, 50, 2,
		new Item(ItemId.TIN_ARROWS.id(), 200), new Item(ItemId.COPPER_ARROWS.id(), 200), new Item(ItemId.BRONZE_ARROWS.id(), 200),
		new Item(ItemId.IRON_ARROWS.id(), 180), new Item(ItemId.STEEL_ARROWS.id(), 160), new Item(ItemId.MITHRIL_ARROWS.id(), 140),
		new Item(ItemId.COPPER_BOLTS.id(), 150), new Item(ItemId.BRONZE_BOLTS.id(), 150), new Item(ItemId.IRON_BOLTS.id(), 130),
		new Item(ItemId.STEEL_BOLTS.id(), 110), new Item(ItemId.MITHRIL_BOLTS.id(), 90),
		new Item(ItemId.SHORTBOW.id(), 4), new Item(ItemId.LONGBOW.id(), 2), new Item(ItemId.OAK_SHORTBOW.id(), 4),
		new Item(ItemId.OAK_LONGBOW.id(), 2), new Item(ItemId.WILLOW_SHORTBOW.id(), 3), new Item(ItemId.WILLOW_LONGBOW.id(), 2),
		new Item(ItemId.MAPLE_SHORTBOW.id(), 2), new Item(ItemId.MAPLE_LONGBOW.id(), 2), new Item(ItemId.YEW_SHORTBOW.id(), 1),
		new Item(ItemId.YEW_LONGBOW.id(), 1), new Item(ItemId.MAGIC_SHORTBOW.id(), 1), new Item(ItemId.MAGIC_LONGBOW.id(), 1),
		new Item(ItemId.CROSSBOW.id(), 2), new Item(ItemId.OAK_CROSSBOW.id(), 2), new Item(ItemId.WILLOW_CROSSBOW.id(), 2),
		new Item(ItemId.MAPLE_CROSSBOW.id(), 1), new Item(ItemId.YEW_CROSSBOW.id(), 1), new Item(ItemId.MAGIC_CROSSBOW.id(), 1),
		new Item(ItemId.TIN_ARROW_HEADS.id(), 200), new Item(ItemId.COPPER_ARROW_HEADS.id(), 180), new Item(ItemId.BRONZE_ARROW_HEADS.id(), 160),
		new Item(ItemId.IRON_ARROW_HEADS.id(), 140), new Item(ItemId.STEEL_ARROW_HEADS.id(), 120), new Item(ItemId.MITHRIL_ARROW_HEADS.id(), 100),
		new Item(ItemId.TIN_AXE.id(), 5), new Item(ItemId.COPPER_AXE.id(), 5), new Item(ItemId.BRONZE_AXE.id(), 5),
		new Item(ItemId.IRON_AXE.id(), 4), new Item(ItemId.STEEL_AXE.id(), 3), new Item(ItemId.MITHRIL_AXE.id(), 2),
		new Item(ItemId.TIN_BATTLE_AXE.id(), 5), new Item(ItemId.COPPER_BATTLE_AXE.id(), 5), new Item(ItemId.BRONZE_BATTLE_AXE.id(), 4),
		new Item(ItemId.IRON_BATTLE_AXE.id(), 3), new Item(ItemId.STEEL_BATTLE_AXE.id(), 2), new Item(ItemId.MITHRIL_BATTLE_AXE.id(), 1),
		new Item(ItemId.TIN_2_HANDED_SWORD.id(), 4), new Item(ItemId.COPPER_2_HANDED_SWORD.id(), 4), new Item(ItemId.BRONZE_2_HANDED_SWORD.id(), 4),
		new Item(ItemId.IRON_2_HANDED_SWORD.id(), 3), new Item(ItemId.STEEL_2_HANDED_SWORD.id(), 2), new Item(ItemId.MITHRIL_2_HANDED_SWORD.id(), 1));

	@Override
	public void onTalkNpc(final Player player, final Npc n) {
		say(player, n, "hello");
		npcsay(player, n, "so are you looking to buy some weapons?",
			"king lathas keeps us very well stocked");
		int option = multi(player, n, "what do you have?", "no thanks");
		switch (option) {

			case 0:
				npcsay(player, n, "take a look");
				player.setAccessingShop(shop);
				ActionSender.showShop(player, shop);
				break;
		}
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.SHOP_KEEPER_TRAINING_CAMP.id();
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
