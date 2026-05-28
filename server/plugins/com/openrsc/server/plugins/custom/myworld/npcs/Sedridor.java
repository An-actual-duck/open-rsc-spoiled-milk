package com.openrsc.server.plugins.custom.myworld.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.custom.myworld.quests.free.PeelingTheOnion;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import java.util.ArrayList;

import static com.openrsc.server.plugins.Functions.*;

public class Sedridor implements TalkNpcTrigger {

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (handlePeelingTheOnion(player, npc)) {
			return;
		}

		npcsay(player,npc,"Welcome, adventurer, to the world-renowned Wizards' Tower",
			"How many I help you?");

		ArrayList<String> menu = new ArrayList<>();
		menu.add("Nothing, thanks. I'm just looking around");
		menu.add("What are you doing down here?");

		if (config().WANT_CUSTOM_QUESTS) {
			switch(player.getQuestStage(Quests.PEELING_THE_ONION)) {
				case PeelingTheOnion.STATE_NOT_BEGUN:
					menu.add("Do you have anything you need doing?");
					break;
				case PeelingTheOnion.STATE_STARTED_QUEST_WITH_KRESH:
					menu.add("Have you been sending people to bother an ogre?");
					break;
				case PeelingTheOnion.STATE_STARTED_QUEST_WITH_SEDRIDOR:
				case PeelingTheOnion.STATE_SEDRIDOR_SUGGESTED_YOU_VISIT_MAKE_OVER_MAGE:
					menu.add("What was I supposed to do again?");
					break;
				case PeelingTheOnion.STATE_STARTED_QUEST_WITH_SEDRIDOR_CONFRONTED_KRESH:
					menu.add("I've been to see the ogre");
					break;
				case PeelingTheOnion.STATE_PLAYER_CONSIDERS_OGRE:
					menu.add("I've reconsidered and I'm ready to become an ogre...");
					break;
			}
		}

		int choice = multi(player,npc, menu.toArray(new String[menu.size()]));
		if (choice <= 0) return;

		if (choice == 1) {
			npcsay(player, npc,
				"Here in the cellar of the Wizards' Tower",
				"we search through the remains of the old tower",
				"for anything the flames did not destroy.",
				"The work is slow, but magical history is not something",
				"to be handled carelessly.");
		} else {
			PeelingTheOnion.sedridorDialogue(player, npc);
		}
	}

	private boolean handlePeelingTheOnion(Player player, Npc npc) {
		if (config().WANT_CUSTOM_QUESTS && player.getQuestStage(Quests.PEELING_THE_ONION) >= PeelingTheOnion.STATE_A_NEW_OGRE) {
			PeelingTheOnion.sedridorDialogue(player, npc);
			return true;
		}
		if (player.getCache().hasKey("sedridor_post_kresh_quest_dialogue")) {
			player.getCache().remove("sedridor_post_kresh_quest_dialogue");
			PeelingTheOnion.sedridorDialogue(player, npc);
			return true;
		}
		return false;
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return npc.getID() == NpcId.SEDRIDOR.id();
	}
}
