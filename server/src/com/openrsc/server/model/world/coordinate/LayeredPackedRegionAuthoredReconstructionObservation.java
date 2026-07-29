package com.openrsc.server.model.world.coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded read-only reconstruction-recipe projection for exact safety sources.
 *
 * <p>Dependency closure here is diagnostic evidence only. A missing source is
 * not a load request, and a closed selection is not a permit, lease, commit
 * token, teardown plan, or proof that reconstruction can run.</p>
 */
public final class LayeredPackedRegionAuthoredReconstructionObservation {
	private final long generation;
	private final long safetyObservedAtTick;
	private final int recipeSourceCount;
	private final int recipeManifestPlacementCount;
	private final int recipeSupersededPlacementCount;
	private final int recipeReconstructionPlacementCount;
	private final List<SourceObservation> sources;
	private final List<RequirementObservation> requirements;
	private final int authoredSourceCount;
	private final int manifestPlacementCount;
	private final int supersededPlacementCount;
	private final int reconstructionPlacementCount;
	private final int crossSourcePlacementCount;
	private final int affectedSourceReferenceCount;
	private final int selectedRequirementSourceCount;
	private final int missingRequirementSourceCount;

	private LayeredPackedRegionAuthoredReconstructionObservation(
		final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final List<SourceObservation> sources,
		final List<RequirementObservation> requirements) {
		this.generation = recipe.getGeneration();
		this.safetyObservedAtTick = safety.getObservedAtTick();
		this.recipeSourceCount = recipe.getSourceCount();
		this.recipeManifestPlacementCount = recipe.getManifestPlacementCount();
		this.recipeSupersededPlacementCount =
			recipe.getSupersededPlacementCount();
		this.recipeReconstructionPlacementCount =
			recipe.getReconstructionPlacementCount();
		this.sources = Collections.unmodifiableList(
			new ArrayList<SourceObservation>(sources));
		this.requirements = Collections.unmodifiableList(
			new ArrayList<RequirementObservation>(requirements));
		int authoredSources = 0;
		int manifest = 0;
		int superseded = 0;
		int reconstruction = 0;
		int crossSource = 0;
		int sourceReferences = 0;
		for (SourceObservation source : sources) {
			authoredSources += source.getReconstructionPlacementCount() > 0
				? 1 : 0;
			manifest = Math.addExact(
				manifest, source.getManifestPlacementCount());
			superseded = Math.addExact(
				superseded, source.getSupersededPlacementCount());
			reconstruction = Math.addExact(
				reconstruction, source.getReconstructionPlacementCount());
			crossSource = Math.addExact(
				crossSource, source.getCrossSourcePlacementCount());
			sourceReferences = Math.addExact(
				sourceReferences, source.getAffectedSourceReferenceCount());
		}
		int selectedRequirements = 0;
		int missingRequirements = 0;
		for (RequirementObservation requirement : requirements) {
			if (requirement.isSelectedSafetySource()) {
				selectedRequirements = Math.incrementExact(selectedRequirements);
			} else {
				missingRequirements = Math.incrementExact(missingRequirements);
			}
		}
		this.authoredSourceCount = authoredSources;
		this.manifestPlacementCount = manifest;
		this.supersededPlacementCount = superseded;
		this.reconstructionPlacementCount = reconstruction;
		this.crossSourcePlacementCount = crossSource;
		this.affectedSourceReferenceCount = sourceReferences;
		this.selectedRequirementSourceCount = selectedRequirements;
		this.missingRequirementSourceCount = missingRequirements;
	}

