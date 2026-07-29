#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
SNAPSHOT_SOURCE = ROOT / "server/src/com/openrsc/server/model/world/region/LayeredRegionTileSnapshot.java"
TILE_SOURCE = ROOT / "server/src/com/openrsc/server/model/world/region/TileValue.java"
TILE_STATE_SOURCE = ROOT / "server/src/com/openrsc/server/model/world/region/LayeredTileState.java"
COLLISION_SOURCE = ROOT / "server/src/com/openrsc/server/util/rsc/CollisionFlag.java"
REGION_MANAGER_SOURCE = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PLAYER_SOURCE = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
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


SNAPSHOT_FIXTURE = r'''
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;

public final class LayeredRegionTileSnapshotFixture {
    public static void main(String[] args) {
        SyntheticSource source = new SyntheticSource();
        WorldRegionKey upperKey = key(1, 4, 0);
        LayeredRegionTileSnapshot upper =
            LayeredRegionTileSnapshot.capture(upperKey, source);
        check(upper.getLogicalRegionKey().equals(upperKey), "upper key");
        check(upper.isComplete(), "upper complete");
        check(upper.getTargetTileCount() == 2304
            && upper.getSupportedTileCount() == 2304, "upper counts");
        check(upper.getSourceFragmentCount() == 2, "upper sources");
        check(upper.getMissingSourceRegionCount() == 0, "upper present sources");
        check(upper.getFingerprint().matches("[0-9a-f]{64}"), "upper fingerprint");
        check(upper.isLegacySupported(0, 0)
            && upper.isLegacySupported(47, 47), "upper support");
        checkTile(upper.getTileValue(0, 0), 4, 19, 0, 32, 0);
        checkTile(upper.getTileValue(0, 16), 4, 20, 0, 0, 0);
        checkTile(upper.getTileValue(47, 47), 4, 20, 47, 31, 0);

        TileValue escaped = upper.getTileValue(0, 0);
        escaped.overlay = 99;
        escaped.addDynamicCollision(1);
        checkTile(upper.getTileValue(0, 0), 4, 19, 0, 32, 0);
        LayeredRegionTileSnapshot repeated =
            LayeredRegionTileSnapshot.capture(upperKey, source);
        check(upper.getFingerprint().equals(repeated.getFingerprint()),
            "stable fingerprint");
        source.revision = 1;
        LayeredRegionTileSnapshot changed =
            LayeredRegionTileSnapshot.capture(upperKey, source);
        check(!upper.getFingerprint().equals(changed.getFingerprint()),
            "changed fingerprint");
        checkTile(upper.getTileValue(0, 0), 4, 19, 0, 32, 0);
        checkTile(changed.getTileValue(0, 0), 4, 19, 0, 32, 1);

        SyntheticSource missingSource = new SyntheticSource();
        missingSource.missingRegionY = 20;
        LayeredRegionTileSnapshot missing =
            LayeredRegionTileSnapshot.capture(upperKey, missingSource);
        check(missing.isComplete(), "missing source logical completeness");
        check(missing.getMissingSourceRegionCount() == 1, "missing source count");
        check(missing.getSupportedTileCount() == 2304, "missing supported count");
        TileValue blank = missing.getTileValue(0, 16);
        check(blank.traversalMask == 112 && blank.overlay == 0,
            "missing source blank tile");
        check(!missing.getFingerprint().equals(upper.getFingerprint()),
            "missing source fingerprint");

        SyntheticSource terminalSource = new SyntheticSource();
        LayeredRegionTileSnapshot terminal = LayeredRegionTileSnapshot.capture(
            key(-1, 682, 19), terminalSource);
        check(!terminal.isComplete(), "terminal partial");
        check(terminal.getSupportedTileCount() == 1024, "terminal count");
        check(terminal.isLegacySupported(31, 31), "terminal corner supported");
        check(!terminal.isLegacySupported(32, 31)
            && !terminal.isLegacySupported(31, 32), "terminal unsupported");
        check(terminal.getTileValue(32, 31) == null, "terminal null tile");
        checkTile(terminal.getTileValue(31, 31), 682, 78, 31, 31, 0);

        SyntheticSource deepSource = new SyntheticSource();
        LayeredRegionTileSnapshot deep = LayeredRegionTileSnapshot.capture(
            key(-2, 4, 12), deepSource);
        check(!deep.isComplete() && deep.getSupportedTileCount() == 0,
            "deep unsupported");
        check(deep.getSourceFragmentCount() == 0
            && deep.getMissingSourceRegionCount() == 0, "deep no sources");
        check(deepSource.readCount == 0, "deep no tile reads");
        check(deep.getTileValue(0, 0) == null, "deep null tile");

        expectIllegal(() -> upper.getTileValue(-1, 0));
        expectIllegal(() -> upper.getTileValue(48, 0));
        expectIllegal(() -> upper.isLegacySupported(0, 48));
        expectNull(() -> LayeredRegionTileSnapshot.capture(null, source));
        expectNull(() -> LayeredRegionTileSnapshot.capture(upperKey, null));
        check(upper.toString().contains("supportedTileCount=2304"),
            "snapshot string");
    }

    private static WorldRegionKey key(int level, int regionX, int regionY) {
        return new WorldRegionKey(WorldSpaceId.GLOBAL, level, regionX, regionY);
    }

    private static void checkTile(
            TileValue tile,
            int regionX,
            int regionY,
            int localX,
            int localY,
            int revision) {
        int seed = seed(regionX, regionY, localX, localY, revision);
        check(tile != null, "tile present");
        check(tile.overlay == (byte) seed, "tile overlay");
        check(tile.elevation == (byte) (seed >>> 8), "tile elevation");
        check(tile.diagWallVal == (short) (seed >>> 16), "tile wall");
        check(tile.isTerrainBlocked() == ((seed & 1) != 0), "tile terrain blocked");
        check(tile.getBlockingSceneryCount() == ((seed >>> 1) & 1),
            "tile scenery count");
        check(tile.getTerrainCollisionMask() == ((seed >>> 2) & 15),
            "tile terrain collision");
        check(tile.getDynamicCollisionCounts()[0] == ((seed >>> 6) & 1),
            "tile dynamic collision");
        check(tile.isTerrainOverlayProjectileBlocked() == ((seed & 128) != 0),
            "tile overlay projectile");
        check(tile.getTerrainWallProjectileCount() == ((seed >>> 8) & 1),
            "tile wall projectile");
        check(tile.getDynamicProjectileCount() == ((seed >>> 9) & 1),
            "tile dynamic projectile");
    }

    private static int seed(
            int regionX, int regionY, int localX, int localY, int revision) {
        return regionX * 1000003 + regionY * 10007 + localX * 101
            + localY * 3 + revision * 17;
    }

    private static final class SyntheticSource
            implements LayeredRegionTileSnapshot.PackedTileSource {
        int revision;
        int missingRegionY = -1;
        int readCount;

        @Override
        public boolean hasPackedRegion(int packedRegionX, int packedRegionY) {
            return packedRegionY != missingRegionY;
        }

        @Override
        public TileValue readPackedTile(
                int packedRegionX,
                int packedRegionY,
                int packedLocalX,
                int packedLocalY) {
            readCount++;
            int seed = seed(
                packedRegionX, packedRegionY, packedLocalX, packedLocalY, revision);
            TileValue tile = new TileValue();
            tile.overlay = (byte) seed;
            tile.elevation = (byte) (seed >>> 8);
            tile.diagWallVal = (short) (seed >>> 16);
            tile.setTerrainBlocked((seed & 1) != 0);
            if (((seed >>> 1) & 1) != 0) {
                tile.addBlockingScenery();
            }
            tile.addTerrainCollision((seed >>> 2) & 15);
            if (((seed >>> 6) & 1) != 0) {
                tile.addDynamicCollision(1);
            }
            tile.setTerrainOverlayProjectileBlocked((seed & 128) != 0);
            if (((seed >>> 8) & 1) != 0) {
                tile.addTerrainWallProjectileBlock();
            }
            if (((seed >>> 9) & 1) != 0) {
                tile.addDynamicProjectileBlock();
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


class LayeredMapsSliceTwentyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-twenty-five-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/region/"
            "LayeredRegionTileSnapshotFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(SNAPSHOT_FIXTURE, encoding="utf-8")

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
                *(str(path) for path in sorted(SERVER_COORDINATES.glob("*.java"))),
                str(COLLISION_SOURCE),
                str(TILE_SOURCE),
                str(TILE_STATE_SOURCE),
                str(SNAPSHOT_SOURCE),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_snapshot_is_detached_complete_partial_and_fingerprinted(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.region."
                "LayeredRegionTileSnapshotFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_snapshot_is_explicit_read_only_and_non_authoritative(self):
        manager = REGION_MANAGER_SOURCE.read_text(encoding="utf-8")
        player = PLAYER_SOURCE.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "public LayeredRegionTileSnapshot getLayeredRegionTileSnapshot(",
            manager,
        )
        self.assertIn("LayeredRegionTileSnapshot.capture(", manager)
        self.assertIn("peekRegionFromSectorCoordinates(", manager)
        self.assertIn("region.getTileValue(packedLocalX, packedLocalY)", manager)
        self.assertIn("ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>", manager)
        self.assertIn("visibleRegionWindowCache.putIfAbsent", manager)
        self.assertNotIn("LayeredRegionTileSnapshot", player)
        self.assertNotIn("getLayeredRegionTileSnapshot(player", manager)
        self.assertIn("### Slice 25: Detached logical-region tile snapshots", plan)


if __name__ == "__main__":
    unittest.main()
