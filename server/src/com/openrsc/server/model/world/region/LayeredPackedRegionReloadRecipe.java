package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredReconstructionRecipe;
import com.openrsc.server.model.world.coordinate
	.LayeredPackedRegionAuthoredReconstructionRecipe.ReconstructionPlacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Detached exact authored inputs for rebuilding the containers represented by
 * one selected packed-source set.
 *
 * <p>The value binds an already captured source-absence preflight to the
 * immutable final-live authored recipe while the real Region lifecycle
 * boundary is still active. A source absent from the authored recipe has an
 * exact empty authored replay; it is not treated as missing metadata.</p>
 *
 * <p>This is deliberately not an executable loader. Active players, NPCs,
 * dynamic objects, ground items, collision products, scheduler state, and
 * visibility state remain separate lifecycle obligations. The value retains
 * no Region, tile, entity, registry, archive, event, monitor, or other runtime
 * handle and cannot create, remove, reload, or reconstruct a Region.</p>
 */
public final class LayeredPackedRegionReloadRecipe {
	private final long generation;
	private final long requirementsObservedAtTick;
	private final long observedAtTick;
	private final long residencyMirrorVersion;
	private final long authoredGeneration;
	private final List<SourceRecipe> sources;
	private final int authoredSourceCount;
	private final int authoredPlacementCount;
	private final int manifestPlacementCount;
	private final int supersededPlacementCount;
	private final int affectedSourceReferenceCount;
	private final long playerCount;
	private final long npcCount;
	private final long dynamicObjectCount;
	private final long groundItemCount;
	private final long collisionProductTileCount;

	private LayeredPackedRegionReloadRecipe(
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionSourceAbsencePreflight preflight,
		final LayeredPackedRegionAuthoredReconstructionRecipe authoredRecipe,
		final boolean regionLifecycleBoundaryHeld) {
		if (!regionLifecycleBoundaryHeld
			|| boundary.getGeneration() != preflight.getGeneration()
			|| boundary.getGeneration() != authoredRecipe.getGeneration()
			|| boundary.getRequirementsObservedAtTick()
				!= preflight.getRequirementsObservedAtTick()
			|| boundary.getResidencyMirrorVersion()
				!= preflight.getResidencyMirrorVersion()
			|| boundary.getSelectedSourceCount() != preflight.getSourceCount()
			|| preflight.getObservedAtTick()
				< boundary.getRequirementsObservedAtTick()) {
			throw new IllegalArgumentException(
				"Packed-source reload recipe inputs do not share one boundary");
		}
		this.generation = boundary.getGeneration();
		this.requirementsObservedAtTick =
			boundary.getRequirementsObservedAtTick();
		this.observedAtTick = preflight.getObservedAtTick();
		this.residencyMirrorVersion = boundary.getResidencyMirrorVersion();
		this.authoredGeneration = authoredRecipe.getGeneration();

		List<SourceRecipe> recipes =
			new ArrayList<SourceRecipe>(boundary.getSelectedSourceCount());
		int authoredSources = 0;
		int authoredPlacements = 0;
		int manifestPlacements = 0;
		int supersededPlacements = 0;
		int affectedReferences = 0;
		for (int index = 0;
			index < boundary.getSelectedSourceCount(); index++) {
			LayeredPackedRegionSourceLifecycleBoundary.PackedSource selected =
				boundary.getSelectedSources().get(index);
			LayeredPackedRegionSourceAbsencePreflight.SourceAssessment observed =
				preflight.getSources().get(index);
			if (selected.getPackedRegionX() != observed.getPackedRegionX()
				|| selected.getPackedRegionY()
					!= observed.getPackedRegionY()) {
				throw new IllegalArgumentException(
					"Packed-source reload recipe order does not match");
			}
			LayeredPackedRegionAuthoredReconstructionRecipe.PackedSourceRecipe
				authored = authoredRecipe.findSource(
					selected.getPackedRegionX(),
					selected.getPackedRegionY());
			SourceRecipe source = new SourceRecipe(observed, authored);
			recipes.add(source);
			authoredSources += source.isAuthoredSourceDeclared() ? 1 : 0;
			authoredPlacements = Math.addExact(
				authoredPlacements, source.getAuthoredPlacementCount());
			manifestPlacements = Math.addExact(
				manifestPlacements, source.getManifestPlacementCount());
			supersededPlacements = Math.addExact(
				supersededPlacements, source.getSupersededPlacementCount());
			affectedReferences = Math.addExact(
				affectedReferences,
				source.getAffectedSourceReferenceCount());
		}
		this.sources = Collections.unmodifiableList(recipes);
		this.authoredSourceCount = authoredSources;
		this.authoredPlacementCount = authoredPlacements;
		this.manifestPlacementCount = manifestPlacements;
		this.supersededPlacementCount = supersededPlacements;
		this.affectedSourceReferenceCount = affectedReferences;
		this.playerCount = preflight.getPlayerCount();
		this.npcCount = preflight.getNpcCount();
		this.dynamicObjectCount = preflight.getDynamicObjectCount();
		this.groundItemCount = preflight.getGroundItemCount();
		this.collisionProductTileCount =
			preflight.getCollisionProductTileCount();
	}

