package com.openrsc.server.plugins.authentic.npcs.tutorial;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.*;

import com.openrsc.server.constants.NpcId;

public class FatigueExpert implements TalkNpcTrigger {
	/**
	 * Tutorial island fatigue expert
	 */
	@Override
	public void onTalkNpc(Player player, Npc n) {
		if(player.getCache().hasKey("tutorial") && player.getCache().getInt("tutorial") <= 85) {
			if (config().WANT_FATIGUE) {
				say(player, n, "Hi I'm feeling a little tired after all this learning");
				npcsay(player, n, "Yes when you use your skills you will slowly get fatigued",
					"If you look on your stats menu you will see a fatigue stat",
					"When your fatigue reaches 100 percent then you will be very tired",
					"You won't be able to concentrate enough to gain experience in your skills",
					"To reduce your fatigue you will need to go to sleep",
					"Click on the bed to go sleep",
					"Then follow the instructions to wake up",
					"When you have done that talk to me again");
			} else {
				mes(n, "You look at the Fatigue expert but he says nothing");
				delay(3);
				say(player, n, "Hi");
				npcsay(player, n, "Hi");
				say(player, n, "...so what is fatigue?");
				npcsay(player, n, "I don't know");
				say(player, n, "But aren't you the fatigue expert?");
				npcsay(player, n, "I guess I am");
				say(player, n, "Then tell me about it!");
				npcsay(player, n, "I don't know what that is!",
					"Oh I know",
					"Actually, you don't need to worry about it here",
					"Keep going and enjoy your adventure");
			}
			player.getCache().set("tutorial", 85);
		} else if(player.getCache().hasKey("tutorial") && player.getCache().getInt("tutorial") == 86) {
			if (config().WANT_FATIGUE) {
				npcsay(player, n, "How are you feeling now?");
				say(player, n, "I feel much better rested now");
				npcsay(player, n, "Good, you can continue now",
					"You can now go through the next door\"");
			} else {
				npcsay(player, n, "What did I tell you?",
					"You do not need to manage fatigue here",
					"You can now go through the next door\"");
			}
			player.getCache().set("tutorial", 90);
		} else {
			if (config().WANT_FATIGUE) {
				npcsay(player, n, "When you use your skills you will slowly get fatigued",
					"If you look on your stats menu you will see a fatigue stat",
					"When your fatigue reaches 100 percent then you will be very tired",
					"You won't be able to concentrate enough to gain experience in your skills",
					"To reduce your fatigue you can either eat some food or go to sleep",
					"Click on a bed to go sleep",
					"Then follow the instructions to wake up",
					"You can now go through the next door\"");
			} else {
				npcsay(player, n, "You do not need to manage fatigue here",
					"Sleeping is not part of your progression",
					"You can now go through the next door\"");
			}
		}
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.FATIGUE_EXPERT.id();
	}

}
