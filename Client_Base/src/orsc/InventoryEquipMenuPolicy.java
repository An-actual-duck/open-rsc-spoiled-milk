package orsc;

import com.openrsc.client.entityhandling.defs.ItemDef;

/**
 * Inventory equip-menu decisions independent of character-model visuals.
 */
public final class InventoryEquipMenuPolicy {
	private InventoryEquipMenuPolicy() {
	}

	public static boolean canOfferEquip(final ItemDef item, final boolean noted) {
		return item != null && item.isWieldable() && !noted;
	}

	public static String actionLabel(final ItemDef item) {
		return item != null && (item.wearableID & 24) != 0 ? "Wield" : "Wear";
	}
}
