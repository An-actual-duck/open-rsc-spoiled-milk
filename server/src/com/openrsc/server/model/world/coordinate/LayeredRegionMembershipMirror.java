package com.openrsc.server.model.world.coordinate;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Checked shadow logical-region membership derived from a layered location. */
public final class LayeredRegionMembershipMirror {
	private final AtomicReference<WorldRegionKey> key =
		new AtomicReference<WorldRegionKey>();

	public WorldRegionKey synchronize(WorldLocation authoritativeLocation) {
		WorldRegionKey projected = WorldRegionKey.from(
			Objects.requireNonNull(authoritativeLocation, "authoritativeLocation"));
		key.set(projected);
		return projected;
	}

	public WorldRegionKey requireCurrent(WorldLocation authoritativeLocation) {
		WorldRegionKey expected = WorldRegionKey.from(
			Objects.requireNonNull(authoritativeLocation, "authoritativeLocation"));
		WorldRegionKey current = key.get();
		if (current == null) {
			throw new IllegalStateException(
				"Layered region membership mirror has not been initialized");
		}
		if (!current.equals(expected)) {
			throw new IllegalStateException(
				"Layered region membership mirror does not match the Player location mirror");
		}
		return current;
	}

	public boolean isInitialized() {
		return key.get() != null;
	}
}
