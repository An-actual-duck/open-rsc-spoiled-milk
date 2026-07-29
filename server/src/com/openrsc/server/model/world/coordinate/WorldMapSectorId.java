package com.openrsc.server.model.world.coordinate;

import java.util.Objects;

/** Immutable logical identity of one 48-tile terrain storage sector. */
public final class WorldMapSectorId {
	private final WorldSpaceId worldSpace;
	private final int level;
	private final int sectorX;
	private final int sectorY;

	public WorldMapSectorId(WorldSpaceId worldSpace, int level, int sectorX, int sectorY) {
		this.worldSpace = Objects.requireNonNull(worldSpace, "worldSpace");
		this.level = level;
		this.sectorX = sectorX;
		this.sectorY = sectorY;
	}

	public static WorldMapSectorId from(WorldLocation location) {
		Objects.requireNonNull(location, "location");
		WorldCoordinate coordinate = location.getCoordinate();
		return new WorldMapSectorId(
			location.getWorldSpace(),
			coordinate.getLevel(),
			coordinate.getSectorX(),
			coordinate.getSectorY());
	}

	public WorldSpaceId getWorldSpace() {
		return worldSpace;
	}

	public int getLevel() {
		return level;
	}

	public int getSectorX() {
		return sectorX;
	}

	public int getSectorY() {
		return sectorY;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof WorldMapSectorId)) {
			return false;
		}
		WorldMapSectorId sector = (WorldMapSectorId) other;
		return level == sector.level
			&& sectorX == sector.sectorX
			&& sectorY == sector.sectorY
			&& worldSpace.equals(sector.worldSpace);
	}

	@Override
	public int hashCode() {
		int result = worldSpace.hashCode();
		result = 31 * result + level;
		result = 31 * result + sectorX;
		result = 31 * result + sectorY;
		return result;
	}

	@Override
	public String toString() {
		return "WorldMapSectorId{worldSpace=" + worldSpace + ", level=" + level
			+ ", sectorX=" + sectorX + ", sectorY=" + sectorY + "}";
	}
}
