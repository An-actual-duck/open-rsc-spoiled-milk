package com.openrsc.server.model.world.region;

import com.openrsc.server.io.NativeLayeredTerrainTile;
import java.util.Objects;
import java.util.function.IntPredicate;

/** Existing server compatibility projection, not a wide raw-terrain representation. */
public final class NativeTerrainTileAdapter {
	private final IntPredicate blockingOverlay, blockingWall, projectileWall,
		structuralWall, enemyFence;
	public NativeTerrainTileAdapter(IntPredicate blockingOverlay, IntPredicate blockingWall,
		IntPredicate projectileWall, IntPredicate structuralWall, IntPredicate enemyFence) {
		this.blockingOverlay = Objects.requireNonNull(blockingOverlay, "blockingOverlay");
		this.blockingWall = Objects.requireNonNull(blockingWall, "blockingWall");
		this.projectileWall = Objects.requireNonNull(projectileWall, "projectileWall");
		this.structuralWall = Objects.requireNonNull(structuralWall, "structuralWall");
		this.enemyFence = Objects.requireNonNull(enemyFence, "enemyFence");
	}
	public TileValue materialize(NativeLayeredTerrainTile source,
		NativeLayeredTerrainTile positiveX, NativeLayeredTerrainTile positiveY) {
		TileValue tile = new TileValue();
		tile.overlay = (byte) source.getOverlay();
		tile.diagWallVal = (short) source.getDiagonalWall();
		tile.horizontalWallVal = (byte) source.getHorizontalWall();
		tile.verticalWallVal = (byte) source.getVerticalWall();
		tile.elevation = (byte) source.getElevation();
		tile.initializeTerrainCollision();
		NativeLayeredTerrainCollisionPlan.derive(source, positiveX, positiveY,
			blockingOverlay, blockingWall, projectileWall, structuralWall, enemyFence).applyTo(tile);
		return tile;
	}
}
