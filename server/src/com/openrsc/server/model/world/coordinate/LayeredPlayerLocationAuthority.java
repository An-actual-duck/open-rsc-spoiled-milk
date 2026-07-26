package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Player-owned layered location authority with a derived legacy Point view.
 *
 * <p>Legacy callers may still propose packed points while the compatibility
 * boundary is active. The proposal is normalized to a {@link WorldLocation}
 * first, and the returned Point is derived from that normalized value. Direct
 * layered callers use the same checked projection path.</p>
 */
public final class LayeredPlayerLocationAuthority {
	public static final String ID = "layered-player-location-authority-v1";

	private final AtomicReference<WorldLocation> location =
		new AtomicReference<WorldLocation>();

	public Point initializeFromLegacy(final Point proposedLegacyPoint) {
		return initialize(LegacyPackedPointAdapter.fromLegacyPoint(
			Objects.requireNonNull(proposedLegacyPoint, "proposedLegacyPoint")));
	}

	public Point initialize(final WorldLocation proposedLocation) {
		return initialize(proposedLocation, false);
	}

	public Point initialize(
		final WorldLocation proposedLocation,
		final boolean allowSyntheticDeepFixture) {
		return initialize(
			proposedLocation, allowSyntheticDeepFixture, false);
	}

	public Point initialize(
		final WorldLocation proposedLocation,
		final boolean allowSyntheticDeepFixture,
		final boolean nativeLayeredLocation) {
		WorldLocation checked = Objects.requireNonNull(
			proposedLocation, "proposedLocation");
		Point projection =
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(
				checked,
				allowSyntheticDeepFixture,
				nativeLayeredLocation);
		location.set(checked);
		return projection;
	}

	public Point moveFromLegacy(final Point proposedLegacyPoint) {
		requireInitialized();
		return initializeFromLegacy(proposedLegacyPoint);
	}

	public Point move(final WorldLocation proposedLocation) {
		return move(proposedLocation, false);
	}

	public Point move(
		final WorldLocation proposedLocation,
		final boolean allowSyntheticDeepFixture) {
		return move(proposedLocation, allowSyntheticDeepFixture, false);
	}

	public Point move(
		final WorldLocation proposedLocation,
		final boolean allowSyntheticDeepFixture,
		final boolean nativeLayeredLocation) {
		requireInitialized();
		return initialize(
			proposedLocation,
			allowSyntheticDeepFixture,
			nativeLayeredLocation);
	}

	public WorldLocation requireCurrent(final Point derivedLegacyPoint) {
		return requireCurrent(derivedLegacyPoint, false);
	}

	public WorldLocation requireCurrent(
		final Point derivedLegacyPoint,
		final boolean allowSyntheticDeepFixture) {
		return requireCurrent(
			derivedLegacyPoint, allowSyntheticDeepFixture, false);
	}

	public WorldLocation requireCurrent(
		final Point derivedLegacyPoint,
		final boolean allowSyntheticDeepFixture,
		final boolean nativeLayeredLocation) {
		Objects.requireNonNull(derivedLegacyPoint, "derivedLegacyPoint");
		WorldLocation current = requireInitialized();
		Point expected =
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(
				current,
				allowSyntheticDeepFixture,
				nativeLayeredLocation);
		if (expected.getX() != derivedLegacyPoint.getX()
			|| expected.getY() != derivedLegacyPoint.getY()) {
			throw new IllegalStateException(
				"Derived legacy Point does not match layered Player authority");
		}
		return current;
	}

	public boolean isInitialized() {
		return location.get() != null;
	}

	private WorldLocation requireInitialized() {
		WorldLocation current = location.get();
		if (current == null) {
			throw new IllegalStateException(
				"Layered Player location authority has not been initialized");
		}
		return current;
	}
}
