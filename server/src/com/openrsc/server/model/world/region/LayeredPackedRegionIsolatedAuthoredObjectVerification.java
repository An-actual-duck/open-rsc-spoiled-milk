package com.openrsc.server.model.world.region;

import java.util.Objects;

/**
 * Detached receipt proving exact authored scenery membership on one
 * terrain-initialized disposable Region.
 *
 * <p>No Region, entity, collision state, manager, collection, monitor, or
 * runtime handle survives in this value.</p>
 */
public final class LayeredPackedRegionIsolatedAuthoredObjectVerification {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int sourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final int terrainTileCount;
	private final String terrainFingerprintSha256;
	private final int replayPlacementCount;
	private final int sceneryPlacementCount;
	private final int boundaryPlacementCount;
	private final int harvestingSceneryPlacementCount;
	private final int skippedNpcSpawnPlacementCount;
	private final int skippedGroundItemSpawnPlacementCount;
	private final int constructedObjectCount;
	private final int heldBoundaryCount;
	private final String authoredReplayFingerprintSha256;

	private LayeredPackedRegionIsolatedAuthoredObjectVerification(
		final LayeredPackedRegionBlankContainerPlan containerPlan,
		final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
		final LayeredPackedRegionAuthoredReplayPlan replayPlan,
		final boolean blankContractMatchedBeforeTerrain,
		final int constructedObjectCount,
		final int heldBoundaryCount,
		final boolean entityFamiliesMatchedAfterReplay,
		final boolean exactObjectMembershipMatchedAfterReplay) {
		LayeredPackedRegionBlankContainerPlan container =
			Objects.requireNonNull(containerPlan, "containerPlan");
		LayeredPackedRegionTerrainInitializationPlan terrain =
			Objects.requireNonNull(terrainPlan, "terrainPlan");
		LayeredPackedRegionAuthoredReplayPlan replay =
			Objects.requireNonNull(replayPlan, "replayPlan");
		if (container.getGeneration() != terrain.getGeneration()
			|| container.getRequirementsObservedAtTick()
				!= terrain.getRequirementsObservedAtTick()
			|| container.getObservedAtTick() != terrain.getObservedAtTick()
			|| container.getResidencyMirrorVersion()
				!= terrain.getResidencyMirrorVersion()
			|| container.getAuthoredGeneration()
				!= terrain.getAuthoredGeneration()
			|| container.getSourceOrdinal() != terrain.getSourceOrdinal()
			|| container.getPackedRegionX() != terrain.getPackedRegionX()
			|| container.getPackedRegionY() != terrain.getPackedRegionY()
			|| replay.getGeneration() != terrain.getGeneration()
			|| replay.getRequirementsObservedAtTick()
				!= terrain.getRequirementsObservedAtTick()
			|| replay.getObservedAtTick() != terrain.getObservedAtTick()
			|| replay.getResidencyMirrorVersion()
				!= terrain.getResidencyMirrorVersion()
			|| replay.getAuthoredGeneration()
				!= terrain.getAuthoredGeneration()
			|| replay.getSelectedSourceOrdinal()
				!= terrain.getSourceOrdinal()
			|| replay.getPackedRegionX() != terrain.getPackedRegionX()
			|| replay.getPackedRegionY() != terrain.getPackedRegionY()
			|| !terrain.isDetachedTerrainDefinition()
			|| !terrain.isTerrainInputDefinitionComplete()
			|| !replay.isPointInTimeOnly()
			|| !replay.isDetachedReplayDefinition()
			|| !replay.isReplayDefinitionComplete()
			|| replay.isExecutableReplay()
			|| replay.isRuntimeHandleRetained()
			|| constructedObjectCount
				!= replay.getAuthoredObjectPlacementCount()
			|| heldBoundaryCount != constructedObjectCount
			|| !blankContractMatchedBeforeTerrain
			|| !entityFamiliesMatchedAfterReplay
			|| !exactObjectMembershipMatchedAfterReplay) {
			throw new IllegalArgumentException(
				"Isolated authored object membership does not match replay");
		}
		this.generation = replay.getGeneration();
		this.requirementsObservedAtTick =
			replay.getRequirementsObservedAtTick();
		this.observedAtTick = replay.getObservedAtTick();
		this.residencyMirrorVersion = replay.getResidencyMirrorVersion();
		this.authoredGeneration = replay.getAuthoredGeneration();
		this.sourceOrdinal = replay.getSelectedSourceOrdinal();
		this.packedRegionX = replay.getPackedRegionX();
		this.packedRegionY = replay.getPackedRegionY();
		this.terrainTileCount = terrain.getTileCount();
		this.terrainFingerprintSha256 = terrain.getFingerprintSha256();
		this.replayPlacementCount = replay.getPlacementCount();
		this.sceneryPlacementCount = replay.getSceneryPlacementCount();
		this.boundaryPlacementCount = replay.getBoundaryPlacementCount();
		this.harvestingSceneryPlacementCount =
			replay.getHarvestingSceneryPlacementCount();
		this.skippedNpcSpawnPlacementCount =
			replay.getNpcSpawnPlacementCount();
		this.skippedGroundItemSpawnPlacementCount =
			replay.getGroundItemSpawnPlacementCount();
		this.constructedObjectCount = constructedObjectCount;
		this.heldBoundaryCount = heldBoundaryCount;
		this.authoredReplayFingerprintSha256 =
			replay.getFingerprintSha256();
	}

