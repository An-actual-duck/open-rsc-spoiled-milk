package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan.RequiredPackedRegion;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Detached receipt for an authored object/collision round trip performed only
 * inside one disposable unregistered Region union.
 */
public final class
	LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long runtimeObservedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int sourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final int authoredObjectCount;
	private final int disposableRegionConstructionCount;
	private final int supportRegionCount;
	private final int reconstructionTransactionCount;
	private final int reconstructionBoundaryCount;
	private final int reconstructionCacheInvalidationCount;
	private final int detachmentTransactionCount;
	private final int detachmentBoundaryCount;
	private final int detachmentCacheInvalidationCount;
	private final int collisionRegistrationCount;
	private final int collisionRegistrationClearedCount;
	private final int collisionContributionReferenceCount;
	private final int collisionRegionReferenceCount;
	private final int verifiedRegionTileCount;
	private final String terrainFingerprintSha256;
	private final String authoredReplayFingerprintSha256;
	private final String collisionFootprintFingerprintSha256;
	private final String detachmentPlanFingerprintSha256;
	private final String preDetachmentRegistrationFingerprintSha256;
	private final String preDetachmentStateFingerprintSha256;
	private final String postDetachmentStateFingerprintSha256;

	private
		LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification(
			final LayeredPackedRegionBlankContainerPlan containerPlan,
			final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
			final LayeredPackedRegionAuthoredReplayPlan replayPlan,
			final LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan,
			final LayeredPackedRegionAuthoredObjectDetachmentPlan detachmentPlan,
			final int sourceOrdinal,
			final int disposableRegionConstructionCount,
			final int supportRegionCount,
			final int reconstructionTransactionCount,
			final int reconstructionBoundaryCount,
			final int reconstructionCacheInvalidationCount,
			final int detachmentTransactionCount,
			final int detachmentBoundaryCount,
			final int detachmentCacheInvalidationCount,
			final int collisionRegistrationCount,
			final int collisionRegistrationClearedCount,
			final int verifiedRegionTileCount,
			final String preDetachmentRegistrationFingerprintSha256,
			final String preDetachmentStateFingerprintSha256,
			final String postDetachmentStateFingerprintSha256,
			final boolean exactRegistrationSequenceMatchedBeforeDetachment,
			final boolean reverseDetachmentOrderMatched,
			final boolean allRegistrationsClearedAfterDetachment,
			final boolean terrainMatchedAfterDetachment,
			final boolean collisionProductsClearedAfterDetachment,
			final boolean objectMembershipEmptyAfterDetachment,
			final boolean supportRegionsRemainedStaticallyBlank,
			final boolean entityFamiliesEmptyAfterDetachment) {
		LayeredPackedRegionBlankContainerPlan container =
			Objects.requireNonNull(containerPlan, "containerPlan");
		LayeredPackedRegionTerrainInitializationPlan terrain =
			Objects.requireNonNull(terrainPlan, "terrainPlan");
		LayeredPackedRegionAuthoredReplayPlan replay =
			Objects.requireNonNull(replayPlan, "replayPlan");
		LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
			Objects.requireNonNull(collisionPlan, "collisionPlan");
		LayeredPackedRegionAuthoredObjectDetachmentPlan detachment =
			Objects.requireNonNull(detachmentPlan, "detachmentPlan");
		if (sourceOrdinal < 0
			|| sourceOrdinal >= detachment.getSourceCount()) {
			throw new IllegalArgumentException(
				"Disposable detachment source ordinal is invalid");
		}
		LayeredPackedRegionAuthoredObjectDetachmentPlan.SourcePlan source =
			detachment.getSources().get(sourceOrdinal);
		int objectCount = replay.getAuthoredObjectPlacementCount();
		int expectedRegions = expectedRegionCount(collision);
		int expectedTiles = Math.multiplyExact(
			expectedRegions,
			Math.multiplyExact(Constants.REGION_SIZE, Constants.REGION_SIZE));
		if (container.getGeneration() != detachment.getGeneration()
			|| terrain.getGeneration() != detachment.getGeneration()
			|| replay.getGeneration() != detachment.getGeneration()
			|| collision.getGeneration() != detachment.getGeneration()
			|| container.getRequirementsObservedAtTick()
				!= detachment.getRequirementsObservedAtTick()
			|| terrain.getObservedAtTick()
				!= detachment.getRecipeObservedAtTick()
			|| replay.getObservedAtTick()
				!= detachment.getRecipeObservedAtTick()
			|| collision.getObservedAtTick()
				!= detachment.getRecipeObservedAtTick()
			|| container.getResidencyMirrorVersion()
				!= detachment.getResidencyMirrorVersion()
			|| replay.getAuthoredGeneration()
				!= detachment.getAuthoredGeneration()
			|| source.getSelectedSourceOrdinal() != sourceOrdinal
			|| container.getSourceOrdinal() != sourceOrdinal
			|| terrain.getSourceOrdinal() != sourceOrdinal
			|| replay.getSelectedSourceOrdinal() != sourceOrdinal
			|| collision.getSourceOrdinal() != sourceOrdinal
			|| source.getPackedRegionX() != container.getPackedRegionX()
			|| source.getPackedRegionY() != container.getPackedRegionY()
			|| objectCount != source.getObjectCount()
			|| objectCount != collision.getObjectFootprintCount()
			|| disposableRegionConstructionCount != expectedRegions
			|| supportRegionCount != expectedRegions - 1
			|| reconstructionTransactionCount != objectCount
			|| reconstructionBoundaryCount
				!= collision.getRequiredRegionReferenceCount()
			|| reconstructionCacheInvalidationCount != objectCount
			|| detachmentTransactionCount != objectCount
			|| detachmentBoundaryCount
				!= collision.getRequiredRegionReferenceCount()
			|| detachmentCacheInvalidationCount != objectCount
			|| collisionRegistrationCount != objectCount
			|| collisionRegistrationClearedCount != objectCount
			|| verifiedRegionTileCount != expectedTiles
			|| !fingerprint(preDetachmentRegistrationFingerprintSha256)
			|| !fingerprint(preDetachmentStateFingerprintSha256)
			|| !fingerprint(postDetachmentStateFingerprintSha256)
			|| !exactRegistrationSequenceMatchedBeforeDetachment
			|| !reverseDetachmentOrderMatched
			|| !allRegistrationsClearedAfterDetachment
			|| !terrainMatchedAfterDetachment
			|| !collisionProductsClearedAfterDetachment
			|| !objectMembershipEmptyAfterDetachment
			|| !supportRegionsRemainedStaticallyBlank
			|| !entityFamiliesEmptyAfterDetachment) {
			throw new IllegalArgumentException(
				"Disposable authored-object detachment receipt is incomplete");
		}
		this.generation = detachment.getGeneration();
		this.requirementsObservedAtTick =
			detachment.getRequirementsObservedAtTick();
		this.observedAtTick = detachment.getRecipeObservedAtTick();
		this.runtimeObservedAtTick = detachment.getRuntimeObservedAtTick();
		this.residencyMirrorVersion =
			detachment.getResidencyMirrorVersion();
		this.authoredGeneration = detachment.getAuthoredGeneration();
		this.sourceOrdinal = sourceOrdinal;
		this.packedRegionX = source.getPackedRegionX();
		this.packedRegionY = source.getPackedRegionY();
		this.authoredObjectCount = objectCount;
		this.disposableRegionConstructionCount =
			disposableRegionConstructionCount;
		this.supportRegionCount = supportRegionCount;
		this.reconstructionTransactionCount = reconstructionTransactionCount;
		this.reconstructionBoundaryCount = reconstructionBoundaryCount;
		this.reconstructionCacheInvalidationCount =
			reconstructionCacheInvalidationCount;
		this.detachmentTransactionCount = detachmentTransactionCount;
		this.detachmentBoundaryCount = detachmentBoundaryCount;
		this.detachmentCacheInvalidationCount =
			detachmentCacheInvalidationCount;
		this.collisionRegistrationCount = collisionRegistrationCount;
		this.collisionRegistrationClearedCount =
			collisionRegistrationClearedCount;
		this.collisionContributionReferenceCount =
			collision.getContributionTileReferenceCount();
		this.collisionRegionReferenceCount =
			collision.getRequiredRegionReferenceCount();
		this.verifiedRegionTileCount = verifiedRegionTileCount;
		this.terrainFingerprintSha256 = terrain.getFingerprintSha256();
		this.authoredReplayFingerprintSha256 = replay.getFingerprintSha256();
		this.collisionFootprintFingerprintSha256 =
			collision.getFingerprintSha256();
		this.detachmentPlanFingerprintSha256 =
			source.getFingerprintSha256();
		this.preDetachmentRegistrationFingerprintSha256 =
			preDetachmentRegistrationFingerprintSha256;
		this.preDetachmentStateFingerprintSha256 =
			preDetachmentStateFingerprintSha256;
		this.postDetachmentStateFingerprintSha256 =
			postDetachmentStateFingerprintSha256;
	}

	static
		LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification verified(
			final LayeredPackedRegionBlankContainerPlan containerPlan,
			final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
			final LayeredPackedRegionAuthoredReplayPlan replayPlan,
			final LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan,
			final LayeredPackedRegionAuthoredObjectDetachmentPlan detachmentPlan,
			final int sourceOrdinal,
			final int disposableRegionConstructionCount,
			final int supportRegionCount,
			final int reconstructionTransactionCount,
			final int reconstructionBoundaryCount,
			final int reconstructionCacheInvalidationCount,
			final int detachmentTransactionCount,
			final int detachmentBoundaryCount,
			final int detachmentCacheInvalidationCount,
			final int collisionRegistrationCount,
			final int collisionRegistrationClearedCount,
			final int verifiedRegionTileCount,
			final String preDetachmentRegistrationFingerprintSha256,
			final String preDetachmentStateFingerprintSha256,
			final String postDetachmentStateFingerprintSha256,
			final boolean exactRegistrationSequenceMatchedBeforeDetachment,
			final boolean reverseDetachmentOrderMatched,
			final boolean allRegistrationsClearedAfterDetachment,
			final boolean terrainMatchedAfterDetachment,
			final boolean collisionProductsClearedAfterDetachment,
			final boolean objectMembershipEmptyAfterDetachment,
			final boolean supportRegionsRemainedStaticallyBlank,
			final boolean entityFamiliesEmptyAfterDetachment) {
		return new
			LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification(
				containerPlan, terrainPlan, replayPlan, collisionPlan,
				detachmentPlan, sourceOrdinal,
				disposableRegionConstructionCount, supportRegionCount,
				reconstructionTransactionCount, reconstructionBoundaryCount,
				reconstructionCacheInvalidationCount,
				detachmentTransactionCount, detachmentBoundaryCount,
				detachmentCacheInvalidationCount, collisionRegistrationCount,
				collisionRegistrationClearedCount, verifiedRegionTileCount,
				preDetachmentRegistrationFingerprintSha256,
				preDetachmentStateFingerprintSha256,
				postDetachmentStateFingerprintSha256,
				exactRegistrationSequenceMatchedBeforeDetachment,
				reverseDetachmentOrderMatched,
				allRegistrationsClearedAfterDetachment,
				terrainMatchedAfterDetachment,
				collisionProductsClearedAfterDetachment,
				objectMembershipEmptyAfterDetachment,
				supportRegionsRemainedStaticallyBlank,
				entityFamiliesEmptyAfterDetachment);
	}

	private static int expectedRegionCount(
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collision) {
		Set<Long> regions = new HashSet<Long>();
		regions.add(regionKey(
			collision.getPackedRegionX(), collision.getPackedRegionY()));
		for (RequiredPackedRegion required : collision.getRequiredRegions()) {
			regions.add(regionKey(
				required.getPackedRegionX(), required.getPackedRegionY()));
		}
		return regions.size();
	}

	private static long regionKey(final int x, final int y) {
		return ((long) x << 32) ^ (y & 0xffffffffL);
	}

	private static boolean fingerprint(final String value) {
		return value != null && value.length() == 64;
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getObservedAtTick() { return observedAtTick; }
	public long getRuntimeObservedAtTick() { return runtimeObservedAtTick; }
	public long getResidencyMirrorVersion() {
		return residencyMirrorVersion;
	}
	public long getAuthoredGeneration() { return authoredGeneration; }
	public int getSourceOrdinal() { return sourceOrdinal; }
	public int getPackedRegionX() { return packedRegionX; }
	public int getPackedRegionY() { return packedRegionY; }
	public int getAuthoredObjectCount() { return authoredObjectCount; }
	public int getDisposableRegionConstructionCount() {
		return disposableRegionConstructionCount;
	}
	public int getSupportRegionCount() { return supportRegionCount; }
	public int getReconstructionTransactionCount() {
		return reconstructionTransactionCount;
	}
	public int getReconstructionBoundaryCount() {
		return reconstructionBoundaryCount;
	}
	public int getReconstructionCacheInvalidationCount() {
		return reconstructionCacheInvalidationCount;
	}
	public int getDetachmentTransactionCount() {
		return detachmentTransactionCount;
	}
	public int getDetachmentBoundaryCount() {
		return detachmentBoundaryCount;
	}
	public int getDetachmentCacheInvalidationCount() {
		return detachmentCacheInvalidationCount;
	}
	public int getCollisionRegistrationCount() {
		return collisionRegistrationCount;
	}
	public int getCollisionRegistrationClearedCount() {
		return collisionRegistrationClearedCount;
	}
	public int getCollisionContributionReferenceCount() {
		return collisionContributionReferenceCount;
	}
	public int getCollisionRegionReferenceCount() {
		return collisionRegionReferenceCount;
	}
	public int getVerifiedRegionTileCount() {
		return verifiedRegionTileCount;
	}
	public String getTerrainFingerprintSha256() {
		return terrainFingerprintSha256;
	}
	public String getAuthoredReplayFingerprintSha256() {
		return authoredReplayFingerprintSha256;
	}
	public String getCollisionFootprintFingerprintSha256() {
		return collisionFootprintFingerprintSha256;
	}
	public String getDetachmentPlanFingerprintSha256() {
		return detachmentPlanFingerprintSha256;
	}
	public String getPreDetachmentRegistrationFingerprintSha256() {
		return preDetachmentRegistrationFingerprintSha256;
	}
	public String getPreDetachmentStateFingerprintSha256() {
		return preDetachmentStateFingerprintSha256;
	}
	public String getPostDetachmentStateFingerprintSha256() {
		return postDetachmentStateFingerprintSha256;
	}

	public boolean isVerificationOnly() { return true; }
	public boolean isDisposableReconstructionPerformed() { return true; }
	public boolean isDisposableDetachmentPerformed() { return true; }
	public boolean isExactRegistrationSequenceMatchedBeforeDetachment() {
		return true;
	}
	public boolean isReverseDetachmentOrderMatched() { return true; }
	public boolean isAllRegistrationsClearedAfterDetachment() { return true; }
	public boolean isTerrainMatchedAfterDetachment() { return true; }
	public boolean isCollisionProductsClearedAfterDetachment() { return true; }
	public boolean isObjectMembershipEmptyAfterDetachment() { return true; }
	public boolean isSupportRegionsRemainedStaticallyBlank() { return true; }
	public boolean isEntityFamiliesEmptyAfterDetachment() { return true; }

	public boolean isUsableRegionContainerReturned() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRuntimeSourceMutated() { return false; }
	public boolean isRuntimeCollisionMutated() { return false; }
	public boolean isRuntimeCacheInvalidated() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isSchedulerCorrelationPerformed() { return false; }
	public boolean isActiveFamilyPreservationPerformed() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }
}
