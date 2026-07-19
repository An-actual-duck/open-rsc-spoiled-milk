package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Read-only packed-fragment assembly plan for one logical region key. */
public final class LegacyLogicalRegionAssembly {
	private final WorldRegionKey logicalRegionKey;
	private final WorldTileBounds targetBounds;
	private final WorldTileBounds legacySupportedBounds;
	private final List<SourceFragment> sourceFragments;
	private final long targetTileCount;
	private final long assembledTileCount;

	private LegacyLogicalRegionAssembly(
		final WorldRegionKey logicalRegionKey,
		final WorldTileBounds targetBounds,
		final WorldTileBounds legacySupportedBounds,
		final List<SourceFragment> sourceFragments,
		final long targetTileCount,
		final long assembledTileCount) {
		this.logicalRegionKey = logicalRegionKey;
		this.targetBounds = targetBounds;
		this.legacySupportedBounds = legacySupportedBounds;
		this.sourceFragments = Collections.unmodifiableList(sourceFragments);
		this.targetTileCount = targetTileCount;
		this.assembledTileCount = assembledTileCount;
	}

	public static LegacyLogicalRegionAssembly fromLogicalRegionKey(
		final WorldRegionKey logicalRegionKey) {
		Objects.requireNonNull(logicalRegionKey, "logicalRegionKey");
		int minX = Math.multiplyExact(
			logicalRegionKey.getRegionX(), WorldRegionKey.REGION_SIZE);
		int minY = Math.multiplyExact(
			logicalRegionKey.getRegionY(), WorldRegionKey.REGION_SIZE);
		int maxX = Math.addExact(minX, WorldRegionKey.REGION_SIZE - 1);
		int maxY = Math.addExact(minY, WorldRegionKey.REGION_SIZE - 1);
		WorldTileBounds target = bounds(
			logicalRegionKey.getWorldSpace(), logicalRegionKey.getLevel(),
			minX, minY, maxX, maxY);
		long targetTiles = tileCount(target);
		List<SourceFragment> sources = new ArrayList<SourceFragment>();

		if (!WorldSpaceId.GLOBAL.equals(logicalRegionKey.getWorldSpace())
			|| !supportsLegacyLevel(logicalRegionKey.getLevel())) {
			return new LegacyLogicalRegionAssembly(
				logicalRegionKey, target, null, sources, targetTiles, 0L);
		}

		int supportedMinX = Math.max(0, minX);
		int supportedMinY = Math.max(0, minY);
		int supportedMaxX = Math.min(LegacyPackedPointAdapter.MAX_LEGACY_X, maxX);
		int supportedMaxY = Math.min(
			LegacyPackedPointAdapter.LEVEL_STRIDE - 1, maxY);
		if (supportedMinX > supportedMaxX || supportedMinY > supportedMaxY) {
			return new LegacyLogicalRegionAssembly(
				logicalRegionKey, target, null, sources, targetTiles, 0L);
		}

		WorldTileBounds supported = bounds(
			WorldSpaceId.GLOBAL, logicalRegionKey.getLevel(),
			supportedMinX, supportedMinY, supportedMaxX, supportedMaxY);
		Point packedMinimum = LegacyPackedPointAdapter.toLegacyPoint(
			supported.getMinimum());
		Point packedMaximum = LegacyPackedPointAdapter.toLegacyPoint(
			supported.getMaximum());
		int minPackedRegionX = Math.floorDiv(
			packedMinimum.getX(), WorldRegionKey.REGION_SIZE);
		int minPackedRegionY = Math.floorDiv(
			packedMinimum.getY(), WorldRegionKey.REGION_SIZE);
		int maxPackedRegionX = Math.floorDiv(
			packedMaximum.getX(), WorldRegionKey.REGION_SIZE);
		int maxPackedRegionY = Math.floorDiv(
			packedMaximum.getY(), WorldRegionKey.REGION_SIZE);
		long assembledTiles = 0L;
		for (int packedRegionX = minPackedRegionX;
			packedRegionX <= maxPackedRegionX; packedRegionX++) {
			for (int packedRegionY = minPackedRegionY;
				packedRegionY <= maxPackedRegionY; packedRegionY++) {
				LegacyPackedRegionPartition partition =
					LegacyPackedRegionPartition.fromPackedRegionCoordinates(
						packedRegionX, packedRegionY);
				for (LegacyPackedRegionPartition.Fragment fragment
					: partition.getFragments()) {
					if (logicalRegionKey.equals(fragment.getLogicalRegionKey())) {
						SourceFragment source = new SourceFragment(
							packedRegionX, packedRegionY, fragment);
						sources.add(source);
						assembledTiles = Math.addExact(
							assembledTiles, fragment.getTileCount());
					}
				}
			}
		}
		validateAssembly(logicalRegionKey, supported, sources, assembledTiles);
		return new LegacyLogicalRegionAssembly(
			logicalRegionKey, target, supported, sources, targetTiles, assembledTiles);
	}

