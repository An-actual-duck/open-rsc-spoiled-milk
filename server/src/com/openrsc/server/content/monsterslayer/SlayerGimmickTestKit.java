package com.openrsc.server.content.monsterslayer;

import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;

/** Administrator testing supplies. Add counters here only once implemented. */
public final class SlayerGimmickTestKit {
	private static final int COPIES_PER_ITEM = 1;
	private static final int[] ITEM_IDS = {GiantFrogCombat.SOLVENT_ITEM_ID};
	private SlayerGimmickTestKit() { }

	public static void grant(Player player, String[] args) {
		if (!player.isAdmin()) return;
		if (!player.getConfig().WANT_MYWORLD) {
			player.message("Slayer gimmick supplies are only available in MyWorld.");
			return;
		}
		if (args.length != 0) {
			player.message("Usage: ::slayergimmicks");
			return;
		}
		Inventory inventory = player.getCarriedItems().getInventory();
		synchronized (inventory.getItems()) {
			int slots = 0;
			for (int id : ITEM_IDS) {
				if (player.getWorld().getServer().getEntityHandler().getItemDef(id) == null
					|| player.getClientLimitations().maxItemId < id
					|| (player.getConfig().RESTRICT_ITEM_ID >= 0 && player.getConfig().RESTRICT_ITEM_ID < id)) {
					player.message("Your client or world cannot receive the full Slayer gimmick kit.");
					return;
				}
				slots += inventory.getRequiredSlots(new Item(id, COPIES_PER_ITEM));
			}
			if (inventory.getCapacity() - inventory.size() < slots) {
				player.message("You need " + slots + " free inventory slots for the Slayer gimmick supplies.");
				return;
			}
			for (int id : ITEM_IDS) {
				if (!inventory.add(new Item(id, COPIES_PER_ITEM))) {
					player.message("Slayer supplies could not be fully added. Check your inventory before retrying.");
					return;
				}
				player.message("Added " + COPIES_PER_ITEM + " x "
					+ player.getWorld().getServer().getEntityHandler().getItemDef(id).getName() + ".");
			}
		}
	}
}
