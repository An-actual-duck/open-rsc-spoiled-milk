package orsc.graphics.three;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Published world-coordinate terrain and live collision contributions for the surface. */
final class SurfaceCollisionCells {
	private static final int CELL_SIZE = World.SECTION_SIZE;
	private final Map<Long, int[]> terrain = new HashMap<>();
	private final Map<Long, IncomingScenery> incomingScenery = new HashMap<>();
	private Map<Long, TileContributions> live = new HashMap<>();
	private Map<Placement, Integer> placements = new HashMap<>();
	private long revision;
	private String scope;
	private int baseX;
	private int baseZ;
	private int[][] view;
	private int incomingCells;

	boolean active() { return view != null; }
	void detachView() { view = null; }

	void clear() {
		detachView();
		scope = null;
		terrain.clear();
		incomingScenery.clear();
		live.clear();
		placements.clear();
		incomingCells = 0;
		revision++;
	}

	void install(String mapScope, int x, int z, int[][] staticFlags) {
		setWindow(mapScope, x, z, staticFlags);
		for (Map.Entry<Long, TileContributions> entry : live.entrySet()) {
			long key = entry.getKey();
			int localX = (int)(key >> 32) - baseX, localZ = (int)key - baseZ;
			if (World.isLocalTile(localX, localZ)) {
				TileContributions tile = entry.getValue();
				tile.terrainFlags = view[localX][localZ];
				view[localX][localZ] = tile.terrainFlags | tile.flags;
			}
		}
	}

	private void setWindow(String mapScope, int x, int z, int[][] flags) {
		if (!mapScope.equals(scope)) clear();
		scope = mapScope;
		baseX = x;
		baseZ = z;
		int west = Math.floorDiv(x, CELL_SIZE), south = Math.floorDiv(z, CELL_SIZE);
		if (!terrain.isEmpty()) {
			Iterator<Long> cached = terrain.keySet().iterator();
			while (cached.hasNext()) {
				long key = cached.next();
				int cellX = (int)(key >> 32), cellZ = (int)key;
				if (cellX < west - 1 || cellX > west + 3 || cellZ < south - 1 || cellZ > south + 3) cached.remove();
			}
		}
		view = flags;
		revision++;
	}

	/** Copy only collision ownership. The current pathfinding grid stays installed. */
	SurfaceCollisionCells copyForRoll() {
		SurfaceCollisionCells next = new SurfaceCollisionCells();
		next.scope = scope;
		next.baseX = baseX;
		next.baseZ = baseZ;
		next.placements.putAll(placements);
		for (Map.Entry<Long, TileContributions> entry : live.entrySet()) {
			next.live.put(entry.getKey(), entry.getValue().copy());
		}
		return next;
	}

	long revision() { return revision; }

	void installRoll(SurfaceCollisionCells prepared) {
		setWindow(prepared.scope, prepared.baseX, prepared.baseZ, prepared.view);
		placements = prepared.placements;
		live = prepared.live;
	}

	int[][] installedFlags() { return view; }

	boolean hasWindow(String mapScope, int x, int z) { return scope.equals(mapScope) && baseX == x && baseZ == z; }

	boolean matchesInstalledCollision(SurfaceCollisionCells other) {
		if (!hasWindow(other.scope, other.baseX, other.baseZ) || !Arrays.deepEquals(view, other.view)
			|| !placements.equals(other.placements) || live.size() != other.live.size()) return false;
		for (Map.Entry<Long, TileContributions> entry : live.entrySet()) {
			TileContributions current = entry.getValue(), expected = other.live.get(entry.getKey());
			if (expected == null || current.flags != expected.flags || current.terrainFlags != expected.terrainFlags
				|| !Arrays.equals(current.counts, expected.counts)) return false;
		}
		return true;
	}

