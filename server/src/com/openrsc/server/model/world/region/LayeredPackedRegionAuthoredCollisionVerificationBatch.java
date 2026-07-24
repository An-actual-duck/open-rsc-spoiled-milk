package com.openrsc.server.model.world.region;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded exact-source summary of disposable authored-object and collision
 * footprint verification.
 *
 * <p>Each source is reduced independently while its already active lifecycle
 * boundary remains held. Terrain and authored object membership are verified
 * on disposable unregistered Regions, active definition scalars are captured,
 * collision footprints are derived without application, and every
 * intermediate object is discarded before the next source. Only detached
 * counts and fingerprints survive.</p>
 */
public final class LayeredPackedRegionAuthoredCollisionVerificationBatch {
	public static final int MAXIMUM_VERIFICATION_SOURCES = 128;

	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final List<SourceVerification> sources;
	private final long replayPlacementCount;
	private final long authoredObjectFootprintCount;
	private final long definitionBackedObjectCount;
	private final long specialCollisionlessObjectCount;
	private final long zeroContributionObjectCount;
	private final long crossSourceCollisionObjectCount;
	private final long collisionBeyondAuthoredDependencyObjectCount;
	private final long contributionTileReferenceCount;
	private final long requiredRegionReferenceCount;
	private final long uniqueRequiredRegionReferenceCount;
	private final String fingerprintSha256;

	private LayeredPackedRegionAuthoredCollisionVerificationBatch(
		final RegionManager regionManager,
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int maximumSources,
		final CollisionPlanFactory collisionPlanFactory) {
		this(
			regionManager, boundary, reloadRecipe, maximumSources,
			collisionPlanFactory, null, null, null);
	}

	private LayeredPackedRegionAuthoredCollisionVerificationBatch(
		final RegionManager regionManager,
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int maximumSources,
		final CollisionPlanFactory collisionPlanFactory,
		final List<
			LayeredPackedRegionIsolatedAuthoredCollisionVerification>
				disposableCollisionApplications) {
		this(
			regionManager, boundary, reloadRecipe, maximumSources,
			collisionPlanFactory, disposableCollisionApplications, null, null);
	}

