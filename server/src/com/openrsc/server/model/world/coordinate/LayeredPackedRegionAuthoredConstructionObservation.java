package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable projection of authored construction-origin counts onto one exact
 * packed Region retirement-safety observation.
 *
 * <p>The projection intentionally says only what a completed whole-world
 * population pass originally constructed at each packed source coordinate.
 * It neither classifies current entities by provenance nor retains placement
 * definitions, so it cannot serve as a reconstruction manifest.</p>
 */
public final class LayeredPackedRegionAuthoredConstructionObservation {
	private final long generation;
	private final long safetyObservedAtTick;
	private final long readinessObservedAtTick;
	private final int inventorySourceCount;
	private final int inventorySceneryCount;
	private final int inventoryBoundaryCount;
	private final int inventoryNpcSpawnCount;
	private final int inventoryGroundItemSpawnCount;
	private final int inventoryHarvestingSceneryCount;
	private final int inventoryAuthoredConstructionCount;
	private final List<SourceObservation> sources;
	private final int authoredSourceCount;
	private final int sceneryCount;
	private final int boundaryCount;
	private final int npcSpawnCount;
	private final int groundItemSpawnCount;
	private final int harvestingSceneryCount;
	private final int authoredConstructionCount;

	private LayeredPackedRegionAuthoredConstructionObservation(
		final LayeredPackedRegionAuthoredConstructionInventory inventory,
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final List<SourceObservation> sources) {
		this.generation = inventory.getGeneration();
		this.safetyObservedAtTick = safety.getObservedAtTick();
		this.readinessObservedAtTick = safety.getReadinessObservedAtTick();
		this.inventorySourceCount = inventory.getSourceCount();
		this.inventorySceneryCount = inventory.getSceneryCount();
		this.inventoryBoundaryCount = inventory.getBoundaryCount();
		this.inventoryNpcSpawnCount = inventory.getNpcSpawnCount();
		this.inventoryGroundItemSpawnCount = inventory.getGroundItemSpawnCount();
		this.inventoryHarvestingSceneryCount =
			inventory.getHarvestingSceneryCount();
		this.inventoryAuthoredConstructionCount =
			inventory.getAuthoredConstructionCount();
		this.sources = Collections.unmodifiableList(sources);
		int sourcesWithConstruction = 0;
		int scenery = 0;
		int boundaries = 0;
		int npcSpawns = 0;
		int groundItemSpawns = 0;
		int harvestingScenery = 0;
		for (SourceObservation source : sources) {
			sourcesWithConstruction += source.getAuthoredConstructionCount() > 0
				? 1 : 0;
			scenery = Math.addExact(scenery, source.getSceneryCount());
			boundaries = Math.addExact(boundaries, source.getBoundaryCount());
			npcSpawns = Math.addExact(npcSpawns, source.getNpcSpawnCount());
			groundItemSpawns = Math.addExact(
				groundItemSpawns, source.getGroundItemSpawnCount());
			harvestingScenery = Math.addExact(
				harvestingScenery, source.getHarvestingSceneryCount());
		}
		this.authoredSourceCount = sourcesWithConstruction;
		this.sceneryCount = scenery;
		this.boundaryCount = boundaries;
		this.npcSpawnCount = npcSpawns;
		this.groundItemSpawnCount = groundItemSpawns;
		this.harvestingSceneryCount = harvestingScenery;
		this.authoredConstructionCount = Math.addExact(
			Math.addExact(scenery, boundaries),
			Math.addExact(
				Math.addExact(npcSpawns, groundItemSpawns),
				harvestingScenery));
	}

