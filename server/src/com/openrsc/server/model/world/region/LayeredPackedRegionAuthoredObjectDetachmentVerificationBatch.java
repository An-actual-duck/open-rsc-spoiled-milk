package com.openrsc.server.model.world.region;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded count/fingerprint reduction of disposable authored-object
 * reconstruction/detachment round trips for one exact selected-source set.
 */
public final class
	LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch {
	public static final int MAXIMUM_VERIFICATION_SOURCES =
		LayeredPackedRegionAuthoredCollisionVerificationBatch
			.MAXIMUM_VERIFICATION_SOURCES;

	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long runtimeObservedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final List<SourceVerification> sources;
	private final long replayPlacementCount;
	private final long authoredObjectCount;
	private final long disposableRegionConstructionCount;
	private final long supportRegionCount;
	private final long reconstructionTransactionCount;
	private final long reconstructionBoundaryCount;
	private final long reconstructionCacheInvalidationCount;
	private final long detachmentTransactionCount;
	private final long detachmentBoundaryCount;
	private final long detachmentCacheInvalidationCount;
	private final long collisionRegistrationCount;
	private final long collisionRegistrationClearedCount;
	private final long collisionContributionReferenceCount;
	private final long collisionRegionReferenceCount;
	private final long verifiedRegionTileCount;
	private final String detachmentPlanFingerprintSha256;
	private final String fingerprintSha256;

	private
		LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch(
			final RegionManager regionManager,
			final LayeredPackedRegionSourceLifecycleBoundary boundary,
			final LayeredPackedRegionReloadRecipe reloadRecipe,
			final LayeredPackedRegionAuthoredObjectDetachmentPlan
				detachmentPlan,
			final int maximumSources,
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				.CollisionPlanFactory collisionPlanFactory) {
		RegionManager manager =
			Objects.requireNonNull(regionManager, "regionManager");
		LayeredPackedRegionSourceLifecycleBoundary checkedBoundary =
			Objects.requireNonNull(boundary, "boundary");
		LayeredPackedRegionReloadRecipe reload =
			Objects.requireNonNull(reloadRecipe, "reloadRecipe");
		LayeredPackedRegionAuthoredObjectDetachmentPlan detachment =
			Objects.requireNonNull(detachmentPlan, "detachmentPlan");
		LayeredPackedRegionAuthoredCollisionVerificationBatch
			.CollisionPlanFactory factory =
				Objects.requireNonNull(
					collisionPlanFactory, "collisionPlanFactory");
		if (maximumSources <= 0
			|| maximumSources > MAXIMUM_VERIFICATION_SOURCES
			|| reload.getSourceCount() <= 0
			|| reload.getSourceCount() > maximumSources
			|| !checkedBoundary.isRegionLifecycleBoundaryHeld()
			|| checkedBoundary.getSelectedSourceCount()
				!= reload.getSourceCount()
			|| detachment.getSourceCount() != reload.getSourceCount()
			|| reload.getGeneration() != checkedBoundary.getGeneration()
			|| detachment.getGeneration() != reload.getGeneration()
			|| detachment.getRequirementsObservedAtTick()
				!= reload.getRequirementsObservedAtTick()
			|| detachment.getRecipeObservedAtTick() != reload.getObservedAtTick()
			|| detachment.getResidencyMirrorVersion()
				!= reload.getResidencyMirrorVersion()
			|| detachment.getAuthoredGeneration()
				!= reload.getAuthoredGeneration()
			|| detachment.isExecutableDetachment()
			|| detachment.isRuntimeMutationPerformed()
			|| detachment.isRuntimeHandleRetained()
			|| detachment.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Disposable detachment batch lacks one exact inert boundary");
		}
		this.generation = reload.getGeneration();
		this.requirementsObservedAtTick =
			reload.getRequirementsObservedAtTick();
		this.observedAtTick = reload.getObservedAtTick();
		this.runtimeObservedAtTick = detachment.getRuntimeObservedAtTick();
		this.residencyMirrorVersion = reload.getResidencyMirrorVersion();
		this.authoredGeneration = reload.getAuthoredGeneration();
		this.detachmentPlanFingerprintSha256 =
			detachment.getFingerprintSha256();

		List<SourceVerification> verified =
			new ArrayList<SourceVerification>(reload.getSourceCount());
		long placements = 0L;
		long objects = 0L;
		long regions = 0L;
		long supportRegions = 0L;
		long reconstructionTransactions = 0L;
		long reconstructionBoundaries = 0L;
		long reconstructionInvalidations = 0L;
		long detachmentTransactions = 0L;
		long detachmentBoundaries = 0L;
		long detachmentInvalidations = 0L;
		long registrations = 0L;
		long registrationsCleared = 0L;
		long contributionReferences = 0L;
		long regionReferences = 0L;
		long verifiedTiles = 0L;
		for (int ordinal = 0; ordinal < reload.getSourceCount(); ordinal++) {
			LayeredPackedRegionBlankContainerPlan container =
				LayeredPackedRegionBlankContainerPlan.define(reload, ordinal);
			LayeredPackedRegionTerrainInitializationPlan terrain =
				LayeredPackedRegionTerrainInitializationPlan
					.defineFromResidentTileStates(
						container, checkedBoundary,
						manager.captureLayeredPackedRegionTerrainTileStates(
							checkedBoundary, ordinal));
			LayeredPackedRegionIsolatedTerrainVerification terrainVerification =
				LayeredPackedRegionIsolatedTerrainVerifier.verify(
					manager, container, terrain);
			LayeredPackedRegionAuthoredReplayPlan replay =
				LayeredPackedRegionAuthoredReplayPlan.define(
					reload, ordinal, terrainVerification);
			LayeredPackedRegionIsolatedAuthoredObjectVerification membership =
				LayeredPackedRegionIsolatedAuthoredObjectVerifier.verify(
					manager, container, terrain, replay);
			LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
				factory.define(replay, membership);
			LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification
				receipt =
					LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerifier
						.verifyDetachment(
							manager, container, terrain, replay, membership,
							collision, detachment, ordinal);
			SourceVerification source =
				new SourceVerification(ordinal, replay, collision, receipt);
			verified.add(source);
			placements = Math.addExact(
				placements, (long) source.getReplayPlacementCount());
			objects = Math.addExact(
				objects, (long) source.getAuthoredObjectCount());
			regions = Math.addExact(
				regions,
				(long) source.getDisposableRegionConstructionCount());
			supportRegions = Math.addExact(
				supportRegions, (long) source.getSupportRegionCount());
			reconstructionTransactions = Math.addExact(
				reconstructionTransactions,
				(long) source.getReconstructionTransactionCount());
			reconstructionBoundaries = Math.addExact(
				reconstructionBoundaries,
				(long) source.getReconstructionBoundaryCount());
			reconstructionInvalidations = Math.addExact(
				reconstructionInvalidations,
				(long) source.getReconstructionCacheInvalidationCount());
			detachmentTransactions = Math.addExact(
				detachmentTransactions,
				(long) source.getDetachmentTransactionCount());
			detachmentBoundaries = Math.addExact(
				detachmentBoundaries,
				(long) source.getDetachmentBoundaryCount());
			detachmentInvalidations = Math.addExact(
				detachmentInvalidations,
				(long) source.getDetachmentCacheInvalidationCount());
			registrations = Math.addExact(
				registrations,
				(long) source.getCollisionRegistrationCount());
			registrationsCleared = Math.addExact(
				registrationsCleared,
				(long) source.getCollisionRegistrationClearedCount());
			contributionReferences = Math.addExact(
				contributionReferences,
				(long) source.getCollisionContributionReferenceCount());
			regionReferences = Math.addExact(
				regionReferences,
				(long) source.getCollisionRegionReferenceCount());
			verifiedTiles = Math.addExact(
				verifiedTiles,
				(long) source.getVerifiedRegionTileCount());
		}
		if (objects != detachment.getAuthoredObjectCount()
			|| reconstructionTransactions != objects
			|| reconstructionInvalidations != objects
			|| detachmentTransactions != objects
			|| detachmentInvalidations != objects
			|| registrations != objects
			|| registrationsCleared != objects
			|| reconstructionBoundaries != regionReferences
			|| detachmentBoundaries != regionReferences) {
			throw new IllegalArgumentException(
				"Disposable detachment batch aggregate counts drifted");
		}
		this.sources = Collections.unmodifiableList(verified);
		this.replayPlacementCount = placements;
		this.authoredObjectCount = objects;
		this.disposableRegionConstructionCount = regions;
		this.supportRegionCount = supportRegions;
		this.reconstructionTransactionCount = reconstructionTransactions;
		this.reconstructionBoundaryCount = reconstructionBoundaries;
		this.reconstructionCacheInvalidationCount =
			reconstructionInvalidations;
		this.detachmentTransactionCount = detachmentTransactions;
		this.detachmentBoundaryCount = detachmentBoundaries;
		this.detachmentCacheInvalidationCount = detachmentInvalidations;
		this.collisionRegistrationCount = registrations;
		this.collisionRegistrationClearedCount = registrationsCleared;
		this.collisionContributionReferenceCount = contributionReferences;
		this.collisionRegionReferenceCount = regionReferences;
		this.verifiedRegionTileCount = verifiedTiles;
		this.fingerprintSha256 = fingerprint(
			detachmentPlanFingerprintSha256, verified);
	}

	public static
		LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch capture(
			final RegionManager regionManager,
			final LayeredPackedRegionSourceLifecycleBoundary boundary,
			final LayeredPackedRegionReloadRecipe reloadRecipe,
			final LayeredPackedRegionAuthoredObjectDetachmentPlan
				detachmentPlan,
			final int maximumSources) {
		final RegionManager manager =
			Objects.requireNonNull(regionManager, "regionManager");
		return new
			LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch(
				manager, boundary, reloadRecipe, detachmentPlan,
				maximumSources,
				new LayeredPackedRegionAuthoredCollisionVerificationBatch
					.CollisionPlanFactory() {
					@Override
					public LayeredPackedRegionAuthoredCollisionFootprintPlan
						define(
							final LayeredPackedRegionAuthoredReplayPlan replay,
							final
								LayeredPackedRegionIsolatedAuthoredObjectVerification
									membership) {
						return manager
							.defineLayeredPackedRegionAuthoredCollisionFootprints(
								replay, membership);
					}
				});
	}

	static
		LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch
			captureWithCollisionPlanFactory(
				final RegionManager regionManager,
				final LayeredPackedRegionSourceLifecycleBoundary boundary,
				final LayeredPackedRegionReloadRecipe reloadRecipe,
				final LayeredPackedRegionAuthoredObjectDetachmentPlan
					detachmentPlan,
				final int maximumSources,
				final LayeredPackedRegionAuthoredCollisionVerificationBatch
					.CollisionPlanFactory collisionPlanFactory) {
		return new
			LayeredPackedRegionAuthoredObjectDetachmentVerificationBatch(
				regionManager, boundary, reloadRecipe, detachmentPlan,
				maximumSources, collisionPlanFactory);
	}

	private static String fingerprint(
		final String planFingerprint,
		final List<SourceVerification> sources) {
		MessageDigest digest = sha256();
		updateString(digest, planFingerprint);
		updateInt(digest, sources.size());
		for (SourceVerification source : sources) {
			updateInt(digest, source.getSourceOrdinal());
			updateInt(digest, source.getPackedRegionX());
			updateInt(digest, source.getPackedRegionY());
			updateString(digest, source.getFingerprintSha256());
		}
		return hex(digest.digest());
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getObservedAtTick() { return observedAtTick; }
	public long getRuntimeObservedAtTick() { return runtimeObservedAtTick; }
	public long getResidencyMirrorVersion() {
		return residencyMirrorVersion;
	}
	public long getAuthoredGeneration() { return authoredGeneration; }
	public List<SourceVerification> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public long getReplayPlacementCount() { return replayPlacementCount; }
	public long getAuthoredObjectCount() { return authoredObjectCount; }
	public long getDisposableRegionConstructionCount() {
		return disposableRegionConstructionCount;
	}
	public long getSupportRegionCount() { return supportRegionCount; }
	public long getReconstructionTransactionCount() {
		return reconstructionTransactionCount;
	}
	public long getReconstructionBoundaryCount() {
		return reconstructionBoundaryCount;
	}
	public long getReconstructionCacheInvalidationCount() {
		return reconstructionCacheInvalidationCount;
	}
	public long getDetachmentTransactionCount() {
		return detachmentTransactionCount;
	}
	public long getDetachmentBoundaryCount() {
		return detachmentBoundaryCount;
	}
	public long getDetachmentCacheInvalidationCount() {
		return detachmentCacheInvalidationCount;
	}
	public long getCollisionRegistrationCount() {
		return collisionRegistrationCount;
	}
	public long getCollisionRegistrationClearedCount() {
		return collisionRegistrationClearedCount;
	}
	public long getCollisionContributionReferenceCount() {
		return collisionContributionReferenceCount;
	}
	public long getCollisionRegionReferenceCount() {
		return collisionRegionReferenceCount;
	}
	public long getVerifiedRegionTileCount() {
		return verifiedRegionTileCount;
	}
	public String getDetachmentPlanFingerprintSha256() {
		return detachmentPlanFingerprintSha256;
	}
	public String getFingerprintSha256() { return fingerprintSha256; }

	public boolean areAllSourcesVerified() {
		return getSourceCount() > 0;
	}
	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isDisposableReconstructionPerformed() { return true; }
	public boolean isDisposableDetachmentPerformed() { return true; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRuntimeSourceMutated() { return false; }
	public boolean isRuntimeCollisionMutated() { return false; }
	public boolean isRuntimeCacheInvalidated() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isSchedulerCorrelationPerformed() { return false; }
	public boolean isActiveFamilyPreservationPerformed() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** Count/fingerprint-only disposable round-trip receipt for one source. */
	public static final class SourceVerification {
		private final int sourceOrdinal;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int replayPlacementCount;
		private final int authoredObjectCount;
		private final int disposableRegionConstructionCount;
		private final int supportRegionCount;
		private final int reconstructionTransactionCount;
		private final int reconstructionBoundaryCount;
		private final int reconstructionCacheInvalidationCount;
		private final int detachmentTransactionCount;
		private final int detachmentBoundaryCount;
		private final int detachmentCacheInvalidationCount;
		private final int collisionRegistrationCount;
		private final int collisionRegistrationClearedCount;
		private final int collisionContributionReferenceCount;
		private final int collisionRegionReferenceCount;
		private final int verifiedRegionTileCount;
		private final String terrainFingerprintSha256;
		private final String authoredReplayFingerprintSha256;
		private final String collisionFootprintFingerprintSha256;
		private final String detachmentPlanFingerprintSha256;
		private final String preDetachmentRegistrationFingerprintSha256;
		private final String preDetachmentStateFingerprintSha256;
		private final String postDetachmentStateFingerprintSha256;
		private final String fingerprintSha256;

		private SourceVerification(
			final int expectedOrdinal,
			final LayeredPackedRegionAuthoredReplayPlan replayPlan,
			final LayeredPackedRegionAuthoredCollisionFootprintPlan collisionPlan,
			final
				LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification
					receipt) {
			LayeredPackedRegionAuthoredReplayPlan replay =
				Objects.requireNonNull(replayPlan, "replayPlan");
			LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
				Objects.requireNonNull(collisionPlan, "collisionPlan");
			LayeredPackedRegionIsolatedAuthoredObjectDetachmentVerification
				verified = Objects.requireNonNull(receipt, "receipt");
			if (expectedOrdinal < 0
				|| replay.getSelectedSourceOrdinal() != expectedOrdinal
				|| collision.getSourceOrdinal() != expectedOrdinal
				|| verified.getSourceOrdinal() != expectedOrdinal
				|| replay.getPackedRegionX() != verified.getPackedRegionX()
				|| replay.getPackedRegionY() != verified.getPackedRegionY()
				|| !verified.isVerificationOnly()
				|| !verified.isDisposableReconstructionPerformed()
				|| !verified.isDisposableDetachmentPerformed()
				|| !verified.isTerrainMatchedAfterDetachment()
				|| !verified.isCollisionProductsClearedAfterDetachment()
				|| !verified.isObjectMembershipEmptyAfterDetachment()
				|| verified.isRuntimeSourceMutated()
				|| verified.isRuntimeHandleRetained()
				|| verified.isLifecycleAuthority()) {
				throw new IllegalArgumentException(
					"Disposable detachment source receipt is unsafe");
			}
			this.sourceOrdinal = expectedOrdinal;
			this.packedRegionX = verified.getPackedRegionX();
			this.packedRegionY = verified.getPackedRegionY();
			this.replayPlacementCount = replay.getPlacementCount();
			this.authoredObjectCount = verified.getAuthoredObjectCount();
			this.disposableRegionConstructionCount =
				verified.getDisposableRegionConstructionCount();
			this.supportRegionCount = verified.getSupportRegionCount();
			this.reconstructionTransactionCount =
				verified.getReconstructionTransactionCount();
			this.reconstructionBoundaryCount =
				verified.getReconstructionBoundaryCount();
			this.reconstructionCacheInvalidationCount =
				verified.getReconstructionCacheInvalidationCount();
			this.detachmentTransactionCount =
				verified.getDetachmentTransactionCount();
			this.detachmentBoundaryCount =
				verified.getDetachmentBoundaryCount();
			this.detachmentCacheInvalidationCount =
				verified.getDetachmentCacheInvalidationCount();
			this.collisionRegistrationCount =
				verified.getCollisionRegistrationCount();
			this.collisionRegistrationClearedCount =
				verified.getCollisionRegistrationClearedCount();
			this.collisionContributionReferenceCount =
				verified.getCollisionContributionReferenceCount();
			this.collisionRegionReferenceCount =
				verified.getCollisionRegionReferenceCount();
			this.verifiedRegionTileCount =
				verified.getVerifiedRegionTileCount();
			this.terrainFingerprintSha256 =
				verified.getTerrainFingerprintSha256();
			this.authoredReplayFingerprintSha256 =
				verified.getAuthoredReplayFingerprintSha256();
			this.collisionFootprintFingerprintSha256 =
				verified.getCollisionFootprintFingerprintSha256();
			this.detachmentPlanFingerprintSha256 =
				verified.getDetachmentPlanFingerprintSha256();
			this.preDetachmentRegistrationFingerprintSha256 =
				verified.getPreDetachmentRegistrationFingerprintSha256();
			this.preDetachmentStateFingerprintSha256 =
				verified.getPreDetachmentStateFingerprintSha256();
			this.postDetachmentStateFingerprintSha256 =
				verified.getPostDetachmentStateFingerprintSha256();
			this.fingerprintSha256 = fingerprintSource(this);
		}

		public int getSourceOrdinal() { return sourceOrdinal; }
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getReplayPlacementCount() { return replayPlacementCount; }
		public int getAuthoredObjectCount() { return authoredObjectCount; }
		public int getDisposableRegionConstructionCount() {
			return disposableRegionConstructionCount;
		}
		public int getSupportRegionCount() { return supportRegionCount; }
		public int getReconstructionTransactionCount() {
			return reconstructionTransactionCount;
		}
		public int getReconstructionBoundaryCount() {
			return reconstructionBoundaryCount;
		}
		public int getReconstructionCacheInvalidationCount() {
			return reconstructionCacheInvalidationCount;
		}
		public int getDetachmentTransactionCount() {
			return detachmentTransactionCount;
		}
		public int getDetachmentBoundaryCount() {
			return detachmentBoundaryCount;
		}
		public int getDetachmentCacheInvalidationCount() {
			return detachmentCacheInvalidationCount;
		}
		public int getCollisionRegistrationCount() {
			return collisionRegistrationCount;
		}
		public int getCollisionRegistrationClearedCount() {
			return collisionRegistrationClearedCount;
		}
		public int getCollisionContributionReferenceCount() {
			return collisionContributionReferenceCount;
		}
		public int getCollisionRegionReferenceCount() {
			return collisionRegionReferenceCount;
		}
		public int getVerifiedRegionTileCount() {
			return verifiedRegionTileCount;
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
		public String getDetachmentPlanFingerprintSha256() {
			return detachmentPlanFingerprintSha256;
		}
		public String getPreDetachmentRegistrationFingerprintSha256() {
			return preDetachmentRegistrationFingerprintSha256;
		}
		public String getPreDetachmentStateFingerprintSha256() {
			return preDetachmentStateFingerprintSha256;
		}
		public String getPostDetachmentStateFingerprintSha256() {
			return postDetachmentStateFingerprintSha256;
		}
		public String getFingerprintSha256() { return fingerprintSha256; }
	}

	private static String fingerprintSource(
		final SourceVerification source) {
		MessageDigest digest = sha256();
		updateInt(digest, source.getSourceOrdinal());
		updateInt(digest, source.getPackedRegionX());
		updateInt(digest, source.getPackedRegionY());
		updateString(digest, source.getTerrainFingerprintSha256());
		updateString(digest, source.getAuthoredReplayFingerprintSha256());
		updateString(digest, source.getCollisionFootprintFingerprintSha256());
		updateString(digest, source.getDetachmentPlanFingerprintSha256());
		updateString(
			digest,
			source.getPreDetachmentRegistrationFingerprintSha256());
		updateString(digest, source.getPreDetachmentStateFingerprintSha256());
		updateString(digest, source.getPostDetachmentStateFingerprintSha256());
		return hex(digest.digest());
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static void updateInt(
		final MessageDigest digest, final int value) {
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	private static void updateString(
		final MessageDigest digest, final String value) {
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
