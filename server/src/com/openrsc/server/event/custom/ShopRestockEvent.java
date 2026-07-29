package com.openrsc.server.event.custom;

import com.openrsc.server.event.DelayedEvent;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.world.World;

public final class ShopRestockEvent extends DelayedEvent {

	private final Shop shop;

	public ShopRestockEvent(World world, Shop shop) {
		// TODO: Verify and change shop restock timers to authentic + GAME_TICK
		super(world, null, shop.getRespawnRate(), "Shop Restock Event");
		this.shop = shop;
	}

	@Override
	public void run() {
		shop.restock();
	}

	/**
	 * Shop stock is world-level state. Restocking neither reads nor mutates a
	 * terrain source, Region, scenery placement, or NPC lifecycle.
	 */
	@Override
	public GameTickEventSpatialAffinity getSpatialAffinity() {
		return GameTickEventSpatialAffinity.nonSpatialGlobal();
	}

}
