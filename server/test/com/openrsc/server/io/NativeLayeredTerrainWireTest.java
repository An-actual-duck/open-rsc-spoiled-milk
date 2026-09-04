package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import java.util.Arrays;

/** Direct regression coverage for legacy and unsigned-16-bit terrain wire images. */
public final class NativeLayeredTerrainWireTest {
	private static final String SHA256 =
		"0000000000000000000000000000000000000000000000000000000000000000";

	private NativeLayeredTerrainWireTest() {}

	public static void main(String[] arguments) {
		NativeLayeredTerrainTile wideTile =
			new NativeLayeredTerrainTile(0x1234, 2, 3, 4, 5, 6, 0x07112233);
		NativeLayeredTerrainTile[] wideTiles =
			new NativeLayeredTerrainTile[NativeLayeredTerrainSector.TILE_COUNT];
		Arrays.fill(wideTiles, wideTile);
		WorldMapSectorId identity =
			new WorldMapSectorId(WorldSpaceId.GLOBAL, 0, 0, 0);
		NativeLayeredTerrainSector wideSector = NativeLayeredTerrainSector.ofTiles(
			identity,
			wideTiles,
			NativeLayeredWorldPackage.RAW_ENCODING_V2,
			"test-wide.raw",
			SHA256);
		assertWideImage(wideSector.copyWireBytes(), wideTiles.length);

		NativeLayeredTerrainTile[] wideChunkTiles =
			new NativeLayeredTerrainTile[24 * 24];
		Arrays.fill(wideChunkTiles, wideTile);
		NativeLayeredTerrainChunk wideChunk = new NativeLayeredTerrainChunk(
			WorldSpaceId.GLOBAL,
			0,
			0,
			0,
			24,
			identity,
			NativeLayeredWorldPackage.RAW_ENCODING_V2,
			SHA256,
			wideChunkTiles);
		assertWideImage(wideChunk.copyWireBytes(), wideChunkTiles.length);

		NativeLayeredTerrainTile legacyTile =
			new NativeLayeredTerrainTile(200, 2, 3, 4, 5, 6, 0x07112233);
		NativeLayeredTerrainTile[] legacyTiles =
			new NativeLayeredTerrainTile[NativeLayeredTerrainSector.TILE_COUNT];
		Arrays.fill(legacyTiles, legacyTile);
		NativeLayeredTerrainSector legacySector = NativeLayeredTerrainSector.ofTiles(
			identity,
			legacyTiles,
			NativeLayeredWorldPackage.RAW_ENCODING,
			"test-legacy.raw",
			SHA256);
		byte[] legacy = legacySector.copyWireBytes();
		check(
			legacy.length
				== legacyTiles.length
					* NativeLayeredTerrainChunk.LEGACY_TILE_WIRE_BYTES,
			"legacy image length");
		check((legacy[0] & 0xff) == 200 && (legacy[1] & 0xff) == 2,
			"legacy field order");

		boolean rejected = false;
		try {
			NativeLayeredTerrainSector.ofTiles(
				identity,
				wideTiles,
				NativeLayeredWorldPackage.RAW_ENCODING,
				"invalid-legacy.raw",
				SHA256).copyWireBytes();
		} catch (IllegalStateException expected) {
			rejected = true;
		}
		check(rejected, "wide elevation under legacy encoding must fail closed");
		System.out.println("PASS: native terrain wire widths preserved");
	}

	private static void assertWideImage(byte[] image, int tileCount) {
		check(
			image.length
				== tileCount * NativeLayeredTerrainChunk.WIDE_TILE_WIRE_BYTES,
			"wide image length");
		check((image[0] & 0xff) == 0x12 && (image[1] & 0xff) == 0x34,
			"wide elevation byte order");
		check((image[2] & 0xff) == 2 && (image[3] & 0xff) == 3,
			"wide visual field order");
		check((image[6] & 0xff) == 6,
			"wide structural field offset");
		check((image[7] & 0xff) == 0x07 && (image[10] & 0xff) == 0x33,
			"wide diagonal byte order");
	}

	private static void check(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}
}