	private static boolean supportsLegacyLevel(final int level) {
		return level == -1 || level == 0 || level == 1 || level == 2;
	}

	private static WorldTileBounds bounds(
		final WorldSpaceId worldSpace,
		final int level,
		final int minX,
		final int minY,
		final int maxX,
		final int maxY) {
		return new WorldTileBounds(
			new WorldLocation(worldSpace, new WorldCoordinate(minX, minY, level)),
			new WorldLocation(worldSpace, new WorldCoordinate(maxX, maxY, level)));
	}

	private static long tileCount(final WorldTileBounds bounds) {
		long width = (long) bounds.getMaxX() - bounds.getMinX() + 1L;
		long height = (long) bounds.getMaxY() - bounds.getMinY() + 1L;
		return Math.multiplyExact(width, height);
	}

	private static void validateAssembly(
		final WorldRegionKey key,
		final WorldTileBounds supported,
		final List<SourceFragment> sources,
		final long assembledTiles) {
		if (sources.isEmpty() || assembledTiles != tileCount(supported)) {
			throw new IllegalStateException(
				"Legacy fragments do not cover the supported logical region");
		}
		int expectedMinY = supported.getMinY();
		for (SourceFragment source : sources) {
			LegacyPackedRegionPartition.Fragment fragment = source.getFragment();
			WorldTileBounds fragmentBounds = fragment.getLogicalBounds();
			if (!key.equals(fragment.getLogicalRegionKey())
				|| fragmentBounds.getMinX() != supported.getMinX()
				|| fragmentBounds.getMaxX() != supported.getMaxX()
				|| fragmentBounds.getMinY() != expectedMinY
				|| fragmentBounds.getMaxY() > supported.getMaxY()) {
				throw new IllegalStateException(
					"Legacy fragment assembly contains a gap, overlap, or foreign key");
			}
			expectedMinY = Math.addExact(fragmentBounds.getMaxY(), 1);
		}
		if (expectedMinY != Math.addExact(supported.getMaxY(), 1)) {
			throw new IllegalStateException(
				"Legacy fragment assembly does not reach the supported maximum Y");
		}
	}

	public WorldRegionKey getLogicalRegionKey() {
		return logicalRegionKey;
	}

	public WorldTileBounds getTargetBounds() {
		return targetBounds;
	}

	/** Returns null only when no tile in the logical region is legacy-representable. */
	public WorldTileBounds getLegacySupportedBounds() {
		return legacySupportedBounds;
	}

	public List<SourceFragment> getSourceFragments() {
		return sourceFragments;
	}

	public long getTargetTileCount() {
		return targetTileCount;
	}

	public long getAssembledTileCount() {
		return assembledTileCount;
	}

	public boolean isComplete() {
		return assembledTileCount == targetTileCount;
	}

	public boolean isPartial() {
		return assembledTileCount > 0L && assembledTileCount < targetTileCount;
	}

	public boolean isUnsupported() {
		return assembledTileCount == 0L;
	}

	@Override
	public String toString() {
		return "LegacyLogicalRegionAssembly{logicalRegionKey=" + logicalRegionKey
			+ ", targetTileCount=" + targetTileCount + ", assembledTileCount="
			+ assembledTileCount + ", sourceFragments=" + sourceFragments + "}";
	}

	/** A packed region cell and its exact fragment for the requested logical key. */
	public static final class SourceFragment {
		private final int packedRegionX;
		private final int packedRegionY;
		private final LegacyPackedRegionPartition.Fragment fragment;

		private SourceFragment(
			final int packedRegionX,
			final int packedRegionY,
			final LegacyPackedRegionPartition.Fragment fragment) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.fragment = Objects.requireNonNull(fragment, "fragment");
		}

		public int getPackedRegionX() {
			return packedRegionX;
		}

		public int getPackedRegionY() {
			return packedRegionY;
		}

		public LegacyPackedRegionPartition.Fragment getFragment() {
			return fragment;
		}

		@Override
		public String toString() {
			return "SourceFragment{packedRegionX=" + packedRegionX
				+ ", packedRegionY=" + packedRegionY + ", fragment="
				+ fragment + "}";
		}
	}
}
