#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION_PACKAGE = ROOT / "server/src/com/openrsc/server/model/world/region"
TILE_VALUE = REGION_PACKAGE / "TileValue.java"
TILE_STATE = REGION_PACKAGE / "LayeredTileState.java"
SNAPSHOT = REGION_PACKAGE / "LayeredRegionTileSnapshot.java"
CELL_COMPARISON = REGION_PACKAGE / "LayeredTileStateParityComparison.java"
NEIGHBORHOOD = REGION_PACKAGE / "LayeredTileNeighborhoodParityComparison.java"
REGION_MANAGER = REGION_PACKAGE / "RegionManager.java"
COLLISION_FLAG = ROOT / "server/src/com/openrsc/server/util/rsc/CollisionFlag.java"
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LayeredTileNeighborhoodParityComparisonFixture {
    public static void main(String[] args) {
        SyntheticSource source = new SyntheticSource();
        WorldLocation center = location(1, 239, 16);
        LayeredTileNeighborhoodParityComparison exact = build(center, source);
        check(exact.getCenter().equals(center), "center");
        check(exact.getCells().size() == 9, "cell count");
        check(exact.getCenterCell().getLogicalLocation().equals(center), "center cell");
        check(exact.getCell(-1, -1).getLogicalLocation().equals(
            location(1, 238, 15)), "first cell");
        check(exact.getCell(1, 1).getLogicalLocation().equals(
            location(1, 240, 17)), "last cell");
        check(exact.getLegacyRepresentableCount() == 9, "representable count");
        check(exact.getUnsupportedCount() == 0, "unsupported count");
        check(exact.getPackedSourcePresentCount() == 9, "source count");
        check(exact.getMissingPackedSourceCount() == 0, "missing count");
        check(exact.getComparableCount() == 9 && exact.getExactCount() == 9,
            "exact counts");
        check(exact.isComplete() && exact.isExact(), "complete exact");

        Set<String> logicalRegions = new HashSet<String>();
        Set<String> packedRegions = new HashSet<String>();
        for (LayeredTileStateParityComparison cell : exact.getCells()) {
            WorldRegionKey key = cell.getAddress().getLogicalRegionKey();
            logicalRegions.add(key.getRegionX() + ":" + key.getRegionY());
            packedRegions.add(cell.getAddress().getPackedRegionX()
                + ":" + cell.getAddress().getPackedRegionY());
        }
        check(logicalRegions.size() == 2, "logical X boundary crossed");
        check(packedRegions.size() == 4, "packed X and Y boundaries crossed");

        try {
            exact.getCells().clear();
            throw new AssertionError("Expected immutable cells");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        expectIllegal(() -> exact.getCell(2, 0));

        SyntheticSource partialSource = new SyntheticSource();
        partialSource.missing.add("4:20");
        LayeredTileNeighborhoodParityComparison partial = build(center, partialSource);
        check(partial.getMissingPackedSourceCount() > 0, "partial missing");
        check(partial.getPackedSourcePresentCount() < 9, "partial present");
        check(partial.getLegacyRepresentableCount() == 9, "partial representable");
        check(!partial.isComplete() && !partial.isExact(), "partial status");
        check(partial.getComparableCount() == partial.getExactCount(),
            "available partial cells exact");

        LayeredTileNeighborhoodParityComparison deep = build(
            location(-2, 239, 16), source);
        check(deep.getUnsupportedCount() == 9, "deep unsupported");
        check(deep.getLegacyRepresentableCount() == 0, "deep representable");
        check(deep.getPackedSourcePresentCount() == 0, "deep sources");
        check(deep.getMissingPackedSourceCount() == 0, "deep missing");
        check(deep.getComparableCount() == 0 && deep.getExactCount() == 0,
            "deep parity counts");
        check(!deep.isComplete() && !deep.isExact(), "deep status");

        List<LayeredTileStateParityComparison> wrong =
            new ArrayList<LayeredTileStateParityComparison>(exact.getCells());
        wrong.set(0, wrong.get(1));
        expectIllegal(() -> LayeredTileNeighborhoodParityComparison.of(center, wrong));
        expectIllegal(() -> LayeredTileNeighborhoodParityComparison.of(
            center, wrong.subList(0, 8)));
        expectNull(() -> LayeredTileNeighborhoodParityComparison.of(null, wrong));
        check(exact.toString().contains("exactCount=9"), "comparison string");
    }

    private static LayeredTileNeighborhoodParityComparison build(
            WorldLocation center, SyntheticSource source) {
        List<LayeredTileStateParityComparison> cells =
            new ArrayList<LayeredTileStateParityComparison>();
        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                WorldLocation location = LayeredTileNeighborhoodParityComparison.offset(
                    center, offsetX, offsetY);
                WorldRegionKey key = WorldRegionKey.from(location);
                LayeredRegionTileSnapshot snapshot =
                    LayeredRegionTileSnapshot.capture(key, source);
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
                    location, snapshot, present, direct));
            }
        }
        return LayeredTileNeighborhoodParityComparison.of(center, cells);
    }

    private static WorldLocation location(int level, int x, int y) {
        return new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(x, y, level));
    }

    private static int seed(
            int regionX, int regionY, int localX, int localY) {
        return regionX * 1000003 + regionY * 10007 + localX * 101 + localY * 3;
    }

    private static final class SyntheticSource
            implements LayeredRegionTileSnapshot.PackedTileSource {
        final Set<String> missing = new HashSet<String>();

        @Override
        public boolean hasPackedRegion(int packedRegionX, int packedRegionY) {
            return !missing.contains(packedRegionX + ":" + packedRegionY);
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
            int seed = seed(
                packedRegionX, packedRegionY, packedLocalX, packedLocalY);
            TileValue tile = new TileValue();
            tile.overlay = (byte) seed;
            tile.elevation = (byte) (seed >>> 8);
            tile.diagWallVal = (short) (seed >>> 16);
            tile.addTerrainCollision(seed & 15);
            if ((seed & 16) != 0) {
                tile.addDynamicCollision(1);
            }
            return tile;
        }
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


class LayeredMapsSliceThirtyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-thirty-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/region/"
            "LayeredTileNeighborhoodParityComparisonFixture.java"
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
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_neighborhood_crosses_boundaries_and_retains_explicit_status(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.region."
                "LayeredTileNeighborhoodParityComparisonFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_manager_neighborhood_is_dormant_bounded_and_non_mutating(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "compareLayeredTileNeighborhood(final Point packedCenter)",
            manager,
        )
        self.assertIn(
            "compareLayeredTileNeighborhood(final WorldLocation logicalCenter)",
            manager,
        )
        self.assertIn(
            "Map<WorldRegionKey, LayeredRegionTileSnapshot> snapshots",
            manager,
        )
        self.assertIn("snapshot = getLayeredRegionTileSnapshot(key)", manager)
        self.assertIn("cells.add(compareLayeredTileState(location, snapshot))", manager)
        neighborhood_block = manager.split(
            "compareLayeredTileNeighborhood(final WorldLocation logicalCenter)", 1
        )[1].split(
            "private LayeredTileStateParityComparison compareLayeredTileState", 1
        )[0]
        self.assertNotIn("getRegion(", neighborhood_block)
        self.assertNotIn("getMutableTile", neighborhood_block)
        self.assertNotIn("LayeredTileNeighborhoodParityComparison", player)
        self.assertIn(
            "### Slice 30: Checked logical tile-neighborhood parity",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
