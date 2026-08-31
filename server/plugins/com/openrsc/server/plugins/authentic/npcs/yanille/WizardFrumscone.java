package com.openrsc.server.plugins.authentic.npcs.yanille;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.MageGuildStoneCredits;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class WizardFrumscone implements TalkNpcTrigger, OpNpcTrigger {
	private static final String CONVERT_OPTION = "Convert my noted Stone";
	private static final String MAGIC_CAPE_OPTION = "Does your cape have any magical properties?";
	private static final String LEAVE_OPTION = "Nothing right now";

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.WIZARD_FRUMSCONE.id();
	}

	@Override
	public boolean blockOpNpc(Player player, Npc n, String command) {
		return n.getID() == NpcId.WIZARD_FRUMSCONE.id()
			&& "Convert Stone".equalsIgnoreCase(command);
	}

	@Override
	public void onOpNpc(Player player, Npc n, String command) {
		openStoneConversion(player, n);
	}

	@Override
	public void onTalkNpc(Player player, Npc n) {
		npcsay(player, n,
			"My Magic Zombies make excellent combat practice",
			"For each one you defeat down here, I'll prepare one Stone",
			"You must bring me the Stone in noted form");

		boolean canBuyMagicCape = config().WANT_CUSTOM_SPRITES
			&& getMaxLevel(player, Skill.MAGIC.id()) >= 99;
		int option = canBuyMagicCape
			? multi(player, n, MAGIC_CAPE_OPTION, CONVERT_OPTION, LEAVE_OPTION)
			: multi(player, n, CONVERT_OPTION, LEAVE_OPTION);
		if (option < 0) {
			return;
		}
		if (canBuyMagicCape && option == 0) {
			handleMagicCape(player, n);
		} else if ((!canBuyMagicCape && option == 0) || (canBuyMagicCape && option == 1)) {
			openStoneConversion(player, n);
		}
	}

	private void openStoneConversion(Player player, Npc n) {
		int credits = MageGuildStoneCredits.getCredits(player);
		int notedStone = player.getCarriedItems().getInventory()
			.countId(ItemId.RUNE_STONE.id(), Optional.of(true));
		if (credits <= 0) {
			npcsay(player, n, "You have no Magic Zombie kill credits yet");
			return;
		}
		if (notedStone <= 0) {
			npcsay(player, n, "Bring me noted Stone to use your " + creditLabel(credits));
			return;
		}

		npcsay(player, n, "You have " + creditLabel(credits),
			"How many noted Stone should I prepare?");
		int option = multi(player, n, "Convert 1", "Convert 5", "Convert all I can", LEAVE_OPTION);
		int requested;
		switch (option) {
			case 0:
				requested = 1;
				break;
			case 1:
				requested = 5;
				break;
			case 2:
				requested = Math.min(credits, notedStone);
				break;
			default:
				return;
		}
		convertStone(player, n, requested);
	}

	private void convertStone(Player player, Npc n, int requested) {
		Inventory inventory = player.getCarriedItems().getInventory();
		int credits = MageGuildStoneCredits.getCredits(player);
		int notedStone = inventory.countId(ItemId.RUNE_STONE.id(), Optional.of(true));
		int quantity = Math.min(requested, Math.min(credits, notedStone));
		if (quantity <= 0) {
			npcsay(player, n, "You don't have both a credit and noted Stone for that");
			return;
		}

		int usableSlots = inventory.getFreeSlots();
		if (quantity == notedStone) {
			usableSlots++;
		}
		if (quantity > usableSlots) {
			npcsay(player, n, "You need " + quantity + " free inventory spaces",
				"The noted Stone only frees its slot when the whole stack is used");
			return;
		}

		Item notedInput = new Item(ItemId.RUNE_STONE.id(), quantity, true);
		if (player.getCarriedItems().remove(notedInput) == -1) {
			npcsay(player, n, "I couldn't take that noted Stone safely");
			return;
		}

		int added = 0;
		for (; added < quantity; added++) {
			if (!inventory.add(new Item(ItemId.RUNE_STONE.id(), 1, false))) {
				break;
			}
		}
		if (added != quantity || !MageGuildStoneCredits.spendCredits(player, quantity)) {
			for (int removed = 0; removed < added; removed++) {
				player.getCarriedItems().remove(new Item(ItemId.RUNE_STONE.id(), 1, false));
			}
			inventory.add(notedInput);
			npcsay(player, n, "The exchange could not be completed, so nothing was spent");
			return;
		}

		npcsay(player, n, "I prepared " + quantity + " Stone for you",
			"You have " + creditLabel(MageGuildStoneCredits.getCredits(player)) + " left");
	}

	private String creditLabel(int credits) {
		return credits + " Magic Zombie kill credit" + (credits == 1 ? "" : "s");
	}

	private void handleMagicCape(Player player, Npc n) {
		npcsay(player, n, "Yes it does",
			"Only masters of magic can harness its power",
			"It seems that you are ready for such power",
			"It will only cost you 99,000 coins.");
		if (multi(player, n, "I am ready", "I am not ready") == 0) {
			if (player.getCarriedItems().getInventory().countId(ItemId.COINS.id()) >= 99000) {
				mes("Wizard Frumscone takes your coins");
				delay(3);
				if (player.getCarriedItems().remove(new Item(ItemId.COINS.id(), 99000)) > -1) {
					mes("And hands you a Magic cape");
					delay(3);
					give(player, ItemId.MAGIC_CAPE.id(), 1);
					npcsay(player, n, "You have now been bestowed with great power",
						"This cape will allow you to cast some spells without using runes");
				}
			} else {
				npcsay(player, n, "You do not have enough coins to unlock your full power");
			}
		}
	}
}
