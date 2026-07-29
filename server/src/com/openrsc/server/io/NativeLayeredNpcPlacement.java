package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.util.Objects;

/** Immutable NPC placement decoded from a native layered package. */
public final class NativeLayeredNpcPlacement {
	private final String placementId;
	private final int npcId;
	private final WorldLocation start;
	private final int minX;
	private final int minY;
	private final int maxX;
	private final int maxY;
	private final int roamRadius;

	NativeLayeredNpcPlacement(
		final String placementId,
		final int npcId,
		final WorldLocation start,
		final int roamRadius) {
		this(
			placementId,
			npcId,
			start,
			Math.subtractExact(start.getCoordinate().getX(), roamRadius),
			Math.subtractExact(start.getCoordinate().getY(), roamRadius),
			Math.addExact(start.getCoordinate().getX(), roamRadius),
			Math.addExact(start.getCoordinate().getY(), roamRadius),
			roamRadius);
	}

	NativeLayeredNpcPlacement(
		final String placementId,
		final int npcId,
		final WorldLocation start,
		final int minX,
		final int minY,
		final int maxX,
		final int maxY) {
		this(
			placementId,
			npcId,
			start,
			minX,
			minY,
			maxX,
			maxY,
			-1);
	}

	private NativeLayeredNpcPlacement(
		final String placementId,
		final int npcId,
		final WorldLocation start,
		final int minX,
		final int minY,
		final int maxX,
		final int maxY,
		final int roamRadius) {
		this.placementId = Objects.requireNonNull(placementId, "placementId");
		this.npcId = npcId;
		this.start = Objects.requireNonNull(start, "start");
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		this.roamRadius = roamRadius;
	}

	public String getPlacementId() {
		return placementId;
	}

	public int getNpcId() {
		return npcId;
	}

	public WorldLocation getStart() {
		return start;
	}

	public int getRoamRadius() {
		return roamRadius;
	}

	public int getMinX() {
		return minX;
	}

	public int getMinY() {
		return minY;
	}

	public int getMaxX() {
		return maxX;
	}

	public int getMaxY() {
		return maxY;
	}
}
