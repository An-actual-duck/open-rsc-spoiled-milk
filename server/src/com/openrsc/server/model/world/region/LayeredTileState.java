package com.openrsc.server.model.world.region;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/** Immutable, full-fidelity logical value copied from one legacy tile. */
public final class LayeredTileState {
	private final byte traversalMask;
	private final short diagonalWallValue;
	private final byte horizontalWallValue;
	private final byte overlay;
	private final byte verticalWallValue;
	private final byte elevation;
	private final boolean projectileAllowed;
	private final boolean originalProjectileAllowed;
	private final boolean terrainBlocked;
	private final int blockingSceneryCount;
	private final int terrainCollisionMask;
	private final int[] dynamicCollisionCounts;
	private final boolean terrainOverlayProjectileBlocked;
	private final int terrainWallProjectileCount;
	private final int dynamicProjectileCount;

	private LayeredTileState(
		final byte traversalMask,
		final short diagonalWallValue,
		final byte horizontalWallValue,
		final byte overlay,
		final byte verticalWallValue,
		final byte elevation,
		final boolean projectileAllowed,
		final boolean originalProjectileAllowed,
		final boolean terrainBlocked,
		final int blockingSceneryCount,
		final int terrainCollisionMask,
		final int[] dynamicCollisionCounts,
		final boolean terrainOverlayProjectileBlocked,
		final int terrainWallProjectileCount,
		final int dynamicProjectileCount) {
		if (blockingSceneryCount < 0
			|| terrainWallProjectileCount < 0
			|| dynamicProjectileCount < 0) {
			throw new IllegalArgumentException("Logical tile counters must not be negative");
		}
		for (int count : dynamicCollisionCounts) {
			if (count < 0) {
				throw new IllegalArgumentException(
					"Logical dynamic collision counters must not be negative");
			}
		}
		this.traversalMask = traversalMask;
		this.diagonalWallValue = diagonalWallValue;
		this.horizontalWallValue = horizontalWallValue;
		this.overlay = overlay;
		this.verticalWallValue = verticalWallValue;
		this.elevation = elevation;
		this.projectileAllowed = projectileAllowed;
		this.originalProjectileAllowed = originalProjectileAllowed;
		this.terrainBlocked = terrainBlocked;
		this.blockingSceneryCount = blockingSceneryCount;
		this.terrainCollisionMask = terrainCollisionMask;
		this.dynamicCollisionCounts = Arrays.copyOf(
			dynamicCollisionCounts, dynamicCollisionCounts.length);
		this.terrainOverlayProjectileBlocked = terrainOverlayProjectileBlocked;
		this.terrainWallProjectileCount = terrainWallProjectileCount;
		this.dynamicProjectileCount = dynamicProjectileCount;
	}

	public static LayeredTileState fromLegacy(final TileValue tile) {
		Objects.requireNonNull(tile, "tile");
		return new LayeredTileState(
			tile.traversalMask,
			tile.diagWallVal,
			tile.horizontalWallVal,
			tile.overlay,
			tile.verticalWallVal,
			tile.elevation,
			tile.projectileAllowed,
			tile.originalProjectileAllowed,
			tile.isTerrainBlocked(),
			tile.getBlockingSceneryCount(),
			tile.getTerrainCollisionMask(),
			tile.getDynamicCollisionCounts(),
			tile.isTerrainOverlayProjectileBlocked(),
			tile.getTerrainWallProjectileCount(),
			tile.getDynamicProjectileCount());
	}

	/** Returns a fresh mutable compatibility copy; this state remains unchanged. */
	public TileValue toLegacyTileValue() {
		return new TileValue(
			traversalMask,
			diagonalWallValue,
			horizontalWallValue,
			overlay,
			verticalWallValue,
			elevation,
			projectileAllowed,
			originalProjectileAllowed,
			terrainBlocked,
			blockingSceneryCount,
			terrainCollisionMask,
			dynamicCollisionCounts,
			terrainOverlayProjectileBlocked,
			terrainWallProjectileCount,
			dynamicProjectileCount);
	}

	void updateDigest(final MessageDigest digest) {
		Objects.requireNonNull(digest, "digest");
		updateInt(digest, traversalMask);
		updateInt(digest, diagonalWallValue);
		updateInt(digest, horizontalWallValue);
		updateInt(digest, overlay);
		updateInt(digest, verticalWallValue);
		updateInt(digest, elevation);
		digest.update((byte) (projectileAllowed ? 1 : 0));
		digest.update((byte) (originalProjectileAllowed ? 1 : 0));
		digest.update((byte) (terrainBlocked ? 1 : 0));
		updateInt(digest, blockingSceneryCount);
		updateInt(digest, terrainCollisionMask);
		for (int count : dynamicCollisionCounts) {
			updateInt(digest, count);
		}
		digest.update((byte) (terrainOverlayProjectileBlocked ? 1 : 0));
		updateInt(digest, terrainWallProjectileCount);
		updateInt(digest, dynamicProjectileCount);
	}

