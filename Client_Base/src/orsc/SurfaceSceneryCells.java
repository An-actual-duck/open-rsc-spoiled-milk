package orsc;

import java.util.ArrayList;
import java.util.List;
import orsc.graphics.three.World;

/** Receives complete scenery cell revisions under one travel and terrain prediction. */
final class SurfaceSceneryCells {
	static final int VERSION = 1;
	static final int HEADER_BYTES = 31;
	static final int RECORD_BYTES = 12;
	static final int PAGE_SIZE = 256;
	static final int MAX_PAGES = 64;
	private static final int RADIUS = 1;
	private final Cell[] cells = new Cell[RADIUS * 2 + 1];
	private int stage, context, generation, minX, minZ, stepX;

	void begin(int stage, int context, int generation, int x, int z, int dx, int dz) {
		reset();
		if (generation <= 0 || Math.abs(dx) + Math.abs(dz) != 1) return;
		this.stage = stage;
		this.context = context;
		this.generation = generation;
		stepX = dx;
		minX = x + (dx == 0 ? -RADIUS : dx * RADIUS);
		minZ = z + (dz == 0 ? -RADIUS : dz * RADIUS);
		for (int i = 0; i < cells.length; i++) cells[i] = new Cell();
	}

	Cell cell(int stage, int context, int generation, int x, int z) {
		if (this.stage == 0 || this.stage != stage || this.context != context || this.generation != generation) return null;
		if (stepX == 0 && z != minZ || stepX != 0 && x != minX) return null;
		long index = stepX == 0 ? (long)x - minX : (long)z - minZ;
		return index >= 0 && index < cells.length ? cells[(int)index] : null;
	}

	void reset() {
		stage = 0;
		for (int i = 0; i < cells.length; i++) cells[i] = null;
	}

	static final class Cell {
		private int revision, nextPage, pages;
		private List<World.SurfaceSceneryPlacement> records;

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
