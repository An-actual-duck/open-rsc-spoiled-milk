#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION_PACKAGE = ROOT / "server/src/com/openrsc/server/model/world/region"
COLLISION_FLAG = ROOT / "server/src/com/openrsc/server/util/rsc/CollisionFlag.java"
TILE_VALUE = REGION_PACKAGE / "TileValue.java"
TILE_STATE = REGION_PACKAGE / "LayeredTileState.java"
SNAPSHOT = REGION_PACKAGE / "LayeredRegionTileSnapshot.java"
CELL_COMPARISON = REGION_PACKAGE / "LayeredTileStateParityComparison.java"
NEIGHBORHOOD = REGION_PACKAGE / "LayeredTileNeighborhoodParityComparison.java"
STEP_COMPARISON = REGION_PACKAGE / "LayeredAdjacentStepCollisionComparison.java"
REGION_MANAGER = REGION_PACKAGE / "RegionManager.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


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
        if (x < 0 || y < 0 || x > Short.MAX_VALUE || y > Short.MAX_VALUE) {
            throw new IllegalArgumentException("packed point out of range");
        }
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


FIXTURE = r'''
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.LegacyLogicalTileAddress;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import com.openrsc.server.util.rsc.CollisionFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LayeredAdjacentStepCollisionComparisonFixture {
    public static void main(String[] args) {
        WorldLocation center = location(0, 239, 16);
        LayeredTileNeighborhoodParityComparison open = build(
            center, new SyntheticSource(), null);
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                if (x == 0 && y == 0) {
                    continue;
                }
                LayeredAdjacentStepCollisionComparison comparison =
                    LayeredAdjacentStepCollisionComparison.of(open, x, y);
                check(comparison.getSource().equals(center), "open source");
                check(comparison.getDestination().equals(offset(center, x, y)),
                    "open destination");
                check(comparison.isLogicalDecisionAvailable(), "open logical available");
                check(comparison.isPackedDecisionAvailable(), "open packed available");
                check(Boolean.TRUE.equals(comparison.getLogicalPassable()),
                    "open logical passable");
                check(Boolean.TRUE.equals(comparison.getPackedPassable()),
                    "open packed passable");
                check(comparison.isPassabilityExact(), "open exact passability");
                check(comparison.isBlockingReasonExact(), "open exact reason");
                check(comparison.areRequiredStatesExact(), "open exact states");
                check(comparison.getLogicalBlockingReason()
                    == LayeredAdjacentStepCollisionComparison.BlockingReason.NONE,
                    "open reason");
                int expectedCells = x == 0 || y == 0 ? 2
                    : x == 1 && y == -1 ? 5 : 4;
                check(comparison.getRequiredCellCount() == expectedCells,
                    "required cells");
            }
        }

        SyntheticSource blockedSource = new SyntheticSource();
        blockedSource.setMask(offset(center, 1, 0), CollisionFlag.FULL_BLOCK_C);
        LayeredAdjacentStepCollisionComparison blocked =
            LayeredAdjacentStepCollisionComparison.of(
                build(center, blockedSource, null), 1, 0);
        check(Boolean.FALSE.equals(blocked.getLogicalPassable()),
            "blocked logical");
        check(Boolean.FALSE.equals(blocked.getPackedPassable()),
            "blocked packed");
        check(blocked.isPassabilityExact() && blocked.isBlockingReasonExact(),
            "blocked exact");

        SyntheticSource currentWallSource = new SyntheticSource();
        currentWallSource.setMask(center, CollisionFlag.WALL_WEST);
        LayeredAdjacentStepCollisionComparison currentWall =
            LayeredAdjacentStepCollisionComparison.of(
                build(center, currentWallSource, null), 1, 0);
        check(currentWall.getLogicalBlockingReason()
            == LayeredAdjacentStepCollisionComparison.BlockingReason.CURRENT_X,
            "current wall reason");

        SyntheticSource mismatchSource = new SyntheticSource();
        LayeredAdjacentStepCollisionComparison mismatch =
            LayeredAdjacentStepCollisionComparison.of(
                build(center, mismatchSource, source -> source.setMask(
                    offset(center, 1, 0), CollisionFlag.FULL_BLOCK_C)), 1, 0);
        check(Boolean.TRUE.equals(mismatch.getLogicalPassable()),
            "mismatch logical passable");
        check(Boolean.FALSE.equals(mismatch.getPackedPassable()),
            "mismatch packed blocked");
        check(!mismatch.isPassabilityExact(), "mismatch detected");
        check(!mismatch.areRequiredStatesExact(), "mismatch state detected");

        SyntheticSource irrelevantSource = new SyntheticSource();
        LayeredAdjacentStepCollisionComparison irrelevantDifference =
            LayeredAdjacentStepCollisionComparison.of(
                build(center, irrelevantSource, source -> source.setElevation(
                    offset(center, -1, 0), 7)), -1, 0);
        check(irrelevantDifference.isPassabilityExact(),
            "irrelevant state keeps decision");
        check(irrelevantDifference.isBlockingReasonExact(),
            "irrelevant state keeps reason");
        check(!irrelevantDifference.areRequiredStatesExact(),
            "irrelevant full-state difference visible");

        SyntheticSource legacyDiagonalSource = new SyntheticSource();
        legacyDiagonalSource.setMask(
            offset(center, 1, 0), CollisionFlag.FULL_BLOCK_C);
        legacyDiagonalSource.setMask(
            offset(center, 1, 1), CollisionFlag.WALL_EAST);
        LayeredAdjacentStepCollisionComparison legacyDiagonal =
            LayeredAdjacentStepCollisionComparison.of(
                build(center, legacyDiagonalSource, null), 1, -1);
        check(legacyDiagonal.getRequiredCellCount() == 5,
            "legacy diagonal auxiliary retained");
        check(legacyDiagonal.getLogicalBlockingReason()
            == LayeredAdjacentStepCollisionComparison.BlockingReason.DIAGONAL_PASS_THROUGH,
            "legacy diagonal reason");

        SyntheticSource missingSource = new SyntheticSource();
        LayeredAdjacentStepCollisionComparison missing =
            LayeredAdjacentStepCollisionComparison.of(
                build(center, missingSource, source -> source.dropDirectSources = true),
                1, 0);
        check(missing.isLogicalDecisionAvailable(), "missing logical available");
        check(!missing.isPackedDecisionAvailable(), "missing packed unavailable");
        check(missing.getPackedPassable() == null, "missing packed null");
        check(!missing.isComparable() && !missing.isPassabilityExact(),
            "missing uncomparable");

        LayeredAdjacentStepCollisionComparison deep =
            LayeredAdjacentStepCollisionComparison.of(
                build(location(-2, 239, 16), new SyntheticSource(), null), 1, 0);
        check(!deep.isLogicalDecisionAvailable(), "deep logical unavailable");
        check(!deep.isPackedDecisionAvailable(), "deep packed unavailable");
        check(deep.getLogicalPassable() == null && deep.getPackedPassable() == null,
            "deep nullable decisions");

        expectIllegal(() -> LayeredAdjacentStepCollisionComparison.of(open, 0, 0));
        expectIllegal(() -> LayeredAdjacentStepCollisionComparison.of(open, 2, 0));
        expectNull(() -> LayeredAdjacentStepCollisionComparison.of(null, 1, 0));
        check(open.getCenter().equals(center), "neighborhood unchanged");
        check(mismatch.toString().contains("offsetX=1"), "comparison string");
    }

    private static LayeredTileNeighborhoodParityComparison build(
            WorldLocation center,
            SyntheticSource source,
            SourceMutation afterSnapshot) {
        Map<WorldRegionKey, LayeredRegionTileSnapshot> snapshots =
            new HashMap<WorldRegionKey, LayeredRegionTileSnapshot>();
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                WorldRegionKey key = WorldRegionKey.from(offset(center, x, y));
                if (!snapshots.containsKey(key)) {
                    snapshots.put(key, LayeredRegionTileSnapshot.capture(key, source));
                }
            }
        }
        if (afterSnapshot != null) {
            afterSnapshot.apply(source);
        }
        List<LayeredTileStateParityComparison> cells =
            new ArrayList<LayeredTileStateParityComparison>();
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                WorldLocation location = offset(center, x, y);
                WorldRegionKey key = WorldRegionKey.from(location);
                LegacyLogicalTileAddress address = LegacyLogicalTileAddress.resolve(
                    key,
                    location.getCoordinate().getLocalX(),
                    location.getCoordinate().getLocalY());
                boolean present = address.isLegacyRepresentable()
                    && source.hasPackedRegion(
                        address.getPackedRegionX(), address.getPackedRegionY());
                TileValue direct = present ? source.readPackedTile(
                    address.getPackedRegionX(),
                    address.getPackedRegionY(),
                    address.getPackedLocalX(),
                    address.getPackedLocalY()) : null;
                cells.add(LayeredTileStateParityComparison.compare(
                    location, snapshots.get(key), present, direct));
            }
        }
        return LayeredTileNeighborhoodParityComparison.of(center, cells);
    }

    private static WorldLocation offset(
            WorldLocation center, int x, int y) {
        return LayeredTileNeighborhoodParityComparison.offset(center, x, y);
    }

    private static WorldLocation location(int level, int x, int y) {
        return new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(x, y, level));
    }

    private interface SourceMutation {
        void apply(SyntheticSource source);
    }

    private static final class SyntheticSource
            implements LayeredRegionTileSnapshot.PackedTileSource {
        final Map<String, Integer> masks = new HashMap<String, Integer>();
        final Map<String, Integer> elevations = new HashMap<String, Integer>();
        boolean dropDirectSources;

        void setMask(WorldLocation location, int mask) {
            masks.put(key(location), mask);
        }

        void setElevation(WorldLocation location, int elevation) {
            elevations.put(key(location), elevation);
        }

        private String key(WorldLocation location) {
            WorldRegionKey region = WorldRegionKey.from(location);
            LegacyLogicalTileAddress address = LegacyLogicalTileAddress.resolve(
                region,
                location.getCoordinate().getLocalX(),
                location.getCoordinate().getLocalY());
            return packedKey(
                address.getPackedRegionX(), address.getPackedRegionY(),
                address.getPackedLocalX(), address.getPackedLocalY());
        }

        @Override
        public boolean hasPackedRegion(int packedRegionX, int packedRegionY) {
            return !dropDirectSources;
        }

        @Override
        public TileValue readPackedTile(
                int packedRegionX,
                int packedRegionY,
                int packedLocalX,
                int packedLocalY) {
            if (!hasPackedRegion(packedRegionX, packedRegionY)) {
                return null;
            }
            String key = packedKey(
                packedRegionX, packedRegionY, packedLocalX, packedLocalY);
            TileValue tile = new TileValue();
            tile.traversalMask = (byte) masks.getOrDefault(key, 0).intValue();
            tile.elevation = (byte) elevations.getOrDefault(key, 0).intValue();
            return tile;
        }

        private String packedKey(
                int regionX, int regionY, int localX, int localY) {
            return regionX + ":" + regionY + ":" + localX + ":" + localY;
        }
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceThirtyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-thirty-two-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/region/"
            "LayeredAdjacentStepCollisionComparisonFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
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
                str(point),
                str(fixture),
                *(str(path) for path in sorted(COORDINATES.glob("*.java"))),
                str(COLLISION_FLAG),
                str(TILE_VALUE),
                str(TILE_STATE),
                str(SNAPSHOT),
                str(CELL_COMPARISON),
                str(NEIGHBORHOOD),
                str(STEP_COMPARISON),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_adjacent_step_retains_directional_collision_and_parity_status(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.region."
                "LayeredAdjacentStepCollisionComparisonFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_projection_is_dormant_and_keeps_path_authority_unchanged(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        mob = MOB.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "compareLayeredAdjacentStepCollision(\n\t\t\tfinal Point packedCenter",
            manager,
        )
        self.assertIn(
            "compareLayeredAdjacentStepCollision(\n\t\t\tfinal WorldLocation logicalCenter",
            manager,
        )
        self.assertIn(
            "compareLayeredAdjacentStepCollisions(final Point packedCenter)",
            manager,
        )
        self.assertIn(
            "compareLayeredAdjacentStepCollisions(final WorldLocation logicalCenter)",
            manager,
        )
        step_block = manager.split(
            "compareLayeredAdjacentStepCollision(\n\t\t\tfinal WorldLocation logicalCenter",
            1,
        )[1].split("private LayeredTileStateParityComparison", 1)[0]
        self.assertIn("compareLayeredTileNeighborhood(logicalCenter)", step_block)
        self.assertNotIn("getRegion(", step_block)
        self.assertNotIn("getMutableTile", step_block)
        batch_block = manager.split(
            "compareLayeredAdjacentStepCollisions(final WorldLocation logicalCenter)",
            1,
        )[1].split("private LayeredTileStateParityComparison", 1)[0]
        self.assertEqual(1, batch_block.count("compareLayeredTileNeighborhood(logicalCenter)"))
        self.assertIn("Collections.unmodifiableList(comparisons)", batch_block)
        self.assertNotIn("LayeredAdjacentStepCollisionComparison", path_validation)
        self.assertNotIn("LayeredAdjacentStepCollisionComparison", mob)
        self.assertNotIn("LayeredAdjacentStepCollisionComparison", player)
        self.assertIn(
            "### Slice 32: Dormant adjacent-step collision projection",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
