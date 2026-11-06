package com.openrsc.server.plugins.authentic.npcs.alkharid;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.*;

public final class BorderGuard implements
	TalkNpcTrigger, OpLocTrigger {

	@Override
	public void onTalkNpc(Player player, final Npc n) {
		if (player.getQuestStage(Quests.PRINCE_ALI_RESCUE) == -1
			|| player.getQuestStage(Quests.PRINCE_ALI_RESCUE) == 3) {
			say(player, n, "Can I come through this gate?");
			npcsay(player, n,
				"You may pass for free, you are a friend of Al Kharid");
			player.message("The gate swings open");
			if (player.getX() > 91)
				crossTollGate(player, 90, 649);
			else
				crossTollGate(player, 93, 649);
			return;
		}
		say(player, n, "Can I come through this gate?");
		npcsay(player, n, "You must pay a toll of 10 gold coins to pass");
		int option = multi(player, n, false, "No thankyou, I'll walk round",
			"Who does my money go to?", "yes ok");
		switch (option) {
			case 0: // no thanks
				say(player, n, "No thankyou");
				npcsay(player, n, "Ok suit yourself");
				break;
			case 1: // who does money go to
				say(player, n, "Who does my money go to?");
				npcsay(player, n, "The money goes to the city of Al Kharid");
				break;
			case 2:
				say(player, n, "Yes ok");
				if (tollGate(player) == null) {
					player.message("The gate is unavailable. Please try again shortly.");
					return;
				}
				if (player.getCarriedItems().remove(new Item(ItemId.COINS.id(), 10)) > -1) {
					player.message("You pay the guard");
					npcsay(player, n, "You may pass");
					player.message("The gate swings open");
					if (player.getX() > 91) {
						approachAndCross(player, 92, 649, 91, 649);
					}
					else {
						approachAndCross(player, 91, 649, 92, 649);
					}
				} else {
					say(player, n,
						"Oh dear I don't actually seem to have enough money");
				}
				break;
		}
	}

	private static void approachAndCross(final Player player, final int x1, final int y1, final int x2, final int y2) {
		player.walk(x1, y1);
		while (!player.getWalkingQueue().finished()) {
			delay();
		}
		crossTollGate(player, x2, y2);
	}

	private static GameObject tollGate(Player player) {
		GameObject gate = player.getWorld().getRegionManager().findInteractionScenery(Point.location(92, 649), player);
		return gate != null && (gate.getID() == 180 || gate.getID() == 181) ? gate : null;
	}

	private static void crossTollGate(Player player, int x, int y) {
		GameObject gate = tollGate(player);
		if (gate == null) {
			player.message("The gate is unavailable. Please try again shortly.");
			return;
		}
		if (gate.getID() == 180) doGate(player, gate, 181, Point.location(x, y));
		else walkThroughDoorway(player, x, y);
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.BORDER_GUARD_ALKHARID.id() || n.getID() == NpcId.BORDER_GUARD_LUMBRIDGE.id();
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (obj.getID() == 180 && command.equals("open")) {
			player.message("You need to talk to the border guard");
		}
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return obj.getID() == 180 && command.equals("open");
	}
}
