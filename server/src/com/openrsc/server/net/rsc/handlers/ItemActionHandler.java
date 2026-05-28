package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.ItemCommandStruct;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ItemActionHandler implements PayloadProcessor<ItemCommandStruct, OpcodeIn> {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private boolean isCombatConsumableAction(final Item item, final String command, final Player player) {
		if (command.equalsIgnoreCase("drink") || command.equalsIgnoreCase("eat")) {
			return true;
		}
		return item.isEdible(player.getWorld())
			|| item.getCatalogId() == ItemId.ROTTEN_APPLES.id()
			|| item.getCatalogId() == ItemId.FISH_OIL.id()
			|| item.getCatalogId() == ItemId.SWEETENED_SLICES.id()
			|| item.getCatalogId() == ItemId.SWEETENED_CHUNKS.id();
	}

	public void process(ItemCommandStruct payload, Player player) throws Exception {
		int idx = payload.index;
		int amount = 1;
		if (player.isUsingCustomClient()) {
			amount = payload.amount;
		}
		int commandIndex = 0;

		if (player == null || player.getCarriedItems().getInventory() == null) {
			return;
		}

		if (idx < -1) {
			player.setSuspiciousPlayer(true, "item idx < -1");
			return;
		}

		if (idx >= player.getCarriedItems().getInventory().size()) {
			return;
		}
		Item tempitem = null;


		//User wants to use the item from equipment tab
		if (idx == -1 && player.isUsingCustomClient()) {
			idx = payload.realIndex;
			int slot = player.getCarriedItems().getEquipment().searchEquipmentForItem(idx);
			if (slot != -1) {
				tempitem = player.getCarriedItems().getEquipment().get(slot);
			}
			commandIndex = payload.commandIndex;
		} else {
			tempitem = player.getCarriedItems().getInventory().get(idx);
			if (player.isUsingCustomClient()) {
				commandIndex = payload.commandIndex;
			}
		}

		if (tempitem == null || tempitem.getCatalogId() == ItemId.NOTHING.id()) return;

		final Item item = amount > 1 ? new Item(tempitem.getCatalogId(), amount) : tempitem;
		if (Summoning.handleRatItemSelection(player, tempitem) || Summoning.handleCamelItemSelection(player, tempitem)) {
			return;
		}
		if (item == null || item.getDef(player.getWorld()).getCommand() == null
		|| commandIndex < 0 || commandIndex >= item.getDef(player.getWorld()).getCommand().length) {
			player.setSuspiciousPlayer(true, "item action item null or null item def");
			return;
		}

		if (item.getDef(player.getWorld()).isMembersOnly() && !player.getConfig().MEMBER_WORLD) {
			player.message("You need to be a member to use this object");
			return;
		}

		final String command = item.getDef(player.getWorld()).getCommand()[commandIndex];
		final boolean combatConsumableAction = isCombatConsumableAction(item, command, player);

		if (player.inCombat() && !combatConsumableAction) {
			player.message("You can't do that whilst you are fighting");
			return;
		}

		if (player.isBusy() && !combatConsumableAction) {
			return;
		}

		if (!combatConsumableAction) {
			player.resetAll(false, false);
		}

		// We want to keep walking, but not perform the action when we get there.
		final WalkToAction walkToAction = player.getWalkToAction();
		if (walkToAction != null) {
			walkToAction.finishExecution();
			player.setWalkToAction(null);
		}

		player.getWorld().getServer().getPluginHandler().handlePlugin(OpInvTrigger.class, player, new Object[]{player, idx, item, command});
	}
}
