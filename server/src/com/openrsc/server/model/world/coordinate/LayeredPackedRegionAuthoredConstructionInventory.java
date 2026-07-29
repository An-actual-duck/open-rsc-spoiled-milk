package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable count-only inventory of authored content constructed by the
 * current whole-world population pass.
 *
 * <p>This value deliberately retains neither placement definitions nor entity
 * handles. It describes construction origins; it is not a reload manifest,
 * teardown permit, active-entity census, or proof that a packed Region can be
 * reconstructed. NPCs may roam away from their authored start Region and
 * authored objects/items may be temporarily removed or replaced after this
 * inventory is frozen.</p>
 */
public final class LayeredPackedRegionAuthoredConstructionInventory {
	public static final int MAXIMUM_PACKED_SOURCES = 8192;

	private final long generation;
	private final List<PackedSourceInventory> sources;
	private final int sceneryCount;
	private final int boundaryCount;
	private final int npcSpawnCount;
	private final int groundItemSpawnCount;
	private final int harvestingSceneryCount;

	private LayeredPackedRegionAuthoredConstructionInventory(
		final long generation,
		final List<PackedSourceInventory> sources) {
		this.generation = generation;
		this.sources = Collections.unmodifiableList(sources);
		int scenery = 0;
		int boundaries = 0;
		int npcSpawns = 0;
		int groundItemSpawns = 0;
		int harvestingScenery = 0;
		for (PackedSourceInventory source : sources) {
			scenery = Math.addExact(scenery, source.getSceneryCount());
			boundaries = Math.addExact(boundaries, source.getBoundaryCount());
			npcSpawns = Math.addExact(npcSpawns, source.getNpcSpawnCount());
			groundItemSpawns = Math.addExact(
				groundItemSpawns, source.getGroundItemSpawnCount());
			harvestingScenery = Math.addExact(
				harvestingScenery, source.getHarvestingSceneryCount());
		}
		this.sceneryCount = scenery;
		this.boundaryCount = boundaries;
		this.npcSpawnCount = npcSpawns;
		this.groundItemSpawnCount = groundItemSpawns;
		this.harvestingSceneryCount = harvestingScenery;
	}

	public static LayeredPackedRegionAuthoredConstructionInventory empty() {
		return new LayeredPackedRegionAuthoredConstructionInventory(
			0L, Collections.<PackedSourceInventory>emptyList());
	}

	public static Builder builder(final long generation) {
		return new Builder(generation);
	}

	public long getGeneration() { return generation; }
	public List<PackedSourceInventory> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getSceneryCount() { return sceneryCount; }
	public int getBoundaryCount() { return boundaryCount; }
	public int getNpcSpawnCount() { return npcSpawnCount; }
	public int getGroundItemSpawnCount() { return groundItemSpawnCount; }
	public int getHarvestingSceneryCount() { return harvestingSceneryCount; }

	public int getAuthoredConstructionCount() {
		return Math.addExact(
			Math.addExact(sceneryCount, boundaryCount),
			Math.addExact(
				Math.addExact(npcSpawnCount, groundItemSpawnCount),
				harvestingSceneryCount));
	}

