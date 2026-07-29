package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan.RequiredPackedRegion;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Detached receipt for one disposable authored source rebuilt through atomic
 * object/collision transactions with exact collision-registration provenance.
 */
public final class
	LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int sourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final int terrainTileCount;
	private final int replayPlacementCount;
	private final int authoredObjectCount;
	private final int disposableRegionConstructionCount;
	private final int supportRegionCount;
	private final int objectCollisionTransactionCount;
	private final int objectCollisionTransactionBoundaryCount;
	private final int disposableCacheInvalidationCount;
	private final int collisionRegistrationCount;
	private final int collisionRegistrationContributionCount;
	private final int collisionRegistrationRegionReferenceCount;
	private final int verifiedRegionTileCount;
	private final long blockingSceneryContributionCount;
	private final long dynamicCollisionContributionCount;
	private final long dynamicProjectileContributionCount;
	private final String terrainFingerprintSha256;
	private final String authoredReplayFingerprintSha256;
	private final String collisionFootprintFingerprintSha256;
	private final String appliedCollisionFingerprintSha256;
	private final String collisionRegistrationFingerprintSha256;
	private final String finalStateFingerprintSha256;

	private
		LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification(
			final LayeredPackedRegionBlankContainerPlan containerPlan,
			final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
			final LayeredPackedRegionAuthoredReplayPlan replayPlan,
			final LayeredPackedRegionIsolatedAuthoredObjectVerification
				membershipVerification,
			final LayeredPackedRegionAuthoredCollisionFootprintPlan
				collisionPlan,
			final int disposableRegionConstructionCount,
			final int supportRegionCount,
			final int objectCollisionTransactionCount,
			final int objectCollisionTransactionBoundaryCount,
			final int disposableCacheInvalidationCount,
			final int collisionRegistrationCount,
			final int collisionRegistrationContributionCount,
			final int collisionRegistrationRegionReferenceCount,
			final LayeredPackedRegionIsolatedAuthoredCollisionVerifier
				.PostStateVerification collisionState,
			final String collisionRegistrationFingerprintSha256,
			final String finalStateFingerprintSha256,
			final boolean blankUnionMatchedBeforeApply,
			final boolean terrainMatchedBeforeAndAfterTransactions,
			final boolean objectMembershipMatchedAfterTransactions,
			final boolean supportRegionsRemainedStaticallyBlank,
			final boolean entityFamiliesMatchedAfterTransactions,
			final boolean allCollisionRegistrationsMatched) {
		LayeredPackedRegionBlankContainerPlan container =
			Objects.requireNonNull(containerPlan, "containerPlan");
		LayeredPackedRegionTerrainInitializationPlan terrain =
			Objects.requireNonNull(terrainPlan, "terrainPlan");
		LayeredPackedRegionAuthoredReplayPlan replay =
			Objects.requireNonNull(replayPlan, "replayPlan");
		LayeredPackedRegionIsolatedAuthoredObjectVerification membership =
			Objects.requireNonNull(
				membershipVerification, "membershipVerification");
		LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
			Objects.requireNonNull(collisionPlan, "collisionPlan");
		LayeredPackedRegionIsolatedAuthoredCollisionVerifier
			.PostStateVerification state =
				Objects.requireNonNull(collisionState, "collisionState");
		String registrationFingerprint = Objects.requireNonNull(
			collisionRegistrationFingerprintSha256,
			"collisionRegistrationFingerprintSha256");
		String finalFingerprint = Objects.requireNonNull(
			finalStateFingerprintSha256, "finalStateFingerprintSha256");
		LayeredPackedRegionIsolatedAuthoredSourceStateVerifier.requireAligned(
			container, terrain, replay, membership, collision);
		int expectedRegionCount = expectedRegionCount(collision);
		if (disposableRegionConstructionCount != expectedRegionCount
			|| supportRegionCount != expectedRegionCount - 1
			|| objectCollisionTransactionCount
				!= replay.getAuthoredObjectPlacementCount()
			|| objectCollisionTransactionCount
				!= collision.getObjectFootprintCount()
			|| objectCollisionTransactionBoundaryCount
				!= collision.getRequiredRegionReferenceCount()
			|| disposableCacheInvalidationCount
				!= objectCollisionTransactionCount
			|| collisionRegistrationCount
				!= objectCollisionTransactionCount
			|| collisionRegistrationContributionCount
				!= collision.getContributionTileReferenceCount()
			|| collisionRegistrationRegionReferenceCount
				!= collision.getRequiredRegionReferenceCount()
			|| state.getVerifiedRegionTileCount()
				!= Math.multiplyExact(
					expectedRegionCount,
					Math.multiplyExact(
						Constants.REGION_SIZE, Constants.REGION_SIZE))
			|| !state.areAllTilesMatched()
			|| registrationFingerprint.length() != 64
			|| finalFingerprint.length() != 64
			|| !blankUnionMatchedBeforeApply
			|| !terrainMatchedBeforeAndAfterTransactions
			|| !objectMembershipMatchedAfterTransactions
			|| !supportRegionsRemainedStaticallyBlank
			|| !entityFamiliesMatchedAfterTransactions
			|| !allCollisionRegistrationsMatched) {
			throw new IllegalArgumentException(
				"Transactional disposable authored source is inconsistent");
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
		this.replayPlacementCount = replay.getPlacementCount();
		this.authoredObjectCount = replay.getAuthoredObjectPlacementCount();
		this.disposableRegionConstructionCount =
			disposableRegionConstructionCount;
		this.supportRegionCount = supportRegionCount;
		this.objectCollisionTransactionCount =
			objectCollisionTransactionCount;
		this.objectCollisionTransactionBoundaryCount =
			objectCollisionTransactionBoundaryCount;
		this.disposableCacheInvalidationCount =
			disposableCacheInvalidationCount;
		this.collisionRegistrationCount = collisionRegistrationCount;
		this.collisionRegistrationContributionCount =
			collisionRegistrationContributionCount;
		this.collisionRegistrationRegionReferenceCount =
			collisionRegistrationRegionReferenceCount;
		this.verifiedRegionTileCount = state.getVerifiedRegionTileCount();
		this.blockingSceneryContributionCount =
			state.getBlockingSceneryContributionCount();
		this.dynamicCollisionContributionCount =
			state.getDynamicCollisionContributionCount();
		this.dynamicProjectileContributionCount =
			state.getDynamicProjectileContributionCount();
		this.terrainFingerprintSha256 = terrain.getFingerprintSha256();
		this.authoredReplayFingerprintSha256 =
			replay.getFingerprintSha256();
		this.collisionFootprintFingerprintSha256 =
			collision.getFingerprintSha256();
		this.appliedCollisionFingerprintSha256 =
			state.getFingerprintSha256();
		this.collisionRegistrationFingerprintSha256 =
			registrationFingerprint;
		this.finalStateFingerprintSha256 = finalFingerprint;
	}

	static
		LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification
			verified(
				final LayeredPackedRegionBlankContainerPlan containerPlan,
				final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
				final LayeredPackedRegionAuthoredReplayPlan replayPlan,
				final LayeredPackedRegionIsolatedAuthoredObjectVerification
					membershipVerification,
				final LayeredPackedRegionAuthoredCollisionFootprintPlan
					collisionPlan,
				final int disposableRegionConstructionCount,
				final int supportRegionCount,
				final int objectCollisionTransactionCount,
				final int objectCollisionTransactionBoundaryCount,
				final int disposableCacheInvalidationCount,
				final int collisionRegistrationCount,
				final int collisionRegistrationContributionCount,
				final int collisionRegistrationRegionReferenceCount,
				final LayeredPackedRegionIsolatedAuthoredCollisionVerifier
					.PostStateVerification collisionState,
				final String collisionRegistrationFingerprintSha256,
				final String finalStateFingerprintSha256,
				final boolean blankUnionMatchedBeforeApply,
				final boolean terrainMatchedBeforeAndAfterTransactions,
				final boolean objectMembershipMatchedAfterTransactions,
				final boolean supportRegionsRemainedStaticallyBlank,
				final boolean entityFamiliesMatchedAfterTransactions,
				final boolean allCollisionRegistrationsMatched) {
		return new
			LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification(
				containerPlan, terrainPlan, replayPlan,
				membershipVerification, collisionPlan,
				disposableRegionConstructionCount, supportRegionCount,
				objectCollisionTransactionCount,
				objectCollisionTransactionBoundaryCount,
				disposableCacheInvalidationCount,
				collisionRegistrationCount,
				collisionRegistrationContributionCount,
				collisionRegistrationRegionReferenceCount,
				collisionState, collisionRegistrationFingerprintSha256,
				finalStateFingerprintSha256, blankUnionMatchedBeforeApply,
				terrainMatchedBeforeAndAfterTransactions,
				objectMembershipMatchedAfterTransactions,
				supportRegionsRemainedStaticallyBlank,
				entityFamiliesMatchedAfterTransactions,
				allCollisionRegistrationsMatched);
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

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getObservedAtTick() { return observedAtTick; }
	public long getResidencyMirrorVersion() {
		return residencyMirrorVersion;
	}
	public long getAuthoredGeneration() { return authoredGeneration; }
	public int getSourceOrdinal() { return sourceOrdinal; }
	public int getPackedRegionX() { return packedRegionX; }
	public int getPackedRegionY() { return packedRegionY; }
	public int getTerrainTileCount() { return terrainTileCount; }
	public int getReplayPlacementCount() { return replayPlacementCount; }
	public int getAuthoredObjectCount() { return authoredObjectCount; }
	public int getDisposableRegionConstructionCount() {
		return disposableRegionConstructionCount;
	}
	public int getSupportRegionCount() { return supportRegionCount; }
	public int getObjectCollisionTransactionCount() {
		return objectCollisionTransactionCount;
	}
	public int getObjectCollisionTransactionBoundaryCount() {
		return objectCollisionTransactionBoundaryCount;
	}
	public int getDisposableCacheInvalidationCount() {
		return disposableCacheInvalidationCount;
	}
	public int getCollisionRegistrationCount() {
		return collisionRegistrationCount;
	}
	public int getCollisionRegistrationContributionCount() {
		return collisionRegistrationContributionCount;
	}
	public int getCollisionRegistrationRegionReferenceCount() {
		return collisionRegistrationRegionReferenceCount;
	}
	public int getVerifiedRegionTileCount() {
		return verifiedRegionTileCount;
	}
	public long getBlockingSceneryContributionCount() {
		return blockingSceneryContributionCount;
	}
	public long getDynamicCollisionContributionCount() {
		return dynamicCollisionContributionCount;
	}
	public long getDynamicProjectileContributionCount() {
		return dynamicProjectileContributionCount;
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
	public String getAppliedCollisionFingerprintSha256() {
		return appliedCollisionFingerprintSha256;
	}
	public String getCollisionRegistrationFingerprintSha256() {
		return collisionRegistrationFingerprintSha256;
	}
	public String getFinalStateFingerprintSha256() {
		return finalStateFingerprintSha256;
	}

	public boolean isVerificationOnly() { return true; }
	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isBlankUnionMatchedBeforeApply() { return true; }
	public boolean isTerrainAppliedToDisposableSourceRegion() { return true; }
	public boolean isObjectCollisionTransactionAppliedToDisposableRegions() {
		return true;
	}
	public boolean isCollisionRegistrationAttachedToDisposableObjects() {
		return true;
	}
	public boolean isDisposableCacheInvalidationOnly() { return true; }
	public boolean isTerrainMatchedBeforeAndAfterTransactions() {
		return true;
	}
	public boolean isObjectMembershipMatchedAfterTransactions() {
		return true;
	}
	public boolean isSupportRegionsRemainedStaticallyBlank() { return true; }
	public boolean isEntityFamiliesMatchedAfterTransactions() { return true; }
	public boolean isAllCollisionRegistrationsMatched() { return true; }

	public boolean isUsableRegionContainerReturned() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRuntimeCollisionApplied() { return false; }
	public boolean isRuntimeSourceMutated() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isNpcMembershipApplied() { return false; }
	public boolean isGroundItemMembershipApplied() { return false; }
	public boolean isSchedulerStateRestored() { return false; }
	public boolean isActiveFamilyPreservationPerformed() { return false; }
	public boolean isRuntimeCacheInvalidated() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }
}
