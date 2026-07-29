package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan
		.AuthoredObjectCollisionFootprint;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan.Contribution;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Detached receipt for exact authored collision application on disposable,
 * unregistered Regions.
 *
 * <p>No Region, TileValue, boundary, planner result, manager, entity, or other
 * runtime handle survives in this value.</p>
 */
public final class
	LayeredPackedRegionIsolatedAuthoredCollisionVerification {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int sourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final int authoredObjectFootprintCount;
	private final int zeroContributionObjectCount;
	private final int contributionTileReferenceCount;
	private final int uniqueContributionTileCount;
	private final int requiredRegionReferenceCount;
	private final int uniqueRequiredRegionCount;
	private final int disposableRegionConstructionCount;
	private final int collisionApplicationCount;
	private final int heldBoundaryCount;
	private final int verifiedRegionTileCount;
	private final long blockingSceneryContributionCount;
	private final long dynamicCollisionContributionCount;
	private final long dynamicProjectileContributionCount;
	private final String collisionFootprintFingerprintSha256;
	private final String appliedCollisionFingerprintSha256;

	private LayeredPackedRegionIsolatedAuthoredCollisionVerification(
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan,
		final int disposableRegionConstructionCount,
		final int collisionApplicationCount,
		final int heldBoundaryCount,
		final int verifiedRegionTileCount,
		final int uniqueContributionTileCount,
		final long blockingSceneryContributionCount,
		final long dynamicCollisionContributionCount,
		final long dynamicProjectileContributionCount,
		final String appliedCollisionFingerprintSha256,
		final boolean blankDynamicProductsMatchedBeforeApply,
		final boolean allPlannerResultsRecreatedExactly,
		final boolean allCollisionApplicationsSucceeded,
		final boolean allAppliedTilesMatched,
		final boolean allEntityMembershipRemainedEmpty) {
		LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
			Objects.requireNonNull(collisionPlan, "collisionPlan");
		Expected expected = Expected.from(collision);
		String appliedFingerprint = Objects.requireNonNull(
			appliedCollisionFingerprintSha256,
			"appliedCollisionFingerprintSha256");
		int expectedRegionCount =
			Math.max(1, collision.getUniqueRequiredRegionCount());
		if (!collision.isPointInTimeOnly()
			|| !collision.isDetachedCollisionDefinition()
			|| !collision.isRegisterFootprintDerived()
			|| collision.isCollisionApplied()
			|| collision.isRuntimeSourceMutated()
			|| disposableRegionConstructionCount != expectedRegionCount
			|| collisionApplicationCount
				!= collision.getObjectFootprintCount()
			|| heldBoundaryCount
				!= collision.getRequiredRegionReferenceCount()
			|| verifiedRegionTileCount
				!= Math.multiplyExact(
					expectedRegionCount,
					Math.multiplyExact(
						Constants.REGION_SIZE, Constants.REGION_SIZE))
			|| uniqueContributionTileCount
				!= expected.uniqueContributionTileCount
			|| blockingSceneryContributionCount
				!= expected.blockingSceneryContributionCount
			|| dynamicCollisionContributionCount
				!= expected.dynamicCollisionContributionCount
			|| dynamicProjectileContributionCount
				!= expected.dynamicProjectileContributionCount
			|| appliedFingerprint.length() != 64
			|| !blankDynamicProductsMatchedBeforeApply
			|| !allPlannerResultsRecreatedExactly
			|| !allCollisionApplicationsSucceeded
			|| !allAppliedTilesMatched
			|| !allEntityMembershipRemainedEmpty) {
			throw new IllegalArgumentException(
				"Isolated authored collision application is inconsistent");
		}
		this.generation = collision.getGeneration();
		this.requirementsObservedAtTick =
			collision.getRequirementsObservedAtTick();
		this.observedAtTick = collision.getObservedAtTick();
		this.residencyMirrorVersion =
			collision.getResidencyMirrorVersion();
		this.authoredGeneration = collision.getAuthoredGeneration();
		this.sourceOrdinal = collision.getSourceOrdinal();
		this.packedRegionX = collision.getPackedRegionX();
		this.packedRegionY = collision.getPackedRegionY();
		this.authoredObjectFootprintCount =
			collision.getObjectFootprintCount();
		this.zeroContributionObjectCount =
			collision.getZeroContributionObjectCount();
		this.contributionTileReferenceCount =
			collision.getContributionTileReferenceCount();
		this.uniqueContributionTileCount = uniqueContributionTileCount;
		this.requiredRegionReferenceCount =
			collision.getRequiredRegionReferenceCount();
		this.uniqueRequiredRegionCount =
			collision.getUniqueRequiredRegionCount();
		this.disposableRegionConstructionCount =
			disposableRegionConstructionCount;
		this.collisionApplicationCount = collisionApplicationCount;
		this.heldBoundaryCount = heldBoundaryCount;
		this.verifiedRegionTileCount = verifiedRegionTileCount;
		this.blockingSceneryContributionCount =
			blockingSceneryContributionCount;
		this.dynamicCollisionContributionCount =
			dynamicCollisionContributionCount;
		this.dynamicProjectileContributionCount =
			dynamicProjectileContributionCount;
		this.collisionFootprintFingerprintSha256 =
			collision.getFingerprintSha256();
		this.appliedCollisionFingerprintSha256 = appliedFingerprint;
	}

	static LayeredPackedRegionIsolatedAuthoredCollisionVerification verified(
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan,
		final int disposableRegionConstructionCount,
		final int collisionApplicationCount,
		final int heldBoundaryCount,
		final int verifiedRegionTileCount,
		final int uniqueContributionTileCount,
		final long blockingSceneryContributionCount,
		final long dynamicCollisionContributionCount,
		final long dynamicProjectileContributionCount,
		final String appliedCollisionFingerprintSha256,
		final boolean blankDynamicProductsMatchedBeforeApply,
		final boolean allPlannerResultsRecreatedExactly,
		final boolean allCollisionApplicationsSucceeded,
		final boolean allAppliedTilesMatched,
		final boolean allEntityMembershipRemainedEmpty) {
		return new
			LayeredPackedRegionIsolatedAuthoredCollisionVerification(
				collisionPlan, disposableRegionConstructionCount,
				collisionApplicationCount, heldBoundaryCount,
				verifiedRegionTileCount, uniqueContributionTileCount,
				blockingSceneryContributionCount,
				dynamicCollisionContributionCount,
				dynamicProjectileContributionCount,
				appliedCollisionFingerprintSha256,
				blankDynamicProductsMatchedBeforeApply,
				allPlannerResultsRecreatedExactly,
				allCollisionApplicationsSucceeded,
				allAppliedTilesMatched,
				allEntityMembershipRemainedEmpty);
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
	public int getAuthoredObjectFootprintCount() {
		return authoredObjectFootprintCount;
	}
	public int getZeroContributionObjectCount() {
		return zeroContributionObjectCount;
	}
	public int getContributionTileReferenceCount() {
		return contributionTileReferenceCount;
	}
	public int getUniqueContributionTileCount() {
		return uniqueContributionTileCount;
	}
	public int getRequiredRegionReferenceCount() {
		return requiredRegionReferenceCount;
	}
	public int getUniqueRequiredRegionCount() {
		return uniqueRequiredRegionCount;
	}
	public int getDisposableRegionConstructionCount() {
		return disposableRegionConstructionCount;
	}
	public int getCollisionApplicationCount() {
		return collisionApplicationCount;
	}
	public int getHeldBoundaryCount() { return heldBoundaryCount; }
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
	public String getCollisionFootprintFingerprintSha256() {
		return collisionFootprintFingerprintSha256;
	}
	public String getAppliedCollisionFingerprintSha256() {
		return appliedCollisionFingerprintSha256;
	}

	public boolean isVerificationOnly() { return true; }
	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isDisposableRegionConstructionPerformed() {
		return true;
	}
	public boolean isBlankDynamicProductsMatchedBeforeApply() {
		return true;
	}
	public boolean isAllPlannerResultsRecreatedExactly() { return true; }
	public boolean isCollisionAppliedToDisposableRegions() { return true; }
	public boolean isAllCollisionApplicationsSucceeded() { return true; }
	public boolean isAllAppliedTilesMatched() { return true; }
	public boolean isAllEntityMembershipRemainedEmpty() { return true; }

	public boolean isAuthoredObjectMembershipApplied() { return false; }
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
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	private static final class Expected {
		private final int uniqueContributionTileCount;
		private final long blockingSceneryContributionCount;
		private final long dynamicCollisionContributionCount;
		private final long dynamicProjectileContributionCount;

		private Expected(
			final int uniqueContributionTileCount,
			final long blockingSceneryContributionCount,
			final long dynamicCollisionContributionCount,
			final long dynamicProjectileContributionCount) {
			this.uniqueContributionTileCount = uniqueContributionTileCount;
			this.blockingSceneryContributionCount =
				blockingSceneryContributionCount;
			this.dynamicCollisionContributionCount =
				dynamicCollisionContributionCount;
			this.dynamicProjectileContributionCount =
				dynamicProjectileContributionCount;
		}

		private static Expected from(
			final LayeredPackedRegionAuthoredCollisionFootprintPlan collision) {
			Set<Long> uniqueTiles = new HashSet<Long>();
			long blocking = 0L;
			long dynamic = 0L;
			long projectile = 0L;
			for (AuthoredObjectCollisionFootprint footprint
					: collision.getFootprints()) {
				for (Contribution contribution
						: footprint.getContributions()) {
					uniqueTiles.add(tileKey(
						contribution.getPackedX(),
						contribution.getPackedY()));
					blocking = Math.addExact(
						blocking,
						(long) contribution.getBlockingSceneryCount());
					for (int bit = 0; bit < 6; bit++) {
						dynamic = Math.addExact(
							dynamic,
							(long) contribution
								.getDynamicCollisionCount(bit));
					}
					projectile = Math.addExact(
						projectile,
						(long) contribution.getDynamicProjectileCount());
				}
			}
			return new Expected(
				uniqueTiles.size(), blocking, dynamic, projectile);
		}
	}

	private static long tileKey(final int x, final int y) {
		return ((long) x << 32) ^ (y & 0xffffffffL);
	}
}
