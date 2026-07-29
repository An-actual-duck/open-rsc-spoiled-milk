package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

	static NativeLayeredTerrainSector ofTiles(
		WorldMapSectorId identity,
		NativeLayeredTerrainTile[] tiles,
		String sourceEncoding,
		String sourcePath,
		String sourceSha256) {
		return new NativeLayeredTerrainSector(
			identity, tiles, sourceEncoding, sourcePath, sourceSha256);
	}

	/**
	 * Creates the deterministic blocking/invisible page used by an isolated
	 * World Builder live allocation before its journal is materialized.
	 */
	public static NativeLayeredTerrainSector worldBuilderVoid(
		WorldMapSectorId identity) {
		NativeLayeredTerrainTile tile =
			new NativeLayeredTerrainTile(0, 1, 8, 0, 0, 0, 0);
		NativeLayeredTerrainTile[] tiles =
			new NativeLayeredTerrainTile[TILE_COUNT];
		Arrays.fill(tiles, tile);
		return new NativeLayeredTerrainSector(
			identity,
			tiles,
			NativeLayeredWorldPackage.RAW_ENCODING,
			"world-builder-live-void",
			sha256(tiles));
	}

	private static String sha256(NativeLayeredTerrainTile[] tiles) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (NativeLayeredTerrainTile tile : tiles) {
				digest.update((byte)tile.getElevation());
				digest.update((byte)tile.getTexture());
				digest.update((byte)tile.getOverlay());
				digest.update((byte)tile.getRoof());
				digest.update((byte)tile.getVerticalWall());
				digest.update((byte)tile.getHorizontalWall());
				int diagonal = tile.getDiagonalWall();
				digest.update((byte)(diagonal >>> 24));
				digest.update((byte)(diagonal >>> 16));
				digest.update((byte)(diagonal >>> 8));
				digest.update((byte)diagonal);
			}
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) {
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
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
	 * Returns a fresh fixed-width x-major/y-minor wire image of this complete
	 * storage sector. Field order matches
	 * {@link NativeLayeredTerrainChunk#copyWireBytes()}.
	 */
	public byte[] copyWireBytes() {
		byte[] result =
			new byte[TILE_COUNT * NativeLayeredTerrainChunk.TILE_WIRE_BYTES];
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