	private static void updateInt(final MessageDigest digest, final int value) {
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	public byte getTraversalMask() {
		return traversalMask;
	}

	public short getDiagonalWallValue() {
		return diagonalWallValue;
	}

	public byte getHorizontalWallValue() {
		return horizontalWallValue;
	}

	public byte getOverlay() {
		return overlay;
	}

	public byte getVerticalWallValue() {
		return verticalWallValue;
	}

	public byte getElevation() {
		return elevation;
	}

	public boolean isProjectileAllowed() {
		return projectileAllowed;
	}

	public boolean isOriginalProjectileAllowed() {
		return originalProjectileAllowed;
	}

	public boolean isTerrainBlocked() {
		return terrainBlocked;
	}

	public int getBlockingSceneryCount() {
		return blockingSceneryCount;
	}

	public int getTerrainCollisionMask() {
		return terrainCollisionMask;
	}

	public int[] getDynamicCollisionCounts() {
		return Arrays.copyOf(dynamicCollisionCounts, dynamicCollisionCounts.length);
	}

	public boolean isTerrainOverlayProjectileBlocked() {
		return terrainOverlayProjectileBlocked;
	}

	public int getTerrainWallProjectileCount() {
		return terrainWallProjectileCount;
	}

	public int getDynamicProjectileCount() {
		return dynamicProjectileCount;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof LayeredTileState)) {
			return false;
		}
		LayeredTileState state = (LayeredTileState) other;
		return traversalMask == state.traversalMask
			&& diagonalWallValue == state.diagonalWallValue
			&& horizontalWallValue == state.horizontalWallValue
			&& overlay == state.overlay
			&& verticalWallValue == state.verticalWallValue
			&& elevation == state.elevation
			&& projectileAllowed == state.projectileAllowed
			&& originalProjectileAllowed == state.originalProjectileAllowed
			&& terrainBlocked == state.terrainBlocked
			&& blockingSceneryCount == state.blockingSceneryCount
			&& terrainCollisionMask == state.terrainCollisionMask
			&& Arrays.equals(dynamicCollisionCounts, state.dynamicCollisionCounts)
			&& terrainOverlayProjectileBlocked == state.terrainOverlayProjectileBlocked
			&& terrainWallProjectileCount == state.terrainWallProjectileCount
			&& dynamicProjectileCount == state.dynamicProjectileCount;
	}

	@Override
	public int hashCode() {
		int result = traversalMask;
		result = 31 * result + diagonalWallValue;
		result = 31 * result + horizontalWallValue;
		result = 31 * result + overlay;
		result = 31 * result + verticalWallValue;
		result = 31 * result + elevation;
		result = 31 * result + (projectileAllowed ? 1 : 0);
		result = 31 * result + (originalProjectileAllowed ? 1 : 0);
		result = 31 * result + (terrainBlocked ? 1 : 0);
		result = 31 * result + blockingSceneryCount;
		result = 31 * result + terrainCollisionMask;
		result = 31 * result + Arrays.hashCode(dynamicCollisionCounts);
		result = 31 * result + (terrainOverlayProjectileBlocked ? 1 : 0);
		result = 31 * result + terrainWallProjectileCount;
		result = 31 * result + dynamicProjectileCount;
		return result;
	}

	@Override
	public String toString() {
		return "LayeredTileState{traversalMask=" + traversalMask
			+ ", diagonalWallValue=" + diagonalWallValue
			+ ", horizontalWallValue=" + horizontalWallValue
			+ ", overlay=" + overlay
			+ ", verticalWallValue=" + verticalWallValue
			+ ", elevation=" + elevation
			+ ", projectileAllowed=" + projectileAllowed
			+ ", originalProjectileAllowed=" + originalProjectileAllowed
			+ ", terrainBlocked=" + terrainBlocked
			+ ", blockingSceneryCount=" + blockingSceneryCount
			+ ", terrainCollisionMask=" + terrainCollisionMask
			+ ", dynamicCollisionCounts=" + Arrays.toString(dynamicCollisionCounts)
			+ ", terrainOverlayProjectileBlocked="
			+ terrainOverlayProjectileBlocked
			+ ", terrainWallProjectileCount=" + terrainWallProjectileCount
			+ ", dynamicProjectileCount=" + dynamicProjectileCount + '}';
	}
}