	private LayeredPackedRegionAuthoredCollisionVerificationBatch(
		final RegionManager regionManager,
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int maximumSources,
		final CollisionPlanFactory collisionPlanFactory,
		final List<
			LayeredPackedRegionIsolatedAuthoredCollisionVerification>
				disposableCollisionApplications,
			final List<
				LayeredPackedRegionIsolatedAuthoredSourceStateVerification>
					combinedSourceStates,
			final List<
				LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification>
					transactionalSourceStates) {
		RegionManager manager =
			Objects.requireNonNull(regionManager, "regionManager");
		LayeredPackedRegionSourceLifecycleBoundary checkedBoundary =
			Objects.requireNonNull(boundary, "boundary");
		LayeredPackedRegionReloadRecipe reload =
			Objects.requireNonNull(reloadRecipe, "reloadRecipe");
		CollisionPlanFactory factory = Objects.requireNonNull(
			collisionPlanFactory, "collisionPlanFactory");
		if (maximumSources <= 0
			|| maximumSources > MAXIMUM_VERIFICATION_SOURCES
			|| !checkedBoundary.isRegionLifecycleBoundaryHeld()
			|| reload.getSourceCount() <= 0
			|| reload.getSourceCount() > maximumSources
			|| reload.getSourceCount()
				!= checkedBoundary.getSelectedSourceCount()
			|| reload.getGeneration() != checkedBoundary.getGeneration()
			|| reload.getRequirementsObservedAtTick()
				!= checkedBoundary.getRequirementsObservedAtTick()
			|| reload.getResidencyMirrorVersion()
				!= checkedBoundary.getResidencyMirrorVersion()
			|| !reload.isDetachedDefinitionComplete()
			|| reload.isExecutableReload()
			|| reload.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Authored collision verification lacks one exact inert boundary");
		}
		this.generation = reload.getGeneration();
		this.requirementsObservedAtTick =
			reload.getRequirementsObservedAtTick();
		this.observedAtTick = reload.getObservedAtTick();
		this.residencyMirrorVersion = reload.getResidencyMirrorVersion();
		this.authoredGeneration = reload.getAuthoredGeneration();

		List<SourceVerification> verified =
			new ArrayList<SourceVerification>(reload.getSourceCount());
		long placements = 0L;
		long objectFootprints = 0L;
		long definitionBacked = 0L;
		long specialCollisionless = 0L;
		long zeroContribution = 0L;
		long crossSource = 0L;
		long beyondAuthoredDependency = 0L;
		long contributionReferences = 0L;
		long requiredRegionReferences = 0L;
		long uniqueRequiredRegionReferences = 0L;
		for (int ordinal = 0; ordinal < reload.getSourceCount(); ordinal++) {
			LayeredPackedRegionBlankContainerPlan container =
				LayeredPackedRegionBlankContainerPlan.define(
					reload, ordinal);
			List<LayeredTileState> resident =
				manager.captureLayeredPackedRegionTerrainTileStates(
					checkedBoundary, ordinal);
			LayeredPackedRegionTerrainInitializationPlan terrain =
				LayeredPackedRegionTerrainInitializationPlan
					.defineFromResidentTileStates(
						container, checkedBoundary, resident);
			LayeredPackedRegionIsolatedTerrainVerification
				terrainVerification =
					LayeredPackedRegionIsolatedTerrainVerifier.verify(
						manager, container, terrain);
			LayeredPackedRegionAuthoredReplayPlan replay =
				LayeredPackedRegionAuthoredReplayPlan.define(
					reload, ordinal, terrainVerification);
			LayeredPackedRegionIsolatedAuthoredObjectVerification
				membershipVerification =
					LayeredPackedRegionIsolatedAuthoredObjectVerifier.verify(
						manager, container, terrain, replay);
			LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
				factory.define(replay, membershipVerification);
			if (disposableCollisionApplications != null) {
				disposableCollisionApplications.add(
					LayeredPackedRegionIsolatedAuthoredCollisionVerifier
						.verify(manager, collision));
			}
			if (combinedSourceStates != null) {
				combinedSourceStates.add(
					LayeredPackedRegionIsolatedAuthoredSourceStateVerifier
						.verify(
							manager, container, terrain, replay,
							membershipVerification, collision));
			}
			if (transactionalSourceStates != null) {
				transactionalSourceStates.add(
					LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerifier
						.verify(
							manager, container, terrain, replay,
							membershipVerification, collision));
			}
			SourceVerification source = new SourceVerification(
				ordinal, terrainVerification, replay,
				membershipVerification, collision);
			verified.add(source);
			placements = Math.addExact(
				placements, source.getReplayPlacementCount());
			objectFootprints = Math.addExact(
				objectFootprints, source.getAuthoredObjectFootprintCount());
			definitionBacked = Math.addExact(
				definitionBacked, source.getDefinitionBackedObjectCount());
			specialCollisionless = Math.addExact(
				specialCollisionless,
				source.getSpecialCollisionlessObjectCount());
			zeroContribution = Math.addExact(
				zeroContribution, source.getZeroContributionObjectCount());
			crossSource = Math.addExact(
				crossSource, source.getCrossSourceCollisionObjectCount());
			beyondAuthoredDependency = Math.addExact(
				beyondAuthoredDependency,
				source.getCollisionBeyondAuthoredDependencyObjectCount());
			contributionReferences = Math.addExact(
				contributionReferences,
				source.getContributionTileReferenceCount());
			requiredRegionReferences = Math.addExact(
				requiredRegionReferences,
				source.getRequiredRegionReferenceCount());
			uniqueRequiredRegionReferences = Math.addExact(
				uniqueRequiredRegionReferences,
				source.getUniqueRequiredRegionCount());
		}
		this.sources = Collections.unmodifiableList(verified);
		this.replayPlacementCount = placements;
		this.authoredObjectFootprintCount = objectFootprints;
		this.definitionBackedObjectCount = definitionBacked;
		this.specialCollisionlessObjectCount = specialCollisionless;
		this.zeroContributionObjectCount = zeroContribution;
		this.crossSourceCollisionObjectCount = crossSource;
		this.collisionBeyondAuthoredDependencyObjectCount =
			beyondAuthoredDependency;
		this.contributionTileReferenceCount = contributionReferences;
		this.requiredRegionReferenceCount = requiredRegionReferences;
		this.uniqueRequiredRegionReferenceCount =
			uniqueRequiredRegionReferences;
		this.fingerprintSha256 = fingerprint(verified);
	}

