package com.openrsc.server.plugins.authentic.misc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.custom.skills.harvesting.Harvesting;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.UseNpcTrigger;
import com.openrsc.server.util.rsc.Formulae;

import static com.openrsc.server.plugins.Functions.*;

public class Sheep implements UseNpcTrigger, OpNpcTrigger {

	private static final String SHEAR_COMMAND = "shear";

	@Override
	public boolean blockUseNpc(Player player, Npc npc, Item item) {
		return npc.getID() == NpcId.SHEEP.id() && Harvesting.isShears(item.getCatalogId());
	}

	@Override
	public void onUseNpc(Player player, Npc npc, Item item) {
		if (player.getConfig().WANT_MYWORLD) {
			int equippedTool = Harvesting.getTool(player);
			if (equippedTool == ItemId.NOTHING.id()) {
				player.message("You need to equip harvesting shears you can use");
				return;
			}
			startShearing(player, npc, equippedTool);
			return;
		}

		startShearing(player, npc, item.getCatalogId());
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return npc.getID() == NpcId.SHEEP.id() && SHEAR_COMMAND.equalsIgnoreCase(command);
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		int equippedTool = Harvesting.getTool(player);
		if (equippedTool == ItemId.NOTHING.id()) {
			player.message("You need to equip harvesting shears you can use");
			return;
		}

		startShearing(player, npc, equippedTool);
	}

	private void startShearing(Player player, Npc npc, int shearsId) {
		npc.resetPath();

		int repeat = 1;
		if (config().BATCH_PROGRESSION) {
			repeat = Math.max(1, player.getCarriedItems().getInventory().getFreeSlots());
		}

		startbatch(repeat);
		batchShear(player, npc, shearsId);
	}

	private void batchShear(Player player, Npc npc, int shearsId) {
		thinkbubble(new Item(shearsId));
		player.message("You attempt to shear the sheep");

		int requiredLevel = Math.max(Harvesting.WOOL_UNLOCK_LEVEL, Harvesting.getShearsRequiredLevel(shearsId));
		if (player.getSkills().getLevel(Skill.HARVESTING.id()) < requiredLevel) {
			player.message("You need a harvesting level of " + requiredLevel + " to use those shears");
			return;
		}

		int quantity = Formulae.calcGatheringYield(Harvesting.WOOL_UNLOCK_LEVEL, player.getSkills().getLevel(Skill.HARVESTING.id()), Harvesting.getShearsTier(shearsId));
		int storedQuantity = Math.min(quantity, player.getCarriedItems().getInventory().getFreeSlots());
		player.message(storedQuantity > 0 ? "You get some wool" : "You get some wool, but have no room to keep it");
		if (storedQuantity < quantity) {
			dropOverflow(player, npc, quantity - storedQuantity);
			player.message("Any excess falls to the ground because you have no room");
		}
		if (storedQuantity > 0) {
			give(player, ItemId.WOOL.id(), storedQuantity);
		}
		player.incExp(Skill.HARVESTING.id(), Harvesting.WOOL_HARVESTING_EXP * quantity, true);

		delay(2);

		// Repeat
		updatebatch();
		if (!ifinterrupted() && !isbatchcomplete()) {
			batchShear(player, npc, shearsId);
		}
	}

	private void dropOverflow(Player player, Npc npc, int amount) {
		for (int i = 0; i < amount; i++) {
			player.getWorld().registerItem(new GroundItem(player.getWorld(), ItemId.WOOL.id(), npc.getX(), npc.getY(), 1, player));
		}
	}
}
