package com.openrsc.server.model.world.coordinate;

import java.util.Objects;

/**
 * Immutable rectangular area in one world space and on one signed level.
 *
 * <p>The boundaries are open to preserve the existing {@code Area.inBounds}
 * contract: a location on a minimum or maximum boundary is outside.</p>
 */
public final class WorldArea {
	private final WorldLocation minimumBoundary;
	private final WorldLocation maximumBoundary;

	public WorldArea(WorldLocation minimumBoundary, WorldLocation maximumBoundary) {
		this.minimumBoundary = Objects.requireNonNull(minimumBoundary, "minimumBoundary");
		this.maximumBoundary = Objects.requireNonNull(maximumBoundary, "maximumBoundary");
		if (!minimumBoundary.getWorldSpace().equals(maximumBoundary.getWorldSpace())) {
			throw new IllegalArgumentException("World-area boundaries must use the same world space");
		}
		WorldCoordinate minimum = minimumBoundary.getCoordinate();
		WorldCoordinate maximum = maximumBoundary.getCoordinate();
		if (minimum.getLevel() != maximum.getLevel()) {
			throw new IllegalArgumentException("World-area boundaries must use the same level");
		}
		if (minimum.getX() > maximum.getX() || minimum.getY() > maximum.getY()) {
			throw new IllegalArgumentException(
				"World-area minimum boundary must not exceed its maximum boundary");
		}
	}

	public WorldLocation getMinimumBoundary() {
		return minimumBoundary;
	}

	public WorldLocation getMaximumBoundary() {
		return maximumBoundary;
	}

	public WorldSpaceId getWorldSpace() {
		return minimumBoundary.getWorldSpace();
	}

	public int getLevel() {
		return minimumBoundary.getCoordinate().getLevel();
	}

	public int getMinX() {
		return minimumBoundary.getCoordinate().getX();
	}

	public int getMaxX() {
		return maximumBoundary.getCoordinate().getX();
	}

	public int getMinY() {
		return minimumBoundary.getCoordinate().getY();
	}

	public int getMaxY() {
		return maximumBoundary.getCoordinate().getY();
	}

	public boolean contains(WorldLocation location) {
		Objects.requireNonNull(location, "location");
		if (!getWorldSpace().equals(location.getWorldSpace())) {
			return false;
		}
		WorldCoordinate coordinate = location.getCoordinate();
		return coordinate.getLevel() == getLevel()
			&& coordinate.getX() > getMinX()
			&& coordinate.getX() < getMaxX()
			&& coordinate.getY() > getMinY()
			&& coordinate.getY() < getMaxY();
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof WorldArea)) {
			return false;
		}
		WorldArea area = (WorldArea) other;
		return minimumBoundary.equals(area.minimumBoundary)
			&& maximumBoundary.equals(area.maximumBoundary);
	}

	@Override
	public int hashCode() {
		return 31 * minimumBoundary.hashCode() + maximumBoundary.hashCode();
	}

	@Override
	public String toString() {
		return "WorldArea{minimumBoundary=" + minimumBoundary
			+ ", maximumBoundary=" + maximumBoundary + "}";
	}
}
