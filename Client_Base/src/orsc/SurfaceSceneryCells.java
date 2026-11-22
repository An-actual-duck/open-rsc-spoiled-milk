package orsc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import orsc.graphics.three.World;

/** Receives complete scenery cell revisions under one travel and terrain prediction. */
final class SurfaceSceneryCells {
	static final int VERSION = 1;
	static final int HEADER_BYTES = 31;
	static final int RECORD_BYTES = 12;
	static final int PAGE_SIZE = 256;
	static final int MAX_PAGES = 64;
	private static final int RADIUS = 1;
	private static final int DRAW_RADIUS = 2;
	private final Map<Long, Cell> cells = new HashMap<>();
	private int stage, context, generation;

	void begin(int stage, int context, int generation, int x, int z, int dx, int dz) {
		begin(stage, context, generation, x, z, dx, dz, false);
	}

	void begin(int stage, int context, int generation, int x, int z, int dx, int dz, boolean prepareDrawCells) {
		reset();
		if (generation <= 0 || Math.max(Math.abs((long)dx), Math.abs((long)dz)) != 1) return;
		this.stage = stage;
		this.context = context;
		this.generation = generation;
		int oldX = x - dx, oldZ = z - dz;
		for (int ox = -RADIUS; ox <= RADIUS; ox++) {
			for (int oz = -RADIUS; oz <= RADIUS; oz++) {
				int cx = x + ox, cz = z + oz;
				if (distance(cx, cz, oldX, oldZ) > RADIUS) cells.put(key(cx, cz), new Cell(Band.ENTERING_PLAY));
				else if (prepareDrawCells) cells.put(key(cx, cz), new Cell(Band.RETAINED_PLAY));
			}
		}
		if (!prepareDrawCells) return;
		for (int ox = -DRAW_RADIUS; ox <= DRAW_RADIUS; ox++) {
			for (int oz = -DRAW_RADIUS; oz <= DRAW_RADIUS; oz++) {
				if (Math.max(Math.abs(ox), Math.abs(oz)) != DRAW_RADIUS) continue;
				int cx = x + ox, cz = z + oz;
				int previousDistance = distance(cx, cz, oldX, oldZ);
				if (previousDistance > DRAW_RADIUS) cells.put(key(cx, cz), new Cell(Band.ENTERING_DRAW));
				else if (previousDistance <= RADIUS) cells.put(key(cx, cz), new Cell(Band.LEAVING_PLAY));
			}
		}
	}

	private static int distance(int x, int z, int centerX, int centerZ) {
		return Math.max(Math.abs(x - centerX), Math.abs(z - centerZ));
	}

	Cell cell(int stage, int context, int generation, int x, int z) {
		if (this.stage == 0 || this.stage != stage || this.context != context || this.generation != generation) return null;
		return cells.get(key(x, z));
	}

	void reset() {
		stage = 0;
		cells.clear();
	}

	static long key(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }

	enum Band { ENTERING_PLAY, RETAINED_PLAY, LEAVING_PLAY, ENTERING_DRAW }

	static final class Cell {
		final Band band;
		private int revision, nextPage, pages;
		private List<World.SurfaceSceneryPlacement> records;

		private Cell(Band band) { this.band = band; }

		boolean startsRevision(int revision, int page) {
			return revision > this.revision && page == 0;
		}

		List<World.SurfaceSceneryPlacement> accept(int revision, int page, int pages,
			List<World.SurfaceSceneryPlacement> incoming) {
			if (startsRevision(revision, page)) {
				this.revision = revision;
				this.pages = pages;
				nextPage = 0;
				records = pages == 0 ? null : new ArrayList<>(incoming.size());
			}
			if (records == null || revision != this.revision || pages != this.pages || page != nextPage) return null;
			records.addAll(incoming);
			if (++nextPage != pages) return null;
			List<World.SurfaceSceneryPlacement> complete = records;
			records = null;
			return complete;
		}
	}
}
