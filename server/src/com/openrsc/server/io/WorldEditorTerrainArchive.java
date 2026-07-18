package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.LegacyTerrainSectorAdapter;
import com.openrsc.server.model.world.coordinate.WorldMapSectorId;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

	/** Access to immutable snapshots of the exact 10-byte landscape records used by the server. */
public final class WorldEditorTerrainArchive implements AutoCloseable {
	public static final int REGION_SIZE = 48;
	private static final int CACHE_LIMIT = 16;
	private final ZipFile archive;
	private final Map<String, Sector> cache = new LinkedHashMap<String, Sector>(CACHE_LIMIT, .75f, true) {
		@Override protected boolean removeEldestEntry(Map.Entry<String, Sector> eldest) {
			return size() > CACHE_LIMIT;
		}
	};

	public WorldEditorTerrainArchive(File file) throws IOException { archive = new ZipFile(file); }

	public synchronized Snapshot inspect(int worldX, int worldY, int plane) throws IOException {
		Coordinates c = Coordinates.fromWorld(worldX, worldY, plane);
		String name = "h" + plane + "x" + c.sectorX + "y" + c.sectorY;
		Sector sector = cache.get(name);
		if (sector == null) {
			ZipEntry entry = archive.getEntry(name);
			if (entry == null) throw new IOException("Landscape sector not found: " + name);
			byte[] bytes = new byte[REGION_SIZE * REGION_SIZE * 10];
			try (java.io.InputStream in = archive.getInputStream(entry)) {
				int offset = 0;
				while (offset < bytes.length) {
					int count = in.read(bytes, offset, bytes.length - offset);
					if (count < 0) break;
					offset += count;
				}
				if (offset != bytes.length || in.read() != -1) throw new IOException("Invalid sector size: " + name);
			}
			sector = Sector.unpack(ByteBuffer.wrap(bytes));
			cache.put(name, sector);
		}
		return new Snapshot(c, sector.getTile(c.localX, c.localY));
	}

	@Override public synchronized void close() throws IOException { cache.clear(); archive.close(); }

	public static final class Coordinates {
		public final int worldX, worldY, plane, sectorX, sectorY, localX, localY;
		private Coordinates(int worldX, int worldY, int plane, int sectorX, int sectorY, int localX, int localY) {
			this.worldX=worldX; this.worldY=worldY; this.plane=plane; this.sectorX=sectorX; this.sectorY=sectorY;
			this.localX=localX; this.localY=localY;
		}
		public static Coordinates fromWorld(int x, int y, int plane) {
			if (plane < 0 || plane > 3) throw new IllegalArgumentException("plane outside 0..3");
			if (Math.floorDiv(y, 944) != plane) throw new IllegalArgumentException("world Y and plane disagree");
			int baseY = y - plane * 944;
			return new Coordinates(x, y, plane, Math.floorDiv(x + 2304, REGION_SIZE),
				Math.floorDiv(baseY + 1776, REGION_SIZE), Math.floorMod(x, REGION_SIZE), Math.floorMod(baseY, REGION_SIZE));
		}
		public WorldMapSectorId toWorldMapSectorId() {
			return LegacyTerrainSectorAdapter.fromLegacySector(plane, sectorX, sectorY);
		}
	}

	public static final class Snapshot {
		public final Coordinates coordinates;
		public final int elevation, groundTexture, groundOverlay, roofTexture, horizontalWall, verticalWall, diagonal;
		private Snapshot(Coordinates coordinates, Tile tile) {
			this.coordinates=coordinates; elevation=tile.getGroundElevation(); groundTexture=tile.getGroundTexture();
			groundOverlay=tile.getGroundOverlay(); roofTexture=tile.getRoofTexture(); horizontalWall=tile.getHorizontalWall();
			verticalWall=tile.getVerticalWall(); diagonal=tile.getDiagonalWalls();
		}
		private Snapshot(Coordinates coordinates, int elevation, int groundTexture, int groundOverlay,
			int roofTexture, int horizontalWall, int verticalWall, int diagonal) {
			this.coordinates=coordinates; this.elevation=elevation; this.groundTexture=groundTexture;
			this.groundOverlay=groundOverlay; this.roofTexture=roofTexture; this.horizontalWall=horizontalWall;
			this.verticalWall=verticalWall; this.diagonal=diagonal;
		}
		public Snapshot paint(int fieldMask, int newElevation, int newGroundTexture, int newGroundOverlay,
			int newRoofTexture,int newHorizontalWall,int newVerticalWall,int newDiagonal) {
			return new Snapshot(coordinates,
				(fieldMask & 1) != 0 ? newElevation : elevation,
				(fieldMask & 2) != 0 ? newGroundTexture : groundTexture,
				(fieldMask & 4) != 0 ? newGroundOverlay : groundOverlay,
				(fieldMask & 8) != 0 ? newRoofTexture : roofTexture,
				(fieldMask & 16) != 0 ? newHorizontalWall : horizontalWall,
				(fieldMask & 32) != 0 ? newVerticalWall : verticalWall,
				(fieldMask & 64) != 0 ? newDiagonal : diagonal);
		}
		public boolean sameRawTile(Snapshot other) {
			return other != null && elevation==other.elevation && groundTexture==other.groundTexture
				&& groundOverlay==other.groundOverlay && roofTexture==other.roofTexture
				&& horizontalWall==other.horizontalWall && verticalWall==other.verticalWall && diagonal==other.diagonal;
		}
		public int diagonalDefinitionId() {
			if (diagonal > 0 && diagonal < 12000) return diagonal - 1;
			if (diagonal > 12000 && diagonal < 24000) return diagonal - 12001;
			return -1;
		}
		public String diagonalOrientation() {
			if (diagonal > 0 && diagonal < 12000) return "NW-SE";
			if (diagonal > 12000 && diagonal < 24000) return "NE-SW";
			return "none";
		}
	}
}
