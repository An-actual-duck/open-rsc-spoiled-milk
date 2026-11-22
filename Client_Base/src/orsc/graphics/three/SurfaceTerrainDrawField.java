package orsc.graphics.three;

import orsc.NativeLayeredTerrainChunk;
import orsc.NativeLayeredTerrainSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fixed surface-cell storage and one frame's projection of those cells. */
final class SurfaceTerrainDrawField {
	private static final int CELL_UNITS = World.SECTION_SIZE * 128;
	private final Map<Long, Cell> cells = new HashMap<>();
	private final List<Renderer3DWorldChunkFrame.ChunkMesh> draws = new ArrayList<>();
	private final List<Renderer3DWorldChunkFrame.ChunkMesh> publishedDraws = Collections.unmodifiableList(draws);
	private final String packageIdentity;
	final int centerX, centerZ;
	private final boolean withRoofs;

	SurfaceTerrainDrawField(NativeLayeredTerrainSnapshot source, int centerX, int centerZ, boolean withRoofs) {
		packageIdentity = source.packageIdentity();
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.withRoofs = withRoofs;
	}

	SurfaceTerrainDrawField snapshot(NativeLayeredTerrainSnapshot source) {
		SurfaceTerrainDrawField snapshot = new SurfaceTerrainDrawField(source, centerX, centerZ, withRoofs);
		snapshot.cells.putAll(cells);
		snapshot.draws.addAll(draws);
		return snapshot;
	}

	Cell reuse(SurfaceTerrainDrawField previous, int x, int z, String[] footprint) {
		if (previous == null || withRoofs != previous.withRoofs || !packageIdentity.equals(previous.packageIdentity)) return null;
		Cell old = previous.cells.get(key(x, z));
		return old != null && Arrays.equals(old.footprint, footprint) ? old : null;
	}

	Renderer3DWorldChunkFrame.ChunkMesh add(int x, int z, Cell cell) {
		cells.put(key(x, z), cell);
		if (cell.mesh == null) return null;
		Renderer3DWorldChunkFrame.ChunkMesh draw = cell.mesh.rebasePresentation((x - centerX) * CELL_UNITS, (z - centerZ) * CELL_UNITS);
		draws.add(draw);
		return draw;
	}

	boolean matches(NativeLayeredTerrainSnapshot source, int x, int z, boolean withRoofs) {
		return source != null && source.getLevel() == 0 && "global".equals(source.getWorldSpace())
			&& x == centerX && z == centerZ && withRoofs == this.withRoofs && packageIdentity.equals(source.packageIdentity());
	}

	List<Renderer3DWorldChunkFrame.ChunkMesh> draws() { return publishedDraws; }

	static String[] footprint(NativeLayeredTerrainSnapshot snapshot, int logicalX, int logicalZ) {
		String[] footprint = new String[9];
		for (int i = 0; i < snapshot.getChunkSlotCount(); i++) {
			NativeLayeredTerrainChunk chunk = snapshot.getChunkMetadata(i);
			int dx = chunk.getChunkX() - logicalX, dz = chunk.getChunkY() - logicalZ;
			if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1 && chunk.isAvailable()) {
				footprint[(dx + 1) * 3 + dz + 1] = chunk.getSourcePayloadSha256();
			}
		}
		return footprint;
	}

	private static long key(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }

	static final class Cell {
		private final String[] footprint;
		private final Renderer3DWorldChunkFrame.ChunkMesh mesh;

		Cell(String[] footprint, Renderer3DWorldChunkFrame.ChunkMesh mesh) {
			this.footprint = footprint;
			this.mesh = mesh;
		}
	}
}
