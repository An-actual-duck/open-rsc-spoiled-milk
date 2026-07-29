package com.openrsc.server.model.world.coordinate;

import java.util.Objects;

/** World-space-qualified geographic location. */
public final class WorldLocation {
	private final WorldSpaceId worldSpace;
	private final WorldCoordinate coordinate;

	public WorldLocation(WorldSpaceId worldSpace, WorldCoordinate coordinate) {
		this.worldSpace = Objects.requireNonNull(worldSpace, "worldSpace");
		this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
	}

	public static WorldLocation global(WorldCoordinate coordinate) {
		return new WorldLocation(WorldSpaceId.GLOBAL, coordinate);
	}

	public WorldSpaceId getWorldSpace() {
		return worldSpace;
	}

	public WorldCoordinate getCoordinate() {
		return coordinate;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof WorldLocation)) {
			return false;
		}
		WorldLocation location = (WorldLocation) other;
		return worldSpace.equals(location.worldSpace) && coordinate.equals(location.coordinate);
	}

	@Override
	public int hashCode() {
		return 31 * worldSpace.hashCode() + coordinate.hashCode();
	}

	@Override
	public String toString() {
		return "WorldLocation{worldSpace=" + worldSpace + ", coordinate=" + coordinate + "}";
	}
}
