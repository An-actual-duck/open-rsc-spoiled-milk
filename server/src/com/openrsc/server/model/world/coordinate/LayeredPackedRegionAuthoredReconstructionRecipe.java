package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable final-live authored inputs grouped by their packed source.
 *
 * <p>The complete manifest remains construction history. This recipe omits
 * population-time collision predecessors while retaining each surviving
 * placement's original identity, source ordinal, primitive definition, and
 * conservative dependency envelope. An authored anchor owns its entry even
 * when that envelope reaches another packed source.</p>
 *
 * <p>This value retains only other immutable detached coordinate values. It
 * has no entity, Region, tile, archive, event, registry, cache, claim, permit,
 * lease, commit, construction, teardown, loading, or rollback authority.</p>
 */
public final class LayeredPackedRegionAuthoredReconstructionRecipe {
	public static final int MAXIMUM_PACKED_SOURCES =
		LayeredPackedRegionAuthoredPlacementManifest.MAXIMUM_PACKED_SOURCES;
	public static final int MAXIMUM_AUTHORED_PLACEMENTS =
		LayeredPackedRegionAuthoredPlacementManifest
			.MAXIMUM_AUTHORED_PLACEMENTS;

	private final long generation;
	private final int manifestPlacementCount;
	private final int supersededPlacementCount;
	private final List<PackedSourceRecipe> sources;
	private final int reconstructionPlacementCount;
	private final int crossSourcePlacementCount;
	private final int affectedSourceReferenceCount;
	private final int maximumAffectedSourceCount;

	private LayeredPackedRegionAuthoredReconstructionRecipe(
		final long generation,
		final int manifestPlacementCount,
		final int supersededPlacementCount,
		final List<PackedSourceRecipe> sources) {
		this.generation = generation;
		this.manifestPlacementCount = manifestPlacementCount;
		this.supersededPlacementCount = supersededPlacementCount;
		this.sources = Collections.unmodifiableList(
			new ArrayList<PackedSourceRecipe>(sources));
		int placements = 0;
		int crossSource = 0;
		int sourceReferences = 0;
		int maximumReferences = 0;
		for (PackedSourceRecipe source : sources) {
			placements = Math.addExact(
				placements, source.getReconstructionPlacementCount());
			crossSource = Math.addExact(
				crossSource, source.getCrossSourcePlacementCount());
			sourceReferences = Math.addExact(
				sourceReferences, source.getAffectedSourceReferenceCount());
			maximumReferences = Math.max(
				maximumReferences, source.getMaximumAffectedSourceCount());
		}
		if (placements != manifestPlacementCount - supersededPlacementCount) {
			throw new IllegalArgumentException(
				"Reconstruction recipe count differs from final-live outcome");
		}
		this.reconstructionPlacementCount = placements;
		this.crossSourcePlacementCount = crossSource;
		this.affectedSourceReferenceCount = sourceReferences;
		this.maximumAffectedSourceCount = maximumReferences;
	}

	public static LayeredPackedRegionAuthoredReconstructionRecipe empty() {
		return new LayeredPackedRegionAuthoredReconstructionRecipe(
			0L, 0, 0, Collections.<PackedSourceRecipe>emptyList());
	}

	/** Derives one exact inert recipe from three completed same-pass values. */
	public static LayeredPackedRegionAuthoredReconstructionRecipe derive(
		final LayeredPackedRegionAuthoredPlacementManifest manifest,
		final LayeredPackedRegionAuthoredPlacementDependencyInventory dependencies,
		final LayeredPackedRegionAuthoredPopulationOutcome populationOutcome) {
		if (manifest == null) {
			throw new NullPointerException("manifest");
		}
		if (dependencies == null) {
			throw new NullPointerException("dependencies");
		}
		if (populationOutcome == null) {
			throw new NullPointerException("populationOutcome");
		}
		if (!dependencies.isAlignedWith(manifest)) {
			throw new IllegalArgumentException(
				"Placement dependencies do not align with the manifest");
		}
		populationOutcome.validateAgainst(manifest);
		List<PackedSourceRecipe> recipes =
			new ArrayList<PackedSourceRecipe>(manifest.getSourceCount());
		for (LayeredPackedRegionAuthoredPlacementManifest.PackedSourceManifest
			source : manifest.getSources()) {
			LayeredPackedRegionAuthoredPlacementDependencyInventory
				.PackedSourceDependencies sourceDependencies =
					dependencies.findSource(
						source.getPackedRegionX(), source.getPackedRegionY());
			if (sourceDependencies == null
				|| sourceDependencies.getDependencyCount()
					!= source.getPlacementCount()) {
				throw new IllegalArgumentException(
					"Recipe source dependencies differ from the manifest");
			}
			List<ReconstructionPlacement> placements =
				new ArrayList<ReconstructionPlacement>(
					source.getPlacementCount());
			int superseded = 0;
			for (LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
				placement : source.getPlacements()) {
				LayeredPackedRegionAuthoredPlacementDependencyInventory
					.PlacementDependency dependency =
						sourceDependencies.findDependency(
							placement.getSourceOrdinal());
				if (dependency == null
					|| dependency.getKind() != placement.getKind()) {
					throw new IllegalArgumentException(
						"Recipe placement dependency differs from the manifest");
				}
				if (populationOutcome.isSuperseded(placement.getIdentity())) {
					superseded = Math.incrementExact(superseded);
					continue;
				}
				placements.add(new ReconstructionPlacement(
					placement, dependency));
			}
			recipes.add(new PackedSourceRecipe(
				source.getPackedRegionX(), source.getPackedRegionY(),
				source.getPlacementCount(), superseded, placements));
		}
		return new LayeredPackedRegionAuthoredReconstructionRecipe(
			manifest.getGeneration(), manifest.getPlacementCount(),
			populationOutcome.getSupersessionCount(), recipes);
	}

