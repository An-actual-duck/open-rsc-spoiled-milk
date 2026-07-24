package com.openrsc.server.model.world.region;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded count/fingerprint-only reduction of complete authored source states
 * verified inside disposable unregistered Region unions.
 */
public final class LayeredPackedRegionAuthoredSourceStateVerificationBatch {
	public static final int MAXIMUM_VERIFICATION_SOURCES =
		LayeredPackedRegionAuthoredCollisionVerificationBatch
			.MAXIMUM_VERIFICATION_SOURCES;

	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final List<SourceVerification> sources;
	private final long replayPlacementCount;
	private final long authoredObjectFootprintCount;
	private final long contributionTileReferenceCount;
	private final long requiredRegionReferenceCount;
	private final long uniqueRequiredRegionReferenceCount;
	private final long combinedDisposableRegionConstructionCount;
	private final long combinedSupportRegionCount;
	private final long combinedObjectMembershipApplicationCount;
	private final long combinedObjectMembershipBoundaryCount;
	private final long combinedCollisionApplicationCount;
	private final long combinedCollisionBoundaryCount;
	private final long combinedVerifiedRegionTileCount;
	private final long combinedBlockingSceneryContributionCount;
	private final long combinedDynamicCollisionContributionCount;
	private final long combinedDynamicProjectileContributionCount;
	private final String baselineFingerprintSha256;
	private final String fingerprintSha256;

	private LayeredPackedRegionAuthoredSourceStateVerificationBatch(
		final LayeredPackedRegionAuthoredCollisionVerificationBatch
			.CombinedStateCapture capture) {
		LayeredPackedRegionAuthoredCollisionVerificationBatch
			.CombinedStateCapture checked =
				Objects.requireNonNull(capture, "capture");
		LayeredPackedRegionAuthoredCollisionVerificationBatch baseline =
			checked.getBaseline();
		List<LayeredPackedRegionIsolatedAuthoredSourceStateVerification>
			combined = checked.getCombinedStates();
		if (baseline.getSourceCount() <= 0
			|| baseline.getSourceCount() > MAXIMUM_VERIFICATION_SOURCES
			|| combined.size() != baseline.getSourceCount()
			|| !baseline.isPointInTimeOnly()
			|| !baseline.isDetachedSummaryOnly()
			|| !baseline.isAllSourcesVerified()
			|| !baseline.isRuntimeDefinitionCapturePerformed()
			|| !baseline.isCollisionFootprintDerivationPerformed()
			|| baseline.isCollisionApplied()
			|| baseline.isRuntimeHandleRetained()
			|| baseline.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Combined disposable source-state baseline is unsafe");
		}
		this.generation = baseline.getGeneration();
		this.requirementsObservedAtTick =
			baseline.getRequirementsObservedAtTick();
		this.observedAtTick = baseline.getObservedAtTick();
		this.residencyMirrorVersion =
			baseline.getResidencyMirrorVersion();
		this.authoredGeneration = baseline.getAuthoredGeneration();
		this.baselineFingerprintSha256 =
			baseline.getFingerprintSha256();

		List<SourceVerification> verified =
			new ArrayList<SourceVerification>(baseline.getSourceCount());
		long placements = 0L;
		long objectFootprints = 0L;
		long contributionReferences = 0L;
		long requiredRegionReferences = 0L;
		long uniqueRequiredRegions = 0L;
		long combinedRegions = 0L;
		long supportRegions = 0L;
		long objectApplications = 0L;
		long objectBoundaries = 0L;
		long collisionApplications = 0L;
		long collisionBoundaries = 0L;
		long verifiedTiles = 0L;
		long blocking = 0L;
		long dynamic = 0L;
		long projectile = 0L;
		for (int ordinal = 0; ordinal < baseline.getSourceCount();
				ordinal++) {
			SourceVerification source = new SourceVerification(
				baseline.getSources().get(ordinal),
				combined.get(ordinal), ordinal);
			verified.add(source);
			placements = Math.addExact(
				placements, (long) source.getReplayPlacementCount());
			objectFootprints = Math.addExact(
				objectFootprints,
				(long) source.getAuthoredObjectFootprintCount());
			contributionReferences = Math.addExact(
				contributionReferences,
				(long) source.getContributionTileReferenceCount());
			requiredRegionReferences = Math.addExact(
				requiredRegionReferences,
				(long) source.getRequiredRegionReferenceCount());
			uniqueRequiredRegions = Math.addExact(
				uniqueRequiredRegions,
				(long) source.getUniqueRequiredRegionCount());
			combinedRegions = Math.addExact(
				combinedRegions,
				(long) source.getDisposableRegionConstructionCount());
			supportRegions = Math.addExact(
				supportRegions, (long) source.getSupportRegionCount());
			objectApplications = Math.addExact(
				objectApplications,
				(long) source.getObjectMembershipApplicationCount());
			objectBoundaries = Math.addExact(
				objectBoundaries,
				(long) source.getObjectMembershipBoundaryCount());
			collisionApplications = Math.addExact(
				collisionApplications,
				(long) source.getCollisionApplicationCount());
			collisionBoundaries = Math.addExact(
				collisionBoundaries,
				(long) source.getCollisionBoundaryCount());
			verifiedTiles = Math.addExact(
				verifiedTiles,
				(long) source.getVerifiedRegionTileCount());
			blocking = Math.addExact(
				blocking, source.getBlockingSceneryContributionCount());
			dynamic = Math.addExact(
				dynamic, source.getDynamicCollisionContributionCount());
			projectile = Math.addExact(
				projectile,
				source.getDynamicProjectileContributionCount());
		}
		if (placements != baseline.getReplayPlacementCount()
			|| objectFootprints
				!= baseline.getAuthoredObjectFootprintCount()
			|| contributionReferences
				!= baseline.getContributionTileReferenceCount()
			|| requiredRegionReferences
				!= baseline.getRequiredRegionReferenceCount()
			|| uniqueRequiredRegions
				!= baseline.getUniqueRequiredRegionReferenceCount()
			|| objectApplications != objectFootprints
			|| objectBoundaries != objectApplications
			|| collisionApplications != objectFootprints
			|| collisionBoundaries != requiredRegionReferences) {
			throw new IllegalArgumentException(
				"Combined disposable source-state aggregates drifted");
		}
		this.sources = Collections.unmodifiableList(verified);
		this.replayPlacementCount = placements;
		this.authoredObjectFootprintCount = objectFootprints;
		this.contributionTileReferenceCount = contributionReferences;
		this.requiredRegionReferenceCount = requiredRegionReferences;
		this.uniqueRequiredRegionReferenceCount = uniqueRequiredRegions;
		this.combinedDisposableRegionConstructionCount = combinedRegions;
		this.combinedSupportRegionCount = supportRegions;
		this.combinedObjectMembershipApplicationCount =
			objectApplications;
		this.combinedObjectMembershipBoundaryCount = objectBoundaries;
		this.combinedCollisionApplicationCount = collisionApplications;
		this.combinedCollisionBoundaryCount = collisionBoundaries;
		this.combinedVerifiedRegionTileCount = verifiedTiles;
		this.combinedBlockingSceneryContributionCount = blocking;
		this.combinedDynamicCollisionContributionCount = dynamic;
		this.combinedDynamicProjectileContributionCount = projectile;
		this.fingerprintSha256 = fingerprint(
			baselineFingerprintSha256, verified);
	}

