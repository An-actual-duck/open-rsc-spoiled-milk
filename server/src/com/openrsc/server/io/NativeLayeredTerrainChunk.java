package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable presentation-sized view copied from one native 48-tile storage
 * sector. Chunk coordinates are global and independent from storage pages.
 */
public final class NativeLayeredTerrainChunk {
	public static final int TILE_WIRE_BYTES = 10;

	private final WorldSpaceId worldSpace;
	private final int level;
	private final int chunkX;
	private final int chunkY;
	private final int size;
	private final WorldMapSectorId sourceSector;
	private final String sourceEncoding;
	private final String sourceSha256;
	private final NativeLayeredTerrainTile[] tiles;

	NativeLayeredTerrainChunk(
		WorldSpaceId worldSpace,
		int level,
		int chunkX,
		int chunkY,
		int size,
		WorldMapSectorId sourceSector,
		String sourceEncoding,
		String sourceSha256,
		NativeLayeredTerrainTile[] tiles) {
		this.worldSpace = Objects.requireNonNull(worldSpace, "worldSpace");
		this.level = level;
		this.chunkX = chunkX;
		this.chunkY = chunkY;
		if (size <= 0 || size > NativeLayeredTerrainSector.SIZE
			|| NativeLayeredTerrainSector.SIZE % size != 0) {
			throw new IllegalArgumentException(
				"Presentation chunk size must be a positive divisor of 48");
		}
		this.size = size;
		this.sourceSector = Objects.requireNonNull(sourceSector, "sourceSector");
		this.sourceEncoding = Objects.requireNonNull(
			sourceEncoding, "sourceEncoding");
		this.sourceSha256 = Objects.requireNonNull(sourceSha256, "sourceSha256");
		if (tiles == null || tiles.length != size * size) {
			throw new IllegalArgumentException(
				"Presentation chunk tile count differs from its declared size");
		}
		this.tiles = Arrays.copyOf(tiles, tiles.length);
		for (NativeLayeredTerrainTile tile : this.tiles) {
			Objects.requireNonNull(tile, "tile");
		}
	}

	public NativeLayeredTerrainTile getTile(int localX, int localY) {
		if (localX < 0 || localX >= size || localY < 0 || localY >= size) {
			throw new IndexOutOfBoundsException(
				"Local presentation coordinate must be 0.." + (size - 1));
		}
		return tiles[localX * size + localY];
	}

	/**
	 * Returns a fresh fixed-width x-major/y-minor wire image. Field order is
	 * elevation, texture, overlay, roof, vertical wall, horizontal wall, then
	 * all 32 diagonal-wall bits in network byte order.
	 */
	public byte[] copyWireBytes() {
		byte[] result = new byte[tiles.length * TILE_WIRE_BYTES];
		int offset = 0;
		for (NativeLayeredTerrainTile tile : tiles) {
			result[offset++] = (byte) tile.getElevation();
			result[offset++] = (byte) tile.getTexture();
			result[offset++] = (byte) tile.getOverlay();
			result[offset++] = (byte) tile.getRoof();
			result[offset++] = (byte) tile.getVerticalWall();
			result[offset++] = (byte) tile.getHorizontalWall();
			int diagonal = tile.getDiagonalWall();
			result[offset++] = (byte) (diagonal >>> 24);
			result[offset++] = (byte) (diagonal >>> 16);
			result[offset++] = (byte) (diagonal >>> 8);
			result[offset++] = (byte) diagonal;
		}
		return result;
	}

	public WorldSpaceId getWorldSpace() {
		return worldSpace;
	}

	public int getLevel() {
		return level;
	}

	public int getChunkX() {
		return chunkX;
	}

	public int getChunkY() {
		return chunkY;
	}

	public int getSize() {
		return size;
	}

	public WorldMapSectorId getSourceSector() {
		return sourceSector;
	}

	public String getSourceEncoding() {
		return sourceEncoding;
	}

	public String getSourceSha256() {
		return sourceSha256;
	}
}