	public long getGeneration() { return generation; }
	public int getManifestPlacementCount() { return manifestPlacementCount; }
	public int getSupersededPlacementCount() {
		return supersededPlacementCount;
	}
	public List<PackedSourceRecipe> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getReconstructionPlacementCount() {
		return reconstructionPlacementCount;
	}
	public int getCrossSourcePlacementCount() {
		return crossSourcePlacementCount;
	}
	public int getAffectedSourceReferenceCount() {
		return affectedSourceReferenceCount;
	}
	public int getMaximumAffectedSourceCount() {
		return maximumAffectedSourceCount;
	}

	public PackedSourceRecipe findSource(
		final int packedRegionX,
		final int packedRegionY) {
		int low = 0;
		int high = sources.size() - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			PackedSourceRecipe source = sources.get(middle);
			int x = Integer.compare(
				source.getPackedRegionX(), packedRegionX);
			int comparison = x != 0 ? x : Integer.compare(
				source.getPackedRegionY(), packedRegionY);
			if (comparison < 0) {
				low = middle + 1;
			} else if (comparison > 0) {
				high = middle - 1;
			} else {
				return source;
			}
		}
		return null;
	}

	/** Final-live entries owned by one authored packed source. */
	public static final class PackedSourceRecipe {
		private final int packedRegionX;
		private final int packedRegionY;
		private final int manifestPlacementCount;
		private final int supersededPlacementCount;
		private final List<ReconstructionPlacement> placements;
		private final int crossSourcePlacementCount;
		private final int affectedSourceReferenceCount;
		private final int maximumAffectedSourceCount;

		private PackedSourceRecipe(
			final int packedRegionX,
			final int packedRegionY,
			final int manifestPlacementCount,
			final int supersededPlacementCount,
			final List<ReconstructionPlacement> placements) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.manifestPlacementCount = manifestPlacementCount;
			this.supersededPlacementCount = supersededPlacementCount;
			this.placements = Collections.unmodifiableList(
				new ArrayList<ReconstructionPlacement>(placements));
			if (placements.size()
				!= manifestPlacementCount - supersededPlacementCount) {
				throw new IllegalArgumentException(
					"Source recipe count differs from final-live outcome");
			}
			int crossSource = 0;
			int sourceReferences = 0;
			int maximumReferences = 0;
			for (ReconstructionPlacement placement : placements) {
				LayeredAuthoredPlacementIdentity identity =
					placement.getIdentity();
				if (identity.getPackedRegionX() != packedRegionX
					|| identity.getPackedRegionY() != packedRegionY) {
					throw new IllegalArgumentException(
						"Recipe entry is owned by another packed source");
				}
				crossSource += placement.isCrossSource() ? 1 : 0;
				sourceReferences = Math.addExact(
					sourceReferences, placement.getAffectedSourceCount());
				maximumReferences = Math.max(
					maximumReferences, placement.getAffectedSourceCount());
			}
			this.crossSourcePlacementCount = crossSource;
			this.affectedSourceReferenceCount = sourceReferences;
			this.maximumAffectedSourceCount = maximumReferences;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getManifestPlacementCount() {
			return manifestPlacementCount;
		}
		public int getSupersededPlacementCount() {
			return supersededPlacementCount;
		}
		public List<ReconstructionPlacement> getPlacements() {
			return placements;
		}
		public int getReconstructionPlacementCount() {
			return placements.size();
		}
		public int getCrossSourcePlacementCount() {
			return crossSourcePlacementCount;
		}
		public int getAffectedSourceReferenceCount() {
			return affectedSourceReferenceCount;
		}
		public int getMaximumAffectedSourceCount() {
			return maximumAffectedSourceCount;
		}
	}

	/** One retained immutable manifest definition and aligned reach envelope. */
	public static final class ReconstructionPlacement {
		private final LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
			placement;
		private final LayeredPackedRegionAuthoredPlacementDependencyInventory
			.PlacementDependency dependency;

		private ReconstructionPlacement(
			final LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
				placement,
			final LayeredPackedRegionAuthoredPlacementDependencyInventory
				.PlacementDependency dependency) {
			if (placement.getSourceOrdinal() != dependency.getSourceOrdinal()
				|| placement.getKind() != dependency.getKind()) {
				throw new IllegalArgumentException(
					"Recipe definition and dependency identities differ");
			}
			this.placement = placement;
			this.dependency = dependency;
		}

		public LayeredAuthoredPlacementIdentity getIdentity() {
			return placement.getIdentity();
		}
		public int getSourceOrdinal() { return placement.getSourceOrdinal(); }
		public ConstructionKind getKind() { return placement.getKind(); }
		public LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement
			getPlacement() { return placement; }
		public LayeredPackedRegionAuthoredPlacementDependencyInventory
			.PlacementDependency getDependency() { return dependency; }
		public int getAffectedSourceCount() {
			return dependency.getAffectedSourceCount();
		}
		public boolean isCrossSource() { return dependency.isCrossSource(); }
	}
}
