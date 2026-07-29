package com.openrsc.server.model.world.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded exact-source summary of disposable static-terrain verification.
 *
 * <p>Only the explicit private preservation diagnostic may call this batch.
 * Each source is captured under the already active lifecycle boundary,
 * reduced to static terrain, applied to one disposable unregistered Region,
 * verified, and discarded before the next source. Only count/fingerprint
 * summaries survive.</p>
 */
public final class LayeredPackedRegionTerrainVerificationBatch {
	public static final int MAXIMUM_VERIFICATION_SOURCES = 128;

	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final List<SourceVerification> sources;
	private final long verifiedTileCount;
	private final long terrainBlockedTileCount;
	private final long terrainCollisionMaskTileCount;
	private final long terrainProjectileBlockedTileCount;
	private final long sealedBaseTraversalTileCount;

	private LayeredPackedRegionTerrainVerificationBatch(
		final RegionManager regionManager,
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int maximumSources) {
		RegionManager manager =
			Objects.requireNonNull(regionManager, "regionManager");
		LayeredPackedRegionSourceLifecycleBoundary checkedBoundary =
			Objects.requireNonNull(boundary, "boundary");
		LayeredPackedRegionReloadRecipe reload =
			Objects.requireNonNull(reloadRecipe, "reloadRecipe");
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
				"Terrain verification batch lacks one exact inert boundary");
		}
		this.generation = reload.getGeneration();
		this.requirementsObservedAtTick =
			reload.getRequirementsObservedAtTick();
		this.observedAtTick = reload.getObservedAtTick();
		this.residencyMirrorVersion = reload.getResidencyMirrorVersion();
		this.authoredGeneration = reload.getAuthoredGeneration();

		List<SourceVerification> verified =
			new ArrayList<SourceVerification>(reload.getSourceCount());
		long tileCount = 0L;
		long terrainBlocked = 0L;
		long terrainCollision = 0L;
		long terrainProjectile = 0L;
		long sealedBase = 0L;
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
			LayeredPackedRegionIsolatedTerrainVerification receipt =
				LayeredPackedRegionIsolatedTerrainVerifier.verify(
					manager, container, terrain);
			SourceVerification source =
				new SourceVerification(receipt);
			verified.add(source);
			tileCount = Math.addExact(
				tileCount, source.getVerifiedTileCount());
			terrainBlocked = Math.addExact(
				terrainBlocked, source.getTerrainBlockedTileCount());
			terrainCollision = Math.addExact(
				terrainCollision,
				source.getTerrainCollisionMaskTileCount());
			terrainProjectile = Math.addExact(
				terrainProjectile,
				source.getTerrainProjectileBlockedTileCount());
			sealedBase = Math.addExact(
				sealedBase, source.getSealedBaseTraversalTileCount());
		}
		this.sources = Collections.unmodifiableList(verified);
		this.verifiedTileCount = tileCount;
		this.terrainBlockedTileCount = terrainBlocked;
		this.terrainCollisionMaskTileCount = terrainCollision;
		this.terrainProjectileBlockedTileCount = terrainProjectile;
		this.sealedBaseTraversalTileCount = sealedBase;
	}

	public static LayeredPackedRegionTerrainVerificationBatch capture(
		final RegionManager regionManager,
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int maximumSources) {
		return new LayeredPackedRegionTerrainVerificationBatch(
			regionManager, boundary, reloadRecipe, maximumSources);
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
	public long getVerifiedTileCount() { return verifiedTileCount; }
	public long getTerrainBlockedTileCount() {
		return terrainBlockedTileCount;
	}
	public long getTerrainCollisionMaskTileCount() {
		return terrainCollisionMaskTileCount;
	}
	public long getTerrainProjectileBlockedTileCount() {
		return terrainProjectileBlockedTileCount;
	}
	public long getSealedBaseTraversalTileCount() {
		return sealedBaseTraversalTileCount;
	}
	public int getDisposableRegionConstructionCount() {
		return getSourceCount();
	}
	public int getDisposableTerrainApplyCount() {
		return getSourceCount();
	}
	public int getUsableRegionContainerCount() { return 0; }

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedSummaryOnly() { return true; }
	public boolean isAllSourcesVerified() { return true; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isTerrainAppliedToRuntimeSource() { return false; }
	public boolean isAuthoredReplayPerformed() { return false; }
	public boolean isDynamicCollisionRebuildPerformed() { return false; }
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
		private final int verifiedTileCount;
		private final int terrainBlockedTileCount;
		private final int terrainCollisionMaskTileCount;
		private final int terrainProjectileBlockedTileCount;
		private final int sealedBaseTraversalTileCount;
		private final String terrainFingerprintSha256;

		private SourceVerification(
			final LayeredPackedRegionIsolatedTerrainVerification receipt) {
			LayeredPackedRegionIsolatedTerrainVerification checked =
				Objects.requireNonNull(receipt, "receipt");
			if (!checked.isVerificationOnly()
				|| !checked.isDisposableRegionConstructed()
				|| !checked.isTerrainApplyPerformedOnDisposableRegion()
				|| !checked.isAllTerrainTilesMatchedAfterApply()
				|| !checked.isDynamicProductsAbsentAfterApply()
				|| !checked.isEmptyEntityMembershipMatchedAfterApply()
				|| checked.isUsableRegionContainerReturned()
				|| checked.isRuntimeHandleRetained()
				|| checked.isRegionRegistryMutated()
				|| checked.isResidencyMirrorMutated()
				|| checked.isVisibilityCacheMutated()
				|| checked.isLifecycleAuthority()) {
				throw new IllegalArgumentException(
					"Source terrain verification receipt is unsafe");
			}
			this.sourceOrdinal = checked.getSourceOrdinal();
			this.packedRegionX = checked.getPackedRegionX();
			this.packedRegionY = checked.getPackedRegionY();
			this.verifiedTileCount = checked.getVerifiedTileCount();
			this.terrainBlockedTileCount =
				checked.getTerrainBlockedTileCount();
			this.terrainCollisionMaskTileCount =
				checked.getTerrainCollisionMaskTileCount();
			this.terrainProjectileBlockedTileCount =
				checked.getTerrainProjectileBlockedTileCount();
			this.sealedBaseTraversalTileCount =
				checked.getSealedBaseTraversalTileCount();
			this.terrainFingerprintSha256 =
				checked.getTerrainFingerprintSha256();
		}

		public int getSourceOrdinal() { return sourceOrdinal; }
		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getVerifiedTileCount() { return verifiedTileCount; }
		public int getTerrainBlockedTileCount() {
			return terrainBlockedTileCount;
		}
		public int getTerrainCollisionMaskTileCount() {
			return terrainCollisionMaskTileCount;
		}
		public int getTerrainProjectileBlockedTileCount() {
			return terrainProjectileBlockedTileCount;
		}
		public int getSealedBaseTraversalTileCount() {
			return sealedBaseTraversalTileCount;
		}
		public String getTerrainFingerprintSha256() {
			return terrainFingerprintSha256;
		}
	}
}
