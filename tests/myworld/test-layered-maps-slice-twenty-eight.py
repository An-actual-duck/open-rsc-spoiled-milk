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
COMPARISON = REGION_PACKAGE / "LayeredTileStateParityComparison.java"
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

public final class LayeredTileStateParityComparisonFixture {
    public static void main(String[] args) {
        SyntheticSource source = new SyntheticSource();
        WorldRegionKey upperKey = key(1, 4, 0);
        LayeredRegionTileSnapshot upper =
            LayeredRegionTileSnapshot.capture(upperKey, source);
        WorldLocation upperLocation = location(1, 223, 44);
        LegacyLogicalTileAddress upperAddress = LegacyLogicalTileAddress.resolve(
            upperKey, 31, 44);
        TileValue direct = source.readPackedTile(
            upperAddress.getPackedRegionX(),
            upperAddress.getPackedRegionY(),
            upperAddress.getPackedLocalX(),
            upperAddress.getPackedLocalY());
        LayeredTileStateParityComparison exact =
            LayeredTileStateParityComparison.compare(
                upperLocation, upper, true, direct);
        check(exact.getLogicalLocation().equals(upperLocation), "location");
        check(exact.getAddress().getLegacyPoint().getX() == 223
            && exact.getAddress().getLegacyPoint().getY() == 988, "address");
        check(exact.isLegacyRepresentable(), "representable");
        check(exact.isPackedSourcePresent() && !exact.isMissingPackedSource(),
            "source present");
        check(exact.isComparable() && exact.isExact(), "exact parity");
        check(exact.getDirectPackedState().equals(exact.getLogicalSnapshotState()),
            "state parity");

        TileValue changed = direct.copy();
        changed.overlay++;
        LayeredTileStateParityComparison mismatch =
            LayeredTileStateParityComparison.compare(
                upperLocation, upper, true, changed);
        check(mismatch.isComparable() && !mismatch.isExact(), "mismatch visible");

        SyntheticSource absentSource = new SyntheticSource();
        absentSource.present = false;
        LayeredRegionTileSnapshot absent =
            LayeredRegionTileSnapshot.capture(upperKey, absentSource);
        LayeredTileStateParityComparison missing =
            LayeredTileStateParityComparison.compare(
                upperLocation, absent, false, null);
        check(missing.isLegacyRepresentable(), "missing representable");
        check(!missing.isPackedSourcePresent() && missing.isMissingPackedSource(),
            "missing source");
        check(!missing.isComparable() && !missing.isExact(), "missing not parity");
        check(missing.getDirectPackedState() == null
            && missing.getLogicalSnapshotState() != null, "missing states");
        check(missing.getLogicalSnapshotState().getTraversalMask() == 112,
            "missing blank state");

        WorldRegionKey deepKey = key(-2, 4, 12);
        LayeredRegionTileSnapshot deep =
            LayeredRegionTileSnapshot.capture(deepKey, source);
        LayeredTileStateParityComparison unsupported =
            LayeredTileStateParityComparison.compare(
                location(-2, 223, 620), deep, false, null);
        check(!unsupported.isLegacyRepresentable(), "deep unsupported");
        check(!unsupported.isMissingPackedSource(), "deep not missing");
        check(!unsupported.isComparable() && !unsupported.isExact(),
            "deep not parity");
        check(unsupported.getDirectPackedState() == null
            && unsupported.getLogicalSnapshotState() == null, "deep states");

        expectIllegal(() -> LayeredTileStateParityComparison.compare(
            location(1, 271, 44), upper, true, direct));
        expectIllegal(() -> LayeredTileStateParityComparison.compare(
            upperLocation, upper, false, direct));
        expectIllegal(() -> LayeredTileStateParityComparison.compare(
            location(-2, 223, 620), deep, true, direct));
        expectNull(() -> LayeredTileStateParityComparison.compare(
            null, upper, true, direct));
        expectNull(() -> LayeredTileStateParityComparison.compare(
            upperLocation, null, true, direct));
        check(exact.toString().contains("exact=true"), "comparison string");
    }

    private static WorldRegionKey key(int level, int regionX, int regionY) {
        return new WorldRegionKey(WorldSpaceId.GLOBAL, level, regionX, regionY);
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
        boolean present = true;

        @Override
        public boolean hasPackedRegion(int packedRegionX, int packedRegionY) {
            return present;
        }

        @Override
        public TileValue readPackedTile(
                int packedRegionX,
                int packedRegionY,
                int packedLocalX,
                int packedLocalY) {
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


class LayeredMapsSliceTwentyEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-twenty-eight-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/region/"
            "LayeredTileStateParityComparisonFixture.java"
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
                str(COMPARISON),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_comparison_distinguishes_exact_mismatch_missing_and_unsupported(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.region."
                "LayeredTileStateParityComparisonFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_manager_comparison_is_dormant_and_non_mutating(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "public LayeredTileStateParityComparison compareLayeredTileState(",
            manager,
        )
        self.assertIn("LegacyPackedPointAdapter.fromLegacyPoint(packedPoint)", manager)
        self.assertIn("getLayeredRegionTileSnapshot(key)", manager)
        self.assertIn("peekRegionFromSectorCoordinates(", manager)
        self.assertIn("LayeredTileStateParityComparison.compare(", manager)
        comparison_block = manager.split(
            "public LayeredTileStateParityComparison compareLayeredTileState(", 1
        )[1].split("private Region peekRegionFromSectorCoordinates", 1)[0]
        self.assertNotIn("getRegion(", comparison_block)
        self.assertNotIn("getMutableTile", comparison_block)
        self.assertNotIn("LayeredTileStateParityComparison", player)
        self.assertIn("### Slice 28: Checked current-tile state parity", plan)


if __name__ == "__main__":
    unittest.main()
