package com.openrsc.server.content.market;

import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;

/** Shared, capacity-aware admission policy for auction purchases and returns. */
public final class MarketInventoryAdmission {
	private MarketInventoryAdmission() {
	}

	public static boolean canReceive(Inventory inventory, int catalogId, int amount,
			boolean stackable) {
		if (amount <= 0) return false;
		// One item is delivered unnoted; larger non-stackable quantities use notes.
		// Admission must test the exact representation that will be delivered.
		return inventory.canHold(new Item(catalogId, amount, !stackable && amount > 1));
	}
}
