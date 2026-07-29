package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Exact logical tile fragments contained by one legacy packed region cell. */
public final class LegacyPackedRegionPartition {
	private final LegacyPackedRegionCoverage coverage;
	private final List<Fragment> fragments;
	private final long partitionedTileCount;

	private LegacyPackedRegionPartition(
		final LegacyPackedRegionCoverage coverage,
		final List<Fragment> fragments,
		final long partitionedTileCount) {
		this.coverage = coverage;
		this.fragments = Collections.unmodifiableList(fragments);
		this.partitionedTileCount = partitionedTileCount;
	}

	public static LegacyPackedRegionPartition fromPackedRegionCoordinates(
		final int packedRegionX,
		final int packedRegionY) {
		LegacyPackedRegionCoverage coverage =
			LegacyPackedRegionCoverage.fromPackedRegionCoordinates(
				packedRegionX, packedRegionY);
		List<Fragment> fragments = new ArrayList<Fragment>();
		if (!coverage.hasLegacyTiles()) {
			return new LegacyPackedRegionPartition(coverage, fragments, 0L);
		}

		int fragmentStartY = coverage.getLegacyMinTileY();
		WorldRegionKey fragmentKey = keyAt(coverage.getLegacyMinTileX(), fragmentStartY);
		long partitionedTiles = 0L;
		for (int packedY = Math.addExact(fragmentStartY, 1);
			packedY <= coverage.getLegacyMaxTileY(); packedY++) {
			WorldRegionKey key = keyAt(coverage.getLegacyMinTileX(), packedY);
			if (!fragmentKey.equals(key)) {
				Fragment fragment = fragment(
					coverage, fragmentKey, fragmentStartY, packedY - 1);
				fragments.add(fragment);
				partitionedTiles = Math.addExact(
					partitionedTiles, fragment.getTileCount());
				fragmentStartY = packedY;
				fragmentKey = key;
			}
		}
		Fragment finalFragment = fragment(
			coverage, fragmentKey, fragmentStartY, coverage.getLegacyMaxTileY());
		fragments.add(finalFragment);
		partitionedTiles = Math.addExact(
			partitionedTiles, finalFragment.getTileCount());
		if (partitionedTiles != coverage.getLegacyTileCount()
			|| fragments.size() != coverage.getCoveredKeys().size()) {
			throw new IllegalStateException("Packed region partition is not lossless");
		}
		for (int index = 0; index < fragments.size(); index++) {
			if (!fragments.get(index).getLogicalRegionKey().equals(
				coverage.getCoveredKeys().get(index))) {
				throw new IllegalStateException(
					"Packed region partition key order differs from coverage");
			}
		}
		return new LegacyPackedRegionPartition(
			coverage, fragments, partitionedTiles);
	}

	private static WorldRegionKey keyAt(final int packedX, final int packedY) {
		return WorldRegionKey.fromLegacyPoint(Point.location(packedX, packedY));
	}

	private static Fragment fragment(
		final LegacyPackedRegionCoverage coverage,
		final WorldRegionKey key,
		final int minPackedY,
		final int maxPackedY) {
		int minPackedX = coverage.getLegacyMinTileX();
		int maxPackedX = coverage.getLegacyMaxTileX();
		WorldLocation minimum = LegacyPackedPointAdapter.fromLegacyPoint(
			Point.location(minPackedX, minPackedY));
		WorldLocation maximum = LegacyPackedPointAdapter.fromLegacyPoint(
			Point.location(maxPackedX, maxPackedY));
		WorldTileBounds logicalBounds = new WorldTileBounds(minimum, maximum);
		if (!key.equals(WorldRegionKey.from(minimum))
			|| !key.equals(WorldRegionKey.from(maximum))) {
			throw new IllegalStateException(
				"Packed fragment crosses a logical region boundary");
		}
		return new Fragment(
			key,
			minPackedX,
			minPackedY,
			maxPackedX,
			maxPackedY,
			minPackedX - coverage.getNominalMinTileX(),
			minPackedY - coverage.getNominalMinTileY(),
			maxPackedX - coverage.getNominalMinTileX(),
			maxPackedY - coverage.getNominalMinTileY(),
			logicalBounds);
	}

	public LegacyPackedRegionCoverage getCoverage() {
		return coverage;
	}

	public List<Fragment> getFragments() {
		return fragments;
	}

	public long getPartitionedTileCount() {
		return partitionedTileCount;
	}

	public boolean isEmpty() {
		return fragments.isEmpty();
	}

