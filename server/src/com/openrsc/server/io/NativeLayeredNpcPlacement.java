package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.util.Objects;

/** Immutable NPC placement decoded from a native layered package. */
public final class NativeLayeredNpcPlacement {
	private final String placementId;
	private final int npcId;
	private final WorldLocation start;
	private final int roamRadius;

	NativeLayeredNpcPlacement(
		final String placementId,
		final int npcId,
		final WorldLocation start,
		final int roamRadius) {
		this.placementId = Objects.requireNonNull(placementId, "placementId");
		this.npcId = npcId;
		this.start = Objects.requireNonNull(start, "start");
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
}
