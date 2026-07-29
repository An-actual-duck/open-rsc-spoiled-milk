package com.openrsc.server.model.world;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
	private long generation;

	public synchronized T register(
		final WorldLocation location,
		final Supplier<T> factory) {
		return registerForGeneration(location, generation, factory);
	}

	public synchronized T registerForGeneration(
		final WorldLocation location,
		final long expectedGeneration,
		final Supplier<T> factory) {
		if (expectedGeneration != generation) {
			return null;
		}
		WorldLocation key = Objects.requireNonNull(location, "location");
		T existing = activeItems.get(key);
		if (existing != null) {
			return existing;
		}
		T item = Objects.requireNonNull(factory.get(), "layered ground item");
		activeItems.put(key, item);
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
		return generation;
	}

	public synchronized int size() {
		return activeItems.size();
	}

	public synchronized void reset() {
		activeItems.clear();
		generation++;
	}
}
