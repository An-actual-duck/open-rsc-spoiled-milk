package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable deterministic difference between two logical region-interest windows. */
public final class WorldRegionInterestDelta {
	private final WorldRegionWindow previousWindow;
	private final WorldRegionWindow currentWindow;
	private final List<WorldRegionKey> entered;
	private final List<WorldRegionKey> retained;
	private final List<WorldRegionKey> exited;

	private WorldRegionInterestDelta(
		final WorldRegionWindow previousWindow,
		final WorldRegionWindow currentWindow,
		final List<WorldRegionKey> entered,
		final List<WorldRegionKey> retained,
		final List<WorldRegionKey> exited) {
		this.previousWindow = previousWindow;
		this.currentWindow = currentWindow;
		this.entered = Collections.unmodifiableList(entered);
		this.retained = Collections.unmodifiableList(retained);
		this.exited = Collections.unmodifiableList(exited);
	}

	/**
	 * Materializes a checked delta in X-major/Y-minor order.
	 *
	 * <p>The explicit per-window limit is a caller-owned allocation budget, not a
	 * world-capacity limit.</p>
	 */
	public static WorldRegionInterestDelta between(
		final WorldRegionWindow previousWindow,
		final WorldRegionWindow currentWindow,
		final int maximumRegionsPerWindow) {
		WorldRegionWindow previous = Objects.requireNonNull(previousWindow, "previousWindow");
		WorldRegionWindow current = Objects.requireNonNull(currentWindow, "currentWindow");
		if (maximumRegionsPerWindow < 1) {
			throw new IllegalArgumentException(
				"Maximum materialized regions per window must be positive");
		}

		List<WorldRegionKey> previousKeys = materializeKeys(
			previous, maximumRegionsPerWindow);
		List<WorldRegionKey> currentKeys = materializeKeys(
			current, maximumRegionsPerWindow);
		Set<WorldRegionKey> previousSet = new LinkedHashSet<WorldRegionKey>(previousKeys);
		Set<WorldRegionKey> currentSet = new LinkedHashSet<WorldRegionKey>(currentKeys);

		List<WorldRegionKey> entered = new ArrayList<WorldRegionKey>();
		List<WorldRegionKey> retained = new ArrayList<WorldRegionKey>();
		for (WorldRegionKey key : currentKeys) {
			if (previousSet.contains(key)) {
				retained.add(key);
			} else {
				entered.add(key);
			}
		}

		List<WorldRegionKey> exited = new ArrayList<WorldRegionKey>();
		for (WorldRegionKey key : previousKeys) {
			if (!currentSet.contains(key)) {
				exited.add(key);
			}
		}

		return new WorldRegionInterestDelta(previous, current, entered, retained, exited);
	}

	/** Materializes deterministic X-major/Y-minor membership under a caller budget. */
	public static List<WorldRegionKey> materializeKeys(
		final WorldRegionWindow window,
		final int maximumRegionsPerWindow) {
		Objects.requireNonNull(window, "window");
		if (maximumRegionsPerWindow < 1) {
			throw new IllegalArgumentException(
				"Maximum materialized regions per window must be positive");
		}
		long regionCount = window.getRegionCount();
		if (regionCount > maximumRegionsPerWindow) {
			throw new IllegalArgumentException(
				"Logical region window requires " + regionCount
					+ " keys, exceeding the caller budget of " + maximumRegionsPerWindow);
		}

		List<WorldRegionKey> keys = new ArrayList<WorldRegionKey>((int) regionCount);
		for (long regionX = window.getMinRegionX();
			regionX <= (long) window.getMaxRegionX(); regionX++) {
			for (long regionY = window.getMinRegionY();
				regionY <= (long) window.getMaxRegionY(); regionY++) {
				keys.add(new WorldRegionKey(
					window.getWorldSpace(), window.getLevel(), (int) regionX, (int) regionY));
			}
		}
		return Collections.unmodifiableList(keys);
	}

	public WorldRegionWindow getPreviousWindow() {
		return previousWindow;
	}

	public WorldRegionWindow getCurrentWindow() {
		return currentWindow;
	}

	public List<WorldRegionKey> getEntered() {
		return entered;
	}

	public List<WorldRegionKey> getRetained() {
		return retained;
	}

	public List<WorldRegionKey> getExited() {
		return exited;
	}

	public boolean isNoOp() {
		return entered.isEmpty() && exited.isEmpty();
	}

	public boolean changesWorldSpace() {
		return !previousWindow.getWorldSpace().equals(currentWindow.getWorldSpace());
	}

	public boolean changesLevel() {
		return previousWindow.getLevel() != currentWindow.getLevel();
	}

	@Override
	public String toString() {
		return "WorldRegionInterestDelta{entered=" + entered.size()
			+ ", retained=" + retained.size() + ", exited=" + exited.size()
			+ ", worldSpaceChange=" + changesWorldSpace()
			+ ", levelChange=" + changesLevel() + "}";
	}
}
