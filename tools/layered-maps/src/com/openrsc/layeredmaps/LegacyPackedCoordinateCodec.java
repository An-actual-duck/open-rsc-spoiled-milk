package com.openrsc.layeredmaps;

/** Exact checked bridge for the four-band packed-Y legacy coordinate model. */
public final class LegacyPackedCoordinateCodec {
	public static final String ID = "legacy-packed-y-v1";
	public static final int LEVEL_STRIDE = 944;
	public static final int LEGACY_PLANE_COUNT = 4;
	public static final int MIN_PACKED_Y = 0;
	public static final int MAX_PACKED_Y = LEVEL_STRIDE * LEGACY_PLANE_COUNT - 1;
	public static final int MAX_LEGACY_X = Short.MAX_VALUE;

	private LegacyPackedCoordinateCodec() {
	}

	public static WorldCoordinate decode(int packedX, int packedY) {
		validatePackedX(packedX);
		if (packedY < MIN_PACKED_Y || packedY > MAX_PACKED_Y) {
			throw new IllegalArgumentException(
				"Packed Y is outside legacy four-plane range 0.." + MAX_PACKED_Y + ": " + packedY);
		}
		int plane = Math.floorDiv(packedY, LEVEL_STRIDE);
		int y = Math.floorMod(packedY, LEVEL_STRIDE);
		return new WorldCoordinate(packedX, y, levelForLegacyPlane(plane));
	}

	public static PackedCoordinate encode(WorldCoordinate coordinate) {
		if (coordinate == null) {
			throw new NullPointerException("coordinate");
		}
		validatePackedX(coordinate.getX());
		if (coordinate.getY() < 0 || coordinate.getY() >= LEVEL_STRIDE) {
			throw new IllegalArgumentException(
				"Layered Y cannot be represented by legacy-packed-y-v1: " + coordinate.getY());
		}
		int plane = legacyPlaneForLevel(coordinate.getLevel());
		int packedY = Math.addExact(Math.multiplyExact(plane, LEVEL_STRIDE), coordinate.getY());
		return new PackedCoordinate(coordinate.getX(), packedY);
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

	/** Immutable legacy wire/storage coordinate. */
	public static final class PackedCoordinate {
		private final int x;
		private final int y;

		private PackedCoordinate(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		@Override
		public boolean equals(Object other) {
			return this == other
				|| other instanceof PackedCoordinate
					&& x == ((PackedCoordinate) other).x
					&& y == ((PackedCoordinate) other).y;
		}

		@Override
		public int hashCode() {
			return 31 * x + y;
		}

		@Override
		public String toString() {
			return "PackedCoordinate{x=" + x + ", y=" + y + "}";
		}
	}
}
