#!/usr/bin/env python3
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
PLAYER_SOURCE = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
CONFIG_SOURCE = ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
COMMAND_SOURCE = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
LOCAL_CONFIG = ROOT / "server/myworld.conf"
HOST_CONFIG = ROOT / "server/myworld-host.conf"
SCHEMA = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v42.schema.json"
SCHEMA_V11 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v11.schema.json"
SCHEMA_V12 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v12.schema.json"
SCHEMA_V13 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v13.schema.json"
SCHEMA_V14 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v14.schema.json"
SCHEMA_V15 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v15.schema.json"
SCHEMA_V16 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v16.schema.json"
SCHEMA_V17 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v17.schema.json"
SCHEMA_V18 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v18.schema.json"
SCHEMA_V21 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v21.schema.json"
SCHEMA_V22 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v22.schema.json"
SCHEMA_V23 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v23.schema.json"
SCHEMA_V24 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v24.schema.json"
SCHEMA_V25 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v25.schema.json"
SCHEMA_V26 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v26.schema.json"
SCHEMA_V27 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v27.schema.json"
SCHEMA_V28 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v28.schema.json"
SCHEMA_V29 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v29.schema.json"
SCHEMA_V30 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v30.schema.json"
SCHEMA_V31 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v31.schema.json"
SCHEMA_V32 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v32.schema.json"
SCHEMA_V33 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v33.schema.json"
SCHEMA_V34 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v34.schema.json"
SCHEMA_V35 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v35.schema.json"
SCHEMA_V36 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v36.schema.json"
SCHEMA_V37 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v37.schema.json"
SCHEMA_V38 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v38.schema.json"
SCHEMA_V39 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v39.schema.json"
SCHEMA_V40 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v40.schema.json"
SCHEMA_V41 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v41.schema.json"


POINT_STUB = r'''
package com.openrsc.server.model;

public class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static Point location(int x, int y) {
        return new Point(x, y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
'''


LOGGER_STUB = r'''
package org.apache.logging.log4j;

public interface Logger {
    void error(String message, Object... arguments);
}
'''


LOG_MANAGER_STUB = r'''
package org.apache.logging.log4j;

public final class LogManager {
    private static final Logger LOGGER = new Logger() {
        @Override
        public void error(String message, Object... arguments) {
        }
    };

    private LogManager() {
    }

    public static Logger getLogger(Class<?> type) {
        return LOGGER;
    }
}
'''


