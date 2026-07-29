package com.openrsc.server.model.world.coordinate;

import java.util.Objects;

/**
 * Stable presentation-chunk center for one native package scope.
 *
 * <p>The retained margin prevents a player walking back and forth across one
 * chunk boundary from repeatedly rebuilding the same client terrain window.
 * It does not alter authoritative position or terrain ownership.</p>
 */
public final class NativeLayeredPresentationWindow {
	private final String packageIdentity;
	private final WorldSpaceId worldSpace;
	private final int level;
	private final int centerChunkX;
	private final int centerChunkY;

	private NativeLayeredPresentationWindow(
		final String packageIdentity,
		final WorldSpaceId worldSpace,
		final int level,
		final int centerChunkX,
		final int centerChunkY) {
		this.packageIdentity = Objects.requireNonNull(
			packageIdentity, "packageIdentity");
		this.worldSpace = Objects.requireNonNull(worldSpace, "worldSpace");
		this.level = level;
		this.centerChunkX = centerChunkX;
		this.centerChunkY = centerChunkY;
	}

	public static NativeLayeredPresentationWindow select(
		final String packageIdentity,
		final WorldLocation location,
		final int chunkSize,
		final int chunkRadius,
		final int retentionMargin,
		final NativeLayeredPresentationWindow previous) {
		String checkedIdentity = Objects.requireNonNull(
			packageIdentity, "packageIdentity");
		WorldLocation checkedLocation = Objects.requireNonNull(
			location, "location");
		if (checkedIdentity.isEmpty() || chunkSize <= 0 || chunkRadius < 1
			|| retentionMargin < 0 || retentionMargin >= chunkSize) {
			throw new IllegalArgumentException(
				"Native presentation-window policy is invalid");
		}
		WorldCoordinate coordinate = checkedLocation.getCoordinate();
		int actualX = Math.floorDiv(coordinate.getX(), chunkSize);
		int actualY = Math.floorDiv(coordinate.getY(), chunkSize);
		if (previous == null
			|| !previous.packageIdentity.equals(checkedIdentity)
			|| !previous.worldSpace.equals(checkedLocation.getWorldSpace())
			|| previous.level != coordinate.getLevel()) {
			return new NativeLayeredPresentationWindow(
				checkedIdentity,
				checkedLocation.getWorldSpace(),
				coordinate.getLevel(),
				actualX,
				actualY);
		}
		return new NativeLayeredPresentationWindow(
			checkedIdentity,
			checkedLocation.getWorldSpace(),
			coordinate.getLevel(),
			retainedCenter(
				coordinate.getX(),
				actualX,
				previous.centerChunkX,
				chunkSize,
				chunkRadius,
				retentionMargin),
			retainedCenter(
				coordinate.getY(),
				actualY,
				previous.centerChunkY,
				chunkSize,
				chunkRadius,
				retentionMargin));
	}

	private static int retainedCenter(
		final int tile,
		final int actualChunk,
		final int previousCenter,
		final int chunkSize,
		final int chunkRadius,
		final int retentionMargin) {
		int distance = Math.abs(actualChunk - previousCenter);
		if (distance == 0) {
			return previousCenter;
		}
		if (distance > chunkRadius) {
			return actualChunk;
		}
		if (actualChunk > previousCenter) {
			int releaseAt = Math.addExact(
				Math.multiplyExact(previousCenter + 1, chunkSize),
				retentionMargin);
			return tile < releaseAt ? previousCenter : actualChunk;
		}
		int releaseBelow = Math.subtractExact(
			Math.multiplyExact(previousCenter, chunkSize),
			retentionMargin);
		return tile >= releaseBelow ? previousCenter : actualChunk;
	}

	public int getCenterChunkX() {
		return centerChunkX;
	}

	public int getCenterChunkY() {
		return centerChunkY;
	}

	public boolean covers(
		final WorldLocation location,
		final int chunkSize,
		final int chunkRadius) {
		WorldLocation checked = Objects.requireNonNull(location, "location");
		return worldSpace.equals(checked.getWorldSpace())
			&& level == checked.getCoordinate().getLevel()
			&& Math.abs(
				Math.floorDiv(
					checked.getCoordinate().getX(), chunkSize)
					- centerChunkX) <= chunkRadius
			&& Math.abs(
				Math.floorDiv(
					checked.getCoordinate().getY(), chunkSize)
					- centerChunkY) <= chunkRadius;
	}
}
