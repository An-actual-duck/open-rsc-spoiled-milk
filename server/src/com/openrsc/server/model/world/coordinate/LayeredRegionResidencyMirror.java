package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checked, non-authoritative logical view of current packed Region residency.
 *
 * <p>The mirror records Region lifecycle only. It never retains tile, collision,
 * entity, or visibility state.</p>
 */
public final class LayeredRegionResidencyMirror {
	private static final int MAX_PACKED_REGION_X = Math.floorDiv(
		LegacyPackedPointAdapter.MAX_LEGACY_X, WorldRegionKey.REGION_SIZE);
	private static final int MAX_PACKED_REGION_Y = Math.floorDiv(
		LegacyPackedPointAdapter.MAX_PACKED_Y, WorldRegionKey.REGION_SIZE);

	private final Set<Long> packedRegions = new HashSet<Long>();
	private final Map<WorldRegionKey, Set<Long>> packedRegionsByLogicalKey =
		new HashMap<WorldRegionKey, Set<Long>>();
	private long version;

	/** Registers one current packed Region and all supported logical keys it covers. */
	public synchronized boolean registerPackedRegion(
		final int packedRegionX,
		final int packedRegionY) {
		long packedKey = packedKey(packedRegionX, packedRegionY);
		if (!packedRegions.add(packedKey)) {
			return false;
		}
		for (WorldRegionKey logicalKey : coveredKeys(packedRegionX, packedRegionY)) {
			Set<Long> sources = packedRegionsByLogicalKey.get(logicalKey);
			if (sources == null) {
				sources = new HashSet<Long>();
				packedRegionsByLogicalKey.put(logicalKey, sources);
			}
			if (!sources.add(packedKey)) {
				throw new IllegalStateException(
					"Packed Region is already indexed for its logical key");
			}
		}
		version++;
		return true;
	}

	/** Removes one packed Region from the future per-region lifecycle boundary. */
	public synchronized boolean unregisterPackedRegion(
		final int packedRegionX,
		final int packedRegionY) {
		long packedKey = packedKey(packedRegionX, packedRegionY);
		if (!packedRegions.remove(packedKey)) {
			return false;
		}
		for (WorldRegionKey logicalKey : coveredKeys(packedRegionX, packedRegionY)) {
			Set<Long> sources = packedRegionsByLogicalKey.get(logicalKey);
			if (sources == null || !sources.remove(packedKey)) {
				throw new IllegalStateException(
					"Logical residency index is missing a registered packed Region");
			}
			if (sources.isEmpty()) {
				packedRegionsByLogicalKey.remove(logicalKey);
			}
		}
		version++;
		return true;
	}

	/** Clears all lifecycle state during an authoritative RegionManager unload. */
	public synchronized boolean clear() {
		if (packedRegions.isEmpty() && packedRegionsByLogicalKey.isEmpty()) {
			return false;
		}
		packedRegions.clear();
		packedRegionsByLogicalKey.clear();
		version++;
		return true;
	}

	/** Captures one immutable, explicitly versioned logical residency view. */
	public synchronized Snapshot snapshot(final WorldRegionKey logicalRegionKey) {
		WorldRegionKey key = Objects.requireNonNull(
			logicalRegionKey, "logicalRegionKey");
		LegacyLogicalRegionAssembly assembly =
			LegacyLogicalRegionAssembly.fromLogicalRegionKey(key);
		Set<Long> indexedSources = packedRegionsByLogicalKey.get(key);
		Set<Long> expectedSources = new HashSet<Long>();
		List<SourceResidency> sources = new ArrayList<SourceResidency>();
		long residentTiles = 0L;
		for (LegacyLogicalRegionAssembly.SourceFragment source
			: assembly.getSourceFragments()) {
			long sourceKey = packedKey(
				source.getPackedRegionX(), source.getPackedRegionY());
			if (!expectedSources.add(sourceKey)) {
				throw new IllegalStateException(
					"Logical assembly repeats a packed source Region");
			}
			boolean resident = indexedSources != null
				&& indexedSources.contains(sourceKey);
			long tileCount = source.getFragment().getTileCount();
			if (resident) {
				residentTiles = Math.addExact(residentTiles, tileCount);
			}
			sources.add(new SourceResidency(
				source.getPackedRegionX(), source.getPackedRegionY(), tileCount,
				resident));
		}
		if (indexedSources != null && !expectedSources.containsAll(indexedSources)) {
			throw new IllegalStateException(
				"Logical residency index contains a source outside its checked assembly");
		}
		return new Snapshot(
			key, version, assembly.getTargetTileCount(),
			assembly.getAssembledTileCount(), residentTiles, sources);
	}

	public synchronized long getVersion() {
		return version;
	}

	public synchronized int getPackedRegionCount() {
		return packedRegions.size();
	}

