package com.openrsc.server.plugins.authentic.npcs.catherby;

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
import com.openrsc.server.plugins.Functions;

import java.util.ArrayList;

import static com.openrsc.server.plugins.RuneScript.*;

public class CandleMakerShop extends AbstractShop {

	private final Shop shop = new Shop(false, 1000, 100, 80, 2, new Item(ItemId.UNLIT_CANDLE.id(), 10));

	@Override
	public boolean blockTalkNpc(final Player player, final Npc npc) {
		return npc.getID() == NpcId.CANDLEMAKER.id();
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
	public void onTalkNpc(final Player player, final Npc npc) {
		if (player.getCache().hasKey("candlemaker")) {
			npcsay("Have you got any wax yet?");
			if (player.getCarriedItems().hasCatalogID(ItemId.WAX_BUCKET.id())) {
				say("Yes I have some now");
				player.getCarriedItems().remove(new Item(ItemId.WAX_BUCKET.id()));
				player.message("You exchange the wax with the candle maker for a black candle");
				give(ItemId.UNLIT_BLACK_CANDLE.id(), 1);
				player.getCache().remove("candlemaker");
			} else {
				//NOTHING HAPPENS
			}
			return;
		}

		ArrayList<String> options = new ArrayList<>();

		npcsay("Hi would you be interested in some of my fine candles");

		String questOption = "Have you got any black candles?";
		if (player.getQuestStage(Quests.MERLINS_CRYSTAL) == 3) {
			options.add(questOption);
		}

		String optionYes = "Yes please";
		options.add(optionYes);

		options.add("No thank you");

		String optionCape = "No but I am interested in your cape";
		if (Functions.config().WANT_CUSTOM_SPRITES) {
			options.add(optionCape);
		}

		String[] finalOptions = new String[options.size()];
		int option = multi(options.toArray(finalOptions));

		if (option == -1) return;
		if (options.get(option).equalsIgnoreCase(questOption)) {
			npcsay("Black candles hmm?",
				"It's very bad luck to make black candles");
			say("I can pay well for one");
			npcsay("I still don't know",
				"Tell you what, I'll supply you with a black candle",
				"If you can bring me a bucket full of wax");
			player.getCache().store("candlemaker", true);
		} else if (options.get(option).equalsIgnoreCase(optionYes)) {
			player.setAccessingShop(shop);
			ActionSender.showShop(player, shop);
		} else if (Functions.config().WANT_CUSTOM_SPRITES && options.get(option).equalsIgnoreCase(optionCape)) {
			npcsay("This is a Firemaking cape",
				"It helps me light fires that stay burning for a very long time",
				"But it isn't something we keep around anymore",
				"Everyone is so good at firemaking now");
		}
	}
}
