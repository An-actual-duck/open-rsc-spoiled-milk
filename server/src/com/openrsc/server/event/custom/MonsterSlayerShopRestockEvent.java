package com.openrsc.server.event.custom;

import com.openrsc.server.event.DelayedEvent;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.model.world.World;

/**
 * World-owned replenishment for the typed Monster Slayer reward stock.
 *
 * <p>The service holds one shared stock ledger, so this event deliberately has
 * no player or NPC owner.  A player opening a shop never advances stock.</p>
 */
public final class MonsterSlayerShopRestockEvent extends DelayedEvent {
	public static final long INTERVAL_MS = 60_000L;

	public MonsterSlayerShopRestockEvent(World world) {
		super(world, null, INTERVAL_MS, "Monster Slayer Shop Restock");
	}

	@Override public void run() {
		if (getWorld().getMonsterSlayerShopService() != null) {
			getWorld().getMonsterSlayerShopService().restock();
		}
	}

	@Override public GameTickEventSpatialAffinity getSpatialAffinity() {
		return GameTickEventSpatialAffinity.nonSpatialGlobal();
	}
}
