package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.LegacyLogicalTileAddress;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;

import java.util.Objects;

/** Read-only full-state comparison of one direct packed and logical snapshot tile. */
public final class LayeredTileStateParityComparison {
	private final WorldLocation logicalLocation;
	private final LegacyLogicalTileAddress address;
	private final boolean packedSourcePresent;
	private final LayeredTileState directPackedState;
	private final LayeredTileState logicalSnapshotState;
	private final boolean exact;

	private LayeredTileStateParityComparison(
		final WorldLocation logicalLocation,
		final LegacyLogicalTileAddress address,
		final boolean packedSourcePresent,
		final LayeredTileState directPackedState,
		final LayeredTileState logicalSnapshotState,
		final boolean exact) {
		this.logicalLocation = logicalLocation;
		this.address = address;
		this.packedSourcePresent = packedSourcePresent;
		this.directPackedState = directPackedState;
		this.logicalSnapshotState = logicalSnapshotState;
		this.exact = exact;
	}

	static LayeredTileStateParityComparison compare(
		final WorldLocation logicalLocation,
		final LayeredRegionTileSnapshot snapshot,
		final boolean packedSourcePresent,
		final TileValue directPackedTile) {
		Objects.requireNonNull(logicalLocation, "logicalLocation");
		Objects.requireNonNull(snapshot, "snapshot");
		WorldRegionKey key = WorldRegionKey.from(logicalLocation);
		if (!key.equals(snapshot.getLogicalRegionKey())) {
			throw new IllegalArgumentException(
				"Logical snapshot key differs from the comparison location");
		}
		WorldCoordinate coordinate = logicalLocation.getCoordinate();
		LegacyLogicalTileAddress address = LegacyLogicalTileAddress.resolve(
			key, coordinate.getLocalX(), coordinate.getLocalY());
		LayeredTileState snapshotState = snapshot.getTileState(
			coordinate.getLocalX(), coordinate.getLocalY());
		if (address.isLegacyRepresentable() != (snapshotState != null)) {
			throw new IllegalStateException(
				"Logical snapshot support differs from its checked tile address");
		}
		if (!address.isLegacyRepresentable()) {
			if (packedSourcePresent || directPackedTile != null) {
				throw new IllegalArgumentException(
					"Unsupported logical tile cannot have a direct packed source");
			}
			return new LayeredTileStateParityComparison(
				logicalLocation, address, false, null, null, false);
		}
		if (packedSourcePresent != (directPackedTile != null)) {
			throw new IllegalArgumentException(
				"Packed source presence differs from its direct tile value");
		}
		LayeredTileState directState = directPackedTile == null
			? null : LayeredTileState.fromLegacy(directPackedTile);
		return new LayeredTileStateParityComparison(
			logicalLocation,
			address,
			packedSourcePresent,
			directState,
			snapshotState,
			directState != null && directState.equals(snapshotState));
	}

	public WorldLocation getLogicalLocation() {
		return logicalLocation;
	}

	public LegacyLogicalTileAddress getAddress() {
		return address;
	}

	public boolean isLegacyRepresentable() {
		return address.isLegacyRepresentable();
	}

	public boolean isPackedSourcePresent() {
		return packedSourcePresent;
	}

	public boolean isMissingPackedSource() {
		return isLegacyRepresentable() && !packedSourcePresent;
	}

	public boolean isComparable() {
		return directPackedState != null && logicalSnapshotState != null;
	}

	/** Returns null when the packed source Region is absent or unsupported. */
	public LayeredTileState getDirectPackedState() {
		return directPackedState;
	}

	/** Returns null only when the logical tile is unsupported. */
	public LayeredTileState getLogicalSnapshotState() {
		return logicalSnapshotState;
	}

	public boolean isExact() {
		return exact;
	}

	@Override
	public String toString() {
		return "LayeredTileStateParityComparison{logicalLocation=" + logicalLocation
			+ ", legacyRepresentable=" + isLegacyRepresentable()
			+ ", packedSourcePresent=" + packedSourcePresent
			+ ", comparable=" + isComparable() + ", exact=" + exact + '}';
	}
}
