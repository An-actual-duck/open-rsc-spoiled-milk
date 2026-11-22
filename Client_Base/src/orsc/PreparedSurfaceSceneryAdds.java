package orsc;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import orsc.graphics.three.World;

/** Incoming rows in legacy addition order, ready for an exact walking packet. */
final class PreparedSurfaceSceneryAdds {
	private final int sourceX, sourceZ, baseX, baseZ;
	private final List<LegacyStaticSceneDeltaBatch.Record> objects = new ArrayList<>();
	private final List<LegacyStaticSceneDeltaBatch.Record> walls = new ArrayList<>();

	private PreparedSurfaceSceneryAdds(int sourceX, int sourceZ, int baseX, int baseZ) {
		this.sourceX = sourceX;
		this.sourceZ = sourceZ;
		this.baseX = baseX;
		this.baseZ = baseZ;
	}

	static PreparedSurfaceSceneryAdds from(int sourceX, int sourceZ, int baseX, int baseZ,
			List<World.SurfaceSceneryPlacement> entering) {
		PreparedSurfaceSceneryAdds prepared = new PreparedSurfaceSceneryAdds(sourceX, sourceZ, baseX, baseZ);
		BitSet objectTiles = new BitSet(), wallSides = new BitSet();
		for (World.SurfaceSceneryPlacement row : entering) {
			int x = row.x - baseX, z = row.z - baseZ;
			if (!World.isLocalTile(x, z) || World.isLocalTile(row.x - sourceX, row.z - sourceZ)
				|| (row.type != 0 && row.type != 1) || row.id < 0 || row.id >= 0xff00
				|| row.id == LegacyStaticSceneDeltaBatch.REMOVE_ID
				|| row.direction < 0 || row.direction > (row.type == 0 ? 7 : 3)) return null;
			int tile = x * World.LOCAL_TILE_COUNT + z;
			boolean wall = row.type == 1;
			BitSet occupied = wall ? wallSides : objectTiles;
			int key = wall ? 4 * tile + row.direction : tile;
			// Repeated updates at a legacy address require the ordered delta path.
			if (occupied.get(key)) return null;
			occupied.set(key);
			prepared.additions(wall).add(LegacyStaticSceneDeltaBatch.Record.tile(row.id, x, z, row.direction));
		}
		return prepared;
	}

	boolean moves(int oldX, int oldZ, int nextX, int nextZ) {
		return sourceX == oldX && sourceZ == oldZ && at(nextX, nextZ);
	}

	boolean at(int x, int z) {
		return baseX == x && baseZ == z;
	}

	List<LegacyStaticSceneDeltaBatch.Record> additions(boolean wall) {
		return wall ? walls : objects;
	}

	boolean matches(byte[] data, int start, int end, int playerX, int playerZ, boolean wall) {
		if (start < 0 || end > data.length || start > end || (end - start) % 5 != 0) return false;
		List<LegacyStaticSceneDeltaBatch.Record> additions = additions(wall);
		int addition = 0;
		for (int at = start; at < end; at += 5) {
			if ((data[at] & 255) == 255) return false; // region clears use a different record format
			int id = (data[at] & 255) << 8 | (data[at + 1] & 255);
			int x = playerX + data[at + 2], z = playerZ + data[at + 3];
			if (id == LegacyStaticSceneDeltaBatch.REMOVE_ID) {
				if (World.isLocalTile(x, z)) return false;
				continue; // the rolling window already dropped this address
			}
			if (addition == additions.size()) return false;
			LegacyStaticSceneDeltaBatch.Record expected = additions.get(addition++);
			if (id != expected.getId() || x != expected.getX() || z != expected.getZ()
				|| data[at + 4] != expected.getDirection()) return false;
		}
		return addition == additions.size();
	}
}
