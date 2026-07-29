package com.openrsc.server.model.world.region;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded count/fingerprint-only reduction of authored collision applications
 * performed solely on disposable unregistered Region unions.
 */
public final class
	LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch {
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
	private final long uniqueContributionTileReferenceCount;
	private final long requiredRegionReferenceCount;
	private final long uniqueRequiredRegionReferenceCount;
	private final long disposableCollisionRegionConstructionCount;
	private final long collisionApplicationCount;
	private final long heldBoundaryCount;
	private final long verifiedRegionTileCount;
	private final long blockingSceneryContributionCount;
	private final long dynamicCollisionContributionCount;
	private final long dynamicProjectileContributionCount;
	private final String baselineFingerprintSha256;
	private final String fingerprintSha256;

	private
		LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch(
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				.ApplicationCapture capture) {
		LayeredPackedRegionAuthoredCollisionVerificationBatch
			.ApplicationCapture checkedCapture =
				Objects.requireNonNull(capture, "capture");
		LayeredPackedRegionAuthoredCollisionVerificationBatch baseline =
			checkedCapture.getBaseline();
		List<LayeredPackedRegionIsolatedAuthoredCollisionVerification>
			applications = checkedCapture.getApplications();
		if (baseline.getSourceCount() <= 0
			|| baseline.getSourceCount() > MAXIMUM_VERIFICATION_SOURCES
			|| applications.size() != baseline.getSourceCount()
			|| !baseline.isPointInTimeOnly()
			|| !baseline.isDetachedSummaryOnly()
			|| !baseline.isAllSourcesVerified()
			|| !baseline.isRuntimeDefinitionCapturePerformed()
			|| !baseline.isCollisionFootprintDerivationPerformed()
			|| baseline.isCollisionApplied()
			|| baseline.isRuntimeHandleRetained()
			|| baseline.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Disposable collision application baseline is unsafe");
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
		long uniqueContributionReferences = 0L;
		long requiredRegionReferences = 0L;
		long uniqueRequiredRegionReferences = 0L;
		long disposableRegions = 0L;
		long collisionApplications = 0L;
		long heldBoundaries = 0L;
		long verifiedTiles = 0L;
		long blocking = 0L;
		long dynamic = 0L;
		long projectile = 0L;
		for (int ordinal = 0; ordinal < baseline.getSourceCount();
				ordinal++) {
			SourceVerification source = new SourceVerification(
				baseline, baseline.getSources().get(ordinal),
				applications.get(ordinal), ordinal);
			verified.add(source);
			placements = Math.addExact(
				placements, (long) source.getReplayPlacementCount());
			objectFootprints = Math.addExact(
				objectFootprints,
				(long) source.getAuthoredObjectFootprintCount());
			contributionReferences = Math.addExact(
				contributionReferences,
				(long) source.getContributionTileReferenceCount());
			uniqueContributionReferences = Math.addExact(
				uniqueContributionReferences,
				(long) source.getUniqueContributionTileCount());
			requiredRegionReferences = Math.addExact(
				requiredRegionReferences,
				(long) source.getRequiredRegionReferenceCount());
			uniqueRequiredRegionReferences = Math.addExact(
				uniqueRequiredRegionReferences,
				(long) source.getUniqueRequiredRegionCount());
			disposableRegions = Math.addExact(
				disposableRegions,
				(long) source.getDisposableRegionConstructionCount());
			collisionApplications = Math.addExact(
				collisionApplications,
				(long) source.getCollisionApplicationCount());
			heldBoundaries = Math.addExact(
				heldBoundaries, (long) source.getHeldBoundaryCount());
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
			|| uniqueRequiredRegionReferences
				!= baseline.getUniqueRequiredRegionReferenceCount()) {
			throw new IllegalArgumentException(
				"Disposable collision application aggregates drifted");
		}
		this.sources = Collections.unmodifiableList(verified);
		this.replayPlacementCount = placements;
		this.authoredObjectFootprintCount = objectFootprints;
		this.contributionTileReferenceCount = contributionReferences;
		this.uniqueContributionTileReferenceCount =
			uniqueContributionReferences;
		this.requiredRegionReferenceCount = requiredRegionReferences;
		this.uniqueRequiredRegionReferenceCount =
			uniqueRequiredRegionReferences;
		this.disposableCollisionRegionConstructionCount =
			disposableRegions;
		this.collisionApplicationCount = collisionApplications;
		this.heldBoundaryCount = heldBoundaries;
		this.verifiedRegionTileCount = verifiedTiles;
		this.blockingSceneryContributionCount = blocking;
		this.dynamicCollisionContributionCount = dynamic;
		this.dynamicProjectileContributionCount = projectile;
		this.fingerprintSha256 = fingerprint(
			baselineFingerprintSha256, verified);
	}

	public static
		LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
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

	static
		LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch
			captureWithCollisionPlanFactory(
				final RegionManager regionManager,
				final LayeredPackedRegionSourceLifecycleBoundary boundary,
				final LayeredPackedRegionReloadRecipe reloadRecipe,
				final int maximumSources,
				final LayeredPackedRegionAuthoredCollisionVerificationBatch
					.CollisionPlanFactory collisionPlanFactory) {
		return new
			LayeredPackedRegionAuthoredCollisionApplicationVerificationBatch(
				LayeredPackedRegionAuthoredCollisionVerificationBatch
					.captureWithDisposableCollisionApplications(
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
					digest,
					source.getAppliedCollisionFingerprintSha256());
			}
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) {
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(
				"SHA-256 is unavailable for collision application batch",
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
	public long getReplayPlacementCount() {
		return replayPlacementCount;
	}
	public long getAuthoredObjectFootprintCount() {
		return authoredObjectFootprintCount;
	}
	public long getContributionTileReferenceCount() {
		return contributionTileReferenceCount;
	}
	public long getUniqueContributionTileReferenceCount() {
		return uniqueContributionTileReferenceCount;
	}
	public long getRequiredRegionReferenceCount() {
		return requiredRegionReferenceCount;
	}
	public long getUniqueRequiredRegionReferenceCount() {
		return uniqueRequiredRegionReferenceCount;
	}
	public long getDisposableCollisionRegionConstructionCount() {
		return disposableCollisionRegionConstructionCount;
	}
	public long getPreApplicationDisposableRegionConstructionCount() {
		return Math.multiplyExact((long) getSourceCount(), 2L);
	}
	public long getTotalDisposableRegionConstructionCount() {
		return Math.addExact(
			getPreApplicationDisposableRegionConstructionCount(),
			disposableCollisionRegionConstructionCount);
	}
	public long getDisposableTerrainApplyCount() {
		return Math.multiplyExact((long) getSourceCount(), 2L);
	}
	public long getDisposableObjectMembershipApplyCount() {
		return getSourceCount();
	}
	public long getCollisionApplicationCount() {
		return collisionApplicationCount;
	}
	public long getHeldBoundaryCount() { return heldBoundaryCount; }
	public long getVerifiedRegionTileCount() {
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
	public boolean isCollisionAppliedToDisposableRegions() { return true; }
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

	public static final class SourceVerification {
		private final int sourceOrdinal;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int replayPlacementCount;
		private final int authoredObjectFootprintCount;
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
		private final String terrainFingerprintSha256;
		private final String authoredReplayFingerprintSha256;
		private final String definitionCaptureFingerprintSha256;
		private final String collisionFootprintFingerprintSha256;
		private final String appliedCollisionFingerprintSha256;

		private SourceVerification(
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				baseline,
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				.SourceVerification source,
			final LayeredPackedRegionIsolatedAuthoredCollisionVerification
				application,
			final int expectedOrdinal) {
			LayeredPackedRegionAuthoredCollisionVerificationBatch parent =
				Objects.requireNonNull(baseline, "baseline");
			LayeredPackedRegionAuthoredCollisionVerificationBatch
				.SourceVerification baselineSource =
					Objects.requireNonNull(source, "source");
			LayeredPackedRegionIsolatedAuthoredCollisionVerification applied =
				Objects.requireNonNull(application, "application");
			if (expectedOrdinal < 0
				|| baselineSource.getSourceOrdinal() != expectedOrdinal
				|| applied.getSourceOrdinal() != expectedOrdinal
				|| applied.getGeneration() != parent.getGeneration()
				|| applied.getRequirementsObservedAtTick()
					!= parent.getRequirementsObservedAtTick()
				|| applied.getObservedAtTick()
					!= parent.getObservedAtTick()
				|| applied.getResidencyMirrorVersion()
					!= parent.getResidencyMirrorVersion()
				|| applied.getAuthoredGeneration()
					!= parent.getAuthoredGeneration()
				|| applied.getPackedRegionX()
					!= baselineSource.getPackedRegionX()
				|| applied.getPackedRegionY()
					!= baselineSource.getPackedRegionY()
				|| applied.getAuthoredObjectFootprintCount()
					!= baselineSource.getAuthoredObjectFootprintCount()
				|| applied.getContributionTileReferenceCount()
					!= baselineSource.getContributionTileReferenceCount()
				|| applied.getRequiredRegionReferenceCount()
					!= baselineSource.getRequiredRegionReferenceCount()
				|| applied.getUniqueRequiredRegionCount()
					!= baselineSource.getUniqueRequiredRegionCount()
				|| !applied.getCollisionFootprintFingerprintSha256()
					.equals(
						baselineSource
							.getCollisionFootprintFingerprintSha256())
				|| !applied.isVerificationOnly()
				|| !applied.isDetachedSummaryOnly()
				|| !applied.isCollisionAppliedToDisposableRegions()
				|| !applied.isAllCollisionApplicationsSucceeded()
				|| !applied.isAllAppliedTilesMatched()
				|| applied.isCollisionRegistrationAttached()
				|| applied.isRuntimeCollisionApplied()
				|| applied.isRuntimeHandleRetained()
				|| applied.isRegionRegistryMutated()
				|| applied.isLifecycleAuthority()) {
				throw new IllegalArgumentException(
					"Disposable collision application source is unsafe");
			}
			this.sourceOrdinal = expectedOrdinal;
			this.packedRegionX = baselineSource.getPackedRegionX();
			this.packedRegionY = baselineSource.getPackedRegionY();
			this.replayPlacementCount =
				baselineSource.getReplayPlacementCount();
			this.authoredObjectFootprintCount =
				baselineSource.getAuthoredObjectFootprintCount();
			this.contributionTileReferenceCount =
				applied.getContributionTileReferenceCount();
			this.uniqueContributionTileCount =
				applied.getUniqueContributionTileCount();
			this.requiredRegionReferenceCount =
				applied.getRequiredRegionReferenceCount();
			this.uniqueRequiredRegionCount =
				applied.getUniqueRequiredRegionCount();
			this.disposableRegionConstructionCount =
				applied.getDisposableRegionConstructionCount();
			this.collisionApplicationCount =
				applied.getCollisionApplicationCount();
			this.heldBoundaryCount = applied.getHeldBoundaryCount();
			this.verifiedRegionTileCount =
				applied.getVerifiedRegionTileCount();
			this.blockingSceneryContributionCount =
				applied.getBlockingSceneryContributionCount();
			this.dynamicCollisionContributionCount =
				applied.getDynamicCollisionContributionCount();
			this.dynamicProjectileContributionCount =
				applied.getDynamicProjectileContributionCount();
			this.terrainFingerprintSha256 =
				baselineSource.getTerrainFingerprintSha256();
			this.authoredReplayFingerprintSha256 =
				baselineSource.getAuthoredReplayFingerprintSha256();
			this.definitionCaptureFingerprintSha256 =
				baselineSource.getDefinitionCaptureFingerprintSha256();
			this.collisionFootprintFingerprintSha256 =
				baselineSource
					.getCollisionFootprintFingerprintSha256();
			this.appliedCollisionFingerprintSha256 =
				applied.getAppliedCollisionFingerprintSha256();
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
	}
}
