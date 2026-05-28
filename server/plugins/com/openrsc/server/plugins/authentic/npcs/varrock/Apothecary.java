package com.openrsc.server.plugins.authentic.npcs.varrock;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.custom.minigames.ABoneToPick;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.ArrayList;
import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public final class Apothecary implements
	TalkNpcTrigger {

	final int[] potionIds = {
		ItemId.FULL_ATTACK_POTION.id(),
		ItemId.TWO_ATTACK_POTION.id(),
		ItemId.ONE_ATTACK_POTION.id(),
		ItemId.FULL_CURE_POISON_POTION.id(),
		ItemId.TWO_CURE_POISON_POTION.id(),
		ItemId.ONE_CURE_POISON_POTION.id(),
		ItemId.FULL_STRENGTH_POTION.id(),
		ItemId.THREE_STRENGTH_POTION.id(),
		ItemId.TWO_STRENGTH_POTION.id(),
		ItemId.ONE_STRENGTH_POTION.id(),
		ItemId.FULL_STAT_RESTORATION_POTION.id(),
		ItemId.TWO_STAT_RESTORATION_POTION.id(),
		ItemId.ONE_STAT_RESTORATION_POTION.id(),
		ItemId.FULL_DEFENSE_POTION.id(),
		ItemId.TWO_DEFENSE_POTION.id(),
		ItemId.ONE_DEFENSE_POTION.id(),
		ItemId.FULL_RESTORE_PRAYER_POTION.id(),
		ItemId.TWO_RESTORE_PRAYER_POTION.id(),
		ItemId.ONE_RESTORE_PRAYER_POTION.id(),
		ItemId.FULL_SUPER_ATTACK_POTION.id(),
		ItemId.TWO_SUPER_ATTACK_POTION.id(),
		ItemId.ONE_SUPER_ATTACK_POTION.id(),
		ItemId.FULL_POISON_ANTIDOTE.id(),
		ItemId.TWO_POISON_ANTIDOTE.id(),
		ItemId.ONE_POISON_ANTIDOTE.id(),
		ItemId.FULL_FISHING_POTION.id(),
		ItemId.TWO_FISHING_POTION.id(),
		ItemId.ONE_FISHING_POTION.id(),
		ItemId.FULL_SUPER_STRENGTH_POTION.id(),
		ItemId.TWO_SUPER_STRENGTH_POTION.id(),
		ItemId.ONE_SUPER_STRENGTH_POTION.id(),
		ItemId.WEAPON_POISON.id(),
		ItemId.FULL_SUPER_DEFENSE_POTION.id(),
		ItemId.TWO_SUPER_DEFENSE_POTION.id(),
		ItemId.ONE_SUPER_DEFENSE_POTION.id(),
		ItemId.FULL_POTION_OF_ZAMORAK.id(),
		ItemId.TWO_POTION_OF_ZAMORAK.id(),
		ItemId.ONE_POTION_OF_ZAMORAK.id(),
		ItemId.FULL_MAGIC_POTION.id(),
		ItemId.TWO_MAGIC_POTION.id(),
		ItemId.ONE_MAGIC_POTION.id(),
		ItemId.FULL_POTION_OF_SARADOMIN.id(),
		ItemId.TWO_POTION_OF_SARADOMIN.id(),
		ItemId.ONE_POTION_OF_SARADOMIN.id(),
	};

	@Override
	public void onTalkNpc(Player player, final Npc npc) {
		if (player.getQuestStage(Quests.ROMEO_N_JULIET) == 4) {
			say(player, npc, "Apothecary. Father Lawrence sent me",
				"I need some Cadava potion to help Romeo and Juliet");
			npcsay(player, npc, "Cadava potion. Its pretty nasty. And hard to make",
				"Wing of Rat, Tail of frog. Ear of snake and horn of dog",
				"I have all that, but I need some cadavaberries",
				"You will have to find them while I get the rest ready",
				"Bring them here when you have them. But be careful. They are nasty");
			player.updateQuestStage(Quests.ROMEO_N_JULIET, 5);
			return;
		} else if (player.getQuestStage(Quests.ROMEO_N_JULIET) == 5) {
			if (!player.getCarriedItems().hasCatalogID(ItemId.CADAVABERRIES.id())) {
				npcsay(player, npc, "Keep searching for the berries",
					"They are needed for the potion");
			} else {
				npcsay(player, npc, "Well done. You have the berries");
				mes("You hand over the berries");
				delay(3);
				player.getCarriedItems().remove(new Item(ItemId.CADAVABERRIES.id()));
				player.message("Which the apothecary shakes up in vial of strange liquid");
				npcsay(player, npc, "Here is what you need");
				player.message("The apothecary gives you a Cadava potion");
				player.getCarriedItems().getInventory().add(new Item(ItemId.CADAVA.id()));
				player.updateQuestStage(Quests.ROMEO_N_JULIET, 6);
			}
			return;
		}
		npcsay(player, npc, "I am the apothecary", "I have potions to brew. Do you need anything specific?");

		/*if (!getServer().getConfig().WANT_EXPERIENCE_ELIXIRS)
			option = showMenu(p, n, "Can you make a strength potion?",
				"Do you know a potion to make hair fall out?",
				"Have you got any good potions to give way?");
		else
			option = showMenu(p, n, "Can you make a strength potion?",
				"Do you know a potion to make hair fall out?",
				"Have you got any good potions to give way?",
				"Do you have any experience elixir?");*/

		// Disabled experience elixir due to not being functional at this time

		ArrayList<String> options = new ArrayList<String>();
		options.add("Do you know a potion to make hair fall out?");
		options.add("Have you got any good potions to give way?");

		if (config().A_BONE_TO_PICK
			&& ABoneToPick.getStage(player) == ABoneToPick.TALKED_TO_ODDENSTEIN
			&& !ifheld(player, ItemId.CHIPPED_PESTLE_AND_MORTAR.id())) {
			options.add("Can I have a pestle and mortar?");
		}

		else if (config().WANT_APOTHECARY_QOL && !player.getQolOptOut()) {
			options.add("Could you empty these vials?");
			options.add("Could you fill these vials with water?");
		}

		int option = multi(player, npc, options.toArray(new String[options.size()]));

		if (option == 0) {
			npcsay(player, npc, "I do indeed. I gave it to my mother. That's why I now live alone");
		} else if (option == 1) {
			if (player.getCarriedItems().hasCatalogID(ItemId.POTION.id(), Optional.of(false))) {
				npcsay(player, npc, "Only that spot cream. Hope you enjoy it",
					"Yes, ok. Try this potion");
				give(player, ItemId.POTION.id(), 1);
			} else {
				int chance = DataConversions.random(0, 2);
				if (chance < 2) {
					npcsay(player, npc, "Yes, ok. Try this potion");
					give(player, ItemId.POTION.id(), 1);
				} else {
					npcsay(player, npc, "Sorry, charity is not my strong point");
				}
			}
		} else if (option == 2 && config().A_BONE_TO_PICK && ABoneToPick.getStage(player) == ABoneToPick.TALKED_TO_ODDENSTEIN
			&& !ifheld(player, ItemId.CHIPPED_PESTLE_AND_MORTAR.id())) {
			ABoneToPick.apothecaryDialogue(player, npc);
		} else if (option == 2 && !player.getQolOptOut() && player.getConfig().WANT_CUSTOM_SPRITES) { // Empty vials
			emptyVials(player, npc);
		} else if (option == 3 && !player.getQolOptOut() && player.getConfig().WANT_CUSTOM_SPRITES) { // Fill with water
			fillWithWater(player, npc);
		}
		/* } else if (option == 3 && config().WANT_EXPERIENCE_ELIXIRS) {
			npcsay(player, n, "Yes, it's my most mysterious and special elixir",
				"It has a strange taste and sure does give you a rush",
				"I would know..",
				"I sell it for 5,000gp");
			int menu = multi(player, n, "Yes please", "No thankyou");
			if (menu == 0) {
				long lastElixir = 0;
				if (player.getCache().hasKey("buy_elixir")) {
					lastElixir = player.getCache().getLong("buy_elixir");
				}
				if (System.currentTimeMillis() - lastElixir < 24 * 60 * 60 * 1000) {
					npcsay(player, n, "Wait.. it's you, I recently made an elixir for you",
						"I don't want to poison my customers",
						"You'll need to wait before I make you a new one");
					int time = (int) (86400 - ((System.currentTimeMillis() - lastElixir) / 1000));
					player.message("You need to wait: " + DataConversions.getDateFromMsec(time * 1000));
					return;
				}
				if (ifheld(player, ItemId.COINS.id(), 5000)) {
					say(player, n, "I have the 5,000 gold with me");
					player.message("you give Apothecary 5,000 gold");
					player.getCarriedItems().remove(new Item(ItemId.COINS.id(), 5000));
					mes("Apothecary: starts brewing and fixes to a elixir");
					delay(3);
					player.message("Apothecary gives you a mysterious experience elixir.");
					//TODO: Determine if elixir will be added and indexed ID if so
					//addItem(p, ItemId.EXPERIENCE_ELIXIR.id(), 1);
					player.getCache().store("buy_elixir", System.currentTimeMillis());
				} else {
					say(player, n, "Oops, I don't have enough coins");
					npcsay(player, n, "Ok. I need my money, the ingredients are hard to find");
				}
			}
		} */
	}

	private void emptyVials(Player player, Npc npc) {
		int costPerVial = 50;

		npcsay(player, npc,
			"Yes, but I'll have to charge you " + costPerVial + " gold per potion for proper disposal",
			"And I can only dispose of completed potions");

		// Get all the potions the player is holding
		int vials = 0;
		for (int potion : potionIds) {
			// Counts both noted and unnoted
			vials += player.getCarriedItems().getInventory().countId(potion, Optional.empty());
		}

		if (vials <= 0) return;

		int cost = vials * costPerVial;
		int heldGp = player.getCarriedItems().getInventory().countId(ItemId.COINS.id());

		npcsay(player, npc,
			"Since you're holding " + vials + " potions, that will cost " + cost + " gold");

		if (heldGp >= cost) {
			npcsay(player, npc, "Would you like me to empty your vials for you?");
			if (multi(player, npc, "Yes please", "No thankyou") == 0) {
				for (int potion : potionIds) {

					// Break out if the player has no more potions or gold.
					if (vials <= 0) break;
					if (player.getCarriedItems().getInventory().countId(ItemId.COINS.id()) <= 0) return;

					// Remove both noted and unnoted versions of the potion
					int notedCount = player.getCarriedItems().getInventory().countId(potion, Optional.of(true));
					int unnotedCount = player.getCarriedItems().getInventory().countId(potion, Optional.of(false));
					int totalRemoved = notedCount + unnotedCount;

					// Break out if they don't have any of this potion
					if (totalRemoved == 0)
						continue;

					if (notedCount > 0)
						player.getCarriedItems().remove(new Item(potion, notedCount, true));

					if (unnotedCount > 0) {
						for (int i = 0; i < unnotedCount; ++i) {
							player.getCarriedItems().remove(new Item(potion, 1, false));
						}
					}

					// Subtract form the total
					vials -= totalRemoved;

					// Subtract gp
					player.getCarriedItems().remove(new Item(ItemId.COINS.id(), totalRemoved * costPerVial));

					// Give vials
					player.getCarriedItems().getInventory().add(new Item(ItemId.EMPTY_VIAL.id(), totalRemoved, true));
				}
				mes("You hand the potions and gold to the apothecary");
				delay(3);
				mes("He takes the potions and empties the contents into a large container");
				delay(3);
				mes("He hands you the newly-emptied vials");
				delay(3);
				npcsay(player, npc, "There you are");
			}
		} else {
			npcsay(player, npc,
				"Unfortunately, it looks like you don't have enough gold to cover the costs",
				"Feel free to come back when you have more gold or less potions");
		}
	}

	private void fillWithWater(Player player, Npc npc) {
		int costPerVial = 50;
		npcsay(player, npc, "Yes, but I'll have to charge you "
			+ costPerVial + " gold per vial to cover my water bill");

		// Get all the vials the player is holding
		int notedCount = player.getCarriedItems().getInventory().countId(ItemId.EMPTY_VIAL.id(), Optional.of(true));
		int unnotedCount = player.getCarriedItems().getInventory().countId(ItemId.EMPTY_VIAL.id(), Optional.of(false));
		int totalVials = notedCount + unnotedCount;

		if (totalVials <= 0) return;

		int cost = totalVials * costPerVial;
		int heldGp = player.getCarriedItems().getInventory().countId(ItemId.COINS.id());

		npcsay(player, npc,
			"Since you're holding " + totalVials + " vials, that will cost " + cost + " gold");

		if (heldGp >= cost) {
			npcsay(player, npc, "Would you like me to fill your vials for you?");
			if (multi(player, npc, "Yes please", "No thank you") == 0) {
				// Remove both noted and unnoted versions of the vials
				if (notedCount > 0)
					player.getCarriedItems().remove(new Item(ItemId.EMPTY_VIAL.id(), notedCount, true));

				if (unnotedCount > 0) {
					for (int i = 0; i < unnotedCount; ++i) {
						player.getCarriedItems().remove(new Item(ItemId.EMPTY_VIAL.id(), 1, false));
					}
				}

				// Subtract gp
				player.getCarriedItems().remove(new Item(ItemId.COINS.id(), totalVials * costPerVial));

				// Give vials
				player.getCarriedItems().getInventory().add(new Item(ItemId.VIAL.id(), totalVials, true));

				// Let player know stuff happened
				mes("You hand the vials and gold to the apothecary");
				delay(3);
				mes("He takes the vials and fills them with water");
				delay(3);
				mes("He hands you the newly-filled vials");
				delay(3);
				npcsay(player, npc, "There you are");
			}
		} else {
			npcsay(player, npc,
				"Unfortunately, it looks like you don't have enough gold to cover the costs",
				"Feel free to come back when you have more gold or less vials");
		}
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.APOTHECARY.id();
	}
}
