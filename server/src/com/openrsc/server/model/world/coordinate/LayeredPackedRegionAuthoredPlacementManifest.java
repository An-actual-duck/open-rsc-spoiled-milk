package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable detached definitions for authored content constructed by one
 * completed whole-world population pass.
 *
 * <p>Each placement has a stable identity within its packed source: the tuple
 * {@code (packedRegionX, packedRegionY, sourceOrdinal)}. Sources are ordered by
 * packed coordinate and placements retain successful construction order, so
 * exact duplicate definitions remain distinct and deterministic.</p>
 *
 * <p>The manifest contains primitive construction inputs and immutable text
 * only. It deliberately retains no entity, Region, tile, archive, event,
 * registry, cache, claim, permit, lease, or commit handle. It is not a reload
 * path, teardown plan, current-state snapshot, or grant of lifecycle
 * authority.</p>
 */
public final class LayeredPackedRegionAuthoredPlacementManifest {
	public static final int MAXIMUM_PACKED_SOURCES =
		LayeredPackedRegionAuthoredConstructionInventory
			.MAXIMUM_PACKED_SOURCES;
	public static final int MAXIMUM_AUTHORED_PLACEMENTS = 262144;
	public static final int NOT_APPLICABLE = -1;

	private final long generation;
	private final List<PackedSourceManifest> sources;
	private final int placementCount;
	private final int sceneryCount;
	private final int boundaryCount;
	private final int npcSpawnCount;
	private final int groundItemSpawnCount;
	private final int harvestingSceneryCount;

	private LayeredPackedRegionAuthoredPlacementManifest(
		final long generation,
		final List<PackedSourceManifest> sources) {
		this.generation = generation;
		this.sources = Collections.unmodifiableList(sources);
		int placements = 0;
		int scenery = 0;
		int boundaries = 0;
		int npcSpawns = 0;
		int groundItemSpawns = 0;
		int harvestingScenery = 0;
		for (PackedSourceManifest source : sources) {
			placements = Math.addExact(
				placements, source.getPlacementCount());
			scenery = Math.addExact(
				scenery, source.getSceneryCount());
			boundaries = Math.addExact(
				boundaries, source.getBoundaryCount());
			npcSpawns = Math.addExact(
				npcSpawns, source.getNpcSpawnCount());
			groundItemSpawns = Math.addExact(
				groundItemSpawns, source.getGroundItemSpawnCount());
			harvestingScenery = Math.addExact(
				harvestingScenery,
				source.getHarvestingSceneryCount());
		}
		this.placementCount = placements;
		this.sceneryCount = scenery;
		this.boundaryCount = boundaries;
		this.npcSpawnCount = npcSpawns;
		this.groundItemSpawnCount = groundItemSpawns;
		this.harvestingSceneryCount = harvestingScenery;
	}

	public static LayeredPackedRegionAuthoredPlacementManifest empty() {
		return new LayeredPackedRegionAuthoredPlacementManifest(
			0L, Collections.<PackedSourceManifest>emptyList());
	}

	public static Builder builder(final long generation) {
		return new Builder(generation);
	}

	public long getGeneration() { return generation; }
	public List<PackedSourceManifest> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getPlacementCount() { return placementCount; }
	public int getSceneryCount() { return sceneryCount; }
	public int getBoundaryCount() { return boundaryCount; }
	public int getNpcSpawnCount() { return npcSpawnCount; }
	public int getGroundItemSpawnCount() { return groundItemSpawnCount; }
	public int getHarvestingSceneryCount() {
		return harvestingSceneryCount;
	}