	public static LayeredPackedRegionAuthoredCollisionVerificationBatch capture(
		final RegionManager regionManager,
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int maximumSources) {
		final RegionManager manager =
			Objects.requireNonNull(regionManager, "regionManager");
		return new LayeredPackedRegionAuthoredCollisionVerificationBatch(
			manager, boundary, reloadRecipe, maximumSources,
			new CollisionPlanFactory() {
				@Override
				public LayeredPackedRegionAuthoredCollisionFootprintPlan define(
					final LayeredPackedRegionAuthoredReplayPlan replay,
					final LayeredPackedRegionIsolatedAuthoredObjectVerification
						membership) {
					return manager
						.defineLayeredPackedRegionAuthoredCollisionFootprints(
							replay, membership);
				}
			});
	}

	static LayeredPackedRegionAuthoredCollisionVerificationBatch
		captureWithCollisionPlanFactory(
			final RegionManager regionManager,
			final LayeredPackedRegionSourceLifecycleBoundary boundary,
			final LayeredPackedRegionReloadRecipe reloadRecipe,
			final int maximumSources,
			final CollisionPlanFactory collisionPlanFactory) {
		return new LayeredPackedRegionAuthoredCollisionVerificationBatch(
			regionManager, boundary, reloadRecipe, maximumSources,
			collisionPlanFactory);
	}

	static ApplicationCapture captureWithDisposableCollisionApplications(
		final RegionManager regionManager,
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int maximumSources,
		final CollisionPlanFactory collisionPlanFactory) {
		List<LayeredPackedRegionIsolatedAuthoredCollisionVerification>
			applications =
				new ArrayList<
					LayeredPackedRegionIsolatedAuthoredCollisionVerification>();
		LayeredPackedRegionAuthoredCollisionVerificationBatch baseline =
			new LayeredPackedRegionAuthoredCollisionVerificationBatch(
				regionManager, boundary, reloadRecipe, maximumSources,
				collisionPlanFactory, applications);
		return new ApplicationCapture(baseline, applications);
	}

	static CombinedStateCapture captureWithCombinedSourceStates(
		final RegionManager regionManager,
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int maximumSources,
		final CollisionPlanFactory collisionPlanFactory) {
		List<LayeredPackedRegionIsolatedAuthoredSourceStateVerification>
			combinedStates =
				new ArrayList<
					LayeredPackedRegionIsolatedAuthoredSourceStateVerification>();
		LayeredPackedRegionAuthoredCollisionVerificationBatch baseline =
			new LayeredPackedRegionAuthoredCollisionVerificationBatch(
				regionManager, boundary, reloadRecipe, maximumSources,
				collisionPlanFactory, null, combinedStates, null);
		return new CombinedStateCapture(baseline, combinedStates);
	}

	static TransactionalStateCapture captureWithTransactionalSourceStates(
		final RegionManager regionManager,
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int maximumSources,
		final CollisionPlanFactory collisionPlanFactory) {
		List<
			LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification>
				transactionalStates =
					new ArrayList<
						LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification>();
		LayeredPackedRegionAuthoredCollisionVerificationBatch baseline =
			new LayeredPackedRegionAuthoredCollisionVerificationBatch(
				regionManager, boundary, reloadRecipe, maximumSources,
				collisionPlanFactory, null, null, transactionalStates);
		return new TransactionalStateCapture(baseline, transactionalStates);
	}