	public synchronized int getLogicalRegionCount() {
		return packedRegionsByLogicalKey.size();
	}

	private static List<WorldRegionKey> coveredKeys(
		final int packedRegionX,
		final int packedRegionY) {
		if (packedRegionX < 0 || packedRegionY < 0
			|| packedRegionX > MAX_PACKED_REGION_X
			|| packedRegionY > MAX_PACKED_REGION_Y) {
			return Collections.emptyList();
		}
		return LegacyPackedRegionCoverage.fromPackedRegionCoordinates(
			packedRegionX, packedRegionY).getCoveredKeys();
	}

	private static long packedKey(final int packedRegionX, final int packedRegionY) {
		return ((long) packedRegionX << 32) ^ (packedRegionY & 0xFFFFFFFFL);
	}

	/** Immutable logical-region residency captured at one mirror version. */
	public static final class Snapshot {
		private final WorldRegionKey logicalRegionKey;
		private final long mirrorVersion;
		private final long targetTileCount;
		private final long legacySupportedTileCount;
		private final long residentTileCount;
		private final List<SourceResidency> sources;
		private final int residentSourceCount;

		private Snapshot(
			final WorldRegionKey logicalRegionKey,
			final long mirrorVersion,
			final long targetTileCount,
			final long legacySupportedTileCount,
			final long residentTileCount,
			final List<SourceResidency> sources) {
			this.logicalRegionKey = logicalRegionKey;
			this.mirrorVersion = mirrorVersion;
			this.targetTileCount = targetTileCount;
			this.legacySupportedTileCount = legacySupportedTileCount;
			this.residentTileCount = residentTileCount;
			this.sources = Collections.unmodifiableList(sources);
			int residentCount = 0;
			long supportedFromSources = 0L;
			long residentFromSources = 0L;
			for (SourceResidency source : sources) {
				supportedFromSources = Math.addExact(
					supportedFromSources, source.getTileCount());
				if (source.isResident()) {
					residentCount++;
					residentFromSources = Math.addExact(
						residentFromSources, source.getTileCount());
				}
			}
			if (supportedFromSources != legacySupportedTileCount
				|| residentFromSources != residentTileCount
				|| residentTileCount > legacySupportedTileCount
				|| legacySupportedTileCount > targetTileCount) {
				throw new IllegalArgumentException(
					"Invalid logical Region residency counts");
			}
			this.residentSourceCount = residentCount;
		}

		public WorldRegionKey getLogicalRegionKey() {
			return logicalRegionKey;
		}

		public long getMirrorVersion() {
			return mirrorVersion;
		}

		public long getTargetTileCount() {
			return targetTileCount;
		}

		public long getLegacySupportedTileCount() {
			return legacySupportedTileCount;
		}

		public long getResidentTileCount() {
			return residentTileCount;
		}

		public List<SourceResidency> getSources() {
			return sources;
		}

		public int getSourceCount() {
			return sources.size();
		}

		public int getResidentSourceCount() {
			return residentSourceCount;
		}

		public int getMissingSourceCount() {
			return getSourceCount() - residentSourceCount;
		}

		public boolean isLegacySupported() {
			return legacySupportedTileCount > 0L;
		}

		public boolean isLegacyCoverageComplete() {
			return legacySupportedTileCount == targetTileCount;
		}

		public boolean isResident() {
			return isLegacySupported()
				&& residentSourceCount == getSourceCount();
		}

		@Override
		public String toString() {
			return "Snapshot{logicalRegionKey=" + logicalRegionKey
				+ ", mirrorVersion=" + mirrorVersion + ", residentSources="
				+ residentSourceCount + '/' + getSourceCount()
				+ ", residentTileCount=" + residentTileCount + '/'
				+ legacySupportedTileCount + "}";
		}
	}

	/** One immutable packed source contribution to a logical Region. */
	public static final class SourceResidency {
		private final int packedRegionX;
		private final int packedRegionY;
		private final long tileCount;
		private final boolean resident;

		private SourceResidency(
			final int packedRegionX,
			final int packedRegionY,
			final long tileCount,
			final boolean resident) {
			if (tileCount < 1L) {
				throw new IllegalArgumentException(
					"Packed source contribution must contain tiles");
			}
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.tileCount = tileCount;
			this.resident = resident;
		}

		public int getPackedRegionX() {
			return packedRegionX;
		}

		public int getPackedRegionY() {
			return packedRegionY;
		}

		public long getTileCount() {
			return tileCount;
		}

		public boolean isResident() {
			return resident;
		}

		@Override
		public String toString() {
			return "SourceResidency{packedRegionX=" + packedRegionX
				+ ", packedRegionY=" + packedRegionY + ", tileCount="
				+ tileCount + ", resident=" + resident + "}";
		}
	}
}
