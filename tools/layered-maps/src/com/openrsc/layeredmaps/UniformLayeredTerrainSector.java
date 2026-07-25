package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Fully validated uniform 48-tile terrain payload used by the native-loader lab. */
public final class UniformLayeredTerrainSector {
	public static final String ENCODING = "uniform-layered-sector-v1";
	public static final int SCHEMA_VERSION = 1;
	public static final int SIZE = 48;
	public static final int TILE_COUNT = SIZE * SIZE;

	private final int elevation;
	private final int texture;
	private final int overlay;
	private final int roof;
	private final int verticalWall;
	private final int horizontalWall;
	private final long diagonalWall;

	private UniformLayeredTerrainSector(
		int elevation,
		int texture,
		int overlay,
		int roof,
		int verticalWall,
		int horizontalWall,
		long diagonalWall) {
		this.elevation = elevation;
		this.texture = texture;
		this.overlay = overlay;
		this.roof = roof;
		this.verticalWall = verticalWall;
		this.horizontalWall = horizontalWall;
		this.diagonalWall = diagonalWall;
	}

	public static UniformLayeredTerrainSector load(Path path)
		throws IOException, PreflightException {
		Map<String, Object> document = JsonDocuments.readObject(path);
		exactKeys(document, "uniform sector",
			"schemaVersion", "encoding", "size", "tile");
		requireInt(document, "schemaVersion", SCHEMA_VERSION);
		requireString(document, "encoding", ENCODING);
		requireInt(document, "size", SIZE);
		Object rawTile = document.get("tile");
		if (!(rawTile instanceof Map)) {
			throw new PreflightException("uniform sector tile must be an object.");
		}
		Map<String, Object> tile = JsonDocuments.object(rawTile);
		exactKeys(tile, "uniform sector tile",
			"elevation", "texture", "overlay", "roof",
			"verticalWall", "horizontalWall", "diagonalWall");
		return new UniformLayeredTerrainSector(
			unsignedByte(tile, "elevation"),
			unsignedByte(tile, "texture"),
			unsignedByte(tile, "overlay"),
			unsignedByte(tile, "roof"),
			unsignedByte(tile, "verticalWall"),
			unsignedByte(tile, "horizontalWall"),
			unsignedInt(tile, "diagonalWall"));
	}

	private static int unsignedByte(Map<String, Object> value, String key)
		throws PreflightException {
		long result = integer(value, key);
		if (result < 0L || result > 255L) {
			throw new PreflightException(key + " must be an unsigned byte.");
		}
		return (int) result;
	}

	private static long unsignedInt(Map<String, Object> value, String key)
		throws PreflightException {
		long result = integer(value, key);
		if (result < 0L || result > 0xffffffffL) {
			throw new PreflightException(key + " must be an unsigned 32-bit integer.");
		}
		return result;
	}

	private static long integer(Map<String, Object> value, String key)
		throws PreflightException {
		Object result = value.get(key);
		if (!(result instanceof Long)) {
			throw new PreflightException(key + " must be an integer.");
		}
		return (Long) result;
	}

	private static void requireInt(
		Map<String, Object> value, String key, int expected) throws PreflightException {
		long actual = integer(value, key);
		if (actual != expected) {
			throw new PreflightException(
				key + " must be " + expected + " but was " + actual + ".");
		}
	}

	private static void requireString(
		Map<String, Object> value, String key, String expected) throws PreflightException {
		Object actual = value.get(key);
		if (!(actual instanceof String) || !expected.equals(actual)) {
			throw new PreflightException(key + " must be " + expected + ".");
		}
	}

	private static void exactKeys(
		Map<String, Object> value, String label, String... keys)
		throws PreflightException {
		Set<String> expected = new HashSet<String>(Arrays.asList(keys));
		if (!value.keySet().equals(expected)) {
			throw new PreflightException(
				label + " fields differ from the v1 contract.");
		}
	}

	public int getElevation() {
		return elevation;
	}

	public int getTexture() {
		return texture;
	}

	public int getOverlay() {
		return overlay;
	}

	public int getRoof() {
		return roof;
	}

	public int getVerticalWall() {
		return verticalWall;
	}

	public int getHorizontalWall() {
		return horizontalWall;
	}

	public long getDiagonalWall() {
		return diagonalWall;
	}
}