	public PackedSourceManifest findSource(
		final int packedRegionX,
		final int packedRegionY) {
		int low = 0;
		int high = sources.size() - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			PackedSourceManifest source = sources.get(middle);
			int x = Integer.compare(
				source.getPackedRegionX(), packedRegionX);
			int comparison = x != 0 ? x : Integer.compare(
				source.getPackedRegionY(), packedRegionY);
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

	/**
	 * Confirms this definition manifest describes exactly the same completed
	 * construction origins as the count-only inventory from the same pass.
	 */
	public boolean isCountEquivalentTo(
		final LayeredPackedRegionAuthoredConstructionInventory inventory) {
		if (inventory == null
			|| generation != inventory.getGeneration()
			|| getSourceCount() != inventory.getSourceCount()
			|| sceneryCount != inventory.getSceneryCount()
			|| boundaryCount != inventory.getBoundaryCount()
			|| npcSpawnCount != inventory.getNpcSpawnCount()
			|| groundItemSpawnCount != inventory.getGroundItemSpawnCount()
			|| harvestingSceneryCount
				!= inventory.getHarvestingSceneryCount()) {
			return false;
		}
		for (PackedSourceManifest source : sources) {
			LayeredPackedRegionAuthoredConstructionInventory
				.PackedSourceInventory counts = inventory.findSource(
					source.getPackedRegionX(), source.getPackedRegionY());
			if (counts == null
				|| source.getSceneryCount() != counts.getSceneryCount()
				|| source.getBoundaryCount() != counts.getBoundaryCount()
				|| source.getNpcSpawnCount() != counts.getNpcSpawnCount()
				|| source.getGroundItemSpawnCount()
					!= counts.getGroundItemSpawnCount()
				|| source.getHarvestingSceneryCount()
					!= counts.getHarvestingSceneryCount()) {
				return false;
			}
		}
		return true;
	}

	/** One immutable ordered placement set for a packed source Region. */
	public static final class PackedSourceManifest {
		private final int packedRegionX;
		private final int packedRegionY;
		private final List<AuthoredPlacement> placements;
		private final int sceneryCount;
		private final int boundaryCount;
		private final int npcSpawnCount;
		private final int groundItemSpawnCount;
		private final int harvestingSceneryCount;

		private PackedSourceManifest(final MutableSource source) {
			this.packedRegionX = source.packedRegionX;
			this.packedRegionY = source.packedRegionY;
			this.placements = Collections.unmodifiableList(
				new ArrayList<AuthoredPlacement>(source.placements));
			this.sceneryCount = source.sceneryCount;
			this.boundaryCount = source.boundaryCount;
			this.npcSpawnCount = source.npcSpawnCount;
			this.groundItemSpawnCount = source.groundItemSpawnCount;
			this.harvestingSceneryCount =
				source.harvestingSceneryCount;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public List<AuthoredPlacement> getPlacements() {
			return placements;
		}
		public int getPlacementCount() { return placements.size(); }
		public int getSceneryCount() { return sceneryCount; }
		public int getBoundaryCount() { return boundaryCount; }
		public int getNpcSpawnCount() { return npcSpawnCount; }
		public int getGroundItemSpawnCount() {
			return groundItemSpawnCount;
		}
		public int getHarvestingSceneryCount() {
			return harvestingSceneryCount;
		}

		public AuthoredPlacement findPlacement(final int sourceOrdinal) {
			if (sourceOrdinal <= 0 || sourceOrdinal > placements.size()) {
				return null;
			}
			return placements.get(sourceOrdinal - 1);
		}
	}

	/**
	 * One detached primitive definition. Fields irrelevant to its kind contain
	 * {@link #NOT_APPLICABLE}; harvesting entries preserve both the source item
	 * inputs and the scenery identity constructed from them.
	 */
	public static final class AuthoredPlacement {
		private final LayeredAuthoredPlacementIdentity identity;
		private final int authoredDefinitionId;
		private final int constructedEntityId;
		private final int packedX;
		private final int packedY;
		private final int permanentObjectId;
		private final int direction;
		private final int objectType;
		private final String objectOwner;
		private final int npcMinimumX;
		private final int npcMaximumX;
		private final int npcMinimumY;
		private final int npcMaximumY;
		private final int itemAmount;
		private final int itemRespawnTime;
		private final int itemNoted;

		private AuthoredPlacement(
			final LayeredAuthoredPlacementIdentity identity,
			final int authoredDefinitionId,
			final int constructedEntityId,
			final int packedX,
			final int packedY,
			final int permanentObjectId,
			final int direction,
			final int objectType,
			final String objectOwner,
			final int npcMinimumX,
			final int npcMaximumX,
			final int npcMinimumY,
			final int npcMaximumY,
			final int itemAmount,
			final int itemRespawnTime,
			final int itemNoted) {
			if (identity == null) {
				throw new NullPointerException("identity");
			}
			this.identity = identity;
			this.authoredDefinitionId = authoredDefinitionId;
			this.constructedEntityId = constructedEntityId;
			this.packedX = packedX;
			this.packedY = packedY;
			this.permanentObjectId = permanentObjectId;
			this.direction = direction;
			this.objectType = objectType;
			this.objectOwner = objectOwner;
			this.npcMinimumX = npcMinimumX;
			this.npcMaximumX = npcMaximumX;
			this.npcMinimumY = npcMinimumY;
			this.npcMaximumY = npcMaximumY;
			this.itemAmount = itemAmount;
			this.itemRespawnTime = itemRespawnTime;
			this.itemNoted = itemNoted;
		}

		public LayeredAuthoredPlacementIdentity getIdentity() {
			return identity;
		}
		public int getSourceOrdinal() {
			return identity.getSourceOrdinal();
		}
		public ConstructionKind getKind() {
			return identity.getConstructionKind();
		}
		public int getAuthoredDefinitionId() {
			return authoredDefinitionId;
		}
		public int getConstructedEntityId() {
			return constructedEntityId;
		}
		public int getPackedX() { return packedX; }
		public int getPackedY() { return packedY; }
		public int getPermanentObjectId() { return permanentObjectId; }
		public int getDirection() { return direction; }
		public int getObjectType() { return objectType; }
		public String getObjectOwner() { return objectOwner; }
		public int getNpcMinimumX() { return npcMinimumX; }
		public int getNpcMaximumX() { return npcMaximumX; }
		public int getNpcMinimumY() { return npcMinimumY; }
		public int getNpcMaximumY() { return npcMaximumY; }
		public int getItemAmount() { return itemAmount; }
		public int getItemRespawnTime() { return itemRespawnTime; }
		public int getItemNoted() { return itemNoted; }
	}

	/** Startup-only accumulator. A completed builder cannot be reused. */
	public static final class Builder {
		private final long generation;
		private final Map<Long, MutableSource> sources =
			new LinkedHashMap<Long, MutableSource>();
		private int placementCount;
		private LayeredAuthoredPlacementIdentity lastRecordedIdentity;
		private boolean built;

		private Builder(final long generation) {
			if (generation <= 0L) {
				throw new IllegalArgumentException(
					"Placement manifest generation must be positive");
			}
			this.generation = generation;
		}

		public Builder recordScenery(
			final int packedRegionX,
			final int packedRegionY,
			final int id,
			final int permanentId,
			final int packedX,
			final int packedY,
			final int direction,
			final int objectType,
			final String owner) {
			return recordObject(
				ConstructionKind.SCENERY, packedRegionX, packedRegionY,
				id, permanentId, packedX, packedY, direction,
				objectType, owner);
		}

		public Builder recordBoundary(
			final int packedRegionX,
			final int packedRegionY,
			final int id,
			final int permanentId,
			final int packedX,
			final int packedY,
			final int direction,
			final int objectType,
			final String owner) {
			return recordObject(
				ConstructionKind.BOUNDARY, packedRegionX, packedRegionY,
				id, permanentId, packedX, packedY, direction,
				objectType, owner);
		}

		public Builder recordNpcSpawn(
			final int packedRegionX,
			final int packedRegionY,
			final int id,
			final int startX,
			final int startY,
			final int minimumX,
			final int maximumX,
			final int minimumY,
			final int maximumY) {
			return record(
				ConstructionKind.NPC_SPAWN, packedRegionX, packedRegionY,
				id, id, startX, startY,
				NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE, null,
				minimumX, maximumX, minimumY, maximumY,
				NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE);
		}

		public Builder recordGroundItemSpawn(
			final int packedRegionX,
			final int packedRegionY,
			final int id,
			final int packedX,
			final int packedY,
			final int amount,
			final int respawnTime,
			final int noted) {
			return record(
				ConstructionKind.GROUND_ITEM_SPAWN,
				packedRegionX, packedRegionY, id, id, packedX, packedY,
				NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE, null,
				NOT_APPLICABLE, NOT_APPLICABLE,
				NOT_APPLICABLE, NOT_APPLICABLE,
				amount, respawnTime, noted);
		}

		public Builder recordHarvestingScenery(
			final int packedRegionX,
			final int packedRegionY,
			final int sourceItemId,
			final int constructedSceneryId,
			final int permanentId,
			final int packedX,
			final int packedY,
			final int direction,
			final int objectType,
			final String owner,
			final int sourceAmount,
			final int sourceRespawnTime,
			final int sourceNoted) {
			return record(
				ConstructionKind.HARVESTING_SCENERY,
				packedRegionX, packedRegionY,
				sourceItemId, constructedSceneryId, packedX, packedY,
				permanentId, direction, objectType, owner,
				NOT_APPLICABLE, NOT_APPLICABLE,
				NOT_APPLICABLE, NOT_APPLICABLE,
				sourceAmount, sourceRespawnTime, sourceNoted);
		}

		private Builder recordObject(
			final ConstructionKind kind,
			final int packedRegionX,
			final int packedRegionY,
			final int id,
			final int permanentId,
			final int packedX,
			final int packedY,
			final int direction,
			final int objectType,
			final String owner) {
			if ((kind == ConstructionKind.SCENERY && objectType != 0)
				|| (kind == ConstructionKind.BOUNDARY && objectType != 1)) {
				throw new IllegalArgumentException(
					"Object type does not match authored family");
			}
			return record(
				kind, packedRegionX, packedRegionY,
				id, id, packedX, packedY,
				permanentId, direction, objectType, owner,
				NOT_APPLICABLE, NOT_APPLICABLE,
				NOT_APPLICABLE, NOT_APPLICABLE,
				NOT_APPLICABLE, NOT_APPLICABLE, NOT_APPLICABLE);
		}

		private Builder record(
			final ConstructionKind kind,
			final int packedRegionX,
			final int packedRegionY,
			final int authoredDefinitionId,
			final int constructedEntityId,
			final int packedX,
			final int packedY,
			final int permanentObjectId,
			final int direction,
			final int objectType,
			final String objectOwner,
			final int npcMinimumX,
			final int npcMaximumX,
			final int npcMinimumY,
			final int npcMaximumY,
			final int itemAmount,
			final int itemRespawnTime,
			final int itemNoted) {
			checkOpen();
			if (kind == null) {
				throw new NullPointerException("kind");
			}
			if (packedRegionX < 0 || packedRegionY < 0
				|| authoredDefinitionId < 0 || constructedEntityId < 0
				|| packedX < 0 || packedY < 0) {
				throw new IllegalArgumentException(
					"Placement identifiers and coordinates must not be negative");
			}
			if (placementCount >= MAXIMUM_AUTHORED_PLACEMENTS) {
				throw new IllegalArgumentException(
					"Authored placement manifest exceeds its placement budget");
			}
			long key = packedSourceKey(packedRegionX, packedRegionY);
			MutableSource source = sources.get(Long.valueOf(key));
			if (source == null) {
				if (sources.size() >= MAXIMUM_PACKED_SOURCES) {
					throw new IllegalArgumentException(
						"Authored placement manifest exceeds its source budget");
				}
				source = new MutableSource(packedRegionX, packedRegionY);
				sources.put(Long.valueOf(key), source);
			}
			int sourceOrdinal = Math.incrementExact(
				source.placementsRecorded);
			source.placementsRecorded = sourceOrdinal;
			LayeredAuthoredPlacementIdentity identity =
				new LayeredAuthoredPlacementIdentity(
					generation, packedRegionX, packedRegionY,
					sourceOrdinal, kind);
			source.placements.add(new AuthoredPlacement(
				identity, authoredDefinitionId,
				constructedEntityId, packedX, packedY,
				permanentObjectId, direction, objectType, objectOwner,
				npcMinimumX, npcMaximumX, npcMinimumY, npcMaximumY,
				itemAmount, itemRespawnTime, itemNoted));
			source.record(kind);
			lastRecordedIdentity = identity;
			placementCount = Math.incrementExact(placementCount);
			return this;
		}

		/** Returns the immutable identity produced by the immediately prior record. */
		public LayeredAuthoredPlacementIdentity getLastRecordedIdentity() {
			checkOpen();
			if (lastRecordedIdentity == null) {
				throw new IllegalStateException(
					"No authored placement has been recorded");
			}
			return lastRecordedIdentity;
		}

		public LayeredPackedRegionAuthoredPlacementManifest build() {
			checkOpen();
			built = true;
			List<MutableSource> ordered =
				new ArrayList<MutableSource>(sources.values());
			Collections.sort(ordered, new Comparator<MutableSource>() {
				@Override
				public int compare(
					final MutableSource left,
					final MutableSource right) {
					int x = Integer.compare(
						left.packedRegionX, right.packedRegionX);
					return x != 0 ? x : Integer.compare(
						left.packedRegionY, right.packedRegionY);
				}
			});
			List<PackedSourceManifest> immutable =
				new ArrayList<PackedSourceManifest>(ordered.size());
			for (MutableSource source : ordered) {
				immutable.add(new PackedSourceManifest(source));
			}
			return new LayeredPackedRegionAuthoredPlacementManifest(
				generation, immutable);
		}

		private void checkOpen() {
			if (built) {
				throw new IllegalStateException(
					"Placement manifest builder is already complete");
			}
		}
	}

	private static long packedSourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xFFFFFFFFL);
	}

	private static final class MutableSource {
		private final int packedRegionX;
		private final int packedRegionY;
		private final List<AuthoredPlacement> placements =
			new ArrayList<AuthoredPlacement>();
		private int placementsRecorded;
		private int sceneryCount;
		private int boundaryCount;
		private int npcSpawnCount;
		private int groundItemSpawnCount;
		private int harvestingSceneryCount;

		private MutableSource(
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
