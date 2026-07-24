package com.openrsc.server.model.world.region;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.util.rsc.CollisionFlag;

import java.util.Objects;

/**
 * Detached construction contract for one sealed, unregistered packed Region
 * container.
 *
 * <p>The contract binds one exact source ordinal from a
 * {@link LayeredPackedRegionReloadRecipe} to the current Region storage shape
 * and blank {@link TileValue} defaults. It copies scalars only. It does not
 * retain the recipe, a source entry, a RegionManager, a Region, a TileValue, or
 * any entity or collection handle.</p>
 *
 * <p>A blank container is deliberately sealed. Its independent tile slots
 * begin with {@link CollisionFlag#FULL_BLOCK}, empty metadata, no collision
 * product ownership, and no entity membership. Terrain initialization,
 * authored replay, active-family preservation, exact collision
 * reconstruction, registration, rollback, arrival gating, and visibility
 * release remain separate mandatory stages.</p>
 */
public final class LayeredPackedRegionBlankContainerPlan {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final int sourceOrdinal;
	private final int packedRegionX;
	private final int packedRegionY;
	private final boolean tileStorageAvailableAtObservation;
	private final int authoredPlacementCount;
	private final int playerCountAtObservation;
	private final int npcCountAtObservation;
	private final int dynamicObjectCountAtObservation;
	private final int groundItemCountAtObservation;
	private final int collisionProductTileCountAtObservation;
	private final int containerSideTileCount;
	private final int containerTileSlotCount;

	private LayeredPackedRegionBlankContainerPlan(
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int sourceOrdinal) {
		LayeredPackedRegionReloadRecipe checked =
			Objects.requireNonNull(reloadRecipe, "reloadRecipe");
		if (!checked.isDetachedDefinitionComplete()
			|| checked.isExecutableReload()
			|| checked.isRegionContainerCreated()
			|| checked.isSourceAbsencePerformed()
			|| checked.isSourceReconstructionPerformed()
			|| checked.isAuthoredReplayPerformed()
			|| checked.isCollisionRebuildPerformed()
			|| checked.isRuntimeHandleRetained()
			|| checked.isRegionRegistryMutated()
			|| checked.isResidencyMirrorMutated()
			|| checked.isVisibilityCacheMutated()
			|| checked.isArrivalGate()
			|| checked.isLifecycleAuthority()) {
			throw new IllegalArgumentException(
				"Blank-container plan requires one detached inert reload recipe");
		}
		if (sourceOrdinal < 0 || sourceOrdinal >= checked.getSourceCount()) {
			throw new IndexOutOfBoundsException(
				"Packed-source ordinal is outside the reload recipe");
		}
		LayeredPackedRegionReloadRecipe.SourceRecipe source =
			checked.getSources().get(sourceOrdinal);
		this.generation = checked.getGeneration();
		this.requirementsObservedAtTick =
			checked.getRequirementsObservedAtTick();
		this.observedAtTick = checked.getObservedAtTick();
		this.residencyMirrorVersion = checked.getResidencyMirrorVersion();
		this.authoredGeneration = checked.getAuthoredGeneration();
		this.sourceOrdinal = sourceOrdinal;
		this.packedRegionX = source.getPackedRegionX();
		this.packedRegionY = source.getPackedRegionY();
		this.tileStorageAvailableAtObservation =
			source.isTileStorageAvailableAtObservation();
		this.authoredPlacementCount = source.getAuthoredPlacementCount();
		this.playerCountAtObservation =
			source.getPlayerCountAtObservation();
		this.npcCountAtObservation = source.getNpcCountAtObservation();
		this.dynamicObjectCountAtObservation =
			source.getDynamicObjectCountAtObservation();
		this.groundItemCountAtObservation =
			source.getGroundItemCountAtObservation();
		this.collisionProductTileCountAtObservation =
			source.getCollisionProductTileCountAtObservation();
		this.containerSideTileCount = Constants.REGION_SIZE;
		this.containerTileSlotCount = Math.multiplyExact(
			containerSideTileCount, containerSideTileCount);
		if (containerSideTileCount <= 0) {
			throw new IllegalStateException(
				"Region container side length must be positive");
		}
	}

