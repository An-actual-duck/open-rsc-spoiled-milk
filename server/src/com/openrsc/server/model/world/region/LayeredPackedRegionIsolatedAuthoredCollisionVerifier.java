package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.event.rsc
	.GameTickEventRestorationCollisionFootprintPlanner.Result;
import com.openrsc.server.event.rsc
	.GameTickEventRestorationCollisionTransactionContract;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan
		.AuthoredObjectCollisionFootprint;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan.Contribution;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan.RequiredPackedRegion;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Replays and applies one source's exact authored collision footprints to a
 * disposable, unregistered Region union, then returns only detached evidence.
 */
final class LayeredPackedRegionIsolatedAuthoredCollisionVerifier {
	private LayeredPackedRegionIsolatedAuthoredCollisionVerifier() { }

	static LayeredPackedRegionIsolatedAuthoredCollisionVerification verify(
		final RegionManager regionManager,
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan) {
		final RegionManager manager =
			Objects.requireNonNull(regionManager, "regionManager");
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
			Objects.requireNonNull(collisionPlan, "collisionPlan");
		final Map<Long, Region> disposable = constructDisposableRegions(
			manager, collision);
		Application application = applyToDisposableRegions(
			disposable, collision);
		boolean entityMembershipRemainedEmpty =
			verifyEntityMembershipEmpty(disposable);
		return
			LayeredPackedRegionIsolatedAuthoredCollisionVerification
				.verified(
					collision, disposable.size(),
					application.getCollisionApplicationCount(),
					application.getHeldBoundaryCount(),
					application.getVerifiedRegionTileCount(),
					application.getUniqueContributionTileCount(),
					application.getBlockingSceneryContributionCount(),
					application.getDynamicCollisionContributionCount(),
					application.getDynamicProjectileContributionCount(),
					application.getAppliedCollisionFingerprintSha256(),
					application
						.isBlankDynamicProductsMatchedBeforeApply(),
					application.isAllPlannerResultsRecreatedExactly(),
					application.isAllCollisionApplicationsSucceeded(),
					application.isAllAppliedTilesMatched(),
					entityMembershipRemainedEmpty);
	}

	static Application applyToDisposableRegions(
		final Map<Long, Region> disposableRegions,
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan) {
		final Map<Long, Region> disposable =
			Objects.requireNonNull(disposableRegions, "disposableRegions");
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
			Objects.requireNonNull(collisionPlan, "collisionPlan");
		if (disposable.isEmpty()
			|| !disposable.containsKey(regionKey(
				collision.getPackedRegionX(), collision.getPackedRegionY()))) {
			throw new IllegalArgumentException(
				"Disposable collision Region union lacks its source");
		}
		for (RequiredPackedRegion required : collision.getRequiredRegions()) {
			if (!disposable.containsKey(regionKey(
					required.getPackedRegionX(),
					required.getPackedRegionY()))) {
				throw new IllegalArgumentException(
					"Disposable collision Region union lacks required reach");
			}
		}
		boolean blankDynamicProductsMatchedBeforeApply =
			verifyBlankDynamicProducts(disposable);
		if (!blankDynamicProductsMatchedBeforeApply) {
			throw new IllegalStateException(
				"Disposable collision Regions were not dynamically blank");
		}

		final Map<Long, ExpectedTile> expected =
			new TreeMap<Long, ExpectedTile>();
		int collisionApplicationCount = 0;
		int heldBoundaryCount = 0;
		boolean allPlannerResultsRecreatedExactly = true;
		boolean allCollisionApplicationsSucceeded = true;
		for (AuthoredObjectCollisionFootprint footprint
				: collision.getFootprints()) {
			addExpected(expected, footprint);
			Result recreated = footprint.recreateVerifiedPlannerResult(
				Constants.MAX_WIDTH, Constants.MAX_HEIGHT);
			allPlannerResultsRecreatedExactly &=
				recreated.getContributionTileCount()
					== footprint.getContributionTileCount()
					&& recreated.getRequiredRegionCount()
						== footprint.getRequiredRegionCount();
			List<RegionObjectCollisionMutationBoundary> boundaries =
				boundaries(disposable, footprint);
			RegionCollisionFootprintMutationExecutor.Result applied =
				RegionCollisionFootprintMutationExecutor.execute(
					boundaries, recreated,
					new RegionCollisionFootprintMutationExecutor
						.MutableTileAccess() {
						@Override
						public TileValue getMutableTile(
							final int x,
							final int y) {
							return mutableTile(disposable, x, y);
						}
					});
			allCollisionApplicationsSucceeded &=
				applied.isApplied()
					&& applied.isMutationAuthorized()
					&& applied.isMutationPerformed()
					&& !applied.isRollbackAuthorized()
					&& !applied.isRollbackPerformed()
					&& applied.getBoundaryCount()
						== footprint.getRequiredRegionCount();
			if (!allCollisionApplicationsSucceeded) {
				throw new IllegalStateException(
					"Disposable authored collision application refused");
			}
			collisionApplicationCount =
				Math.incrementExact(collisionApplicationCount);
			heldBoundaryCount = Math.addExact(
				heldBoundaryCount, applied.getBoundaryCount());
		}

		PostStateVerification verification =
			verifyApplied(disposable, expected);
		return new Application(
			collisionApplicationCount, heldBoundaryCount,
			verification.verifiedRegionTileCount, expected.size(),
			verification.blockingSceneryContributionCount,
			verification.dynamicCollisionContributionCount,
			verification.dynamicProjectileContributionCount,
			verification.fingerprintSha256,
			blankDynamicProductsMatchedBeforeApply,
			allPlannerResultsRecreatedExactly,
			allCollisionApplicationsSucceeded,
			verification.allTilesMatched);
	}

