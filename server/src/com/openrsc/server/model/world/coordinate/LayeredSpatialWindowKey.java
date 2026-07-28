package com.openrsc.server.model.world.coordinate;

import java.util.Objects;

/**
 * Exact world-space/level-qualified identity for one runtime visibility query.
 *
 * <p>The legacy visibility caches packed several truncated coordinates into a
 * long. This value keeps every component typed and participates in equality
 * directly; its hash is never treated as identity.</p>
 */
public final class LayeredSpatialWindowKey {
	private final WorldRegionWindow regionWindow;
	private final int centerX;
	private final int centerY;
	private final int tileRadius;
	private final int minTileX;
	private final int minTileY;
	private final int maxTileXExclusive;
	private final int maxTileYExclusive;
	private final boolean exactTileBounds;

	private LayeredSpatialWindowKey(
		final WorldRegionWindow regionWindow,
		final int centerX,
		final int centerY,
		final int tileRadius,
		final int minTileX,
		final int minTileY,
		final int maxTileXExclusive,
		final int maxTileYExclusive,
		final boolean exactTileBounds) {
		this.regionWindow = Objects.requireNonNull(
			regionWindow, "regionWindow");
		if (tileRadius < 0) {
			throw new IllegalArgumentException(
				"Spatial window tile radius must not be negative");
		}
		if (minTileX >= maxTileXExclusive
			|| minTileY >= maxTileYExclusive) {
			throw new IllegalArgumentException(
				"Spatial window tile bounds must be non-empty");
		}
		this.centerX = centerX;
		this.centerY = centerY;
		this.tileRadius = tileRadius;
		this.minTileX = minTileX;
		this.minTileY = minTileY;
		this.maxTileXExclusive = maxTileXExclusive;
		this.maxTileYExclusive = maxTileYExclusive;
		this.exactTileBounds = exactTileBounds;
	}

	public static LayeredSpatialWindowKey around(
		final WorldLocation center,
		final int tileRadius) {
		Objects.requireNonNull(center, "center");
		WorldCoordinate coordinate = center.getCoordinate();
		final int minTileX = Math.subtractExact(
			coordinate.getX(), tileRadius);
		final int minTileY = Math.subtractExact(
			coordinate.getY(), tileRadius);
		final int maxTileXExclusive = Math.addExact(
			Math.addExact(coordinate.getX(), tileRadius), 1);
		final int maxTileYExclusive = Math.addExact(
			Math.addExact(coordinate.getY(), tileRadius), 1);
		return new LayeredSpatialWindowKey(
			WorldRegionWindow.around(center, tileRadius),
			coordinate.getX(),
			coordinate.getY(),
			tileRadius,
			minTileX,
			minTileY,
			maxTileXExclusive,
			maxTileYExclusive,
			false);
	}

	/**
	 * Identifies one exact half-open tile rectangle. This is used by client
	 * scene windows whose three-by-three sector footprint is intentionally not
	 * a player-centered radius.
	 */
	public static LayeredSpatialWindowKey exact(
		final WorldLocation scope,
		final int minTileX,
		final int minTileY,
		final int maxTileXExclusive,
		final int maxTileYExclusive) {
		Objects.requireNonNull(scope, "scope");
		if (minTileX >= maxTileXExclusive
			|| minTileY >= maxTileYExclusive) {
			throw new IllegalArgumentException(
				"Exact spatial window tile bounds must be non-empty");
		}
		final int maxTileX = Math.subtractExact(maxTileXExclusive, 1);
		final int maxTileY = Math.subtractExact(maxTileYExclusive, 1);
		final int width = Math.subtractExact(maxTileXExclusive, minTileX);
		final int height = Math.subtractExact(maxTileYExclusive, minTileY);
		final int centerX = Math.toIntExact(
			(long) minTileX + ((long) width - 1L) / 2L);
		final int centerY = Math.toIntExact(
			(long) minTileY + ((long) height - 1L) / 2L);
		final int diagnosticRadius = Math.max(width, height) / 2;
		return new LayeredSpatialWindowKey(
			new WorldRegionWindow(
				scope.getWorldSpace(),
				scope.getCoordinate().getLevel(),
				Math.floorDiv(minTileX, WorldRegionKey.REGION_SIZE),
				Math.floorDiv(minTileY, WorldRegionKey.REGION_SIZE),
				Math.floorDiv(maxTileX, WorldRegionKey.REGION_SIZE),
				Math.floorDiv(maxTileY, WorldRegionKey.REGION_SIZE)),
			centerX,
			centerY,
			diagnosticRadius,
			minTileX,
			minTileY,
			maxTileXExclusive,
			maxTileYExclusive,
			true);
	}

	public WorldRegionWindow getRegionWindow() {
		return regionWindow;
	}

	public int getCenterX() {
		return centerX;
	}

	public int getCenterY() {
		return centerY;
	}

	public int getTileRadius() {
		return tileRadius;
	}

	public boolean hasExactTileBounds() {
		return exactTileBounds;
	}

	public int getMinTileX() {
		return minTileX;
	}

	public int getMinTileY() {
		return minTileY;
	}

	public int getMaxTileXExclusive() {
		return maxTileXExclusive;
	}

	public int getMaxTileYExclusive() {
		return maxTileYExclusive;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof LayeredSpatialWindowKey)) {
			return false;
		}
		LayeredSpatialWindowKey key = (LayeredSpatialWindowKey) other;
		return centerX == key.centerX
			&& centerY == key.centerY
			&& tileRadius == key.tileRadius
			&& minTileX == key.minTileX
			&& minTileY == key.minTileY
			&& maxTileXExclusive == key.maxTileXExclusive
			&& maxTileYExclusive == key.maxTileYExclusive
			&& exactTileBounds == key.exactTileBounds
			&& regionWindow.equals(key.regionWindow);
	}

	@Override
	public int hashCode() {
		int result = regionWindow.hashCode();
		result = 31 * result + centerX;
		result = 31 * result + centerY;
		result = 31 * result + tileRadius;
		result = 31 * result + minTileX;
		result = 31 * result + minTileY;
		result = 31 * result + maxTileXExclusive;
		result = 31 * result + maxTileYExclusive;
		result = 31 * result + (exactTileBounds ? 1 : 0);
		return result;
	}

	@Override
	public String toString() {
		return "LayeredSpatialWindowKey{regionWindow=" + regionWindow
			+ ", centerX=" + centerX + ", centerY=" + centerY
			+ ", tileRadius=" + tileRadius
			+ ", tileBounds=[" + minTileX + "," + minTileY
			+ ".." + maxTileXExclusive + "," + maxTileYExclusive
			+ "), exact=" + exactTileBounds + "}";
	}
}