	private static String fingerprint(
		final List<SourceVerification> sources) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			updateInt(digest, sources.size());
			for (SourceVerification source : sources) {
				updateInt(digest, source.getSourceOrdinal());
				updateInt(digest, source.getPackedRegionX());
				updateInt(digest, source.getPackedRegionY());
				updateString(
					digest, source.getTerrainFingerprintSha256());
				updateString(
					digest, source.getAuthoredReplayFingerprintSha256());
				updateString(
					digest, source.getDefinitionCaptureFingerprintSha256());
				updateString(
					digest, source.getCollisionFootprintFingerprintSha256());
			}
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest.digest()) {
				result.append(String.format("%02x", value & 0xff));
			}
			return result.toString();
		} catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(
				"SHA-256 is unavailable for authored collision verification",
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
	public long getResidencyMirrorVersion() { return residencyMirrorVersion; }
	public long getAuthoredGeneration() { return authoredGeneration; }
	public List<SourceVerification> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public long getReplayPlacementCount() { return replayPlacementCount; }
	public long getAuthoredObjectFootprintCount() {
		return authoredObjectFootprintCount;
	}
	public long getDefinitionBackedObjectCount() {
		return definitionBackedObjectCount;
	}
	public long getSpecialCollisionlessObjectCount() {
		return specialCollisionlessObjectCount;
	}
	public long getZeroContributionObjectCount() {
		return zeroContributionObjectCount;
	}
	public long getCrossSourceCollisionObjectCount() {
		return crossSourceCollisionObjectCount;
	}
	public long getCollisionBeyondAuthoredDependencyObjectCount() {
		return collisionBeyondAuthoredDependencyObjectCount;
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
	public String getFingerprintSha256() { return fingerprintSha256; }
	public int getDisposableRegionConstructionCount() {
		return Math.multiplyExact(getSourceCount(), 2);
	}
	public int getDisposableTerrainApplyCount() {
		return Math.multiplyExact(getSourceCount(), 2);
	}
	public int getDisposableObjectMembershipApplyCount() {
		return getSourceCount();
	}
	public int getUsableRegionContainerCount() { return 0; }

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isAllSourcesVerified() { return true; }
	public boolean isRuntimeDefinitionCapturePerformed() { return true; }
	public boolean isCollisionFootprintDerivationPerformed() { return true; }
	public boolean isCollisionApplied() { return false; }
	public boolean isCollisionRegistrationAttached() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isTerrainAppliedToRuntimeSource() { return false; }
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

	interface CollisionPlanFactory {
		LayeredPackedRegionAuthoredCollisionFootprintPlan define(
			LayeredPackedRegionAuthoredReplayPlan replay,
			LayeredPackedRegionIsolatedAuthoredObjectVerification membership);
	}

	static final class ApplicationCapture {
		private final LayeredPackedRegionAuthoredCollisionVerificationBatch
			baseline;
		private final List<
			LayeredPackedRegionIsolatedAuthoredCollisionVerification>
				applications;

		private ApplicationCapture(
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				baseline,
			final List<
				LayeredPackedRegionIsolatedAuthoredCollisionVerification>
					applications) {
			this.baseline = Objects.requireNonNull(baseline, "baseline");
			if (applications.size() != baseline.getSourceCount()) {
				throw new IllegalArgumentException(
					"Disposable collision application count is incomplete");
			}
			this.applications = Collections.unmodifiableList(
				new ArrayList<
					LayeredPackedRegionIsolatedAuthoredCollisionVerification>(
						applications));
		}

		LayeredPackedRegionAuthoredCollisionVerificationBatch getBaseline() {
			return baseline;
		}

		List<LayeredPackedRegionIsolatedAuthoredCollisionVerification>
			getApplications() {
			return applications;
		}
	}

	static final class CombinedStateCapture {
		private final LayeredPackedRegionAuthoredCollisionVerificationBatch
			baseline;
		private final List<
			LayeredPackedRegionIsolatedAuthoredSourceStateVerification>
				combinedStates;

		private CombinedStateCapture(
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				baseline,
			final List<
				LayeredPackedRegionIsolatedAuthoredSourceStateVerification>
					combinedStates) {
			this.baseline = Objects.requireNonNull(baseline, "baseline");
			if (combinedStates.size() != baseline.getSourceCount()) {
				throw new IllegalArgumentException(
					"Combined disposable source-state count is incomplete");
			}
			this.combinedStates = Collections.unmodifiableList(
				new ArrayList<
					LayeredPackedRegionIsolatedAuthoredSourceStateVerification>(
						combinedStates));
		}

		LayeredPackedRegionAuthoredCollisionVerificationBatch getBaseline() {
			return baseline;
		}

		List<LayeredPackedRegionIsolatedAuthoredSourceStateVerification>
			getCombinedStates() {
			return combinedStates;
		}
	}

	static final class TransactionalStateCapture {
		private final LayeredPackedRegionAuthoredCollisionVerificationBatch
			baseline;
		private final List<
			LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification>
				transactionalStates;

		private TransactionalStateCapture(
			final LayeredPackedRegionAuthoredCollisionVerificationBatch
				baseline,
			final List<
				LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification>
					transactionalStates) {
			this.baseline = Objects.requireNonNull(baseline, "baseline");
			if (transactionalStates.size() != baseline.getSourceCount()) {
				throw new IllegalArgumentException(
					"Transactional disposable source-state count is incomplete");
			}
			this.transactionalStates = Collections.unmodifiableList(
				new ArrayList<
					LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification>(
						transactionalStates));
		}

		LayeredPackedRegionAuthoredCollisionVerificationBatch getBaseline() {
			return baseline;
		}

		List<
			LayeredPackedRegionIsolatedTransactionalAuthoredSourceVerification>
				getTransactionalStates() {
			return transactionalStates;
		}
	}

	/** Count/fingerprint-only receipt for one exact selected source. */
	public static final class SourceVerification {
		private final int sourceOrdinal;
		private final int packedRegionX;
		private final int packedRegionY;
		private final int replayPlacementCount;
		private final int authoredObjectFootprintCount;
		private final int definitionBackedObjectCount;
		private final int specialCollisionlessObjectCount;
		private final int zeroContributionObjectCount;
		private final int crossSourceCollisionObjectCount;
		private final int collisionBeyondAuthoredDependencyObjectCount;
		private final int contributionTileReferenceCount;
		private final int requiredRegionReferenceCount;
		private final int uniqueRequiredRegionCount;
		private final String terrainFingerprintSha256;
		private final String authoredReplayFingerprintSha256;
		private final String definitionCaptureFingerprintSha256;
		private final String collisionFootprintFingerprintSha256;

		private SourceVerification(
			final int expectedOrdinal,
			final LayeredPackedRegionIsolatedTerrainVerification terrain,
			final LayeredPackedRegionAuthoredReplayPlan replayPlan,
			final LayeredPackedRegionIsolatedAuthoredObjectVerification
				membership,
			final LayeredPackedRegionAuthoredCollisionFootprintPlan
				collisionPlan) {
			LayeredPackedRegionIsolatedTerrainVerification terrainReceipt =
				Objects.requireNonNull(terrain, "terrain");
			LayeredPackedRegionAuthoredReplayPlan replay =
				Objects.requireNonNull(replayPlan, "replayPlan");
			LayeredPackedRegionIsolatedAuthoredObjectVerification
				membershipReceipt =
					Objects.requireNonNull(membership, "membership");
			LayeredPackedRegionAuthoredCollisionFootprintPlan collision =
				Objects.requireNonNull(collisionPlan, "collisionPlan");
			if (expectedOrdinal < 0
				|| terrainReceipt.getSourceOrdinal() != expectedOrdinal
				|| replay.getSelectedSourceOrdinal() != expectedOrdinal
				|| membershipReceipt.getSourceOrdinal() != expectedOrdinal
				|| collision.getSourceOrdinal() != expectedOrdinal
				|| terrainReceipt.getPackedRegionX()
					!= replay.getPackedRegionX()
				|| terrainReceipt.getPackedRegionY()
					!= replay.getPackedRegionY()
				|| membershipReceipt.getPackedRegionX()
					!= replay.getPackedRegionX()
				|| membershipReceipt.getPackedRegionY()
					!= replay.getPackedRegionY()
				|| collision.getPackedRegionX() != replay.getPackedRegionX()
				|| collision.getPackedRegionY() != replay.getPackedRegionY()
				|| replay.getPlacementCount()
					!= membershipReceipt.getReplayPlacementCount()
				|| replay.getAuthoredObjectPlacementCount()
					!= membershipReceipt.getConstructedObjectCount()
				|| replay.getAuthoredObjectPlacementCount()
					!= collision.getObjectFootprintCount()
				|| !terrainReceipt.getTerrainFingerprintSha256().equals(
					membershipReceipt.getTerrainFingerprintSha256())
				|| !replay.getFingerprintSha256().equals(
					membershipReceipt
						.getAuthoredReplayFingerprintSha256())
				|| !replay.getFingerprintSha256().equals(
					collision.getAuthoredReplayFingerprintSha256())
				|| !terrainReceipt.isVerificationOnly()
				|| !membershipReceipt.isVerificationOnly()
				|| !membershipReceipt
					.isExactObjectMembershipMatchedAfterReplay()
				|| !collision.isRuntimeDefinitionCapturePerformed()
				|| !collision.isRegisterFootprintDerived()
				|| collision.getDefinitionCaptureFingerprintSha256() == null
				|| collision.isRegionBoundaryAcquired()
				|| collision.isCollisionApplied()
				|| collision.isCollisionRegistrationAttached()
				|| collision.isRuntimeSourceMutated()
				|| collision.isRuntimeHandleRetained()
				|| collision.isRegionRegistryMutated()
				|| collision.isResidencyMirrorMutated()
				|| collision.isVisibilityCacheMutated()
				|| collision.isArrivalGate()
				|| collision.isVisibilityReleased()
				|| collision.isLifecycleAuthority()) {
				throw new IllegalArgumentException(
					"Authored collision source verification is unsafe");
			}
			this.sourceOrdinal = expectedOrdinal;
			this.packedRegionX = replay.getPackedRegionX();
			this.packedRegionY = replay.getPackedRegionY();
			this.replayPlacementCount = replay.getPlacementCount();
			this.authoredObjectFootprintCount =
				collision.getObjectFootprintCount();
			this.definitionBackedObjectCount =
				collision.getDefinitionBackedObjectCount();
			this.specialCollisionlessObjectCount =
				collision.getSpecialCollisionlessObjectCount();
			this.zeroContributionObjectCount =
				collision.getZeroContributionObjectCount();
			this.crossSourceCollisionObjectCount =
				collision.getCrossSourceCollisionObjectCount();
			this.collisionBeyondAuthoredDependencyObjectCount =
				collision
					.getCollisionBeyondAuthoredDependencyObjectCount();
			this.contributionTileReferenceCount =
				collision.getContributionTileReferenceCount();
			this.requiredRegionReferenceCount =
				collision.getRequiredRegionReferenceCount();
			this.uniqueRequiredRegionCount =
				collision.getUniqueRequiredRegionCount();
			this.terrainFingerprintSha256 =
				terrainReceipt.getTerrainFingerprintSha256();
			this.authoredReplayFingerprintSha256 =
				replay.getFingerprintSha256();
			this.definitionCaptureFingerprintSha256 =
				collision.getDefinitionCaptureFingerprintSha256();
			this.collisionFootprintFingerprintSha256 =
				collision.getFingerprintSha256();
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
		public int getDefinitionBackedObjectCount() {
			return definitionBackedObjectCount;
		}
		public int getSpecialCollisionlessObjectCount() {
			return specialCollisionlessObjectCount;
		}
		public int getZeroContributionObjectCount() {
			return zeroContributionObjectCount;
		}
		public int getCrossSourceCollisionObjectCount() {
			return crossSourceCollisionObjectCount;
		}
		public int getCollisionBeyondAuthoredDependencyObjectCount() {
			return collisionBeyondAuthoredDependencyObjectCount;
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
	}
}
