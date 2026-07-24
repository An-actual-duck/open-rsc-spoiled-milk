package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.event.rsc
	.GameTickEventRestorationCollisionFootprintPlanner.Result;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GameObjectCollisionRegistrationState;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredCollisionFootprintPlan
		.AuthoredObjectCollisionFootprint;
import com.openrsc.server.model.world.region
	.LayeredPackedRegionAuthoredReplayPlan.AuthoredReplayPlacement;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Rebuilds one authored source through the canonical atomic object/collision
 * transaction, solely inside a disposable unregistered Region union.
 */
final class
	LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerifier {
	private
		LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerifier() { }

	static LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification
		verify(
			final RegionManager regionManager,
			final LayeredPackedRegionBlankContainerPlan containerPlan,
			final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
			final LayeredPackedRegionAuthoredReplayPlan replayPlan,
			final LayeredPackedRegionIsolatedAuthoredObjectVerification
				membershipVerification,
			final LayeredPackedRegionAuthoredCollisionFootprintPlan
				collisionPlan) {
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
		LayeredPackedRegionIsolatedAuthoredSourceStateVerifier.requireAligned(
			container, terrain, replay, membership, collision);

		Map<Long, Region> disposable =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.constructDisposableUnion(manager, collision);
		Region source = disposable.get(
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier.regionKey(
				container.getPackedRegionX(), container.getPackedRegionY()));
		boolean blankUnionMatched =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.verifyBlankUnion(disposable, manager, container);
		if (!blankUnionMatched || source == null) {
			throw new IllegalStateException(
				"Transactional disposable authored union was not blank");
		}

		for (LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
				input : terrain.getTiles()) {
			LayeredPackedRegionIsolatedAuthoredObjectVerifier.applyTerrainTile(
				source.getMutableTileValue(
					input.getLocalX(), input.getLocalY()),
				input);
		}
		boolean terrainMatchedBeforeTransactions =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.verifySourceTerrain(source, terrain, true);
		if (!terrainMatchedBeforeTransactions) {
			throw new IllegalStateException(
				"Transactional disposable terrain did not match");
		}

		final int[] disposableInvalidationCount = new int[]{0};
		List<GameObject> constructed = new ArrayList<GameObject>(
			replay.getAuthoredObjectPlacementCount());
		int footprintIndex = 0;
		int transactionCount = 0;
		int transactionBoundaryCount = 0;
		int registrationCount = 0;
		int registrationContributionCount = 0;
		int registrationRegionReferenceCount = 0;
		for (AuthoredReplayPlacement placement : replay.getPlacements()) {
			if (!LayeredPackedRegionIsolatedAuthoredObjectVerifier
					.isObjectFamily(placement.getConstructionKind())) {
				continue;
			}
			if (footprintIndex >= collision.getFootprints().size()) {
				throw new IllegalStateException(
					"Transactional authored footprint order is incomplete");
			}
			AuthoredObjectCollisionFootprint footprint =
				collision.getFootprints().get(footprintIndex++);
			requirePlacementMatchesFootprint(placement, footprint);
			GameObject object =
				LayeredPackedRegionIsolatedAuthoredObjectVerifier
					.construct(placement);
			Result recreated = footprint.recreateVerifiedPlannerResult(
				Constants.MAX_WIDTH, Constants.MAX_HEIGHT);
			RegionObjectCollisionTransactionExecutor.Result transaction =
				RegionObjectCollisionTransactionExecutor.execute(
					LayeredPackedRegionIsolatedAuthoredCollisionVerifier
						.boundaries(disposable, footprint),
					null, null, null, null,
					source, object, recreated,
					new RegionCollisionFootprintMutationExecutor
						.MutableTileAccess() {
						@Override
						public TileValue getMutableTile(
							final int x, final int y) {
							return
								LayeredPackedRegionIsolatedAuthoredCollisionVerifier
									.mutableTile(disposable, x, y);
						}
					},
					new RegionObjectCollisionTransactionExecutor
						.CacheInvalidator() {
						@Override
						public void invalidate(final Region region) {
							if (region != source) {
								throw new IllegalStateException(
									"Disposable invalidation escaped source");
							}
							disposableInvalidationCount[0] =
								Math.incrementExact(
									disposableInvalidationCount[0]);
						}
					});
			if (!transaction.isApplied()
				|| transaction.isRefused()
				|| transaction.isMembershipRemoved()
				|| !transaction.isMembershipRegistered()
				|| !transaction.isMutationAuthorized()
				|| !transaction.isMutationPerformed()
				|| transaction.isRollbackAuthorized()
				|| transaction.isExecutableRestoration()
				|| transaction.isCommitToken()
				|| transaction.isArrivalGate()
				|| transaction.isLifecycleAuthority()
				|| transaction.getBoundaryCount()
					!= footprint.getRequiredRegionCount()) {
				throw new IllegalStateException(
					"Disposable authored object transaction refused");
			}
			GameObjectCollisionRegistrationState registration =
				object.getCollisionRegistrationState();
			if (!registrationMatches(
					object, registration, footprint)) {
				throw new IllegalStateException(
					"Disposable collision registration provenance drifted");
			}
			transactionCount = Math.incrementExact(transactionCount);
			transactionBoundaryCount = Math.addExact(
				transactionBoundaryCount, transaction.getBoundaryCount());
			registrationCount = Math.incrementExact(registrationCount);
			registrationContributionCount = Math.addExact(
				registrationContributionCount,
				registration.getContributionTileCount());
			registrationRegionReferenceCount = Math.addExact(
				registrationRegionReferenceCount,
				registration.getRequiredRegionCount());
			constructed.add(object);
		}
		if (footprintIndex != collision.getFootprints().size()
			|| transactionCount != replay.getAuthoredObjectPlacementCount()
			|| disposableInvalidationCount[0] != transactionCount) {
			throw new IllegalStateException(
				"Transactional authored application count drifted");
		}

		LayeredPackedRegionIsolatedAuthoredCollisionVerifier
			.PostStateVerification collisionState =
				LayeredPackedRegionIsolatedAuthoredCollisionVerifier
					.verifyAppliedState(disposable, collision);
		boolean terrainMatchedAfterTransactions =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.verifySourceTerrain(source, terrain, false);
		boolean membershipMatchedAfterTransactions =
			LayeredPackedRegionIsolatedAuthoredObjectVerifier
				.verifyExactMembership(source, replay);
		boolean supportRegionsRemainedStaticallyBlank =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.verifySupportStatic(disposable, source, container);
		boolean entityFamiliesMatched =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.verifyEntityFamilies(
					disposable, source,
					replay.getAuthoredObjectPlacementCount());
		boolean allRegistrationsMatched =
			verifyRegistrationSequence(
				constructed, collision.getFootprints());
		if (!collisionState.areAllTilesMatched()
			|| !terrainMatchedAfterTransactions
			|| !membershipMatchedAfterTransactions
			|| !supportRegionsRemainedStaticallyBlank
			|| !entityFamiliesMatched
			|| !allRegistrationsMatched) {
			throw new IllegalStateException(
				"Transactional disposable final state did not match");
		}

		String finalStateFingerprint =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.fingerprintFinalState(
					disposable, source, terrain, replay, collision,
					collisionState.getFingerprintSha256());
		String registrationFingerprint =
			fingerprintRegistrations(constructed);
		return
			LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification
				.verified(
					container, terrain, replay, membership, collision,
					disposable.size(), disposable.size() - 1,
					transactionCount, transactionBoundaryCount,
					disposableInvalidationCount[0], registrationCount,
					registrationContributionCount,
					registrationRegionReferenceCount,
					collisionState, registrationFingerprint,
					finalStateFingerprint, blankUnionMatched,
					terrainMatchedBeforeTransactions
						&& terrainMatchedAfterTransactions,
					membershipMatchedAfterTransactions,
					supportRegionsRemainedStaticallyBlank,
					entityFamiliesMatched, allRegistrationsMatched);
	}

	private static void requirePlacementMatchesFootprint(
		final AuthoredReplayPlacement placement,
		final AuthoredObjectCollisionFootprint footprint) {
		if (placement.getAuthoredGeneration()
				!= footprint.getAuthoredGeneration()
			|| placement.getSourcePackedRegionX()
				!= footprint.getSourcePackedRegionX()
			|| placement.getSourcePackedRegionY()
				!= footprint.getSourcePackedRegionY()
			|| placement.getAuthoredSourceOrdinal()
				!= footprint.getAuthoredSourceOrdinal()
			|| placement.getConstructionKind()
				!= footprint.getConstructionKind()
			|| placement.getConstructedEntityId() != footprint.getObjectId()
			|| placement.getPermanentObjectId()
				!= footprint.getPermanentObjectId()
			|| placement.getPackedX() != footprint.getPackedX()
			|| placement.getPackedY() != footprint.getPackedY()
			|| placement.getDirection() != footprint.getDirection()
			|| placement.getObjectType() != footprint.getObjectType()
			|| !Objects.equals(
				placement.getObjectOwner(), footprint.getObjectOwner())) {
			throw new IllegalArgumentException(
				"Authored placement does not match collision footprint");
		}
	}

	private static boolean registrationMatches(
		final GameObject object,
		final GameObjectCollisionRegistrationState registration,
		final AuthoredObjectCollisionFootprint footprint) {
		if (registration == null
			|| !registration.matchesConstructor(object)
			|| registration.getContributionTileCount()
				!= footprint.getContributionTileCount()
			|| registration.getRequiredRegionCount()
				!= footprint.getRequiredRegionCount()
			|| !registration.isDetachedPrimitiveCopy()
			|| registration.isRuntimeHandleRetained()
			|| registration.isRegionLoadingPerformed()
			|| registration.isMutationAuthorized()
			|| registration.isMutationPerformed()
			|| registration.isArrivalGate()
			|| registration.isLifecycleAuthority()) {
			return false;
		}
		for (int index = 0;
				index < footprint.getContributionTileCount(); index++) {
			LayeredPackedRegionAuthoredCollisionFootprintPlan.Contribution
				expected = footprint.getContributions().get(index);
			GameObjectCollisionRegistrationState.CollisionContribution actual =
				registration.getContributions().get(index);
			if (expected.getPackedX() != actual.getX()
				|| expected.getPackedY() != actual.getY()
				|| expected.getBlockingSceneryCount()
					!= actual.getBlockingSceneryCount()
				|| expected.getDynamicProjectileCount()
					!= actual.getDynamicProjectileCount()) {
				return false;
			}
			for (int bit = 0; bit < 6; bit++) {
				if (expected.getDynamicCollisionCount(bit)
						!= actual.getDynamicCollisionCount(bit)) {
					return false;
				}
			}
		}
		for (int index = 0;
				index < footprint.getRequiredRegionCount(); index++) {
			LayeredPackedRegionAuthoredCollisionFootprintPlan
				.RequiredPackedRegion expected =
					footprint.getRequiredRegions().get(index);
			GameObjectCollisionRegistrationState.PackedRegionCoordinate actual =
				registration.getRequiredRegions().get(index);
			if (expected.getPackedRegionX() != actual.getRegionX()
				|| expected.getPackedRegionY() != actual.getRegionY()) {
				return false;
			}
		}
		return true;
	}

	private static boolean verifyRegistrationSequence(
		final List<GameObject> objects,
		final List<AuthoredObjectCollisionFootprint> footprints) {
		if (objects.size() != footprints.size()) {
			return false;
		}
		for (int index = 0; index < objects.size(); index++) {
			if (!registrationMatches(
					objects.get(index),
					objects.get(index).getCollisionRegistrationState(),
					footprints.get(index))) {
				return false;
			}
		}
		return true;
	}

	private static String fingerprintRegistrations(
		final List<GameObject> objects) {
		MessageDigest digest = sha256();
		updateInt(digest, objects.size());
		for (GameObject object : objects) {
			GameObjectCollisionRegistrationState registration =
				Objects.requireNonNull(
					object.getCollisionRegistrationState(),
					"collisionRegistrationState");
			updateInt(digest, registration.getObjectId());
			updateInt(digest, registration.getPermanentObjectId());
			updateInt(digest, registration.getX());
			updateInt(digest, registration.getY());
			updateInt(digest, registration.getDirection());
			updateInt(digest, registration.getType());
			updateInt(digest, registration.getContributionTileCount());
			for (GameObjectCollisionRegistrationState.CollisionContribution
					contribution : registration.getContributions()) {
				updateInt(digest, contribution.getX());
				updateInt(digest, contribution.getY());
				updateInt(
					digest, contribution.getBlockingSceneryCount());
				for (int count
						: contribution.getDynamicCollisionCounts()) {
					updateInt(digest, count);
				}
				updateInt(
					digest, contribution.getDynamicProjectileCount());
			}
			updateInt(digest, registration.getRequiredRegionCount());
			for (GameObjectCollisionRegistrationState.PackedRegionCoordinate
					region : registration.getRequiredRegions()) {
				updateInt(digest, region.getRegionX());
				updateInt(digest, region.getRegionY());
			}
		}
		return hex(digest.digest());
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
}
