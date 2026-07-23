package com.openrsc.server.model.world.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detached, read-only inventory of the runtime state that prevents one exact
 * packed-source set from being made temporarily absent.
 *
 * <p>The preflight is captured while a
 * {@link LayeredPackedRegionSourceLifecycleBoundary} is active, but it retains
 * only coordinates, counts, versions, and typed blockers. It is not a permit,
 * lease, retirement decision, reload recipe, or lifecycle operation.</p>
 */
public final class LayeredPackedRegionSourceAbsencePreflight {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final List<SourceAssessment> sources;
	private final List<BlockerSummary> blockerSummaries;
	private final int blockedSourceCount;
	private final long playerCount;
	private final long npcCount;
	private final long authoredObjectCount;
	private final long dynamicObjectCount;
	private final long groundItemCount;
	private final long collisionProductTileCount;

	private LayeredPackedRegionSourceAbsencePreflight(
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final List<SourceInventory> inventories,
		final long observedAtTick,
		final boolean reloadSupported,
		final boolean regionLifecycleBoundaryHeld) {
		if (!regionLifecycleBoundaryHeld || observedAtTick < 0L
			|| observedAtTick < boundary.getRequirementsObservedAtTick()
			|| inventories.size() != boundary.getSelectedSourceCount()) {
			throw new IllegalArgumentException(
				"Packed-source absence preflight is stale or outside its boundary");
		}
		this.generation = boundary.getGeneration();
		this.requirementsObservedAtTick =
			boundary.getRequirementsObservedAtTick();
		this.observedAtTick = observedAtTick;
		this.residencyMirrorVersion = boundary.getResidencyMirrorVersion();

		List<SourceAssessment> assessed =
			new ArrayList<SourceAssessment>(inventories.size());
		Map<Blocker, Integer> blockerCounts =
			new EnumMap<Blocker, Integer>(Blocker.class);
		long players = 0L;
		long npcs = 0L;
		long authoredObjects = 0L;
		long dynamicObjects = 0L;
		long groundItems = 0L;
		long collisionProducts = 0L;
		int blocked = 0;
		for (int index = 0; index < inventories.size(); index++) {
			LayeredPackedRegionSourceLifecycleBoundary.PackedSource expected =
				boundary.getSelectedSources().get(index);
			SourceInventory inventory = Objects.requireNonNull(
				inventories.get(index), "inventories[" + index + "]");
			if (expected.getPackedRegionX() != inventory.getPackedRegionX()
				|| expected.getPackedRegionY()
					!= inventory.getPackedRegionY()) {
				throw new IllegalArgumentException(
					"Absence inventory must match the exact source order");
			}
			SourceAssessment source =
				SourceAssessment.assess(inventory, reloadSupported);
			assessed.add(source);
			blocked += source.isAbsenceReadyAtObservation() ? 0 : 1;
			for (Blocker blocker : source.getBlockers()) {
				Integer count = blockerCounts.get(blocker);
				blockerCounts.put(
					blocker,
					Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			}
			players = Math.addExact(players, inventory.getPlayerCount());
			npcs = Math.addExact(npcs, inventory.getNpcCount());
			authoredObjects = Math.addExact(
				authoredObjects, inventory.getAuthoredObjectCount());
			dynamicObjects = Math.addExact(
				dynamicObjects, inventory.getDynamicObjectCount());
			groundItems = Math.addExact(
				groundItems, inventory.getGroundItemCount());
			collisionProducts = Math.addExact(
				collisionProducts,
				inventory.getCollisionProductTileCount());
		}
		this.sources = Collections.unmodifiableList(assessed);
		this.blockedSourceCount = blocked;
		this.playerCount = players;
		this.npcCount = npcs;
		this.authoredObjectCount = authoredObjects;
		this.dynamicObjectCount = dynamicObjects;
		this.groundItemCount = groundItems;
		this.collisionProductTileCount = collisionProducts;

		List<BlockerSummary> summaries =
			new ArrayList<BlockerSummary>(Blocker.values().length);
		for (Blocker blocker : Blocker.values()) {
			Integer count = blockerCounts.get(blocker);
			summaries.add(new BlockerSummary(
				blocker, count == null ? 0 : count.intValue()));
		}
		this.blockerSummaries = Collections.unmodifiableList(summaries);
	}

	/**
	 * Correlates exact source-order inventories with one active Region boundary.
	 */
	static LayeredPackedRegionSourceAbsencePreflight assess(
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final List<SourceInventory> inventories,
		final long observedAtTick,
		final boolean reloadSupported,
		final boolean regionLifecycleBoundaryHeld) {
		return new LayeredPackedRegionSourceAbsencePreflight(
			Objects.requireNonNull(boundary, "boundary"),
			Objects.requireNonNull(inventories, "inventories"),
			observedAtTick, reloadSupported, regionLifecycleBoundaryHeld);
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getObservedAtTick() { return observedAtTick; }
	public long getResidencyMirrorVersion() { return residencyMirrorVersion; }
	public List<SourceAssessment> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getBlockedSourceCount() { return blockedSourceCount; }
	public int getReadySourceCount() {
		return getSourceCount() - blockedSourceCount;
	}
	public long getPlayerCount() { return playerCount; }
	public long getNpcCount() { return npcCount; }
	public long getAuthoredObjectCount() { return authoredObjectCount; }
	public long getDynamicObjectCount() { return dynamicObjectCount; }
	public long getGroundItemCount() { return groundItemCount; }
	public long getCollisionProductTileCount() {
		return collisionProductTileCount;
	}
	public List<BlockerSummary> getBlockerSummaries() {
		return blockerSummaries;
	}
	public BlockerSummary getBlockerSummary(final Blocker blocker) {
		return blockerSummaries.get(
			Objects.requireNonNull(blocker, "blocker").ordinal());
	}
	public boolean isAbsenceReadyAtObservation() {
		return blockedSourceCount == 0;
	}

	public boolean isPointInTimeOnly() { return true; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** Stable reasons why one observed source cannot yet become absent. */
	public enum Blocker {
		TILE_STORAGE_UNAVAILABLE,
		ACTIVE_PLAYER_PRESENT,
		NPC_MEMBERSHIP_PRESERVATION_UNAVAILABLE,
		AUTHORED_OBJECT_RELOAD_UNAVAILABLE,
		DYNAMIC_OBJECT_PRESERVATION_UNAVAILABLE,
		GROUND_ITEM_PRESERVATION_UNAVAILABLE,
		COLLISION_REBUILD_UNAVAILABLE,
		REGION_RELOAD_PATH_UNAVAILABLE
	}

	/** Immutable Region-local input; all runtime handles are discarded. */
	static final class SourceInventory {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean tileStorageAvailable;
		private final int playerCount;
		private final int npcCount;
		private final int authoredObjectCount;
		private final int dynamicObjectCount;
		private final int groundItemCount;
		private final int collisionProductTileCount;

		private SourceInventory(
			final int packedRegionX,
			final int packedRegionY,
			final boolean tileStorageAvailable,
			final int playerCount,
			final int npcCount,
			final int objectCount,
			final int dynamicObjectCount,
			final int groundItemCount,
			final int collisionProductTileCount) {
			if (packedRegionX < 0 || packedRegionY < 0
				|| playerCount < 0 || npcCount < 0 || objectCount < 0
				|| dynamicObjectCount < 0
				|| dynamicObjectCount > objectCount
				|| groundItemCount < 0 || collisionProductTileCount < 0) {
				throw new IllegalArgumentException(
					"Packed-source absence inventory is invalid");
			}
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.tileStorageAvailable = tileStorageAvailable;
			this.playerCount = playerCount;
			this.npcCount = npcCount;
			this.authoredObjectCount = objectCount - dynamicObjectCount;
			this.dynamicObjectCount = dynamicObjectCount;
			this.groundItemCount = groundItemCount;
			this.collisionProductTileCount = collisionProductTileCount;
		}

		static SourceInventory of(
			final int packedRegionX,
			final int packedRegionY,
			final boolean tileStorageAvailable,
			final int playerCount,
			final int npcCount,
			final int objectCount,
			final int dynamicObjectCount,
			final int groundItemCount,
			final int collisionProductTileCount) {
			return new SourceInventory(
				packedRegionX, packedRegionY, tileStorageAvailable,
				playerCount, npcCount, objectCount, dynamicObjectCount,
				groundItemCount, collisionProductTileCount);
		}

		int getPackedRegionX() { return packedRegionX; }
		int getPackedRegionY() { return packedRegionY; }
		boolean isTileStorageAvailable() { return tileStorageAvailable; }
		int getPlayerCount() { return playerCount; }
		int getNpcCount() { return npcCount; }
		int getAuthoredObjectCount() { return authoredObjectCount; }
		int getDynamicObjectCount() { return dynamicObjectCount; }
		int getGroundItemCount() { return groundItemCount; }
		int getCollisionProductTileCount() {
			return collisionProductTileCount;
		}
	}

	/** One exact source's detached counts and stable blocker list. */
	public static final class SourceAssessment {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean tileStorageAvailable;
		private final int playerCount;
		private final int npcCount;
		private final int authoredObjectCount;
		private final int dynamicObjectCount;
		private final int groundItemCount;
		private final int collisionProductTileCount;
		private final List<Blocker> blockers;

		private SourceAssessment(
			final SourceInventory inventory,
			final List<Blocker> blockers) {
			this.packedRegionX = inventory.getPackedRegionX();
			this.packedRegionY = inventory.getPackedRegionY();
			this.tileStorageAvailable =
				inventory.isTileStorageAvailable();
			this.playerCount = inventory.getPlayerCount();
			this.npcCount = inventory.getNpcCount();
			this.authoredObjectCount = inventory.getAuthoredObjectCount();
			this.dynamicObjectCount = inventory.getDynamicObjectCount();
			this.groundItemCount = inventory.getGroundItemCount();
			this.collisionProductTileCount =
				inventory.getCollisionProductTileCount();
			this.blockers = Collections.unmodifiableList(blockers);
		}

		private static SourceAssessment assess(
			final SourceInventory inventory,
			final boolean reloadSupported) {
			List<Blocker> blockers = new ArrayList<Blocker>();
			if (!inventory.isTileStorageAvailable()) {
				blockers.add(Blocker.TILE_STORAGE_UNAVAILABLE);
			}
			if (inventory.getPlayerCount() > 0) {
				blockers.add(Blocker.ACTIVE_PLAYER_PRESENT);
			}
			if (inventory.getNpcCount() > 0) {
				blockers.add(
					Blocker.NPC_MEMBERSHIP_PRESERVATION_UNAVAILABLE);
			}
			if (inventory.getAuthoredObjectCount() > 0
				&& !reloadSupported) {
				blockers.add(
					Blocker.AUTHORED_OBJECT_RELOAD_UNAVAILABLE);
			}
			if (inventory.getDynamicObjectCount() > 0) {
				blockers.add(
					Blocker.DYNAMIC_OBJECT_PRESERVATION_UNAVAILABLE);
			}
			if (inventory.getGroundItemCount() > 0) {
				blockers.add(
					Blocker.GROUND_ITEM_PRESERVATION_UNAVAILABLE);
			}
			if (inventory.getCollisionProductTileCount() > 0) {
				blockers.add(Blocker.COLLISION_REBUILD_UNAVAILABLE);
			}
			if (!reloadSupported) {
				blockers.add(Blocker.REGION_RELOAD_PATH_UNAVAILABLE);
			}
			return new SourceAssessment(inventory, blockers);
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isTileStorageAvailable() {
			return tileStorageAvailable;
		}
		public int getPlayerCount() { return playerCount; }
		public int getNpcCount() { return npcCount; }
		public int getAuthoredObjectCount() { return authoredObjectCount; }
		public int getDynamicObjectCount() { return dynamicObjectCount; }
		public int getGroundItemCount() { return groundItemCount; }
		public int getCollisionProductTileCount() {
			return collisionProductTileCount;
		}
		public List<Blocker> getBlockers() { return blockers; }
		public boolean isAbsenceReadyAtObservation() {
			return blockers.isEmpty();
		}
	}

	/** Number of exact selected sources carrying one blocker. */
	public static final class BlockerSummary {
		private final Blocker blocker;
		private final int blockedSourceCount;

		private BlockerSummary(
			final Blocker blocker,
			final int blockedSourceCount) {
			this.blocker = Objects.requireNonNull(blocker, "blocker");
			if (blockedSourceCount < 0) {
				throw new IllegalArgumentException(
					"Blocked source count must not be negative");
			}
			this.blockedSourceCount = blockedSourceCount;
		}

		public Blocker getBlocker() { return blocker; }
		public int getBlockedSourceCount() { return blockedSourceCount; }
	}
}
