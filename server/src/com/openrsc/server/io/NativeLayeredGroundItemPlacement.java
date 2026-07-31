package com.openrsc.server.io;

import com.openrsc.server.model.world.coordinate.WorldLocation;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable ground-item placement decoded from a native layered package. */
public final class NativeLayeredGroundItemPlacement {
	public static final int MAX_RESPAWN_SECONDS = 86400;
	private static final Pattern ID =
		Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

	private final String placementId;
	private final int itemId;
	private final WorldLocation location;
	private final int amount;
	private final int respawnSeconds;

	NativeLayeredGroundItemPlacement(
		final String placementId,
		final int itemId,
		final WorldLocation location,
		final int amount,
		final int respawnSeconds) {
		this.placementId = Objects.requireNonNull(placementId, "placementId");
		this.itemId = itemId;
		this.location = Objects.requireNonNull(location, "location");
		this.amount = amount;
		this.respawnSeconds = respawnSeconds;
	}

	/**
	 * Creates a validated placement for an authoritative authoring workflow.
	 *
	 * <p>Package decoding performs the same checks while retaining checked
	 * {@link java.io.IOException} diagnostics for malformed documents.</p>
	 */
	public static NativeLayeredGroundItemPlacement authored(
		final String placementId,
		final int itemId,
		final WorldLocation location,
		final int amount,
		final int respawnSeconds) {
		if (placementId == null || !ID.matcher(placementId).matches()) {
			throw new IllegalArgumentException(
				"Ground-item placement identity is invalid.");
		}
		if (itemId < 0) {
			throw new IllegalArgumentException(
				"Ground-item definition ID must be non-negative.");
		}
		if (amount < 1) {
			throw new IllegalArgumentException(
				"Ground-item amount must be at least 1.");
		}
		if (respawnSeconds < 1
			|| respawnSeconds > MAX_RESPAWN_SECONDS) {
			throw new IllegalArgumentException(
				"Ground-item respawn time must be from 1 to "
					+ MAX_RESPAWN_SECONDS + " seconds.");
		}
		return new NativeLayeredGroundItemPlacement(
			placementId,
			itemId,
			Objects.requireNonNull(location, "location"),
			amount,
			respawnSeconds);
	}

	public String getPlacementId() {
		return placementId;
	}

	public int getItemId() {
		return itemId;
	}

	public WorldLocation getLocation() {
		return location;
	}

	public int getAmount() {
		return amount;
	}

	public int getRespawnSeconds() {
		return respawnSeconds;
	}
}
