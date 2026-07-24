package com.openrsc.server.model.world.region;

import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GameObjectType;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredReplayPlan.AuthoredReplayPlacement;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Populates only authored scenery membership on one terrain-initialized,
 * disposable Region and returns detached verification evidence.
 *
 * <p>The Region is never registered or returned. NPCs, ground items, collision
 * derivation, scheduler state, runtime-source mutation, and visibility remain
 * outside this verifier.</p>
 */
final class LayeredPackedRegionIsolatedAuthoredObjectVerifier {
	private static final int MAX_TERRAIN_WALL_PROJECTILE_COUNT_PER_TILE = 16;

	private LayeredPackedRegionIsolatedAuthoredObjectVerifier() { }

	static LayeredPackedRegionIsolatedAuthoredObjectVerification verify(
		final RegionManager regionManager,
		final LayeredPackedRegionBlankContainerPlan containerPlan,
		final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
		final LayeredPackedRegionAuthoredReplayPlan replayPlan) {
		RegionManager checkedManager =
			Objects.requireNonNull(regionManager, "regionManager");
		LayeredPackedRegionBlankContainerPlan container =
			Objects.requireNonNull(containerPlan, "containerPlan");
		LayeredPackedRegionTerrainInitializationPlan terrain =
			Objects.requireNonNull(terrainPlan, "terrainPlan");
		LayeredPackedRegionAuthoredReplayPlan replay =
			Objects.requireNonNull(replayPlan, "replayPlan");
		requireContainerTerrainAligned(container, terrain);
		requireAligned(container, terrain, replay);

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
				"Disposable Region is not blank before authored replay");
		}

		for (LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
			input : terrain.getTiles()) {
			applyTerrainTile(
				isolated.getMutableTileValue(
					input.getLocalX(), input.getLocalY()),
				input);
		}
		requireExactTerrain(isolated, terrain);

		int constructedObjectCount = 0;
		int heldBoundaryCount = 0;
		for (AuthoredReplayPlacement placement : replay.getPlacements()) {
			if (!isObjectFamily(placement.getConstructionKind())) {
				continue;
			}
			GameObject object = construct(placement);
			heldBoundaryCount = Math.addExact(
				heldBoundaryCount, addMembership(isolated, object));
			constructedObjectCount = Math.incrementExact(
				constructedObjectCount);
		}

		requireExactTerrain(isolated, terrain);
		Region.RetirementContentsSnapshot contents =
			isolated.captureRetirementContentsSnapshot();
		boolean entityFamiliesMatched =
			contents.getPlayerCount() == 0
				&& contents.getNpcCount() == 0
				&& contents.getObjectCount()
					== replay.getAuthoredObjectPlacementCount()
				&& contents.getDynamicObjectCount() == 0
				&& contents.getGroundItemCount() == 0;
		boolean exactObjectMembershipMatched =
			verifyExactMembership(isolated, replay);
		return LayeredPackedRegionIsolatedAuthoredObjectVerification.verified(
			container, terrain, replay, blankMatched,
			constructedObjectCount, heldBoundaryCount,
			entityFamiliesMatched, exactObjectMembershipMatched);
	}

	private static void requireAligned(
		final LayeredPackedRegionBlankContainerPlan container,
		final LayeredPackedRegionTerrainInitializationPlan terrain,
		final LayeredPackedRegionAuthoredReplayPlan replay) {
		if (replay.getGeneration() != terrain.getGeneration()
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
			|| replay.getPackedRegionX() != container.getPackedRegionX()
			|| replay.getPackedRegionY() != container.getPackedRegionY()
			|| !replay.isPointInTimeOnly()
			|| !replay.isDetachedReplayDefinition()
			|| !replay.isReplayDefinitionComplete()
			|| !replay.isTerrainVerificationRequiredAndMatched()
			|| replay.isExecutableReplay()
			|| replay.isRegionContainerReturned()
			|| replay.isAuthoredSceneryMembershipApplied()
			|| replay.isNpcMembershipApplied()
			|| replay.isGroundItemMembershipApplied()
			|| replay.isCollisionDerived()
			|| replay.isSchedulerStateRestored()
			|| replay.isSourceAbsencePerformed()
			|| replay.isSourceReconstructionPerformed()
			|| replay.isRuntimeHandleRetained()
			|| replay.isRegionRegistryMutated()
			|| replay.isResidencyMirrorMutated()
			|| replay.isVisibilityCacheMutated()
			|| replay.isArrivalGate()
			|| replay.isVisibilityReleased()
			|| replay.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Authored replay does not match one inert terrain source");
		}
	}

	private static void requireContainerTerrainAligned(
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

	static void applyTerrainTile(
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

	private static void requireExactTerrain(
		final Region isolated,
		final LayeredPackedRegionTerrainInitializationPlan terrain) {
		for (LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
			input : terrain.getTiles()) {
			TileValue actual = isolated.getTileValue(
				input.getLocalX(), input.getLocalY());
			if (!matchesTerrain(actual, input)
				|| !hasNoDynamicProducts(actual)) {
				throw new IllegalStateException(
					"Disposable Region terrain changed during object replay");
			}
		}
	}

	static boolean matchesTerrain(
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

	static boolean hasNoDynamicProducts(final TileValue tile) {
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

	static boolean isObjectFamily(final ConstructionKind kind) {
		return kind == ConstructionKind.SCENERY
			|| kind == ConstructionKind.BOUNDARY
			|| kind == ConstructionKind.HARVESTING_SCENERY;
	}

	static GameObject construct(
		final AuthoredReplayPlacement placement) {
		GameObjectLoc location = new GameObjectLoc(
			placement.getConstructedEntityId(),
			placement.getPermanentObjectId(),
			Point.location(
				placement.getPackedX(), placement.getPackedY()),
			placement.getDirection(), placement.getObjectType(),
			placement.getObjectOwner());
		location.assignSerializedAuthoredPlacementIdentity(
			placement.getAuthoredGeneration(),
			placement.getSourcePackedRegionX(),
			placement.getSourcePackedRegionY(),
			placement.getAuthoredSourceOrdinal(),
			placement.getConstructionKind().name());
		GameObject object = new GameObject(null, location);
		if (object.getAuthoredPlacementIdentity() == null
			|| object.getCollisionRegistrationState() != null
			|| object.getRuntimeAttributeCount() != 0
			|| object.getLocation() != null
			|| object.getRegion() != null) {
			throw new IllegalStateException(
				"Authored object constructor was not detached and inert");
		}
		return object;
	}

	static int addMembership(
		final Region isolated,
		final GameObject object) {
		final Point point = object.getLoc().getLocation();
		final boolean[] inserted = new boolean[]{false};
		List<RegionObjectCollisionMutationBoundary> boundaries =
			Collections.singletonList(
				isolated.getObjectCollisionMutationBoundary());
		RegionObjectCollisionMutationBoundary.MutationExecution execution =
			RegionObjectCollisionMutationBoundary.executeMutation(
				boundaries, held -> {
					synchronized (
						isolated.getGameObjectTransactionMonitor()) {
						GameObject existing =
							isolated.getCollidingGameObjectUnderTransaction(
								point, GameObjectType.fromInt(object.getType()),
								object.getDirection());
						if (existing != null) {
							return;
						}
						boolean attached = false;
						try {
							object.attachOrderedTransactionState(
								point, isolated);
							attached = true;
							inserted[0] =
								isolated.addGameObjectUnderTransaction(
									object);
						} finally {
							if (attached && !inserted[0]) {
								object.detachOrderedTransactionState(
									point, isolated);
							}
						}
					}
				});
		if (!execution.wereAllBoundariesHeldDuringOperation()
			|| execution.getDeclaredBoundaryCount() != 1
			|| !execution.isMutationAuthorized()
			|| !execution.isMutationOperationInvoked()
			|| execution.isRollbackAuthorized()
			|| execution.isExecutableRestoration()
			|| execution.isCommitToken()
			|| execution.isArrivalGate()
			|| execution.isLifecycleAuthority()
			|| !inserted[0]
			|| object.getRegion() != isolated
			|| !point.equals(object.getLocation())
			|| object.getCollisionRegistrationState() != null) {
			throw new IllegalStateException(
				"Isolated authored object membership was not exact");
		}
		return execution.getDeclaredBoundaryCount();
	}

	static boolean verifyExactMembership(
		final Region isolated,
		final LayeredPackedRegionAuthoredReplayPlan replay) {
		int verified = 0;
		for (AuthoredReplayPlacement placement : replay.getPlacements()) {
			if (!isObjectFamily(placement.getConstructionKind())) {
				continue;
			}
			Region.RestorationTargetSlotSnapshot slot =
				isolated.captureRestorationTargetSlotSnapshot(
					placement.getPackedX(), placement.getPackedY(),
					placement.getObjectType(), placement.getDirection());
			if (slot.getObjectCount() != 1
				|| !matches(slot.getObjects().get(0), placement)) {
				return false;
			}
			verified = Math.incrementExact(verified);
		}
		return verified == replay.getAuthoredObjectPlacementCount();
	}

	private static boolean matches(
		final Region.RestorationTargetObjectSnapshot actual,
		final AuthoredReplayPlacement expected) {
		return actual.getObjectId() == expected.getConstructedEntityId()
			&& actual.getPermanentObjectId()
				== expected.getPermanentObjectId()
			&& actual.getX() == expected.getPackedX()
			&& actual.getY() == expected.getPackedY()
			&& actual.getDirection() == expected.getDirection()
			&& actual.getType() == expected.getObjectType()
			&& Objects.equals(
				actual.getOwner(), expected.getObjectOwner())
			&& actual.getRuntimeAttributeCount() == 0
			&& actual.hasAuthoredIdentity()
			&& actual.getAuthoredGeneration()
				== expected.getAuthoredGeneration()
			&& actual.getAuthoredPackedRegionX()
				== expected.getSourcePackedRegionX()
			&& actual.getAuthoredPackedRegionY()
				== expected.getSourcePackedRegionY()
			&& actual.getAuthoredSourceOrdinal()
				== expected.getAuthoredSourceOrdinal()
			&& expected.getConstructionKind().name().equals(
				actual.getAuthoredConstructionKind());
	}
}
