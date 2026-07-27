package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.util.Objects;

/** One immutable package-owned native layered scenery placement. */
public final class NativeLayeredSceneryPlacement {
	private final String placementId;
	private final int sceneryId;
	private final WorldLocation location;
	private final int direction;

	public NativeLayeredSceneryPlacement(
		final String placementId,
		final int sceneryId,
		final WorldLocation location,
		final int direction) {
		this.placementId = Objects.requireNonNull(
			placementId, "placementId");
		if (sceneryId < 0 || direction < 0 || direction > 8) {
			throw new IllegalArgumentException(
				"Native layered scenery placement is invalid");
		}
		this.sceneryId = sceneryId;
		this.location = Objects.requireNonNull(location, "location");
		this.direction = direction;
	}

	public String getPlacementId() {
		return placementId;
	}

	public int getSceneryId() {
		return sceneryId;
	}

	public WorldLocation getLocation() {
		return location;
	}

	public int getDirection() {
		return direction;
	}
}
