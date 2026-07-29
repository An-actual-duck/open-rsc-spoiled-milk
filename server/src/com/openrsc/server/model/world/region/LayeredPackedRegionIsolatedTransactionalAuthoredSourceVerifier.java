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
import java.nio.charset.StandardCharsets;
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

	/**
	 * Reconstructs and then detaches one exact authored source solely inside a
	 * disposable unregistered Region union.
	 */
	static
		LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification
			verifyDetachment(
				final RegionManager regionManager,
				final LayeredPackedRegionBlankContainerPlan containerPlan,
				final LayeredPackedRegionTerrainInitializationPlan terrainPlan,
				final LayeredPackedRegionAuthoredReplayPlan replayPlan,
				final LayeredPackedRegionIsolatedAuthoredObjectVerification
					membershipVerification,
				final LayeredPackedRegionAuthoredCollisionFootprintPlan
					collisionPlan,
				final LayeredPackedRegionAuthoredObjectDetachmentPlan
					detachmentPlan,
				final int sourceOrdinal) {
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
		LayeredPackedRegionAuthoredObjectDetachmentPlan detachment =
			Objects.requireNonNull(detachmentPlan, "detachmentPlan");
		LayeredPackedRegionIsolatedAuthoredSourceStateVerifier.requireAligned(
			container, terrain, replay, membership, collision);
		if (sourceOrdinal < 0
			|| sourceOrdinal >= detachment.getSourceCount()
			|| sourceOrdinal != replay.getSelectedSourceOrdinal()
			|| detachment.getGeneration() != replay.getGeneration()
			|| detachment.getRequirementsObservedAtTick()
				!= replay.getRequirementsObservedAtTick()
			|| detachment.getRecipeObservedAtTick() != replay.getObservedAtTick()
			|| detachment.getResidencyMirrorVersion()
				!= replay.getResidencyMirrorVersion()
			|| detachment.getAuthoredGeneration()
				!= replay.getAuthoredGeneration()
			|| detachment.getSources().get(sourceOrdinal).getObjectCount()
				!= replay.getAuthoredObjectPlacementCount()
			|| detachment.isExecutableDetachment()
			|| detachment.isSchedulerCorrelationPerformed()
			|| detachment.isRuntimeMutationPerformed()
			|| detachment.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Disposable detachment inputs do not share one inert source");
		}
		LayeredPackedRegionAuthoredObjectDetachmentPlan.SourcePlan
			detachmentSource = detachment.getSources().get(sourceOrdinal);

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
				"Disposable detachment union was not blank");
		}
		for (LayeredPackedRegionTerrainInitializationPlan.TerrainTileInput
				input : terrain.getTiles()) {
			LayeredPackedRegionIsolatedAuthoredObjectVerifier.applyTerrainTile(
				source.getMutableTileValue(
					input.getLocalX(), input.getLocalY()),
				input);
		}
		if (!LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.verifySourceTerrain(source, terrain, true)) {
			throw new IllegalStateException(
				"Disposable detachment terrain did not initialize");
		}

		final int[] invalidationCount = new int[]{0};
		RegionObjectCollisionTransactionExecutor.CacheInvalidator invalidator =
			new RegionObjectCollisionTransactionExecutor.CacheInvalidator() {
				@Override
				public void invalidate(final Region region) {
					if (region != source) {
						throw new IllegalStateException(
							"Disposable detachment invalidation escaped source");
					}
					invalidationCount[0] =
						Math.incrementExact(invalidationCount[0]);
				}
			};
		RegionCollisionFootprintMutationExecutor.MutableTileAccess tileAccess =
			new RegionCollisionFootprintMutationExecutor.MutableTileAccess() {
				@Override
				public TileValue getMutableTile(
					final int x, final int y) {
					return
						LayeredPackedRegionIsolatedAuthoredCollisionVerifier
							.mutableTile(disposable, x, y);
				}
			};

		List<GameObject> constructed = new ArrayList<GameObject>(
			replay.getAuthoredObjectPlacementCount());
		List<AuthoredObjectCollisionFootprint> footprints =
			new ArrayList<AuthoredObjectCollisionFootprint>(
				collision.getObjectFootprintCount());
		int footprintIndex = 0;
		int reconstructionTransactions = 0;
		int reconstructionBoundaries = 0;
		for (AuthoredReplayPlacement placement : replay.getPlacements()) {
			if (!LayeredPackedRegionIsolatedAuthoredObjectVerifier
					.isObjectFamily(placement.getConstructionKind())) {
				continue;
			}
			AuthoredObjectCollisionFootprint footprint =
				collision.getFootprints().get(footprintIndex++);
			requirePlacementMatchesFootprint(placement, footprint);
			GameObject object =
				LayeredPackedRegionIsolatedAuthoredObjectVerifier
					.construct(placement);
			Result register = footprint.recreateVerifiedPlannerResult(
				Constants.MAX_WIDTH, Constants.MAX_HEIGHT);
			RegionObjectCollisionTransactionExecutor.Result transaction =
				RegionObjectCollisionTransactionExecutor.execute(
					LayeredPackedRegionIsolatedAuthoredCollisionVerifier
						.boundaries(disposable, footprint),
					null, null, null, null,
					source, object, register, tileAccess, invalidator);
			if (!transaction.isApplied()
				|| transaction.isMembershipRemoved()
				|| !transaction.isMembershipRegistered()
				|| !registrationMatches(
					object, object.getCollisionRegistrationState(), footprint)) {
				throw new IllegalStateException(
					"Disposable detachment reconstruction refused");
			}
			reconstructionTransactions =
				Math.incrementExact(reconstructionTransactions);
			reconstructionBoundaries = Math.addExact(
				reconstructionBoundaries, transaction.getBoundaryCount());
			constructed.add(object);
			footprints.add(footprint);
		}
		int reconstructionInvalidations = invalidationCount[0];
		if (footprintIndex != collision.getObjectFootprintCount()
			|| reconstructionTransactions != constructed.size()
			|| reconstructionInvalidations != constructed.size()) {
			throw new IllegalStateException(
				"Disposable detachment reconstruction count drifted");
		}

		LayeredPackedRegionIsolatedAuthoredCollisionVerifier
			.PostStateVerification collisionState =
				LayeredPackedRegionIsolatedAuthoredCollisionVerifier
					.verifyAppliedState(disposable, collision);
		String registrationFingerprint =
			fingerprintRegistrations(constructed);
		String preDetachmentStateFingerprint =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.fingerprintFinalState(
					disposable, source, terrain, replay, collision,
					collisionState.getFingerprintSha256());
		boolean registrationSequenceMatched =
			verifyRegistrationSequence(constructed, footprints)
				&& registrationFingerprint.equals(
					detachmentSource
						.getRuntimeRegistrationFingerprintSha256())
				&& registrationFingerprint.equals(
					detachmentSource
						.getBaselineRegistrationFingerprintSha256());
		if (!collisionState.areAllTilesMatched()
			|| !registrationSequenceMatched) {
			throw new IllegalStateException(
				"Disposable detachment pre-state did not match baseline");
		}

		int detachmentTransactions = 0;
		int detachmentBoundaries = 0;
		int registrationsCleared = 0;
		for (LayeredPackedRegionAuthoredObjectDetachmentPlan.ObjectDetachment
				planned : detachmentSource.getObjects()) {
			int objectIndex = findObject(
				constructed, planned.getAuthoredSourceOrdinal());
			if (objectIndex < 0) {
				throw new IllegalStateException(
					"Disposable detachment object identity was absent");
			}
			GameObject object = constructed.remove(objectIndex);
			AuthoredObjectCollisionFootprint footprint =
				footprints.remove(objectIndex);
			if (!matchesDetachment(planned, object, footprint)) {
				throw new IllegalStateException(
					"Disposable detachment constructor drifted");
			}
			Result unregister =
				footprint.recreateVerifiedUnregisterPlannerResult(
					Constants.MAX_WIDTH, Constants.MAX_HEIGHT);
			Result rollbackRegister = footprint.recreateVerifiedPlannerResult(
				Constants.MAX_WIDTH, Constants.MAX_HEIGHT);
			RegionObjectCollisionTransactionExecutor.Result transaction =
				RegionObjectCollisionTransactionExecutor.execute(
					LayeredPackedRegionIsolatedAuthoredCollisionVerifier
						.boundaries(disposable, footprint),
					source, object, unregister, rollbackRegister,
					null, null, null, tileAccess, invalidator);
			if (!transaction.isApplied()
				|| !transaction.isMembershipRemoved()
				|| transaction.isMembershipRegistered()
				|| object.getCollisionRegistrationState() != null) {
				throw new IllegalStateException(
					"Disposable authored-object detachment refused");
			}
			detachmentTransactions =
				Math.incrementExact(detachmentTransactions);
			detachmentBoundaries = Math.addExact(
				detachmentBoundaries, transaction.getBoundaryCount());
			registrationsCleared =
				Math.incrementExact(registrationsCleared);
		}
		int detachmentInvalidations =
			invalidationCount[0] - reconstructionInvalidations;
		boolean reverseOrderMatched =
			constructed.isEmpty() && footprints.isEmpty()
				&& detachmentTransactions
					== detachmentSource.getObjectCount();
		boolean terrainMatchedAfterDetachment =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.verifySourceTerrain(source, terrain, true);
		boolean collisionProductsCleared =
			verifyCollisionProductsCleared(disposable);
		boolean membershipEmpty =
			source.captureRetirementContentsSnapshot().getObjectCount() == 0;
		boolean supportRegionsStatic =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.verifySupportStatic(disposable, source, container);
		boolean entityFamiliesEmpty =
			LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
				.verifyEntityFamilies(disposable, source, 0);
		String postDetachmentStateFingerprint =
			fingerprintTerrainOnlyState(
				disposable, terrain,
				detachmentSource.getFingerprintSha256());
		int verifiedTiles = Math.multiplyExact(
			disposable.size(),
			Math.multiplyExact(
				Constants.REGION_SIZE, Constants.REGION_SIZE));
		return
			LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification
				.verified(
					container, terrain, replay, collision, detachment,
					sourceOrdinal, disposable.size(), disposable.size() - 1,
					reconstructionTransactions, reconstructionBoundaries,
					reconstructionInvalidations, detachmentTransactions,
					detachmentBoundaries, detachmentInvalidations,
					reconstructionTransactions, registrationsCleared,
					verifiedTiles, registrationFingerprint,
					preDetachmentStateFingerprint,
					postDetachmentStateFingerprint,
					registrationSequenceMatched, reverseOrderMatched,
					registrationsCleared == reconstructionTransactions,
					terrainMatchedAfterDetachment,
					collisionProductsCleared, membershipEmpty,
					supportRegionsStatic, entityFamiliesEmpty);
	}

	private static int findObject(
		final List<GameObject> objects,
		final int authoredSourceOrdinal) {
		for (int index = 0; index < objects.size(); index++) {
			if (objects.get(index).getAuthoredPlacementIdentity()
					.getSourceOrdinal() == authoredSourceOrdinal) {
				return index;
			}
		}
		return -1;
	}

	private static boolean matchesDetachment(
		final LayeredPackedRegionAuthoredObjectDetachmentPlan.ObjectDetachment
			planned,
		final GameObject object,
		final AuthoredObjectCollisionFootprint footprint) {
		return object.getAuthoredPlacementIdentity() != null
			&& planned.getAuthoredGeneration()
				== object.getAuthoredPlacementIdentity().getGeneration()
			&& planned.getSourcePackedRegionX()
				== object.getAuthoredPlacementIdentity().getPackedRegionX()
			&& planned.getSourcePackedRegionY()
				== object.getAuthoredPlacementIdentity().getPackedRegionY()
			&& planned.getAuthoredSourceOrdinal()
				== object.getAuthoredPlacementIdentity().getSourceOrdinal()
			&& planned.getObjectId() == object.getID()
			&& planned.getPermanentObjectId()
				== object.getLoc().getPermId()
			&& planned.getPackedX() == object.getX()
			&& planned.getPackedY() == object.getY()
			&& planned.getDirection() == object.getDirection()
			&& planned.getObjectType() == object.getType()
			&& Objects.equals(planned.getObjectOwner(), object.getOwner())
			&& registrationMatches(
				object, object.getCollisionRegistrationState(), footprint);
	}

	private static boolean verifyCollisionProductsCleared(
		final Map<Long, Region> disposable) {
		for (Region region : disposable.values()) {
			for (int localX = 0; localX < Constants.REGION_SIZE; localX++) {
				for (int localY = 0; localY < Constants.REGION_SIZE;
						localY++) {
					if (!LayeredPackedRegionIsolatedAuthoredObjectVerifier
							.hasNoDynamicProducts(
								region.getTileValue(localX, localY))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static String fingerprintTerrainOnlyState(
		final Map<Long, Region> disposable,
		final LayeredPackedRegionTerrainInitializationPlan terrain,
		final String detachmentFingerprint) {
		MessageDigest digest = sha256();
		updateString(digest, terrain.getFingerprintSha256());
		updateString(digest, detachmentFingerprint);
		updateInt(digest, disposable.size());
		for (Region region : disposable.values()) {
			updateInt(digest, region.getRegionX());
			updateInt(digest, region.getRegionY());
			Region.RetirementContentsSnapshot contents =
				region.captureRetirementContentsSnapshot();
			updateInt(digest, contents.getPlayerCount());
			updateInt(digest, contents.getNpcCount());
			updateInt(digest, contents.getObjectCount());
			updateInt(digest, contents.getDynamicObjectCount());
			updateInt(digest, contents.getGroundItemCount());
			for (int localX = 0; localX < Constants.REGION_SIZE; localX++) {
				for (int localY = 0; localY < Constants.REGION_SIZE;
						localY++) {
					TileValue tile = region.getTileValue(localX, localY);
					updateInt(digest, tile.traversalMask & 0xff);
					updateInt(digest, tile.diagWallVal);
					updateInt(digest, tile.horizontalWallVal);
					updateInt(digest, tile.overlay);
					updateInt(digest, tile.verticalWallVal);
					updateInt(digest, tile.elevation);
					updateInt(digest, tile.projectileAllowed ? 1 : 0);
					updateInt(
						digest, tile.originalProjectileAllowed ? 1 : 0);
					updateInt(digest, tile.getBlockingSceneryCount());
					for (int count : tile.getDynamicCollisionCounts()) {
						updateInt(digest, count);
					}
					updateInt(digest, tile.getDynamicProjectileCount());
				}
			}
		}
		return hex(digest.digest());
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

	private static void updateString(
		final MessageDigest digest,
		final String value) {
		byte[] bytes = Objects.requireNonNull(value, "fingerprint value")
			.getBytes(StandardCharsets.UTF_8);
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
