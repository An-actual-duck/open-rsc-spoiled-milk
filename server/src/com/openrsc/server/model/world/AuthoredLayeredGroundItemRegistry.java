package com.openrsc.server.model.world;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Tracks one active ground-item instance for each package-owned layered spawn.
 *
 * <p>Unlike the legacy authored registry, identity includes world space and
 * signed level rather than only packed X/Y.</p>
 */
public final class AuthoredLayeredGroundItemRegistry<T> {
	public static final long NO_GENERATION = -1L;

	private final Map<WorldLocation, T> activeItems =
		new HashMap<WorldLocation, T>();
	private final Set<WorldLocation> authoredLocations =
		new HashSet<WorldLocation>();
	private final Map<WorldLocation, Long> locationGenerations =
		new HashMap<WorldLocation, Long>();
	private long defaultGeneration;
	private long nextGeneration = 1L;

	public synchronized T register(
		final WorldLocation location,
		final Supplier<T> factory) {
		WorldLocation key = Objects.requireNonNull(location, "location");
		return registerForGeneration(
			key, generationFor(key), factory);
	}

	public synchronized T registerForGeneration(
		final WorldLocation location,
		final long expectedGeneration,
		final Supplier<T> factory) {
		WorldLocation key = Objects.requireNonNull(location, "location");
		if (expectedGeneration != generationFor(key)) {
			return null;
		}
		T existing = activeItems.get(key);
		if (existing != null) {
			return existing;
		}
		T item = Objects.requireNonNull(factory.get(), "layered ground item");
		activeItems.put(key, item);
		authoredLocations.add(key);
		return item;
	}

	@SuppressWarnings("PMD.CompareObjectsWithEquals")
	public synchronized long remove(
		final WorldLocation location,
		final T item) {
		WorldLocation key = Objects.requireNonNull(location, "location");
		if (activeItems.get(key) != item) {
			return NO_GENERATION;
		}
		activeItems.remove(key);
		return generationFor(key);
	}

	/**
	 * Permanently retires one active authored placement and invalidates any
	 * delayed replacement already holding the prior location generation.
	 */
	@SuppressWarnings("PMD.CompareObjectsWithEquals")
	public synchronized boolean retire(
		final WorldLocation location,
		final T item) {
		WorldLocation key = Objects.requireNonNull(location, "location");
		if (activeItems.get(key) != item) {
			return false;
		}
		activeItems.remove(key);
		authoredLocations.remove(key);
		locationGenerations.put(
			key, Long.valueOf(nextGeneration++));
		return true;
	}

	public synchronized int size() {
		return activeItems.size();
	}

	public synchronized T find(final WorldLocation location) {
		return activeItems.get(
			Objects.requireNonNull(location, "location"));
	}

	public synchronized boolean containsPlacement(
		final WorldLocation location) {
		return authoredLocations.contains(
			Objects.requireNonNull(location, "location"));
	}

	public synchronized void reset() {
		activeItems.clear();
		authoredLocations.clear();
		locationGenerations.clear();
		defaultGeneration = nextGeneration++;
	}

	private long generationFor(final WorldLocation location) {
		Long generation = locationGenerations.get(location);
		return generation == null
			? defaultGeneration : generation.longValue();
	}
}
