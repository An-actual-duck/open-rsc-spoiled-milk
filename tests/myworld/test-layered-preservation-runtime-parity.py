#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
LADDERS = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/defaults/Ladders.java"
)
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
ASTAR = ROOT / "server/src/com/openrsc/server/model/AStarPathfinder.java"
GAME_STATE_UPDATER = ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
CLIENT_STATE = ROOT / "Client_Base/src/orsc/LayeredSceneContextState.java"
CLIENT_WORLD = ROOT / "Client_Base/src/orsc/graphics/three/World.java"


FIXTURE = r"""
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.model.world.region.NativeLayeredTerrainCollisionPlan;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;

public final class LayeredPreservationRuntimeParityFixture {
    public static void main(String[] args) {
        WorldLocation surface = location(138, 666, 0);
        WorldLocation upper = LayeredRelativeTransition.destination(
            surface, 138, 666, 1);
        check(upper.equals(location(138, 666, 1)), "surface to upper");
        WorldLocation returned = LayeredRelativeTransition.destination(
            upper, 138, 666, -1);
        check(returned.equals(surface), "upper returns to surface");
        WorldLocation deep = LayeredRelativeTransition.destination(
            location(138, 666, -1), 138, 666, -1);
        check(deep.equals(location(138, 666, -2)),
            "underground can descend to declared deeper level");
        expectIllegal(() -> LayeredRelativeTransition.destination(
            surface, 138, 666, 0));

        NativeLayeredPresentationWindow initial =
            NativeLayeredPresentationWindow.select(
                "package@1.0.0:manifest", location(120, 648, 0),
                24, 1, 6, null);
        check(initial.getCenterChunkX() == 5
            && initial.getCenterChunkY() == 27,
            "initial center follows the containing chunk");

        NativeLayeredPresentationWindow boundary =
            NativeLayeredPresentationWindow.select(
                "package@1.0.0:manifest", location(119, 648, 0),
                24, 1, 6, initial);
        check(boundary.getCenterChunkX() == 5,
            "one-tile boundary reversal retains the presentation window");
        check(boundary.covers(location(119, 648, 0), 24, 1),
            "retained window still covers the player");

        NativeLayeredPresentationWindow released =
            NativeLayeredPresentationWindow.select(
                "package@1.0.0:manifest", location(113, 648, 0),
                24, 1, 6, boundary);
        check(released.getCenterChunkX() == 4,
            "window shifts after crossing the retention margin");

        NativeLayeredPresentationWindow teleported =
            NativeLayeredPresentationWindow.select(
                "package@1.0.0:manifest", location(300, 648, 0),
                24, 1, 6, released);
        check(teleported.getCenterChunkX() == 12
            && teleported.covers(location(300, 648, 0), 24, 1),
            "large teleport selects a fresh covering center");

        NativeLayeredPresentationWindow changedLevel =
            NativeLayeredPresentationWindow.select(
                "package@1.0.0:manifest", location(300, 648, 1),
                24, 1, 6, teleported);
        check(changedLevel.getCenterChunkX() == 12,
            "level change starts a fresh window");

        NativeLayeredTerrainTile current = new NativeLayeredTerrainTile(
            0, 0, 250, 0, 1, 2, 3);
        NativeLayeredTerrainTile positiveX = new NativeLayeredTerrainTile(
            0, 0, 0, 0, 0, 5, 0);
        NativeLayeredTerrainTile positiveY = new NativeLayeredTerrainTile(
            0, 0, 0, 0, 4, 0, 0);
        NativeLayeredTerrainCollisionPlan.Result collision =
            NativeLayeredTerrainCollisionPlan.derive(
                current,
                positiveX,
                positiveY,
                overlayId -> overlayId == 2,
                wallId -> true,
                wallId -> wallId % 2 == 0);
        int expectedTerrainMask =
            CollisionFlag.WALL_NORTH
                | CollisionFlag.WALL_EAST
                | CollisionFlag.WALL_SOUTH
                | CollisionFlag.WALL_WEST
                | CollisionFlag.FULL_BLOCK_B;
        check(collision.getTraversalMask() == expectedTerrainMask,
            "native walls derive current and reciprocal collision");
        check(collision.isTerrainBlocked(),
            "overlay 250 retains legacy collision alias 2");
        check(!collision.isOverlayProjectileBlocked(),
            "overlay projectile behavior retains the raw legacy value");
        check(collision.getProjectileWallCount() == 2,
            "native projectile wall contributions are exact");
        TileValue runtimeTile = new TileValue();
        runtimeTile.initializeTerrainCollision();
        collision.applyTo(runtimeTile);
        check((runtimeTile.traversalMask & expectedTerrainMask)
                == expectedTerrainMask,
            "derived native traversal mask reaches runtime tile");
        check((runtimeTile.traversalMask & CollisionFlag.FULL_BLOCK_C) != 0,
            "derived native overlay block reaches runtime tile");
        check(runtimeTile.getTerrainWallProjectileCount() == 2,
            "derived projectile count reaches runtime tile");
    }

    private static WorldLocation location(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void expectIllegal(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
"""


