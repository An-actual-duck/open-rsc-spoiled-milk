package com.openrsc.server.model.world.region;

import java.util.Objects;

/**
 * Applies static terrain to one disposable Region outside all runtime indexes,
 * verifies exact parity, and returns only a detached receipt.
 */
final class LayeredPackedRegionIsolatedTerrainVerifier {
	private static final int MAX_TERRAIN_WALL_PROJECTILE_COUNT_PER_TILE = 16;

	private LayeredPackedRegionIsolatedTerrainVerifier() { }

	static LayeredPackedRegionIsolatedTerrainVerification verify(
		final RegionManager regionManager,
		final LayeredPackedRegionBlankContainerPlan containerPlan,
		final LayeredPackedRegionTerrainInitializationPlan terrainPlan) {
		RegionManager checkedManager =
			Objects.requireNonNull(regionManager, "regionManager");
		LayeredPackedRegionBlankContainerPlan container =
			Objects.requireNonNull(containerPlan, "containerPlan");
		LayeredPackedRegionTerrainInitializationPlan terrain =
			Objects.requireNonNull(terrainPlan, "terrainPlan");
		requireAligned(container, terrain);

		Region isolated = new Region(
			checkedManager, container.getPackedRegionX(),
			container.getPackedRegionY());
		Region.BlankContainerVerificationSnapshot blank =
			isolated.verifyLayeredBlankContainer(
				blankExpectation(checkedManager, container));
		boolean blankMatched =
			blank.isRegionManagerMatched()
				&& blank.isSourceCoordinatesMatched()
				&& blank.isCollisionBoundaryCoordinatesMatched()
				&& blank.isExpandedTileStorageMatched()
				&& blank.isIndependentMutableTilesMatched()
				&& blank.isSealedTileDefaultsMatched()
				&& blank.isEmptyEntityMembershipMatched();
		if (!blankMatched) {
			throw new IllegalStateException(
				"Disposable Region is not blank before terrain apply");
		}

		for (LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
			input : terrain.getTiles()) {
			apply(isolated.getMutableTileValue(
				input.getLocalX(), input.getLocalY()), input);
		}

		boolean allTerrainTilesMatched = true;
		boolean dynamicProductsAbsent = true;
		for (LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
			input : terrain.getTiles()) {
			TileValue actual = isolated.getTileValue(
				input.getLocalX(), input.getLocalY());
			allTerrainTilesMatched &=
				matchesTerrain(actual, input);
			dynamicProductsAbsent &=
				hasNoDynamicProducts(actual);
		}
		Region.RetirementContentsSnapshot contents =
			isolated.captureRetirementContentsSnapshot();
		boolean emptyEntityMembership =
			contents.getPlayerCount() == 0
				&& contents.getNpcCount() == 0
				&& contents.getObjectCount() == 0
				&& contents.getDynamicObjectCount() == 0
				&& contents.getGroundItemCount() == 0;
		return LayeredPackedRegionIsolatedTerrainVerification.verified(
			container, terrain, blankMatched, allTerrainTilesMatched,
			dynamicProductsAbsent, emptyEntityMembership);
	}

	private static void requireAligned(
		final LayeredPackedRegionBlankContainerPlan container,
		final LayeredPackedRegionTerrainInitializationPlan terrain) {
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
			|| container.getContainerSideTileCount()
				!= terrain.getSideTileCount()
			|| container.getContainerTileSlotCount()
				!= terrain.getTileCount()) {
			throw new IllegalArgumentException(
				"Terrain plan does not match the blank container");
		}
	}

	private static Region.BlankContainerExpectation blankExpectation(
		final RegionManager regionManager,
		final LayeredPackedRegionBlankContainerPlan container) {
		return new Region.BlankContainerExpectation(
			regionManager, container.getPackedRegionX(),
			container.getPackedRegionY(),
			container.getContainerSideTileCount(),
			container.getContainerTileSlotCount(),
			container.getInitialTraversalMask(),
			container.getInitialDiagonalWallValue(),
			container.getInitialHorizontalWallValue(),
			container.getInitialOverlayValue(),
			container.getInitialVerticalWallValue(),
			container.getInitialElevationValue(),
			container.isInitialProjectileAllowed(),
			container.isInitialOriginalProjectileAllowed(),
			container.getInitialPlayerCount(),
			container.getInitialNpcCount(),
			container.getInitialObjectCount(),
			container.getInitialGroundItemCount());
	}

	private static void apply(
		final TileValue tile,
		final LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
			input) {
		if (input.getTerrainWallProjectileCount() < 0
			|| input.getTerrainWallProjectileCount()
				> MAX_TERRAIN_WALL_PROJECTILE_COUNT_PER_TILE) {
			throw new IllegalArgumentException(
				"Terrain wall projectile count exceeds isolated apply bound");
		}
		tile.diagWallVal = input.getDiagonalWallValue();
		tile.horizontalWallVal = input.getHorizontalWallValue();
		tile.overlay = input.getOverlay();
		tile.verticalWallVal = input.getVerticalWallValue();
		tile.elevation = input.getElevation();
		tile.initializeTerrainCollision();
		if (input.getTerrainCollisionMask() != 0) {
			tile.addTerrainCollision(input.getTerrainCollisionMask());
		}
		tile.setTerrainBlocked(input.isTerrainBlocked());
		tile.setTerrainOverlayProjectileBlocked(
			input.isTerrainOverlayProjectileBlocked());
		for (int count = 0;
			count < input.getTerrainWallProjectileCount(); count++) {
			tile.addTerrainWallProjectileBlock();
		}
		tile.traversalMask = (byte) input.getStaticTraversalMask();
	}

	private static boolean matchesTerrain(
		final TileValue tile,
		final LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
			input) {
		return (tile.traversalMask & 0xff)
				== input.getStaticTraversalMask()
			&& tile.diagWallVal == input.getDiagonalWallValue()
			&& tile.horizontalWallVal == input.getHorizontalWallValue()
			&& tile.overlay == input.getOverlay()
			&& tile.verticalWallVal == input.getVerticalWallValue()
			&& tile.elevation == input.getElevation()
			&& tile.projectileAllowed
				== input.isStaticProjectileBlocked()
			&& tile.originalProjectileAllowed
				== input.isStaticProjectileBlocked()
			&& tile.isTerrainBlocked() == input.isTerrainBlocked()
			&& tile.getTerrainCollisionMask()
				== input.getTerrainCollisionMask()
			&& tile.isTerrainOverlayProjectileBlocked()
				== input.isTerrainOverlayProjectileBlocked()
			&& tile.getTerrainWallProjectileCount()
				== input.getTerrainWallProjectileCount();
	}

	private static boolean hasNoDynamicProducts(final TileValue tile) {
		if (tile.getBlockingSceneryCount() != 0
			|| tile.getDynamicProjectileCount() != 0) {
			return false;
		}
		for (int count : tile.getDynamicCollisionCounts()) {
			if (count != 0) {
				return false;
			}
		}
		return true;
	}
}
