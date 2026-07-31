package orsc;

/**
 * Converts resident-object chunk cells between stable world-grid identity and
 * the current region's local tile coordinates.
 */
final class ResidentObjectChunkGrid {
	private ResidentObjectChunkGrid() {
	}

	static int worldCellForLocalTile(
		int localTile,
		int regionBase,
		int cellTileSize) {
		validateCellTileSize(cellTileSize);
		return Math.floorDiv(
			Math.addExact(localTile, regionBase),
			cellTileSize);
	}

	static int localOriginTileForWorldCell(
		int worldCell,
		int regionBase,
		int cellTileSize) {
		validateCellTileSize(cellTileSize);
		return Math.subtractExact(
			Math.multiplyExact(worldCell, cellTileSize),
			regionBase);
	}

	private static void validateCellTileSize(int cellTileSize) {
		if (cellTileSize <= 0) {
			throw new IllegalArgumentException(
				"Resident object chunk size must be positive");
		}
	}
}
