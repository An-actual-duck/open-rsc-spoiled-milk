package com.openrsc.server.model.world.coordinate;

import java.util.Objects;

/** Pure signed-level destination calculation for aligned vertical travel. */
public final class LayeredRelativeTransition {
	private LayeredRelativeTransition() { }

	public static WorldLocation destination(
		final WorldLocation source,
		final int destinationX,
		final int destinationY,
		final int levelDelta) {
		WorldLocation checked = Objects.requireNonNull(source, "source");
		if (levelDelta == 0) {
			throw new IllegalArgumentException(
				"Relative layered transition requires a non-zero level delta");
		}
		return new WorldLocation(
			checked.getWorldSpace(),
			new WorldCoordinate(
				destinationX,
				destinationY,
				Math.addExact(
					checked.getCoordinate().getLevel(), levelDelta)));
	}
}