	static LayeredPackedRegionReloadRecipe compose(
		final LayeredPackedRegionSourceLifecycleBoundary boundary,
		final LayeredPackedRegionSourceAbsencePreflight preflight,
		final LayeredPackedRegionAuthoredReconstructionRecipe authoredRecipe,
		final boolean regionLifecycleBoundaryHeld) {
		return new LayeredPackedRegionReloadRecipe(
			Objects.requireNonNull(boundary, "boundary"),
			Objects.requireNonNull(preflight, "preflight"),
			Objects.requireNonNull(authoredRecipe, "authoredRecipe"),
			regionLifecycleBoundaryHeld);
	}

	public long getGeneration() { return generation; }
	public long getRequirementsObservedAtTick() {
		return requirementsObservedAtTick;
	}
	public long getObservedAtTick() { return observedAtTick; }
	public long getResidencyMirrorVersion() { return residencyMirrorVersion; }
	public long getAuthoredGeneration() { return authoredGeneration; }
	public List<SourceRecipe> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getAuthoredSourceCount() { return authoredSourceCount; }
	public int getEmptyAuthoredSourceCount() {
		return getSourceCount() - authoredSourceCount;
	}
	public int getAuthoredPlacementCount() {
		return authoredPlacementCount;
	}
	public int getManifestPlacementCount() {
		return manifestPlacementCount;
	}
	public int getSupersededPlacementCount() {
		return supersededPlacementCount;
	}
	public int getAffectedSourceReferenceCount() {
		return affectedSourceReferenceCount;
	}
	public long getPlayerCount() { return playerCount; }
	public long getNpcCount() { return npcCount; }
	public long getDynamicObjectCount() { return dynamicObjectCount; }
	public long getGroundItemCount() { return groundItemCount; }
	public long getCollisionProductTileCount() {
		return collisionProductTileCount;
	}

	public boolean isPointInTimeOnly() { return true; }
	public boolean isDetachedDefinitionComplete() { return true; }
	public boolean isExecutableReload() { return false; }
	public boolean isRegionContainerCreated() { return false; }
	public boolean isSourceAbsencePerformed() { return false; }
	public boolean isSourceReconstructionPerformed() { return false; }
	public boolean isAuthoredReplayPerformed() { return false; }
	public boolean isCollisionRebuildPerformed() { return false; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRegionRegistryMutated() { return false; }
	public boolean isResidencyMirrorMutated() { return false; }
	public boolean isVisibilityCacheMutated() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** Exact selected-source definition plus its observed unresolved families. */
	public static final class SourceRecipe {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean tileStorageAvailableAtObservation;
		private final int playerCountAtObservation;
		private final int npcCountAtObservation;
		private final int dynamicObjectCountAtObservation;
		private final int groundItemCountAtObservation;
		private final int collisionProductTileCountAtObservation;
		private final boolean authoredSourceDeclared;
		private final int manifestPlacementCount;
		private final int supersededPlacementCount;
		private final List<ReconstructionPlacement> authoredPlacements;
		private final int affectedSourceReferenceCount;

		private SourceRecipe(
			final LayeredPackedRegionSourceAbsencePreflight.SourceAssessment
				observed,
			final LayeredPackedRegionAuthoredReconstructionRecipe
				.PackedSourceRecipe authored) {
			this.packedRegionX = observed.getPackedRegionX();
			this.packedRegionY = observed.getPackedRegionY();
			this.tileStorageAvailableAtObservation =
				observed.isTileStorageAvailable();
			this.playerCountAtObservation = observed.getPlayerCount();
			this.npcCountAtObservation = observed.getNpcCount();
			this.dynamicObjectCountAtObservation =
				observed.getDynamicObjectCount();
			this.groundItemCountAtObservation =
				observed.getGroundItemCount();
			this.collisionProductTileCountAtObservation =
				observed.getCollisionProductTileCount();
			this.authoredSourceDeclared = authored != null;
			this.manifestPlacementCount = authored == null
				? 0 : authored.getManifestPlacementCount();
			this.supersededPlacementCount = authored == null
				? 0 : authored.getSupersededPlacementCount();
			this.authoredPlacements = Collections.unmodifiableList(
				authored == null
					? Collections.<ReconstructionPlacement>emptyList()
					: new ArrayList<ReconstructionPlacement>(
						authored.getPlacements()));
			this.affectedSourceReferenceCount = authored == null
				? 0 : authored.getAffectedSourceReferenceCount();
			if (authored != null
				&& (authored.getPackedRegionX() != packedRegionX
					|| authored.getPackedRegionY() != packedRegionY
					|| authoredPlacements.size()
						!= manifestPlacementCount
							- supersededPlacementCount)) {
				throw new IllegalArgumentException(
					"Authored source recipe does not match selected source");
			}
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isTileStorageAvailableAtObservation() {
			return tileStorageAvailableAtObservation;
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
		public boolean isAuthoredSourceDeclared() {
			return authoredSourceDeclared;
		}
		public boolean isEmptyAuthoredReplay() {
			return authoredPlacements.isEmpty();
		}
		public int getManifestPlacementCount() {
			return manifestPlacementCount;
		}
		public int getSupersededPlacementCount() {
			return supersededPlacementCount;
		}
		public List<ReconstructionPlacement> getAuthoredPlacements() {
			return authoredPlacements;
		}
		public int getAuthoredPlacementCount() {
			return authoredPlacements.size();
		}
		public int getAffectedSourceReferenceCount() {
			return affectedSourceReferenceCount;
		}
	}
}