class LayeredPreservationRuntimeParityTest(unittest.TestCase):
    def test_signed_vertical_transition_and_window_hysteresis(self):
        with tempfile.TemporaryDirectory(
            prefix="layered-preservation-runtime-"
        ) as temp:
            temp_path = Path(temp)
            fixture = temp_path / "LayeredPreservationRuntimeParityFixture.java"
            fixture.write_text(FIXTURE, encoding="utf-8")
            sources = [
                COORDINATES / "WorldSpaceId.java",
                COORDINATES / "WorldCoordinate.java",
                COORDINATES / "WorldLocation.java",
                COORDINATES / "LayeredRelativeTransition.java",
                COORDINATES / "NativeLayeredPresentationWindow.java",
                ROOT / (
                    "server/src/com/openrsc/server/io/"
                    "NativeLayeredTerrainTile.java"
                ),
                ROOT / "server/src/com/openrsc/server/io/Tile.java",
                ROOT / (
                    "server/src/com/openrsc/server/model/world/region/"
                    "NativeLayeredTerrainCollisionPlan.java"
                ),
                ROOT / (
                    "server/src/com/openrsc/server/model/world/region/"
                    "TileValue.java"
                ),
                ROOT / (
                    "server/src/com/openrsc/server/util/rsc/"
                    "CollisionFlag.java"
                ),
                fixture,
            ]
            subprocess.run(
                [
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-d",
                    str(temp_path),
                    *(str(source) for source in sources),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    str(temp_path),
                    (
                        "com.openrsc.server.model.world.coordinate."
                        "LayeredPreservationRuntimeParityFixture"
                    ),
                ],
                cwd=ROOT,
                check=True,
            )

    def test_runtime_consumers_use_signed_level_and_scoped_collision(self):
        player = PLAYER.read_text(encoding="utf-8")
        ladders = LADDERS.read_text(encoding="utf-8")
        npc = NPC.read_text(encoding="utf-8")
        astar = ASTAR.read_text(encoding="utf-8")
        updater = GAME_STATE_UPDATER.read_text(encoding="utf-8")

        self.assertIn("public void teleportRelativeLayer(", player)
        self.assertIn("public void teleportToConfiguredRespawn(", player)
        self.assertIn(
            "LegacyPackedPointAdapter.fromPackedValues(", player
        )
        self.assertEqual(
            player.count("teleportToConfiguredRespawn(false);"), 3
        )
        self.assertIn("LayeredRelativeTransition.destination(", player)
        self.assertIn("teleportVertical(player, false, obj);", ladders)
        self.assertIn("player.teleportRelativeLayer(", ladders)
        self.assertIn("!nativeLayered", ladders)
        self.assertIn("TileValue t = getTileAtCurrentLevel(x, y);", npc)
        self.assertIn("public AStarPathfinder(Mob owner", astar)
        self.assertIn("owner.getTileAtCurrentLevel(", astar)
        self.assertIn(
            "NATIVE_LAYERED_WIRE_CHUNK_SIZE =", updater
        )
        self.assertIn("updateCustomMovementClientRegion(player);", updater)
        self.assertIn("compressNativeTerrain(", updater)
        self.assertIn("isWithinClientLocalTileWindow(", updater)

        region_manager = (
            ROOT
            / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
        ).read_text(encoding="utf-8")
        self.assertIn("NativeLayeredTerrainCollisionPlan.derive(", region_manager)
        self.assertIn("nativeLayeredNeighbor(owner, location, 1, 0)", region_manager)
        self.assertIn("nativeLayeredNeighbor(owner, location, 0, 1)", region_manager)

    def test_native_legacy_levels_keep_authentic_client_plane_semantics(self):
        state = CLIENT_STATE.read_text(encoding="utf-8")
        world = CLIENT_WORLD.read_text(encoding="utf-8")

        self.assertIn(
            "if (level == 0 || level == 1 || level == 2 || level == -1)",
            state,
        )
        self.assertIn("return legacyPlaneForLevel(level);", state)
        self.assertIn(
            "int nativePresentationPlane = nativeLayeredPresentationPlane();",
            world,
        )
        self.assertIn("case -1:\n\t\t\t\treturn 3;", world)
        self.assertIn(
            "height == nativePresentationPlane\n"
            "\t\t\t&& nativeLayeredTerrainSnapshot != null",
            world,
        )


if __name__ == "__main__":
    unittest.main()
