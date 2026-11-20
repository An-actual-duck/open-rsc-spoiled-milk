package com.openrsc.server;

import com.openrsc.server.net.rsc.struct.outgoing.LayeredSceneContextStruct;
import com.openrsc.server.net.rsc.struct.outgoing.LayeredSceneTerrainChunkStruct;

/** Checks when the private preview must release its existing scene center. */
public final class CoveredWalkingBounds {
	public static void main(String[] args) {
		int samples = 0;
		for (int x = -50; x <= 100; x++) {
			for (int y = -50; y <= 100; y++) {
				boolean expected = x >= -24 && x < 72 && y >= -24 && y < 72;
				if (GameStateUpdater.canRetainCoveredWalkingCenter(96 + x, 624 + y, 96, 624) != expected) {
					throw new AssertionError("Wrong coverage decision for " + x + "," + y);
				}
				samples++;
			}
		}
		if (GameStateUpdater.canRetainCoveredWalkingCenter(Integer.MAX_VALUE, 624, Integer.MIN_VALUE, 624)) {
			throw new AssertionError("Wrapped coordinate admitted to coverage");
		}
		testInstalledOverlap();
		System.out.println("PASS " + (samples + 1) + " covered walking bounds checks");
	}

	private static void testInstalledOverlap() {
		LayeredSceneContextStruct context = new LayeredSceneContextStruct();
		context.nativeChunkRadius = 1;
		context.nativeCurrentChunkX = 2;
		context.nativeCurrentChunkY = 13;
		for (int x = 1; x <= 3; x++) {
			for (int y = 12; y <= 14; y++) {
				LayeredSceneTerrainChunkStruct sector = new LayeredSceneTerrainChunkStruct();
				sector.chunkX = x;
				sector.chunkY = y;
				sector.available = x != 1 || y != 12;
				context.nativeChunks.add(sector);
			}
		}
		InstalledSurfaceCoverage installed = new InstalledSurfaceCoverage(context);
		if (installed.centerX() != 96 || installed.centerY() != 624
			|| GameStateUpdater.canRetainCoveredWalkingCenter(168, 624, 96, 624)
			|| !installed.contains(168, 624) || !installed.contains(191, 624)
			|| installed.atOuterEdge(168, 624) || !installed.atOuterEdge(191, 624)
			|| installed.inWindow(192, 624)
			|| !installed.inWindow(48, 576) || installed.contains(48, 576)) {
			throw new AssertionError("Physical coverage must outlive the requested center and exclude void sectors");
		}
		System.out.println("PASS old coverage remains available while an adjacent center is pending");
	}
}
