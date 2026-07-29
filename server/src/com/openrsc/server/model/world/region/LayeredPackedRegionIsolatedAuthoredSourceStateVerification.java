package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan.RequiredPackedRegion;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Detached receipt proving terrain, authored object membership, and authored
 * collision coexist in one disposable unregistered Region union.
 *
 * <p>No Region, entity, TileValue, boundary, planner result, manager,
 * collection, or other runtime handle survives in this value.</p>
 */
public final class
	LayeredPackedRegionIsolatedAuthoredSourceStateVerification {
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
	private final int objectMembershipApplicationCount;
	private final int objectMembershipBoundaryCount;
	private final int collisionApplicationCount;
	private final int collisionBoundaryCount;
	private final int verifiedRegionTileCount;
	private final int uniqueContributionTileCount;
	private final long blockingSceneryContributionCount;
	private final long dynamicCollisionContributionCount;
	private final long dynamicProjectileContributionCount;
	private final String terrainFingerprintSha256;
	private final String authoredReplayFingerprintSha256;
	private final String collisionFootprintFingerprintSha256;
	private final String appliedCollisionFingerprintSha256;
	private final String finalStateFingerprintSha256;

	private
		LayeredPackedRegionIsolatedAuthoredSourceStateVerification(
			final LayeredPackedRegionBlankContainerPlan containerPlan,
			final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
			final LayeredPackedRegionAuthoredReplayPlan replayPlan,
			final LayeredPackedRegionIsolatedAuthoredObjectVerification
				membershipVerification,
			final LayeredPackedRegionAuthoredCollisionFootprintPlan
				collisionPlan,
			final LayeredPackedRegionIsolatedAuthoredCollisionVerifier
				.Application collisionApplication,
			final int disposableRegionConstructionCount,
			final int supportRegionCount,
			final int objectMembershipApplicationCount,
			final int objectMembershipBoundaryCount,
			final String finalStateFingerprintSha256,
			final boolean blankUnionMatchedBeforeApply,
			final boolean terrainMatchedBeforeAndAfterCollision,
			final boolean objectMembershipMatchedBeforeAndAfterCollision,
			final boolean objectCollisionCoexistedInSourceRegion,
			final boolean supportRegionsRemainedStaticallyBlank,
			final boolean entityFamiliesMatchedAfterCollision) {
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
		LayeredPackedRegionIsolatedAuthoredCollisionVerifier.Application
			application = Objects.requireNonNull(
				collisionApplication, "collisionApplication");
		String finalFingerprint = Objects.requireNonNull(
			finalStateFingerprintSha256, "finalStateFingerprintSha256");
		int expectedRegionCount = expectedRegionCount(collision);
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
			|| membership.getSourceOrdinal()
				!= replay.getSelectedSourceOrdinal()
			|| membership.getPackedRegionX() != replay.getPackedRegionX()
			|| membership.getPackedRegionY() != replay.getPackedRegionY()
			|| collision.getSourceOrdinal()
				!= replay.getSelectedSourceOrdinal()
			|| collision.getPackedRegionX() != replay.getPackedRegionX()
			|| collision.getPackedRegionY() != replay.getPackedRegionY()
			|| !membership.getTerrainFingerprintSha256().equals(
				terrain.getFingerprintSha256())
			|| !membership.getAuthoredReplayFingerprintSha256().equals(
				replay.getFingerprintSha256())
			|| !collision.getAuthoredReplayFingerprintSha256().equals(
				replay.getFingerprintSha256())
			|| replay.getAuthoredObjectPlacementCount()
				!= membership.getConstructedObjectCount()
			|| replay.getAuthoredObjectPlacementCount()
				!= collision.getObjectFootprintCount()
			|| disposableRegionConstructionCount != expectedRegionCount
			|| supportRegionCount != expectedRegionCount - 1
			|| objectMembershipApplicationCount
				!= replay.getAuthoredObjectPlacementCount()
			|| objectMembershipBoundaryCount
				!= objectMembershipApplicationCount
			|| application.getCollisionApplicationCount()
				!= collision.getObjectFootprintCount()
			|| application.getHeldBoundaryCount()
				!= collision.getRequiredRegionReferenceCount()
			|| application.getVerifiedRegionTileCount()
				!= Math.multiplyExact(
					expectedRegionCount,
					Math.multiplyExact(
						Constants.REGION_SIZE, Constants.REGION_SIZE))
			|| finalFingerprint.length() != 64
			|| !membership.isVerificationOnly()
			|| !membership.isExactObjectMembershipMatchedAfterReplay()
			|| !collision.isPointInTimeOnly()
			|| !collision.isDetachedCollisionDefinition()
			|| collision.isCollisionApplied()
			|| !application.isBlankDynamicProductsMatchedBeforeApply()
			|| !application.isAllPlannerResultsRecreatedExactly()
			|| !application.isAllCollisionApplicationsSucceeded()
			|| !application.isAllAppliedTilesMatched()
			|| !blankUnionMatchedBeforeApply
			|| !terrainMatchedBeforeAndAfterCollision
			|| !objectMembershipMatchedBeforeAndAfterCollision
			|| !objectCollisionCoexistedInSourceRegion
			|| !supportRegionsRemainedStaticallyBlank
			|| !entityFamiliesMatchedAfterCollision) {
			throw new IllegalArgumentException(
				"Combined disposable authored source state is inconsistent");
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
		this.objectMembershipApplicationCount =
			objectMembershipApplicationCount;
		this.objectMembershipBoundaryCount = objectMembershipBoundaryCount;
		this.collisionApplicationCount =
			application.getCollisionApplicationCount();
		this.collisionBoundaryCount = application.getHeldBoundaryCount();
		this.verifiedRegionTileCount =
			application.getVerifiedRegionTileCount();
		this.uniqueContributionTileCount =
			application.getUniqueContributionTileCount();
		this.blockingSceneryContributionCount =
			application.getBlockingSceneryContributionCount();
		this.dynamicCollisionContributionCount =
			application.getDynamicCollisionContributionCount();
		this.dynamicProjectileContributionCount =
			application.getDynamicProjectileContributionCount();
		this.terrainFingerprintSha256 = terrain.getFingerprintSha256();
		this.authoredReplayFingerprintSha256 =
			replay.getFingerprintSha256();
		this.collisionFootprintFingerprintSha256 =
			collision.getFingerprintSha256();
		this.appliedCollisionFingerprintSha256 =
			application.getAppliedCollisionFingerprintSha256();
		this.finalStateFingerprintSha256 = finalFingerprint;
	}

	static LayeredPackedRegionIsolatedAuthoredSourceStateVerification verified(
		final LayeredPackedRegionBlankContainerPlan containerPlan,
		final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
		final LayeredPackedRegionAuthoredReplayPlan replayPlan,
		final LayeredPackedRegionIsolatedAuthoredObjectVerification
			membershipVerification,
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan,
		final LayeredPackedRegionIsolatedAuthoredCollisionVerifier.Application
			collisionApplication,
		final int disposableRegionConstructionCount,
		final int supportRegionCount,
		final int objectMembershipApplicationCount,
		final int objectMembershipBoundaryCount,
		final String finalStateFingerprintSha256,
		final boolean blankUnionMatchedBeforeApply,
		final boolean terrainMatchedBeforeAndAfterCollision,
		final boolean objectMembershipMatchedBeforeAndAfterCollision,
		final boolean objectCollisionCoexistedInSourceRegion,
		final boolean supportRegionsRemainedStaticallyBlank,
		final boolean entityFamiliesMatchedAfterCollision) {
		return new
			LayeredPackedRegionIsolatedAuthoredSourceStateVerification(
				containerPlan, terrainPlan, replayPlan,
				membershipVerification, collisionPlan,
				collisionApplication, disposableRegionConstructionCount,
				supportRegionCount, objectMembershipApplicationCount,
				objectMembershipBoundaryCount, finalStateFingerprintSha256,
				blankUnionMatchedBeforeApply,
				terrainMatchedBeforeAndAfterCollision,
				objectMembershipMatchedBeforeAndAfterCollision,
				objectCollisionCoexistedInSourceRegion,
				supportRegionsRemainedStaticallyBlank,
				entityFamiliesMatchedAfterCollision);
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
	public int getObjectMembershipApplicationCount() {
		return objectMembershipApplicationCount;
	}
	public int getObjectMembershipBoundaryCount() {
		return objectMembershipBoundaryCount;
	}
	public int getCollisionApplicationCount() {
		return collisionApplicationCount;
	}
	public int getCollisionBoundaryCount() {
		return collisionBoundaryCount;
	}
	public int getVerifiedRegionTileCount() {
		return verifiedRegionTileCount;
	}
	public int getUniqueContributionTileCount() {
		return uniqueContributionTileCount;
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
	public String getFinalStateFingerprintSha256() {
		return finalStateFingerprintSha256;
	}

	public boolean isVerificationOnly() { return true; }
	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isBlankUnionMatchedBeforeApply() { return true; }
	public boolean isTerrainAppliedToDisposableSourceRegion() {
		return true;
	}
	public boolean isAuthoredObjectMembershipAppliedToDisposableSourceRegion() {
		return true;
	}
	public boolean isCollisionAppliedToSameDisposableRegionUnion() {
		return true;
	}
	public boolean isTerrainMatchedBeforeAndAfterCollision() { return true; }
	public boolean isObjectMembershipMatchedBeforeAndAfterCollision() {
		return true;
	}
	public boolean isObjectCollisionCoexistedInSourceRegion() {
		return true;
	}
	public boolean isSupportRegionsRemainedStaticallyBlank() {
		return true;
	}
	public boolean isEntityFamiliesMatchedAfterCollision() { return true; }

	public boolean isCollisionRegistrationAttached() { return false; }
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
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }
}
