package com.openrsc.server.plugins.authentic.npcs.lumbridge;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.custom.minigames.ArmyOfObscurity;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import java.util.ArrayList;
import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public class Urhney implements TalkNpcTrigger {

	@Override
	public void onTalkNpc(final Player player, final Npc n) {
		npcsay(player, n, "Go away, I'm meditating");

		ArrayList<String> options = new ArrayList<>();
		final String sentToTalk = "Father Aereck sent me to talk to you";
		final String lostAmulet = "I've lost the amulet";
		final String thatsFriendly = "Well that's friendly";
		final String repossess = "I've come to repossess your house";
		String armyOfObscurity = "Halloween event";
		final String lostBoomstick = "I lost the Boomstick";

		if (player.getQuestStage(Quests.THE_RESTLESS_GHOST) == 1
			&& !player.getCarriedItems().hasCatalogID(ItemId.AMULET_OF_GHOSTSPEAK.id(), Optional.empty())) {
			options.add(sentToTalk);
		}
		else if (player.getQuestStage(Quests.THE_RESTLESS_GHOST) >= 2
			&& !player.getCarriedItems().hasCatalogID(ItemId.AMULET_OF_GHOSTSPEAK.id(), Optional.empty())) {
			options.add(lostAmulet);
		}
		options.add(thatsFriendly);
		options.add(repossess);

		// Halloween 2024
		if (config().ARMY_OF_OBSCURITY) {
			int stage = ArmyOfObscurity.getStage(player);
			if (stage >= ArmyOfObscurity.STAGE_NOT_STARTED
				&& stage <= ArmyOfObscurity.STAGE_ATTEMPTED_TO_TAKE_BOOK) {
				armyOfObscurity = "Who is this guy in your house?";
				options.add(armyOfObscurity);
			} else if (stage == ArmyOfObscurity.STAGE_TOLD_ASH_WORDS_BAD) {
				armyOfObscurity = "Do you know how to get the book";
				options.add(armyOfObscurity);
			} else if (stage == ArmyOfObscurity.STAGE_GOT_NEW_WORD) {
				armyOfObscurity = "About the enchantment-breaking spell";
				options.add(armyOfObscurity);
			} else if (stage == ArmyOfObscurity.STAGE_COMPLETED) {
				armyOfObscurity = "Where did Ash go?";
				options.add(armyOfObscurity);
			}
		}

		// Recover Boomstick if lost
		if (ArmyOfObscurity.getStage(player) == ArmyOfObscurity.STAGE_COMPLETED 
			&& !player.getCarriedItems().hasCatalogID(ItemId.BOOMSTICK.id())) {
			options.add(lostBoomstick);
		}

		String[] finalOptions = new String[options.size()];
		int option = multi(player, n, options.toArray(finalOptions));

		if (option == -1) {
			return;
		} else if (options.get(option).equals(sentToTalk)) {
			beforeAmuletDialog(player, n);
		} else if (options.get(option).equals(lostAmulet)) {
			mes("Father Urhney sighs");
			delay(3);

			npcsay(player, n, "How careless can you get",
				"Those things aren't easy to come by you know",
				"It's a good job I've got a spare");
			give(player, ItemId.AMULET_OF_GHOSTSPEAK.id(), 1);
			mes("Father Urhney hands you an amulet");
			delay(3);
			npcsay(player, n, "Be more careful this time");
			say(player, n, "Ok I'll try to be");
		} else if (options.get(option).equals(thatsFriendly)) {
			npcsay(player, n, "I said go away!");
			say(player, n, "Ok, ok");
		} else if (options.get(option).equals(repossess)) {
			repossessDialog(player, n);
		} else if (config().ARMY_OF_OBSCURITY && options.get(option).equals(armyOfObscurity)) {
			ArmyOfObscurity.fatherUrhneyDialogue(player, n);
		} else if (options.get(option).equals(lostBoomstick)) {
			ArmyOfObscurity.recoverBoomstick(player, n);
		}
	}

	private void repossessDialog(Player player, Npc n) {
		npcsay(player, n, "Under what grounds?");
		int option = multi(player, n,
			"Repeated failure on mortgage payments",
			"I don't know, I just wanted this house"
		);
		if (option == 0) {
			npcsay(player, n, "I don't have a mortgage", "I built this house myself");
			say(player, n, "Sorry I must have got the wrong address", "All the houses look the same around here");
		}
		else if (option == 1) {
			npcsay(player, n, "Oh go away and stop wasting my time");
		}
	}

	private void beforeAmuletDialog(Player player, Npc n) {
		npcsay(player, n, "I suppose I'd better talk to you then",
			"What problems has he got himself into this time?");
		int option = multi(player, n,
			"He's got a ghost haunting his graveyard",
			"You mean he gets himself into lots of problems?"
		);

		if (option == 0) {
			npcsay(player,
				n,
				"Oh the silly fool",
				"I leave town for just five months",
				"and already he can't manage",
				"Sigh",
				"Well I can't go back and exorcise it",
				"I vowed not to leave this place",
				"Until I had done a full two years of prayer and meditation",
				"Tell you what I can do though",
				"Take this amulet");
			mes("Father Urhney hands you an amulet");
			delay(3);
			give(player, ItemId.AMULET_OF_GHOSTSPEAK.id(), 1); // AMULET OF GHOST SPEAK.
			npcsay(player,
				n,
				"It is an amulet of Ghostspeak",
				"So called because when you wear it you can speak to ghosts",
				"A lot of ghosts are doomed to be ghosts",
				"Because they have left some task uncompleted",
				"Maybe if you know what this task is",
				"You can get rid of the ghost",
				"I'm not making any guarantees mind you",
				"But it is the best I can do right now");
			say(player, n,
				"Thank you, I'll give it a try");
			player.updateQuestStage(Quests.THE_RESTLESS_GHOST, 2);
		}

		else if (option == 1) {
			npcsay(player,
				n,
				"Yeah. For example when we were trainee priests",
				"He kept on getting stuck up bell ropes",
				"Anyway I don't have time for chitchat",
				"What's his problem this time?");
			say(player, n,
				"He's got a ghost haunting his graveyard");
			npcsay(player,
				n,
				"Oh the silly fool",
				"I leave town for just five months",
				"and already he can't manage",
				"Sigh",
				"Well I can't go back and exorcise it",
				"I vowed not to leave this place",
				"Until I had done a full two years of prayer and meditation",
				"Tell you what I can do though",
				"Take this amulet");
			mes("Father Urhney hands you an amulet");
			delay(3);
			give(player, ItemId.AMULET_OF_GHOSTSPEAK.id(), 1); // AMULET OF GHOST SPEAK.
			npcsay(player,
				n,
				"It is an amulet of Ghostspeak",
				"So called because when you wear it you can speak to ghosts",
				"A lot of ghosts are doomed to be ghosts",
				"Because they have left some task uncompleted",
				"Maybe if you know what this task is",
				"You can get rid of the ghost",
				"I'm not making any guarantees mind you",
				"But it is the best I can do right now");
			say(player, n,
				"Thank you, I'll give it a try");
			player.updateQuestStage(Quests.THE_RESTLESS_GHOST, 2);
		}
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.URHNEY.id();
	}

}
