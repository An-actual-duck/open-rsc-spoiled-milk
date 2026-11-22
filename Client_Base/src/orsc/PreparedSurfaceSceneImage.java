package orsc;

import java.util.Arrays;
import java.util.List;
import orsc.graphics.three.World;

/** Exact placement inventory built from one complete terrain prediction. */
final class PreparedSurfaceSceneImage {
	final int centerX, centerZ;
	private final int baseX, baseZ;
	private final long preparedAt;
	private final long[] placements;

	private PreparedSurfaceSceneImage(int centerX, int centerZ, int baseX, int baseZ, long[] placements) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.baseX = baseX;
		this.baseZ = baseZ;
		this.placements = placements;
		preparedAt = System.currentTimeMillis();
	}

	static PreparedSurfaceSceneImage from(int centerX, int centerZ, List<World.SurfaceSceneryPlacement> records) {
		int baseX = World.sectionToLocalBaseTile(centerX), baseZ = World.sectionToLocalBaseTile(centerZ);
		long[] placements = new long[records.size()];
		for (int i = 0; i < placements.length; i++) {
			World.SurfaceSceneryPlacement row = records.get(i);
			int x = row.x - baseX, z = row.z - baseZ;
			if (row.type < 0 || row.type > 1 || !validRow(x, z, row.id, row.direction)) return null;
			placements[i] = key(x, z, row.type, row.id, row.direction);
		}
		Arrays.sort(placements);
		return new PreparedSurfaceSceneImage(centerX, centerZ, baseX, baseZ, placements);
	}

	String compareLive(mudclient client) {
		String center = centerX + "," + centerZ;
		if (client.getMidRegionBaseX() != baseX || client.getMidRegionBaseZ() != baseZ) return center + " basis-changed";
		int objects = client.getGameObjectInstanceCount(), walls = client.getWallObjectInstanceCount();
		long[] live = new long[objects + walls];
		for (int i = 0; i < objects; i++) {
			int x = client.getGameObjectInstanceX(i), z = client.getGameObjectInstanceZ(i);
			int id = client.getGameObjectInstanceID(i), direction = client.getGameObjectInstanceDir(i);
			if (!validRow(x, z, id, direction)) return center + " unrepresentable object";
			live[i] = key(x, z, 0, id, direction);
		}
		for (int i = 0; i < walls; i++) {
			int x = client.getWallObjectInstanceX(i), z = client.getWallObjectInstanceZ(i);
			int id = client.getWallObjectInstanceID(i), direction = client.getWallObjectInstanceDir(i);
			if (!validRow(x, z, id, direction)) return center + " unrepresentable wall";
			live[objects + i] = key(x, z, 1, id, direction);
		}
		Arrays.sort(live);
		long firstMissing = -1, firstExtra = -1;
		int missing = 0, extra = 0;
		int predictedIndex = 0, liveIndex = 0;
		while (predictedIndex < placements.length && liveIndex < live.length) {
			long predicted = placements[predictedIndex], actual = live[liveIndex];
			if (predicted == actual) {
				predictedIndex++;
				liveIndex++;
			} else if (predicted < actual) {
				if (missing++ == 0) firstMissing = predicted;
				predictedIndex++;
			} else {
				if (extra++ == 0) firstExtra = actual;
				liveIndex++;
			}
		}
		if (missing == 0 && predictedIndex < placements.length) firstMissing = placements[predictedIndex];
		if (extra == 0 && liveIndex < live.length) firstExtra = live[liveIndex];
		missing += placements.length - predictedIndex;
		extra += live.length - liveIndex;
		return center + " expected=" + placements.length + " actual=" + live.length
			+ " unchangedMs=" + Math.max(0, System.currentTimeMillis() - preparedAt)
			+ " expectedOnly=" + missing + describeFirst(firstMissing)
			+ " actualOnly=" + extra + describeFirst(firstExtra);
	}

	private static boolean validRow(int x, int z, int id, int direction) {
		return World.isLocalTile(x, z) && id >= 0 && id <= 0xffff && direction >= 0 && direction <= 255;
	}

	private static long key(int x, int z, int type, int id, int direction) {
		return ((long) x << 33) | ((long) z << 25) | ((long) type << 24) | ((long) id << 8) | direction;
	}

	private static String describeFirst(long key) {
		if (key < 0) return "";
		return "[" + (key >>> 33) + "," + ((key >>> 25) & 255) + "/" + ((key >>> 24) & 1)
			+ "/" + ((key >>> 8) & 65535) + "/" + (key & 255) + "]";
	}
}
