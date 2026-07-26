package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;

/**
 * Immutable layered projection of one legacy player-location persistence value.
 *
 * <p>This Slice 14 bridge captures the authoritative packed point exactly once,
 * proves that it round-trips through the layered coordinate contract, and keeps
 * the original packed values for the unchanged database schema. It deliberately
 * exposes no layered-to-legacy factory.</p>
 */
public final class LegacyPlayerLocationPersistenceSnapshot {
	public static final String ID = "legacy-player-location-shadow-v1";

	private final int packedX;
	private final int packedY;
	private final WorldLocation layeredLocation;

	private LegacyPlayerLocationPersistenceSnapshot(
		final int packedX,
		final int packedY,
		final WorldLocation layeredLocation) {
		this.packedX = packedX;
		this.packedY = packedY;
		this.layeredLocation = layeredLocation;
	}

	public static LegacyPlayerLocationPersistenceSnapshot capture(
		final Point authoritativeLegacyPoint) {
		Objects.requireNonNull(authoritativeLegacyPoint, "authoritativeLegacyPoint");
		WorldLocation projected = LegacyPackedPointAdapter.fromLegacyPoint(
			authoritativeLegacyPoint);
		Point reconstructed = LegacyPackedPointAdapter.toLegacyPoint(projected);
		if (reconstructed.getX() != authoritativeLegacyPoint.getX()
			|| reconstructed.getY() != authoritativeLegacyPoint.getY()) {
			throw new IllegalArgumentException(
				"Legacy player location does not round-trip through the layered persistence snapshot");
		}
		return new LegacyPlayerLocationPersistenceSnapshot(
			authoritativeLegacyPoint.getX(), authoritativeLegacyPoint.getY(), projected);
	}

	/**
	 * Captures an authoritative layered location together with its checked
	 * compatibility receipt.
	 *
	 * <p>This overload is required for named projections whose packed X/Y
	 * cannot be decoded by the legacy Y-band codec. It retains the exact same
	 * database columns while refusing any receipt that does not match the
	 * supplied layered location.</p>
	 */
	public static LegacyPlayerLocationPersistenceSnapshot capture(
		final Point authoritativeCompatibilityPoint,
		final WorldLocation authoritativeLayeredLocation,
		final boolean allowSyntheticDeepFixture) {
		return capture(
			authoritativeCompatibilityPoint,
			authoritativeLayeredLocation,
			allowSyntheticDeepFixture,
			false);
	}

	public static LegacyPlayerLocationPersistenceSnapshot capture(
		final Point authoritativeCompatibilityPoint,
		final WorldLocation authoritativeLayeredLocation,
		final boolean allowSyntheticDeepFixture,
		final boolean nativeLayeredLocation) {
		Point checkedPoint = Objects.requireNonNull(
			authoritativeCompatibilityPoint,
			"authoritativeCompatibilityPoint");
		WorldLocation checkedLocation = Objects.requireNonNull(
			authoritativeLayeredLocation,
			"authoritativeLayeredLocation");
		Point expected =
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(
				checkedLocation,
				allowSyntheticDeepFixture,
				nativeLayeredLocation);
		if (expected.getX() != checkedPoint.getX()
			|| expected.getY() != checkedPoint.getY()) {
			throw new IllegalArgumentException(
				"Layered player location does not match its compatibility receipt");
		}
		return new LegacyPlayerLocationPersistenceSnapshot(
			checkedPoint.getX(), checkedPoint.getY(), checkedLocation);
	}

	public int getPackedX() {
		return packedX;
	}

	public int getPackedY() {
		return packedY;
	}

	public WorldLocation getLayeredLocation() {
		return layeredLocation;
	}

	public Point toLegacyPoint() {
		return Point.location(packedX, packedY);
	}

	public WorldLocation requireLayeredLocation(final WorldLocation actualLocation) {
		if (!layeredLocation.equals(Objects.requireNonNull(actualLocation, "actualLocation"))) {
			throw new IllegalStateException(
				"Loaded Player mirror does not match the layered persistence snapshot");
		}
		return actualLocation;
	}
}