	/** Projects one recipe onto the exact same-order safety-source selection. */
	public static LayeredPackedRegionAuthoredReconstructionObservation observe(
		final LayeredPackedRegionAuthoredReconstructionRecipe recipe,
		final LayeredPackedRegionRetirementSafetyAssessment safety,
		final int maximumSafetySources,
		final int maximumRequirementSources) {
		if (recipe == null) {
			throw new NullPointerException("recipe");
		}
		if (safety == null) {
			throw new NullPointerException("safety");
		}
		if (maximumSafetySources < 0
			|| maximumSafetySources
				> LayeredPackedRegionAuthoredReconstructionRecipe
					.MAXIMUM_PACKED_SOURCES
			|| maximumRequirementSources < 0
			|| maximumRequirementSources
				> LayeredPackedRegionAuthoredReconstructionRecipe
					.MAXIMUM_PACKED_SOURCES
			|| safety.getSourceCount() > maximumSafetySources) {
			throw new IllegalArgumentException(
				"Reconstruction observation exceeds its source budget");
		}
		Map<Long, MutableSource> selected =
			new LinkedHashMap<Long, MutableSource>();
		for (LayeredPackedRegionRetirementSafetyAssessment.SourceAssessment
			safetySource : safety.getSources()) {
			MutableSource source = new MutableSource(
				safetySource.getPackedRegionX(),
				safetySource.getPackedRegionY(),
				recipe.findSource(
					safetySource.getPackedRegionX(),
					safetySource.getPackedRegionY()));
			if (selected.put(Long.valueOf(packedSourceKey(
					source.packedRegionX, source.packedRegionY)), source) != null) {
				throw new IllegalArgumentException(
					"Safety observation contains a duplicate packed source");
			}
		}

		Map<Long, MutableRequirement> requirements =
			new LinkedHashMap<Long, MutableRequirement>();
		for (MutableSource source : selected.values()) {
			if (source.recipe == null) {
				continue;
			}
			for (LayeredPackedRegionAuthoredReconstructionRecipe
				.ReconstructionPlacement placement
				: source.recipe.getPlacements()) {
				LayeredPackedRegionAuthoredPlacementDependencyInventory
					.PlacementDependency dependency = placement.getDependency();
				for (int x = dependency.getMinimumPackedRegionX();
					x <= dependency.getMaximumPackedRegionX(); x++) {
					for (int y = dependency.getMinimumPackedRegionY();
						y <= dependency.getMaximumPackedRegionY(); y++) {
						long key = packedSourceKey(x, y);
						MutableRequirement requirement = requirements.get(
							Long.valueOf(key));
						if (requirement == null) {
							if (requirements.size()
								>= maximumRequirementSources) {
								throw new IllegalArgumentException(
									"Reconstruction dependency requirements exceed "
										+ "their source budget");
							}
							requirement = new MutableRequirement(
								x, y, selected.containsKey(Long.valueOf(key)),
								recipe.findSource(x, y) != null);
							requirements.put(Long.valueOf(key), requirement);
						}
						requirement.placementReferenceCount = Math.incrementExact(
							requirement.placementReferenceCount);
						if (source.requirements.add(Long.valueOf(key))) {
							requirement.ownerSourceCount = Math.incrementExact(
								requirement.ownerSourceCount);
						}
					}
				}
			}
		}

		List<SourceObservation> sourceObservations =
			new ArrayList<SourceObservation>(selected.size());
		for (MutableSource source : selected.values()) {
			int selectedRequirements = 0;
			for (Long requirementKey : source.requirements) {
				if (selected.containsKey(requirementKey)) {
					selectedRequirements = Math.incrementExact(
						selectedRequirements);
				}
			}
			sourceObservations.add(new SourceObservation(
				source, selectedRequirements));
		}
		List<MutableRequirement> orderedRequirements =
			new ArrayList<MutableRequirement>(requirements.values());
		Collections.sort(orderedRequirements,
			new Comparator<MutableRequirement>() {
				@Override
				public int compare(
					final MutableRequirement left,
					final MutableRequirement right) {
					int x = Integer.compare(
						left.packedRegionX, right.packedRegionX);
					return x != 0 ? x : Integer.compare(
						left.packedRegionY, right.packedRegionY);
				}
			});
		List<RequirementObservation> requirementObservations =
			new ArrayList<RequirementObservation>(orderedRequirements.size());
		for (MutableRequirement requirement : orderedRequirements) {
			requirementObservations.add(
				new RequirementObservation(requirement));
		}
		return new LayeredPackedRegionAuthoredReconstructionObservation(
			recipe, safety, sourceObservations, requirementObservations);
	}

	public long getGeneration() { return generation; }
	public long getSafetyObservedAtTick() { return safetyObservedAtTick; }
	public int getRecipeSourceCount() { return recipeSourceCount; }
	public int getRecipeManifestPlacementCount() {
		return recipeManifestPlacementCount;
	}
	public int getRecipeSupersededPlacementCount() {
		return recipeSupersededPlacementCount;
	}
	public int getRecipeReconstructionPlacementCount() {
		return recipeReconstructionPlacementCount;
	}
	public List<SourceObservation> getSources() { return sources; }
	public int getSourceCount() { return sources.size(); }
	public int getAuthoredSourceCount() { return authoredSourceCount; }
	public int getManifestPlacementCount() { return manifestPlacementCount; }
	public int getSupersededPlacementCount() {
		return supersededPlacementCount;
	}
	public int getReconstructionPlacementCount() {
		return reconstructionPlacementCount;
	}
	public int getCrossSourcePlacementCount() {
		return crossSourcePlacementCount;
	}
	public int getAffectedSourceReferenceCount() {
		return affectedSourceReferenceCount;
	}
	public List<RequirementObservation> getRequirements() {
		return requirements;
	}
	public int getRequirementSourceCount() { return requirements.size(); }
	public int getSelectedRequirementSourceCount() {
		return selectedRequirementSourceCount;
	}
	public int getMissingRequirementSourceCount() {
		return missingRequirementSourceCount;
	}
	public boolean isSelectionDependencyClosed() {
		return missingRequirementSourceCount == 0;
	}

