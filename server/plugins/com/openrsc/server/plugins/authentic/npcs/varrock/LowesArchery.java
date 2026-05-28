package com.openrsc.server.plugins.authentic.npcs.varrock;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.AbstractShop;
import com.openrsc.server.plugins.Functions;

import java.util.ArrayList;

import static com.openrsc.server.plugins.RuneScript.*;

public final class LowesArchery extends AbstractShop {

	private final Shop shop = new Shop(false, 3000, 100, 55, 1,
		new Item(ItemId.TIN_ARROWS.id(), 200),
		new Item(ItemId.COPPER_ARROWS.id(), 200),
		new Item(ItemId.BRONZE_ARROWS.id(), 200),
		new Item(ItemId.IRON_ARROWS.id(), 180),
		new Item(ItemId.STEEL_ARROWS.id(), 160),
		new Item(ItemId.MITHRIL_ARROWS.id(), 140),
		new Item(ItemId.COPPER_BOLTS.id(), 150),
		new Item(ItemId.BRONZE_BOLTS.id(), 150),
		new Item(ItemId.IRON_BOLTS.id(), 130),
		new Item(ItemId.STEEL_BOLTS.id(), 110),
		new Item(ItemId.MITHRIL_BOLTS.id(), 90),
		new Item(ItemId.TIN_ARROW_HEADS.id(), 200),
		new Item(ItemId.COPPER_ARROW_HEADS.id(), 200),
		new Item(ItemId.BRONZE_ARROW_HEADS.id(), 180),
		new Item(ItemId.IRON_ARROW_HEADS.id(), 160),
		new Item(ItemId.STEEL_ARROW_HEADS.id(), 140),
		new Item(ItemId.MITHRIL_ARROW_HEADS.id(), 120),
		new Item(ItemId.SHORTBOW.id(), 4),
		new Item(ItemId.LONGBOW.id(), 2),
		new Item(ItemId.OAK_SHORTBOW.id(), 4),
		new Item(ItemId.OAK_LONGBOW.id(), 2),
		new Item(ItemId.WILLOW_SHORTBOW.id(), 3),
		new Item(ItemId.WILLOW_LONGBOW.id(), 2),
		new Item(ItemId.MAPLE_SHORTBOW.id(), 2),
		new Item(ItemId.MAPLE_LONGBOW.id(), 2),
		new Item(ItemId.YEW_SHORTBOW.id(), 1),
		new Item(ItemId.YEW_LONGBOW.id(), 1),
		new Item(ItemId.MAGIC_SHORTBOW.id(), 1),
		new Item(ItemId.MAGIC_LONGBOW.id(), 1),
		new Item(ItemId.CROSSBOW.id(), 2),
		new Item(ItemId.OAK_CROSSBOW.id(), 2),
		new Item(ItemId.WILLOW_CROSSBOW.id(), 2),
		new Item(ItemId.MAPLE_CROSSBOW.id(), 1),
		new Item(ItemId.YEW_CROSSBOW.id(), 1),
		new Item(ItemId.MAGIC_CROSSBOW.id(), 1));

	@Override
	public boolean blockTalkNpc(final Player player, final Npc npc) {
		return !player.getConfig().WANT_OPENPK_POINTS && npc.getID() == NpcId.LOWE.id();
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
	public void onTalkNpc(final Player player, final Npc npc) {
		npcsay("Welcome to Lowe's Archery Store",
			"Do you want to see my wares?");

		ArrayList<String> options = new ArrayList<String>();
		options.add("Yes please");
		options.add("No, I prefer to bash things close up");

		if (Functions.config().WANT_CUSTOM_SPRITES) {
			options.add("I'd actually like to see what you're ware-ing!");
		}

		int option = multi(false, options.toArray(new String[0]));

		if (option == 0) {
			say("Yes Please");
			player.setAccessingShop(shop);
			ActionSender.showShop(player, shop);
		} else if (option == 1) {
			say("No, I prefer to bash things close up");
		} else if (Functions.config().WANT_CUSTOM_SPRITES && option == 2) {
			say("I'd actually like to see what you're ware-ing!");
			npcsay("Haha good one",
				"This is my Ranged cape",
				"I've seen other skilled individuals with similar-looking capes",
				"So I thought I'd make one to show off what I'm good at!");
			if (player.getSkills().getMaxStat(Skill.RANGED.id()) >= 99) {
				npcsay("Hey, it looks like you're actually pretty good at archery yourself",
					"I can make you a cape like this one if you'd like",
					"It'd only cost 99,000 coins for materials and labor",
					"You aren't just buying the cape either...",
					"...this cape actually helps improve your archery.",
					"It can help you shoot two arrows at once!");
				if (multi("Wow I'd love one", "I think I'm alright, thankyou") == 0) {
					if (ifheld(ItemId.COINS.id(), 99000)) {
						remove(ItemId.COINS.id(), 99000);
						mes("You exchange 99,000 coins for a Ranged cape");
						delay(3);
						give(ItemId.RANGED_CAPE.id(), 1);
						npcsay("I wish you well, adventurer!");
					} else {
						say("But I don't have the money right now");
						npcsay("Well, if you manage to scrape together the change I'll be here");
					}
				}
			}
		}
	}
}
