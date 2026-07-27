package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;

/**
 * Explicit selector for compatibility projections used by layered authority.
 *
 * <p>The legacy adapter remains the only general projection. The synthetic
 * deep projection is a private, bounded fixture that deliberately reuses one
 * checked plane-0 terrain rectangle without claiming archive support for a
 * fifth plane.</p>
 */
public final class LayeredCompatibilityPointAdapter {
	public static final String NATIVE_LAYERED_PACKAGE_ID =
		"native-layered-package-v1";
	public static final String SYNTHETIC_DEEP_FIXTURE_ID =
		"synthetic-deep-fixture-v1";
	public static final int SYNTHETIC_DEEP_LEVEL = -2;
	public static final int SYNTHETIC_DEEP_MIN_X = 440;
	public static final int SYNTHETIC_DEEP_MAX_X = 460;
	public static final int SYNTHETIC_DEEP_MIN_Y = 590;
	public static final int SYNTHETIC_DEEP_MAX_Y = 610;
	public static final int SYNTHETIC_DEEP_ENTRY_X = 450;
	public static final int SYNTHETIC_DEEP_ENTRY_Y = 600;

	private LayeredCompatibilityPointAdapter() {
	}

	public static Point toCompatibilityPoint(
		final WorldLocation location,
		final boolean allowSyntheticDeepFixture) {
		return toCompatibilityPoint(
			location, allowSyntheticDeepFixture, false);
	}

	/**
	 * Produces the temporary Point carrier used by unchanged runtime APIs.
	 *
	 * <p>The native selector is deliberately supplied per location by the
	 * package-aware runtime. It is not a general permission boolean: callers
	 * must prove that the exact location has package terrain before setting it.
	 * Unlike the synthetic selector, it preserves the signed level in the
	 * authoritative {@link WorldLocation} and imposes no fixture rectangle.</p>
	 */
	public static Point toCompatibilityPoint(
		final WorldLocation location,
		final boolean allowSyntheticDeepFixture,
		final boolean nativeLayeredLocation) {
		WorldLocation checked = Objects.requireNonNull(location, "location");
		if (nativeLayeredLocation) {
			return nativeLayeredPoint(checked);
		}
		if (isSyntheticDeepLevel(checked)) {
			requireSyntheticDeepLocation(checked, allowSyntheticDeepFixture);
			return Point.location(
				checked.getCoordinate().getX(),
				checked.getCoordinate().getY());
		}
		return LegacyPackedPointAdapter.toLegacyPoint(checked);
	}

	public static String projectionId(
		final WorldLocation location,
		final boolean allowSyntheticDeepFixture) {
		return projectionId(location, allowSyntheticDeepFixture, false);
	}

	public static String projectionId(
		final WorldLocation location,
		final boolean allowSyntheticDeepFixture,
		final boolean nativeLayeredLocation) {
		WorldLocation checked = Objects.requireNonNull(location, "location");
		if (nativeLayeredLocation) {
			nativeLayeredPoint(checked);
			return NATIVE_LAYERED_PACKAGE_ID;
		}
		if (isSyntheticDeepLevel(checked)) {
			requireSyntheticDeepLocation(checked, allowSyntheticDeepFixture);
			return SYNTHETIC_DEEP_FIXTURE_ID;
		}
		LegacyPackedPointAdapter.toLegacyPoint(checked);
		return LegacyPackedPointAdapter.ID;
	}

	public static int compatibilityPlane(
		final WorldLocation location,
		final boolean allowSyntheticDeepFixture) {
		return compatibilityPlane(
			location, allowSyntheticDeepFixture, false);
	}

	public static int compatibilityPlane(
		final WorldLocation location,
		final boolean allowSyntheticDeepFixture,
		final boolean nativeLayeredLocation) {
		WorldLocation checked = Objects.requireNonNull(location, "location");
		if (nativeLayeredLocation) {
			nativeLayeredPoint(checked);
			return 0;
		}
		if (isSyntheticDeepLevel(checked)) {
			requireSyntheticDeepLocation(checked, allowSyntheticDeepFixture);
			return 0;
		}
		return LegacyPackedPointAdapter.legacyPlaneForLevel(
			checked.getCoordinate().getLevel());
	}

	/**
	 * Selects the legacy client model plane used to present one signed level.
	 *
	 * <p>The native Point carrier deliberately keeps geographic Y unpacked, so
	 * deriving this value from {@code Formulae.getHeight(Point)} would reset
	 * every native location to plane zero. The four historically renderable
	 * levels retain their authentic model planes; arbitrary declared levels use
	 * the isolated plane-zero carrier until the client no longer needs it.</p>
	 */
	public static int clientPresentationPlane(
		final WorldLocation location) {
		int level = Objects.requireNonNull(location, "location")
			.getCoordinate().getLevel();
		switch (level) {
			case 0:
			case 1:
			case 2:
				return level;
			case -1:
				return 3;
			default:
				return 0;
		}
	}

	/**
	 * Resolves an incoming compatibility Point inside an established scope.
	 *
	 * <p>Ordinary movement cannot walk out of the fixture and become surface
	 * movement. A caller performing an explicit teleport/recovery may permit
	 * that scope exit.</p>
	 */
	public static WorldLocation fromCompatibilityPoint(
		final Point point,
		final WorldLocation currentScope,
		final boolean allowSyntheticDeepFixture,
		final boolean allowExplicitScopeExit) {
		return fromCompatibilityPoint(
			point,
			currentScope,
			allowSyntheticDeepFixture,
			allowExplicitScopeExit,
			false);
	}