	static LayeredPackedRegionIsolatedAuthoredObjectVerification verified(
		final LayeredPackedRegionBlankContainerPlan containerPlan,
		final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
		final LayeredPackedRegionAuthoredReplayPlan replayPlan,
		final boolean blankContractMatchedBeforeTerrain,
		final int constructedObjectCount,
		final int heldBoundaryCount,
		final boolean entityFamiliesMatchedAfterReplay,
		final boolean exactObjectMembershipMatchedAfterReplay) {
		return new LayeredPackedRegionIsolatedAuthoredObjectVerification(
			containerPlan, terrainPlan, replayPlan,
			blankContractMatchedBeforeTerrain, constructedObjectCount,
			heldBoundaryCount, entityFamiliesMatchedAfterReplay,
			exactObjectMembershipMatchedAfterReplay);
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getObservedAtTick() { return observedAtTick; }
	public long getResidencyMirrorVersion() { return residencyMirrorVersion; }
	public long getAuthoredGeneration() { return authoredGeneration; }
	public int getSourceOrdinal() { return sourceOrdinal; }
	public int getPackedRegionX() { return packedRegionX; }
	public int getPackedRegionY() { return packedRegionY; }
	public int getTerrainTileCount() { return terrainTileCount; }
	public String getTerrainFingerprintSha256() {
		return terrainFingerprintSha256;
	}
	public int getReplayPlacementCount() { return replayPlacementCount; }
	public int getSceneryPlacementCount() {
		return sceneryPlacementCount;
	}
	public int getBoundaryPlacementCount() {
		return boundaryPlacementCount;
	}
	public int getHarvestingSceneryPlacementCount() {
		return harvestingSceneryPlacementCount;
	}
	public int getSkippedNpcSpawnPlacementCount() {
		return skippedNpcSpawnPlacementCount;
	}
	public int getSkippedGroundItemSpawnPlacementCount() {
		return skippedGroundItemSpawnPlacementCount;
	}
	public int getConstructedObjectCount() {
		return constructedObjectCount;
	}
	public int getHeldBoundaryCount() { return heldBoundaryCount; }
	public String getAuthoredReplayFingerprintSha256() {
		return authoredReplayFingerprintSha256;
	}

	public boolean isVerificationOnly() { return true; }
	public boolean isDisposableRegionConstructed() { return true; }
	public boolean isBlankContractMatchedBeforeTerrain() { return true; }
	public boolean isTerrainAppliedBeforeObjectMembership() { return true; }
	public boolean isTerrainMatchedAfterObjectMembership() { return true; }
	public boolean isAuthoredSceneryMembershipApplied() { return true; }
	public boolean isExactObjectMembershipMatchedAfterReplay() {
		return true;
	}
	public boolean isNpcMembershipApplied() { return false; }
	public boolean isGroundItemMembershipApplied() { return false; }
	public boolean isCollisionDerived() { return false; }
	public boolean isCollisionRegistrationAttached() { return false; }
	public boolean isDynamicCollisionStateChanged() { return false; }
	public boolean isSchedulerStateRestored() { return false; }

	public boolean isExecutableReload() { return false; }
	public boolean isUsableRegionContainerReturned() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRuntimeSourceMutated() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }
}
