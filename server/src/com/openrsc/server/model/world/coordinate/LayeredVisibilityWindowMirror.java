package com.openrsc.server.model.world.coordinate;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Checked Player shadow of the projected logical visibility window. */
public final class LayeredVisibilityWindowMirror {
	private final AtomicReference<WorldRegionWindow> window =
		new AtomicReference<WorldRegionWindow>();

	public WorldRegionWindow synchronize(final WorldRegionWindow projectedWindow) {
		WorldRegionWindow expected = Objects.requireNonNull(projectedWindow, "projectedWindow");
		window.set(expected);
		return expected;
	}

	public WorldRegionWindow requireCurrent(final WorldRegionWindow projectedWindow) {
		WorldRegionWindow expected = Objects.requireNonNull(projectedWindow, "projectedWindow");
		WorldRegionWindow current = window.get();
		if (current == null) {
			throw new IllegalStateException(
				"Layered visibility-window mirror has not been initialized");
		}
		if (!current.equals(expected)) {
			throw new IllegalStateException(
				"Layered visibility-window mirror does not match the Player location/configuration");
		}
		return current;
	}

	public boolean isInitialized() {
		return window.get() != null;
	}
}
