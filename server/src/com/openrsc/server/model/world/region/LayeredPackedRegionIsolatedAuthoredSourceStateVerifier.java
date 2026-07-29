package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan.RequiredPackedRegion;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredReplayPlan.AuthoredReplayPlacement;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Verifies one complete authored source state inside a single disposable,
 * unregistered Region union and returns only detached evidence.
 */
final class LayeredPackedRegionIsolatedAuthoredSourceStateVerifier {
	private LayeredPackedRegionIsolatedAuthoredSourceStateVerifier() { }

	static LayeredPackedRegionIsolatedAuthoredSourceStateVerification verify(
		final RegionManager regionManager,
		final LayeredPackedRegionBlankContainerPlan containerPlan,
		final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
		final LayeredPackedRegionAuthoredReplayPlan replayPlan,
		final LayeredPackedRegionIsolatedAuthoredObjectVerification
			membershipVerification,
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan) {
		RegionManager manager =
			Objects.requireNonNull(regionManager, "regionManager");
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
		requireAligned(container, terrain, replay, membership, collision);

		Map<Long, Region> disposable = constructDisposableUnion(
			manager, collision);
		Region source = disposable.get(regionKey(
			container.getPackedRegionX(), container.getPackedRegionY()));
		boolean blankUnionMatched = verifyBlankUnion(
			disposable, manager, container);
		if (!blankUnionMatched || source == null) {
			throw new IllegalStateException(
				"Combined disposable authored union was not blank");
		}

		for (LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
				input : terrain.getTiles()) {
			LayeredPackedRegionIsolatedAuthoredObjectVerifier
				.applyTerrainTile(
					source.getMutableTileValue(
						input.getLocalX(), input.getLocalY()),
					input);
		}
		boolean terrainMatchedBeforeCollision =
			verifySourceTerrain(source, terrain, true);
		if (!terrainMatchedBeforeCollision) {
			throw new IllegalStateException(
				"Combined disposable source terrain did not match");
		}

		int objectMembershipApplicationCount = 0;
		int objectMembershipBoundaryCount = 0;
		for (AuthoredReplayPlacement placement : replay.getPlacements()) {
			if (!LayeredPackedRegionIsolatedAuthoredObjectVerifier
					.isObjectFamily(placement.getConstructionKind())) {
				continue;
			}
			GameObject object =
				LayeredPackedRegionIsolatedAuthoredObjectVerifier
					.construct(placement);
			objectMembershipBoundaryCount = Math.addExact(
				objectMembershipBoundaryCount,
				LayeredPackedRegionIsolatedAuthoredObjectVerifier
					.addMembership(source, object));
			objectMembershipApplicationCount = Math.incrementExact(
				objectMembershipApplicationCount);
		}
		boolean membershipMatchedBeforeCollision =
			LayeredPackedRegionIsolatedAuthoredObjectVerifier
				.verifyExactMembership(source, replay);
		boolean entityFamiliesMatchedBeforeCollision = verifyEntityFamilies(
			disposable, source, replay.getAuthoredObjectPlacementCount());
		if (!membershipMatchedBeforeCollision
			|| !entityFamiliesMatchedBeforeCollision) {
			throw new IllegalStateException(
				"Combined disposable authored membership did not match");
		}

		LayeredPackedRegionIsolatedAuthoredCollisionVerifier.Application
			application =
				LayeredPackedRegionIsolatedAuthoredCollisionVerifier
					.applyToDisposableRegions(disposable, collision);
		boolean terrainMatchedAfterCollision =
			verifySourceTerrain(source, terrain, false);
		boolean membershipMatchedAfterCollision =
			LayeredPackedRegionIsolatedAuthoredObjectVerifier
				.verifyExactMembership(source, replay);
		boolean supportRegionsRemainedStaticallyBlank =
			verifySupportStatic(disposable, source, container);
		boolean entityFamiliesMatchedAfterCollision = verifyEntityFamilies(
			disposable, source, replay.getAuthoredObjectPlacementCount());
		boolean objectCollisionCoexistedInSourceRegion =
			disposable.get(regionKey(
				collision.getPackedRegionX(),
				collision.getPackedRegionY())) == source
				&& terrainMatchedAfterCollision
				&& membershipMatchedAfterCollision;
		if (!terrainMatchedAfterCollision
			|| !membershipMatchedAfterCollision
			|| !supportRegionsRemainedStaticallyBlank
			|| !entityFamiliesMatchedAfterCollision
			|| !objectCollisionCoexistedInSourceRegion) {
			throw new IllegalStateException(
				"Combined disposable authored final state did not match");
		}

		String finalStateFingerprint = fingerprintFinalState(
			disposable, source, terrain, replay, collision,
			application.getAppliedCollisionFingerprintSha256());
		return
			LayeredPackedRegionIsolatedAuthoredSourceStateVerification
				.verified(
					container, terrain, replay, membership, collision,
					application, disposable.size(),
					disposable.size() - 1,
					objectMembershipApplicationCount,
					objectMembershipBoundaryCount,
					finalStateFingerprint, blankUnionMatched,
					terrainMatchedBeforeCollision
						&& terrainMatchedAfterCollision,
					membershipMatchedBeforeCollision
						&& membershipMatchedAfterCollision,
					objectCollisionCoexistedInSourceRegion,
					supportRegionsRemainedStaticallyBlank,
					entityFamiliesMatchedAfterCollision);
	}

