package com.openrsc.server.plugins.authentic.npcs.rimmington;


import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.plugins.authentic.defaults.Ladders;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.*;

public class MasterCrafter implements TalkNpcTrigger, OpLocTrigger {
	private static final int CRAFTING_GUILD_EXTENSION_STAIRS = 42;
	private static final int CRAFTING_GUILD_CAVES_LEVEL = 70;
	private static final WorldLocation CRAFTING_GUILD_EXTENSION_STAIRS_LOCATION =
		WorldLocation.global(new WorldCoordinate(338, 614, 0));
	private static final String CAVES_REQUIREMENT =
		"You need level 70 Crafting to enter the caves below the guild";
	private static final String CAVES_WARNING =
		"Gem dragons lurk down there. Be prepared for a dangerous fight";
	private static final Ladders LADDERS = new Ladders();

	@Override
	public void onTalkNpc(Player player, Npc n) {
		if (n.getID() == NpcId.MASTER_CRAFTER.id()) {
			npcsay(player, n, "Hello welcome to the Crafter's guild",
				"Accomplished crafters from all over the land come here",
				"All to use our top notch workshops");
			if (config().WANT_CUSTOM_SPRITES
				&& getMaxLevel(player, Skill.CRAFTING.id()) >= 99) {

				if (multi(player, n, "That's a nice cape you've got on there",
					"Thank you. Have a nice day") == 0) {

					npcsay(player, n, "Ah yes",
						"This cape is only granted to those who have mastered Crafting",
						"I see that you too are a master craftworker",
						"Would you like to buy a Crafting cape for 99,000 coins?");
					if (multi(player, n, "Yes please", "No thank you") == 0) {
						if (player.getCarriedItems().getInventory().countId(ItemId.COINS.id()) >= 99000) {
							mes("The Master Crafter takes your coins");
							delay(3);
							if (player.getCarriedItems().remove(new Item(ItemId.COINS.id(), 99000)) > -1) {
								mes("And hands you a Crafting cape");
								delay(3);
								give(player, ItemId.CRAFTING_CAPE.id(), 1);
								npcsay(player, n, "There you are",
									"This cape will allow you to teleport to this guild as much as you like",
									"You can also wear this cape instead of an apron to gain entry");
							}
						} else {
							npcsay(player, n, "Sorry, you don't have enough coins");
						}
					}
				}
			}
		}
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.MASTER_CRAFTER.id();
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (!blockOpLoc(player, obj, command)) {
			return;
		}
		if (getMaxLevel(player, Skill.CRAFTING.id()) >= CRAFTING_GUILD_CAVES_LEVEL) {
			player.message(CAVES_WARNING);
			LADDERS.onObjectAction(obj, command, player);
			return;
		}
		Npc master = findGuildMaster(player);
		if (master != null) {
			npcsay(player, master, CAVES_REQUIREMENT, CAVES_WARNING);
		} else {
			player.message(CAVES_REQUIREMENT);
			player.message(CAVES_WARNING);
		}
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return obj.getID() == CRAFTING_GUILD_EXTENSION_STAIRS
			&& command.equals("go down")
			&& isCaveStairLocation(obj.getWorldLocation())
			&& player.getWorldLocation().getWorldSpace().equals(
				CRAFTING_GUILD_EXTENSION_STAIRS_LOCATION.getWorldSpace())
			&& player.getWorldLocation().getCoordinate().getLevel() == 0;
	}

	private static boolean isCaveStairLocation(WorldLocation location) {
		return CRAFTING_GUILD_EXTENSION_STAIRS_LOCATION.equals(location);
	}

	private static Npc findGuildMaster(Player player) {
		for (Npc npc : player.getWorld().getNpcs()) {
			if (npc.getID() != NpcId.MASTER_CRAFTER.id()
				|| npc.isRemoved() || npc.isRespawning()) {
				continue;
			}
			WorldLocation location = npc.getWorldLocation();
			WorldCoordinate coordinate = location.getCoordinate();
			if (location.getWorldSpace().equals(player.getWorldLocation().getWorldSpace())
				&& coordinate.getLevel() == 0
				&& coordinate.getX() >= 341 && coordinate.getX() <= 349
				&& coordinate.getY() >= 599 && coordinate.getY() <= 612) {
				return npc;
			}
		}
		return null;
	}
}
