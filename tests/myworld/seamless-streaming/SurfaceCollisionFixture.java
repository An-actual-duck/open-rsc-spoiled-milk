package orsc.graphics.three;

import java.util.Collections;

public final class SurfaceCollisionFixture {
	public static void main(String[] args) {
		exercise(false);
		exercise(true);
		System.out.println("PASS published collision strips, cross-cell footprints, overlapping contributors and scope changes");
	}

	private static void exercise(boolean readCells) {
		SurfaceCollisionCells cells = new SurfaceCollisionCells(readCells);
		int[][] initial = terrain();
		initial[47][60] = CollisionFlag.WALL_WEST;
		cells.install("signed-surface", 0, 0, initial);
		cells.wall(1, 48, 60, 1, true);
		cells.wall(2, 48, 60, 1, true);
		cells.wall(99, 48, 60, 1, false);
		cells.wall(1, 48, 60, 1, false);
		check(cells.flagsAt(48, 60, initial[48][60]) == CollisionFlag.WALL_EAST, "removal kept the other wall contribution");
		cells.wall(2, 48, 60, 1, false);
		check(cells.flagsAt(48, 60, initial[48][60]) == 0 && cells.flagsAt(47, 60, initial[47][60]) == CollisionFlag.WALL_WEST, "removal kept authored terrain");

		cells.object(3, 47, 67, 2, 2, 3, 1, true);
		cells.object(4, 48, 68, 0, 1, 1, 1, true);
		cells.object(3, 47, 67, 2, 2, 3, 1, false);
		check(cells.flagsAt(48, 68, initial[48][68]) == CollisionFlag.FULL_BLOCK_C && cells.flagsAt(48, 69, initial[48][69]) == 0, "multi-tile overlap at world cell seam");
		cells.object(4, 48, 68, 0, 1, 1, 1, false);

		int[][] east = terrain();
		east[100][60] = CollisionFlag.FULL_BLOCK_A;
		SurfaceCollisionCells.Prepared incoming = SurfaceCollisionCells.prepare(48, 0, east);
		cells.stage("other-surface", incoming);
		check(cells.incomingCells() == 0, "stale map did not publish");
		cells.stage("signed-surface", incoming);
		check(cells.incomingCells() == 3, "one incoming strip was published");
		check(cells.terrainFlags(148, 60) == CollisionFlag.FULL_BLOCK_A && cells.flagsAt(100, 60, initial[100][60]) == 0,
			"incoming cell did not change the active window");
		check(cells.terrainFlags(47, 60) == CollisionFlag.WALL_WEST, "incoming snapshot kept active terrain");
		cells.stage("signed-surface", incoming);
		check(cells.incomingCells() == 3, "identical cells were retained");
		check(!cells.hasIncomingWindow(48, 0), "terrain alone cannot roll the interactive window");
		cells.publishIncomingScenery(3, 0, 1, Collections.emptyList());
		cells.publishIncomingScenery(3, 1, 1, Collections.emptyList());
		check(!cells.hasIncomingWindow(48, 0), "an incomplete entering strip is unavailable");
		cells.publishIncomingScenery(3, 2, 1, Collections.emptyList());
		check(cells.hasIncomingWindow(48, 0), "explicit empty scenery completes all three terrain cells");
		check(!cells.hasIncomingWindow(48, 48) && !cells.hasIncomingWindow(-48, 0), "wrong edge cannot reuse readiness");
		cells.invalidateIncomingScenery(3, 1);
		check(!cells.hasIncomingWindow(48, 0), "replacement revision must complete before rolling");

		cells.stage("signed-surface", SurfaceCollisionCells.prepare(48, 48, terrain()));
		for (int[] cell : new int[][] {{3, 1}, {3, 2}, {1, 3}, {2, 3}}) {
			cells.publishIncomingScenery(cell[0], cell[1], 1, Collections.emptyList());
		}
		check(!cells.hasIncomingWindow(48, 48), "diagonal requires the corner as well as both edges");
		cells.publishIncomingScenery(3, 3, 1, Collections.emptyList());
		check(cells.hasIncomingWindow(48, 48), "five ready cells cover a diagonal crossing");
		cells.invalidateIncomingScenery(1, 3);
		check(!cells.hasIncomingWindow(48, 48), "unverified edge cannot take part in a diagonal crossing");

		cells.wall(5, 85, 0, 0, true);
		cells.detachView();
		int[][] north = terrain();
		cells.install("signed-surface", 0, -48, north);
		check(cells.flagsAt(85, 47, north[85][47]) == CollisionFlag.WALL_SOUTH && cells.flagsAt(85, 48, north[85][48]) == CollisionFlag.WALL_NORTH,
			"boundary wall and reciprocal contribution survived a world-origin translation");
		cells.wall(5, 85, 48, 0, false);
		check(cells.flagsAt(85, 47, north[85][47]) == 0 && cells.flagsAt(85, 48, north[85][48]) == 0, "wall removal used the new projection consistently");
		cells.wall(6, 85, 48, 0, true);
		int[][] newWorld = terrain();
		cells.install("replacement-surface", 0, -48, newWorld);
		check(cells.flagsAt(85, 47, newWorld[85][47]) == 0 && cells.incomingCells() == 0, "world replacement discarded old contributors");
		cells.install("unaligned-surface", -1, -49, terrain());
		cells.object(7, 142, 142, 0, 2, 2, 1, true);
		int[][] unaligned = terrain();
		unaligned[94][94] = CollisionFlag.FULL_BLOCK_A;
		cells.install("unaligned-surface", 47, -1, unaligned);
		check(cells.flagsAt(94, 94, unaligned[94][94]) == (CollisionFlag.FULL_BLOCK_A | CollisionFlag.FULL_BLOCK_C),
			"overlapping footprint survived an unaligned roll");
		cells.object(7, 94, 94, 0, 2, 2, 1, false);
		check(cells.flagsAt(94, 94, unaligned[94][94]) == CollisionFlag.FULL_BLOCK_A,
			"unaligned roll preserved authored terrain");
	}

	private static int[][] terrain() { return new int[World.LOCAL_TILE_COUNT][World.LOCAL_TILE_COUNT]; }
	private static void check(boolean passed, String cause) {
		if (!passed) throw new AssertionError(cause);
	}
}