	void stage(String mapScope, Prepared prepared) {
		int currentX = Math.floorDiv(baseX, CELL_SIZE), currentZ = Math.floorDiv(baseZ, CELL_SIZE);
		if (!active() || !mapScope.equals(scope) || Math.abs((long)prepared.cellX - currentX) > 1
			|| Math.abs((long)prepared.cellZ - currentZ) > 1) return;
		int size = World.LOCAL_TILE_COUNT / CELL_SIZE;
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				int cellX = prepared.cellX + dx, cellZ = prepared.cellZ + dz;
				if (cellX >= currentX && cellX < currentX + size
					&& cellZ >= currentZ && cellZ < currentZ + size) continue;
				long key = encode(cellX, cellZ);
				int[] data = prepared.cells[dx * size + dz];
				if (!Arrays.equals(terrain.get(key), data)) {
					terrain.put(key, data);
					incomingCells++;
				}
			}
		}
	}

	static Prepared prepare(int x, int z, int[][] flags) {
		if (Math.floorMod(x, CELL_SIZE) != 0 || Math.floorMod(z, CELL_SIZE) != 0) {
			throw new IllegalArgumentException("Surface collision cells require an aligned world origin");
		}
		int size = World.LOCAL_TILE_COUNT / CELL_SIZE;
		int[][] cells = new int[size * size][CELL_SIZE * CELL_SIZE];
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				int[] cell = cells[dx * size + dz];
				for (int tx = 0; tx < CELL_SIZE; tx++) {
					int[] row = flags[dx * CELL_SIZE + tx];
					for (int tz = 0; tz < CELL_SIZE; tz++) cell[tx * CELL_SIZE + tz] = row[dz * CELL_SIZE + tz];
				}
			}
		}
		return new Prepared(Math.floorDiv(x, CELL_SIZE), Math.floorDiv(z, CELL_SIZE), cells);
	}

	static final class Prepared {
		private final int cellX, cellZ;
		private final int[][] cells;
		private Prepared(int cellX, int cellZ, int[][] cells) {
			this.cellX = cellX;
			this.cellZ = cellZ;
			this.cells = cells;
		}
	}

	void object(int id, int x, int z, int direction, int width, int height, int type, boolean add) {
		if (!updatePlacement(new Placement(baseX + x, baseZ + z, id, direction, false), add)) return;
		objectFlags(x, z, direction, width, height, type, (tx, tz, flag) -> change(tx, tz, flag, add));
	}

	private static void objectFlags(int x, int z, int direction, int width, int height, int type, FlagWriter writer) {
		for (int dx = 0; dx < width; dx++) {
			for (int dz = 0; dz < height; dz++) {
				int atX = x + dx, atZ = z + dz;
				if (type == 1) writer.flag(atX, atZ, CollisionFlag.FULL_BLOCK_C);
				else if (direction == 0) edge(atX, atZ, -1, 0, CollisionFlag.WALL_EAST, CollisionFlag.WALL_WEST, writer);
				else if (direction == 2) edge(atX, atZ, 0, 1, CollisionFlag.WALL_SOUTH, CollisionFlag.WALL_NORTH, writer);
				else if (direction == 4) edge(atX, atZ, 1, 0, CollisionFlag.WALL_WEST, CollisionFlag.WALL_EAST, writer);
				else if (direction == 6) edge(atX, atZ, 0, -1, CollisionFlag.WALL_NORTH, CollisionFlag.WALL_SOUTH, writer);
			}
		}
	}

	void wall(int id, int x, int z, int direction, boolean add) {
		if (!updatePlacement(new Placement(baseX + x, baseZ + z, id, direction, true), add)) return;
		wallFlags(x, z, direction, (tx, tz, flag) -> change(tx, tz, flag, add));
	}

	private static void wallFlags(int x, int z, int direction, FlagWriter writer) {
		if (direction == 0) edge(x, z, 0, -1, CollisionFlag.WALL_NORTH, CollisionFlag.WALL_SOUTH, writer);
		else if (direction == 1) edge(x, z, -1, 0, CollisionFlag.WALL_EAST, CollisionFlag.WALL_WEST, writer);
		else if (direction == 2) writer.flag(x, z, CollisionFlag.FULL_BLOCK_A);
		else if (direction == 3) writer.flag(x, z, CollisionFlag.FULL_BLOCK_B);
	}

	private boolean updatePlacement(Placement placement, boolean add) {
		int copies = placements.getOrDefault(placement, 0);
		if (add) placements.put(placement, copies + 1);
		else if (copies == 0) return false;
		else if (copies == 1) placements.remove(placement);
		else placements.put(placement, copies - 1);
		revision++;
		return true;
	}

	private static void edge(int x, int z, int dx, int dz, int flag, int reciprocal, FlagWriter writer) {
		writer.flag(x, z, flag);
		// Keep the reciprocal contribution world-addressed even at the current window's edge.
		writer.flag(x + dx, z + dz, reciprocal);
	}

	void clearIncomingScenery() { incomingScenery.clear(); }

	boolean hasIncomingWindow(int x, int z) {
		if (!active() || Math.floorMod(x, CELL_SIZE) != 0 || Math.floorMod(z, CELL_SIZE) != 0) return false;
		int nextX = Math.floorDiv(x, CELL_SIZE), nextZ = Math.floorDiv(z, CELL_SIZE);
		int oldX = Math.floorDiv(baseX, CELL_SIZE), oldZ = Math.floorDiv(baseZ, CELL_SIZE);
		int dx = nextX - oldX, dz = nextZ - oldZ;
		if (Math.max(Math.abs(dx), Math.abs(dz)) != 1) return false;
		int width = World.LOCAL_TILE_COUNT / CELL_SIZE;
		for (int cx = nextX; cx < nextX + width; cx++) {
			for (int cz = nextZ; cz < nextZ + width; cz++) {
				if (cx >= oldX && cx < oldX + width && cz >= oldZ && cz < oldZ + width) continue;
				long key = encode(cx, cz);
				if (!terrain.containsKey(key) || !incomingScenery.containsKey(key)) return false;
			}
		}
		return true;
	}

	void invalidateIncomingScenery(int cellX, int cellZ) { incomingScenery.remove(encode(cellX, cellZ)); }

	void publishIncomingScenery(int cellX, int cellZ, int revision, List<World.SurfaceSceneryPlacement> records) {
		IncomingScenery cell = new IncomingScenery(revision, records);
		FlagWriter writer = (x, z, flag) -> cell.blockers.merge(encode(x, z), flag, (first, second) -> first | second);
		for (World.SurfaceSceneryPlacement record : records) {
			if (record.type == 1) {
				if (EntityHandler.getDoorDef(record.id).getDoorType() == 1) {
					wallFlags(record.x, record.z, record.direction, writer);
				}
			} else {
				GameObjectDef definition = EntityHandler.getObjectDef(record.id);
				int type = definition.getType();
				if (type == 1 || type == 2) {
					boolean straight = record.direction == 0 || record.direction == 4;
					int width = straight ? definition.getWidth() : definition.getHeight();
					int height = straight ? definition.getHeight() : definition.getWidth();
					objectFlags(record.x, record.z, record.direction, width, height, type, writer);
				}
			}
		}
		incomingScenery.put(encode(cellX, cellZ), cell);
	}

	int incomingSceneryRevision(int cellX, int cellZ) {
		IncomingScenery cell = incomingScenery.get(encode(cellX, cellZ));
		return cell == null ? 0 : cell.revision;
	}

	int incomingSceneryCount(int cellX, int cellZ) {
		IncomingScenery cell = incomingScenery.get(encode(cellX, cellZ));
		return cell == null ? -1 : cell.records.size();
	}

	int incomingSceneryObjectCount(int x, int z, int id) {
		IncomingScenery cell = incomingScenery.get(encode(Math.floorDiv(x, CELL_SIZE), Math.floorDiv(z, CELL_SIZE)));
		if (cell == null) return -1;
		int count = 0;
		for (World.SurfaceSceneryPlacement record : cell.records) {
			if (record.x == x && record.z == z && record.id == id && record.type == 0) count++;
		}
		return count;
	}

	int incomingFlags(int x, int z) {
		long cellKey = encode(Math.floorDiv(x, CELL_SIZE), Math.floorDiv(z, CELL_SIZE));
		if (!terrain.containsKey(cellKey) || !incomingScenery.containsKey(cellKey)) return -1;
		long tile = encode(x, z);
		int flags = terrainFlags(x, z);
		TileContributions active = live.get(tile);
		if (active != null) flags |= active.flags;
		for (IncomingScenery cell : incomingScenery.values()) flags |= cell.blockers.getOrDefault(tile, 0);
		return flags;
	}

	private static final class IncomingScenery {
		private final int revision;
		private final List<World.SurfaceSceneryPlacement> records;
		private final Map<Long, Integer> blockers = new HashMap<>();

		private IncomingScenery(int revision, List<World.SurfaceSceneryPlacement> records) {
			this.revision = revision;
			this.records = records;
		}
	}

	private interface FlagWriter {
		void flag(int x, int z, int flag);
	}

	private void change(int localX, int localZ, int flag, boolean add) {
		int x = baseX + localX, z = baseZ + localZ;
		long key = encode(x, z);
		boolean visible = view != null && World.isLocalTile(localX, localZ);
		TileContributions cell = live.get(key);
		if (cell == null && add) {
			cell = new TileContributions();
			if (visible) cell.terrainFlags = view[localX][localZ];
			live.put(key, cell);
		}
		if (cell == null) return;
		int bit = Integer.numberOfTrailingZeros(flag);
		if (add) {
			cell.counts[bit]++;
			cell.flags |= flag;
		} else if (cell.counts[bit] > 0 && --cell.counts[bit] == 0) {
			cell.flags &= ~flag;
		}
		if (cell.flags == 0) live.remove(key);
		if (visible) view[localX][localZ] = cell.terrainFlags | cell.flags;
	}

	int terrainFlags(int x, int z) {
		int localX = x - baseX, localZ = z - baseZ;
		if (view != null && World.isLocalTile(localX, localZ)) {
			TileContributions occupied = live.get(encode(x, z));
			return occupied == null ? view[localX][localZ] : occupied.terrainFlags;
		}
		int[] cell = terrain.get(encode(Math.floorDiv(x, CELL_SIZE), Math.floorDiv(z, CELL_SIZE)));
		return cell == null ? CollisionFlag.FULL_BLOCK_C
			: cell[Math.floorMod(x, CELL_SIZE) * CELL_SIZE + Math.floorMod(z, CELL_SIZE)];
	}

	int incomingCells() { return incomingCells; }

	private static long encode(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }

	private static final class TileContributions {
		private final int[] counts = new int[8];
		private int flags;
		private int terrainFlags;

		private TileContributions copy() {
			TileContributions copy = new TileContributions();
			System.arraycopy(counts, 0, copy.counts, 0, counts.length);
			copy.flags = flags;
			copy.terrainFlags = terrainFlags;
			return copy;
		}
	}

	private static final class Placement {
		private final int x, z, id, direction;
		private final boolean wall;

		private Placement(int x, int z, int id, int direction, boolean wall) {
			this.x = x;
			this.z = z;
			this.id = id;
			this.direction = direction;
			this.wall = wall;
		}

		@Override public boolean equals(Object other) {
			if (!(other instanceof Placement)) return false;
			Placement p = (Placement)other;
			return x == p.x && z == p.z && id == p.id && direction == p.direction && wall == p.wall;
		}

		@Override public int hashCode() { return ((((x * 31 + z) * 31 + id) * 31 + direction) * 31) + (wall ? 1 : 0); }
	}
}
