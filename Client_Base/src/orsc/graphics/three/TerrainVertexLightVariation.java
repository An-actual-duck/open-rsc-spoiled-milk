package orsc.graphics.three;

/**
 * Stable world-space replacement for the legacy random terrain vertex light.
 *
 * <p>The light value enters the terrain mesh storage signature. Random values
 * therefore made an unchanged section look new whenever a layered transition
 * rebuilt it, forcing avoidable GPU uploads and changing the lighting at
 * section boundaries.</p>
 */
final class TerrainVertexLightVariation {
	private static final int MINIMUM_LIGHT = -5;
	private static final int LIGHT_VALUE_COUNT = 10;
	private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;

	private TerrainVertexLightVariation() {
	}

	static int forWorldVertex(
		int plane,
		int worldTileX,
		int worldTileZ) {
		long hash = mix(FNV_OFFSET_BASIS, plane);
		hash = mix(hash, worldTileX);
		hash = mix(hash, worldTileZ);
		hash ^= hash >>> 32;
		return (int) Math.floorMod(hash, (long) LIGHT_VALUE_COUNT)
			+ MINIMUM_LIGHT;
	}

	private static long mix(long hash, int value) {
		return (hash ^ (value & 0xffffffffL)) * FNV_PRIME;
	}
}
