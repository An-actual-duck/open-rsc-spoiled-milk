package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;

/** Immutable world-space, level, and 48-tile region identity. */
public final class WorldRegionKey {
	public static final int REGION_SIZE = 48;

	private final WorldSpaceId worldSpace;
	private final int level;
	private final int regionX;
	private final int regionY;
	private final int hashCode;

	public WorldRegionKey(WorldSpaceId worldSpace, int level, int regionX, int regionY) {
		this.worldSpace = Objects.requireNonNull(worldSpace, "worldSpace");
		this.level = level;
		this.regionX = regionX;
		this.regionY = regionY;
		this.hashCode = calculateHashCode(
			this.worldSpace, level, regionX, regionY);
	}

	public static WorldRegionKey from(WorldLocation location) {
		Objects.requireNonNull(location, "location");
		WorldCoordinate coordinate = location.getCoordinate();
		return new WorldRegionKey(
			location.getWorldSpace(),
			coordinate.getLevel(),
			Math.floorDiv(coordinate.getX(), REGION_SIZE),
			Math.floorDiv(coordinate.getY(), REGION_SIZE));
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
		return hashCode;
	}

	private static int calculateHashCode(
		final WorldSpaceId worldSpace,
		final int level,
		final int regionX,
		final int regionY) {
		/*
		 * A conventional 31-based chain makes adjacent two-dimensional keys
		 * collide whenever regionX increases by one and regionY decreases by
		 * 31. Large interest windows then treeify HashMap buckets and turn the
		 * server's hottest spatial lookups into repeated identity comparisons.
		 * Mix each axis independently before the final avalanche so nearby
		 * regions retain stable value semantics without that grid pattern.
		 */
		int result = worldSpace.hashCode();
		result ^= Integer.rotateLeft(level * 0x9e3779b9, 5);
		result ^= Integer.rotateLeft(regionX * 0x85ebca6b, 13);
		result ^= Integer.rotateLeft(regionY * 0xc2b2ae35, 21);
		result ^= result >>> 16;
		result *= 0x7feb352d;
		result ^= result >>> 15;
		result *= 0x846ca68b;
		return result ^ result >>> 16;
	}

	@Override
	public String toString() {
		return "WorldRegionKey{worldSpace=" + worldSpace + ", level=" + level
			+ ", regionX=" + regionX + ", regionY=" + regionY + "}";
	}
}