	/** Projects one immutable inventory onto the exact same-order safety sources. */
	public static LayeredPackedRegionAuthoredConstructionObservation observe(
		final LayeredPackedRegionAuthoredConstructionInventory inventory,
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final int maximumPackedSources) {
		LayeredPackedRegionAuthoredConstructionInventory checkedInventory =
			Objects.requireNonNull(inventory, "inventory");
		LayeredPackedRegionRetirementSafetyAssessment checkedSafety =
			Objects.requireNonNull(safety, "safety");
		if (maximumPackedSources < 0
			|| maximumPackedSources
				> LayeredPackedRegionAuthoredConstructionInventory
					.MAXIMUM_PACKED_SOURCES
			|| checkedSafety.getSourceCount() > maximumPackedSources) {
			throw new IllegalArgumentException(
				"Authored construction observation exceeds the source budget");
		}
		List<SourceObservation> observations =
			new ArrayList<SourceObservation>(checkedSafety.getSourceCount());
		for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment source
			: checkedSafety.getSources()) {
			LayeredPackedRegionAuthoredConstructionInventory.PackedSourceInventory
				authored = checkedInventory.findSource(
					source.getPackedRegionX(), source.getPackedRegionY());
			observations.add(SourceObservation.from(source, authored));
		}
		return new LayeredPackedRegionAuthoredConstructionObservation(
			checkedInventory, checkedSafety, observations);
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public long getReadinessObservedAtTick() { return readinessObservedAtTick; }
	public int getInventorySourceCount() { return inventorySourceCount; }
	public int getInventorySceneryCount() { return inventorySceneryCount; }
	public int getInventoryBoundaryCount() { return inventoryBoundaryCount; }
	public int getInventoryNpcSpawnCount() { return inventoryNpcSpawnCount; }
	public int getInventoryGroundItemSpawnCount() {
		return inventoryGroundItemSpawnCount;
	}
	public int getInventoryHarvestingSceneryCount() {
		return inventoryHarvestingSceneryCount;
	}
	public int getInventoryAuthoredConstructionCount() {
		return inventoryAuthoredConstructionCount;
	}
	public List<SourceObservation> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getAuthoredSourceCount() { return authoredSourceCount; }
	public int getSceneryCount() { return sceneryCount; }
	public int getBoundaryCount() { return boundaryCount; }
	public int getNpcSpawnCount() { return npcSpawnCount; }
	public int getGroundItemSpawnCount() { return groundItemSpawnCount; }
	public int getHarvestingSceneryCount() { return harvestingSceneryCount; }
	public int getAuthoredConstructionCount() {
		return authoredConstructionCount;
	}

	/** Same-order count-only observation for one safety source. */
	public static final class SourceObservation {
		private final int packedRegionX;
		private final int packedRegionY;
		private final int sceneryCount;
		private final int boundaryCount;
		private final int npcSpawnCount;
		private final int groundItemSpawnCount;
		private final int harvestingSceneryCount;
		private final int authoredConstructionCount;

		private SourceObservation(
			final int packedRegionX,
			final int packedRegionY,
			final int sceneryCount,
			final int boundaryCount,
			final int npcSpawnCount,
			final int groundItemSpawnCount,
			final int harvestingSceneryCount) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.sceneryCount = sceneryCount;
			this.boundaryCount = boundaryCount;
			this.npcSpawnCount = npcSpawnCount;
			this.groundItemSpawnCount = groundItemSpawnCount;
			this.harvestingSceneryCount = harvestingSceneryCount;
			this.authoredConstructionCount = Math.addExact(
				Math.addExact(sceneryCount, boundaryCount),
				Math.addExact(
					Math.addExact(npcSpawnCount, groundItemSpawnCount),
					harvestingSceneryCount));
		}

		private static SourceObservation from(
			final LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
				safety,
			final LayeredPackedRegionAuthoredConstructionInventory
				.PackedSourceInventory authored) {
			return new SourceObservation(
				safety.getPackedRegionX(), safety.getPackedRegionY(),
				authored == null ? 0 : authored.getSceneryCount(),
				authored == null ? 0 : authored.getBoundaryCount(),
				authored == null ? 0 : authored.getNpcSpawnCount(),
				authored == null ? 0 : authored.getGroundItemSpawnCount(),
				authored == null ? 0 : authored.getHarvestingSceneryCount());
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getSceneryCount() { return sceneryCount; }
		public int getBoundaryCount() { return boundaryCount; }
		public int getNpcSpawnCount() { return npcSpawnCount; }
		public int getGroundItemSpawnCount() { return groundItemSpawnCount; }
		public int getHarvestingSceneryCount() { return harvestingSceneryCount; }
		public int getAuthoredConstructionCount() {
			return authoredConstructionCount;
		}
	}
}
