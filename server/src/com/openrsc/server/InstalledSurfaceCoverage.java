package com.openrsc.server;

import com.openrsc.server.io.NativeLayeredTerrainSector;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.net.rsc.struct.outgoing.LayeredSceneContextStruct;
import com.openrsc.server.net.rsc.struct.outgoing.LayeredSceneTerrainChunkStruct;

/** The last surface window sent to a private preview client. */
final class InstalledSurfaceCoverage {
	private final String manifest;
	private final int sectorX;
	private final int sectorY;
	private final int radius;
	private final int availableSectors;

	InstalledSurfaceCoverage(LayeredSceneContextStruct context) {
		if (context.nativeChunkRadius != 1) {
			throw new IllegalArgumentException("Expected a 3x3 authoritative surface window");
		}
		manifest = context.nativeManifestSha256;
		sectorX = context.nativeCurrentChunkX;
		sectorY = context.nativeCurrentChunkY;
		radius = context.nativeChunkRadius;
		int availability = 0;
		for (LayeredSceneTerrainChunkStruct chunk : context.nativeChunks) {
			if (chunk.available) {
				availability |= sectorBit(chunk.chunkX, chunk.chunkY);
			}
		}
		availableSectors = availability;
	}

	boolean matches(NativeLayeredWorldPackage terrainPackage) {
		return manifest.equals(terrainPackage.getManifestSha256());
	}

	int centerX() {
		return Math.multiplyExact(sectorX, NativeLayeredTerrainSector.SIZE);
	}

	int centerY() {
		return Math.multiplyExact(sectorY, NativeLayeredTerrainSector.SIZE);
	}

	boolean contains(int x, int y) {
		return (availableSectors & tileSectorBit(x, y)) != 0;
	}

	boolean inWindow(int x, int y) {
		return tileSectorBit(x, y) != 0;
	}

	boolean atOuterEdge(int x, int y) {
		final long dx = (long) x - centerX();
		final long dy = (long) y - centerY();
		final int lower = -radius * NativeLayeredTerrainSector.SIZE;
		final int upper = (radius + 1) * NativeLayeredTerrainSector.SIZE - 1;
		return dx == lower || dx == upper || dy == lower || dy == upper;
	}

	private int tileSectorBit(int x, int y) {
		return sectorBit(Math.floorDiv(x, NativeLayeredTerrainSector.SIZE),
			Math.floorDiv(y, NativeLayeredTerrainSector.SIZE));
	}

	private int sectorBit(int x, int y) {
		final long dx = (long) x - sectorX + radius;
		final long dy = (long) y - sectorY + radius;
		final int width = 2 * radius + 1;
		return dx >= 0 && dx < width && dy >= 0 && dy < width ? 1 << (dx * width + dy) : 0;
	}
}
