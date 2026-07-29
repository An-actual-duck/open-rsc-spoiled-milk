package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;

/** Checked bridge between the existing packed {@link Point} and layered locations. */
public final class LegacyPackedPointAdapter {
	public static final String ID = "legacy-packed-y-v1";
	public static final int LEVEL_STRIDE = 944;
	public static final int LEGACY_PLANE_COUNT = 4;
	public static final int MIN_PACKED_Y = 0;
	public static final int MAX_PACKED_Y = LEVEL_STRIDE * LEGACY_PLANE_COUNT - 1;
	public static final int MAX_LEGACY_X = Short.MAX_VALUE;

	private LegacyPackedPointAdapter() {
	}

	public static WorldLocation fromLegacyPoint(Point point) {
		Objects.requireNonNull(point, "point");
		return fromPackedValues(point.getX(), point.getY());
	}

	public static WorldLocation fromPackedValues(int packedX, int packedY) {
		validatePackedX(packedX);
		if (packedY < MIN_PACKED_Y || packedY > MAX_PACKED_Y) {
			throw new IllegalArgumentException(
				"Packed Y is outside legacy four-plane range 0.." + MAX_PACKED_Y + ": " + packedY);
		}
		int plane = Math.floorDiv(packedY, LEVEL_STRIDE);
		int y = Math.floorMod(packedY, LEVEL_STRIDE);
		return WorldLocation.global(new WorldCoordinate(packedX, y, levelForLegacyPlane(plane)));
	}

	public static Point toLegacyPoint(WorldLocation location) {
		Objects.requireNonNull(location, "location");
		if (!WorldSpaceId.GLOBAL.equals(location.getWorldSpace())) {
			throw new IllegalArgumentException(
				"Legacy Point cannot represent world space: " + location.getWorldSpace());
		}
		WorldCoordinate coordinate = location.getCoordinate();
		validatePackedX(coordinate.getX());
		if (coordinate.getY() < 0 || coordinate.getY() >= LEVEL_STRIDE) {
			throw new IllegalArgumentException(
				"Layered Y cannot be represented by legacy-packed-y-v1: " + coordinate.getY());
		}
		int plane = legacyPlaneForLevel(coordinate.getLevel());
		int packedY = Math.addExact(
			Math.multiplyExact(plane, LEVEL_STRIDE), coordinate.getY());
		return Point.location(coordinate.getX(), packedY);
	}

	public static int levelForLegacyPlane(int plane) {
		switch (plane) {
			case 0:
				return 0;
			case 1:
				return 1;
			case 2:
				return 2;
			case 3:
				return -1;
			default:
				throw new IllegalArgumentException("Unsupported legacy plane: " + plane);
		}
	}

	public static int legacyPlaneForLevel(int level) {
		switch (level) {
			case 0:
				return 0;
			case 1:
				return 1;
			case 2:
				return 2;
			case -1:
				return 3;
			default:
				throw new IllegalArgumentException(
					"Layered level cannot be represented by legacy-packed-y-v1: " + level);
		}
	}

	private static void validatePackedX(int packedX) {
		if (packedX < 0 || packedX > MAX_LEGACY_X) {
			throw new IllegalArgumentException(
				"Packed X is outside the non-negative legacy short range 0.."
					+ MAX_LEGACY_X + ": " + packedX);
		}
	}
}