	static void requireAligned(
		final LayeredPackedRegionBlankContainerPlan container,
		final LayeredPackedRegionTerrainInitializationPlan terrain,
		final LayeredPackedRegionAuthoredReplayPlan replay,
		final LayeredPackedRegionIsolatedAuthoredObjectVerification membership,
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collision) {
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
			|| !terrain.isDetachedTerrainDefinition()
			|| !terrain.isTerrainInputDefinitionComplete()
			|| !replay.isDetachedReplayDefinition()
			|| !replay.isReplayDefinitionComplete()
			|| !membership.isVerificationOnly()
			|| !collision.isDetachedCollisionDefinition()
			|| collision.isCollisionApplied()
			|| collision.isCollisionRegistrationAttached()
			|| collision.isRuntimeSourceMutated()
			|| collision.isRuntimeHandleRetained()
			|| collision.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Combined authored source inputs are not exact and inert");
		}
	}

	static Map<Long, Region> constructDisposableUnion(
		final RegionManager manager,
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collision) {
		Map<Long, Region> disposable = new LinkedHashMap<Long, Region>();
		addDisposable(
			disposable, manager, collision.getPackedRegionX(),
			collision.getPackedRegionY());
		for (RequiredPackedRegion required : collision.getRequiredRegions()) {
			addDisposable(
				disposable, manager, required.getPackedRegionX(),
				required.getPackedRegionY());
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
				"Combined disposable Region identity did not match");
		}
		disposable.put(key, region);
	}

	static boolean verifyBlankUnion(
		final Map<Long, Region> disposable,
		final RegionManager manager,
		final LayeredPackedRegionBlankContainerPlan container) {
		for (Region region : disposable.values()) {
			Region.BlankContainerVerificationSnapshot blank =
				region.verifyLayeredBlankContainer(
					blankExpectation(manager, container, region));
			if (!blank.isRegionManagerMatched()
				|| !blank.isSourceCoordinatesMatched()
				|| !blank.isCollisionBoundaryCoordinatesMatched()
				|| !blank.isExpandedTileStorageMatched()
				|| !blank.isIndependentMutableTilesMatched()
				|| !blank.isSealedTileDefaultsMatched()
				|| !blank.isEmptyEntityMembershipMatched()) {
				return false;
			}
		}
		return true;
	}

	private static Region.BlankContainerExpectation blankExpectation(
		final RegionManager manager,
		final LayeredPackedRegionBlankContainerPlan container,
		final Region region) {
		return new Region.BlankContainerExpectation(
			manager, region.getRegionX(), region.getRegionY(),
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

	static boolean verifySourceTerrain(
		final Region source,
		final LayeredPackedRegionTerrainInitializationPlan terrain,
		final boolean requireNoDynamicProducts) {
		for (LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
				input : terrain.getTiles()) {
			TileValue tile = source.getTileValue(
				input.getLocalX(), input.getLocalY());
			boolean terrainMatched = requireNoDynamicProducts
				? LayeredPackedRegionIsolatedAuthoredObjectVerifier
					.matchesTerrain(tile, input)
				: matchesTerrainDefinition(tile, input);
			if (!terrainMatched
				|| (requireNoDynamicProducts
					&& !LayeredPackedRegionIsolatedAuthoredObjectVerifier
						.hasNoDynamicProducts(tile))) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesTerrainDefinition(
		final TileValue tile,
		final LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
			input) {
		return tile.diagWallVal == input.getDiagonalWallValue()
			&& tile.horizontalWallVal == input.getHorizontalWallValue()
			&& tile.overlay == input.getOverlay()
			&& tile.verticalWallVal == input.getVerticalWallValue()
			&& tile.elevation == input.getElevation()
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

	static boolean verifySupportStatic(
		final Map<Long, Region> disposable,
		final Region source,
		final LayeredPackedRegionBlankContainerPlan container) {
		for (Region region : disposable.values()) {
			if (region == source) {
				continue;
			}
			for (int localX = 0; localX < Constants.REGION_SIZE; localX++) {
				for (int localY = 0; localY < Constants.REGION_SIZE;
						localY++) {
					if (!matchesBlankStatic(
							region.getTileValue(localX, localY),
							container)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static boolean matchesBlankStatic(
		final TileValue tile,
		final LayeredPackedRegionBlankContainerPlan container) {
		return tile.diagWallVal == container.getInitialDiagonalWallValue()
			&& tile.horizontalWallVal
				== container.getInitialHorizontalWallValue()
			&& tile.overlay == container.getInitialOverlayValue()
			&& tile.verticalWallVal
				== container.getInitialVerticalWallValue()
			&& tile.elevation == container.getInitialElevationValue()
			&& tile.originalProjectileAllowed
				== container.isInitialOriginalProjectileAllowed()
			&& !tile.isTerrainBlocked()
			&& tile.getTerrainCollisionMask() == 0
			&& !tile.isTerrainOverlayProjectileBlocked()
			&& tile.getTerrainWallProjectileCount() == 0;
	}

	static boolean verifyEntityFamilies(
		final Map<Long, Region> disposable,
		final Region source,
		final int expectedObjectCount) {
		for (Region region : disposable.values()) {
			Region.RetirementContentsSnapshot contents =
				region.captureRetirementContentsSnapshot();
			int objectCount = region == source ? expectedObjectCount : 0;
			if (contents.getPlayerCount() != 0
				|| contents.getNpcCount() != 0
				|| contents.getObjectCount() != objectCount
				|| contents.getDynamicObjectCount() != 0
				|| contents.getGroundItemCount() != 0) {
				return false;
			}
		}
		return true;
	}

	static String fingerprintFinalState(
		final Map<Long, Region> disposable,
		final Region source,
		final LayeredPackedRegionTerrainInitializationPlan terrain,
		final LayeredPackedRegionAuthoredReplayPlan replay,
		final LayeredPackedRegionAuthoredCollisionFootprintPlan collision,
		final String appliedCollisionFingerprintSha256) {
		MessageDigest digest = sha256();
		updateString(digest, terrain.getFingerprintSha256());
		updateString(digest, replay.getFingerprintSha256());
		updateString(digest, collision.getFingerprintSha256());
		updateString(digest, appliedCollisionFingerprintSha256);
		updateInt(digest, disposable.size());
		for (Region region : disposable.values()) {
			updateInt(digest, region.getRegionX());
			updateInt(digest, region.getRegionY());
			for (int localX = 0; localX < Constants.REGION_SIZE; localX++) {
				for (int localY = 0; localY < Constants.REGION_SIZE;
						localY++) {
					TileValue tile =
						region.getTileValue(localX, localY);
					updateInt(digest, tile.traversalMask & 0xff);
					updateInt(digest, tile.diagWallVal);
					updateInt(digest, tile.horizontalWallVal);
					updateInt(digest, tile.overlay);
					updateInt(digest, tile.verticalWallVal);
					updateInt(digest, tile.elevation);
					updateBoolean(digest, tile.projectileAllowed);
					updateBoolean(digest, tile.originalProjectileAllowed);
					updateBoolean(digest, tile.isTerrainBlocked());
					updateInt(digest, tile.getTerrainCollisionMask());
					updateBoolean(
						digest,
						tile.isTerrainOverlayProjectileBlocked());
					updateInt(
						digest, tile.getTerrainWallProjectileCount());
					updateInt(
						digest, tile.getBlockingSceneryCount());
					for (int count
							: tile.getDynamicCollisionCounts()) {
						updateInt(digest, count);
					}
					updateInt(
						digest, tile.getDynamicProjectileCount());
				}
			}
		}
		updateInt(digest, replay.getAuthoredObjectPlacementCount());
		for (AuthoredReplayPlacement placement : replay.getPlacements()) {
			if (!LayeredPackedRegionIsolatedAuthoredObjectVerifier
					.isObjectFamily(placement.getConstructionKind())) {
				continue;
			}
			Region.RestorationTargetSlotSnapshot slot =
				source.captureRestorationTargetSlotSnapshot(
					placement.getPackedX(), placement.getPackedY(),
					placement.getObjectType(), placement.getDirection());
			if (slot.getObjectCount() != 1) {
				throw new IllegalStateException(
					"Final authored object fingerprint is ambiguous");
			}
			Region.RestorationTargetObjectSnapshot object =
				slot.getObjects().get(0);
			updateInt(digest, object.getObjectId());
			updateInt(digest, object.getPermanentObjectId());
			updateInt(digest, object.getX());
			updateInt(digest, object.getY());
			updateInt(digest, object.getDirection());
			updateInt(digest, object.getType());
			updateString(digest, object.getOwner());
			updateInt(digest, object.getRuntimeAttributeCount());
			updateLong(digest, object.getAuthoredGeneration());
			updateInt(digest, object.getAuthoredPackedRegionX());
			updateInt(digest, object.getAuthoredPackedRegionY());
			updateInt(digest, object.getAuthoredSourceOrdinal());
			updateString(
				digest, object.getAuthoredConstructionKind());
		}
		return hex(digest.digest());
	}

	static long regionKey(final int x, final int y) {
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

	private static void updateBoolean(
		final MessageDigest digest,
		final boolean value) {
		digest.update((byte) (value ? 1 : 0));
	}

	private static void updateInt(
		final MessageDigest digest,
		final int value) {
		digest.update(ByteBuffer.allocate(4).putInt(value).array());
	}

	private static void updateLong(
		final MessageDigest digest,
		final long value) {
		digest.update(ByteBuffer.allocate(8).putLong(value).array());
	}

	private static void updateString(
		final MessageDigest digest,
		final String value) {
		if (value == null) {
			updateInt(digest, -1);
			return;
		}
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		updateInt(digest, bytes.length);
		digest.update(bytes);
	}

	private static String hex(final byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(String.format("%02x", value & 0xff));
		}
		return result.toString();
	}
}
