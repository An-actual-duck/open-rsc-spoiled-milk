package com.openrsc.server;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.enums.OpcodeOut;
import com.openrsc.server.net.rsc.struct.outgoing.SceneBaselineStruct.ObjectRecord;
import com.openrsc.server.net.rsc.struct.outgoing.SurfaceSceneryCellStruct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps one viewer's prepared surface scenery current, including changed placements in shared gameplay cells. */
final class SurfaceScenerySubscription {
	private static final int CELL_SIZE = WorldRegionKey.REGION_SIZE;
	private static final int RADIUS = 1;
	private static final int DRAW_RADIUS = 2;
	private static final int MAX_PAGES = 64;
	private static final int PAGES_PER_CELL_PER_TICK = 2;
	private static final boolean PREPARED_ADDITIONS = Boolean.getBoolean("spoiledmilk.browserPreparedSurfaceAdds");
	private static final Comparator<ObjectRecord> ORDER = Comparator
		.comparingInt((ObjectRecord record) -> record.x)
		.thenComparingInt(record -> record.y)
		.thenComparingInt(record -> record.type)
		.thenComparingInt(record -> record.direction)
		.thenComparingInt(record -> record.id);

	final int context;
	final int generation;
	private final int terrainStage;
	private final int targetX, targetY;
	private final int minCellX, minCellY, maxCellX, maxCellY;
	private final Map<Long, Cell> cells = new LinkedHashMap<>();

	SurfaceScenerySubscription(int context, int generation, int terrainStage,
		int targetX, int targetY, int stepX, int stepY, boolean prepareDrawCells) {
		this.context = context;
		this.generation = generation;
		this.terrainStage = terrainStage;
		this.targetX = targetX;
		this.targetY = targetY;
		addChangedCells(RADIUS, stepX, stepY, false);
		if (prepareDrawCells) {
			addChangedCells(RADIUS, stepX, stepY, true);
			addChangedCells(DRAW_RADIUS, stepX, stepY, false);
		}
		int west = Integer.MAX_VALUE, south = Integer.MAX_VALUE;
		int east = Integer.MIN_VALUE, north = Integer.MIN_VALUE;
		for (Cell cell : cells.values()) {
			west = Math.min(west, cell.x);
			south = Math.min(south, cell.y);
			east = Math.max(east, cell.x);
			north = Math.max(north, cell.y);
		}
		minCellX = west;
		minCellY = south;
		maxCellX = east;
		maxCellY = north;
	}

	private void addChangedCells(int radius, int stepX, int stepY, boolean retainedGameplay) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				int previousDistance = Math.max(Math.abs(dx + stepX), Math.abs(dy + stepY));
				boolean changes = radius == RADIUS ? (previousDistance <= RADIUS) == retainedGameplay
					: Math.max(Math.abs(dx), Math.abs(dy)) == DRAW_RADIUS
						&& (previousDistance > DRAW_RADIUS || previousDistance <= RADIUS);
				if (!changes) continue;
				int x = targetX + dx, y = targetY + dy;
				cells.put(cellKey(x, y), new Cell(x, y, PREPARED_ADDITIONS && radius == RADIUS && !retainedGameplay));
			}
		}
	}

	boolean matchesTarget(int x, int y) {
		return x == targetX && y == targetY;
	}

	void update(Player viewer) {
		for (Cell cell : cells.values()) cell.current.clear();
		List<GameObject> objects = viewer.getWorld().getRegionManager().snapshotSurfaceScenery(
			viewer, minCellX, minCellY, maxCellX, maxCellY);
		for (GameObject object : objects) {
			Cell cell = cells.get(cellKey(Math.floorDiv(object.getX(), CELL_SIZE), Math.floorDiv(object.getY(), CELL_SIZE)));
			if (cell == null) continue;
			cell.current.add(new ObjectRecord(object.getID(), object.getX(), object.getY(),
				object.getDirection(), object.getType()));
		}
		for (Cell cell : cells.values()) {
			// Match the legacy entering additions, which follow spatial index insertion order.
			if (!cell.orderedAdditions) cell.current.sort(ORDER);
			if (cell.sent == null || !sameContents(cell.current, cell.sent)) {
				cell.sent = new ArrayList<>(cell.current);
				cell.revision++;
				cell.nextPage = 0;
			}
			sendPages(viewer, cell);
		}
	}

	private static long cellKey(int x, int y) { return ((long)x << 32) | (y & 0xffffffffL); }

	private void sendPages(Player viewer, Cell cell) {
		int pageSize = SurfaceSceneryCellStruct.PAGE_SIZE;
		int pages = Math.max(1, (cell.sent.size() + pageSize - 1) / pageSize);
		boolean oversize = pages > MAX_PAGES;
		int transmissiblePages = oversize ? 1 : pages;
		for (int budget = PAGES_PER_CELL_PER_TICK; budget > 0 && cell.nextPage < transmissiblePages; budget--) {
			SurfaceSceneryCellStruct packet = new SurfaceSceneryCellStruct();
			packet.context = context;
			packet.generation = generation;
			packet.terrainStage = terrainStage;
			packet.cellX = cell.x;
			packet.cellY = cell.y;
			packet.revision = cell.revision;
			packet.page = cell.nextPage;
			packet.pages = oversize ? 0 : pages;
			int from = cell.nextPage * pageSize;
			packet.records = oversize ? Collections.emptyList()
				: cell.sent.subList(from, Math.min(from + pageSize, cell.sent.size()));
			if (!ActionSender.tryFinalizeAndSendPacketChecked(OpcodeOut.SEND_SURFACE_SCENERY_CELL, packet, viewer)) return;
			cell.nextPage++;
		}
	}

	private static boolean sameContents(List<ObjectRecord> first, List<ObjectRecord> second) {
		if (first.size() != second.size()) return false;
		for (int i = 0; i < first.size(); i++) {
			if (ORDER.compare(first.get(i), second.get(i)) != 0) return false;
		}
		return true;
	}

	private static final class Cell {
		private final int x, y;
		private final boolean orderedAdditions;
		private final List<ObjectRecord> current = new ArrayList<>();
		private List<ObjectRecord> sent;
		private int revision, nextPage;

		private Cell(int x, int y, boolean orderedAdditions) {
			this.x = x;
			this.y = y;
			this.orderedAdditions = orderedAdditions;
		}
	}
}
