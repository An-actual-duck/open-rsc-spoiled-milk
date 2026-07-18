package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;

/** Immutable world-space, level, and 48-tile region identity. */
public final class WorldRegionKey {
	private final WorldSpaceId worldSpace;
	private final int level;
	private final int regionX;
	private final int regionY;

	public WorldRegionKey(WorldSpaceId worldSpace, int level, int regionX, int regionY) {
		this.worldSpace = Objects.requireNonNull(worldSpace, "worldSpace");
		this.level = level;
		this.regionX = regionX;
		this.regionY = regionY;
	}

	public static WorldRegionKey from(WorldLocation location) {
		Objects.requireNonNull(location, "location");
		WorldCoordinate coordinate = location.getCoordinate();
		return new WorldRegionKey(
			location.getWorldSpace(),
			coordinate.getLevel(),
			coordinate.getSectorX(),
			coordinate.getSectorY());
	}

	public static WorldRegionKey fromLegacyPoint(Point point) {
		return from(LegacyPackedPointAdapter.fromLegacyPoint(point));
	}

	public WorldSpaceId getWorldSpace() {
		return worldSpace;
	}

	public int getLevel() {
		return level;
	}

	public int getRegionX() {
		return regionX;
	}

	public int getRegionY() {
		return regionY;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof WorldRegionKey)) {
			return false;
		}
		WorldRegionKey key = (WorldRegionKey) other;
		return level == key.level
			&& regionX == key.regionX
			&& regionY == key.regionY
			&& worldSpace.equals(key.worldSpace);
	}

	@Override
	public int hashCode() {
		int result = worldSpace.hashCode();
		result = 31 * result + level;
		result = 31 * result + regionX;
		result = 31 * result + regionY;
		return result;
	}

	@Override
	public String toString() {
		return "WorldRegionKey{worldSpace=" + worldSpace + ", level=" + level
			+ ", regionX=" + regionX + ", regionY=" + regionY + "}";
	}
}
