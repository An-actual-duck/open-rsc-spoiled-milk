package com.openrsc.server.model.world.coordinate;

import java.util.Locale;

/**
 * Canonical signed coordinates for the relocated Zanaris fairy dimension.
 */
public final class ZanarisLocation {
	public static final int LEGACY_LEVEL = -1;
	public static final int LEVEL = 10;
	public static final int LEGACY_PLANE_Y_OFFSET = 3 * 944;
	public static final int MINIMUM_X = 97;
	public static final int MINIMUM_Y = 679;
	public static final int MAXIMUM_X = 180;
	public static final int MAXIMUM_Y = 727;
	public static final int ENTRY_X = 126;
	public static final int ENTRY_Y = 686;
	public static final int EXIT_LADDER_X = 98;
	public static final int EXIT_LADDER_Y = 705;
	public static final int SURFACE_EXIT_X = 98;
	public static final int SURFACE_EXIT_Y = 706;
	public static final String PERSISTENCE_MIGRATION_ORIGIN =
		"zanaris-level-10-relocation-v1";

	private ZanarisLocation() {
	}

	public static WorldLocation at(int x, int y) {
		return new WorldLocation(
			WorldSpaceId.GLOBAL,
			new WorldCoordinate(x, y, LEVEL));
	}

	public static WorldLocation entrance() {
		return at(ENTRY_X, ENTRY_Y);
	}

	public static WorldLocation surfaceExit() {
		return new WorldLocation(
			WorldSpaceId.GLOBAL,
			new WorldCoordinate(
				SURFACE_EXIT_X,
				SURFACE_EXIT_Y,
				0));
	}

	public static boolean isRelocated(WorldLocation location) {
		return location != null
			&& WorldSpaceId.GLOBAL.equals(location.getWorldSpace())
			&& location.getCoordinate().getLevel() == LEVEL
			&& containsLogicalCoordinate(location);
	}

	public static boolean isLegacyComponent(WorldLocation location) {
		return location != null
			&& WorldSpaceId.GLOBAL.equals(location.getWorldSpace())
			&& location.getCoordinate().getLevel() == LEGACY_LEVEL
			&& containsLogicalCoordinate(location);
	}

	public static WorldLocation relocateLegacyComponent(
		WorldLocation location) {
		return isLegacyComponent(location)
			? at(
				location.getCoordinate().getX(),
				location.getCoordinate().getY())
			: location;
	}

	public static WorldLocation migratePersistedLocation(
		WorldLocation location,
		boolean destinationTerrainExists,
		int destinationOverlay) {
		if (!destinationTerrainExists
			|| destinationOverlay == 8) {
			return location;
		}
		return relocateLegacyComponent(location);
	}

	public static boolean isAt(
		WorldLocation location,
		int x,
		int y) {
		return isRelocated(location)
			&& location.getCoordinate().getX() == x
			&& location.getCoordinate().getY() == y;
	}

	public static boolean isBank(WorldLocation location) {
		if (!isRelocated(location)) {
			return false;
		}
		int x = location.getCoordinate().getX();
		int y = location.getCoordinate().getY();
		return inBounds(x, y, 172, 689, 176, 696)
			|| inBounds(x, y, 172, 697, 174, 697)
			|| inBounds(x, y, 170, 689, 171, 693);
	}

	public static boolean isFlourChute(WorldLocation location) {
		return isAt(location, 162, 701);
	}

	public static int logicalY(int legacyPackedY) {
		return Math.subtractExact(
			legacyPackedY,
			LEGACY_PLANE_Y_OFFSET);
	}

	public static boolean isTownAlias(String value) {
		if (value == null) {
			return false;
		}
		String normalized = value.toLowerCase(Locale.ROOT);
		return "zanaris".equals(normalized)
			|| "lostcity".equals(normalized);
	}

	private static boolean containsLogicalCoordinate(
		WorldLocation location) {
		WorldCoordinate coordinate = location.getCoordinate();
		return coordinate.getX() >= MINIMUM_X
			&& coordinate.getX() <= MAXIMUM_X
			&& coordinate.getY() >= MINIMUM_Y
			&& coordinate.getY() <= MAXIMUM_Y;
	}

	private static boolean inBounds(
		int x,
		int y,
		int minimumX,
		int minimumY,
		int maximumX,
		int maximumY) {
		return x >= minimumX
			&& x <= maximumX
			&& y >= minimumY
			&& y <= maximumY;
	}
}
