#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGION_PACKAGE = ROOT / "server/src/com/openrsc/server/model/world/region"
TILE_VALUE = REGION_PACKAGE / "TileValue.java"
LAYERED_TILE_STATE = REGION_PACKAGE / "LayeredTileState.java"
SNAPSHOT = REGION_PACKAGE / "LayeredRegionTileSnapshot.java"
COLLISION_FLAG = ROOT / "server/src/com/openrsc/server/util/rsc/CollisionFlag.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


FIXTURE = r'''
package com.openrsc.server.model.world.region;

import java.security.MessageDigest;
import java.util.Arrays;

public final class LayeredTileStateFixture {
    public static void main(String[] args) throws Exception {
        TileValue original = populatedTile();
        LayeredTileState state = LayeredTileState.fromLegacy(original);
        check(state.getTraversalMask() == original.traversalMask, "traversal");
        check(state.getDiagonalWallValue() == original.diagWallVal, "diagonal wall");
        check(state.getHorizontalWallValue() == original.horizontalWallVal,
            "horizontal wall");
        check(state.getOverlay() == original.overlay, "overlay");
        check(state.getVerticalWallValue() == original.verticalWallVal, "vertical wall");
        check(state.getElevation() == original.elevation, "elevation");
        check(state.isProjectileAllowed() == original.projectileAllowed, "projectile");
        check(state.isOriginalProjectileAllowed() == original.originalProjectileAllowed,
            "original projectile");
        check(state.isTerrainBlocked() == original.isTerrainBlocked(), "terrain block");
        check(state.getBlockingSceneryCount() == 2, "scenery count");
        check(state.getTerrainCollisionMask() == 13, "terrain collision");
        check(Arrays.equals(new int[] {2, 0, 1, 0, 0, 0},
            state.getDynamicCollisionCounts()), "dynamic collision");
        check(state.isTerrainOverlayProjectileBlocked(), "overlay projectile");
        check(state.getTerrainWallProjectileCount() == 2, "wall projectile");
        check(state.getDynamicProjectileCount() == 3, "dynamic projectile");

        int[] escapedCounts = state.getDynamicCollisionCounts();
        escapedCounts[0] = 999;
        check(state.getDynamicCollisionCounts()[0] == 2, "defensive counts");

        TileValue bridge = state.toLegacyTileValue();
        check(original.equals(bridge), "full legacy round trip");
        LayeredTileState repeated = LayeredTileState.fromLegacy(bridge);
        check(state.equals(repeated), "value equality");
        check(state.hashCode() == repeated.hashCode(), "value hash");
        check(state.toString().contains("dynamicProjectileCount=3"), "value string");

        MessageDigest stateDigest = MessageDigest.getInstance("SHA-256");
        state.updateDigest(stateDigest);
        check(Arrays.equals(stateDigest.digest(), legacyDigest(bridge)),
            "legacy fingerprint field order");

        original.overlay = 99;
        original.removeDynamicCollision(1);
        original.removeDynamicProjectileBlock();
        check(!state.equals(LayeredTileState.fromLegacy(original)), "change sensitive");
        bridge.overlay = 88;
        bridge.removeBlockingScenery();
        check(state.getOverlay() == 17 && state.getBlockingSceneryCount() == 2,
            "bridge mutation isolation");
        check(state.toLegacyTileValue().overlay == 17, "fresh bridge");

        expectNull(() -> LayeredTileState.fromLegacy(null));
    }

    private static TileValue populatedTile() {
        TileValue tile = new TileValue();
        tile.diagWallVal = 1234;
        tile.horizontalWallVal = 11;
        tile.overlay = 17;
        tile.verticalWallVal = 23;
        tile.elevation = -9;
        tile.setTerrainBlocked(true);
        tile.addBlockingScenery();
        tile.addBlockingScenery();
        tile.addTerrainCollision(13);
        tile.addDynamicCollision(5);
        tile.addDynamicCollision(1);
        tile.setTerrainOverlayProjectileBlocked(true);
        tile.addTerrainWallProjectileBlock();
        tile.addTerrainWallProjectileBlock();
        tile.addDynamicProjectileBlock();
        tile.addDynamicProjectileBlock();
        tile.addDynamicProjectileBlock();
        tile.traversalMask = -73;
        tile.projectileAllowed = false;
        tile.originalProjectileAllowed = true;
        return tile;
    }

    private static byte[] legacyDigest(TileValue tile) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        updateInt(digest, tile.traversalMask);
        updateInt(digest, tile.diagWallVal);
        updateInt(digest, tile.horizontalWallVal);
        updateInt(digest, tile.overlay);
        updateInt(digest, tile.verticalWallVal);
        updateInt(digest, tile.elevation);
        digest.update((byte) (tile.projectileAllowed ? 1 : 0));
        digest.update((byte) (tile.originalProjectileAllowed ? 1 : 0));
        digest.update((byte) (tile.isTerrainBlocked() ? 1 : 0));
        updateInt(digest, tile.getBlockingSceneryCount());
        updateInt(digest, tile.getTerrainCollisionMask());
        for (int count : tile.getDynamicCollisionCounts()) {
            updateInt(digest, count);
        }
        digest.update((byte) (tile.isTerrainOverlayProjectileBlocked() ? 1 : 0));
        updateInt(digest, tile.getTerrainWallProjectileCount());
        updateInt(digest, tile.getDynamicProjectileCount());
        return digest.digest();
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
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


class LayeredMapsSliceTwentySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-twenty-seven-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/region/LayeredTileStateFixture.java"
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
                str(fixture),
                str(COLLISION_FLAG),
                str(TILE_VALUE),
                str(LAYERED_TILE_STATE),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_immutable_state_is_full_fidelity_and_legacy_compatible(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.region.LayeredTileStateFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_snapshot_adopts_state_without_changing_gameplay_authority(self):
        state = LAYERED_TILE_STATE.read_text(encoding="utf-8")
        snapshot = SNAPSHOT.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("public final class LayeredTileState", state)
        self.assertNotIn("public void set", state)
        self.assertIn("private final int[] dynamicCollisionCounts", state)
        self.assertIn("LayeredTileState[][] tileStates", snapshot)
        self.assertIn("LayeredTileState.fromLegacy(packedTile)", snapshot)
        self.assertIn("public LayeredTileState getTileState(", snapshot)
        self.assertIn("state.toLegacyTileValue()", snapshot)
        self.assertNotIn("TileValue[][]", snapshot)
        self.assertNotIn("LayeredTileState", player)
        self.assertIn("### Slice 27: Immutable logical tile state", plan)


if __name__ == "__main__":
    unittest.main()
