package com.openrsc.server;

import com.openrsc.server.model.Point;
import com.openrsc.server.net.rsc.struct.outgoing.LayeredSceneContextStruct;
import com.openrsc.server.net.rsc.struct.outgoing.LayeredSceneTerrainChunkStruct;

import java.util.Arrays;

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
		testPredictionAtCoveredExit();
		System.out.println("PASS " + (samples + 1) + " covered walking bounds checks");
	}

	private static void testPredictionAtCoveredExit() {
		// Near Falador the player released center (6,10) at (360,487).
		// The later destination lay in (7,9), still within the covered Y band.
		checkPrediction(350, 491, 7, 10,
			Point.location(358, 488), Point.location(360, 487), Point.location(381, 472));
		checkPrediction(350, 459, 7, 9,
			Point.location(359, 456), Point.location(360, 455), Point.location(374, 450));
		checkPrediction(270, 460, 5, 9,
			Point.location(264, 456), Point.location(263, 455), Point.location(250, 451));
		if (GameStateUpdater.selectCoveredWalkingPredictionCenter(350, 491, 6, 10,
				Arrays.asList(Point.location(355, 484), Point.location(359, 479))) != null
			|| GameStateUpdater.selectCoveredWalkingPredictionCenter(301, 491, 6, 10,
				Arrays.asList(Point.location(325, 490), Point.location(360, 487))) != null) {
			throw new AssertionError("An in-window or out-of-lead path acquired a definite prediction");
		}
		System.out.println("PASS covered prediction selects the first real crossing, including a corner");
	}

	private static void checkPrediction(int x, int y, int expectedX, int expectedY, Point... path) {
		int[] actual = GameStateUpdater.selectCoveredWalkingPredictionCenter(x, y, 6, 10, Arrays.asList(path));
		if (!Arrays.equals(actual, new int[] {expectedX, expectedY})) {
			throw new AssertionError("Wrong crossing prediction: " + Arrays.toString(actual));
		}
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
