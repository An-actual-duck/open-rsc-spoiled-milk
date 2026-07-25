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

	private LayeredSpatialWindowKey(
		final WorldRegionWindow regionWindow,
		final int centerX,
		final int centerY,
		final int tileRadius) {
		this.regionWindow = Objects.requireNonNull(
			regionWindow, "regionWindow");
		if (tileRadius < 0) {
			throw new IllegalArgumentException(
				"Spatial window tile radius must not be negative");
		}
		this.centerX = centerX;
		this.centerY = centerY;
		this.tileRadius = tileRadius;
	}

	public static LayeredSpatialWindowKey around(
		final WorldLocation center,
		final int tileRadius) {
		Objects.requireNonNull(center, "center");
		return new LayeredSpatialWindowKey(
			WorldRegionWindow.around(center, tileRadius),
			center.getCoordinate().getX(),
			center.getCoordinate().getY(),
			tileRadius);
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
			&& regionWindow.equals(key.regionWindow);
	}

	@Override
	public int hashCode() {
		int result = regionWindow.hashCode();
		result = 31 * result + centerX;
		result = 31 * result + centerY;
		result = 31 * result + tileRadius;
		return result;
	}

	@Override
	public String toString() {
		return "LayeredSpatialWindowKey{regionWindow=" + regionWindow
			+ ", centerX=" + centerX + ", centerY=" + centerY
			+ ", tileRadius=" + tileRadius + "}";
	}
}
