package com.openrsc.layeredmaps;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict full-fidelity terrain payload whose runs expand in x-major/y-minor
 * order to exactly one 48-by-48 storage sector.
 */
public final class RleLayeredTerrainSector {
	public static final String ENCODING = "rle-layered-sector-v1";
	public static final String TILE_ORDER = "x-major-y-minor";
	public static final int SCHEMA_VERSION = 1;
	public static final int SIZE = 48;
	public static final int TILE_COUNT = SIZE * SIZE;

	private RleLayeredTerrainSector() {
	}

	public static void load(Path path) throws IOException, PreflightException {
		Map<String, Object> document = JsonDocuments.readObject(path);
		exactKeys(document, "RLE sector",
			"schemaVersion", "encoding", "size", "tileOrder", "runs");
		requireInt(document, "schemaVersion", SCHEMA_VERSION);
		requireString(document, "encoding", ENCODING);
		requireInt(document, "size", SIZE);
		requireString(document, "tileOrder", TILE_ORDER);

		Object rawRuns = document.get("runs");
		if (!(rawRuns instanceof List)) {
			throw new PreflightException("RLE sector runs must be an array.");
		}
		List<Object> runs = JsonDocuments.array(rawRuns);
		if (runs.isEmpty() || runs.size() > TILE_COUNT) {
			throw new PreflightException(
				"RLE sector runs count must be 1.." + TILE_COUNT + ".");
		}

		int expanded = 0;
		for (int index = 0; index < runs.size(); index++) {
			Map<String, Object> run = object(runs.get(index), "runs[" + index + "]");
			exactKeys(run, "runs[" + index + "]", "count", "tile");
			int count = integer(run, "count");
			if (count <= 0 || count > TILE_COUNT - expanded) {
				throw new PreflightException(
					"runs[" + index + "].count exceeds the remaining sector capacity.");
			}
			readTile(object(run.get("tile"), "runs[" + index + "].tile"),
				"runs[" + index + "].tile");
			expanded += count;
		}
		if (expanded != TILE_COUNT) {
			throw new PreflightException(
				"RLE sector runs must expand to exactly " + TILE_COUNT
					+ " tiles but expanded to " + expanded + ".");
		}
	}

	private static void readTile(Map<String, Object> tile, String label)
		throws PreflightException {
		exactKeys(tile, label,
			"elevation", "texture", "overlay", "roof",
			"verticalWall", "horizontalWall", "diagonalWall");
		unsignedByte(tile, "elevation");
		unsignedByte(tile, "texture");
		unsignedByte(tile, "overlay");
		unsignedByte(tile, "roof");
		unsignedByte(tile, "verticalWall");
		unsignedByte(tile, "horizontalWall");
		unsignedInt(tile, "diagonalWall");
	}

	private static Map<String, Object> object(Object value, String label)
		throws PreflightException {
		if (!(value instanceof Map)) {
			throw new PreflightException(label + " must be an object.");
		}
		return JsonDocuments.object(value);
	}

	private static int unsignedByte(Map<String, Object> value, String key)
		throws PreflightException {
		int result = integer(value, key);
		if (result < 0 || result > 255) {
			throw new PreflightException(key + " must be an unsigned byte.");
		}
		return result;
	}

	private static long unsignedInt(Map<String, Object> value, String key)
		throws PreflightException {
		Object raw = value.get(key);
		if (!(raw instanceof Long)) {
			throw new PreflightException(key + " must be an unsigned 32-bit integer.");
		}
		long result = (Long) raw;
		if (result < 0L || result > 0xffffffffL) {
			throw new PreflightException(key + " must be an unsigned 32-bit integer.");
		}
		return result;
	}

	private static int integer(Map<String, Object> value, String key)
		throws PreflightException {
		Object raw = value.get(key);
		if (!(raw instanceof Long)
			|| (Long) raw < Integer.MIN_VALUE || (Long) raw > Integer.MAX_VALUE) {
			throw new PreflightException(key + " must be a signed 32-bit integer.");
		}
		return ((Long) raw).intValue();
	}

	private static void requireInt(
		Map<String, Object> value, String key, int expected) throws PreflightException {
		int actual = integer(value, key);
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
}
