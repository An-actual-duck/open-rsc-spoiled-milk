package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import java.util.Arrays;
import java.util.Objects;

/** Detached native terrain page keyed by explicit world space and signed level. */
public final class NativeLayeredTerrainSector {
	public static final int SIZE = 48;
	public static final int TILE_COUNT = SIZE * SIZE;

	private final WorldMapSectorId identity;
	private final NativeLayeredTerrainTile[] tiles;
	private final String sourceEncoding;
	private final String sourcePath;
	private final String sourceSha256;

	NativeLayeredTerrainSector(
		WorldMapSectorId identity,
		NativeLayeredTerrainTile[] tiles,
		String sourceEncoding,
		String sourcePath,
		String sourceSha256) {
		this.identity = Objects.requireNonNull(identity, "identity");
		if (tiles == null || tiles.length != TILE_COUNT) {
			throw new IllegalArgumentException(
				"A native terrain sector must contain exactly " + TILE_COUNT + " tiles");
		}
		this.tiles = Arrays.copyOf(tiles, tiles.length);
		for (NativeLayeredTerrainTile tile : this.tiles) {
			Objects.requireNonNull(tile, "tile");
		}
		this.sourceEncoding = Objects.requireNonNull(sourceEncoding, "sourceEncoding");
		this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
		this.sourceSha256 = Objects.requireNonNull(sourceSha256, "sourceSha256");
	}

	static NativeLayeredTerrainSector uniform(
		WorldMapSectorId identity,
		NativeLayeredTerrainTile tile,
		String sourceEncoding,
		String sourcePath,
		String sourceSha256) {
		NativeLayeredTerrainTile[] tiles = new NativeLayeredTerrainTile[TILE_COUNT];
		Arrays.fill(tiles, Objects.requireNonNull(tile, "tile"));
		return new NativeLayeredTerrainSector(
			identity, tiles, sourceEncoding, sourcePath, sourceSha256);
	}

	public NativeLayeredTerrainTile getTile(int localX, int localY) {
		if (localX < 0 || localX >= SIZE || localY < 0 || localY >= SIZE) {
			throw new IndexOutOfBoundsException(
				"Local terrain coordinate must be 0.." + (SIZE - 1)
					+ ": " + localX + "," + localY);
		}
		return tiles[localX * SIZE + localY];
	}

	/**
	 * Creates a detached legacy-shaped value for parity tests and compatibility
	 * application. The returned Sector is not registered with World or RegionManager.
	 */
	public Sector copyToDetachedLegacySector() {
		Sector result = new Sector();
		for (int localX = 0; localX < SIZE; localX++) {
			for (int localY = 0; localY < SIZE; localY++) {
				result.setTile(
					localX,
					localY,
					getTile(localX, localY).copyToLegacyTile());
			}
		}
		return result;
	}

	public WorldMapSectorId getIdentity() {
		return identity;
	}

	public String getSourceEncoding() {
		return sourceEncoding;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public String getSourceSha256() {
		return sourceSha256;
	}
}
