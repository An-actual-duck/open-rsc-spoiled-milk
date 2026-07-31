package com.openrsc.server.model;

import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;

import java.util.Objects;
import java.util.Optional;

/** Stores exact layered return locations alongside legacy packed fallbacks. */
public final class PlayerReturnLocationStore {
	private static final String SPACE_SUFFIX = "_layered_space";
	private static final String X_SUFFIX = "_layered_x";
	private static final String Y_SUFFIX = "_layered_y";
	private static final String LEVEL_SUFFIX = "_layered_level";

	private PlayerReturnLocationStore() {
	}

	public static void storeExact(
		final Cache cache,
		final String prefix,
		final WorldLocation location) {
		Objects.requireNonNull(cache, "cache");
		Objects.requireNonNull(prefix, "prefix");
		Objects.requireNonNull(location, "location");
		cache.store(prefix + SPACE_SUFFIX, location.getWorldSpace().getValue());
		cache.set(prefix + X_SUFFIX, location.getCoordinate().getX());
		cache.set(prefix + Y_SUFFIX, location.getCoordinate().getY());
		cache.set(prefix + LEVEL_SUFFIX, location.getCoordinate().getLevel());
	}

	/** Returns no value for missing, partial, corrupt, or invalid metadata. */
	public static Optional<WorldLocation> readExact(
		final Cache cache,
		final String prefix) {
		Objects.requireNonNull(cache, "cache");
		Objects.requireNonNull(prefix, "prefix");
		String spaceKey = prefix + SPACE_SUFFIX;
		String xKey = prefix + X_SUFFIX;
		String yKey = prefix + Y_SUFFIX;
		String levelKey = prefix + LEVEL_SUFFIX;
		if (!cache.hasKey(spaceKey)
			|| !cache.hasKey(xKey)
			|| !cache.hasKey(yKey)
			|| !cache.hasKey(levelKey)) {
			return Optional.empty();
		}
		try {
			return Optional.of(new WorldLocation(
				new WorldSpaceId(cache.getString(spaceKey)),
				new WorldCoordinate(
					cache.getInt(xKey),
					cache.getInt(yKey),
					cache.getInt(levelKey))));
		} catch (RuntimeException corruptMetadata) {
			return Optional.empty();
		}
	}

	/**
	 * Stores a rollback-compatible packed point when the exact location can be
	 * represented by the classic four-plane global coordinate format.
	 */
	public static void storeLegacyProjection(
		final Cache cache,
		final String xKey,
		final String yKey,
		final WorldLocation location) {
		Objects.requireNonNull(cache, "cache");
		try {
			Point legacy = LegacyPackedPointAdapter.toLegacyPoint(location);
			cache.set(xKey, legacy.getX());
			cache.set(yKey, legacy.getY());
		} catch (IllegalArgumentException unrepresentableLocation) {
			cache.remove(xKey, yKey);
		}
	}

	public static Optional<Point> readLegacy(
		final Cache cache,
		final String xKey,
		final String yKey) {
		Objects.requireNonNull(cache, "cache");
		if (!cache.hasKey(xKey) || !cache.hasKey(yKey)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Point.location(
				cache.getInt(xKey), cache.getInt(yKey)));
		} catch (RuntimeException corruptMetadata) {
			return Optional.empty();
		}
	}

	public static void clearExact(final Cache cache, final String prefix) {
		Objects.requireNonNull(cache, "cache");
		cache.remove(
			prefix + SPACE_SUFFIX,
			prefix + X_SUFFIX,
			prefix + Y_SUFFIX,
			prefix + LEVEL_SUFFIX);
	}
}
