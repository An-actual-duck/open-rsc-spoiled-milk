package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Map;
import java.util.Objects;

/**
 * Versioned Player-cache representation for an authoritative layered location.
 *
 * <p>The unchanged legacy player X/Y columns remain a rollback-compatible
 * receipt during the private authority gate. Missing records bootstrap from
 * those columns. A complete record whose receipt no longer matches them is
 * conservatively rebased from the legacy columns so offline legacy updates are
 * not lost. Partial or malformed records refuse rather than being overwritten.</p>
 */
public final class LayeredPlayerLocationPersistence {
	public static final String FORMAT = "signed-xyz-player-v1";
	public static final String LEGACY_BOOTSTRAP = "legacy-bootstrap-v1";
	public static final String LEGACY_REBASE = "legacy-rebase-v1";
	public static final String SYNTHETIC_DISABLED_REBASE =
		"synthetic-deep-disabled-rebase-v1";

	public static final String KEY_FORMAT = "layerloc_format";
	public static final String KEY_SPACE = "layerloc_space";
	public static final String KEY_X = "layerloc_x";
	public static final String KEY_Y = "layerloc_y";
	public static final String KEY_LEVEL = "layerloc_level";
	public static final String KEY_ADAPTER = "layerloc_adapter";
	public static final String KEY_PACKED_X = "layerloc_packed_x";
	public static final String KEY_PACKED_Y = "layerloc_packed_y";
	public static final String KEY_ORIGIN = "layerloc_origin";

	private static final String[] KEYS = {
		KEY_FORMAT,
		KEY_SPACE,
		KEY_X,
		KEY_Y,
		KEY_LEVEL,
		KEY_ADAPTER,
		KEY_PACKED_X,
		KEY_PACKED_Y,
		KEY_ORIGIN
	};

	private LayeredPlayerLocationPersistence() {
	}

	public static RestoreResult restore(
		final Map<String, Object> cache,
		final Point legacyPoint) {
		return restore(cache, legacyPoint, false);
	}

	public static RestoreResult restore(
		final Map<String, Object> cache,
		final Point legacyPoint,
		final boolean allowSyntheticDeepFixture) {
		Objects.requireNonNull(cache, "cache");
		Objects.requireNonNull(legacyPoint, "legacyPoint");

		int present = 0;
		for (String key : KEYS) {
			if (cache.containsKey(key)) {
				present++;
			}
		}
		if (present == 0) {
			return new RestoreResult(
				LegacyPackedPointAdapter.fromLegacyPoint(legacyPoint),
				LEGACY_BOOTSTRAP,
				true);
		}
		if (present != KEYS.length) {
			throw new IllegalStateException(
				"Incomplete layered Player location persistence record");
		}

		requireString(cache, KEY_FORMAT, FORMAT);
		String adapter = requireString(cache, KEY_ADAPTER);
		String space = requireString(cache, KEY_SPACE);
		String origin = requireString(cache, KEY_ORIGIN);
		WorldLocation persisted = new WorldLocation(
			new WorldSpaceId(space),
			new WorldCoordinate(
				requireInteger(cache, KEY_X),
				requireInteger(cache, KEY_Y),
				requireInteger(cache, KEY_LEVEL)));
		Point persistedProjection =
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(
				persisted,
				LayeredCompatibilityPointAdapter
					.SYNTHETIC_DEEP_FIXTURE_ID.equals(adapter));
		String expectedAdapter =
			LayeredCompatibilityPointAdapter.projectionId(
				persisted,
				LayeredCompatibilityPointAdapter
					.SYNTHETIC_DEEP_FIXTURE_ID.equals(adapter));
		if (!expectedAdapter.equals(adapter)) {
			throw new IllegalStateException(
				"Layered Player location adapter does not match its location");
		}
		int receiptX = requireInteger(cache, KEY_PACKED_X);
		int receiptY = requireInteger(cache, KEY_PACKED_Y);
		if (persistedProjection.getX() != receiptX
			|| persistedProjection.getY() != receiptY) {
			throw new IllegalStateException(
				"Layered Player location does not match its legacy receipt");
		}
		if (legacyPoint.getX() != receiptX || legacyPoint.getY() != receiptY) {
			return new RestoreResult(
				LegacyPackedPointAdapter.fromLegacyPoint(legacyPoint),
				LEGACY_REBASE,
				true);
		}
		if (LayeredCompatibilityPointAdapter.SYNTHETIC_DEEP_FIXTURE_ID
				.equals(adapter)
			&& !allowSyntheticDeepFixture) {
			return new RestoreResult(
				LegacyPackedPointAdapter.fromLegacyPoint(legacyPoint),
				SYNTHETIC_DISABLED_REBASE,
				true);
		}
		return new RestoreResult(persisted, origin, false);
	}

