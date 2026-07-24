package com.openrsc.server.model.world.region;

import java.util.Objects;

/**
 * Detached receipt proving one disposable isolated Region accepted and
 * exactly matched one static-terrain initialization plan.
 *
 * <p>The applied Region is never returned or retained. This receipt is not a
 * reload result, registration candidate, transaction token, rollback token,
 * arrival permit, or visibility permit.</p>
 */
public final class LayeredPackedRegionIsolatedTerrainVerification {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int sourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final int verifiedTileCount;
	private final int terrainBlockedTileCount;
	private final int terrainCollisionMaskTileCount;
	private final int terrainProjectileBlockedTileCount;
	private final int sealedBaseTraversalTileCount;
	private final String terrainFingerprintSha256;

	private LayeredPackedRegionIsolatedTerrainVerification(
		final LayeredPackedRegionBlankContainerPlan containerPlan,
		final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
		final boolean blankContractMatchedBeforeApply,
		final boolean allTerrainTilesMatchedAfterApply,
		final boolean dynamicProductsAbsentAfterApply,
		final boolean emptyEntityMembershipMatchedAfterApply) {
		LayeredPackedRegionBlankContainerPlan container =
			Objects.requireNonNull(containerPlan, "containerPlan");
		LayeredPackedRegionTerrainInitializationPlan terrain =
			Objects.requireNonNull(terrainPlan, "terrainPlan");
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
			|| container.getContainerTileSlotCount()
				!= terrain.getTileCount()
			|| !terrain.isDetachedTerrainDefinition()
			|| !terrain.isTerrainInputDefinitionComplete()
			|| !blankContractMatchedBeforeApply
			|| !allTerrainTilesMatchedAfterApply
			|| !dynamicProductsAbsentAfterApply
			|| !emptyEntityMembershipMatchedAfterApply) {
			throw new IllegalArgumentException(
				"Isolated Region does not exactly match its terrain plan");
		}
		this.generation = terrain.getGeneration();
		this.requirementsObservedAtTick =
			terrain.getRequirementsObservedAtTick();
		this.observedAtTick = terrain.getObservedAtTick();
		this.residencyMirrorVersion = terrain.getResidencyMirrorVersion();
		this.authoredGeneration = terrain.getAuthoredGeneration();
		this.sourceOrdinal = terrain.getSourceOrdinal();
		this.packedRegionX = terrain.getPackedRegionX();
		this.packedRegionY = terrain.getPackedRegionY();
		this.verifiedTileCount = terrain.getTileCount();
		this.terrainBlockedTileCount =
			terrain.getTerrainBlockedTileCount();
		this.terrainCollisionMaskTileCount =
			terrain.getTerrainCollisionMaskTileCount();
		this.terrainProjectileBlockedTileCount =
			terrain.getTerrainProjectileBlockedTileCount();
		this.sealedBaseTraversalTileCount =
			terrain.getSealedBaseTraversalTileCount();
		this.terrainFingerprintSha256 = terrain.getFingerprintSha256();
	}

	static LayeredPackedRegionIsolatedTerrainVerification verified(
		final LayeredPackedRegionBlankContainerPlan containerPlan,
		final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
		final boolean blankContractMatchedBeforeApply,
		final boolean allTerrainTilesMatchedAfterApply,
		final boolean dynamicProductsAbsentAfterApply,
		final boolean emptyEntityMembershipMatchedAfterApply) {
		return new LayeredPackedRegionIsolatedTerrainVerification(
			containerPlan, terrainPlan, blankContractMatchedBeforeApply,
			allTerrainTilesMatchedAfterApply,
			dynamicProductsAbsentAfterApply,
			emptyEntityMembershipMatchedAfterApply);
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
	public int getVerifiedTileCount() { return verifiedTileCount; }
	public int getTerrainBlockedTileCount() {
		return terrainBlockedTileCount;
	}
	public int getTerrainCollisionMaskTileCount() {
		return terrainCollisionMaskTileCount;
	}
	public int getTerrainProjectileBlockedTileCount() {
		return terrainProjectileBlockedTileCount;
	}
	public int getSealedBaseTraversalTileCount() {
		return sealedBaseTraversalTileCount;
	}
	public String getTerrainFingerprintSha256() {
		return terrainFingerprintSha256;
	}

	public boolean isVerificationOnly() { return true; }
	public boolean isDisposableRegionConstructed() { return true; }
	public boolean isBlankContractMatchedBeforeApply() { return true; }
	public boolean isTerrainApplyPerformedOnDisposableRegion() {
		return true;
	}
	public boolean isAllTerrainTilesMatchedAfterApply() { return true; }
	public boolean isDynamicProductsAbsentAfterApply() { return true; }
	public boolean isEmptyEntityMembershipMatchedAfterApply() {
		return true;
	}

	public boolean isExecutableReload() { return false; }
	public boolean isUsableRegionContainerReturned() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isAuthoredReplayPerformed() { return false; }
	public boolean isDynamicCollisionRebuildPerformed() { return false; }
	public boolean isActiveFamilyPreservationPerformed() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }
}
