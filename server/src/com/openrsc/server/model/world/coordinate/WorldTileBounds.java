package com.openrsc.server.model.world.coordinate;

import java.util.Objects;

/** Immutable inclusive tile bounds in one world space and on one signed level. */
public final class WorldTileBounds {
	private final WorldLocation minimum;
	private final WorldLocation maximum;

	public WorldTileBounds(WorldLocation minimum, WorldLocation maximum) {
		this.minimum = Objects.requireNonNull(minimum, "minimum");
		this.maximum = Objects.requireNonNull(maximum, "maximum");
		if (!minimum.getWorldSpace().equals(maximum.getWorldSpace())) {
			throw new IllegalArgumentException("Tile bounds must use the same world space");
		}
		WorldCoordinate minimumCoordinate = minimum.getCoordinate();
		WorldCoordinate maximumCoordinate = maximum.getCoordinate();
		if (minimumCoordinate.getLevel() != maximumCoordinate.getLevel()) {
			throw new IllegalArgumentException("Tile bounds must use the same level");
		}
		if (minimumCoordinate.getX() > maximumCoordinate.getX()
			|| minimumCoordinate.getY() > maximumCoordinate.getY()) {
			throw new IllegalArgumentException("Minimum tile bounds must not exceed maximum bounds");
		}
	}

	public WorldLocation getMinimum() {
		return minimum;
	}

	public WorldLocation getMaximum() {
		return maximum;
	}

	public WorldSpaceId getWorldSpace() {
		return minimum.getWorldSpace();
	}

	public int getLevel() {
		return minimum.getCoordinate().getLevel();
	}

	public int getMinX() {
		return minimum.getCoordinate().getX();
	}

	public int getMaxX() {
		return maximum.getCoordinate().getX();
	}

	public int getMinY() {
		return minimum.getCoordinate().getY();
	}

	public int getMaxY() {
		return maximum.getCoordinate().getY();
	}

	public boolean contains(WorldLocation location) {
		Objects.requireNonNull(location, "location");
		if (!getWorldSpace().equals(location.getWorldSpace())) {
			return false;
		}
		WorldCoordinate coordinate = location.getCoordinate();
		return coordinate.getLevel() == getLevel()
			&& coordinate.getX() >= getMinX()
			&& coordinate.getX() <= getMaxX()
			&& coordinate.getY() >= getMinY()
			&& coordinate.getY() <= getMaxY();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof WorldTileBounds)) {
			return false;
		}
		WorldTileBounds bounds = (WorldTileBounds) other;
		return minimum.equals(bounds.minimum) && maximum.equals(bounds.maximum);
	}

	@Override
	public int hashCode() {
		return 31 * minimum.hashCode() + maximum.hashCode();
	}

	@Override
	public String toString() {
		return "WorldTileBounds{minimum=" + minimum + ", maximum=" + maximum + "}";
	}
}
