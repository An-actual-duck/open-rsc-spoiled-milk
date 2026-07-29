package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.Objects;

/** Checked packed source address for one logical region-local tile. */
public final class LegacyLogicalTileAddress {
	private final WorldRegionKey logicalRegionKey;
	private final int logicalLocalX;
	private final int logicalLocalY;
	private final WorldLocation logicalLocation;
	private final Point legacyPoint;
	private final int packedRegionX;
	private final int packedRegionY;
	private final int packedLocalX;
	private final int packedLocalY;
	private final LegacyLogicalRegionAssembly.SourceFragment sourceFragment;

	private LegacyLogicalTileAddress(
		final WorldRegionKey logicalRegionKey,
		final int logicalLocalX,
		final int logicalLocalY,
		final WorldLocation logicalLocation,
		final Point legacyPoint,
		final int packedRegionX,
		final int packedRegionY,
		final int packedLocalX,
		final int packedLocalY,
		final LegacyLogicalRegionAssembly.SourceFragment sourceFragment) {
		this.logicalRegionKey = logicalRegionKey;
		this.logicalLocalX = logicalLocalX;
		this.logicalLocalY = logicalLocalY;
		this.logicalLocation = logicalLocation;
		this.legacyPoint = legacyPoint;
		this.packedRegionX = packedRegionX;
		this.packedRegionY = packedRegionY;
		this.packedLocalX = packedLocalX;
		this.packedLocalY = packedLocalY;
		this.sourceFragment = sourceFragment;
	}

	public static LegacyLogicalTileAddress resolve(
		final WorldRegionKey logicalRegionKey,
		final int logicalLocalX,
		final int logicalLocalY) {
		Objects.requireNonNull(logicalRegionKey, "logicalRegionKey");
		validateLocal(logicalLocalX, "X");
		validateLocal(logicalLocalY, "Y");
		LegacyLogicalRegionAssembly assembly =
			LegacyLogicalRegionAssembly.fromLogicalRegionKey(logicalRegionKey);
		WorldTileBounds target = assembly.getTargetBounds();
		WorldLocation logicalLocation = new WorldLocation(
			logicalRegionKey.getWorldSpace(),
			new WorldCoordinate(
				Math.addExact(target.getMinX(), logicalLocalX),
				Math.addExact(target.getMinY(), logicalLocalY),
				logicalRegionKey.getLevel()));
		LegacyLogicalRegionAssembly.SourceFragment selected = null;
		for (LegacyLogicalRegionAssembly.SourceFragment source
			: assembly.getSourceFragments()) {
			if (source.getFragment().containsLogicalLocation(logicalLocation)) {
				if (selected != null) {
					throw new IllegalStateException(
						"Logical tile belongs to overlapping legacy fragments");
				}
				selected = source;
			}
		}
		if (selected == null) {
			return new LegacyLogicalTileAddress(
				logicalRegionKey, logicalLocalX, logicalLocalY, logicalLocation,
				null, -1, -1, -1, -1, null);
		}

		Point packed = LegacyPackedPointAdapter.toLegacyPoint(logicalLocation);
		int packedRegionX = Math.floorDiv(
			packed.getX(), WorldRegionKey.REGION_SIZE);
		int packedRegionY = Math.floorDiv(
			packed.getY(), WorldRegionKey.REGION_SIZE);
		int packedLocalX = Math.floorMod(
			packed.getX(), WorldRegionKey.REGION_SIZE);
		int packedLocalY = Math.floorMod(
			packed.getY(), WorldRegionKey.REGION_SIZE);
		LegacyPackedRegionPartition.Fragment fragment = selected.getFragment();
		if (packedRegionX != selected.getPackedRegionX()
			|| packedRegionY != selected.getPackedRegionY()
			|| packedLocalX < fragment.getMinPackedLocalX()
			|| packedLocalX > fragment.getMaxPackedLocalX()
			|| packedLocalY < fragment.getMinPackedLocalY()
			|| packedLocalY > fragment.getMaxPackedLocalY()
			|| !fragment.containsPackedTile(packed.getX(), packed.getY())) {
			throw new IllegalStateException(
				"Logical tile address disagrees with its legacy source fragment");
		}
		return new LegacyLogicalTileAddress(
			logicalRegionKey, logicalLocalX, logicalLocalY, logicalLocation,
			packed, packedRegionX, packedRegionY, packedLocalX, packedLocalY,
			selected);
	}

	private static void validateLocal(final int coordinate, final String axis) {
		if (coordinate < 0 || coordinate >= WorldRegionKey.REGION_SIZE) {
			throw new IllegalArgumentException(
				"Logical region-local " + axis + " must be in 0.."
					+ (WorldRegionKey.REGION_SIZE - 1) + ": " + coordinate);
		}
	}

	public WorldRegionKey getLogicalRegionKey() {
		return logicalRegionKey;
	}

	public int getLogicalLocalX() {
		return logicalLocalX;
	}

	public int getLogicalLocalY() {
		return logicalLocalY;
	}

	public WorldLocation getLogicalLocation() {
		return logicalLocation;
	}

	/** Returns null when the logical tile has no legacy representation. */
	public Point getLegacyPoint() {
		return legacyPoint;
	}

	public boolean isLegacyRepresentable() {
		return sourceFragment != null;
	}

	public int getPackedRegionX() {
		requireLegacyRepresentation();
		return packedRegionX;
	}

	public int getPackedRegionY() {
		requireLegacyRepresentation();
		return packedRegionY;
	}

	public int getPackedLocalX() {
		requireLegacyRepresentation();
		return packedLocalX;
	}

	public int getPackedLocalY() {
		requireLegacyRepresentation();
		return packedLocalY;
	}

	/** Returns null when the logical tile has no legacy representation. */
	public LegacyLogicalRegionAssembly.SourceFragment getSourceFragment() {
		return sourceFragment;
	}

	private void requireLegacyRepresentation() {
		if (!isLegacyRepresentable()) {
			throw new IllegalStateException(
				"Logical tile has no legacy packed source: " + logicalLocation);
		}
	}

	@Override
	public String toString() {
		return "LegacyLogicalTileAddress{logicalRegionKey=" + logicalRegionKey
			+ ", logicalLocalX=" + logicalLocalX + ", logicalLocalY="
			+ logicalLocalY + ", logicalLocation=" + logicalLocation
			+ ", legacyRepresentable=" + isLegacyRepresentable()
			+ (isLegacyRepresentable()
				? ", packedRegion=(" + packedRegionX + ',' + packedRegionY
					+ "), packedLocal=(" + packedLocalX + ',' + packedLocalY + ')'
				: "") + "}";
	}
}
