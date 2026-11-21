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
import java.util.List;

/** Keeps the incoming edge of the next surface window current for one viewer. */
final class SurfaceScenerySubscription {
	private static final int CELL_SIZE = WorldRegionKey.REGION_SIZE;
	private static final int RADIUS = 1;
	private static final int WIDTH = RADIUS * 2 + 1;
	private static final int MAX_PAGES = 64;
	private static final int PAGES_PER_CELL_PER_TICK = 2;
	private static final Comparator<ObjectRecord> ORDER = Comparator
		.comparingInt((ObjectRecord record) -> record.x)
		.thenComparingInt(record -> record.y)
		.thenComparingInt(record -> record.type)
		.thenComparingInt(record -> record.direction)
		.thenComparingInt(record -> record.id);

	final int context;
	final int generation;
	private final int terrainStage;
	private final int minCellX, minCellY, stepX;
	private final Cell[] cells = new Cell[WIDTH];

	SurfaceScenerySubscription(int context, int generation, int terrainStage,
		int targetX, int targetY, int stepX, int stepY) {
		this.context = context;
		this.generation = generation;
		this.terrainStage = terrainStage;
		this.stepX = stepX;
		minCellX = targetX + (stepX == 0 ? -RADIUS : stepX * RADIUS);
		minCellY = targetY + (stepY == 0 ? -RADIUS : stepY * RADIUS);
		for (int i = 0; i < WIDTH; i++) {
			cells[i] = new Cell(minCellX + (stepX == 0 ? i : 0), minCellY + (stepX == 0 ? 0 : i));
		}
	}

	void update(Player viewer) {
		for (Cell cell : cells) cell.current.clear();
		List<GameObject> objects = viewer.getWorld().getRegionManager().snapshotSurfaceScenery(
			viewer, minCellX, minCellY, cells[WIDTH - 1].x, cells[WIDTH - 1].y);
		for (GameObject object : objects) {
			int index = stepX == 0 ? Math.floorDiv(object.getX(), CELL_SIZE) - minCellX
				: Math.floorDiv(object.getY(), CELL_SIZE) - minCellY;
			cells[index].current.add(new ObjectRecord(object.getID(), object.getX(), object.getY(),
				object.getDirection(), object.getType()));
		}
		for (Cell cell : cells) {
			cell.current.sort(ORDER);
			if (cell.sent == null || !sameContents(cell.current, cell.sent)) {
				cell.sent = new ArrayList<>(cell.current);
				cell.revision++;
				cell.nextPage = 0;
			}
			sendPages(viewer, cell);
		}
	}

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
		private final List<ObjectRecord> current = new ArrayList<>();
		private List<ObjectRecord> sent;
		private int revision, nextPage;

		private Cell(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}
}
