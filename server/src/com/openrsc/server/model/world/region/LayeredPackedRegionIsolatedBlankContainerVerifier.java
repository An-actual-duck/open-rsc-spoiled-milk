package com.openrsc.server.model.world.region;

import java.util.Objects;

/**
 * Dormant package-local verifier for one disposable blank Region.
 *
 * <p>This class deliberately sits outside RegionManager so it cannot reach the
 * packed registry, logical residency mirror, visibility caches, ownership
 * ledgers, or gameplay lookup. It constructs a Region directly, verifies its
 * sealed initial state, returns only a detached receipt, and drops the Region
 * reference before returning.</p>
 */
final class LayeredPackedRegionIsolatedBlankContainerVerifier {
	private LayeredPackedRegionIsolatedBlankContainerVerifier() { }

	static LayeredPackedRegionBlankContainerVerification verify(
		final RegionManager regionManager,
		final LayeredPackedRegionBlankContainerPlan plan) {
		RegionManager checkedManager =
			Objects.requireNonNull(regionManager, "regionManager");
		LayeredPackedRegionBlankContainerPlan checked =
			Objects.requireNonNull(plan, "plan");
		Region isolated = new Region(
			checkedManager, checked.getPackedRegionX(),
			checked.getPackedRegionY());
		Region.BlankContainerExpectation expectation =
			new Region.BlankContainerExpectation(
				checkedManager, checked.getPackedRegionX(),
				checked.getPackedRegionY(),
				checked.getContainerSideTileCount(),
				checked.getContainerTileSlotCount(),
				checked.getInitialTraversalMask(),
				checked.getInitialDiagonalWallValue(),
				checked.getInitialHorizontalWallValue(),
				checked.getInitialOverlayValue(),
				checked.getInitialVerticalWallValue(),
				checked.getInitialElevationValue(),
				checked.isInitialProjectileAllowed(),
				checked.isInitialOriginalProjectileAllowed(),
				checked.getInitialPlayerCount(),
				checked.getInitialNpcCount(),
				checked.getInitialObjectCount(),
				checked.getInitialGroundItemCount());
		Region.BlankContainerVerificationSnapshot verified =
			isolated.verifyLayeredBlankContainer(expectation);
		return LayeredPackedRegionBlankContainerVerification.verified(
			checked, verified.isRegionManagerMatched(),
			verified.isSourceCoordinatesMatched(),
			verified.isCollisionBoundaryCoordinatesMatched(),
			verified.isExpandedTileStorageMatched(),
			verified.isIndependentMutableTilesMatched(),
			verified.isSealedTileDefaultsMatched(),
			verified.isEmptyEntityMembershipMatched());
	}
}