	public boolean requiresSplit() {
		return fragments.size() > 1;
	}

	@Override
	public String toString() {
		return "LegacyPackedRegionPartition{packedRegionX="
			+ coverage.getPackedRegionX() + ", packedRegionY="
			+ coverage.getPackedRegionY() + ", partitionedTileCount="
			+ partitionedTileCount + ", fragments=" + fragments + "}";
	}

	/** One contiguous packed rectangle wholly contained by one logical key. */
	public static final class Fragment {
		private final WorldRegionKey logicalRegionKey;
		private final int minPackedTileX;
		private final int minPackedTileY;
		private final int maxPackedTileX;
		private final int maxPackedTileY;
		private final int minPackedLocalX;
		private final int minPackedLocalY;
		private final int maxPackedLocalX;
		private final int maxPackedLocalY;
		private final WorldTileBounds logicalBounds;
		private final long tileCount;

		private Fragment(
			final WorldRegionKey logicalRegionKey,
			final int minPackedTileX,
			final int minPackedTileY,
			final int maxPackedTileX,
			final int maxPackedTileY,
			final int minPackedLocalX,
			final int minPackedLocalY,
			final int maxPackedLocalX,
			final int maxPackedLocalY,
			final WorldTileBounds logicalBounds) {
			this.logicalRegionKey = Objects.requireNonNull(
				logicalRegionKey, "logicalRegionKey");
			this.logicalBounds = Objects.requireNonNull(logicalBounds, "logicalBounds");
			if (minPackedTileX > maxPackedTileX || minPackedTileY > maxPackedTileY
				|| minPackedLocalX < 0 || minPackedLocalY < 0
				|| maxPackedLocalX >= WorldRegionKey.REGION_SIZE
				|| maxPackedLocalY >= WorldRegionKey.REGION_SIZE
				|| minPackedLocalX > maxPackedLocalX
				|| minPackedLocalY > maxPackedLocalY) {
				throw new IllegalArgumentException("Invalid packed fragment bounds");
			}
			this.minPackedTileX = minPackedTileX;
			this.minPackedTileY = minPackedTileY;
			this.maxPackedTileX = maxPackedTileX;
			this.maxPackedTileY = maxPackedTileY;
			this.minPackedLocalX = minPackedLocalX;
			this.minPackedLocalY = minPackedLocalY;
			this.maxPackedLocalX = maxPackedLocalX;
			this.maxPackedLocalY = maxPackedLocalY;
			long width = (long) maxPackedTileX - minPackedTileX + 1L;
			long height = (long) maxPackedTileY - minPackedTileY + 1L;
			this.tileCount = Math.multiplyExact(width, height);
		}

		public WorldRegionKey getLogicalRegionKey() {
			return logicalRegionKey;
		}

		public int getMinPackedTileX() {
			return minPackedTileX;
		}

		public int getMinPackedTileY() {
			return minPackedTileY;
		}

		public int getMaxPackedTileX() {
			return maxPackedTileX;
		}

		public int getMaxPackedTileY() {
			return maxPackedTileY;
		}

		public int getMinPackedLocalX() {
			return minPackedLocalX;
		}

		public int getMinPackedLocalY() {
			return minPackedLocalY;
		}

		public int getMaxPackedLocalX() {
			return maxPackedLocalX;
		}

		public int getMaxPackedLocalY() {
			return maxPackedLocalY;
		}

		public WorldTileBounds getLogicalBounds() {
			return logicalBounds;
		}

		public long getTileCount() {
			return tileCount;
		}

		public boolean containsPackedTile(final int packedX, final int packedY) {
			return packedX >= minPackedTileX && packedX <= maxPackedTileX
				&& packedY >= minPackedTileY && packedY <= maxPackedTileY;
		}

		public boolean containsLogicalLocation(final WorldLocation location) {
			return logicalBounds.contains(Objects.requireNonNull(location, "location"));
		}

		@Override
		public String toString() {
			return "Fragment{logicalRegionKey=" + logicalRegionKey
				+ ", packedTiles=(" + minPackedTileX + ',' + minPackedTileY
				+ ".." + maxPackedTileX + ',' + maxPackedTileY
				+ "), packedLocal=(" + minPackedLocalX + ',' + minPackedLocalY
				+ ".." + maxPackedLocalX + ',' + maxPackedLocalY
				+ "), logicalBounds=" + logicalBounds + ", tileCount="
				+ tileCount + "}";
		}
	}
}