	public static void write(
		final Map<String, Object> cache,
		final WorldLocation location,
		final Point derivedLegacyPoint,
		final String origin) {
		write(cache, location, derivedLegacyPoint, origin, false);
	}

	public static void write(
		final Map<String, Object> cache,
		final WorldLocation location,
		final Point derivedLegacyPoint,
		final String origin,
		final boolean allowSyntheticDeepFixture) {
		Objects.requireNonNull(cache, "cache");
		Objects.requireNonNull(location, "location");
		Objects.requireNonNull(derivedLegacyPoint, "derivedLegacyPoint");
		Objects.requireNonNull(origin, "origin");

		Point projection =
			LayeredCompatibilityPointAdapter.toCompatibilityPoint(
				location, allowSyntheticDeepFixture);
		String adapter = LayeredCompatibilityPointAdapter.projectionId(
			location, allowSyntheticDeepFixture);
		if (projection.getX() != derivedLegacyPoint.getX()
			|| projection.getY() != derivedLegacyPoint.getY()) {
			throw new IllegalStateException(
				"Cannot persist divergent layered and legacy Player locations");
		}
		WorldCoordinate coordinate = location.getCoordinate();
		cache.put(KEY_FORMAT, FORMAT);
		cache.put(KEY_SPACE, location.getWorldSpace().getValue());
		cache.put(KEY_X, coordinate.getX());
		cache.put(KEY_Y, coordinate.getY());
		cache.put(KEY_LEVEL, coordinate.getLevel());
		cache.put(KEY_ADAPTER, adapter);
		cache.put(KEY_PACKED_X, projection.getX());
		cache.put(KEY_PACKED_Y, projection.getY());
		cache.put(KEY_ORIGIN, origin);
	}

	private static int requireInteger(
		final Map<String, Object> cache,
		final String key) {
		Object value = cache.get(key);
		if (!(value instanceof Integer)) {
			throw new IllegalStateException(
				"Layered Player location field is not an Integer: " + key);
		}
		return (Integer) value;
	}

	private static String requireString(
		final Map<String, Object> cache,
		final String key) {
		Object value = cache.get(key);
		if (!(value instanceof String) || ((String) value).isEmpty()) {
			throw new IllegalStateException(
				"Layered Player location field is not a non-empty String: " + key);
		}
		return (String) value;
	}

	private static void requireString(
		final Map<String, Object> cache,
		final String key,
		final String expected) {
		String actual = requireString(cache, key);
		if (!expected.equals(actual)) {
			throw new IllegalStateException(
				"Unsupported layered Player location field " + key + ": " + actual);
		}
	}

	public static final class RestoreResult {
		private final WorldLocation location;
		private final String origin;
		private final boolean rewriteRequired;

		private RestoreResult(
			final WorldLocation location,
			final String origin,
			final boolean rewriteRequired) {
			this.location = location;
			this.origin = origin;
			this.rewriteRequired = rewriteRequired;
		}

		public WorldLocation getLocation() {
			return location;
		}

		public String getOrigin() {
			return origin;
		}

		public boolean isRewriteRequired() {
			return rewriteRequired;
		}
	}
}
