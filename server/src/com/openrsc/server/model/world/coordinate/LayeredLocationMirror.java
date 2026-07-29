package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Checked layered mirror of an authoritative compatibility point.
 *
 * <p>Legacy callers retain the one-argument path. A named non-legacy
 * projection must supply its exact layered location and capability.</p>
 */
public final class LayeredLocationMirror {
	private final AtomicReference<State> state = new AtomicReference<State>();

	public WorldLocation synchronize(Point authoritativePoint) {
		Objects.requireNonNull(authoritativePoint, "authoritativePoint");
		WorldLocation projected = LegacyPackedPointAdapter.fromLegacyPoint(authoritativePoint);
		return synchronize(authoritativePoint, projected, false);
	}

	public WorldLocation synchronize(
		final Point compatibilityPoint,
		final WorldLocation projectedLocation,
		final boolean allowSyntheticDeepFixture) {
		return synchronize(
			compatibilityPoint,
			projectedLocation,
			allowSyntheticDeepFixture,
			false);
	}

	public WorldLocation synchronize(
		final Point compatibilityPoint,
		final WorldLocation projectedLocation,
		final boolean allowSyntheticDeepFixture,
		final boolean nativeLayeredLocation) {
		Objects.requireNonNull(compatibilityPoint, "compatibilityPoint");
		WorldLocation projected = Objects.requireNonNull(
			projectedLocation, "projectedLocation");
		Point reconstructed =
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(
				projected,
				allowSyntheticDeepFixture,
				nativeLayeredLocation);
		if (reconstructed.getX() != compatibilityPoint.getX()
			|| reconstructed.getY() != compatibilityPoint.getY()) {
			throw new IllegalArgumentException(
				"Compatibility point does not round-trip through the layered mirror");
		}
		state.set(new State(
			compatibilityPoint.getX(),
			compatibilityPoint.getY(),
			projected));
		return projected;
	}

	public WorldLocation requireCurrent(Point authoritativePoint) {
		return requireCurrent(authoritativePoint, false);
	}

	public WorldLocation requireCurrent(
		final Point authoritativePoint,
		final boolean allowSyntheticDeepFixture) {
		return requireCurrent(
			authoritativePoint, allowSyntheticDeepFixture, false);
	}

	public WorldLocation requireCurrent(
		final Point authoritativePoint,
		final boolean allowSyntheticDeepFixture,
		final boolean nativeLayeredLocation) {
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
		Point reconstructed =
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(
				current.location,
				allowSyntheticDeepFixture,
				nativeLayeredLocation);
		if (reconstructed.getX() != authoritativePoint.getX()
			|| reconstructed.getY() != authoritativePoint.getY()) {
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
