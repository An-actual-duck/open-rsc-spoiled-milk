package com.openrsc.server.model.world.coordinate;

/**
 * Canonical signed coordinates for the relocated lava-forge dungeon.
 */
public final class LavaForgeLocation {
	public static final int LEGACY_LEVEL = -1;
	public static final int LEVEL = -2;
	public static final int MINIMUM_X = 288;
	public static final int MINIMUM_Y = 576;
	public static final int MAXIMUM_X = 335;
	public static final int MAXIMUM_Y = 623;
	public static final int ENTRY_X = 329;
	public static final int ENTRY_Y = 587;
	public static final int EXIT_LADDER_X = 329;
	public static final int EXIT_LADDER_Y = 586;
	public static final int DWARVEN_MINE_LADDER_X = 271;
	public static final int DWARVEN_MINE_LADDER_Y = 508;
	public static final int DWARVEN_MINE_RETURN_X = 271;
	public static final int DWARVEN_MINE_RETURN_Y = 507;
	public static final String PERSISTENCE_MIGRATION_ORIGIN =
		"lava-forge-level-minus-2-relocation-v1";

	private LavaForgeLocation() {
	}

	public static WorldLocation at(int x, int y) {
		return new WorldLocation(
			WorldSpaceId.GLOBAL,
			new WorldCoordinate(x, y, LEVEL));
	}

	public static WorldLocation entrance() {
		return at(ENTRY_X, ENTRY_Y);
	}

	public static WorldLocation dwarvenMineReturn() {
		return new WorldLocation(
			WorldSpaceId.GLOBAL,
			new WorldCoordinate(
				DWARVEN_MINE_RETURN_X,
				DWARVEN_MINE_RETURN_Y,
				LEGACY_LEVEL));
	}

	public static boolean isRelocated(WorldLocation location) {
		return location != null
			&& WorldSpaceId.GLOBAL.equals(location.getWorldSpace())
			&& location.getCoordinate().getLevel() == LEVEL
			&& containsLogicalCoordinate(location);
	}

	public static boolean isLegacyComponentCandidate(
		WorldLocation location) {
		return location != null
			&& WorldSpaceId.GLOBAL.equals(location.getWorldSpace())
			&& location.getCoordinate().getLevel() == LEGACY_LEVEL
			&& containsLogicalCoordinate(location);
	}

	public static WorldLocation relocateLegacyComponentCandidate(
		WorldLocation location) {
		return isLegacyComponentCandidate(location)
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
		return relocateLegacyComponentCandidate(location);
	}

	public static boolean isAt(
		WorldLocation location,
		int x,
		int y) {
		return isRelocated(location)
			&& location.getCoordinate().getX() == x
			&& location.getCoordinate().getY() == y;
	}

	public static boolean isExitLadder(WorldLocation location) {
		return isAt(location, EXIT_LADDER_X, EXIT_LADDER_Y);
	}

	public static boolean isDwarvenMineDownLadder(
		WorldLocation location) {
		return location != null
			&& WorldSpaceId.GLOBAL.equals(location.getWorldSpace())
			&& location.getCoordinate().getLevel() == LEGACY_LEVEL
			&& location.getCoordinate().getX()
				== DWARVEN_MINE_LADDER_X
			&& location.getCoordinate().getY()
				== DWARVEN_MINE_LADDER_Y;
	}

	private static boolean containsLogicalCoordinate(
		WorldLocation location) {
		WorldCoordinate coordinate = location.getCoordinate();
		return coordinate.getX() >= MINIMUM_X
			&& coordinate.getX() <= MAXIMUM_X
			&& coordinate.getY() >= MINIMUM_Y
			&& coordinate.getY() <= MAXIMUM_Y;
	}
}
