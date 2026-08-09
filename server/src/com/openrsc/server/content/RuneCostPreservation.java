package com.openrsc.server.content;

import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.util.rsc.DataConversions;

/** Shared robe-and-staff rune-cost preservation used by spells and summons. */
public final class RuneCostPreservation {
	private RuneCostPreservation() {
	}

	public static boolean shouldPreserve(final Player player, final int runeId) {
		final double chance = getChance(player, runeId);
		return chance > 0.0D && DataConversions.getRandom().nextDouble() < chance;
	}

	public static double getChance(final Player player, final int runeId) {
		if (player == null) {
			return 0.0D;
		}
		double chance = player.getCarriedItems().getEquipment().getWoolRobeRunePreservationChance(runeId);
		final Item equippedStaff = getEquippedMainHand(player);
		if (equippedStaff != null) {
			chance += EnchantingItemEffects.getStaffRunePreservationChance(equippedStaff.getCatalogId(), runeId);
		}
		return Math.min(1.0D, chance);
	}

	private static Item getEquippedMainHand(final Player player) {
		if (player.getConfig().WANT_EQUIPMENT_TAB) {
			return player.getCarriedItems().getEquipment().get(Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex());
		}
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (item != null && item.isWielded()
					&& item.getDef(player.getWorld()) != null
					&& item.getDef(player.getWorld()).getWieldPosition() == Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex()) {
					return item;
				}
			}
		}
		return null;
	}
}
