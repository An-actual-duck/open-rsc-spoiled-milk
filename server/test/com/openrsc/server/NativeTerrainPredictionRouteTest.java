package com.openrsc.server;

import com.openrsc.server.model.Point;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NativeTerrainPredictionRouteTest {
	@Test
	void diagonalDestinationWinsOverTransientCardinalCrossing() {
		assertArrayEquals(
			new int[] {12, 12},
			select(
				574,
				574,
				11,
				11,
				true,
				new Point(575, 576),
				new Point(576, 577)));
	}

	@Test
	void cardinalRouteRetainsItsCardinalTarget() {
		assertArrayEquals(
			new int[] {11, 12},
			select(
				574,
				574,
				11,
				11,
				true,
				new Point(575, 576),
				new Point(575, 584)));
	}

	@Test
	void routeInsideCurrentCenterDoesNotPredict() {
		assertNull(select(
			550,
			550,
			11,
			11,
			true,
			new Point(560, 560),
			new Point(575, 575)));
	}

	@Test
	void waypointBeyondBoundedLeadCannotPullPredictionFurtherAhead() {
		assertArrayEquals(
			new int[] {12, 11},
			select(
				550,
				550,
				11,
				11,
				true,
				new Point(576, 550),
				new Point(599, 550)));
	}

	@Test
	void noWaypointsAndNonAdjacentTargetsRemainUnpredicted() {
		assertNull(GameStateUpdater.selectNativeTerrainPredictionCenter(
			550,
			550,
			11,
			11,
			true,
			Collections.<Point>emptyList()));
		assertNull(select(
				550,
				550,
				10,
				10,
				true,
				new Point(576, 576)));
	}

	@Test
	void legacyWindowStillUsesItsEstablishedMidpointBoundary() {
		assertArrayEquals(
			new int[] {13, 12},
			select(
				575,
				575,
				12,
				12,
				false,
				new Point(608, 575)));
	}

	private static int[] select(
		int playerX,
		int playerY,
		int activeCenterX,
		int activeCenterY,
		boolean centered,
		Point... waypoints) {
		return GameStateUpdater.selectNativeTerrainPredictionCenter(
			playerX,
			playerY,
			activeCenterX,
			activeCenterY,
			centered,
			Arrays.asList(waypoints));
	}
}