	private static Map<Long, Region> constructDisposableRegions(
		final RegionManager manager,
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collision) {
		Map<Long, Region> disposable = new LinkedHashMap<Long, Region>();
		for (RequiredPackedRegion required : collision.getRequiredRegions()) {
			addDisposable(
				disposable, manager, required.getPackedRegionX(),
				required.getPackedRegionY());
		}
		if (disposable.isEmpty()) {
			addDisposable(
				disposable, manager, collision.getPackedRegionX(),
				collision.getPackedRegionY());
		}
		return disposable;
	}

	private static void addDisposable(
		final Map<Long, Region> disposable,
		final RegionManager manager,
		final int regionX,
		final int regionY) {
		long key = regionKey(regionX, regionY);
		if (disposable.containsKey(key)) {
			return;
		}
		Region region = new Region(manager, regionX, regionY);
		if (region.getRegionManager() != manager
			|| region.getRegionX() != regionX
			|| region.getRegionY() != regionY
			|| region.getObjectCollisionMutationBoundary().getRegionX()
				!= regionX
			|| region.getObjectCollisionMutationBoundary().getRegionY()
				!= regionY) {
			throw new IllegalStateException(
				"Disposable collision Region identity did not match");
		}
		disposable.put(key, region);
	}

	private static boolean verifyBlankDynamicProducts(
		final Map<Long, Region> disposable) {
		for (Region region : disposable.values()) {
			for (int localX = 0; localX < Constants.REGION_SIZE; localX++) {
				for (int localY = 0; localY < Constants.REGION_SIZE; localY++) {
					if (!hasDynamicProducts(
							region.getTileValue(localX, localY),
							ExpectedTile.ZERO)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	static List<RegionObjectCollisionMutationBoundary> boundaries(
		final Map<Long, Region> disposable,
		final AuthoredObjectCollisionFootprint footprint) {
		List<RegionObjectCollisionMutationBoundary> boundaries =
			new ArrayList<RegionObjectCollisionMutationBoundary>(
				footprint.getRequiredRegionCount());
		for (RequiredPackedRegion required
				: footprint.getRequiredRegions()) {
			Region region = disposable.get(regionKey(
				required.getPackedRegionX(),
				required.getPackedRegionY()));
			if (region == null) {
				throw new IllegalStateException(
					"Disposable collision Region coverage is incomplete");
			}
			boundaries.add(region.getObjectCollisionMutationBoundary());
		}
		return boundaries;
	}

	static TileValue mutableTile(
		final Map<Long, Region> disposable,
		final int x,
		final int y) {
		int regionX = Math.floorDiv(
			x, GameTickEventRestorationCollisionTransactionContract
				.PACKED_REGION_SIZE);
		int regionY = Math.floorDiv(
			y, GameTickEventRestorationCollisionTransactionContract
				.PACKED_REGION_SIZE);
		Region region = disposable.get(regionKey(regionX, regionY));
		return region == null ? null : region.getMutableTileValue(
			Math.floorMod(
				x, GameTickEventRestorationCollisionTransactionContract
					.PACKED_REGION_SIZE),
			Math.floorMod(
				y, GameTickEventRestorationCollisionTransactionContract
					.PACKED_REGION_SIZE));
	}

	private static void addExpected(
		final Map<Long, ExpectedTile> expected,
		final AuthoredObjectCollisionFootprint footprint) {
		for (Contribution contribution : footprint.getContributions()) {
			long key = tileKey(
				contribution.getPackedX(),
				contribution.getPackedY());
			ExpectedTile tile = expected.get(key);
			if (tile == null) {
				tile = new ExpectedTile(
					contribution.getPackedX(),
					contribution.getPackedY());
				expected.put(key, tile);
			}
			tile.add(contribution);
		}
	}

	static PostStateVerification verifyAppliedState(
		final Map<Long, Region> disposableRegions,
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan) {
		Map<Long, Region> disposable =
			Objects.requireNonNull(disposableRegions, "disposableRegions");
		LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
			Objects.requireNonNull(collisionPlan, "collisionPlan");
		Map<Long, ExpectedTile> expected = new TreeMap<Long, ExpectedTile>();
		for (AuthoredObjectCollisionFootprint footprint
				: collision.getFootprints()) {
			addExpected(expected, footprint);
		}
		return verifyApplied(disposable, expected);
	}

	private static PostStateVerification verifyApplied(
		final Map<Long, Region> disposable,
		final Map<Long, ExpectedTile> expected) {
		MessageDigest digest = sha256();
		int verifiedTiles = 0;
		long blocking = 0L;
		long dynamic = 0L;
		long projectile = 0L;
		boolean allTilesMatched = true;
		for (Region region : disposable.values()) {
			updateInt(digest, region.getRegionX());
			updateInt(digest, region.getRegionY());
			for (int localX = 0; localX < Constants.REGION_SIZE; localX++) {
				for (int localY = 0; localY < Constants.REGION_SIZE; localY++) {
					int x = Math.addExact(
						Math.multiplyExact(
							region.getRegionX(), Constants.REGION_SIZE),
						localX);
					int y = Math.addExact(
						Math.multiplyExact(
							region.getRegionY(), Constants.REGION_SIZE),
						localY);
					TileValue actual =
						region.getTileValue(localX, localY);
					int[] actualDynamicCollisionCounts =
						actual.getDynamicCollisionCounts();
					ExpectedTile wanted = expected.get(tileKey(x, y));
					if (wanted == null) {
						wanted = ExpectedTile.ZERO;
					}
					allTilesMatched &= hasDynamicProducts(
						actual, actualDynamicCollisionCounts, wanted);
					blocking = Math.addExact(
						blocking,
						(long) actual.getBlockingSceneryCount());
					for (int bit = 0; bit < 6; bit++) {
						dynamic = Math.addExact(
							dynamic,
							(long) actualDynamicCollisionCounts[bit]);
					}
					projectile = Math.addExact(
						projectile,
						(long) actual.getDynamicProjectileCount());
					updateInt(digest, x);
					updateInt(digest, y);
					updateInt(
						digest, actual.getBlockingSceneryCount());
					for (int bit = 0; bit < 6; bit++) {
						updateInt(
							digest,
							actualDynamicCollisionCounts[bit]);
					}
					updateInt(
						digest, actual.getDynamicProjectileCount());
					verifiedTiles = Math.incrementExact(verifiedTiles);
				}
			}
		}
		if (!allTilesMatched) {
			throw new IllegalStateException(
				"Disposable authored collision post-state did not match");
		}
		return new PostStateVerification(
			verifiedTiles, blocking, dynamic, projectile,
			hex(digest.digest()), true);
	}

	private static boolean hasDynamicProducts(
		final TileValue actual,
		final ExpectedTile expected) {
		return hasDynamicProducts(
			actual, actual.getDynamicCollisionCounts(), expected);
	}

	private static boolean hasDynamicProducts(
		final TileValue actual,
		final int[] actualDynamicCollisionCounts,
		final ExpectedTile expected) {
		if (actual.getBlockingSceneryCount()
				!= expected.blockingSceneryCount
			|| actual.getDynamicProjectileCount()
				!= expected.dynamicProjectileCount) {
			return false;
		}
		for (int bit = 0; bit < 6; bit++) {
			if (actualDynamicCollisionCounts[bit]
					!= expected.dynamicCollisionCounts[bit]) {
				return false;
			}
		}
		return true;
	}

	private static boolean verifyEntityMembershipEmpty(
		final Map<Long, Region> disposable) {
		for (Region region : disposable.values()) {
			Region.RetirementContentsSnapshot contents =
				region.captureRetirementContentsSnapshot();
			if (contents.getPlayerCount() != 0
				|| contents.getNpcCount() != 0
				|| contents.getObjectCount() != 0
				|| contents.getDynamicObjectCount() != 0
				|| contents.getGroundItemCount() != 0) {
				return false;
			}
		}
		return true;
	}

	private static long regionKey(final int x, final int y) {
		return ((long) x << 32) ^ (y & 0xffffffffL);
	}

	private static long tileKey(final int x, final int y) {
		return ((long) x << 32) ^ (y & 0xffffffffL);
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(
				"SHA-256 is unavailable", impossible);
		}
	}

	private static void updateInt(
		final MessageDigest digest,
		final int value) {
		digest.update(ByteBuffer.allocate(4).putInt(value).array());
	}

	private static String hex(final byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}

	private static final class ExpectedTile {
		private static final ExpectedTile ZERO =
			new ExpectedTile(0, 0);

		private final int x;
		private final int y;
		private int blockingSceneryCount;
		private final int[] dynamicCollisionCounts = new int[6];
		private int dynamicProjectileCount;

		private ExpectedTile(final int x, final int y) {
			this.x = x;
			this.y = y;
		}

		private void add(final Contribution contribution) {
			if (x != contribution.getPackedX()
				|| y != contribution.getPackedY()) {
				throw new IllegalArgumentException(
					"Expected collision tile coordinate mismatch");
			}
			blockingSceneryCount = Math.addExact(
				blockingSceneryCount,
				contribution.getBlockingSceneryCount());
			for (int bit = 0; bit < 6; bit++) {
				dynamicCollisionCounts[bit] = Math.addExact(
					dynamicCollisionCounts[bit],
					contribution.getDynamicCollisionCount(bit));
			}
			dynamicProjectileCount = Math.addExact(
				dynamicProjectileCount,
				contribution.getDynamicProjectileCount());
		}
	}

	static final class PostStateVerification {
		private final int verifiedRegionTileCount;
		private final long blockingSceneryContributionCount;
		private final long dynamicCollisionContributionCount;
		private final long dynamicProjectileContributionCount;
		private final String fingerprintSha256;
		private final boolean allTilesMatched;

		private PostStateVerification(
			final int verifiedRegionTileCount,
			final long blockingSceneryContributionCount,
			final long dynamicCollisionContributionCount,
			final long dynamicProjectileContributionCount,
			final String fingerprintSha256,
			final boolean allTilesMatched) {
			this.verifiedRegionTileCount = verifiedRegionTileCount;
			this.blockingSceneryContributionCount =
				blockingSceneryContributionCount;
			this.dynamicCollisionContributionCount =
				dynamicCollisionContributionCount;
			this.dynamicProjectileContributionCount =
				dynamicProjectileContributionCount;
			this.fingerprintSha256 = fingerprintSha256;
			this.allTilesMatched = allTilesMatched;
		}

		int getVerifiedRegionTileCount() {
			return verifiedRegionTileCount;
		}
		long getBlockingSceneryContributionCount() {
			return blockingSceneryContributionCount;
		}
		long getDynamicCollisionContributionCount() {
			return dynamicCollisionContributionCount;
		}
		long getDynamicProjectileContributionCount() {
			return dynamicProjectileContributionCount;
		}
		String getFingerprintSha256() { return fingerprintSha256; }
		boolean areAllTilesMatched() { return allTilesMatched; }
	}

	static final class Application {
		private final int collisionApplicationCount;
		private final int heldBoundaryCount;
		private final int verifiedRegionTileCount;
		private final int uniqueContributionTileCount;
		private final long blockingSceneryContributionCount;
		private final long dynamicCollisionContributionCount;
		private final long dynamicProjectileContributionCount;
		private final String appliedCollisionFingerprintSha256;
		private final boolean blankDynamicProductsMatchedBeforeApply;
		private final boolean allPlannerResultsRecreatedExactly;
		private final boolean allCollisionApplicationsSucceeded;
		private final boolean allAppliedTilesMatched;

		private Application(
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
			final boolean allAppliedTilesMatched) {
			this.collisionApplicationCount = collisionApplicationCount;
			this.heldBoundaryCount = heldBoundaryCount;
			this.verifiedRegionTileCount = verifiedRegionTileCount;
			this.uniqueContributionTileCount = uniqueContributionTileCount;
			this.blockingSceneryContributionCount =
				blockingSceneryContributionCount;
			this.dynamicCollisionContributionCount =
				dynamicCollisionContributionCount;
			this.dynamicProjectileContributionCount =
				dynamicProjectileContributionCount;
			this.appliedCollisionFingerprintSha256 =
				appliedCollisionFingerprintSha256;
			this.blankDynamicProductsMatchedBeforeApply =
				blankDynamicProductsMatchedBeforeApply;
			this.allPlannerResultsRecreatedExactly =
				allPlannerResultsRecreatedExactly;
			this.allCollisionApplicationsSucceeded =
				allCollisionApplicationsSucceeded;
			this.allAppliedTilesMatched = allAppliedTilesMatched;
		}

		int getCollisionApplicationCount() {
			return collisionApplicationCount;
		}
		int getHeldBoundaryCount() { return heldBoundaryCount; }
		int getVerifiedRegionTileCount() {
			return verifiedRegionTileCount;
		}
		int getUniqueContributionTileCount() {
			return uniqueContributionTileCount;
		}
		long getBlockingSceneryContributionCount() {
			return blockingSceneryContributionCount;
		}
		long getDynamicCollisionContributionCount() {
			return dynamicCollisionContributionCount;
		}
		long getDynamicProjectileContributionCount() {
			return dynamicProjectileContributionCount;
		}
		String getAppliedCollisionFingerprintSha256() {
			return appliedCollisionFingerprintSha256;
		}
		boolean isBlankDynamicProductsMatchedBeforeApply() {
			return blankDynamicProductsMatchedBeforeApply;
		}
		boolean isAllPlannerResultsRecreatedExactly() {
			return allPlannerResultsRecreatedExactly;
		}
		boolean isAllCollisionApplicationsSucceeded() {
			return allCollisionApplicationsSucceeded;
		}
		boolean isAllAppliedTilesMatched() {
			return allAppliedTilesMatched;
		}
	}
}
