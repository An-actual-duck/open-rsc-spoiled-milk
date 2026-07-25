package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.util.Objects;

/** Immutable ground-item placement decoded from a native layered package. */
public final class NativeLayeredGroundItemPlacement {
	private final String placementId;
	private final int itemId;
	private final WorldLocation location;
	private final int amount;
	private final int respawnSeconds;

	NativeLayeredGroundItemPlacement(
		final String placementId,
		final int itemId,
		final WorldLocation location,
		final int amount,
		final int respawnSeconds) {
		this.placementId = Objects.requireNonNull(placementId, "placementId");
		this.itemId = itemId;
		this.location = Objects.requireNonNull(location, "location");
		this.amount = amount;
		this.respawnSeconds = respawnSeconds;
	}

	public String getPlacementId() {
		return placementId;
	}

	public int getItemId() {
		return itemId;
	}

	public WorldLocation getLocation() {
		return location;
	}

	public int getAmount() {
		return amount;
	}

	public int getRespawnSeconds() {
		return respawnSeconds;
	}
}