	/** Recipe and closure counts for one same-order safety source. */
	public static final class SourceObservation {
		private final int packedRegionX;
		private final int packedRegionY;
		private final int manifestPlacementCount;
		private final int supersededPlacementCount;
		private final int reconstructionPlacementCount;
		private final int crossSourcePlacementCount;
		private final int affectedSourceReferenceCount;
		private final int requirementSourceCount;
		private final int selectedRequirementSourceCount;
		private final int missingRequirementSourceCount;

		private SourceObservation(
			final MutableSource source,
			final int selectedRequirementSourceCount) {
			this.packedRegionX = source.packedRegionX;
			this.packedRegionY = source.packedRegionY;
			this.manifestPlacementCount = source.recipe == null ? 0
				: source.recipe.getManifestPlacementCount();
			this.supersededPlacementCount = source.recipe == null ? 0
				: source.recipe.getSupersededPlacementCount();
			this.reconstructionPlacementCount = source.recipe == null ? 0
				: source.recipe.getReconstructionPlacementCount();
			this.crossSourcePlacementCount = source.recipe == null ? 0
				: source.recipe.getCrossSourcePlacementCount();
			this.affectedSourceReferenceCount = source.recipe == null ? 0
				: source.recipe.getAffectedSourceReferenceCount();
			this.requirementSourceCount = source.requirements.size();
			this.selectedRequirementSourceCount =
				selectedRequirementSourceCount;
			this.missingRequirementSourceCount =
				requirementSourceCount - selectedRequirementSourceCount;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public int getManifestPlacementCount() {
			return manifestPlacementCount;
		}
		public int getSupersededPlacementCount() {
			return supersededPlacementCount;
		}
		public int getReconstructionPlacementCount() {
			return reconstructionPlacementCount;
		}
		public int getCrossSourcePlacementCount() {
			return crossSourcePlacementCount;
		}
		public int getAffectedSourceReferenceCount() {
			return affectedSourceReferenceCount;
		}
		public int getRequirementSourceCount() {
			return requirementSourceCount;
		}
		public int getSelectedRequirementSourceCount() {
			return selectedRequirementSourceCount;
		}
		public int getMissingRequirementSourceCount() {
			return missingRequirementSourceCount;
		}
		public boolean isDependencyClosed() {
			return missingRequirementSourceCount == 0;
		}
	}

	/** One unique packed source required by at least one selected recipe entry. */
	public static final class RequirementObservation {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean selectedSafetySource;
		private final boolean authoredRecipeSource;
		private final int ownerSourceCount;
		private final int placementReferenceCount;

		private RequirementObservation(final MutableRequirement requirement) {
			this.packedRegionX = requirement.packedRegionX;
			this.packedRegionY = requirement.packedRegionY;
			this.selectedSafetySource = requirement.selectedSafetySource;
			this.authoredRecipeSource = requirement.authoredRecipeSource;
			this.ownerSourceCount = requirement.ownerSourceCount;
			this.placementReferenceCount =
				requirement.placementReferenceCount;
		}

		public int getPackedRegionX() { return packedRegionX; }
		public int getPackedRegionY() { return packedRegionY; }
		public boolean isSelectedSafetySource() {
			return selectedSafetySource;
		}
		public boolean isAuthoredRecipeSource() {
			return authoredRecipeSource;
		}
		public int getOwnerSourceCount() { return ownerSourceCount; }
		public int getPlacementReferenceCount() {
			return placementReferenceCount;
		}
	}

	private static long packedSourceKey(
		final int packedRegionX,
		final int packedRegionY) {
		return ((long) packedRegionX << 32)
			^ (packedRegionY & 0xFFFFFFFFL);
	}

	private static final class MutableSource {
		private final int packedRegionX;
		private final int packedRegionY;
		private final LayeredPackedRegionAuthoredReconstructionRecipe
			.PackedSourceRecipe recipe;
		private final Set<Long> requirements = new LinkedHashSet<Long>();

		private MutableSource(
			final int packedRegionX,
			final int packedRegionY,
			final LayeredPackedRegionAuthoredReconstructionRecipe
				.PackedSourceRecipe recipe) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.recipe = recipe;
		}
	}

	private static final class MutableRequirement {
		private final int packedRegionX;
		private final int packedRegionY;
		private final boolean selectedSafetySource;
		private final boolean authoredRecipeSource;
		private int ownerSourceCount;
		private int placementReferenceCount;

		private MutableRequirement(
			final int packedRegionX,
			final int packedRegionY,
			final boolean selectedSafetySource,
			final boolean authoredRecipeSource) {
			this.packedRegionX = packedRegionX;
			this.packedRegionY = packedRegionY;
			this.selectedSafetySource = selectedSafetySource;
			this.authoredRecipeSource = authoredRecipeSource;
		}
	}
}
