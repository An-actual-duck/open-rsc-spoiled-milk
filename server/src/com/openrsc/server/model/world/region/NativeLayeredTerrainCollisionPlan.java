package com.openrsc.server.model.world.region;

import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.util.rsc.CollisionFlag;

import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Derives the legacy server collision product for one immutable native tile.
 *
 * <p>The legacy loader writes reciprocal wall flags into neighboring mutable
 * tiles. Native package tiles are materialized independently, so the same
 * result must be derived from the current tile plus its positive-axis
 * neighbors without mutating either neighbor.</p>
 */
public final class NativeLayeredTerrainCollisionPlan {
	private NativeLayeredTerrainCollisionPlan() {
	}

	public static Result derive(
		final NativeLayeredTerrainTile current,
		final NativeLayeredTerrainTile positiveX,
		final NativeLayeredTerrainTile positiveY,
		final IntPredicate blockingOverlay,
		final IntPredicate blockingWall,
		final IntPredicate projectileBlockingWall) {
		NativeLayeredTerrainTile checkedCurrent =
			Objects.requireNonNull(current, "current");
		IntPredicate checkedOverlay =
			Objects.requireNonNull(blockingOverlay, "blockingOverlay");
		IntPredicate checkedWall =
			Objects.requireNonNull(blockingWall, "blockingWall");
		IntPredicate checkedProjectile = Objects.requireNonNull(
			projectileBlockingWall, "projectileBlockingWall");

		int mask = 0;
		int projectileWallCount = 0;

		int currentVertical = checkedCurrent.getVerticalWall();
		if (wallBlocks(currentVertical, checkedWall)) {
			mask |= CollisionFlag.WALL_NORTH;
			projectileWallCount += projectileBlocks(
				currentVertical, checkedProjectile);
		}
		int positiveYVertical =
			positiveY == null ? 0 : positiveY.getVerticalWall();
		if (wallBlocks(positiveYVertical, checkedWall)) {
			mask |= CollisionFlag.WALL_SOUTH;
			projectileWallCount += projectileBlocks(
				positiveYVertical, checkedProjectile);
		}

		int currentHorizontal = checkedCurrent.getHorizontalWall();
		if (wallBlocks(currentHorizontal, checkedWall)) {
			mask |= CollisionFlag.WALL_EAST;
			projectileWallCount += projectileBlocks(
				currentHorizontal, checkedProjectile);
		}
		int positiveXHorizontal =
			positiveX == null ? 0 : positiveX.getHorizontalWall();
		if (wallBlocks(positiveXHorizontal, checkedWall)) {
			mask |= CollisionFlag.WALL_WEST;
			projectileWallCount += projectileBlocks(
				positiveXHorizontal, checkedProjectile);
		}

		int diagonal = checkedCurrent.getDiagonalWall();
		int diagonalWallId = 0;
		int diagonalFlag = 0;
		if (diagonal > 0 && diagonal < 12000) {
			diagonalWallId = diagonal;
			diagonalFlag = CollisionFlag.FULL_BLOCK_B;
		} else if (diagonal > 12000 && diagonal < 24000) {
			diagonalWallId = diagonal - 12000;
			diagonalFlag = CollisionFlag.FULL_BLOCK_A;
		}
		if (wallBlocks(diagonalWallId, checkedWall)) {
			mask |= diagonalFlag;
			projectileWallCount += projectileBlocks(
				diagonal & 0xff, checkedProjectile);
		}

		int rawOverlay = checkedCurrent.getOverlay();
		int collisionOverlay = rawOverlay == 250 ? 2 : rawOverlay;
		boolean terrainBlocked =
			collisionOverlay > 0 && checkedOverlay.test(collisionOverlay);
		boolean overlayProjectileBlocked =
			rawOverlay == 2 || rawOverlay == 11;
		return new Result(
			mask,
			terrainBlocked,
			overlayProjectileBlocked,
			projectileWallCount);
	}

	private static boolean wallBlocks(
		final int wallId,
		final IntPredicate blockingWall) {
		return wallId > 0 && blockingWall.test(wallId);
	}

	private static int projectileBlocks(
		final int wallId,
		final IntPredicate projectileBlockingWall) {
		return wallId > 0 && projectileBlockingWall.test(wallId) ? 1 : 0;
	}

	public static final class Result {
		private final int traversalMask;
		private final boolean terrainBlocked;
		private final boolean overlayProjectileBlocked;
		private final int projectileWallCount;

		private Result(
			final int traversalMask,
			final boolean terrainBlocked,
			final boolean overlayProjectileBlocked,
			final int projectileWallCount) {
			this.traversalMask = traversalMask;
			this.terrainBlocked = terrainBlocked;
			this.overlayProjectileBlocked = overlayProjectileBlocked;
			this.projectileWallCount = projectileWallCount;
		}

		public int getTraversalMask() {
			return traversalMask;
		}

		public boolean isTerrainBlocked() {
			return terrainBlocked;
		}

		public boolean isOverlayProjectileBlocked() {
			return overlayProjectileBlocked;
		}

		public int getProjectileWallCount() {
			return projectileWallCount;
		}

		public void applyTo(final TileValue tile) {
			TileValue target = Objects.requireNonNull(tile, "tile");
			target.addTerrainCollision(traversalMask);
			target.setTerrainBlocked(terrainBlocked);
			target.setTerrainOverlayProjectileBlocked(
				overlayProjectileBlocked);
			for (int i = 0; i < projectileWallCount; i++) {
				target.addTerrainWallProjectileBlock();
			}
		}
	}
}
