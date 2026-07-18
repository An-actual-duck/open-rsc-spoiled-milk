package com.openrsc.server.model.world.coordinate;

/** Immutable signed layered tile coordinate. */
public final class WorldCoordinate {
	public static final int TERRAIN_SECTOR_SIZE = 48;

	private final int x;
	private final int y;
	private final int level;

	public WorldCoordinate(int x, int y, int level) {
		this.x = x;
		this.y = y;
		this.level = level;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getLevel() {
		return level;
	}

	public int getSectorX() {
		return Math.floorDiv(x, TERRAIN_SECTOR_SIZE);
	}

	public int getSectorY() {
		return Math.floorDiv(y, TERRAIN_SECTOR_SIZE);
	}

	public int getLocalX() {
		return Math.floorMod(x, TERRAIN_SECTOR_SIZE);
	}

	public int getLocalY() {
		return Math.floorMod(y, TERRAIN_SECTOR_SIZE);
	}

	public WorldCoordinate translate(int deltaX, int deltaY, int deltaLevel) {
		return new WorldCoordinate(
			Math.addExact(x, deltaX),
			Math.addExact(y, deltaY),
			Math.addExact(level, deltaLevel));
	}

	public WorldCoordinate atLevel(int newLevel) {
		return new WorldCoordinate(x, y, newLevel);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof WorldCoordinate)) {
			return false;
		}
		WorldCoordinate coordinate = (WorldCoordinate) other;
		return x == coordinate.x && y == coordinate.y && level == coordinate.level;
	}

	@Override
	public int hashCode() {
		int result = x;
		result = 31 * result + y;
		result = 31 * result + level;
		return result;
	}

	@Override
	public String toString() {
		return "WorldCoordinate{x=" + x + ", y=" + y + ", level=" + level + "}";
	}
}