	static LayeredPackedRegionBlankContainerPlan define(
		final LayeredPackedRegionReloadRecipe reloadRecipe,
		final int sourceOrdinal) {
		return new LayeredPackedRegionBlankContainerPlan(
			reloadRecipe, sourceOrdinal);
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
	public boolean wasTileStorageAvailableAtObservation() {
		return tileStorageAvailableAtObservation;
	}
	public int getAuthoredPlacementCount() {
		return authoredPlacementCount;
	}
	public int getPlayerCountAtObservation() {
		return playerCountAtObservation;
	}
	public int getNpcCountAtObservation() {
		return npcCountAtObservation;
	}
	public int getDynamicObjectCountAtObservation() {
		return dynamicObjectCountAtObservation;
	}
	public int getGroundItemCountAtObservation() {
		return groundItemCountAtObservation;
	}
	public int getCollisionProductTileCountAtObservation() {
		return collisionProductTileCountAtObservation;
	}

	public int getContainerSideTileCount() {
		return containerSideTileCount;
	}
	public int getContainerTileSlotCount() {
		return containerTileSlotCount;
	}
	public int getInitialTraversalMask() {
		return CollisionFlag.FULL_BLOCK;
	}
	public int getInitialDiagonalWallValue() { return 0; }
	public int getInitialHorizontalWallValue() { return 0; }
	public int getInitialOverlayValue() { return 0; }
	public int getInitialVerticalWallValue() { return 0; }
	public int getInitialElevationValue() { return 0; }
	public int getInitialCollisionProductTileCount() { return 0; }
	public int getInitialPlayerCount() { return 0; }
	public int getInitialNpcCount() { return 0; }
	public int getInitialObjectCount() { return 0; }
	public int getInitialGroundItemCount() { return 0; }

	public boolean isInitialProjectileAllowed() { return false; }
	public boolean isInitialOriginalProjectileAllowed() { return false; }
	public boolean isExpandedTileStorageRequired() { return true; }
	public boolean isIndependentMutableTilePerSlotRequired() { return true; }
	public boolean isSealedUntilTerrainInitialization() { return true; }
	public boolean isTerrainInitializationRequired() { return true; }
	public boolean isAuthoredReplayRequired() {
		return authoredPlacementCount > 0;
	}
	public boolean isPlayerPreservationRequired() {
		return playerCountAtObservation > 0;
	}
	public boolean isNpcPreservationRequired() {
		return npcCountAtObservation > 0;
	}
	public boolean isDynamicObjectPreservationRequired() {
		return dynamicObjectCountAtObservation > 0;
	}
	public boolean isGroundItemPreservationRequired() {
		return groundItemCountAtObservation > 0;
	}
	public boolean isCollisionRebuildRequired() { return true; }
	public boolean isRegionManagerBindingRequired() { return true; }
	public boolean isTransactionalRegistrationRequired() { return true; }
	public boolean isRollbackRequired() { return true; }
	public boolean isArrivalGateRequired() { return true; }
	public boolean isVisibilityGateRequired() { return true; }

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedConstructionContract() { return true; }
	public boolean isConstructionDefinitionComplete() { return true; }
	public boolean isExecutableConstruction() { return false; }
	public boolean isRegionContainerCreated() { return false; }
	public boolean isTileStorageAllocated() { return false; }
	public boolean isRegionManagerBound() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isTerrainInitialized() { return false; }
	public boolean isAuthoredReplayPerformed() { return false; }
	public boolean isActiveFamilyPreservationPerformed() { return false; }
	public boolean isCollisionRebuildPerformed() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isVisibilityReleased() { return false; }
	public boolean isLifecycleAuthority() { return false; }
}
