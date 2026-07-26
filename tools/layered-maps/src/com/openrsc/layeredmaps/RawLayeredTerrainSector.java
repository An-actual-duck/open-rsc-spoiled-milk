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
	public static final int SIZE = 48;
	public static final int TILE_BYTES = 10;
	public static final int BYTE_COUNT = SIZE * SIZE * TILE_BYTES;

	private RawLayeredTerrainSector() {
	}

	public static void load(Path path) throws IOException, PreflightException {
		long size = Files.size(path);
		if (size != BYTE_COUNT) {
			throw new PreflightException(
				"Raw sector must contain exactly " + BYTE_COUNT
					+ " bytes but contained " + size + ".");
		}
	}
}