	public static LayeredPackedRegionAuthoredSourceStateVerificationBatch
		capture(
			final RegionManager regionManager,
			final LayeredPackedRegionSourceLifecycleBoundary boundary,
			final LayeredPackedRegionReloadRecipe reloadRecipe,
			final int maximumSources) {
		final RegionManager manager =
			Objects.requireNonNull(regionManager, "regionManager");
		return captureWithCollisionPlanFactory(
			manager, boundary, reloadRecipe, maximumSources,
			new LayeredPackedRegionAuthoredCollisionVerificationBatch
				.CollisionPlanFactory() {
				@Override
				public LayeredPackedRegionAuthoredCollisionFootprintPlan define(
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

	static LayeredPackedRegionAuthoredSourceStateVerificationBatch
		captureWithCollisionPlanFactory(
			final RegionManager regionManager,
			final LayeredPackedRegionSourceLifecycleBoundary boundary,
			final LayeredPackedRegionReloadRecipe reloadRecipe,
			final int maximumSources,
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				.CollisionPlanFactory collisionPlanFactory) {
		return new LayeredPackedRegionAuthoredSourceStateVerificationBatch(
			LayeredPackedRegionAuthoredCollisionVerificationBatch
				.captureWithCombinedSourceStates(
					regionManager, boundary, reloadRecipe,
					maximumSources, collisionPlanFactory));
	}

	private static String fingerprint(
		final String baselineFingerprint,
		final List<SourceVerification> sources) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			updateString(digest, baselineFingerprint);
			updateInt(digest, sources.size());
			for (SourceVerification source : sources) {
				updateInt(digest, source.getSourceOrdinal());
				updateInt(digest, source.getPackedRegionX());
				updateInt(digest, source.getPackedRegionY());
				updateString(
					digest, source.getFinalStateFingerprintSha256());
			}
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) {
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(
				"SHA-256 is unavailable for combined source-state batch",
				unavailable);
		}
	}

	private static void updateInt(
		final MessageDigest digest,
		final int value) {
		digest.update(Integer.toString(value).getBytes(
			StandardCharsets.UTF_8));
		digest.update((byte) 0);
	}

	private static void updateString(
		final MessageDigest digest,
		final String value) {
		digest.update(Objects.requireNonNull(value, "fingerprint value")
			.getBytes(StandardCharsets.UTF_8));
		digest.update((byte) 0);
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
	public List<SourceVerification> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public long getReplayPlacementCount() { return replayPlacementCount; }
	public long getAuthoredObjectFootprintCount() {
		return authoredObjectFootprintCount;
	}
	public long getContributionTileReferenceCount() {
		return contributionTileReferenceCount;
	}
	public long getRequiredRegionReferenceCount() {
		return requiredRegionReferenceCount;
	}
	public long getUniqueRequiredRegionReferenceCount() {
		return uniqueRequiredRegionReferenceCount;
	}
	public long getPreCombinedDisposableRegionConstructionCount() {
		return Math.multiplyExact((long) getSourceCount(), 2L);
	}
	public long getCombinedDisposableRegionConstructionCount() {
		return combinedDisposableRegionConstructionCount;
	}
	public long getTotalDisposableRegionConstructionCount() {
		return Math.addExact(
			getPreCombinedDisposableRegionConstructionCount(),
			combinedDisposableRegionConstructionCount);
	}
	public long getCombinedSupportRegionCount() {
		return combinedSupportRegionCount;
	}
	public long getPreCombinedTerrainApplyCount() {
		return Math.multiplyExact((long) getSourceCount(), 2L);
	}
	public long getCombinedTerrainApplyCount() {
		return getSourceCount();
	}
	public long getTotalTerrainApplyCount() {
		return Math.addExact(
			getPreCombinedTerrainApplyCount(),
			getCombinedTerrainApplyCount());
	}
	public long getPreCombinedObjectMembershipApplyCount() {
		return getSourceCount();
	}
	public long getCombinedObjectMembershipApplicationCount() {
		return combinedObjectMembershipApplicationCount;
	}
	public long getCombinedObjectMembershipBoundaryCount() {
		return combinedObjectMembershipBoundaryCount;
	}
	public long getCombinedCollisionApplicationCount() {
		return combinedCollisionApplicationCount;
	}
	public long getCombinedCollisionBoundaryCount() {
		return combinedCollisionBoundaryCount;
	}
	public long getCombinedVerifiedRegionTileCount() {
		return combinedVerifiedRegionTileCount;
	}
	public long getCombinedBlockingSceneryContributionCount() {
		return combinedBlockingSceneryContributionCount;
	}
	public long getCombinedDynamicCollisionContributionCount() {
		return combinedDynamicCollisionContributionCount;
	}
	public long getCombinedDynamicProjectileContributionCount() {
		return combinedDynamicProjectileContributionCount;
	}
	public String getBaselineFingerprintSha256() {
		return baselineFingerprintSha256;
	}
	public String getFingerprintSha256() { return fingerprintSha256; }
	public int getUsableRegionContainerCount() { return 0; }

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isAllSourcesVerified() { return true; }
	public boolean isRuntimeDefinitionCapturePerformed() { return true; }
	public boolean isCollisionFootprintDerivationPerformed() { return true; }
	public boolean isTerrainAppliedToCombinedDisposableSourceRegions() {
		return true;
	}
	public boolean
		isAuthoredObjectMembershipAppliedToCombinedDisposableSourceRegions() {
		return true;
	}
	public boolean isCollisionAppliedToSameDisposableRegionUnions() {
		return true;
	}
	public boolean isCollisionRegistrationAttached() { return false; }
	public boolean isRuntimeCollisionApplied() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isTerrainAppliedToRuntimeSource() { return false; }
	public boolean isAuthoredObjectMembershipAppliedToRuntimeSource() {
		return false;
	}
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

	/** Count/fingerprint-only receipt for one exact selected source. */
	public static final class SourceVerification {
		private final int sourceOrdinal;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int replayPlacementCount;
		private final int authoredObjectFootprintCount;
		private final int contributionTileReferenceCount;
		private final int requiredRegionReferenceCount;
		private final int uniqueRequiredRegionCount;
		private final int disposableRegionConstructionCount;
		private final int supportRegionCount;
		private final int objectMembershipApplicationCount;
		private final int objectMembershipBoundaryCount;
		private final int collisionApplicationCount;
		private final int collisionBoundaryCount;
		private final int verifiedRegionTileCount;
		private final long blockingSceneryContributionCount;
		private final long dynamicCollisionContributionCount;
		private final long dynamicProjectileContributionCount;
		private final String terrainFingerprintSha256;
		private final String authoredReplayFingerprintSha256;
		private final String definitionCaptureFingerprintSha256;
		private final String collisionFootprintFingerprintSha256;
		private final String appliedCollisionFingerprintSha256;
		private final String finalStateFingerprintSha256;

		private SourceVerification(
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				.SourceVerification baseline,
			final LayeredPackedRegionIsolatedAuthoredSourceStateVerification
				combined,
			final int expectedOrdinal) {
			LayeredPackedRegionAuthoredCollisionVerificationBatch
				.SourceVerification source =
					Objects.requireNonNull(baseline, "baseline");
			LayeredPackedRegionIsolatedAuthoredSourceStateVerification state =
				Objects.requireNonNull(combined, "combined");
			if (expectedOrdinal < 0
				|| source.getSourceOrdinal() != expectedOrdinal
				|| state.getSourceOrdinal() != expectedOrdinal
				|| source.getPackedRegionX() != state.getPackedRegionX()
				|| source.getPackedRegionY() != state.getPackedRegionY()
				|| source.getReplayPlacementCount()
					!= state.getReplayPlacementCount()
				|| source.getAuthoredObjectFootprintCount()
					!= state.getAuthoredObjectCount()
				|| source.getAuthoredObjectFootprintCount()
					!= state.getObjectMembershipApplicationCount()
				|| source.getAuthoredObjectFootprintCount()
					!= state.getCollisionApplicationCount()
				|| source.getRequiredRegionReferenceCount()
					!= state.getCollisionBoundaryCount()
				|| !source.getTerrainFingerprintSha256().equals(
					state.getTerrainFingerprintSha256())
				|| !source.getAuthoredReplayFingerprintSha256().equals(
					state.getAuthoredReplayFingerprintSha256())
				|| !source
					.getCollisionFootprintFingerprintSha256().equals(
						state.getCollisionFootprintFingerprintSha256())
				|| !state.isVerificationOnly()
				|| !state.isDetachedSummaryOnly()
				|| !state
					.isTerrainAppliedToDisposableSourceRegion()
				|| !state
					.isAuthoredObjectMembershipAppliedToDisposableSourceRegion()
				|| !state
					.isCollisionAppliedToSameDisposableRegionUnion()
				|| state.isCollisionRegistrationAttached()
				|| state.isRuntimeCollisionApplied()
				|| state.isRuntimeHandleRetained()
				|| state.isRuntimeSourceMutated()
				|| state.isRegionRegistryMutated()
				|| state.isLifecycleAuthority()) {
				throw new IllegalArgumentException(
					"Combined disposable source-state identity drifted");
			}
			this.sourceOrdinal = expectedOrdinal;
			this.packedRegionX = source.getPackedRegionX();
			this.packedRegionY = source.getPackedRegionY();
			this.replayPlacementCount = source.getReplayPlacementCount();
			this.authoredObjectFootprintCount =
				source.getAuthoredObjectFootprintCount();
			this.contributionTileReferenceCount =
				source.getContributionTileReferenceCount();
			this.requiredRegionReferenceCount =
				source.getRequiredRegionReferenceCount();
			this.uniqueRequiredRegionCount =
				source.getUniqueRequiredRegionCount();
			this.disposableRegionConstructionCount =
				state.getDisposableRegionConstructionCount();
			this.supportRegionCount = state.getSupportRegionCount();
			this.objectMembershipApplicationCount =
				state.getObjectMembershipApplicationCount();
			this.objectMembershipBoundaryCount =
				state.getObjectMembershipBoundaryCount();
			this.collisionApplicationCount =
				state.getCollisionApplicationCount();
			this.collisionBoundaryCount =
				state.getCollisionBoundaryCount();
			this.verifiedRegionTileCount =
				state.getVerifiedRegionTileCount();
			this.blockingSceneryContributionCount =
				state.getBlockingSceneryContributionCount();
			this.dynamicCollisionContributionCount =
				state.getDynamicCollisionContributionCount();
			this.dynamicProjectileContributionCount =
				state.getDynamicProjectileContributionCount();
			this.terrainFingerprintSha256 =
				source.getTerrainFingerprintSha256();
			this.authoredReplayFingerprintSha256 =
				source.getAuthoredReplayFingerprintSha256();
			this.definitionCaptureFingerprintSha256 =
				source.getDefinitionCaptureFingerprintSha256();
			this.collisionFootprintFingerprintSha256 =
				source.getCollisionFootprintFingerprintSha256();
			this.appliedCollisionFingerprintSha256 =
				state.getAppliedCollisionFingerprintSha256();
			this.finalStateFingerprintSha256 =
				state.getFinalStateFingerprintSha256();
		}

		public int getSourceOrdinal() { return sourceOrdinal; }
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getReplayPlacementCount() {
			return replayPlacementCount;
		}
		public int getAuthoredObjectFootprintCount() {
			return authoredObjectFootprintCount;
		}
		public int getContributionTileReferenceCount() {
			return contributionTileReferenceCount;
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
		public String getDefinitionCaptureFingerprintSha256() {
			return definitionCaptureFingerprintSha256;
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
	}
}