OBSERVER_FIXTURE = r'''
package com.openrsc.server.diagnostics;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LegacyLogicalRegionAssembly;
import com.openrsc.server.model.world.coordinate.LayeredAuthoredPlacementIdentity;
import com.openrsc.server.model.world.coordinate.LayeredCoordinateParitySnapshot;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcResidencyObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcResidencyObservation.NpcInstanceSnapshot;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionActiveNpcBoundaryRequirementProjection;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPlacementDependencyInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPlacementManifest;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPopulationOutcome;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredProvenanceObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionObservation;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionCohortAnalysis;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionCohortAttribution;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredReconstructionRecipe;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionDynamicObjectPreservationRecord;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionPreservationBurdenAssessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementReadiness;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementRefinementProposal;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementRefinementReassessment;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionRetirementSafetyAssessment;
import com.openrsc.server.model.world.coordinate.LayeredRegionInterestOwnershipLedger;
import com.openrsc.server.model.world.coordinate.LayeredRegionResidencyMirror;
import com.openrsc.server.model.world.coordinate.LayeredRegionRetirementDecisionArbiter;
import com.openrsc.server.model.world.coordinate.LayeredRegionRetirementEligibilityLedger;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionInterestDelta;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class LayeredCoordinateParityObserverFixture {
    public static void main(String[] args) {
        System.setProperty(LayeredCoordinateParityObserver.LOG_ROOT_PROPERTY, args[0]);
        LayeredCoordinateParityObserver.resetForTests();

        int[] packedBoundaries = {0, 943, 944, 1887, 1888, 2831, 2832, 3775};
        int[] expectedLevels = {0, 0, 1, 1, 2, 2, -1, -1};
        int[] expectedY = {0, 943, 0, 943, 0, 943, 0, 943};
        for (int index = 0; index < packedBoundaries.length; index++) {
            LayeredCoordinateParitySnapshot snapshot = LayeredCoordinateParitySnapshot.capture(
                Point.location(100, packedBoundaries[index]));
            check(snapshot.getLocation().getCoordinate().getLevel() == expectedLevels[index],
                "boundary level " + packedBoundaries[index]);
            check(snapshot.getLocation().getCoordinate().getY() == expectedY[index],
                "boundary Y " + packedBoundaries[index]);
            check(snapshot.isRoundTripExact(), "boundary round trip " + packedBoundaries[index]);
            check(snapshot.toCompactString().contains("roundTrip=OK"), "compact round trip");
        }
        expectIllegal(() -> LayeredCoordinateParitySnapshot.capture(Point.location(100, 3776)));
        expectNull(() -> LayeredCoordinateParitySnapshot.capture(null));

        LayeredRegionInterestOwnershipLedger ownershipLedger =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionInterestOwnershipLedger.OpenedOwner firstOwner =
            ownershipLedger.openOwner(
                new WorldRegionWindow(WorldSpaceId.GLOBAL, 0, 4, 0, 5, 0), 2);
        LayeredRegionInterestOwnershipLedger.OpenedOwner secondOwner =
            ownershipLedger.openOwner(
                new WorldRegionWindow(WorldSpaceId.GLOBAL, 0, 5, 0, 6, 0), 2);
        LayeredCoordinateParityObserver.InterestOwnershipMetadata sharedOpen =
            LayeredCoordinateParityObserver.InterestOwnershipMetadata.fromChange(
                secondOwner.getChange());
        check(sharedOpen.isOwnerOpen() && sharedOpen.getEnteredCount() == 2,
            "second owner exact open transition");
        check(sharedOpen.getGloballyAcquiredCount() == 1
            && sharedOpen.getSharedAcquisitionCount() == 1,
            "second owner distinguishes global and shared acquisition");
        check(sharedOpen.getMinimumReferenceCount().intValue() == 1
            && sharedOpen.getMaximumReferenceCount().intValue() == 2,
            "exact change carries post-transition owner reference range");
        LayeredCoordinateParityObserver.InterestOwnershipMetadata currentFirst =
            LayeredCoordinateParityObserver.InterestOwnershipMetadata.fromOwnerSnapshot(
                ownershipLedger.snapshotOwner(firstOwner.getOwnerToken()));
        check(currentFirst.isNoOp()
            && currentFirst.getMaximumReferenceCount().intValue() == 2,
            "same-version snapshot captures shared reference count");
        LayeredCoordinateParityObserver.InterestOwnershipMetadata sharedClose =
            LayeredCoordinateParityObserver.InterestOwnershipMetadata.fromChange(
                ownershipLedger.closeOwner(secondOwner.getOwnerToken()));
        check(!sharedClose.isOwnerOpen() && sharedClose.getExitedCount() == 2,
            "close reports a closed owner and every exit");
        check(sharedClose.getGloballyReleasedCount() == 1
            && sharedClose.getSharedReleaseCount() == 1,
            "close distinguishes global and shared release");
        check(sharedClose.getMinimumReferenceCount() == null
            && sharedClose.getMaximumReferenceCount() == null,
            "closed owner has no owned reference range");

        int playerId = 7;
        long usernameHash = 123456789L;
        LayeredCoordinateParityObserver.TileSnapshotSource tileSnapshots =
            key -> key.getLevel() == 0 && key.getRegionY() == 19
                ? LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
                    key, 1, 0, 1536, 2304, false,
                    "0000000000000000000000000000000000000000000000000000000000000000")
                : LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
                    key, key.getLevel() == 1 ? 2 : 1, 0, 2304, 2304, true,
                    "1111111111111111111111111111111111111111111111111111111111111111");
        int[] tileParityCaptures = {0};
        LayeredCoordinateParityObserver.TileParitySource tileParity = current -> {
            tileParityCaptures[0]++;
            return LayeredCoordinateParityObserver.TileParityMetadata.of(
                LayeredCoordinateParitySnapshot.capture(current).getLocation(),
                current, true, false, true, true);
        };
        int[] tileNeighborhoodCaptures = {0};
        LayeredCoordinateParityObserver.TileNeighborhoodSource tileNeighborhood = current -> {
            tileNeighborhoodCaptures[0]++;
            return LayeredCoordinateParityObserver.TileNeighborhoodMetadata.of(
                LayeredCoordinateParitySnapshot.capture(current).getLocation(),
                9, 9, 0, 9, 9, true, true);
        };
        int[] adjacentCollisionCaptures = {0};
        LayeredCoordinateParityObserver.AdjacentCollisionSource adjacentCollision = current -> {
            adjacentCollisionCaptures[0]++;
            WorldLocation center =
                LayeredCoordinateParitySnapshot.capture(current).getLocation();
            return LayeredCoordinateParityObserver.AdjacentCollisionMetadata.of(
                center, openDirections(center));
        };
		int[] traversalCaptures = {0};
		List<Integer> traversalRouteSizes = new ArrayList<Integer>();
		List<Integer> traversalDroppedCounts = new ArrayList<Integer>();
		List<Integer> traversalDiscontinuityCounts = new ArrayList<Integer>();
		LayeredCoordinateParityObserver.TraversalCollisionSource traversalCollision =
			(route, droppedStepCount, discontinuityCount) -> {
				traversalCaptures[0]++;
				traversalRouteSizes.add(Integer.valueOf(route.size()));
				traversalDroppedCounts.add(Integer.valueOf(droppedStepCount));
				traversalDiscontinuityCounts.add(Integer.valueOf(discontinuityCount));
				return LayeredCoordinateParityObserver.RecentTraversalMetadata.of(
					openTraversal(route), droppedStepCount, discontinuityCount);
			};
        int[] regionResidencyCaptures = {0};
        LayeredCoordinateParityObserver.RegionResidencySource regionResidency =
            (previousWindow, currentWindow, maximumRegionsPerWindow) -> {
                regionResidencyCaptures[0]++;
                return residentRegionResidency(
                    previousWindow, currentWindow, maximumRegionsPerWindow);
            };
        expectNull(() -> LayeredCoordinateParityObserver.start(
            99, 99L, Point.location(100, 943), 2, null, tileParity,
            tileNeighborhood, adjacentCollision, traversalCollision, regionResidency));
        expectNull(() -> LayeredCoordinateParityObserver.start(
            99, 99L, Point.location(100, 943), 2, tileSnapshots, null,
            tileNeighborhood, adjacentCollision, traversalCollision, regionResidency));
        expectNull(() -> LayeredCoordinateParityObserver.start(
            99, 99L, Point.location(100, 943), 2, tileSnapshots, tileParity,
            null, adjacentCollision, traversalCollision, regionResidency));
        expectNull(() -> LayeredCoordinateParityObserver.start(
            99, 99L, Point.location(100, 943), 2, tileSnapshots, tileParity,
            tileNeighborhood, null, traversalCollision, regionResidency));
        expectNull(() -> LayeredCoordinateParityObserver.start(
            99, 99L, Point.location(100, 943), 2, tileSnapshots, tileParity,
            tileNeighborhood, adjacentCollision, null, regionResidency));
        expectNull(() -> LayeredCoordinateParityObserver.start(
            99, 99L, Point.location(100, 943), 2, tileSnapshots, tileParity,
            tileNeighborhood, adjacentCollision, traversalCollision, null));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getRegionKey(),
            1, 2, 2304, 2304, true,
            "0000000000000000000000000000000000000000000000000000000000000000"));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getRegionKey(),
            1, 0, 1536, 2304, true,
            "0000000000000000000000000000000000000000000000000000000000000000"));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getRegionKey(),
            1, 0, 1536, 2304, false, "not-a-fingerprint"));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileSnapshotMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getRegionKey(),
            1, 0, 1536, 2303, false,
            "0000000000000000000000000000000000000000000000000000000000000000"));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileParityMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            Point.location(100, 943), true, true, true, true));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileParityMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            null, true, false, true, true));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileParityMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            Point.location(100, 943), true, false, false, false));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileParityMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            null, false, false, false, true));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileNeighborhoodMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            8, 9, 0, 9, 9, false, true));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileNeighborhoodMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            9, 8, 0, 8, 8, false, false));
        expectIllegal(() -> LayeredCoordinateParityObserver.TileNeighborhoodMetadata.of(
            LayeredCoordinateParitySnapshot.capture(Point.location(100, 943)).getLocation(),
            9, 9, 0, 9, 8, true, true));
        expectIllegal(() -> LayeredCoordinateParityObserver.AdjacentDirectionMetadata.of(
            1, 0,
            new WorldLocation(
                LayeredCoordinateParitySnapshot.capture(Point.location(100, 943))
                    .getLocation().getWorldSpace(),
                new WorldCoordinate(101, 943, 0)),
            2, 2, Boolean.TRUE,
            LayeredCoordinateParityObserver.AdjacentBlockingReason.CURRENT_X,
            Boolean.TRUE, LayeredCoordinateParityObserver.AdjacentBlockingReason.NONE));
        check(!LayeredCoordinateParityObserver.status(playerId, usernameHash).isEnabled(),
            "initially disabled");
        LayeredCoordinateParityObserver.Status started = LayeredCoordinateParityObserver.start(
            playerId, usernameHash, Point.location(100, 943), 2, tileSnapshots,
            tileParity, tileNeighborhood, adjacentCollision, traversalCollision,
            regionResidency);
        check(started.isEnabled() && started.getRecordCount() == 1, "start");
        check(started.getError() == null, "start error");
        check(started.getLastSnapshot().getVisibilityWindow().getRegionCount() == 2L,
            "start visibility window");
        expectIllegal(() -> LayeredCoordinateParityObserver.start(
            playerId, usernameHash, Point.location(100, 943), 3, tileSnapshots,
            tileParity, tileNeighborhood, adjacentCollision, traversalCollision,
            regionResidency));

        LayeredCoordinateParityObserver.onLocationChanged(
            playerId, usernameHash, Point.location(100, 943), Point.location(100, 944), false);
        LayeredCoordinateParityObserver.onLocationChanged(
            playerId, usernameHash, Point.location(100, 944), Point.location(100, 2832), true);
        LayeredCoordinateParityObserver.onLocationChanged(
            playerId, usernameHash, Point.location(100, 2832), Point.location(101, 2832), false);
        LayeredCoordinateParityObserver.onLocationChanged(
            playerId, usernameHash, Point.location(100, 2832), Point.location(100, 2832), false);
        LayeredCoordinateParityObserver.mark(
            playerId, usernameHash, Point.location(101, 2832), "after-ladder");
        LayeredCoordinateParityObserver.snapshot(
            playerId, usernameHash, Point.location(101, 2832));
        LayeredCoordinateParityObserver.onSession(
            playerId, usernameHash, Point.location(101, 2832), false);
        WorldRegionWindow reconnectWindow = LayeredCoordinateParitySnapshot.capture(
            Point.location(101, 2832), 2).getVisibilityWindow();
        LayeredRegionInterestOwnershipLedger.OpenedOwner reconnectOwner =
            ownershipLedger.openOwner(reconnectWindow, 4096);
        int[] reconnectOwnershipCaptures = {0};
        LayeredCoordinateParityObserver.InterestOwnershipSource reconnectOwnership =
            (currentWindow, maximumRegionsPerWindow) -> {
                reconnectOwnershipCaptures[0]++;
                LayeredRegionInterestOwnershipLedger.OwnerSnapshot snapshot =
                    ownershipLedger.snapshotOwner(reconnectOwner.getOwnerToken());
                snapshot.requireWindow(currentWindow);
                check(snapshot.getReferences().size() <= maximumRegionsPerWindow,
                    "reconnected ownership budget");
                return LayeredCoordinateParityObserver.InterestOwnershipMetadata
                    .fromOwnerSnapshot(snapshot);
            };
        LayeredCoordinateParityObserver.onSession(
            playerId, usernameHash, Point.location(101, 2832), true, null,
            reconnectOwnership);
        check(reconnectOwnershipCaptures[0] == 1,
            "reconnect replaces the current-owner reader before the login event");
        expectIllegal(() -> LayeredCoordinateParityObserver.mark(
            playerId, usernameHash, Point.location(101, 2832), "unsafe label"));

        LayeredCoordinateParityObserver.Status active =
            LayeredCoordinateParityObserver.status(playerId, usernameHash);
        check(active.isEnabled() && active.getRecordCount() == 8, "active record count");
        check(active.getLastSnapshot().getLocation().getCoordinate().getLevel() == -1,
            "active layered level");

        long otherHash = 987654321L;
        LayeredCoordinateParityObserver.Status other = LayeredCoordinateParityObserver.start(
            playerId, otherHash, Point.location(200, 944), 2, tileSnapshots,
            tileParity, tileNeighborhood, adjacentCollision, traversalCollision,
            regionResidency);
        check(other.isEnabled() && other.getRecordCount() == 1, "identity-isolated start");
        check(!other.getPath().equals(active.getPath()), "identity-isolated path");
        LayeredCoordinateParityObserver.stop(playerId, otherHash, Point.location(200, 944));

        LayeredCoordinateParityObserver.Status stopped = LayeredCoordinateParityObserver.stop(
            playerId, usernameHash, Point.location(101, 2832));
        check(!stopped.isEnabled() && stopped.getRecordCount() == 9, "stop");
        LayeredCoordinateParityObserver.onLocationChanged(
            playerId, usernameHash, Point.location(101, 2832), Point.location(102, 2832), false);
        check(!LayeredCoordinateParityObserver.status(playerId, usernameHash).isEnabled(),
            "movement after stop ignored");

        int decisionPlayerId = 11;
        long decisionHash = 333L;
        Point firstDecisionPoint = Point.location(193, 1);
        Point secondDecisionPoint = Point.location(481, 1);
        WorldRegionWindow firstDecisionWindow =
            LayeredCoordinateParitySnapshot.capture(firstDecisionPoint, 0)
                .getVisibilityWindow();
        WorldRegionWindow secondDecisionWindow =
            LayeredCoordinateParitySnapshot.capture(secondDecisionPoint, 0)
                .getVisibilityWindow();
        LayeredRegionInterestOwnershipLedger decisionOwnership =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionInterestOwnershipLedger.OpenedOwner decisionOwner =
            decisionOwnership.openOwner(firstDecisionWindow, 1);
        LayeredRegionResidencyMirror decisionResidency =
            new LayeredRegionResidencyMirror();
        check(decisionResidency.registerPackedRegion(4, 0),
            "decision first Region resident");
        check(decisionResidency.registerPackedRegion(10, 0),
            "decision second Region resident");
        LayeredRegionRetirementEligibilityLedger decisionRetirement =
            new LayeredRegionRetirementEligibilityLedger(1L);
        long[] decisionTick = {0L};
        decisionRetirement.observeOwnershipChange(
            decisionOwner.getChange(), decisionTick[0]);
        LayeredCoordinateParityObserver.InterestOwnershipSource decisionInterest =
            (currentWindow, maximumRegionsPerWindow) -> {
                LayeredRegionInterestOwnershipLedger.OwnerSnapshot snapshot =
                    decisionOwnership.snapshotOwner(decisionOwner.getOwnerToken());
                snapshot.requireWindow(currentWindow);
                check(snapshot.getReferences().size() <= maximumRegionsPerWindow,
                    "decision interest budget");
                return LayeredCoordinateParityObserver.InterestOwnershipMetadata
                    .fromOwnerSnapshot(snapshot);
            };
        LayeredCoordinateParityObserver.RegionRetirementSource decisionRetirementSource =
            (transitionKeys, trackedCandidateKeys, droppedCandidateCount,
                    maximumRegions) -> {
                LinkedHashSet<WorldRegionKey> observed =
                    new LinkedHashSet<WorldRegionKey>(transitionKeys);
                observed.addAll(trackedCandidateKeys);
                check(observed.size() <= maximumRegions,
                    "decision retirement evidence budget");
                List<LayeredRegionRetirementEligibilityLedger.Snapshot> snapshots =
                    new ArrayList<LayeredRegionRetirementEligibilityLedger.Snapshot>();
                for (WorldRegionKey key : observed) {
                    snapshots.add(decisionRetirement.snapshot(
                        decisionOwnership.snapshot(key),
                        decisionResidency.snapshot(key), decisionTick[0]));
                }
                return LayeredCoordinateParityObserver.RegionRetirementMetadata
                    .fromSnapshots(snapshots, transitionKeys,
                        trackedCandidateKeys, droppedCandidateCount);
            };
        LayeredRegionRetirementDecisionArbiter decisionArbiter =
            new LayeredRegionRetirementDecisionArbiter();
        LayeredCoordinateParityObserver.RegionRetirementDecisionSource
            decisionSource = (candidates, droppedCandidateCount,
                    maximumRegions) -> {
                check(candidates.size() <= maximumRegions,
                    "retirement decision budget");
                List<LayeredRegionRetirementDecisionArbiter.Decision> decisions =
                    new ArrayList<LayeredRegionRetirementDecisionArbiter.Decision>();
                for (LayeredRegionRetirementEligibilityLedger.Snapshot candidate
                        : candidates) {
                    WorldRegionKey key = candidate.getLogicalRegionKey();
                    LayeredRegionRetirementEligibilityLedger.Snapshot current =
                        decisionRetirement.snapshot(
                            decisionOwnership.snapshot(key),
                            decisionResidency.snapshot(key), decisionTick[0]);
                    decisions.add(decisionArbiter.evaluate(candidate, current));
                }
                return LayeredCoordinateParityObserver
                    .RegionRetirementDecisionMetadata.fromDecisions(
                        decisions, droppedCandidateCount);
            };
        LayeredCoordinateParityObserver.PackedRegionRetirementSafetySource
            decisionSafetySource = (readiness, maximumPackedSources) -> {
                check(readiness.getSourceCount() <= maximumPackedSources,
                    "packed retirement safety budget");
                List<LayeredPackedRegionRetirementSafetyAssessment
                    .PackedSourceContents> contents =
                    new ArrayList<LayeredPackedRegionRetirementSafetyAssessment
                        .PackedSourceContents>();
                for (LayeredPackedRegionRetirementReadiness.SourceReadiness source
                        : readiness.getSources()) {
                    contents.add(LayeredPackedRegionRetirementSafetyAssessment
                        .PackedSourceContents.of(
                            source.getPackedRegionX(), source.getPackedRegionY(),
                            true, true, false,
                            source.isReady() ? 0 : 1,
                            source.isReady() ? 2 : 0,
                            source.isReady() ? 3 : 0,
                            source.isReady() ? 4 : 0));
                }
                return LayeredPackedRegionRetirementSafetyAssessment.assess(
                    readiness, contents, decisionTick[0], maximumPackedSources);
            };
        LayeredCoordinateParityObserver.PackedRegionAuthoredConstructionSource
            decisionConstructionSource = (safety, maximumPackedSources) -> {
                LayeredPackedRegionAuthoredConstructionInventory.Builder builder =
                    LayeredPackedRegionAuthoredConstructionInventory.builder(7L);
                for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
                        source : safety.getSources()) {
                    int x = source.getPackedRegionX();
                    int y = source.getPackedRegionY();
                    builder.record(
                        LayeredPackedRegionAuthoredConstructionInventory
                            .ConstructionKind.SCENERY, x, y);
                    builder.record(
                        LayeredPackedRegionAuthoredConstructionInventory
                            .ConstructionKind.SCENERY, x, y);
                    builder.record(
                        LayeredPackedRegionAuthoredConstructionInventory
                            .ConstructionKind.BOUNDARY, x, y);
                    builder.record(
                        LayeredPackedRegionAuthoredConstructionInventory
                            .ConstructionKind.NPC_SPAWN, x, y);
                    builder.record(
                        LayeredPackedRegionAuthoredConstructionInventory
                            .ConstructionKind.GROUND_ITEM_SPAWN, x, y);
                    builder.record(
                        LayeredPackedRegionAuthoredConstructionInventory
                            .ConstructionKind.HARVESTING_SCENERY, x, y);
                }
                return LayeredPackedRegionAuthoredConstructionObservation.observe(
                    builder.build(), safety, maximumPackedSources);
            };
		LayeredCoordinateParityObserver.PackedRegionAuthoredProvenanceSource
			decisionProvenanceSource = safety -> {
				LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
					LayeredPackedRegionAuthoredPlacementManifest.builder(7L);
				for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
						source : safety.getSources()) {
					int x = source.getPackedRegionX();
					int y = source.getPackedRegionY();
					manifest.recordScenery(
						x, y, 100, 100, x * 48, y * 48, 0, 0, null);
				}
				return LayeredPackedRegionAuthoredProvenanceObservation.builder(
					manifest.build(), safety, decisionTick[0]).build();
			};
		LayeredCoordinateParityObserver.PackedRegionAuthoredReconstructionSource
			decisionReconstructionSource = (safety, maximumSafetySources,
					maximumRequirementSources) -> {
				LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
					LayeredPackedRegionAuthoredPlacementManifest.builder(7L);
				LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
					dependencies =
						LayeredPackedRegionAuthoredPlacementDependencyInventory
							.builder(7L);
				for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
						source : safety.getSources()) {
					int x = source.getPackedRegionX();
					int y = source.getPackedRegionY();
					manifest.recordScenery(
						x, y, 100, 100, x * 48, y * 48, 0, 0, null);
					dependencies.record(
						LayeredPackedRegionAuthoredConstructionInventory
							.ConstructionKind.SCENERY,
						LayeredPackedRegionAuthoredPlacementDependencyInventory
							.DependencyKind.OBJECT_FOOTPRINT,
						x, y, x * 48, x * 48, y * 48, y * 48,
						x, x, y, y);
				}
				LayeredPackedRegionAuthoredPlacementManifest builtManifest =
					manifest.build();
				LayeredPackedRegionAuthoredReconstructionRecipe recipe =
					LayeredPackedRegionAuthoredReconstructionRecipe.derive(
						builtManifest, dependencies.build(),
						LayeredPackedRegionAuthoredPopulationOutcome.builder(7L)
							.build(builtManifest));
				return LayeredPackedRegionAuthoredReconstructionObservation.observe(
					recipe, safety, maximumSafetySources,
					maximumRequirementSources);
			};
		LayeredCoordinateParityObserver
			.PackedRegionAuthoredReconstructionCohortSource
				decisionReconstructionCohortSource =
					(safety, maximumCohortSources,
						maximumRequirementSources) -> {
				LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
					LayeredPackedRegionAuthoredPlacementManifest.builder(7L);
				LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
					dependencies =
						LayeredPackedRegionAuthoredPlacementDependencyInventory
							.builder(7L);
				for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
						source : safety.getSources()) {
					int x = source.getPackedRegionX();
					int y = source.getPackedRegionY();
					manifest.recordScenery(
						x, y, 100, 100, x * 48, y * 48, 0, 0, null);
					dependencies.record(
						LayeredPackedRegionAuthoredConstructionInventory
							.ConstructionKind.SCENERY,
						LayeredPackedRegionAuthoredPlacementDependencyInventory
							.DependencyKind.OBJECT_FOOTPRINT,
						x, y, x * 48, x * 48, y * 48, y * 48,
						x, x, y, y);
				}
				LayeredPackedRegionAuthoredPlacementManifest builtManifest =
					manifest.build();
				LayeredPackedRegionAuthoredReconstructionRecipe recipe =
					LayeredPackedRegionAuthoredReconstructionRecipe.derive(
						builtManifest, dependencies.build(),
						LayeredPackedRegionAuthoredPopulationOutcome.builder(7L)
							.build(builtManifest));
				return LayeredPackedRegionAuthoredReconstructionCohortAnalysis
					.analyze(recipe, safety, maximumCohortSources,
						maximumRequirementSources);
			};
		final LayeredPackedRegionAuthoredReconstructionRecipe[]
			decisionAttributionRecipe =
				new LayeredPackedRegionAuthoredReconstructionRecipe[1];
		LayeredCoordinateParityObserver
			.PackedRegionAuthoredReconstructionCohortSource
				wrappedDecisionReconstructionCohortSource =
					(safety, maximumCohortSources,
						maximumRequirementSources) -> {
			LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort =
				decisionReconstructionCohortSource.capture(
					safety, maximumCohortSources, maximumRequirementSources);
			LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
				LayeredPackedRegionAuthoredPlacementManifest.builder(7L);
			LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
				dependencies =
					LayeredPackedRegionAuthoredPlacementDependencyInventory
						.builder(7L);
			for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
					source : safety.getSources()) {
				int x = source.getPackedRegionX();
				int y = source.getPackedRegionY();
				manifest.recordScenery(
					x, y, 100, 100, x * 48, y * 48, 0, 0, null);
				dependencies.record(
					LayeredPackedRegionAuthoredConstructionInventory
						.ConstructionKind.SCENERY,
					LayeredPackedRegionAuthoredPlacementDependencyInventory
						.DependencyKind.OBJECT_FOOTPRINT,
					x, y, x * 48, x * 48, y * 48, y * 48,
					x, x, y, y);
			}
			LayeredPackedRegionAuthoredPlacementManifest builtManifest =
				manifest.build();
			decisionAttributionRecipe[0] =
				LayeredPackedRegionAuthoredReconstructionRecipe.derive(
					builtManifest, dependencies.build(),
					LayeredPackedRegionAuthoredPopulationOutcome.builder(7L)
						.build(builtManifest));
			return cohort;
		};
		LayeredCoordinateParityObserver
			.PackedRegionAuthoredReconstructionCohortAttributionSource
				decisionReconstructionCohortAttributionSource =
					(cohort, maximumEdges, maximumBridgePlacements) ->
			LayeredPackedRegionAuthoredReconstructionCohortAttribution.analyze(
				decisionAttributionRecipe[0], cohort, maximumEdges,
				maximumBridgePlacements);
		LayeredCoordinateParityObserver
			.PackedRegionAuthoredReconstructionDependencySemanticsSource
				decisionReconstructionDependencySemanticsSource =
					(safety, maximumSelectedSources, maximumSupportSources,
						maximumIncomingOwners, maximumIncomingPlacements) ->
			LayeredPackedRegionAuthoredReconstructionDependencySemanticsAnalysis
				.analyze(
					decisionAttributionRecipe[0], safety,
					maximumSelectedSources, maximumSupportSources,
					maximumIncomingOwners, maximumIncomingPlacements);
		LayeredPackedRegionAuthoredPlacementManifest.Builder activeNpcManifest =
			LayeredPackedRegionAuthoredPlacementManifest.builder(7L);
		activeNpcManifest.recordNpcSpawn(
			5, 0, 10, 241, 1, 192, 241, 0, 47);
		LayeredAuthoredPlacementIdentity externalNpcIdentity =
			activeNpcManifest.getLastRecordedIdentity();
		LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
			activeNpcDependencies =
				LayeredPackedRegionAuthoredPlacementDependencyInventory
					.builder(7L);
		activeNpcDependencies.record(
			LayeredPackedRegionAuthoredConstructionInventory
				.ConstructionKind.NPC_SPAWN,
			LayeredPackedRegionAuthoredPlacementDependencyInventory
				.DependencyKind.NPC_ROAMING,
			5, 0, 192, 241, 0, 47, 4, 5, 0, 0);
		LayeredPackedRegionAuthoredPlacementManifest builtActiveNpcManifest =
			activeNpcManifest.build();
		LayeredPackedRegionAuthoredReconstructionRecipe activeNpcRecipe =
			LayeredPackedRegionAuthoredReconstructionRecipe.derive(
				builtActiveNpcManifest, activeNpcDependencies.build(),
				LayeredPackedRegionAuthoredPopulationOutcome.builder(7L)
					.build(builtActiveNpcManifest));
		List<NpcInstanceSnapshot> activeNpcCensus = Collections.singletonList(
			new NpcInstanceSnapshot(
				externalNpcIdentity, 10, 4, 0, true));
		LayeredCoordinateParityObserver.PackedRegionActiveNpcResidencySource
			decisionActiveNpcResidencySource =
				(safety, maximumInstances, maximumRelevantDetails) ->
			LayeredPackedRegionActiveNpcResidencyObservation.observe(
				activeNpcRecipe, safety, 3L, activeNpcCensus, maximumInstances,
				maximumRelevantDetails);
		final int[] reassessmentAttempts = {0};
		LayeredCoordinateParityObserver
			.PackedRegionRetirementRefinementReassessmentSource
				decisionRefinementReassessmentSource =
					(previous, maximumCandidates, maximumSupport,
						maximumInstances, maximumRelevantDetails,
						maximumActiveRequirements) -> {
			if (reassessmentAttempts[0]++ == 0) {
				return null;
			}
			long freshTick = Math.max(
				previous.getSafetyObservedAtTick(),
				previous.getCensusObservedAtTick()) + 1L;
			List<LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents>
				contents = new ArrayList<
					LayeredPackedRegionRetirementSafetyAssessment
						.PackedSourceContents>();
			LayeredPackedRegionAuthoredPlacementManifest.Builder manifest =
				LayeredPackedRegionAuthoredPlacementManifest.builder(
					previous.getGeneration());
			LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
				dependencies =
					LayeredPackedRegionAuthoredPlacementDependencyInventory.builder(
						previous.getGeneration());
			for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource
					candidate : previous.getCandidates()) {
				int x = candidate.getPackedRegionX();
				int y = candidate.getPackedRegionY();
				contents.add(
					LayeredPackedRegionRetirementSafetyAssessment
						.PackedSourceContents.of(
							x, y, true, true, false, 0, 0, 0, 0));
				manifest.recordScenery(
					x, y, 100, 100, x * 48, y * 48, 0, 0, null);
				dependencies.record(
					LayeredPackedRegionAuthoredConstructionInventory
						.ConstructionKind.SCENERY,
					LayeredPackedRegionAuthoredPlacementDependencyInventory
						.DependencyKind.OBJECT_FOOTPRINT,
					x, y, x * 48, x * 48, y * 48, y * 48,
					x, x, y, y);
			}
			LayeredPackedRegionRetirementSafetyAssessment freshSafety =
				LayeredPackedRegionRetirementSafetyAssessment
					.assessDiagnosticSelection(
						contents, freshTick, maximumCandidates);
			LayeredPackedRegionAuthoredPlacementManifest builtManifest =
				manifest.build();
			LayeredPackedRegionAuthoredReconstructionRecipe recipe =
				LayeredPackedRegionAuthoredReconstructionRecipe.derive(
					builtManifest, dependencies.build(),
					LayeredPackedRegionAuthoredPopulationOutcome
						.builder(previous.getGeneration()).build(builtManifest));
			LayeredPackedRegionAuthoredReconstructionCohortAnalysis cohort =
				LayeredPackedRegionAuthoredReconstructionCohortAnalysis.analyze(
					recipe, freshSafety, maximumCandidates, maximumSupport);
			LayeredPackedRegionActiveNpcResidencyObservation freshActiveNpc =
				LayeredPackedRegionActiveNpcResidencyObservation.observe(
					recipe, freshSafety, freshTick,
					Collections.<NpcInstanceSnapshot>emptyList(),
					maximumInstances, maximumRelevantDetails);
			return LayeredPackedRegionRetirementRefinementReassessment.reassess(
				previous, freshSafety, cohort,
				LayeredPackedRegionActiveNpcBoundaryRequirementProjection.project(
					freshActiveNpc, maximumActiveRequirements),
				maximumCandidates, maximumSupport);
		};
		LayeredCoordinateParityObserver.PackedRegionPreservationBurdenSource
			decisionPreservationBurdenSource =
				(proposal, maximumCandidates) -> {
			List<LayeredPackedRegionRetirementSafetyAssessment.PackedSourceContents>
				contents = new ArrayList<
					LayeredPackedRegionRetirementSafetyAssessment
						.PackedSourceContents>();
			List<LayeredPackedRegionPreservationBurdenAssessment.PackedSourceInventory>
				inventories = new ArrayList<
					LayeredPackedRegionPreservationBurdenAssessment
						.PackedSourceInventory>();
			for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource
					candidate : proposal.getCandidates()) {
				int x = candidate.getPackedRegionX();
				int y = candidate.getPackedRegionY();
				int dynamicObjectCount = x == 4 && y == 0 ? 1 : 0;
				contents.add(LayeredPackedRegionRetirementSafetyAssessment
					.PackedSourceContents.of(
						x, y, true, true, false, 0, 0,
						dynamicObjectCount, 0));
				inventories.add(LayeredPackedRegionPreservationBurdenAssessment
					.currentRuntimeInventory(
						x, y, 0, dynamicObjectCount, 0, 0));
			}
			long observedAtTick = Math.max(
				proposal.getSafetyObservedAtTick(), decisionTick[0]);
			LayeredPackedRegionRetirementSafetyAssessment safety =
				LayeredPackedRegionRetirementSafetyAssessment
					.assessDiagnosticSelection(
						contents, observedAtTick, maximumCandidates);
			return LayeredPackedRegionPreservationBurdenAssessment.assess(
				safety, inventories, observedAtTick, maximumCandidates);
		};
		LayeredCoordinateParityObserver
			.PackedRegionDynamicObjectPreservationSource
				decisionDynamicObjectPreservationSource =
					(proposal, maximumCandidates, maximumDynamicObjects) -> {
			List<LayeredPackedRegionDynamicObjectPreservationRecord
				.PackedSourceCapture> captures = new ArrayList<
					LayeredPackedRegionDynamicObjectPreservationRecord
						.PackedSourceCapture>();
			for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource
					candidate : proposal.getCandidates()) {
				int x = candidate.getPackedRegionX();
				int y = candidate.getPackedRegionY();
				List<LayeredPackedRegionDynamicObjectPreservationRecord
					.DynamicObjectState> objects = new ArrayList<
						LayeredPackedRegionDynamicObjectPreservationRecord
							.DynamicObjectState>();
				if (x == 4 && y == 0) {
					objects.add(LayeredPackedRegionDynamicObjectPreservationRecord
						.DynamicObjectState.of(
							64, 63, x * 48 + 1, y * 48 + 2, 4, 0,
							"private-owner", 2));
				}
				captures.add(LayeredPackedRegionDynamicObjectPreservationRecord
					.PackedSourceCapture.of(x, y, true, objects));
			}
			long observedAtTick = Math.max(
				proposal.getSafetyObservedAtTick(), decisionTick[0]);
			return LayeredPackedRegionDynamicObjectPreservationRecord.record(
				proposal.getGeneration(), observedAtTick, captures,
				maximumCandidates, maximumDynamicObjects);
		};
		LayeredCoordinateParityObserver.start(
			decisionPlayerId, decisionHash, firstDecisionPoint, 0, tileSnapshots,
			tileParity, tileNeighborhood, adjacentCollision, traversalCollision,
			regionResidency, decisionInterest, decisionRetirementSource,
			decisionSource, decisionSafetySource, decisionConstructionSource,
			decisionProvenanceSource, decisionReconstructionSource,
			wrappedDecisionReconstructionCohortSource,
			decisionReconstructionCohortAttributionSource, null,
			decisionReconstructionDependencySemanticsSource,
			decisionActiveNpcResidencySource,
			decisionRefinementReassessmentSource,
			decisionPreservationBurdenSource,
			decisionDynamicObjectPreservationSource,
			(proposal, maximumEvents, maximumReferences) -> {
				List<LayeredPackedRegionEventOwnershipInventory.PackedSource>
					sources = new ArrayList<
						LayeredPackedRegionEventOwnershipInventory.PackedSource>();
				for (LayeredPackedRegionRetirementRefinementProposal.CandidateSource
						candidate : proposal.getCandidates()) {
					sources.add(LayeredPackedRegionEventOwnershipInventory.PackedSource
						.of(candidate.getPackedRegionX(), candidate.getPackedRegionY()));
				}
				List<LayeredPackedRegionEventOwnershipInventory.EventState> states =
					Arrays.asList(
						LayeredPackedRegionEventOwnershipInventory.EventState.of(
							0, 101L,
							LayeredPackedRegionEventOwnershipInventory.OwnerKind.NONE,
							LayeredPackedRegionEventOwnershipInventory.AttributionKind
								.EXACT_SPATIAL,
							true, 5L, 0,
							Collections.singletonList(
								LayeredPackedRegionEventOwnershipInventory
									.SpatialReference.of(
										LayeredPackedRegionEventOwnershipInventory
											.SpatialRole.FIXED_EFFECT_LOCATION,
										193, 2)),
							LayeredPackedRegionEventOwnershipInventory
								.EventRestorationState.scenerySpawn(
									LayeredPackedRegionEventOwnershipInventory
										.SceneryRestorationState.of(
											310, 310, 193, 2, 0, 0,
											"private-event-owner", 0,
											LayeredPackedRegionEventOwnershipInventory
												.AuthoredPlacementRestorationState.of(
													7L, 4, 0, 42,
													LayeredPackedRegionEventOwnershipInventory
														.AuthoredConstructionKind.SCENERY)),
									true,
								LayeredPackedRegionEventOwnershipInventory
									.ExecutionSemantics.ONE_SHOT,
								LayeredPackedRegionEventOwnershipInventory
									.TimeProgressionPolicy.CONTINUE_SERVER_TICKS),
							true),
						LayeredPackedRegionEventOwnershipInventory.EventState.of(
							1, 102L,
							LayeredPackedRegionEventOwnershipInventory.OwnerKind.NONE,
							LayeredPackedRegionEventOwnershipInventory.AttributionKind
								.UNATTRIBUTED,
							true, 10L, 1, Collections.emptyList()));
				return LayeredPackedRegionEventOwnershipInventory.inventory(
					proposal.getGeneration(),
					Math.max(proposal.getSafetyObservedAtTick(), decisionTick[0]),
					"00000000-0000-0000-0000-000000000011",
					sources, states, sources.size(), maximumEvents,
					maximumReferences);
			});
        decisionTick[0] = 1L;
        LayeredRegionInterestOwnershipLedger.Change decisionRelease =
            decisionOwnership.synchronizeOwner(
                decisionOwner.getOwnerToken(), secondDecisionWindow, 1);
        decisionRetirement.observeOwnershipChange(
            decisionRelease, decisionTick[0]);
        LayeredCoordinateParityObserver.onLocationChanged(
            decisionPlayerId, decisionHash, firstDecisionPoint,
            secondDecisionPoint, true, decisionRelease);
        decisionTick[0] = 2L;
        LayeredCoordinateParityObserver.mark(
            decisionPlayerId, decisionHash, secondDecisionPoint,
            "eligible-decision");
        LayeredRegionInterestOwnershipLedger.Change decisionReacquire =
            decisionOwnership.synchronizeOwner(
                decisionOwner.getOwnerToken(), firstDecisionWindow, 1);
        decisionRetirement.observeOwnershipChange(
            decisionReacquire, decisionTick[0]);
        LayeredCoordinateParityObserver.onLocationChanged(
            decisionPlayerId, decisionHash, secondDecisionPoint,
            firstDecisionPoint, true, decisionReacquire);
        LayeredCoordinateParityObserver.mark(
            decisionPlayerId, decisionHash, firstDecisionPoint,
            "refusal-pruned");
        LayeredCoordinateParityObserver.stop(
            decisionPlayerId, decisionHash, firstDecisionPoint);

        LayeredCoordinateParityObserver.Status invalid = LayeredCoordinateParityObserver.start(
            8, 111L, Point.location(100, 3776), 2, tileSnapshots, tileParity,
            tileNeighborhood, adjacentCollision, traversalCollision, regionResidency);
        check(invalid.isEnabled() && invalid.getRecordCount() == 0, "invalid trace retained");
        check(invalid.getError() != null && invalid.getError().contains("IllegalArgumentException"),
            "invalid trace visible error");

		int routePlayerId = 10;
		long routeHash = 222L;
		LayeredCoordinateParityObserver.start(
			routePlayerId, routeHash, Point.location(300, 500), 2, tileSnapshots,
			tileParity, tileNeighborhood, adjacentCollision, traversalCollision,
			regionResidency);
		for (int step = 0; step < 18; step++) {
			LayeredCoordinateParityObserver.onLocationChanged(
				routePlayerId, routeHash,
				Point.location(300 + step, 500),
				Point.location(301 + step, 500), false);
		}
		LayeredCoordinateParityObserver.mark(
			routePlayerId, routeHash, Point.location(318, 500), "bounded-route");
		LayeredCoordinateParityObserver.onLocationChanged(
			routePlayerId, routeHash,
			Point.location(318, 500), Point.location(400, 500), false);
		LayeredCoordinateParityObserver.onLocationChanged(
			routePlayerId, routeHash,
			Point.location(400, 500), Point.location(401, 500), false);
		LayeredCoordinateParityObserver.mark(
			routePlayerId, routeHash, Point.location(401, 500), "after-gap");

        check(tileParityCaptures[0] == 15, "bounded tile parity capture count");
        check(tileNeighborhoodCaptures[0] == 15,
            "bounded tile neighborhood capture count");
        check(adjacentCollisionCaptures[0] == 15,
            "bounded adjacent collision capture count");
        check(traversalCaptures[0] == 3,
            "bounded recent traversal capture count");
		check(regionResidencyCaptures[0] == 21,
			"bounded Region residency capture count");
		check(traversalRouteSizes.equals(Arrays.asList(2, 17, 2)),
			"bounded route location counts");
		check(traversalDroppedCounts.equals(Arrays.asList(0, 2, 0)),
			"bounded route dropped counts");
		check(traversalDiscontinuityCounts.equals(Arrays.asList(0, 0, 1)),
			"bounded route discontinuity counts");
        LayeredCoordinateParityObserver.resetForTests();
    }

    private static List<LayeredCoordinateParityObserver.AdjacentDirectionMetadata>
            openDirections(WorldLocation center) {
        List<LayeredCoordinateParityObserver.AdjacentDirectionMetadata> directions =
            new ArrayList<LayeredCoordinateParityObserver.AdjacentDirectionMetadata>();
        WorldCoordinate coordinate = center.getCoordinate();
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                if (offsetX == 0 && offsetY == 0) {
                    continue;
                }
                int required = offsetX == 0 || offsetY == 0 ? 2
                    : offsetX == 1 && offsetY == -1 ? 5 : 4;
                WorldLocation destination = new WorldLocation(
                    center.getWorldSpace(),
                    new WorldCoordinate(
                        coordinate.getX() + offsetX,
                        coordinate.getY() + offsetY,
                        coordinate.getLevel()));
                directions.add(
                    LayeredCoordinateParityObserver.AdjacentDirectionMetadata.of(
                        offsetX, offsetY, destination, required, required,
                        Boolean.TRUE,
                        LayeredCoordinateParityObserver.AdjacentBlockingReason.NONE,
                        Boolean.TRUE,
                        LayeredCoordinateParityObserver.AdjacentBlockingReason.NONE));
            }
        }
        return directions;
    }

	private static List<LayeredCoordinateParityObserver.TraversalStepMetadata>
			openTraversal(List<WorldLocation> route) {
		List<LayeredCoordinateParityObserver.TraversalStepMetadata> steps =
			new ArrayList<LayeredCoordinateParityObserver.TraversalStepMetadata>();
		for (int index = 1; index < route.size(); index++) {
			WorldLocation source = route.get(index - 1);
			WorldLocation destination = route.get(index);
			int offsetX = destination.getCoordinate().getX()
				- source.getCoordinate().getX();
			int offsetY = destination.getCoordinate().getY()
				- source.getCoordinate().getY();
			int required = offsetX == 0 || offsetY == 0 ? 2
				: offsetX == 1 && offsetY == -1 ? 5 : 4;
			steps.add(LayeredCoordinateParityObserver.TraversalStepMetadata.of(
				index - 1, source, offsetX, offsetY, destination,
				required, required, Boolean.TRUE,
				LayeredCoordinateParityObserver.AdjacentBlockingReason.NONE,
				Boolean.TRUE,
				LayeredCoordinateParityObserver.AdjacentBlockingReason.NONE));
		}
		return steps;
	}

    private static LayeredCoordinateParityObserver.RegionResidencyMetadata
            residentRegionResidency(
                WorldRegionWindow previousWindow,
                WorldRegionWindow currentWindow,
                int maximumRegionsPerWindow) {
        WorldRegionInterestDelta delta = WorldRegionInterestDelta.between(
            previousWindow, currentWindow, maximumRegionsPerWindow);
        List<LayeredCoordinateParityObserver.RegionResidencyCandidateMetadata>
            releases = new ArrayList<>();
        for (WorldRegionKey key : delta.getExited()) {
            LegacyLogicalRegionAssembly assembly =
                LegacyLogicalRegionAssembly.fromLogicalRegionKey(key);
            if (!assembly.isUnsupported()) {
                releases.add(regionCandidate(
                    key, LayeredCoordinateParityObserver.RegionInterestState.EXITED,
                    LayeredCoordinateParityObserver.RegionResidencyState.RESIDENT,
                    assembly, true));
            }
        }
        List<LayeredCoordinateParityObserver.RegionResidencyCandidateMetadata>
            unsupported = new ArrayList<>();
        int residentCurrent = 0;
        for (WorldRegionKey key : WorldRegionInterestDelta.materializeKeys(
                currentWindow, maximumRegionsPerWindow)) {
            LegacyLogicalRegionAssembly assembly =
                LegacyLogicalRegionAssembly.fromLogicalRegionKey(key);
            if (assembly.isUnsupported()) {
                LayeredCoordinateParityObserver.RegionInterestState interest =
                    delta.getEntered().contains(key)
                        ? LayeredCoordinateParityObserver.RegionInterestState.ENTERED
                        : LayeredCoordinateParityObserver.RegionInterestState.RETAINED;
                unsupported.add(regionCandidate(
                    key, interest,
                    LayeredCoordinateParityObserver.RegionResidencyState.UNSUPPORTED,
                    assembly, false));
            } else {
                residentCurrent++;
            }
        }
        return LayeredCoordinateParityObserver.RegionResidencyMetadata.of(
            1L,
            delta.getRetained().size() + delta.getExited().size(),
            delta.getEntered().size() + delta.getRetained().size(),
            delta.getEntered().size(), delta.getRetained().size(),
            delta.getExited().size(), delta.changesWorldSpace(),
            delta.changesLevel(), delta.isNoOp(), residentCurrent, 0, 0,
            new ArrayList<LayeredCoordinateParityObserver.RegionResidencyCandidateMetadata>(),
            releases, unsupported);
    }

    private static LayeredCoordinateParityObserver.RegionResidencyCandidateMetadata
            regionCandidate(
                WorldRegionKey key,
                LayeredCoordinateParityObserver.RegionInterestState interest,
                LayeredCoordinateParityObserver.RegionResidencyState residency,
                LegacyLogicalRegionAssembly assembly,
                boolean resident) {
        return LayeredCoordinateParityObserver.RegionResidencyCandidateMetadata.of(
            key, interest, residency, assembly.getSourceFragments().size(),
            resident ? assembly.getSourceFragments().size() : 0,
            assembly.getAssembledTileCount(),
            resident ? assembly.getAssembledTileCount() : 0L,
            assembly.isComplete());
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceElevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-eleven-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        sources = {
            "com/openrsc/server/model/Point.java": POINT_STUB,
            "org/apache/logging/log4j/Logger.java": LOGGER_STUB,
            "org/apache/logging/log4j/LogManager.java": LOG_MANAGER_STUB,
            "com/openrsc/server/diagnostics/LayeredCoordinateParityObserverFixture.java":
                OBSERVER_FIXTURE,
        }
        fixture_sources = []
        for relative, content in sources.items():
            path = cls.temp / "src" / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            fixture_sources.append(str(path))

        subprocess.run(
            [
                "javac",
                "-Xlint:all",
                "-source",
                "8",
                "-target",
                "8",
                "-encoding",
                "UTF-8",
                "-d",
                str(cls.classes),
                *fixture_sources,
                *(str(path) for path in sorted(SERVER_COORDINATES.glob("*.java"))),
                str(OBSERVER),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_snapshot_and_jsonl_observer_cover_boundaries_transitions_and_identity(self):
        with tempfile.TemporaryDirectory(prefix="layered-parity-jsonl-") as log_dir:
            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(self.classes),
                    "com.openrsc.server.diagnostics.LayeredCoordinateParityObserverFixture",
                    log_dir,
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, result.returncode, result.stderr)

            logs = sorted(Path(log_dir).glob("*.jsonl"))
            self.assertEqual(4, len(logs))
            primary = next(path for path in logs if "123456789" in path.name)
            events = [json.loads(line) for line in primary.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(
                [
                    "start", "move", "teleport", "move", "marker",
                    "snapshot", "logout", "login", "stop",
                ],
                [event["eventType"] for event in events],
            )
            self.assertEqual(list(range(1, 10)), [event["sequence"] for event in events])
            self.assertTrue(all(event["roundTripExact"] for event in events))
            self.assertEqual("after-ladder", events[4]["label"])
            self.assertEqual(1, events[1]["delta"]["level"])
            self.assertEqual(-2, events[2]["delta"]["level"])
            self.assertEqual(-1, events[2]["to"]["layered"]["level"])
            self.assertEqual({"x": 2, "y": 0}, events[2]["to"]["region"])
            self.assertTrue(all(event["schema"] == "layered-map-parity-event-v42" for event in events))
            self.assertTrue(all(
                event["packedRegionPreservationBurden"] is None
                for event in events
            ))
            self.assertTrue(all(
                event["packedRegionDynamicObjectPreservation"] is None
                for event in events
            ))
            self.assertTrue(all(
                event["packedRegionEventOwnership"] is None
                for event in events
            ))
            self.assertTrue(all(
                event["packedRegionRetirementRefinementReassessment"] is None
                for event in events
            ))
            self.assertTrue(all(
                event["packedRegionAuthoredConstruction"] is None
                for event in events
            ))
            self.assertTrue(all(
                event["packedRegionAuthoredProvenance"] is None
                for event in events
            ))
            self.assertTrue(all(
                event["interestOwnership"] is not None
                for event in events
                if event["eventType"] != "move"
            ))
            self.assertIsNone(events[3]["interestOwnership"])
            self.assertEqual(1, events[0]["interestOwnership"]["ownerSequence"])
            self.assertTrue(events[0]["interestOwnership"]["ownerOpen"])
            self.assertTrue(events[0]["interestOwnership"]["noOp"])
            upper_window = events[1]["to"]["visibilityWindow"]
            self.assertEqual(2, upper_window["gridDistance"])
            self.assertEqual(16, upper_window["tileRadius"])
            self.assertEqual(1, upper_window["level"])
            self.assertEqual({
                "minRegionX": 1,
                "minRegionY": -1,
                "maxRegionX": 2,
                "maxRegionY": 0,
            }, {key: upper_window[key] for key in (
                "minRegionX", "minRegionY", "maxRegionX", "maxRegionY")})
            self.assertEqual(4, upper_window["regionCount"])
            start_coverage = events[0]["packedCoverage"]
            self.assertEqual({
                "packedCellCount": 2,
                "unsupportedPackedCellCount": 0,
                "expectedKeyCount": 2,
                "packedCoverageKeyCount": 4,
                "missingKeyCount": 0,
                "extraKeyCount": 2,
                "exact": False,
            }, {key: start_coverage[key] for key in (
                "packedCellCount",
                "unsupportedPackedCellCount",
                "expectedKeyCount",
                "packedCoverageKeyCount",
                "missingKeyCount",
                "extraKeyCount",
                "exact",
            )})
            self.assertEqual(
                {"worldSpace": "global", "level": 1, "x": 1, "y": 0},
                start_coverage["extraKeys"][0],
            )
            start_tiles = events[0]["tileSnapshot"]
            self.assertEqual({
                "logicalRegion": {"worldSpace": "global", "level": 0, "x": 2, "y": 19},
                "sourceFragmentCount": 1,
                "missingSourceRegionCount": 0,
                "supportedTileCount": 1536,
                "targetTileCount": 2304,
                "complete": False,
                "fingerprint": "0" * 64,
            }, start_tiles)
            upper_coverage = events[1]["packedCoverage"]
            self.assertEqual(2, upper_coverage["missingKeyCount"])
            self.assertEqual(4, upper_coverage["extraKeyCount"])
            self.assertEqual(
                {"worldSpace": "global", "level": 1, "x": 1, "y": -1},
                upper_coverage["missingKeys"][0],
            )
            self.assertEqual(
                {"worldSpace": "global", "level": 0, "x": 1, "y": 19},
                upper_coverage["extraKeys"][0],
            )
            upper_tiles = events[1]["tileSnapshot"]
            self.assertEqual(
                {"worldSpace": "global", "level": 1, "x": 2, "y": 0},
                upper_tiles["logicalRegion"],
            )
            self.assertEqual(2, upper_tiles["sourceFragmentCount"])
            self.assertEqual(2304, upper_tiles["supportedTileCount"])
            self.assertTrue(upper_tiles["complete"])
            self.assertTrue(all(event["tileSnapshot"] is not None for event in events))
            self.assertEqual(
                {"start", "teleport", "marker", "stop"},
                {
                    event["eventType"]
                    for event in events
                    if event["tileParity"] is not None
                },
            )
            self.assertTrue(all(
                (event["tileParity"] is not None)
                == (event["eventType"] in {"start", "teleport", "marker", "stop"})
                for event in events
            ))
            start_parity = events[0]["tileParity"]
            self.assertEqual(
                {
                    "logicalLocation": {
                        "worldSpace": "global", "x": 100, "y": 943, "level": 0
                    },
                    "legacyPackedAddress": {"x": 100, "y": 943},
                    "legacyRepresentable": True,
                    "packedSourcePresent": True,
                    "missingPackedSource": False,
                    "comparable": True,
                    "exact": True,
                },
                start_parity,
            )
            self.assertTrue(all(
                (event["tileNeighborhood"] is not None)
                == (event["eventType"] in {"start", "teleport", "marker", "stop"})
                for event in events
            ))
            self.assertEqual(
                {
                    "center": {
                        "worldSpace": "global", "x": 100, "y": 943, "level": 0
                    },
                    "cellCount": 9,
                    "legacyRepresentableCount": 9,
                    "unsupportedCount": 0,
                    "packedSourcePresentCount": 9,
                    "missingPackedSourceCount": 0,
                    "comparableCount": 9,
                    "exactCount": 9,
                    "complete": True,
                    "exact": True,
                },
                events[0]["tileNeighborhood"],
            )
            self.assertTrue(all(
                (event["adjacentCollision"] is not None)
                == (event["eventType"] in {"start", "teleport", "marker", "stop"})
                for event in events
            ))
            start_collision = events[0]["adjacentCollision"]
            self.assertEqual(
                {
                    "directionCount": 8,
                    "logicalDecisionAvailableCount": 8,
                    "packedDecisionAvailableCount": 8,
                    "comparableCount": 8,
                    "passabilityExactCount": 8,
                    "blockingReasonExactCount": 8,
                    "requiredStatesExactCount": 8,
                    "allComparable": True,
                    "allPassabilityExact": True,
                    "allBlockingReasonsExact": True,
                    "allRequiredStatesExact": True,
                },
                {key: start_collision[key] for key in (
                    "directionCount",
                    "logicalDecisionAvailableCount",
                    "packedDecisionAvailableCount",
                    "comparableCount",
                    "passabilityExactCount",
                    "blockingReasonExactCount",
                    "requiredStatesExactCount",
                    "allComparable",
                    "allPassabilityExact",
                    "allBlockingReasonsExact",
                    "allRequiredStatesExact",
                )},
            )
            self.assertEqual({"x": -1, "y": -1}, start_collision["directions"][0]["offset"])
            self.assertEqual({"x": 1, "y": 1}, start_collision["directions"][-1]["offset"])
            self.assertTrue(all(
                direction["logicalPassable"]
                and direction["packedPassable"]
                and direction["logicalBlockingReason"] == "NONE"
                and direction["packedBlockingReason"] == "NONE"
                for direction in start_collision["directions"]
            ))
            self.assertTrue(all(
                (event["recentTraversal"] is not None)
                == (event["eventType"] == "marker")
                for event in events
            ))
            recent = events[4]["recentTraversal"]
            self.assertEqual({
                "source": {"worldSpace": "global", "x": 100, "y": 0, "level": -1},
                "destination": {
                    "worldSpace": "global", "x": 101, "y": 0, "level": -1
                },
                "stepCount": 1,
                "droppedStepCount": 0,
                "discontinuityCount": 0,
                "logicalDecisionAvailableCount": 1,
                "packedDecisionAvailableCount": 1,
                "comparableCount": 1,
                "passabilityExactCount": 1,
                "blockingReasonExactCount": 1,
                "requiredStatesExactCount": 1,
                "logicalPassable": True,
                "packedPassable": True,
                "comparable": True,
                "passabilityExact": True,
                "allStepsComparable": True,
                "allStepPassabilitiesExact": True,
                "allStepBlockingReasonsExact": True,
                "allRequiredStatesExact": True,
                "firstLogicalBlockedStepIndex": None,
                "firstPackedBlockedStepIndex": None,
                "firstPassabilityMismatchStepIndex": None,
                "firstBlockingReasonMismatchStepIndex": None,
            }, {key: recent[key] for key in (
                "source", "destination", "stepCount", "droppedStepCount",
                "discontinuityCount", "logicalDecisionAvailableCount",
                "packedDecisionAvailableCount", "comparableCount",
                "passabilityExactCount", "blockingReasonExactCount",
                "requiredStatesExactCount", "logicalPassable", "packedPassable",
                "comparable", "passabilityExact", "allStepsComparable",
                "allStepPassabilitiesExact", "allStepBlockingReasonsExact",
                "allRequiredStatesExact", "firstLogicalBlockedStepIndex",
                "firstPackedBlockedStepIndex",
                "firstPassabilityMismatchStepIndex",
                "firstBlockingReasonMismatchStepIndex",
            )})
            self.assertEqual(0, recent["steps"][0]["index"])
            self.assertEqual({"x": 1, "y": 0}, recent["steps"][0]["offset"])
            upper_interest = events[1]["interestDelta"]
            self.assertEqual({
                "previousRegionCount": 2,
                "currentRegionCount": 4,
                "enteredCount": 4,
                "retainedCount": 0,
                "exitedCount": 2,
                "worldSpaceChanged": False,
                "levelChanged": True,
                "noOp": False,
            }, {key: upper_interest[key] for key in (
                "previousRegionCount",
                "currentRegionCount",
                "enteredCount",
                "retainedCount",
                "exitedCount",
                "worldSpaceChanged",
                "levelChanged",
                "noOp",
            )})
            self.assertEqual(
                {"worldSpace": "global", "level": 1, "x": 1, "y": -1},
                upper_interest["enteredKeys"][0],
            )
            self.assertEqual(
                {"worldSpace": "global", "level": 0, "x": 1, "y": 19},
                upper_interest["exitedKeys"][0],
            )
            self.assertIsNotNone(events[2]["interestDelta"])
            self.assertTrue(events[2]["interestDelta"]["levelChanged"])
            self.assertTrue(all(
                (event["interestDelta"] is not None)
                == (event["eventType"] in {"move", "teleport"})
                for event in events
            ))
            self.assertTrue(all(
                (event["regionResidency"] is not None)
                == (event["eventType"] != "move" or event is events[1])
                for event in events
            ))
            start_residency = events[0]["regionResidency"]
            self.assertEqual({
                "mirrorVersion": 1,
                "previousRegionCount": 2,
                "currentRegionCount": 2,
                "enteredCount": 0,
                "retainedCount": 2,
                "exitedCount": 0,
                "worldSpaceChanged": False,
                "levelChanged": False,
                "noOp": True,
                "residentCurrentCount": 2,
                "partialCurrentCount": 0,
                "missingCurrentCount": 0,
                "unsupportedCurrentCount": 0,
                "loadCandidateCount": 0,
                "releaseCandidateCount": 0,
            }, {key: start_residency[key] for key in (
                "mirrorVersion", "previousRegionCount", "currentRegionCount",
                "enteredCount", "retainedCount", "exitedCount",
                "worldSpaceChanged", "levelChanged", "noOp",
                "residentCurrentCount", "partialCurrentCount",
                "missingCurrentCount", "unsupportedCurrentCount",
                "loadCandidateCount", "releaseCandidateCount",
            )})
            upper_residency = events[1]["regionResidency"]
            self.assertEqual(2, upper_residency["residentCurrentCount"])
            self.assertEqual(2, upper_residency["unsupportedCurrentCount"])
            self.assertEqual(0, upper_residency["loadCandidateCount"])
            self.assertEqual(2, upper_residency["releaseCandidateCount"])
            self.assertEqual(
                {"worldSpace": "global", "level": 1, "x": 1, "y": -1},
                upper_residency["unsupportedCurrent"][0]["logicalRegion"],
            )
            self.assertEqual(
                {"interestState": "ENTERED", "residencyState": "UNSUPPORTED",
                 "sourceCount": 0, "residentSourceCount": 0,
                 "missingSourceCount": 0, "supportedTileCount": 0,
                 "residentTileCount": 0, "legacyCoverageComplete": False},
                {key: upper_residency["unsupportedCurrent"][0][key] for key in (
                    "interestState", "residencyState", "sourceCount",
                    "residentSourceCount", "missingSourceCount",
                    "supportedTileCount", "residentTileCount",
                    "legacyCoverageComplete",
                )},
            )
            raw_log = primary.read_text(encoding="utf-8").lower()
            self.assertNotIn('"username":', raw_log)
            self.assertNotIn("password", raw_log)
            self.assertNotIn("ipaddress", raw_log)

            decision_log = next(path for path in logs if "333" in path.name)
            decision_events = [
                json.loads(line)
                for line in decision_log.read_text(encoding="utf-8").splitlines()
            ]
            self.assertEqual(6, len(decision_events))
            eligible = decision_events[2]["regionRetirementDecisions"]
            self.assertEqual((1, 1, 0), (
                eligible["candidateCount"], eligible["eligibleCount"],
                eligible["refusedCount"],
            ))
            self.assertEqual("ELIGIBLE", eligible["entries"][0]["decisionState"])
            eligible_sources = decision_events[2][
                "packedRegionRetirementReadiness"
            ]
            self.assertEqual((1, 1, 0), (
                eligible_sources["sourceCount"],
                eligible_sources["readySourceCount"],
                eligible_sources["blockedSourceCount"],
            ))
            self.assertEqual(
                "READY", eligible_sources["entries"][0]["sourceState"]
            )
            eligible_safety = decision_events[2][
                "packedRegionRetirementSafety"
            ]
            self.assertEqual((1, 0, 0, 1), (
                eligible_safety["sourceCount"],
                eligible_safety["contentQuiescentSourceCount"],
                eligible_safety["lifecycleReadySourceCount"],
                eligible_safety["blockedSourceCount"],
            ))
            self.assertEqual(
                {
                    "readinessState": "READY",
                    "resident": True,
                    "tileStorageAvailable": True,
                    "regionReloadSupported": False,
                    "playerCount": 0,
                    "npcCount": 2,
                    "objectCount": 3,
                    "groundItemCount": 4,
                    "contentQuiescent": False,
                    "lifecycleReady": False,
                    "blockers": [
                        "NPCS_PRESENT", "OBJECTS_PRESENT",
                        "GROUND_ITEMS_PRESENT", "RELOAD_PATH_UNAVAILABLE",
                    ],
                },
                {key: eligible_safety["entries"][0][key] for key in (
                    "readinessState", "resident", "tileStorageAvailable",
                    "regionReloadSupported", "playerCount", "npcCount",
                    "objectCount", "groundItemCount", "contentQuiescent",
                    "lifecycleReady", "blockers",
                )},
            )
            eligible_construction = decision_events[2][
                "packedRegionAuthoredConstruction"
            ]
            self.assertEqual((7, 1, 1, 6), (
                eligible_construction["generation"],
                eligible_construction["sourceCount"],
                eligible_construction["authoredSourceCount"],
                eligible_construction["authoredConstructionCount"],
            ))
            self.assertTrue(eligible_construction["originCountsOnly"])
            self.assertFalse(eligible_construction["reconstructionManifest"])
            self.assertEqual(
                {
                    "sceneryCount": 2,
                    "boundaryCount": 1,
                    "npcSpawnCount": 1,
                    "groundItemSpawnCount": 1,
                    "harvestingSceneryCount": 1,
                    "authoredConstructionCount": 6,
                },
                {key: eligible_construction["entries"][0][key] for key in (
                    "sceneryCount", "boundaryCount", "npcSpawnCount",
                    "groundItemSpawnCount", "harvestingSceneryCount",
                    "authoredConstructionCount",
                )},
            )
            eligible_provenance = decision_events[2][
                "packedRegionAuthoredProvenance"
            ]
            self.assertEqual((7, 1, 1, 1, 0), (
                eligible_provenance["generation"],
                eligible_provenance["sourceCount"],
                eligible_provenance["absentIdentityCount"],
                eligible_provenance["anomalyDetailCount"],
                eligible_provenance["droppedAnomalyDetailCount"],
            ))
            self.assertTrue(eligible_provenance["identityMetadataOnly"])
            self.assertFalse(eligible_provenance["entityRegistry"])
            self.assertFalse(eligible_provenance["lifecycleAuthority"])
            self.assertEqual({
                "anomalyKind": "ABSENT",
                "generation": 7,
                "sourceOrdinal": 1,
                "constructionKind": "SCENERY",
                "manifestRecognized": True,
                "authoredDefinitionId": 100,
                "expectedConstructedEntityId": 100,
                "runtimeObserved": False,
                "runtimeEntityId": None,
                "runtimeActive": None,
                "runtimeInstanceCount": 0,
                "replacementObjectInstanceCount": 0,
            }, {key: eligible_provenance["anomalyDetails"][0][key]
                for key in (
                    "anomalyKind", "generation", "sourceOrdinal",
                    "constructionKind", "manifestRecognized",
                    "authoredDefinitionId", "expectedConstructedEntityId",
                    "runtimeObserved", "runtimeEntityId", "runtimeActive",
                    "runtimeInstanceCount",
                    "replacementObjectInstanceCount",
                )})
            eligible_reconstruction = decision_events[2][
                "packedRegionAuthoredReconstruction"
            ]
            self.assertEqual((7, 1, 1, 1, 1, 0), (
                eligible_reconstruction["generation"],
                eligible_reconstruction["sourceCount"],
                eligible_reconstruction["reconstructionPlacementCount"],
                eligible_reconstruction["requirementSourceCount"],
                eligible_reconstruction["selectedRequirementSourceCount"],
                eligible_reconstruction["missingRequirementSourceCount"],
            ))
            self.assertTrue(
                eligible_reconstruction["selectionDependencyClosed"]
            )
            self.assertTrue(eligible_reconstruction["identityMetadataOnly"])
            self.assertFalse(eligible_reconstruction["entityRegistry"])
            self.assertFalse(eligible_reconstruction["lifecycleAuthority"])
            self.assertTrue(
                eligible_reconstruction["entries"][0]["dependencyClosed"]
            )
            self.assertEqual({
                "selectedSafetySource": True,
                "authoredRecipeSource": True,
                "ownerSourceCount": 1,
                "placementReferenceCount": 1,
            }, {key: eligible_reconstruction["requirements"][0][key]
                for key in (
                    "selectedSafetySource", "authoredRecipeSource",
                    "ownerSourceCount", "placementReferenceCount",
                )})
            eligible_cohort = decision_events[2][
                "packedRegionAuthoredReconstructionCohort"
            ]
            self.assertEqual((7, 1, 1, 0, 1, 1, 0, 0), (
                eligible_cohort["generation"],
                eligible_cohort["seedSourceCount"],
                eligible_cohort["cohortSourceCount"],
                eligible_cohort["expandedAuthoredSourceCount"],
                eligible_cohort["authoredContentSourceCount"],
                eligible_cohort["requirementSourceCount"],
                eligible_cohort["externalSupportRequirementSourceCount"],
                eligible_cohort["maximumExpansionRound"],
            ))
            self.assertTrue(eligible_cohort["authoredClosureComplete"])
            self.assertTrue(eligible_cohort["fullySelfContained"])
            self.assertTrue(eligible_cohort["identityMetadataOnly"])
            self.assertFalse(eligible_cohort["entityRegistry"])
            self.assertFalse(eligible_cohort["lifecycleAuthority"])
            self.assertEqual("SEED", eligible_cohort["entries"][0]["role"])
            self.assertTrue(
                eligible_cohort["entries"][0]["dependencySelfContained"]
            )
            self.assertTrue(
                eligible_cohort["requirements"][0]["cohortSource"]
            )
            self.assertFalse(
                eligible_cohort["requirements"][0][
                    "externalSupportRequired"
                ]
            )
            eligible_attribution = decision_events[2][
                "packedRegionAuthoredReconstructionCohortAttribution"
            ]
            self.assertEqual((7, 1, 1, 1, 1, 0, 0), (
                eligible_attribution["generation"],
                eligible_attribution["kindCount"],
                eligible_attribution["edgeCount"],
                eligible_attribution["placementCount"],
                eligible_attribution["affectedSourceReferenceCount"],
                eligible_attribution["bridgePlacementCount"],
                eligible_attribution["expansionFrontierEdgeCount"],
            ))
            self.assertEqual(
                "SCENERY",
                eligible_attribution["kinds"][0]["constructionKind"],
            )
            self.assertEqual(
                "OBJECT_FOOTPRINT",
                eligible_attribution["kinds"][0]["dependencyKind"],
            )
            self.assertTrue(
                eligible_attribution["edges"][0]["selfReference"]
            )
            self.assertEqual([], eligible_attribution["bridgePlacements"])
            self.assertTrue(eligible_attribution["identityMetadataOnly"])
            self.assertFalse(eligible_attribution["entityRegistry"])
            self.assertFalse(eligible_attribution["lifecycleAuthority"])
            eligible_semantics = decision_events[2][
                "packedRegionAuthoredReconstructionDependencySemantics"
            ]
            self.assertEqual((7, 1, 1, 1, 1, 0, 0), (
                eligible_semantics["generation"],
                eligible_semantics["selectedSourceCount"],
                eligible_semantics["selectedAuthoredReplaySourceCount"],
                eligible_semantics["replayPlacementCount"],
                eligible_semantics["outboundSupportSourceCount"],
                eligible_semantics["externalOutboundSupportSourceCount"],
                eligible_semantics["incomingOwnerSourceCount"],
            ))
            self.assertEqual(
                "STATIC_FOOTPRINT_SUPPORT",
                eligible_semantics["kinds"][0]["semantics"],
            )
            self.assertTrue(eligible_semantics["sourceLocalReplay"])
            self.assertTrue(eligible_semantics["spatialReachPreserved"])
            self.assertFalse(eligible_semantics["activeInstanceEvidence"])
            self.assertFalse(eligible_semantics["entityRegistry"])
            self.assertFalse(eligible_semantics["lifecycleAuthority"])
            eligible_active_npcs = decision_events[2][
                "packedRegionActiveNpcResidency"
            ]
            self.assertEqual((7, 1, 1, 1, 1), (
                eligible_active_npcs["generation"],
                eligible_active_npcs["selectedSourceCount"],
                eligible_active_npcs["observedInstanceCount"],
                eligible_active_npcs["activeInstanceCount"],
                eligible_active_npcs["relevantActiveInstanceCount"],
            ))
            self.assertEqual(6, len(eligible_active_npcs["identityStatuses"]))
            self.assertEqual(1, len(
                eligible_active_npcs["relevantActiveInstances"]
            ))
            external_active_npc = eligible_active_npcs[
                "relevantActiveInstances"
            ][0]
            self.assertEqual({
                "identity": {
                    "generation": 7,
                    "packedRegionX": 5,
                    "packedRegionY": 0,
                    "sourceOrdinal": 1,
                    "constructionKind": "NPC_SPAWN",
                },
                "runtimeNpcId": 10,
                "currentPackedRegionX": 4,
                "currentPackedRegionY": 0,
                "identityStatus": "RECOGNIZED",
                "expectedRuntimeNpcId": 10,
                "classification": "EXTERNAL_OWNER_INSIDE",
            }, external_active_npc)
            self.assertTrue(eligible_active_npcs["pointInTimeCensus"])
            self.assertTrue(eligible_active_npcs["activeInstanceEvidence"])
            self.assertFalse(eligible_active_npcs["entityRegistry"])
            self.assertFalse(eligible_active_npcs["arrivalGate"])
            self.assertFalse(eligible_active_npcs["lifecycleAuthority"])
            eligible_active_npc_containment = decision_events[2][
                "packedRegionActiveNpcContainment"
            ]
            self.assertEqual((7, 1, 1, 1, 0, 0, 1, 1), (
                eligible_active_npc_containment["generation"],
                eligible_active_npc_containment["selectedSourceCount"],
                eligible_active_npc_containment["activeInstanceCount"],
                eligible_active_npc_containment[
                    "relevantActiveInstanceCount"
                ],
                eligible_active_npc_containment[
                    "sameSourceSelectedOwnerInsideCount"
                ],
                eligible_active_npc_containment[
                    "crossSourceSelectedOwnerInsideCount"
                ],
                eligible_active_npc_containment["blockingConditionCount"],
                eligible_active_npc_containment["blockingEvidenceCount"],
            ))
            self.assertFalse(
                eligible_active_npc_containment["boundaryContained"]
            )
            self.assertEqual(
                6, len(eligible_active_npc_containment["blockers"])
            )
            self.assertEqual(
                {"EXTERNAL_OWNER_INSIDE": 1},
                {
                    blocker["kind"]: blocker["instanceCount"]
                    for blocker in eligible_active_npc_containment["blockers"]
                    if blocker["instanceCount"] > 0
                },
            )
            self.assertTrue(
                eligible_active_npc_containment["pointInTimeOnly"]
            )
            self.assertTrue(
                eligible_active_npc_containment["containmentEvidence"]
            )
            self.assertTrue(
                eligible_active_npc_containment["entityPreservationRequired"]
            )
            self.assertFalse(eligible_active_npc_containment["lifecycleReady"])
            self.assertFalse(eligible_active_npc_containment["entityRegistry"])
            self.assertFalse(eligible_active_npc_containment["arrivalGate"])
            self.assertFalse(
                eligible_active_npc_containment["lifecycleAuthority"]
            )
            eligible_active_npc_boundary_requirements = decision_events[2][
                "packedRegionActiveNpcBoundaryRequirements"
            ]
            self.assertEqual((7, 1, 0, 1, 1, 0, 0), (
                eligible_active_npc_boundary_requirements["generation"],
                eligible_active_npc_boundary_requirements[
                    "selectedSourceCount"
                ],
                eligible_active_npc_boundary_requirements[
                    "selectedOwnerOutsideInstanceCount"
                ],
                eligible_active_npc_boundary_requirements[
                    "externalOwnerInsideInstanceCount"
                ],
                eligible_active_npc_boundary_requirements[
                    "expandableBoundaryInstanceCount"
                ],
                eligible_active_npc_boundary_requirements[
                    "hardBlockingConditionCount"
                ],
                eligible_active_npc_boundary_requirements[
                    "hardBlockingEvidenceCount"
                ],
            ))
            self.assertFalse(
                eligible_active_npc_boundary_requirements[
                    "boundaryContainedNow"
                ]
            )
            self.assertEqual(
                [{
                    "packedRegionX": 5,
                    "packedRegionY": 0,
                    "selectedOwnerCurrentSourceInstanceCount": 0,
                    "externalOwnerAuthoredSourceInstanceCount": 1,
                    "boundaryInstanceCount": 1,
                    "reasons": [
                        {
                            "reason": "SELECTED_OWNER_CURRENT_SOURCE",
                            "instanceCount": 0,
                        },
                        {
                            "reason": "EXTERNAL_OWNER_AUTHORED_SOURCE",
                            "instanceCount": 1,
                        },
                    ],
                }],
                eligible_active_npc_boundary_requirements["requirements"],
            )
            self.assertTrue(
                eligible_active_npc_boundary_requirements[
                    "freshSafetyAssessmentRequired"
                ]
            )
            self.assertTrue(
                eligible_active_npc_boundary_requirements[
                    "freshNpcCensusRequired"
                ]
            )
            for authority_flag in (
                "selectionMutated", "boundaryClosureProved", "entityRegistry",
                "arrivalGate", "lifecycleAuthority",
            ):
                self.assertFalse(
                    eligible_active_npc_boundary_requirements[authority_flag]
                )
            eligible_retirement_refinement = decision_events[2][
                "packedRegionRetirementRefinement"
            ]
            self.assertEqual((7, 1, 1, 0, 1, 2, 1, 0, 0, 0, 0, 0), (
                eligible_retirement_refinement["generation"],
                eligible_retirement_refinement["originalSafetySourceCount"],
                eligible_retirement_refinement["authoredCohortSourceCount"],
                eligible_retirement_refinement["expandedAuthoredSourceCount"],
                eligible_retirement_refinement[
                    "activeNpcRequirementSourceCount"
                ],
                eligible_retirement_refinement["candidateSourceCount"],
                eligible_retirement_refinement["addedCandidateSourceCount"],
                eligible_retirement_refinement[
                    "activeNpcAndAuthoredOverlapSourceCount"
                ],
                eligible_retirement_refinement[
                    "externalSupportRequirementSourceCount"
                ],
                eligible_retirement_refinement[
                    "supportPromotedToCandidateSourceCount"
                ],
                eligible_retirement_refinement["hardBlockingConditionCount"],
                eligible_retirement_refinement["hardBlockingEvidenceCount"],
            ))
            self.assertEqual(
                eligible_active_npc_boundary_requirements[
                    "safetyObservedAtTick"
                ],
                eligible_retirement_refinement["safetyObservedAtTick"],
            )
            self.assertEqual(
                eligible_active_npc_boundary_requirements[
                    "censusObservedAtTick"
                ],
                eligible_retirement_refinement["censusObservedAtTick"],
            )
            self.assertFalse(
                eligible_retirement_refinement["boundaryContainedAtInput"]
            )
            self.assertFalse(
                eligible_retirement_refinement["nonExpandableHardBlockers"]
            )
            self.assertEqual([], eligible_retirement_refinement[
                "externalSupportRequirements"
            ])
            self.assertEqual([
                {
                    "packedRegionX": 4,
                    "packedRegionY": 0,
                    "originalSafetySource": True,
                    "authoredCohortSource": True,
                    "authoredExpansionRound": 0,
                    "externalStaticSupportSource": False,
                    "staticSupportOwnerSourceCount": 0,
                    "staticSupportPlacementReferenceCount": 0,
                    "selectedOwnerCurrentSourceInstanceCount": 0,
                    "externalOwnerAuthoredSourceInstanceCount": 0,
                    "activeNpcBoundaryInstanceCount": 0,
                    "activeNpcBoundarySource": False,
                    "addedBeyondOriginalSafety": False,
                    "freshSafetyEvidenceRequired": False,
                    "freshNpcCensusRequired": False,
                },
                {
                    "packedRegionX": 5,
                    "packedRegionY": 0,
                    "originalSafetySource": False,
                    "authoredCohortSource": False,
                    "authoredExpansionRound": None,
                    "externalStaticSupportSource": False,
                    "staticSupportOwnerSourceCount": 0,
                    "staticSupportPlacementReferenceCount": 0,
                    "selectedOwnerCurrentSourceInstanceCount": 0,
                    "externalOwnerAuthoredSourceInstanceCount": 1,
                    "activeNpcBoundaryInstanceCount": 1,
                    "activeNpcBoundarySource": True,
                    "addedBeyondOriginalSafety": True,
                    "freshSafetyEvidenceRequired": True,
                    "freshNpcCensusRequired": True,
                },
            ], eligible_retirement_refinement["candidates"])
            for required_flag in (
                "freshSafetyAssessmentRequired", "freshNpcCensusRequired",
                "reassessmentRequired",
            ):
                self.assertTrue(
                    eligible_retirement_refinement[required_flag]
                )
            for authority_flag in (
                "candidateSelectionMutated", "fixedPointClosureProved",
                "loadRequest", "entityRegistry", "arrivalGate",
                "lifecycleAuthority",
            ):
                self.assertFalse(
                    eligible_retirement_refinement[authority_flag]
                )
            preservation_burden = decision_events[2][
                "packedRegionPreservationBurden"
            ]
            self.assertEqual((2, 0, 2), (
                preservation_burden["sourceCount"],
                preservation_burden["burdenSatisfiedSourceCount"],
                preservation_burden["blockedSourceCount"],
            ))
            self.assertEqual(
                [
                    "PLAYER_SESSION", "DYNAMIC_OBJECT", "GROUND_ITEM",
                    "COLLISION_PRODUCT", "OWNED_EVENT",
                ],
                [
                    summary["family"]
                    for summary in preservation_burden["familySummaries"]
                ],
            )
            self.assertEqual(
                [3, 4], sorted(
                    source["blockedFamilyCount"]
                    for source in preservation_burden["sources"]
                )
            )
            self.assertTrue(all(
                not source["burdenSatisfiedAtObservation"]
                and len(source["families"]) == 5
                for source in preservation_burden["sources"]
            ))
            for authority_flag in (
                "retirementReadinessEvidence", "candidateSelectionMutated",
                "preservationPerformed", "reloadRequest", "entityRegistry",
                "arrivalGate", "teardownTransaction", "lifecycleAuthority",
            ):
                self.assertFalse(preservation_burden[authority_flag])
            self.assertTrue(preservation_burden["pointInTimeOnly"])
            dynamic_objects = decision_events[2][
                "packedRegionDynamicObjectPreservation"
            ]
            self.assertEqual((2, 1, 1, 1, 0), (
                dynamic_objects["sourceCount"],
                dynamic_objects["dynamicObjectCount"],
                dynamic_objects["objectsWithRuntimeAttributesCount"],
                dynamic_objects["constructorStateCompleteObjectCount"],
                dynamic_objects["standaloneRestorationCompleteObjectCount"],
            ))
            self.assertTrue(dynamic_objects["pointInTimeOnly"])
            self.assertTrue(dynamic_objects["detachedPrimitiveCopy"])
            recorded = [
                obj
                for source in dynamic_objects["sources"]
                for obj in source["objects"]
            ]
            self.assertEqual(1, len(recorded))
            self.assertEqual(
                {
                    "sourceOrdinal": 0,
                    "objectId": 64,
                    "permanentObjectId": 63,
                    "packedX": 193,
                    "packedY": 2,
                    "direction": 4,
                    "type": 0,
                    "ownerPresent": True,
                    "runtimeAttributeCount": 2,
                    "constructorStateComplete": True,
                    "standaloneRestorationComplete": False,
                },
                recorded[0],
            )
            self.assertNotIn("private-owner", json.dumps(decision_events))
            event_ownership = decision_events[2]["packedRegionEventOwnership"]
            self.assertEqual((2, 2, 1, 0, 0, 1, 1, 1), (
                event_ownership["sourceCount"], event_ownership["eventCount"],
                event_ownership["exactSpatialEventCount"],
                event_ownership["ownerPositionHintEventCount"],
                event_ownership["nonSpatialGlobalEventCount"],
                event_ownership["unattributedEventCount"],
                event_ownership["restorationStateAvailableEventCount"],
                event_ownership[
                    "detachedCallbackPayloadCompleteEventCount"
                ],
            ))
            self.assertFalse(event_ownership["candidateAttributionComplete"])
            self.assertEqual(2, event_ownership[
                "registrationIdentityCapturedEventCount"
            ])
            self.assertTrue(event_ownership["registrationIdentityCaptured"])
            self.assertTrue(event_ownership["registrationIdentityComplete"])
            self.assertTrue(event_ownership[
                "schedulerInstanceIdentityCaptured"
            ])
            self.assertEqual(
                "00000000-0000-0000-0000-000000000011",
                event_ownership["schedulerInstanceIdentity"],
            )
            self.assertEqual(1, event_ownership[
                "executionSemanticsCapturedEventCount"
            ])
            self.assertTrue(event_ownership["executionSemanticsCaptured"])
            self.assertTrue(event_ownership["executionSemanticsComplete"])
            self.assertEqual(
                1, event_ownership["atomicTimingCapturedEventCount"]
            )
            self.assertTrue(event_ownership["atomicTimingCaptured"])
            self.assertTrue(event_ownership["atomicTimingComplete"])
            self.assertEqual(1, event_ownership[
                "targetBindingRequirementCapturedEventCount"
            ])
            self.assertTrue(event_ownership[
                "targetBindingRequirementCaptured"
            ])
            self.assertTrue(event_ownership[
                "targetBindingRequirementComplete"
            ])
            self.assertEqual(
                1, event_ownership["targetBindingCompleteEventCount"]
            )
            self.assertTrue(event_ownership["targetBindingComplete"])
            self.assertEqual(
                1, event_ownership["arrivalOrderingCapturedEventCount"]
            )
            self.assertTrue(event_ownership["arrivalOrderingCaptured"])
            self.assertTrue(event_ownership["arrivalOrderingComplete"])
            self.assertEqual(1, event_ownership[
                "generationBindingRequirementCapturedEventCount"
            ])
            self.assertTrue(event_ownership[
                "generationBindingRequirementCaptured"
            ])
            self.assertTrue(event_ownership[
                "generationBindingRequirementComplete"
            ])
            self.assertEqual(
                1, event_ownership["generationBindingCompleteEventCount"]
            )
            self.assertTrue(event_ownership["generationBindingComplete"])
            self.assertEqual(1, event_ownership[
                "idempotencyRequirementCapturedEventCount"
            ])
            self.assertTrue(event_ownership[
                "idempotencyRequirementCaptured"
            ])
            self.assertTrue(event_ownership[
                "idempotencyRequirementComplete"
            ])
            self.assertEqual(
                [101, 102],
                [event["registrationSequence"]
                 for event in event_ownership["events"]],
            )
            self.assertEqual(
                "EXACT_SPATIAL",
                event_ownership["events"][0]["attributionKind"],
            )
            self.assertTrue(
                event_ownership["events"][0]["atomicTimingCaptured"]
            )
            self.assertFalse(
                event_ownership["events"][1]["atomicTimingCaptured"]
            )
            self.assertEqual(
                {"role": "FIXED_EFFECT_LOCATION", "packedX": 193,
                 "packedY": 2},
                event_ownership["events"][0]["spatialReferences"][0],
            )
            self.assertEqual([0], event_ownership["sources"][0][
                "restorationStateEventOrdinals"
            ])
            self.assertEqual(1, event_ownership["sources"][0][
                "restorationStateEventCount"
            ])
            restoration = event_ownership["events"][0]["restorationState"]
            self.assertEqual((
                "SCENERY_SPAWN", True, "NOT_REQUIRED", True,
                "ONE_SHOT", "CONTINUE_SERVER_TICKS", True, True,
                "AUTHORED_DESTINATION_SLOT",
                "AUTHORED_PLACEMENT_IDENTITY",
                "REFUSE_MISMATCH_OR_AMBIGUITY", True, True,
                "RECONCILE_BEFORE_FIRST_VISIBILITY", True,
                "MATCH_RECONSTRUCTION_GENERATION", True, True,
                "AUTHORED_SCENERY_PRESENT",
                "ALREADY_SATISFIED_IS_NO_OP_SUCCESS",
                "DESTINATION_EMPTY_OR_EXACT_AUTHORED_TRANSIENT", True,
                False, False, False,
            ), (
                restoration["kind"], restoration["forceFullBlock"],
                restoration["targetBindingEvidence"],
                restoration["detachedCallbackPayloadComplete"],
                restoration["executionSemantics"],
                restoration["timeProgressionPolicy"],
                restoration["executionSemanticsCaptured"],
                restoration["atomicTimingCaptured"],
                restoration["targetSubject"],
                restoration["bindingEvidence"],
                restoration["targetConflictPolicy"],
                restoration["targetBindingRequirementCaptured"],
                restoration["targetBindingComplete"],
                restoration["arrivalOrderingRequirement"],
                restoration["arrivalOrderingCaptured"],
                restoration["generationBindingRequirement"],
                restoration["generationBindingRequirementCaptured"],
                restoration["generationBindingComplete"],
                restoration["desiredState"],
                restoration["idempotencyPolicy"],
                restoration["mutationPrecondition"],
                restoration["idempotencyRequirementCaptured"],
                restoration["schedulerIdentityCaptured"],
                restoration["targetBindingLookupPerformed"],
                restoration["standaloneRestorationComplete"],
            ))
            self.assertEqual({
                "objectId": 310,
                "permanentObjectId": 310,
                "packedX": 193,
                "packedY": 2,
                "direction": 0,
                "type": 0,
                "ownerPresent": True,
                "runtimeAttributeCount": 0,
                "constructorStateComplete": True,
                "authoredPlacement": {
                    "generation": 7,
                    "packedRegionX": 4,
                    "packedRegionY": 0,
                    "sourceOrdinal": 42,
                    "constructionKind": "SCENERY",
                },
            }, restoration["scenery"])
            self.assertIsNone(
                event_ownership["events"][1]["restorationState"]
            )
            self.assertNotIn("private-event-owner", json.dumps(decision_events))
            for authority_flag in (
                "callbackStateCaptured", "schedulerIdentityCaptured",
                "preservationPerformed", "reloadRequest", "eventCancellation",
                "eventReschedule", "entityRegistry", "arrivalGate",
                "teardownTransaction", "lifecycleAuthority",
            ):
                self.assertFalse(event_ownership[authority_flag])
            for authority_flag in (
                "runtimeAttributesCaptured", "eventOwnershipCaptured",
                "preservationPerformed", "reloadRequest", "entityRegistry",
                "arrivalGate", "teardownTransaction", "lifecycleAuthority",
            ):
                self.assertFalse(dynamic_objects[authority_flag])
            deferred_reassessment = decision_events[3][
                "packedRegionRetirementRefinementReassessment"
            ]
            self.assertEqual(
                "DEFERRED_NOT_NEWER", deferred_reassessment["status"]
            )
            self.assertTrue(deferred_reassessment["attempted"])
            self.assertTrue(deferred_reassessment["deferredNotNewer"])
            self.assertTrue(deferred_reassessment["pendingRetained"])
            self.assertEqual(2, deferred_reassessment[
                "pendingBeforeCandidateSourceCount"
            ])
            self.assertEqual(2, deferred_reassessment[
                "pendingAfterCandidateSourceCount"
            ])
            self.assertIsNone(deferred_reassessment["reassessment"])
            self.assertEqual(
                2,
                decision_events[3]["packedRegionPreservationBurden"][
                    "sourceCount"
                ],
            )
            stable_reassessment = decision_events[4][
                "packedRegionRetirementRefinementReassessment"
            ]
            self.assertEqual("STABLE", stable_reassessment["status"])
            self.assertFalse(stable_reassessment["deferredNotNewer"])
            self.assertFalse(stable_reassessment["pendingRetained"])
            self.assertEqual(0, stable_reassessment[
                "pendingAfterCandidateSourceCount"
            ])
            stable_result = stable_reassessment["reassessment"]
            self.assertTrue(stable_result["freshEvidenceAligned"])
            self.assertTrue(stable_result[
                "candidateSetStableAtObservation"
            ])
            self.assertTrue(stable_result[
                "refinementConvergedAtObservation"
            ])
            self.assertFalse(stable_result["retirementReadinessEvidence"])
            self.assertEqual(0, stable_result[
                "lifecycleReadyEvidenceSourceCount"
            ])
            self.assertEqual(2, stable_result["freshSafety"]["sourceCount"])
            self.assertEqual(
                2,
                decision_events[4]["packedRegionPreservationBurden"][
                    "sourceCount"
                ],
            )
            self.assertEqual(
                {"DIAGNOSTIC_SELECTION_ONLY"},
                {
                    entry["readinessState"]
                    for entry in stable_result["freshSafety"]["entries"]
                },
            )
            for authority_flag in (
                "candidateSelectionMutated",
                "fixedPointLifecycleClosureProved", "loadRequest",
                "entityRegistry", "arrivalGate", "retirementCommitToken",
                "lifecycleAuthority",
            ):
                self.assertFalse(stable_result[authority_flag])
            refusal = decision_events[3]["regionRetirementDecisions"]
            self.assertEqual((1, 0, 1), (
                refusal["candidateCount"], refusal["eligibleCount"],
                refusal["refusedCount"],
            ))
            self.assertEqual("PINNED", refusal["entries"][0]["decisionState"])
            self.assertEqual("PINNED", refusal["entries"][0]["currentRetirementState"])
            refused_sources = decision_events[3][
                "packedRegionRetirementReadiness"
            ]
            self.assertEqual((1, 0, 1), (
                refused_sources["sourceCount"],
                refused_sources["readySourceCount"],
                refused_sources["blockedSourceCount"],
            ))
            self.assertEqual(
                "REFUSED_COVERAGE",
                refused_sources["entries"][0]["sourceState"],
            )
            refused_safety = decision_events[3][
                "packedRegionRetirementSafety"
            ]
            self.assertEqual(
                [
                    "READINESS_NOT_READY", "PLAYERS_PRESENT",
                    "RELOAD_PATH_UNAVAILABLE",
                ],
                refused_safety["entries"][0]["blockers"],
            )
            self.assertEqual(1, refused_safety["entries"][0]["playerCount"])
            self.assertEqual(
                0, decision_events[4]["regionRetirementDecisions"]["candidateCount"]
            )
            self.assertEqual(
                0,
                decision_events[4]["packedRegionRetirementReadiness"][
                    "sourceCount"
                ],
            )
            self.assertEqual(
                0,
                decision_events[4]["packedRegionRetirementSafety"][
                    "sourceCount"
                ],
            )
            self.assertEqual(
                0,
                decision_events[4]["packedRegionAuthoredConstruction"][
                    "sourceCount"
                ],
            )

            schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
            try:
                import jsonschema
            except ImportError:
                jsonschema = None
            if jsonschema is not None:
                from referencing import Registry, Resource

                jsonschema.Draft202012Validator.check_schema(schema)
                v11 = json.loads(SCHEMA_V11.read_text(encoding="utf-8"))
                v12 = json.loads(SCHEMA_V12.read_text(encoding="utf-8"))
                v13 = json.loads(SCHEMA_V13.read_text(encoding="utf-8"))
                v14 = json.loads(SCHEMA_V14.read_text(encoding="utf-8"))
                v15 = json.loads(SCHEMA_V15.read_text(encoding="utf-8"))
                v16 = json.loads(SCHEMA_V16.read_text(encoding="utf-8"))
                v17 = json.loads(SCHEMA_V17.read_text(encoding="utf-8"))
                v18 = json.loads(SCHEMA_V18.read_text(encoding="utf-8"))
                v21 = json.loads(SCHEMA_V21.read_text(encoding="utf-8"))
                v22 = json.loads(SCHEMA_V22.read_text(encoding="utf-8"))
                v23 = json.loads(SCHEMA_V23.read_text(encoding="utf-8"))
                v24 = json.loads(SCHEMA_V24.read_text(encoding="utf-8"))
                v25 = json.loads(SCHEMA_V25.read_text(encoding="utf-8"))
                v26 = json.loads(SCHEMA_V26.read_text(encoding="utf-8"))
                v27 = json.loads(SCHEMA_V27.read_text(encoding="utf-8"))
                v28 = json.loads(SCHEMA_V28.read_text(encoding="utf-8"))
                v29 = json.loads(SCHEMA_V29.read_text(encoding="utf-8"))
                v30 = json.loads(SCHEMA_V30.read_text(encoding="utf-8"))
                v31 = json.loads(SCHEMA_V31.read_text(encoding="utf-8"))
                v32 = json.loads(SCHEMA_V32.read_text(encoding="utf-8"))
                v33 = json.loads(SCHEMA_V33.read_text(encoding="utf-8"))
                v34 = json.loads(SCHEMA_V34.read_text(encoding="utf-8"))
                v35 = json.loads(SCHEMA_V35.read_text(encoding="utf-8"))
                v36 = json.loads(SCHEMA_V36.read_text(encoding="utf-8"))
                v37 = json.loads(SCHEMA_V37.read_text(encoding="utf-8"))
                v38 = json.loads(SCHEMA_V38.read_text(encoding="utf-8"))
                v39 = json.loads(SCHEMA_V39.read_text(encoding="utf-8"))
                v40 = json.loads(SCHEMA_V40.read_text(encoding="utf-8"))
                v41 = json.loads(SCHEMA_V41.read_text(encoding="utf-8"))
                registry = Registry().with_resources([
                    (v11["$id"], Resource.from_contents(v11)),
                    (v12["$id"], Resource.from_contents(v12)),
                    (v13["$id"], Resource.from_contents(v13)),
                    (v14["$id"], Resource.from_contents(v14)),
                    (v15["$id"], Resource.from_contents(v15)),
                    (v16["$id"], Resource.from_contents(v16)),
                    (v17["$id"], Resource.from_contents(v17)),
                    (v18["$id"], Resource.from_contents(v18)),
                    (v21["$id"], Resource.from_contents(v21)),
                    (v22["$id"], Resource.from_contents(v22)),
                    (v23["$id"], Resource.from_contents(v23)),
                    (v24["$id"], Resource.from_contents(v24)),
                    (v25["$id"], Resource.from_contents(v25)),
                    (v26["$id"], Resource.from_contents(v26)),
                    (v27["$id"], Resource.from_contents(v27)),
                    (v28["$id"], Resource.from_contents(v28)),
                    (v29["$id"], Resource.from_contents(v29)),
                    (v30["$id"], Resource.from_contents(v30)),
                    (v31["$id"], Resource.from_contents(v31)),
                    (v32["$id"], Resource.from_contents(v32)),
                    (v33["$id"], Resource.from_contents(v33)),
                    (v34["$id"], Resource.from_contents(v34)),
                    (v35["$id"], Resource.from_contents(v35)),
                    (v36["$id"], Resource.from_contents(v36)),
                    (v37["$id"], Resource.from_contents(v37)),
                    (v38["$id"], Resource.from_contents(v38)),
                    (v39["$id"], Resource.from_contents(v39)),
                    (v40["$id"], Resource.from_contents(v40)),
                    (v41["$id"], Resource.from_contents(v41)),
                ])
                validator = jsonschema.Draft202012Validator(
                    schema, registry=registry
                )
                for event in events + decision_events:
                    validator.validate(event)

    def test_runtime_wiring_is_opt_in_dev_only_and_observational(self):
        config = CONFIG_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        command = COMMAND_SOURCE.read_text(encoding="utf-8")
        local_config = LOCAL_CONFIG.read_text(encoding="utf-8")
        host_config = HOST_CONFIG.read_text(encoding="utf-8")

        self.assertIn("OPENRSC_LAYERED_MAP_PARITY_OBSERVER", config)
        self.assertIn('"want_layered_map_parity_observer"', config)
        self.assertIn("WANT_LAYERED_MAP_PARITY_OBSERVER", player)
        self.assertIn("LayeredCoordinateParityObserver.onLocationChanged", player)
        self.assertIn("LayeredCoordinateParityObserver.onSession", player)
        self.assertIn("layeredTileSnapshotSource(player)", command)
        self.assertIn("layeredTileParitySource(player)", command)
        self.assertIn("layeredTileNeighborhoodSource(player)", command)
        self.assertIn("layeredAdjacentCollisionSource(player)", command)
        self.assertIn("layeredRegionResidencySource(player)", command)
        self.assertIn("layeredPackedRegionAuthoredConstructionSource(player)", command)
        self.assertIn("regionManager.getLayeredRegionTileSnapshot", command)
        self.assertIn("regionManager.compareLayeredTileState(current)", command)
        self.assertIn("regionManager.compareLayeredTileNeighborhood(current)", command)
        self.assertIn("regionManager.compareLayeredAdjacentStepCollisions(current)", command)
        self.assertIn("regionManager.compareLayeredRegionInterestResidency(", command)
        self.assertIn('command.equalsIgnoreCase("layerparity")', command)
        self.assertIn('command.equalsIgnoreCase("lp")', command)
        self.assertIn("player.isDev()", command)
        self.assertIn("WANT_LAYERED_MAP_PARITY_OBSERVER", command)
        self.assertIn("[start|status|snapshot|mark LABEL|stop]", command)
        self.assertIn("want_layered_map_parity_observer: false", local_config)
        self.assertIn("want_layered_map_parity_observer: false", host_config)
        self.assertNotIn("LegacyPackedPointAdapter.toLegacyPoint", player)


if __name__ == "__main__":
    unittest.main()
