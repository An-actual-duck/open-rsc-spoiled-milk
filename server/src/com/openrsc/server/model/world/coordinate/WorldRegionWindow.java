package com.openrsc.server.model.world.coordinate;

import java.util.Objects;

/** Immutable inclusive logical-region window in one world space and level. */
public final class WorldRegionWindow {
	private final WorldSpaceId worldSpace;
	private final int level;
	private final int minRegionX;
	private final int minRegionY;
	private final int maxRegionX;
	private final int maxRegionY;

	public WorldRegionWindow(
		final WorldSpaceId worldSpace,
		final int level,
		final int minRegionX,
		final int minRegionY,
		final int maxRegionX,
		final int maxRegionY) {
		this.worldSpace = Objects.requireNonNull(worldSpace, "worldSpace");
		if (minRegionX > maxRegionX || minRegionY > maxRegionY) {
			throw new IllegalArgumentException(
				"Minimum logical-region bounds must not exceed maximum bounds");
		}
		this.level = level;
		this.minRegionX = minRegionX;
		this.minRegionY = minRegionY;
		this.maxRegionX = maxRegionX;
		this.maxRegionY = maxRegionY;
	}

	public static WorldRegionWindow around(
		final WorldLocation center,
		final int tileRadius) {
		Objects.requireNonNull(center, "center");
		if (tileRadius < 0) {
			throw new IllegalArgumentException("Tile radius must not be negative");
		}
		WorldCoordinate coordinate = center.getCoordinate();
		int minTileX = Math.subtractExact(coordinate.getX(), tileRadius);
		int minTileY = Math.subtractExact(coordinate.getY(), tileRadius);
		int maxTileX = Math.addExact(coordinate.getX(), tileRadius);
		int maxTileY = Math.addExact(coordinate.getY(), tileRadius);
		return new WorldRegionWindow(
			center.getWorldSpace(),
			coordinate.getLevel(),
			Math.floorDiv(minTileX, WorldRegionKey.REGION_SIZE),
			Math.floorDiv(minTileY, WorldRegionKey.REGION_SIZE),
			Math.floorDiv(maxTileX, WorldRegionKey.REGION_SIZE),
			Math.floorDiv(maxTileY, WorldRegionKey.REGION_SIZE));
	}

	public WorldSpaceId getWorldSpace() {
		return worldSpace;
	}

	public int getLevel() {
		return level;
	}

	public int getMinRegionX() {
		return minRegionX;
	}

	public int getMinRegionY() {
		return minRegionY;
	}

	public int getMaxRegionX() {
		return maxRegionX;
	}

	public int getMaxRegionY() {
		return maxRegionY;
	}

	public boolean contains(final WorldRegionKey key) {
		Objects.requireNonNull(key, "key");
		return worldSpace.equals(key.getWorldSpace())
			&& level == key.getLevel()
			&& key.getRegionX() >= minRegionX
			&& key.getRegionX() <= maxRegionX
			&& key.getRegionY() >= minRegionY
			&& key.getRegionY() <= maxRegionY;
	}

	public long getRegionCount() {
		long width = (long) maxRegionX - minRegionX + 1L;
		long height = (long) maxRegionY - minRegionY + 1L;
		return Math.multiplyExact(width, height);
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof WorldRegionWindow)) {
			return false;
		}
		WorldRegionWindow window = (WorldRegionWindow) other;
		return level == window.level
			&& minRegionX == window.minRegionX
			&& minRegionY == window.minRegionY
			&& maxRegionX == window.maxRegionX
			&& maxRegionY == window.maxRegionY
			&& worldSpace.equals(window.worldSpace);
	}

	@Override
	public int hashCode() {
		int result = worldSpace.hashCode();
		result = 31 * result + level;
		result = 31 * result + minRegionX;
		result = 31 * result + minRegionY;
		result = 31 * result + maxRegionX;
		result = 31 * result + maxRegionY;
		return result;
	}

	@Override
	public String toString() {
		return "WorldRegionWindow{worldSpace=" + worldSpace + ", level=" + level
			+ ", minRegionX=" + minRegionX + ", minRegionY=" + minRegionY
			+ ", maxRegionX=" + maxRegionX + ", maxRegionY=" + maxRegionY + "}";
	}
}
