package com.openrsc.server.model.world.region;

import java.util.Objects;

/**
 * Detached receipt proving one disposable, unregistered Region matched its
 * sealed blank-container contract at construction.
 *
 * <p>The Region is deliberately not retained or exposed. This receipt is not
 * a container, registration candidate, reload result, commit token, or
 * lifecycle permit.</p>
 */
public final class LayeredPackedRegionBlankContainerVerification {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int sourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final int verifiedTileSlotCount;
	private final int initialTraversalMask;

	private LayeredPackedRegionBlankContainerVerification(
		final LayeredPackedRegionBlankContainerPlan plan,
		final boolean regionManagerMatched,
		final boolean sourceCoordinatesMatched,
		final boolean collisionBoundaryCoordinatesMatched,
		final boolean expandedTileStorageMatched,
		final boolean independentMutableTilesMatched,
		final boolean sealedTileDefaultsMatched,
		final boolean emptyEntityMembershipMatched) {
		LayeredPackedRegionBlankContainerPlan checked =
			Objects.requireNonNull(plan, "plan");
		if (!checked.isDetachedConstructionContract()
			|| !checked.isConstructionDefinitionComplete()
			|| checked.isExecutableConstruction()
			|| checked.isRegionContainerCreated()
			|| checked.isTileStorageAllocated()
			|| checked.isRegionManagerBound()
			|| checked.isSourceAbsencePerformed()
			|| checked.isSourceReconstructionPerformed()
			|| checked.isTerrainInitialized()
			|| checked.isAuthoredReplayPerformed()
			|| checked.isActiveFamilyPreservationPerformed()
			|| checked.isCollisionRebuildPerformed()
			|| checked.isRuntimeHandleRetained()
			|| checked.isRegionRegistryMutated()
			|| checked.isResidencyMirrorMutated()
			|| checked.isVisibilityCacheMutated()
			|| checked.isArrivalGate()
			|| checked.isVisibilityReleased()
			|| checked.isLifecycleAuthority()
			|| !regionManagerMatched
			|| !sourceCoordinatesMatched
			|| !collisionBoundaryCoordinatesMatched
			|| !expandedTileStorageMatched
			|| !independentMutableTilesMatched
			|| !sealedTileDefaultsMatched
			|| !emptyEntityMembershipMatched) {
			throw new IllegalArgumentException(
				"Isolated Region does not satisfy its blank-container contract");
		}
		this.generation = checked.getGeneration();
		this.requirementsObservedAtTick =
			checked.getRequirementsObservedAtTick();
		this.observedAtTick = checked.getObservedAtTick();
		this.residencyMirrorVersion = checked.getResidencyMirrorVersion();
		this.authoredGeneration = checked.getAuthoredGeneration();
		this.sourceOrdinal = checked.getSourceOrdinal();
		this.packedRegionX = checked.getPackedRegionX();
		this.packedRegionY = checked.getPackedRegionY();
		this.verifiedTileSlotCount = checked.getContainerTileSlotCount();
		this.initialTraversalMask = checked.getInitialTraversalMask();
	}

	static LayeredPackedRegionBlankContainerVerification verified(
		final LayeredPackedRegionBlankContainerPlan plan,
		final boolean regionManagerMatched,
		final boolean sourceCoordinatesMatched,
		final boolean collisionBoundaryCoordinatesMatched,
		final boolean expandedTileStorageMatched,
		final boolean independentMutableTilesMatched,
		final boolean sealedTileDefaultsMatched,
		final boolean emptyEntityMembershipMatched) {
		return new LayeredPackedRegionBlankContainerVerification(
			plan, regionManagerMatched, sourceCoordinatesMatched,
			collisionBoundaryCoordinatesMatched, expandedTileStorageMatched,
			independentMutableTilesMatched, sealedTileDefaultsMatched,
			emptyEntityMembershipMatched);
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getObservedAtTick() { return observedAtTick; }
	public long getResidencyMirrorVersion() { return residencyMirrorVersion; }
	public long getAuthoredGeneration() { return authoredGeneration; }
	public int getSourceOrdinal() { return sourceOrdinal; }
	public int getPackedRegionX() { return packedRegionX; }
	public int getPackedRegionY() { return packedRegionY; }
	public int getVerifiedTileSlotCount() {
		return verifiedTileSlotCount;
	}
	public int getInitialTraversalMask() { return initialTraversalMask; }

	public boolean isVerificationOnly() { return true; }
	public boolean isDisposableRegionConstructed() { return true; }
	public boolean isRegionManagerMatched() { return true; }
	public boolean isSourceCoordinatesMatched() { return true; }
	public boolean isCollisionBoundaryCoordinatesMatched() { return true; }
	public boolean isExpandedTileStorageMatched() { return true; }
	public boolean isIndependentMutableTilesMatched() { return true; }
	public boolean isSealedTileDefaultsMatched() { return true; }
	public boolean isEmptyEntityMembershipMatched() { return true; }

	public boolean isExecutableReload() { return false; }
	public boolean isUsableRegionContainerReturned() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isTerrainInitialized() { return false; }
	public boolean isAuthoredReplayPerformed() { return false; }
	public boolean isActiveFamilyPreservationPerformed() { return false; }
	public boolean isCollisionRebuildPerformed() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }
}
