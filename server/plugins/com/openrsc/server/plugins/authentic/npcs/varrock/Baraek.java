package com.openrsc.server.plugins.authentic.npcs.varrock;

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

import java.util.ArrayList;

import static com.openrsc.server.plugins.Functions.*;

public final class Baraek extends AbstractShop {
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
	public void onTalkNpc(final Player player, final Npc n) {
		int menu;
		ArrayList<String> options = new ArrayList<>();
		int start;
		start = 0;
		if (canGetInfoGang(player)) {
			options.add("Can you tell me where I can find the phoenix gang?");
		} else {
			start = 1; // menu start at 1
		}
		options.add("What leather armour do you sell?");
		options.add("Hello. I am in search of a quest");
		String[] finalOptions = new String[options.size()];
		menu = multi(player, n, false, //do not send over
			options.toArray(finalOptions));

		if (menu >= 0) {
			menu += start;
			baraekDialogue(player, n, menu);
		}
	}

	private void baraekDialogue(Player player, Npc n, int chosenOption) {
		if (chosenOption == 0) {
			say(player, n, "Can you tell me where I can find the phoenix gang?");
			npcsay(player, n, "Sh Sh, not so loud",
				"You don't want to get me in trouble");
			say(player, n, "So do you know where they are?");
			npcsay(player, n, "I may do",
				"Though I don't want to get into trouble for revealing their hideout",
				"Now if I was say 20 gold coins richer",
				"I may happen to be more inclined to take that sort of risk");
			int sub_menu = multi(player, n, "Okay have 20 gold coins",
				"No I don't like things like bribery",
				"Yes I'd like to be 20 gold coins richer too");
			if (sub_menu == 0) {
				if (!ifheld(player, ItemId.COINS.id(), 20)) {
					say(player, n, "Oops. I don't have 20 coins. Silly me.");
				} else {
					player.getCarriedItems().remove(new Item(ItemId.COINS.id(), 20));
					npcsay(player, n,
						"Cheers",
						"Ok to get to the gang hideout",
						"After entering Varrock through the south gate",
						"If you take the first turning east",
						"Somewhere along there is an alleyway to the south",
						"The door at the end of there is the entrance to the phoenix gang",
						"They're operating there under the name of the VTAM corporation",
						"Be careful",
						"The phoenix gang ain't the types to be messed with");
					say(player, n, "Thanks");
					if (player.getQuestStage(Quests.SHIELD_OF_ARRAV) == 2) {
						player.updateQuestStage(Quests.SHIELD_OF_ARRAV, 3);
					}
				}
			} else if (sub_menu == 1) {
				npcsay(player, n, "Heh, if you wanna deal with the phoenix gang",
					"They're involved in much worse than a bit of bribery");
			} else if (sub_menu == 2) {
				//nothing
			}
		} else if (chosenOption == 1) {
			say(player, n, "What leather armour do you sell?");
			npcsay(player, n, "I keep bear and unicorn hide pieces",
				"Better than common leather, but still light enough to move in");
			player.setAccessingShop(shop);
			ActionSender.showShop(player, shop);
		} else if (chosenOption == 2) {
			say(player, n, "Hello I am in search of a quest");
			npcsay(player, n,
				"Sorry kiddo, I sell armour, not adventures");
		}
	}

	private boolean canGetInfoGang(Player player) {
		return player.getQuestStage(Quests.SHIELD_OF_ARRAV) == 2
				|| (player.getQuestStage(Quests.SHIELD_OF_ARRAV) == 3 && !player.getCache().hasKey("arrav_mission"));
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.BARAEK.id();
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
