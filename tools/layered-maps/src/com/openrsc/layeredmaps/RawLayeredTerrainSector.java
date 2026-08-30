package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Strict fixed-width native terrain payload in x-major/y-minor tile order.
 *
 * Each tile is ten bytes: elevation, texture, overlay, roof, vertical wall,
 * horizontal wall, and a big-endian 32-bit diagonal-wall value.
 */
public final class RawLayeredTerrainSector {
	public static final String ENCODING = "raw-layered-sector-v1";
	public static final String ENCODING_V2 = "raw-layered-sector-v2-u16";
	public static final int SIZE = 48;
	public static final int TILE_BYTES = 10;
	public static final int TILE_BYTES_V2 = 11;
	public static final int BYTE_COUNT = SIZE * SIZE * TILE_BYTES;
	public static final int BYTE_COUNT_V2 = SIZE * SIZE * TILE_BYTES_V2;

	private RawLayeredTerrainSector() {
	}

	public static void load(Path path) throws IOException, PreflightException {
		load(path, ENCODING);
	}

	public static void load(Path path, String encoding)
		throws IOException, PreflightException {
		int expected = ENCODING_V2.equals(encoding) ? BYTE_COUNT_V2 : BYTE_COUNT;
		long size = Files.size(path);
		if (size != expected) {
			throw new PreflightException(
				"Raw sector must contain exactly " + expected
					+ " bytes but contained " + size + ".");
		}
	}
}