	public PackedSourceInventory findSource(
		final int packedRegionX,
		final int packedRegionY) {
		int low = 0;
		int high = sources.size() - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			PackedSourceInventory source = sources.get(middle);
			int x = Integer.compare(source.getPackedRegionX(), packedRegionX);
			int comparison = x != 0 ? x
				: Integer.compare(source.getPackedRegionY(), packedRegionY);
			if (comparison < 0) {
				low = middle + 1;
			} else if (comparison > 0) {
				high = middle - 1;
			} else {
				return source;
			}
		}
		return null;
	}

	public enum ConstructionKind {
		SCENERY,
		BOUNDARY,
		NPC_SPAWN,
		GROUND_ITEM_SPAWN,
		HARVESTING_SCENERY
	}

	/** One immutable authored-construction count set for a packed Region. */
	public static final class PackedSourceInventory {
		private final int packedRegionX;
		private final int packedRegionY;
		private final int sceneryCount;
		private final int boundaryCount;
		private final int npcSpawnCount;
		private final int groundItemSpawnCount;
		private final int harvestingSceneryCount;

		private PackedSourceInventory(
			final int packedRegionX,
			final int packedRegionY,
			final MutableCounts counts) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.sceneryCount = counts.sceneryCount;
			this.boundaryCount = counts.boundaryCount;
			this.npcSpawnCount = counts.npcSpawnCount;
			this.groundItemSpawnCount = counts.groundItemSpawnCount;
			this.harvestingSceneryCount = counts.harvestingSceneryCount;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getSceneryCount() { return sceneryCount; }
		public int getBoundaryCount() { return boundaryCount; }
		public int getNpcSpawnCount() { return npcSpawnCount; }
		public int getGroundItemSpawnCount() { return groundItemSpawnCount; }
		public int getHarvestingSceneryCount() { return harvestingSceneryCount; }

		public int getAuthoredConstructionCount() {
			return Math.addExact(
				Math.addExact(sceneryCount, boundaryCount),
				Math.addExact(
					Math.addExact(npcSpawnCount, groundItemSpawnCount),
					harvestingSceneryCount));
		}
	}

	/** Startup-only accumulator. A completed builder cannot be reused. */
	public static final class Builder {
		private final long generation;
		private final Map<Long, MutableCounts> sources =
			new LinkedHashMap<Long, MutableCounts>();
		private boolean built;

		private Builder(final long generation) {
			if (generation <= 0L) {
				throw new IllegalArgumentException(
					"Construction inventory generation must be positive");
			}
			this.generation = generation;
		}

		public Builder record(
			final ConstructionKind kind,
			final int packedRegionX,
			final int packedRegionY) {
			if (built) {
				throw new IllegalStateException(
					"Construction inventory builder is already complete");
			}
			if (kind == null) {
				throw new NullPointerException("kind");
			}
			if (packedRegionX < 0 || packedRegionY < 0) {
				throw new IllegalArgumentException(
					"Packed Region coordinates must not be negative");
			}
			long key = packedSourceKey(packedRegionX, packedRegionY);
			MutableCounts counts = sources.get(Long.valueOf(key));
			if (counts == null) {
				if (sources.size() >= MAXIMUM_PACKED_SOURCES) {
					throw new IllegalArgumentException(
						"Authored construction inventory exceeds the source budget");
				}
				counts = new MutableCounts(packedRegionX, packedRegionY);
				sources.put(Long.valueOf(key), counts);
			}
			counts.record(kind);
			return this;
		}

		public LayeredPackedRegionAuthoredConstructionInventory build() {
			if (built) {
				throw new IllegalStateException(
					"Construction inventory builder is already complete");
			}
			built = true;
			List<MutableCounts> ordered =
				new ArrayList<MutableCounts>(sources.values());
			Collections.sort(ordered, new Comparator<MutableCounts>() {
				@Override
				public int compare(
					final MutableCounts left,
					final MutableCounts right) {
					int x = Integer.compare(
						left.packedRegionX, right.packedRegionX);
					return x != 0 ? x : Integer.compare(
						left.packedRegionY, right.packedRegionY);
				}
			});
			List<PackedSourceInventory> immutable =
				new ArrayList<PackedSourceInventory>(ordered.size());
			for (MutableCounts counts : ordered) {
				immutable.add(new PackedSourceInventory(
					counts.packedRegionX, counts.packedRegionY, counts));
			}
			return new LayeredPackedRegionAuthoredConstructionInventory(
				generation, immutable);
		}
	}

	private static long packedSourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32) ^ (packedRegionY & 0xFFFFFFFFL);
	}

	private static final class MutableCounts {
		private final int packedRegionX;
		private final int packedRegionY;
		private int sceneryCount;
		private int boundaryCount;
		private int npcSpawnCount;
		private int groundItemSpawnCount;
		private int harvestingSceneryCount;

		private MutableCounts(
			final int packedRegionX,
			final int packedRegionY) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
		}

		private void record(final ConstructionKind kind) {
			switch (kind) {
				case SCENERY:
					sceneryCount = Math.incrementExact(sceneryCount);
					break;
				case BOUNDARY:
					boundaryCount = Math.incrementExact(boundaryCount);
					break;
				case NPC_SPAWN:
					npcSpawnCount = Math.incrementExact(npcSpawnCount);
					break;
				case GROUND_ITEM_SPAWN:
					groundItemSpawnCount = Math.incrementExact(
						groundItemSpawnCount);
					break;
				case HARVESTING_SCENERY:
					harvestingSceneryCount = Math.incrementExact(
						harvestingSceneryCount);
					break;
				default:
					throw new IllegalArgumentException(
						"Unsupported authored construction kind: " + kind);
			}
		}
	}
}
