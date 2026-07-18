package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Checked read-only layered mirror of an authoritative legacy packed point.
 *
 * <p>The mirror can only be synchronized from a packed {@link Point}. It does
 * not expose a layered-to-packed mutation path.</p>
 */
public final class LayeredLocationMirror {
	private final AtomicReference<State> state = new AtomicReference<State>();

	public WorldLocation synchronize(Point authoritativePoint) {
		Objects.requireNonNull(authoritativePoint, "authoritativePoint");
		WorldLocation projected = LegacyPackedPointAdapter.fromLegacyPoint(authoritativePoint);
		Point reconstructed = LegacyPackedPointAdapter.toLegacyPoint(projected);
		if (reconstructed.getX() != authoritativePoint.getX()
			|| reconstructed.getY() != authoritativePoint.getY()) {
			throw new IllegalArgumentException(
				"Authoritative packed point does not round-trip through the layered mirror");
		}
		state.set(new State(authoritativePoint.getX(), authoritativePoint.getY(), projected));
		return projected;
	}

	public WorldLocation requireCurrent(Point authoritativePoint) {
		Objects.requireNonNull(authoritativePoint, "authoritativePoint");
		State current = state.get();
		if (current == null) {
			throw new IllegalStateException("Layered location mirror has not been initialized");
		}
		if (current.packedX != authoritativePoint.getX()
			|| current.packedY != authoritativePoint.getY()) {
			throw new IllegalStateException(
				"Layered location mirror does not match the authoritative packed point");
		}
		WorldLocation projected = LegacyPackedPointAdapter.fromLegacyPoint(authoritativePoint);
		if (!current.location.equals(projected)) {
			throw new IllegalStateException(
				"Layered location mirror projection does not match the authoritative packed point");
		}
		return current.location;
	}

	public boolean isInitialized() {
		return state.get() != null;
	}

	private static final class State {
		final int packedX;
		final int packedY;
		final WorldLocation location;

		State(int packedX, int packedY, WorldLocation location) {
			this.packedX = packedX;
			this.packedY = packedY;
			this.location = location;
		}
	}
}
