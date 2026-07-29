package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.util.Objects;

/** One immutable package-owned native layered boundary placement. */
public final class NativeLayeredBoundaryPlacement {
	private final String placementId;
	private final int boundaryId;
	private final WorldLocation location;
	private final int direction;

	public NativeLayeredBoundaryPlacement(
		final String placementId,
		final int boundaryId,
		final WorldLocation location,
		final int direction) {
		this.placementId = Objects.requireNonNull(
			placementId, "placementId");
		if (boundaryId < 0 || direction < 0 || direction > 7) {
			throw new IllegalArgumentException(
				"Native layered boundary placement is invalid");
		}
		this.boundaryId = boundaryId;
		this.location = Objects.requireNonNull(location, "location");
		this.direction = direction;
	}

	public String getPlacementId() {
		return placementId;
	}

	public int getBoundaryId() {
		return boundaryId;
	}

	public WorldLocation getLocation() {
		return location;
	}

	public int getDirection() {
		return direction;
	}
}