	public static WorldLocation fromCompatibilityPoint(
		final Point point,
		final WorldLocation currentScope,
		final boolean allowSyntheticDeepFixture,
		final boolean allowExplicitScopeExit,
		final boolean nativeLayeredCurrentScope) {
		Point checked = Objects.requireNonNull(point, "point");
		if (nativeLayeredCurrentScope) {
			WorldLocation checkedScope = Objects.requireNonNull(
				currentScope, "currentScope");
			return new WorldLocation(
				checkedScope.getWorldSpace(),
				new WorldCoordinate(
					checked.getX(),
					checked.getY(),
					checkedScope.getCoordinate().getLevel()));
		}
		if (currentScope != null && isSyntheticDeepLevel(currentScope)) {
			requireSyntheticDeepLocation(
				currentScope, allowSyntheticDeepFixture);
			if (containsSyntheticDeepCoordinate(
				checked.getX(), checked.getY())) {
				return deepLocation(checked.getX(), checked.getY());
			}
			if (!allowExplicitScopeExit) {
				throw new IllegalArgumentException(
					"Ordinary movement cannot leave the synthetic deep fixture");
			}
		}
		return LegacyPackedPointAdapter.fromLegacyPoint(checked);
	}

	public static WorldLocation requireReceipt(
		final String projectionId,
		final WorldLocation location,
		final Point receipt,
		final boolean allowSyntheticDeepFixture) {
		return requireReceipt(
			projectionId,
			location,
			receipt,
			allowSyntheticDeepFixture,
			false);
	}

	public static WorldLocation requireReceipt(
		final String projectionId,
		final WorldLocation location,
		final Point receipt,
		final boolean allowSyntheticDeepFixture,
		final boolean allowNativeLayeredLocation) {
		String checkedId = Objects.requireNonNull(
			projectionId, "projectionId");
		WorldLocation checkedLocation = Objects.requireNonNull(
			location, "location");
		Point checkedReceipt = Objects.requireNonNull(receipt, "receipt");
		boolean nativeLayeredLocation =
			NATIVE_LAYERED_PACKAGE_ID.equals(checkedId);
		if (nativeLayeredLocation && !allowNativeLayeredLocation) {
			throw new IllegalArgumentException(
				"Native layered package projection is disabled");
		}
		String expectedId = projectionId(
			checkedLocation,
			allowSyntheticDeepFixture,
			nativeLayeredLocation);
		Point expectedReceipt = toCompatibilityPoint(
			checkedLocation,
			allowSyntheticDeepFixture,
			nativeLayeredLocation);
		if (!expectedId.equals(checkedId)
			|| expectedReceipt.getX() != checkedReceipt.getX()
			|| expectedReceipt.getY() != checkedReceipt.getY()) {
			throw new IllegalArgumentException(
				"Layered compatibility projection receipt mismatch");
		}
		return checkedLocation;
	}

	public static WorldLocation syntheticDeepEntry() {
		return deepLocation(
			SYNTHETIC_DEEP_ENTRY_X, SYNTHETIC_DEEP_ENTRY_Y);
	}

	public static WorldLocation deepLocation(final int x, final int y) {
		if (!containsSyntheticDeepCoordinate(x, y)) {
			throw new IllegalArgumentException(
				"Synthetic deep coordinate is outside the fixture bounds");
		}
		return WorldLocation.global(
			new WorldCoordinate(x, y, SYNTHETIC_DEEP_LEVEL));
	}

	public static boolean isSyntheticDeepLevel(
		final WorldLocation location) {
		return WorldSpaceId.GLOBAL.equals(
				Objects.requireNonNull(location, "location").getWorldSpace())
			&& location.getCoordinate().getLevel()
				== SYNTHETIC_DEEP_LEVEL;
	}

	public static boolean containsSyntheticDeepCoordinate(
		final int x,
		final int y) {
		return x >= SYNTHETIC_DEEP_MIN_X
			&& x <= SYNTHETIC_DEEP_MAX_X
			&& y >= SYNTHETIC_DEEP_MIN_Y
			&& y <= SYNTHETIC_DEEP_MAX_Y;
	}

	private static Point nativeLayeredPoint(
		final WorldLocation location) {
		WorldCoordinate coordinate = location.getCoordinate();
		int x = coordinate.getX();
		int y = coordinate.getY();
		if (x < 0 || x > Short.MAX_VALUE
			|| y < 0 || y > Short.MAX_VALUE) {
			throw new IllegalArgumentException(
				"Native layered location is outside the temporary Point "
					+ "carrier range: " + location);
		}
		return Point.location(x, y);
	}

	private static void requireSyntheticDeepLocation(
		final WorldLocation location,
		final boolean allowSyntheticDeepFixture) {
		if (!allowSyntheticDeepFixture) {
			throw new IllegalArgumentException(
				"Synthetic deep compatibility projection is disabled");
		}
		if (!WorldSpaceId.GLOBAL.equals(location.getWorldSpace())
			|| location.getCoordinate().getLevel()
				!= SYNTHETIC_DEEP_LEVEL
			|| !containsSyntheticDeepCoordinate(
				location.getCoordinate().getX(),
				location.getCoordinate().getY())) {
			throw new IllegalArgumentException(
				"Location is outside synthetic-deep-fixture-v1");
		}
	}
}
